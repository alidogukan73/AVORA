"""
Soil moisture sensor provider module.

Provides a common interface for wired ADS1115 and
wireless MQTT soil moisture sensors.
"""

from __future__ import annotations

from pathlib import Path
import sys
import time
from typing import Literal

PROJECT_ROOT = Path(__file__).resolve().parents[1]

if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from core.logger import AppLogger
from hardware.mqtt_sensor import MqttSoilMoistureSensor
from hardware.sensor import SoilMoistureSensor
from models.sensor_reading import SensorReading


SensorMode = Literal[
    "wired",
    "mqtt",
]


class SoilMoistureSensorProvider:
    """
    Provides soil moisture readings from either:

    - wired ADS1115 connected directly to Raspberry Pi
    - wireless ESP32 sensor connected through MQTT

    Both modes return the existing SensorReading model.
    """

    def __init__(
        self,
        mode: SensorMode = "wired",
        mqtt_broker: str = "127.0.0.1",
        mqtt_port: int = 1883,
        mqtt_topic: str = (
            "avora/sensors/soil-001"
        ),
        mqtt_ads_status_topic: str = "avora/status/esp32/ads1115",
        mqtt_sensor_id: str = "soil-001",
        mqtt_stale_after_seconds: float = 30.0,
        mqtt_startup_timeout_seconds: float = 20.0,
    ) -> None:
        normalized_mode = mode.strip().lower()

        if normalized_mode not in {
            "wired",
            "mqtt",
        }:
            raise ValueError(
                "Invalid sensor mode. "
                "Expected 'wired' or 'mqtt', "
                f"received: {mode!r}"
            )

        if mqtt_startup_timeout_seconds <= 0:
            raise ValueError(
                "mqtt_startup_timeout_seconds "
                "must be greater than zero."
            )

        self._logger = AppLogger().logger

        self._mode: SensorMode = normalized_mode

        self._mqtt_startup_timeout_seconds = (
            mqtt_startup_timeout_seconds
        )

        self._wired_sensor: (
            SoilMoistureSensor | None
        ) = None

        self._mqtt_sensor: (
            MqttSoilMoistureSensor | None
        ) = None

        self._initialized = False
        self._initialized_at_monotonic = 0.0

        if self._mode == "wired":
            self._wired_sensor = (
                SoilMoistureSensor()
            )

        else:
            self._mqtt_sensor = (
                MqttSoilMoistureSensor(
                    broker=mqtt_broker,
                    port=mqtt_port,
                    topic=mqtt_topic,
                    ads_status_topic=mqtt_ads_status_topic,
                    sensor_id=mqtt_sensor_id,
                    stale_after_seconds=(
                        mqtt_stale_after_seconds
                    ),
                )
            )

    @property
    def mode(self) -> SensorMode:
        """
        Return active sensor mode.
        """

        return self._mode

    @property
    def is_initialized(self) -> bool:
        """
        Return whether the provider was initialized.
        """

        return self._initialized

    def is_waiting_for_first_reading(self) -> bool:
        """Return whether MQTT is still inside its safe startup window."""

        if (
            not self._initialized
            or self._mode != "mqtt"
            or self._mqtt_sensor is None
        ):
            return False

        latest_readings_getter = getattr(
            self._mqtt_sensor,
            "get_latest_readings",
            None,
        )
        if callable(latest_readings_getter):
            has_any_reading = bool(latest_readings_getter())
        else:
            has_any_reading = (
                self._mqtt_sensor.get_latest_reading() is not None
            )
        if has_any_reading:
            return False

        return (
            time.monotonic() - self._initialized_at_monotonic
            < self._mqtt_startup_timeout_seconds
        )

    def initialize(self) -> None:
        """
        Initialize the selected sensor source.
        """

        if self._initialized:
            self._logger.debug(
                "Sensor provider is already initialized.",
            )
            return

        if self._mode == "wired":
            self._initialize_wired_sensor()

        else:
            self._initialize_mqtt_sensor()

        self._initialized = True
        self._initialized_at_monotonic = time.monotonic()

        self._logger.info(
            "Soil moisture sensor provider initialized. "
            "mode=%s",
            self._mode,
        )

    def stop(self) -> None:
        """
        Stop the selected sensor source safely.
        """

        if (
            self._mode == "mqtt"
            and self._mqtt_sensor is not None
        ):
            self._mqtt_sensor.stop()

        self._initialized = False
        self._initialized_at_monotonic = 0.0

        self._logger.info(
            "Soil moisture sensor provider stopped. "
            "mode=%s",
            self._mode,
        )

    def read(self) -> SensorReading:
        """
        Read one complete soil moisture measurement.

        Returns the same SensorReading model for both
        wired and wireless sensor modes.
        """

        self._ensure_initialized()

        if self._mode == "wired":
            return self._read_wired_sensor()

        return self._read_mqtt_sensor()

    def get_fresh_readings(
        self,
    ) -> dict[str, SensorReading]:
        """
        Return fresh readings from every wireless sensor.
        """

        self._ensure_initialized()

        if (
            self._mode != "mqtt"
            or self._mqtt_sensor is None
        ):
            return {}

        return {
            sensor_id: SensorReading(
                raw=reading.raw,
                voltage=reading.voltage,
                moisture=reading.moisture,
                sensor_id=reading.sensor_id,
                firmware=reading.firmware,
                rssi=reading.rssi,
                uptime_seconds=reading.uptime_seconds,
            )
            for sensor_id, reading
            in self._mqtt_sensor
            .get_fresh_readings()
            .items()
        }

    def get_ads1115_status(self):
        """Return the latest ESP32 ADS1115 module status, when available."""

        self._ensure_initialized()
        if (
            self._mode != "mqtt"
            or self._mqtt_sensor is None
        ):
            return None
        return self._mqtt_sensor.get_latest_ads1115_status()

    def _initialize_wired_sensor(self) -> None:
        """
        Initialize the Raspberry Pi ADS1115 sensor.
        """

        if self._wired_sensor is None:
            raise RuntimeError(
                "Wired sensor instance is missing."
            )

        self._logger.info(
            "Initializing wired soil moisture sensor.",
        )

        self._wired_sensor.initialize()

    def _initialize_mqtt_sensor(self) -> None:
        """
        Start the MQTT sensor listener.

        The Raspberry Pi backend must remain online even if the ESP32 is
        temporarily unpowered. The normal update loop keeps actuators safe
        and suppresses outage reporting during the configured startup window.
        """

        if self._mqtt_sensor is None:
            raise RuntimeError(
                "MQTT sensor instance is missing."
            )

        self._logger.info(
            "Initializing wireless MQTT soil "
            "moisture sensor.",
        )

        self._mqtt_sensor.start()
        self._logger.info(
            "Wireless MQTT listener started. "
            "Waiting for the first sensor measurement."
        )

    def _read_wired_sensor(
        self,
    ) -> SensorReading:
        """
        Read from the Raspberry Pi ADS1115 sensor.
        """

        if self._wired_sensor is None:
            raise RuntimeError(
                "Wired sensor instance is missing."
            )

        return self._wired_sensor.read()

    def _read_mqtt_sensor(
        self,
    ) -> SensorReading:
        """
        Read the latest fresh wireless MQTT value.

        Stale data is rejected so irrigation cannot use
        an old measurement after ESP32 communication loss.
        """

        if self._mqtt_sensor is None:
            raise RuntimeError(
                "MQTT sensor instance is missing."
            )

        reading = (
            self._mqtt_sensor.get_fresh_reading()
        )

        if reading is None:
            fresh_readings_getter = getattr(
                self._mqtt_sensor,
                "get_fresh_readings",
                None,
            )
            fresh_readings = (
                fresh_readings_getter()
                if callable(fresh_readings_getter)
                else {}
            )
            if fresh_readings:
                fallback_sensor_id = sorted(fresh_readings)[0]
                reading = fresh_readings[fallback_sensor_id]

        if reading is None:
            latest = (
                self._mqtt_sensor
                .get_latest_reading()
            )

            if latest is None:
                raise RuntimeError(
                    "No wireless soil moisture "
                    "measurement has been received."
                )

            raise RuntimeError(
                "Wireless soil moisture measurement "
                "is stale. "
                f"Age={latest.age_seconds:.1f}s, "
                f"maximum={self._mqtt_sensor.stale_after_seconds:.1f}s."
            )
        return SensorReading(
            raw=reading.raw,
            voltage=reading.voltage,
            moisture=reading.moisture,

            sensor_id=reading.sensor_id,
            firmware=reading.firmware,
            rssi=reading.rssi,
            uptime_seconds=reading.uptime_seconds,
        )

    def _ensure_initialized(self) -> None:
        """
        Ensure provider initialization before reading.
        """

        if not self._initialized:
            raise RuntimeError(
                "Soil moisture sensor provider "
                "is not initialized."
            )


