package com.alidogukan.avora.models;

import com.google.firebase.database.IgnoreExtraProperties;
import java.util.Locale;

/**
 * Açık alan sulamasında uygun zaman penceresini yöneten kullanıcı tercihleri.
 */
@IgnoreExtraProperties
public class IrrigationTimingSettings {

    public static final boolean DEFAULT_SMART_TIMING_ENABLED = true;
    public static final String DEFAULT_GARDEN_ENVIRONMENT = "OPEN_FIELD";
    public static final String DEFAULT_TIMING_STRATEGY = "SMART";
    public static final boolean DEFAULT_EVENING_ALLOWED = true;
    public static final int DEFAULT_MAX_DEFER_MINUTES = 720;
    public static final int DEFAULT_CRITICAL_DEFICIT = 12;
    public static final boolean DEFAULT_RECHECK_ENABLED = true;
    public static final int DEFAULT_START_HOUR = 5;
    public static final int DEFAULT_END_HOUR = 9;
    public static final int DEFAULT_MANUAL_WATERING_MAX_DURATION_SECONDS = 5 * 60 * 60;
    public static final int HARD_MANUAL_WATERING_MAX_DURATION_SECONDS = 5 * 60 * 60;

    private boolean smartTimingEnabled = DEFAULT_SMART_TIMING_ENABLED;
    private String gardenEnvironment = DEFAULT_GARDEN_ENVIRONMENT;
    private String timingStrategy = DEFAULT_TIMING_STRATEGY;
    private boolean eveningIrrigationAllowed = DEFAULT_EVENING_ALLOWED;
    private int maxIrrigationDeferMinutes = DEFAULT_MAX_DEFER_MINUTES;
    private int criticalMoistureDeficit = DEFAULT_CRITICAL_DEFICIT;
    private boolean timingRecheckEnabled = DEFAULT_RECHECK_ENABLED;
    private int preferredStartHour = DEFAULT_START_HOUR;
    private int preferredEndHour = DEFAULT_END_HOUR;
    private int manualWateringMaxDurationSeconds =
            DEFAULT_MANUAL_WATERING_MAX_DURATION_SECONDS;
    private long updatedAtEpoch;

    public IrrigationTimingSettings() {
        // Required by Firebase.
    }

    public static IrrigationTimingSettings defaults() {
        return new IrrigationTimingSettings();
    }

    public boolean isSmartTimingEnabled() { return smartTimingEnabled; }
    public void setSmartTimingEnabled(boolean value) { smartTimingEnabled = value; }
    public String getGardenEnvironment() { return gardenEnvironment; }
    public void setGardenEnvironment(String value) {
        gardenEnvironment = safeCode(value, DEFAULT_GARDEN_ENVIRONMENT,
                "OPEN_FIELD", "GREENHOUSE", "INDOOR");
    }
    public String getTimingStrategy() { return timingStrategy; }
    public void setTimingStrategy(String value) {
        timingStrategy = safeCode(value, DEFAULT_TIMING_STRATEGY,
                "SMART", "MORNING_ONLY", "CUSTOM", "IMMEDIATE");
    }
    public boolean isEveningIrrigationAllowed() { return eveningIrrigationAllowed; }
    public void setEveningIrrigationAllowed(boolean value) { eveningIrrigationAllowed = value; }
    public int getMaxIrrigationDeferMinutes() { return maxIrrigationDeferMinutes; }
    public void setMaxIrrigationDeferMinutes(int value) {
        maxIrrigationDeferMinutes = Math.max(0, Math.min(1440, value));
    }
    public int getCriticalMoistureDeficit() { return criticalMoistureDeficit; }
    public void setCriticalMoistureDeficit(int value) {
        criticalMoistureDeficit = Math.max(3, Math.min(30, value));
    }
    public boolean isTimingRecheckEnabled() { return timingRecheckEnabled; }
    public void setTimingRecheckEnabled(boolean value) { timingRecheckEnabled = true; }
    public int getPreferredStartHour() { return preferredStartHour; }
    public void setPreferredStartHour(int value) {
        preferredStartHour = Math.max(0, Math.min(23, value));
    }
    public int getPreferredEndHour() { return preferredEndHour; }
    public void setPreferredEndHour(int value) {
        preferredEndHour = Math.max(0, Math.min(23, value));
    }
    public int getManualWateringMaxDurationSeconds() {
        return manualWateringMaxDurationSeconds;
    }
    public void setManualWateringMaxDurationSeconds(int value) {
        manualWateringMaxDurationSeconds = Math.max(
                5,
                Math.min(HARD_MANUAL_WATERING_MAX_DURATION_SECONDS, value));
    }
    public long getUpdatedAtEpoch() { return updatedAtEpoch; }
    public void setUpdatedAtEpoch(long value) { updatedAtEpoch = Math.max(0, value); }

    private static String safeCode(String value, String fallback, String... allowed) {
        if (value == null || value.isBlank()) return fallback;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (String option : allowed) {
            if (option.equals(normalized)) return option;
        }
        return fallback;
    }
}
