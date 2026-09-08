"""Read-only check for the latest seedling snapshot in Firebase."""
from __future__ import annotations

import json
import time

import firebase_admin
from firebase_admin import credentials, db

from core.config import AppConfig, FirebaseConfig, SeedlingConfig


def main() -> int:
    app = firebase_admin.initialize_app(
        credentials.Certificate(FirebaseConfig.CREDENTIALS_FILE),
        {"databaseURL": FirebaseConfig.DATABASE_URL},
        name="avora-seedling-readonly-check",
    )
    try:
        value = db.reference(
            f"devices/{AppConfig.DEVICE_ID}/seedling/nodes/seedling-001",
            app=app,
        ).get()
    finally:
        firebase_admin.delete_app(app)

    if not isinstance(value, dict):
        print("SEEDLING_SNAPSHOT_MISSING")
        return 1

    latest = value.get("latest") if isinstance(value.get("latest"), dict) else {}
    recommendation = (
        value.get("recommendation")
        if isinstance(value.get("recommendation"), dict)
        else {}
    )
    received_at = int(latest.get("received_at_epoch", 0) or 0)
    result = {
        "node_id": latest.get("node_id"),
        "online": latest.get("online") is True,
        "age_seconds": max(0, int(time.time()) - received_at) if received_at else None,
        "soil_moisture_pct": latest.get("soil_moisture_pct"),
        "air_temperature_c": latest.get("air_temperature_c"),
        "air_humidity_pct": latest.get("air_humidity_pct"),
        "root_temperature_c": latest.get("root_temperature_c"),
        "soil_moisture_available": latest.get("soil_moisture_available", True),
        "light_lux": latest.get("light_lux"),
        "recommendation_severity": recommendation.get("severity"),
        "stale_after_seconds": SeedlingConfig.TELEMETRY_STALE_AFTER_SECONDS,
    }
    print(json.dumps(result, ensure_ascii=False, separators=(",", ":")))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
