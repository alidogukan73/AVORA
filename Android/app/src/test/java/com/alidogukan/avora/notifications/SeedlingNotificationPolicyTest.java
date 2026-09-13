package com.alidogukan.avora.notifications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.alidogukan.avora.models.SeedlingBatch;
import com.alidogukan.avora.models.SeedlingTelemetry;
import com.alidogukan.avora.seedling.SeedlingStagePolicy;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;

public final class SeedlingNotificationPolicyTest {
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final long SEPTEMBER_10 = 1_789_001_600L;

    @Test public void inactiveAndReadyBatchesDoNotCreateMilestoneReminders() {
        SeedlingBatch inactive = batch(SeedlingStagePolicy.SOWN);
        inactive.setStatus("ARCHIVED");
        assertEquals(SeedlingNotificationPolicy.Milestone.NONE,
                SeedlingNotificationPolicy.dueMilestone(
                        inactive, LocalDate.of(2026, 9, 10), UTC));

        SeedlingBatch ready = batch(SeedlingStagePolicy.READY);
        assertEquals(SeedlingNotificationPolicy.Milestone.NONE,
                SeedlingNotificationPolicy.dueMilestone(
                        ready, LocalDate.of(2026, 10, 20), UTC));
    }

    @Test public void eachActiveStageMapsToItsNextVisibleMilestone() {
        LocalDate lateDate = LocalDate.of(2026, 12, 31);
        assertEquals(SeedlingNotificationPolicy.Milestone.GERMINATION,
                SeedlingNotificationPolicy.dueMilestone(
                        batch(SeedlingStagePolicy.SOWN), lateDate, UTC));
        assertEquals(SeedlingNotificationPolicy.Milestone.FIRST_LEAF,
                SeedlingNotificationPolicy.dueMilestone(
                        batch(SeedlingStagePolicy.GERMINATING), lateDate, UTC));
        assertEquals(SeedlingNotificationPolicy.Milestone.HARDENING,
                SeedlingNotificationPolicy.dueMilestone(
                        batch(SeedlingStagePolicy.COTYLEDON), lateDate, UTC));
        assertEquals(SeedlingNotificationPolicy.Milestone.HARDENING,
                SeedlingNotificationPolicy.dueMilestone(
                        batch(SeedlingStagePolicy.TRUE_LEAVES), lateDate, UTC));
        assertEquals(SeedlingNotificationPolicy.Milestone.READY,
                SeedlingNotificationPolicy.dueMilestone(
                        batch(SeedlingStagePolicy.HARDENING), lateDate, UTC));
    }

    @Test public void futureMilestoneWaitsUntilItsLocalCalendarDate() {
        SeedlingBatch value = batch(SeedlingStagePolicy.SOWN);
        value.setEstimated_emergence_epoch(SEPTEMBER_10);
        assertEquals(SeedlingNotificationPolicy.Milestone.NONE,
                SeedlingNotificationPolicy.dueMilestone(
                        value, LocalDate.of(2026, 9, 9), UTC));
        assertEquals(SeedlingNotificationPolicy.Milestone.GERMINATION,
                SeedlingNotificationPolicy.dueMilestone(
                        value, LocalDate.of(2026, 9, 10), UTC));
    }

    @Test public void dailyCheckIsDaytimeOnlyAndRecognizesTodaysLog() {
        assertFalse(SeedlingNotificationPolicy.shouldSendDailyCheck(8, 2));
        assertTrue(SeedlingNotificationPolicy.shouldSendDailyCheck(9, 2));
        assertFalse(SeedlingNotificationPolicy.shouldSendDailyCheck(20, 2));
        assertFalse(SeedlingNotificationPolicy.shouldSendDailyCheck(12, 0));
        assertTrue(SeedlingNotificationPolicy.hasLogToday(
                SEPTEMBER_10, LocalDate.of(2026, 9, 10), UTC));
        assertFalse(SeedlingNotificationPolicy.hasLogToday(
                SEPTEMBER_10, LocalDate.of(2026, 9, 11), UTC));
    }

    @Test public void sensorMustBeOnlineAndRecent() {
        SeedlingTelemetry telemetry = new SeedlingTelemetry();
        telemetry.setOnline(true);
        telemetry.setReceived_at_epoch(10_000L);
        assertTrue(SeedlingNotificationPolicy.hasFreshTelemetry(telemetry, 10_090L));
        assertFalse(SeedlingNotificationPolicy.hasFreshTelemetry(telemetry, 10_091L));
        assertFalse(SeedlingNotificationPolicy.hasFreshTelemetry(
                telemetry, 10_000L + SeedlingNotificationPolicy.TELEMETRY_MAX_AGE_SECONDS + 1L));
        telemetry.setOnline(false);
        assertFalse(SeedlingNotificationPolicy.hasFreshTelemetry(telemetry, 10_090L));
    }

    private static SeedlingBatch batch(String stage) {
        SeedlingBatch value = new SeedlingBatch();
        value.setBatch_id("batch-1");
        value.setPlant_type("Domates");
        value.setStatus("ACTIVE");
        value.setStage(stage);
        value.setSowing_date_epoch(1_788_800_000L);
        value.setEstimated_emergence_epoch(SEPTEMBER_10);
        value.setEstimated_transplant_epoch(1_790_000_000L);
        return value;
    }
}
