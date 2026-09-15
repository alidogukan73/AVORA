"""Regression checks for live telemetry after a zone sensor reassignment."""

from __future__ import annotations

import time
import sys
from pathlib import Path
import types


ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

try:
    import paho.mqtt.client  # noqa: F401
except ModuleNotFoundError:
    paho = types.ModuleType("paho")
    paho_mqtt = types.ModuleType("paho.mqtt")
    mqtt_client = types.ModuleType("paho.mqtt.client")
    paho.mqtt = paho_mqtt
    paho_mqtt.client = mqtt_client
    sys.modules["paho"] = paho
    sys.modules["paho.mqtt"] = paho_mqtt
    sys.modules["paho.mqtt.client"] = mqtt_client


from core.firebase_service import FirebaseService
from models.sensor_reading import SensorReading


class FakeDeviceReference:
    def __init__(self) -> None:
        self.updates: list[dict[str, object]] = []

    def update(self, values: dict[str, object]) -> None:
        self.updates.append(dict(values))


def reading(sensor_id: str, moisture: int) -> SensorReading:
    return SensorReading(
        raw=5000,
        voltage=0.625,
        moisture=moisture,
        sensor_id=sensor_id,
        firmware="test",
        rssi=-60,
        uptime_seconds=10,
    )


def make_service(
    sensor_id: str,
    zone_id: str,
) -> tuple[FirebaseService, FakeDeviceReference]:
    service = FirebaseService.__new__(FirebaseService)
    reference = FakeDeviceReference()
    service._zone_by_sensor_id = {sensor_id: zone_id}
    service._zone_config_by_sensor_id = {
        sensor_id: {
            "zone_id": zone_id,
            "sensor_id": sensor_id,
        },
    }
    service._zone_map_refreshed_at = time.monotonic()
    service._zone_map_refresh_seconds = 10.0
    service._zone_sensor_publish_interval_seconds = 15.0
    service._published_zone_sensor_signatures = {}
    service._zone_sensor_published_at = {}
    service._device_ref = lambda: reference
    return service, reference


def main() -> None:
    initial_signature = FirebaseService._zone_sensor_map_signature(
        {
            "soil-002": "zone-002",
            "soil-001": "zone-001",
        }
    )
    same_signature = FirebaseService._zone_sensor_map_signature(
        {
            "soil-001": "zone-001",
            "soil-002": "zone-002",
        }
    )
    reassigned_signature = FirebaseService._zone_sensor_map_signature(
        {
            "soil-001": "zone-004",
            "soil-002": "zone-002",
        }
    )
    assert initial_signature == same_signature
    assert initial_signature != reassigned_signature

    service, reference = make_service(
        "soil-004",
        "zone-004",
    )

    service.update_zone_sensors(
        {"soil-004": reading("soil-004", 41)}
    )
    first_update = reference.updates[-1]
    assert first_update["zones/zone-004/moisture"] == 41
    assert "zones/zone-004/sensor_id" not in first_update

    service.update_zone_sensors(
        {"soil-004": reading("soil-004", 41)}
    )
    assert len(reference.updates) == 1

    # Even changed telemetry is cloud-throttled; local irrigation reads are
    # not routed through this upload method.
    service.update_zone_sensors(
        {"soil-004": reading("soil-004", 42)}
    )
    assert len(reference.updates) == 1

    # Simulate the next refreshed map after Patlican is reassigned to soil-006.
    service._zone_by_sensor_id = {
        "soil-006": "zone-004",
    }
    service._zone_config_by_sensor_id = {
        "soil-006": {
            "zone_id": "zone-004",
            "sensor_id": "soil-006",
        },
    }
    service.update_zone_sensors(
        {"soil-006": reading("soil-006", 67)}
    )
    second_update = reference.updates[-1]
    assert second_update["zones/zone-004/moisture"] == 67
    assert second_update["zones/zone-004/raw"] == 5000
    assert "zones/zone-004/sensor_id" not in second_update

    print("[PASS] Zone telemetry is deduplicated and preserves configuration.")


if __name__ == "__main__":
    main()
