package com.alidogukan.avora.journal;

import org.junit.Test;
import static org.junit.Assert.*;

public class JournalEntryPolicyTest {
    @Test public void rejectsDatesOutsideActiveSeasonAndFuture() {
        assertFalse(JournalEntryPolicy.validDate(0, 100, 200));
        assertFalse(JournalEntryPolicy.validDate(99, 100, 200));
        assertTrue(JournalEntryPolicy.validDate(100, 100, 200));
        assertTrue(JournalEntryPolicy.validDate(200, 100, 200));
        assertFalse(JournalEntryPolicy.validDate(201, 100, 200));
        assertTrue(JournalEntryPolicy.validDate(50, 0, 200));
    }

    @Test public void realOperationsCannotBecomeManualEvents() {
        assertFalse(JournalEntryPolicy.isManualEvent("watering"));
        assertFalse(JournalEntryPolicy.isManualEvent("fertilization"));
        assertFalse(JournalEntryPolicy.isManualEvent("photo"));
        assertFalse(JournalEntryPolicy.isManualEvent(null));
        assertTrue(JournalEntryPolicy.isManualEvent("observation"));
        assertTrue(JournalEntryPolicy.isManualEvent("harvest"));
        assertTrue(JournalEntryPolicy.isManualEvent("flowering"));
    }
}
