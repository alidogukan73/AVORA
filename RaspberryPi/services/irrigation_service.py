"""
Irrigation service.

Coordinates sensor, controller and Firebase.
"""

from __future__ import annotations

import time
from dataclasses import asdict, is_dataclass, replace
from datetime import datetime


from core.config import (
    AppConfig,
    FirebaseConfig,
    IrrigationConfig,
    SensorConfig,
    SeedlingConfig,
)
from core.firebase_service import FirebaseService
from core.logger import AppLogger
from core.system_monitor import SystemMonitor
from core.network_configuration import (
    NetworkConfigurationRequest,
    NetworkConfigurationService,
)
from services.feedback_email_service import FeedbackEmailService
from services.weather_service import WeatherService

from controllers.smart_irrigation_engine import SmartIrrigationEngine
from controllers.adaptive_irrigation_engine import AdaptiveIrrigationEngine
from controllers.runtime_watering_duration_policy import (
    RuntimeWateringDurationPolicy,
)
from controllers.multi_zone_decision_engine import (
    MultiZoneDecisionEngine,
    ZoneDecisionResult,
)
from controllers.zone_irrigation_scheduler import (
    ZoneIrrigationScheduler,
)
from controllers.weather_irrigation_policy import (
    WeatherIrrigationPolicy,
)
from controllers.optimal_irrigation_time_engine import (
    IrrigationTimePlan,
    OptimalIrrigationTimeEngine,
)
from controllers.ai_pipeline import AIPipeline
from controllers.prediction_validation_queue import PredictionValidationQueue
from controllers.shared_pump_zone_executor import (
    SharedPumpZoneExecutor,
)

from hardware.relay import RelayController
from hardware.valve_controller import ValveController
from hardware.sensor_provider import SoilMoistureSensorProvider
from hardware.seedling_mqtt_bridge import SeedlingMqttBridge

from models.sensor_history_entry import SensorHistoryEntry
from models.moisture_history import MoistureSample
from models.watering_record import WateringRecord
from models.pending_watering_measurement import PendingWateringMeasurement
from models.moisture_prediction import MoisturePrediction


