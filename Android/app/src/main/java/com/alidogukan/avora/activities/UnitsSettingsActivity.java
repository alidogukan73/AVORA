package com.alidogukan.avora.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.ArrayRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.models.DisplayUnitSettings;
import com.alidogukan.avora.ui.PrimaryBottomNavigation;
import com.alidogukan.avora.viewmodels.GardenSettingsViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

/** Controls display units only; all automation calculations remain metric and unchanged. */
public class UnitsSettingsActivity extends AppCompatActivity {
    private GardenSettingsViewModel viewModel;
    private MaterialAutoCompleteTextView temperature;
    private MaterialAutoCompleteTextView area;
    private MaterialAutoCompleteTextView length;
    private MaterialAutoCompleteTextView volume;
    private MaterialAutoCompleteTextView weight;
    private TextView preview;
    private TextView status;
    private boolean applyingValues;
    private boolean dirty;

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_units_settings);
        applyWindowInsets();

        viewModel = new ViewModelProvider(this).get(GardenSettingsViewModel.class);
        bindViews();
        configureToolbar();
        configureDropdowns();
        configureActions();
        PrimaryBottomNavigation.bind(this, PrimaryBottomNavigation.SETTINGS);

        applySettings(viewModel.loadUnits());
        restoreFromCloud();
    }

    private void bindViews() {
        temperature = findViewById(R.id.inputTemperatureUnit);
        area = findViewById(R.id.inputAreaUnit);
        length = findViewById(R.id.inputLengthUnit);
        volume = findViewById(R.id.inputVolumeUnit);
        weight = findViewById(R.id.inputWeightUnit);
        preview = findViewById(R.id.txtUnitsPreview);
        status = findViewById(R.id.txtUnitsStatus);
    }

    private void configureToolbar() {
        ((TextView) findViewById(R.id.txtSettingsToolbarTitle))
                .setText(R.string.settings_units_title);
        findViewById(R.id.btnSettingsToolbarBack).setOnClickListener(view -> requestExit());
        findViewById(R.id.btnSettingsToolbarAction).setVisibility(View.GONE);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { requestExit(); }
        });
    }

    private void configureDropdowns() {
        attach(temperature, R.array.temperature_unit_options);
        attach(area, R.array.area_unit_options);
        attach(length, R.array.length_unit_options);
        attach(volume, R.array.volume_unit_options);
        attach(weight, R.array.weight_unit_options);
    }

    private void attach(MaterialAutoCompleteTextView input, @ArrayRes int options) {
        input.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                getResources().getStringArray(options)));
        input.setOnItemClickListener((parent, view, position, id) -> markDirty());
    }

    private void configureActions() {
        findViewById(R.id.btnSaveUnits).setOnClickListener(view -> save(false));
        findViewById(R.id.btnResetUnits).setOnClickListener(view -> {
            applySettings(defaultSettings());
            dirty = true;
            status.setText(R.string.units_defaults_ready);
        });
    }

    private void restoreFromCloud() {
        viewModel.getCloudUnits().observe(this, cloud -> {
            if (dirty || !viewModel.shouldAcceptCloudUnits(cloud)) return;
            viewModel.acceptCloudUnits(cloud);
            applySettings(cloud);
            status.setText(R.string.units_cloud_loaded);
        });
    }

    private void applySettings(DisplayUnitSettings settings) {
        applyingValues = true;
        temperature.setText(labelForTemperature(settings.getTemperature()), false);
        area.setText(labelForArea(settings.getArea()), false);
        length.setText(labelForLength(settings.getLength()), false);
        volume.setText(labelForVolume(settings.getVolume()), false);
        weight.setText(labelForWeight(settings.getWeight()), false);
        applyingValues = false;
        dirty = false;
        updatePreview(settings);
    }

    private void save(boolean closeAfterSave) {
        DisplayUnitSettings settings = selectedSettings();
        dirty = false;
        updatePreview(settings);
        status.setText(R.string.settings_status_saving);
        viewModel.saveUnits(settings)
                .addOnSuccessListener(unused -> {
                    status.setText(R.string.units_saved);
                    Toast.makeText(this, R.string.units_saved, Toast.LENGTH_SHORT).show();
                    if (closeAfterSave) finish();
                })
                .addOnFailureListener(error -> {
                    status.setText(R.string.units_local_only);
                    Toast.makeText(this, R.string.units_local_only, Toast.LENGTH_LONG).show();
                    if (closeAfterSave) finish();
                });
    }

    private DisplayUnitSettings selectedSettings() {
        return new DisplayUnitSettings(
                valueForTemperature(text(temperature)),
                valueForArea(text(area)),
                valueForLength(text(length)),
                valueForVolume(text(volume)),
                valueForWeight(text(weight))
        );
    }

    private void markDirty() {
        if (applyingValues) return;
        dirty = true;
        updatePreview(selectedSettings());
        status.setText(R.string.settings_status_unsaved);
    }

    private void updatePreview(DisplayUnitSettings settings) {
        preview.setText(getString(R.string.units_preview_format,
                DisplayUnitSettings.FAHRENHEIT.equals(settings.getTemperature()) ? "68 °F" : "20 °C",
                DisplayUnitSettings.DECARE.equals(settings.getArea()) ? "1 da" : "1000 m²",
                DisplayUnitSettings.METER.equals(settings.getLength()) ? "1 m" : "100 cm",
                DisplayUnitSettings.CUBIC_METER.equals(settings.getVolume()) ? "1 m³" : "1000 L",
                DisplayUnitSettings.KILOGRAM.equals(settings.getWeight()) ? "1 kg" : "1000 g"));
    }

    private void requestExit() {
        if (!dirty) {
            finish();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_unsaved_dialog_title)
                .setMessage(R.string.settings_unsaved_dialog_message)
                .setNegativeButton(R.string.settings_continue_editing, null)
                .setNeutralButton(R.string.settings_discard_changes, (dialog, which) -> finish())
                .setPositiveButton(R.string.settings_save_and_exit, (dialog, which) -> save(true))
                .show();
    }

    private DisplayUnitSettings defaultSettings() {
        return new DisplayUnitSettings(DisplayUnitSettings.CELSIUS,
                DisplayUnitSettings.SQUARE_METER, DisplayUnitSettings.CENTIMETER,
                DisplayUnitSettings.LITER, DisplayUnitSettings.GRAM);
    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.unitsSettingsRoot),
                (view, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });
    }

    private String text(MaterialAutoCompleteTextView input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private String labelForTemperature(String value) {
        return getString(DisplayUnitSettings.FAHRENHEIT.equals(value)
                ? R.string.unit_temperature_fahrenheit : R.string.unit_temperature_celsius);
    }

    private String labelForArea(String value) {
        return getString(DisplayUnitSettings.DECARE.equals(value)
                ? R.string.unit_area_decare : R.string.unit_area_square_meter);
    }

    private String labelForLength(String value) {
        return getString(DisplayUnitSettings.METER.equals(value)
                ? R.string.unit_length_meter : R.string.unit_length_centimeter);
    }

    private String labelForVolume(String value) {
        return getString(DisplayUnitSettings.CUBIC_METER.equals(value)
                ? R.string.unit_volume_cubic_meter : R.string.unit_volume_liter);
    }

    private String labelForWeight(String value) {
        return getString(DisplayUnitSettings.KILOGRAM.equals(value)
                ? R.string.unit_weight_kilogram : R.string.unit_weight_gram);
    }

    private String valueForTemperature(String label) {
        return label.equals(getString(R.string.unit_temperature_fahrenheit))
                ? DisplayUnitSettings.FAHRENHEIT : DisplayUnitSettings.CELSIUS;
    }

    private String valueForArea(String label) {
        return label.equals(getString(R.string.unit_area_decare))
                ? DisplayUnitSettings.DECARE : DisplayUnitSettings.SQUARE_METER;
    }

    private String valueForLength(String label) {
        return label.equals(getString(R.string.unit_length_meter))
                ? DisplayUnitSettings.METER : DisplayUnitSettings.CENTIMETER;
    }

    private String valueForVolume(String label) {
        return label.equals(getString(R.string.unit_volume_cubic_meter))
                ? DisplayUnitSettings.CUBIC_METER : DisplayUnitSettings.LITER;
    }

    private String valueForWeight(String label) {
        return label.equals(getString(R.string.unit_weight_kilogram))
                ? DisplayUnitSettings.KILOGRAM : DisplayUnitSettings.GRAM;
    }
}
