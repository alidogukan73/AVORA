"""
Smart irrigation decision engine.
"""

from __future__ import annotations

import math

from controllers.moisture_trend_analyzer import (
    MoistureTrendAnalyzer,
)
from core.config import IrrigationConfig
from core.logger import AppLogger
from models.command_state import CommandState
from models.irrigation_decision import IrrigationDecision
from models.moisture_history import MoistureHistory
from models.moisture_history import MoistureSample
from models.moisture_trend import MoistureTrend
from models.sensor_reading import SensorReading


class SmartIrrigationEngine:
    """
    Evaluates moisture readings and decides whether watering should start.
    """

    DEFAULT_SAMPLE_WINDOW = 5
    DEFAULT_MAX_SENSOR_SPREAD = 4
    DEFAULT_HISTORY_SIZE = 20
    DEFAULT_MAX_AUTOMATIC_WATERING_CYCLES = (
        IrrigationConfig.DEFAULT_MAX_AUTOMATIC_WATERING_CYCLES
    )

    def __init__(
        self,
        *,
        sample_window: int = DEFAULT_SAMPLE_WINDOW,
        max_sensor_spread: int = DEFAULT_MAX_SENSOR_SPREAD,
        history_size: int = DEFAULT_HISTORY_SIZE,
        max_automatic_watering_cycles: int = (
            DEFAULT_MAX_AUTOMATIC_WATERING_CYCLES
        ),
    ) -> None:

        if sample_window < 3:
            raise ValueError(
                "sample_window must be at least 3.",
            )

        if max_sensor_spread < 0:
            raise ValueError(
                "max_sensor_spread cannot be negative.",
            )

        if history_size < sample_window:
            raise ValueError(
                "history_size cannot be smaller than sample_window.",
            )

        if max_automatic_watering_cycles < 1:
            raise ValueError(
                "max_automatic_watering_cycles must be at least 1.",
            )

        self._logger = AppLogger().logger

        self._sample_window = sample_window
        self._max_sensor_spread = max_sensor_spread
        self._history_size = history_size

        # Nem ölçümlerinin tek ortak kaynağı.
        self._history = MoistureHistory(
            max_samples=history_size,
        )

        # Trend motoru aynı geçmişi yalnızca analiz eder.
        self._trend_analyzer = MoistureTrendAnalyzer(
            history=self._history,
        )

        self._waiting_for_moisture_recovery = False
        self._max_automatic_watering_cycles = (
            max_automatic_watering_cycles
        )
        self._completed_watering_cycles = 0

    def evaluate(
        self,
        *,
        reading: SensorReading,
        commands: CommandState,
        cooldown_active: bool,
    ) -> IrrigationDecision:
        """
        Evaluate the latest sensor reading.
        """

        try:
            moisture_value = float(reading.moisture)
        except (TypeError, ValueError):
            moisture_value = float("nan")

        moisture_valid = (
            math.isfinite(moisture_value)
            and 0.0 <= moisture_value <= 100.0
        )
        moisture = (
            int(round(moisture_value))
            if moisture_valid
            else 0
        )

        try:
            moisture_limit = int(commands.moisture_limit)
        except (TypeError, ValueError):
            moisture_limit = IrrigationConfig.DEFAULT_MOISTURE_LIMIT
        moisture_limit = max(0, min(100, moisture_limit))

        # Sensor providers normally validate this range, but the decision
        # engine is the final hardware safety boundary. Invalid telemetry
        # must neither enter history nor be interpreted as critically dry.
        if not moisture_valid:
            return self._decision(
                should_water=False,
                reason="SENSOR_INVALID",
                moisture=moisture,
                moisture_limit=moisture_limit,
                sensor_stable=False,
                cooldown_active=cooldown_active,
                trend=self.get_current_trend(),
            )

        # Ölçüm yalnızca bir kez ortak geçmişe eklenir.
        trend = self.observe(moisture)

        sensor_stable = (
            self._is_sensor_stable()
        )

        if not commands.enabled:

            return self._decision(
                should_water=False,
                reason="SYSTEM_DISABLED",
                moisture=moisture,
                moisture_limit=moisture_limit,
                sensor_stable=sensor_stable,
                cooldown_active=cooldown_active,
                trend=trend,
            )

        if not commands.auto_mode:

            return self._decision(
                should_water=False,
                reason="AUTO_MODE_DISABLED",
                moisture=moisture,
                moisture_limit=moisture_limit,
                sensor_stable=sensor_stable,
                cooldown_active=cooldown_active,
                trend=trend,
            )

        if not self._has_enough_samples():

            return self._decision(
                should_water=False,
                reason="INSUFFICIENT_SENSOR_SAMPLES",
                moisture=moisture,
                moisture_limit=moisture_limit,
                sensor_stable=False,
                cooldown_active=cooldown_active,
                trend=trend,
            )

        if not sensor_stable:

            return self._decision(
                should_water=False,
                reason="SENSOR_UNSTABLE",
                moisture=moisture,
                moisture_limit=moisture_limit,
                sensor_stable=False,
                cooldown_active=cooldown_active,
                trend=trend,
            )

        recovery_limit = min(
            100,
            moisture_limit
            + int(commands.restart_delta),
        )

        if moisture >= recovery_limit:
            if (
                self._waiting_for_moisture_recovery
                or self._completed_watering_cycles > 0
            ):
                self._waiting_for_moisture_recovery = False
                self._completed_watering_cycles = 0
                self._logger.info(
                    "Soil moisture recovery completed. "
                    "moisture=%d%% recovery_limit=%d%%",
                    moisture,
                    recovery_limit,
                )

        if self._waiting_for_moisture_recovery:
            if moisture < recovery_limit:
                return self._decision(
                    should_water=False,
                    reason="WAITING_FOR_MOISTURE_RECOVERY",
                    moisture=moisture,
                    moisture_limit=moisture_limit,
                    sensor_stable=True,
                    cooldown_active=cooldown_active,
                    trend=trend,
                )

        if cooldown_active:

            return self._decision(
                should_water=False,
                reason="COOLDOWN_ACTIVE",
                moisture=moisture,
                moisture_limit=moisture_limit,
                sensor_stable=True,
                cooldown_active=True,
                trend=trend,
            )

        if moisture >= moisture_limit:

            return self._decision(
                should_water=False,
                reason="MOISTURE_SUFFICIENT",
                moisture=moisture,
                moisture_limit=moisture_limit,
                sensor_stable=True,
                cooldown_active=False,
                trend=trend,
            )

        return self._decision(
            should_water=True,
            reason="MOISTURE_BELOW_LIMIT",
            moisture=moisture,
            moisture_limit=moisture_limit,
            sensor_stable=True,
            cooldown_active=False,
            trend=trend,
        )

    def mark_watering_completed(self) -> None:
        """
        Track a completed pulse and require moisture recovery only
        after the configured number of automatic watering cycles.
        """

        # Pre-watering and post-watering samples must never share one trend.
        # Fresh observations are collected before another automatic decision.
        self._history.clear()

        self._completed_watering_cycles += 1
        self._waiting_for_moisture_recovery = (
            self._completed_watering_cycles
            >= self._max_automatic_watering_cycles
        )

        self._logger.info(
            "Automatic watering cycle completed. cycles=%d/%d "
            "recovery_required=%s",
            self._completed_watering_cycles,
            self._max_automatic_watering_cycles,
            self._waiting_for_moisture_recovery,
        )

    def get_safety_state(self) -> dict:
        """Return the restart-safe automatic-watering guard state."""

        return {
            "completed_watering_cycles": self._completed_watering_cycles,
            "waiting_for_moisture_recovery": (
                self._waiting_for_moisture_recovery
            ),
        }

    def restore_safety_state(
        self,
        *,
        completed_watering_cycles: object,
        waiting_for_moisture_recovery: object,
    ) -> None:
        """Restore a bounded guard state after a backend restart."""

        try:
            cycles = int(completed_watering_cycles)
        except (TypeError, ValueError):
            cycles = 0

        cycles = max(
            0,
            min(self._max_automatic_watering_cycles, cycles),
        )
        waiting = waiting_for_moisture_recovery is True
        if waiting:
            cycles = self._max_automatic_watering_cycles

        self._completed_watering_cycles = cycles
        self._waiting_for_moisture_recovery = (
            waiting or cycles >= self._max_automatic_watering_cycles
        )

    def restore_observation_history(
        self,
        samples: list[MoistureSample],
    ) -> int:
        """
        Restore persisted samples for AI observation after a restart.

        This method is intentionally used only by the observation engine.
        The multi-zone irrigation engines still collect fresh samples after
        every start before they can make an automatic watering decision.
        """

        valid_samples = sorted(
            (
                sample
                for sample in samples
                if (
                    0 <= sample.moisture <= 100
                    and math.isfinite(float(sample.timestamp))
                )
            ),
            key=lambda sample: sample.timestamp,
        )[-self._history_size:]

        self._history.clear()
        self._history.extend(valid_samples)

        return len(valid_samples)

    def observe(
        self,
        moisture: int,
        *,
        timestamp: float | None = None,
    ) -> MoistureTrend:
        """Store one observation without making an irrigation decision."""

        try:
            moisture_value = float(moisture)
        except (TypeError, ValueError):
            return self._trend_analyzer.analyze()

        if not math.isfinite(moisture_value) or not 0 <= moisture_value <= 100:
            return self._trend_analyzer.analyze()

        if timestamp is not None:
            try:
                timestamp_value = float(timestamp)
            except (TypeError, ValueError):
                return self._trend_analyzer.analyze()
            if not math.isfinite(timestamp_value):
                return self._trend_analyzer.analyze()
            timestamp = timestamp_value

        self._history.add(
            int(round(moisture_value)),
            timestamp=timestamp,
        )
        return self._trend_analyzer.analyze()

    def get_current_trend(
        self,
    ) -> MoistureTrend:
        """
        Return the latest moisture trend from shared history.
        """

        return self._trend_analyzer.analyze()

    def reset(
        self,
    ) -> None:
        """
        Clear all stored moisture samples.
        """

        self._history.clear()
        self._waiting_for_moisture_recovery = False
        self._completed_watering_cycles = 0

    def _has_enough_samples(
        self,
    ) -> bool:
        """
        Return whether enough readings exist for a safe decision.
        """

        return self._history.has_at_least(
            self._sample_window,
        )

    def _is_sensor_stable(
        self,
    ) -> bool:
        """
        Check whether the most recent readings are consistent.
        """

        if not self._has_enough_samples():
            return False

        recent_values = (
            self._history.last_values(
                self._sample_window,
            )
        )

        minimum = min(
            recent_values,
        )

        maximum = max(
            recent_values,
        )

        spread = (
            maximum
            - minimum
        )

        return (
            spread
            <= self._max_sensor_spread
        )

    def _decision(
        self,
        *,
        should_water: bool,
        reason: str,
        moisture: int,
        moisture_limit: int,
        sensor_stable: bool,
        cooldown_active: bool,
        trend: MoistureTrend,
    ) -> IrrigationDecision:
        """
        Build and log an irrigation decision.
        """

        effective_change_per_minute = (
            0.0
            if trend.classification == "INSUFFICIENT_DATA"
            else trend.change_per_minute
        )

        decision = IrrigationDecision(
            should_water=should_water,
            reason=reason,
            moisture=moisture,
            moisture_limit=moisture_limit,
            sensor_stable=sensor_stable,
            cooldown_active=cooldown_active,
            trend_classification=(
                trend.classification
            ),
            trend_sample_count=(
                trend.sample_count
            ),
            moisture_change_per_minute=(
                effective_change_per_minute
            ),
            trend_duration_seconds=(
                trend.duration_seconds
            ),

            average_moisture=(
                trend.average_moisture
            ),
        )

        self._logger.debug(
            "Decision=%s "
            "Reason=%s "
            "Trend=%s "
            "TrendSamples=%d "
            "Speed=%.3f/min "
            "Moisture=%d%% "
            "Limit=%d%% "
            "SensorStable=%s "
            "Cooldown=%s",
            decision.should_water,
            decision.reason,
            decision.trend_classification,
            decision.trend_sample_count,
            decision.moisture_change_per_minute,
            decision.moisture,
            decision.moisture_limit,
            decision.sensor_stable,
            decision.cooldown_active,
        )

        return decision
