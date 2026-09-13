"""
Firebase service.

Handles Firebase Realtime Database communication.
"""

from __future__ import annotations
import threading
import time
import uuid
from datetime import datetime
from pathlib import Path

import firebase_admin
from firebase_admin import credentials
from firebase_admin import auth
from firebase_admin import db
from firebase_admin import messaging

from core.config import AppConfig
from core.config import FirebaseConfig
from core.config import IrrigationConfig
from core.config import SensorConfig
from core.logger import AppLogger
from core.device_control import DeviceControl
from core.zone_capacity import validate_zone_configurations
from hardware.esp32_sensor_config_publisher import (
    Esp32SensorConfigPublisher,
)

from models.command_state import CommandState
from models.sensor_reading import SensorReading
from models.watering_record import WateringRecord
from models.watering_result import WateringResult
from models.pending_watering_measurement import PendingWateringMeasurement
from models.watering_statistics import WateringStatistics
from models.health_status import HealthStatus
from models.irrigation_decision import IrrigationDecision
from models.sensor_history_entry import SensorHistoryEntry
from models.adaptive_irrigation_recommendation import AdaptiveIrrigationRecommendation
from models.soil_learning_profile import SoilLearningProfile
from models.ai_decision_summary import AIDecisionSummary
from models.ai_explanation import AIExplanation
from models.moisture_prediction import MoisturePrediction
from models.prediction_accuracy import PredictionAccuracy
from models.unified_confidence import UnifiedConfidence
from models.prediction_validation_status import PredictionValidationStatus


def _valid_uuid(value: str) -> bool:
    try:
        return str(uuid.UUID(str(value))).lower() == str(value).lower()
    except (ValueError, TypeError, AttributeError):
        return False


