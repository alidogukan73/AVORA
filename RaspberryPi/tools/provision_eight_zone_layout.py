"""Provision the fixed eight-zone garden inventory without arming new valves.

The command is a dry run unless ``--apply`` is supplied.  Applying creates a
JSON backup before one atomic Firebase update.  Existing zone, season and crop
history is preserved; only the current hardware inventory fields are aligned.
"""

from __future__ import annotations

import argparse
import copy
import json
import sys
import time
import uuid
from pathlib import Path

import firebase_admin
from firebase_admin import credentials, db


ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from core.config import AppConfig, FirebaseConfig  # noqa: E402


def _text(value: object) -> str:
    return str(value or "").strip()


def _closed_season(now: int) -> dict[str, object]:
    return {
        "active_season_id": "",
        "active_season_ids": None,
        "status": "CLOSED",
        "label": "",
        "started_at_epoch": 0,
        "ended_at_epoch": 0,
        "include_legacy_records": False,
        "updated_at_epoch": now,
    }


def _provisioned_zone(slot: int, existing: object, now: int) -> dict:
    current = copy.deepcopy(existing) if isinstance(existing, dict) else {}
    zone_id = f"zone-{slot:03d}"
    valve_id = f"valve-{slot:03d}"

    # Keep working sensor assignments until their physical probes are moved.
    # New/manual-only valve channels intentionally start without a sensor.
    sensor_id = _text(current.get("sensor_id"))
    if not bool(current.get("enabled", False)):
        sensor_id = ""

    current.update({
        "zone_id": zone_id,
        "area_id": _text(current.get("area_id")) or f"area-{uuid.uuid4()}",
        "area_name": _text(current.get("area_name")) or f"{slot}. Bölge",
        "location_name": _text(current.get("location_name")),
        "area_icon": _text(current.get("area_icon")) or "🌿",
        "area_color": _text(current.get("area_color")) or "#2E7D32",
        "name": _text(current.get("name")),
        "plant_type": _text(current.get("plant_type")),
        "emoji": _text(current.get("emoji")) or "🌿",
        "sensor_id": sensor_id,
        "sensor_enabled": bool(sensor_id),
        "valve_id": valve_id,
        "valve_type": "SOLENOID",
        "valve_mode": "PHYSICAL",
        "enabled": True,
        # Automatic watering stays off until sensor calibration and an active
        # season are confirmed for this exact physical area.
        "irrigation_enabled": bool(current.get("irrigation_enabled", False))
        if slot == 1 else False,
        "order": slot,
        "lifecycle_status": "ACTIVE" if slot == 1 else "HARDWARE_PENDING",
        "archived_at_epoch": 0,
        "updated_at_epoch": now,
        "low_moisture_alert_enabled": bool(
            current.get("low_moisture_alert_enabled", True)
        ),
        "watering_complete_alert_enabled": bool(
            current.get("watering_complete_alert_enabled", True)
        ),
        "moisture_limit": int(current.get("moisture_limit", 40) or 40),
        "pump_duration": int(current.get("pump_duration", 10) or 10),
        "cooldown_seconds": int(current.get("cooldown_seconds", 600) or 600),
        "restart_delta": int(current.get("restart_delta", 10) or 10),
    })
    current.setdefault("created_at_epoch", now)
    if not isinstance(current.get("season"), dict):
        current["season"] = _closed_season(now)

    irrigation_status = current.get("irrigation_status")
    if not isinstance(irrigation_status, dict):
        irrigation_status = {}
    irrigation_status.update({
        "hardware_ready": slot == 1,
        "watering_active": False,
        "selected_for_watering": False,
        "queue_position": 0,
    })
    current["irrigation_status"] = irrigation_status
    return current


def _ensure_safe(device: dict) -> None:
    status = device.get("status") if isinstance(device, dict) else {}
    commands = device.get("commands") if isinstance(device, dict) else {}
    manual = commands.get("manual_watering") if isinstance(commands, dict) else {}
    zone_test = commands.get("zone_test") if isinstance(commands, dict) else {}
    if bool((status or {}).get("relay")) or bool((status or {}).get("valve_open")):
        raise RuntimeError("Pump or valve is active; provisioning was blocked.")
    if bool((manual or {}).get("active")) or bool((zone_test or {}).get("active")):
        raise RuntimeError("A manual/test command is active; provisioning was blocked.")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--backup-dir", default=str(ROOT.parent / ".deploy-backups"))
    args = parser.parse_args()

    credential_path = ROOT / FirebaseConfig.CREDENTIALS_FILE
    firebase_admin.initialize_app(
        credentials.Certificate(credential_path),
        {"databaseURL": FirebaseConfig.DATABASE_URL},
    )
    device_ref = db.reference(f"devices/{AppConfig.DEVICE_ID}")
    device = device_ref.get() or {}
    _ensure_safe(device)
    zones = device.get("zones") if isinstance(device, dict) else {}
    zones = zones if isinstance(zones, dict) else {}
    now = int(time.time())
    provisioned = {
        f"zone-{slot:03d}": _provisioned_zone(
            slot,
            zones.get(f"zone-{slot:03d}"),
            now,
        )
        for slot in range(1, 9)
    }

    summary = {
        zone_id: {
            "area_name": value["area_name"],
            "sensor_id": value["sensor_id"],
            "valve_id": value["valve_id"],
            "lifecycle_status": value["lifecycle_status"],
            "irrigation_enabled": value["irrigation_enabled"],
        }
        for zone_id, value in provisioned.items()
    }
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    if not args.apply:
        print("DRY RUN: no Firebase data changed.")
        return

    backup_dir = Path(args.backup_dir)
    backup_dir.mkdir(parents=True, exist_ok=True)
    backup_path = backup_dir / f"{now}-eight-zone-before.json"
    backup_path.write_text(
        json.dumps(zones, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    device_ref.update({f"zones/{key}": value for key, value in provisioned.items()})
    print(f"APPLIED: backup={backup_path}")


if __name__ == "__main__":
    main()
