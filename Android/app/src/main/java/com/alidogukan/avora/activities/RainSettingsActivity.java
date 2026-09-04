package com.alidogukan.avora.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.models.RainSettings;
import com.alidogukan.avora.models.WeatherForecast;
import com.alidogukan.avora.models.WeatherLocation;
import com.alidogukan.avora.ui.PrimaryBottomNavigation;
import com.alidogukan.avora.viewmodels.GardenSettingsViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

import java.util.Locale;

/** Controls the safe influence of forecast rain on automatic irrigation. */
public class RainSettingsActivity extends AppCompatActivity {
    private GardenSettingsViewModel viewModel;
    private MaterialSwitch rainDelayEnabled;
    private MaterialSwitch weatherNotifications;
    private Slider rainProbability;
    private Slider rainMm;
    private TextView probabilityValue;
    private TextView rainMmValue;
    private TextView locationSummary;
    private TextView forecastSummary;
    private TextView sourceSummary;
    private TextView status;

    private boolean applyingValues;
    private boolean settingsLoaded;
    private boolean dirty;
    private boolean savedRainDelayEnabled = RainSettings.DEFAULT_RAIN_DELAY_ENABLED;
    private boolean savedWeatherNotifications = true;
    private double savedRainProbability = RainSettings.DEFAULT_RAIN_PROBABILITY;
    private double savedRainMm = RainSettings.DEFAULT_RAIN_MM;

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_rain_settings);
        applyWindowInsets();

        viewModel = new ViewModelProvider(this).get(GardenSettingsViewModel.class);
        bindViews();
        configureToolbar();
        configureActions();
        PrimaryBottomNavigation.bind(this, PrimaryBottomNavigation.SETTINGS);
        observeLiveData();
    }

    private void bindViews() {
        rainDelayEnabled = findViewById(R.id.switchRainDelayEnabled);
        weatherNotifications = findViewById(R.id.switchWeatherNotifications);
        rainProbability = findViewById(R.id.sliderRainProbability);
        rainMm = findViewById(R.id.sliderRainMm);
        probabilityValue = findViewById(R.id.txtRainProbabilityValue);
        rainMmValue = findViewById(R.id.txtRainMmValue);
        locationSummary = findViewById(R.id.txtRainLocationSummary);
        forecastSummary = findViewById(R.id.txtRainForecastSummary);
        sourceSummary = findViewById(R.id.txtRainSourceSummary);
        status = findViewById(R.id.txtRainSettingsStatus);
    }

    private void configureToolbar() {
        ((TextView) findViewById(R.id.txtSettingsToolbarTitle))
                .setText(R.string.rain_settings_title);
        findViewById(R.id.btnSettingsToolbarBack).setOnClickListener(view -> requestExit());
        findViewById(R.id.btnSettingsToolbarAction).setVisibility(View.GONE);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { requestExit(); }
        });
    }

    private void configureActions() {
        rainDelayEnabled.setOnCheckedChangeListener((button, checked) -> {
            updatePolicyControlState();
            markDirty();
        });
        weatherNotifications.setOnCheckedChangeListener((button, checked) -> markDirty());
        rainProbability.addOnChangeListener((slider, value, fromUser) -> {
            renderProbability(value);
            if (fromUser) markDirty();
        });
        rainMm.addOnChangeListener((slider, value, fromUser) -> {
            renderRainMm(value);
            if (fromUser) markDirty();
        });

        findViewById(R.id.btnOpenGardenLocation).setOnClickListener(view ->
                startActivity(new Intent(this, GardenLocationActivity.class)));
        findViewById(R.id.btnOpenWeatherForecast).setOnClickListener(view ->
                startActivity(new Intent(this, WeatherForecastActivity.class)));
        findViewById(R.id.btnSaveRainSettings).setOnClickListener(view -> save(false));
        findViewById(R.id.btnResetRainSettings).setOnClickListener(view -> applyDefaults());
    }

    private void observeLiveData() {
        viewModel.getRainSettings().observe(this, value -> {
            if (value == null || (settingsLoaded && dirty)) return;
            applySettings(value);
        });
        viewModel.getWeatherLocation().observe(this, this::renderLocation);
        viewModel.getWeatherForecast().observe(this, this::renderForecast);

        savedWeatherNotifications = viewModel.isCategoryEnabled("weather");
        applyingValues = true;
        weatherNotifications.setChecked(savedWeatherNotifications);
        applyingValues = false;
    }

    private void applySettings(RainSettings value) {
        applyingValues = true;
        savedRainDelayEnabled = value.isRainDelayEnabled();
        savedRainProbability = value.getRainProbability();
        savedRainMm = value.getRainMm();
        rainDelayEnabled.setChecked(savedRainDelayEnabled);
        rainProbability.setValue((float) savedRainProbability);
        rainMm.setValue((float) savedRainMm);
        renderProbability(savedRainProbability);
        renderRainMm(savedRainMm);
        updatePolicyControlState();
        applyingValues = false;
        settingsLoaded = true;
        dirty = false;
        status.setText(R.string.settings_status_ready);
    }

    private void renderLocation(WeatherLocation location) {
        if (location == null || (location.getCity().isBlank() && location.getDistrict().isBlank())) {
            locationSummary.setText(R.string.rain_settings_location_missing);
            sourceSummary.setText(R.string.rain_settings_source_missing);
            return;
        }
        locationSummary.setText(getString(R.string.rain_settings_location_format,
                location.getDistrict(), location.getCity()));
        sourceSummary.setText(getString(R.string.rain_settings_source_format,
                sourceLabel(location.getForecastSource())));
    }

    private void renderForecast(WeatherForecast forecast) {
        if (forecast == null || forecast.getTodayRainProbability() == null) {
            forecastSummary.setText(R.string.rain_settings_forecast_waiting);
            return;
        }
        double mm = forecast.getTodayRainMm() == null ? 0d : forecast.getTodayRainMm();
        forecastSummary.setText(getString(R.string.rain_settings_forecast_format,
                Math.round(forecast.getTodayRainProbability()), formatDecimal(mm)));
        if (!forecast.getSource().isBlank()) {
            sourceSummary.setText(getString(R.string.rain_settings_live_source_format,
                    sourceLabel(forecast.getSource())));
        }
    }

    private void renderProbability(double value) {
        probabilityValue.setText(getString(R.string.settings_percentage_format, Math.round(value)));
    }

    private void renderRainMm(double value) {
        rainMmValue.setText(getString(R.string.rain_settings_mm_format, formatDecimal(value)));
    }

    private void updatePolicyControlState() {
        boolean enabled = rainDelayEnabled.isChecked();
        rainProbability.setEnabled(enabled);
        rainMm.setEnabled(enabled);
        probabilityValue.setAlpha(enabled ? 1f : 0.45f);
        rainMmValue.setAlpha(enabled ? 1f : 0.45f);
    }

    private void applyDefaults() {
        applyingValues = true;
        rainDelayEnabled.setChecked(RainSettings.DEFAULT_RAIN_DELAY_ENABLED);
        rainProbability.setValue((float) RainSettings.DEFAULT_RAIN_PROBABILITY);
        rainMm.setValue((float) RainSettings.DEFAULT_RAIN_MM);
        weatherNotifications.setChecked(true);
        renderProbability(RainSettings.DEFAULT_RAIN_PROBABILITY);
        renderRainMm(RainSettings.DEFAULT_RAIN_MM);
        updatePolicyControlState();
        applyingValues = false;
        markDirty();
        status.setText(R.string.rain_settings_defaults_ready);
    }

    private void save(boolean closeAfterSave) {
        if (!hasUnsavedChanges()) {
            if (closeAfterSave) finish();
            else Toast.makeText(this, R.string.settings_no_changes, Toast.LENGTH_SHORT).show();
            return;
        }
        status.setText(R.string.settings_status_saving);
        RainSettings values = new RainSettings(rainDelayEnabled.isChecked(),
                rainProbability.getValue(), rainMm.getValue(), System.currentTimeMillis() / 1000L);
        viewModel.saveRainSettings(values, weatherNotifications.isChecked())
                .addOnSuccessListener(unused -> {
                    rememberSavedValues();
                    status.setText(R.string.rain_settings_saved);
                    Toast.makeText(this, R.string.rain_settings_saved, Toast.LENGTH_SHORT).show();
                    if (closeAfterSave) finish();
                })
                .addOnFailureListener(error -> {
                    status.setText(R.string.rain_settings_save_failed);
                    Toast.makeText(this, R.string.rain_settings_save_failed, Toast.LENGTH_LONG).show();
                });
    }

    private void markDirty() {
        if (applyingValues || !settingsLoaded) return;
        dirty = hasUnsavedChanges();
        status.setText(dirty ? R.string.settings_status_unsaved : R.string.settings_status_ready);
    }

    private boolean hasUnsavedChanges() {
        return rainDelayEnabled.isChecked() != savedRainDelayEnabled
                || weatherNotifications.isChecked() != savedWeatherNotifications
                || Math.abs(rainProbability.getValue() - savedRainProbability) > 0.01d
                || Math.abs(rainMm.getValue() - savedRainMm) > 0.01d;
    }

    private void rememberSavedValues() {
        savedRainDelayEnabled = rainDelayEnabled.isChecked();
        savedWeatherNotifications = weatherNotifications.isChecked();
        savedRainProbability = rainProbability.getValue();
        savedRainMm = rainMm.getValue();
        dirty = false;
    }

    private void requestExit() {
        if (!hasUnsavedChanges()) {
            finish();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_unsaved_dialog_title)
                .setMessage(R.string.rain_settings_unsaved_message)
                .setNegativeButton(R.string.settings_continue_editing, null)
                .setNeutralButton(R.string.settings_discard_changes, (dialog, which) -> finish())
                .setPositiveButton(R.string.settings_save_and_exit,
                        (dialog, which) -> save(true))
                .show();
    }

    private String sourceLabel(String source) {
        if (source == null) return getString(R.string.rain_settings_source_auto);
        String normalized = source.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("openweather")) return "OpenWeather";
        if (normalized.contains("open-meteo") || normalized.contains("open_meteo")) {
            return "Open-Meteo";
        }
        return getString(R.string.rain_settings_source_auto);
    }

    private String formatDecimal(double value) {
        return value == Math.rint(value)
                ? String.format(Locale.getDefault(), "%.0f", value)
                : String.format(Locale.getDefault(), "%.1f", value);
    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rainSettingsRoot),
                (view, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });
    }
}
