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
import com.alidogukan.avora.models.FertilizationProfile;
import com.alidogukan.avora.models.FertilizerProduct;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.ui.PrimaryBottomNavigation;
import com.alidogukan.avora.viewmodels.FertilizationSettingsViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.Collections;
import java.util.List;

/** Global fertilizer guidance, reminder and inventory preferences. */
public class FertilizationSettingsActivity extends AppCompatActivity {
    private FertilizationSettingsViewModel viewModel;

    private MaterialSwitch fertilizationReminders;
    private MaterialSwitch stockWarnings;
    private MaterialSwitch preferOrganicInputs;
    private TextView planSummary;
    private TextView productSummary;
    private TextView stockSummary;
    private TextView status;

    private boolean applyingValues;
    private boolean dirty;
    private boolean savedPreferOrganicInputs;
    private boolean savedFertilizationReminders;
    private boolean savedStockWarnings;
    private List<GardenZone> zones = Collections.emptyList();
    private List<FertilizerProduct> products = Collections.emptyList();

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_fertilization_settings);
        viewModel = new ViewModelProvider(this).get(FertilizationSettingsViewModel.class);
        applyWindowInsets();

        bindViews();
        configureToolbar();
        configureActions();
        PrimaryBottomNavigation.bind(this, PrimaryBottomNavigation.SETTINGS);

        applyStoredSettings();
        restoreSettingsFromCloud();
        observeLiveSummary();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!dirty && viewModel != null) {
            applyStoredSettings();
        }
    }

    private void bindViews() {
        fertilizationReminders = findViewById(R.id.switchFertilizationReminders);
        stockWarnings = findViewById(R.id.switchFertilizerStockWarnings);
        preferOrganicInputs = findViewById(R.id.switchPreferOrganicInputs);
        planSummary = findViewById(R.id.txtFertilizationPlanSummary);
        productSummary = findViewById(R.id.txtFertilizerProductSummary);
        stockSummary = findViewById(R.id.txtFertilizerStockSummary);
        status = findViewById(R.id.txtFertilizationSettingsStatus);
    }

    private void configureToolbar() {
        ((TextView) findViewById(R.id.txtSettingsToolbarTitle))
                .setText(R.string.fertilization_settings_title);
        findViewById(R.id.btnSettingsToolbarBack).setOnClickListener(view -> requestExit());
        findViewById(R.id.btnSettingsToolbarAction).setVisibility(View.GONE);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { requestExit(); }
        });
    }

    private void configureActions() {
        preferOrganicInputs.setOnCheckedChangeListener((button, checked) -> markDirty());
        fertilizationReminders.setOnCheckedChangeListener((button, checked) -> markDirty());
        stockWarnings.setOnCheckedChangeListener((button, checked) -> markDirty());

        findViewById(R.id.btnOpenFertilizationAssistant).setOnClickListener(view ->
                open(FertilizationCalendarActivity.class));
        findViewById(R.id.btnManageFertilizerProducts).setOnClickListener(view ->
                open(FertilizerProductsActivity.class));
        findViewById(R.id.btnOpenFertilizerHistory).setOnClickListener(view ->
                open(FertilizerHistoryActivity.class));
        findViewById(R.id.btnOpenFertilizerNotificationSettings).setOnClickListener(view ->
                open(NotificationSettingsActivity.class));
        findViewById(R.id.btnSaveFertilizationSettings).setOnClickListener(view -> save(false));
        findViewById(R.id.btnResetFertilizationSettings).setOnClickListener(view -> {
            applyingValues = true;
            preferOrganicInputs.setChecked(true);
            fertilizationReminders.setChecked(true);
            stockWarnings.setChecked(true);
            applyingValues = false;
            dirty = hasUnsavedChanges();
            status.setText(R.string.fertilization_settings_defaults_ready);
        });
    }

    private void applyStoredSettings() {
        applyingValues = true;
        savedPreferOrganicInputs = viewModel.preferOrganicInputs();
        savedFertilizationReminders = viewModel.isCategoryEnabled("fertilization");
        savedStockWarnings = viewModel.isCategoryEnabled("stock");
        preferOrganicInputs.setChecked(savedPreferOrganicInputs);
        fertilizationReminders.setChecked(savedFertilizationReminders);
        stockWarnings.setChecked(savedStockWarnings);
        applyingValues = false;
        dirty = false;
        status.setText(R.string.settings_status_ready);
    }

    private void restoreSettingsFromCloud() {
        viewModel.getNotificationBackup().observe(this, values -> {
            if (dirty || !viewModel.applyNotificationBackup(values)) return;
            applyStoredSettings();
            status.setText(R.string.fertilization_settings_cloud_loaded);
        });
        viewModel.getPreferenceBackup().observe(this, values -> {
            if (dirty || !viewModel.applyPreferenceBackup(values)) return;
            applyStoredSettings();
            status.setText(R.string.fertilization_settings_cloud_loaded);
        });
    }

    private void observeLiveSummary() {
        viewModel.getZones().observe(this, values -> {
            zones = values == null ? Collections.emptyList() : values;
            renderSummary();
        });
        viewModel.getProducts().observe(this, values -> {
            products = values == null ? Collections.emptyList() : values;
            renderSummary();
        });
    }

    private void renderSummary() {
        int enabledZones = 0;
        int activePlans = 0;
        int zoneReminders = 0;
        for (GardenZone zone : zones) {
            if (zone == null || !zone.isEnabled()) continue;
            enabledZones++;
            FertilizationProfile profile = zone.getFertilization();
            if (profile != null && profile.isEnabled()) {
                activePlans++;
                if (profile.isReminder_enabled()) zoneReminders++;
            }
        }

        int enabledProducts = 0;
        int stockEntered = 0;
        int lowStock = 0;
        for (FertilizerProduct product : products) {
            if (product == null || !product.isEnabled()) continue;
            enabledProducts++;
            if (product.getStock_unit() != null && !product.getStock_unit().trim().isEmpty()) {
                stockEntered++;
                if (product.getLow_stock_threshold() > 0d
                        && product.getStock_amount() <= product.getLow_stock_threshold()) {
                    lowStock++;
                }
            }
        }

        planSummary.setText(getString(R.string.fertilization_settings_plan_summary,
                activePlans, enabledZones, zoneReminders));
        productSummary.setText(getString(R.string.fertilization_settings_product_summary,
                enabledProducts, stockEntered));
        stockSummary.setText(lowStock == 0
                ? getString(R.string.fertilization_settings_stock_normal)
                : getResources().getQuantityString(
                        R.plurals.fertilization_settings_low_stock_count, lowStock, lowStock));
        stockSummary.setTextColor(getColor(lowStock == 0 ? R.color.success : R.color.warning));
    }

    private void markDirty() {
        if (applyingValues) return;
        dirty = hasUnsavedChanges();
        status.setText(dirty
                ? R.string.settings_status_unsaved
                : R.string.settings_status_ready);
    }

    private boolean hasUnsavedChanges() {
        return preferOrganicInputs.isChecked() != savedPreferOrganicInputs
                || fertilizationReminders.isChecked() != savedFertilizationReminders
                || stockWarnings.isChecked() != savedStockWarnings;
    }

    private void save(boolean closeAfterSave) {
        savedPreferOrganicInputs = preferOrganicInputs.isChecked();
        savedFertilizationReminders = fertilizationReminders.isChecked();
        savedStockWarnings = stockWarnings.isChecked();
        dirty = false;
        status.setText(R.string.settings_status_saving);
        viewModel.save(savedPreferOrganicInputs, savedFertilizationReminders,
                        savedStockWarnings)
                .addOnSuccessListener(unused -> {
                    status.setText(R.string.fertilization_settings_saved);
                    Toast.makeText(this, R.string.fertilization_settings_saved,
                            Toast.LENGTH_SHORT).show();
                    if (closeAfterSave) finish();
                })
                .addOnFailureListener(error -> {
                    status.setText(R.string.fertilization_settings_local_only);
                    Toast.makeText(this, R.string.fertilization_settings_local_only,
                            Toast.LENGTH_LONG).show();
                    if (closeAfterSave) finish();
                });
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
                .setPositiveButton(R.string.settings_save_and_exit,
                        (dialog, which) -> save(true))
                .show();
    }

    private void open(Class<?> target) {
        startActivity(new Intent(this, target));
    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.fertilizationSettingsRoot),
                (view, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });
    }
}
