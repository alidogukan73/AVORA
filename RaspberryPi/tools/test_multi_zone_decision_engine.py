"""
Verify that zone sensor histories remain independent.
"""

from __future__ import annotations

import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from controllers.multi_zone_decision_engine import (
    MultiZoneDecisionEngine,
)
from controllers.zone_irrigation_scheduler import (
    ZoneIrrigationScheduler,
)
from models.command_state import CommandState
from models.moisture_history import MoistureSample
from models.sensor_reading import SensorReading


def main() -> None:
    engine = MultiZoneDecisionEngine()
    scheduler = ZoneIrrigationScheduler()
    commands = CommandState(
        moisture_limit=40,
        pump_duration=10,
        cooldown_seconds=600,
        restart_delta=10,
    )

    latest = {}
    for moisture in (31, 30, 31, 29, 30):
        latest["zone-001"] = engine.evaluate(
            zone_id="zone-001",
            valve_id="valve-001",
            order=1,
            irrigation_enabled=True,
            reading=SensorReading(
                raw=0,
                voltage=0.0,
                moisture=moisture,
                sensor_id="soil-001",
            ),
            commands=commands,
            cooldown_active=False,
        )

    for moisture in (55, 54, 55, 54):
        latest["zone-002"] = engine.evaluate(
            zone_id="zone-002",
            valve_id="valve-002",
            order=2,
            irrigation_enabled=True,
            reading=SensorReading(
                raw=0,
                voltage=0.0,
                moisture=moisture,
                sensor_id="soil-002",
            ),
            commands=commands,
            cooldown_active=False,
        )

    assert latest["zone-001"].decision.should_water
    assert not latest["zone-002"].decision.should_water
    assert (
        latest["zone-002"].decision.reason
        == "INSUFFICIENT_SENSOR_SAMPLES"
    )

    selected = scheduler.select([
        result.candidate
        for result in latest.values()
    ])
    assert selected is not None
    assert selected.zone_id == "zone-001"

    # Long-term learning samples are zone/sensor scoped and never seed the
    # decision engine that is allowed to authorize physical watering.
    learning_engine = MultiZoneDecisionEngine()
    for index in range(20):
        learning_engine.observe_for_learning(
            zone_id="zone-001",
            reading=SensorReading(
                raw=0,
                voltage=0.0,
                moisture=60 - index,
                sensor_id="soil-001",
            ),
            timestamp=1000.0 + index * 300.0,
        )
        learning_engine.observe_for_learning(
            zone_id="zone-002",
            reading=SensorReading(
                raw=0,
                voltage=0.0,
                moisture=70,
                sensor_id="soil-002",
            ),
            timestamp=1000.0 + index * 300.0,
        )

    zone_one_trend = learning_engine.get_learning_trend(
        zone_id="zone-001",
        sensor_id="soil-001",
    )
    zone_two_trend = learning_engine.get_learning_trend(
        zone_id="zone-002",
        sensor_id="soil-002",
    )
    assert zone_one_trend.sample_count == 20
    assert zone_one_trend.duration_seconds == 5700.0
    assert zone_one_trend.change_per_minute < 0
    assert zone_two_trend.sample_count == 20
    assert zone_two_trend.change_per_minute == 0

    # Reusing a sensor ID in another zone cannot inherit the first zone's
    # learning profile.
    reassigned = learning_engine.get_learning_trend(
        zone_id="zone-003",
        sensor_id="soil-001",
    )
    assert reassigned.sample_count == 0

    restored_engine = MultiZoneDecisionEngine()
    restored = restored_engine.restore_learning_history(
        zone_id="zone-001",
        sensor_id="soil-001",
        samples=[
            MoistureSample(
                moisture=60 - index,
                timestamp=1000.0 + index * 300.0,
            )
            for index in range(20)
        ],
    )
    assert restored == 20
    restored_decision = restored_engine.evaluate(
        zone_id="zone-001",
        valve_id="valve-001",
        order=1,
        irrigation_enabled=True,
        reading=SensorReading(
            raw=0,
            voltage=0.0,
            moisture=20,
            sensor_id="soil-001",
        ),
        commands=commands,
        cooldown_active=False,
    )
    assert restored_decision.decision.reason == "INSUFFICIENT_SENSOR_SAMPLES"

    print(
        "[PASS] Multi-zone histories and selection are independent.",
    )
    print("[PASS] Zone learning is persistent, isolated and non-actuating.")


if __name__ == "__main__":
    main()
