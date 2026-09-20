"""End-to-end checks for persistent, zone-scoped irrigation learning."""

from __future__ import annotations

import logging
import sys
import types
from datetime import datetime, timedelta
from pathlib import Path
from unittest.mock import patch


ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

try:
    import RPi.GPIO  # noqa: F401
except ModuleNotFoundError:
    rpi = types.ModuleType("RPi")
    gpio = types.ModuleType("RPi.GPIO")
    rpi.GPIO = gpio
    sys.modules["RPi"] = rpi
    sys.modules["RPi.GPIO"] = gpio

from tools.hardware_test_stubs import install_hardware_import_stubs

install_hardware_import_stubs()

from controllers.multi_zone_decision_engine import MultiZoneDecisionEngine
from models.sensor_reading import SensorReading
from models.watering_record import WateringRecord
from services.irrigation_service import IrrigationService


def zone(zone_id: str, *, active: bool = True) -> dict:
    return {
        "zone_id": zone_id,
        "season": {
            "status": "ACTIVE" if active else "CLOSED",
            "active_season_id": "",
            "active_season_ids": (
                {f"season-{zone_id}": True} if active else {}
            ),
        },
    }


class FakeFirebase:
    def __init__(self) -> None:
        self.configs = {
            "soil-001": zone("zone-001"),
            "soil-002": zone("zone-002"),
        }
        self.load_calls: list[tuple[str, str, int]] = []
        self.saved = []
        self.watering_records = []

    def get_all_zone_configs_by_sensor(self) -> dict[str, dict]:
        return self.configs

    def load_recent_sensor_history(
        self,
        *,
        limit: int,
        sensor_id: str,
        zone_id: str,
    ) -> list[tuple[int, str]]:
        self.load_calls.append((zone_id, sensor_id, limit))
        now = datetime.now()
        baseline = 70 if sensor_id == "soil-001" else 55
        return [
            (
                (
                    baseline - (index // 10)
                    if sensor_id == "soil-001"
                    else baseline
                ),
                (now - timedelta(minutes=5 * (99 - index))).isoformat(),
            )
            for index in range(100)
        ]

    def save_sensor_history(self, entry) -> None:
        self.saved.append(entry)

    def get_recent_watering_records(self, **kwargs) -> list:
        zone_id = str(kwargs.get("zone_id", ""))
        sensor_id = str(kwargs.get("sensor_id", ""))
        return [
            record
            for record in self.watering_records
            if (
                (not zone_id or record.zone_id == zone_id)
                and (not sensor_id or record.sensor_id == sensor_id)
            )
        ]


def reading(sensor_id: str, moisture: int) -> SensorReading:
    return SensorReading(
        raw=1000,
        voltage=1.5,
        moisture=moisture,
        sensor_id=sensor_id,
    )


def main() -> None:
    firebase = FakeFirebase()
    service = IrrigationService.__new__(IrrigationService)
    service._firebase = firebase
    service._multi_zone_engine = MultiZoneDecisionEngine()
    service._logger = logging.getLogger("zone-learning-test")

    service._restore_zone_learning_histories()
    assert set(firebase.load_calls) == {
        ("zone-001", "soil-001", 100),
        ("zone-002", "soil-002", 100),
    }
    first = service._multi_zone_engine.get_learning_trend(
        zone_id="zone-001",
        sensor_id="soil-001",
    )
    second = service._multi_zone_engine.get_learning_trend(
        zone_id="zone-002",
        sensor_id="soil-002",
    )
    assert first.sample_count == 100
    assert second.sample_count == 100
    assert first.change_per_minute < 0
    assert second.change_per_minute == 0

    finished = datetime.now() - timedelta(minutes=60)
    firebase.watering_records = [
        WateringRecord(
            started_at=(finished - timedelta(seconds=10)).isoformat(),
            finished_at=finished.isoformat(),
            duration=10,
            moisture_before=35,
            moisture_after=45,
            moisture_delta=10,
            moisture_limit=40,
            restart_delta=10,
            cooldown_seconds=600,
            completed=True,
            stop_reason="COMPLETED",
            mode="AUTO",
            firmware="2.12.2",
            zone_id="zone-001",
            sensor_id="soil-001",
        )
    ]
    after_watering = IrrigationService.__new__(IrrigationService)
    after_watering._firebase = firebase
    after_watering._multi_zone_engine = MultiZoneDecisionEngine()
    after_watering._pending_watering_measurements = []
    after_watering._logger = logging.getLogger("zone-cutoff-test")
    after_watering._restore_zone_learning_histories()
    post_watering_trend = after_watering._multi_zone_engine.get_learning_trend(
        zone_id="zone-001",
        sensor_id="soil-001",
    )
    # The fake history creates its own ``now`` a few milliseconds after the
    # watering cutoff is calculated, so the boundary sample may be included.
    # In either case, no pre-watering history may survive the restore.
    assert 10 <= post_watering_trend.sample_count <= 11
    assert post_watering_trend.first_moisture >= 61
    assert after_watering._multi_zone_engine.get_learning_trend(
        zone_id="zone-002",
        sensor_id="soil-002",
    ).sample_count == 100
    firebase.watering_records = []

    sampler = IrrigationService.__new__(IrrigationService)
    sampler._firebase = firebase
    sampler._multi_zone_engine = MultiZoneDecisionEngine()
    sampler._logger = logging.getLogger("zone-sampler-test")
    sampler._last_sensor_history_update = 0.0
    sampler._sensor_history_interval_seconds = 300.0
    sampler._last_zone_ai_update = 10.0
    firebase.saved.clear()
    firebase.configs["soil-002"] = zone("zone-002", active=False)

    with patch(
        "services.irrigation_service.time.monotonic",
        return_value=600.0,
    ):
        sampler._save_zone_sensor_histories_if_needed(
            readings={
                "soil-001": reading("soil-001", 48),
                "soil-002": reading("soil-002", 39),
            }
        )
    assert len(firebase.saved) == 1
    assert firebase.saved[0].zone_id == "zone-001"
    assert firebase.saved[0].sensor_id == "soil-001"
    assert sampler._last_zone_ai_update == 0.0

    print("[PASS] All zone learning histories restore independently.")
    print("[PASS] Only active-season zones receive learning samples.")


if __name__ == "__main__":
    main()
