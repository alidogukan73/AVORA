package com.alidogukan.avora.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.config.AppInfo;
import com.alidogukan.avora.models.DeviceNetworkStatus;
import com.alidogukan.avora.models.Health;
import com.alidogukan.avora.models.NetworkConfigurationRequest;
import com.alidogukan.avora.models.NetworkConfigurationResult;
import com.alidogukan.avora.models.NetworkSettingsValidator;
import com.alidogukan.avora.models.Status;
import com.alidogukan.avora.ui.PrimaryBottomNavigation;
import com.alidogukan.avora.viewmodels.DeviceInfoViewModel;
import com.alidogukan.avora.viewmodels.DeviceInfoViewModel.DeviceInfoState;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Read-only device identity, connection and installed hardware overview. */
public class DeviceInfoActivity extends AppCompatActivity {
    private static final String STATE_LOCAL_REQUEST_PENDING = "network_local_request_pending";
    private static final String STATE_PENDING_REQUEST_ID = "network_pending_request_id";
    private static final String STATE_PENDING_MODE = "network_pending_mode";
    private static final String STATE_PENDING_IP = "network_pending_ip";
    private static final String STATE_AWAITING_REFRESH = "network_awaiting_refresh";
    private static final String STATE_COMPLETED_REQUEST_ID = "network_completed_request_id";
    private static final String STATE_EXPECTED_MODE = "network_expected_mode";
    private static final String STATE_EXPECTED_IP = "network_expected_ip";

