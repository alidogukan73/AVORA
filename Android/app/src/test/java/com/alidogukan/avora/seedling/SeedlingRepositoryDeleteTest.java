package com.alidogukan.avora.seedling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Map;

public final class SeedlingRepositoryDeleteTest {
    @Test public void deletionTargetsOnlyTheGuardedBatch() {
        Map<String, Object> updates =
                SeedlingRepository.batchDeletionUpdates("batch-123");

        assertEquals(1, updates.size());
        assertTrue(updates.containsKey("batches/batch-123"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void deletionRejectsUnsafeBatchId() {
        SeedlingRepository.batchDeletionUpdates("../nodes/seedling-001");
    }
}
