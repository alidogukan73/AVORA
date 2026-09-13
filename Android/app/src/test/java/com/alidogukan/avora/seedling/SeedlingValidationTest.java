package com.alidogukan.avora.seedling;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.alidogukan.avora.models.SeedlingBatch;
import com.alidogukan.avora.models.SeedlingDailyLog;

import org.junit.Test;

public final class SeedlingValidationTest {
    @Test public void directSowBatchIsValidButOversizedBatchIsRejected() {
        SeedlingBatch batch = activeBatch();
        batch.setTray_cell_count(0);
        assertTrue(SeedlingValidation.isValidNewBatch(batch));

        batch.setSeed_count(SeedlingValidation.MAX_SEED_COUNT + 1);
        assertFalse(SeedlingValidation.isValidNewBatch(batch));
    }

    @Test public void archivedBatchCannotAcceptDailyLog() {
        SeedlingBatch batch = activeBatch();
        SeedlingDailyLog log = validLog();
        assertTrue(SeedlingValidation.isValidLog(log, batch));

        batch.setStatus(SeedlingBatch.STATUS_ARCHIVED);
        assertFalse(SeedlingValidation.isValidLog(log, batch));
    }

    @Test public void dailyLogLimitsMatchFirebaseSchema() {
        SeedlingBatch batch = activeBatch();
        SeedlingDailyLog log = validLog();
        log.setHeight_cm(SeedlingValidation.MAX_HEIGHT_CM + 0.1d);
        assertFalse(SeedlingValidation.isValidLog(log, batch));

        log = validLog();
        log.setLeaf_count(SeedlingValidation.MAX_LEAF_COUNT + 1);
        assertFalse(SeedlingValidation.isValidLog(log, batch));
    }

    private static SeedlingBatch activeBatch() {
        SeedlingBatch batch = new SeedlingBatch();
        batch.setPlant_type("Domates");
        batch.setSeed_count(100);
        batch.setTray_cell_count(100);
        batch.setHealthy_count(100);
        batch.setStatus(SeedlingBatch.STATUS_ACTIVE);
        batch.setNode_id("seedling-001");
        batch.setSowing_date_epoch(1_000L);
        batch.setEstimated_emergence_epoch(2_000L);
        batch.setEstimated_transplant_epoch(3_000L);
        batch.setCreated_at_epoch(1_000L);
        batch.setUpdated_at_epoch(1_000L);
        return batch;
    }

    private static SeedlingDailyLog validLog() {
        SeedlingDailyLog log = new SeedlingDailyLog();
        log.setBatch_id("batch-1");
        log.setHeight_cm(5d);
        log.setLeaf_count(2);
        log.setHealthy_count(90);
        log.setNote("İyi gelişiyor");
        log.setCreated_at_epoch(1_000L);
        return log;
    }
}
