package com.alidogukan.avora.plantassistant;

import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.WeatherForecast;
import com.alidogukan.avora.fertilization.FertilizerDataFreshnessPolicy;
import com.alidogukan.avora.settings.DisplayUnitFormatter;

import java.util.Collections;
import java.util.List;

/**
 * Produces the short, advisory-only recommendation shown on the home screen.
 * It deliberately never diagnoses a disease or controls irrigation; it only
 * turns the latest garden signals into a clear observation prompt.
 */
public final class PlantAssistantHomeRecommendation {
    private static final long RECENT_ANALYSIS_SECONDS = 3L * 24L * 60L * 60L;

    public enum Level {
        NORMAL,
        FOLLOW_UP,
        WARNING
    }

    public static final class Recommendation {
        private final String message;
        private final Level level;

        private Recommendation(String message, Level level) {
            this.message = message;
            this.level = level;
        }

        public String getMessage() { return message; }
        public Level getLevel() { return level; }
    }

    private PlantAssistantHomeRecommendation() { }

    public static String create(
            List<GardenZone> zones,
            WeatherForecast weather,
            PlantAssistantHealthSignal recentAnalysis,
            long nowEpoch
    ) {
        return evaluate(zones, weather, recentAnalysis, nowEpoch).getMessage();
    }

    public static Recommendation evaluate(
            List<GardenZone> zones,
            WeatherForecast weather,
            PlantAssistantHealthSignal recentAnalysis,
            long nowEpoch
    ) {
        return evaluateWithSignals(zones, weather, recentAnalysis == null
                ? Collections.emptyList() : Collections.singletonList(recentAnalysis), nowEpoch);
    }

    public static Recommendation evaluateWithSignals(
            List<GardenZone> zones,
            WeatherForecast weather,
            List<PlantAssistantHealthSignal> recentAnalyses,
            long nowEpoch
    ) {
        return evaluateWithSignals(zones, weather, recentAnalyses, nowEpoch,
                DisplayUnitFormatter.metric());
    }

    public static Recommendation evaluateWithSignals(
            List<GardenZone> zones,
            WeatherForecast weather,
            List<PlantAssistantHealthSignal> recentAnalyses,
            long nowEpoch,
            DisplayUnitFormatter units
    ) {
        if (zones == null || zones.isEmpty()) {
            return recommendation(
                    "Bahçe bölgesi bekleniyor. Bitki önerisi için bir bölge ekleyin.",
                    Level.FOLLOW_UP
            );
        }

        PlantAssistantHealthSignal recentAnalysis =
                strongestActionableSignal(zones, recentAnalyses, nowEpoch);
        if (recentAnalysis != null) {
            GardenZone analyzedZone = findZone(zones, recentAnalysis.getZoneId());
            String zoneName = zoneName(analyzedZone);
            String title = clean(recentAnalysis.getTitle());
            return recommendation(
                    zoneName + " için son analiz: "
                            + (title.isEmpty() ? "yaprakları tekrar kontrol edin" : title)
                            + ". Takip gözlemini ihmal etmeyin.",
                    isHighUrgency(recentAnalysis) ? Level.WARNING : Level.FOLLOW_UP
            );
        }

        GardenZone criticalDry = firstDryZone(zones, 15, nowEpoch);
        if (criticalDry != null) {
            return recommendation(
                    zoneName(criticalDry) + " için su stresi riski var: nem %"
                            + criticalDry.getMoisture() + ", sınır %"
                            + criticalDry.getMoisture_limit()
                            + ". Yapraklarda solma ve kuruma kontrolü öneriliyor.",
                    Level.WARNING
            );
        }

        GardenZone dryInHeat = firstDryZone(zones, 5, nowEpoch);
        WeatherForecast currentWeather = FertilizerDataFreshnessPolicy.isWeatherFresh(
                weather, nowEpoch) ? weather : null;
        Double heat = hottestUpcomingTemperature(currentWeather);
        if (dryInHeat != null && heat != null && heat >= 32D) {
            return recommendation(
                    zoneName(dryInHeat) + " için nem %" + dryInHeat.getMoisture()
                            + "; sıcaklık " + units.formatTemperature(heat)
                            + " bekleniyor. Yapraklarda sıcaklık stresi kontrolü öneriliyor.",
                    Level.WARNING
            );
        }

        GardenZone missingSensor = firstMissingSensor(zones, nowEpoch);
        if (missingSensor != null) {
            return recommendation(
                    zoneName(missingSensor)
                            + " için güncel sensör verisi yok. Sensörü ve yapraklarda solma veya kuruma olup olmadığını kontrol edin.",
                    Level.FOLLOW_UP
            );
        }

        if (heat != null && heat >= 38D) {
            GardenZone zone = firstActiveZone(zones);
            return recommendation(
                    units.formatTemperature(heat) + " sıcaklık bekleniyor. " + zoneName(zone)
                            + " için öğle saatlerinde solma ve yaprak yanığı gözlemi öneriliyor.",
                    Level.FOLLOW_UP
            );
        }

        return recommendation(
                "Şu an kritik bir bitki uyarısı yok. Bu hafta gelişim fotoğrafı ekleyerek görsel takibi sürdürün.",
                Level.NORMAL
        );
    }

