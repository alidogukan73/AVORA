package com.alidogukan.avora.models;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SeedlingTelemetryTest {
    @Test public void smallFutureClockDifferenceDoesNotMakeLiveReadingStale() {
        SeedlingTelemetry telemetry = telemetry(
                true,
                10_000L + SeedlingTelemetry.MAX_FUTURE_SKEW_SECONDS
        );
        assertTrue(telemetry.isFresh(10_000L, 45L));
    }

    @Test public void timestampBeyondClockSkewLimitIsRejected() {
        SeedlingTelemetry telemetry = telemetry(
                true,
                10_000L + SeedlingTelemetry.MAX_FUTURE_SKEW_SECONDS + 1L
        );
        assertFalse(telemetry.isFresh(10_000L, 45L));
    }

    @Test public void oldOrOfflineReadingIsNotFresh() {
        assertFalse(telemetry(true, 9_954L).isFresh(10_000L, 45L));
        assertFalse(telemetry(false, 10_000L).isFresh(10_000L, 45L));
        assertFalse(telemetry(true, 0L).isFresh(10_000L, 45L));
    }

    private static SeedlingTelemetry telemetry(boolean online, long receivedAtEpoch) {
        SeedlingTelemetry telemetry = new SeedlingTelemetry();
        telemetry.setOnline(online);
        telemetry.setReceived_at_epoch(receivedAtEpoch);
        return telemetry;
    }
}
