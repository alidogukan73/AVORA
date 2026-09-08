"""Deterministic, advisory-only recommendations for seedling telemetry."""
from __future__ import annotations
from dataclasses import asdict, dataclass
from datetime import datetime
from typing import Any

LIGHT_EVALUATION_START_HOUR = 8
LIGHT_EVALUATION_END_HOUR = 18


@dataclass(frozen=True)
class SeedlingTelemetry:
    node_id: str
    firmware: str
    air_temperature_c: float
    air_humidity_pct: float
    root_temperature_c: float
    soil_moisture_available: bool
    soil_moisture_pct: float
    soil_raw: int
    light_lux: float
    rssi: int
    uptime_seconds: int

    @classmethod
    def from_mapping(cls, value: dict[str, Any], expected_node_id: str = "") -> "SeedlingTelemetry":
        if not isinstance(value, dict):
            raise ValueError("Seedling telemetry must be a JSON object.")
        required = {
            "node_id", "air_temperature_c", "air_humidity_pct", "root_temperature_c",
            "light_lux", "rssi", "uptime_seconds",
        }
        soil_moisture_available = value.get("soil_moisture_available", True)
        if not isinstance(soil_moisture_available, bool):
            raise ValueError("soil_moisture_available must be a boolean.")
        if soil_moisture_available:
            required.update({"soil_moisture_pct", "soil_raw"})
        missing = sorted(required.difference(value))
        if missing:
            raise ValueError(f"Missing fields: {', '.join(missing)}")
        try:
            telemetry = cls(
                node_id=str(value["node_id"]).strip(),
                firmware=str(value.get("firmware", "")).strip()[:32],
                air_temperature_c=float(value["air_temperature_c"]),
                air_humidity_pct=float(value["air_humidity_pct"]),
                root_temperature_c=float(value["root_temperature_c"]),
                soil_moisture_available=soil_moisture_available,
                soil_moisture_pct=float(value.get("soil_moisture_pct", 0)),
                soil_raw=int(value.get("soil_raw", 0)),
                light_lux=float(value["light_lux"]),
                rssi=int(value["rssi"]),
                uptime_seconds=int(value["uptime_seconds"]),
            )
        except (TypeError, ValueError) as exc:
            raise ValueError("Seedling telemetry contains an invalid value.") from exc
        if not telemetry.node_id:
            raise ValueError("node_id cannot be empty.")
        if expected_node_id and telemetry.node_id != expected_node_id:
            raise ValueError("MQTT topic and payload node ids do not match.")
        ranges = [
            ("air_temperature_c", telemetry.air_temperature_c, -20, 70),
            ("air_humidity_pct", telemetry.air_humidity_pct, 0, 100),
            ("root_temperature_c", telemetry.root_temperature_c, -20, 70),
            ("light_lux", telemetry.light_lux, 0, 200000),
            ("rssi", telemetry.rssi, -120, 0),
            ("uptime_seconds", telemetry.uptime_seconds, 0, 4294967),
        ]
        if telemetry.soil_moisture_available:
            ranges.extend((
                ("soil_moisture_pct", telemetry.soil_moisture_pct, 0, 100),
                ("soil_raw", telemetry.soil_raw, 0, 32767),
            ))
        for name, current, minimum, maximum in ranges:
            if not minimum <= current <= maximum:
                raise ValueError(f"{name} is outside the accepted range.")
        return telemetry

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass(frozen=True)
class SeedlingRecommendation:
    score: int
    severity: str
    title: str
    message: str
    action: str
    reasons: tuple[str, ...]
    advisory_only: bool = True

    def to_dict(self) -> dict[str, Any]:
        value = asdict(self)
        value["reasons"] = list(self.reasons)
        return value


