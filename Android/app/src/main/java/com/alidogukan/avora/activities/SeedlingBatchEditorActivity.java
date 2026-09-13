package com.alidogukan.avora.activities;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.models.CropCatalogItem;
import com.alidogukan.avora.seedling.SeedlingCropCatalog;
import com.alidogukan.avora.seedling.SeedlingPlantingGuide;
import com.alidogukan.avora.seedling.SeedlingValidation;
import com.alidogukan.avora.seedling.SeedlingVarietyCatalog;
import com.alidogukan.avora.viewmodels.SeedlingViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Creates a new traceable seedling batch from the shared crop catalog. */
public final class SeedlingBatchEditorActivity extends EdgeToEdgeActivity {
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.forLanguageTag("tr-TR"));
    private static final String STATE_CROP_ID = "seedling_editor_crop_id";
    private static final String STATE_VARIETY = "seedling_editor_variety";
    private static final String STATE_AREA = "seedling_editor_area";
    private static final String STATE_SOWING_DAY = "seedling_editor_sowing_day";
    private static final String STATE_PRESERVED_TRAY = "seedling_editor_preserved_tray";

    private final List<CropCatalogItem> crops = new ArrayList<>();
    private SeedlingViewModel viewModel;
    private TextView plantValue;
    private TextView varietyValue;
    private TextView sowingDateValue;
    private TextView areaValue;
    private TextView emergenceDateValue;
    private TextView transplantDateValue;
    private TextView trayLabel;
    private TextView guideCrop;
    private TextView guideSoil;
    private TextView guideTray;
    private EditText seedCount;
    private EditText trayCells;
    private MaterialButton create;
    private CropCatalogItem selectedCrop;
    private String selectedVariety = "";
    private String selectedArea = "";
    private LocalDate sowingDate = LocalDate.now();
    private int preservedTrayCellCount = 100;
    private String pendingCropId = "";

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_seedling_batch_editor);
        viewModel = new ViewModelProvider(this).get(SeedlingViewModel.class);

        plantValue = findViewById(R.id.txtSeedlingPlantValue);
        varietyValue = findViewById(R.id.txtSeedlingVarietyValue);
        sowingDateValue = findViewById(R.id.txtSeedlingSowingDateValue);
        areaValue = findViewById(R.id.txtSeedlingAreaValue);
        emergenceDateValue = findViewById(R.id.txtSeedlingEmergenceDateValue);
        transplantDateValue = findViewById(R.id.txtSeedlingTransplantDateValue);
        trayLabel = findViewById(R.id.txtSeedlingTrayLabel);
        guideCrop = findViewById(R.id.txtSeedlingGuideCrop);
        guideSoil = findViewById(R.id.txtSeedlingGuideSoil);
        guideTray = findViewById(R.id.txtSeedlingGuideTray);
        seedCount = findViewById(R.id.inputSeedlingSeedCount);
        trayCells = findViewById(R.id.inputSeedlingTrayCells);
        create = findViewById(R.id.btnCreateSeedlingBatch);

        if (state == null) {
            seedCount.setText(R.string.seedling_form_seed_hint);
            trayCells.setText(R.string.seedling_form_tray_hint);
            selectedArea = getResources().getStringArray(R.array.seedling_area_options)[0];
        } else {
            pendingCropId = state.getString(STATE_CROP_ID, "");
            selectedVariety = state.getString(STATE_VARIETY, "");
            selectedArea = state.getString(STATE_AREA, "");
            sowingDate = LocalDate.ofEpochDay(state.getLong(
                    STATE_SOWING_DAY, LocalDate.now().toEpochDay()));
            preservedTrayCellCount = state.getInt(STATE_PRESERVED_TRAY, 100);
        }

        findViewById(R.id.btnBack).setOnClickListener(view -> finish());
        findViewById(R.id.rowSeedlingPlant).setOnClickListener(view -> showCropPicker());
        findViewById(R.id.rowSeedlingVariety).setOnClickListener(view -> showVarietyPicker());
        findViewById(R.id.rowSeedlingSowingDate).setOnClickListener(view -> showDatePicker());
        findViewById(R.id.rowSeedlingArea).setOnClickListener(view -> showAreaPicker());
        create.setOnClickListener(view -> submit());

        updateCrops(viewModel.mergedCrops(null), state != null);
        viewModel.getCropCatalogItems().observe(this,
                values -> updateCrops(viewModel.mergedCrops(values), true));
    }

    private void updateCrops(List<CropCatalogItem> values, boolean preserveSelection) {
        String selectedId = preserveSelection
                ? (selectedCrop == null ? pendingCropId : selectedCrop.getCrop_id())
                : null;
        crops.clear();
        if (values != null) crops.addAll(values);
        selectedCrop = findCrop(selectedId);
        if (selectedCrop == null && !crops.isEmpty()) selectedCrop = crops.get(0);

        List<String> varieties = varietiesForSelectedCrop();
        String preservedVariety = SeedlingVarietyCatalog.find(varieties, selectedVariety);
        if (!preserveSelection || preservedVariety == null) {
            selectedVariety = varieties.isEmpty() ? "" : varieties.get(0);
        } else {
            selectedVariety = preservedVariety;
        }
        renderSelection();
    }

    @Nullable
    private CropCatalogItem findCrop(String cropId) {
        for (CropCatalogItem crop : crops) {
            if (cropId != null && cropId.equals(crop.getCrop_id())) return crop;
        }
        return null;
    }

    private void showCropPicker() {
        if (crops.isEmpty()) return;
        String[] labels = new String[crops.size()];
        int checked = 0;
        for (int i = 0; i < crops.size(); i++) {
            labels[i] = crops.get(i).toString();
            if (selectedCrop != null
                    && selectedCrop.getCrop_id().equals(crops.get(i).getCrop_id())) {
                checked = i;
            }
        }
        final int selectedIndex = checked;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.seedling_plant_type)
                .setSingleChoiceItems(labels, selectedIndex, (dialog, which) -> {
                    selectedCrop = crops.get(which);
                    pendingCropId = selectedCrop.getCrop_id();
                    List<String> varieties = varietiesForSelectedCrop();
                    selectedVariety = varieties.isEmpty() ? "" : varieties.get(0);
                    renderSelection();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.settings_cancel, null)
                .show();
    }

    private void showVarietyPicker() {
        if (selectedCrop == null) return;
        List<String> varieties = varietiesForSelectedCrop();
        List<String> options = new ArrayList<>(varieties);
        options.add(getString(R.string.seedling_variety_custom));
        String current = SeedlingVarietyCatalog.find(varieties, selectedVariety);
        int checked = current == null ? -1 : varieties.indexOf(current);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.seedling_variety)
                .setSingleChoiceItems(options.toArray(new String[0]), checked,
                        (dialog, which) -> {
                            dialog.dismiss();
                            if (which == varieties.size()) {
                                showCustomVarietyDialog();
                            } else {
                                selectedVariety = varieties.get(which);
                                varietyValue.setText(selectedVariety);
                            }
                        })
                .setNegativeButton(R.string.settings_cancel, null)
                .show();
    }

    private void showCustomVarietyDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(SeedlingVarietyCatalog.MAX_NAME_LENGTH)
        });
        input.setHint(R.string.seedling_variety_custom_hint);
        if (SeedlingVarietyCatalog.find(
                SeedlingCropCatalog.profileFor(selectedCrop).getVarieties(),
                selectedVariety) == null) {
            input.setText(selectedVariety);
            input.setSelection(input.length());
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.seedling_variety_custom_title)
                .setView(input)
                .setNegativeButton(R.string.settings_cancel, null)
                .setPositiveButton(R.string.settings_save, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String value = SeedlingVarietyCatalog.normalize(
                            input.getText().toString());
                    if (value.isBlank()) {
                        input.setError(getString(R.string.seedling_variety_required));
                        return;
                    }
                    List<String> currentOptions = varietiesForSelectedCrop();
                    String existing = SeedlingVarietyCatalog.find(currentOptions, value);
                    if (existing != null) {
                        selectedVariety = existing;
                        varietyValue.setText(existing);
                        Toast.makeText(this, R.string.seedling_variety_already_exists,
                                Toast.LENGTH_SHORT).show();
                    } else {
                        selectedVariety = viewModel.saveVariety(selectedCrop, value);
                        varietyValue.setText(selectedVariety);
                        Toast.makeText(this, R.string.seedling_variety_saved,
                                Toast.LENGTH_SHORT).show();
                    }
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private List<String> varietiesForSelectedCrop() {
        return new ArrayList<>(viewModel.varietiesFor(selectedCrop));
    }

    private void showAreaPicker() {
        String[] options = getResources().getStringArray(R.array.seedling_area_options);
        int checked = 0;
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(selectedArea)) checked = i;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.seedling_area)
                .setSingleChoiceItems(options, checked, (dialog, which) -> {
                    selectedArea = options[which];
                    areaValue.setText(selectedArea);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.settings_cancel, null)
                .show();
    }

    private void showDatePicker() {
        new DatePickerDialog(this, (view, year, month, day) -> {
            sowingDate = LocalDate.of(year, month + 1, day);
            renderDates();
        }, sowingDate.getYear(), sowingDate.getMonthValue() - 1,
                sowingDate.getDayOfMonth()).show();
    }

    private void renderSelection() {
        plantValue.setText(selectedCrop == null ? "—" : selectedCrop.getName());
        varietyValue.setText(selectedVariety);
        areaValue.setText(selectedArea);
        renderPlantingGuide();
        renderDates();
    }

    private void renderPlantingGuide() {
        SeedlingPlantingGuide.Guide guide = SeedlingPlantingGuide.forCrop(selectedCrop);
        String cropLabel = selectedCrop == null ? "—" : selectedCrop.toString();
        guideCrop.setText(getString(R.string.seedling_guide_for_crop, cropLabel));
        guideSoil.setText(guide.getSoilText());
        guideTray.setText(guide.getTrayText());

        if (guide.isTrayCountRequired()) {
            trayLabel.setText(R.string.seedling_tray_cells);
            trayCells.setVisibility(View.VISIBLE);
            trayCells.setEnabled(true);
            if (integer(trayCells) <= 0) {
                trayCells.setText(String.valueOf(Math.max(1, preservedTrayCellCount)));
            }
        } else {
            int current = integer(trayCells);
            if (current > 0) preservedTrayCellCount = current;
            trayLabel.setText(R.string.seedling_tray_not_needed);
            trayCells.setText("0");
            trayCells.setEnabled(false);
            trayCells.setVisibility(View.GONE);
        }
    }

    private void renderDates() {
        SeedlingCropCatalog.Profile profile =
                SeedlingCropCatalog.profileFor(selectedCrop);
        sowingDateValue.setText(DATE_FORMAT.format(sowingDate));
        emergenceDateValue.setText(DATE_FORMAT.format(
                sowingDate.plusDays(profile.getEmergenceDays())));
        transplantDateValue.setText(DATE_FORMAT.format(
                sowingDate.plusDays(profile.getTransplantDays())));
    }

    private void submit() {
        int seedValue = integer(seedCount);
        int trayValue = integer(trayCells);
        SeedlingPlantingGuide.Guide plantingGuide =
                SeedlingPlantingGuide.forCrop(selectedCrop);
        if (selectedCrop == null || selectedVariety.isBlank()
                || seedValue <= 0
                || seedValue > SeedlingValidation.MAX_SEED_COUNT
                || trayValue > SeedlingValidation.MAX_TRAY_CELL_COUNT
                || (plantingGuide.isTrayCountRequired() && trayValue <= 0)) {
            Toast.makeText(this, plantingGuide.isTrayCountRequired()
                            ? R.string.seedling_required_fields
                            : R.string.seedling_required_fields_no_tray,
                    Toast.LENGTH_LONG).show();
            return;
        }

        SeedlingCropCatalog.Profile profile =
                SeedlingCropCatalog.profileFor(selectedCrop);
        long sowingEpoch = epoch(sowingDate);
        create.setEnabled(false);
        viewModel.createBatch(selectedCrop, selectedVariety, selectedArea,
                        seedValue, trayValue, sowingEpoch,
                        epoch(sowingDate.plusDays(profile.getEmergenceDays())),
                        epoch(sowingDate.plusDays(profile.getTransplantDays())))
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, R.string.seedling_create_success,
                            Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(error -> {
                    create.setEnabled(true);
                    Toast.makeText(this, R.string.seedling_create_failed,
                            Toast.LENGTH_LONG).show();
                });
    }

    private static int integer(EditText input) {
        try {
            return Integer.parseInt(input.getText().toString().trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static long epoch(LocalDate value) {
        return value.atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString(STATE_CROP_ID, selectedCrop == null
                ? pendingCropId : selectedCrop.getCrop_id());
        state.putString(STATE_VARIETY, selectedVariety);
        state.putString(STATE_AREA, selectedArea);
        state.putLong(STATE_SOWING_DAY, sowingDate.toEpochDay());
        state.putInt(STATE_PRESERVED_TRAY, preservedTrayCellCount);
        super.onSaveInstanceState(state);
    }
}
