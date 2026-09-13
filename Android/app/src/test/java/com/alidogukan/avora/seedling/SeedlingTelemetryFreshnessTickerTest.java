package com.alidogukan.avora.seedling;

import static org.junit.Assert.assertEquals;

import com.alidogukan.avora.models.SeedlingTelemetry;

import org.junit.Test;

public final class SeedlingTelemetryFreshnessTickerTest {
    @Test public void freshReadingSchedulesFirstStaleSecond() {
        assertEquals(46_000L, delay(telemetry(true, 10_000L), 10_000L));
        assertEquals(1_000L, delay(telemetry(true, 10_000L), 10_045L));
    }

    @Test public void staleOfflineOrMissingReadingDoesNotSchedule() {
        assertEquals(-1L, delay(telemetry(true, 9_954L), 10_000L));
        assertEquals(-1L, delay(telemetry(false, 10_000L), 10_000L));
        assertEquals(-1L, delay(null, 10_000L));
    }

    @Test public void newReadingMovesExpiryForward() {
        assertEquals(41_000L, delay(telemetry(true, 10_005L), 10_010L));
    }

    @Test public void farFutureReadingDoesNotSchedule() {
        long received = 10_000L + SeedlingTelemetry.MAX_FUTURE_SKEW_SECONDS + 1L;
        assertEquals(-1L, delay(telemetry(true, received), 10_000L));
    }

    private static long delay(SeedlingTelemetry telemetry, long nowEpochSeconds) {
        return SeedlingTelemetryFreshnessTicker.delayUntilStaleMillis(
                telemetry,
                nowEpochSeconds,
                45L
        );
    }

    private static SeedlingTelemetry telemetry(boolean online, long receivedAtEpoch) {
        SeedlingTelemetry telemetry = new SeedlingTelemetry();
        telemetry.setOnline(online);
        telemetry.setReceived_at_epoch(receivedAtEpoch);
        return telemetry;
    }
}
