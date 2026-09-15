package com.alidogukan.avora.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.alidogukan.avora.settings.DisplayUnitFormatter;
import com.alidogukan.avora.settings.UnitPreferences;

/** Activity-safe access to the current display-unit preferences. */
public final class DisplayUnitsViewModel extends AndroidViewModel {
    private final UnitPreferences preferences;

    public DisplayUnitsViewModel(@NonNull Application application) {
        super(application);
        preferences = new UnitPreferences(application);
    }

    private DisplayUnitFormatter formatter() { return preferences.formatter(); }
    public DisplayUnitFormatter formatterSnapshot() { return formatter(); }
    public String formatTemperature(double value) { return formatter().formatTemperature(value); }
    public String formatTemperatureRange(double min, double max) { return formatter().formatTemperatureRange(min, max); }
    public String temperatureSymbol() { return formatter().temperatureSymbol(); }
    public String formatArea(double value) { return formatter().formatArea(value); }
    public String formatEditableArea(double value) { return formatter().formatEditableArea(value); }
    public double areaFromSquareMeters(double value) { return formatter().areaFromSquareMeters(value); }
    public double areaToSquareMeters(double value) { return formatter().areaToSquareMeters(value); }
    public String areaSymbol() { return formatter().areaSymbol(); }
    public String formatLength(double value) { return formatter().formatLength(value); }
    public String formatEditableLength(double value) { return formatter().formatEditableLength(value); }
    public double lengthFromCentimeters(double value) { return formatter().lengthFromCentimeters(value); }
    public double lengthToCentimeters(double value) { return formatter().lengthToCentimeters(value); }
    public String lengthSymbol() { return formatter().lengthSymbol(); }
    public String formatVolume(double value) { return formatter().formatVolume(value); }
    public String formatEditableVolume(double value) { return formatter().formatEditableVolume(value); }
    public double volumeFromLiters(double value) { return formatter().volumeFromLiters(value); }
    public double volumeToLiters(double value) { return formatter().volumeToLiters(value); }
    public String volumeSymbol() { return formatter().volumeSymbol(); }
    public String formatWeight(double value) { return formatter().formatWeight(value); }
    public double weightFromGrams(double value) { return formatter().weightFromGrams(value); }
    public double weightToGrams(double value) { return formatter().weightToGrams(value); }
    public String weightSymbol() { return formatter().weightSymbol(); }
}
