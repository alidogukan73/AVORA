"""Checks for exact zone/sensor/season prediction history persistence."""

from __future__ import annotations

from datetime import datetime

from core.firebase_service import FirebaseService
from models.moisture_prediction import MoisturePrediction


class _Logger:
    def info(self, *_args, **_kwargs) -> None:
        return None

    def debug(self, *_args, **_kwargs) -> None:
        return None

    def warning(self, *_args, **_kwargs) -> None:
        return None


class _Ref:
    def __init__(self, store: dict, path: tuple[str, ...] = ()) -> None:
        self.store = store
        self.path = path

    def child(self, value: str):
        parts = tuple(part for part in str(value).split("/") if part)
        return _Ref(self.store, self.path + parts)

    def _parent(self, create: bool):
        node = self.store
        for part in self.path[:-1]:
            if create:
                node = node.setdefault(part, {})
            else:
                node = node.get(part, {})
        return node

    def set(self, value) -> None:
        self._parent(True)[self.path[-1]] = value

    def get(self):
        node = self.store
        for part in self.path:
            if not isinstance(node, dict) or part not in node:
                return None
            node = node[part]
        return node

    def delete(self) -> None:
        self._parent(False).pop(self.path[-1], None)


def _prediction() -> MoisturePrediction:
    now = datetime.now().isoformat()
    return MoisturePrediction(
        prediction_status="READY",
        prediction_method="LINEAR_TREND_V1",
        current_moisture=50.0,
        moisture_limit=40.0,
        drying_rate_per_minute=0.1,
        predicted_moisture_1_hour=44.0,
        predicted_moisture_3_hours=32.0,
        predicted_moisture_6_hours=14.0,
        estimated_minutes_until_limit=100.0,
        estimated_limit_reached_at=now,
        confidence=0.7,
        confidence_level="MEDIUM",
        generated_at=now,
    )


def main() -> None:
    store: dict = {}
    service = FirebaseService.__new__(FirebaseService)
    service._logger = _Logger()
    service._device_ref = lambda: _Ref(store)
    history = [(_prediction(), 43.0)]

    service.save_prediction_history(
        history,
        zone_id="zone-001",
        sensor_id="soil-001",
        season_scope="season-2026",
    )
    restored = service.load_prediction_history(
        zone_id="zone-001",
        sensor_id="soil-001",
        season_scope="season-2026",
    )
    assert len(restored) == 1
    assert restored[0][1] == 43.0
    assert service.load_prediction_history(
        zone_id="zone-001",
        sensor_id="soil-002",
        season_scope="season-2026",
    ) == []
    assert service.load_prediction_history(
        zone_id="zone-001",
        sensor_id="soil-001",
        season_scope="season-2027",
    ) == []

    service.clear_zone_prediction_history("zone-001")
    assert service.load_prediction_history(
        zone_id="zone-001",
        sensor_id="soil-001",
        season_scope="season-2026",
    ) == []
    print("[PASS] Zone prediction history stays scope-safe and persistent.")


if __name__ == "__main__":
    main()
