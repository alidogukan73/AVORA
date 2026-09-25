package com.alidogukan.avora.activities;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.models.Command;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.IrrigationTimingSettings;
import com.alidogukan.avora.models.ZoneIrrigationStatus;
import com.alidogukan.avora.ui.PrimaryBottomNavigation;
import com.alidogukan.avora.viewmodels.SettingsViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class IrrigationSettingsActivity extends AppCompatActivity {

    private static final long DEFAULT_MOISTURE_LIMIT = 40;
    private static final long DEFAULT_PUMP_DURATION = 10;
    private static final long DEFAULT_COOLDOWN_SECONDS = 600;
    private static final long DEFAULT_RESTART_DELTA = 10;
    private static final boolean DEFAULT_SYSTEM_ENABLED = true;
    private static final boolean DEFAULT_AUTO_MODE = true;
    private static final long AUTO_SAVE_DELAY_MS = 650L;
    private static final String ALL_ZONES_RESET_SCOPE = "ALL";
    private static final String[] ENVIRONMENT_CODES = {"OPEN_FIELD", "GREENHOUSE", "INDOOR"};
    private static final String[] TIMING_STRATEGY_CODES = {"SMART", "MORNING_ONLY", "CUSTOM", "IMMEDIATE"};

    private SettingsViewModel viewModel;
    private final Handler autoSaveHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoSaveRunnable = () -> performSave(false);

    private MaterialButton btnBack;
    private MaterialButton btnResetSettings;
    private MaterialCardView cardRestartIrrigationProcess;
    private MaterialCardView cardManualWateringSafety;

    private Slider sliderMoistureLimit;
    private Slider sliderPumpDuration;
    private Slider sliderCooldown;
    private Slider sliderRestartDelta;

    private TextView txtMoistureLimitValue;
    private TextView txtPumpDurationValue;
    private TextView txtCooldownValue;
    private TextView txtRestartDeltaValue;
    private TextView txtAutoModeDescription;
    private TextView txtSystemEnabledDescription;
    private TextView txtSettingsStatus;
    private TextView txtManualWateringSafetyLimit;
    private TextView txtManualWateringSafetyAccess;

    private MaterialSwitch switchAutoMode;
    private MaterialSwitch switchSystemEnabled;
    private MaterialSwitch switchSmartTiming;
    private MaterialSwitch switchEveningIrrigation;
    private MaterialSwitch switchIrrigationTimingRecheck;
    private MaterialAutoCompleteTextView dropdownIrrigationEnvironment;
    private MaterialAutoCompleteTextView dropdownIrrigationTimingStrategy;
    private MaterialAutoCompleteTextView dropdownIrrigationStartHour;
    private MaterialAutoCompleteTextView dropdownIrrigationEndHour;
    private LinearLayout layoutCustomIrrigationHours;
    private Slider sliderIrrigationMaxDefer;
    private Slider sliderIrrigationCriticalDeficit;
    private TextView txtIrrigationMaxDeferValue;
    private TextView txtIrrigationCriticalDeficitValue;

    private boolean updatingUi;
    private boolean pendingExitAfterSave;
    private boolean commandLoaded;
    private boolean timingSettingsLoaded;
    private boolean restartRequestInFlight;
    private boolean deviceOwner;
    private boolean settingsControlsEnabled = true;
    private int manualWateringSafetyLimitSeconds =
            IrrigationTimingSettings.DEFAULT_MANUAL_WATERING_MAX_DURATION_SECONDS;
    private final List<GardenZone> restartZones = new ArrayList<>();

    private long originalMoistureLimit = DEFAULT_MOISTURE_LIMIT;
    private long originalPumpDuration = DEFAULT_PUMP_DURATION;
    private long originalCooldownSeconds = DEFAULT_COOLDOWN_SECONDS;
    private long originalRestartDelta = DEFAULT_RESTART_DELTA;
    private boolean originalSystemEnabled = DEFAULT_SYSTEM_ENABLED;
    private boolean originalAutoMode = DEFAULT_AUTO_MODE;
    private IrrigationTimingSettings originalTimingSettings = IrrigationTimingSettings.defaults();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);
        PrimaryBottomNavigation.bind(this, PrimaryBottomNavigation.SETTINGS);

        applyWindowInsets();
        initializeViews();
        initializeViewModel();
        initializeListeners();
        observeViewModel();
        initializeActions();
    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.settingsScreenRoot),
                (view, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                    view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                    return insets;
                }
        );
    }

    private void initializeViews() {
        btnBack = findViewById(R.id.btnBack);
        btnResetSettings = findViewById(R.id.btnResetSettings);
        cardRestartIrrigationProcess =
                findViewById(R.id.cardRestartIrrigationProcess);
        cardManualWateringSafety = findViewById(R.id.cardManualWateringSafety);
        txtManualWateringSafetyLimit = findViewById(
                R.id.txtManualWateringSafetyLimit);
        txtManualWateringSafetyAccess = findViewById(
                R.id.txtManualWateringSafetyAccess);

        sliderMoistureLimit = findViewById(R.id.sliderMoistureLimit);
        sliderPumpDuration = findViewById(R.id.sliderPumpDuration);
        sliderCooldown = findViewById(R.id.sliderCooldown);
        sliderRestartDelta = findViewById(R.id.sliderRestartDelta);

        txtMoistureLimitValue = findViewById(R.id.txtMoistureLimitValue);
        txtPumpDurationValue = findViewById(R.id.txtPumpDurationValue);
        txtCooldownValue = findViewById(R.id.txtCooldownValue);
        txtRestartDeltaValue = findViewById(R.id.txtRestartDeltaValue);
        txtAutoModeDescription = findViewById(R.id.txtAutoModeDescription);
        txtSystemEnabledDescription = findViewById(R.id.txtSystemEnabledDescription);
        txtSettingsStatus = findViewById(R.id.txtSettingsStatus);

        switchAutoMode = findViewById(R.id.switchAutoMode);
        switchSystemEnabled = findViewById(R.id.switchSystemEnabled);
        switchSmartTiming = findViewById(R.id.switchSmartTiming);
        switchEveningIrrigation = findViewById(R.id.switchEveningIrrigation);
        switchIrrigationTimingRecheck = findViewById(R.id.switchIrrigationTimingRecheck);
        dropdownIrrigationEnvironment = findViewById(R.id.dropdownIrrigationEnvironment);
        dropdownIrrigationTimingStrategy = findViewById(R.id.dropdownIrrigationTimingStrategy);
        dropdownIrrigationStartHour = findViewById(R.id.dropdownIrrigationStartHour);
        dropdownIrrigationEndHour = findViewById(R.id.dropdownIrrigationEndHour);
        layoutCustomIrrigationHours = findViewById(R.id.layoutCustomIrrigationHours);
        sliderIrrigationMaxDefer = findViewById(R.id.sliderIrrigationMaxDefer);
        sliderIrrigationCriticalDeficit = findViewById(R.id.sliderIrrigationCriticalDeficit);
        txtIrrigationMaxDeferValue = findViewById(R.id.txtIrrigationMaxDeferValue);
        txtIrrigationCriticalDeficitValue = findViewById(R.id.txtIrrigationCriticalDeficitValue);
        initializeTimingDropdowns();
    }

    private void initializeViewModel() {
        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);
    }

    private void initializeListeners() {
        sliderMoistureLimit.addOnChangeListener((slider, value, fromUser) -> {
            updateMoistureLimitLabel(Math.round(value));
            onSettingChanged(fromUser);
        });
        sliderPumpDuration.addOnChangeListener((slider, value, fromUser) -> {
            updatePumpDurationLabel(Math.round(value));
            onSettingChanged(fromUser);
        });
        sliderCooldown.addOnChangeListener((slider, value, fromUser) -> {
            updateCooldownLabel(Math.round(value));
            onSettingChanged(fromUser);
        });
        sliderRestartDelta.addOnChangeListener((slider, value, fromUser) -> {
            updateRestartDeltaLabel(Math.round(value));
            onSettingChanged(fromUser);
        });

        switchAutoMode.setOnCheckedChangeListener((button, checked) -> {
            updateAutoModeDescription(checked);
            if (!updatingUi) {
                onSettingChanged(true);
            }
        });
        switchSystemEnabled.setOnCheckedChangeListener((button, checked) -> {
            updateSystemEnabledDescription(checked);
            updateAutoModeDescription(switchAutoMode.isChecked());
            if (!updatingUi) {
                onSettingChanged(true);
            }
        });

        sliderIrrigationMaxDefer.addOnChangeListener((slider, value, fromUser) -> {
            updateIrrigationMaxDeferLabel(Math.round(value));
            onSettingChanged(fromUser);
        });
        sliderIrrigationCriticalDeficit.addOnChangeListener((slider, value, fromUser) -> {
            updateIrrigationCriticalDeficitLabel(Math.round(value));
            onSettingChanged(fromUser);
        });
        switchSmartTiming.setOnCheckedChangeListener((button, checked) -> {
            updateTimingControlState();
            if (!updatingUi) {
                onSettingChanged(true);
            }
        });
        switchEveningIrrigation.setOnCheckedChangeListener((button, checked) -> {
            if (!updatingUi) {
                onSettingChanged(true);
            }
        });
    }

    private void onSettingChanged(boolean fromUser) {
        if (fromUser && !updatingUi) {
            updateUnsavedState();
            scheduleAutoSave();
        }
    }

    private void scheduleAutoSave() {
        autoSaveHandler.removeCallbacks(autoSaveRunnable);
        if (!commandLoaded || !timingSettingsLoaded || !hasUnsavedChanges()) {
            return;
        }
        autoSaveHandler.postDelayed(autoSaveRunnable, AUTO_SAVE_DELAY_MS);
    }

    private void observeViewModel() {
        viewModel.getCommand().observe(this, this::renderCommand);
        viewModel.getIrrigationTimingSettings().observe(this, this::renderTimingSettings);
        viewModel.getDeviceOwner().observe(this, owner -> {
            deviceOwner = Boolean.TRUE.equals(owner);
            updateManualWateringSafetyAccess();
        });
        viewModel.getActiveGardenZones().observe(this, zones -> {
            restartZones.clear();
            if (zones != null) {
                restartZones.addAll(zones);
                restartZones.sort(Comparator.comparingInt(GardenZone::getOrder));
            }
        });
        viewModel.getLoading().observe(this, loading -> {
            boolean active = Boolean.TRUE.equals(loading);
            setControlsEnabled(!active);
            txtSettingsStatus.setText(active
                    ? R.string.settings_status_loading
                    : R.string.settings_status_ready);
        });
        viewModel.getSaving().observe(this, saving -> {
            boolean active = Boolean.TRUE.equals(saving);
            setControlsEnabled(!active);
            if (active) {
                txtSettingsStatus.setText(R.string.settings_status_saving);
            }
        });
        viewModel.getSaveSuccess().observe(this, success -> {
            if (!Boolean.TRUE.equals(success)) {
                return;
            }
            saveCurrentValuesAsOriginal();
            updateUnsavedState();
            txtSettingsStatus.setText(R.string.settings_status_saved);
            viewModel.clearSaveSuccess();

            if (pendingExitAfterSave) {
                pendingExitAfterSave = false;
                finish();
            }
        });
        viewModel.getError().observe(this, message -> {
            if (message == null || message.isBlank()) {
                return;
            }
            pendingExitAfterSave = false;
            txtSettingsStatus.setText(R.string.settings_status_error);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

    private void initializeActions() {
        btnBack.setOnClickListener(view -> handleBackAction());
        findViewById(R.id.cardWateringControlShortcut).setOnClickListener(view ->
                startActivity(new android.content.Intent(this, WateringControlActivity.class)));
        cardManualWateringSafety.setOnClickListener(view -> {
            if (deviceOwner) showManualWateringSafetyDialog();
        });
        cardRestartIrrigationProcess.setOnClickListener(
                view -> showRestartScopeDialog());
        btnResetSettings.setOnClickListener(view -> showResetConfirmation());
        getOnBackPressedDispatcher().addCallback(
                this,
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        handleBackAction();
                    }
                }
        );
    }

    private void showRestartScopeDialog() {
        List<GardenZone> zones = new ArrayList<>(restartZones);
        if (zones.isEmpty()) {
            Toast.makeText(
                    this,
                    R.string.ai_restart_no_active_zone,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        CharSequence[] scopes = new CharSequence[zones.size() + 1];
        for (int index = 0; index < zones.size(); index++) {
            GardenZone zone = zones.get(index);
            scopes[index] = getString(
                    R.string.ai_restart_scope_zone,
                    zoneName(zone),
                    zone.getZone_id()
            );
        }
        scopes[zones.size()] = getString(R.string.ai_restart_scope_all);

        boolean[] selectedScopes = new boolean[scopes.length];
        AlertDialog scopeDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ai_restart_scope_title)
                .setMultiChoiceItems(scopes, selectedScopes, (dialog, which, checked) -> {
                    if (dialog instanceof AlertDialog) {
                        AlertDialog alertDialog = (AlertDialog) dialog;
                        int allZonesIndex = zones.size();
                        selectedScopes[which] = checked;
                        if (which == allZonesIndex) {
                            for (int index = 0; index < zones.size(); index++) {
                                selectedScopes[index] = checked;
                                alertDialog.getListView().setItemChecked(index, checked);
                            }
                        } else {
                            boolean allSelected = true;
                            for (int index = 0; index < zones.size(); index++) {
                                if (!selectedScopes[index]) {
                                    allSelected = false;
                                    break;
                                }
                            }
                            selectedScopes[allZonesIndex] = allSelected;
                            alertDialog.getListView().setItemChecked(
                                    allZonesIndex,
                                    allSelected
                            );
                        }

                        boolean anySelected = false;
                        for (int index = 0; index < zones.size(); index++) {
                            if (selectedScopes[index]) {
                                anySelected = true;
                                break;
                            }
                        }
                        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                .setEnabled(anySelected);
                    }
                })
                .setNegativeButton(R.string.ai_restart_cancel, null)
                .setPositiveButton(R.string.ai_restart_continue, null)
                .create();
        scopeDialog.setOnShowListener(ignored -> {
            scopeDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            scopeDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                List<GardenZone> selectedZones = new ArrayList<>();
                for (int index = 0; index < zones.size(); index++) {
                    if (selectedScopes[index]) {
                        selectedZones.add(zones.get(index));
                    }
                }
                if (selectedZones.isEmpty()) {
                    return;
                }
                scopeDialog.dismiss();
                if (selectedZones.size() == zones.size()) {
                    confirmRestartAllZones();
                } else if (selectedZones.size() == 1) {
                    confirmRestartZone(selectedZones.get(0));
                } else {
                    confirmRestartSelectedZones(selectedZones);
                }
            });
        });
        scopeDialog.show();
    }

    private void confirmRestartSelectedZones(List<GardenZone> selectedZones) {
        List<GardenZone> currentZones = new ArrayList<>();
        List<String> currentZoneIds = new ArrayList<>();
        StringBuilder names = new StringBuilder();
        for (GardenZone selectedZone : selectedZones) {
            GardenZone currentZone = findRestartZone(
                    selectedZone == null ? null : selectedZone.getZone_id());
            if (currentZone == null
                    || currentZoneIds.contains(currentZone.getZone_id())) {
                continue;
            }
            if (isWateringActive(currentZone)) {
                Toast.makeText(
                        this,
                        R.string.ai_restart_selected_watering_active,
                        Toast.LENGTH_LONG
                ).show();
                return;
            }
            currentZones.add(currentZone);
            currentZoneIds.add(currentZone.getZone_id());
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(zoneName(currentZone));
        }

        if (currentZones.isEmpty()) {
            Toast.makeText(
                    this,
                    R.string.ai_restart_no_active_zone,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }
        if (currentZones.size() == 1) {
            confirmRestartZone(currentZones.get(0));
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(
                        R.string.ai_restart_selected_dialog_title,
                        currentZones.size()))
                .setMessage(getString(
                        R.string.ai_restart_selected_dialog_message,
                        names.toString()))
                .setNegativeButton(R.string.ai_restart_cancel, null)
                .setPositiveButton(
                        R.string.ai_restart_selected_confirm,
                        (dialog, which) -> restartIrrigationProcesses(currentZoneIds)
                )
                .show();
    }

    private void confirmRestartZone(GardenZone zone) {
        GardenZone currentZone = findRestartZone(
                zone == null ? null : zone.getZone_id());
        if (currentZone == null) {
            Toast.makeText(
                    this,
                    R.string.ai_restart_no_active_zone,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }
        if (isWateringActive(currentZone)) {
            Toast.makeText(
                    this,
                    R.string.ai_restart_watering_active,
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(
                        R.string.ai_restart_dialog_title,
                        zoneName(currentZone)))
                .setMessage(R.string.ai_restart_dialog_message)
                .setNegativeButton(R.string.ai_restart_cancel, null)
                .setPositiveButton(
                        R.string.ai_restart_confirm,
                        (dialog, which) -> restartIrrigationProcess(
                                currentZone.getZone_id(),
                                R.string.ai_restart_request_sent
                        )
                )
                .show();
    }

    private void confirmRestartAllZones() {
        if (restartZones.isEmpty()) {
            Toast.makeText(
                    this,
                    R.string.ai_restart_no_active_zone,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }
        for (GardenZone zone : restartZones) {
            if (isWateringActive(zone)) {
                Toast.makeText(
                        this,
                        R.string.ai_restart_all_watering_active,
                        Toast.LENGTH_LONG
                ).show();
                return;
            }
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ai_restart_all_dialog_title)
                .setMessage(R.string.ai_restart_all_dialog_message)
                .setNegativeButton(R.string.ai_restart_cancel, null)
                .setPositiveButton(
                        R.string.ai_restart_all_confirm,
                        (dialog, which) -> restartIrrigationProcess(
                                ALL_ZONES_RESET_SCOPE,
                                R.string.ai_restart_all_request_sent
                        )
                )
                .show();
    }

    private boolean isWateringActive(GardenZone zone) {
        ZoneIrrigationStatus status = zone == null
                ? null
                : zone.getIrrigation_status();
        return status != null && status.isWatering_active();
    }

    private GardenZone findRestartZone(String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            return null;
        }
        for (GardenZone zone : restartZones) {
            if (zone != null && zoneId.equals(zone.getZone_id())) {
                return zone;
            }
        }
        return null;
    }

    private String zoneName(GardenZone zone) {
        if (zone == null) {
            return "";
        }
        return com.alidogukan.avora.zones.PhysicalZoneIdentity.name(zone);
    }

    private void restartIrrigationProcess(String scope, int successMessage) {
        if (restartRequestInFlight) {
            return;
        }
        setRestartRequestInFlight(true);
        viewModel.restartIrrigationAssistant(scope)
                .addOnSuccessListener(unused -> {
                    setRestartRequestInFlight(false);
                    Toast.makeText(
                            this,
                            successMessage,
                            Toast.LENGTH_LONG
                    ).show();
                })
                .addOnFailureListener(error -> {
                    setRestartRequestInFlight(false);
                    Toast.makeText(
                            this,
                            R.string.ai_restart_request_failed,
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void setRestartRequestInFlight(boolean inFlight) {
        restartRequestInFlight = inFlight;
        cardRestartIrrigationProcess.setEnabled(!inFlight);
        cardRestartIrrigationProcess.setAlpha(inFlight ? 0.6f : 1f);
    }

    private void renderCommand(Command command) {
        if (command == null) {
            return;
        }
        if (commandLoaded && hasUnsavedChanges()) {
            return;
        }

        updatingUi = true;
        originalMoistureLimit = positiveOrDefault(
                command.getMoistureLimit(), DEFAULT_MOISTURE_LIMIT, 10, 80);
        originalPumpDuration = clamp(command.getPumpDuration(), 0, 18000);
        originalCooldownSeconds = roundToMinute(
                positiveOrDefault(command.getCooldownSeconds(), DEFAULT_COOLDOWN_SECONDS, 300, 3600));
        originalRestartDelta = positiveOrDefault(
                command.getRestartDelta(), DEFAULT_RESTART_DELTA, 1, 30);
        originalSystemEnabled = command.isEnabled();
        originalAutoMode = command.isAutoMode();

        sliderMoistureLimit.setValue(originalMoistureLimit);
        sliderPumpDuration.setValue(originalPumpDuration);
        sliderCooldown.setValue(originalCooldownSeconds);
        sliderRestartDelta.setValue(originalRestartDelta);
        switchSystemEnabled.setChecked(originalSystemEnabled);
        switchAutoMode.setChecked(originalAutoMode);

        updateMoistureLimitLabel(originalMoistureLimit);
        updatePumpDurationLabel(originalPumpDuration);
        updateCooldownLabel(originalCooldownSeconds);
        updateRestartDeltaLabel(originalRestartDelta);
        updateSystemEnabledDescription(originalSystemEnabled);
        updateAutoModeDescription(originalAutoMode);

        updatingUi = false;
        commandLoaded = true;
        updateUnsavedState();
    }

    private void performSave(boolean closeAfterSave) {
        autoSaveHandler.removeCallbacks(autoSaveRunnable);
        if (!hasUnsavedChanges()) {
            if (closeAfterSave) {
                finish();
            }
            return;
        }
        if (Boolean.TRUE.equals(viewModel.getSaving().getValue())) {
            pendingExitAfterSave = pendingExitAfterSave || closeAfterSave;
            return;
        }
        pendingExitAfterSave = closeAfterSave;
        viewModel.saveSettings(
                getMoistureLimit(),
                getPumpDuration(),
                getCooldownSeconds(),
                getRestartDelta(),
                switchSystemEnabled.isChecked(),
                switchAutoMode.isChecked(),
                collectTimingSettings()
        );
    }

    private void showResetConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_reset_dialog_title)
                .setMessage(R.string.settings_reset_dialog_message)
                .setNegativeButton(R.string.settings_cancel, null)
                .setPositiveButton(R.string.settings_reset_confirm,
                        (dialog, which) -> applyDefaultValuesToUi())
                .show();
    }

    private void applyDefaultValuesToUi() {
        updatingUi = true;
        sliderMoistureLimit.setValue(DEFAULT_MOISTURE_LIMIT);
        sliderPumpDuration.setValue(DEFAULT_PUMP_DURATION);
        sliderCooldown.setValue(DEFAULT_COOLDOWN_SECONDS);
        sliderRestartDelta.setValue(DEFAULT_RESTART_DELTA);
        switchSystemEnabled.setChecked(DEFAULT_SYSTEM_ENABLED);
        switchAutoMode.setChecked(DEFAULT_AUTO_MODE);

        updateMoistureLimitLabel(DEFAULT_MOISTURE_LIMIT);
        updatePumpDurationLabel(DEFAULT_PUMP_DURATION);
        updateCooldownLabel(DEFAULT_COOLDOWN_SECONDS);
        updateRestartDeltaLabel(DEFAULT_RESTART_DELTA);
        updateSystemEnabledDescription(DEFAULT_SYSTEM_ENABLED);
        updateAutoModeDescription(DEFAULT_AUTO_MODE);
        applyTimingSettingsToUi(IrrigationTimingSettings.defaults());
        updatingUi = false;

        updateUnsavedState();
        txtSettingsStatus.setText(R.string.settings_defaults_applied);
        scheduleAutoSave();
    }

    private void handleBackAction() {
        autoSaveHandler.removeCallbacks(autoSaveRunnable);
        if (Boolean.TRUE.equals(viewModel.getSaving().getValue())) {
            pendingExitAfterSave = true;
            return;
        }
        if (!hasUnsavedChanges()) {
            finish();
            return;
        }
        performSave(true);
    }
    private void updateUnsavedState() {
        boolean changed = hasUnsavedChanges();
        txtSettingsStatus.setText(changed
                ? R.string.settings_status_unsaved
                : R.string.settings_status_ready);
    }

    private boolean hasUnsavedChanges() {
        return getMoistureLimit() != originalMoistureLimit
                || getPumpDuration() != originalPumpDuration
                || getCooldownSeconds() != originalCooldownSeconds
                || getRestartDelta() != originalRestartDelta
                || switchSystemEnabled.isChecked() != originalSystemEnabled
                || switchAutoMode.isChecked() != originalAutoMode
                || (timingSettingsLoaded && !timingSettingsEqual(
                        collectTimingSettings(), originalTimingSettings));
    }

    private void saveCurrentValuesAsOriginal() {
        originalMoistureLimit = getMoistureLimit();
        originalPumpDuration = getPumpDuration();
        originalCooldownSeconds = getCooldownSeconds();
        originalRestartDelta = getRestartDelta();
        originalSystemEnabled = switchSystemEnabled.isChecked();
        originalAutoMode = switchAutoMode.isChecked();
        originalTimingSettings = copyTimingSettings(collectTimingSettings());
    }

    private void setControlsEnabled(boolean enabled) {
        settingsControlsEnabled = enabled;
        sliderMoistureLimit.setEnabled(enabled);
        sliderPumpDuration.setEnabled(enabled);
        sliderCooldown.setEnabled(enabled);
        sliderRestartDelta.setEnabled(enabled);
        switchAutoMode.setEnabled(enabled);
        switchSystemEnabled.setEnabled(enabled);
        switchSmartTiming.setEnabled(enabled);
        switchEveningIrrigation.setEnabled(enabled && switchSmartTiming.isChecked());
        switchIrrigationTimingRecheck.setEnabled(false);
        dropdownIrrigationEnvironment.setEnabled(enabled && switchSmartTiming.isChecked());
        dropdownIrrigationTimingStrategy.setEnabled(enabled && switchSmartTiming.isChecked());
        dropdownIrrigationStartHour.setEnabled(enabled && switchSmartTiming.isChecked());
        dropdownIrrigationEndHour.setEnabled(enabled && switchSmartTiming.isChecked());
        sliderIrrigationMaxDefer.setEnabled(enabled && switchSmartTiming.isChecked());
        sliderIrrigationCriticalDeficit.setEnabled(enabled && switchSmartTiming.isChecked());
        btnResetSettings.setEnabled(enabled);
        updateManualWateringSafetyAccess();
    }

    private void updateManualWateringSafetyAccess() {
        if (cardManualWateringSafety == null) return;
        boolean editable = settingsControlsEnabled && deviceOwner;
        cardManualWateringSafety.setEnabled(editable);
        cardManualWateringSafety.setAlpha(editable ? 1f : 0.72f);
        txtManualWateringSafetyAccess.setText(deviceOwner
                ? R.string.manual_watering_safety_owner_hint
                : R.string.manual_watering_safety_family_hint);
    }

    private void showManualWateringSafetyDialog() {
        View content = getLayoutInflater().inflate(
                R.layout.dialog_manual_watering_duration, null, false);
        TextView message = content.findViewById(
                R.id.txtManualWateringDurationMessage);
        TextInputLayout hoursLayout = content.findViewById(
                R.id.layoutManualWateringHours);
        TextInputLayout minutesLayout = content.findViewById(
                R.id.layoutManualWateringMinutes);
        TextInputLayout secondsLayout = content.findViewById(
                R.id.layoutManualWateringSeconds);
        TextInputEditText hoursInput = content.findViewById(
                R.id.inputManualWateringHours);
        TextInputEditText minutesInput = content.findViewById(
                R.id.inputManualWateringMinutes);
        TextInputEditText secondsInput = content.findViewById(
                R.id.inputManualWateringSeconds);

        int current = manualWateringSafetyLimitSeconds;
        message.setText(R.string.manual_watering_safety_dialog_message);
        hoursInput.setText(String.valueOf(current / 3600));
        minutesInput.setText(String.valueOf((current % 3600) / 60));
        secondsInput.setText(String.valueOf(current % 60));

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.manual_watering_safety_dialog_title)
                .setView(content)
                .setNegativeButton(R.string.settings_cancel, null)
                .setPositiveButton(R.string.manual_watering_safety_save, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(
                AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            hoursLayout.setError(null);
            minutesLayout.setError(null);
            secondsLayout.setError(null);
            int duration;
            try {
                duration = viewModel.manualWateringSafetyLimitFromParts(
                        parseDurationPart(hoursInput),
                        parseDurationPart(minutesInput),
                        parseDurationPart(secondsInput));
            } catch (IllegalArgumentException error) {
                secondsLayout.setError(getString(
                        R.string.manual_watering_safety_invalid));
                return;
            }
            dialog.dismiss();
            confirmOrSaveManualWateringSafetyLimit(duration);
        }));
        dialog.show();
    }

    private void confirmOrSaveManualWateringSafetyLimit(int durationSeconds) {
        if (!viewModel.requiresExtendedManualWateringConfirmation(durationSeconds)) {
            saveManualWateringSafetyLimit(durationSeconds);
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.manual_watering_safety_extended_title)
                .setMessage(getString(
                        R.string.manual_watering_safety_extended_message,
                        formatDuration(durationSeconds)))
                .setNegativeButton(R.string.settings_cancel, null)
                .setPositiveButton(R.string.manual_watering_safety_save,
                        (dialog, which) -> saveManualWateringSafetyLimit(durationSeconds))
                .show();
    }

    private void saveManualWateringSafetyLimit(int durationSeconds) {
        cardManualWateringSafety.setEnabled(false);
        viewModel.saveManualWateringSafetyLimit(durationSeconds)
                .addOnSuccessListener(unused -> {
                    manualWateringSafetyLimitSeconds = durationSeconds;
                    txtManualWateringSafetyLimit.setText(formatDuration(durationSeconds));
                    updateManualWateringSafetyAccess();
                    Toast.makeText(
                            this,
                            R.string.manual_watering_safety_saved,
                            Toast.LENGTH_SHORT
                    ).show();
                })
                .addOnFailureListener(error -> {
                    updateManualWateringSafetyAccess();
                    Toast.makeText(
                            this,
                            R.string.manual_watering_safety_save_failed,
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void restartIrrigationProcesses(List<String> zoneIds) {
        if (restartRequestInFlight || zoneIds == null || zoneIds.isEmpty()) {
            return;
        }
        setRestartRequestInFlight(true);
        viewModel.restartIrrigationAssistant(zoneIds)
                .addOnSuccessListener(unused -> {
                    setRestartRequestInFlight(false);
                    Toast.makeText(
                            this,
                            getString(
                                    R.string.ai_restart_selected_request_sent,
                                    zoneIds.size()),
                            Toast.LENGTH_LONG
                    ).show();
                })
                .addOnFailureListener(error -> {
                    setRestartRequestInFlight(false);
                    Toast.makeText(
                            this,
                            R.string.ai_restart_request_failed,
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private static int parseDurationPart(TextInputEditText input) {
        CharSequence value = input.getText();
        if (value == null || value.toString().trim().isEmpty()) return 0;
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private long getMoistureLimit() {
        return Math.round(sliderMoistureLimit.getValue());
    }

    private long getPumpDuration() {
        return Math.round(sliderPumpDuration.getValue());
    }

    private long getCooldownSeconds() {
        return Math.round(sliderCooldown.getValue());
    }

    private long getRestartDelta() {
        return Math.round(sliderRestartDelta.getValue());
    }

    private void initializeTimingDropdowns() {
        dropdownIrrigationEnvironment.setSimpleItems(new String[] {
                getString(R.string.irrigation_timing_environment_open_field),
                getString(R.string.irrigation_timing_environment_greenhouse),
                getString(R.string.irrigation_timing_environment_indoor)
        });
        dropdownIrrigationTimingStrategy.setSimpleItems(new String[] {
                getString(R.string.irrigation_timing_strategy_smart),
                getString(R.string.irrigation_timing_strategy_morning),
                getString(R.string.irrigation_timing_strategy_custom),
                getString(R.string.irrigation_timing_strategy_immediate)
        });
        String[] hourLabels = new String[24];
        for (int hour = 0; hour < hourLabels.length; hour++) {
            hourLabels[hour] = getString(R.string.irrigation_timing_hour_format, hour);
        }
        dropdownIrrigationStartHour.setSimpleItems(hourLabels);
        dropdownIrrigationEndHour.setSimpleItems(hourLabels);

        dropdownIrrigationEnvironment.setOnItemClickListener((parent, view, position, id) -> {
            if (!updatingUi) {
                onSettingChanged(true);
            }
        });
        dropdownIrrigationTimingStrategy.setOnItemClickListener((parent, view, position, id) -> {
            updateTimingControlState();
            if (!updatingUi) {
                onSettingChanged(true);
            }
        });
        dropdownIrrigationStartHour.setOnItemClickListener((parent, view, position, id) -> {
            if (!updatingUi) {
                onSettingChanged(true);
            }
        });
        dropdownIrrigationEndHour.setOnItemClickListener((parent, view, position, id) -> {
            if (!updatingUi) {
                onSettingChanged(true);
            }
        });
    }

    @Override
    protected void onStop() {
        autoSaveHandler.removeCallbacks(autoSaveRunnable);
        if (commandLoaded
                && timingSettingsLoaded
                && hasUnsavedChanges()
                && !Boolean.TRUE.equals(viewModel.getSaving().getValue())) {
            performSave(false);
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        autoSaveHandler.removeCallbacks(autoSaveRunnable);
        super.onDestroy();
    }
    private void renderTimingSettings(IrrigationTimingSettings settings) {
        if (settings == null) {
            return;
        }
        if (timingSettingsLoaded && hasUnsavedChanges()) {
            return;
        }
        updatingUi = true;
        originalTimingSettings = copyTimingSettings(settings);
        applyTimingSettingsToUi(originalTimingSettings);
        manualWateringSafetyLimitSeconds = settings.getManualWateringMaxDurationSeconds();
        txtManualWateringSafetyLimit.setText(
                formatDuration(manualWateringSafetyLimitSeconds));
        updatingUi = false;
        timingSettingsLoaded = true;
        updateUnsavedState();
        if (hasUnsavedChanges()) {
            scheduleAutoSave();
        }
    }

    private void applyTimingSettingsToUi(IrrigationTimingSettings settings) {
        switchSmartTiming.setChecked(settings.isSmartTimingEnabled());
        switchEveningIrrigation.setChecked(settings.isEveningIrrigationAllowed());
        switchIrrigationTimingRecheck.setChecked(true);
        sliderIrrigationMaxDefer.setValue(settings.getMaxIrrigationDeferMinutes());
        sliderIrrigationCriticalDeficit.setValue(settings.getCriticalMoistureDeficit());
        selectEnvironment(settings.getGardenEnvironment());
        selectTimingStrategy(settings.getTimingStrategy());
        dropdownIrrigationStartHour.setText(
                getString(R.string.irrigation_timing_hour_format, settings.getPreferredStartHour()), false);
        dropdownIrrigationEndHour.setText(
                getString(R.string.irrigation_timing_hour_format, settings.getPreferredEndHour()), false);
        updateIrrigationMaxDeferLabel(settings.getMaxIrrigationDeferMinutes());
        updateIrrigationCriticalDeficitLabel(settings.getCriticalMoistureDeficit());
        updateTimingControlState();
    }

    private void selectEnvironment(String code) {
        int index = indexOfCode(ENVIRONMENT_CODES, code);
        int[] labels = {
                R.string.irrigation_timing_environment_open_field,
                R.string.irrigation_timing_environment_greenhouse,
                R.string.irrigation_timing_environment_indoor
        };
        dropdownIrrigationEnvironment.setText(getString(labels[index]), false);
    }

    private void selectTimingStrategy(String code) {
        int index = indexOfCode(TIMING_STRATEGY_CODES, code);
        int[] labels = {
                R.string.irrigation_timing_strategy_smart,
                R.string.irrigation_timing_strategy_morning,
                R.string.irrigation_timing_strategy_custom,
                R.string.irrigation_timing_strategy_immediate
        };
        dropdownIrrigationTimingStrategy.setText(getString(labels[index]), false);
    }

    private IrrigationTimingSettings collectTimingSettings() {
        IrrigationTimingSettings settings = new IrrigationTimingSettings();
        settings.setSmartTimingEnabled(switchSmartTiming.isChecked());
        settings.setGardenEnvironment(ENVIRONMENT_CODES[indexOfEnvironmentLabel(
                dropdownIrrigationEnvironment.getText().toString())]);
        settings.setTimingStrategy(TIMING_STRATEGY_CODES[indexOfTimingStrategyLabel(
                dropdownIrrigationTimingStrategy.getText().toString())]);
        settings.setEveningIrrigationAllowed(switchEveningIrrigation.isChecked());
        settings.setMaxIrrigationDeferMinutes(Math.round(sliderIrrigationMaxDefer.getValue()));
        settings.setCriticalMoistureDeficit(Math.round(sliderIrrigationCriticalDeficit.getValue()));
        settings.setTimingRecheckEnabled(true);
        settings.setPreferredStartHour(parseHour(dropdownIrrigationStartHour.getText().toString(), 5));
        settings.setPreferredEndHour(parseHour(dropdownIrrigationEndHour.getText().toString(), 9));
        return settings;
    }

    private int indexOfEnvironmentLabel(String label) {
        String[] labels = {
                getString(R.string.irrigation_timing_environment_open_field),
                getString(R.string.irrigation_timing_environment_greenhouse),
                getString(R.string.irrigation_timing_environment_indoor)
        };
        return indexOfCode(labels, label);
    }

    private int indexOfTimingStrategyLabel(String label) {
        String[] labels = {
                getString(R.string.irrigation_timing_strategy_smart),
                getString(R.string.irrigation_timing_strategy_morning),
                getString(R.string.irrigation_timing_strategy_custom),
                getString(R.string.irrigation_timing_strategy_immediate)
        };
        return indexOfCode(labels, label);
    }

    private int indexOfCode(String[] values, String value) {
        if (value != null) {
            for (int index = 0; index < values.length; index++) {
                if (values[index].equalsIgnoreCase(value.trim())) {
                    return index;
                }
            }
        }
        return 0;
    }

    private int parseHour(String value, int fallback) {
        if (value == null || value.length() < 2) {
            return fallback;
        }
        try {
            return Math.max(0, Math.min(23, Integer.parseInt(value.substring(0, 2))));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void updateTimingControlState() {
        boolean enabled = switchSmartTiming.isChecked();
        boolean custom = "CUSTOM".equals(TIMING_STRATEGY_CODES[indexOfTimingStrategyLabel(
                dropdownIrrigationTimingStrategy.getText().toString())]);
        layoutCustomIrrigationHours.setVisibility(enabled && custom ? View.VISIBLE : View.GONE);
        switchEveningIrrigation.setEnabled(enabled);
        switchIrrigationTimingRecheck.setEnabled(false);
        dropdownIrrigationEnvironment.setEnabled(enabled);
        dropdownIrrigationTimingStrategy.setEnabled(enabled);
        sliderIrrigationMaxDefer.setEnabled(enabled);
        sliderIrrigationCriticalDeficit.setEnabled(enabled);
        dropdownIrrigationStartHour.setEnabled(enabled && custom);
        dropdownIrrigationEndHour.setEnabled(enabled && custom);
    }

    private void updateIrrigationMaxDeferLabel(long minutes) {
        long safeMinutes = Math.max(0, minutes);
        long hours = safeMinutes / 60;
        long remaining = safeMinutes % 60;
        if (hours == 0) {
            txtIrrigationMaxDeferValue.setText(
                    getString(R.string.irrigation_timing_minutes_format, remaining));
        } else if (remaining == 0) {
            txtIrrigationMaxDeferValue.setText(
                    getString(R.string.irrigation_timing_hours_format, hours));
        } else {
            txtIrrigationMaxDeferValue.setText(
                    getString(R.string.irrigation_timing_hours_minutes_format, hours, remaining));
        }
    }

    private void updateIrrigationCriticalDeficitLabel(long value) {
        txtIrrigationCriticalDeficitValue.setText(
                getString(R.string.settings_percentage_format, value));
    }

    private IrrigationTimingSettings copyTimingSettings(IrrigationTimingSettings source) {
        IrrigationTimingSettings copy = new IrrigationTimingSettings();
        copy.setSmartTimingEnabled(source.isSmartTimingEnabled());
        copy.setGardenEnvironment(source.getGardenEnvironment());
        copy.setTimingStrategy(source.getTimingStrategy());
        copy.setEveningIrrigationAllowed(source.isEveningIrrigationAllowed());
        copy.setMaxIrrigationDeferMinutes(source.getMaxIrrigationDeferMinutes());
        copy.setCriticalMoistureDeficit(source.getCriticalMoistureDeficit());
        copy.setTimingRecheckEnabled(source.isTimingRecheckEnabled());
        copy.setPreferredStartHour(source.getPreferredStartHour());
        copy.setPreferredEndHour(source.getPreferredEndHour());
        copy.setUpdatedAtEpoch(source.getUpdatedAtEpoch());
        return copy;
    }

    private boolean timingSettingsEqual(
            IrrigationTimingSettings first,
            IrrigationTimingSettings second
    ) {
        return first.isSmartTimingEnabled() == second.isSmartTimingEnabled()
                && first.getGardenEnvironment().equals(second.getGardenEnvironment())
                && first.getTimingStrategy().equals(second.getTimingStrategy())
                && first.isEveningIrrigationAllowed() == second.isEveningIrrigationAllowed()
                && first.getMaxIrrigationDeferMinutes() == second.getMaxIrrigationDeferMinutes()
                && first.getCriticalMoistureDeficit() == second.getCriticalMoistureDeficit()
                && first.isTimingRecheckEnabled() == second.isTimingRecheckEnabled()
                && first.getPreferredStartHour() == second.getPreferredStartHour()
                && first.getPreferredEndHour() == second.getPreferredEndHour();
    }

    private void updateMoistureLimitLabel(long value) {
        txtMoistureLimitValue.setText(
                getString(R.string.settings_percentage_format, value));
    }

    private void updatePumpDurationLabel(long value) {
        txtPumpDurationValue.setText(formatDuration(value));
    }

    private void updateCooldownLabel(long value) {
        txtCooldownValue.setText(formatDuration(value));
    }

    private void updateRestartDeltaLabel(long value) {
        txtRestartDeltaValue.setText(
                getString(R.string.settings_percentage_format, value));
    }

    private void updateSystemEnabledDescription(boolean enabled) {
        txtSystemEnabledDescription.setText(enabled
                ? R.string.settings_system_enabled_active
                : R.string.settings_system_enabled_inactive);
        txtSystemEnabledDescription.setTextColor(getColor(
                enabled ? R.color.online : R.color.offline));
    }

    private void updateAutoModeDescription(boolean autoMode) {
        if (!switchSystemEnabled.isChecked()) {
            txtAutoModeDescription.setText(R.string.irrigation_settings_auto_mode_blocked);
            txtAutoModeDescription.setTextColor(getColor(R.color.textSecondary));
            return;
        }
        txtAutoModeDescription.setText(autoMode
                ? R.string.irrigation_settings_auto_mode_active
                : R.string.irrigation_settings_auto_mode_inactive);
        txtAutoModeDescription.setTextColor(getColor(
                autoMode ? R.color.online : R.color.textSecondary));
    }

    private String formatDuration(long seconds) {
        long safeSeconds = Math.max(0, seconds);
        if (safeSeconds < 60) {
            return getString(R.string.settings_seconds_format, safeSeconds);
        }
        if (safeSeconds >= 3600) {
            long hours = safeSeconds / 3600;
            long minutes = (safeSeconds % 3600) / 60;
            return minutes == 0
                    ? getString(R.string.settings_hours_format, hours)
                    : getString(R.string.settings_hours_minutes_format, hours, minutes);
        }
        long minutes = safeSeconds / 60;
        long remainingSeconds = safeSeconds % 60;
        return remainingSeconds == 0
                ? getString(R.string.settings_minutes_format, minutes)
                : getString(R.string.settings_minutes_seconds_format, minutes, remainingSeconds);
    }

    private long positiveOrDefault(long value, long defaultValue, long min, long max) {
        return value <= 0 ? defaultValue : clamp(value, min, max);
    }

    private long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private long roundToMinute(long value) {
        return Math.round((double) value / 60L) * 60L;
    }
}
