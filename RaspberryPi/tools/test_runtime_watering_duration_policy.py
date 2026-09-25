"""Safety tests for runtime watering-duration selection."""

from __future__ import annotations

from types import SimpleNamespace

from controllers.runtime_watering_duration_policy import (
    RuntimeWateringDurationPolicy,
)
from controllers.weather_irrigation_policy import (
    WeatherIrrigationAdjustment,
)


def recommendation(
    *,
    should_apply: bool,
    recommended: int,
    confidence: float = 0.90,
    recommendation_type: str = "INCREASE_PUMP_DURATION",
):
    return SimpleNamespace(
        should_apply=should_apply,
        confidence=confidence,
        watering_count_analyzed=15,
        recommendation_type=recommendation_type,
        recommended_pump_duration_seconds=recommended,
    )


def main() -> None:
    policy = RuntimeWateringDurationPolicy()

    configured = policy.resolve(
        configured_duration_seconds=100,
        adaptive_recommendation=recommendation(
            should_apply=False,
            recommended=120,
        ),
    )
    assert configured.effective_duration_seconds == 100
    assert configured.source == "CONFIGURED"
    print("[PASS] Low-confidence/observation result kept user duration.")

    learned = policy.resolve(
        configured_duration_seconds=100,
        adaptive_recommendation=recommendation(
            should_apply=True,
            recommended=120,
        ),
    )
    assert learned.learned_duration_seconds == 120
    assert learned.effective_duration_seconds == 120
    assert learned.source == "LEARNED"
    print("[PASS] High-confidence learning applied within 20 percent.")

    combined = policy.resolve(
        configured_duration_seconds=100,
        adaptive_recommendation=recommendation(
            should_apply=True,
            recommended=120,
        ),
        weather_adjustment=WeatherIrrigationAdjustment(
            duration_multiplier=1.15,
            reason="WEATHER_HEAT_DURATION",
        ),
    )
    assert combined.effective_duration_seconds == 130
    assert combined.source == "LEARNED_AND_WEATHER"
    print("[PASS] Learning plus heat remained inside 30 percent cap.")

    decreased = policy.resolve(
        configured_duration_seconds=100,
        adaptive_recommendation=recommendation(
            should_apply=True,
            recommended=60,
            recommendation_type="DECREASE_PUMP_DURATION",
        ),
    )
    assert decreased.effective_duration_seconds == 80
    assert decreased.learned_duration_seconds == 80
    print("[PASS] Excessive decrease was limited to 20 percent.")

    uncertain = policy.resolve(
        configured_duration_seconds=100,
        adaptive_recommendation=recommendation(
            should_apply=True,
            recommended=120,
            confidence=0.84,
        ),
    )
    assert uncertain.effective_duration_seconds == 100
    print("[PASS] Recommendation below confidence threshold was rejected.")

    drip_irrigation = policy.resolve(
        configured_duration_seconds=5 * 60 * 60,
    )
    assert drip_irrigation.effective_duration_seconds == 5 * 60 * 60
    assert drip_irrigation.source == "CONFIGURED"
    print("[PASS] Five-hour drip irrigation duration was preserved.")

    print("All runtime watering-duration policy tests passed.")


if __name__ == "__main__":
    main()
