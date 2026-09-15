package com.alidogukan.avora.seedling;

import androidx.annotation.Nullable;

import com.alidogukan.avora.models.SeedlingBatch;
import com.alidogukan.avora.models.SeedlingDailyLog;

/** Shared limits kept in sync with the Firebase seedling schema. */
public final class SeedlingValidation {
    public static final int MAX_SEED_COUNT = 10_000;
    public static final int MAX_TRAY_CELL_COUNT = 10_000;
    public static final double MAX_HEIGHT_CM = 500d;
    public static final int MAX_LEAF_COUNT = 1_000;
    public static final int MAX_NOTE_LENGTH = 1_000;
    public static final long MAX_EPOCH_SECONDS = 4_102_444_800L;

    private SeedlingValidation() { }

    public static boolean isValidNewBatch(@Nullable SeedlingBatch batch) {
        if (batch == null || batch.getPlant_type().isBlank()
                || batch.getPlant_type().length() > 80
                || batch.getEmoji().length() > 16
                || batch.getVariety().length() > 80
                || batch.getArea().length() > 120
                || batch.getCrop_id().length() > 120
                || !batch.getNode_id().matches("^seedling-[0-9]{3}$")) {
            return false;
        }
        if (batch.getSeed_count() <= 0 || batch.getSeed_count() > MAX_SEED_COUNT) {
            return false;
        }
        if (batch.getTray_cell_count() < 0
                || batch.getTray_cell_count() > MAX_TRAY_CELL_COUNT) {
            return false;
        }
        long sowing = batch.getSowing_date_epoch();
        long emergence = batch.getEstimated_emergence_epoch();
        long transplant = batch.getEstimated_transplant_epoch();
        long created = batch.getCreated_at_epoch();
        long updated = batch.getUpdated_at_epoch();
        return batch.getHealthy_count() >= 0
                && batch.getHealthy_count() <= batch.getSeed_count()
                && sowing > 0L && sowing <= MAX_EPOCH_SECONDS
                && emergence >= sowing && emergence <= MAX_EPOCH_SECONDS
                && transplant >= emergence && transplant <= MAX_EPOCH_SECONDS
                && created > 0L && created <= MAX_EPOCH_SECONDS
                && updated >= created && updated <= MAX_EPOCH_SECONDS;
    }

    public static boolean isValidLog(@Nullable SeedlingDailyLog log,
                                     @Nullable SeedlingBatch batch) {
        return batch != null && batch.isActive() && hasValidLogValues(log, batch);
    }

    /** Allows correcting an existing observation even after its batch is archived. */
    public static boolean isValidLogUpdate(@Nullable SeedlingDailyLog log,
                                           @Nullable SeedlingBatch batch,
                                           @Nullable SeedlingDailyLog persisted) {
        return canEditLog(batch, persisted)
                && log != null
                && log.getLog_id().equals(persisted.getLog_id())
                && log.getBatch_id().equals(persisted.getBatch_id())
                && log.getCreated_at_epoch() == persisted.getCreated_at_epoch()
                && hasValidLogValues(log, batch);
    }

    /** Existing records stay correctable; new records still require an active batch. */
    public static boolean canEditLog(@Nullable SeedlingBatch batch,
                                     @Nullable SeedlingDailyLog log) {
        return batch != null
                && (batch.isActive() || batch.isArchived())
                && log != null
                && !log.getLog_id().isBlank()
                && !log.getBatch_id().isBlank()
                && log.getBatch_id().equals(batch.getBatch_id());
    }

    private static boolean hasValidLogValues(@Nullable SeedlingDailyLog log,
                                             SeedlingBatch batch) {
        if (log == null) return false;
        return Double.isFinite(log.getHeight_cm())
                && log.getHeight_cm() >= 0d && log.getHeight_cm() <= MAX_HEIGHT_CM
                && log.getLeaf_count() >= 0 && log.getLeaf_count() <= MAX_LEAF_COUNT
                && log.getHealthy_count() >= 0
                && log.getHealthy_count() <= batch.getSeed_count()
                && log.getNote().length() <= MAX_NOTE_LENGTH
                && log.getCreated_at_epoch() > 0L
                && log.getCreated_at_epoch() <= MAX_EPOCH_SECONDS;
    }
}
