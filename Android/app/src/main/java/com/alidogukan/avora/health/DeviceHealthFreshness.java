package com.alidogukan.avora.health;

import com.alidogukan.avora.models.SeedlingTelemetry;

/** Applies the same bounded clock tolerance to all device-health readings. */
public final class DeviceHealthFreshness {
    private DeviceHealthFreshness() { }

    public static boolean isFresh(long receivedEpoch, long nowEpoch, long maximumAgeSeconds) {
        if (receivedEpoch <= 0L || nowEpoch < 0L) return false;
        if (receivedEpoch > nowEpoch) {
            return receivedEpoch - nowEpoch <= SeedlingTelemetry.MAX_FUTURE_SKEW_SECONDS;
        }
        return nowEpoch - receivedEpoch <= Math.max(0L, maximumAgeSeconds);
    }
}