"""Safe combination of learned and weather-based watering durations."""

from __future__ import annotations

from models.watering_duration_plan import WateringDurationPlan


class RuntimeWateringDurationPolicy:
    """Build a bounded runtime duration while preserving user configuration."""

    MINIMUM_ADAPTIVE_CONFIDENCE = 0.85
    MAXIMUM_ADAPTIVE_CHANGE_RATIO = 0.20
    MAXIMUM_COMBINED_INCREASE_RATIO = 0.30
    APPLICABLE_TYPES = {
        "INCREASE_PUMP_DURATION",
        "DECREASE_PUMP_DURATION",
    }

    def resolve(
        self,
        *,
        configured_duration_seconds: int,
        adaptive_recommendation=None,
        weather_adjustment=None,
        minimum_duration_seconds: int = 1,
        maximum_duration_seconds: int = 5 * 60 * 60,
    ) -> WateringDurationPlan:
        configured = self._clamp(
            int(configured_duration_seconds),
            minimum_duration_seconds,
            maximum_duration_seconds,
        )
        learned = configured
        adaptive_applied = False
        confidence = 0.0
        watering_count = 0
        recommendation_type = "INSUFFICIENT_DATA"

        if adaptive_recommendation is not None:
            confidence = float(
                getattr(adaptive_recommendation, "confidence", 0.0)
            )
            watering_count = int(
                getattr(
                    adaptive_recommendation,
                    "watering_count_analyzed",
                    0,
                )
            )
            recommendation_type = str(
                getattr(
                    adaptive_recommendation,
                    "recommendation_type",
                    "INSUFFICIENT_DATA",
                )
            )
            should_apply = bool(
                getattr(adaptive_recommendation, "should_apply", False)
            )
            if (
                should_apply
                and confidence >= self.MINIMUM_ADAPTIVE_CONFIDENCE
                and recommendation_type in self.APPLICABLE_TYPES
            ):
                requested = int(
                    getattr(
                        adaptive_recommendation,
                        "recommended_pump_duration_seconds",
                        configured,
                    )
                )
                minimum_learned = max(
                    minimum_duration_seconds,
                    round(
                        configured
                        * (1.0 - self.MAXIMUM_ADAPTIVE_CHANGE_RATIO)
                    ),
                )
                maximum_learned = min(
                    maximum_duration_seconds,
                    round(
                        configured
                        * (1.0 + self.MAXIMUM_ADAPTIVE_CHANGE_RATIO)
                    ),
                )
                learned = self._clamp(
                    requested,
                    minimum_learned,
                    maximum_learned,
                )
                adaptive_applied = learned != configured

        multiplier = 1.0
        weather_reason = "WEATHER_NEUTRAL"
        if weather_adjustment is not None:
            multiplier = max(
                0.0,
                float(
                    getattr(
                        weather_adjustment,
                        "duration_multiplier",
                        1.0,
                    )
                ),
            )
            weather_reason = str(
                getattr(
                    weather_adjustment,
                    "reason",
                    "WEATHER_NEUTRAL",
                )
            )

        effective = self._clamp(
            round(learned * multiplier),
            minimum_duration_seconds,
            maximum_duration_seconds,
        )
        combined_maximum = min(
            maximum_duration_seconds,
            max(
                configured,
                round(
                    configured
                    * (1.0 + self.MAXIMUM_COMBINED_INCREASE_RATIO)
                ),
            ),
        )
        effective = min(effective, combined_maximum)

        weather_applied = effective != learned
        if adaptive_applied and weather_applied:
            source = "LEARNED_AND_WEATHER"
            reason = f"{recommendation_type}+{weather_reason}"
        elif adaptive_applied:
            source = "LEARNED"
            reason = recommendation_type
        elif weather_applied:
            source = "WEATHER"
            reason = weather_reason
        else:
            source = "CONFIGURED"
            reason = "CONFIGURED_DURATION"

        return WateringDurationPlan(
            configured_duration_seconds=configured,
            learned_duration_seconds=learned,
            effective_duration_seconds=effective,
            source=source,
            reason=reason,
            adaptive_applied=adaptive_applied,
            adaptive_confidence=round(confidence, 2),
            adaptive_watering_count=watering_count,
            adaptive_recommendation_type=recommendation_type,
        )

    @staticmethod
    def _clamp(value: int, minimum: int, maximum: int) -> int:
        return max(minimum, min(maximum, value))
