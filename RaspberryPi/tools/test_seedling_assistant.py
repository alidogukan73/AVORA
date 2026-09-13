"""Offline tests for the seedling telemetry contract and advisor."""
from __future__ import annotations
import json
from datetime import datetime, timezone
from unittest.mock import patch
from controllers.seedling_assistant_engine import SeedlingAssistantEngine, SeedlingTelemetry
from core import firebase_service as firebase_module
from core.firebase_service import FirebaseService
from hardware.seedling_mqtt_bridge import SeedlingMqttBridge


def sample(**changes) -> dict:
    value = {
        "node_id": "seedling-001", "firmware": "1.0.0",
        "air_temperature_c": 23.4, "air_humidity_pct": 66.0,
        "root_temperature_c": 22.8, "soil_moisture_available": True,
        "soil_moisture_pct": 58.0,
        "soil_raw": 14200, "light_lux": 14000.0,
        "rssi": -58, "uptime_seconds": 120,
    }
    value.update(changes)
    return value


class Sink:
    def __init__(self) -> None:
        self.calls = []
        self.online_calls = []

    def update_seedling_snapshot(
        self, node_id: str, telemetry: dict, recommendation: dict
    ) -> None:
        self.calls.append((node_id, telemetry, recommendation))

    def update_seedling_online(self, node_id: str, online: bool) -> None:
        self.online_calls.append((node_id, online))


class SubscriptionClient:
    def __init__(self) -> None:
        self.topics = []

    def subscribe(self, topic: str, qos: int = 0) -> tuple[int, int]:
        self.topics.append((topic, qos))
        return 0, len(self.topics)


class Reference:
    def __init__(self, values: dict, path: tuple[str, ...] = ()) -> None:
        self.values = values
        self.path = path

    def child(self, name: str) -> "Reference":
        return Reference(self.values, self.path + tuple(str(name).split("/")))

    def update(self, values: dict) -> None:
        node = self.values
        for name in self.path:
            node = node.setdefault(name, {})
        node.update(values)


def assert_raises(callable_value) -> None:
    try:
        callable_value()
    except ValueError:
        return
    raise AssertionError("Expected ValueError")


