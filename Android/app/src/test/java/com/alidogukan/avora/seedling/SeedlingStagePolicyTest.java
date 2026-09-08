package com.alidogukan.avora.seedling;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public final class SeedlingStagePolicyTest {
    @Test public void stagesAdvanceInOneDirection() {
        assertEquals(SeedlingStagePolicy.GERMINATING,
                SeedlingStagePolicy.next(SeedlingStagePolicy.SOWN));
        assertEquals(SeedlingStagePolicy.TRUE_LEAVES,
                SeedlingStagePolicy.next(SeedlingStagePolicy.COTYLEDON));
        assertEquals(SeedlingStagePolicy.READY,
                SeedlingStagePolicy.next(SeedlingStagePolicy.READY));
    }
    @Test public void unknownStageSafelyStartsAtSown() {
        assertEquals(SeedlingStagePolicy.SOWN, SeedlingStagePolicy.normalize("bad"));
        assertEquals(1, SeedlingStagePolicy.progress(null));
    }
    @Test public void readyStageCannotAdvance() {
        assertTrue(SeedlingStagePolicy.canAdvance(SeedlingStagePolicy.HARDENING));
        assertFalse(SeedlingStagePolicy.canAdvance(SeedlingStagePolicy.READY));
    }
    @Test public void stagesCanReturnOneStepWithoutPassingSown() {
        assertEquals(SeedlingStagePolicy.SOWN,
                SeedlingStagePolicy.previous(SeedlingStagePolicy.GERMINATING));
        assertEquals(SeedlingStagePolicy.HARDENING,
                SeedlingStagePolicy.previous(SeedlingStagePolicy.READY));
        assertEquals(SeedlingStagePolicy.SOWN,
                SeedlingStagePolicy.previous(SeedlingStagePolicy.SOWN));
        assertTrue(SeedlingStagePolicy.canRetreat(SeedlingStagePolicy.GERMINATING));
        assertFalse(SeedlingStagePolicy.canRetreat(SeedlingStagePolicy.SOWN));
    }
}
