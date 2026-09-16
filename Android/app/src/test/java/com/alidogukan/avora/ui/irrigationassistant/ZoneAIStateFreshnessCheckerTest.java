package com.alidogukan.avora.ui.irrigationassistant;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDateTime;

public class ZoneAIStateFreshnessCheckerTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 15, 21, 0);

    @Test
    public void recentResultIsFresh() {
        assertTrue(ZoneAIStateFreshnessChecker.isFresh(
                "2026-09-15T20:58:00",
                now
        ));
    }

    @Test
    public void staleMalformedAndFutureResultsAreRejected() {
        assertFalse(ZoneAIStateFreshnessChecker.isFresh(
                "2026-09-15T20:56:59",
                now
        ));
        assertFalse(ZoneAIStateFreshnessChecker.isFresh("invalid", now));
        assertFalse(ZoneAIStateFreshnessChecker.isFresh(
                "2026-09-15T21:01:01",
                now
        ));
    }
}
