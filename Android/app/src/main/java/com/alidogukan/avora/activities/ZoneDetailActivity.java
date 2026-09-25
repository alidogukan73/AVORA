package com.alidogukan.avora.activities;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.settings.ZoneCooldownOptions;
import com.alidogukan.avora.viewmodels.ZoneDetailViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;

public class ZoneDetailActivity extends EdgeToEdgeActivity {

    public static final String EXTRA_ZONE_ID = "zone_id";
    public static final String EXTRA_INITIAL_SECTION = "zone_detail.initial_section";
    public static final String SECTION_SENSOR = "sensor";
    public static final String SECTION_IRRIGATION = "irrigation";

    private ZoneDetailViewModel viewModel;

    private GardenZone currentZone;
    private TextView title;
    private TextView subtitle;
    private TextView valveMode;
    private TextView irrigationDescription;
    private TextView settingsStatus;
    private TextView sensorIdValue;
    private MaterialSwitch irrigationEnabled;
    private MaterialSwitch sensorEnabled;
    private TextInputEditText sensorDryRawInput;
    private TextInputEditText sensorWetRawInput;
    private Slider moistureSlider;
    private Slider durationSlider;
    private Slider cooldownSlider;
    private Slider restartDeltaSlider;
    private MaterialButton testButton;
    private MaterialButton saveButton;
    private MaterialButton resetButton;
    private MaterialCardView unsavedChangesCard;
    private boolean hasLocalChanges;
    private boolean renderingRemoteValues;
    private boolean saveAndExit;
    private int originalMoistureLimit = 40;
    private int originalPumpDuration = 10;
    private int originalCooldownMinutes = 10;
    private int originalRestartDelta = 10;
    private boolean originalIrrigationEnabled;
    private boolean originalSensorEnabled = true;
    private int originalSensorDryRaw = 12650;
    private int originalSensorWetRaw = 505;
    private boolean zoneTestActive;

    @Override
    protected void onCreate(
            @Nullable Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_zone_detail);

        String zoneId = getIntent().getStringExtra(
                EXTRA_ZONE_ID
        );
        if (zoneId == null || zoneId.isBlank()) {
            finish();
            return;
        }

        bindViews();
        bindActions(zoneId);
        if (savedInstanceState == null) {
            String section = getIntent().getStringExtra(EXTRA_INITIAL_SECTION);
            View sectionView = SECTION_SENSOR.equals(section) ? sensorEnabled
                    : SECTION_IRRIGATION.equals(section) ? irrigationEnabled : null;
            if (sectionView != null) {
                sectionView.post(() -> sectionView.requestRectangleOnScreen(
                        new android.graphics.Rect(0, 0, sectionView.getWidth(), sectionView.getHeight()), true));
            }
        }
        viewModel = new ViewModelProvider(this).get(ZoneDetailViewModel.class);
        viewModel.initialize(zoneId);

        viewModel.getZone().observe(
                this,
                this::render
        );

