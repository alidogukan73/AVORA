package com.alidogukan.avora.seedling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SeedlingConditionSummaryTest {
    @Test public void rendersEveryConditionOnItsOwnLine() {
        assertEquals(
                "• Ortam sıcaklığı fideler için çok yüksek.\n"
                        + "• Yetiştirme ortamı fazla ıslak.",
                SeedlingConditionSummary.bulletList(
                        "Ortam sıcaklığı fideler için çok yüksek. "
                                + "Yetiştirme ortamı fazla ıslak."));
    }

    @Test public void removesDuplicateConditionsAndHandlesEmptyInput() {
        assertEquals(
                "• Hava nemi düşük.",
                SeedlingConditionSummary.bulletList(
                        "Hava nemi düşük. Hava nemi düşük."));
        assertTrue(SeedlingConditionSummary.bulletList("  ").isBlank());
    }
}
