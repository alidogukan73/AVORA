package com.alidogukan.avora.models;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class RainSettingsTest {
    @Test
    public void nonFiniteThresholdsUseSafetyDefaults() {
        RainSettings settings = new RainSettings(
                true, Double.NaN, Double.POSITIVE_INFINITY, -1L);

        assertEquals(RainSettings.DEFAULT_RAIN_PROBABILITY,
                settings.getRainProbability(), 0d);
        assertEquals(RainSettings.DEFAULT_RAIN_MM, settings.getRainMm(), 0d);
        assertEquals(0L, settings.getUpdatedAtEpoch());
    }

    @Test
    public void thresholdsRemainInsideSupportedRange() {
        RainSettings settings = new RainSettings(true, 20d, 30d, 10L);

        assertEquals(50d, settings.getRainProbability(), 0d);
        assertEquals(10d, settings.getRainMm(), 0d);
    }
}
