package com.alidogukan.avora.seedling;

import com.alidogukan.avora.models.SeedlingBatch;

/** Maps the six internal nursery states onto the five milestones shown to users. */
public final class SeedlingTimeline {
    private static final long DAY_SECONDS = 86_400L;

    private SeedlingTimeline() { }

    public static int completedSteps(String stage) {
        int progress = SeedlingStagePolicy.progress(stage);
        if (progress <= 2) return progress;
        if (progress <= 4) return 3;
        return progress - 1;
    }

    public static long[] milestoneEpochs(SeedlingBatch batch) {
        if (batch == null) return new long[] {0L, 0L, 0L, 0L, 0L};
        long sowing = batch.getSowing_date_epoch();
        long emergence = actualOrEstimate(batch.getGermination_date_epoch(),
                batch.getEstimated_emergence_epoch());
        long transplant = actualOrEstimate(batch.getHardening_date_epoch(),
                batch.getEstimated_transplant_epoch());
        long firstLeafEstimate = firstLeafEpoch(sowing,
                emergence, batch.getEstimated_transplant_epoch());
        long firstLeaf = actualOrEstimate(batch.getFirst_leaf_date_epoch(), firstLeafEstimate);
        long ready = actualOrEstimate(batch.getReady_date_epoch(), 0L);
        if (ready <= 0L && SeedlingStagePolicy.READY.equals(
                SeedlingStagePolicy.normalize(batch.getStage()))) {
            ready = Math.max(transplant, batch.getUpdated_at_epoch());
        }
        return new long[] {sowing, emergence, firstLeaf, transplant, ready};
    }

    private static long actualOrEstimate(Long actual, long estimate) {
        return actual != null && actual > 0L ? actual : estimate;
    }

    private static long firstLeafEpoch(long sowing, long emergence, long transplant) {
        if (sowing <= 0L || transplant <= sowing) return emergence;
        long nurseryDays = Math.max(1L, (transplant - sowing) / DAY_SECONDS);
        long firstLeaf = sowing + ((nurseryDays + 1L) / 2L) * DAY_SECONDS;
        if (emergence > 0L && firstLeaf <= emergence) {
            firstLeaf = Math.min(transplant, emergence + 3L * DAY_SECONDS);
        }
        return firstLeaf;
    }
}
