package com.alidogukan.avora.settings;

import com.alidogukan.avora.models.DisplayUnitSettings;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Pure display/input conversion. Persisted measurements always remain metric. */
public final class DisplayUnitFormatter {
    private final DisplayUnitSettings settings;
    private final Locale locale;

    public DisplayUnitFormatter(DisplayUnitSettings settings) {
        this(settings, Locale.getDefault());
    }

    public static DisplayUnitFormatter metric() {
        return new DisplayUnitFormatter(defaults());
    }

    DisplayUnitFormatter(DisplayUnitSettings settings, Locale locale) {
        this.settings = settings == null ? defaults() : settings;
        this.locale = locale == null ? Locale.getDefault() : locale;
    }

    public double temperatureFromCelsius(double value) {
        return DisplayUnitSettings.FAHRENHEIT.equals(settings.getTemperature())
                ? value * 9d / 5d + 32d : value;
    }

    public double temperatureToCelsius(double value) {
        return DisplayUnitSettings.FAHRENHEIT.equals(settings.getTemperature())
                ? (value - 32d) * 5d / 9d : value;
    }

    public double areaFromSquareMeters(double value) {
        return DisplayUnitSettings.DECARE.equals(settings.getArea()) ? value / 1000d : value;
    }

    public double areaToSquareMeters(double value) {
        return DisplayUnitSettings.DECARE.equals(settings.getArea()) ? value * 1000d : value;
    }

    public double lengthFromCentimeters(double value) {
        return DisplayUnitSettings.METER.equals(settings.getLength()) ? value / 100d : value;
    }

    public double lengthToCentimeters(double value) {
        return DisplayUnitSettings.METER.equals(settings.getLength()) ? value * 100d : value;
    }

    public double volumeFromLiters(double value) {
        return DisplayUnitSettings.CUBIC_METER.equals(settings.getVolume()) ? value / 1000d : value;
    }

    public double volumeToLiters(double value) {
        return DisplayUnitSettings.CUBIC_METER.equals(settings.getVolume()) ? value * 1000d : value;
    }

    public double weightFromGrams(double value) {
        return DisplayUnitSettings.KILOGRAM.equals(settings.getWeight()) ? value / 1000d : value;
    }

    public double weightToGrams(double value) {
        return DisplayUnitSettings.KILOGRAM.equals(settings.getWeight()) ? value * 1000d : value;
    }

    public String temperatureSymbol() {
        return DisplayUnitSettings.FAHRENHEIT.equals(settings.getTemperature()) ? "°F" : "°C";
    }

    public String areaSymbol() {
        return DisplayUnitSettings.DECARE.equals(settings.getArea()) ? "da" : "m²";
    }

    public String lengthSymbol() {
        return DisplayUnitSettings.METER.equals(settings.getLength()) ? "m" : "cm";
    }

    public String volumeSymbol() {
        return DisplayUnitSettings.CUBIC_METER.equals(settings.getVolume()) ? "m³" : "L";
    }

    public String weightSymbol() {
        return DisplayUnitSettings.KILOGRAM.equals(settings.getWeight()) ? "kg" : "g";
    }

    public String formatTemperature(double celsius) {
        return number(temperatureFromCelsius(celsius)) + " " + temperatureSymbol();
    }

    public String formatTemperatureRange(double minCelsius, double maxCelsius) {
        return number(temperatureFromCelsius(minCelsius)) + "–"
                + number(temperatureFromCelsius(maxCelsius)) + " " + temperatureSymbol();
    }

    public String formatArea(double squareMeters) {
        return number(areaFromSquareMeters(squareMeters)) + " " + areaSymbol();
    }

    public String formatLength(double centimeters) {
        return number(lengthFromCentimeters(centimeters)) + " " + lengthSymbol();
    }

    public String formatVolume(double liters) {
        return number(volumeFromLiters(liters)) + " " + volumeSymbol();
    }

    public String formatWeight(double grams) {
        return number(weightFromGrams(grams)) + " " + weightSymbol();
    }

    public String formatEditableArea(double squareMeters) {
        return editable(areaFromSquareMeters(squareMeters));
    }

    public String formatEditableLength(double centimeters) {
        return editable(lengthFromCentimeters(centimeters));
    }

    public String formatEditableVolume(double liters) {
        return editable(volumeFromLiters(liters));
    }

    private String number(double value) {
        DecimalFormat format = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(locale));
        format.setGroupingUsed(false);
        return format.format(value);
    }

    private String editable(double value) {
        DecimalFormat format = new DecimalFormat("0.######", DecimalFormatSymbols.getInstance(Locale.US));
        format.setGroupingUsed(false);
        return format.format(value);
    }

    private static DisplayUnitSettings defaults() {
        return new DisplayUnitSettings(DisplayUnitSettings.CELSIUS,
                DisplayUnitSettings.SQUARE_METER, DisplayUnitSettings.CENTIMETER,
                DisplayUnitSettings.LITER, DisplayUnitSettings.GRAM);
    }
}
