package com.alidogukan.avora.zones;

/** Shared Android contract for safe, user-adjustable manual watering durations. */
public final class ManualWateringDurationPolicy {
    public static final int MIN_SECONDS = 5;
    public static final int HARD_MAX_SECONDS = 12 * 60 * 60;
    public static final int DEFAULT_SAFETY_LIMIT_SECONDS = 4 * 60 * 60;
    public static final int EXTENDED_CONFIRMATION_SECONDS = 4 * 60 * 60;
    public static final int DEFAULT_SECONDS = 30;

    private ManualWateringDurationPolicy() {
    }

    public static int configuredLimitOrDefault(int seconds) {
        return isWithinHardLimit(seconds) ? seconds : DEFAULT_SAFETY_LIMIT_SECONDS;
    }

    public static int configuredDurationOrDefault(int seconds, int configuredLimitSeconds) {
        int limit = configuredLimitOrDefault(configuredLimitSeconds);
        return seconds >= MIN_SECONDS && seconds <= limit ? seconds : Math.min(DEFAULT_SECONDS, limit);
    }

    public static int clampToConfiguredLimit(int seconds, int configuredLimitSeconds) {
        int limit = configuredLimitOrDefault(configuredLimitSeconds);
        return Math.max(MIN_SECONDS, Math.min(limit, seconds));
    }

    public static boolean isWithinHardLimit(int seconds) {
        return seconds >= MIN_SECONDS && seconds <= HARD_MAX_SECONDS;
    }

    public static int fromHoursMinutesAndSeconds(
            int hours,
            int minutes,
            int seconds,
            int configuredLimitSeconds
    ) {
        if (hours < 0 || hours > 12
                || minutes < 0 || minutes > 59
                || seconds < 0 || seconds > 59) {
            throw new IllegalArgumentException("INVALID_MANUAL_WATERING_DURATION");
        }
        long total = (long) hours * 3600L + (long) minutes * 60L + seconds;
        int configuredLimit = configuredLimitOrDefault(configuredLimitSeconds);
        if (total < MIN_SECONDS || total > configuredLimit || total > HARD_MAX_SECONDS) {
            throw new IllegalArgumentException("INVALID_MANUAL_WATERING_DURATION");
        }
        return (int) total;
    }

    public static boolean requiresExtendedConfirmation(int seconds) {
        return seconds > EXTENDED_CONFIRMATION_SECONDS;
    }
}
