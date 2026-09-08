"""MQTT-to-Firebase bridge for the isolated seedling sensor node."""
from __future__ import annotations
import json
import logging
import threading
from datetime import datetime
from typing import Any, Protocol
import paho.mqtt.client as mqtt
from controllers.seedling_assistant_engine import SeedlingAssistantEngine, SeedlingTelemetry

logger = logging.getLogger(__name__)


class SeedlingSnapshotSink(Protocol):
    def update_seedling_snapshot(
        self, node_id: str, telemetry: dict, recommendation: dict
    ) -> None: ...


class SeedlingMqttBridge:
    """Receives, validates and publishes advisory seedling data."""

    def __init__(self, sink: SeedlingSnapshotSink, broker: str, port: int,
                 topic: str = "avora/seedling/+/telemetry",
                 client_id: str = "avora-pi-seedling-assistant") -> None:
        if not broker:
            raise ValueError("MQTT broker cannot be empty.")
        if not 1 <= port <= 65535:
            raise ValueError("MQTT port is invalid.")
        if topic.count("+") != 1:
            raise ValueError("Seedling topic must contain one node wildcard.")
        self._sink, self._broker, self._port, self._topic = sink, broker, port, topic
        self._engine = SeedlingAssistantEngine()
        self._lock = threading.Lock()
        self._started = False
        self._client = mqtt.Client(
            callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
            client_id=client_id,
            protocol=mqtt.MQTTv311,
        )
        self._client.on_connect = self._on_connect
        self._client.on_disconnect = self._on_disconnect
        self._client.on_message = self._on_message
        self._client.reconnect_delay_set(min_delay=1, max_delay=30)

    @property
    def is_started(self) -> bool:
        with self._lock:
            return self._started

    def start(self) -> None:
        with self._lock:
            if self._started:
                return
            self._started = True
        try:
            self._client.connect(self._broker, self._port, keepalive=60)
            self._client.loop_start()
        except Exception:
            with self._lock:
                self._started = False
            raise
        logger.info("Seedling MQTT bridge started: %s", self._topic)

    def stop(self) -> None:
        with self._lock:
            if not self._started:
                return
            self._started = False
        try:
            self._client.disconnect()
        finally:
            self._client.loop_stop()

    def handle_payload(self, topic: str, payload: bytes) -> SeedlingTelemetry:
        node_id = self._node_id_from_topic(topic)
        try:
            value = json.loads(payload.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise ValueError("Seedling MQTT payload is not valid UTF-8 JSON.") from exc
        telemetry = SeedlingTelemetry.from_mapping(value, expected_node_id=node_id)
        recommendation = self._engine.evaluate(
            telemetry,
            observed_at=datetime.now().astimezone(),
        )
        self._sink.update_seedling_snapshot(
            telemetry.node_id, telemetry.to_dict(), recommendation.to_dict()
        )
        return telemetry

    @staticmethod
    def _node_id_from_topic(topic: str) -> str:
        parts = str(topic).split("/")
        if (len(parts) != 4 or parts[0] != "avora" or parts[1] != "seedling"
                or parts[3] != "telemetry" or not parts[2]):
            raise ValueError("Unexpected seedling MQTT topic.")
        return parts[2]

    def _on_connect(self, client: mqtt.Client, userdata: Any,
                    flags: mqtt.ConnectFlags, reason_code: mqtt.ReasonCode,
                    properties: mqtt.Properties | None) -> None:
        if reason_code != 0:
            logger.error("Seedling MQTT connection failed: %s", reason_code)
            return
        result, _ = client.subscribe(self._topic, qos=0)
        if result != mqtt.MQTT_ERR_SUCCESS:
            logger.error("Seedling MQTT subscription failed: %s", result)

    def _on_disconnect(self, client: mqtt.Client, userdata: Any,
                       disconnect_flags: mqtt.DisconnectFlags,
                       reason_code: mqtt.ReasonCode,
                       properties: mqtt.Properties | None) -> None:
        if reason_code != 0:
            logger.warning("Seedling MQTT disconnected unexpectedly: %s", reason_code)

    def _on_message(self, client: mqtt.Client, userdata: Any,
                    message: mqtt.MQTTMessage) -> None:
        try:
            self.handle_payload(message.topic, message.payload)
        except (TypeError, ValueError) as exc:
            logger.warning("Invalid seedling telemetry ignored: %s", exc)
        except Exception:
            logger.exception("Seedling telemetry could not be published.")
