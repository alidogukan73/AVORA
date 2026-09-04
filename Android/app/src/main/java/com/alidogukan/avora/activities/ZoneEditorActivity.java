package com.alidogukan.avora.activities;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.models.CropCatalogItem;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.viewmodels.ZoneManagementViewModel;
import com.alidogukan.avora.zones.PhysicalZoneIdentity;
import com.alidogukan.avora.zones.ZoneHardwareSelection;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

/** Full-screen, live-preview editor shared by create and edit flows. */
public final class ZoneEditorActivity extends EdgeToEdgeActivity {
    public static final String EXTRA_ZONE_ID = "zone_editor.zone_id";

    private ZoneManagementViewModel viewModel;
    private final List<GardenZone> zones = new ArrayList<>();
    private final List<Integer> selectableSlots = new ArrayList<>();
    private List<CropCatalogItem> crops = new ArrayList<>();
    private List<HardwareOption> sensorOptions = new ArrayList<>();
    private List<HardwareOption> valveOptions = new ArrayList<>();
    private final ZoneHardwareSelection hardwareSelection = new ZoneHardwareSelection();

    private TextInputEditText areaName;
    private TextView editorTitle;
    private TextView editorSubtitle;
    private MaterialAutoCompleteTextView zoneDropdown;
    private MaterialAutoCompleteTextView locationDropdown;
    private MaterialAutoCompleteTextView cropDropdown;
    private MaterialAutoCompleteTextView valveDropdown;
    private MaterialAutoCompleteTextView sensorDropdown;
    private MaterialSwitch automaticIrrigation;
    private MaterialSwitch lowMoistureAlert;
    private MaterialSwitch wateringCompleteAlert;
    private Slider moistureThreshold;
    private TextView thresholdValue;
    private TextView previewIcon;
    private TextView previewTitle;
    private TextView previewMoisture;
    private TextView previewWatering;
    private MaterialCardView previewCard;
    private MaterialButton saveButton;

    private int slot = -1;
    private int selectedCropIndex;
    private String selectedLocation = "";
    private String selectedIcon = "🌿";
    private String selectedColor = "#2E7D32";
    private String requestedZoneId = "";
    private boolean editMode;
    private boolean existingValuesBound;
    private boolean zonesReady;
    private boolean saving;
    private boolean thresholdChangedByUser;

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        requestedZoneId = safe(getIntent().getStringExtra(EXTRA_ZONE_ID));
        editMode = !requestedZoneId.isEmpty();
        setContentView(R.layout.activity_zone_editor);
        bindViews();
        configureStaticControls();
        configureModeLabels();

        viewModel = new ViewModelProvider(this).get(ZoneManagementViewModel.class);
        crops = viewModel.mergedCrops(null);
        renderCropChoices("tomato");

        viewModel.getZones().observe(this, values -> {
            zonesReady = true;
            zones.clear();
            if (values != null) zones.addAll(values);
            if (editMode) {
                GardenZone existing = findZone(requestedZoneId);
                if (existing == null || viewModel.isInactive(existing)) {
                    Toast.makeText(this, R.string.zone_editor_zone_not_found,
                            Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                slot = PhysicalZoneIdentity.slot(existing);
                if (!existingValuesBound) bindExisting(existing);
            } else {
                selectAvailableSlot();
            }
            renderSlotChoices();
            renderHardwareChoices();
            updateReadyState();
            renderPreview();
        });
        viewModel.getCropCatalogItems().observe(this, values -> {
            String selectedType = selectedCrop() == null
                    ? "tomato" : selectedCrop().getPlant_type();
            crops = viewModel.mergedCrops(values);
            renderCropChoices(selectedType);
            updateReadyState();
            renderPreview();
        });
    }

    private void bindViews() {
        areaName = findViewById(R.id.inputAreaName);
        editorTitle = findViewById(R.id.txtZoneEditorTitle);
        editorSubtitle = findViewById(R.id.txtZoneEditorSubtitle);
        zoneDropdown = findViewById(R.id.dropdownZoneSlot);
        locationDropdown = findViewById(R.id.dropdownLocation);
        cropDropdown = findViewById(R.id.dropdownCrop);
        valveDropdown = findViewById(R.id.dropdownValve);
        sensorDropdown = findViewById(R.id.dropdownSensor);
        automaticIrrigation = findViewById(R.id.switchAutomaticIrrigation);
        lowMoistureAlert = findViewById(R.id.switchLowMoistureAlert);
        wateringCompleteAlert = findViewById(R.id.switchWateringCompleteAlert);
        moistureThreshold = findViewById(R.id.sliderMoistureThreshold);
        thresholdValue = findViewById(R.id.txtMoistureThreshold);
        previewIcon = findViewById(R.id.txtPreviewIcon);
        previewTitle = findViewById(R.id.txtPreviewTitle);
        previewMoisture = findViewById(R.id.txtPreviewMoisture);
        previewWatering = findViewById(R.id.txtPreviewWatering);
        previewCard = findViewById(R.id.cardPreview);
        saveButton = findViewById(R.id.btnSaveZone);

        findViewById(R.id.btnBack).setOnClickListener(view -> finish());
        findViewById(R.id.btnCancel).setOnClickListener(view -> finish());
        saveButton.setOnClickListener(view -> save());
    }

    private void configureStaticControls() {
        String[] locations = getResources().getStringArray(R.array.zone_editor_locations);
        locationDropdown.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, locations));
        if (locations.length > 0) {
            selectedLocation = locations[0];
            locationDropdown.setText(selectedLocation, false);
        }
        locationDropdown.setOnItemClickListener((parent, view, position, id) -> {
            selectedLocation = locations[position];
            renderPreview();
        });

