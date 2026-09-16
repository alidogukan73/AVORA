"""
Manual test for AIDecisionEngine.
"""

from controllers.ai_decision_engine import (
    AIDecisionEngine,
)
from models.adaptive_irrigation_recommendation import (
    AdaptiveIrrigationRecommendation,
)
from models.irrigation_decision import IrrigationDecision
from models.soil_learning_profile import SoilLearningProfile


def create_irrigation_decision(
    *,
    should_water: bool = False,
    reason: str = "MOISTURE_SUFFICIENT",
    sensor_stable: bool = True,
    trend_classification: str = "STABLE",
) -> IrrigationDecision:
    """
    Create one irrigation decision for testing.
    """

    return IrrigationDecision(
        should_water=should_water,
        reason=reason,
        moisture=55,
        moisture_limit=40,
        sensor_stable=sensor_stable,
        cooldown_active=False,
        trend_classification=trend_classification,
        trend_sample_count=20,
        moisture_change_per_minute=0.0,
        trend_duration_seconds=900.0,
        average_moisture=54.5,
    )


def create_adaptive_recommendation(
    *,
    recommendation_type: str = "KEEP_CURRENT_SETTINGS",
    reason: str = "MOISTURE_GAIN_IN_TARGET_RANGE",
    confidence: float = 0.80,
    confidence_level: str = "MEDIUM",
) -> AdaptiveIrrigationRecommendation:
    """
    Create one adaptive recommendation for testing.
    """

    return AdaptiveIrrigationRecommendation(
        recommendation_type=recommendation_type,
        should_apply=False,
        reason=reason,
        confidence=confidence,
        confidence_level=confidence_level,
        current_pump_duration_seconds=300,
        recommended_pump_duration_seconds=300,
        current_cooldown_seconds=600,
        recommended_cooldown_seconds=600,
        watering_count_analyzed=12,
        average_moisture_delta=6.5,
        average_watering_duration_seconds=300.0,
    )


def create_soil_profile(
    *,
    profile_status: str = "READY",
    soil_classification: str = "BALANCED",
    confidence: float = 0.90,
    confidence_level: str = "HIGH",
    next_milestone_code: str = "PROFILE_READY",
    next_milestone_text: str = (
        "Toprak öğrenme profili hazır"
    ),
) -> SoilLearningProfile:
    """
    Create one soil learning profile for testing.
    """

    return SoilLearningProfile(
        profile_status=profile_status,
        soil_classification=soil_classification,
        confidence=confidence,
        confidence_level=confidence_level,
        learning_stage=(
            5
            if profile_status == "READY"
            else 1
        ),
        next_milestone_code=next_milestone_code,
        next_milestone_text=next_milestone_text,
        remaining_sensor_samples=(
            0
            if profile_status == "READY"
            else 7
        ),
        remaining_auto_waterings=0,
        sensor_history_count=100,
        watering_count_analyzed=12,
        average_moisture=50.0,
        average_drying_rate_per_minute=-0.50,
        average_moisture_gain_per_watering=6.5,
        average_watering_duration_seconds=300.0,
        estimated_water_retention_minutes=2.0,
        irrigation_efficiency=0.022,
        learned_at="2026-07-16T18:00:00",
    )


def print_summary(
    name: str,
    summary,
) -> None:
    """
    Print one AI decision summary.
    """

    print()
    print("=" * 72)
    print(name)
    print("=" * 72)

    print(
        f"decision_code={summary.decision_code}"
    )

    print(
        f"title={summary.decision_title}"
    )

    print(
        f"message={summary.decision_message}"
    )

    print(
        f"severity={summary.severity}"
    )

    print(
        f"confidence={summary.confidence}"
    )

    print(
        "confidence_level="
        f"{summary.confidence_level}"
    )

    print(
        f"should_water={summary.should_water}"
    )

    print(
        "recommendation_type="
        f"{summary.recommendation_type}"
    )

    print(
        "soil_classification="
        f"{summary.soil_classification}"
    )

    print(
        "trend_classification="
        f"{summary.trend_classification}"
    )

    print(
        f"primary_reason={summary.primary_reason}"
    )

    print(
        f"secondary_reason={summary.secondary_reason}"
    )


def run_scenario(
    engine: AIDecisionEngine,
    *,
    name: str,
    irrigation_decision: IrrigationDecision,
    adaptive_recommendation: (
        AdaptiveIrrigationRecommendation
    ),
    soil_profile: SoilLearningProfile,
) -> None:
    """
    Run one AI decision scenario.
    """

    summary = engine.analyze(
        irrigation_decision=irrigation_decision,
        adaptive_recommendation=(
            adaptive_recommendation
        ),
        soil_profile=soil_profile,
    )

    print_summary(
        name,
        summary,
    )


