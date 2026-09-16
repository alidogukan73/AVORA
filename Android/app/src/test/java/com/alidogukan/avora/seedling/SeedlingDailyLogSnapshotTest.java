package com.alidogukan.avora.seedling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.alidogukan.avora.models.SeedlingDailyLog;
import com.alidogukan.avora.models.SeedlingTelemetry;

import org.junit.Test;

public final class SeedlingDailyLogSnapshotTest {
    @Test public void captureCopiesValuesInsteadOfKeepingLiveReference() {
        SeedlingTelemetry live = telemetry(1000L);
        SeedlingDailyLog log = new SeedlingDailyLog();
        log.captureSensorSnapshot(live, 1010L, 45L);
        live.setAir_temperature_c(99d);
        live.setSoil_moisture_pct(1d);

        SeedlingTelemetry stored = log.storedSensorSnapshot();
        assertNotNull(stored);
        assertEquals(24.5d, stored.getAir_temperature_c(), 0.001d);
        assertEquals(62d, stored.getSoil_moisture_pct(), 0.001d);
        assertTrue(log.isSensor_snapshot_fresh());
        assertEquals(1010L, log.getSensor_captured_at_epoch());
    }

    @Test public void editingCopyPreservesOriginalSnapshot() {
        SeedlingDailyLog original = new SeedlingDailyLog();
        original.captureSensorSnapshot(telemetry(1000L), 1010L, 45L);
        SeedlingDailyLog edited = new SeedlingDailyLog();
        edited.copySensorSnapshotFrom(original);

        assertTrue(edited.isSensor_snapshot_available());
        assertEquals(24.5d, edited.getSensor_air_temperature_c(), 0.001d);
        assertEquals(1000L, edited.getSensor_received_at_epoch());
    }

    @Test public void staleReadingIsRememberedAsStaleAtCapture() {
        SeedlingDailyLog log = new SeedlingDailyLog();
        log.captureSensorSnapshot(telemetry(900L), 1000L, 45L);
        assertTrue(log.isSensor_snapshot_available());
        assertFalse(log.isSensor_snapshot_fresh());
    }

    private static SeedlingTelemetry telemetry(long receivedAt) {
        SeedlingTelemetry value = new SeedlingTelemetry();
        value.setNode_id("seedling-001");
        value.setOnline(true);
        value.setAir_temperature_c(24.5d);
        value.setAir_humidity_pct(71d);
        value.setRoot_temperature_c(23d);
        value.setSoil_moisture_available(true);
        value.setSoil_moisture_pct(62d);
        value.setSoil_raw(12000);
        value.setLight_lux(8400d);
        value.setReceived_at_epoch(receivedAt);
        return value;
    }
}
