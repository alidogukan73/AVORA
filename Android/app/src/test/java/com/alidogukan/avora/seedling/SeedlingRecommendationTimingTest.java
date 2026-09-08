package com.alidogukan.avora.seedling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.alidogukan.avora.models.SeedlingRecommendation;

import org.junit.Test;

public final class SeedlingRecommendationTimingTest {
    private static final String LOW_LIGHT_REASON = "Işık seviyesi düşük.";
    private static final String LOW_LIGHT_ACTION =
            "Fideleri daha aydınlık konuma alın veya uygun yetiştirme ışığı kullanın.";

    @Test public void daylightKeepsLowLightAdvice() {
        SeedlingRecommendation advice = advice(
                LOW_LIGHT_ACTION,
                LOW_LIGHT_REASON
        );

        assertEquals(LOW_LIGHT_ACTION,
                SeedlingRecommendationTiming.actionForHour(advice, 12));
        assertEquals(LOW_LIGHT_REASON,
                SeedlingRecommendationTiming.messageForHour(advice, 12));
    }

    @Test public void nightRemovesOnlyLightAdvice() {
        SeedlingRecommendation advice = advice(
                LOW_LIGHT_ACTION + " Nem kaybını azaltın.",
                LOW_LIGHT_REASON + " Hava nemi düşük."
        );

        assertEquals("Nem kaybını azaltın.",
                SeedlingRecommendationTiming.actionForHour(advice, 22));
        assertEquals("Hava nemi düşük.",
                SeedlingRecommendationTiming.messageForHour(advice, 22));
    }

    @Test public void nightRemovesPureLowLightAdviceCompletely() {
        SeedlingRecommendation advice = advice(
                LOW_LIGHT_ACTION,
                LOW_LIGHT_REASON
        );

        assertTrue(SeedlingRecommendationTiming.actionForHour(advice, 22).isBlank());
        assertTrue(SeedlingRecommendationTiming.messageForHour(advice, 22).isBlank());
    }

    @Test public void lightWindowIsEightUntilEighteen() {
        assertFalse(SeedlingRecommendationTiming.shouldEvaluateLight(7));
        assertTrue(SeedlingRecommendationTiming.shouldEvaluateLight(8));
        assertTrue(SeedlingRecommendationTiming.shouldEvaluateLight(17));
        assertFalse(SeedlingRecommendationTiming.shouldEvaluateLight(18));
    }

    private static SeedlingRecommendation advice(String action, String message) {
        SeedlingRecommendation value = new SeedlingRecommendation();
        value.setAction(action);
        value.setMessage(message);
        return value;
    }
}
