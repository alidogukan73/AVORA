package com.alidogukan.avora.seedling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.alidogukan.avora.models.SeedlingBatch;

import org.junit.Test;

import java.util.Map;

public final class SeedlingRepositoryArchiveTest {
    @Test public void manualArchivePreservesDataAndChangesOnlyLifecycleFields() {
        Map<String, Object> updates =
                SeedlingRepository.manualArchiveUpdateValues(1234L);

        assertEquals(4, updates.size());
        assertEquals(SeedlingBatch.STATUS_ARCHIVED, updates.get("status"));
        assertEquals(SeedlingBatch.ARCHIVE_REASON_MANUAL, updates.get("archive_reason"));
        assertEquals(1234L, updates.get("archived_at_epoch"));
        assertEquals(1234L, updates.get("updated_at_epoch"));
        assertFalse(updates.containsKey("daily_logs"));
    }

    @Test public void restoreClearsManualArchiveState() {
        Map<String, Object> updates =
                SeedlingRepository.restoreUpdateValues(2345L);

        assertEquals(SeedlingBatch.STATUS_ACTIVE, updates.get("status"));
        assertTrue(updates.containsKey("archive_reason"));
        assertNull(updates.get("archive_reason"));
        assertEquals(0L, updates.get("archived_at_epoch"));
        assertEquals(2345L, updates.get("updated_at_epoch"));
    }

    @Test public void transferredBatchRequiresReasonAndSeasonLink() {
        SeedlingBatch batch = new SeedlingBatch();
        batch.setStatus(SeedlingBatch.STATUS_ARCHIVED);
        batch.setArchive_reason(SeedlingBatch.ARCHIVE_REASON_TRANSFERRED);
        assertFalse(batch.isTransferred());

        batch.setTransferred_season_id("zone-1-seedling-batch-1");
        assertTrue(batch.isTransferred());
        assertTrue(batch.isArchived());
    }
}
