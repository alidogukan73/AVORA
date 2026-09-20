"""MQTT-to-Firebase bridge for the isolated seedling sensor node."""
from __future__ import annotations
import json
import logging
import threading
from datetime import datetime
from typing import Any, Iterable, Protocol
import paho.mqtt.client as mqtt
from controllers.seedling_assistant_engine import SeedlingAssistantEngine, SeedlingTelemetry
from hardware.mqtt_security import configure_mqtt_credentials

logger = logging.getLogger(__name__)


class SeedlingSnapshotSink(Protocol):
    def update_seedling_snapshot(
        self, node_id: str, telemetry: dict, recommendation: dict
    ) -> None: ...

    def update_seedling_online(self, node_id: str, online: bool) -> None: ...


class SeedlingMqttBridge:
    """Receives, validates and publishes advisory seedling data."""

    def __init__(self, sink: SeedlingSnapshotSink, broker: str, port: int,
                 topic: str = "avora/seedling/+/telemetry",
                 client_id: str = "avora-pi-seedling-assistant",
                 allowed_node_ids: Iterable[str] | None = None,
                 username: str | None = None,
                 password: str | None = None) -> None:
        if not broker:
            raise ValueError("MQTT broker cannot be empty.")
        if not 1 <= port <= 65535:
            raise ValueError("MQTT port is invalid.")
        topic_parts = topic.split("/")
        if (len(topic_parts) != 4 or topic_parts[:2] != ["avora", "seedling"]
                or topic_parts[2] != "+" or topic_parts[3] != "telemetry"):
            raise ValueError(
                "Seedling topic must be avora/seedling/+/telemetry."
            )
        configured_node_ids = (
            ("seedling-001",) if allowed_node_ids is None else allowed_node_ids
        )
        if isinstance(configured_node_ids, (str, bytes)):
            raise ValueError("Seedling node allowlist must be a collection.")
        normalized_node_ids = frozenset(
            str(node_id).strip().lower()
            for node_id in configured_node_ids
            if str(node_id).strip()
        )
        if not normalized_node_ids:
            raise ValueError("At least one seedling node must be allowed.")
        if any(
            character not in "abcdefghijklmnopqrstuvwxyz0123456789-_"
            for node_id in normalized_node_ids
            for character in node_id
        ):
            raise ValueError("Seedling node allowlist contains an invalid id.")
        self._allowed_node_ids = normalized_node_ids
        self._sink, self._broker, self._port, self._topic = sink, broker, port, topic
        self._status_topic = f"{topic.rsplit('/', 1)[0]}/status"
        self._engine = SeedlingAssistantEngine()
        self._lock = threading.Lock()
        self._started = False
        self._client = mqtt.Client(
            callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
            client_id=client_id,
            protocol=mqtt.MQTTv311,
        )
        self._authenticated = configure_mqtt_credentials(
            self._client, username, password
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
            self._client.connect_async(
                self._broker, self._port, keepalive=60
            )
            result = self._client.loop_start()
            if result != mqtt.MQTT_ERR_SUCCESS:
                raise RuntimeError(
                    f"Seedling MQTT network loop could not start: {result}"
                )
        except Exception:
            with self._lock:
                self._started = False
            raise
        logger.info(
            "Seedling MQTT bridge started: %s authenticated=%s",
            self._topic,
            self._authenticated,
        )

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
        node_id = self._node_id_from_topic(topic, "telemetry")
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

    def handle_status_payload(self, topic: str, payload: bytes) -> bool:
        """Persist a retained online/offline event from an allowed sensor node."""
        node_id = self._node_id_from_topic(topic, "status")
        try:
            status = payload.decode("utf-8").strip().lower()
        except UnicodeDecodeError as exc:
            raise ValueError("Seedling MQTT status is not valid UTF-8.") from exc
        if status not in {"online", "offline"}:
            raise ValueError("Seedling MQTT status must be online or offline.")
        online = status == "online"
        self._sink.update_seedling_online(node_id, online)
        return online

    def _node_id_from_topic(self, topic: str, expected_leaf: str) -> str:
        parts = str(topic).split("/")
        if (len(parts) != 4 or parts[0] != "avora" or parts[1] != "seedling"
                or parts[3] != expected_leaf or not parts[2]):
            raise ValueError("Unexpected seedling MQTT topic.")
        node_id = parts[2].strip().lower()
        if node_id not in self._allowed_node_ids:
            raise ValueError("Seedling node is not allowed.")
        return node_id

    def _on_connect(self, client: mqtt.Client, userdata: Any,
                    flags: mqtt.ConnectFlags, reason_code: mqtt.ReasonCode,
                    properties: mqtt.Properties | None) -> None:
        if reason_code != 0:
            logger.error("Seedling MQTT connection failed: %s", reason_code)
            return
        for topic in (self._topic, self._status_topic):
            result, _ = client.subscribe(topic, qos=0)
            if result != mqtt.MQTT_ERR_SUCCESS:
                logger.error(
                    "Seedling MQTT subscription failed for %s: %s",
                    topic,
                    result,
                )

    def _on_disconnect(self, client: mqtt.Client, userdata: Any,
                       disconnect_flags: mqtt.DisconnectFlags,
                       reason_code: mqtt.ReasonCode,
                       properties: mqtt.Properties | None) -> None:
        if reason_code != 0:
            logger.warning("Seedling MQTT disconnected unexpectedly: %s", reason_code)

    def _on_message(self, client: mqtt.Client, userdata: Any,
                    message: mqtt.MQTTMessage) -> None:
        try:
            if str(message.topic).endswith("/status"):
                self.handle_status_payload(message.topic, message.payload)
            else:
                self.handle_payload(message.topic, message.payload)
        except (TypeError, ValueError) as exc:
            logger.warning("Invalid seedling MQTT message ignored: %s", exc)
        except Exception:
            logger.exception("Seedling MQTT message could not be published.")
