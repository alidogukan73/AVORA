"""
AI pipeline orchestrator.
"""

from __future__ import annotations


from controllers.adaptive_irrigation_engine import (
    AdaptiveIrrigationEngine,
)

from controllers.ai_decision_engine import (
    AIDecisionEngine,
)

from controllers.moisture_prediction_engine import (
    MoisturePredictionEngine,
)

from controllers.soil_learning_engine import (
    SoilLearningEngine,
)

from models.adaptive_irrigation_recommendation import (
    AdaptiveIrrigationRecommendation,
)

from models.ai_decision_summary import (
    AIDecisionSummary,
)

from models.irrigation_decision import (
    IrrigationDecision,
)

from models.moisture_prediction import (
    MoisturePrediction,
)

from models.moisture_trend import (
    MoistureTrend,
)

from models.sensor_reading import (
    SensorReading,
)

from models.soil_learning_profile import (
    SoilLearningProfile,
)

from models.watering_record import (
    WateringRecord,
)

from controllers.prediction_accuracy_engine import (
    PredictionAccuracyEngine,
)

from models.prediction_accuracy import (
    PredictionAccuracy,
)

from controllers.unified_confidence_engine import (
    UnifiedConfidenceEngine,
)

from controllers.ai_explanation_engine import (
    AIExplanationEngine,
)

from models.unified_confidence import (
    UnifiedConfidence,
)

from models.ai_explanation import (
    AIExplanation,
)

class AIPipeline:
    """
    Executes every AI component in the correct order.

    Observation mode only.
    """

    def __init__(self) -> None:

        self._soil_learning = SoilLearningEngine()

        self._adaptive = AdaptiveIrrigationEngine()

        self._prediction = MoisturePredictionEngine()

        self._decision = AIDecisionEngine()

        self._prediction_accuracy = PredictionAccuracyEngine()

        self._confidence = UnifiedConfidenceEngine()

        self._explanation = AIExplanationEngine()

    def analyze(
        self,
        *,
        irrigation_decision: IrrigationDecision,
        trend: MoistureTrend,
        reading: SensorReading,
        watering_records: list[WateringRecord],
        prediction_history: list[
            tuple[
                MoisturePrediction,
                float,
            ]
        ],

        current_pump_duration_seconds: int,
        current_cooldown_seconds: int,
    ) -> tuple[
        SoilLearningProfile,
        AdaptiveIrrigationRecommendation,
        MoisturePrediction,
        PredictionAccuracy,
        UnifiedConfidence,
        AIDecisionSummary,
        AIExplanation,
    ]:

        soil_profile = self._soil_learning.analyze(
            moisture_trend=trend,
            watering_records=watering_records,
        )

        adaptive = self._adaptive.analyze(
            records=watering_records,
            current_pump_duration_seconds=current_pump_duration_seconds,
            current_cooldown_seconds=current_cooldown_seconds,
        )

        prediction = self._prediction.analyze(
            trend=trend,
            moisture=reading.moisture,
            moisture_limit=irrigation_decision.moisture_limit,
            transition_blocked=(
                self._post_watering_transition(irrigation_decision)
            ),
        )

        prediction_accuracy = (
            self._prediction_accuracy.analyze(
                predictions=prediction_history,
            )
        )

        unified_confidence = (
            self._confidence.analyze(
                soil_profile=soil_profile,
                prediction_accuracy=prediction_accuracy,
                connection_confidence=self._connection_confidence(
                    reading,
                ),
                measurement_confidence=self._measurement_confidence(
                    reading,
                    irrigation_decision,
                ),
                decision_confidence=self._decision_confidence(
                    irrigation_decision,
                    self._trend_confidence(trend),
                ),
                trend_confidence=self._trend_confidence(
                    trend,
                ),
            )
        )

        decision = self._decision.analyze(
            irrigation_decision=irrigation_decision,
            adaptive_recommendation=adaptive,
            soil_profile=soil_profile,
        )

        explanation = self._explanation.analyze(
            decision=decision,
            soil_profile=soil_profile,
            prediction=prediction,
            prediction_accuracy=prediction_accuracy,
            unified_confidence=unified_confidence,
        )

        return (
            soil_profile,
            adaptive,
            prediction,
            prediction_accuracy,
            unified_confidence,
            decision,
            explanation,
        )

    @staticmethod
    def _connection_confidence(
        reading: SensorReading,
    ) -> float:
        """
        Convert Wi-Fi signal strength to connection confidence only.

        An RSSI of zero means that signal quality was not supplied.
        """

        if reading.rssi == 0:
            return 0.0

        if reading.rssi >= -50:
            return 1.0

        if reading.rssi <= -90:
            return 0.2

        return round(
            0.2 + ((reading.rssi + 90) / 40.0) * 0.8,
            2,
        )

    @staticmethod
    def _sensor_confidence(reading: SensorReading) -> float:
        """Backward-compatible alias for older tests and callers."""

        return AIPipeline._connection_confidence(reading)

    @staticmethod
    def _measurement_confidence(
        reading: SensorReading,
        decision: IrrigationDecision,
    ) -> float:
        """Score electrical validity separately from Wi-Fi connectivity."""

        if (
            reading.raw < 0
            or reading.voltage < 0.0
            or reading.voltage > 3.6
            or reading.moisture < 0
            or reading.moisture > 100
        ):
            return 0.0

        if decision.sensor_stable:
            return 1.0
        if decision.reason == "INSUFFICIENT_SENSOR_SAMPLES":
            return 0.55
        if decision.reason in {"SENSOR_UNSTABLE", "SENSOR_INVALID"}:
            return 0.20
        return 0.45

    @staticmethod
    def _decision_confidence(
        decision: IrrigationDecision,
        trend_confidence: float,
    ) -> float:
        """Score how much valid evidence supports the current decision."""

        if decision.reason in {"SENSOR_UNSTABLE", "SENSOR_INVALID"}:
            return 0.15
        if decision.reason == "INSUFFICIENT_SENSOR_SAMPLES":
            return 0.30
        if not decision.sensor_stable:
            return 0.25

        evidence = max(0.50, min(1.0, trend_confidence))
        if decision.reason in {
            "MOISTURE_BELOW_LIMIT",
            "MOISTURE_SUFFICIENT",
            "COOLDOWN_ACTIVE",
            "WAITING_FOR_MOISTURE_RECOVERY",
        }:
            return round(0.70 + evidence * 0.30, 2)
        if decision.reason in {
            "SYSTEM_DISABLED",
            "AUTO_MODE_DISABLED",
        }:
            return 0.90
        return round(0.50 + evidence * 0.30, 2)

    @staticmethod
    def _post_watering_transition(
        decision: IrrigationDecision,
    ) -> bool:
        """Block forecasts while post-watering moisture is still settling."""

        return (
            decision.cooldown_active
            or decision.reason in {
                "COOLDOWN_ACTIVE",
                "WAITING_FOR_MOISTURE_RECOVERY",
            }
        )

    @staticmethod
    def _trend_confidence(
        trend: MoistureTrend,
    ) -> float:
        """
        Score trend quality from both sample count and duration.
        """

        sample_score = min(
            1.0,
            trend.sample_count / 20.0,
        )
        duration_score = min(
            1.0,
            trend.duration_seconds / 300.0,
        )

        return round(
            sample_score * duration_score,
            2,
        )
