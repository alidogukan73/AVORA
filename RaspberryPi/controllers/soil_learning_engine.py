"""
Soil learning engine.
"""

from __future__ import annotations

from datetime import datetime

from models.soil_learning_profile import SoilLearningProfile
from models.moisture_trend import MoistureTrend
from models.watering_record import WateringRecord


class SoilLearningEngine:
    """
    Learns long-term soil behaviour.

    Observation only.

    Never changes irrigation parameters automatically.
    """

    MIN_SENSOR_HISTORY = 20
    MIN_WATERINGS = 5
    MINIMUM_TREND_DURATION_SECONDS = 300.0

    SLOW_DRYING_RATE = 0.25
    NORMAL_DRYING_RATE = 0.75
    FAST_DRYING_RATE = 1.50

    HIGH_RETENTION_RATE = 0.10

    def analyze(
        self,
        *,
        moisture_trend: MoistureTrend,
        watering_records: list[WateringRecord],
    ) -> SoilLearningProfile:
        """
        Analyze soil behaviour and learning progress.
        """

        valid_records = self._filter_records(
            watering_records,
        )

        watering_count = len(
            valid_records
        )

        remaining_sensor_samples = max(
            0,
            self.MIN_SENSOR_HISTORY
            - moisture_trend.sample_count,
        )

        remaining_auto_waterings = max(
            0,
            self.MIN_WATERINGS
            - watering_count,
        )

        average_gain = self._average(
            record.moisture_delta
            for record in valid_records
        )

        average_duration = self._average(
            record.duration
            for record in valid_records
        )

        efficiency = 0.0

        if average_duration > 0:

            efficiency = (
                average_gain
                / average_duration
            )

        has_enough_sensor_history = (
            moisture_trend.sample_count
            >= self.MIN_SENSOR_HISTORY
        )

        has_enough_duration = (
            moisture_trend.duration_seconds
            >= self.MINIMUM_TREND_DURATION_SECONDS
        )

        has_enough_waterings = (
            watering_count
            >= self.MIN_WATERINGS
        )

        if (
            not has_enough_sensor_history
            or not has_enough_duration
            or not has_enough_waterings
        ):

            learning_stage, next_milestone_code = (
                self._learning_progress(
                    has_enough_sensor_history=(
                        has_enough_sensor_history
                    ),
                    has_enough_duration=(
                        has_enough_duration
                    ),
                    has_enough_waterings=(
                        has_enough_waterings
                    ),
                )
            )

            next_milestone_text = (
                self._milestone_text(
                    next_milestone_code
                )
            )

            return SoilLearningProfile(
                profile_status="INSUFFICIENT_DATA",
                soil_classification="UNKNOWN",
                confidence=0.0,
                confidence_level="LOW",

                learning_stage=learning_stage,

                next_milestone_code=(
                    next_milestone_code
                ),

                next_milestone_text=(
                    next_milestone_text
                ),

                remaining_sensor_samples=(
                    remaining_sensor_samples
                ),

                remaining_auto_waterings=(
                    remaining_auto_waterings
                ),

                sensor_history_count=(
                    moisture_trend.sample_count
                ),

                watering_count_analyzed=(
                    watering_count
                ),

                average_moisture=(
                    moisture_trend.average_moisture
                ),

                average_drying_rate_per_minute=(
                    moisture_trend.change_per_minute
                ),

                average_moisture_gain_per_watering=round(
                    average_gain,
                    2,
                ),

                average_watering_duration_seconds=round(
                    average_duration,
                    2,
                ),

                estimated_water_retention_minutes=0.0,

                irrigation_efficiency=round(
                    efficiency,
                    3,
                ),

                learned_at=datetime.now().isoformat(),
            )

        return self._build_profile(
            moisture_trend=moisture_trend,
            watering_records=valid_records,
        )
    
    def _learning_progress(
        self,
        *,
        has_enough_sensor_history: bool,
        has_enough_duration: bool,
        has_enough_waterings: bool,
    ) -> tuple[int, str]:
        """
        Return the current learning stage and next milestone.
        """

        if not has_enough_sensor_history:
            return (
                1,
                "COLLECT_SENSOR_HISTORY",
            )

        if not has_enough_duration:
            return (
                2,
                "OBSERVE_SOIL_TREND",
            )

        if not has_enough_waterings:
            return (
                3,
                "COLLECT_AUTO_WATERINGS",
            )

        return (
            4,
            "ANALYZE_SOIL_BEHAVIOUR",
        )

    def _filter_records(

        self,

        records: list[WateringRecord],

    ) -> list[WateringRecord]:

        """
        Keep only reliable learning records.
        """

        filtered = []

        for record in records:

            if not record.completed:
                continue

            if record.mode != "AUTO":
                continue

            if record.duration <= 0:
                continue

            if not (
                0 <= record.moisture_before <= 100
                and 0 <= record.moisture_after <= 100
            ):
                continue

            observed_delta = (
                record.moisture_after
                - record.moisture_before
            )

            if observed_delta < 0:
                continue

            if observed_delta > 40:
                continue

            if abs(record.moisture_delta - observed_delta) > 0.01:
                continue

            filtered.append(
                record,
            )

        return filtered
    
    def _milestone_text(
        self,
        milestone_code: str,
    ) -> str:
        """
        Return a user-friendly Turkish milestone description.
        """

        milestone_texts = {
            "COLLECT_SENSOR_HISTORY": (
                "Sensör verisi toplanıyor"
            ),

            "OBSERVE_SOIL_TREND": (
                "Toprağın nem değişimi gözlemleniyor"
            ),

            "COLLECT_AUTO_WATERINGS": (
                "Otomatik sulama sonuçları toplanıyor"
            ),

            "ANALYZE_SOIL_BEHAVIOUR": (
                "Toprak davranışı analiz ediliyor"
            ),

            "BUILD_CONFIDENCE": (
                "Öğrenme güveni artırılıyor"
            ),

            "PROFILE_READY": (
                "Toprak öğrenme profili hazır"
            ),
        }

        return milestone_texts.get(
            milestone_code,
            "Toprak öğrenme süreci devam ediyor",
        )

    def _build_profile(
        self,
        *,
        moisture_trend: MoistureTrend,
        watering_records: list[WateringRecord],
    ) -> SoilLearningProfile:
        """
        Build a soil behaviour profile.
        """

        average_gain = self._average(
            record.moisture_delta
            for record in watering_records
        )

        average_duration = self._average(
            record.duration
            for record in watering_records
        )

        efficiency = 0.0

        if average_duration > 0:

            efficiency = (
                average_gain
                / average_duration
            )

        soil_classification = (
            self._classify_soil_behaviour(
                moisture_trend
            )
        )

        confidence = self._calculate_confidence(
            moisture_trend=moisture_trend,
            watering_count=len(
                watering_records
            ),
        )

        confidence_level = (
            self._confidence_level(
                confidence
            )
        )

        profile_status = (
            "READY"
            if confidence >= 0.65
            else "LEARNING"
        )

        retention_minutes = (
            self._estimate_water_retention_minutes(
                moisture_trend
            )
        )

        return SoilLearningProfile(
            profile_status=profile_status,

            soil_classification=(
                soil_classification
            ),

            confidence=confidence,

            confidence_level=(
                confidence_level
            ),

            learning_stage=(
                5
                if profile_status == "READY"
                else 4
            ),

            next_milestone_code=(
                "PROFILE_READY"
                if profile_status == "READY"
                else "BUILD_CONFIDENCE"
            ),

            next_milestone_text=(
                self._milestone_text(
                    "PROFILE_READY"
                    if profile_status == "READY"
                    else "BUILD_CONFIDENCE"
                )
            ),

            remaining_sensor_samples=0,

            remaining_auto_waterings=0,

            sensor_history_count=(
                moisture_trend.sample_count
            ),

            watering_count_analyzed=(
                len(watering_records)
            ),

            average_moisture=(
                moisture_trend.average_moisture
            ),

            average_drying_rate_per_minute=(
                moisture_trend.change_per_minute
            ),

            average_moisture_gain_per_watering=round(
                average_gain,
                2,
            ),

            average_watering_duration_seconds=round(
                average_duration,
                2,
            ),

            estimated_water_retention_minutes=round(
                retention_minutes,
                2,
            ),

            irrigation_efficiency=round(
                efficiency,
                3,
            ),

            learned_at=datetime.now().isoformat(),
        )
    
    def _classify_soil_behaviour(
        self,
        trend: MoistureTrend,
    ) -> str:
        """
        Classify observed soil drying behaviour.
        """

        if (
            trend.sample_count
            < self.MIN_SENSOR_HISTORY
        ):
            return "UNKNOWN"

        if (
            trend.duration_seconds
            < self.MINIMUM_TREND_DURATION_SECONDS
        ):
            return "UNKNOWN"

        if trend.classification == "RISING":
            return "UNKNOWN"

        drying_rate = max(
            0.0,
            -trend.change_per_minute,
        )

        if (
            trend.is_stable
            or drying_rate
            <= self.HIGH_RETENTION_RATE
        ):
            return "HIGH_WATER_RETENTION"

        if drying_rate < self.SLOW_DRYING_RATE:
            return "SLOW_DRYING"

        if drying_rate <= self.NORMAL_DRYING_RATE:
            return "BALANCED"

        if drying_rate < self.FAST_DRYING_RATE:
            return "FAST_DRYING"

        return "VERY_FAST_DRYING"
    
    def _calculate_confidence(
        self,
        *,
        moisture_trend: MoistureTrend,
        watering_count: int,
    ) -> float:
        """
        Calculate conservative soil-profile confidence.
        """

        sensor_score = min(
            moisture_trend.sample_count
            / 100.0,
            1.0,
        )

        watering_score = min(
            watering_count
            / 15.0,
            1.0,
        )

        duration_score = min(
            moisture_trend.duration_seconds
            / 3600.0,
            1.0,
        )

        confidence = (
            sensor_score * 0.40
            + watering_score * 0.40
            + duration_score * 0.20
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
    def _confidence_level(
        self,
        confidence: float,
    ) -> str:
        """
        Convert confidence score to a readable level.
        """

        if confidence >= 0.85:
            return "HIGH"

        if confidence >= 0.65:
            return "MEDIUM"

        return "LOW"

    def _estimate_water_retention_minutes(
        self,
        trend: MoistureTrend,
    ) -> float:
        """
        Estimate how long one moisture percentage point is retained.

        This is an early behavioural estimate, not a physical
        laboratory measurement.
        """

        drying_rate = abs(
            min(
                trend.change_per_minute,
                0.0,
            )
        )

        if drying_rate <= 0.001:
            return 0.0

        return (
            1.0
            / drying_rate
        )

    def _average(
        self,
        values,
    ) -> float:
        """
        Return arithmetic mean.
        """

        value_list = list(
            values
        )

        if not value_list:
            return 0.0

        return (
            sum(value_list)
            / len(value_list)
        )