def main() -> None:
    """
    Run all AI decision scenarios.
    """

    engine = AIDecisionEngine()

    disabled_without_samples = engine.analyze(
        irrigation_decision=create_irrigation_decision(
            reason="SYSTEM_DISABLED",
            sensor_stable=False,
        ),
        adaptive_recommendation=create_adaptive_recommendation(),
        soil_profile=create_soil_profile(),
    )
    assert disabled_without_samples.decision_code == "SYSTEM_DISABLED"

    learning_but_watering = engine.analyze(
        irrigation_decision=create_irrigation_decision(
            should_water=True,
            reason="MOISTURE_BELOW_LIMIT",
        ),
        adaptive_recommendation=create_adaptive_recommendation(
            recommendation_type="INSUFFICIENT_DATA",
            confidence=0.0,
            confidence_level="LOW",
        ),
        soil_profile=create_soil_profile(
            profile_status="INSUFFICIENT_DATA",
            soil_classification="UNKNOWN",
            confidence=0.0,
            confidence_level="LOW",
        ),
    )
    assert learning_but_watering.decision_code == "WATERING_RECOMMENDED"
    assert learning_but_watering.should_water is True

    run_scenario(
        engine,
        name="Senaryo 1 - Sensör kararsız",
        irrigation_decision=(
            create_irrigation_decision(
                sensor_stable=False,
                reason="SENSOR_UNSTABLE",
                trend_classification="INSUFFICIENT_DATA",
            )
        ),
        adaptive_recommendation=(
            create_adaptive_recommendation()
        ),
        soil_profile=create_soil_profile(),
    )

    run_scenario(
        engine,
        name="Senaryo 2 - Sistem devre dışı",
        irrigation_decision=(
            create_irrigation_decision(
                reason="SYSTEM_DISABLED",
            )
        ),
        adaptive_recommendation=(
            create_adaptive_recommendation()
        ),
        soil_profile=create_soil_profile(),
    )

    run_scenario(
        engine,
        name="Senaryo 3 - Manuel mod",
        irrigation_decision=(
            create_irrigation_decision(
                reason="AUTO_MODE_DISABLED",
            )
        ),
        adaptive_recommendation=(
            create_adaptive_recommendation()
        ),
        soil_profile=create_soil_profile(),
    )

    run_scenario(
        engine,
        name="Senaryo 4 - Öğrenme devam ediyor",
        irrigation_decision=(
            create_irrigation_decision()
        ),
        adaptive_recommendation=(
            create_adaptive_recommendation(
                recommendation_type=(
                    "INSUFFICIENT_DATA"
                ),
                confidence=0.0,
                confidence_level="LOW",
            )
        ),
        soil_profile=create_soil_profile(
            profile_status="INSUFFICIENT_DATA",
            soil_classification="UNKNOWN",
            confidence=0.0,
            confidence_level="LOW",
            next_milestone_code=(
                "COLLECT_SENSOR_HISTORY"
            ),
            next_milestone_text=(
                "Sensör verisi toplanıyor"
            ),
        ),
    )

    run_scenario(
        engine,
        name="Senaryo 5 - Sulama öneriliyor",
        irrigation_decision=(
            create_irrigation_decision(
                should_water=True,
                reason="MOISTURE_BELOW_LIMIT",
                trend_classification="SLOW_DRYING",
            )
        ),
        adaptive_recommendation=(
            create_adaptive_recommendation()
        ),
        soil_profile=create_soil_profile(),
    )

    run_scenario(
        engine,
        name="Senaryo 6 - Pompa süresini artır",
        irrigation_decision=(
            create_irrigation_decision()
        ),
        adaptive_recommendation=(
            create_adaptive_recommendation(
                recommendation_type=(
                    "INCREASE_PUMP_DURATION"
                ),
                reason=(
                    "AVERAGE_MOISTURE_GAIN_TOO_LOW"
                ),
                confidence=0.72,
                confidence_level="MEDIUM",
            )
        ),
        soil_profile=create_soil_profile(),
    )

    run_scenario(
        engine,
        name="Senaryo 7 - Pompa süresini azalt",
        irrigation_decision=(
            create_irrigation_decision()
        ),
        adaptive_recommendation=(
            create_adaptive_recommendation(
                recommendation_type=(
                    "DECREASE_PUMP_DURATION"
                ),
                reason=(
                    "AVERAGE_MOISTURE_GAIN_HIGH"
                ),
                confidence=0.75,
                confidence_level="MEDIUM",
            )
        ),
        soil_profile=create_soil_profile(),
    )

    run_scenario(
        engine,
        name="Senaryo 8 - İşlem gerekmiyor",
        irrigation_decision=(
            create_irrigation_decision(
                reason="MOISTURE_SUFFICIENT",
            )
        ),
        adaptive_recommendation=(
            create_adaptive_recommendation(
                recommendation_type=(
                    "KEEP_CURRENT_SETTINGS"
                ),
                confidence=0.88,
                confidence_level="HIGH",
            )
        ),
        soil_profile=create_soil_profile(
            confidence=0.92,
            confidence_level="HIGH",
        ),
    )


if __name__ == "__main__":
    main()
