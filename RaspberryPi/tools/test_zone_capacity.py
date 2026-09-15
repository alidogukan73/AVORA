"""Regression checks for the fixed eight-channel zone map."""

from __future__ import annotations

import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from core.zone_capacity import (
    validate_zone_configurations,
    validate_zone_valve_configurations,
)


def _zone(slot: int) -> dict:
    return {
        "enabled": True,
        "sensor_id": f"soil-{slot:03d}",
        "valve_id": f"valve-{slot:03d}",
        "valve_mode": "PHYSICAL",
        "irrigation_enabled": True,
    }


def main() -> None:
    zones = {f"zone-{slot:03d}": _zone(slot) for slot in range(1, 9)}
    sensor_map, configs = validate_zone_configurations(zones)
    assert len(sensor_map) == 8
    assert sensor_map["soil-008"] == "zone-008"
    assert configs["soil-008"]["valve_id"] == "valve-008"

    zones["zone-009"] = {
        "enabled": True,
        "sensor_id": "soil-009",
        "valve_id": "valve-009",
        "irrigation_enabled": True,
    }
    sensor_map, _ = validate_zone_configurations(zones)
    assert len(sensor_map) == 8

    valve_only = {
        "zone-008": {
            "enabled": True,
            "sensor_id": "",
            "valve_id": "valve-008",
            "valve_mode": "PHYSICAL",
        },
    }
    sensor_map, _ = validate_zone_configurations(valve_only)
    valve_configs = validate_zone_valve_configurations(valve_only)
    assert sensor_map == {}
    assert valve_configs["zone-008"]["valve_id"] == "valve-008"

    duplicate_sensor = {
        "zone-001": _zone(1),
        "zone-002": {**_zone(2), "sensor_id": "soil-001"},
    }
    sensor_map, _ = validate_zone_configurations(duplicate_sensor)
    assert sensor_map == {"soil-001": "zone-001"}

    duplicate_valve = {
        "zone-001": _zone(1),
        "zone-002": {**_zone(2), "valve_id": "valve-001"},
    }
    sensor_map, configs = validate_zone_configurations(duplicate_valve)
    assert len(sensor_map) == 2
    assert configs["soil-002"]["valve_id"] == ""
    assert configs["soil-002"]["irrigation_enabled"] is False

    hardware_pending = {
        "zone-006": {
            "enabled": True,
            "sensor_id": "soil-006",
            "valve_id": "",
            "irrigation_enabled": True,
        }
    }
    _, configs = validate_zone_configurations(hardware_pending)
    assert configs["soil-006"]["irrigation_enabled"] is False

    inactive = {
        "zone-007": {
            **_zone(7),
            "enabled": False,
            "lifecycle_status": "INACTIVE",
        }
    }
    sensor_map, _ = validate_zone_configurations(inactive)
    assert sensor_map == {}

    inactive_duplicate = {
        "zone-004": {
            **_zone(4),
            "sensor_id": "soil-006",
        },
        "zone-006": {
            **_zone(6),
            "sensor_id": "soil-006",
            "enabled": False,
            "lifecycle_status": "INACTIVE",
        },
    }
    sensor_map, _ = validate_zone_configurations(inactive_duplicate)
    assert sensor_map == {"soil-006": "zone-004"}

    print("[PASS] Eight-zone capacity and fail-safe mapping rules verified.")


if __name__ == "__main__":
    main()

