package com.alidogukan.avora.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.ui.PrimaryBottomNavigation;
import com.alidogukan.avora.viewmodels.SensorPointsViewModel;

import java.util.ArrayList;
import java.util.List;

/** Groups sensor monitoring and calibration in one settings destination. */
public class SensorSettingsActivity extends AppCompatActivity {
    private static final long CONNECTED_MAX_AGE_SECONDS = 90L;
    private static final long STATUS_REFRESH_MILLIS = 5_000L;

    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private final List<GardenZone> sensorPoints = new ArrayList<>();
    private TextView sensorPointsSummary;

    private final Runnable statusUpdater = new Runnable() {
        @Override
        public void run() {
            renderSensorPointsSummary();
            statusHandler.postDelayed(this, STATUS_REFRESH_MILLIS);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sensor_settings);
        applyWindowInsets();

        ((TextView) findViewById(R.id.txtSettingsToolbarTitle))
                .setText(R.string.settings_sensor_settings_title);
        findViewById(R.id.btnSettingsToolbarBack).setOnClickListener(view -> finish());
        findViewById(R.id.btnSettingsToolbarAction).setVisibility(View.GONE);

        sensorPointsSummary = findViewById(R.id.txtSensorSettingsPointsSummary);
        findViewById(R.id.rowSensorSettingsPoints).setOnClickListener(
                view -> open(SensorPointsActivity.class)
        );
        findViewById(R.id.rowSensorSettingsCalibration).setOnClickListener(
                view -> open(SensorCalibrationWizardActivity.class)
        );

        SensorPointsViewModel viewModel = new ViewModelProvider(this)
                .get(SensorPointsViewModel.class);
        viewModel.getSensorPoints().observe(this, zones -> {
            sensorPoints.clear();
            if (zones != null) {
                sensorPoints.addAll(zones);
            }
            renderSensorPointsSummary();
        });

        PrimaryBottomNavigation.bind(this, PrimaryBottomNavigation.SETTINGS);
    }

    @Override
    protected void onStart() {
        super.onStart();
        statusHandler.removeCallbacks(statusUpdater);
        statusHandler.post(statusUpdater);
    }

    @Override
    protected void onStop() {
        statusHandler.removeCallbacks(statusUpdater);
        super.onStop();
    }

    private void renderSensorPointsSummary() {
        if (sensorPointsSummary == null) {
            return;
        }

        int enabled = 0;
        int connected = 0;
        long now = System.currentTimeMillis() / 1000L;
        for (GardenZone zone : sensorPoints) {
            if (zone == null || !zone.isSensor_enabled()) {
                continue;
            }
            enabled++;
            long updatedAt = zone.getUpdated_at_epoch();
            long age = updatedAt > 0L
                    ? Math.max(0L, now - updatedAt)
                    : Long.MAX_VALUE;
            if (age <= CONNECTED_MAX_AGE_SECONDS) {
                connected++;
            }
        }

        if (enabled == 0) {
            sensorPointsSummary.setText(
                    R.string.sensor_points_health_waiting
            );
            return;
        }

        sensorPointsSummary.setText(getString(
                R.string.sensor_points_health_summary_format,
                connected,
                enabled
        ));
    }

    private void open(Class<?> target) {
        startActivity(new Intent(this, target));
    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.sensorSettingsRoot),
                (view, insets) -> {
                    Insets bars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
                    );
                    view.setPadding(
                            bars.left,
                            bars.top,
                            bars.right,
                            bars.bottom
                    );
                    return insets;
                }
        );
    }
}
