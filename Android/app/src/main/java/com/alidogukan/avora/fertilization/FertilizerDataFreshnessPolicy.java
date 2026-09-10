package com.alidogukan.avora.fertilization;

import com.alidogukan.avora.models.FertilizationProfile;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.WeatherForecast;

import java.util.ArrayList;
import java.util.List;

/** Central freshness rules for data used by fertilizer recommendations. */
public final class FertilizerDataFreshnessPolicy {

    public static final long SENSOR_MAX_AGE_SECONDS = 90L;
    public static final long WEATHER_MAX_AGE_SECONDS = 6L * 60L * 60L;
    public static final long WATER_ANALYSIS_MAX_AGE_SECONDS = 30L * 24L * 60L * 60L;
    private static final long CLOCK_SKEW_TOLERANCE_SECONDS = 60L;

    private FertilizerDataFreshnessPolicy() { }

    public static boolean requiresLiveSensor(GardenZone zone) {
        return zone != null && zone.isSensor_enabled()
                && zone.getSensor_id() != null && !zone.getSensor_id().isBlank();
    }

    public static boolean isSensorFresh(GardenZone zone, long nowEpochSeconds) {
        return zone != null && isFresh(zone.getUpdated_at_epoch(), nowEpochSeconds,
                SENSOR_MAX_AGE_SECONDS);
    }

    public static boolean isWeatherFresh(WeatherForecast weather, long nowEpochSeconds) {
        return weather != null && isFresh(weather.getUpdatedAtEpoch(), nowEpochSeconds,
                WEATHER_MAX_AGE_SECONDS);
    }

    public static boolean hasWaterAnalysis(FertilizationProfile profile) {
        return profile != null && (profile.getWater_ph() > 0.0
                || profile.getWater_ec_ms() > 0.0);
    }

    public static boolean isWaterAnalysisFresh(FertilizationProfile profile,
                                               long nowEpochSeconds) {
        return hasWaterAnalysis(profile)
                && isFresh(profile.getWater_analysis_updated_at_epoch(), nowEpochSeconds,
                WATER_ANALYSIS_MAX_AGE_SECONDS);
    }

    public static List<String> warnings(GardenZone zone, WeatherForecast weather,
                                        long nowEpochSeconds) {
        List<String> warnings = new ArrayList<>();
        if (requiresLiveSensor(zone) && !isSensorFresh(zone, nowEpochSeconds)) {
            warnings.add(zone.getUpdated_at_epoch() <= 0L
                    ? "Güncel sensör verisi bulunamadı. Toprak nemini doğrulamadan uygulama kararı vermeyin."
                    : "Sensör verisi güncel değil. Toprak nemini yenileyip öneriyi tekrar değerlendirin.");
        }
        if (weather != null && !isWeatherFresh(weather, nowEpochSeconds)) {
            warnings.add("Hava tahmini güncel değil; sıcaklık ve yağış verileri bu öneride kullanılmadı.");
        }
        FertilizationProfile profile = zone == null ? null : zone.getFertilization();
        if (hasWaterAnalysis(profile) && !isWaterAnalysisFresh(profile, nowEpochSeconds)) {
            warnings.add(profile.getWater_analysis_updated_at_epoch() <= 0L
                    ? "Su pH/EC analizinin tarihi bilinmiyor. Değerleri yeni bir ölçümle doğrulayın."
                    : "Su pH/EC analizi 30 günden eski. Gübreleme öncesi analizi yenileyin.");
        }
        return warnings;
    }

    private static boolean isFresh(long timestamp, long nowEpochSeconds,
                                   long maxAgeSeconds) {
        if (timestamp <= 0L || nowEpochSeconds <= 0L) return false;
        long normalizedTimestamp = timestamp > 10_000_000_000L
                ? timestamp / 1000L : timestamp;
        long age = nowEpochSeconds - normalizedTimestamp;
        if (age < -CLOCK_SKEW_TOLERANCE_SECONDS) return false;
        age = Math.max(0L, age);
        return age <= maxAgeSeconds;
    }
}
