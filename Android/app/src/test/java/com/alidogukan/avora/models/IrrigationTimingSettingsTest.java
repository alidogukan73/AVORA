package com.alidogukan.avora.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class IrrigationTimingSettingsTest {
    @Test
    public void unknownFirebaseCodesReturnToSafeDefaults() {
        IrrigationTimingSettings settings = new IrrigationTimingSettings();

        settings.setGardenEnvironment("balcony");
        settings.setTimingStrategy("night_only");

        assertEquals(IrrigationTimingSettings.DEFAULT_GARDEN_ENVIRONMENT,
                settings.getGardenEnvironment());
        assertEquals(IrrigationTimingSettings.DEFAULT_TIMING_STRATEGY,
                settings.getTimingStrategy());
    }

    @Test
    public void mandatorySafetyRecheckCannotBeDisabled() {
        IrrigationTimingSettings settings = new IrrigationTimingSettings();

        settings.setTimingRecheckEnabled(false);

        assertTrue(settings.isTimingRecheckEnabled());
    }
}
