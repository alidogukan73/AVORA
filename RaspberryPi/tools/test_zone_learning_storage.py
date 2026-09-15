"""Verify narrow Firebase reads and zone/sensor learning isolation."""

from __future__ import annotations

import logging
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from core.firebase_service import FirebaseService


class FakeQuery:
    def __init__(self, data: dict, tracker: list[tuple]) -> None:
        self.data = data
        self.tracker = tracker
        self.field = ""
        self.expected = None
        self.limit = None

    def order_by_key(self):
        self.field = "__key__"
        return self

    def order_by_child(self, field: str):
        self.field = field
        return self

    def equal_to(self, expected):
        self.expected = expected
        return self

    def limit_to_last(self, limit: int):
        self.limit = limit
        return self

    def get(self):
        self.tracker.append((self.field, self.expected, self.limit))
        items = list(sorted(self.data.items()))
        if self.field and self.field != "__key__" and self.expected is not None:
            items = [
                (key, value)
                for key, value in items
                if isinstance(value, dict)
                and value.get(self.field) == self.expected
            ]
        if self.limit is not None:
            items = items[-self.limit:]
        return dict(items)


class FakeDeviceRef:
    def __init__(self, datasets: dict[str, dict]) -> None:
        self.datasets = datasets
        self.queries: list[tuple] = []

    def child(self, path: str):
        return FakeQuery(self.datasets.get(path, {}), self.queries)


def watering_item(zone_id: str, sensor_id: str, season_id: str) -> dict:
    return {
        "started_at": "2026-09-15T10:00:00",
        "finished_at": "2026-09-15T10:01:00",
        "duration": 60,
        "moisture_before": 30,
        "moisture_after": 36,
        "moisture_delta": 6,
        "moisture_limit": 40,
        "restart_delta": 10,
        "cooldown_seconds": 600,
        "completed": True,
        "stop_reason": "COMPLETED",
        "mode": "AUTO",
        "firmware": "2.12.0",
        "zone_id": zone_id,
        "sensor_id": sensor_id,
        "season_id": season_id,
        "season_ids": [season_id],
    }


def main() -> None:
    watering_history = {}
    for index in range(10):
        watering_history[f"2026-zone-001-{index:03d}"] = watering_item(
            "zone-001",
            "soil-001",
            "season-001",
        )
    # A noisy second zone has enough newer entries to evict zone-001 from the
    # old shared 100-record window.
    for index in range(140):
        watering_history[f"2027-zone-002-{index:03d}"] = watering_item(
            "zone-002",
            "soil-002",
            "season-002",
        )

    device_ref = FakeDeviceRef({"watering_history": watering_history})
    service = FirebaseService.__new__(FirebaseService)
    service._logger = logging.getLogger("zone-learning-storage-test")
    service._zone_config_by_sensor_id = {
        "soil-001": {
            "zone_id": "zone-001",
            "season": {
                "status": "ACTIVE",
                "active_season_id": "season-001",
            },
        }
    }
    service._watering_records_cache = {}
    service._watering_records_cache_seconds = 60.0
    service._device_ref = lambda: device_ref

    records = service.get_recent_watering_records(
        limit=30,
        zone_id="zone-001",
        sensor_id="soil-001",
    )
    assert len(records) == 10
    assert all(record.zone_id == "zone-001" for record in records)
    assert all(record.sensor_id == "soil-001" for record in records)
    assert any(query[0] == "zone_id" for query in device_ref.queries)

    query_count = len(device_ref.queries)
    cached = service.get_recent_watering_records(
        limit=30,
        zone_id="zone-001",
        sensor_id="soil-001",
    )
    assert len(cached) == 10
    assert len(device_ref.queries) == query_count

    print("[PASS] A busy zone cannot starve another zone's learning data.")
    print("[PASS] Repeated AI cycles reuse the bounded history cache.")


if __name__ == "__main__":
    main()
