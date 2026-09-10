package com.alidogukan.avora.seedling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.alidogukan.avora.models.SeedlingBatch;
import java.util.Map;
import org.junit.Test;

public final class SeedlingStageDateTest {
    private static final long NOW = 1_788_357_600L;

    @Test public void forwardTransitionRecordsTheReachedVisibleMilestone() {
        Map<String, Object> germinating = SeedlingRepository.stageUpdateValues(
                SeedlingStagePolicy.GERMINATING, NOW, true);
        assertEquals(NOW, germinating.get("germination_date_epoch"));
        assertNull(germinating.get("first_leaf_date_epoch"));

        Map<String, Object> firstLeaf = SeedlingRepository.stageUpdateValues(
                SeedlingStagePolicy.COTYLEDON, NOW + 60L, true);
        assertEquals(NOW + 60L, firstLeaf.get("first_leaf_date_epoch"));
        assertFalse(firstLeaf.containsKey("germination_date_epoch"));

        Map<String, Object> trueLeaves = SeedlingRepository.stageUpdateValues(
                SeedlingStagePolicy.TRUE_LEAVES, NOW + 120L, true);
        assertEquals(NOW + 120L, trueLeaves.get("true_leaves_date_epoch"));
        assertFalse(trueLeaves.containsKey("first_leaf_date_epoch"));
    }

    @Test public void rollbackClearsOnlyDatesBeyondTheSelectedStage() {
        Map<String, Object> germinating = SeedlingRepository.stageUpdateValues(
                SeedlingStagePolicy.GERMINATING, NOW, false);
        assertFalse(germinating.containsKey("germination_date_epoch"));
        assertTrue(germinating.containsKey("first_leaf_date_epoch"));
        assertNull(germinating.get("first_leaf_date_epoch"));
        assertNull(germinating.get("true_leaves_date_epoch"));
        assertNull(germinating.get("hardening_date_epoch"));
        assertNull(germinating.get("ready_date_epoch"));

        Map<String, Object> sown = SeedlingRepository.stageUpdateValues(
                SeedlingStagePolicy.SOWN, NOW, false);
        assertNull(sown.get("germination_date_epoch"));
    }

    @Test public void legacyReachedDateIsBackfilledOnceAndNeverOverwritten() {
        SeedlingBatch batch = new SeedlingBatch();
        batch.setStage(SeedlingStagePolicy.GERMINATING);
        batch.setSowing_date_epoch(NOW);
        batch.setEstimated_emergence_epoch(NOW + 6L * 86_400L);
        batch.setEstimated_transplant_epoch(NOW + 20L * 86_400L);
        batch.setCreated_at_epoch(NOW);
        batch.setUpdated_at_epoch(NOW + 120L);

        Map<String, Object> firstMigration =
                SeedlingRepository.legacyStageDateUpdates(batch);
        assertEquals(NOW + 120L, firstMigration.get("germination_date_epoch"));

        batch.setUpdated_at_epoch(NOW + 3_600L);
        assertTrue(SeedlingRepository.legacyStageDateUpdates(batch).isEmpty());
        assertEquals(Long.valueOf(NOW + 120L), batch.getGermination_date_epoch());
    }

    @Test public void legacyTrueLeavesUsesCropTimingInsteadOfLastEditTime() {
        long day = 86_400L;
        SeedlingBatch batch = new SeedlingBatch();
        batch.setPlant_type("Domates");
        batch.setStage(SeedlingStagePolicy.TRUE_LEAVES);
        batch.setSowing_date_epoch(NOW);
        batch.setEstimated_emergence_epoch(NOW + 6L * day);
        batch.setEstimated_transplant_epoch(NOW + 17L * day);
        batch.setGermination_date_epoch(NOW + 6L * day);
        batch.setFirst_leaf_date_epoch(NOW + 12L * day);
        batch.setCreated_at_epoch(NOW);
        batch.setUpdated_at_epoch(NOW + 20L * day);

        Map<String, Object> migration = SeedlingRepository.legacyStageDateUpdates(batch);

        assertEquals(NOW + 15L * day, migration.get("true_leaves_date_epoch"));
    }
}
