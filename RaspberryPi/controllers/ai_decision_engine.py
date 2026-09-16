"""
AI decision engine.
"""

from __future__ import annotations

from datetime import datetime

from models.adaptive_irrigation_recommendation import (
    AdaptiveIrrigationRecommendation,
)
from models.ai_decision_summary import AIDecisionSummary
from models.irrigation_decision import IrrigationDecision
from models.soil_learning_profile import SoilLearningProfile


class AIDecisionEngine:
    """
    Combines irrigation, adaptive and soil-learning outputs
    into one explainable decision summary.

    Observation mode only.

    This engine never changes irrigation commands and never
    controls the relay directly.
    """

    def analyze(
        self,
        *,
        irrigation_decision: IrrigationDecision,
        adaptive_recommendation: AdaptiveIrrigationRecommendation,
        soil_profile: SoilLearningProfile,
    ) -> AIDecisionSummary:
        """
        Produce one unified AI decision.
        """

        if (
            irrigation_decision.reason
            == "SYSTEM_DISABLED"
        ):

            return self._summary(
                decision_code="SYSTEM_DISABLED",
                decision_title="Sistem devre dışı",
                decision_message=(
                    "Sulama sistemi Firebase ayarlarından "
                    "devre dışı bırakılmış durumda."
                ),
                severity="INFO",
                confidence=1.0,
                confidence_level="HIGH",
                should_water=False,
                recommendation_type=(
                    adaptive_recommendation.recommendation_type
                ),
                soil_classification=(
                    soil_profile.soil_classification
                ),
                trend_classification=(
                    irrigation_decision.trend_classification
                ),
                primary_reason="SYSTEM_DISABLED",
                secondary_reason="USER_SETTING",
            )

        if (
            irrigation_decision.reason
            == "AUTO_MODE_DISABLED"
        ):

            return self._summary(
                decision_code="MANUAL_MODE",
                decision_title="Manuel mod etkin",
                decision_message=(
                    "Otomatik sulama kapalı. Pompa kontrolü "
                    "kullanıcı tarafından manuel olarak yönetiliyor."
                ),
                severity="INFO",
                confidence=1.0,
                confidence_level="HIGH",
                should_water=False,
                recommendation_type=(
                    adaptive_recommendation.recommendation_type
                ),
                soil_classification=(
                    soil_profile.soil_classification
                ),
                trend_classification=(
                    irrigation_decision.trend_classification
                ),
                primary_reason="AUTO_MODE_DISABLED",
                secondary_reason="MANUAL_CONTROL_ACTIVE",
            )

        if not irrigation_decision.sensor_stable:

            return self._summary(
                decision_code="SENSOR_UNSTABLE",
                decision_title="Sensör verisi kararsız",
                decision_message=(
                    "Sulama kararı verilmeden önce sensör "
                    "ölçümlerinin kararlı hale gelmesi bekleniyor."
                ),
                severity="WARNING",
                confidence=0.0,
                confidence_level="LOW",
                should_water=False,
                recommendation_type=(
                    adaptive_recommendation.recommendation_type
                ),
                soil_classification=(
                    soil_profile.soil_classification
                ),
                trend_classification=(
                    irrigation_decision.trend_classification
                ),
                primary_reason=(
                    "SENSOR_INVALID"
                    if irrigation_decision.reason == "SENSOR_INVALID"
                    else "SENSOR_UNSTABLE"
                ),
                secondary_reason=(
                    irrigation_decision.reason
                ),
            )

        if (
            soil_profile.profile_status
            != "READY"
            and not irrigation_decision.should_water
        ):

            return self._summary(
                decision_code="LEARNING",
                decision_title=(
                    "Sistem öğrenmeye devam ediyor"
                ),
                decision_message=(
                    soil_profile.next_milestone_text
                ),
                severity="INFO",
                confidence=(
                    soil_profile.confidence
                ),
                confidence_level=(
                    soil_profile.confidence_level
                ),
                should_water=(
                    irrigation_decision.should_water
                ),
                recommendation_type=(
                    adaptive_recommendation.recommendation_type
                ),
                soil_classification=(
                    soil_profile.soil_classification
                ),
                trend_classification=(
                    irrigation_decision.trend_classification
                ),
                primary_reason="SOIL_PROFILE_NOT_READY",
                secondary_reason=(
                    soil_profile.next_milestone_code
                ),
            )

        if irrigation_decision.should_water:

            return self._summary(
                decision_code="WATERING_RECOMMENDED",
                decision_title="Sulama öneriliyor",
                decision_message=(
                    "Toprak nemi belirlenen sınırın altında "
                    "ve sensör verileri sulama için uygun görünüyor."
                ),
                severity="WARNING",
                confidence=self._combined_confidence(
                    adaptive_recommendation=(
                        adaptive_recommendation
                    ),
                    soil_profile=soil_profile,
                ),
                confidence_level=self._combined_confidence_level(
                    adaptive_recommendation=(
                        adaptive_recommendation
                    ),
                    soil_profile=soil_profile,
                ),
                should_water=True,
                recommendation_type=(
                    adaptive_recommendation.recommendation_type
                ),
                soil_classification=(
                    soil_profile.soil_classification
                ),
                trend_classification=(
                    irrigation_decision.trend_classification
                ),
                primary_reason=(
                    irrigation_decision.reason
                ),
                secondary_reason=(
                    soil_profile.soil_classification
                ),
            )

        if (
            adaptive_recommendation.recommendation_type
            == "INCREASE_PUMP_DURATION"
        ):

            return self._summary(
                decision_code="INCREASE_PUMP_DURATION",
                decision_title=(
                    "Pompa süresi artırılabilir"
                ),
                decision_message=(
                    "Geçmiş otomatik sulamalarda ortalama nem "
                    "artışı düşük kaldığı için pompa süresinin "
                    "kontrollü biçimde artırılması öneriliyor."
                ),
                severity="WARNING",
                confidence=(
                    adaptive_recommendation.confidence
                ),
                confidence_level=(
                    adaptive_recommendation.confidence_level
                ),
                should_water=False,
                recommendation_type=(
                    adaptive_recommendation.recommendation_type
                ),
                soil_classification=(
                    soil_profile.soil_classification
                ),
                trend_classification=(
                    irrigation_decision.trend_classification
                ),
                primary_reason=(
                    adaptive_recommendation.reason
                ),
                secondary_reason=(
                    soil_profile.soil_classification
                ),
            )

        if (
            adaptive_recommendation.recommendation_type
            == "DECREASE_PUMP_DURATION"
        ):

            return self._summary(
                decision_code="DECREASE_PUMP_DURATION",
                decision_title=(
                    "Pompa süresi azaltılabilir"
                ),
                decision_message=(
                    "Geçmiş sulamalarda nem artışı yüksek olduğu "
                    "için daha kısa pompa süresi yeterli olabilir."
                ),
                severity="INFO",
                confidence=(
                    adaptive_recommendation.confidence
                ),
                confidence_level=(
                    adaptive_recommendation.confidence_level
                ),
                should_water=False,
                recommendation_type=(
                    adaptive_recommendation.recommendation_type
                ),
                soil_classification=(
                    soil_profile.soil_classification
                ),
                trend_classification=(
                    irrigation_decision.trend_classification
                ),
                primary_reason=(
                    adaptive_recommendation.reason
                ),
                secondary_reason=(
                    soil_profile.soil_classification
                ),
            )

        return self._summary(
            decision_code="NO_ACTION_REQUIRED",
            decision_title="Mevcut ayarlar uygun",
            decision_message=(
                "Toprak nemi, kuruma davranışı ve geçmiş "
                "sulama sonuçları mevcut ayarların korunmasını "
                "destekliyor."
            ),
            severity="SUCCESS",
            confidence=self._combined_confidence(
                adaptive_recommendation=(
                    adaptive_recommendation
                ),
                soil_profile=soil_profile,
            ),
            confidence_level=self._combined_confidence_level(
                adaptive_recommendation=(
                    adaptive_recommendation
                ),
                soil_profile=soil_profile,
            ),
            should_water=False,
            recommendation_type=(
                adaptive_recommendation.recommendation_type
            ),
            soil_classification=(
                soil_profile.soil_classification
            ),
            trend_classification=(
                irrigation_decision.trend_classification
            ),
            primary_reason=(
                irrigation_decision.reason
            ),
            secondary_reason=(
                adaptive_recommendation.reason
            ),
        )

    def _combined_confidence(
        self,
        *,
        adaptive_recommendation: AdaptiveIrrigationRecommendation,
        soil_profile: SoilLearningProfile,
    ) -> float:
        """
        Combine adaptive and soil-profile confidence conservatively.
        """

        confidence = (
            adaptive_recommendation.confidence
            * 0.45
            + soil_profile.confidence
            * 0.55
        )

        return round(
            max(
                0.0,
                min(
                    confidence,
                    0.95,
                ),
            ),
            2,
        )

    def _combined_confidence_level(
        self,
        *,
        adaptive_recommendation: AdaptiveIrrigationRecommendation,
        soil_profile: SoilLearningProfile,
    ) -> str:
        """
        Return combined readable confidence level.
        """

        confidence = self._combined_confidence(
            adaptive_recommendation=(
                adaptive_recommendation
            ),
            soil_profile=soil_profile,
        )

        if confidence >= 0.85:
            return "HIGH"

        if confidence >= 0.65:
            return "MEDIUM"

        return "LOW"

    def _summary(
        self,
        *,
        decision_code: str,
        decision_title: str,
        decision_message: str,
        severity: str,
        confidence: float,
        confidence_level: str,
        should_water: bool,
        recommendation_type: str,
        soil_classification: str,
        trend_classification: str,
        primary_reason: str,
        secondary_reason: str,
    ) -> AIDecisionSummary:
        """
        Build one immutable AI decision summary.
        """

        return AIDecisionSummary(
            decision_code=decision_code,
            decision_title=decision_title,
            decision_message=decision_message,
            severity=severity,
            confidence=round(
                max(
                    0.0,
                    min(
                        confidence,
                        0.95,
                    ),
                ),
                2,
            ),
            confidence_level=confidence_level,
            should_water=should_water,
            recommendation_type=recommendation_type,
            soil_classification=soil_classification,
            trend_classification=trend_classification,
            primary_reason=primary_reason,
            secondary_reason=secondary_reason,
            generated_at=datetime.now().isoformat(),
        )
