package com.alidogukan.avora.seedling;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.alidogukan.avora.models.SeedlingBatch;
import org.junit.Test;

public final class SeedlingTimelineTest {
    private static final long DAY = 86_400L;

    @Test public void mapsSixInternalStatesToFiveVisibleMilestones() {
        assertEquals(1, SeedlingTimeline.completedSteps(SeedlingStagePolicy.SOWN));
        assertEquals(2, SeedlingTimeline.completedSteps(SeedlingStagePolicy.GERMINATING));
        assertEquals(3, SeedlingTimeline.completedSteps(SeedlingStagePolicy.COTYLEDON));
        assertEquals(3, SeedlingTimeline.completedSteps(SeedlingStagePolicy.TRUE_LEAVES));
        assertEquals(4, SeedlingTimeline.completedSteps(SeedlingStagePolicy.HARDENING));
        assertEquals(5, SeedlingTimeline.completedSteps(SeedlingStagePolicy.READY));
    }

    @Test public void derivesFirstLeafHalfwayThroughNurseryPeriod() {
        SeedlingBatch batch = new SeedlingBatch();
        batch.setSowing_date_epoch(10L * DAY);
        batch.setEstimated_emergence_epoch(16L * DAY);
        batch.setEstimated_transplant_epoch(27L * DAY);
        batch.setStage(SeedlingStagePolicy.TRUE_LEAVES);

        assertArrayEquals(new long[] {
                10L * DAY, 16L * DAY, 19L * DAY, 27L * DAY, 0L
        }, SeedlingTimeline.milestoneEpochs(batch));
    }

    @Test public void actualStageDatesReplaceEstimatesWithoutLosingLegacyFallbacks() {
        SeedlingBatch batch = new SeedlingBatch();
        batch.setSowing_date_epoch(10L * DAY);
        batch.setEstimated_emergence_epoch(16L * DAY);
        batch.setEstimated_transplant_epoch(27L * DAY);
        batch.setGermination_date_epoch(12L * DAY);
        batch.setFirst_leaf_date_epoch(15L * DAY);
        batch.setHardening_date_epoch(24L * DAY);
        batch.setReady_date_epoch(28L * DAY);
        batch.setStage(SeedlingStagePolicy.READY);

        assertArrayEquals(new long[] {
                10L * DAY, 12L * DAY, 15L * DAY, 24L * DAY, 28L * DAY
        }, SeedlingTimeline.milestoneEpochs(batch));
    }

    @Test public void lateGerminationMovesThePendingFirstLeafEstimateForward() {
        SeedlingBatch batch = new SeedlingBatch();
        batch.setSowing_date_epoch(10L * DAY);
        batch.setEstimated_emergence_epoch(16L * DAY);
        batch.setEstimated_transplant_epoch(27L * DAY);
        batch.setGermination_date_epoch(20L * DAY);
        batch.setStage(SeedlingStagePolicy.GERMINATING);

        assertArrayEquals(new long[] {
                10L * DAY, 20L * DAY, 23L * DAY, 27L * DAY, 0L
        }, SeedlingTimeline.milestoneEpochs(batch));
    }
}
