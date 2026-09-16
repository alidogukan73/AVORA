"""Safe, deterministic irrigation-time planning for outdoor gardens."""

from __future__ import annotations

from dataclasses import asdict, dataclass
from datetime import datetime, timedelta
import time


@dataclass(frozen=True)
class IrrigationTimePlan:
    """Serializable result published for one garden zone."""

    status: str = "READY_NOW"
    postpone: bool = False
    recommended_at_epoch: int = 0
    window_start_epoch: int = 0
    window_end_epoch: int = 0
    reason: str = "TIMING_READY_NOW"
    detail: str = "Sulama ihtiyaci simdi yeniden kontrol edilebilir."
    score: int = 0
    emergency: bool = False
    weather_based: bool = False
    recheck_before_watering: bool = True
    scope_key: str = ""
    sensor_id: str = ""

    def to_dict(self) -> dict:
        return asdict(self)


class OptimalIrrigationTimeEngine:
    """
    Separate *whether water is needed* from *when it should run*.

    The engine may defer a non-critical outdoor watering request to an early
    morning/evening low-stress window. It never blocks critical dryness and it
    never starts hardware itself; the regular sensor, valve and pump guards are
    re-evaluated by ``IrrigationService`` when the planned time arrives.
    """

    MAX_FORECAST_AGE_SECONDS = 3 * 60 * 60
    MINIMUM_SCHEDULE_LEAD_SECONDS = 5 * 60

    def __init__(self) -> None:
        self.enabled = True
        self.environment = "OPEN_FIELD"
        self.strategy = "SMART"
        self.evening_allowed = True
        self.max_defer_minutes = 12 * 60
        self.critical_moisture_deficit = 12
        self.recheck_before_watering = True
        self.preferred_start_hour = 5
        self.preferred_end_hour = 9

    def configure(self, settings: dict | None) -> None:
        values = settings if isinstance(settings, dict) else {}
        self.enabled = bool(values.get("smart_timing_enabled", True))
        self.environment = self._choice(
            values.get("garden_environment", "OPEN_FIELD"),
            {"OPEN_FIELD", "GREENHOUSE", "INDOOR"},
            "OPEN_FIELD",
        )
        self.strategy = self._choice(
            values.get("irrigation_timing_strategy", "SMART"),
            {"SMART", "MORNING_ONLY", "CUSTOM", "IMMEDIATE"},
            "SMART",
        )
        self.evening_allowed = bool(
            values.get("evening_irrigation_allowed", True)
        )
        self.max_defer_minutes = self._integer(
            values.get("max_irrigation_defer_minutes", 720), 0, 1440, 720
        )
        self.critical_moisture_deficit = self._integer(
            values.get("critical_moisture_deficit", 12), 3, 30, 12
        )
        # This is a mandatory hardware safety guard, not a user preference.
        self.recheck_before_watering = True
        self.preferred_start_hour = self._integer(
            values.get("preferred_start_hour", 5), 0, 23, 5
        )
        self.preferred_end_hour = self._integer(
            values.get("preferred_end_hour", 9), 0, 23, 9
        )

    def evaluate(
        self,
        *,
        forecast: dict | None,
        moisture_deficit: int,
        irrigation_method: str = "DRIP",
        zone_settings: dict | None = None,
        existing_plan: dict | None = None,
        now_epoch: int | None = None,
        scope_key: str = "",
        sensor_id: str = "",
    ) -> IrrigationTimePlan:
        now = int(time.time() if now_epoch is None else now_epoch)
        values = zone_settings if isinstance(zone_settings, dict) else {}
        environment = self._choice(
            values.get("garden_environment", self.environment),
            {"OPEN_FIELD", "GREENHOUSE", "INDOOR"},
            self.environment,
        )
        strategy = self._choice(
            values.get("irrigation_timing_strategy", self.strategy),
            {"SMART", "MORNING_ONLY", "CUSTOM", "IMMEDIATE"},
            self.strategy,
        )
        enabled = bool(values.get("smart_timing_enabled", self.enabled))
        max_defer = self._integer(
            values.get("max_irrigation_defer_minutes", self.max_defer_minutes),
            0,
            1440,
            self.max_defer_minutes,
        )
        critical_deficit = self._integer(
            values.get(
                "critical_moisture_deficit",
                self.critical_moisture_deficit,
            ),
            3,
            30,
            self.critical_moisture_deficit,
        )
        # Moisture, rain, sensor, valve and pump are always rechecked.
        recheck = True

        if moisture_deficit >= critical_deficit:
            return IrrigationTimePlan(
                status="EMERGENCY_READY",
                reason="TIMING_CRITICAL_DRYNESS",
                detail=(
                    "Nem acigi kritik seviyede; bitkiyi korumak icin uygun saat "
                    "beklenmeden guvenlik kontrolleri yeniden yapilacak."
                ),
                emergency=True,
                recheck_before_watering=recheck,
            )

        if not enabled or strategy == "IMMEDIATE" or environment == "INDOOR":
            return IrrigationTimePlan(
                status="READY_NOW",
                reason=(
                    "TIMING_DISABLED" if not enabled else "TIMING_IMMEDIATE_MODE"
                ),
                detail="Akilli saat ertelemesi uygulanmiyor.",
                recheck_before_watering=recheck,
            )

        if environment == "GREENHOUSE" and strategy == "SMART":
            return IrrigationTimePlan(
                status="READY_NOW",
                reason="TIMING_GREENHOUSE_READY",
                detail=(
                    "Sera modu acik; dogrudan gunes ertelemesi uygulanmadan "
                    "nem ve donanim kontrolleri kullanilir."
                ),
                recheck_before_watering=recheck,
            )

        persisted = self._valid_existing_plan(
            existing_plan,
            now,
            max_defer * 60,
            expected_scope_key=str(scope_key or "").strip(),
            expected_sensor_id=str(sensor_id or "").strip(),
        )
        if persisted is not None:
            if persisted.recommended_at_epoch <= now:
                return IrrigationTimePlan(
                    status="READY_FOR_RECHECK",
                    reason="TIMING_PLANNED_TIME_REACHED",
                    detail=(
                        "Planlanan sulama saati geldi; nem, yagis, vana ve "
                        "pompa kosullari yeniden kontrol edilecek."
                    ),
                    weather_based=persisted.weather_based,
                    recheck_before_watering=True,
                )
            return persisted

        deadline = now + max_defer * 60
        candidates = self._candidate_slots(
            forecast=forecast,
            now_epoch=now,
            deadline_epoch=deadline,
            irrigation_method=irrigation_method,
            strategy=strategy,
            values=values,
        )
        if candidates:
            score, candidate_epoch, reason, detail = max(
                candidates,
                key=lambda item: (item[0], -item[1]),
            )
            if candidate_epoch <= now + self.MINIMUM_SCHEDULE_LEAD_SECONDS:
                return IrrigationTimePlan(
                    status="READY_NOW",
                    reason=reason,
                    detail=detail,
                    score=score,
                    weather_based=True,
                    recheck_before_watering=recheck,
                )
            return IrrigationTimePlan(
                status="SCHEDULED",
                postpone=True,
                recommended_at_epoch=candidate_epoch,
                window_start_epoch=max(now, candidate_epoch - 30 * 60),
                window_end_epoch=min(deadline, candidate_epoch + 60 * 60),
                reason=reason,
                detail=detail,
                score=score,
                weather_based=True,
                recheck_before_watering=recheck,
            )

        fallback = self._fallback_window(now, deadline, strategy, values)
        if fallback > now + self.MINIMUM_SCHEDULE_LEAD_SECONDS:
            return IrrigationTimePlan(
                status="SCHEDULED",
                postpone=True,
                recommended_at_epoch=fallback,
                window_start_epoch=fallback,
                window_end_epoch=min(deadline, fallback + 60 * 60),
                reason="TIMING_FALLBACK_MORNING",
                detail=(
                    "Saatlik hava verisi uygun bir pencere vermedi; acik alan "
                    "icin serin sabah penceresi secildi."
                ),
                weather_based=False,
                recheck_before_watering=recheck,
            )

        return IrrigationTimePlan(
            status="READY_NOW",
            reason="TIMING_MAX_DEFER_REACHED",
            detail=(
                "Kullanici erteleme siniri icinde daha uygun bir saat "
                "bulunamadi; guvenlik kontrolleriyle simdi degerlendirilecek."
            ),
            recheck_before_watering=recheck,
        )

    def _candidate_slots(
        self,
        *,
        forecast: dict | None,
        now_epoch: int,
        deadline_epoch: int,
        irrigation_method: str,
        strategy: str,
        values: dict,
    ) -> list[tuple[int, int, str, str]]:
        if not self._forecast_fresh(forecast, now_epoch):
            return []
        hourly = forecast.get("hourly", []) if isinstance(forecast, dict) else []
        if not isinstance(hourly, list):
            return []

        start_hour = self._integer(
            values.get("preferred_start_hour", self.preferred_start_hour),
            0,
            23,
            self.preferred_start_hour,
        )
        end_hour = self._integer(
            values.get("preferred_end_hour", self.preferred_end_hour),
            0,
            23,
            self.preferred_end_hour,
        )
        evening_allowed = bool(
            values.get("evening_irrigation_allowed", self.evening_allowed)
        )
        method = str(irrigation_method or "DRIP").strip().upper()
        slots: list[tuple[int, int, str, str]] = []
        for item in hourly:
            if not isinstance(item, dict):
                continue
            epoch = self._integer(item.get("epoch", 0), 0, 4_000_000_000, 0)
            if epoch < now_epoch or epoch > deadline_epoch:
                continue
            local_hour = self._integer(item.get("local_hour", -1), -1, 23, -1)
            if local_hour < 0:
                local_hour = datetime.fromtimestamp(epoch).hour

            in_custom = self._hour_in_window(local_hour, start_hour, end_hour)
            in_morning = 4 <= local_hour <= 9
            in_evening = 18 <= local_hour <= 22 and evening_allowed
            if strategy == "CUSTOM" and not in_custom:
                continue
            if strategy == "MORNING_ONLY" and not in_morning:
                continue
            if strategy == "SMART" and not (in_morning or in_evening):
                continue

            temperature = self._number(item.get("temperature"), 25.0)
            rain_probability = self._number(item.get("rain_probability"), 0.0)
            rain_mm = self._number(item.get("rain_mm"), 0.0)
            wind = self._number(item.get("wind_kmh"), 0.0)
            radiation = self._number(item.get("shortwave_radiation"), 0.0)
            is_day = bool(item.get("is_day", 0))

            score = 50
            reason = "TIMING_LOW_STRESS_WINDOW"
            detail = "Serin ve dusuk buharlasma riskli bir sulama penceresi secildi."
            if in_morning:
                score += 35
                reason = "TIMING_EARLY_MORNING"
                detail = (
                    "Erken sabah; sicaklik ve buharlasma dusukken sulama "
                    "icin en uygun pencere."
                )
            elif in_evening:
                score += 18 if method == "DRIP" else 6
                reason = "TIMING_EVENING"
                detail = (
                    "Aksam serinligi secildi; sulama oncesi nem ve yaprak "
                    "islakligi riski yeniden kontrol edilecek."
                )
            if not is_day:
                score += 8
            score -= int(max(0.0, temperature - 26.0) * 3)
            score -= int(max(0.0, radiation - 150.0) / 20.0)
            score -= int(max(0.0, wind - 15.0))
            if rain_probability >= 70 or rain_mm >= 1.0:
                score -= 45
            slots.append((score, epoch, reason, detail))
        return slots

    def _fallback_window(
        self, now_epoch: int, deadline_epoch: int, strategy: str, values: dict
    ) -> int:
        start_hour = self._integer(
            values.get("preferred_start_hour", self.preferred_start_hour),
            0,
            23,
            self.preferred_start_hour,
        )
        now = datetime.fromtimestamp(now_epoch)
        target = now.replace(hour=start_hour, minute=30, second=0, microsecond=0)
        if target.timestamp() <= now_epoch + self.MINIMUM_SCHEDULE_LEAD_SECONDS:
            target += timedelta(days=1)
        if strategy == "SMART" and self.evening_allowed and now.hour < 20:
            evening = now.replace(hour=19, minute=30, second=0, microsecond=0)
            if now_epoch + self.MINIMUM_SCHEDULE_LEAD_SECONDS < evening.timestamp() <= deadline_epoch:
                target = min(target, evening)
        return int(target.timestamp()) if target.timestamp() <= deadline_epoch else now_epoch

    def _valid_existing_plan(
        self,
        existing: dict | None,
        now_epoch: int,
        max_defer_seconds: int,
        *,
        expected_scope_key: str = "",
        expected_sensor_id: str = "",
    ) -> IrrigationTimePlan | None:
        if not isinstance(existing, dict) or existing.get("status") != "SCHEDULED":
            return None
        if expected_scope_key and str(existing.get("scope_key", "")).strip() != expected_scope_key:
            return None
        if expected_sensor_id and str(existing.get("sensor_id", "")).strip() != expected_sensor_id:
            return None
        recommended = self._integer(
            existing.get("recommended_at_epoch", 0), 0, 4_000_000_000, 0
        )
        if recommended <= 0 or recommended > now_epoch + max_defer_seconds:
            return None
        return IrrigationTimePlan(
            status="SCHEDULED",
            postpone=recommended > now_epoch,
            recommended_at_epoch=recommended,
            window_start_epoch=self._integer(existing.get("window_start_epoch", recommended), 0, 4_000_000_000, recommended),
            window_end_epoch=self._integer(existing.get("window_end_epoch", recommended + 3600), 0, 4_000_000_000, recommended + 3600),
            reason=str(existing.get("reason", "TIMING_PERSISTED_PLAN")),
            detail=str(existing.get("detail", "Kayitli sulama penceresi korunuyor.")),
            score=self._integer(existing.get("score", 0), -1000, 1000, 0),
            weather_based=bool(existing.get("weather_based", False)),
            recheck_before_watering=True,
            scope_key=str(existing.get("scope_key", "")).strip(),
            sensor_id=str(existing.get("sensor_id", "")).strip(),
        )

    def _forecast_fresh(self, forecast: dict | None, now_epoch: int) -> bool:
        if not isinstance(forecast, dict):
            return False
        updated = self._integer(
            forecast.get("updated_at_epoch", 0), 0, 4_000_000_000, 0
        )
        return updated > 0 and 0 <= now_epoch - updated <= self.MAX_FORECAST_AGE_SECONDS

    @staticmethod
    def _hour_in_window(hour: int, start: int, end: int) -> bool:
        if start == end:
            return True
        if start < end:
            return start <= hour < end
        return hour >= start or hour < end

    @staticmethod
    def _choice(value, allowed: set[str], fallback: str) -> str:
        normalized = str(value or "").strip().upper()
        return normalized if normalized in allowed else fallback

    @staticmethod
    def _integer(value, minimum: int, maximum: int, fallback: int) -> int:
        try:
            return max(minimum, min(maximum, int(float(value))))
        except (TypeError, ValueError):
            return fallback

    @staticmethod
    def _number(value, fallback: float) -> float:
        try:
            return float(value)
        except (TypeError, ValueError):
            return fallback

