package com.alidogukan.avora.settings;

import android.content.Context;
import android.content.SharedPreferences;

import com.alidogukan.avora.models.DisplayUnitSettings;

/**
 * Stores display-only units. Sensor, irrigation and Firebase measurements keep their
 * canonical metric values so changing a unit can never change an automation decision.
 */
public final class UnitPreferences {
    public static final String CELSIUS = DisplayUnitSettings.CELSIUS;
    public static final String FAHRENHEIT = DisplayUnitSettings.FAHRENHEIT;
    public static final String SQUARE_METER = DisplayUnitSettings.SQUARE_METER;
    public static final String DECARE = DisplayUnitSettings.DECARE;
    public static final String CENTIMETER = DisplayUnitSettings.CENTIMETER;
    public static final String METER = DisplayUnitSettings.METER;
    public static final String LITER = DisplayUnitSettings.LITER;
    public static final String CUBIC_METER = DisplayUnitSettings.CUBIC_METER;
    public static final String GRAM = DisplayUnitSettings.GRAM;
    public static final String KILOGRAM = DisplayUnitSettings.KILOGRAM;

    private static final String PREFS = "avora_display_units";
    private static final String KEY_TEMPERATURE = "temperature";
    private static final String KEY_AREA = "area";
    private static final String KEY_LENGTH = "length";
    private static final String KEY_VOLUME = "volume";
    private static final String KEY_WEIGHT = "weight";
    private static final String KEY_UPDATED_AT = "updated_at_epoch";

    private final SharedPreferences preferences;

    public UnitPreferences(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public DisplayUnitSettings load() {
        DisplayUnitSettings settings = new DisplayUnitSettings(
                preferences.getString(KEY_TEMPERATURE, CELSIUS),
                preferences.getString(KEY_AREA, SQUARE_METER),
                preferences.getString(KEY_LENGTH, CENTIMETER),
                preferences.getString(KEY_VOLUME, LITER),
                preferences.getString(KEY_WEIGHT, GRAM)
        );
        settings.setUpdated_at_epoch(preferences.getLong(KEY_UPDATED_AT, 0L));
        return settings;
    }

    public boolean hasSavedValues() {
        return preferences.contains(KEY_TEMPERATURE)
                && preferences.contains(KEY_AREA)
                && preferences.contains(KEY_LENGTH)
                && preferences.contains(KEY_VOLUME)
                && preferences.contains(KEY_WEIGHT);
    }
    public void save(DisplayUnitSettings settings) {
        preferences.edit()
                .putString(KEY_TEMPERATURE, safe(settings.getTemperature(), CELSIUS))
                .putString(KEY_AREA, safe(settings.getArea(), SQUARE_METER))
                .putString(KEY_LENGTH, safe(settings.getLength(), CENTIMETER))
                .putString(KEY_VOLUME, safe(settings.getVolume(), LITER))
                .putString(KEY_WEIGHT, safe(settings.getWeight(), GRAM))
                .putLong(KEY_UPDATED_AT, Math.max(0L, settings.getUpdated_at_epoch()))
                .apply();
    }

    public void reset() {
        preferences.edit().clear().apply();
    }

    public double areaFromSquareMeters(double value) {
        return formatter().areaFromSquareMeters(value);
    }

    public double areaToSquareMeters(double displayedValue) {
        return formatter().areaToSquareMeters(displayedValue);
    }

    public String areaSymbol() {
        return formatter().areaSymbol();
    }

    public String formatTemperature(double celsius) {
        return formatter().formatTemperature(celsius);
    }

    public String formatArea(double squareMeters) {
        return formatter().formatArea(squareMeters);
    }

    public String formatLength(double centimeters) {
        return formatter().formatLength(centimeters);
    }

    public String formatVolume(double liters) {
        return formatter().formatVolume(liters);
    }

    public String formatWeight(double grams) {
        return formatter().formatWeight(grams);
    }

    public DisplayUnitFormatter formatter() { return new DisplayUnitFormatter(load()); }

    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
