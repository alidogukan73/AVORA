package com.alidogukan.avora.seedling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.alidogukan.avora.models.SeedlingBatch;

import org.junit.Test;

import java.util.Map;

public final class SeedlingRepositoryCreateTest {
    @Test public void directSowPayloadKeepsZeroTrayAndOmitsEmptyLifecycleFields() {
        SeedlingBatch batch = batch();
        batch.setTray_cell_count(0);

        Map<String, Object> values = SeedlingRepository.batchCreateValues(batch);

        assertEquals(0, values.get("tray_cell_count"));
        assertEquals("carrot", values.get("crop_id"));
        assertFalse(values.containsKey("archive_reason"));
        assertFalse(values.containsKey("archived_at_epoch"));
        assertFalse(values.containsKey("transferred_season_id"));
        assertFalse(values.containsKey("transferred_zone_id"));
        assertFalse(values.containsKey("transferred_at_epoch"));
    }

    private static SeedlingBatch batch() {
        SeedlingBatch batch = new SeedlingBatch();
        batch.setBatch_id("batch-1");
        batch.setCrop_id("carrot");
        batch.setPlant_type("Havuç");
        batch.setEmoji("🥕");
        batch.setVariety("Nantes");
        batch.setArea("Fide rafı 1");
        batch.setNode_id("seedling-001");
        batch.setStage(SeedlingStagePolicy.SOWN);
        batch.setSowing_date_epoch(1_000L);
        batch.setEstimated_emergence_epoch(2_000L);
        batch.setEstimated_transplant_epoch(3_000L);
        batch.setSeed_count(25);
        batch.setHealthy_count(25);
        batch.setCreated_at_epoch(1_000L);
        batch.setUpdated_at_epoch(1_000L);
        return batch;
    }
}
