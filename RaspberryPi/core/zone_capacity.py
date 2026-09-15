"""Safety validation for the fixed eight-channel garden hardware map."""

from __future__ import annotations

import re
from typing import Any, Callable

MAX_ZONES = 8
_ZONE_PATTERN = re.compile(r"^zone-00[1-8]$")
_SENSOR_PATTERN = re.compile(r"^soil-00[1-8]$")
_VALVE_PATTERN = re.compile(r"^valve-00[1-8]$")


def validate_zone_configurations(
    zones: object,
    warn: Callable[[str, object], None] | None = None,
) -> tuple[dict[str, str], dict[str, dict[str, Any]]]:
    """Return deterministic, fail-safe sensor maps for valid active zones."""

    if not isinstance(zones, dict):
        return {}, {}

    sensor_to_zone: dict[str, str] = {}
    config_by_sensor: dict[str, dict[str, Any]] = {}
    used_valves: set[str] = set()

    for raw_zone_id in sorted(zones):
        zone_id = str(raw_zone_id).strip()
        zone = zones.get(raw_zone_id)
        if not _ZONE_PATTERN.fullmatch(zone_id) or not isinstance(zone, dict):
            _warn(warn, "Invalid garden zone ignored: %s", zone_id)
            continue
        if not bool(zone.get("enabled", True)):
            continue
        if str(zone.get("lifecycle_status", "")).strip().upper() == "INACTIVE":
            continue

        sensor_id = str(zone.get("sensor_id", "")).strip()
        if not _SENSOR_PATTERN.fullmatch(sensor_id):
            if sensor_id:
                _warn(warn, "Invalid zone sensor mapping ignored: %s", sensor_id)
            continue
        if sensor_id in sensor_to_zone:
            _warn(warn, "Duplicate zone sensor mapping ignored: %s", sensor_id)
            continue

        safe_zone = {**zone, "zone_id": zone_id}
        valve_id = str(zone.get("valve_id", "")).strip()
        if valve_id:
            if not _VALVE_PATTERN.fullmatch(valve_id):
                _warn(warn, "Invalid zone valve mapping blocked: %s", valve_id)
                valve_id = ""
            elif valve_id in used_valves:
                _warn(warn, "Duplicate zone valve mapping blocked: %s", valve_id)
                valve_id = ""
            else:
                used_valves.add(valve_id)

        if not valve_id:
            safe_zone["valve_id"] = ""
            safe_zone["valve_mode"] = "SIMULATION"
            safe_zone["irrigation_enabled"] = False

        sensor_to_zone[sensor_id] = zone_id
        config_by_sensor[sensor_id] = safe_zone

    return sensor_to_zone, config_by_sensor


def validate_zone_valve_configurations(
    zones: object,
    warn: Callable[[str, object], None] | None = None,
) -> dict[str, dict[str, Any]]:
    """Return active, uniquely assigned valve channels without requiring a sensor.

    Manual watering and valve commissioning must remain available before a
    moisture sensor is installed.  Automatic irrigation continues to use
    ``validate_zone_configurations`` and therefore still requires a valid,
    unique sensor channel.
    """

    if not isinstance(zones, dict):
        return {}

    config_by_zone: dict[str, dict[str, Any]] = {}
    used_valves: set[str] = set()

    for raw_zone_id in sorted(zones):
        zone_id = str(raw_zone_id).strip()
        zone = zones.get(raw_zone_id)
        if not _ZONE_PATTERN.fullmatch(zone_id) or not isinstance(zone, dict):
            continue
        if not bool(zone.get("enabled", True)):
            continue
        if str(zone.get("lifecycle_status", "")).strip().upper() == "INACTIVE":
            continue

        valve_id = str(zone.get("valve_id", "")).strip()
        if not _VALVE_PATTERN.fullmatch(valve_id):
            if valve_id:
                _warn(warn, "Invalid manual valve mapping ignored: %s", valve_id)
            continue
        if valve_id in used_valves:
            _warn(warn, "Duplicate manual valve mapping ignored: %s", valve_id)
            continue

        used_valves.add(valve_id)
        config_by_zone[zone_id] = {**zone, "zone_id": zone_id}

    return config_by_zone


def _warn(
    warn: Callable[[str, object], None] | None,
    message: str,
    value: object,
) -> None:
    if warn is not None:
        warn(message, value)

