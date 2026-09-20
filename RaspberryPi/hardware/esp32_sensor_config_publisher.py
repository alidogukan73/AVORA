"""Publish retained ESP32 soil-sensor configuration over MQTT."""

from __future__ import annotations

import logging
import threading

import paho.mqtt.client as mqtt

from hardware.mqtt_security import configure_mqtt_credentials


logger = logging.getLogger(__name__)


class Esp32SensorConfigPublisher:
    """Bridge approved Firebase sensor settings to the ESP32 MQTT client."""

    def __init__(
        self,
        broker: str,
        port: int,
        client_id: str = "avora-pi-sensor-config",
        username: str | None = None,
        password: str | None = None,
    ) -> None:
        self._broker = broker
        self._port = port
        self._started = False
        self._lock = threading.Lock()
        self._client = mqtt.Client(
            callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
            client_id=client_id,
            protocol=mqtt.MQTTv311,
        )
        self._authenticated = configure_mqtt_credentials(
            self._client,
            username,
            password,
        )
        self._client.reconnect_delay_set(min_delay=1, max_delay=30)

    def start(self) -> None:
        """Start the persistent MQTT publishing connection."""
        with self._lock:
            if self._started:
                return
            self._started = True

        try:
            self._client.connect(self._broker, self._port, keepalive=60)
            self._client.loop_start()
            logger.info(
                "ESP32 sensor configuration publisher started. broker=%s:%s authenticated=%s",
                self._broker,
                self._port,
                self._authenticated,
            )
        except Exception:
            with self._lock:
                self._started = False
            logger.exception("ESP32 sensor configuration MQTT connection failed.")
            raise

    def stop(self) -> None:
        """Stop the MQTT connection safely."""
        with self._lock:
            if not self._started:
                return
            self._started = False

        try:
            self._client.disconnect()
        finally:
            self._client.loop_stop()

    def publish(
        self,
        sensor_id: str,
        enabled: bool,
        dry_raw: int,
        wet_raw: int,
    ) -> None:
        """Publish retained enable and calibration values for one sensor."""
        if not sensor_id.replace("-", "").replace("_", "").isalnum():
            raise ValueError("Sensor ID contains unsupported characters.")
        if dry_raw <= wet_raw:
            raise ValueError("Dry calibration must be greater than wet calibration.")

        self.start()
        base_topic = "avora/config/esp32"
        enabled_result = self._client.publish(
            f"{base_topic}/sensors/{sensor_id}",
            "1" if enabled else "0",
            qos=1,
            retain=True,
        )
        calibration_result = self._client.publish(
            f"{base_topic}/calibration/{sensor_id}",
            f"{dry_raw},{wet_raw}",
            qos=1,
            retain=True,
        )

        if (
            enabled_result.rc != mqtt.MQTT_ERR_SUCCESS
            or calibration_result.rc != mqtt.MQTT_ERR_SUCCESS
        ):
            raise RuntimeError("ESP32 sensor configuration could not be queued.")
