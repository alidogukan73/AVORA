"""Safety and scheduling checks for optimal irrigation time planning."""

from dataclasses import replace

from controllers.optimal_irrigation_time_engine import OptimalIrrigationTimeEngine


NOW = 1_786_243_200  # 2026-08-08 08:00:00 UTC; tests use explicit local_hour.


def forecast(*slots) -> dict:
    return {
        "updated_at_epoch": NOW,
        "hourly": list(slots),
    }


def slot(hours: int, local_hour: int, **values) -> dict:
    return {
        "epoch": NOW + hours * 3600,
        "local_hour": local_hour,
        "temperature": 25,
        "rain_probability": 0,
        "rain_mm": 0,
        "wind_kmh": 8,
        "shortwave_radiation": 50,
        "is_day": 1,
        **values,
    }


def main() -> None:
    engine = OptimalIrrigationTimeEngine()

    emergency = engine.evaluate(
        forecast=forecast(slot(1, 14, temperature=39, shortwave_radiation=800)),
        moisture_deficit=15,
        now_epoch=NOW,
    )
    assert not emergency.postpone
    assert emergency.emergency
    assert emergency.recheck_before_watering
    assert emergency.reason == "TIMING_CRITICAL_DRYNESS"

    planned = engine.evaluate(
        forecast=forecast(
            slot(1, 14, temperature=38, shortwave_radiation=780),
            slot(10, 6, temperature=20, shortwave_radiation=20),
        ),
        moisture_deficit=6,
        now_epoch=NOW,
    )
    assert planned.postpone
    assert planned.recommended_at_epoch == NOW + 10 * 3600
    assert planned.reason == "TIMING_EARLY_MORNING"

    persisted = engine.evaluate(
        forecast=forecast(slot(2, 6)),
        moisture_deficit=6,
        existing_plan=planned.to_dict(),
        now_epoch=NOW + 60,
    )
    assert persisted.recommended_at_epoch == planned.recommended_at_epoch

    scoped_plan = replace(
        planned,
        scope_key="season-2026",
        sensor_id="soil-001",
    )
    same_scope = engine.evaluate(
        forecast=forecast(slot(2, 6)),
        moisture_deficit=6,
        existing_plan=scoped_plan.to_dict(),
        now_epoch=NOW + 60,
        scope_key="season-2026",
        sensor_id="soil-001",
    )
    assert same_scope.recommended_at_epoch == planned.recommended_at_epoch

    changed_scope = engine.evaluate(
        forecast=forecast(slot(2, 6)),
        moisture_deficit=6,
        existing_plan=scoped_plan.to_dict(),
        now_epoch=NOW + 60,
        scope_key="season-2027",
        sensor_id="soil-001",
    )
    assert changed_scope.recommended_at_epoch != planned.recommended_at_epoch

    due = engine.evaluate(
        forecast=forecast(slot(1, 6)),
        moisture_deficit=6,
        existing_plan=planned.to_dict(),
        now_epoch=planned.recommended_at_epoch,
    )
    assert not due.postpone
    assert due.status == "READY_FOR_RECHECK"

    mandatory_recheck = engine.evaluate(
        forecast=None,
        moisture_deficit=15,
        zone_settings={"timing_recheck_enabled": False},
        now_epoch=NOW,
    )
    assert mandatory_recheck.recheck_before_watering

    indoor = engine.evaluate(
        forecast=None,
        moisture_deficit=5,
        zone_settings={"garden_environment": "INDOOR"},
        now_epoch=NOW,
    )
    assert not indoor.postpone

    stale = engine.evaluate(
        forecast={"updated_at_epoch": NOW - 20_000, "hourly": []},
        moisture_deficit=5,
        zone_settings={"max_irrigation_defer_minutes": 0},
        now_epoch=NOW,
    )
    assert not stale.postpone
    assert stale.reason == "TIMING_MAX_DEFER_REACHED"

    print("[PASS] Optimal irrigation time safety scenarios.")


if __name__ == "__main__":
    main()