class FirebaseService:

    """
    Firebase Realtime Database service.
    """

    # -------------------------------------------------
    # Initialization
    # -------------------------------------------------
    
    def __init__(self) -> None:

        self._logger = AppLogger().logger

        self._initialized = False

        self._command_state = CommandState(
            auto_mode=True,
            relay=False,
            enabled=False,
            moisture_limit=IrrigationConfig.DEFAULT_MOISTURE_LIMIT,
            pump_duration=IrrigationConfig.DEFAULT_PUMP_DURATION_SECONDS,
            restart_delta=IrrigationConfig.DEFAULT_RESTART_DELTA,
            cooldown_seconds=IrrigationConfig.DEFAULT_COOLDOWN_SECONDS,
        )

        self._command_lock = threading.Lock()

        self._sync_thread: threading.Thread | None = None
        self._command_listener = None
        self._running = False
        self._stop_event = threading.Event()

        self._retry_delay = 0.5
        self._max_retry_delay = 30.0

        self._zone_by_sensor_id: dict[str, str] = {}
        self._zone_config_by_sensor_id: dict[str, dict] = {}
        self._zone_map_refreshed_at = 0.0
        self._zone_map_refresh_seconds = max(
            10.0,
            FirebaseConfig.ZONE_MAP_REFRESH_INTERVAL_SECONDS,
        )
        self._zone_map_signature: tuple[tuple[str, str], ...] | None = None
        self._published_sensor_configs: dict[
            str,
            tuple[bool, int, int],
        ] = {}
        self._published_valve_hardware_map: dict[
            str,
            tuple[int, int],
        ] = {}
        self._published_zone_sensor_signatures: dict[
            str,
            tuple[int, float, int, str, int, int],
        ] = {}
        self._zone_sensor_published_at: dict[str, float] = {}
        self._last_push_sent_at: dict[str, float] = {}
        self._owner_uid_cache: dict[str, tuple[bool, float]] = {}
        self._active_error_incident_id = ""
        self._sensor_config_publisher = (
            Esp32SensorConfigPublisher(
                broker=SensorConfig.MQTT_BROKER,
                port=SensorConfig.MQTT_PORT,
            )
        )

        self.device_control = DeviceControl()

    def initialize(self) -> None:
        """
        Initialize Firebase.
        """

        if self._initialized:
            return

        try:

            credentials_path = Path(
                FirebaseConfig.CREDENTIALS_FILE,
            )

            if not credentials_path.is_absolute():
                credentials_path = (
                    Path(__file__).resolve().parents[1]
                    / credentials_path
                )

            credential = credentials.Certificate(
                credentials_path,
            )

            firebase_admin.initialize_app(
                credential,
                {
                    "databaseURL": FirebaseConfig.DATABASE_URL,
                    "httpTimeout": FirebaseConfig.HTTP_TIMEOUT_SECONDS,
                },
            )

            self._initialized = True

            self.initialize_commands()
            self._sensor_config_publisher.start()
            self._refresh_zone_sensor_map()

            initial_command_state = self.get_commands()

            with self._command_lock:
                self._command_state = initial_command_state

            self.check_restart_command(
                initial_command_state,
            )

            self.increment_restart_count()

            self.start_command_sync()

            self._logger.info(
                "Firebase initialized successfully.",
            )

        except Exception as exc:

            self._logger.exception(exc)

            raise
        
    def initialize_commands(
        self,
    ) -> None:
        """
        Create default commands only once.
        """

        commands_ref = self._device_ref().child(
            "commands",
        )

        if commands_ref.get() is not None:

            return

        commands_ref.set(
            {
                "auto_mode": True,
                "relay": False,
                "enabled": True,
                "moisture_limit": IrrigationConfig.DEFAULT_MOISTURE_LIMIT,
                "pump_duration": IrrigationConfig.DEFAULT_PUMP_DURATION_SECONDS,
                "restart_delta": IrrigationConfig.DEFAULT_RESTART_DELTA,
                "cooldown_seconds": IrrigationConfig.DEFAULT_COOLDOWN_SECONDS,

                # Device restart command

                # One-shot selected-zone assistant reset command.
                "irrigation_assistant_reset": {
                    "requested": False,
                },
                "network_configuration": {
                    "requested": False,
                },
                "restart_device": False,
            },
        )

        self._logger.info(
            "Default commands created.",
        )

    def start_command_sync(self) -> None:
        """
        Start realtime synchronization with a safe polling fallback.
        """

        if self._running:
            return

        self._running = True
        self._stop_event.clear()

        try:
            self._command_listener = (
                self._device_ref()
                .child("commands")
                .listen(self._handle_command_event)
            )
            self._logger.info(
                "Realtime Firebase command synchronization started.",
            )
            return
        except Exception as exc:
            self._command_listener = None
            self._logger.warning(
                "Realtime Firebase command listener unavailable; "
                "using low-frequency fallback polling. error=%s",
                exc,
            )

        self._sync_thread = threading.Thread(
            target=self._sync_commands,
            daemon=True,
            name="FirebaseSyncFallback",
        )

        self._sync_thread.start()

    def stop_command_sync(self) -> None:
        """
        Stop background synchronization.
        """

        self._running = False
        self._stop_event.set()

        listener = self._command_listener
        self._command_listener = None
        if listener is not None:
            try:
                listener.close()
            except Exception as exc:
                self._logger.debug(
                    "Firebase command listener close failed: %s",
                    exc,
                )

        sync_thread = self._sync_thread
        self._sync_thread = None
        if sync_thread is not None:
            sync_thread.join(
                timeout=2,
            )

            if sync_thread.is_alive():
                self._logger.warning(
                    "Command synchronization thread did not stop gracefully.",
                )

        self._sensor_config_publisher.stop()

    # -------------------------------------------------
    # Device status
    # -------------------------------------------------

    def update_status(self) -> None:
        """
        Update the device heartbeat without changing an active error.

        ``last_error`` is owned by report_error()/clear_error(). Clearing it
        from the periodic heartbeat briefly turns one continuing failure into
        a recovery and lets clients create duplicate incidents.
        """

        self._device_ref().child(
            "status",
        ).update(
            {
                "online": True,
                "version": AppConfig.VERSION,
                "last_seen": datetime.now().isoformat(),
                "last_seen_epoch": int(time.time()),
            },
        )
        self._process_access_request_notifications()

    def has_active_error(self) -> bool:
        """Return whether Firebase still contains an unresolved service error."""

        try:
            status = self._device_ref().child("status").get() or {}
        except Exception as exc:
            self._logger.warning(
                "Active device error state could not be restored: %s",
                exc,
            )
            return False

        return bool(str(status.get("last_error", "")).strip())

    def set_online(
        self,
        online: bool,
    ) -> None:
        """
        Update device online status.
        """

        self._device_ref().child(
            "status",
        ).update(
            {
                "online": online,
                "last_seen": datetime.now().isoformat(),
                "last_seen_epoch": int(time.time()),
            },
        )

    def update_runtime_status(
        self,
        *,
        relay: bool,
        uptime: int,
        sensor_time: str,
        watering_state: str,
        cooldown_remaining: int,
    ) -> None:
        """
        Update runtime status information.
        """

        self._device_ref().child(
            "status",
        ).update(
            {
                "relay": relay,
                "uptime_seconds": uptime,
                "last_sensor_read": sensor_time,
                "watering_state": watering_state,
                "cooldown_remaining": cooldown_remaining,
            },
        )

    def update_relay_status(
        self,
        relay: bool,
    ) -> None:
        """
        Update physical relay status immediately.
        """

        self._device_ref().child(
            "status",
        ).update(
            {
                "relay": relay,
                "last_seen": datetime.now().isoformat(),
            },
        )

    def update_active_zone_valve(
        self,
        valve_id: str | None,
        is_open: bool,
        zone_id: str | None = None,
        hardware_valve_id: str | None = None,
        is_physical: bool | None = None,
    ) -> None:
        """
        Publish the selected valve state for UI and simulation tests.
        """

        from core.config import ValveConfig

        physical = is_physical
        if physical is None:
            # Compatibility fallback for callers that do not yet provide the
            # live controller result.
            physical = (
                (valve_id or hardware_valve_id)
                in ValveConfig.PHYSICAL_VALVE_IDS
                and not ValveConfig.SIMULATION_MODE
            )

        valve_mode = "PHYSICAL" if physical else "SIMULATION"
        updated_at = datetime.now().isoformat()
        valve_status = {
            "active_valve_id": valve_id or "",
            "valve_open": is_open,
            "valve_mode": valve_mode,
        }

        self._device_ref().child("irrigation_hardware").update(
            {
                **valve_status,
                "pump_interlock": (
                    "READY" if physical else "BLOCKED"
                ),
                "updated_at": updated_at,
            },
        )

        # WateringControlActivity observes /status. Mirror the authoritative
        # state so a real open valve is never rendered as simulated.
        self._device_ref().child("status").update(
            {
                **valve_status,
                "last_seen": updated_at,
            },
        )

        if zone_id:
            self._device_ref().child(
                f"zones/{zone_id}/irrigation_status",
            ).update(
                {
                    "watering_active": is_open,
                    "updated_at": datetime.now().isoformat(),
                },
            )
            self._device_ref().child(
                f"zones/{zone_id}",
            ).update(
                {
                    "valve_mode": (
                        "PHYSICAL" if physical else "SIMULATION"
                    ),
                },
            )

    def reset_all_zone_watering_states(self) -> None:
        """Clear stale per-zone watering flags after startup or safe shutdown."""

        zones = self._device_ref().child("zones").get()
        if not isinstance(zones, dict):
            return

        updated_at = datetime.now().isoformat()
        updates: dict[str, object] = {}
        for zone_key, value in zones.items():
            if not isinstance(value, dict):
                continue
            normalized_key = str(zone_key or "").strip()
            if not normalized_key:
                continue
            prefix = f"zones/{normalized_key}/irrigation_status/"
            updates[prefix + "watering_active"] = False
            updates[prefix + "selected_for_watering"] = False
            updates[prefix + "updated_at"] = updated_at

        if updates:
            self._device_ref().update(updates)

    def update_health_status(
        self,
        health: HealthStatus,
    ) -> None:
        """
        Upload Raspberry Pi health information.
        """

        self._device_ref().child(
            "health",
        ).update(
            {
                "cpu_temperature":
                    health.cpu_temperature,

                "cpu_usage":
                    health.cpu_usage,

                "memory_usage":
                    health.memory_usage,

                "disk_usage":
                    health.disk_usage,

                "uptime_seconds":
                    health.uptime_seconds,

                "ip_address":
                    health.ip_address,

                "wifi_signal":
                    health.wifi_signal,

                # Genel aktif throttling durumu
                "is_throttled":
                    health.is_throttled,

                # Ham vcgencmd get_throttled değeri
                "throttled_raw":
                    health.throttled_raw,

                # Şu anda aktif olan durumlar
                "under_voltage_now":
                    health.under_voltage_now,

                "frequency_capped_now":
                    health.frequency_capped_now,

                "throttled_now":
                    health.throttled_now,

                "soft_temperature_limit_now":
                    health.soft_temperature_limit_now,

                # Geçmişte oluşmuş durumlar
                "under_voltage_history":
                    health.under_voltage_history,

                "frequency_capped_history":
                    health.frequency_capped_history,

                "throttled_history":
                    health.throttled_history,

                "soft_temperature_limit_history":
                    health.soft_temperature_limit_history,

                "firmware":
                    AppConfig.VERSION,

                "updated_at":
                    datetime.now().isoformat(),
            },
        )

    def update_ads1115_status(
        self,
        *,
        node_online: bool,
        primary_available: bool,
        secondary_available: bool,
        firmware: str,
        rssi: int,
        uptime_seconds: int,
        received_at_epoch: int,
    ) -> None:
        """Publish independent ESP32 ADS1115 module health."""

        self._device_ref().child("health").update({
            "esp32_node_online": bool(node_online),
            "ads1115_primary_available": bool(primary_available),
            "ads1115_secondary_available": bool(secondary_available),
            "ads1115_primary_address": "0x48",
            "ads1115_secondary_address": "0x49",
            "ads1115_status_firmware": str(firmware or "")[:32],
            "ads1115_status_rssi": int(rssi),
            "ads1115_status_uptime_seconds": int(uptime_seconds),
            "ads1115_status_updated_at_epoch": int(received_at_epoch),
        })

    def increment_restart_count(self) -> None:
        """
        Increment device restart counter.
        """

        status_ref = self._device_ref().child(
            "status",
        )

        status = status_ref.get() or {}

        restart_count = int(
            status.get(
                "restart_count",
                0,
            ),
        )

        status_ref.update(
            {
                "restart_count": restart_count + 1,
            },
        )

    def report_error(
        self,
        message: str,
    ) -> None:
        """
        Save last application error.
        """

        status_ref = self._device_ref().child("status")
        if not self._active_error_incident_id:
            try:
                current_status = status_ref.get() or {}
                active_id = str(current_status.get("error_incident_id", "")).strip()
                if active_id and str(current_status.get("last_error", "")).strip():
                    self._active_error_incident_id = active_id
                else:
                    self._active_error_incident_id = uuid.uuid4().hex
            except Exception:
                self._active_error_incident_id = uuid.uuid4().hex

        status_ref.update(
            {
                "last_error": message,
                "last_seen": datetime.now().isoformat(),
                "error_incident_id": self._active_error_incident_id,
            },
        )

        reminder_seconds = 6 * 60 * 60
        normalized_message = message.lower()
        sensor_failure = any(
            marker in normalized_message
            for marker in (
                "sensor",
                "wireless",
                "mqtt",
                "soil moisture",
                "measurement",
            )
        )
        self._send_push_notification(
            event_code=(
                "DEVICE_SENSOR_UNAVAILABLE"
                if sensor_failure
                else "DEVICE_WARNING"
            ),
            event_id=f"device-error:incident:{self._active_error_incident_id}",
            zone_id="",
            minimum_interval_seconds=reminder_seconds,
        )

    def clear_error(self) -> None:
        """
        Clear the active application error after a successful
        recovery cycle.
        """

        self._device_ref().child(
            "status",
        ).update(
            {
                "last_error": "",
                "last_seen": datetime.now().isoformat(),
                "error_incident_id": "",
            },
        )
        self._active_error_incident_id = ""

    # -------------------------------------------------
    # Sensor
    # -------------------------------------------------

    def update_zone_sensors(
        self,
        readings: dict[str, SensorReading],
    ) -> None:
        """
        Upload all fresh MQTT readings to their garden zones.

        The legacy top-level sensor node remains managed by
        update_sensor() for the primary irrigation sensor.
        """

        if not readings:
            return

        current_time = time.monotonic()
        publishable_readings: dict[str, SensorReading] = {}
        publishable_signatures: dict[
            str,
            tuple[int, float, int, str, int, int],
        ] = {}

        for sensor_id, reading in readings.items():
            signature = self._sensor_reading_signature(reading)
            last_signature = self._published_zone_sensor_signatures.get(
                sensor_id,
            )
            last_published_at = self._zone_sensor_published_at.get(
                sensor_id,
                0.0,
            )
            heartbeat_due = (
                current_time - last_published_at
                >= SensorConfig.MQTT_STALE_AFTER_SECONDS
            )
            if signature == last_signature and not heartbeat_due:
                continue
            publishable_readings[sensor_id] = reading
            publishable_signatures[sensor_id] = signature

        if not publishable_readings:
            return

        if (
            not self._zone_by_sensor_id
            or (
                current_time
                - self._zone_map_refreshed_at
                >= self._zone_map_refresh_seconds
            )
        ):
            self._refresh_zone_sensor_map()

        updates: dict[str, object] = {}
        published_sensor_ids: list[str] = []
        updated_at = datetime.now().isoformat()
        updated_at_epoch = int(time.time())

        for sensor_id, reading in publishable_readings.items():
            zone_id = self._zone_by_sensor_id.get(
                sensor_id,
            )

            if zone_id is None:
                continue

            published_sensor_ids.append(sensor_id)

            prefix = f"zones/{zone_id}"
            # sensor_id is configuration owned by the app. Telemetry must
            # never overwrite it while an older map is still cached.

            updates.update(
                {
                    f"{prefix}/raw": reading.raw,
                    f"{prefix}/voltage": round(
                        reading.voltage,
                        3,
                    ),
                    f"{prefix}/moisture": reading.moisture,
                    f"{prefix}/firmware": reading.firmware,
                    f"{prefix}/rssi": reading.rssi,
                    f"{prefix}/uptime_seconds":
                        reading.uptime_seconds,
                    f"{prefix}/updated_at": updated_at,
                    f"{prefix}/updated_at_epoch":
                        updated_at_epoch,
                },
            )

        if updates:
            self._device_ref().update(
                updates,
            )
            for sensor_id in published_sensor_ids:
                self._published_zone_sensor_signatures[sensor_id] = (
                    publishable_signatures[sensor_id]
                )
                self._zone_sensor_published_at[sensor_id] = current_time

    @staticmethod
    def _sensor_reading_signature(
        reading: SensorReading,
    ) -> tuple[int, float, int, str, int, int]:
        """Identify one MQTT sample without changing sensor cadence."""

        return (
            reading.raw,
            round(reading.voltage, 4),
            reading.moisture,
            reading.firmware,
            reading.rssi,
            reading.uptime_seconds,
        )

    def _refresh_zone_sensor_map(self) -> None:
        """
        Cache the Firebase zone assigned to each sensor ID.
        """

        zones = (
            self._device_ref()
            .child("zones")
            .get()
        )

        (
            zone_by_sensor_id,
            zone_config_by_sensor_id,
        ) = validate_zone_configurations(
            zones,
            self._logger.warning,
        )

        map_signature = self._zone_sensor_map_signature(
            zone_by_sensor_id,
        )
        map_changed = map_signature != self._zone_map_signature

        self._zone_by_sensor_id = (
            zone_by_sensor_id
        )
        self._zone_config_by_sensor_id = (
            zone_config_by_sensor_id
        )
        self._zone_map_signature = map_signature
        self._zone_map_refreshed_at = (
            time.monotonic()
        )

        self._publish_saved_sensor_configs(
            zone_config_by_sensor_id,
        )
        self._publish_valve_hardware_map(
            zone_config_by_sensor_id,
        )

        if map_changed:
            self._logger.info(
                "Garden zone sensor map refreshed. count=%d",
                len(self._zone_by_sensor_id),
            )

    @staticmethod
    def _zone_sensor_map_signature(
        zone_by_sensor_id: dict[str, str],
    ) -> tuple[tuple[str, str], ...]:
        """Return a stable signature for sensor-to-zone assignments."""

        return tuple(sorted(zone_by_sensor_id.items()))

    def _publish_saved_sensor_configs(
        self,
        zones_by_sensor: dict[str, dict],
    ) -> None:
        """Publish a complete retained map so removed sensors cannot stay enabled."""
        default_dry_raw = 12650
        default_wet_raw = 505
        supported_sensor_ids = tuple(
            f"soil-{index:03d}"
            for index in range(1, 9)
        )
        current_configs: dict[str, tuple[bool, int, int]] = {}

        for sensor_id in supported_sensor_ids:
            zone = zones_by_sensor.get(sensor_id)
            if not isinstance(zone, dict):
                current_configs[sensor_id] = (
                    False,
                    default_dry_raw,
                    default_wet_raw,
                )
                continue

            enabled = bool(zone.get("sensor_enabled", False))
            try:
                dry_raw = int(
                    zone.get(
                        "sensor_calibration_dry_raw",
                        default_dry_raw,
                    )
                )
                wet_raw = int(
                    zone.get(
                        "sensor_calibration_wet_raw",
                        default_wet_raw,
                    )
                )
            except (TypeError, ValueError):
                dry_raw = default_dry_raw
                wet_raw = default_wet_raw
                self._logger.warning(
                    "Invalid sensor calibration replaced with safe defaults. "
                    "sensor_id=%s",
                    sensor_id,
                )

            if dry_raw <= wet_raw:
                dry_raw = default_dry_raw
                wet_raw = default_wet_raw
                self._logger.warning(
                    "Unsafe sensor calibration replaced with safe defaults. "
                    "sensor_id=%s",
                    sensor_id,
                )

            current_configs[sensor_id] = (
                enabled,
                dry_raw,
                wet_raw,
            )

        for sensor_id, config in current_configs.items():
            if self._published_sensor_configs.get(sensor_id) == config:
                continue

            try:
                self._sensor_config_publisher.publish(
                    sensor_id=sensor_id,
                    enabled=config[0],
                    dry_raw=config[1],
                    wet_raw=config[2],
                )
                self._published_sensor_configs[sensor_id] = config
                self._logger.info(
                    "ESP32 sensor configuration published. sensor_id=%s enabled=%s",
                    sensor_id,
                    config[0],
                )
            except Exception as exc:
                self._logger.warning(
                    "ESP32 sensor configuration publish failed. sensor_id=%s error=%s",
                    sensor_id,
                    exc,
                )

    def _publish_valve_hardware_map(
        self,
        zones_by_sensor: dict[str, dict],
    ) -> None:
        """Expose the fixed Pi wiring map to the app without making it editable."""
        from core.config import ValveConfig

        updates: dict[str, object] = {}
        for zone in zones_by_sensor.values():
            zone_id = str(zone.get("zone_id", "")).strip()
            valve_id = str(zone.get("valve_id", "")).strip()
            gpio = ValveConfig.GPIO_PINS.get(valve_id)
            physical_pin = ValveConfig.GPIO_PHYSICAL_PINS.get(valve_id)
            if not zone_id or gpio is None or physical_pin is None:
                continue

            current = (gpio, physical_pin)
            if self._published_valve_hardware_map.get(zone_id) == current:
                continue

            path = f"zones/{zone_id}/"
            updates[path + "valve_gpio_bcm"] = gpio
            updates[path + "valve_gpio_physical_pin"] = physical_pin
            self._published_valve_hardware_map[zone_id] = current

        if updates:
            self._device_ref().update(updates)

    def get_zone_config_for_sensor(
        self,
        sensor_id: str,
    ) -> dict | None:
        """
        Return the cached irrigation configuration for a sensor.
        """

        zone = self._zone_config_by_sensor_id.get(
            sensor_id,
        )

        if zone is None:
            return None

        return dict(zone)

    def get_all_zone_configs_by_sensor(
        self,
    ) -> dict[str, dict]:
        """
        Return a copy of all cached sensor-to-zone settings.
        """

        return {
            sensor_id: dict(zone)
            for sensor_id, zone
            in self._zone_config_by_sensor_id.items()
        }

    @staticmethod
    def _active_season_ids_from_zone(zone: object) -> tuple[str, ...]:
        """Return every active crop season, keeping the primary id first."""
        if not isinstance(zone, dict):
            return ()
        season = zone.get("season")
        if not isinstance(season, dict):
            return ()
        values: list[str] = []
        primary = str(season.get("active_season_id", "")).strip()
        if primary:
            values.append(primary)
        active = season.get("active_season_ids")
        if isinstance(active, dict):
            for key, enabled in active.items():
                value = str(key).strip()
                if enabled and value:
                    values.append(value)
        elif isinstance(active, (list, tuple)):
            for item in active:
                value = str(item).strip()
                if value:
                    values.append(value)
        return tuple(dict.fromkeys(values))

    @staticmethod
    def _season_scope_from_zone(zone: object) -> tuple[bool, str, bool]:
        """Return (watering_allowed, active_season_id, includes_legacy)."""
        if not isinstance(zone, dict):
            return True, "", True
        season = zone.get("season")
        if not isinstance(season, dict):
            # Backward compatibility: installations created before season
            # management continue inside their one-time legacy season.
            return True, "", True
        status = str(season.get("status", "")).strip().upper()
        active_season_ids = FirebaseService._active_season_ids_from_zone(zone)
        season_id = active_season_ids[0] if active_season_ids else ""
        includes_legacy = bool(
            season.get("include_legacy_records", False)
        )
        if not status:
            return True, season_id, True
        return status == "ACTIVE" and bool(season_id), season_id, includes_legacy

    def get_zone_season_scope(
        self,
        zone_id: str,
        *,
        fresh: bool = False,
    ) -> tuple[bool, str, bool]:
        """Read the season boundary used by watering and AI learning."""
        normalized_zone_id = str(zone_id or "").strip()
        if not normalized_zone_id:
            return True, "", True
        zone = None
        if not fresh:
            for cached in self._zone_config_by_sensor_id.values():
                if str(cached.get("zone_id", "")).strip() == normalized_zone_id:
                    zone = cached
                    break
        if zone is None:
            zone = self._device_ref().child(
                f"zones/{normalized_zone_id}"
            ).get()
        return self._season_scope_from_zone(zone)

    def get_zone_active_season_ids(
        self,
        zone_id: str,
        *,
        fresh: bool = False,
    ) -> tuple[str, ...]:
        """Read every crop season currently sharing one physical zone."""
        normalized_zone_id = str(zone_id or "").strip()
        if not normalized_zone_id:
            return ()
        zone = None
        if not fresh:
            for cached in self._zone_config_by_sensor_id.values():
                if str(cached.get("zone_id", "")).strip() == normalized_zone_id:
                    zone = cached
                    break
        if zone is None:
            zone = self._device_ref().child(
                f"zones/{normalized_zone_id}"
            ).get()
        return self._active_season_ids_from_zone(zone)

    def verify_zone_seasons_before_watering(
        self,
        zone_id: str,
    ) -> tuple[bool, tuple[str, ...]]:
        """Use one fresh server read before opening a shared physical valve."""
        normalized_zone_id = str(zone_id or "").strip()
        if not normalized_zone_id:
            return True, ()
        zone = self._device_ref().child(
            f"zones/{normalized_zone_id}"
        ).get()
        allowed, primary, _ = self._season_scope_from_zone(zone)
        season_ids = self._active_season_ids_from_zone(zone)
        if not season_ids and primary:
            season_ids = (primary,)
        return allowed, season_ids

    def verify_zone_season_before_watering(
        self,
        zone_id: str,
    ) -> tuple[bool, str]:
        """Backward-compatible primary-season view for older callers."""
        allowed, season_ids = self.verify_zone_seasons_before_watering(zone_id)
        return allowed, season_ids[0] if season_ids else ""

    def get_physical_valve_ids(self) -> set[str]:
        """Return only zones explicitly approved for real valve control."""
        return {
            str(zone.get("valve_id", "")).strip()
            for zone in self._zone_config_by_sensor_id.values()
            if str(zone.get("valve_mode", "SIMULATION")).upper()
            == "PHYSICAL"
            and str(zone.get("valve_id", "")).strip()
        }

    def update_zone_irrigation_decisions(
        self,
        states: dict[str, dict],
    ) -> None:
        """
        Publish independent irrigation decisions under each zone.
        """

        if not states:
            return

        updates: dict[str, object] = {}
        updated_at = datetime.now().isoformat()

        for zone_id, state in states.items():
            prefix = f"zones/{zone_id}/irrigation_status"
            for field, value in state.items():
                updates[f"{prefix}/{field}"] = value
            updates[f"{prefix}/updated_at"] = updated_at

        self._device_ref().update(updates)

    def update_zone_ai_states(
        self,
        states: dict[str, dict],
        garden_summary: dict,
    ) -> None:
        """Publish every zone AI state and the separate garden summary."""

        if not states and not garden_summary:
            return

        updates: dict[str, object] = {}
        updated_at = datetime.now().isoformat()

        for zone_id, state in states.items():
            prefix = f"zones/{zone_id}/ai"
            for field, value in state.items():
                updates[f"{prefix}/{field}"] = value
            updates[f"{prefix}/updated_at"] = updated_at

        if garden_summary:
            prefix = "ai/garden_summary"
            for field, value in garden_summary.items():
                updates[f"{prefix}/{field}"] = value
            updates[f"{prefix}/updated_at"] = updated_at

        self._device_ref().update(updates)

    def update_zone_cooldown(
        self,
        *,
        zone_id: str,
        cooldown_until_epoch: int,
        cooldown_remaining: int,
    ) -> None:
        """
        Persist a zone cooldown immediately after watering.
        """

        self._device_ref().child(
            f"zones/{zone_id}/irrigation_status",
        ).update(
            {
                "cooldown_active": cooldown_remaining > 0,
                "cooldown_remaining": max(
                    0,
                    int(cooldown_remaining),
                ),
                "cooldown_until_epoch": max(
                    0,
                    int(cooldown_until_epoch),
                ),
                "updated_at": datetime.now().isoformat(),
            },
        )
    
    def save_sensor_history(
        self,
        entry: SensorHistoryEntry,
    ) -> None:
        """
        Save one sensor history entry.
        """

        history_ref = (
            self._device_ref()
            .child("sensor_history")
            .push()
        )

        history_ref.set(
            {
                "moisture":
                    entry.moisture,

                "sensor_id":
                    entry.sensor_id,

                "voltage":
                    round(
                        entry.voltage,
                        3,
                    ),

                "raw":
                    entry.raw,

                "trend_classification":
                    entry.trend_classification,

                "moisture_change_per_minute":
                    round(
                        entry.moisture_change_per_minute,
                        3,
                    ),

                "trend_sample_count":
                    entry.trend_sample_count,

                "trend_duration_seconds":
                    round(
                        entry.trend_duration_seconds,
                        2,
                    ),

                "average_moisture":
                    round(
                        entry.average_moisture,
                        2,
                    ),

                "recorded_at":
                    entry.recorded_at,
            },
        )

    def load_recent_sensor_history(
        self,
        *,
        limit: int = 20,
        sensor_id: str = "",
    ) -> list[tuple[int, str]]:
        """
        Load valid persisted observations for AI learning recovery.
        """

        if limit <= 0:
            return []

        data = (
            self._device_ref()
            .child("sensor_history")
            .order_by_key()
            .limit_to_last(max(limit * 5, 100))
            .get()
        ) or {}

        history: list[tuple[int, str]] = []

        for item in data.values():
            if not isinstance(item, dict):
                continue

            item_sensor_id = str(item.get("sensor_id", ""))
            if sensor_id and item_sensor_id and item_sensor_id != sensor_id:
                continue

            try:
                moisture = int(item.get("moisture", -1))
                recorded_at = str(item.get("recorded_at", ""))
            except (TypeError, ValueError):
                continue

            if 0 <= moisture <= 100 and recorded_at:
                history.append((moisture, recorded_at))

        return history[-limit:]

    # -------------------------------------------------
    # Watering
    # -------------------------------------------------

    def save_pending_watering(
        self,
        pending: PendingWateringMeasurement,
    ) -> None:
        self._device_ref().child(
            f"irrigation_runtime/pending_waterings/{pending.pending_key}"
        ).set(pending.to_payload())

    def load_pending_waterings(self) -> list[PendingWateringMeasurement]:
        raw = self._device_ref().child(
            "irrigation_runtime/pending_waterings"
        ).get() or {}
        if not isinstance(raw, dict):
            return []

        pending_items = []
        for pending_key, payload in raw.items():
            if not isinstance(payload, dict):
                continue
            normalized = dict(payload)
            normalized.setdefault("pending_key", str(pending_key))
            pending = PendingWateringMeasurement.from_payload(normalized)
            if pending is not None:
                pending_items.append(pending)

        return sorted(
            pending_items,
            key=lambda item: item.finalize_after_epoch,
        )

    def delete_pending_watering(self, pending_key: str) -> None:
        if pending_key:
            self._device_ref().child(
                f"irrigation_runtime/pending_waterings/{pending_key}"
            ).delete()

    def update_zone_irrigation_safety_state(
        self,
        *,
        zone_id: str,
        completed_watering_cycles: int,
        waiting_for_moisture_recovery: bool,
    ) -> None:
        if not zone_id:
            return
        self._device_ref().child(
            f"zones/{zone_id}/irrigation_status"
        ).update({
            "completed_watering_cycles": max(
                0,
                int(completed_watering_cycles),
            ),
            "waiting_for_moisture_recovery": bool(
                waiting_for_moisture_recovery
            ),
            "safety_state_updated_at_epoch": int(time.time()),
        })

    def save_watering(
        self,
        result: WateringResult,
        record: WateringRecord,
    ) -> None:
        """
        Save all watering related data.
        """
        history_ref = self._device_ref().child(
            f"watering_history/{record.firebase_key}"
        )
        if history_ref.get() is not None:
            self._logger.info(
                "Duplicate watering save skipped. key=%s",
                record.firebase_key,
            )
            return


        statistics = self.get_statistics()

        today = datetime.now().date().isoformat()

        if statistics.statistics_date != today:

            statistics.statistics_date = today
            statistics.waterings_today = 0
            statistics.watering_seconds_today = 0

        statistics.total_waterings += 1

        if result.completed:
            statistics.completed_waterings += 1
        else:
            statistics.interrupted_waterings += 1

        statistics.total_watering_seconds += result.duration

        statistics.waterings_today += 1

        statistics.watering_seconds_today += result.duration

        statistics.last_watering_duration = result.duration

        statistics.last_stop_reason = result.stop_reason

        statistics.before_moisture = record.moisture_before

        statistics.after_moisture = record.moisture_after

        statistics.moisture_delta = record.moisture_delta

        if statistics.total_waterings > 0:

            statistics.average_duration = int(
                statistics.total_watering_seconds
                / statistics.total_waterings
            )

            statistics.success_rate = int(
                statistics.completed_waterings
                * 100
                / statistics.total_waterings
            )

        else:

            statistics.average_duration = 0
            statistics.success_rate = 0

        statistics_data = {
            "total_waterings":
                statistics.total_waterings,

            "completed_waterings":
                statistics.completed_waterings,

            "interrupted_waterings":
                statistics.interrupted_waterings,

            "total_watering_seconds":
                statistics.total_watering_seconds,

            "last_watering_duration":
                statistics.last_watering_duration,

            "last_stop_reason":
                statistics.last_stop_reason,

            "success_rate":
                statistics.success_rate,

            "waterings_today":
                statistics.waterings_today,

            "watering_seconds_today":
                statistics.watering_seconds_today,

            "statistics_date":
                statistics.statistics_date,

            "average_duration":
                statistics.average_duration,

            "before_moisture":
                statistics.before_moisture,

            "after_moisture":
                statistics.after_moisture,

            "moisture_delta":
                statistics.moisture_delta,
        }

        active_season_ids = tuple(record.season_ids)
        if not active_season_ids:
            active_season_ids = self.get_zone_active_season_ids(record.zone_id)
        primary_season_id = record.season_id or (
            active_season_ids[0] if active_season_ids else ""
        )
        history_data = {
            "season_id": primary_season_id,
            "season_ids": list(active_season_ids),

            "started_at":
                record.started_at,

            "finished_at":
                record.finished_at,

            "duration":
                record.duration,

            "moisture_before":
                record.moisture_before,

            "moisture_after":
                record.moisture_after,

            "moisture_delta":
                record.moisture_delta,

            "moisture_limit":
                record.moisture_limit,

            "restart_delta":
                record.restart_delta,

            "cooldown_seconds":
                record.cooldown_seconds,

            "completed":
                record.completed,

            "stop_reason":
                record.stop_reason,

            "mode":
                record.mode,

            "firmware":
                record.firmware,

            "zone_id":
                record.zone_id,

            "sensor_id":
                record.sensor_id,
        }

        updates = {
            f"watering_history/{record.firebase_key}":
                history_data,

            "statistics":
                statistics_data,

            "status/last_watering":
                record.finished_at,
        }

        self._device_ref().update(
            updates,
        )

        if record.completed:
            self._send_push_notification(
                event_code="IRRIGATION_COMPLETED",
                event_id=f"watering:{record.zone_id}:{record.firebase_key}",
                zone_id=record.zone_id,
                duration_seconds=record.duration,
            )
        elif (
            record.duration > 0
            and str(record.stop_reason or "").strip().upper()
            not in {"VALVE_SIMULATION", "SHARED_PUMP_BUSY", "ZERO_DURATION"}
        ):
            self._send_push_notification(
                event_code="IRRIGATION_INTERRUPTED",
                event_id=(
                    f"watering-interrupted:{record.zone_id}:"
                    f"{record.firebase_key}"
                ),
                zone_id=record.zone_id,
                duration_seconds=record.duration,
            )

    def notify_watering_started(
        self,
        *,
        zone_id: str,
        duration: int,
        moisture_before: int,
        started_at: str,
    ) -> None:
        """Notify only after the physical relay has actually switched on."""
        self._send_push_notification(
            event_code="IRRIGATION_STARTED",
            event_id=f"watering-started:{zone_id}:{started_at}",
            zone_id=zone_id,
            duration_seconds=duration,
        )

    def _send_push_notification(
        self,
        *,
        event_code: str,
        event_id: str,
        zone_id: str,
        duration_seconds: int = 0,
        minimum_interval_seconds: int = 0,
        owner_only: bool = False,
    ) -> bool:
        """Send a language-neutral data-only event to AVORA installations.

        Android owns category, priority and user-facing text. Firebase database
        state remains the source of truth, and push delivery never affects the
        irrigation loop.
        """
        now = time.time()
        previous = self._last_push_sent_at.get(event_id, 0.0)
        if minimum_interval_seconds and now - previous < minimum_interval_seconds:
            return False

        try:
            tokens = self._device_ref().child("push_tokens").get() or {}
            if not isinstance(tokens, dict):
                return False

            payload = self._notification_event_payload(
                event_code=event_code,
                event_id=event_id,
                zone_id=zone_id,
                duration_seconds=duration_seconds,
            )
            delivered = False
            for token_key, value in tokens.items():
                token = str(value.get("token", "")) if isinstance(value, dict) else ""
                if not token:
                    continue
                if owner_only and not self._is_owner_token(value):
                    continue
                try:
                    messaging.send(
                        messaging.Message(
                            data=payload,
                            token=token,
                            android=messaging.AndroidConfig(priority="high"),
                        )
                    )
                    delivered = True
                except messaging.UnregisteredError:
                    self._device_ref().child(f"push_tokens/{token_key}").delete()
                except Exception as exc:
                    self._logger.warning("FCM push delivery skipped: %s", exc)
            if delivered:
                self._last_push_sent_at[event_id] = now
        except Exception as exc:
            self._logger.warning("FCM push preparation skipped: %s", exc)
            return False
        return delivered

    def _is_owner_token(self, value: object) -> bool:
        if not isinstance(value, dict):
            return False
        firebase_uid = str(value.get("firebase_uid", "")).strip()
        if not firebase_uid:
            return False
        now = time.time()
        cached = self._owner_uid_cache.get(firebase_uid)
        if cached is not None and now - cached[1] < 300:
            return cached[0]
        try:
            user = auth.get_user(firebase_uid)
            claims = user.custom_claims or {}
            is_owner = (
                str(claims.get("avora_device_id", "")).strip()
                == AppConfig.DEVICE_ID
            )
        except Exception as exc:
            self._logger.warning("FCM owner verification skipped: %s", exc)
            is_owner = False
        self._owner_uid_cache[firebase_uid] = (is_owner, now)
        return is_owner

    def _access_request_notification_ref(self) -> db.Reference:
        return db.reference(
            f"access_request_notifications/{AppConfig.DEVICE_ID}"
        )

    def _process_access_request_notifications(self) -> None:
        """Deliver validated access requests only to verified owner devices."""
        try:
            queue_ref = self._access_request_notification_ref()
            requests = queue_ref.get() or {}
            if not isinstance(requests, dict):
                return
            now_millis = int(time.time() * 1000)
            for requester_uid, value in requests.items():
                if not isinstance(value, dict) or value.get("processed_at_epoch"):
                    continue
                request_id = str(value.get("request_id", "")).strip()
                firebase_uid = str(value.get("firebase_uid", "")).strip()
                try:
                    requested_at = int(value.get("requested_at_epoch", 0) or 0)
                except (TypeError, ValueError):
                    requested_at = 0
                valid = (
                    firebase_uid == str(requester_uid)
                    and _valid_uuid(request_id)
                    and now_millis - 86_400_000
                    <= requested_at
                    <= now_millis + 10_000
                )
                request_ref = queue_ref.child(str(requester_uid))
                if not valid:
                    request_ref.update({
                        "processed_at_epoch": now_millis,
                        "delivery_status": "invalid",
                    })
                    continue
                delivered = self._send_push_notification(
                    event_code="GARDEN_ACCESS_REQUEST",
                    event_id=f"access-request:{request_id}",
                    zone_id="",
                    owner_only=True,
                )
                result = {
                    "delivery_status": "sent" if delivered else "not_delivered",
                    "last_attempt_at_epoch": now_millis,
                }
                if delivered:
                    result["processed_at_epoch"] = now_millis
                request_ref.update(result)
        except Exception as exc:
            self._logger.warning(
                "Garden access notification processing skipped: %s", exc
            )

    @staticmethod
    def _notification_event_payload(
        *,
        event_code: str,
        event_id: str,
        zone_id: str,
        duration_seconds: int = 0,
    ) -> dict[str, str]:
        """Build the stable, language-neutral Pi-to-Android event contract."""
        return {
            "event_code": str(event_code or "").strip().upper(),
            "event_id": str(event_id or "").strip(),
            "zone_id": str(zone_id or "").strip(),
            "duration_seconds": str(max(0, int(duration_seconds))),
        }

    def get_statistics(
        self,
    ) -> WateringStatistics:
        """
        Read watering statistics.
        """

        data = (
            self._device_ref()
            .child("statistics")
            .get()
        ) or {}

        return WateringStatistics(
            total_waterings=int(
                data.get(
                    "total_waterings",
                    0,
                ),
            ),
            completed_waterings=int(
                data.get(
                    "completed_waterings",
                    0,
                ),
            ),
            interrupted_waterings=int(
                data.get(
                    "interrupted_waterings",
                    0,
                ),
            ),
            total_watering_seconds=int(
                data.get(
                    "total_watering_seconds",
                    0,
                ),
            ),
            last_watering_duration=int(
                data.get(
                    "last_watering_duration",
                    0,
                ),
            ),

            last_stop_reason=str(
                data.get(
                    "last_stop_reason",
                    "",
                ),
            ),

            success_rate=int(
                data.get(
                    "success_rate",
                    0,
                ),
            ),
            waterings_today=int(
                data.get(
                    "waterings_today",
                    0,
                ),
            ),

            watering_seconds_today=int(
                data.get(
                    "watering_seconds_today",
                    0,
                ),
            ),

            statistics_date=str(
                data.get(
                    "statistics_date",
                    "",
                ),
            ),

            average_duration=int(
                data.get(
                    "average_duration",
                    0,
                ),
            ),

            before_moisture=int(
                data.get(
                    "before_moisture",
                    0,
                ),
            ),

            after_moisture=int(
                data.get(
                    "after_moisture",
                    0,
                ),
            ),

            moisture_delta=int(
                data.get(
                    "moisture_delta",
                    0,
                ),
            ),
        )
            
    def get_recent_watering_records(
        self,
        *,
        limit: int = 20,
        sensor_id: str = "",
        zone_id: str = "",
    ) -> list[WateringRecord]:
        """
        Read recent watering history records for one zone and/or sensor.

        AI learning must never combine watering results from different
        garden zones. A wider source window is read first so an active zone
        is not starved when other zones have more recent records.
        """

        if limit <= 0:
            return []

        data = (
            self._device_ref()
            .child("watering_history")
            .order_by_key()
            .limit_to_last(max(limit * 5, 100))
            .get()
        ) or {}

        records: list[WateringRecord] = []

        for firebase_key, item in data.items():

            if not isinstance(item, dict):
                continue

            try:

                record = WateringRecord(
                    started_at=str(
                        item.get(
                            "started_at",
                            "",
                        ),
                    ),

                    finished_at=str(
                        item.get(
                            "finished_at",
                            "",
                        ),
                    ),

                    duration=int(
                        item.get(
                            "duration",
                            0,
                        ),
                    ),

                    moisture_before=int(
                        item.get(
                            "moisture_before",
                            0,
                        ),
                    ),

                    moisture_after=int(
                        item.get(
                            "moisture_after",
                            0,
                        ),
                    ),

                    moisture_delta=int(
                        item.get(
                            "moisture_delta",
                            0,
                        ),
                    ),

                    moisture_limit=int(
                        item.get(
                            "moisture_limit",
                            0,
                        ),
                    ),

                    restart_delta=int(
                        item.get(
                            "restart_delta",
                            0,
                        ),
                    ),

                    cooldown_seconds=int(
                        item.get(
                            "cooldown_seconds",
                            0,
                        ),
                    ),

                    completed=bool(
                        item.get(
                            "completed",
                            False,
                        ),
                    ),

                    stop_reason=str(
                        item.get(
                            "stop_reason",
                            "",
                        ),
                    ),

                    mode=str(
                        item.get(
                            "mode",
                            "",
                        ),
                    ),

                    firmware=str(
                        item.get(
                            "firmware",
                            "",
                        ),
                    ),

                    zone_id=str(
                        item.get(
                            "zone_id",
                            "",
                        ),
                    ),

                    sensor_id=str(
                        item.get(
                            "sensor_id",
                            "",
                        ),
                    ),

                    season_id=str(
                        item.get(
                            "season_id",
                            "",
                        ),
                    ),
                    season_ids=tuple(
                        str(value).strip() for value in item.get("season_ids", [])
                        if str(value).strip()
                    ),
                )

                if (
                    sensor_id
                    and record.sensor_id != sensor_id
                ):
                    continue

                if (
                    zone_id
                    and record.zone_id != zone_id
                ):
                    continue

                if zone_id:
                    _, _, includes_legacy = (
                        self.get_zone_season_scope(zone_id)
                    )
                    active_season_ids = set(
                        self.get_zone_active_season_ids(zone_id)
                    )
                    record_season_ids = set(record.season_ids)
                    if record.season_id:
                        record_season_ids.add(record.season_id)
                    if active_season_ids:
                        if record_season_ids:
                            if active_season_ids.isdisjoint(record_season_ids):
                                continue
                        elif not includes_legacy:
                            continue
                    elif record_season_ids:
                        # A deliberately closed season must not train a new
                        # active model until the user starts another season.
                        continue

                records.append(record)

            except (
                TypeError,
                ValueError,
            ) as exc:

                self._logger.warning(
                    "Invalid watering history record skipped. "
                    "key=%s error=%s",
                    firebase_key,
                    exc,
                )

        records.sort(
            key=lambda record: record.finished_at
        )

        return records[-limit:]
        
    # -------------------------------------------------
    # Commands
    # -------------------------------------------------

    def set_relay_command(
        self,
        relay: bool,
    ) -> None:
        """
        Update the manual relay command.
        """

        self._device_ref().child(
            "commands",
        ).update(
            {
                "relay": relay,
            },
        )

    def _bounded_command_int(
        self,
        *,
        commands: dict,
        field: str,
        default: int,
        minimum: int,
        maximum: int,
    ) -> int:
        """
        Parse and constrain one numeric Firebase command.
        """

        raw_value = commands.get(
            field,
            default,
        )

        try:
            value = int(raw_value)
        except (TypeError, ValueError):
            self._logger.warning(
                "Invalid Firebase command ignored. "
                "field=%s value=%r default=%d",
                field,
                raw_value,
                default,
            )
            return default

        bounded_value = max(
            minimum,
            min(
                maximum,
                value,
            ),
        )

        if bounded_value != value:
            self._logger.warning(
                "Firebase command constrained. "
                "field=%s value=%d bounded=%d range=%d..%d",
                field,
                value,
                bounded_value,
                minimum,
                maximum,
            )

        return bounded_value

    def _boolean_command(
        self,
        *,
        commands: dict,
        field: str,
        default: bool,
    ) -> bool:
        """
        Accept only real boolean Firebase command values.
        """

        value = commands.get(
            field,
            default,
        )

        if isinstance(
            value,
            bool,
        ):
            return value

        self._logger.warning(
            "Invalid boolean Firebase command ignored. "
            "field=%s value=%r default=%s",
            field,
            value,
            default,
        )

        return default

    def get_commands(self) -> CommandState:
        """
        Read commands from Firebase.
        """

        commands = (
            self._device_ref()
            .child("commands")
            .get()
        )

        return self._parse_commands(commands)

    def _parse_commands(
        self,
        commands: object,
    ) -> CommandState:
        """Validate an already received Firebase command payload."""

        if commands is None:

            return CommandState(
                auto_mode=True,
                relay=False,
                enabled=True,
                moisture_limit=IrrigationConfig.DEFAULT_MOISTURE_LIMIT,
                pump_duration=IrrigationConfig.DEFAULT_PUMP_DURATION_SECONDS,
                restart_delta=IrrigationConfig.DEFAULT_RESTART_DELTA,
                cooldown_seconds=IrrigationConfig.DEFAULT_COOLDOWN_SECONDS,

            )

        if not isinstance(
            commands,
            dict,
        ):
            raise ValueError(
                "Firebase commands must be a JSON object.",
            )

        assistant_reset = commands.get(
            "irrigation_assistant_reset",
            {},
        )
        if not isinstance(assistant_reset, dict):
            assistant_reset = {}

        zone_test = commands.get("zone_test", {})
        if not isinstance(zone_test, dict):
            zone_test = {}

        network_configuration = commands.get(
            "network_configuration",
            {},
        )
        if not isinstance(network_configuration, dict):
            network_configuration = {}

        return CommandState(
            auto_mode=self._boolean_command(
                commands=commands,
                field="auto_mode",
                default=True,
            ),
            relay=self._boolean_command(
                commands=commands,
                field="relay",
                default=False,
            ),
            relay_requested_at_ms=int(
                commands.get("relay_requested_at", 0) or 0
            ),
            enabled=self._boolean_command(
                commands=commands,
                field="enabled",
                default=True,
            ),
            moisture_limit=self._bounded_command_int(
                commands=commands,
                field="moisture_limit",
                default=IrrigationConfig.DEFAULT_MOISTURE_LIMIT,
                minimum=IrrigationConfig.MIN_MOISTURE_LIMIT,
                maximum=IrrigationConfig.MAX_MOISTURE_LIMIT,
            ),
            pump_duration=self._bounded_command_int(
                commands=commands,
                field="pump_duration",
                default=IrrigationConfig.DEFAULT_PUMP_DURATION_SECONDS,
                minimum=IrrigationConfig.MIN_PUMP_DURATION_SECONDS,
                maximum=IrrigationConfig.MAX_PUMP_DURATION_SECONDS,
            ),
            restart_delta=self._bounded_command_int(
                commands=commands,
                field="restart_delta",
                default=IrrigationConfig.DEFAULT_RESTART_DELTA,
                minimum=IrrigationConfig.MIN_RESTART_DELTA,
                maximum=IrrigationConfig.MAX_RESTART_DELTA,
            ),
            cooldown_seconds=self._bounded_command_int(
                commands=commands,
                field="cooldown_seconds",
                default=IrrigationConfig.DEFAULT_COOLDOWN_SECONDS,
                minimum=IrrigationConfig.MIN_COOLDOWN_SECONDS,
                maximum=IrrigationConfig.MAX_COOLDOWN_SECONDS,
            ),
            restart_device=self._boolean_command(
                commands=commands,
                field="restart_device",
                default=False,
            ),
            zone_test_requested=self._boolean_command(
                commands=zone_test,
                field="requested",
                default=False,
            ),
            zone_test_request_id=str(
                zone_test.get("request_id", ""),
            ),
            zone_test_zone_id=str(
                zone_test.get("zone_id", ""),
            ),
            zone_test_valve_id=str(
                zone_test.get("valve_id", ""),
            ),
            zone_test_duration=self._bounded_command_int(
                commands=zone_test,
                field="duration",
                default=10,
                minimum=1,
                maximum=IrrigationConfig.MAX_PUMP_DURATION_SECONDS,
            ),
            zone_test_cancel_requested=self._boolean_command(
                commands=zone_test,
                field="cancel_requested",
                default=False,
            ),
            zone_test_requested_at_ms=int(
                zone_test.get("requested_at", 0) or 0
            ),
            irrigation_assistant_reset_requested=(
                self._boolean_command(
                    commands=assistant_reset,
                    field="requested",
                    default=False,
                )
            ),
            irrigation_assistant_reset_request_id=str(
                assistant_reset.get("request_id", ""),
            ),
            irrigation_assistant_reset_zone_id=str(
                assistant_reset.get("zone_id", ""),
            ),
            irrigation_assistant_reset_requested_at_ms=int(
                assistant_reset.get("requested_at", 0) or 0
            ),
            network_configuration_requested=self._boolean_command(
                commands=network_configuration,
                field="requested",
                default=False,
            ),
            network_configuration_request_id=str(
                network_configuration.get("request_id", "")
            ),
            network_configuration_interface=str(
                network_configuration.get("interface", "")
            ),
            network_configuration_mode=str(
                network_configuration.get("mode", "")
            ),
            network_configuration_ip_address=str(
                network_configuration.get("ip_address", "")
            ),
            network_configuration_prefix_length=self._bounded_command_int(
                commands=network_configuration,
                field="prefix_length",
                default=24,
                minimum=1,
                maximum=30,
            ),
            network_configuration_gateway=str(
                network_configuration.get("gateway", "")
            ),
            network_configuration_primary_dns=str(
                network_configuration.get("primary_dns", "")
            ),
            network_configuration_secondary_dns=str(
                network_configuration.get("secondary_dns", "")
            ),
            network_configuration_requested_at_ms=int(
                network_configuration.get("requested_at", 0) or 0
            ),
            network_configuration_expires_at_ms=int(
                network_configuration.get("expires_at", 0) or 0
            ),
            network_configuration_source=str(
                network_configuration.get("source", "")
            ),
        )

    def acknowledge_zone_test(
        self,
        *,
        request_id: str,
        result: str,
        active: bool = False,
        remaining_seconds: int = 0,
    ) -> None:
        """
        Complete a one-shot Android zone test command.
        """

        updates = {
            "requested": False,
            "active": active,
            "remaining_seconds": max(0, remaining_seconds),
            "result": result,
            "completed_request_id": request_id,
            "completed_at": datetime.now().isoformat(),
        }
        if not active:
            updates["cancel_requested"] = False

        self._device_ref().child("commands").child(
            "zone_test",
        ).update(updates)

    def reset_zone_test_after_restart(self) -> None:
        """Clear a stale Android valve-test state after a service restart."""
        self._device_ref().child("commands").child(
            "zone_test",
        ).update(
            {
                "requested": False,
                "cancel_requested": False,
                "active": False,
                "remaining_seconds": 0,
                "result": "SERVICE_RESTARTED",
                "completed_at": datetime.now().isoformat(),
            },
        )


    def acknowledge_irrigation_assistant_reset(
        self,
        *,
        request_id: str,
        zone_id: str,
        result: str,
    ) -> None:
        """Complete a one-shot selected-zone assistant reset command."""

        self._device_ref().child("commands").child(
            "irrigation_assistant_reset",
        ).update(
            {
                "requested": False,
                "result": result,
                "completed_request_id": request_id,
                "completed_zone_id": zone_id,
                "completed_at": datetime.now().isoformat(),
            },
        )

    def get_user_feedback(self) -> dict[str, dict]:
        """Return structured Android feedback records."""

        values = self._device_ref().child("user_feedback").get()
        if not isinstance(values, dict):
            return {}

        return {
            str(feedback_id): dict(feedback)
            for feedback_id, feedback in values.items()
            if isinstance(feedback, dict)
        }

    def get_or_create_feedback_email_activation_epoch(
        self,
        now_epoch: int,
    ) -> int:
        """Persist the first activation time so old test feedback is skipped."""

        activation_ref = self._device_ref().child(
            "user_feedback_email_state/activation_epoch",
        )

        def preserve_or_create(current):
            try:
                activation_epoch = int(current)
            except (TypeError, ValueError):
                activation_epoch = 0

            return activation_epoch if activation_epoch > 0 else now_epoch

        result = activation_ref.transaction(preserve_or_create)
        try:
            return int(result)
        except (TypeError, ValueError):
            return now_epoch

    def claim_user_feedback_email(
        self,
        feedback_id: str,
        *,
        lease_token: str,
        now_epoch: int,
        lease_seconds: int,
    ) -> dict | None:
        """Atomically claim one feedback email for this Pi process."""

        delivery_ref = (
            self._device_ref()
            .child("user_feedback")
            .child(feedback_id)
            .child("email_delivery")
        )

        def claim(current):
            state = dict(current) if isinstance(current, dict) else {}
            status = str(state.get("status", "")).strip().lower()
            if status == "sent":
                return state

            try:
                next_attempt = int(state.get("next_attempt_at_epoch", 0))
            except (TypeError, ValueError):
                next_attempt = 0
            if next_attempt > now_epoch:
                return state

            if status == "sending":
                try:
                    claimed_at = int(state.get("claimed_at_epoch", 0))
                except (TypeError, ValueError):
                    claimed_at = 0
                if now_epoch - claimed_at < lease_seconds:
                    return state

            try:
                attempt_count = int(state.get("attempt_count", 0))
            except (TypeError, ValueError):
                attempt_count = 0

            return {
                "status": "sending",
                "lease_token": lease_token,
                "claimed_at_epoch": now_epoch,
                "attempt_count": max(0, attempt_count) + 1,
            }

        result = delivery_ref.transaction(claim)
        if not isinstance(result, dict):
            return None
        if result.get("status") != "sending":
            return None
        if result.get("lease_token") != lease_token:
            return None
        return dict(result)

    def mark_user_feedback_email_sent(
        self,
        feedback_id: str,
        *,
        lease_token: str,
        message_id: str,
        now_epoch: int,
    ) -> None:
        """Complete a claimed feedback email without overwriting another worker."""

        delivery_ref = (
            self._device_ref()
            .child("user_feedback")
            .child(feedback_id)
            .child("email_delivery")
        )

        def complete(current):
            state = dict(current) if isinstance(current, dict) else {}
            if state.get("lease_token") != lease_token:
                return state
            if state.get("status") != "sending":
                return state

            return {
                "status": "sent",
                "attempt_count": state.get("attempt_count", 1),
                "sent_at_epoch": now_epoch,
                "message_id": message_id,
                "sender": "raspberry_pi",
            }

        delivery_ref.transaction(complete)

    def mark_user_feedback_email_failed(
        self,
        feedback_id: str,
        *,
        lease_token: str,
        error_message: str,
        now_epoch: int,
        next_attempt_at_epoch: int,
    ) -> None:
        """Release a failed claim and schedule a bounded retry."""

        delivery_ref = (
            self._device_ref()
            .child("user_feedback")
            .child(feedback_id)
            .child("email_delivery")
        )

        def fail(current):
            state = dict(current) if isinstance(current, dict) else {}
            if state.get("lease_token") != lease_token:
                return state
            if state.get("status") != "sending":
                return state

            return {
                "status": "failed",
                "attempt_count": state.get("attempt_count", 1),
                "failed_at_epoch": now_epoch,
                "next_attempt_at_epoch": next_attempt_at_epoch,
                "last_error": error_message[:500],
                "sender": "raspberry_pi",
            }

        delivery_ref.transaction(fail)

    @property
    def command_state(self) -> CommandState:
        """
        Return cached commands.
        """

        with self._command_lock:

            return self._command_state

    # -------------------------------------------------
    # Private
    # -------------------------------------------------

    def _device_ref(self) -> db.Reference:
        """
        Return device database reference.
        """

        if not self._initialized:
            raise RuntimeError(
                "Firebase is not initialized.",
            )

        return db.reference(
            f"devices/{AppConfig.DEVICE_ID}",
        )

    def update_seedling_snapshot(
        self,
        node_id: str,
        telemetry: dict,
        recommendation: dict,
    ) -> None:
        """Publish a validated seedling snapshot for the Android client."""
        safe_node_id = str(node_id).strip().lower()
        if not safe_node_id or any(
            character not in "abcdefghijklmnopqrstuvwxyz0123456789-_"
            for character in safe_node_id
        ):
            raise ValueError("Invalid seedling node id.")

        now_epoch = int(time.time())
        latest = dict(telemetry)
        latest["received_at_epoch"] = now_epoch
        latest["online"] = True

        advice = dict(recommendation)
        advice["updated_at_epoch"] = now_epoch

        self._device_ref().child("seedling/nodes").child(
            safe_node_id
        ).update({"latest": latest, "recommendation": advice})

    def update_seedling_online(self, node_id: str, online: bool) -> None:
        """Publish the retained MQTT availability state for a seedling node."""
        safe_node_id = str(node_id).strip().lower()
        if not safe_node_id or any(
            character not in "abcdefghijklmnopqrstuvwxyz0123456789-_"
            for character in safe_node_id
        ):
            raise ValueError("Invalid seedling node id.")
        if not isinstance(online, bool):
            raise ValueError("Seedling online state must be a boolean.")

        self._device_ref().child("seedling/nodes").child(
            safe_node_id
        ).child("latest").update({
            "online": online,
            "status_updated_at_epoch": int(time.time()),
        })

    def get_weather_location(self) -> dict:
        """Return the user-selected garden location, if one is configured."""
        location = self._device_ref().child("weather/location").get()
        return dict(location) if isinstance(location, dict) else {}

    def get_weather_irrigation_settings(self) -> dict:
        """Return user-controlled weather policy values."""
        settings = self._device_ref().child(
            "weather/irrigation_settings"
        ).get()
        return dict(settings) if isinstance(settings, dict) else {}
    def update_weather_forecast(self, forecast: dict) -> None:
        """Publish advisory-only forecast data for Android and the AI assistant."""
        payload = dict(forecast)
        payload["updated_at"] = datetime.now().isoformat()
        payload["updated_at_epoch"] = int(time.time())
        self._device_ref().child("weather/forecast").set(payload)

    def check_restart_command(
            self,
            command: CommandState,
    ) -> None:
        """
        Handle a one-time device restart request.

        The Firebase command is acknowledged before rebooting
        to prevent an endless restart loop.
        """

        if not command.restart_device:
            return

        self._logger.warning(
            "Device restart request detected.",
        )

        # Önce komutu tüketiyoruz.
        # Raspberry yeniden açıldığında tekrar restart etmesin.
        self._device_ref().child(
            "commands",
        ).update(
            {
                "restart_device": False,
            },
        )

        self._logger.warning(
            "Restart command acknowledged in Firebase.",
        )

        self.device_control.restart_device()

    def _handle_command_event(self, event) -> None:
        """Apply one realtime command event without periodic REST reads."""

        if not self._running or self._stop_event.is_set():
            return

        try:
            if (
                getattr(event, "event_type", "") == "put"
                and getattr(event, "path", "") == "/"
            ):
                new_state = self._parse_commands(
                    getattr(event, "data", None),
                )
            else:
                # Partial put/patch events do not contain the complete command
                # object. One scoped read on a real command change preserves
                # correctness without continuous polling.
                new_state = self.get_commands()

            with self._command_lock:
                self._command_state = new_state

            self.check_restart_command(new_state)
        except Exception as exc:
            self._logger.warning(
                "Realtime Firebase command event could not be applied: %s",
                exc,
            )

    def _sync_commands(self) -> None:
        """
        Low-frequency command synchronization used only as a fallback.
        """

        self._logger.info(
            "Fallback Firebase command synchronization started.",
        )

        while (
            self._running
            and not self._stop_event.is_set()
        ):

            try:

                new_state = self.get_commands()

                with self._command_lock:
                    self._command_state = new_state

                self.check_restart_command(
                    new_state
                )

                # Connection recovered
                self._retry_delay = 0.5

            except Exception as exc:

                self._logger.warning(
                    "Firebase unavailable. Retrying in %.1f seconds.",
                    self._retry_delay,
                )

                self._logger.debug(
                    "Firebase error: %s",
                    exc,
                )

                if self._stop_event.wait(
                    self._retry_delay,
                ):
                    break

                self._retry_delay = min(
                    self._retry_delay * 2,
                    self._max_retry_delay,
                )

                continue

            if self._stop_event.wait(
                FirebaseConfig.COMMAND_SYNC_FALLBACK_INTERVAL_SECONDS,
            ):
                break

        self._logger.info(
            "Fallback Firebase command synchronization stopped.",
        )

    def update_network_status(self, status: dict) -> None:
        """Publish the active IPv4 profile separately from general health."""
        payload = dict(status) if isinstance(status, dict) else {}
        payload["updated_at_epoch"] = int(time.time())
        self._device_ref().child("network/status").set(payload)

    def acknowledge_network_configuration_request(self, request_id: str) -> None:
        """Consume a matching request before the active interface is changed."""
        command_ref = self._device_ref().child("commands/network_configuration")

        def consume(current):
            value = dict(current) if isinstance(current, dict) else {}
            if str(value.get("request_id", "")) != str(request_id):
                return value
            value["requested"] = False
            value["acknowledged_at"] = int(time.time() * 1000)
            return value

        command_ref.transaction(consume)

    def update_network_configuration_result(
        self,
        *,
        request_id: str,
        status: str,
        message: str = "",
        applied_ip: str = "",
    ) -> None:
        """Publish bounded progress for the Android device-information screen."""
        self._device_ref().child("network/configuration_result").set(
            {
                "request_id": str(request_id),
                "status": str(status)[:40],
                "message": str(message)[:300],
                "applied_ip": str(applied_ip)[:45],
                "updated_at_epoch": int(time.time()),
            }
        )

    def publish_network_configuration_completion(
        self,
        *,
        request_id: str,
        status: str,
        message: str = "",
        applied_ip: str = "",
        network_status: dict | None = None,
    ) -> None:
        """Publish the final result and active network through a fresh HTTP session."""
        now = int(time.time())
        current_network = (
            dict(network_status)
            if isinstance(network_status, dict)
            else {}
        )
        current_network["updated_at_epoch"] = now
        completion = {
            "configuration_result": {
                "request_id": str(request_id),
                "status": str(status)[:40],
                "message": str(message)[:300],
                "applied_ip": str(applied_ip)[:45],
                "updated_at_epoch": now,
            },
            "status": current_network,
        }

        primary_app = firebase_admin.get_app()
        temporary_app = firebase_admin.initialize_app(
            primary_app.credential,
            {
                "databaseURL": FirebaseConfig.DATABASE_URL,
                "httpTimeout": FirebaseConfig.HTTP_TIMEOUT_SECONDS,
            },
            name=f"avora-network-publish-{uuid.uuid4().hex}",
        )
        try:
            db.reference(
                f"devices/{AppConfig.DEVICE_ID}/network",
                app=temporary_app,
            ).update(completion)
        finally:
            firebase_admin.delete_app(temporary_app)

    def update_irrigation_decision(
        self,
        decision: IrrigationDecision,
    ) -> None:
        """
        Upload the latest smart irrigation decision.
        """

        self._device_ref().child(
            "decision",
        ).set(
            {
                "should_water":
                    decision.should_water,

                "reason":
                    decision.reason,

                "moisture":
                    decision.moisture,

                "moisture_limit":
                    decision.moisture_limit,

                "sensor_stable":
                    decision.sensor_stable,

                "cooldown_active":
                    decision.cooldown_active,

                "trend_classification":
                    decision.trend_classification,

                "trend_sample_count":
                    decision.trend_sample_count,

                "moisture_change_per_minute":
                    decision.moisture_change_per_minute,

                "trend_duration_seconds":
                    round(
                        decision.trend_duration_seconds,
                        2,
                    ),

                "average_moisture":
                    round(
                        decision.average_moisture,
                        2,
                    ),

                "updated_at":
                    datetime.now().isoformat(),
            },
        )

    def update_adaptive_recommendation(
        self,
        recommendation: AdaptiveIrrigationRecommendation,
    ) -> None:
        """
        Upload the latest adaptive irrigation recommendation.

        Observation mode only.
        This method does not modify Firebase commands.
        """

        self._device_ref().child(
            "adaptive_recommendation",
        ).set(
            {
                "recommendation_type":
                    recommendation.recommendation_type,

                "reason":
                    recommendation.reason,

                "should_apply":
                    recommendation.should_apply,

                "confidence":
                    recommendation.confidence,

                "confidence_level":
                    recommendation.confidence_level,

                "current_pump_duration_seconds":
                    recommendation.current_pump_duration_seconds,

                "recommended_pump_duration_seconds":
                    recommendation.recommended_pump_duration_seconds,

                "current_cooldown_seconds":
                    recommendation.current_cooldown_seconds,

                "recommended_cooldown_seconds":
                    recommendation.recommended_cooldown_seconds,

                "watering_count_analyzed":
                    recommendation.watering_count_analyzed,

                "average_moisture_delta":
                    recommendation.average_moisture_delta,

                "average_watering_duration_seconds":
                    recommendation.average_watering_duration_seconds,

                "updated_at":
                    datetime.now().isoformat(),
            },
        ) 

    def update_soil_learning_profile(
        self,
        profile: SoilLearningProfile,
    ) -> None:
        """
        Upload the latest learned soil behaviour profile.

        Observation mode only.
        This method does not modify irrigation commands.
        """

        self._device_ref().child(
            "soil_learning_profile",
        ).set(
            {
                "profile_status":
                    profile.profile_status,

                "soil_classification":
                    profile.soil_classification,

                "confidence":
                    profile.confidence,

                "confidence_level":
                    profile.confidence_level,

                "learning_stage":
                    profile.learning_stage,

                "next_milestone_code":
                    profile.next_milestone_code,

                "next_milestone_text":
                    profile.next_milestone_text,

                "remaining_sensor_samples":
                    profile.remaining_sensor_samples,

                "remaining_auto_waterings":
                    profile.remaining_auto_waterings,

                "sensor_history_count":
                    profile.sensor_history_count,

                "watering_count_analyzed":
                    profile.watering_count_analyzed,

                "average_moisture":
                    round(
                        profile.average_moisture,
                        2,
                    ),

                "average_drying_rate_per_minute":
                    round(
                        profile.average_drying_rate_per_minute,
                        3,
                    ),

                "average_moisture_gain_per_watering":
                    round(
                        profile.average_moisture_gain_per_watering,
                        2,
                    ),

                "average_watering_duration_seconds":
                    round(
                        profile.average_watering_duration_seconds,
                        2,
                    ),

                "estimated_water_retention_minutes":
                    round(
                        profile.estimated_water_retention_minutes,
                        2,
                    ),

                "irrigation_efficiency":
                    round(
                        profile.irrigation_efficiency,
                        3,
                    ),

                "learned_at":
                    profile.learned_at,

                "updated_at":
                    datetime.now().isoformat(),
            },
        )

    def update_ai_decision(
        self,
        summary: AIDecisionSummary,
        *,
        analysis_sensor_id: str = "",
        analysis_zone_id: str = "",
    ) -> None:
        """
        Upload the latest unified AI decision summary.

        Observation mode only.
        This method does not modify irrigation commands.
        """

        self._device_ref().child(
            "ai_decision",
        ).set(
            {
                "decision_code":
                    summary.decision_code,

                "decision_title":
                    summary.decision_title,

                "decision_message":
                    summary.decision_message,

                "severity":
                    summary.severity,

                "confidence":
                    round(
                        summary.confidence,
                        2,
                    ),

                "confidence_level":
                    summary.confidence_level,

                "should_water":
                    summary.should_water,

                "recommendation_type":
                    summary.recommendation_type,

                "soil_classification":
                    summary.soil_classification,

                "trend_classification":
                    summary.trend_classification,

                "primary_reason":
                    summary.primary_reason,

                "secondary_reason":
                    summary.secondary_reason,

                "analysis_sensor_id": analysis_sensor_id,

                "analysis_zone_id": analysis_zone_id,

                "generated_at":
                    summary.generated_at,

                "updated_at":
                    datetime.now().isoformat(),
            },
        )
    def update_ai_explanation(
        self,
        explanation: AIExplanation,
    ) -> None:
        """
        Upload the latest user-friendly AI explanation.

        Observation mode only.
        This method does not modify irrigation commands.
        """

        self._device_ref().child(
            "ai_explanation",
        ).set(
            {
                "explanation_code":
                    explanation.explanation_code,

                "title":
                    explanation.title,

                "summary":
                    explanation.summary,

                "reason_lines":
                    list(
                        explanation.reason_lines
                    ),

                "next_step":
                    explanation.next_step,

                "progress_percent":
                    max(
                        0,
                        min(
                            explanation.progress_percent,
                            100,
                        ),
                    ),

                "severity":
                    explanation.severity,

                "generated_at":
                    explanation.generated_at,

                "decision_flow": {
                    "sensor": explanation.decision_flow.sensor,
                    "sensor_status": (
                        explanation.decision_flow.sensor_status
                    ),

                    "moisture": explanation.decision_flow.moisture,
                    "moisture_status": (
                        explanation.decision_flow.moisture_status
                    ),

                    "soil": explanation.decision_flow.soil,
                    "soil_status": (
                        explanation.decision_flow.soil_status
                    ),

                    "history": explanation.decision_flow.history,
                    "history_status": (
                        explanation.decision_flow.history_status
                    ),

                    "result": explanation.decision_flow.result,
                    "result_status": (
                        explanation.decision_flow.result_status
                    ),
                },

                "updated_at":
                    datetime.now().isoformat(),
            },
        )

    def update_prediction_validation_status(
        self,
        status: PredictionValidationStatus,
    ) -> None:
        """
        Upload prediction-validation queue status.

        Observation mode only.
        """

        self._device_ref().child(
            "ai",
        ).child(
            "prediction_validation",
        ).set(
            {
                "validation_status":
                    status.validation_status,

                "pending_count":
                    status.pending_count,

                "target_minutes":
                    status.target_minutes,

                "next_validation_at":
                    status.next_validation_at,

                "remaining_seconds":
                    status.remaining_seconds,

                "updated_at":
                    status.updated_at,
            }
        )

        self._logger.debug(
            "Prediction validation status updated. "
            "status=%s pending=%d remaining=%d",
            status.validation_status,
            status.pending_count,
            status.remaining_seconds,
        )

    def update_moisture_prediction(
        self,
        prediction: MoisturePrediction,
    ) -> None:
        """
        Upload the latest moisture prediction.

        Observation mode only.
        """

        self._device_ref().child(
            "moisture_prediction",
        ).set(
            {
                "prediction_status":
                    prediction.prediction_status,

                "prediction_method":
                    prediction.prediction_method,

                "current_moisture":
                    round(
                        prediction.current_moisture,
                        2,
                    ),

                "moisture_limit":
                    round(
                        prediction.moisture_limit,
                        2,
                    ),

                "drying_rate_per_minute":
                    round(
                        prediction.drying_rate_per_minute,
                        3,
                    ),

                "predicted_moisture_1_hour":
                    round(
                        prediction.predicted_moisture_1_hour,
                        2,
                    ),

                "predicted_moisture_3_hours":
                    round(
                        prediction.predicted_moisture_3_hours,
                        2,
                    ),

                "predicted_moisture_6_hours":
                    round(
                        prediction.predicted_moisture_6_hours,
                        2,
                    ),

                "estimated_minutes_until_limit":
                    round(
                        prediction.estimated_minutes_until_limit,
                        2,
                    ),

                "estimated_limit_reached_at":
                    prediction.estimated_limit_reached_at,

                "confidence":
                    round(
                        prediction.confidence,
                        2,
                    ),

                "confidence_level":
                    prediction.confidence_level,

                "generated_at":
                    prediction.generated_at,

                "updated_at":
                    datetime.now().isoformat(),
            },
        )

    def update_prediction_accuracy(
        self,
        accuracy: PredictionAccuracy,
    ) -> None:
        """
        Upload prediction accuracy statistics.

        Observation mode only.
        """

        self._device_ref().child(
            "prediction_accuracy",
        ).set(
            {
                "prediction_count":
                    accuracy.prediction_count,

                "successful_predictions":
                    accuracy.successful_predictions,

                "average_error":
                    round(
                        accuracy.average_error,
                        2,
                    ),

                "maximum_error":
                    round(
                        accuracy.maximum_error,
                        2,
                    ),

                "minimum_error":
                    round(
                        accuracy.minimum_error,
                        2,
                    ),

                "accuracy_percent":
                    round(
                        accuracy.accuracy_percent,
                        2,
                    ),

                "confidence_multiplier":
                    round(
                        accuracy.confidence_multiplier,
                        3,
                    ),

                "status":
                    accuracy.status,

                "generated_at":
                    accuracy.generated_at,

                "updated_at":
                    datetime.now().isoformat(),
            },
        )

    def update_unified_confidence(
        self,
        confidence: UnifiedConfidence,
    ) -> None:
        """
        Upload unified AI confidence.

        Observation mode only.
        """

        self._device_ref().child(
            "unified_confidence",
        ).set(
            {
                "overall_confidence":
                    round(
                        confidence.overall_confidence,
                        2,
                    ),

                "confidence_level":
                    confidence.confidence_level,

                "soil_learning_confidence":
                    round(
                        confidence.soil_learning_confidence,
                        2,
                    ),

                "prediction_accuracy":
                    round(
                        confidence.prediction_accuracy,
                        2,
                    ),

                "connection_confidence":
                    round(
                        confidence.connection_confidence,
                        2,
                    ),

                "measurement_confidence":
                    round(
                        confidence.measurement_confidence,
                        2,
                    ),

                "decision_confidence":
                    round(
                        confidence.decision_confidence,
                        2,
                    ),

                "sensor_confidence":
                    round(
                        confidence.sensor_confidence,
                        2,
                    ),

                "trend_confidence":
                    round(
                        confidence.trend_confidence,
                        2,
                    ),

                "weighted_score":
                    round(
                        confidence.weighted_score,
                        3,
                    ),

                "status":
                    confidence.status,

                "generated_at":
                    confidence.generated_at,

                "updated_at":
                    datetime.now().isoformat(),
            },
        )

    def save_prediction_history(
        self,
        history: list[
            tuple[
                MoisturePrediction,
                float,
            ]
        ],
    ) -> None:
        """
        Save prediction history to Firebase.

        Each history item contains:

            (
                prediction,
                actual_moisture,
            )

        Observation mode only.
        """

        history_data: dict[str, dict] = {}

        for index, (
            prediction,
            actual_moisture,
        ) in enumerate(
            history,
            start=1,
        ):
            item_key = f"item_{index:04d}"

            history_data[item_key] = {
                "prediction_status":
                    prediction.prediction_status,

                "prediction_method":
                    prediction.prediction_method,

                "current_moisture":
                    prediction.current_moisture,

                "moisture_limit":
                    prediction.moisture_limit,

                "drying_rate_per_minute":
                    prediction.drying_rate_per_minute,

                "predicted_moisture_1_hour":
                    prediction.predicted_moisture_1_hour,

                "predicted_moisture_3_hours":
                    prediction.predicted_moisture_3_hours,

                "predicted_moisture_6_hours":
                    prediction.predicted_moisture_6_hours,

                "estimated_minutes_until_limit":
                    prediction.estimated_minutes_until_limit,

                "estimated_limit_reached_at":
                    prediction.estimated_limit_reached_at,

                "confidence":
                    prediction.confidence,

                "confidence_level":
                    prediction.confidence_level,

                "generated_at":
                    prediction.generated_at,

                "actual_moisture":
                    actual_moisture,
            }

        history_ref = (
            self._device_ref()
            .child("ai")
            .child("prediction_history")
        )

        if not history_data:
            history_ref.delete()

            self._logger.debug(
                "Prediction history cleared.",
            )

            return

        history_ref.set(history_data)

        self._logger.debug(
            "Prediction history saved. count=%d",
            len(history),
        )

    def load_prediction_history(
        self,
    ) -> list[
        tuple[
            MoisturePrediction,
            float,
        ]
    ]:
        """
        Load prediction history from Firebase.

        Invalid or incomplete history items are skipped.

        Observation mode only.
        """

        history_ref = (
            self._device_ref()
            .child("ai")
            .child("prediction_history")
        )

        history_data = history_ref.get()

        if not isinstance(
            history_data,
            dict,
        ):
            self._logger.info(
                "No prediction history found.",
            )

            return []

        loaded_history: list[
            tuple[
                MoisturePrediction,
                float,
            ]
        ] = []

        for item_key in sorted(
            history_data.keys(),
        ):
            item = history_data[item_key]

            if not isinstance(
                item,
                dict,
            ):
                self._logger.warning(
                    "Invalid prediction history item skipped. "
                    "key=%s",
                    item_key,
                )

                continue

            try:
                prediction = MoisturePrediction(
                    prediction_status=str(
                        item["prediction_status"],
                    ),

                    prediction_method=str(
                        item["prediction_method"],
                    ),

                    current_moisture=float(
                        item["current_moisture"],
                    ),

                    moisture_limit=float(
                        item["moisture_limit"],
                    ),

                    drying_rate_per_minute=float(
                        item[
                            "drying_rate_per_minute"
                        ],
                    ),

                    predicted_moisture_1_hour=float(
                        item[
                            "predicted_moisture_1_hour"
                        ],
                    ),

                    predicted_moisture_3_hours=float(
                        item[
                            "predicted_moisture_3_hours"
                        ],
                    ),

                    predicted_moisture_6_hours=float(
                        item[
                            "predicted_moisture_6_hours"
                        ],
                    ),

                    estimated_minutes_until_limit=float(
                        item[
                            "estimated_minutes_until_limit"
                        ],
                    ),

                    estimated_limit_reached_at=str(
                        item[
                            "estimated_limit_reached_at"
                        ],
                    ),

                    confidence=float(
                        item["confidence"],
                    ),

                    confidence_level=str(
                        item["confidence_level"],
                    ),

                    generated_at=str(
                        item["generated_at"],
                    ),
                )

                actual_moisture = float(
                    item["actual_moisture"],
                )

            except (
                KeyError,
                TypeError,
                ValueError,
            ) as error:
                self._logger.warning(
                    "Prediction history item could not "
                    "be loaded. key=%s error=%s",
                    item_key,
                    error,
                )

                continue

            loaded_history.append(
                (
                    prediction,
                    actual_moisture,
                )
            )

        self._logger.info(
            "Prediction history loaded. count=%d",
            len(loaded_history),
        )

        return loaded_history
