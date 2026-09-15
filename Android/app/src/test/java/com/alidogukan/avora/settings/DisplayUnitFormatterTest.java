package com.alidogukan.avora.settings;

import static org.junit.Assert.assertEquals;

import com.alidogukan.avora.models.DisplayUnitSettings;

import org.junit.Test;

import java.util.Locale;

public final class DisplayUnitFormatterTest {
    private static final double DELTA = 0.000001d;

    @Test
    public void temperatureConvertsBothDirections() {
        DisplayUnitFormatter units = formatter(DisplayUnitSettings.FAHRENHEIT,
                DisplayUnitSettings.SQUARE_METER, DisplayUnitSettings.CENTIMETER,
                DisplayUnitSettings.LITER, DisplayUnitSettings.GRAM);

        assertEquals(32d, units.temperatureFromCelsius(0d), DELTA);
        assertEquals(68d, units.temperatureFromCelsius(20d), DELTA);
        assertEquals(212d, units.temperatureFromCelsius(100d), DELTA);
        assertEquals(20d, units.temperatureToCelsius(68d), DELTA);
        assertEquals("68 °F", units.formatTemperature(20d));
    }

    @Test
    public void areaLengthVolumeAndWeightConvertBothDirections() {
        DisplayUnitFormatter units = formatter(DisplayUnitSettings.CELSIUS,
                DisplayUnitSettings.DECARE, DisplayUnitSettings.METER,
                DisplayUnitSettings.CUBIC_METER, DisplayUnitSettings.KILOGRAM);

        assertEquals(1d, units.areaFromSquareMeters(1000d), DELTA);
        assertEquals(1000d, units.areaToSquareMeters(1d), DELTA);
        assertEquals(1d, units.lengthFromCentimeters(100d), DELTA);
        assertEquals(100d, units.lengthToCentimeters(1d), DELTA);
        assertEquals(1d, units.volumeFromLiters(1000d), DELTA);
        assertEquals(1000d, units.volumeToLiters(1d), DELTA);
        assertEquals(1d, units.weightFromGrams(1000d), DELTA);
        assertEquals(1000d, units.weightToGrams(1d), DELTA);
    }

    @Test
    public void editableValuesPreserveSmallCanonicalMeasurements() {
        DisplayUnitFormatter units = formatter(DisplayUnitSettings.CELSIUS,
                DisplayUnitSettings.DECARE, DisplayUnitSettings.METER,
                DisplayUnitSettings.CUBIC_METER, DisplayUnitSettings.GRAM);

        assertEquals("0.001", units.formatEditableArea(1d));
        assertEquals(1d, units.areaToSquareMeters(
                Double.parseDouble(units.formatEditableArea(1d))), DELTA);
        assertEquals("0.01", units.formatEditableLength(1d));
        assertEquals(1d, units.lengthToCentimeters(
                Double.parseDouble(units.formatEditableLength(1d))), DELTA);
        assertEquals("0.001", units.formatEditableVolume(1d));
        assertEquals(1d, units.volumeToLiters(
                Double.parseDouble(units.formatEditableVolume(1d))), DELTA);
    }

    @Test
    public void metricDefaultsDoNotChangeCanonicalValues() {
        DisplayUnitFormatter units = DisplayUnitFormatter.metric();

        assertEquals(35d, units.temperatureFromCelsius(35d), DELTA);
        assertEquals(25d, units.areaFromSquareMeters(25d), DELTA);
        assertEquals(12.5d, units.lengthFromCentimeters(12.5d), DELTA);
        assertEquals(80d, units.volumeFromLiters(80d), DELTA);
        assertEquals(250d, units.weightFromGrams(250d), DELTA);
    }

    private static DisplayUnitFormatter formatter(String temperature, String area,
                                                   String length, String volume,
                                                   String weight) {
        return new DisplayUnitFormatter(new DisplayUnitSettings(
                temperature, area, length, volume, weight), Locale.US);
    }
}