class SeedlingAssistantEngine:
    """Turns sensor readings into safe, explainable user guidance."""

    def evaluate(
        self,
        telemetry: SeedlingTelemetry,
        observed_at: datetime | None = None,
    ) -> SeedlingRecommendation:
        score, reasons, actions, critical = 100, [], [], False
        moment = observed_at or datetime.now().astimezone()
        evaluate_light = (
            LIGHT_EVALUATION_START_HOUR
            <= moment.hour
            < LIGHT_EVALUATION_END_HOUR
        )
        if telemetry.air_temperature_c < 12:
            score -= 30; critical = True
            reasons.append("Ortam sıcaklığı fide gelişimi için çok düşük.")
            actions.append("Fideleri soğuktan koruyup ortamı kademeli ısıtın.")
        elif telemetry.air_temperature_c < 18:
            score -= 12; reasons.append("Ortam sıcaklığı hedef aralığın altında.")
            actions.append("Gece sıcaklığını ve cereyanı kontrol edin.")
        elif telemetry.air_temperature_c > 35:
            score -= 30; critical = True
            reasons.append("Ortam sıcaklığı fideler için çok yüksek.")
            actions.append("Gölgeleme ve kontrollü havalandırma uygulayın.")
        elif telemetry.air_temperature_c > 29:
            score -= 12; reasons.append("Ortam sıcaklığı hedef aralığın üzerinde.")
            actions.append("Öğle saatlerinde ortamı havalandırın.")
        if telemetry.air_humidity_pct < 35:
            score -= 15; reasons.append("Hava nemi düşük.")
            actions.append("Nem kaybını azaltın; fideleri doğrudan püskürtmeyin.")
        elif telemetry.air_humidity_pct > 85:
            score -= 18; reasons.append("Hava nemi mantar riski oluşturacak kadar yüksek.")
            actions.append("Yaprakları ıslatmadan hava dolaşımını artırın.")
        if telemetry.root_temperature_c < 12:
            score -= 25; critical = True; reasons.append("Kök bölgesi sıcaklığı çok düşük.")
            actions.append("Viyolü soğuk yüzeyden ayırın.")
        elif telemetry.root_temperature_c > 32:
            score -= 25; critical = True; reasons.append("Kök bölgesi sıcaklığı çok yüksek.")
            actions.append("Viyolü doğrudan ısı ve güneşten uzaklaştırın.")
        if not telemetry.soil_moisture_available:
            score -= 16
            reasons.append("Toprak nemi sensörü geçici olarak kullanılamıyor.")
            actions.append("ADS1115 takılana kadar toprak nemini elle kontrol edin.")
        elif telemetry.soil_moisture_pct < 25:
            score -= 28; critical = True; reasons.append("Yetiştirme ortamı çok kuru.")
            actions.append("Elle kontrol ettikten sonra az miktarda ve eşit sulayın.")
        elif telemetry.soil_moisture_pct < 40:
            score -= 10; reasons.append("Yetiştirme ortamı kurumaya yaklaşıyor.")
            actions.append("Sabah nemini elle doğrulayıp sulama ihtiyacını değerlendirin.")
        elif telemetry.soil_moisture_pct > 85:
            score -= 22; reasons.append("Yetiştirme ortamı fazla ıslak.")
            actions.append("Yeni sulama yapmayın; drenaj ve hava dolaşımını kontrol edin.")
        if evaluate_light and telemetry.light_lux < 1000:
            score -= 12; reasons.append("Işık seviyesi düşük.")
            actions.append("Fideleri daha aydınlık konuma alın veya uygun yetiştirme ışığı kullanın.")
        elif evaluate_light and telemetry.light_lux > 60000:
            score -= 12; reasons.append("Işık seviyesi genç fideler için çok yüksek olabilir.")
            actions.append("Yaprak sıcaklığını kontrol edip öğlen gölgeleme uygulayın.")
        score = max(0, score)
        if critical or score < 60:
            severity, title = "CRITICAL", "Fide koşulları acil kontrol edilmeli"
        elif score < 85:
            severity, title = "WARNING", "Fide ortamında iyileştirme önerisi var"
        else:
            severity, title = "GOOD", "Fide ortamı dengeli görünüyor"
        if not reasons:
            reasons.append(
                "Sıcaklık, nem, toprak ve ışık değerleri hedef aralıkta."
                if evaluate_light
                else "Sıcaklık, nem ve toprak değerleri hedef aralıkta. "
                     "Gece ışık seviyesi değerlendirmeye alınmadı."
            )
            actions.append("Günlük gözleme devam edin.")
        return SeedlingRecommendation(score, severity, title, " ".join(reasons),
                                      " ".join(dict.fromkeys(actions)), tuple(reasons))
