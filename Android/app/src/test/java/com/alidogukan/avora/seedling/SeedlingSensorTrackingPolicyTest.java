package com.alidogukan.avora.seedling;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.alidogukan.avora.models.SeedlingBatch;

import org.junit.Test;

public final class SeedlingSensorTrackingPolicyTest {
    @Test public void onlyActiveBatchUsesLiveTelemetry() {
        SeedlingBatch batch = new SeedlingBatch();

        batch.setStatus(SeedlingBatch.STATUS_ACTIVE);
        assertTrue(SeedlingSensorTrackingPolicy.shouldObserveLive(batch));

        batch.setStatus(SeedlingBatch.STATUS_ARCHIVED);
        assertFalse(SeedlingSensorTrackingPolicy.shouldObserveLive(batch));

        batch.setStatus(SeedlingBatch.STATUS_TRANSFERRED);
        assertFalse(SeedlingSensorTrackingPolicy.shouldObserveLive(batch));

        assertFalse(SeedlingSensorTrackingPolicy.shouldObserveLive(null));
    }
}
