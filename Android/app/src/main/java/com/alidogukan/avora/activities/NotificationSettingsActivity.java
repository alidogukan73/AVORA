package com.alidogukan.avora.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.ui.PrimaryBottomNavigation;
import com.alidogukan.avora.viewmodels.GardenSettingsViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

/** Chooses which AVORA event categories are recorded and shown as phone notifications. */
public class NotificationSettingsActivity extends AppCompatActivity {
    private GardenSettingsViewModel viewModel;
    private MaterialButton phonePermissionButton;
    private TextView phonePermission;
    private boolean applyingCloudBackup;

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                viewModel.markPhonePermissionRequested();
                renderPhonePermission();
            });

    @Override
    public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_notification_settings);
        applyWindowInsets();
        viewModel = new ViewModelProvider(this).get(GardenSettingsViewModel.class);
        configureToolbar();
        phonePermission = findViewById(R.id.txtPhoneNotificationPermission);
        phonePermissionButton = findViewById(R.id.btnPhoneNotificationPermission);
        phonePermissionButton.setOnClickListener(view -> requestPhonePermission());
        bind(findViewById(R.id.switchNotificationIrrigation), "irrigation");
        bind(findViewById(R.id.switchNotificationFertilization), "fertilization");
        bind(findViewById(R.id.switchNotificationPlant), "plant");
        bind(findViewById(R.id.switchNotificationWeather), "weather");
        bind(findViewById(R.id.switchNotificationDevice), "device");
        bind(findViewById(R.id.switchNotificationStock), "stock");
        findViewById(R.id.btnOpenNotificationCenter).setOnClickListener(view ->
                startActivity(new Intent(this, NotificationCenterActivity.class)));
        restoreCloudBackup();
        renderPhonePermission();
        PrimaryBottomNavigation.bind(this, PrimaryBottomNavigation.SETTINGS);

        findViewById(R.id.btnOpenReminderSettings)
                .setOnClickListener(view ->
                        startActivity(
                                new Intent(
                                        this,
                                        ReminderSettingsActivity.class
                                )
                        )
                );
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (phonePermission != null) renderPhonePermission();
    }

    private void configureToolbar() {
        ((TextView) findViewById(R.id.txtSettingsToolbarTitle))
                .setText(R.string.notification_settings_title);
        findViewById(R.id.btnSettingsToolbarBack).setOnClickListener(view -> finish());
        findViewById(R.id.btnSettingsToolbarAction).setVisibility(View.GONE);
    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.notificationSettingsRoot),
                (view, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });
    }

    private void bind(MaterialSwitch view, String category) {
        view.setChecked(viewModel.isCategoryEnabled(category));
        view.setOnCheckedChangeListener((button, checked) -> {
            if (applyingCloudBackup) return;
            viewModel.setCategoryEnabled(category, checked);
            syncSettings();
        });
    }

    private void restoreCloudBackup() {
        viewModel.loadNotificationSettings(values -> runOnUiThread(() -> {
            if (!viewModel.applyNotificationBackup(values)) return;
            applyingCloudBackup = true;
            ((MaterialSwitch) findViewById(R.id.switchNotificationIrrigation))
                    .setChecked(viewModel.isCategoryEnabled("irrigation"));
            ((MaterialSwitch) findViewById(R.id.switchNotificationFertilization))
                    .setChecked(viewModel.isCategoryEnabled("fertilization"));
            ((MaterialSwitch) findViewById(R.id.switchNotificationPlant))
                    .setChecked(viewModel.isCategoryEnabled("plant"));
            ((MaterialSwitch) findViewById(R.id.switchNotificationWeather))
                    .setChecked(viewModel.isCategoryEnabled("weather"));
            ((MaterialSwitch) findViewById(R.id.switchNotificationDevice))
                    .setChecked(viewModel.isCategoryEnabled("device"));
            ((MaterialSwitch) findViewById(R.id.switchNotificationStock))
                    .setChecked(viewModel.isCategoryEnabled("stock"));
            applyingCloudBackup = false;
        }));
    }

    private void syncSettings() {
        viewModel.saveCategorySettings()
                .addOnFailureListener(error -> Toast.makeText(this,
                        R.string.notification_settings_save_failed, Toast.LENGTH_SHORT).show());
    }

    private boolean phoneNotificationsAllowed() {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return false;
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void renderPhonePermission() {
        boolean allowed = phoneNotificationsAllowed();
        phonePermission.setText(allowed
                ? R.string.notification_phone_open_message
                : R.string.notification_phone_closed_message);
        phonePermissionButton.setText(allowed
                ? R.string.notification_system_settings
                : R.string.notification_allow);
    }

    private void requestPhonePermission() {
        if (phoneNotificationsAllowed()) {
            openSystemNotificationSettings();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            boolean requested = viewModel.wasPhonePermissionRequested();
            if (requested) openSystemNotificationSettings();
            else notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            openSystemNotificationSettings();
        }
    }

    private void openSystemNotificationSettings() {
        startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
    }
}