def main() -> None:
    engine = SeedlingAssistantEngine()
    healthy = engine.evaluate(SeedlingTelemetry.from_mapping(sample()))
    assert healthy.severity == "GOOD"
    assert healthy.score == 100
    assert healthy.advisory_only is True

    without_ads = engine.evaluate(SeedlingTelemetry.from_mapping(sample(
        soil_moisture_available=False,
    )))
    assert without_ads.severity == "WARNING"
    assert without_ads.score == 84
    assert "ADS1115" in without_ads.action
    partial = sample(soil_moisture_available=False)
    partial.pop("soil_moisture_pct")
    partial.pop("soil_raw")
    assert SeedlingTelemetry.from_mapping(partial).soil_moisture_available is False

    dry = engine.evaluate(SeedlingTelemetry.from_mapping(sample(
        soil_moisture_pct=18,
    )))
    assert dry.severity == "CRITICAL"
    assert dry.score == 72
    assert "Elle kontrol" in dry.action

    wet_hot = engine.evaluate(SeedlingTelemetry.from_mapping(sample(
        air_temperature_c=37, soil_moisture_pct=92,
    )))
    assert wet_hot.severity == "CRITICAL"
    assert wet_hot.score == 48

    daytime_dark = engine.evaluate(
        SeedlingTelemetry.from_mapping(sample(light_lux=100)),
        observed_at=datetime(2026, 9, 8, 12, tzinfo=timezone.utc),
    )
    assert "daha aydınlık" in daytime_dark.action
    assert daytime_dark.score == 88

    nighttime_dark = engine.evaluate(
        SeedlingTelemetry.from_mapping(sample(light_lux=0)),
        observed_at=datetime(2026, 9, 8, 22, tzinfo=timezone.utc),
    )
    assert "daha aydınlık" not in nighttime_dark.action
    assert nighttime_dark.score == 100
    assert "Gece ışık seviyesi değerlendirmeye alınmadı." in nighttime_dark.message

    assert_raises(lambda: SeedlingTelemetry.from_mapping(sample(air_humidity_pct=101)))
    assert_raises(lambda: SeedlingTelemetry.from_mapping(sample(
        soil_moisture_available="false",
    )))
    assert_raises(lambda: SeedlingTelemetry.from_mapping(
        sample(), expected_node_id="seedling-002",
    ))

    sink = Sink()
    bridge = SeedlingMqttBridge(sink, "127.0.0.1", 1883)
    reading = bridge.handle_payload(
        "avora/seedling/seedling-001/telemetry",
        json.dumps(sample()).encode("utf-8"),
    )
    assert reading.node_id == "seedling-001"
    assert len(sink.calls) == 1
    assert sink.calls[0][2]["advisory_only"] is True
    assert_raises(lambda: bridge.handle_payload(
        "avora/seedling/seedling-002/telemetry", json.dumps(sample()).encode("utf-8"),
    ))
    assert_raises(lambda: bridge.handle_payload(
        "avora/seedling/seedling-001/telemetry", b"not-json",
    ))

    assert bridge.handle_status_payload(
        "avora/seedling/seedling-001/status", b"offline",
    ) is False
    assert sink.online_calls == [("seedling-001", False)]
    assert bridge.handle_status_payload(
        "avora/seedling/seedling-001/status", b" online\n",
    ) is True
    assert sink.online_calls[-1] == ("seedling-001", True)
    assert_raises(lambda: bridge.handle_status_payload(
        "avora/seedling/seedling-002/status", b"online",
    ))
    assert_raises(lambda: bridge.handle_status_payload(
        "avora/seedling/seedling-001/status", b"unknown",
    ))
    assert_raises(lambda: SeedlingMqttBridge(
        Sink(), "127.0.0.1", 1883, allowed_node_ids=(),
    ))
    assert_raises(lambda: SeedlingMqttBridge(
        Sink(), "127.0.0.1", 1883, allowed_node_ids=("invalid/node",),
    ))

    subscription_client = SubscriptionClient()
    bridge._on_connect(subscription_client, None, None, 0, None)
    assert subscription_client.topics == [
        ("avora/seedling/+/telemetry", 0),
        ("avora/seedling/+/status", 0),
    ]

    with (
        patch.object(bridge._client, "connect_async", return_value=None) as connect,
        patch.object(bridge._client, "loop_start", return_value=0) as loop_start,
        patch.object(bridge._client, "disconnect") as disconnect,
        patch.object(bridge._client, "loop_stop") as loop_stop,
    ):
        bridge.start()
        bridge.start()
        connect.assert_called_once_with("127.0.0.1", 1883, keepalive=60)
        loop_start.assert_called_once_with()
        bridge.stop()
        disconnect.assert_called_once_with()
        loop_stop.assert_called_once_with()

    custom_sink = Sink()
    custom_bridge = SeedlingMqttBridge(
        custom_sink,
        "127.0.0.1",
        1883,
        allowed_node_ids=("seedling-002",),
    )
    custom_sample = sample(node_id="seedling-002")
    custom_bridge.handle_payload(
        "avora/seedling/seedling-002/telemetry",
        json.dumps(custom_sample).encode("utf-8"),
    )
    assert len(custom_sink.calls) == 1
    assert_raises(lambda: custom_bridge.handle_payload(
        "avora/seedling/seedling-001/telemetry",
        json.dumps(sample()).encode("utf-8"),
    ))

    firebase_values: dict = {}
    firebase = FirebaseService.__new__(FirebaseService)
    firebase._device_ref = lambda: Reference(firebase_values)
    with patch.object(firebase_module.time, "time", return_value=1_789_300_000):
        firebase.update_seedling_online("Seedling-001", False)
    assert firebase_values == {
        "seedling": {
            "nodes": {
                "seedling-001": {
                    "latest": {
                        "online": False,
                        "status_updated_at_epoch": 1_789_300_000,
                    },
                },
            },
        },
    }
    assert_raises(lambda: firebase.update_seedling_online("bad/node", True))
    assert_raises(lambda: firebase.update_seedling_online(
        "seedling-001", "offline",
    ))
    print("[PASS] Seedling assistant telemetry and safety scenarios.")


if __name__ == "__main__":
    main()