        viewModel.getTestActive().observe(this, active -> {
            zoneTestActive = Boolean.TRUE.equals(active);
            updateZoneTestUi();
        });
    }

    private void bindViews() {
        title = findViewById(R.id.txtZoneDetailTitle);
        subtitle = findViewById(R.id.txtZoneDetailSubtitle);
        TextView moistureValue = findViewById(R.id.txtZoneMoistureLimitValue);
        TextView durationValue = findViewById(R.id.txtZonePumpDurationValue);
        TextView cooldownValue = findViewById(R.id.txtZoneCooldownValue);
        TextView restartDeltaValue = findViewById(
                R.id.txtZoneRestartDeltaValue
        );
        valveMode = findViewById(R.id.txtZoneValveMode);
        irrigationDescription = findViewById(
                R.id.txtZoneIrrigationDescription
        );
        settingsStatus = findViewById(
                R.id.txtZoneSettingsStatus
        );
        sensorIdValue = findViewById(R.id.txtZoneSensorId);
        irrigationEnabled = findViewById(
                R.id.switchZoneIrrigation
        );
        sensorEnabled = findViewById(R.id.switchZoneSensor);
        sensorDryRawInput = findViewById(
                R.id.inputZoneSensorDryRaw
        );
        sensorWetRawInput = findViewById(
                R.id.inputZoneSensorWetRaw
        );
        moistureSlider = findViewById(
                R.id.sliderZoneMoistureLimit
        );
        durationSlider = findViewById(
                R.id.sliderZonePumpDuration
        );
        cooldownSlider = findViewById(
                R.id.sliderZoneCooldown
        );
        restartDeltaSlider = findViewById(
                R.id.sliderZoneRestartDelta
        );
        testButton = findViewById(R.id.btnTestZoneValve);
        saveButton = findViewById(R.id.btnSaveZone);
        resetButton = findViewById(R.id.btnResetZone);
        unsavedChangesCard = findViewById(
                R.id.cardZoneUnsavedChanges
        );

        findViewById(R.id.btnBack).setOnClickListener(
                view -> requestClose()
        );

        getOnBackPressedDispatcher().addCallback(
                this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        requestClose();
                    }
                }
        );

        moistureSlider.addOnChangeListener(
                (slider, value, fromUser) -> {
                    moistureValue.setText(
                            getString(
                                    R.string.zone_percentage_format,
                                    Math.round(value)
                            )
                    );
                    if (fromUser) {
                        updateUnsavedState();
                    }
                }
        );

        durationSlider.addOnChangeListener(
                (slider, value, fromUser) -> {
                    durationValue.setText(
                            formatPumpDuration(
                                    Math.round(value)
                            )
                    );
                    if (fromUser) {
                        updateUnsavedState();
                    }
                }
        );

        cooldownSlider.addOnChangeListener(
                (slider, value, fromUser) -> {
                    int cooldownMinutes =
                            ZoneCooldownOptions.minutesForSliderValue(value);
                    cooldownValue.setText(
                            getString(
                                    R.string.zone_minutes_format,
                                    cooldownMinutes
                            )
                    );
                    if (fromUser) {
                        updateUnsavedState();
                    }
                }
        );
        cooldownSlider.setLabelFormatter(
                value -> getString(
                        R.string.zone_minutes_format,
                        ZoneCooldownOptions.minutesForSliderValue(value)
                )
        );

        restartDeltaSlider.addOnChangeListener(
                (slider, value, fromUser) -> {
                    restartDeltaValue.setText(
                            getString(
                                    R.string.zone_percentage_format,
                                    Math.round(value)
                            )
                    );
                    if (fromUser) {
                        updateUnsavedState();
                    }
                }
        );

        irrigationEnabled.setOnCheckedChangeListener(
                (button, checked) -> {
                    updateIrrigationDescription(checked);
                    if (!renderingRemoteValues) {
                        updateUnsavedState();
                    }
                }
        );

        sensorEnabled.setOnCheckedChangeListener(
                (button, checked) -> {
                    if (!renderingRemoteValues) {
                        updateUnsavedState();
                    }
                }
        );

        TextWatcher calibrationWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence value,
                    int start,
                    int count,
                    int after
            ) { }

            @Override
            public void onTextChanged(
                    CharSequence value,
                    int start,
                    int before,
                    int count
            ) {
                if (!renderingRemoteValues) {
                    updateUnsavedState();
                }
            }

            @Override
            public void afterTextChanged(Editable value) { }
        };
        sensorDryRawInput.addTextChangedListener(calibrationWatcher);
        sensorWetRawInput.addTextChangedListener(calibrationWatcher);
    }

    private void bindActions(String zoneId) {
        saveButton.setOnClickListener(
                view -> saveSettings(zoneId)
        );

        resetButton.setOnClickListener(
                view -> showResetConfirmation()
        );

        testButton.setOnClickListener(
                view -> {
                    if (zoneTestActive) {
                        cancelZoneTest();
                        return;
                    }
                    if (
                            currentZone == null
                                    || currentZone.getValve_id() == null
                    ) {
                        return;
                    }
                    showZoneTestConfirmation();
                }
        );
    }

    private void showZoneTestConfirmation() {
        int duration = Math.max(
                1,
                Math.round(durationSlider.getValue())
        );
        boolean physical = "PHYSICAL".equals(
                currentZone.getValve_mode()
        );
        new MaterialAlertDialogBuilder(this)
                .setTitle(
                        physical
                                ? R.string.zone_physical_test_start_title
                                : R.string.zone_test_start_title
                )
                .setMessage(
                        getString(
                                physical
                                        ? R.string.zone_physical_test_start_message
                                        : R.string.zone_test_start_message,
                                com.alidogukan.avora.zones.PhysicalZoneIdentity.name(currentZone),
                                formatPumpDuration(duration)
                        )
                )
                .setNegativeButton(
                        android.R.string.cancel,
                        null
                )
                .setPositiveButton(
                        physical
                                ? R.string.zone_physical_test_start
                                : R.string.zone_test_start,
                        (dialog, which) ->
                                startZoneTest(duration)
                )
                .show();
    }

    private void startZoneTest(int duration) {
        testButton.setEnabled(false);
        viewModel.startTest(
                currentZone,
                duration
        ).addOnCompleteListener(
                task -> {
                    testButton.setEnabled(true);
                    Toast.makeText(
                            this,
                            task.isSuccessful()
                                    ? R.string.zone_test_sent
                                    : R.string.zone_operation_failed,
                            Toast.LENGTH_LONG
                    ).show();
                }
        );
    }

    private void cancelZoneTest() {
        testButton.setEnabled(false);
        viewModel.cancelTest()
                .addOnCompleteListener(
                        task -> {
                            testButton.setEnabled(true);
                            Toast.makeText(
                                    this,
                                    task.isSuccessful()
                                            ? R.string.zone_test_cancelled
                                            : R.string.zone_operation_failed,
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                );
    }

    private void updateZoneTestUi() {
        if (zoneTestActive) {
            testButton.setText(R.string.zone_test_cancel);
            valveMode.setText(R.string.zone_test_active);
        } else if (currentZone != null) {
            boolean simulation =
                    !"PHYSICAL".equals(
                            currentZone.getValve_mode()
                    );
            testButton.setText(
                    simulation
                            ? R.string.zone_test_simulation
                            : R.string.zone_test_physical
            );
            valveMode.setText(
                    simulation
                            ? R.string.zone_valve_simulation
                            : R.string.zone_valve_physical
            );
        }
    }

    private void saveSettings(String zoneId) {
        if (!hasUnsavedChanges()) {
            Toast.makeText(
                    this,
                    R.string.settings_no_changes,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        Integer sensorDryRaw = readCalibrationInput(sensorDryRawInput);
        Integer sensorWetRaw = readCalibrationInput(sensorWetRawInput);
        if (
                sensorDryRaw == null
                        || sensorWetRaw == null
                        || sensorDryRaw <= sensorWetRaw
        ) {
            Toast.makeText(
                    this,
                    R.string.zone_sensor_calibration_invalid,
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        setControlsEnabled(false);
        saveButton.setText(R.string.settings_saving);
        settingsStatus.setText(
                R.string.settings_status_saving
        );

        viewModel.saveSettings(
                        zoneId,
                        irrigationEnabled.isChecked(),
                        Math.round(moistureSlider.getValue()),
                        Math.round(durationSlider.getValue()),
                        getCooldownMinutes() * 60,
                        Math.round(restartDeltaSlider.getValue()),
                        sensorEnabled.isChecked(),
                        sensorDryRaw,
                        sensorWetRaw
                ).addOnSuccessListener(
                        unused -> {
                            saveCurrentValuesAsOriginal();
                            updateUnsavedState();
                            settingsStatus.setText(
                                    R.string.settings_status_saved
                            );
                            setControlsEnabled(true);
                            saveButton.setText(
                                    R.string.zone_save_settings
                            );
                            Toast.makeText(
                                    this,
                                    R.string.zone_settings_saved,
                                    Toast.LENGTH_SHORT
                            ).show();
                            if (saveAndExit) {
                                finish();
                            }
                        }
                ).addOnFailureListener(
                        error -> {
                            saveAndExit = false;
                            setControlsEnabled(true);
                            saveButton.setText(
                                    R.string.zone_save_settings
                            );
                            settingsStatus.setText(
                                    R.string.settings_status_error
                            );
                            Toast.makeText(
                                    this,
                                    R.string.zone_operation_failed,
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                );
    }

    private void render(GardenZone zone) {
        if (zone == null) {
            return;
        }

        currentZone = zone;
        sensorIdValue.setText(
                getString(
                        R.string.runtime_label_value,
                        getString(R.string.zone_sensor_id_label),
                        zone.getSensor_id())
        );
        title.setText(
                getString(
                        R.string.runtime_icon_label,
                        zone.getEmoji() == null
                                ? getString(R.string.symbol_plant)
                                : zone.getEmoji(),
                        com.alidogukan.avora.zones.PhysicalZoneIdentity.name(zone))
        );
        subtitle.setText(
                getString(
                        R.string.runtime_sensor_valve,
                        zone.getSensor_id(),
                        zone.getValve_id())
        );
        if (!hasLocalChanges) {
            renderingRemoteValues = true;

            originalIrrigationEnabled =
                    zone.isIrrigation_enabled();
            originalSensorEnabled = zone.isSensor_enabled();
            originalSensorDryRaw = zone.getSensor_calibration_dry_raw();
            originalSensorWetRaw = zone.getSensor_calibration_wet_raw();
            originalMoistureLimit = Math.max(
                    5,
                    Math.min(95, zone.getMoisture_limit())
            );
            originalPumpDuration = Math.max(
                    0,
                    Math.min(18000, zone.getPump_duration())
            );
            originalCooldownMinutes = Math.max(
                    1,
                    Math.min(
                            1440,
                            Math.round(
                                    zone.getCooldown_seconds() / 60f
                            )
                    )
            );
            originalRestartDelta = Math.max(
                    1,
                    Math.min(30, zone.getRestart_delta())
            );

            irrigationEnabled.setChecked(
                    originalIrrigationEnabled
            );
            sensorEnabled.setChecked(originalSensorEnabled);
            sensorDryRawInput.setText(
                    String.valueOf(originalSensorDryRaw)
            );
            sensorWetRawInput.setText(
                    String.valueOf(originalSensorWetRaw)
            );
            moistureSlider.setValue(
                    originalMoistureLimit
            );
            durationSlider.setValue(
                    originalPumpDuration
            );
            cooldownSlider.setValue(
                    ZoneCooldownOptions.sliderValueForMinutes(
                            originalCooldownMinutes
                    )
            );
            restartDeltaSlider.setValue(
                    originalRestartDelta
            );

            renderingRemoteValues = false;
            updateIrrigationDescription(
                    originalIrrigationEnabled
            );
            updateUnsavedState();
        }

        boolean simulation =
                !"PHYSICAL".equals(zone.getValve_mode());
        updateZoneTestUi();
    }

    private String formatPumpDuration(long seconds) {
        long safeSeconds = Math.max(0, seconds);

        if (safeSeconds < 60) {
            return getString(
                    R.string.settings_seconds_format,
                    safeSeconds
            );
        }

        if (safeSeconds < 3600) {
            long minutes = safeSeconds / 60;
            long remainingSeconds = safeSeconds % 60;

            if (remainingSeconds == 0) {
                return getString(
                        R.string.settings_minutes_format,
                        minutes
                );
            }

            return getString(
                    R.string.settings_minutes_seconds_format,
                    minutes,
                    remainingSeconds
            );
        }

        long hours = safeSeconds / 3600;
        long remainingMinutes =
                (safeSeconds % 3600) / 60;

        if (remainingMinutes == 0) {
            return getString(
                    R.string.settings_hours_format,
                    hours
            );
        }

        return getString(
                R.string.settings_hours_minutes_format,
                hours,
                remainingMinutes
        );
    }

    private void updateUnsavedState() {
        hasLocalChanges = hasUnsavedChanges();
        unsavedChangesCard.setVisibility(
                hasLocalChanges ? View.VISIBLE : View.GONE
        );
        saveButton.setEnabled(hasLocalChanges);
        settingsStatus.setText(
                hasLocalChanges
                        ? R.string.settings_status_unsaved
                        : R.string.settings_status_ready
        );
    }

    private boolean hasUnsavedChanges() {
        return Math.round(moistureSlider.getValue())
                != originalMoistureLimit
                || Math.round(durationSlider.getValue())
                != originalPumpDuration
                || getCooldownMinutes()
                != originalCooldownMinutes
                || Math.round(restartDeltaSlider.getValue())
                != originalRestartDelta
                || irrigationEnabled.isChecked()
                != originalIrrigationEnabled
                || sensorEnabled.isChecked()
                != originalSensorEnabled
                || calibrationInputChanged(
                        sensorDryRawInput,
                        originalSensorDryRaw
                )
                || calibrationInputChanged(
                        sensorWetRawInput,
                        originalSensorWetRaw
                );
    }

    private void saveCurrentValuesAsOriginal() {
        originalMoistureLimit =
                Math.round(moistureSlider.getValue());
        originalPumpDuration =
                Math.round(durationSlider.getValue());
        originalCooldownMinutes =
                getCooldownMinutes();
        originalRestartDelta =
                Math.round(restartDeltaSlider.getValue());
        originalIrrigationEnabled =
                irrigationEnabled.isChecked();
        originalSensorEnabled = sensorEnabled.isChecked();
        originalSensorDryRaw = readCalibrationInput(sensorDryRawInput);
        originalSensorWetRaw = readCalibrationInput(sensorWetRawInput);
    }

    private void updateIrrigationDescription(
            boolean enabled
    ) {
        irrigationDescription.setText(
                enabled
                        ? R.string.zone_irrigation_enabled_description
                        : R.string.zone_irrigation_disabled_description
        );
        irrigationDescription.setTextColor(
                getColor(
                        enabled
                                ? R.color.online
                                : R.color.textSecondary
                )
        );
    }

    private void setControlsEnabled(boolean enabled) {
        moistureSlider.setEnabled(enabled);
        durationSlider.setEnabled(enabled);
        cooldownSlider.setEnabled(enabled);
        restartDeltaSlider.setEnabled(enabled);
        irrigationEnabled.setEnabled(enabled);
        sensorEnabled.setEnabled(enabled);
        sensorDryRawInput.setEnabled(enabled);
        sensorWetRawInput.setEnabled(enabled);
        resetButton.setEnabled(enabled);
        testButton.setEnabled(enabled);
        saveButton.setEnabled(
                enabled && hasUnsavedChanges()
        );
    }

    private Integer readCalibrationInput(
            TextInputEditText input
    ) {
        String value = input.getText() == null
                ? ""
                : input.getText().toString().trim();
        if (value.isEmpty()) {
            return null;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private boolean calibrationInputChanged(
            TextInputEditText input,
            int originalValue
    ) {
        Integer currentValue = readCalibrationInput(input);
        return currentValue == null || currentValue != originalValue;
    }

    private void showResetConfirmation() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_reset_dialog_title)
                .setMessage(R.string.settings_reset_dialog_message)
                .setNegativeButton(R.string.settings_cancel, null)
                .setPositiveButton(
                        R.string.settings_reset_confirm,
                        (dialog, which) -> applyDefaultValues()
                )
                .show();
    }

    private void applyDefaultValues() {
        renderingRemoteValues = true;
        moistureSlider.setValue(40);
        durationSlider.setValue(10);
        cooldownSlider.setValue(
                ZoneCooldownOptions.sliderValueForMinutes(10)
        );
        restartDeltaSlider.setValue(10);
        irrigationEnabled.setChecked(false);
        renderingRemoteValues = false;

        updateIrrigationDescription(false);
        updateUnsavedState();
        settingsStatus.setText(
                R.string.settings_defaults_applied
        );
    }

    private int getCooldownMinutes() {
        return ZoneCooldownOptions.minutesForSliderValue(
                cooldownSlider.getValue()
        );
    }

    private void requestClose() {
        if (!hasLocalChanges) {
            finish();
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.zone_unsaved_dialog_title)
                .setMessage(R.string.zone_unsaved_dialog_message)
                .setNegativeButton(
                        R.string.zone_continue_editing,
                        null
                )
                .setNeutralButton(
                        R.string.zone_discard_changes,
                        (dialog, which) -> finish()
                )
                .setPositiveButton(
                        R.string.settings_save_and_exit,
                        (dialog, which) -> {
                            if (currentZone == null) {
                                return;
                            }
                            saveAndExit = true;
                            saveSettings(
                                    currentZone.getZone_id()
                            );
                        }
                )
                .show();
    }

}
