package com.alidogukan.avora.health;

import com.alidogukan.avora.models.Health;
import com.alidogukan.avora.models.Status;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/** Resolves the Pi summary without treating stale health metrics as live. */
public final class DeviceHealthOverallResolver {
    public static final long MAX_HEARTBEAT_AGE_SECONDS = 30L;
    // The Pi publishes health every 60 seconds. Allow two missed updates.
    public static final long MAX_HEALTH_AGE_SECONDS = 180L;
    private static final long MAX_CLOCK_SKEW_SECONDS = 30L;

    public enum State {
        OFFLINE,
        UNVERIFIED,
        STALE,
        CRITICAL,
        WARNING,
        HEALTHY
    }

    private DeviceHealthOverallResolver() {
    }

    public static boolean isPiOnline(Status status, long nowEpoch) {
        if (status == null || !status.isOnline()
                || status.getLastSeenEpoch() <= 0L) {
            return false;
        }
        return isFresh(status.getLastSeenEpoch(), nowEpoch, MAX_HEARTBEAT_AGE_SECONDS);
    }

    public static boolean isFresh(long timestamp, long nowEpoch, long maxAgeSeconds) {
        return timestamp > 0L && timestamp <= nowEpoch + MAX_CLOCK_SKEW_SECONDS
                && timestamp >= nowEpoch - maxAgeSeconds;
    }

    public static long healthUpdatedAtEpoch(String value) {
        if (value == null || value.isBlank()) return 0L;
        try {
            return OffsetDateTime.parse(value.trim()).toEpochSecond();
        } catch (DateTimeParseException ignored) {
            try {
                // Legacy Pi reports use datetime.now().isoformat() without an offset.
                return LocalDateTime.parse(value.trim()).atZone(ZoneId.systemDefault()).toEpochSecond();
            } catch (DateTimeParseException invalid) {
                return 0L;
            }
        }
    }

    public static State evaluate(Health health, Status status, long nowEpoch) {
        return evaluate(health, status, nowEpoch, true);
    }

    public static State evaluate(Health health, Status status, long nowEpoch, boolean connectionVerified) {
        if (!connectionVerified) return State.UNVERIFIED;
        if (!isPiOnline(status, nowEpoch)) {
            return State.OFFLINE;
        }
        if (health == null || !isFresh(healthUpdatedAtEpoch(health.getUpdatedAt()), nowEpoch, MAX_HEALTH_AGE_SECONDS)) {
            return State.STALE;
        }

        boolean critical =
                health.isThrottled()
                        || health.getCpuTemperature() >= 75
                        || health.getCpuUsage() >= 85
                        || health.getMemoryUsage() >= 90
                        || health.getDiskUsage() >= 90
                        || health.getWifiSignal() < -80;
        if (critical) {
            return State.CRITICAL;
        }

        boolean warning =
                health.getCpuTemperature() >= 65
                        || health.getCpuUsage() >= 65
                        || health.getMemoryUsage() >= 75
                        || health.getDiskUsage() >= 75
                        || health.getWifiSignal() < -67;
        return warning ? State.WARNING : State.HEALTHY;
    }
}
