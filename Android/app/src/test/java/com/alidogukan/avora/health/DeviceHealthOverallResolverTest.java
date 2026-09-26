package com.alidogukan.avora.health;

import static org.junit.Assert.assertEquals;

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
        health.setCpuTemperature(45);
        health.setCpuUsage(20);
        health.setMemoryUsage(40);
        health.setDiskUsage(50);
        health.setWifiSignal(-55);
        health.setThrottled(false);
        return health;
    }

    private static Status status(boolean online, long lastSeenEpoch) {
        Status status = new Status();
        status.setOnline(online);
        status.setLastSeenEpoch(lastSeenEpoch);
        return status;
    }
}
