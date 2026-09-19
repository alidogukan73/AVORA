package com.alidogukan.avora.health;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.alidogukan.avora.models.SeedlingTelemetry;
import org.junit.Test;

public final class DeviceHealthFreshnessTest {
    @Test public void expiresWithoutAnotherSensorPacket() {
        assertTrue(DeviceHealthFreshness.isFresh(1000L, 1090L, 90L));
        assertFalse(DeviceHealthFreshness.isFresh(1000L, 1091L, 90L));
        assertTrue(DeviceHealthFreshness.isFresh(1000L, 1030L, 30L));
        assertFalse(DeviceHealthFreshness.isFresh(1000L, 1031L, 30L));
    }

    @Test public void matchesNodeMcuClockToleranceAndMissingData() {
        for (long received : new long[] {0L, 909L, 910L, 1000L, 1120L, 1121L, Long.MAX_VALUE}) {
            SeedlingTelemetry node = new SeedlingTelemetry();
            node.setOnline(true);
            node.setReceived_at_epoch(received);
            assertEquals(node.isFresh(1000L, 90L),
                    DeviceHealthFreshness.isFresh(received, 1000L, 90L));
        }
    }
}