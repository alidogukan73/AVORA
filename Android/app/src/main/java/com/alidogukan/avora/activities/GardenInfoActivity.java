package com.alidogukan.avora.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
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
import com.alidogukan.avora.config.AppInfo;
import com.alidogukan.avora.models.GardenProfile;
import com.alidogukan.avora.ui.PrimaryBottomNavigation;
import com.alidogukan.avora.viewmodels.GardenSettingsViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.DecimalFormat;

/** Manages the garden identity while location remains in its dedicated safe workflow. */
public class GardenInfoActivity extends AppCompatActivity {
    private GardenSettingsViewModel viewModel;
    private TextInputEditText gardenName;
    private MaterialAutoCompleteTextView gardenType;
    private TextInputEditText gardenArea;
    private TextInputEditText gardenNotes;
    private TextView deviceId;
    private TextView locationSummary;
    private TextView status;
    private boolean applyingValues;
    private boolean dirty;

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_garden_info);
        applyWindowInsets();

        viewModel = new ViewModelProvider(this).get(GardenSettingsViewModel.class);
        bindViews();
        configureToolbar();
        configureFields();
        configureActions();
        PrimaryBottomNavigation.bind(this, PrimaryBottomNavigation.SETTINGS);

        GardenProfile local = viewModel.loadLocalProfile();
        applyProfile(local);
        observeCloudProfile();
        observeLocation();
    }

    private void bindViews() {
        gardenName = findViewById(R.id.inputGardenName);
        gardenType = findViewById(R.id.inputGardenType);
        gardenArea = findViewById(R.id.inputGardenArea);
        gardenNotes = findViewById(R.id.inputGardenNotes);
        deviceId = findViewById(R.id.txtGardenDeviceId);
        locationSummary = findViewById(R.id.txtGardenLocationSummary);
        status = findViewById(R.id.txtGardenInfoStatus);
    }

    private void configureToolbar() {
        ((TextView) findViewById(R.id.txtSettingsToolbarTitle))
                .setText(R.string.settings_garden_info_title);
        findViewById(R.id.btnSettingsToolbarBack).setOnClickListener(view -> requestExit());
        findViewById(R.id.btnSettingsToolbarAction).setVisibility(View.GONE);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { requestExit(); }
        });
    }

    private void configureFields() {
        gardenType.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                getResources().getStringArray(R.array.garden_type_options)));
        TextInputLayout areaLayout = findViewById(R.id.layoutGardenArea);
        areaLayout.setHint(getString(R.string.garden_info_area_hint,
                viewModel.areaSymbol()));
        deviceId.setText(AppInfo.DEVICE_ID);

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                markDirty();
            }
            @Override public void afterTextChanged(Editable value) { }
        };
        gardenName.addTextChangedListener(watcher);
        gardenType.addTextChangedListener(watcher);
        gardenArea.addTextChangedListener(watcher);
        gardenNotes.addTextChangedListener(watcher);
    }

    private void configureActions() {
        findViewById(R.id.btnManageGardenLocation).setOnClickListener(view ->
                startActivity(new Intent(this, GardenLocationActivity.class)));
        findViewById(R.id.btnSaveGardenInfo).setOnClickListener(view -> save(false));
    }

    private void observeCloudProfile() {
        viewModel.getCloudProfile().observe(this, cloud -> {
            if (cloud == null || !cloud.hasData() || dirty) return;
            long localUpdatedAt = viewModel.loadLocalProfile().getUpdated_at_epoch();
            if (!com.alidogukan.avora.settings.SettingsSyncPolicy.isCloudValueNewer(
                    cloud.getUpdated_at_epoch(), localUpdatedAt)) {
                return;
            }
            viewModel.acceptCloudProfile(cloud);
            applyProfile(cloud);
            status.setText(R.string.garden_info_cloud_loaded);
        });
    }

    private void observeLocation() {
        viewModel.getWeatherLocation().observe(this, location -> {
            if (location == null || isBlank(location.getCity())) {
                locationSummary.setText(R.string.garden_info_location_missing);
                return;
            }
            String place = isBlank(location.getDistrict())
                    ? location.getCity()
                    : location.getDistrict() + " / " + location.getCity();
            if (location.getLatitude() != null && location.getLongitude() != null) {
                locationSummary.setText(getString(R.string.garden_info_location_gps, place));
            } else {
                locationSummary.setText(getString(R.string.garden_info_location_district, place));
            }
        });
    }

    private void applyProfile(GardenProfile profile) {
        applyingValues = true;
        gardenName.setText(valueOr(profile.getGarden_name(), getString(R.string.garden_info_default_name)));
        gardenType.setText(valueOr(profile.getGarden_type(),
                getResources().getStringArray(R.array.garden_type_options)[0]), false);
        double displayedArea = viewModel.areaFromSquareMeters(profile.getArea_square_meters());
        gardenArea.setText(displayedArea <= 0d ? "" : new DecimalFormat("0.##").format(displayedArea));
        gardenNotes.setText(valueOr(profile.getNotes(), ""));
        applyingValues = false;
        dirty = false;
    }

    private void save(boolean closeAfterSave) {
        String name = text(gardenName);
        String type = gardenType.getText() == null ? "" : gardenType.getText().toString().trim();
        if (name.isEmpty()) {
            gardenName.setError(getString(R.string.garden_info_name_required));
            gardenName.requestFocus();
            return;
        }
        double displayedArea;
        try {
            String raw = text(gardenArea).replace(',', '.');
            displayedArea = raw.isEmpty() ? 0d : Double.parseDouble(raw);
            if (displayedArea < 0d) throw new NumberFormatException();
        } catch (NumberFormatException error) {
            gardenArea.setError(getString(R.string.garden_info_area_invalid));
            gardenArea.requestFocus();
            return;
        }

        GardenProfile profile = new GardenProfile(name, type,
                viewModel.areaToSquareMeters(displayedArea),
                text(gardenNotes), System.currentTimeMillis() / 1000L);
        dirty = false;
        status.setText(R.string.settings_status_saving);
        viewModel.saveGardenProfile(profile)
                .addOnSuccessListener(unused -> {
                    status.setText(R.string.garden_info_saved);
                    Toast.makeText(this, R.string.garden_info_saved, Toast.LENGTH_SHORT).show();
                    if (closeAfterSave) finish();
                })
                .addOnFailureListener(error -> {
                    status.setText(R.string.garden_info_local_only);
                    Toast.makeText(this, R.string.garden_info_local_only, Toast.LENGTH_LONG).show();
                    if (closeAfterSave) finish();
                });
    }

    private void markDirty() {
        if (applyingValues) return;
        dirty = true;
        status.setText(R.string.settings_status_unsaved);
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

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.gardenInfoRoot),
                (view, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });
    }

    private String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String valueOr(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }
}