        areaName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                renderPreview();
            }
            @Override public void afterTextChanged(Editable value) { }
        });
        automaticIrrigation.setOnCheckedChangeListener((button, checked) -> renderPreview());
        moistureThreshold.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) thresholdChangedByUser = true;
            renderThreshold(Math.round(value));
            renderPreview();
        });

        MaterialButtonToggleGroup icons = findViewById(R.id.groupIcons);
        icons.addOnButtonCheckedListener((group, checkedId, checked) -> {
            if (!checked) return;
            if (checkedId == R.id.btnIconFlower) selectedIcon = "🌼";
            else if (checkedId == R.id.btnIconSeedling) selectedIcon = "🌱";
            else if (checkedId == R.id.btnIconTree) selectedIcon = "🌳";
            else selectedIcon = "🌿";
            renderPreview();
        });
        icons.check(R.id.btnIconLeaf);

        MaterialButtonToggleGroup colors = findViewById(R.id.groupColors);
        colors.addOnButtonCheckedListener((group, checkedId, checked) -> {
            if (!checked) return;
            if (checkedId == R.id.btnColorBlue) selectedColor = "#1976D2";
            else if (checkedId == R.id.btnColorPurple) selectedColor = "#7E57C2";
            else if (checkedId == R.id.btnColorOrange) selectedColor = "#EF6C00";
            else if (checkedId == R.id.btnColorBrown) selectedColor = "#8D4E24";
            else selectedColor = "#2E7D32";
            renderPreview();
        });
        colors.check(R.id.btnColorGreen);
        renderThreshold(Math.round(moistureThreshold.getValue()));
    }

    private void configureModeLabels() {
        if (!editMode) return;
        editorTitle.setText(R.string.zone_editor_edit_title);
        editorSubtitle.setText(R.string.zone_editor_edit_subtitle);
        saveButton.setText(R.string.zone_editor_update);
    }

    private void bindExisting(GardenZone existing) {
        existingValuesBound = true;
        areaName.setText(PhysicalZoneIdentity.name(existing));
        areaName.setSelection(areaName.length());

        String storedLocation = safe(existing.getLocation_name());
        if (!storedLocation.isEmpty()) {
            selectedLocation = storedLocation;
            locationDropdown.setText(storedLocation, false);
        }

        renderCropChoices(existing.getPlant_type());
        thresholdChangedByUser = true;
        moistureThreshold.setValue(clampThreshold(existing.getMoisture_limit()));
        automaticIrrigation.setChecked(existing.isIrrigation_enabled());
        lowMoistureAlert.setChecked(existing.isLow_moisture_alert_enabled());
        wateringCompleteAlert.setChecked(existing.isWatering_complete_alert_enabled());

        selectedIcon = PhysicalZoneIdentity.icon(existing);
        selectIcon(selectedIcon);
        selectedColor = PhysicalZoneIdentity.color(existing);
        selectColor(selectedColor);
    }

    private void selectIcon(String icon) {
        MaterialButtonToggleGroup group = findViewById(R.id.groupIcons);
        if ("🌼".equals(icon)) group.check(R.id.btnIconFlower);
        else if ("🌱".equals(icon)) group.check(R.id.btnIconSeedling);
        else if ("🌳".equals(icon)) group.check(R.id.btnIconTree);
        else group.check(R.id.btnIconLeaf);
    }

    private void selectColor(String color) {
        MaterialButtonToggleGroup group = findViewById(R.id.groupColors);
        if ("#1976D2".equalsIgnoreCase(color)) group.check(R.id.btnColorBlue);
        else if ("#7E57C2".equalsIgnoreCase(color)) group.check(R.id.btnColorPurple);
        else if ("#EF6C00".equalsIgnoreCase(color)) group.check(R.id.btnColorOrange);
        else if ("#8D4E24".equalsIgnoreCase(color)) group.check(R.id.btnColorBrown);
        else group.check(R.id.btnColorGreen);
    }

    private void selectAvailableSlot() {
        List<Integer> available = viewModel.availableSlots(zones);
        if (available.isEmpty()) {
            Toast.makeText(this, R.string.zone_management_capacity_full,
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (!available.contains(slot)) selectSlot(available.get(0));
    }

    private void renderSlotChoices() {
        selectableSlots.clear();
        if (editMode) {
            if (slot > 0) selectableSlots.add(slot);
        } else {
            selectableSlots.addAll(viewModel.availableSlots(zones));
        }

        List<String> labels = new ArrayList<>();
        for (Integer option : selectableSlots) {
            labels.add(PhysicalZoneIdentity.defaultName(option));
        }
        zoneDropdown.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, labels));
        zoneDropdown.setEnabled(!editMode && !selectableSlots.isEmpty());

        int selectedPosition = selectableSlots.indexOf(slot);
        if (selectedPosition >= 0) {
            zoneDropdown.setText(labels.get(selectedPosition), false);
        } else {
            zoneDropdown.setText("", false);
        }
        zoneDropdown.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= selectableSlots.size()) return;
            selectSlot(selectableSlots.get(position));
            renderHardwareChoices();
            updateReadyState();
            renderPreview();
        });
    }

    private void selectSlot(int nextSlot) {
        if (nextSlot <= 0 || nextSlot == slot) return;
        int previousSlot = slot;
        String currentName = text(areaName);
        String previousDefault = previousSlot > 0
                ? PhysicalZoneIdentity.defaultName(previousSlot) : "";
        slot = nextSlot;
        if (currentName.isEmpty() || previousSlot <= 0 || currentName.equals(previousDefault)) {
            areaName.setText(PhysicalZoneIdentity.defaultName(slot));
            areaName.setSelection(areaName.length());
        }
    }

    private void renderCropChoices(String preferredType) {
        if (crops.isEmpty()) return;
        selectedCropIndex = 0;
        for (int index = 0; index < crops.size(); index++) {
            if (safe(crops.get(index).getPlant_type()).equalsIgnoreCase(safe(preferredType))) {
                selectedCropIndex = index;
                break;
            }
        }
        cropDropdown.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, crops));
        cropDropdown.setText(crops.get(selectedCropIndex).toString(), false);
        cropDropdown.setOnItemClickListener((parent, view, position, id) -> {
            selectedCropIndex = position;
            if (!thresholdChangedByUser) {
                moistureThreshold.setValue(clampThreshold(
                        crops.get(position).getIdeal_moisture_min()));
            }
            renderPreview();
        });
        if (!thresholdChangedByUser) {
            moistureThreshold.setValue(clampThreshold(selectedCrop().getIdeal_moisture_min()));
        }
    }

    private void renderHardwareChoices() {
        if (slot <= 0) return;
        GardenZone existing = editMode ? findZone(requestedZoneId) : null;
        String preferredSensor = existing == null ? viewModel.sensorId(slot) : existing.getSensor_id();
        String preferredValve = existing == null ? viewModel.valveId(slot) : existing.getValve_id();
        if (!editMode) {
            if (assignedToActiveZone(preferredSensor, true)) preferredSensor = "";
            if (assignedToActiveZone(preferredValve, false)) preferredValve = "";
        }
        // Bind defaults once per channel, never on every live telemetry update.
        hardwareSelection.bindZone(viewModel.zoneId(slot), preferredSensor, preferredValve);
        sensorOptions = hardwareOptions(true);
        valveOptions = hardwareOptions(false);
        sensorDropdown.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, sensorOptions));
        valveDropdown.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, valveOptions));

        sensorDropdown.setText(
                sensorOptions.get(optionPosition(sensorOptions, selectedSensorId())).toString(), false);
        valveDropdown.setText(
                valveOptions.get(optionPosition(valveOptions, selectedValveId())).toString(), false);
        renderHardwareErrors();
        sensorDropdown.setOnItemClickListener((parent, view, position, id) -> {
            hardwareSelection.selectSensor(sensorOptions.get(position).id);
            renderHardwareErrors();
            renderPreview();
        });
        valveDropdown.setOnItemClickListener((parent, view, position, id) -> {
            hardwareSelection.selectValve(valveOptions.get(position).id);
            renderHardwareErrors();
            renderPreview();
        });
    }

    private void renderHardwareErrors() {
        sensorDropdown.setError(assignedToActiveZone(selectedSensorId(), true)
                ? getString(R.string.zone_management_error_sensor_used) : null);
        valveDropdown.setError(assignedToActiveZone(selectedValveId(), false)
                ? getString(R.string.zone_management_error_valve_used) : null);
    }

    private List<HardwareOption> hardwareOptions(boolean sensor) {
        List<HardwareOption> result = new ArrayList<>();
        result.add(new HardwareOption("", getString(R.string.zone_management_unassigned)));
        String selected = sensor ? selectedSensorId() : selectedValveId();
        for (int index = 1; index <= ZoneManagementViewModel.MAX_ZONES; index++) {
            String id = sensor ? viewModel.sensorId(index) : viewModel.valveId(index);
            // Keep a conflicting draft visible; validation reports it instead of changing it.
            if (id.equalsIgnoreCase(selected) || !assignedToActiveZone(id, sensor)) {
                result.add(new HardwareOption(id, id));
            }
        }
        return result;
    }

    private boolean assignedToActiveZone(String id, boolean sensor) {
        if (safe(id).isEmpty()) return false;
        for (GardenZone zone : zones) {
            if (zone == null || viewModel.isInactive(zone)) continue;
            if (editMode && requestedZoneId.equals(safe(zone.getZone_id()))) continue;
            String assigned = sensor ? safe(zone.getSensor_id()) : safe(zone.getValve_id());
            if (id.equalsIgnoreCase(assigned)) return true;
        }
        return false;
    }

    private void renderPreview() {
        if (previewTitle == null) return;
        CropCatalogItem crop = selectedCrop();
        String name = text(areaName);
        if (name.isEmpty() && slot > 0) name = PhysicalZoneIdentity.defaultName(slot);
        String cropName = crop == null ? "" : safe(crop.getName());
        previewTitle.setText(cropName.isEmpty() ? name : name + " · " + cropName);
        previewIcon.setText(selectedIcon);
        renderThreshold(Math.round(moistureThreshold.getValue()));

        boolean hardwareReady = selectedSensorId().length() > 0 && selectedValveId().length() > 0;
        if (automaticIrrigation.isChecked() && hardwareReady) {
            previewWatering.setText(R.string.zone_editor_watering_auto);
        } else if (automaticIrrigation.isChecked()) {
            previewWatering.setText(R.string.zone_editor_hardware_pending);
        } else {
            previewWatering.setText(R.string.zone_editor_watering_off);
        }
        applyPreviewColor();
    }

    private void renderThreshold(int value) {
        thresholdValue.setText(getString(R.string.zone_editor_preview_threshold, value));
        previewMoisture.setText(getString(R.string.zone_editor_preview_threshold, value));
    }

    private void applyPreviewColor() {
        int color;
        try {
            color = Color.parseColor(selectedColor);
        } catch (IllegalArgumentException ignored) {
            color = Color.parseColor("#2E7D32");
        }
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.argb(32, Color.red(color), Color.green(color), Color.blue(color)));
        circle.setStroke(dp(2), color);
        previewIcon.setBackground(circle);
        previewCard.setStrokeColor(color);
    }

    private void save() {
        if (saving) return;
        if (!zonesReady || slot <= 0 || selectedCrop() == null) {
            Toast.makeText(this, R.string.zone_editor_not_ready, Toast.LENGTH_LONG).show();
            return;
        }
        if (!editMode && !viewModel.availableSlots(zones).contains(slot)) {
            Toast.makeText(this, R.string.zone_editor_slot_unavailable,
                    Toast.LENGTH_LONG).show();
            selectAvailableSlot();
            renderSlotChoices();
            return;
        }
        String name = text(areaName);
        if (name.isEmpty()) {
            areaName.setError(getString(R.string.zone_editor_name_required));
            areaName.requestFocus();
            return;
        }
        String sensorId = selectedSensorId();
        String valveId = selectedValveId();
        if (assignedToActiveZone(sensorId, true) || assignedToActiveZone(valveId, false)) {
            renderHardwareErrors();
            return;
        }
        if (automaticIrrigation.isChecked() && (sensorId.isEmpty() || valveId.isEmpty())) {
            Toast.makeText(this, R.string.zone_editor_hardware_required,
                    Toast.LENGTH_LONG).show();
            return;
        }

        GardenZone existing = findZone(viewModel.zoneId(slot));
        GardenZone candidate = viewModel.createCandidate(existing, slot, selectedCrop(),
                name, sensorId, valveId, automaticIrrigation.isChecked());
        candidate.setLocation_name(selectedLocation);
        candidate.setArea_icon(selectedIcon);
        candidate.setArea_color(selectedColor);
        candidate.setMoisture_limit(Math.round(moistureThreshold.getValue()));
        candidate.setLow_moisture_alert_enabled(lowMoistureAlert.isChecked());
        candidate.setWatering_complete_alert_enabled(wateringCompleteAlert.isChecked());

        saving = true;
        updateReadyState();
        viewModel.saveZone(candidate, !editMode)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this,
                            sensorId.isEmpty() || valveId.isEmpty()
                                    ? R.string.zone_management_saved_pending
                                    : R.string.zone_management_saved,
                            Toast.LENGTH_LONG).show();
                    finish();
                })
                .addOnFailureListener(error -> {
                    saving = false;
                    updateReadyState();
                    Toast.makeText(this, friendlyError(error), Toast.LENGTH_LONG).show();
                });
    }

    private void updateReadyState() {
        saveButton.setEnabled(!saving && zonesReady && slot > 0 && !crops.isEmpty());
    }

    private CropCatalogItem selectedCrop() {
        return selectedCropIndex >= 0 && selectedCropIndex < crops.size()
                ? crops.get(selectedCropIndex) : null;
    }

    private String selectedSensorId() {
        return hardwareSelection.getSensorId();
    }

    private String selectedValveId() {
        return hardwareSelection.getValveId();
    }

    private int optionPosition(List<HardwareOption> options, String id) {
        for (int index = 0; index < options.size(); index++) {
            if (options.get(index).id.equalsIgnoreCase(safe(id))) return index;
        }
        return 0;
    }

    private GardenZone findZone(String zoneId) {
        for (GardenZone zone : zones) {
            if (zone != null && zoneId.equals(zone.getZone_id())) return zone;
        }
        return null;
    }

    private String friendlyError(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null) current = current.getCause();
        String code = current == null || current.getMessage() == null ? "" : current.getMessage();
        if (ZoneManagementViewModel.ERROR_SENSOR_IN_USE.equals(code)) {
            return getString(R.string.zone_management_error_sensor_used);
        }
        if (ZoneManagementViewModel.ERROR_VALVE_IN_USE.equals(code)) {
            return getString(R.string.zone_management_error_valve_used);
        }
        if (ZoneManagementViewModel.ERROR_ZONE_IN_USE.equals(code)) {
            return getString(R.string.zone_management_error_zone_used);
        }
        return getString(R.string.zone_editor_save_failed, code);
    }

    private float clampThreshold(int value) {
        return Math.max(20, Math.min(70, Math.round(value / 5f) * 5));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String text(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class HardwareOption {
        final String id;
        final String label;

        HardwareOption(String id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override public String toString() { return label; }
    }
}