    private TextView connectionStatus;
    private TextView connectionSummary;
    private TextView backendVersion;
    private TextView espFirmware;
    private TextView ipAddress;
    private TextView lastConnection;
    private TextView uptime;
    private TextView zoneCount;
    private TextView sensorCount;
    private TextView physicalValveCount;
    private TextView networkSupport;
    private TextView networkInterface;
    private TextView networkTailscale;
    private TextView networkOperationStatus;
    private MaterialButtonToggleGroup networkModeGroup;
    private MaterialButton networkDhcp;
    private MaterialButton networkStatic;
    private MaterialButton applyNetwork;
    private LinearLayout networkAddressFields;
    private TextInputLayout networkIpLayout;
    private TextInputLayout networkSubnetLayout;
    private TextInputLayout networkGatewayLayout;
    private TextInputLayout networkPrimaryDnsLayout;
    private TextInputLayout networkSecondaryDnsLayout;
    private EditText networkIp;
    private EditText networkSubnet;
    private EditText networkGateway;
    private EditText networkPrimaryDns;
    private EditText networkSecondaryDns;
    private DeviceInfoViewModel viewModel;
    private DeviceInfoState lastState;
    private boolean bindingNetwork;
    private boolean networkFieldsInitialized;
    private boolean localNetworkRequestPending;
    private String pendingNetworkRequestId = "";
    private String pendingNetworkMode = "";
    private String pendingNetworkIp = "";
    private boolean awaitingNetworkStatusRefresh;
    private String completedNetworkRequestId = "";
    private String expectedNetworkMode = "";
    private String expectedNetworkIp = "";
    private String boundNetworkSignature = "";

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_device_info);
        applyWindowInsets();
        bindViews();
        restoreNetworkOperationState(state);
        configureToolbar();
        configureLinks();
        configureNetworkEditor();
        PrimaryBottomNavigation.bind(this, PrimaryBottomNavigation.SETTINGS);
        observeDevice();
    }

    private void bindViews() {
        connectionStatus = findViewById(R.id.txtDeviceInfoConnectionStatus);
        connectionSummary = findViewById(R.id.txtDeviceInfoConnectionSummary);

        LinearLayout identity = findViewById(R.id.layoutDeviceInfoIdentity);
        TextView deviceId = addValueRow(identity, R.string.device_info_device_id, false);
        backendVersion = addValueRow(identity, R.string.device_info_backend_version, true);
        espFirmware = addValueRow(identity, R.string.device_info_esp_firmware, true);

        LinearLayout connection = findViewById(R.id.layoutDeviceInfoConnection);
        ipAddress = addValueRow(connection, R.string.device_info_ip_address, false);
        lastConnection = addValueRow(connection, R.string.device_info_last_connection, true);
        uptime = addValueRow(connection, R.string.device_info_uptime, true);

        LinearLayout hardware = findViewById(R.id.layoutDeviceInfoHardware);
        zoneCount = addValueRow(hardware, R.string.device_info_zone_count, false);
        sensorCount = addValueRow(hardware, R.string.device_info_sensor_count, true);
        physicalValveCount = addValueRow(hardware, R.string.device_info_valve_count, true);

        networkSupport = findViewById(R.id.txtNetworkSupport);
        networkInterface = findViewById(R.id.txtNetworkInterface);
        networkTailscale = findViewById(R.id.txtNetworkTailscale);
        networkOperationStatus = findViewById(R.id.txtNetworkOperationStatus);
        networkModeGroup = findViewById(R.id.groupNetworkMode);
        networkDhcp = findViewById(R.id.btnNetworkDhcp);
        networkStatic = findViewById(R.id.btnNetworkStatic);
        applyNetwork = findViewById(R.id.btnApplyNetworkSettings);
        networkAddressFields = findViewById(R.id.layoutNetworkAddressFields);
        networkIpLayout = findViewById(R.id.layoutNetworkIp);
        networkSubnetLayout = findViewById(R.id.layoutNetworkSubnet);
        networkGatewayLayout = findViewById(R.id.layoutNetworkGateway);
        networkPrimaryDnsLayout = findViewById(R.id.layoutNetworkPrimaryDns);
        networkSecondaryDnsLayout = findViewById(R.id.layoutNetworkSecondaryDns);
        networkIp = findViewById(R.id.inputNetworkIp);
        networkSubnet = findViewById(R.id.inputNetworkSubnet);
        networkGateway = findViewById(R.id.inputNetworkGateway);
        networkPrimaryDns = findViewById(R.id.inputNetworkPrimaryDns);
        networkSecondaryDns = findViewById(R.id.inputNetworkSecondaryDns);

        deviceId.setText(AppInfo.DEVICE_ID);
        setWaitingValues();
    }

    private void setWaitingValues() {
        backendVersion.setText(R.string.device_info_waiting);
        espFirmware.setText(R.string.device_info_waiting);
        ipAddress.setText(R.string.device_info_waiting);
        lastConnection.setText(R.string.device_info_waiting);
        uptime.setText(R.string.device_info_waiting);
        zoneCount.setText(R.string.device_info_waiting);
        sensorCount.setText(R.string.device_info_waiting);
        physicalValveCount.setText(R.string.device_info_waiting);
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
                .setText(R.string.device_info_title);
        findViewById(R.id.btnSettingsToolbarBack).setOnClickListener(view -> finish());
        findViewById(R.id.btnSettingsToolbarAction).setVisibility(View.GONE);
    }

    private void configureLinks() {
        MaterialCardView healthLink = findViewById(R.id.deviceInfoHealthLink);
        MaterialCardView sensorsLink = findViewById(R.id.deviceInfoSensorsLink);
        configureLink(healthLink, R.drawable.ic_device_health_24,
                R.string.device_info_open_health,
                R.string.device_info_open_health_subtitle,
                () -> startActivity(new Intent(this, DeviceHealthActivity.class)));
        configureLink(sensorsLink, R.drawable.ic_ai_sensor_24,
                R.string.device_info_open_sensors,
                R.string.device_info_open_sensors_subtitle,
                () -> startActivity(new Intent(this, SensorPointsActivity.class)));
    }

    private void configureLink(MaterialCardView card, int iconRes, int titleRes,
                               int subtitleRes, Runnable action) {
        ((AppCompatImageView) card.findViewById(R.id.imgSettingsRowIcon))
                .setImageResource(iconRes);
        ((TextView) card.findViewById(R.id.txtSettingsRowTitle)).setText(titleRes);
        ((TextView) card.findViewById(R.id.txtSettingsRowSubtitle)).setText(subtitleRes);
        card.setOnClickListener(view -> action.run());
    }

    private void configureNetworkEditor() {
        setNetworkControlsEnabled(false);
        networkModeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || bindingNetwork) return;
            renderNetworkMode();
        });
        applyNetwork.setOnClickListener(view -> prepareNetworkRequest());
    }

    private void observeDevice() {
        viewModel = new ViewModelProvider(this).get(DeviceInfoViewModel.class);
        viewModel.getReadError().observe(this, failed -> {
            if (!Boolean.TRUE.equals(failed)) return;
            connectionStatus.setText(R.string.device_info_status_offline);
            connectionStatus.setTextColor(ContextCompat.getColor(
                    DeviceInfoActivity.this, R.color.offline));
            connectionSummary.setText(R.string.device_info_read_error);
        });
        viewModel.getState().observe(this, this::render);
    }

    private void render(DeviceInfoState value) {
        if (value == null) return;
        Status status = value.status;
        Health health = value.health;
        renderConnection(value.connected);
        lastState = value;

        backendVersion.setText(firstMeaningful(
                health == null ? null : health.getFirmware(),
                status == null ? null : status.getVersion(),
                getString(R.string.device_info_waiting)));
        DeviceNetworkStatus currentNetwork = value.networkStatus;
        ipAddress.setText(firstMeaningful(
                currentNetwork == null ? null : currentNetwork.getIpAddress(),
                health == null ? null : health.getIpAddress(),
                getString(R.string.device_info_unavailable)));
        lastConnection.setText(value.lastSeenMillis > 0L
                ? formatDateTime(value.lastSeenMillis)
                : getString(R.string.device_info_waiting));
        uptime.setText(health != null && health.getUptimeSeconds() > 0L
                ? formatDuration(health.getUptimeSeconds())
                : getString(R.string.device_info_waiting));
        zoneCount.setText(getString(R.string.device_info_zone_count_value, value.enabledZones));
        sensorCount.setText(getString(
                R.string.device_info_sensor_count_value, value.enabledSensors));
        physicalValveCount.setText(getString(
                R.string.device_info_valve_count_value, value.physicalValves));
        espFirmware.setText(value.firmwareVersions.isEmpty()
                ? getString(R.string.device_info_sensor_firmware_waiting)
                : String.join(" / ", value.firmwareVersions));
        renderNetwork(value);
    }

    private void renderNetwork(DeviceInfoState value) {
        DeviceNetworkStatus network = value.networkStatus;
        NetworkConfigurationResult result = value.networkResult;
        if (network == null) {
            networkSupport.setText(value.connected
                    ? R.string.network_settings_waiting
                    : R.string.network_settings_offline);
            networkSupport.setTextColor(ContextCompat.getColor(this,
                    value.connected ? R.color.textSecondary : R.color.warning));
            setNetworkControlsEnabled(false);
            return;
        }

        String connection = firstMeaningful(network.getConnectionName(),
                network.getInterfaceName(), getString(R.string.device_info_unavailable));
        if (isMeaningful(network.getConnectionName())
                && isMeaningful(network.getInterfaceName())) {
            connection = network.getConnectionName() + " · " + network.getInterfaceName();
        }
        networkInterface.setText(connection);
        networkTailscale.setText(getString(R.string.network_settings_tailscale_value,
                firstMeaningful(network.getTailscaleIp(),
                        getString(R.string.device_info_unavailable))));

        boolean supported = network.isSupported();
        networkSupport.setText(!value.connected
                ? R.string.network_settings_offline
                : supported ? R.string.network_settings_supported
                : R.string.network_settings_unsupported);
        networkSupport.setTextColor(ContextCompat.getColor(this,
                value.connected && supported ? R.color.textSecondary : R.color.warning));

        boolean hasLocalPendingRequest = localNetworkRequestPending
                && isMeaningful(pendingNetworkRequestId);
        String currentSignature = networkSignature(network);
        if (!networkFieldsInitialized
                || (!hasLocalPendingRequest
                    && !awaitingNetworkStatusRefresh
                    && !currentSignature.equals(boundNetworkSignature))) {
            bindNetworkFields(network);
            networkFieldsInitialized = true;
        }
        boolean recentRemoteProgress = result != null && result.isInProgress()
                && result.getUpdatedAtEpoch() > 0L
                && Math.abs(System.currentTimeMillis() / 1000L
                        - result.getUpdatedAtEpoch()) <= 120L;
        if (result != null) {
            boolean matchesLocalPendingRequest = hasLocalPendingRequest
                    && pendingNetworkRequestId.equals(result.getRequestId());
            if (!hasLocalPendingRequest || matchesLocalPendingRequest) {
                renderNetworkResult(result);
            }
            if (matchesLocalPendingRequest && !result.isInProgress()
                    && isMeaningful(result.getStatus())) {
                if ("SUCCESS".equals(result.getStatus())) {
                    awaitingNetworkStatusRefresh = true;
                    completedNetworkRequestId = result.getRequestId();
                    expectedNetworkMode = pendingNetworkMode;
                    expectedNetworkIp = firstMeaningful(
                            result.getAppliedIp(), pendingNetworkIp);
                } else {
                    bindNetworkFields(network);
                    clearNetworkStatusRefresh();
                }
                localNetworkRequestPending = false;
                pendingNetworkRequestId = "";
                pendingNetworkMode = "";
                pendingNetworkIp = "";
            }
            if (awaitingNetworkStatusRefresh
                    && completedNetworkRequestId.equals(result.getRequestId())
                    && DeviceInfoViewModel.representsAppliedNetworkConfiguration(
                            network, expectedNetworkMode, expectedNetworkIp)) {
                bindNetworkFields(network);
                clearNetworkStatusRefresh();
            }
        }
        boolean inProgress = (localNetworkRequestPending
                && isMeaningful(pendingNetworkRequestId))
                || recentRemoteProgress
                || awaitingNetworkStatusRefresh;
        setNetworkControlsEnabled(value.connected && supported && !inProgress);
    }

    private void clearNetworkStatusRefresh() {
        awaitingNetworkStatusRefresh = false;
        completedNetworkRequestId = "";
        expectedNetworkMode = "";
        expectedNetworkIp = "";
    }

    private void bindNetworkFields(DeviceNetworkStatus network) {
        bindingNetwork = true;
        networkModeGroup.check("STATIC".equalsIgnoreCase(network.getMode())
                ? R.id.btnNetworkStatic : R.id.btnNetworkDhcp);
        networkIp.setText(network.getIpAddress());
        networkSubnet.setText(isMeaningful(network.getSubnetMask())
                ? network.getSubnetMask()
                : NetworkSettingsValidator.subnetMaskForPrefix(
                        (int) network.getPrefixLength()));
        networkGateway.setText(network.getGateway());
        networkPrimaryDns.setText(network.getPrimaryDns());
        networkSecondaryDns.setText(network.getSecondaryDns());
        boundNetworkSignature = networkSignature(network);
        bindingNetwork = false;
        renderNetworkMode();
    }

    private void renderNetworkMode() {
        boolean editable = controlsMayBeEnabled();
        boolean isStatic = networkModeGroup.getCheckedButtonId() == R.id.btnNetworkStatic;
        networkAddressFields.setAlpha(isStatic ? 1f : 0.62f);
        networkIp.setEnabled(editable && isStatic);
        networkSubnet.setEnabled(editable && isStatic);
        networkGateway.setEnabled(editable && isStatic);
        networkPrimaryDns.setEnabled(editable && isStatic);
        networkSecondaryDns.setEnabled(editable && isStatic);
        clearNetworkErrors();
    }

    private boolean controlsMayBeEnabled() {
        return lastState != null && lastState.connected
                && lastState.networkStatus != null
                && lastState.networkStatus.isSupported()
                && !localNetworkRequestPending
                && (lastState.networkResult == null
                    || !lastState.networkResult.isInProgress());
    }

    private void setNetworkControlsEnabled(boolean enabled) {
        networkDhcp.setEnabled(enabled);
        networkStatic.setEnabled(enabled);
        applyNetwork.setEnabled(enabled);
        boolean isStatic = networkModeGroup.getCheckedButtonId() == R.id.btnNetworkStatic;
        networkIp.setEnabled(enabled && isStatic);
        networkSubnet.setEnabled(enabled && isStatic);
        networkGateway.setEnabled(enabled && isStatic);
        networkPrimaryDns.setEnabled(enabled && isStatic);
        networkSecondaryDns.setEnabled(enabled && isStatic);
    }

    private void prepareNetworkRequest() {
        if (!controlsMayBeEnabled() || lastState.networkStatus == null) return;
        clearNetworkErrors();
        String interfaceName = lastState.networkStatus.getInterfaceName();
        boolean isStatic = networkModeGroup.getCheckedButtonId() == R.id.btnNetworkStatic;
        NetworkConfigurationRequest request;
        if (isStatic) {
            NetworkSettingsValidator.Result validation =
                    NetworkSettingsValidator.validateStatic(
                            text(networkIp), text(networkSubnet), text(networkGateway),
                            text(networkPrimaryDns), text(networkSecondaryDns));
            if (!validation.valid) {
                showNetworkValidationError(validation.error);
                return;
            }
            request = NetworkConfigurationRequest.fixed(interfaceName, validation);
        } else {
            request = NetworkConfigurationRequest.dhcp(interfaceName);
        }
        String message = isStatic
                ? getString(R.string.network_settings_confirm_static, request.ipAddress)
                : getString(R.string.network_settings_confirm_dhcp);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.network_settings_confirm_title)
                .setMessage(message)
                .setNegativeButton(R.string.settings_quick_cancel, null)
                .setPositiveButton(R.string.network_settings_confirm_action,
                        (dialog, which) -> sendNetworkRequest(request))
                .show();
    }

    private void sendNetworkRequest(NetworkConfigurationRequest request) {
        localNetworkRequestPending = true;
        clearNetworkStatusRefresh();
        pendingNetworkRequestId = request.requestId;
        pendingNetworkMode = request.mode;
        pendingNetworkIp = request.ipAddress;
        setNetworkControlsEnabled(false);
        networkOperationStatus.setText(R.string.network_settings_status_pending);
        networkOperationStatus.setTextColor(ContextCompat.getColor(this, R.color.info));
        viewModel.requestNetworkConfiguration(request).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(this, R.string.network_settings_request_sent,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            localNetworkRequestPending = false;
            pendingNetworkRequestId = "";
            pendingNetworkMode = "";
            pendingNetworkIp = "";
            setNetworkControlsEnabled(controlsMayBeEnabled());
            networkOperationStatus.setText(R.string.network_settings_request_failed);
            networkOperationStatus.setTextColor(ContextCompat.getColor(this, R.color.error));
        });
    }

    private void renderNetworkResult(NetworkConfigurationResult result) {
        int text;
        int color = R.color.info;
        switch (result.getStatus()) {
            case "PENDING": text = R.string.network_settings_status_pending; break;
            case "VALIDATING": text = R.string.network_settings_status_validating; break;
            case "APPLYING": text = R.string.network_settings_status_applying; break;
            case "VERIFYING": text = R.string.network_settings_status_verifying; break;
            case "SUCCESS":
                text = R.string.network_settings_status_success;
                color = R.color.success;
                break;
            case "ROLLED_BACK":
                text = R.string.network_settings_status_rolled_back;
                color = R.color.warning;
                break;
            case "WATERING_ACTIVE":
                text = R.string.network_settings_status_busy;
                color = R.color.warning;
                break;
            case "":
                text = R.string.network_settings_safety_note;
                color = R.color.textSecondary;
                break;
            default:
                text = R.string.network_settings_status_failed;
                color = R.color.error;
                break;
        }
        networkOperationStatus.setText(text);
        networkOperationStatus.setTextColor(ContextCompat.getColor(this, color));
    }

    private void showNetworkValidationError(NetworkSettingsValidator.Error error) {
        switch (error) {
            case ADDRESS_REQUIRED:
                networkIpLayout.setError(getString(R.string.network_settings_error_ip_required));
                break;
            case ADDRESS_INVALID:
                networkIpLayout.setError(getString(R.string.network_settings_error_ip_invalid));
                break;
            case SUBNET_INVALID:
                networkSubnetLayout.setError(getString(R.string.network_settings_error_subnet));
                break;
            case GATEWAY_REQUIRED:
                networkGatewayLayout.setError(getString(
                        R.string.network_settings_error_gateway_required));
                break;
            case GATEWAY_INVALID:
                networkGatewayLayout.setError(getString(R.string.network_settings_error_gateway));
                break;
            case GATEWAY_OUTSIDE_SUBNET:
                networkGatewayLayout.setError(getString(
                        R.string.network_settings_error_gateway_subnet));
                break;
            case DNS_REQUIRED:
                networkPrimaryDnsLayout.setError(getString(
                        R.string.network_settings_error_dns_required));
                break;
            case DNS_INVALID:
                networkPrimaryDnsLayout.setError(getString(R.string.network_settings_error_dns));
                break;
            default:
                break;
        }
    }

    private void restoreNetworkOperationState(@Nullable Bundle state) {
        if (state == null) return;
        localNetworkRequestPending = state.getBoolean(STATE_LOCAL_REQUEST_PENDING, false);
        pendingNetworkRequestId = safeStateValue(state.getString(STATE_PENDING_REQUEST_ID));
        pendingNetworkMode = safeStateValue(state.getString(STATE_PENDING_MODE));
        pendingNetworkIp = safeStateValue(state.getString(STATE_PENDING_IP));
        awaitingNetworkStatusRefresh = state.getBoolean(STATE_AWAITING_REFRESH, false);
        completedNetworkRequestId = safeStateValue(
                state.getString(STATE_COMPLETED_REQUEST_ID));
        expectedNetworkMode = safeStateValue(state.getString(STATE_EXPECTED_MODE));
        expectedNetworkIp = safeStateValue(state.getString(STATE_EXPECTED_IP));
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_LOCAL_REQUEST_PENDING, localNetworkRequestPending);
        outState.putString(STATE_PENDING_REQUEST_ID, pendingNetworkRequestId);
        outState.putString(STATE_PENDING_MODE, pendingNetworkMode);
        outState.putString(STATE_PENDING_IP, pendingNetworkIp);
        outState.putBoolean(STATE_AWAITING_REFRESH, awaitingNetworkStatusRefresh);
        outState.putString(STATE_COMPLETED_REQUEST_ID, completedNetworkRequestId);
        outState.putString(STATE_EXPECTED_MODE, expectedNetworkMode);
        outState.putString(STATE_EXPECTED_IP, expectedNetworkIp);
    }

    private String networkSignature(DeviceNetworkStatus value) {
        return safeStateValue(value.getMode()) + "|"
                + safeStateValue(value.getIpAddress()) + "|"
                + safeStateValue(value.getSubnetMask()) + "|"
                + value.getPrefixLength() + "|"
                + safeStateValue(value.getGateway()) + "|"
                + safeStateValue(value.getPrimaryDns()) + "|"
                + safeStateValue(value.getSecondaryDns());
    }

    private static String safeStateValue(String value) {
        return value == null ? "" : value.trim();
    }

    private void clearNetworkErrors() {
        networkIpLayout.setError(null);
        networkSubnetLayout.setError(null);
        networkGatewayLayout.setError(null);
        networkPrimaryDnsLayout.setError(null);
        networkSecondaryDnsLayout.setError(null);
    }

    private static String text(EditText view) {
        return view.getText() == null ? "" : view.getText().toString().trim();
    }

    private void renderConnection(boolean connected) {
        connectionStatus.setText(connected
                ? R.string.device_info_status_online
                : R.string.device_info_status_offline);
        connectionSummary.setText(connected
                ? R.string.device_info_summary_online
                : R.string.device_info_summary_offline);
        connectionStatus.setTextColor(ContextCompat.getColor(this,
                connected ? R.color.online : R.color.warning));
    }

    private String formatDateTime(long epochMillis) {
        return new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.forLanguageTag("tr-TR"))
                .format(new Date(epochMillis));
    }

    private String formatDuration(long totalSeconds) {
        long days = totalSeconds / 86_400L;
        long hours = (totalSeconds % 86_400L) / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        if (days > 0L) {
            return getString(R.string.device_info_duration_days, days, hours);
        }
        if (hours > 0L) {
            return getString(R.string.device_info_duration_hours, hours, minutes);
        }
        return getString(R.string.device_info_duration_minutes, Math.max(1L, minutes));
    }

    private String firstMeaningful(String first, String fallback) {
        return isMeaningful(first) ? first.trim() : fallback;
    }

    private String firstMeaningful(String first, String second, String fallback) {
        if (isMeaningful(first)) {
            return first.trim();
        }
        return isMeaningful(second) ? second.trim() : fallback;
    }

    private boolean isMeaningful(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private int dividerHeightPx() {
        return Math.round(getResources().getDisplayMetrics().density);
    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.deviceInfoRoot),
                (view, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });
    }

}
