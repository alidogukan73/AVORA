package com.alidogukan.avora.ui.irrigationassistant;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** Prevents a disconnected backend's old AI result from looking current. */
public final class ZoneAIStateFreshnessChecker {
    static final long MAX_AGE_SECONDS = 3 * 60L;
    static final long MAX_FUTURE_SKEW_SECONDS = 60L;

    private ZoneAIStateFreshnessChecker() {
    }

    public static boolean isFresh(String updatedAt, LocalDateTime now) {
        if (updatedAt == null || updatedAt.trim().isEmpty() || now == null) {
            return false;
        }
        LocalDateTime timestamp;
        try {
            timestamp = LocalDateTime.parse(updatedAt.trim());
        } catch (Exception localFailure) {
            try {
                timestamp = OffsetDateTime.parse(updatedAt.trim())
                        .atZoneSameInstant(ZoneId.systemDefault())
                        .toLocalDateTime();
            } catch (Exception offsetFailure) {
                return false;
            }
        }
        long ageSeconds = Duration.between(timestamp, now).getSeconds();
        return ageSeconds >= -MAX_FUTURE_SKEW_SECONDS
                && ageSeconds <= MAX_AGE_SECONDS;
    }
}
