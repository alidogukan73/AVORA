package com.alidogukan.avora.activities;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.models.Health;
import com.alidogukan.avora.models.Status;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.SeedlingTelemetry;
import com.alidogukan.avora.viewmodels.DeviceHealthViewModel;
import com.alidogukan.avora.viewmodels.MainViewModel;
import com.alidogukan.avora.viewmodels.SeedlingViewModel;
import com.alidogukan.avora.ui.PrimaryBottomNavigation;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.List;
import java.util.Collections;

public class DeviceHealthActivity extends AppCompatActivity {

    private DeviceHealthViewModel viewModel;
    private com.alidogukan.avora.viewmodels.DisplayUnitsViewModel displayUnits;

    private MaterialButton btnBack;
    private MaterialButton btnRestartDevice;

    private MaterialCardView cardHealthSummary;
    private MaterialCardView cardOverallHealth;
    private MaterialCardView cardOverallHealthBadge;
    private MaterialCardView cardWifiStatus;
    private MaterialCardView cardThrottling;
    private MaterialCardView cardThrottlingBadge;
    private MaterialCardView cardEsp32SensorHealth;
    private MaterialCardView cardEsp32SensorBadge;
    private MaterialCardView cardNodeMcuHealth;
    private MaterialCardView cardNodeMcuBadge;

    private TextView txtHealthSummaryIcon;
    private TextView txtOverallHealth;
    private TextView txtLastHealthUpdate;
    private TextView txtOverallHealthBadge;
    private TextView txtEsp32SensorBadge;
    private TextView txtEsp32SensorStatus;
    private TextView txtEsp32SensorDetail;
    private TextView txtEsp32SensorLastSeen;
    private TextView txtNodeMcuBadge;
    private TextView txtNodeMcuStatus;
    private TextView txtNodeMcuDetail;
    private TextView txtNodeMcuLastSeen;
    private TextView txtCpuTemperature;
    private TextView txtCpuUsage;
    private ProgressBar progressCpu;

    private TextView txtRamUsage;
    private ProgressBar progressRam;
    private TextView txtRamDetail;

    private TextView txtDiskUsage;
    private ProgressBar progressDisk;
    private TextView txtDiskDetail;

    private TextView txtWifiSignal;
    private TextView txtWifiQuality;
    private TextView txtWifiStatusIcon;
    private TextView txtIpAddress;

    private TextView txtThrottlingDescription;
    private TextView txtThrottlingStatus;

    private TextView txtFirmware;
    private TextView txtUptime;

