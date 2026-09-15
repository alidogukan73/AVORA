package com.alidogukan.avora.seedling;

import androidx.annotation.Nullable;

import com.alidogukan.avora.models.SeedlingBatch;

/** Defines when a seedling batch may consume and present live node telemetry. */
public final class SeedlingSensorTrackingPolicy {
    private SeedlingSensorTrackingPolicy() { }

    public static boolean shouldObserveLive(@Nullable SeedlingBatch batch) {
        return batch != null && batch.isActive();
    }
}
