package com.alidogukan.avora.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.BuildConfig;
import com.alidogukan.avora.R;
import com.alidogukan.avora.config.AppInfo;
import com.alidogukan.avora.models.Health;
import com.alidogukan.avora.ui.PrimaryBottomNavigation;
import com.alidogukan.avora.viewmodels.DeviceHealthViewModel;

/** AVORA product, system and developer information shown in the Settings design language. */
public class AboutActivity extends AppCompatActivity {
    private TextView appVersionBadge;
    private TextView systemAppVersion;
    private TextView deviceId;
    private TextView backendVersion;
    private TextView developerName;
    private TextView developerRole;

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_about_settings);
        applyWindowInsets();
        bindViews();
        configureToolbar();
        renderStaticInfo();
        observeBackendVersion();
        findViewById(R.id.btnOpenGitHub).setOnClickListener(view -> openGitHub());
        PrimaryBottomNavigation.bind(this, PrimaryBottomNavigation.SETTINGS);
    }

    private void bindViews() {
        appVersionBadge = findViewById(R.id.txtAppVersion);
        systemAppVersion = findViewById(R.id.txtSystemAppVersion);
        deviceId = findViewById(R.id.txtDeviceId);
        backendVersion = findViewById(R.id.txtBackendVersion);
        developerName = findViewById(R.id.txtDeveloperName);
        developerRole = findViewById(R.id.txtDeveloperRole);
    }

    private void configureToolbar() {
        ((TextView) findViewById(R.id.txtSettingsToolbarTitle))
                .setText(R.string.settings_about_title);
        findViewById(R.id.btnSettingsToolbarBack).setOnClickListener(view -> finish());
        findViewById(R.id.btnSettingsToolbarAction).setVisibility(View.GONE);
    }

    private void renderStaticInfo() {
        String version = getString(R.string.about_settings_version_format,
                BuildConfig.VERSION_NAME);
        appVersionBadge.setText(version);
        systemAppVersion.setText(version);
        deviceId.setText(AppInfo.DEVICE_ID);
        developerName.setText(AppInfo.DEVELOPER_NAME);
        developerRole.setText(AppInfo.DEVELOPER_ROLE);
    }

    private void observeBackendVersion() {
        DeviceHealthViewModel viewModel = new ViewModelProvider(this)
                .get(DeviceHealthViewModel.class);
        viewModel.getHealth().observe(this, this::renderHealth);
        viewModel.getError().observe(this, error -> {
            if (error != null && !error.isBlank()) {
                backendVersion.setText(R.string.about_settings_backend_unavailable);
            }
        });
    }

    private void renderHealth(Health health) {
        if (health == null) {
            backendVersion.setText(R.string.about_settings_backend_waiting);
            return;
        }
        String firmware = health.getFirmware();
        backendVersion.setText(firmware == null || firmware.isBlank()
                ? getString(R.string.about_settings_backend_unavailable)
                : firmware);
    }

    private void openGitHub() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(AppInfo.GITHUB_URL)));
        } catch (Exception exception) {
            Toast.makeText(this, R.string.about_github_open_error, Toast.LENGTH_LONG).show();
        }
    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.aboutSettingsRoot),
                (view, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });
    }
}