    private static Recommendation recommendation(String message, Level level) {
        return new Recommendation(message, level);
    }

    private static boolean hasActionableRecentAnalysis(
            PlantAssistantHealthSignal signal,
            long nowEpoch
    ) {
        if (signal == null || signal.getTitle().isBlank() || !signal.isRecent(nowEpoch)) return false;
        long age = nowEpoch - signal.getCreatedAtEpoch();
        if (age > RECENT_ANALYSIS_SECONDS) return false;
        return PlantAssistantUrgency.severity(signal.getUrgency()) > 0;
    }

    private static boolean isHighUrgency(PlantAssistantHealthSignal signal) {
        return signal != null && PlantAssistantUrgency.isHigh(signal.getUrgency());
    }

    private static PlantAssistantHealthSignal strongestActionableSignal(
            List<GardenZone> zones,
            List<PlantAssistantHealthSignal> signals,
            long nowEpoch
    ) {
        if (signals == null || signals.isEmpty()) return null;
        PlantAssistantHealthSignal selected = null;
        int selectedSeverity = -1;
        for (PlantAssistantHealthSignal signal : signals) {
            if (!hasActionableRecentAnalysis(signal, nowEpoch)) continue;
            GardenZone zone = findZone(zones, signal.getZoneId());
            if (!isActive(zone) || !signal.appliesTo(zone, nowEpoch)) continue;
            int severity = PlantAssistantUrgency.severity(signal.getUrgency());
            if (selected == null || severity > selectedSeverity
                    || (severity == selectedSeverity
                    && signal.getCreatedAtEpoch() > selected.getCreatedAtEpoch())) {
                selected = signal;
                selectedSeverity = severity;
            }
        }
        return selected;
    }

    private static GardenZone firstMissingSensor(List<GardenZone> zones, long nowEpoch) {
        for (GardenZone zone : zones) {
            if (isActive(zone)
                    && zone.isSensor_enabled()
                    && !hasCurrentSensorData(zone, nowEpoch)) {
                return zone;
            }
        }
        return null;
    }

    private static GardenZone firstDryZone(
            List<GardenZone> zones,
            int deficit,
            long nowEpoch
    ) {
        GardenZone result = null;
        int biggestDeficit = 0;
        for (GardenZone zone : zones) {
            if (!isActive(zone)
                    || !zone.isSensor_enabled()
                    || !hasCurrentSensorData(zone, nowEpoch)) {
                continue;
            }
            int currentDeficit = zone.getMoisture_limit() - zone.getMoisture();
            if (currentDeficit >= deficit && currentDeficit > biggestDeficit) {
                result = zone;
                biggestDeficit = currentDeficit;
            }
        }
        return result;
    }

    private static boolean hasCurrentSensorData(GardenZone zone, long nowEpoch) {
        return zone != null && zone.hasSensorData()
                && FertilizerDataFreshnessPolicy.isSensorFresh(zone, nowEpoch);
    }

    private static GardenZone firstActiveZone(List<GardenZone> zones) {
        for (GardenZone zone : zones) if (isActive(zone)) return zone;
        return zones.get(0);
    }

    private static GardenZone findZone(List<GardenZone> zones, String zoneId) {
        if (zoneId == null || zoneId.isBlank()) return null;
        for (GardenZone zone : zones) {
            if (zoneId.equals(zone.getZone_id())) return zone;
        }
        return null;
    }

    private static Double hottestUpcomingTemperature(WeatherForecast weather) {
        if (weather == null) return null;
        Double today = weather.getTodayTemperatureMax();
        Double tomorrow = weather.getTomorrowTemperatureMax();
        if (today == null) return tomorrow;
        if (tomorrow == null) return today;
        return Math.max(today, tomorrow);
    }

    private static boolean isActive(GardenZone zone) {
        return zone != null && zone.isEnabled();
    }

    private static String zoneName(GardenZone zone) {
        return com.alidogukan.avora.zones.PhysicalZoneIdentity.name(zone);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
