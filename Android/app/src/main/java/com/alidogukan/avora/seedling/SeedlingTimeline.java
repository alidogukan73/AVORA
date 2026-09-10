package com.alidogukan.avora.seedling;

import com.alidogukan.avora.models.SeedlingBatch;

/** Maps the six internal nursery states onto five visible, crop-aware milestones. */
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
        SeedlingCropCatalog.Profile profile =
                SeedlingCropCatalog.profileForPlant(batch.getPlant_type());
        long sowing = batch.getSowing_date_epoch();
        long plannedEmergence = batch.getEstimated_emergence_epoch() > 0L
                ? batch.getEstimated_emergence_epoch()
                : plusDays(sowing, profile.getEmergenceDays());
        long emergence = actualOrEstimate(batch.getGermination_date_epoch(),
                plannedEmergence);

        long firstLeafEstimate = plusDays(emergence,
                profile.getFirstLeafAfterEmergenceDays());
        long firstLeaf = actualOrEstimate(batch.getFirst_leaf_date_epoch(), firstLeafEstimate);

        long hardeningEstimate = hardeningEstimate(batch, profile,
                emergence, firstLeaf, sowing);
        long hardening = actualOrEstimate(batch.getHardening_date_epoch(),
                hardeningEstimate);
        long readyEstimate = plusDays(hardening, profile.getReadyAfterHardeningDays());
        long ready = actualOrEstimate(batch.getReady_date_epoch(), readyEstimate);
        return new long[] {sowing, emergence, firstLeaf, hardening, ready};
    }

    private static long actualOrEstimate(Long actual, long estimate) {
        return actual != null && actual > 0L ? actual : estimate;
    }

    private static long hardeningEstimate(SeedlingBatch batch,
                                          SeedlingCropCatalog.Profile profile,
                                          long emergence, long firstLeaf,
                                          long sowing) {
        Long trueLeaves = batch.getTrue_leaves_date_epoch();
        if (isActual(trueLeaves)) {
            return plusDays(trueLeaves, profile.getHardeningAfterTrueLeavesDays());
        }
        if (isActual(batch.getFirst_leaf_date_epoch())) {
            return plusDays(firstLeaf, profile.getTrueLeavesAfterFirstLeafDays()
                    + profile.getHardeningAfterTrueLeavesDays());
        }
        if (isActual(batch.getGermination_date_epoch())) {
            return plusDays(emergence, profile.getFirstLeafAfterEmergenceDays()
                    + profile.getTrueLeavesAfterFirstLeafDays()
                    + profile.getHardeningAfterTrueLeavesDays());
        }
        if (batch.getEstimated_transplant_epoch() > 0L) {
            return batch.getEstimated_transplant_epoch();
        }
        return plusDays(sowing, profile.getTransplantDays());
    }

    private static boolean isActual(Long value) {
        return value != null && value > 0L;
    }

    private static long plusDays(long epoch, int days) {
        if (epoch <= 0L) return 0L;
        return epoch + Math.max(0L, days) * DAY_SECONDS;
    }
}
