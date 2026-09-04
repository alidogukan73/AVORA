package com.alidogukan.avora.activities;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.ui.PrimaryBottomNavigation;
import com.alidogukan.avora.viewmodels.DataSyncViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Verifies the real Firebase session and refreshes AVORA's essential device summaries. */
public class DataSyncActivity extends AppCompatActivity {
    private static final long CONNECTION_TIMEOUT_MS = 12_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView headline;
    private TextView connectionValue;
    private TextView lastSuccessValue;
    private TextView lastDeviceDataValue;
    private TextView verifiedScopeValue;
    private TextView operationStatus;
    private MaterialSwitch automaticSwitch;
    private MaterialButton syncButton;
    private LinearProgressIndicator progress;
    private DataSyncViewModel viewModel;
    private boolean firebaseConnected;
    private boolean manualSyncPending;
    private boolean remoteReadInFlight;
    private boolean suppressSwitchCallback;

    private final Runnable connectionTimeout = () -> {
        if (manualSyncPending) {
            manualSyncPending = false;
            remoteReadInFlight = false;
            setSyncing(false);
            showOperation(R.string.data_sync_offline_error, R.color.warning);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_data_sync);
        viewModel = new ViewModelProvider(this).get(DataSyncViewModel.class);
        applyWindowInsets();
        bindViews();
        configureToolbar();
        configureAutomaticSync();
        configureActions();
        PrimaryBottomNavigation.bind(this, PrimaryBottomNavigation.SETTINGS);
        renderStoredState();
        observeConnection();
        if (automaticSwitch.isChecked()) {
            startManualSync();
        }
    }

    private void bindViews() {
        headline = findViewById(R.id.txtDataSyncConnectionHeadline);
        automaticSwitch = findViewById(R.id.switchDataSyncAutomatic);
        syncButton = findViewById(R.id.btnDataSyncNow);
        progress = findViewById(R.id.progressDataSync);
        operationStatus = findViewById(R.id.txtDataSyncOperationStatus);

        LinearLayout values = findViewById(R.id.layoutDataSyncValues);
        connectionValue = addValueRow(values, R.string.data_sync_connection_label, false);
        lastSuccessValue = addValueRow(values, R.string.data_sync_last_success_label, true);
        lastDeviceDataValue = addValueRow(values, R.string.data_sync_device_data_label, true);
        verifiedScopeValue = addValueRow(values, R.string.data_sync_scope_label, true);
    }

