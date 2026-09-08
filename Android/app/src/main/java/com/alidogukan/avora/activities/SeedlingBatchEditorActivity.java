package com.alidogukan.avora.activities;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.models.CropCatalogItem;
import com.alidogukan.avora.seedling.SeedlingCropCatalog;
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

    private final List<CropCatalogItem> crops = new ArrayList<>();
    private SeedlingViewModel viewModel;
    private TextView plantValue;
    private TextView varietyValue;
    private TextView sowingDateValue;
    private TextView areaValue;
    private TextView emergenceDateValue;
    private TextView transplantDateValue;
    private EditText seedCount;
    private EditText trayCells;
    private MaterialButton create;
    private CropCatalogItem selectedCrop;
    private String selectedVariety = "";
    private String selectedArea = "";
    private LocalDate sowingDate = LocalDate.now();

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
        seedCount = findViewById(R.id.inputSeedlingSeedCount);
        trayCells = findViewById(R.id.inputSeedlingTrayCells);
        create = findViewById(R.id.btnCreateSeedlingBatch);

        seedCount.setText(R.string.seedling_form_seed_hint);
        trayCells.setText(R.string.seedling_form_tray_hint);
        selectedArea = getResources().getStringArray(R.array.seedling_area_options)[0];

        findViewById(R.id.btnBack).setOnClickListener(view -> finish());
        findViewById(R.id.rowSeedlingPlant).setOnClickListener(view -> showCropPicker());
        findViewById(R.id.rowSeedlingVariety).setOnClickListener(view -> showVarietyPicker());
        findViewById(R.id.rowSeedlingSowingDate).setOnClickListener(view -> showDatePicker());
        findViewById(R.id.rowSeedlingArea).setOnClickListener(view -> showAreaPicker());
        create.setOnClickListener(view -> submit());

        updateCrops(viewModel.mergedCrops(null), false);
        viewModel.getCropCatalogItems().observe(this,
                values -> updateCrops(viewModel.mergedCrops(values), true));
    }

    private void updateCrops(List<CropCatalogItem> values, boolean preserveSelection) {
        String selectedId = preserveSelection && selectedCrop != null
                ? selectedCrop.getCrop_id() : "tomato";
        crops.clear();
        if (values != null) crops.addAll(values);
        selectedCrop = findCrop(selectedId);
        if (selectedCrop == null) selectedCrop = findCrop("tomato");
        if (selectedCrop == null && !crops.isEmpty()) selectedCrop = crops.get(0);

        List<String> varieties = SeedlingCropCatalog.profileFor(selectedCrop).getVarieties();
        if (!preserveSelection || selectedVariety.isBlank()) {
            selectedVariety = varieties.get(0);
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
                    List<String> varieties =
                            SeedlingCropCatalog.profileFor(selectedCrop).getVarieties();
                    selectedVariety = varieties.get(0);
                    renderSelection();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.settings_cancel, null)
                .show();
    }

    private void showVarietyPicker() {
        if (selectedCrop == null) return;
        List<String> profileVarieties = SeedlingCropCatalog
                .profileFor(selectedCrop).getVarieties();
        List<String> options = new ArrayList<>(profileVarieties);
        options.add(getString(R.string.seedling_variety_custom));
        int checked = profileVarieties.indexOf(selectedVariety);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.seedling_variety)
                .setSingleChoiceItems(options.toArray(new String[0]), checked,
                        (dialog, which) -> {
                            dialog.dismiss();
                            if (which == profileVarieties.size()) {
                                showCustomVarietyDialog();
                            } else {
                                selectedVariety = profileVarieties.get(which);
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
        input.setHint(R.string.seedling_variety_custom_hint);
        if (!SeedlingCropCatalog.profileFor(selectedCrop)
                .getVarieties().contains(selectedVariety)) {
            input.setText(selectedVariety);
            input.setSelection(input.length());
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.seedling_variety_custom_title)
                .setView(input)
                .setNegativeButton(R.string.settings_cancel, null)
                .setPositiveButton(R.string.settings_save, (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (!value.isBlank()) {
                        selectedVariety = value;
                        varietyValue.setText(value);
                    }
                })
                .show();
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
        renderDates();
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
        if (selectedCrop == null || selectedVariety.isBlank()
                || seedValue <= 0 || trayValue <= 0) {
            Toast.makeText(this, R.string.seedling_required_fields, Toast.LENGTH_LONG).show();
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
}
