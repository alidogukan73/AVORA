package com.alidogukan.avora.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class DisplayUnitSettingsTest {
    @Test
    public void invalidUnitCodesFallBackToSafeMetricDefaults() {
        DisplayUnitSettings settings = new DisplayUnitSettings(
                "kelvin", "acre", "inch", "gallon", "pound");

        assertEquals(DisplayUnitSettings.CELSIUS, settings.getTemperature());
        assertEquals(DisplayUnitSettings.SQUARE_METER, settings.getArea());
        assertEquals(DisplayUnitSettings.CENTIMETER, settings.getLength());
        assertEquals(DisplayUnitSettings.LITER, settings.getVolume());
        assertEquals(DisplayUnitSettings.GRAM, settings.getWeight());
        assertTrue(settings.isComplete());
    }

    @Test
    public void emptyFirebaseObjectRemainsIncompleteUntilValuesArrive() {
        DisplayUnitSettings settings = new DisplayUnitSettings();

        assertFalse(settings.isComplete());
        settings.setTemperature(DisplayUnitSettings.FAHRENHEIT);
        settings.setArea(DisplayUnitSettings.DECARE);
        settings.setLength(DisplayUnitSettings.METER);
        settings.setVolume(DisplayUnitSettings.CUBIC_METER);
        settings.setWeight(DisplayUnitSettings.KILOGRAM);

        assertTrue(settings.isComplete());
    }
}