class IrrigationService:
    """
    Smart irrigation service.
    """

    def __init__(self) -> None:

        self._logger = AppLogger().logger

        self._sensor = SoilMoistureSensorProvider(
            mode=SensorConfig.SENSOR_MODE,
            mqtt_broker=SensorConfig.MQTT_BROKER,
            mqtt_port=SensorConfig.MQTT_PORT,
            mqtt_topic=SensorConfig.MQTT_TOPIC,
            mqtt_ads_status_topic=SensorConfig.MQTT_ADS_STATUS_TOPIC,
            mqtt_sensor_id=SensorConfig.MQTT_SENSOR_ID,
            mqtt_stale_after_seconds=(
                SensorConfig.MQTT_STALE_AFTER_SECONDS
            ),
            mqtt_startup_timeout_seconds=(
                SensorConfig.MQTT_STARTUP_TIMEOUT_SECONDS
            ),
        )

        self._system_monitor = SystemMonitor()
        self._relay = RelayController()
        self._valves = ValveController()
        self._firebase = FirebaseService()
        self._seedling_bridge = SeedlingMqttBridge(
            sink=self._firebase,
            broker=SeedlingConfig.MQTT_BROKER,
            port=SeedlingConfig.MQTT_PORT,
            topic=SeedlingConfig.MQTT_TOPIC,
            client_id=SeedlingConfig.MQTT_CLIENT_ID,
        )
        self._network_configuration = NetworkConfigurationService()
        self._feedback_email = FeedbackEmailService(
            self._firebase,
        )
        self._weather = WeatherService()
        self._last_weather_update = 0.0
        self._weather_update_interval_seconds = 60 * 60
        self._latest_weather_forecast = None
        self._weather_location_signature = ""
        self._weather_policy = WeatherIrrigationPolicy()
        self._last_weather_policy_settings_update = 0.0
        self._weather_policy_settings_interval_seconds = 30
        self._weather_policy_settings_signature = None
        self._weather_adjustments_by_zone = {}
        self._irrigation_time_engine = OptimalIrrigationTimeEngine()
        self._irrigation_time_plans_by_zone = {}
        self._adaptive_engine = AdaptiveIrrigationEngine()
        self._duration_policy = RuntimeWateringDurationPolicy()
        self._adaptive_recommendation_cache = {}
        self._adaptive_recommendation_cache_seconds = 300.0
        self._adaptive_recommendations_by_zone = {}
        self._watering_duration_plans_by_zone = {}


        self._zone_executor = SharedPumpZoneExecutor(
            self._relay,
            self._valves,
        )

        self._smart_engine = SmartIrrigationEngine()
        self._multi_zone_engine = MultiZoneDecisionEngine()
        self._zone_scheduler = ZoneIrrigationScheduler()
        self._last_multi_zone_status_signature = None
        self._last_multi_zone_log_signature = None
        self._last_ads1115_status_signature = None
        self._last_ads1115_status_publish_monotonic = 0.0

        self._ai_pipeline = AIPipeline()
        self._prediction_validation_queue = PredictionValidationQueue()

        self._zone_ai_pipelines: dict[str, AIPipeline] = {}
        self._zone_prediction_validation_queues: dict[str, PredictionValidationQueue] = {}
        self._zone_prediction_histories: dict[str, list] = {}
        self._zone_ai_season_ids: dict[str, str] = {}
        self._last_zone_ai_update = 0.0

        self._prediction_history = []
        self._prediction_history_limit = 100

        self._last_moisture_prediction = None
        self._last_prediction_accuracy = None
        self._last_unified_confidence = None
        self._last_ai_decision = None
        self._last_ai_explanation = None
        self._last_soil_learning_profile = None
        self._last_adaptive_recommendation = None

        self._last_ai_decision_update = 0.0
        self._ai_decision_interval_seconds = 30             #30 sn olacak   

        self._last_prediction_validation_status_update = 0.0
        self._prediction_validation_status_interval_seconds = 30

        self._last_irrigation_decision = None

        self._last_status_update = 0.0
        self._last_health_update = 0.0

        self._last_sensor_history_update = 0.0
        self._sensor_history_interval_seconds = 300

        self._started_at = 0.0
        self._last_watering_iso = ""

        self._update_error_active = False
        self._last_update_error_log = 0.0
        self._update_error_log_interval_seconds = 30.0
        self._update_recovery_started_at = 0.0
        self._update_recovery_success_count = 0
        self._update_recovery_confirmation_seconds = 120.0
        self._update_recovery_required_successes = 3

        self._manual_relay_started_at = 0.0
        self._manual_relay_timeout_latched = False
        self._last_zone_test_request_id = ""
        self._active_zone_test_request_id = ""
        self._active_zone_test_valve_id = ""
        self._active_zone_test_mode = ""
        self._active_zone_test_deadline = 0.0
        self._last_irrigation_assistant_reset_request_id = ""
        self._last_network_configuration_request_id = ""
        self._last_zone_config_signatures = {}
        self._pending_watering_measurements: list[
            PendingWateringMeasurement
        ] = []

    def initialize(self) -> None:
        """
        Initialize all services.
        """

        self._sensor.initialize()

        self._relay.initialize()
        self._valves.initialize()

        self._firebase.initialize()
        try:
            self._seedling_bridge.start()
        except Exception as exc:
            # Seedling monitoring is advisory and must never stop irrigation.
            self._logger.warning(
                "Seedling assistant could not start: %s",
                exc,
            )
        self._feedback_email.start()
        # A service restart closes every relay/valve. Clear any stale
        # Firebase status as well, otherwise Android can keep a manual valve
        # switch visually locked after the hardware is already safe.
        self._firebase.update_active_zone_valve(
            None,
            False,
        )
        self._firebase.reset_all_zone_watering_states()
        self._firebase.reset_zone_test_after_restart()

        self._restore_zone_cooldowns()
        self._restore_zone_irrigation_safety_states()
        self._restore_pending_watering_measurements()

        self._restore_prediction_history()
        self._restore_ai_sensor_history()

        # Preserve a continuing incident across service restarts. A healthy
        # service cycle will clear it only after the normal stability window.
        self._update_error_active = self._firebase.has_active_error()
        self._firebase.update_status()

        self._update_prediction_validation_status(
            force=True,
        )

        health = self._system_monitor.read()

        self._firebase.update_health_status(
            health,
        )        
        self._publish_network_status()

        self._last_status_update = time.monotonic()
        self._last_health_update = time.monotonic()

        self._last_sensor_history_update = time.monotonic()

        self._last_ai_decision_update = 0.0

        self._started_at = time.monotonic()

        self._logger.info(
            "Irrigation service initialized.",
        )

    def _restore_prediction_history(
        self,
    ) -> None:
        """
        Restore prediction history from Firebase.

        A restore failure must not prevent the irrigation
        service from starting.
        """

        try:
            loaded_history = (
                self._firebase.load_prediction_history()
            )

            if not isinstance(
                loaded_history,
                list,
            ):
                self._logger.warning(
                    "Prediction history restore returned "
                    "an invalid value."
                )

                self._prediction_history = []

                return

            self._prediction_history = loaded_history[
                -self._prediction_history_limit:
            ]

            self._logger.info(
                "Prediction history restored. count=%d",
                len(self._prediction_history),
            )

        except Exception as exc:
            self._prediction_history = []

            self._logger.exception(
                "Prediction history could not be restored: %s",
                exc,
            )

    def _restore_ai_sensor_history(
        self,
    ) -> None:
        """
        Restore only the primary observation history for the AI dashboard.

        Automatic multi-zone irrigation deliberately starts with fresh
        readings, so restored data can never cause a pump action.
        """

        try:
            stored = self._firebase.load_recent_sensor_history(
                limit=20,
                sensor_id=SensorConfig.MQTT_SENSOR_ID,
            )
            now_wall = datetime.now().timestamp()
            now_monotonic = time.monotonic()
            samples: list[MoistureSample] = []

            for moisture, recorded_at in stored:
                recorded_wall = datetime.fromisoformat(
                    recorded_at
                ).timestamp()
                age_seconds = max(0.0, now_wall - recorded_wall)
                samples.append(
                    MoistureSample(
                        moisture=moisture,
                        timestamp=now_monotonic - age_seconds,
                    )
                )

            restored = self._smart_engine.restore_observation_history(
                samples,
            )
            self._logger.info(
                "AI observation history restored. count=%d",
                restored,
            )

        except Exception as exc:
            self._logger.warning(
                "AI observation history could not be restored: %s",
                exc,
            )

    def _update_status_if_needed(self) -> None:
        """
        Update online status periodically.
        """

        current_time = time.monotonic()

        if (
            current_time - self._last_status_update
            >= FirebaseConfig.STATUS_UPDATE_INTERVAL_SECONDS
        ):

            self._firebase.update_status()

            self._last_status_update = current_time

    def _update_health_if_needed(
        self,
    ) -> None:
        """
        Update Raspberry Pi health information periodically.
        """

        current_time = time.monotonic()

        if (
            current_time - self._last_health_update
            >= FirebaseConfig.HEALTH_UPDATE_INTERVAL_SECONDS
        ):

            health = self._system_monitor.read()

            self._firebase.update_health_status(
                health,
            )
            self._publish_network_status()

            self._last_health_update = current_time





    def _update_ai_pipeline_if_needed(
        self,
        *,
        reading,
        commands,
        zone_id,
    ) -> None:
        """
        Execute the complete observation-mode AI pipeline
        periodically and upload its outputs to Firebase.
        """

        current_time = time.monotonic()

        if (
            current_time
            - self._last_ai_decision_update
            < self._ai_decision_interval_seconds
        ):
            return

        if self._last_irrigation_decision is None:
            return

        trend = self._smart_engine.get_current_trend()

        watering_records = (
            self._firebase.get_recent_watering_records(
                limit=30,
                sensor_id=reading.sensor_id,
                zone_id=zone_id,
            )
        )

        (
            soil_profile,
            adaptive,
            prediction,
            prediction_accuracy,
            unified_confidence,
            ai_decision,
            explanation,
        ) = self._ai_pipeline.analyze(
            irrigation_decision=(
                self._last_irrigation_decision
            ),
            trend=trend,
            reading=reading,
            watering_records=watering_records,
            prediction_history=self._prediction_history,
            current_pump_duration_seconds=(
                commands.pump_duration
            ),
            current_cooldown_seconds=(
                commands.cooldown_seconds
            ),
        )

        explanation = self._apply_weather_advice(explanation)

        self._firebase.update_moisture_prediction(
            prediction,
        )

        self._firebase.update_prediction_accuracy(
            prediction_accuracy,
        )

        self._firebase.update_unified_confidence(
            unified_confidence,
        )

        # -------------------------------------------------
        # Keep latest AI outputs in memory
        # -------------------------------------------------

        self._last_soil_learning_profile = (
            soil_profile
        )

        self._last_adaptive_recommendation = (
            adaptive
        )

        self._last_moisture_prediction = (
            prediction
        )

        self._last_prediction_accuracy = (
            prediction_accuracy
        )

        self._last_unified_confidence = (
            unified_confidence
        )

        self._last_ai_decision = (
            ai_decision
        )

        self._last_ai_explanation = (
            explanation
        )

        if prediction.prediction_status == "READY":

            prediction_queued = (
                self._prediction_validation_queue.enqueue(
                    prediction=prediction,
                )
            )

            if prediction_queued:
                self._logger.info(
                    "Moisture prediction queued for "
                    "one-hour validation. pending=%d",
                    self._prediction_validation_queue.count,
                )
                self._update_prediction_validation_status(
                    force=True,
                )

            else:
                self._logger.debug(
                    "Moisture prediction was not queued "
                    "because a validation is already pending.",
                )

        # -------------------------------------------------
        # Upload currently supported outputs
        # -------------------------------------------------

        self._firebase.update_soil_learning_profile(
            soil_profile,
        )

        self._firebase.update_adaptive_recommendation(
            adaptive,
        )

        self._firebase.update_ai_decision(
            ai_decision,
            analysis_sensor_id=reading.sensor_id,
            analysis_zone_id=zone_id,
        )

        self._firebase.update_ai_explanation(
            explanation,
        )

        self._last_ai_decision_update = current_time

        # -------------------------------------------------
        # Logs
        # -------------------------------------------------

        self._logger.info(
            "AI pipeline updated. "
            "decision=%s severity=%s "
            "should_water=%s confidence=%s "
            "prediction_status=%s",
            ai_decision.decision_code,
            ai_decision.severity,
            ai_decision.should_water,
            unified_confidence.overall_confidence,
            prediction.prediction_status,
        )

        self._logger.info(
            "AI explanation updated. "
            "code=%s progress=%d severity=%s "
            "prediction_accuracy=%.1f count=%d",
            explanation.explanation_code,
            explanation.progress_percent,
            explanation.severity,
            prediction_accuracy.accuracy_percent,
            prediction_accuracy.prediction_count,
        )

    @classmethod
    def _firebase_ai_value(cls, value):
        """Convert nested AI dataclasses to Firebase-safe values."""

        if is_dataclass(value):
            value = asdict(value)
        if isinstance(value, dict):
            return {
                str(key): cls._firebase_ai_value(item)
                for key, item in value.items()
            }
        if isinstance(value, (list, tuple)):
            return [
                cls._firebase_ai_value(item)
                for item in value
            ]
        return value

    @staticmethod
    def _zone_has_operational_season(zone: dict) -> bool:
        """Accept modern zones only while their season is active.

        Zones without season metadata are legacy installations and retain
        their existing behaviour until the one-time migration completes.
        """

        if not isinstance(zone, dict):
            return False
        season = zone.get("season")
        if not isinstance(season, dict):
            return True
        status = str(season.get("status", "")).strip().upper()
        season_id = str(season.get("active_season_id", "")).strip()
        if not status:
            return True
        return status == "ACTIVE" and bool(season_id)

    @staticmethod
    def _active_zone_season_id(zone: dict) -> str:
        """Return the active season id without accepting a closed season."""

        season = zone.get("season") if isinstance(zone, dict) else None
        if not isinstance(season, dict):
            return ""
        status = str(season.get("status", "")).strip().upper()
        season_id = str(season.get("active_season_id", "")).strip()
        if status and status != "ACTIVE":
            return ""
        return season_id

    @staticmethod
    def _active_zone_season_scope_key(zone: dict) -> str:
        """Return one deterministic key for all crops sharing a zone."""

        if not isinstance(zone, dict):
            return ""
        season = zone.get("season")
        if not isinstance(season, dict):
            return ""
        status = str(season.get("status", "")).strip().upper()
        if status and status != "ACTIVE":
            return ""

        season_ids: set[str] = set()
        primary = str(season.get("active_season_id", "")).strip()
        if primary:
            season_ids.add(primary)

        active = season.get("active_season_ids")
        if isinstance(active, dict):
            for key, enabled in active.items():
                value = str(key or "").strip()
                if enabled and value:
                    season_ids.add(value)
        elif isinstance(active, (list, tuple)):
            for item in active:
                value = str(item or "").strip()
                if value:
                    season_ids.add(value)

        return "|".join(sorted(season_ids))

    def _synchronize_zone_ai_season_scopes(self, configs: dict) -> None:
        """Reset stale AI state before the current decision plans are built."""

        for sensor_id, zone in configs.items():
            if not isinstance(zone, dict):
                continue
            zone_id = str(zone.get("zone_id", "")).strip()
            if not zone_id:
                continue
            active_scope = self._active_zone_season_scope_key(zone)
            known_scope = self._zone_ai_season_ids.get(zone_id)
            if known_scope is None:
                self._zone_ai_season_ids[zone_id] = active_scope
                continue
            if known_scope == active_scope:
                continue
            self._reset_transient_zone_ai_for_season(
                zone_id=zone_id,
                sensor_id=str(sensor_id),
                previous_season_id=known_scope,
                active_season_id=active_scope,
            )
            self._zone_ai_season_ids[zone_id] = active_scope

    def _reset_transient_zone_ai_for_season(
        self,
        *,
        zone_id: str,
        sensor_id: str,
        previous_season_id: str,
        active_season_id: str,
    ) -> None:
        """Start a clean decision window while keeping learned physical data."""

        self._zone_ai_pipelines.pop(zone_id, None)
        self._zone_prediction_validation_queues.pop(zone_id, None)
        self._zone_prediction_histories.pop(zone_id, None)
        self._adaptive_recommendations_by_zone.pop(zone_id, None)
        self._watering_duration_plans_by_zone.pop(zone_id, None)
        self._adaptive_recommendation_cache.pop(zone_id, None)
        self._multi_zone_engine.reset(sensor_id)
        self._persist_zone_irrigation_safety_state(
            zone_id=zone_id,
            sensor_id=sensor_id,
        )
        self._last_zone_ai_update = 0.0
        self._last_multi_zone_status_signature = None
        self._last_multi_zone_log_signature = None
        self._logger.info(
            "Zone season changed; transient irrigation AI reset. "
            "zone_id=%s sensor_id=%s previous_season_id=%s active_season_id=%s",
            zone_id,
            sensor_id,
            previous_season_id or "LEGACY",
            active_season_id or "LEGACY",
        )

    def _update_multi_zone_ai_if_needed(
        self,
        *,
        results: list[ZoneDecisionResult],
        configs: dict,
        readings: dict,
        global_commands,
    ) -> None:
        """Analyze and publish an independent AI state for every zone."""

        current_time = time.monotonic()
        if (
            current_time - self._last_zone_ai_update
            < self._ai_decision_interval_seconds
        ):
            return

        zone_states: dict[str, dict] = {}
        confidence_total = 0.0
        ready_predictions = 0
        watering_recommended = 0
        warnings = 0

        for result in results:
            candidate = result.candidate
            zone_id = candidate.zone_id
            sensor_id = candidate.sensor_id
            reading = readings.get(sensor_id)
            zone = configs.get(sensor_id)

            if reading is None or not isinstance(zone, dict):
                continue

            commands, _, _, _ = self._effective_zone_commands(
                global_commands,
                zone,
            )
            pipeline = self._zone_ai_pipelines.setdefault(
                zone_id,
                AIPipeline(),
            )
            queue = self._zone_prediction_validation_queues.setdefault(
                zone_id,
                PredictionValidationQueue(),
            )
            history = self._zone_prediction_histories.setdefault(
                zone_id,
                [],
            )

            validated = queue.validate_due(
                actual_moisture=reading.moisture,
            )
            if validated:
                history.extend(validated)
                if len(history) > self._prediction_history_limit:
                    del history[:-self._prediction_history_limit]

            watering_records = (
                self._firebase.get_recent_watering_records(
                    limit=30,
                    sensor_id=sensor_id,
                    zone_id=zone_id,
                )
            )
            (
                soil_profile,
                adaptive,
                prediction,
                prediction_accuracy,
                unified_confidence,
                ai_decision,
                explanation,
            ) = pipeline.analyze(
                irrigation_decision=result.decision,
                trend=self._multi_zone_engine.get_current_trend(
                    sensor_id,
                ),
                reading=reading,
                watering_records=watering_records,
                prediction_history=history,
                current_pump_duration_seconds=commands.pump_duration,
                current_cooldown_seconds=commands.cooldown_seconds,
            )
            explanation = self._apply_weather_advice(explanation)

            if prediction.prediction_status == "READY":
                queue.enqueue(prediction=prediction)
                ready_predictions += 1
            if ai_decision.should_water:
                watering_recommended += 1
            if ai_decision.severity.upper() in {"WARNING", "CRITICAL"}:
                warnings += 1

            confidence_total += unified_confidence.overall_confidence
            zone_states[zone_id] = {
                "zone_id": zone_id,
                "sensor_id": sensor_id,
                "decision": self._firebase_ai_value(ai_decision),
                "explanation": self._firebase_ai_value(explanation),
                "moisture_prediction": self._firebase_ai_value(
                    prediction,
                ),
                "prediction_accuracy": self._firebase_ai_value(
                    prediction_accuracy,
                ),
                "confidence": self._firebase_ai_value(
                    unified_confidence,
                ),
                "learning_profile": self._firebase_ai_value(
                    soil_profile,
                ),
                "adaptive_recommendation": self._firebase_ai_value(
                    adaptive,
                ),
                "prediction_validation": self._firebase_ai_value(
                    queue.get_status(),
                ),
            }

        analyzed_zones = len(zone_states)
        configured_zone_ids = {
            str(zone.get("zone_id", "")).strip()
            for zone in configs.values()
            if isinstance(zone, dict)
            and str(zone.get("zone_id", "")).strip()
        }
        average_confidence = (
            round(confidence_total / analyzed_zones, 2)
            if analyzed_zones
            else 0.0
        )
        if analyzed_zones == 0:
            status = "WAITING_FOR_SENSOR"
        elif analyzed_zones < len(configured_zone_ids):
            status = "PARTIAL"
        else:
            status = "READY"

        garden_summary = {
            "total_zones": len(configured_zone_ids),
            "analyzed_zones": analyzed_zones,
            "ready_predictions": ready_predictions,
            "watering_recommended": watering_recommended,
            "warnings": warnings,
            "average_confidence": average_confidence,
            "confidence_level": (
                "HIGH"
                if average_confidence >= 0.75
                else "MEDIUM"
                if average_confidence >= 0.45
                else "LOW"
            ),
            "status": status,
        }
        self._firebase.update_zone_ai_states(
            zone_states,
            garden_summary,
        )
        self._last_zone_ai_update = current_time
        self._logger.info(
            "Multi-zone AI updated. analyzed=%d configured=%d "
            "ready_predictions=%d recommendations=%d",
            analyzed_zones,
            len(configured_zone_ids),
            ready_predictions,
            watering_recommended,
        )

    def _cancel_zone_prediction_validations(
        self,
        *,
        reason: str,
        zone_id: str | None = None,
    ) -> None:
        """Cancel only predictions affected by an irrigation event."""

        queues = (
            {zone_id: self._zone_prediction_validation_queues.get(zone_id)}
            if zone_id
            else self._zone_prediction_validation_queues
        )
        cancelled = 0
        for queue in queues.values():
            if queue is not None:
                cancelled += queue.cancel_all()
        if cancelled:
            self._logger.info(
                "Zone prediction validations cancelled. "
                "count=%d zone_id=%s reason=%s",
                cancelled,
                zone_id or "all",
                reason,
            )

    def _store_prediction_result(
        self,
        *,
        prediction: MoisturePrediction,
        actual_moisture: float,
    ) -> None:
        """
        Store a time-validated prediction together with
        the measured future moisture value.

        The updated history is persisted to Firebase.
        """

        self._prediction_history.append(
            (
                prediction,
                actual_moisture,
            )
        )

        if (
            len(self._prediction_history)
            > self._prediction_history_limit
        ):
            self._prediction_history = (
                self._prediction_history[
                    -self._prediction_history_limit:
                ]
            )

        self._logger.info(
            "Validated prediction stored. "
            "actual_moisture=%.2f count=%d",
            actual_moisture,
            len(self._prediction_history),
        )

        try:
            self._firebase.save_prediction_history(
                self._prediction_history,
            )

            self._logger.debug(
                "Prediction history persisted to Firebase. "
                "count=%d",
                len(self._prediction_history),
            )

        except Exception as exc:
            self._logger.exception(
                "Prediction history could not be saved: %s",
                exc,
            )

    def _validate_due_predictions(
        self,
        *,
        actual_moisture: float,
    ) -> None:
        """
        Validate pending predictions whose target time
        has arrived.

        The current sensor reading is used as the actual
        future moisture value.
        """

        validated_results = (
            self._prediction_validation_queue.validate_due(
                actual_moisture=actual_moisture,
            )
        )

        if not validated_results:
            return

        for prediction, measured_moisture in (
            validated_results
        ):
            self._store_prediction_result(
                prediction=prediction,
                actual_moisture=measured_moisture,
            )

        self._logger.info(
            "Due prediction validations completed. "
            "validated=%d pending=%d",
            len(validated_results),
            self._prediction_validation_queue.count,
        )
        self._update_prediction_validation_status(
            force=True,
        )

    def _cancel_pending_prediction_validations(
        self,
        *,
        reason: str,
    ) -> None:
        """
        Cancel pending predictions when irrigation changes
        the natural soil-moisture behaviour.
        """

        cancelled_count = (
            self._prediction_validation_queue.cancel_all()
        )

        if cancelled_count == 0:
            return

        self._logger.info(
            "Pending prediction validations cancelled. "
            "count=%d reason=%s",
            cancelled_count,
            reason,
        )
        self._update_prediction_validation_status(
            force=True,
        )

    def _save_sensor_history_if_needed(
        self,
        *,
        reading,
        decision,
    ) -> None:
        """
        Save sensor history periodically.
        """

        current_time = time.monotonic()

        if (
            current_time
            - self._last_sensor_history_update
            < self._sensor_history_interval_seconds
        ):
            return

        entry = SensorHistoryEntry(
            moisture=reading.moisture,
            sensor_id=reading.sensor_id,
            voltage=reading.voltage,
            raw=reading.raw,
            trend_classification=(
                decision.trend_classification
            ),
            moisture_change_per_minute=(
                decision.moisture_change_per_minute
            ),
            trend_sample_count=(
                decision.trend_sample_count
            ),
            trend_duration_seconds=(
                decision.trend_duration_seconds
            ),

            average_moisture=(
                decision.average_moisture
            ),

            recorded_at=datetime.now().isoformat(),
        )

        self._firebase.save_sensor_history(
            entry,
        )

        self._last_sensor_history_update = current_time



    def _finalize_pending_watering_measurements(
        self,
        fresh_readings,
    ) -> None:
        """Save automatic watering records after their cooldown measurement."""

        if not self._pending_watering_measurements:
            return

        remaining = []
        current_epoch = int(time.time())

        for pending in self._pending_watering_measurements:
            if current_epoch < pending.finalize_after_epoch:
                remaining.append(pending)
                continue

            result = pending.result
            record = pending.record
            reading = fresh_readings.get(record.sensor_id)
            if reading is None:
                remaining.append(pending)
                continue

            finalized_record = replace(
                record,
                moisture_after=reading.moisture,
                moisture_delta=(
                    reading.moisture - record.moisture_before
                ),
            )
            try:
                self._firebase.save_watering(
                    result=result,
                    record=finalized_record,
                )
                self._firebase.delete_pending_watering(
                    pending.pending_key
                )
                self._logger.info(
                    "Watering record finalized after cooldown. "
                    "zone_id=%s sensor_id=%s before=%s after=%s",
                    record.zone_id,
                    record.sensor_id,
                    record.moisture_before,
                    reading.moisture,
                )
            except Exception:
                remaining.append(pending)
                self._logger.exception(
                    "Pending watering record finalization failed. "
                    "pending_key=%s",
                    pending.pending_key,
                )

        self._pending_watering_measurements = remaining

    def _update_weather_forecast_if_needed(self) -> None:
        """Refresh the advisory forecast without ever affecting pump safety."""
        now = time.monotonic()
        location = self._firebase.get_weather_location()
        city = str(location.get("city", "")).strip()
        district = str(location.get("district", "")).strip()
        latitude = location.get("latitude")
        longitude = location.get("longitude")
        source_preference = str(location.get("forecast_source", "auto")).strip().lower()
        has_coordinates = isinstance(latitude, (int, float)) and isinstance(longitude, (int, float))
        if not has_coordinates and (not city or not district):
            return
        location_signature = f"{city.lower()}|{district.lower()}|{latitude}|{longitude}|{source_preference}"
        if (
            location_signature == self._weather_location_signature
            and now - self._last_weather_update < self._weather_update_interval_seconds
        ):
            return

        self._last_weather_update = now

        try:
            forecast = self._weather.forecast_for(
                city, district, latitude, longitude, source_preference
            )
            self._firebase.update_weather_forecast(forecast)
            self._latest_weather_forecast = forecast
            self._weather_location_signature = location_signature
            self._logger.info(
                "Weather forecast updated. source=%s location=%s/%s tomorrow_max=%s rain_probability=%s",
                forecast.get("source", "unknown"),
                city,
                district,
                forecast.get("tomorrow_temperature_max"),
                forecast.get("tomorrow_rain_probability"),
            )
        except Exception as error:
            self._logger.warning("Weather forecast update skipped: %s", error)

    def _update_weather_policy_settings_if_needed(self) -> None:
        """Refresh rain thresholds without restarting the backend."""
        now = time.monotonic()
        if (
            now - self._last_weather_policy_settings_update
            < self._weather_policy_settings_interval_seconds
        ):
            return
        self._last_weather_policy_settings_update = now
        try:
            settings = self._firebase.get_weather_irrigation_settings()
            signature = (
                settings.get("rain_delay_enabled", True),
                settings.get("rain_probability_threshold", 80),
                settings.get("rain_mm_threshold", 2),
                settings.get("smart_timing_enabled", True),
                settings.get("garden_environment", "OPEN_FIELD"),
                settings.get("irrigation_timing_strategy", "SMART"),
                settings.get("evening_irrigation_allowed", True),
                settings.get("max_irrigation_defer_minutes", 720),
                settings.get("critical_moisture_deficit", 12),
                settings.get("timing_recheck_enabled", True),
                settings.get("preferred_start_hour", 5),
                settings.get("preferred_end_hour", 9),
            )
            self._weather_policy.configure(settings)
            self._irrigation_time_engine.configure(settings)
            if signature != self._weather_policy_settings_signature:
                self._weather_policy_settings_signature = signature
                self._logger.info(
                    "Weather irrigation settings refreshed. "
                    "rain_delay=%s probability=%s rain_mm=%s "
                    "smart_timing=%s environment=%s strategy=%s",
                    self._weather_policy.rain_delay_enabled,
                    self._weather_policy.rain_delay_probability,
                    self._weather_policy.rain_delay_mm,
                    self._irrigation_time_engine.enabled,
                    self._irrigation_time_engine.environment,
                    self._irrigation_time_engine.strategy,
                )
        except Exception as error:
            self._logger.warning(
                "Weather irrigation settings refresh skipped: %s", error
            )
    def _apply_weather_advice(self, explanation):
        """Add a clear forecast note to the AI advice; never changes watering commands."""
        forecast = self._latest_weather_forecast
        if not isinstance(forecast, dict):
            return explanation
        if not self._weather_policy.is_forecast_fresh(forecast):
            return explanation

        temperature = forecast.get("tomorrow_temperature_max")
        rain_probability = forecast.get("tomorrow_rain_probability")
        if temperature is None:
            return explanation

        lines = list(explanation.reason_lines)
        weather_note = None
        if temperature >= 35:
            weather_note = (
                f"Yarın {round(temperature)}°C sıcaklık bekleniyor; "
                "sulamayı sabah erken saatte gözlemleyin."
            )
        elif rain_probability is not None and rain_probability >= 60:
            weather_note = (
                f"Yarın %{round(rain_probability)} yağış olasılığı var; "
                "sulama kararını yağıştan sonra tekrar kontrol edin."
            )
        if not weather_note:
            return explanation

        lines.append(weather_note)
        return replace(explanation, reason_lines=tuple(lines))

    def _publish_network_status(self) -> None:
        try:
            self._firebase.update_network_status(
                self._network_configuration.read_status()
            )
        except Exception as exc:
            self._logger.warning(
                "Network status could not be published: %s",
                exc,
            )

    def _process_network_configuration_command(self, commands) -> bool:
        """Consume and execute one network request while every actuator is idle."""
        if not commands.network_configuration_requested:
            return False
        identifier = commands.network_configuration_request_id.strip().lower()
        if not identifier or identifier == self._last_network_configuration_request_id:
            return False
        self._last_network_configuration_request_id = identifier

        try:
            self._firebase.acknowledge_network_configuration_request(identifier)
        except Exception as exc:
            self._logger.warning(
                "Network request could not be acknowledged; no change applied: %s",
                exc,
            )
            return True

        now_ms = int(time.time() * 1000)
        stale = (
            commands.network_configuration_source != "android"
            or commands.network_configuration_requested_at_ms <= 0
            or commands.network_configuration_requested_at_ms > now_ms + 10_000
            or now_ms - commands.network_configuration_requested_at_ms > 180_000
            or commands.network_configuration_expires_at_ms < now_ms
        )
        if stale:
            self._firebase.update_network_configuration_result(
                request_id=identifier,
                status="STALE_COMMAND",
                message="Expired or invalid network request.",
            )
            return True

        if (
            self._relay.is_on
            or self._zone_executor.active_zone_id is not None
            or bool(self._active_zone_test_request_id)
        ):
            self._firebase.update_network_configuration_result(
                request_id=identifier,
                status="WATERING_ACTIVE",
                message="Watering or valve test is active.",
            )
            return True

        request = NetworkConfigurationRequest(
            request_id=identifier,
            interface=commands.network_configuration_interface,
            mode=commands.network_configuration_mode,
            ip_address=commands.network_configuration_ip_address,
            prefix_length=commands.network_configuration_prefix_length,
            gateway=commands.network_configuration_gateway,
            primary_dns=commands.network_configuration_primary_dns,
            secondary_dns=commands.network_configuration_secondary_dns,
        )

        def publish_stage(status: str, message: str) -> None:
            try:
                self._firebase.update_network_configuration_result(
                    request_id=identifier,
                    status=status,
                    message=message,
                )
            except Exception as exc:
                self._logger.debug(
                    "Network progress could not be published during reconnect: %s",
                    exc,
                )

        try:
            outcome = self._network_configuration.apply(request, publish_stage)
            network_status = self._network_configuration.read_status()
            self._firebase.publish_network_configuration_completion(
                request_id=identifier,
                status=outcome.status,
                message=outcome.message,
                applied_ip=outcome.applied_ip,
                network_status=network_status,
            )
            self._logger.info(
                "Network configuration finished. request_id=%s status=%s",
                identifier,
                outcome.status,
            )
        except Exception as exc:
            self._logger.exception(
                "Network configuration failed safely: %s",
                exc,
            )
            try:
                self._firebase.update_network_configuration_result(
                    request_id=identifier,
                    status="FAILED",
                    message=str(exc)[:300],
                )
            except Exception:
                pass
        return True

    def _update_ads1115_health_if_needed(self) -> None:
        """Publish each ADS1115 state without coupling it to sensor reads."""

        status_getter = getattr(self._sensor, "get_ads1115_status", None)
        if not callable(status_getter):
            return
        status = status_getter()
        if status is None:
            return

        signature = (
            status.node_online,
            status.primary_available,
            status.secondary_available,
            status.firmware,
        )
        now = time.monotonic()
        if (
            signature == self._last_ads1115_status_signature
            and now - self._last_ads1115_status_publish_monotonic < 15.0
        ):
            return

        self._firebase.update_ads1115_status(
            node_online=status.node_online,
            primary_available=status.primary_available,
            secondary_available=status.secondary_available,
            firmware=status.firmware,
            rssi=status.rssi,
            uptime_seconds=status.uptime_seconds,
            received_at_epoch=int(status.received_at.timestamp()),
        )
        self._last_ads1115_status_signature = signature
        self._last_ads1115_status_publish_monotonic = now

    def update(self) -> None:
        """
        Execute one irrigation cycle.
        """

        zone_id = ""

        try:

            commands = self._firebase.command_state

            self._update_weather_forecast_if_needed()
            self._update_weather_policy_settings_if_needed()
            # The Pi itself can be healthy while an ESP32 is offline.
            # Publish backend health before attempting a sensor read so the
            # Android diagnostics never mislabel a sensor outage as a Pi
            # connection outage.
            self._update_status_if_needed()
            self._update_health_if_needed()
            self._update_ads1115_health_if_needed()

            if self._process_network_configuration_command(commands):
                return

            if self._sensor.is_waiting_for_first_reading():
                # A fresh service starts with every actuator closed. Do not
                # process queued commands or publish a false sensor outage
                # while the MQTT listener awaits its first packet.
                return

            self._process_zone_test_command(
                commands,
            )

            self._process_irrigation_assistant_reset_command(
                commands,
            )

            reading = self._sensor.read()

            fresh_readings = self._sensor.get_fresh_readings()

            self._finalize_pending_watering_measurements(
                fresh_readings,
            )

            self._firebase.update_zone_sensors(
                fresh_readings,
            )

            self._valves.configure_physical_valves(
                self._firebase.get_physical_valve_ids(),
            )

            selected_zone_result = self._update_multi_zone_decisions(
                readings=fresh_readings,
                global_commands=commands,
            )


            zone_config = (
                self._firebase.get_zone_config_for_sensor(
                    reading.sensor_id,
                )
            )

            (
                effective_commands,
                zone_irrigation_enabled,
                zone_id,
                valve_id,
            ) = self._effective_zone_commands(
                commands,
                zone_config,
            )

            self._validate_due_predictions(
                actual_moisture=reading.moisture,
            )

            self._update_prediction_validation_status()

            self._update_status_if_needed()

            self._update_health_if_needed()

            # -------------------------------------------------
            # Smart irrigation decision
            # -------------------------------------------------

            decision = self._smart_engine.evaluate(
                reading=reading,
                commands=effective_commands,
                cooldown_active=(
                    self._zone_executor.is_cooldown_active(
                        zone_id,
                    )
                ),
            )

            self._last_irrigation_decision = decision

            self._firebase.update_irrigation_decision(
                decision,
            )
        
            self._save_sensor_history_if_needed(
                reading=reading,
                decision=decision,
            )

            self._update_ai_pipeline_if_needed(
                reading=reading,
                commands=effective_commands,
                zone_id=zone_id,
            )

            self._logger.debug(
                "Smart irrigation decision: "
                "should_water=%s reason=%s "
                "moisture=%d%% limit=%d%% "
                "sensor_stable=%s cooldown_active=%s "
                "trend=%s trend_samples=%d "
                "change_per_minute=%.3f",
                decision.should_water,
                decision.reason,
                decision.moisture,
                decision.moisture_limit,
                decision.sensor_stable,
                decision.cooldown_active,
                decision.trend_classification,
                decision.trend_sample_count,
                decision.moisture_change_per_minute,
            )

            if not commands.enabled:

                self._relay.off()
                self._reset_manual_relay_safety()

                self._logger.info(
                    "System disabled from Firebase.",
                )

                self._mark_update_cycle_recovered()

                return

            # ---------------- AUTO MODE ----------------

            if commands.auto_mode:

                self._reset_manual_relay_safety()

                if (
                    selected_zone_result is not None
                    and self._valves.is_physical_valve(
                        selected_zone_result.candidate.valve_id,
                    )
                ):
                    selected_candidate = (
                        selected_zone_result.candidate
                    )
                    reading = fresh_readings[
                        selected_candidate.sensor_id
                    ]
                    zone_config = (
                        self._firebase
                        .get_zone_config_for_sensor(
                            selected_candidate.sensor_id,
                        )
                    )
                    (
                        effective_commands,
                        zone_irrigation_enabled,
                        zone_id,
                        valve_id,
                    ) = self._effective_zone_commands(
                        commands,
                        zone_config,
                    )
                    weather_adjustment = (
                        self._weather_adjustments_by_zone.get(
                            selected_candidate.zone_id,
                        )
                    )
                    duration_plan = (
                        self._watering_duration_plans_by_zone.get(
                            selected_candidate.zone_id
                        )
                    )
                    if duration_plan is None:
                        duration_plan = self._duration_plan_for_zone(
                            zone_id=selected_candidate.zone_id,
                            sensor_id=selected_candidate.sensor_id,
                            commands=effective_commands,
                            weather_adjustment=weather_adjustment,
                        )
                    requested_duration = (
                        duration_plan.effective_duration_seconds
                    )
                    if requested_duration != effective_commands.pump_duration:
                        self._logger.info(
                            "Automatic watering duration refined safely. "
                            "zone_id=%s configured=%s effective=%s "
                            "source=%s reason=%s confidence=%.2f records=%d",
                            selected_candidate.zone_id,
                            effective_commands.pump_duration,
                            requested_duration,
                            duration_plan.source,
                            duration_plan.reason,
                            duration_plan.adaptive_confidence,
                            duration_plan.adaptive_watering_count,
                        )

                    season_allowed, active_season_ids = (
                        self._firebase.verify_zone_seasons_before_watering(
                            selected_candidate.zone_id
                        )
                    )
                    if not season_allowed:
                        self._relay.off()
                        self._valves.close_all()
                        self._logger.warning(
                            "Automatic watering blocked because the zone season "
                            "is not active. zone_id=%s",
                            selected_candidate.zone_id,
                        )
                        self._mark_update_cycle_recovered()
                        return

                    self._cancel_pending_prediction_validations(
                        reason="AUTO_IRRIGATION_STARTED",
                    )
                    self._cancel_zone_prediction_validations(
                        reason="AUTO_IRRIGATION_STARTED",
                        zone_id=selected_candidate.zone_id,
                    )

                    # Röle açılıyor

                    started_at = datetime.now()
                    watering_start_notified = False

                    def on_relay_changed(relay_on: bool) -> None:
                        nonlocal watering_start_notified
                        self._firebase.update_relay_status(relay_on)
                        if not relay_on or watering_start_notified:
                            return
                        watering_start_notified = True
                        notify_started = getattr(
                            self._firebase,
                            "notify_watering_started",
                            None,
                        )
                        if callable(notify_started):
                            notify_started(
                                zone_id=selected_candidate.zone_id,
                                duration=requested_duration,
                                moisture_before=reading.moisture,
                                started_at=started_at.isoformat(),
                            )

                    result = self._zone_executor.execute(
                        zone_id=zone_id,
                        valve_id=valve_id,
                        duration=requested_duration,
                        get_commands=(
                            lambda:
                            self._effective_zone_commands(
                                self._firebase.command_state,
                                zone_config,
                            )[0]
                        ),
                        on_relay_changed=on_relay_changed,
                        on_valve_changed=(
                            lambda active_valve_id, is_open:
                            self._firebase.update_active_zone_valve(
                                active_valve_id,
                                is_open,
                                zone_id,
                                valve_id,
                                self._valves.is_physical_valve(valve_id),
                            )
                        ),
                    )

                    if result.completed:
                        self._zone_scheduler.mark_served(zone_id)
                        self._multi_zone_engine.mark_watering_completed(
                            selected_candidate.sensor_id,
                        )
                        if (
                            selected_candidate.sensor_id
                            == SensorConfig.MQTT_SENSOR_ID
                        ):
                            self._smart_engine.mark_watering_completed()
                        self._persist_zone_irrigation_safety_state(
                            zone_id=zone_id,
                            sensor_id=selected_candidate.sensor_id,
                        )


                        self._firebase.update_zone_cooldown(
                            zone_id=zone_id,
                            cooldown_until_epoch=(
                                self._zone_executor
                                .cooldown_until_epoch_for(zone_id)
                            ),
                            cooldown_remaining=(
                                self._zone_executor
                                .cooldown_remaining_for(zone_id)
                            ),
                        )

                    finished_at = datetime.now()
                    finished_reading = (
                        self._sensor.get_fresh_readings().get(
                            selected_candidate.sensor_id,
                            reading,
                        )
                    )

                    # Röle kapandı

                    record = WateringRecord(
                        started_at=started_at.isoformat(),
                        finished_at=finished_at.isoformat(),
                        duration=result.duration,

                        moisture_before=reading.moisture,
                        moisture_after=finished_reading.moisture,
                        moisture_delta=(
                            finished_reading.moisture
                            - reading.moisture
                        ),
                        moisture_limit=(
                            effective_commands.moisture_limit
                        ),

                        restart_delta=(
                            effective_commands.restart_delta
                        ),
                        cooldown_seconds=(
                            effective_commands.cooldown_seconds
                        ),

                        completed=result.completed,

                        stop_reason=result.stop_reason,

                        mode="AUTO",

                        firmware=AppConfig.VERSION,
                        zone_id=selected_candidate.zone_id,
                        sensor_id=selected_candidate.sensor_id,
                        season_id=(
                            active_season_ids[0] if active_season_ids else ""
                        ),
                        season_ids=active_season_ids,
                    )

                    if (
                        result.completed
                        and effective_commands.cooldown_seconds > 0
                    ):
                        pending = PendingWateringMeasurement(
                            pending_key=record.firebase_key,
                            finalize_after_epoch=(
                                int(time.time())
                                + effective_commands.cooldown_seconds
                            ),
                            result=result,
                            record=record,
                        )
                        try:
                            self._firebase.save_pending_watering(pending)
                            self._pending_watering_measurements.append(pending)
                            self._logger.info(
                                "Watering record will be finalized after cooldown. "
                                "zone_id=%s sensor_id=%s cooldown=%s",
                                zone_id,
                                selected_candidate.sensor_id,
                                effective_commands.cooldown_seconds,
                            )
                        except Exception:
                            self._logger.exception(
                                "Pending watering record could not be persisted; "
                                "saving the immediate result instead."
                            )
                            self._firebase.save_watering(
                                result=result,
                                record=record,
                            )
                    else:
                        self._firebase.save_watering(
                            result=result,
                            record=record,
                        )
                else:

                    self._relay.off()

                mode = "AUTO"

            # ---------------- MANUAL MODE ----------------

            else:

                relay_requested = commands.relay
                if (
                    relay_requested
                    and not self._manual_pump_interlock_ready()
                ):
                    # Never allow the manual pump command to run dry.  The
                    # Android screen is only a convenience layer; the Pi is
                    # the final safety authority.
                    relay_requested = False
                    self._relay.off()
                    self._reset_manual_relay_safety()
                    self._logger.warning(
                        "Manual relay command rejected: no physical valve "
                        "is open.",
                    )
                    self._firebase.set_relay_command(False)

                if (
                    relay_requested
                    and not self._is_recent_command(
                        commands.relay_requested_at_ms
                    )
                ):
                    relay_requested = False
                    self._relay.off()
                    self._reset_manual_relay_safety()
                    self._logger.warning(
                        "Stale manual relay command rejected.",
                    )
                    self._firebase.set_relay_command(False)

                manual_timed_out = (
                    self._apply_manual_relay_command(
                        relay_requested,
                    )
                )

                if relay_requested:
                    self._cancel_pending_prediction_validations(
                        reason="MANUAL_IRRIGATION_STARTED",
                    )
                    self._cancel_zone_prediction_validations(
                        reason="MANUAL_IRRIGATION_STARTED",
                    )

                if manual_timed_out:
                    try:
                        self._firebase.set_relay_command(
                            False,
                        )
                    except Exception as exc:
                        self._logger.exception(
                            "Manual relay command could not "
                            "be reset after timeout: %s",
                            exc,
                        )
                    
                self._firebase.update_relay_status(
                    self._relay.is_on,
                )

                mode = "MANUAL"
            """

            Burada ki .info olunca terminalde görünüyor. .debug olunca gerekirse görünüyor 
            
            """
            self._logger.debug(
                "Mode=%s Raw=%d Voltage=%.3f V Moisture=%d%% "
                "Limit=%d%% Relay=%s",
                mode,
                reading.raw,
                reading.voltage,
                reading.moisture,
                effective_commands.moisture_limit,
                "ON" if self._relay.is_on else "OFF",
            )

            self._logger.debug(
                "Commands: %s",
                commands,
            )

            self._mark_update_cycle_recovered()

        except Exception as exc:

            self._enter_fail_safe(
                reason=type(exc).__name__,
            )

            # One successful retry must not close an outage. Reset the
            # recovery candidate on every failed cycle so intermittent ESP32
            # data remains a single incident instead of notification spam.
            self._update_recovery_started_at = 0.0
            self._update_recovery_success_count = 0

            current_time = time.monotonic()

            should_report_error = (
                not self._update_error_active
                or (
                    current_time
                    - self._last_update_error_log
                    >= self._update_error_log_interval_seconds
                )
            )

            self._update_error_active = True

            if not should_report_error:
                return

            self._last_update_error_log = current_time

            self._logger.exception(
                "Update cycle failed. Relay=%s Error=%s",
                "ON" if self._relay.is_on else "OFF",
                exc,
            )

            try:

                self._firebase.report_error(
                    str(exc),
                )

            except Exception as report_exc:
                self._logger.debug(
                    "Firebase error report failed: %s",
                    report_exc,
                )

        finally:

            uptime = int(
                time.monotonic()
                - self._started_at
            )

            try:

                self._firebase.update_runtime_status(
                    relay=self._relay.is_on,
                    uptime=uptime,
                    sensor_time=datetime.now().isoformat(),
                    watering_state=self._zone_executor.state.value,
                    cooldown_remaining=(
                        self._zone_executor.cooldown_remaining_for(
                            zone_id,
                        )
                    ),
                )

            except Exception as exc:

                self._logger.exception(
                    "Runtime status update failed: %s",
                    exc,
                )

    def _enter_fail_safe(
        self,
        *,
        reason: str,
    ) -> None:
        """
        Independently attempt every physical safety action.

        One hardware cleanup failure must never prevent the
        remaining pump/valve shutdown steps.
        """

        relay_error = None
        valve_error = None

        try:
            self._relay.off()
        except Exception as exc:
            relay_error = exc

        try:
            self._valves.close_all()
        except Exception as exc:
            valve_error = exc

        self._reset_manual_relay_safety()

        if relay_error is not None:
            self._logger.error(
                "Fail-safe relay shutdown failed. "
                "reason=%s error=%s",
                reason,
                relay_error,
            )

        if valve_error is not None:
            self._logger.error(
                "Fail-safe valve shutdown failed. "
                "reason=%s error=%s",
                reason,
                valve_error,
            )

        self._logger.warning(
            "Fail-safe applied. reason=%s relay=%s",
            reason,
            "ON" if self._relay.is_on else "OFF",
        )

    def _effective_zone_commands(
        self,
        commands,
        zone_config,
    ):
        """
        Overlay one zone's irrigation settings on global commands.
        """

        if not isinstance(zone_config, dict):
            return commands, False, "", ""

        def bounded_int(
            field,
            default,
            minimum,
            maximum,
        ):
            try:
                value = int(
                    zone_config.get(field, default),
                )
            except (TypeError, ValueError):
                value = default

            return max(minimum, min(maximum, value))

        effective = replace(
            commands,
            moisture_limit=bounded_int(
                "moisture_limit",
                commands.moisture_limit,
                IrrigationConfig.MIN_MOISTURE_LIMIT,
                IrrigationConfig.MAX_MOISTURE_LIMIT,
            ),
            pump_duration=bounded_int(
                "pump_duration",
                commands.pump_duration,
                IrrigationConfig.MIN_PUMP_DURATION_SECONDS,
                IrrigationConfig.MAX_PUMP_DURATION_SECONDS,
            ),
            restart_delta=bounded_int(
                "restart_delta",
                commands.restart_delta,
                IrrigationConfig.MIN_RESTART_DELTA,
                IrrigationConfig.MAX_RESTART_DELTA,
            ),
            cooldown_seconds=bounded_int(
                "cooldown_seconds",
                commands.cooldown_seconds,
                IrrigationConfig.MIN_COOLDOWN_SECONDS,
                IrrigationConfig.MAX_COOLDOWN_SECONDS,
            ),
        )

        season = zone_config.get("season")
        season_status = ""
        season_id = ""
        if isinstance(season, dict):
            season_status = str(season.get("status", "")).strip().upper()
            season_id = str(
                season.get("active_season_id", "")
            ).strip()
        season_allows_irrigation = (
            not season_status
            or (season_status == "ACTIVE" and bool(season_id))
        )
        zone_enabled = (
            zone_config.get("enabled", True) is True
            and zone_config.get(
                "irrigation_enabled",
                False,
            ) is True
            and season_allows_irrigation
        )
        zone_id = str(
            zone_config.get("zone_id", ""),
        )
        valve_id = str(
            zone_config.get("valve_id", ""),
        )

        signature = (
            zone_id,
            zone_enabled,
            effective.moisture_limit,
            effective.pump_duration,
            effective.cooldown_seconds,
            effective.restart_delta,
            valve_id,
        )

        if (
            signature
            != self._last_zone_config_signatures.get(zone_id)
        ):
            self._last_zone_config_signatures[zone_id] = signature
            self._logger.info(
                "Zone irrigation settings applied. "
                "zone_id=%s enabled=%s limit=%d duration=%d "
                "cooldown=%d restart_delta=%d valve_id=%s",
                zone_id,
                zone_enabled,
                effective.moisture_limit,
                effective.pump_duration,
                effective.cooldown_seconds,
                effective.restart_delta,
                valve_id,
            )

        return (
            effective,
            zone_enabled,
            zone_id,
            valve_id,
        )

    def _adaptive_recommendation_for_zone(
        self,
        *,
        zone_id: str,
        sensor_id: str,
        commands,
    ):
        """Return a cached, zone-scoped recommendation from completed records."""

        reader = getattr(
            self._firebase,
            "get_recent_watering_records",
            None,
        )
        if not callable(reader) or not zone_id or not sensor_id:
            return None

        cache = getattr(self, "_adaptive_recommendation_cache", None)
        if cache is None:
            cache = {}
            self._adaptive_recommendation_cache = cache

        signature = (
            sensor_id,
            int(commands.pump_duration),
            int(commands.cooldown_seconds),
        )
        now = time.monotonic()
        cached = cache.get(zone_id)
        cache_seconds = float(
            getattr(
                self,
                "_adaptive_recommendation_cache_seconds",
                300.0,
            )
        )
        if (
            isinstance(cached, dict)
            and cached.get("signature") == signature
            and now - float(cached.get("updated_at", 0.0)) < cache_seconds
        ):
            return cached.get("recommendation")

        engine = getattr(self, "_adaptive_engine", None)
        if engine is None:
            engine = AdaptiveIrrigationEngine()
            self._adaptive_engine = engine

        try:
            records = reader(
                limit=30,
                sensor_id=sensor_id,
                zone_id=zone_id,
            )
            recommendation = engine.analyze(
                records=records,
                current_pump_duration_seconds=commands.pump_duration,
                current_cooldown_seconds=commands.cooldown_seconds,
            )
        except Exception as exc:
            self._logger.warning(
                "Adaptive zone duration could not be evaluated. "
                "zone_id=%s sensor_id=%s error=%s",
                zone_id,
                sensor_id,
                exc,
            )
            return None

        cache[zone_id] = {
            "signature": signature,
            "updated_at": now,
            "recommendation": recommendation,
        }
        return recommendation

    def _duration_plan_for_zone(
        self,
        *,
        zone_id: str,
        sensor_id: str,
        commands,
        weather_adjustment,
    ):
        """Resolve the explainable runtime duration for one automatic cycle."""

        recommendation = self._adaptive_recommendation_for_zone(
            zone_id=zone_id,
            sensor_id=sensor_id,
            commands=commands,
        )
        policy = getattr(self, "_duration_policy", None)
        if policy is None:
            policy = RuntimeWateringDurationPolicy()
            self._duration_policy = policy

        plan = policy.resolve(
            configured_duration_seconds=commands.pump_duration,
            adaptive_recommendation=recommendation,
            weather_adjustment=weather_adjustment,
            minimum_duration_seconds=max(
                1,
                IrrigationConfig.MIN_PUMP_DURATION_SECONDS,
            ),
            maximum_duration_seconds=(
                IrrigationConfig.MAX_PUMP_DURATION_SECONDS
            ),
        )

        recommendations = getattr(
            self,
            "_adaptive_recommendations_by_zone",
            None,
        )
        if recommendations is None:
            recommendations = {}
            self._adaptive_recommendations_by_zone = recommendations
        recommendations[zone_id] = recommendation

        return plan

    def _update_multi_zone_decisions(
        self,
        *,
        readings,
        global_commands,
    ) -> ZoneDecisionResult | None:
        """
        Evaluate every connected zone and publish queue state.
        """

        hardware_configs = (
            self._firebase.get_all_zone_configs_by_sensor()
        )
        # Hardware configuration and telemetry stay active before planting,
        # but irrigation and AI decisions begin only with an active season.
        configs = {
            sensor_id: zone
            for sensor_id, zone in hardware_configs.items()
            if self._zone_has_operational_season(zone)
        }
        results = []
        self._weather_adjustments_by_zone = {}
        self._irrigation_time_plans_by_zone = {}
        self._watering_duration_plans_by_zone = {}
        self._adaptive_recommendations_by_zone = {}
        self._synchronize_zone_ai_season_scopes(configs)

        for sensor_id, reading in readings.items():
            zone = configs.get(sensor_id)
            if not isinstance(zone, dict):
                continue

            (
                commands,
                irrigation_enabled,
                zone_id,
                valve_id,
            ) = self._effective_zone_commands(
                global_commands,
                zone,
            )

            result = self._multi_zone_engine.evaluate(
                zone_id=zone_id,
                valve_id=valve_id,
                order=int(zone.get("order", 0)),
                irrigation_enabled=(
                    irrigation_enabled
                    and global_commands.enabled
                    and global_commands.auto_mode
                    and commands.pump_duration > 0
                ),
                hardware_ready=(
                    self._valves.is_physical_valve(valve_id)
                ),
                reading=reading,
                commands=commands,
                cooldown_active=(
                    self._zone_executor.is_cooldown_active(
                        zone_id,
                    )
                ),
            )
            adjustment = self._weather_policy.evaluate(
                forecast=self._latest_weather_forecast,
                moisture_deficit=result.candidate.moisture_deficit,
                irrigation_method=str(
                    zone.get("irrigation_method", "DRIP"),
                ),
            )
            self._weather_adjustments_by_zone[zone_id] = adjustment
            duration_plan = self._duration_plan_for_zone(
                zone_id=zone_id,
                sensor_id=sensor_id,
                commands=commands,
                weather_adjustment=adjustment,
            )
            self._watering_duration_plans_by_zone[zone_id] = duration_plan
            timing_plan = IrrigationTimePlan(
                status="NOT_REQUIRED",
                reason="TIMING_NOT_REQUIRED",
                detail="Nem ve sulama guvenlik kosullari sulama istemiyor.",
                recheck_before_watering=True,
            )
            if result.candidate.should_water:
                irrigation_status = zone.get("irrigation_status")
                persisted_timing_plan = (
                    irrigation_status.get("timing_plan")
                    if isinstance(irrigation_status, dict)
                    else None
                )
                timing_plan = self._irrigation_time_engine.evaluate(
                    forecast=self._latest_weather_forecast,
                    moisture_deficit=result.candidate.moisture_deficit,
                    irrigation_method=str(
                        zone.get("irrigation_method", "DRIP"),
                    ),
                    zone_settings=zone.get("irrigation_timing"),
                    existing_plan=persisted_timing_plan,
                )

            if result.candidate.should_water and adjustment.postpone:
                result = replace(
                    result,
                    candidate=replace(
                        result.candidate,
                        should_water=False,
                        reason=adjustment.reason,
                    ),
                    decision=replace(
                        result.decision,
                        should_water=False,
                        reason=adjustment.reason,
                    ),
                )
                timing_plan = IrrigationTimePlan(
                    status="WEATHER_POSTPONED",
                    postpone=True,
                    reason=adjustment.reason,
                    detail=(
                        "Yagis ve hava guvenligi nedeniyle sulama ertelendi; "
                        "sonraki dongude kosullar yeniden degerlendirilecek."
                    ),
                    weather_based=True,
                    recheck_before_watering=True,
                )
                self._logger.info(
                    "Weather postponed automatic irrigation. "
                    "zone_id=%s deficit=%s reason=%s",
                    zone_id,
                    result.candidate.moisture_deficit,
                    adjustment.reason,
                )
            elif result.candidate.should_water and timing_plan.postpone:
                result = replace(
                    result,
                    candidate=replace(
                        result.candidate,
                        should_water=False,
                        reason=timing_plan.reason,
                    ),
                    decision=replace(
                        result.decision,
                        should_water=False,
                        reason=timing_plan.reason,
                    ),
                )
                self._logger.info(
                    "Automatic irrigation scheduled for a safer time. "
                    "zone_id=%s deficit=%s recommended_at=%s reason=%s",
                    zone_id,
                    result.candidate.moisture_deficit,
                    timing_plan.recommended_at_epoch,
                    timing_plan.reason,
                )

            self._irrigation_time_plans_by_zone[zone_id] = timing_plan
            results.append(result)

        self._update_multi_zone_ai_if_needed(
            results=results,
            configs=configs,
            readings=readings,
            global_commands=global_commands,
        )

        ordered_candidates = self._zone_scheduler.ordered([
            result.candidate
            for result in results
        ])
        selected = (
            ordered_candidates[0]
            if ordered_candidates
            else None
        )
        selected_result = next(
            (
                result
                for result in results
                if (
                    selected is not None
                    and result.candidate.zone_id
                    == selected.zone_id
                )
            ),
            None,
        )

        queue_positions = {
            item.zone_id: index
            for index, item in enumerate(
                ordered_candidates,
                start=1,
            )
        }

        states = {}
        signature_items = []

        for result in results:
            candidate = result.candidate
            decision = result.decision
            is_selected = (
                selected is not None
                and selected.zone_id == candidate.zone_id
            )
            safety_state = self._multi_zone_engine.get_safety_state(
                candidate.sensor_id
            )
            published_reason = (
                "VALVE_NOT_PHYSICAL"
                if decision.should_water and not candidate.hardware_ready
                else decision.reason
            )
            duration_plan = self._watering_duration_plans_by_zone[
                candidate.zone_id
            ]
            state = {
                "decision": (
                    "WATER"
                    if decision.should_water and candidate.hardware_ready
                    else "WAIT"
                ),
                "decision_reason": published_reason,
                "sensor_stable": decision.sensor_stable,
                "cooldown_active": decision.cooldown_active,
                "cooldown_remaining": (
                    self._zone_executor.cooldown_remaining_for(
                        candidate.zone_id,
                    )
                ),
                "cooldown_until_epoch": (
                    self._zone_executor.cooldown_until_epoch_for(
                        candidate.zone_id,
                    )
                ),
                "queue_position": queue_positions.get(
                    candidate.zone_id,
                    0,
                ),
                "fairness_waiting_turns": (
                    self._zone_scheduler.waiting_turns(
                        candidate.zone_id,
                    )
                ),
                "selected_for_watering": is_selected,
                "moisture_deficit": candidate.moisture_deficit,
                "hardware_ready": candidate.hardware_ready,
                "completed_watering_cycles": safety_state[
                    "completed_watering_cycles"
                ],
                "waiting_for_moisture_recovery": safety_state[
                    "waiting_for_moisture_recovery"
                ],
                "weather_adjustment": (
                    self._weather_adjustments_by_zone[
                        candidate.zone_id
                    ].reason
                ),
                "timing_plan": self._irrigation_time_plans_by_zone[
                    candidate.zone_id
                ].to_dict(),
                "configured_duration_seconds": (
                    duration_plan.configured_duration_seconds
                ),
                "learned_duration_seconds": (
                    duration_plan.learned_duration_seconds
                ),
                "effective_duration_seconds": (
                    duration_plan.effective_duration_seconds
                ),
                "duration_source": duration_plan.source,
                "duration_adjustment_reason": duration_plan.reason,
                "adaptive_confidence": (
                    duration_plan.adaptive_confidence
                ),
                "adaptive_watering_count": (
                    duration_plan.adaptive_watering_count
                ),
                "adaptive_recommendation_type": (
                    duration_plan.adaptive_recommendation_type
                ),
                "adaptive_applied": duration_plan.adaptive_applied,
            }

            states[candidate.zone_id] = state
            signature_items.append(
                (
                    candidate.zone_id,
                    *state.values(),
                )
            )

        signature = tuple(sorted(signature_items))
        if signature == self._last_multi_zone_status_signature:
            return selected_result

        self._last_multi_zone_status_signature = signature
        self._firebase.update_zone_irrigation_decisions(
            states,
        )
        selected_zone_id = (
            selected.zone_id
            if selected is not None
            else "none"
        )
        log_signature = (
            len(results),
            len(ordered_candidates),
            selected_zone_id,
        )
        if log_signature != self._last_multi_zone_log_signature:
            self._last_multi_zone_log_signature = log_signature
            self._logger.info(
                "Multi-zone decisions updated. "
                "connected=%d queued=%d selected=%s",
                len(results),
                len(ordered_candidates),
                selected_zone_id,
            )

        return selected_result

    def _restore_zone_cooldowns(self) -> None:
        """
        Restore valid per-zone cooldowns after a service restart.
        """

        restored_count = 0
        configs = self._firebase.get_all_zone_configs_by_sensor()

        for zone in configs.values():
            if not isinstance(zone, dict):
                continue

            zone_id = str(zone.get("zone_id", ""))
            irrigation_status = zone.get("irrigation_status")
            if (
                not zone_id
                or not isinstance(irrigation_status, dict)
            ):
                continue

            persisted_until = irrigation_status.get(
                "cooldown_until_epoch",
                0,
            )
            configured_cooldown = min(
                IrrigationConfig.MAX_COOLDOWN_SECONDS,
                max(
                    0,
                    int(
                        zone.get(
                            "cooldown_seconds",
                            IrrigationConfig.DEFAULT_COOLDOWN_SECONDS,
                        )
                    ),
                ),
            )

            remaining = self._zone_executor.restore_cooldown(
                zone_id=zone_id,
                cooldown_until_epoch=persisted_until,
                max_remaining_seconds=configured_cooldown,
            )

            if remaining > 0:
                restored_count += 1
            elif persisted_until:
                self._firebase.update_zone_cooldown(
                    zone_id=zone_id,
                    cooldown_until_epoch=0,
                    cooldown_remaining=0,
                )

        self._logger.info(
            "Zone cooldowns restored. count=%d",
            restored_count,
        )

    def _restore_zone_irrigation_safety_states(self) -> None:
        """Restore bounded automatic-watering cycle guards after restart."""

        restored_count = 0
        configs = self._firebase.get_all_zone_configs_by_sensor()
        for sensor_id, zone in configs.items():
            if not sensor_id or not isinstance(zone, dict):
                continue
            status = zone.get("irrigation_status")
            if not isinstance(status, dict):
                continue

            cycles = status.get("completed_watering_cycles", 0)
            waiting = status.get(
                "waiting_for_moisture_recovery",
                False,
            )
            self._multi_zone_engine.restore_safety_state(
                str(sensor_id),
                completed_watering_cycles=cycles,
                waiting_for_moisture_recovery=waiting,
            )
            if str(sensor_id) == SensorConfig.MQTT_SENSOR_ID:
                self._smart_engine.restore_safety_state(
                    completed_watering_cycles=cycles,
                    waiting_for_moisture_recovery=waiting,
                )
            if cycles or waiting is True:
                restored_count += 1

        self._logger.info(
            "Zone irrigation safety states restored. count=%d",
            restored_count,
        )

    def _persist_zone_irrigation_safety_state(
        self,
        *,
        zone_id: str,
        sensor_id: str,
    ) -> None:
        state = self._multi_zone_engine.get_safety_state(sensor_id)
        try:
            self._firebase.update_zone_irrigation_safety_state(
                zone_id=zone_id,
                completed_watering_cycles=state[
                    "completed_watering_cycles"
                ],
                waiting_for_moisture_recovery=state[
                    "waiting_for_moisture_recovery"
                ],
            )
        except Exception:
            self._logger.exception(
                "Zone irrigation safety state could not be persisted. "
                "zone_id=%s sensor_id=%s",
                zone_id,
                sensor_id,
            )

    def _restore_pending_watering_measurements(self) -> None:
        """Restore post-cooldown measurements without duplicating history."""

        try:
            self._pending_watering_measurements = (
                self._firebase.load_pending_waterings()
            )
        except Exception:
            self._pending_watering_measurements = []
            self._logger.exception(
                "Pending watering measurements could not be restored."
            )
            return

        self._logger.info(
            "Pending watering measurements restored. count=%d",
            len(self._pending_watering_measurements),
        )

    def _apply_manual_relay_command(
        self,
        commanded_on: bool,
    ) -> bool:
        """
        Apply manual relay control with a hard safety timeout.

        Returns True when the timeout has been reached.
        """

        if not commanded_on:
            self._relay.off()
            self._reset_manual_relay_safety()

            return False

        if self._manual_relay_timeout_latched:
            self._relay.off()
            return True

        current_time = time.monotonic()

        if self._manual_relay_started_at <= 0:
            self._manual_relay_started_at = current_time

        elapsed = (
            current_time
            - self._manual_relay_started_at
        )

        if (
            elapsed
            >= IrrigationConfig.MAX_MANUAL_PUMP_DURATION_SECONDS
        ):
            self._relay.off()
            self._manual_relay_timeout_latched = True

            self._logger.warning(
                "Manual irrigation safety timeout reached. "
                "maximum=%d seconds",
                IrrigationConfig.MAX_MANUAL_PUMP_DURATION_SECONDS,
            )

            return True

        self._relay.on()

        return False

    def _manual_pump_interlock_ready(self) -> bool:
        """A manual pump run requires one fully opened physical valve."""
        active_valve_id = self._valves.active_valve_id
        return (
            active_valve_id is not None
            and self._valves.is_physical_valve(active_valve_id)
            and self._valves.is_ready_for_pump(active_valve_id)
        )

    def _process_zone_test_command(
        self,
        commands,
    ) -> None:
        """
        Run one safe valve-only test requested by Android.

        The real pump remains blocked while valves are simulated.
        """

        if self._active_zone_test_request_id:
            remaining = max(
                0,
                int(
                    self._active_zone_test_deadline
                    - time.monotonic()
                ),
            )
            if (
                commands.zone_test_cancel_requested
                or remaining <= 0
            ):
                self._relay.off()
                self._firebase.update_active_zone_valve(
                    None,
                    False,
                    commands.zone_test_zone_id,
                    self._active_zone_test_valve_id,
                    self._active_zone_test_mode == "PHYSICAL_TEST",
                )
                self._valves.close_all()
                self._firebase.acknowledge_zone_test(
                    request_id=(
                        self._active_zone_test_request_id
                    ),
                    result=(
                        f"{self._active_zone_test_mode}_CANCELLED"
                        if commands.zone_test_cancel_requested
                        else f"{self._active_zone_test_mode}_COMPLETED"
                    ),
                )
                self._active_zone_test_request_id = ""
                self._active_zone_test_valve_id = ""
                self._active_zone_test_mode = ""
                self._active_zone_test_deadline = 0.0

        if (
            not commands.zone_test_requested
            or self._active_zone_test_request_id
        ):
            return

        request_id = commands.zone_test_request_id

        if (
            not request_id
            or request_id == self._last_zone_test_request_id
        ):
            return

        self._last_zone_test_request_id = request_id

        if not commands.zone_test_valve_id:
            result = "INVALID_VALVE"
        elif not self._is_recent_command(
            commands.zone_test_requested_at_ms
        ):
            result = "STALE_COMMAND"
        else:
            duration = max(1, commands.zone_test_duration)
            test_mode = (
                "PHYSICAL_TEST"
                if self._valves.is_physical_valve(
                    commands.zone_test_valve_id,
                )
                else "SIMULATION"
            )
            self._relay.off()
            self._valves.open(
                commands.zone_test_valve_id,
            )
            self._firebase.update_active_zone_valve(
                commands.zone_test_valve_id,
                True,
                commands.zone_test_zone_id,
                commands.zone_test_valve_id,
                test_mode == "PHYSICAL_TEST",
            )
            self._active_zone_test_request_id = request_id
            self._active_zone_test_valve_id = (
                commands.zone_test_valve_id
            )
            self._active_zone_test_mode = test_mode
            self._active_zone_test_deadline = (
                time.monotonic() + duration
            )
            result = f"{test_mode}_ACTIVE"

        self._firebase.acknowledge_zone_test(
            request_id=request_id,
            result=result,
            active=result in {
                "SIMULATION_ACTIVE",
                "PHYSICAL_TEST_ACTIVE",
            },
            remaining_seconds=(
                commands.zone_test_duration
                if result in {
                    "SIMULATION_ACTIVE",
                    "PHYSICAL_TEST_ACTIVE",
                }
                else 0
            ),
        )

    def _process_irrigation_assistant_reset_command(
        self,
        commands,
    ) -> None:
        """Safely reset one or all zones' transient assistant state."""

        if not commands.irrigation_assistant_reset_requested:
            return

        request_id = (
            commands.irrigation_assistant_reset_request_id.strip()
        )
        zone_id = commands.irrigation_assistant_reset_zone_id.strip()

        if (
            not request_id
            or request_id
            == self._last_irrigation_assistant_reset_request_id
        ):
            return

        self._last_irrigation_assistant_reset_request_id = request_id

        if not zone_id:
            result = "INVALID_ZONE"
        elif not self._is_recent_command(
            commands.irrigation_assistant_reset_requested_at_ms
        ):
            result = "STALE_COMMAND"
        elif (
            self._relay.is_on
            or self._zone_executor.active_zone_id is not None
            or bool(self._active_zone_test_request_id)
        ):
            result = "WATERING_ACTIVE"
        else:
            reset_all_zones = zone_id.upper() == "ALL"
            configured_zones = (
                self._firebase.get_all_zone_configs_by_sensor()
            )
            zone_sensor_pairs: list[tuple[str, str]] = []

            for configured_sensor_id, zone in configured_zones.items():
                sensor_id = str(configured_sensor_id).strip()
                configured_zone_id = str(
                    zone.get("zone_id", "")
                ).strip()
                if not sensor_id or not configured_zone_id:
                    continue
                if reset_all_zones or configured_zone_id == zone_id:
                    zone_sensor_pairs.append(
                        (configured_zone_id, sensor_id)
                    )

            if not zone_sensor_pairs:
                result = "ZONE_NOT_FOUND"
            else:
                reset_reason = (
                    "IRRIGATION_ASSISTANT_RESET_ALL"
                    if reset_all_zones
                    else "IRRIGATION_ASSISTANT_RESET"
                )
                primary_sensor_reset = False
                reset_count = 0
                reset_sensor_ids: set[str] = set()

                for configured_zone_id, sensor_id in zone_sensor_pairs:
                    if sensor_id in reset_sensor_ids:
                        continue
                    reset_sensor_ids.add(sensor_id)
                    self._multi_zone_engine.reset(sensor_id)
                    self._persist_zone_irrigation_safety_state(
                        zone_id=configured_zone_id,
                        sensor_id=sensor_id,
                    )
                    reset_count += 1
                    if sensor_id == SensorConfig.MQTT_SENSOR_ID:
                        primary_sensor_reset = True

                self._cancel_zone_prediction_validations(
                    reason=reset_reason,
                    zone_id=None if reset_all_zones else zone_id,
                )

                if primary_sensor_reset:
                    self._smart_engine.reset()
                    self._cancel_pending_prediction_validations(
                        reason=reset_reason,
                    )

                self._last_zone_ai_update = 0.0
                self._last_ai_decision_update = 0.0
                self._last_prediction_validation_status_update = 0.0
                self._last_multi_zone_status_signature = None
                self._last_multi_zone_log_signature = None
                result = (
                    "COMPLETED_ALL"
                    if reset_all_zones
                    else "COMPLETED"
                )

                self._logger.info(
                    "Irrigation assistant process restarted safely. "
                    "scope=%s reset_count=%d",
                    zone_id,
                    reset_count,
                )

        self._firebase.acknowledge_irrigation_assistant_reset(
            request_id=request_id,
            zone_id=zone_id,
            result=result,
        )

        if result not in {"COMPLETED", "COMPLETED_ALL"}:
            self._logger.warning(
                "Irrigation assistant restart rejected. "
                "zone_id=%s result=%s",
                zone_id or "unknown",
                result,
            )
    @staticmethod
    def _is_recent_command(
        requested_at_ms: int,
        maximum_age_seconds: int = 30,
    ) -> bool:
        """
        Reject actuator commands queued while the device was offline.
        """

        if requested_at_ms <= 0:
            return False

        age_seconds = (
            int(time.time() * 1000) - requested_at_ms
        ) / 1000.0

        return -300 <= age_seconds <= maximum_age_seconds

    def _reset_manual_relay_safety(self) -> None:
        """
        Reset manual relay timeout state after an OFF command
        or a mode change.
        """

        self._manual_relay_started_at = 0.0
        self._manual_relay_timeout_latched = False

    def _mark_update_cycle_recovered(self) -> None:
        """
        Close an update incident only after recovery remains stable.

        ESP32 power loss can briefly alternate between a cached successful
        read and a failed fresh read. Immediate clearing would turn that one
        outage into a new notification on every retry.
        """

        if not self._update_error_active:
            return

        current_time = time.monotonic()
        recovery_started_at = getattr(
            self,
            "_update_recovery_started_at",
            0.0,
        )

        if recovery_started_at <= 0.0:
            self._update_recovery_started_at = current_time
            self._update_recovery_success_count = 1
            return

        self._update_recovery_success_count = (
            getattr(self, "_update_recovery_success_count", 0) + 1
        )

        recovery_seconds = current_time - recovery_started_at
        required_seconds = getattr(
            self,
            "_update_recovery_confirmation_seconds",
            120.0,
        )
        required_successes = getattr(
            self,
            "_update_recovery_required_successes",
            3,
        )

        if (
            recovery_seconds < required_seconds
            or self._update_recovery_success_count < required_successes
        ):
            return

        self._update_error_active = False
        self._last_update_error_log = 0.0
        self._update_recovery_started_at = 0.0
        self._update_recovery_success_count = 0

        self._logger.info(
            "Update cycle recovered.",
        )

        try:
            self._firebase.clear_error()
        except Exception as exc:
            self._logger.warning(
                "Recovered error status could not be cleared: %s",
                exc,
            )


    def cleanup(self) -> None:
        """
        Release resources without allowing one cleanup failure
        to prevent the remaining safety steps.
        """

        try:
            self._relay.cleanup()
        except Exception as exc:
            self._logger.exception(
                "Relay cleanup failed: %s",
                exc,
            )

        try:
            self._valves.cleanup()
        except Exception as exc:
            self._logger.exception(
                "Valve cleanup failed: %s",
                exc,
            )

        try:
            self._sensor.stop()
        except Exception as exc:
            self._logger.exception(
                "Sensor provider cleanup failed: %s",
                exc,
            )

        try:
            self._seedling_bridge.stop()
        except Exception as exc:
            self._logger.exception(
                "Seedling assistant cleanup failed: %s",
                exc,
            )

        try:
            self._feedback_email.stop()
        except Exception as exc:
            self._logger.exception(
                "Feedback email service cleanup failed: %s",
                exc,
            )

        try:
            self._firebase.update_active_zone_valve(
                None,
                False,
            )
            self._firebase.reset_all_zone_watering_states()
        except Exception as exc:
            self._logger.exception(
                "Firebase watering state cleanup failed: %s",
                exc,
            )

        try:
            self._firebase.stop_command_sync()
        except Exception as exc:
            self._logger.exception(
                "Firebase command synchronization cleanup failed: %s",
                exc,
            )

        try:
            self._firebase.set_online(False)
        except Exception as exc:
            self._logger.exception(
                "Device could not be marked offline: %s",
                exc,
            )

        self._logger.info(
            "Irrigation service stopped.",
        )

    def _update_prediction_validation_status(
        self,
        *,
        force: bool = False,
    ) -> None:
        """
        Upload the current prediction-validation queue status.

        Status is uploaded periodically or immediately when
        the queue state changes.
        """

        current_time = time.monotonic()

        if (
            not force
            and (
                current_time
                - self._last_prediction_validation_status_update
                < self._prediction_validation_status_interval_seconds
            )
        ):
            return

        try:
            status = (
                self._prediction_validation_queue.get_status()
            )

            self._firebase.update_prediction_validation_status(
                status,
            )

            self._last_prediction_validation_status_update = (
                current_time
            )

            self._logger.debug(
                "Prediction validation status uploaded. "
                "status=%s pending=%d remaining=%d",
                status.validation_status,
                status.pending_count,
                status.remaining_seconds,
            )

        except Exception as exc:
            self._logger.exception(
                "Prediction validation status update failed: %s",
                exc,
            )