    private TextView txtCurrentPowerEvents;
    private TextView txtHistoricalPowerEvents;
    private TextView txtThrottlingRaw;
    private TextView txtDiagnosticsSummary;
    private LinearLayout layoutDiagnostics;
    private Status latestStatus;
    private Health latestHealth;
    private SeedlingTelemetry latestSeedlingTelemetry;
    private List<GardenZone> latestZones =
            Collections.emptyList();


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);

        setContentView(R.layout.activity_device_health);
        displayUnits = new ViewModelProvider(this)
                .get(com.alidogukan.avora.viewmodels.DisplayUnitsViewModel.class);

        applyWindowInsets();
        initializeViews();
        initializeViewModel();
        observeViewModel();
        initializeActions();
        PrimaryBottomNavigation.bind(this, PrimaryBottomNavigation.DEVICE_HEALTH);
    }

    private void applyWindowInsets() {

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.healthRoot),
                (view, insets) -> {

                    Insets systemBars =
                            insets.getInsets(
                                    WindowInsetsCompat.Type.systemBars()
                                            | WindowInsetsCompat.Type.displayCutout()
                            );

                    view.setPadding(
                            systemBars.left,
                            systemBars.top,
                            systemBars.right,
                            systemBars.bottom
                    );

                    return insets;
                }
        );
    }

    private void initializeViews() {
        txtCurrentPowerEvents =
                findViewById(R.id.txtCurrentPowerEvents);

        txtHistoricalPowerEvents =
                findViewById(R.id.txtHistoricalPowerEvents);

        txtThrottlingRaw =
                findViewById(R.id.txtThrottlingRaw);

        txtDiagnosticsSummary =
                findViewById(R.id.txtDiagnosticsSummary);

        layoutDiagnostics =
                findViewById(R.id.layoutDiagnostics);

        btnBack = findViewById(R.id.btnBack);

        btnRestartDevice =
                findViewById(
                        R.id.btnRestartDevice
                );

        cardHealthSummary =
                findViewById(R.id.cardHealthSummary);

        cardOverallHealth =
                findViewById(R.id.cardOverallHealth);

        cardOverallHealthBadge =
                findViewById(R.id.cardOverallHealthBadge);

        cardWifiStatus =
                findViewById(R.id.cardWifiStatus);

        cardThrottling =
                findViewById(R.id.cardThrottling);

        cardThrottlingBadge =
                findViewById(R.id.cardThrottlingBadge);
        cardEsp32SensorHealth =
                findViewById(R.id.cardEsp32SensorHealth);
        cardEsp32SensorBadge =
                findViewById(R.id.cardEsp32SensorBadge);
        cardNodeMcuHealth =
                findViewById(R.id.cardNodeMcuHealth);
        cardNodeMcuBadge =
                findViewById(R.id.cardNodeMcuBadge);

        txtHealthSummaryIcon =
                findViewById(R.id.txtHealthSummaryIcon);

        txtOverallHealth =
                findViewById(R.id.txtOverallHealth);

        txtLastHealthUpdate =
                findViewById(R.id.txtLastHealthUpdate);

        txtOverallHealthBadge =
                findViewById(R.id.txtOverallHealthBadge);
        txtEsp32SensorBadge =
                findViewById(R.id.txtEsp32SensorBadge);
        txtEsp32SensorStatus =
                findViewById(R.id.txtEsp32SensorStatus);
        txtEsp32SensorDetail =
                findViewById(R.id.txtEsp32SensorDetail);
        txtEsp32SensorLastSeen =
                findViewById(R.id.txtEsp32SensorLastSeen);
        txtNodeMcuBadge =
                findViewById(R.id.txtNodeMcuBadge);
        txtNodeMcuStatus =
                findViewById(R.id.txtNodeMcuStatus);
        txtNodeMcuDetail =
                findViewById(R.id.txtNodeMcuDetail);
        txtNodeMcuLastSeen =
                findViewById(R.id.txtNodeMcuLastSeen);
        txtCpuTemperature =
                findViewById(R.id.txtCpuTemperature);

        txtCpuUsage =
                findViewById(R.id.txtCpuUsage);

        progressCpu =
                findViewById(R.id.progressCpu);

        txtRamUsage =
                findViewById(R.id.txtRamUsage);

        progressRam =
                findViewById(R.id.progressRam);

        txtRamDetail =
                findViewById(R.id.txtRamDetail);

        txtDiskUsage =
                findViewById(R.id.txtDiskUsage);

        progressDisk =
                findViewById(R.id.progressDisk);

        txtDiskDetail =
                findViewById(R.id.txtDiskDetail);

        txtWifiSignal =
                findViewById(R.id.txtWifiSignal);

        txtWifiQuality =
                findViewById(R.id.txtWifiQuality);

        txtWifiStatusIcon =
                findViewById(R.id.txtWifiStatusIcon);

        txtIpAddress =
                findViewById(R.id.txtIpAddress);

        txtThrottlingDescription =
                findViewById(R.id.txtThrottlingDescription);

        txtThrottlingStatus =
                findViewById(R.id.txtThrottlingStatus);

        txtFirmware =
                findViewById(R.id.txtFirmware);

        txtUptime =
                findViewById(R.id.txtUptime);

    }

    private void initializeViewModel() {

        viewModel = new ViewModelProvider(this)
                .get(DeviceHealthViewModel.class);
    }

    private void observeViewModel() {

        viewModel.getHealth().observe(
                this,
                this::renderHealth
        );

        viewModel.getError().observe(
                this,
                message -> {

                    if (
                            message == null
                                    || message.isBlank()
                    ) {
                        return;
                    }

                    Toast.makeText(
                            this,
                            message,
                            Toast.LENGTH_LONG
                    ).show();
                }
        );

        MainViewModel mainViewModel =
                new ViewModelProvider(this)
                        .get(MainViewModel.class);

        mainViewModel.getStatus().observe(
                this,
                status -> {
                    latestStatus = status;
                    renderDiagnostics();
                }
        );

        mainViewModel.getGardenZones().observe(
                this,
                zones -> {
                    latestZones = zones == null
                            ? Collections.emptyList()
                            : zones;
                    renderDiagnostics();
                }
        );

        SeedlingViewModel seedlingViewModel =
                new ViewModelProvider(this)
                        .get(SeedlingViewModel.class);
        seedlingViewModel.getNode("seedling-001").observe(
                this,
                nodeState -> {
                    latestSeedlingTelemetry = nodeState == null
                            ? null
                            : nodeState.getLatest();
                    renderNodeMcuHealth();
                }
        );
    }

    private void initializeActions() {

        btnBack.setOnClickListener(
                view -> finish()
        );

        btnRestartDevice.setOnClickListener(
                view -> showRestartConfirmationDialog()
        );

    }
    private void showRestartConfirmationDialog() {

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                this
        )
                .setTitle(R.string.runtime_restart_title)
                .setMessage(R.string.runtime_restart_message)
                .setNegativeButton(
                        R.string.settings_quick_cancel,
                        (dialog, which) -> dialog.dismiss()
                )
                .setPositiveButton(
                        R.string.runtime_restart_action,
                        (dialog, which) -> {

                            viewModel.restartDevice();

                            Toast.makeText(
                                    this,
                                    getString(R.string.runtime_restart_sent),
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                )
                .show();
    }
    private void renderHealth(Health health) {

        if (health == null) {
            return;
        }

        latestHealth = health;

        renderCpu(health);
        renderMemory(health);
        renderDisk(health);
        renderNetwork(health);
        renderThrottling(health);
        renderSystemInfo(health);
        renderOverallHealth(health);
        renderPowerDetails(health);
        renderDiagnostics();
    }

    private void renderDiagnostics() {
        if (
                layoutDiagnostics == null
                        || txtDiagnosticsSummary == null
        ) {
            return;
        }

        layoutDiagnostics.removeAllViews();
        int normalCount = 0;
        final int totalChecks = 7;
        long nowEpoch = System.currentTimeMillis() / 1000L;

        boolean piOnline =
                latestStatus != null
                        && latestStatus.isOnline()
                        && latestStatus.getLastSeenEpoch() > 0L
                        && nowEpoch
                        - latestStatus.getLastSeenEpoch() <= 30L;
        addDiagnosticRow(
                piOnline,
                piOnline
                        ? R.string.diagnostics_pi_ok
                        : R.string.diagnostics_pi_error
        );
        if (piOnline) normalCount++;

        boolean sensorFresh = false;
        for (GardenZone zone : latestZones) {
            if (
                    zone.getUpdated_at_epoch() > 0L
                            && nowEpoch
                            - zone.getUpdated_at_epoch() <= 90L
            ) {
                sensorFresh = true;
                break;
            }
        }
        addDiagnosticRow(
                sensorFresh,
                sensorFresh
                        ? R.string.diagnostics_sensor_ok
                        : R.string.diagnostics_sensor_error
        );
        if (sensorFresh) normalCount++;

        boolean relaySafe =
                latestStatus != null
                        && !latestStatus.isRelay();
        addDiagnosticRow(
                relaySafe,
                relaySafe
                        ? R.string.diagnostics_relay_ok
                        : R.string.diagnostics_relay_active
        );
        if (relaySafe) normalCount++;

        boolean physicalValveReady = false;
        for (GardenZone zone : latestZones) {
            if ("PHYSICAL".equalsIgnoreCase(
                    zone.getValve_mode()
            )) {
                physicalValveReady = true;
                break;
            }
        }
        addDiagnosticRow(
                true,
                physicalValveReady
                        ? R.string.diagnostics_valve_physical_ready
                        : R.string.diagnostics_valve_simulation
        );
        normalCount++;

        String lastError = latestStatus == null
                ? ""
                : latestStatus.getLastError();
        boolean errorClear =
                lastError == null || lastError.trim().isEmpty();
        addDiagnosticRow(
                errorClear,
                errorClear
                        ? getString(
                                R.string.diagnostics_error_clear
                        )
                        : getString(
                                R.string.diagnostics_error_active,
                                lastError.trim()
                        )
        );
        if (errorClear) normalCount++;

        boolean adsStatusFresh = latestHealth != null
                && latestHealth.getAds1115StatusUpdatedAtEpoch() > 0L
                && (latestHealth.getAds1115StatusUpdatedAtEpoch() > nowEpoch
                || nowEpoch - latestHealth.getAds1115StatusUpdatedAtEpoch() <= 90L);
        boolean esp32Online = adsStatusFresh
                && latestHealth.isEsp32NodeOnline();
        boolean primaryAds = esp32Online
                && latestHealth.isAds1115PrimaryAvailable();
        boolean secondaryAds = esp32Online
                && latestHealth.isAds1115SecondaryAvailable();
        boolean esp32AdsHealthy = primaryAds && secondaryAds;
        String esp32Diagnostic;
        if (!adsStatusFresh) {
            esp32Diagnostic = getString(
                    R.string.diagnostics_esp32_status_stale
            );
        } else if (!esp32Online) {
            esp32Diagnostic = getString(
                    R.string.diagnostics_esp32_offline
            );
        } else if (esp32AdsHealthy) {
            esp32Diagnostic = getString(
                    R.string.diagnostics_esp32_ads_ok
            );
        } else if (primaryAds || secondaryAds) {
            esp32Diagnostic = getString(
                    R.string.diagnostics_esp32_ads_partial,
                    primaryAds ? "0x49" : "0x48"
            );
        } else {
            esp32Diagnostic = getString(
                    R.string.diagnostics_esp32_ads_none
            );
        }
        addDiagnosticRow(esp32AdsHealthy, esp32Diagnostic);
        if (esp32AdsHealthy) normalCount++;

        boolean nodeMcuFresh = latestSeedlingTelemetry != null
                && latestSeedlingTelemetry.isFresh(nowEpoch, 90L);
        boolean nodeMcuHealthy = nodeMcuFresh
                && latestSeedlingTelemetry.isSoil_moisture_available();
        String nodeMcuDiagnostic;
        if (latestSeedlingTelemetry == null
                || latestSeedlingTelemetry.getReceived_at_epoch() <= 0L) {
            nodeMcuDiagnostic = getString(
                    R.string.diagnostics_nodemcu_no_data
            );
        } else if (!nodeMcuFresh) {
            nodeMcuDiagnostic = getString(
                    R.string.diagnostics_nodemcu_stale
            );
        } else if (!latestSeedlingTelemetry.isSoil_moisture_available()) {
            nodeMcuDiagnostic = getString(
                    R.string.diagnostics_nodemcu_ads_missing
            );
        } else {
            nodeMcuDiagnostic = getString(
                    R.string.diagnostics_nodemcu_ok
            );
        }
        addDiagnosticRow(nodeMcuHealthy, nodeMcuDiagnostic);
        if (nodeMcuHealthy) normalCount++;

        txtDiagnosticsSummary.setText(
                getString(
                        R.string.diagnostics_summary,
                        normalCount,
                        totalChecks
                )
        );
        txtDiagnosticsSummary.setTextColor(
                color(
                        normalCount == totalChecks
                                ? R.color.online
                                : R.color.warning
                )
        );

        renderEsp32SensorHealth(nowEpoch);
    }

    /**
     * ESP32 is independent of the Raspberry Pi. A missing wireless
     * measurement therefore belongs to this card only; it must never turn
     * the Pi diagnostic into a false connection error.
     */
    private void renderEsp32SensorHealth(long nowEpoch) {
        if (cardEsp32SensorHealth == null) {
            return;
        }

        int configured = 0;
        int connected = 0;
        GardenZone newest = null;
        long newestEpoch = 0L;

        for (GardenZone zone : latestZones) {
            if (!zone.isSensor_enabled()
                    || zone.getSensor_id() == null
                    || zone.getSensor_id().trim().isEmpty()) {
                continue;
            }
            configured++;
            long updatedAt = zone.getUpdated_at_epoch();
            if (updatedAt > newestEpoch) {
                newestEpoch = updatedAt;
                newest = zone;
            }
            if (updatedAt > 0L && nowEpoch - updatedAt <= 90L) {
                connected++;
            }
        }

        if (configured == 0) {
            setEsp32SensorCard(
                    getString(R.string.runtime_monitoring_off_badge),
                    getString(R.string.runtime_monitoring_off),
                    getString(R.string.runtime_monitoring_no_sensor),
                    "",
                    R.color.textSecondary,
                    R.color.surfaceSoft
            );
            return;
        }

        String lastSeen = newestEpoch == 0L
                ? getString(R.string.runtime_no_esp32_data)
                : getString(R.string.runtime_last_esp32_data,
                        formatSensorAge(Math.max(0L, nowEpoch - newestEpoch)),
                        newest.getRssi() != 0
                                ? getString(R.string.runtime_wifi_suffix, newest.getRssi()) : "");

        if (renderAds1115ModuleFault(nowEpoch)) {
            return;
        }

        if (connected == 0) {
            setEsp32SensorCard(
                    getString(R.string.runtime_no_connection_badge),
                    getString(R.string.runtime_live_sensor_count, 0, configured),
                    getString(R.string.runtime_esp32_waiting),
                    lastSeen,
                    R.color.offline,
                    R.color.offlineBackground
            );
        } else if (connected < configured) {
            setEsp32SensorCard(
                    getString(R.string.runtime_partial_connection_badge),
                    getString(R.string.runtime_live_sensor_count, connected, configured),
                    getString(R.string.runtime_esp32_partial),
                    lastSeen,
                    R.color.warning,
                    R.color.warningBackground
            );
        } else {
            setEsp32SensorCard(
                    getString(R.string.runtime_connected_badge),
                    getString(R.string.runtime_live_sensor_count, connected, configured),
                    getString(R.string.runtime_esp32_healthy),
                    lastSeen,
                    R.color.online,
                    R.color.onlineBackground
            );
        }
    }

    private boolean renderAds1115ModuleFault(long nowEpoch) {
        if (latestHealth == null) {
            return false;
        }
        long updatedAt = latestHealth.getAds1115StatusUpdatedAtEpoch();
        if (updatedAt <= 0L || nowEpoch - updatedAt > 90L) {
            return false;
        }

        String lastSeen = getString(
                R.string.health_ads_status_last_seen,
                formatSensorAge(Math.max(0L, nowEpoch - updatedAt))
        );
        if (!latestHealth.isEsp32NodeOnline()) {
            setEsp32SensorCard(
                    getString(R.string.runtime_no_connection_badge),
                    getString(R.string.health_ads_count, 0),
                    getString(R.string.health_ads_node_offline),
                    lastSeen,
                    R.color.offline,
                    R.color.offlineBackground
            );
            return true;
        }

        boolean primary = latestHealth.isAds1115PrimaryAvailable();
        boolean secondary = latestHealth.isAds1115SecondaryAvailable();
        if (primary && secondary) {
            return false;
        }

        int working = (primary ? 1 : 0) + (secondary ? 1 : 0);
        int detailResource;
        if (!primary && secondary) {
            detailResource = R.string.health_ads_primary_fault;
        } else if (primary) {
            detailResource = R.string.health_ads_secondary_fault;
        } else {
            detailResource = R.string.health_ads_both_fault;
        }
        setEsp32SensorCard(
                getString(R.string.health_ads_fault_badge),
                getString(R.string.health_ads_count, working),
                getString(detailResource),
                lastSeen,
                working > 0 ? R.color.warning : R.color.offline,
                working > 0 ? R.color.warningBackground : R.color.offlineBackground
        );
        return true;
    }

    private void setEsp32SensorCard(
            String badge,
            String status,
            String detail,
            String lastSeen,
            int colorResource,
            int backgroundResource
    ) {
        int statusColor = color(colorResource);
        txtEsp32SensorBadge.setText(badge);
        txtEsp32SensorBadge.setTextColor(statusColor);
        txtEsp32SensorStatus.setText(status);
        txtEsp32SensorStatus.setTextColor(statusColor);
        txtEsp32SensorDetail.setText(detail);
        txtEsp32SensorLastSeen.setText(lastSeen);
        cardEsp32SensorBadge.setCardBackgroundColor(color(backgroundResource));
        cardEsp32SensorBadge.setStrokeColor(statusColor);
        cardEsp32SensorHealth.setStrokeColor(
                colorResource == R.color.online
                        ? color(R.color.border)
                        : statusColor
        );
    }

    private void renderNodeMcuHealth() {
        long nowEpoch = System.currentTimeMillis() / 1000L;
        SeedlingTelemetry telemetry = latestSeedlingTelemetry;

        if (telemetry == null || telemetry.getReceived_at_epoch() <= 0L) {
            setNodeMcuCard(
                    getString(R.string.runtime_no_connection_badge),
                    getString(R.string.health_nodemcu_waiting),
                    getString(R.string.health_nodemcu_no_data),
                    getString(R.string.health_nodemcu_never_seen),
                    R.color.offline,
                    R.color.offlineBackground
            );
            return;
        }

        long ageSeconds = Math.max(
                0L,
                nowEpoch - telemetry.getReceived_at_epoch()
        );
        String firmware = telemetry.getFirmware().isBlank()
                ? getString(R.string.health_nodemcu_unknown_firmware)
                : telemetry.getFirmware();
        String lastSeen = getString(
                R.string.health_nodemcu_last_seen,
                formatSensorAge(ageSeconds),
                telemetry.getRssi(),
                firmware
        );

        if (!telemetry.isFresh(nowEpoch, 90L)) {
            setNodeMcuCard(
                    getString(R.string.runtime_no_connection_badge),
                    getString(R.string.health_nodemcu_stale),
                    getString(R.string.health_nodemcu_stale_detail),
                    lastSeen,
                    R.color.offline,
                    R.color.offlineBackground
            );
            return;
        }

        if (!telemetry.isSoil_moisture_available()) {
            setNodeMcuCard(
                    getString(R.string.health_nodemcu_ads_missing_badge),
                    getString(R.string.health_nodemcu_running),
                    getString(R.string.health_nodemcu_without_ads),
                    lastSeen,
                    R.color.warning,
                    R.color.warningBackground
            );
            return;
        }

        setNodeMcuCard(
                getString(R.string.runtime_connected_badge),
                getString(R.string.health_nodemcu_healthy),
                getString(R.string.health_nodemcu_all_sensors),
                lastSeen,
                R.color.online,
                R.color.onlineBackground
        );
    }

    private void setNodeMcuCard(
            String badge,
            String status,
            String detail,
            String lastSeen,
            int colorResource,
            int backgroundResource
    ) {
        int statusColor = color(colorResource);
        txtNodeMcuBadge.setText(badge);
        txtNodeMcuBadge.setTextColor(statusColor);
        txtNodeMcuStatus.setText(status);
        txtNodeMcuStatus.setTextColor(statusColor);
        txtNodeMcuDetail.setText(detail);
        txtNodeMcuLastSeen.setText(lastSeen);
        cardNodeMcuBadge.setCardBackgroundColor(color(backgroundResource));
        cardNodeMcuBadge.setStrokeColor(statusColor);
        cardNodeMcuHealth.setStrokeColor(
                colorResource == R.color.online
                        ? color(R.color.border)
                        : statusColor
        );
    }

    private String formatSensorAge(long ageSeconds) {
        if (ageSeconds < 5L) {
            return getString(R.string.runtime_just_now);
        }
        if (ageSeconds < 60L) {
            return getString(R.string.runtime_seconds_ago, ageSeconds);
        }
        return getString(R.string.runtime_minutes_ago, ageSeconds / 60L);
    }

    private void addDiagnosticRow(
            boolean healthy,
            int textResource
    ) {
        addDiagnosticRow(healthy, getString(textResource));
    }

    private void addDiagnosticRow(
            boolean healthy,
            String message
    ) {
        TextView row = new TextView(this);
        row.setText(getString(
                R.string.runtime_icon_label,
                getString(healthy
                        ? R.string.symbol_check
                        : R.string.symbol_warning),
                message));
        row.setTextColor(
                color(
                        healthy
                                ? R.color.online
                                : R.color.warning
                )
        );
        row.setTextSize(13f);
        row.setPadding(0, 7, 0, 7);
        layoutDiagnostics.addView(row);
    }
    private void renderPowerDetails(
            Health health
    ) {

        String currentEvents =
                buildCurrentPowerEvents(
                        health
                );

        String historicalEvents =
                buildHistoricalPowerEvents(
                        health
                );

        boolean hasCurrentEvents =
                health.isUnderVoltageNow()
                        || health.isFrequencyCappedNow()
                        || health.isThrottledNow()
                        || health.isSoftTemperatureLimitNow();

        boolean hasHistoricalEvents =
                health.isUnderVoltageHistory()
                        || health.isFrequencyCappedHistory()
                        || health.isThrottledHistory()
                        || health.isSoftTemperatureLimitHistory();

        txtCurrentPowerEvents.setText(
                currentEvents
        );

        txtCurrentPowerEvents.setTextColor(
                color(
                        hasCurrentEvents
                                ? R.color.offline
                                : R.color.online
                )
        );

        txtHistoricalPowerEvents.setText(
                historicalEvents
        );

        txtHistoricalPowerEvents.setTextColor(
                color(
                        hasHistoricalEvents
                                ? R.color.warning
                                : R.color.textSecondary
                )
        );

        txtThrottlingRaw.setText(
                getString(
                        R.string.health_throttling_raw_format,
                        health.getThrottledRaw()
                )
        );
    }

    private String buildCurrentPowerEvents(
            Health health
    ) {

        StringBuilder builder =
                new StringBuilder();

        appendPowerEvent(
                builder,
                health.isUnderVoltageNow(),
                getString(
                        R.string.health_event_under_voltage_now
                )
        );

        appendPowerEvent(
                builder,
                health.isFrequencyCappedNow(),
                getString(
                        R.string.health_event_frequency_capped_now
                )
        );

        appendPowerEvent(
                builder,
                health.isThrottledNow(),
                getString(
                        R.string.health_event_throttled_now
                )
        );

        appendPowerEvent(
                builder,
                health.isSoftTemperatureLimitNow(),
                getString(
                        R.string.health_event_temperature_limit_now
                )
        );

        if (builder.length() == 0) {

            return getString(R.string.symbol_check) + " "
                    + getString(
                    R.string.health_no_current_events
            );
        }

        return builder.toString();
    }

    private String buildHistoricalPowerEvents(
            Health health
    ) {

        StringBuilder builder =
                new StringBuilder();

        appendPowerEvent(
                builder,
                health.isUnderVoltageHistory(),
                getString(
                        R.string.health_event_under_voltage_history
                )
        );

        appendPowerEvent(
                builder,
                health.isFrequencyCappedHistory(),
                getString(
                        R.string.health_event_frequency_capped_history
                )
        );

        appendPowerEvent(
                builder,
                health.isThrottledHistory(),
                getString(
                        R.string.health_event_throttled_history
                )
        );

        appendPowerEvent(
                builder,
                health.isSoftTemperatureLimitHistory(),
                getString(
                        R.string.health_event_temperature_limit_history
                )
        );

        if (builder.length() == 0) {

            return getString(R.string.symbol_check) + " "
                    + getString(
                    R.string.health_no_history_events
            );
        }

        return builder.toString();
    }

    private void appendPowerEvent(
            StringBuilder builder,
            boolean condition,
            String eventText
    ) {

        if (!condition) {
            return;
        }

        if (builder.length() > 0) {

            builder.append(
                    "\n"
            );
        }

        builder.append(getString(R.string.symbol_warning)).append(" ");

        builder.append(
                eventText
        );
    }
    private void renderCpu(Health health) {

        double temperature =
                Math.max(0, health.getCpuTemperature());

        double usage =
                clampPercentage(health.getCpuUsage());

        txtCpuTemperature.setText(
                displayUnits.formatTemperature(temperature)
        );

        txtCpuUsage.setText(
                getString(
                        R.string.health_percentage_format,
                        usage
                )
        );

        int cpuUsage =
                (int) Math.round(
                        health.getCpuUsage()
                );

        int visualCpuProgress;

        if (cpuUsage <= 0) {

            visualCpuProgress = 0;

        } else {

            visualCpuProgress =
                    Math.max(
                            cpuUsage,
                            4
                    );
        }

        progressCpu.setProgress(
                visualCpuProgress
        );

        int color =
                usage >= 85 || temperature >= 75
                        ? color(R.color.offline)
                        : usage >= 65 || temperature >= 65
                        ? color(R.color.warning)
                        : color(R.color.online);

        progressCpu.setProgressTintList(
                ColorStateList.valueOf(color)
        );

        txtCpuTemperature.setTextColor(color);
        txtCpuUsage.setTextColor(color);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (latestHealth != null) renderCpu(latestHealth);
    }

    private void renderMemory(Health health) {

        double memoryUsage =
                clampPercentage(health.getMemoryUsage());

        txtRamUsage.setText(
                getString(
                        R.string.health_percentage_format,
                        memoryUsage
                )
        );

        txtRamDetail.setText(
                getString(
                        R.string.health_memory_summary_format,
                        memoryUsage
                )
        );

        progressRam.setProgress(
                (int) Math.round(memoryUsage)
        );

        int color =
                resourceColorForUsage(memoryUsage);

        txtRamUsage.setTextColor(color);

        progressRam.setProgressTintList(
                ColorStateList.valueOf(color)
        );
    }

    private void renderDisk(Health health) {

        double diskUsage =
                clampPercentage(health.getDiskUsage());

        txtDiskUsage.setText(
                getString(
                        R.string.health_percentage_format,
                        diskUsage
                )
        );

        txtDiskDetail.setText(
                getString(
                        R.string.health_disk_summary_format,
                        diskUsage
                )
        );

        progressDisk.setProgress(
                (int) Math.round(diskUsage)
        );

        int color =
                resourceColorForUsage(diskUsage);

        txtDiskUsage.setTextColor(color);

        progressDisk.setProgressTintList(
                ColorStateList.valueOf(color)
        );
    }

    private void renderNetwork(Health health) {

        long wifiSignal =
                health.getWifiSignal();

        txtWifiSignal.setText(
                getString(
                        R.string.health_signal_format,
                        wifiSignal
                )
        );

        String quality;
        int statusColor;
        int backgroundColor;

        if (wifiSignal >= -55) {

            quality =
                    getString(R.string.health_wifi_excellent);

            statusColor =
                    color(R.color.online);

            backgroundColor =
                    color(R.color.onlineBackground);

        } else if (wifiSignal >= -67) {

            quality =
                    getString(R.string.health_wifi_good);

            statusColor =
                    color(R.color.primary);

            backgroundColor =
                    color(R.color.primaryLight);

        } else if (wifiSignal >= -75) {

            quality =
                    getString(R.string.health_wifi_weak);

            statusColor =
                    color(R.color.warning);

            backgroundColor =
                    color(R.color.warningBackground);

        } else {

            quality =
                    getString(R.string.health_wifi_poor);

            statusColor =
                    color(R.color.offline);

            backgroundColor =
                    color(R.color.offlineBackground);
        }

        txtWifiQuality.setText(quality);
        txtWifiQuality.setTextColor(statusColor);

        txtWifiStatusIcon.setTextColor(statusColor);

        cardWifiStatus.setCardBackgroundColor(
                backgroundColor
        );

        cardWifiStatus.setStrokeColor(
                statusColor
        );

        String ipAddress =
                health.getIpAddress();

        txtIpAddress.setText(
                ipAddress == null || ipAddress.isBlank()
                        ? getString(R.string.health_default_ip)
                        : ipAddress
        );
    }

    private void renderThrottling(Health health) {

        boolean throttled =
                health.isThrottled();

        int statusColor =
                throttled
                        ? color(R.color.offline)
                        : color(R.color.online);

        int backgroundColor =
                throttled
                        ? color(R.color.offlineBackground)
                        : color(R.color.onlineBackground);

        txtThrottlingStatus.setText(
                throttled
                        ? R.string.health_throttling_active
                        : R.string.health_throttling_clear
        );

        txtThrottlingDescription.setText(
                throttled
                        ? R.string.health_throttling_active_description
                        : R.string.health_throttling_clear_description
        );

        txtThrottlingStatus.setTextColor(
                statusColor
        );

        txtThrottlingDescription.setTextColor(
                statusColor
        );

        cardThrottlingBadge.setCardBackgroundColor(
                backgroundColor
        );

        cardThrottlingBadge.setStrokeColor(
                statusColor
        );

        cardThrottling.setStrokeColor(
                throttled
                        ? statusColor
                        : color(R.color.border)
        );
    }

    private void renderSystemInfo(Health health) {

        String firmware = health.getFirmware();

        txtFirmware.setText(
                firmware == null || firmware.isBlank()
                        ? getString(R.string.health_firmware_unavailable)
                        : firmware
        );

        txtUptime.setText(
                formatUptime(
                        health.getUptimeSeconds()
                )
        );

        txtLastHealthUpdate.setText(
                formatUpdatedAt(
                        health.getUpdatedAt()
                )
        );
    }

    private void renderOverallHealth(Health health) {

        boolean critical =
                health.isThrottled()
                        || health.getCpuTemperature() >= 75
                        || health.getCpuUsage() >= 85
                        || health.getMemoryUsage() >= 90
                        || health.getDiskUsage() >= 90
                        || health.getWifiSignal() < -80;

        boolean warning =
                health.getCpuTemperature() >= 65
                        || health.getCpuUsage() >= 65
                        || health.getMemoryUsage() >= 75
                        || health.getDiskUsage() >= 75
                        || health.getWifiSignal() < -67;

        int statusColor;
        int backgroundColor;
        int titleResource;
        int badgeResource;

        if (critical) {

            statusColor =
                    color(R.color.offline);

            backgroundColor =
                    color(R.color.offlineBackground);

            titleResource =
                    R.string.health_overall_critical;

            badgeResource =
                    R.string.health_badge_critical;

        } else if (warning) {

            statusColor =
                    color(R.color.warning);

            backgroundColor =
                    color(R.color.warningBackground);

            titleResource =
                    R.string.health_overall_warning;

            badgeResource =
                    R.string.health_badge_warning;

        } else {

            statusColor =
                    color(R.color.online);

            backgroundColor =
                    color(R.color.onlineBackground);

            titleResource =
                    R.string.health_overall_good;

            badgeResource =
                    R.string.health_badge_good;
        }

        txtOverallHealth.setText(
                titleResource
        );

        txtOverallHealth.setTextColor(
                statusColor
        );

        txtOverallHealthBadge.setText(
                badgeResource
        );

        txtOverallHealthBadge.setTextColor(
                statusColor
        );

        cardOverallHealth.setStrokeColor(
                statusColor
        );

        cardOverallHealthBadge.setCardBackgroundColor(
                backgroundColor
        );

        cardOverallHealthBadge.setStrokeColor(
                statusColor
        );

        cardHealthSummary.setCardBackgroundColor(
                backgroundColor
        );

        cardHealthSummary.setStrokeColor(
                statusColor
        );

        txtHealthSummaryIcon.setTextColor(
                statusColor
        );
    }

    private double clampPercentage(double value) {

        return Math.max(
                0,
                Math.min(
                        100,
                        value
                )
        );
    }

    private int resourceColorForUsage(double usage) {

        if (usage >= 90) {
            return color(R.color.offline);
        }

        if (usage >= 75) {
            return color(R.color.warning);
        }

        return color(R.color.online);
    }

    private String formatUptime(long totalSeconds) {

        long safeSeconds =
                Math.max(0, totalSeconds);

        long days =
                safeSeconds / 86400;

        long hours =
                (safeSeconds % 86400) / 3600;

        long minutes =
                (safeSeconds % 3600) / 60;

        if (days > 0) {

            return getString(
                    R.string.health_uptime_days_format,
                    days,
                    hours
            );
        }

        if (hours > 0) {

            return getString(
                    R.string.health_uptime_hours_format,
                    hours,
                    minutes
            );
        }

        return getString(
                R.string.health_uptime_minutes_format,
                minutes
        );
    }

    private String formatUpdatedAt(String updatedAt) {

        if (
                updatedAt == null
                        || updatedAt.isBlank()
        ) {

            return getString(
                    R.string.health_update_waiting
            );
        }

        try {

            String normalized =
                    updatedAt.length() >= 19
                            ? updatedAt.substring(0, 19)
                            : updatedAt;

            SimpleDateFormat sourceFormat =
                    new SimpleDateFormat(
                            "yyyy-MM-dd'T'HH:mm:ss",
                            Locale.US
                    );

            SimpleDateFormat displayFormat =
                    new SimpleDateFormat(
                            "dd-MM-yyyy HH:mm",
                            Locale.forLanguageTag("tr-TR")
                    );

            Date date =
                    sourceFormat.parse(normalized);

            if (date == null) {

                return getString(
                        R.string.health_update_waiting
                );
            }

            return getString(
                    R.string.health_last_update_format,
                    displayFormat.format(date)
            );

        } catch (ParseException exception) {

            return getString(
                    R.string.health_update_waiting
            );
        }
    }

    private int color(int colorResource) {

        return ContextCompat.getColor(
                this,
                colorResource
        );
    }

}
