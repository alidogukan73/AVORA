package com.alidogukan.avora.season;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.alidogukan.avora.models.SeedlingBatch;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public final class SeasonRepositorySeedlingTransferTest {
    @Test public void transferUsesStableSeasonIdForRetries() {
        String first = SeasonRepository.seedlingSeasonId("zone-001", "-batch_ABC");
        String retry = SeasonRepository.seedlingSeasonId("zone-001", "-batch_ABC");

        assertEquals("zone-001-seedling--batch_ABC", first);
        assertEquals(first, retry);
    }

    @Test public void transferLinksSeasonAndArchivesBatchInOneUpdateMap() {
        SeedlingBatch batch = new SeedlingBatch();
        batch.setBatch_id("batch-123");
        batch.setVariety("H2274");
        batch.setHealthy_count(18);
        Map<String, Object> updates = new HashMap<>();

        SeasonRepository.putSeedlingTransfer(
                updates,
                "garden_journal/seasons/season-123/",
                batch,
                "zone-001",
                "season-123",
                3000L
        );

        assertEquals("batch-123", updates.get(
                "garden_journal/seasons/season-123/source_seedling_batch_id"));
        assertEquals("H2274", updates.get(
                "garden_journal/seasons/season-123/source_seedling_variety"));
        assertEquals(18, updates.get(
                "garden_journal/seasons/season-123/source_seedling_healthy_count"));
        assertEquals(SeedlingBatch.STATUS_ARCHIVED,
                updates.get("seedling/batches/batch-123/status"));
        assertEquals(SeedlingBatch.ARCHIVE_REASON_TRANSFERRED,
                updates.get("seedling/batches/batch-123/archive_reason"));
        assertEquals("season-123",
                updates.get("seedling/batches/batch-123/transferred_season_id"));
        assertEquals("zone-001",
                updates.get("seedling/batches/batch-123/transferred_zone_id"));
        assertEquals("batch-123",
                updates.get("seedling/transfer_claims/batch-123/batch_id"));
        assertEquals("zone-001",
                updates.get("seedling/transfer_claims/batch-123/zone_id"));
        assertEquals("season-123",
                updates.get("seedling/transfer_claims/batch-123/season_id"));
        assertFalse(updates.containsKey("seedling/daily_logs/batch-123"));
    }

    @Test public void undoTransferRestoresBatchAndClearsEverySeasonLink() {
        Map<String, Object> updates = new HashMap<>();

        SeasonRepository.putSeedlingTransferUndo(
                updates,
                "batch-123",
                4000L
        );

        assertEquals(SeedlingBatch.STATUS_ACTIVE,
                updates.get("seedling/batches/batch-123/status"));
        assertNull(updates.get("seedling/batches/batch-123/archive_reason"));
        assertEquals(0L, updates.get("seedling/batches/batch-123/archived_at_epoch"));
        assertNull(updates.get("seedling/batches/batch-123/transferred_season_id"));
        assertNull(updates.get("seedling/batches/batch-123/transferred_zone_id"));
        assertNull(updates.get("seedling/batches/batch-123/transferred_at_epoch"));
        assertEquals(4000L, updates.get("seedling/batches/batch-123/updated_at_epoch"));
        assertNull(updates.get("seedling/transfer_claims/batch-123"));
        assertFalse(updates.containsKey("seedling/daily_logs/batch-123"));
    }

    @Test public void retryIsIdempotentOnlyForTheOriginalTargetZone() {
        SeedlingBatch batch = new SeedlingBatch();
        batch.setBatch_id("batch-123");
        batch.setStatus(SeedlingBatch.STATUS_ARCHIVED);
        batch.setArchive_reason(SeedlingBatch.ARCHIVE_REASON_TRANSFERRED);
        batch.setTransferred_zone_id("zone-001");
        batch.setTransferred_season_id(
                SeasonRepository.seedlingSeasonId("zone-001", "batch-123"));

        assertTrue(SeasonRepository.sameSeedlingTransferTarget(batch, "zone-001"));
        assertFalse(SeasonRepository.sameSeedlingTransferTarget(batch, "zone-002"));
    }
}
