package com.alidogukan.avora.health;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.alidogukan.avora.models.Health;
import com.alidogukan.avora.models.Status;

import org.junit.Test;

public class DeviceHealthOverallResolverTest {
    private static final long NOW = 2_000_000L;

    @Test
    public void staleHeartbeatOverridesHealthyCachedMetrics() {
        assertEquals(
                DeviceHealthOverallResolver.State.OFFLINE,
                DeviceHealthOverallResolver.evaluate(
                        healthyMetrics(),
                        status(true, NOW - 31L),
                        NOW
                )
        );
    }

    @Test
    public void explicitOfflineStatusOverridesHealthyCachedMetrics() {
        assertEquals(
                DeviceHealthOverallResolver.State.OFFLINE,
                DeviceHealthOverallResolver.evaluate(
                        healthyMetrics(),
                        status(false, NOW),
                        NOW
                )
        );
    }

    @Test
    public void freshHeartbeatAllowsResourceHealthEvaluation() {
        assertEquals(
                DeviceHealthOverallResolver.State.HEALTHY,
                DeviceHealthOverallResolver.evaluate(
                        healthyMetrics(),
                        status(true, NOW - 30L),
                        NOW
                )
        );

        Health warning = healthyMetrics();
        warning.setMemoryUsage(75);
        assertEquals(
                DeviceHealthOverallResolver.State.WARNING,
                DeviceHealthOverallResolver.evaluate(
                        warning,
                        status(true, NOW),
                        NOW
                )
        );

        Health critical = healthyMetrics();
        critical.setCpuTemperature(75);
        assertEquals(
                DeviceHealthOverallResolver.State.CRITICAL,
                DeviceHealthOverallResolver.evaluate(
                        critical,
                        status(true, NOW),
                        NOW
                )
        );
    }

    private static Health healthyMetrics() {
        Health health = new Health();
        health.setUpdatedAt(java.time.Instant.ofEpochSecond(NOW).toString());
        health.setCpuTemperature(45);
        health.setCpuUsage(20);
        health.setMemoryUsage(40);
        health.setDiskUsage(50);
        health.setWifiSignal(-55);
        health.setThrottled(false);
        return health;
    }

    @Test public void disconnectedClientCannotVerifyEvenFreshHealthyPi() {
        assertEquals(DeviceHealthOverallResolver.State.UNVERIFIED,
                DeviceHealthOverallResolver.evaluate(healthyMetrics(), status(true, NOW), NOW, false));
    }

    @Test public void freshHeartbeatDoesNotMakeOldHealthCurrent() {
        Health health = healthyMetrics();
        health.setUpdatedAt(java.time.Instant.ofEpochSecond(NOW - 181).toString());
        assertEquals(DeviceHealthOverallResolver.State.STALE,
                DeviceHealthOverallResolver.evaluate(health, status(true, NOW), NOW));
        health.setUpdatedAt(java.time.Instant.ofEpochSecond(NOW - 180).toString());
        assertEquals(DeviceHealthOverallResolver.State.HEALTHY,
                DeviceHealthOverallResolver.evaluate(health, status(true, NOW), NOW));
    }

    @Test public void absentOrInvalidHealthTimestampCannotLookHealthy() {
        for (String value : new String[] {null, "", "invalid", "2026-02-30T10:24:00"}) {
            Health health = healthyMetrics();
            health.setUpdatedAt(value);
            assertEquals(DeviceHealthOverallResolver.State.STALE,
                    DeviceHealthOverallResolver.evaluate(health, status(true, NOW), NOW));
        }
        assertEquals(DeviceHealthOverallResolver.State.STALE,
                DeviceHealthOverallResolver.evaluate(null, status(true, NOW), NOW));
    }

    @Test public void futureClockErrorsCannotKeepPiHealthyIndefinitely() {
        assertFalse(DeviceHealthOverallResolver.isPiOnline(status(true, NOW + 31), NOW));
        Health health = healthyMetrics();
        health.setUpdatedAt(java.time.Instant.ofEpochSecond(NOW + 31).toString());
        assertEquals(DeviceHealthOverallResolver.State.STALE,
                DeviceHealthOverallResolver.evaluate(health, status(true, NOW), NOW));
    }

    @Test public void parsesOffsetAndLegacyFractionalHealthTimestamps() {
        assertEquals(NOW, DeviceHealthOverallResolver.healthUpdatedAtEpoch(
                java.time.Instant.ofEpochSecond(NOW).atOffset(java.time.ZoneOffset.ofHours(3)).toString()));
        String legacy = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(NOW),
                java.time.ZoneId.systemDefault()).toString();
        assertEquals(NOW, DeviceHealthOverallResolver.healthUpdatedAtEpoch(legacy));
        assertEquals(NOW, DeviceHealthOverallResolver.healthUpdatedAtEpoch(
                java.time.Instant.ofEpochSecond(NOW, 123456000).toString()));
    }

    @Test public void missingStatusAndHeartbeatRemainOffline() {
        assertEquals(DeviceHealthOverallResolver.State.OFFLINE,
                DeviceHealthOverallResolver.evaluate(healthyMetrics(), null, NOW));
        assertFalse(DeviceHealthOverallResolver.isPiOnline(status(true, 0), NOW));
    }

    private static Status status(boolean online, long lastSeenEpoch) {
        Status status = new Status();
        status.setOnline(online);
        status.setLastSeenEpoch(lastSeenEpoch);
        return status;
    }
}