def main() -> None:
    """
    Run a standalone MQTT provider test.
    """

    import time

    provider = SoilMoistureSensorProvider(
        mode="mqtt",
        mqtt_broker="127.0.0.1",
        mqtt_port=1883,
        mqtt_topic=(
            "avora/sensors/soil-001"
        ),
        mqtt_sensor_id="soil-001",
        mqtt_stale_after_seconds=15.0,
        mqtt_startup_timeout_seconds=15.0,
    )

    try:
        provider.initialize()

        print()
        print(
            "Kablosuz sensör sağlayıcısı çalışıyor."
        )
        print(
            "Çıkmak için Ctrl+C kullan."
        )
        print()

        while True:
            reading = provider.read()

            print(
                f"Mode={provider.mode} "
                f"Raw={reading.raw} "
                f"Voltage={reading.voltage:.3f} V "
                f"Moisture={reading.moisture}% "
                f"Sensor={reading.sensor_id} "
                f"Firmware={reading.firmware} "
                f"RSSI={reading.rssi} "
                f"Uptime={reading.uptime_seconds}s"
            )

            time.sleep(5)

    except KeyboardInterrupt:
        print(
            "\nTest kullanıcı tarafından durduruldu."
        )

    except Exception as exc:
        print(
            f"\nSENSÖR HATASI: {exc}"
        )

    finally:
        provider.stop()


if __name__ == "__main__":
    main()
