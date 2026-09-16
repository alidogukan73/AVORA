package com.alidogukan.avora.activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alidogukan.avora.R;
import com.alidogukan.avora.adapters.FertilizerProductAdapter;
import com.alidogukan.avora.models.FertilizerProduct;
import com.alidogukan.avora.viewmodels.FertilizerProductsViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import java.text.NumberFormat;
import java.util.List;

public class FertilizerProductsActivity extends EdgeToEdgeActivity {

    private static final String[] FUNCTION_TAG_CODES = {
            "",
            "TRACE_ELEMENTS",
            "ORGANIC_MATTER",
            "HUMIC_FULVIC",
            "SEAWEED",
            "CALCIUM_MAGNESIUM",
            "AMINO_ACIDS",
            "MICROBIAL",
            "CALCIUM",
            "PHOSPHATE",
            "SULFATE"
    };

    private FertilizerProductsViewModel viewModel;
    private final FertilizerProductAdapter adapter =
            new FertilizerProductAdapter();
    private TextView txtEmpty;
    private TextView txtProductStockSummary;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fertilizer_products);
        viewModel = new ViewModelProvider(this).get(FertilizerProductsViewModel.class);

        txtEmpty = findViewById(R.id.txtEmpty);
        txtProductStockSummary = findViewById(
                R.id.txtProductStockSummary
        );
        RecyclerView recycler = findViewById(R.id.recyclerProducts);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);
        recycler.setNestedScrollingEnabled(false);

        findViewById(R.id.btnBack).setOnClickListener(
                view -> finish()
        );
        findViewById(R.id.btnAddProduct).setOnClickListener(
                view -> showProductDialog(null)
        );
        adapter.setOnProductClickListener(this::showProductDialog);

        viewModel.getProducts().observe(
                this,
                this::renderProducts
        );
    }

    private void renderProducts(List<FertilizerProduct> products) {
        adapter.submitList(products);
        txtEmpty.setVisibility(
                products == null || products.isEmpty()
                        ? View.VISIBLE
                        : View.GONE
        );
        if (products == null) {
            txtProductStockSummary.setText(R.string.fertilizer_stock_summary_empty);
            return;
        }
        StringBuilder summary = new StringBuilder();
        for (FertilizerProduct product : products) {
            if (!product.isEnabled()
                    || product.getStock_unit() == null
                    || product.getStock_unit().isBlank()) {
                continue;
            }
            String status = product.getStock_amount() <= 0.0
                    ? getString(R.string.fertilizer_stock_empty_status)
                    : product.getLow_stock_threshold() > 0.0
                    && product.getStock_amount()
                    <= product.getLow_stock_threshold()
                    ? getString(R.string.fertilizer_stock_status_low)
                    : getString(R.string.fertilizer_stock_ok);
            if (summary.length() > 0) {
                summary.append("\n");
            }
            summary.append(product.getName()).append(" · ")
                    .append(formatNumber(product.getStock_amount()))
                    .append(" ").append(product.getStock_unit())
                    .append(" · ").append(status);
        }
        txtProductStockSummary.setText(summary.length() == 0
                ? getString(R.string.fertilizer_stock_summary_empty)
                : summary.toString());
    }

    private void showProductDialog(
            @Nullable FertilizerProduct existing
    ) {
        View content = LayoutInflater.from(this).inflate(
                R.layout.dialog_fertilizer_product,
                null,
                false
        );
        TextInputEditText inputName =
                content.findViewById(R.id.inputName);
        TextInputEditText inputNpk =
                content.findViewById(R.id.inputNpk);
        TextInputEditText inputDose =
                content.findViewById(R.id.inputDose);
        TextInputEditText inputInterval =
                content.findViewById(R.id.inputInterval);
        TextInputEditText inputStockAmount =
                content.findViewById(R.id.inputStockAmount);
        TextInputEditText inputLowStockThreshold =
                content.findViewById(R.id.inputLowStockThreshold);
        MaterialAutoCompleteTextView dropdownForm =
                content.findViewById(R.id.dropdownForm);
        MaterialAutoCompleteTextView dropdownUnit =
                content.findViewById(R.id.dropdownUnit);
        MaterialAutoCompleteTextView dropdownStockUnit =
                content.findViewById(R.id.dropdownStockUnit);
        MaterialAutoCompleteTextView dropdownApplicationType =
                content.findViewById(R.id.dropdownApplicationType);
        TextInputEditText inputFunctionalTags =
                content.findViewById(R.id.inputFunctionalTags);
        MaterialSwitch switchOrganicFarmingEligible =
                content.findViewById(R.id.switchOrganicFarmingEligible);
        TextInputEditText inputRecommendedStages =
                content.findViewById(R.id.inputRecommendedStages);
        boolean[] selectedStages = new boolean[viewModel.stageCodes().length];
        boolean[] selectedFunctionalTags =
                new boolean[FUNCTION_TAG_CODES.length - 1];
        TextView txtAiProductProfile =
                content.findViewById(R.id.txtAiProductProfile);

        String[] forms = {
                getString(R.string.runtime_form_liquid),
                getString(R.string.runtime_form_powder),
                getString(R.string.runtime_form_granular)
        };
        String[] units = {
                getString(R.string.unit_kg_dekar),
                getString(R.string.unit_kg_dekar_ton),
                getString(R.string.unit_kg_dekar_soil),
                getString(R.string.unit_l_dekar),
                getString(R.string.unit_l_dekar_ton),
                getString(R.string.unit_l_dekar_soil),
                getString(R.string.unit_ml_100l),
                getString(R.string.unit_g_100l),
                getString(R.string.unit_ml_liter),
                getString(R.string.unit_g_liter),
                getString(R.string.unit_ml_plant),
                getString(R.string.unit_g_plant)
        };
        dropdownForm.setSimpleItems(forms);
        dropdownUnit.setSimpleItems(units);
        String[] stockUnits = {"kg", "g", "L", "ml"};
        String[] applicationTypes = {
                getString(R.string.fertilizer_type_nutrition),
                getString(R.string.fertilizer_type_organic),
                getString(R.string.fertilizer_type_conditioner),
                getString(R.string.fertilizer_type_biostimulant)
        };
        dropdownStockUnit.setSimpleItems(stockUnits);
        dropdownApplicationType.setSimpleItems(applicationTypes);
        applyExistingStageSelection(existing, selectedStages);
        updateStageSelectionText(inputRecommendedStages, selectedStages);
        inputRecommendedStages.setOnClickListener(view ->
                showStageSelectionDialog(
                        inputRecommendedStages,
                        selectedStages
                )
        );
        applyExistingFunctionalTagSelection(existing, selectedFunctionalTags);
        updateFunctionalTagSelectionText(
                inputFunctionalTags,
                selectedFunctionalTags
        );
        inputFunctionalTags.setOnClickListener(view ->
                showFunctionalTagSelectionDialog(
                        inputFunctionalTags,
                        selectedFunctionalTags
                )
        );

        bindAiProductProfile(txtAiProductProfile, existing);

        if (existing == null) {
            dropdownForm.setText(forms[0], false);
            dropdownUnit.setText(units[0], false);
            inputInterval.setText(
                    NumberFormat.getIntegerInstance().format(14));
            dropdownStockUnit.setText(stockUnits[0], false);
            dropdownApplicationType.setText(applicationTypes[0], false);
            switchOrganicFarmingEligible.setChecked(false);
        } else {
            inputName.setText(existing.getName());
            inputNpk.setText(existing.getNpk());
            inputDose.setText(
                    String.valueOf(existing.getLabel_dosage())
            );
            inputInterval.setText(
                    String.valueOf(
                            existing.getMinimum_interval_days()
                    )
            );
            dropdownForm.setText(
                    "GRANULAR".equals(existing.getForm())
                            ? forms[2]
                            : "POWDER".equals(existing.getForm())
                            ? forms[1]
                            : forms[0],
                    false
            );
            dropdownUnit.setText(existing.getDosage_unit(), false);
            if (existing.getStock_amount() > 0.0) {
                inputStockAmount.setText(
                        formatNumber(existing.getStock_amount())
                );
            }
            if (existing.getLow_stock_threshold() > 0.0) {
                inputLowStockThreshold.setText(
                        formatNumber(
                                existing.getLow_stock_threshold()
                        )
                );
            }
            dropdownStockUnit.setText(
                    existing.getStock_unit() == null
                            || existing.getStock_unit().isBlank()
                            ? ("LIQUID".equals(existing.getForm())
                            ? stockUnits[2] : stockUnits[0])
                            : existing.getStock_unit(),
                    false
            );
            dropdownApplicationType.setText(
                    applicationTypeLabel(
                            existing.getApplication_type()
                    ),
                    false
            );
            switchOrganicFarmingEligible.setChecked(existing.isOrganic_farming_eligible());
        }

        MaterialAlertDialogBuilder dialogBuilder =
                new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null
                        ? R.string.fertilizer_add_product
                        : R.string.fertilizer_edit_product)
                .setView(content)
                .setNegativeButton(R.string.settings_cancel, null)
                .setPositiveButton(R.string.settings_save, null);
        if (existing != null) {
            dialogBuilder.setNeutralButton(
                    R.string.fertilizer_remove,
                    null
            );
        }
        AlertDialog dialog = dialogBuilder.create();

        dialog.setOnShowListener(unused -> {
            sizeProductDialog(dialog);
            if (existing != null) {
                arrangeEditDialogButtons(dialog);
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(view -> {
                            if (selectedStageCodes(selectedStages).isEmpty()) {
                                Toast.makeText(
                                        this,
                                        R.string.fertilizer_stage_required,
                                        Toast.LENGTH_LONG
                                ).show();
                                return;
                            }
                            FertilizerProduct product =
                                    readProduct(
                                            existing,
                                            inputName,
                                            inputNpk,
                                            inputDose,
                                            inputInterval,
                                            dropdownForm,
                                            dropdownUnit,
                                            inputStockAmount,
                                            inputLowStockThreshold,
                                            dropdownStockUnit,
                                            dropdownApplicationType,
                                            selectedStages,
                                            selectedFunctionalTags,
                                            switchOrganicFarmingEligible.isChecked()
                                    );
                            if (product == null) {
                                Toast.makeText(
                                        this,
                                        R.string.fertilizer_required_fields,
                                        Toast.LENGTH_LONG
                                ).show();
                                return;
                            }
                            viewModel.saveProduct(product)
                                    .addOnSuccessListener(result -> {
                                        dialog.dismiss();
                                        Toast.makeText(
                                                this,
                                                R.string.fertilizer_product_saved,
                                                Toast.LENGTH_SHORT
                                        ).show();
                                    })
                                    .addOnFailureListener(error ->
                                            Toast.makeText(
                                                    this,
                                                    R.string.fertilization_save_failed,
                                                    Toast.LENGTH_LONG
                                            ).show()
                                    );
                        });
            if (existing != null) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                        .setOnClickListener(view ->
                                checkAndRemoveProduct(
                                        dialog,
                                        existing
                                )
                        );
            }
        });
        dialog.show();
    }

    private void applyExistingStageSelection(
            @Nullable FertilizerProduct product,
            boolean[] selected
    ) {
        List<String> configured = viewModel.effectiveStages(product);
        String[] stageCodes = viewModel.stageCodes();
        for (int index = 0; index < stageCodes.length; index++) {
            selected[index] = configured.contains(stageCodes[index]);
        }
    }

    private void showStageSelectionDialog(
            TextInputEditText target,
            boolean[] selected
    ) {
        boolean[] draft = selected.clone();
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.fertilizer_stage_dialog_title)
                .setMultiChoiceItems(
                        productStageLabels(),
                        draft,
                        (dialog, which, checked) -> draft[which] = checked
                )
                .setNegativeButton(R.string.settings_cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    System.arraycopy(draft, 0, selected, 0, selected.length);
                    updateStageSelectionText(target, selected);
                })
                .show();
    }

    private void updateStageSelectionText(
            TextInputEditText target,
            boolean[] selected
    ) {
        String[] labels = productStageLabels();
        List<String> values = new java.util.ArrayList<>();
        for (int index = 0; index < selected.length; index++) {
            if (selected[index]) values.add(labels[index]);
        }
        target.setText(values.isEmpty()
                ? getString(R.string.fertilizer_stage_missing)
                : String.join(", ", values));
    }

    private List<String> selectedStageCodes(boolean[] selected) {
        List<String> result = new java.util.ArrayList<>();
        for (int index = 0; index < selected.length; index++) {
            if (selected[index]) result.add(viewModel.stageCodes()[index]);
        }
        return result;
    }

    private String[] productStageLabels() {
        return new String[]{
                getString(R.string.fertilizer_stage_soil_preparation),
                getString(R.string.growth_stage_rooting),
                getString(R.string.growth_stage_vegetative),
                getString(R.string.growth_stage_flowering),
                getString(R.string.growth_stage_fruiting),
                getString(R.string.growth_stage_harvest)
        };
    }
    private String[] functionalTagLabels() {
        return new String[]{
                getString(R.string.fertilizer_function_general),
                getString(R.string.fertilizer_function_trace_elements),
                getString(R.string.fertilizer_function_organic_matter),
                getString(R.string.fertilizer_function_humic_fulvic),
                getString(R.string.fertilizer_function_seaweed),
                getString(R.string.fertilizer_function_calcium_magnesium),
                getString(R.string.fertilizer_function_amino_acids),
                getString(R.string.fertilizer_function_microbial),
                getString(R.string.fertilizer_function_calcium),
                getString(R.string.fertilizer_function_phosphate),
                getString(R.string.fertilizer_function_sulfate)
        };
    }

    private void applyExistingFunctionalTagSelection(
            @Nullable FertilizerProduct product,
            boolean[] selected
    ) {
        List<String> configured = product == null
                || product.getFunctional_tags() == null
                ? java.util.Collections.emptyList()
                : product.getFunctional_tags();
        for (int index = 0; index < selected.length; index++) {
            selected[index] = containsIgnoreCase(
                    configured,
                    FUNCTION_TAG_CODES[index + 1]
            );
        }
    }

    private void showFunctionalTagSelectionDialog(
            TextInputEditText target,
            boolean[] selected
    ) {
        boolean[] draft = selected.clone();
        String[] allLabels = functionalTagLabels();
        String[] selectableLabels = new String[selected.length];
        System.arraycopy(allLabels, 1, selectableLabels, 0, selected.length);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.fertilizer_function_label)
                .setMultiChoiceItems(
                        selectableLabels,
                        draft,
                        (dialog, which, checked) -> draft[which] = checked
                )
                .setNegativeButton(R.string.settings_cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    System.arraycopy(draft, 0, selected, 0, selected.length);
                    updateFunctionalTagSelectionText(target, selected);
                })
                .show();
    }

    private void updateFunctionalTagSelectionText(
            TextInputEditText target,
            boolean[] selected
    ) {
        String[] labels = functionalTagLabels();
        List<String> values = new java.util.ArrayList<>();
        for (int index = 0; index < selected.length; index++) {
            if (selected[index]) values.add(labels[index + 1]);
        }
        target.setText(values.isEmpty()
                ? labels[0]
                : String.join(", ", values));
    }

    private List<String> selectedFunctionalTagCodes(boolean[] selected) {
        List<String> values = new java.util.ArrayList<>();
        for (int index = 0; index < selected.length; index++) {
            if (selected[index]) values.add(FUNCTION_TAG_CODES[index + 1]);
        }
        return values;
    }

    private boolean containsIgnoreCase(List<String> values, String expected) {
        for (String value : values) {
            if (expected.equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    private void bindAiProductProfile(
            TextView target,
            @Nullable FertilizerProduct product
    ) {
        FertilizerProductsViewModel.ProductGuidance profile =
                viewModel.guidanceFor(product);
        target.setText(
                getString(
                        R.string.runtime_four_lines,
                        getString(R.string.runtime_product_suitability,
                                profile.suitability),
                        getString(R.string.runtime_reason_label,
                                profile.reason),
                        getString(R.string.runtime_fruit_stage,
                                profile.fruitStageAdvice),
                        getString(R.string.runtime_safety_label,
                                profile.safetyNote))
        );
    }

    private void arrangeEditDialogButtons(AlertDialog dialog) {
        View save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        View remove = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        View cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (!(save.getParent() instanceof ViewGroup)) {
            return;
        }
        ViewGroup panel = (ViewGroup) save.getParent();
        panel.removeView(save);
        panel.removeView(remove);
        panel.removeView(cancel);
        panel.addView(save);
        panel.addView(remove);
        panel.addView(cancel);
        if (panel instanceof LinearLayout) {
            ((LinearLayout) panel).setGravity(
                    Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL
            );
        }
        if (panel instanceof LinearLayout
                && getResources().getConfiguration().screenWidthDp < 430) {
            LinearLayout buttonPanel = (LinearLayout) panel;
            buttonPanel.setOrientation(LinearLayout.HORIZONTAL);
            setCompactDialogButton(save, getString(R.string.settings_quick_save));
            setCompactDialogButton(remove, getString(R.string.runtime_delete_ellipsis));
            setCompactDialogButton(cancel, getString(R.string.settings_quick_cancel));
        }
    }

    private void setCompactDialogButton(View button, String label) {
        button.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));
        button.setMinimumWidth(0);
        button.setPadding(2, button.getPaddingTop(), 2, button.getPaddingBottom());
        if (button instanceof TextView) {
            TextView text = (TextView) button;
            text.setText(label);
            text.setTextSize(12f);
        }
    }

    @SuppressWarnings("deprecation")
    private void sizeProductDialog(AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        window.setLayout((int) (width * 0.94f), (int) (height * 0.90f));
        // Kept for the API 26-29 devices supported by the app. Newer Android
        // versions still honor this compatibility behavior for dialog windows.
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private void checkAndRemoveProduct(
            AlertDialog editDialog,
            FertilizerProduct product
    ) {
        editDialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                .setEnabled(false);
        viewModel.findActiveZonesUsingProduct(
                product.getProduct_id()
        ).addOnSuccessListener(zoneNames -> {
            editDialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                    .setEnabled(true);
            if (zoneNames != null && !zoneNames.isEmpty()) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.fertilizer_in_use_title)
                        .setMessage(getString(
                                R.string.fertilizer_in_use_message,
                                String.join(", ", zoneNames)
                        ))
                        .setPositiveButton(
                                android.R.string.ok,
                                null
                        )
                        .show();
                return;
            }
            confirmRemoveProduct(editDialog, product);
        }).addOnFailureListener(error -> {
            editDialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                    .setEnabled(true);
            Toast.makeText(
                    this,
                    R.string.fertilization_save_failed,
                    Toast.LENGTH_LONG
            ).show();
        });
    }

    private void confirmRemoveProduct(
            AlertDialog editDialog,
            FertilizerProduct product
    ) {
        boolean systemProduct = product.isVerified();
        new MaterialAlertDialogBuilder(this)
                .setTitle(
                        systemProduct
                                ? R.string.fertilizer_deactivate_title
                                : R.string.fertilizer_delete_title
                )
                .setMessage(getString(
                        systemProduct
                                ? R.string.fertilizer_deactivate_message
                                : R.string.fertilizer_delete_message,
                        product.getName()
                ))
                .setNegativeButton(R.string.settings_cancel, null)
                .setPositiveButton(
                        R.string.fertilizer_remove_confirm,
                        (dialog, which) -> {
                            com.google.android.gms.tasks.Task<Void> task =
                                    viewModel.removeProduct(product);
                            task.addOnSuccessListener(unused -> {
                                editDialog.dismiss();
                                Toast.makeText(
                                        this,
                                        systemProduct
                                                ? R.string
                                                .fertilizer_deactivated
                                                : R.string
                                                .fertilizer_deleted,
                                        Toast.LENGTH_SHORT
                                ).show();
                            }).addOnFailureListener(error ->
                                    Toast.makeText(
                                            this,
                                            R.string
                                                    .fertilization_save_failed,
                                            Toast.LENGTH_LONG
                                    ).show()
                            );
                        }
                )
                .show();
    }

    @Nullable
    private FertilizerProduct readProduct(
            @Nullable FertilizerProduct existing,
            TextInputEditText inputName,
            TextInputEditText inputNpk,
            TextInputEditText inputDose,
            TextInputEditText inputInterval,
            MaterialAutoCompleteTextView dropdownForm,
            MaterialAutoCompleteTextView dropdownUnit,
            TextInputEditText inputStockAmount,
            TextInputEditText inputLowStockThreshold,
            MaterialAutoCompleteTextView dropdownStockUnit,
            MaterialAutoCompleteTextView dropdownApplicationType,
            boolean[] selectedStages,
            boolean[] selectedFunctionalTags,
            boolean organicFarmingEligible
    ) {
        String name = text(inputName);
        String doseText = text(inputDose).replace(',', '.');
        String intervalText = text(inputInterval);
        String unit = dropdownUnit.getText().toString().trim();
        if (name.isBlank() || doseText.isBlank()
                || intervalText.isBlank() || unit.isBlank()) {
            return null;
        }
        try {
            double dose = Double.parseDouble(doseText);
            int interval = Integer.parseInt(intervalText);
            if (!Double.isFinite(dose) || dose <= 0 || interval < 0 || interval > 365) {
                return null;
            }
            FertilizerProduct product = existing == null
                    ? new FertilizerProduct()
                    : existing;
            product.setName(name);
            product.setNpk(text(inputNpk));
            product.setApplication_type(
                    applicationTypeCode(
                            dropdownApplicationType.getText().toString()
                    )
            );
            product.setLabel_dosage(dose);
            product.setMinimum_interval_days(interval);
            product.setDosage_unit(unit);
            product.setForm(
                    getString(R.string.runtime_form_granular).contentEquals(dropdownForm.getText())
                            ? "GRANULAR"
                            : getString(R.string.runtime_form_powder).contentEquals(
                                    dropdownForm.getText()
                            )
                            ? "POWDER"
                            : "LIQUID"
            );
            product.setEnabled(true);
            product.setStock_amount(
                    optionalNumber(inputStockAmount)
            );
            product.setLow_stock_threshold(
                    optionalNumber(inputLowStockThreshold)
            );
            product.setStock_unit(
                    dropdownStockUnit.getText().toString().trim()
            );
            product.setRecommended_stages(
                    selectedStageCodes(selectedStages)
            );
            product.setFunctional_tags(selectedFunctionalTagCodes(selectedFunctionalTags));
            product.setOrganic_farming_eligible(organicFarmingEligible);
            return product;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String text(TextInputEditText input) {
        return input.getText() == null
                ? ""
                : input.getText().toString().trim();
    }

    private double optionalNumber(TextInputEditText input) {
        String value = text(input).replace(',', '.');
        if (value.isBlank()) {
            return 0.0;
        }
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed) || parsed < 0.0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private String formatNumber(double value) {
        return value == Math.rint(value)
                ? String.format(java.util.Locale.getDefault(), "%.0f", value)
                : String.format(java.util.Locale.getDefault(), "%.2f", value);
    }

    private String applicationTypeCode(String label) {
        if (getString(R.string.fertilizer_type_organic).equals(label)) {
            return "ORGANIC";
        }
        if (getString(R.string.fertilizer_type_conditioner).equals(label)) {
            return "CONDITIONER";
        }
        if (getString(R.string.fertilizer_type_biostimulant).equals(label)) {
            return "BIOSTIMULANT";
        }
        return "NUTRITION";
    }

    private String applicationTypeLabel(String type) {
        if ("ORGANIC".equals(type)) {
            return getString(R.string.fertilizer_type_organic);
        }
        if ("CONDITIONER".equals(type)) {
            return getString(R.string.fertilizer_type_conditioner);
        }
        if ("BIOSTIMULANT".equals(type)) {
            return getString(R.string.fertilizer_type_biostimulant);
        }
        return getString(R.string.fertilizer_type_nutrition);
    }
}
