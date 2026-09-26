package com.alidogukan.avora.health;

import com.alidogukan.avora.models.Health;
import com.alidogukan.avora.models.Status;

/** Resolves the Pi summary without treating stale health metrics as live. */
public final class DeviceHealthOverallResolver {
    public static final long MAX_HEARTBEAT_AGE_SECONDS = 30L;

    public enum State {
        OFFLINE,
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
        if (status.getLastSeenEpoch() > nowEpoch) {
            return true;
        }
        return nowEpoch - status.getLastSeenEpoch()
                <= MAX_HEARTBEAT_AGE_SECONDS;
    }

    public static State evaluate(Health health, Status status, long nowEpoch) {
        if (!isPiOnline(status, nowEpoch) || health == null) {
            return State.OFFLINE;
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
