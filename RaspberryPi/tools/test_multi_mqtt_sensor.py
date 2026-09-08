"""Scenario tests for the multi-sensor MQTT registry."""

from __future__ import annotations

import json
from types import SimpleNamespace

from hardware.mqtt_sensor import (
    MqttSoilMoistureSensor,
)


def _message(
    sensor_id: str,
    *,
    moisture: int,
    topic_sensor_id: str | None = None,
) -> SimpleNamespace:
    payload = {
        "sensor_id": sensor_id,
        "raw": 2000,
        "voltage": 0.25,
        "moisture": moisture,
        "rssi": -60,
    }

    return SimpleNamespace(
        topic=(
            "avora/sensors/"
            f"{topic_sensor_id or sensor_id}"
        ),
        payload=json.dumps(
            payload,
        ).encode("utf-8"),
    )


def _ads_status_message(
    *,
    primary_available: bool,
    secondary_available: bool,
) -> SimpleNamespace:
    return SimpleNamespace(
        topic="avora/status/esp32/ads1115",
        payload=json.dumps({
            "node_online": True,
            "primary_available": primary_available,
            "secondary_available": secondary_available,
            "rssi": -58,
            "firmware": "2.3.0",
            "uptime": 120,
        }).encode("utf-8"),
    )


def main() -> None:
    sensor = MqttSoilMoistureSensor(
        topic="avora/sensors/+",
        sensor_id="soil-001",
    )

    sensor._on_message(
        sensor._client,
        None,
        _message(
            "soil-001",
            moisture=84,
        ),
    )
    sensor._on_message(
        sensor._client,
        None,
        _message(
            "soil-002",
            moisture=61,
        ),
    )

    readings = sensor.get_fresh_readings()

    assert set(readings) == {
        "soil-001",
        "soil-002",
    }
    assert (
        sensor.get_fresh_reading()
        .moisture
        == 84
    )
    assert (
        sensor.get_fresh_reading(
            "soil-002",
        ).moisture
        == 61
    )

    sensor._on_message(
        sensor._client,
        None,
        _message(
            "soil-003",
            moisture=50,
            topic_sensor_id="soil-999",
        ),
    )

    assert (
        sensor.get_latest_reading(
            "soil-003",
        )
        is None
    )

    sensor._on_message(
        sensor._client,
        None,
        _ads_status_message(
            primary_available=False,
            secondary_available=True,
        ),
    )
    ads_status = sensor.get_latest_ads1115_status()
    assert ads_status is not None
    assert ads_status.node_online is True
    assert ads_status.primary_available is False
    assert ads_status.secondary_available is True
    assert ads_status.firmware == "2.3.0"

    invalid_status = SimpleNamespace(
        topic="avora/status/esp32/ads1115",
        payload=b'{"node_online":1}',
    )
    sensor._on_message(sensor._client, None, invalid_status)
    assert sensor.get_latest_ads1115_status() == ads_status

    print(
        "[PASS] Multi-sensor MQTT scenarios.",
    )


if __name__ == "__main__":
    main()
