package com.alidogukan.avora.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.config.AppInfo;
import com.alidogukan.avora.nas.NasApiClient;
import com.alidogukan.avora.nas.NasAuthClient;
import com.alidogukan.avora.nas.NasBackupArchive;
import com.alidogukan.avora.nas.NasDocumentClient;
import com.alidogukan.avora.nas.NasSession;
import com.alidogukan.avora.ui.PrimaryBottomNavigation;
import com.alidogukan.avora.viewmodels.DataSyncViewModel;
import com.alidogukan.avora.viewmodels.BackupViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Verifies the real Firebase session and refreshes AVORA's essential device summaries. */
public class DataSyncActivity extends AppCompatActivity {
    private static final long CONNECTION_TIMEOUT_MS = 12_000L;
    private static final String NAS_BACKUP_KEY = NasDocumentClient.backupKey(AppInfo.DEVICE_ID);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService nasExecutor = Executors.newSingleThreadExecutor();

    private TextView headline;
    private TextView connectionValue;
    private TextView nasConnectionValue;
    private TextView nasAccountValue;
    private TextView lastSuccessValue;
    private TextView lastDeviceDataValue;
    private TextView nasBackupValue;
    private TextView nasBackupValidationValue;
    private TextView nasPhotoValue;
    private TextView verifiedScopeValue;
    private TextView operationStatus;
    private MaterialSwitch automaticSwitch;
    private MaterialSwitch automaticNasBackupSwitch;
    private MaterialSwitch automaticNasPhotoBackupSwitch;
    private MaterialButton syncButton;
    private MaterialButton nasAccountButton;
    private MaterialButton nasBackupButton;
    private MaterialButton nasRestoreButton;
    private MaterialButton nasPhotoBackupButton;
    private MaterialButton nasPhotoRestoreButton;
    private LinearProgressIndicator progress;
    private DataSyncViewModel viewModel;
    private BackupViewModel backupViewModel;
    private NasSession activeNasSession;
    private JSONObject nasBackupData;
    private List<NasBackupEntry> nasBackups = Collections.emptyList();
    private DataSyncViewModel.PhotoInspection nasPhotoInspection;
    private long nasBackupUpdatedAt;
    private int nasBackupZoneCount;
    private int nasBackupRecordCount;
    private String nasBackupAppVersion = "";
    private boolean nasBackupValidated;
    private boolean nasBackupInvalid;
    private boolean firebaseConnected;
    private boolean manualSyncPending;
    private boolean remoteReadInFlight;
    private boolean nasCheckInFlight;
    private boolean nasAccountBusy;
    private boolean nasBackupBusy;
    private boolean nasRestoreBusy;
    private boolean nasBackupStatusLoading;
    private boolean nasBackupStatusFailed;
    private boolean nasPhotoBusy;
    private boolean nasPhotoStatusLoading;
    private boolean nasPhotoStatusFailed;
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
        backupViewModel = new ViewModelProvider(this).get(BackupViewModel.class);
        applyWindowInsets();
        bindViews();
        configureToolbar();
        configureAutomaticSync();
        configureAutomaticNasBackup();
        configureAutomaticNasPhotoBackup();
        configureActions();
        restoreNasSession();
        PrimaryBottomNavigation.bind(this, PrimaryBottomNavigation.SETTINGS);
        renderStoredState();
        observeConnection();
        checkNasConnection();
        if (automaticSwitch.isChecked()) {
            startManualSync();
        }
    }

    private void bindViews() {
        headline = findViewById(R.id.txtDataSyncConnectionHeadline);
        automaticSwitch = findViewById(R.id.switchDataSyncAutomatic);
        automaticNasBackupSwitch = findViewById(R.id.switchDataSyncNasAutomatic);
        automaticNasPhotoBackupSwitch = findViewById(R.id.switchDataSyncNasPhotosAutomatic);
        syncButton = findViewById(R.id.btnDataSyncNow);
        nasAccountButton = findViewById(R.id.btnDataSyncNasAccount);
        nasBackupButton = findViewById(R.id.btnDataSyncNasBackup);
        nasRestoreButton = findViewById(R.id.btnDataSyncNasRestore);
        nasPhotoBackupButton = findViewById(R.id.btnDataSyncNasPhotoBackup);
        nasPhotoRestoreButton = findViewById(R.id.btnDataSyncNasPhotoRestore);
        progress = findViewById(R.id.progressDataSync);
        operationStatus = findViewById(R.id.txtDataSyncOperationStatus);

        LinearLayout values = findViewById(R.id.layoutDataSyncValues);
        connectionValue = addValueRow(values, R.string.data_sync_connection_label, false);
        nasConnectionValue = addValueRow(values, R.string.data_sync_nas_connection_label, true);
        nasAccountValue = addValueRow(values, R.string.data_sync_nas_account_label, true);
        nasBackupValue = addValueRow(values, R.string.data_sync_nas_backup_label, true);
        nasBackupValidationValue = addValueRow(values,
                R.string.data_sync_nas_backup_validation_label, true);
        nasPhotoValue = addValueRow(values, R.string.data_sync_nas_photo_label, true);
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

    private void configureAutomaticNasBackup() {
        suppressSwitchCallback = true;
        automaticNasBackupSwitch.setChecked(viewModel.automaticNasBackupEnabled());
        suppressSwitchCallback = false;
        automaticNasBackupSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (suppressSwitchCallback) return;
            if (checked && activeNasSession == null) {
                suppressSwitchCallback = true;
                automaticNasBackupSwitch.setChecked(false);
                suppressSwitchCallback = false;
                Toast.makeText(this, R.string.data_sync_nas_auto_account_required,
                        Toast.LENGTH_LONG).show();
                showNasLoginDialog();
                return;
            }
            viewModel.setAutomaticNasBackupEnabled(checked);
            Toast.makeText(this, checked
                    ? R.string.data_sync_nas_auto_enabled
                    : R.string.data_sync_nas_auto_disabled,
                    Toast.LENGTH_LONG).show();
        });
    }

    private void configureAutomaticNasPhotoBackup() {
        suppressSwitchCallback = true;
        automaticNasPhotoBackupSwitch.setChecked(viewModel.automaticNasPhotoBackupEnabled());
        suppressSwitchCallback = false;
        automaticNasPhotoBackupSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (suppressSwitchCallback) return;
            if (checked && activeNasSession == null) {
                suppressSwitchCallback = true;
                automaticNasPhotoBackupSwitch.setChecked(false);
                suppressSwitchCallback = false;
                Toast.makeText(this, R.string.data_sync_nas_auto_account_required,
                        Toast.LENGTH_LONG).show();
                showNasLoginDialog();
                return;
            }
            viewModel.setAutomaticNasPhotoBackupEnabled(checked);
            Toast.makeText(this, checked
                    ? R.string.data_sync_nas_photo_auto_enabled
                    : R.string.data_sync_nas_photo_auto_disabled,
                    Toast.LENGTH_LONG).show();
        });
    }

    private void configureActions() {
        syncButton.setOnClickListener(view -> startManualSync());
        nasAccountButton.setOnClickListener(view -> {
            if (nasAccountBusy) return;
            if (activeNasSession == null) showNasLoginDialog();
            else startActivity(new Intent(this, NasSecurityActivity.class));
        });
        nasBackupButton.setOnClickListener(view -> beginNasBackup());
        nasRestoreButton.setOnClickListener(view -> showNasRestoreSelection());
        nasPhotoBackupButton.setOnClickListener(view -> beginNasPhotoBackup());
        nasPhotoRestoreButton.setOnClickListener(view -> confirmNasPhotoRestore());
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        restoreNasSession();
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
        checkNasConnection();
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

    private void checkNasConnection() {
        if (nasCheckInFlight) return;
        nasCheckInFlight = true;
        nasConnectionValue.setText(R.string.data_sync_nas_checking);
        nasConnectionValue.setTextColor(ContextCompat.getColor(this, R.color.textSecondary));
        nasExecutor.execute(() -> {
            try {
                NasApiClient.Health health = NasApiClient.checkHealth();
                handler.post(() -> {
                    nasCheckInFlight = false;
                    if (isFinishing() || isDestroyed()) return;
                    nasConnectionValue.setText(getString(
                            R.string.data_sync_nas_connected, health.version));
                    nasConnectionValue.setTextColor(
                            ContextCompat.getColor(this, R.color.online));
                });
            } catch (Exception error) {
                handler.post(() -> {
                    nasCheckInFlight = false;
                    if (isFinishing() || isDestroyed()) return;
                    nasConnectionValue.setText(R.string.data_sync_nas_disconnected);
                    nasConnectionValue.setTextColor(
                            ContextCompat.getColor(this, R.color.warning));
                });
            }
        });
    }

    private void restoreNasSession() {
        activeNasSession = viewModel.loadNasSession();
        resetNasBackupStatus();
        renderNasAccount();
        if (activeNasSession != null) {
            loadNasBackupStatus();
            loadNasPhotoStatus();
        }
    }

    private void renderNasAccount() {
        if (activeNasSession == null) {
            nasAccountValue.setText(R.string.data_sync_nas_account_not_connected);
            nasAccountValue.setTextColor(ContextCompat.getColor(this, R.color.warning));
            nasAccountButton.setText(R.string.data_sync_nas_account_connect);
        } else {
            nasAccountValue.setText(getString(R.string.data_sync_nas_account_connected,
                    activeNasSession.user.displayName));
            nasAccountValue.setTextColor(ContextCompat.getColor(this, R.color.online));
            nasAccountButton.setText(R.string.data_sync_nas_account_manage);
        }
        boolean anyNasBusy = nasAccountBusy || nasBackupBusy || nasRestoreBusy || nasPhotoBusy;
        nasAccountButton.setEnabled(!anyNasBusy);
        automaticNasBackupSwitch.setEnabled(
                !anyNasBusy);
        automaticNasPhotoBackupSwitch.setEnabled(!anyNasBusy);

        if (activeNasSession == null) {
            nasBackupValue.setText(R.string.data_sync_nas_backup_account_required);
        } else if (nasBackupBusy) {
            nasBackupValue.setText(R.string.data_sync_nas_backup_uploading);
        } else if (nasBackupStatusLoading) {
            nasBackupValue.setText(R.string.data_sync_nas_backup_checking);
        } else if (nasBackupStatusFailed) {
            nasBackupValue.setText(R.string.data_sync_nas_backup_status_unavailable);
        } else if (nasBackupUpdatedAt > 0L) {
            nasBackupValue.setText(formatDateTime(nasBackupUpdatedAt * 1000L));
        } else {
            nasBackupValue.setText(R.string.data_sync_nas_backup_never);
        }
        if (activeNasSession == null) {
            nasBackupValidationValue.setText(R.string.data_sync_nas_backup_account_required);
            nasBackupValidationValue.setTextColor(ContextCompat.getColor(this, R.color.textSecondary));
        } else if (nasBackupBusy || nasBackupStatusLoading) {
            nasBackupValidationValue.setText(R.string.data_sync_nas_backup_validation_checking);
            nasBackupValidationValue.setTextColor(ContextCompat.getColor(this, R.color.textSecondary));
        } else if (nasBackupStatusFailed) {
            nasBackupValidationValue.setText(
                    R.string.data_sync_nas_backup_validation_unavailable);
            nasBackupValidationValue.setTextColor(
                    ContextCompat.getColor(this, R.color.warning));
        } else if (nasBackupInvalid) {
            nasBackupValidationValue.setText(R.string.data_sync_nas_backup_validation_invalid);
            nasBackupValidationValue.setTextColor(ContextCompat.getColor(this, R.color.warning));
        } else if (nasBackupValidated) {
            nasBackupValidationValue.setText(getString(
                    R.string.data_sync_nas_backup_validation_valid,
                    nasBackupAppVersion, nasBackupZoneCount, nasBackupRecordCount));
            nasBackupValidationValue.setTextColor(ContextCompat.getColor(this, R.color.online));
        } else {
            nasBackupValidationValue.setText(R.string.data_sync_nas_backup_validation_waiting);
            nasBackupValidationValue.setTextColor(ContextCompat.getColor(this, R.color.textSecondary));
        }
        if (activeNasSession == null) {
            nasPhotoValue.setText(R.string.data_sync_nas_backup_account_required);
            nasPhotoValue.setTextColor(ContextCompat.getColor(this, R.color.textSecondary));
        } else if (nasPhotoBusy) {
            nasPhotoValue.setText(R.string.data_sync_nas_photo_working);
            nasPhotoValue.setTextColor(ContextCompat.getColor(this, R.color.textSecondary));
        } else if (nasPhotoStatusLoading) {
            nasPhotoValue.setText(R.string.data_sync_nas_photo_checking);
            nasPhotoValue.setTextColor(ContextCompat.getColor(this, R.color.textSecondary));
        } else if (nasPhotoStatusFailed) {
            nasPhotoValue.setText(R.string.data_sync_nas_photo_status_unavailable);
            nasPhotoValue.setTextColor(ContextCompat.getColor(this, R.color.warning));
        } else if (nasPhotoInspection != null) {
            nasPhotoValue.setText(getString(R.string.data_sync_nas_photo_status,
                    nasPhotoInspection.remoteCount, nasPhotoInspection.localCount,
                    nasPhotoInspection.missingOnPhoneCount));
            nasPhotoValue.setTextColor(ContextCompat.getColor(this,
                    nasPhotoInspection.remoteCount > 0 ? R.color.online : R.color.textSecondary));
        } else {
            nasPhotoValue.setText(R.string.data_sync_nas_photo_never);
            nasPhotoValue.setTextColor(ContextCompat.getColor(this, R.color.textSecondary));
        }
        nasBackupButton.setEnabled(activeNasSession != null
                && !anyNasBusy
                && !nasBackupStatusLoading);
        nasRestoreButton.setEnabled(activeNasSession != null && nasBackupValidated
                && nasBackupData != null && !anyNasBusy && !nasBackupStatusLoading);
        nasPhotoBackupButton.setEnabled(activeNasSession != null
                && !anyNasBusy && !nasPhotoStatusLoading);
        nasPhotoRestoreButton.setEnabled(activeNasSession != null
                && nasPhotoInspection != null
                && nasPhotoInspection.missingOnPhoneCount > 0
                && !anyNasBusy && !nasPhotoStatusLoading);
    }

    private void showNasLoginDialog() {
        View content = getLayoutInflater().inflate(R.layout.dialog_nas_login, null, false);
        TextInputLayout emailLayout = content.findViewById(R.id.layoutNasEmail);
        TextInputLayout passwordLayout = content.findViewById(R.id.layoutNasPassword);
        TextInputEditText emailInput = content.findViewById(R.id.inputNasEmail);
        TextInputEditText passwordInput = content.findViewById(R.id.inputNasPassword);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.data_sync_nas_login_title)
                .setView(content)
                .setNegativeButton(R.string.data_sync_nas_cancel, null)
                .setNeutralButton(R.string.data_sync_nas_register_action,
                        (ignored, which) -> showNasRegistrationDialog())
                .setPositiveButton(R.string.data_sync_nas_account_connect, null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view ->
                        beginNasLogin(dialog, emailLayout, passwordLayout,
                                emailInput, passwordInput)));
        dialog.show();
    }

    private void beginNasLogin(AlertDialog dialog, TextInputLayout emailLayout,
                               TextInputLayout passwordLayout,
                               TextInputEditText emailInput,
                               TextInputEditText passwordInput) {
        emailLayout.setError(null);
        passwordLayout.setError(null);
        String email = textOf(emailInput).trim();
        String password = textOf(passwordInput);
        if (email.isEmpty()) {
            emailLayout.setError(getString(R.string.data_sync_nas_error_required));
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.setError(getString(R.string.data_sync_nas_error_email));
            return;
        }
        if (password.isEmpty()) {
            passwordLayout.setError(getString(R.string.data_sync_nas_error_required));
            return;
        }

        passwordInput.setText(null);
        nasAccountBusy = true;
        renderNasAccount();
        nasAccountValue.setText(R.string.data_sync_nas_connecting);
        nasAccountValue.setTextColor(ContextCompat.getColor(this, R.color.textSecondary));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);

        nasExecutor.execute(() -> {
            try {
                NasSession session = NasAuthClient.login(email, password);
                boolean accessPending = false;
                boolean accessRequestFailed = false;
                if (!firebaseConnected && "user".equals(session.user.role)) {
                    try {
                        NasAuthClient.AccessRequest request = submitFamilyAccessRequest(session);
                        accessPending = request != null
                                && "pending".equals(request.status);
                        accessRequestFailed = request == null;
                    } catch (Exception ignored) {
                        accessRequestFailed = true;
                    }
                }
                boolean finalAccessPending = accessPending;
                boolean finalAccessRequestFailed = accessRequestFailed;
                viewModel.saveNasSession(session);
                handler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    activeNasSession = session;
                    nasAccountBusy = false;
                    resetNasBackupStatus();
                    renderNasAccount();
                    if (dialog.isShowing()) dialog.dismiss();
                    loadNasBackupStatus();
                    loadNasPhotoStatus();
                    viewModel.refreshAutomaticNasSchedules();
                    if (finalAccessRequestFailed) {
                        showOperation(R.string.data_sync_nas_access_request_error,
                                R.color.warning);
                    } else if (finalAccessPending) {
                        showOperation(R.string.data_sync_nas_access_request_pending,
                                R.color.warning);
                    }
                });
            } catch (Exception error) {
                handler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    nasAccountBusy = false;
                    renderNasAccount();
                    if (dialog.isShowing()) {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                        passwordLayout.setError(nasLoginError(error));
                    }
                });
            }
        });
    }

    private void showNasRegistrationDialog() {
        View content = getLayoutInflater().inflate(R.layout.dialog_nas_register,
                null, false);
        TextInputLayout inviteLayout = content.findViewById(R.id.layoutNasInviteCode);
        TextInputLayout nameLayout = content.findViewById(R.id.layoutNasRegisterName);
        TextInputLayout emailLayout = content.findViewById(R.id.layoutNasRegisterEmail);
        TextInputLayout passwordLayout = content.findViewById(
                R.id.layoutNasRegisterPassword);
        TextInputLayout confirmLayout = content.findViewById(
                R.id.layoutNasRegisterConfirmPassword);
        TextInputEditText inviteInput = content.findViewById(R.id.inputNasInviteCode);
        TextInputEditText nameInput = content.findViewById(R.id.inputNasRegisterName);
        TextInputEditText emailInput = content.findViewById(R.id.inputNasRegisterEmail);
        TextInputEditText passwordInput = content.findViewById(
                R.id.inputNasRegisterPassword);
        TextInputEditText confirmInput = content.findViewById(
                R.id.inputNasRegisterConfirmPassword);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.data_sync_nas_register_title)
                .setView(content)
                .setNegativeButton(R.string.data_sync_nas_cancel, null)
                .setPositiveButton(R.string.data_sync_nas_register_confirm, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> beginNasRegistration(
                        dialog, inviteLayout, nameLayout, emailLayout, passwordLayout,
                        confirmLayout, inviteInput, nameInput, emailInput,
                        passwordInput, confirmInput)));
        dialog.show();
    }

    private void beginNasRegistration(
            AlertDialog dialog,
            TextInputLayout inviteLayout,
            TextInputLayout nameLayout,
            TextInputLayout emailLayout,
            TextInputLayout passwordLayout,
            TextInputLayout confirmLayout,
            TextInputEditText inviteInput,
            TextInputEditText nameInput,
            TextInputEditText emailInput,
            TextInputEditText passwordInput,
            TextInputEditText confirmInput) {
        inviteLayout.setError(null);
        nameLayout.setError(null);
        emailLayout.setError(null);
        passwordLayout.setError(null);
        confirmLayout.setError(null);
        String inviteCode = textOf(inviteInput).trim();
        String displayName = textOf(nameInput).trim();
        String email = textOf(emailInput).trim();
        String password = textOf(passwordInput);
        String confirmation = textOf(confirmInput);
        if (inviteCode.isEmpty()) {
            inviteLayout.setError(getString(R.string.data_sync_nas_error_required));
            return;
        }
        if (displayName.isEmpty()) {
            nameLayout.setError(getString(R.string.data_sync_nas_error_required));
            return;
        }
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.setError(getString(R.string.data_sync_nas_error_email));
            return;
        }
        if (password.length() < 12) {
            passwordLayout.setError(getString(R.string.data_sync_nas_password_policy));
            return;
        }
        if (!password.equals(confirmation)) {
            confirmLayout.setError(getString(R.string.data_sync_nas_password_mismatch));
            return;
        }
        passwordInput.setText(null);
        confirmInput.setText(null);
        nasAccountBusy = true;
        renderNasAccount();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
        nasExecutor.execute(() -> {
            try {
                NasSession session = NasAuthClient.register(
                        inviteCode, email, displayName, password);
                boolean accessPending = false;
                boolean accessRequestFailed = false;
                if (!firebaseConnected) {
                    try {
                        NasAuthClient.AccessRequest request = submitFamilyAccessRequest(session);
                        accessPending = request != null
                                && "pending".equals(request.status);
                        accessRequestFailed = request == null;
                    } catch (Exception ignored) {
                        accessRequestFailed = true;
                    }
                }
                boolean finalAccessPending = accessPending;
                boolean finalAccessRequestFailed = accessRequestFailed;
                viewModel.saveNasSession(session);
                handler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    activeNasSession = session;
                    nasAccountBusy = false;
                    resetNasBackupStatus();
                    renderNasAccount();
                    if (dialog.isShowing()) dialog.dismiss();
                    showOperation(finalAccessRequestFailed
                                    ? getString(R.string.data_sync_nas_register_access_retry,
                                            session.user.displayName)
                                    : finalAccessPending
                                    ? getString(R.string.data_sync_nas_register_access_pending,
                                            session.user.displayName)
                                    : getString(R.string.data_sync_nas_register_success,
                                            session.user.displayName),
                            finalAccessRequestFailed || finalAccessPending
                                    ? R.color.warning : R.color.online);
                    loadNasBackupStatus();
                    loadNasPhotoStatus();
                });
            } catch (Exception error) {
                handler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    nasAccountBusy = false;
                    renderNasAccount();
                    if (!dialog.isShowing()) return;
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                    String code = error.getMessage();
                    if ("NAS_INVALID_INVITE".equals(code)) {
                        inviteLayout.setError(getString(
                                R.string.data_sync_nas_invite_invalid));
                    } else if ("NAS_WEAK_PASSWORD".equals(code)) {
                        passwordLayout.setError(getString(
                                R.string.data_sync_nas_password_policy));
                    } else if ("NAS_RATE_LIMITED".equals(code)) {
                        inviteLayout.setError(getString(
                                R.string.data_sync_nas_error_rate_limited));
                    } else if ("NAS_TIMEOUT".equals(code)
                            || "NAS_UNAVAILABLE".equals(code)) {
                        inviteLayout.setError(getString(
                                R.string.data_sync_nas_error_connection));
                    } else {
                        inviteLayout.setError(getString(
                                R.string.data_sync_nas_register_error));
                    }
                });
            }
        });
    }

    private void showNasAccountManagement() {
        boolean administrator = activeNasSession != null
                && "admin".equals(activeNasSession.user.role);
        CharSequence[] actions = administrator
                ? new CharSequence[] {
                    getString(R.string.data_sync_nas_create_invite),
                    getString(R.string.data_sync_nas_access_requests),
                    getString(R.string.data_sync_nas_change_password),
                    getString(R.string.data_sync_nas_revoke_others),
                    getString(R.string.data_sync_nas_account_disconnect)
                }
                : new CharSequence[] {
                    getString(R.string.data_sync_nas_request_access),
                    getString(R.string.data_sync_nas_change_password),
                    getString(R.string.data_sync_nas_revoke_others),
                    getString(R.string.data_sync_nas_account_disconnect)
                };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.data_sync_nas_manage_title)
                .setItems(actions, (dialog, which) -> {
                    if (administrator) {
                        if (which == 0) beginNasInviteCreation();
                        else if (which == 1) beginNasAccessRequests();
                        else if (which == 2) showNasChangePasswordDialog();
                        else if (which == 3) confirmNasRevokeOtherSessions();
                        else confirmNasDisconnect();
                    } else {
                        if (which == 0) beginFamilyAccessRequest();
                        else if (which == 1) showNasChangePasswordDialog();
                        else if (which == 2) confirmNasRevokeOtherSessions();
                        else confirmNasDisconnect();
                    }
                })
                .setNegativeButton(R.string.data_sync_nas_cancel, null)
                .show();
    }

    private NasAuthClient.AccessRequest submitFamilyAccessRequest(NasSession session)
            throws Exception {
        String firebaseUid = viewModel.currentFirebaseUserId();
        if (firebaseUid.isEmpty()) return null;
        return NasAuthClient.requestDeviceAccess(
                session.accessToken, AppInfo.DEVICE_ID, firebaseUid);
    }

    private void beginFamilyAccessRequest() {
        NasSession session = activeNasSession;
        if (session == null || "admin".equals(session.user.role)) return;
        nasAccountBusy = true;
        renderNasAccount();
        showOperation(R.string.data_sync_nas_access_request_sending,
                R.color.textSecondary);
        nasExecutor.execute(() -> {
            try {
                NasAuthClient.AccessRequest request = submitFamilyAccessRequest(session);
                if (request == null) throw new IllegalStateException("Firebase UID unavailable");
                handler.post(() -> {
                    if (isFinishing() || isDestroyed()
                            || activeNasSession != session) return;
                    nasAccountBusy = false;
                    renderNasAccount();
                    showOperation("approved".equals(request.status)
                                    ? R.string.data_sync_nas_access_already_approved
                                    : R.string.data_sync_nas_access_request_pending,
                            "approved".equals(request.status)
                                    ? R.color.online : R.color.warning);
                });
            } catch (Exception error) {
                handler.post(() -> {
                    if (isFinishing() || isDestroyed()
                            || activeNasSession != session) return;
                    nasAccountBusy = false;
                    renderNasAccount();
                    if ("NAS_SESSION_EXPIRED".equals(error.getMessage())) {
                        expireNasSession();
                    } else {
                        showOperation(R.string.data_sync_nas_access_request_error,
                                R.color.warning);
                    }
                });
            }
        });
    }

    private void beginNasAccessRequests() {
        NasSession session = activeNasSession;
        if (session == null || !"admin".equals(session.user.role)) return;
        nasAccountBusy = true;
        renderNasAccount();
        showOperation(R.string.data_sync_nas_access_loading, R.color.textSecondary);
        nasExecutor.execute(() -> {
            try {
                List<NasAuthClient.AccessRequest> requests =
                        NasAuthClient.pendingAccessRequests(
                                session.accessToken, AppInfo.DEVICE_ID);
                handler.post(() -> {
                    if (isFinishing() || isDestroyed()
                            || activeNasSession != session) return;
                    nasAccountBusy = false;
                    renderNasAccount();
                    if (requests.isEmpty()) {
                        showOperation(R.string.data_sync_nas_access_none,
                                R.color.online);
                    } else {
                        showNasAccessRequestSelection(session, requests);
                    }
                });
            } catch (Exception error) {
                handler.post(() -> {
                    if (isFinishing() || isDestroyed()
                            || activeNasSession != session) return;
                    nasAccountBusy = false;
                    renderNasAccount();
                    if ("NAS_SESSION_EXPIRED".equals(error.getMessage())) {
                        expireNasSession();
                    } else {
                        showOperation(R.string.data_sync_nas_access_loading_error,
                                R.color.warning);
                    }
                });
            }
        });
    }

    private void showNasAccessRequestSelection(
            NasSession session, List<NasAuthClient.AccessRequest> requests) {
        CharSequence[] labels = new CharSequence[requests.size()];
        for (int index = 0; index < requests.size(); index++) {
            NasAuthClient.AccessRequest request = requests.get(index);
            labels[index] = getString(R.string.data_sync_nas_access_request_item,
                    request.displayName, request.email);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.data_sync_nas_access_requests_title)
                .setItems(labels, (dialog, which) ->
                        confirmNasAccessRequest(session, requests.get(which)))
                .setNegativeButton(R.string.data_sync_nas_cancel, null)
                .show();
    }

    private void confirmNasAccessRequest(NasSession session,
                                         NasAuthClient.AccessRequest request) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.data_sync_nas_access_confirm_title)
                .setMessage(getString(R.string.data_sync_nas_access_confirm_message,
                        request.displayName, request.email))
                .setNegativeButton(R.string.data_sync_nas_cancel, null)
                .setPositiveButton(R.string.data_sync_nas_access_confirm, (dialog, which) ->
                        approveNasAccessRequest(session, request))
                .show();
    }

    private void approveNasAccessRequest(NasSession session,
                                         NasAuthClient.AccessRequest request) {
        if (activeNasSession != session) return;
        nasAccountBusy = true;
        renderNasAccount();
        showOperation(R.string.data_sync_nas_access_approving,
                R.color.textSecondary);
        viewModel.grantDeviceAccess(
                request.firebaseUid, request.userId,
                request.email, request.displayName)
                .addOnSuccessListener(ignored -> nasExecutor.execute(() -> {
                    try {
                        NasAuthClient.approveAccessRequest(
                                session.accessToken, request.id);
                        handler.post(() -> {
                            if (isFinishing() || isDestroyed()
                                    || activeNasSession != session) return;
                            nasAccountBusy = false;
                            renderNasAccount();
                            showOperation(getString(
                                            R.string.data_sync_nas_access_approved,
                                            request.displayName),
                                    R.color.online);
                        });
                    } catch (Exception error) {
                        handler.post(() -> {
                            if (isFinishing() || isDestroyed()
                                    || activeNasSession != session) return;
                            nasAccountBusy = false;
                            renderNasAccount();
                            showOperation(R.string.data_sync_nas_access_finalize_error,
                                    R.color.warning);
                        });
                    }
                }))
                .addOnFailureListener(error -> {
                    if (isFinishing() || isDestroyed()
                            || activeNasSession != session) return;
                    nasAccountBusy = false;
                    renderNasAccount();
                    showOperation(R.string.data_sync_nas_access_approval_error,
                            R.color.warning);
                });
    }

    private void beginNasInviteCreation() {
        NasSession session = activeNasSession;
        if (session == null || !"admin".equals(session.user.role)) return;
        nasAccountBusy = true;
        renderNasAccount();
        showOperation(R.string.data_sync_nas_invite_creating, R.color.textSecondary);
        nasExecutor.execute(() -> {
            try {
                NasAuthClient.Invite invite = NasAuthClient.createInvite(
                        session.accessToken, 24, 1);
                handler.post(() -> {
                    if (isFinishing() || isDestroyed()
                            || activeNasSession != session) return;
                    nasAccountBusy = false;
                    renderNasAccount();
                    showNasInviteDialog(session, invite);
                });
            } catch (Exception error) {
                handler.post(() -> {
                    if (isFinishing() || isDestroyed()
                            || activeNasSession != session) return;
                    nasAccountBusy = false;
                    renderNasAccount();
                    if ("NAS_SESSION_EXPIRED".equals(error.getMessage())) {
                        expireNasSession();
                    } else {
                        showOperation(nasAccountSecurityError(error), R.color.warning);
                    }
                });
            }
        });
    }

    private void showNasInviteDialog(NasSession session,
                                     NasAuthClient.Invite invite) {
        View content = getLayoutInflater().inflate(R.layout.dialog_nas_invite,
                null, false);
        TextView code = content.findViewById(R.id.txtNasInviteCode);
        TextView detail = content.findViewById(R.id.txtNasInviteDetail);
        code.setText(invite.code);
        detail.setText(getString(R.string.data_sync_nas_invite_detail,
                formatDateTime(invite.expiresAt * 1000L)));
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.data_sync_nas_invite_ready_title)
                .setView(content)
                .setNegativeButton(R.string.data_sync_nas_invite_close, null)
                .setNeutralButton(R.string.data_sync_nas_invite_revoke, null)
                .setPositiveButton(R.string.data_sync_nas_invite_share, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view ->
                    shareNasInvite(invite));
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view ->
                    confirmNasInviteRevocation(session, invite, dialog));
        });
        dialog.show();
    }

    private void shareNasInvite(NasAuthClient.Invite invite) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, getString(
                R.string.data_sync_nas_invite_share_text,
                invite.code, formatDateTime(invite.expiresAt * 1000L)));
        startActivity(Intent.createChooser(share,
                getString(R.string.data_sync_nas_invite_share_chooser)));
    }

    private void confirmNasInviteRevocation(
            NasSession session, NasAuthClient.Invite invite, AlertDialog inviteDialog) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.data_sync_nas_invite_revoke_title)
                .setMessage(R.string.data_sync_nas_invite_revoke_message)
                .setNegativeButton(R.string.data_sync_nas_cancel, null)
                .setPositiveButton(R.string.data_sync_nas_invite_revoke,
                        (ignored, which) -> beginNasInviteRevocation(
                                session, invite, inviteDialog))
                .show();
    }

    private void beginNasInviteRevocation(
            NasSession session, NasAuthClient.Invite invite, AlertDialog inviteDialog) {
        if (activeNasSession != session) return;
        nasAccountBusy = true;
        renderNasAccount();
        nasExecutor.execute(() -> {
            try {
                NasAuthClient.revokeInvite(session.accessToken, invite.code);
                handler.post(() -> {
                    if (isFinishing() || isDestroyed()
                            || activeNasSession != session) return;
                    nasAccountBusy = false;
                    renderNasAccount();
                    if (inviteDialog.isShowing()) inviteDialog.dismiss();
                    showOperation(R.string.data_sync_nas_invite_revoked, R.color.online);
                });
            } catch (Exception error) {
                handler.post(() -> {
                    if (isFinishing() || isDestroyed()
                            || activeNasSession != session) return;
                    nasAccountBusy = false;
                    renderNasAccount();
                    if ("NAS_SESSION_EXPIRED".equals(error.getMessage())) {
                        if (inviteDialog.isShowing()) inviteDialog.dismiss();
                        expireNasSession();
                    } else if ("NAS_INVITE_NOT_FOUND".equals(error.getMessage())) {
                        if (inviteDialog.isShowing()) inviteDialog.dismiss();
                        showOperation(R.string.data_sync_nas_invite_already_used,
                                R.color.warning);
                    } else {
                        showOperation(nasAccountSecurityError(error), R.color.warning);
                    }
                });
            }
        });
    }

    private void showNasChangePasswordDialog() {
        View content = getLayoutInflater().inflate(
                R.layout.dialog_nas_change_password, null, false);
        TextInputLayout currentLayout = content.findViewById(
                R.id.layoutNasCurrentPassword);
        TextInputLayout newLayout = content.findViewById(R.id.layoutNasNewPassword);
        TextInputLayout confirmLayout = content.findViewById(
                R.id.layoutNasConfirmPassword);
        TextInputEditText currentInput = content.findViewById(
                R.id.inputNasCurrentPassword);
        TextInputEditText newInput = content.findViewById(R.id.inputNasNewPassword);
        TextInputEditText confirmInput = content.findViewById(
                R.id.inputNasConfirmPassword);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.data_sync_nas_change_password_title)
                .setView(content)
                .setNegativeButton(R.string.data_sync_nas_cancel, null)
                .setPositiveButton(R.string.data_sync_nas_change_password_confirm, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> beginNasPasswordChange(
                        dialog, currentLayout, newLayout, confirmLayout,
                        currentInput, newInput, confirmInput)));
        dialog.show();
    }

    private void beginNasPasswordChange(
            AlertDialog dialog,
            TextInputLayout currentLayout,
            TextInputLayout newLayout,
            TextInputLayout confirmLayout,
            TextInputEditText currentInput,
            TextInputEditText newInput,
            TextInputEditText confirmInput) {
        currentLayout.setError(null);
        newLayout.setError(null);
        confirmLayout.setError(null);
        String currentPassword = textOf(currentInput);
        String newPassword = textOf(newInput);
        String confirmation = textOf(confirmInput);
        if (currentPassword.isEmpty()) {
            currentLayout.setError(getString(R.string.data_sync_nas_error_required));
            return;
        }
        if (newPassword.length() < 12) {
            newLayout.setError(getString(R.string.data_sync_nas_password_policy));
            return;
        }
        if (!newPassword.equals(confirmation)) {
            confirmLayout.setError(getString(R.string.data_sync_nas_password_mismatch));
            return;
        }
        if (currentPassword.equals(newPassword)) {
            newLayout.setError(getString(R.string.data_sync_nas_password_unchanged));
            return;
        }
        NasSession session = activeNasSession;
        if (session == null) {
            dialog.dismiss();
            showNasLoginDialog();
            return;
        }
        currentInput.setText(null);
        newInput.setText(null);
        confirmInput.setText(null);
        nasAccountBusy = true;
        renderNasAccount();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
        nasExecutor.execute(() -> {
            try {
                int revoked = NasAuthClient.changePassword(
                        session.accessToken, currentPassword, newPassword);
                handler.post(() -> {
                    if (isFinishing() || isDestroyed()
                            || activeNasSession != session) return;
                    nasAccountBusy = false;
                    renderNasAccount();
                    if (dialog.isShowing()) dialog.dismiss();
                    showOperation(getString(
                            R.string.data_sync_nas_password_changed, revoked),
                            R.color.online);
                });
            } catch (Exception error) {
                handler.post(() -> {
                    if (isFinishing() || isDestroyed()
                            || activeNasSession != session) return;
                    nasAccountBusy = false;
                    renderNasAccount();
                    String code = error.getMessage();
                    if ("NAS_SESSION_EXPIRED".equals(code)) {
                        if (dialog.isShowing()) dialog.dismiss();
                        expireNasSession();
                        return;
                    }
                    if (dialog.isShowing()) {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                        if ("NAS_CURRENT_PASSWORD_INVALID".equals(code)) {
                            currentLayout.setError(getString(
                                    R.string.data_sync_nas_current_password_invalid));
                        } else if ("NAS_WEAK_PASSWORD".equals(code)) {
                            newLayout.setError(getString(
                                    R.string.data_sync_nas_password_policy));
                        } else if ("NAS_PASSWORD_UNCHANGED".equals(code)) {
                            newLayout.setError(getString(
                                    R.string.data_sync_nas_password_unchanged));
                        } else {
                            newLayout.setError(nasAccountSecurityError(error));
                        }
                    }
                });
            }
        });
    }

    private void confirmNasRevokeOtherSessions() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.data_sync_nas_revoke_others_title)
                .setMessage(R.string.data_sync_nas_revoke_others_message)
                .setNegativeButton(R.string.data_sync_nas_cancel, null)
                .setPositiveButton(R.string.data_sync_nas_revoke_others_confirm,
                        (dialog, which) -> beginNasRevokeOtherSessions())
                .show();
    }

    private void beginNasRevokeOtherSessions() {
        NasSession session = activeNasSession;
        if (session == null) return;
        nasAccountBusy = true;
        renderNasAccount();
        nasExecutor.execute(() -> {
            try {
                int revoked = NasAuthClient.revokeOtherSessions(session.accessToken);
                handler.post(() -> {
                    if (isFinishing() || isDestroyed()
                            || activeNasSession != session) return;
                    nasAccountBusy = false;
                    renderNasAccount();
                    showOperation(revoked == 0
                                    ? getString(R.string.data_sync_nas_no_other_sessions)
                                    : getString(R.string.data_sync_nas_other_sessions_revoked,
                                            revoked),
                            R.color.online);
                });
            } catch (Exception error) {
                handler.post(() -> {
                    if (isFinishing() || isDestroyed()
                            || activeNasSession != session) return;
                    nasAccountBusy = false;
                    renderNasAccount();
                    if ("NAS_SESSION_EXPIRED".equals(error.getMessage())) {
                        expireNasSession();
                    } else {
                        showOperation(nasAccountSecurityError(error), R.color.warning);
                    }
                });
            }
        });
    }

    private void confirmNasDisconnect() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.data_sync_nas_disconnect_title)
                .setMessage(R.string.data_sync_nas_disconnect_message)
                .setNegativeButton(R.string.data_sync_nas_cancel, null)
                .setPositiveButton(R.string.data_sync_nas_account_disconnect,
                        (dialog, which) -> disconnectNasAccount())
                .show();
    }

    private void disconnectNasAccount() {
        NasSession session = activeNasSession;
        activeNasSession = null;
        viewModel.disableAutomaticNasSchedules();
        suppressSwitchCallback = true;
        automaticNasBackupSwitch.setChecked(false);
        automaticNasPhotoBackupSwitch.setChecked(false);
        suppressSwitchCallback = false;
        viewModel.clearNasSession();
        resetNasBackupStatus();
        renderNasAccount();
        if (session == null) return;
        nasExecutor.execute(() -> {
            try {
                NasAuthClient.logout(session.accessToken);
            } catch (Exception ignored) {
                // Local access is removed immediately; the server token expires automatically.
            }
        });
    }

    private void resetNasBackupStatus() {
        nasBackupUpdatedAt = 0L;
        nasBackupZoneCount = 0;
        nasBackupRecordCount = 0;
        nasBackupAppVersion = "";
        nasBackupValidated = false;
        nasBackupInvalid = false;
        nasBackupData = null;
        nasBackups = Collections.emptyList();
        nasRestoreBusy = false;
        nasBackupBusy = false;
        nasBackupStatusLoading = false;
        nasBackupStatusFailed = false;
        nasPhotoInspection = null;
        nasPhotoBusy = false;
        nasPhotoStatusLoading = false;
        nasPhotoStatusFailed = false;
    }

    private void loadNasBackupStatus() {
        NasSession session = activeNasSession;
        if (session == null) return;
        nasBackupStatusLoading = true;
        nasBackupStatusFailed = false;
        renderNasAccount();
        nasExecutor.execute(() -> {
            try {
                List<NasDocumentClient.Document> documents =
                        NasBackupArchive.loadAvailable(session.accessToken, AppInfo.DEVICE_ID);
                List<NasBackupEntry> validBackups = new ArrayList<>();
                boolean invalidBackupFound = false;
                for (NasDocumentClient.Document document : documents) {
                    DataSyncViewModel.BackupValidation validation =
                            viewModel.validateNasBackup(document.data);
                    if (validation.valid) {
                        validBackups.add(new NasBackupEntry(document, validation));
                    } else {
                        invalidBackupFound = true;
                    }
                }
                NasBackupEntry newest = validBackups.isEmpty() ? null : validBackups.get(0);
                boolean hasInvalidBackup = invalidBackupFound;
                handler.post(() -> {
                    if (isFinishing() || isDestroyed() || activeNasSession != session) return;
                    nasBackupStatusLoading = false;
                    nasBackups = validBackups;
                    nasBackupUpdatedAt = newest == null ? 0L : newest.document.updatedAt;
                    nasBackupData = newest == null ? null : newest.document.data;
                    applyNasBackupValidation(newest == null ? null : newest.validation);
                    nasBackupInvalid = newest == null && hasInvalidBackup;
                    renderNasAccount();
                });
            } catch (Exception error) {
                handler.post(() -> {
                    if (isFinishing() || isDestroyed() || activeNasSession != session) return;
                    nasBackupStatusLoading = false;
                    if ("NAS_SESSION_EXPIRED".equals(error.getMessage())) {
                        expireNasSession();
                    } else {
                        nasBackupStatusFailed = true;
                        renderNasAccount();
                    }
                });
            }
        });
    }

    private void loadNasPhotoStatus() {
        NasSession session = activeNasSession;
        if (session == null || nasPhotoStatusLoading || nasPhotoBusy) return;
        nasPhotoStatusLoading = true;
        nasPhotoStatusFailed = false;
        renderNasAccount();
        nasExecutor.execute(() -> {
            try {
                DataSyncViewModel.PhotoInspection inspection =
                        viewModel.inspectNasPhotos(session.accessToken);
                handler.post(() -> {
                    if (isFinishing() || isDestroyed() || activeNasSession != session) return;
                    nasPhotoStatusLoading = false;
                    nasPhotoInspection = inspection;
                    nasPhotoStatusFailed = false;
                    renderNasAccount();
                });
            } catch (Exception error) {
                handler.post(() -> finishNasPhotoFailure(session, error));
            }
        });
    }

    private void beginNasPhotoBackup() {
        NasSession session = activeNasSession;
        if (session == null || nasPhotoBusy || nasPhotoStatusLoading) return;
        nasPhotoBusy = true;
        nasPhotoStatusFailed = false;
        renderNasAccount();
        showOperation(R.string.data_sync_nas_photo_backup_preparing, R.color.textSecondary);
        nasExecutor.execute(() -> {
            try {
                DataSyncViewModel.PhotoBackupResult result =
                        viewModel.backupNasPhotos(session.accessToken);
                DataSyncViewModel.PhotoInspection inspection =
                        viewModel.inspectNasPhotos(session.accessToken);
                viewModel.recordNasPhotoBackupSuccess(
                        result.completedAtEpochMs, result.remoteCount);
                handler.post(() -> {
                    if (isFinishing() || isDestroyed() || activeNasSession != session) return;
                    nasPhotoBusy = false;
                    nasPhotoInspection = inspection;
                    nasPhotoStatusFailed = false;
                    renderNasAccount();
                    String message = getString(R.string.data_sync_nas_photo_backup_success,
                            result.uploadedCount, result.remoteCount);
                    showOperation(message, R.color.online);
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                });
            } catch (Exception error) {
                handler.post(() -> finishNasPhotoFailure(session, error));
            }
        });
    }

    private void confirmNasPhotoRestore() {
        DataSyncViewModel.PhotoInspection inspection = nasPhotoInspection;
        if (inspection == null || inspection.missingOnPhoneCount <= 0 || nasPhotoBusy) {
            Toast.makeText(this, R.string.data_sync_nas_photo_restore_none,
                    Toast.LENGTH_LONG).show();
            return;
        }
        String message = getString(R.string.data_sync_nas_photo_restore_preview,
                inspection.missingOnPhoneCount,
                Formatter.formatShortFileSize(this, inspection.missingBytes));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.data_sync_nas_photo_restore_title)
                .setMessage(message)
                .setNegativeButton(R.string.data_sync_nas_cancel, null)
                .setPositiveButton(R.string.data_sync_nas_photo_restore_confirm,
                        (dialog, which) -> beginNasPhotoRestore())
                .show();
    }

    private void beginNasPhotoRestore() {
        NasSession session = activeNasSession;
        if (session == null || nasPhotoBusy) return;
        nasPhotoBusy = true;
        nasPhotoStatusFailed = false;
        renderNasAccount();
        showOperation(R.string.data_sync_nas_photo_restoring, R.color.textSecondary);
        nasExecutor.execute(() -> {
            try {
                DataSyncViewModel.PhotoRestoreResult result =
                        viewModel.restoreMissingNasPhotos(session.accessToken);
                DataSyncViewModel.PhotoInspection inspection =
                        viewModel.inspectNasPhotos(session.accessToken);
                handler.post(() -> {
                    if (isFinishing() || isDestroyed() || activeNasSession != session) return;
                    nasPhotoBusy = false;
                    nasPhotoInspection = inspection;
                    nasPhotoStatusFailed = false;
                    renderNasAccount();
                    String message = getString(R.string.data_sync_nas_photo_restore_success,
                            result.restoredCount, result.existingCount);
                    showOperation(message, R.color.online);
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                });
            } catch (Exception error) {
                handler.post(() -> finishNasPhotoFailure(session, error));
            }
        });
    }

    private void finishNasPhotoFailure(NasSession session, Exception error) {
        if (isFinishing() || isDestroyed() || activeNasSession != session) return;
        nasPhotoBusy = false;
        nasPhotoStatusLoading = false;
        String code = error == null ? "" : error.getMessage();
        if ("NAS_SESSION_EXPIRED".equals(code)) {
            expireNasSession();
            return;
        }
        nasPhotoStatusFailed = true;
        renderNasAccount();
        int message = "NAS_TIMEOUT".equals(code) || "NAS_UNAVAILABLE".equals(code)
                ? R.string.data_sync_nas_photo_connection_error
                : "NAS_PHOTO_INVALID".equals(code) || "NAS_PHOTO_TOO_LARGE".equals(code)
                ? R.string.data_sync_nas_photo_invalid
                : R.string.data_sync_nas_photo_error;
        showOperation(message, R.color.warning);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void beginNasBackup() {
        NasSession session = activeNasSession;
        if (session == null || nasBackupBusy || nasBackupStatusLoading) return;
        nasBackupBusy = true;
        nasBackupStatusFailed = false;
        renderNasAccount();
        showOperation(R.string.data_sync_nas_backup_preparing, R.color.textSecondary);
        viewModel.createNasBackup()
                .addOnSuccessListener(backup -> uploadNasBackup(session, backup))
                .addOnFailureListener(error -> finishNasBackupFailure(session, error));
    }

    private void uploadNasBackup(NasSession session, org.json.JSONObject backup) {
        nasExecutor.execute(() -> {
            try {
                NasDocumentClient.Document document = NasBackupArchive.save(
                        session.accessToken, AppInfo.DEVICE_ID, backup);
                DataSyncViewModel.BackupValidation validation =
                        viewModel.validateNasBackup(document.data);
                if (!validation.valid) {
                    throw new IllegalStateException("NAS_BACKUP_INVALID");
                }
                handler.post(() -> {
                    if (isFinishing() || isDestroyed() || activeNasSession != session) return;
                    nasBackupBusy = false;
                    nasBackupStatusFailed = false;
                    nasBackupUpdatedAt = document.updatedAt;
                    nasBackupData = document.data;
                    List<NasBackupEntry> updated = new ArrayList<>();
                    updated.add(new NasBackupEntry(document, validation));
                    long createdAt = validation.createdAtEpochMs;
                    for (NasBackupEntry existing : nasBackups) {
                        if (existing.validation.createdAtEpochMs != createdAt
                                && updated.size() < NasBackupArchive.HISTORY_LIMIT) {
                            updated.add(existing);
                        }
                    }
                    nasBackups = updated;
                    applyNasBackupValidation(validation);
                    renderNasAccount();
                    showOperation(getString(R.string.data_sync_nas_backup_success,
                            formatDateTime(document.updatedAt * 1000L)), R.color.online);
                    Toast.makeText(this, R.string.data_sync_nas_backup_success_short,
                            Toast.LENGTH_LONG).show();
                });
            } catch (Exception error) {
                handler.post(() -> finishNasBackupFailure(session, error));
            }
        });
    }

    private void applyNasBackupValidation(
            @Nullable DataSyncViewModel.BackupValidation validation) {
        nasBackupValidated = validation != null && validation.valid;
        nasBackupInvalid = validation != null && !validation.valid;
        if (nasBackupValidated) {
            nasBackupZoneCount = validation.zoneCount;
            nasBackupRecordCount = validation.recordCount;
            nasBackupAppVersion = validation.appVersion;
        } else {
            nasBackupZoneCount = 0;
            nasBackupRecordCount = 0;
            nasBackupAppVersion = "";
        }
    }

    private void finishNasBackupFailure(NasSession session, Exception error) {
        if (isFinishing() || isDestroyed() || activeNasSession != session) return;
        nasBackupBusy = false;
        String code = error == null ? "" : error.getMessage();
        if ("NAS_SESSION_EXPIRED".equals(code)) {
            expireNasSession();
            return;
        }
        renderNasAccount();
        if ("NAS_CONFLICT".equals(code)) {
            showOperation(R.string.data_sync_nas_backup_conflict, R.color.warning);
            loadNasBackupStatus();
        } else if ("NAS_BACKUP_TOO_LARGE".equals(code)
                || "NAS_RESPONSE_TOO_LARGE".equals(code)) {
            showOperation(R.string.data_sync_nas_backup_too_large, R.color.warning);
        } else if ("NAS_TIMEOUT".equals(code) || "NAS_UNAVAILABLE".equals(code)) {
            showOperation(R.string.data_sync_nas_backup_connection_error, R.color.warning);
        } else {
            showOperation(R.string.data_sync_nas_backup_error, R.color.warning);
        }
    }

    private void showNasRestoreSelection() {
        if (nasBackups.isEmpty() || nasRestoreBusy) return;
        if (nasBackups.size() == 1) {
            showNasRestorePreview(nasBackups.get(0).document.data);
            return;
        }
        String[] labels = new String[nasBackups.size()];
        for (int index = 0; index < nasBackups.size(); index++) {
            NasBackupEntry entry = nasBackups.get(index);
            DataSyncViewModel.BackupValidation validation = entry.validation;
            labels[index] = getString(R.string.data_sync_nas_history_item,
                    formatDateTime(validation.createdAtEpochMs),
                    validation.appVersion,
                    validation.zoneCount,
                    validation.recordCount);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.data_sync_nas_history_title)
                .setItems(labels, (dialog, which) ->
                        showNasRestorePreview(nasBackups.get(which).document.data))
                .setNegativeButton(R.string.data_sync_nas_cancel, null)
                .show();
    }

    private void showNasRestorePreview(JSONObject backup) {
        if (backup == null || nasRestoreBusy) return;
        DataSyncViewModel.BackupValidation validation = viewModel.validateNasBackup(backup);
        if (!validation.valid) {
            nasBackupInvalid = true;
            nasBackupValidated = false;
            renderNasAccount();
            showOperation(R.string.data_sync_nas_restore_invalid, R.color.warning);
            return;
        }
        String date = validation.createdAtEpochMs > 0L
                ? formatDateTime(validation.createdAtEpochMs)
                : getString(R.string.data_sync_nas_restore_unknown_date);
        String version = validation.appVersion == null || validation.appVersion.trim().isEmpty()
                ? getString(R.string.data_sync_nas_restore_unknown_version)
                : validation.appVersion;
        String message = getString(R.string.data_sync_nas_restore_preview,
                date, version, validation.zoneCount, validation.recordCount);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.data_sync_nas_restore_title)
                .setMessage(message)
                .setNegativeButton(R.string.data_sync_nas_cancel, null)
                .setPositiveButton(R.string.data_sync_nas_restore_continue,
                        (dialog, which) -> showNasRestoreFinalConfirmation(backup))
                .show();
    }

    private void showNasRestoreFinalConfirmation(JSONObject backup) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.data_sync_nas_restore_final_title)
                .setMessage(R.string.data_sync_nas_restore_final_message)
                .setNegativeButton(R.string.data_sync_nas_cancel, null)
                .setPositiveButton(R.string.data_sync_nas_restore_confirm,
                        (dialog, which) -> beginNasRestore(backup))
                .show();
    }

    private void beginNasRestore(JSONObject backup) {
        DataSyncViewModel.BackupValidation validation = viewModel.validateNasBackup(backup);
        if (!validation.valid || activeNasSession == null) {
            showOperation(R.string.data_sync_nas_restore_invalid, R.color.warning);
            return;
        }
        nasRestoreBusy = true;
        renderNasAccount();
        showOperation(R.string.data_sync_nas_restoring, R.color.textSecondary);
        backupViewModel.restore(backup, "NAS · " + NAS_BACKUP_KEY)
                .addOnSuccessListener(unused -> {
                    if (isFinishing() || isDestroyed()) return;
                    nasRestoreBusy = false;
                    renderNasAccount();
                    showOperation(R.string.data_sync_nas_restore_success, R.color.online);
                    Toast.makeText(this, R.string.data_sync_nas_restore_success_short,
                            Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(error -> {
                    if (isFinishing() || isDestroyed()) return;
                    nasRestoreBusy = false;
                    renderNasAccount();
                    String message = getString(R.string.data_sync_nas_restore_error,
                            safeMessage(error == null ? null : error.getMessage()));
                    showOperation(message, R.color.warning);
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                });
    }

    private void expireNasSession() {
        activeNasSession = null;
        viewModel.clearNasSession();
        resetNasBackupStatus();
        renderNasAccount();
        showOperation(R.string.data_sync_nas_session_expired, R.color.warning);
    }

    private String nasLoginError(Exception error) {
        if (error instanceof IllegalStateException) {
            return getString(R.string.data_sync_nas_error_storage);
        }
        String code = error == null ? "" : error.getMessage();
        if ("NAS_INVALID_CREDENTIALS".equals(code)) {
            return getString(R.string.data_sync_nas_error_credentials);
        }
        if ("NAS_RATE_LIMITED".equals(code)) {
            return getString(R.string.data_sync_nas_error_rate_limited);
        }
        if ("NAS_TIMEOUT".equals(code) || "NAS_UNAVAILABLE".equals(code)) {
            return getString(R.string.data_sync_nas_error_connection);
        }
        return getString(R.string.data_sync_nas_error_generic);
    }

    private String nasAccountSecurityError(Exception error) {
        String code = error == null ? "" : error.getMessage();
        if ("NAS_RATE_LIMITED".equals(code)) {
            return getString(R.string.data_sync_nas_error_rate_limited);
        }
        if ("NAS_TIMEOUT".equals(code) || "NAS_UNAVAILABLE".equals(code)) {
            return getString(R.string.data_sync_nas_error_connection);
        }
        return getString(R.string.data_sync_nas_account_security_error);
    }

    private String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
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
        nasExecutor.shutdownNow();
        super.onDestroy();
    }

    private static final class NasBackupEntry {
        final NasDocumentClient.Document document;
        final DataSyncViewModel.BackupValidation validation;

        NasBackupEntry(NasDocumentClient.Document document,
                       DataSyncViewModel.BackupValidation validation) {
            this.document = document;
            this.validation = validation;
        }
    }
}