    private TextView addValueRow(LinearLayout container, int labelRes, boolean dividerAbove) {
        if (dividerAbove) {
            View divider = new View(this);
            divider.setBackgroundColor(ContextCompat.getColor(this, R.color.divider));
            container.addView(divider, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dividerHeightPx()));
        }
        View row = LayoutInflater.from(this)
                .inflate(R.layout.item_device_info_value, container, false);
        ((TextView) row.findViewById(R.id.txtDeviceInfoRowLabel)).setText(labelRes);
        TextView value = row.findViewById(R.id.txtDeviceInfoRowValue);
        container.addView(row);
        return value;
    }

    private void configureToolbar() {
        ((TextView) findViewById(R.id.txtSettingsToolbarTitle))
                .setText(R.string.data_sync_title);
        findViewById(R.id.btnSettingsToolbarBack).setOnClickListener(view -> finish());
        findViewById(R.id.btnSettingsToolbarAction).setVisibility(View.GONE);
    }

    private void configureAutomaticSync() {
        boolean enabled = viewModel.automaticSyncEnabled();
        suppressSwitchCallback = true;
        automaticSwitch.setChecked(enabled);
        suppressSwitchCallback = false;
        viewModel.setAutomaticSyncEnabled(enabled);
        automaticSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (suppressSwitchCallback) {
                return;
            }
            viewModel.setAutomaticSyncEnabled(checked);
            Toast.makeText(this, checked
                    ? R.string.data_sync_auto_enabled
                    : R.string.data_sync_auto_disabled, Toast.LENGTH_LONG).show();
            if (checked) {
                startManualSync();
            }
        });
    }

    private void configureActions() {
        syncButton.setOnClickListener(view -> startManualSync());
    }

    private void observeConnection() {
        viewModel.getConnectionError().observe(this, message -> {
            if (message == null) return;
            firebaseConnected = false;
            renderConnection();
            if (manualSyncPending) {
                manualSyncPending = false;
                handler.removeCallbacks(connectionTimeout);
                setSyncing(false);
                showOperation(getString(R.string.data_sync_read_error,
                        safeMessage(message)), R.color.warning);
            }
        });
        viewModel.getConnected().observe(this, connected -> {
            firebaseConnected = Boolean.TRUE.equals(connected);
            renderConnection();
            if (firebaseConnected && manualSyncPending) {
                performRemoteRead();
            }
        });
    }

    private void startManualSync() {
        if (manualSyncPending) {
            return;
        }
        manualSyncPending = true;
        setSyncing(true);
        showOperation(R.string.data_sync_syncing, R.color.textSecondary);
        viewModel.goOnline();
        handler.removeCallbacks(connectionTimeout);
        handler.postDelayed(connectionTimeout, CONNECTION_TIMEOUT_MS);
        if (firebaseConnected) {
            performRemoteRead();
        }
    }

    private void performRemoteRead() {
        if (remoteReadInFlight) {
            return;
        }
        remoteReadInFlight = true;
        viewModel.readSummary().addOnSuccessListener(result -> {
            if (!manualSyncPending) {
                return;
            }
            manualSyncPending = false;
            remoteReadInFlight = false;
            handler.removeCallbacks(connectionTimeout);
            if (!firebaseConnected) {
                setSyncing(false);
                showOperation(R.string.data_sync_offline_error, R.color.warning);
                return;
            }

            if (result.empty) {
                setSyncing(false);
                showOperation(R.string.data_sync_empty_error, R.color.warning);
                return;
            }

            int zoneCount = result.zoneCount;
            int scopeText = result.hasStatus && result.hasHealth && result.hasWeather
                    ? R.string.data_sync_scope_status_health_weather
                    : result.hasStatus && result.hasHealth
                    ? R.string.data_sync_scope_status_health
                    : R.string.data_sync_scope_partial;
            String scope = getString(R.string.data_sync_scope_value,
                    zoneCount, getString(scopeText));
            viewModel.rememberSuccess(result.lastDeviceEpoch, scope);
            renderStoredState();
            setSyncing(false);
            showOperation(getString(R.string.data_sync_success, zoneCount), R.color.online);
        }).addOnFailureListener(error -> {
            if (!manualSyncPending) {
                return;
            }
            manualSyncPending = false;
            remoteReadInFlight = false;
            handler.removeCallbacks(connectionTimeout);
            setSyncing(false);
            showOperation(getString(R.string.data_sync_read_error,
                    safeMessage(error.getMessage())), R.color.warning);
        });
    }

    private void renderStoredState() {
        DataSyncViewModel.StoredState state = viewModel.storedState();
        long lastSuccess = state.lastSuccess;
        long lastDeviceData = state.lastDeviceData;
        String scope = state.scope;
        lastSuccessValue.setText(lastSuccess > 0L
                ? formatDateTime(lastSuccess)
                : getString(R.string.data_sync_never));
        lastDeviceDataValue.setText(lastDeviceData > 0L
                ? formatDateTime(lastDeviceData)
                : getString(R.string.data_sync_no_device_data));
        verifiedScopeValue.setText(scope == null || scope.trim().isEmpty()
                ? getString(R.string.data_sync_scope_waiting)
                : scope);
    }

    private void renderConnection() {
        headline.setText(firebaseConnected
                ? R.string.data_sync_intro_connected
                : R.string.data_sync_intro_offline);
        headline.setTextColor(ContextCompat.getColor(this,
                firebaseConnected ? R.color.online : R.color.warning));
        connectionValue.setText(firebaseConnected
                ? R.string.data_sync_connected
                : R.string.data_sync_disconnected);
        connectionValue.setTextColor(ContextCompat.getColor(this,
                firebaseConnected ? R.color.online : R.color.warning));
    }

    private String formatDateTime(long epochMillis) {
        return new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.forLanguageTag("tr-TR"))
                .format(new Date(epochMillis));
    }

    private void setSyncing(boolean syncing) {
        syncButton.setEnabled(!syncing);
        progress.setVisibility(syncing ? View.VISIBLE : View.GONE);
    }

    private void showOperation(int messageRes, int colorRes) {
        showOperation(getString(messageRes), colorRes);
    }

    private void showOperation(String message, int colorRes) {
        operationStatus.setVisibility(View.VISIBLE);
        operationStatus.setText(message);
        operationStatus.setTextColor(ContextCompat.getColor(this, colorRes));
    }

    private String safeMessage(@Nullable String message) {
        return message == null || message.trim().isEmpty()
                ? getString(R.string.data_sync_disconnected)
                : message.trim();
    }

    private int dividerHeightPx() {
        return Math.round(getResources().getDisplayMetrics().density);
    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.dataSyncRoot),
                (view, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(connectionTimeout);
        super.onDestroy();
    }
}
