package com.alidogukan.avora.activities;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.config.AppInfo;
import com.alidogukan.avora.ui.PrimaryBottomNavigation;
import com.alidogukan.avora.viewmodels.BackupViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Creates and safely restores portable AVORA backup files. */
public class BackupActivity extends AppCompatActivity {
    private ActivityResultLauncher<String> createDocumentLauncher;
    private ActivityResultLauncher<String[]> openDocumentLauncher;
    private BackupViewModel viewModel;
    private MaterialButton createButton;
    private MaterialButton restoreButton;
    private LinearProgressIndicator progress;
    private TextView operationStatus;
    private TextView lastBackupValue;
    private TextView lastRestoreValue;

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_backup);
        viewModel = new ViewModelProvider(this).get(BackupViewModel.class);
        registerFileLaunchers();
        applyWindowInsets();
        bindViews();
        configureToolbar();
        configureActions();
        PrimaryBottomNavigation.bind(this, PrimaryBottomNavigation.SETTINGS);
        renderStoredState();
    }

    private void registerFileLaunchers() {
        createDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/json"),
                this::writeBackupFile);
        openDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), this::readBackupFile);
    }

    private void bindViews() {
        createButton = findViewById(R.id.btnCreateBackup);
        restoreButton = findViewById(R.id.btnRestoreBackup);
        progress = findViewById(R.id.progressBackup);
        operationStatus = findViewById(R.id.txtBackupOperationStatus);
        LinearLayout values = findViewById(R.id.layoutBackupValues);
        lastBackupValue = addValueRow(values, R.string.backup_last_created_label, false);
        lastRestoreValue = addValueRow(values, R.string.backup_last_restored_label, true);
        addValueRow(values, R.string.backup_device_label, true).setText(AppInfo.DEVICE_ID);
        addValueRow(values, R.string.backup_format_label, true)
                .setText(R.string.backup_format_value);
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
        ((TextView) findViewById(R.id.txtSettingsToolbarTitle)).setText(R.string.backup_title);
        findViewById(R.id.btnSettingsToolbarBack).setOnClickListener(view -> finish());
        findViewById(R.id.btnSettingsToolbarAction).setVisibility(View.GONE);
    }

    private void configureActions() {
        createButton.setOnClickListener(view -> prepareBackup());
        restoreButton.setOnClickListener(view -> openDocumentLauncher.launch(
                new String[]{"application/json", "text/plain", "application/octet-stream"}));
    }

    private void prepareBackup() {
        setBusy(true);
        showOperation(R.string.backup_preparing, R.color.textSecondary);
        viewModel.prepareBackup()
                .addOnSuccessListener(backup -> {
                    setBusy(false);
                    createDocumentLauncher.launch(viewModel.pendingFileName());
                })
                .addOnFailureListener(error -> {
                    setBusy(false);
                    showError(getString(R.string.backup_create_error, safeMessage(error)));
                });
    }

    private void writeBackupFile(@Nullable Uri uri) {
        if (uri == null || !viewModel.hasPendingBackup()) {
            viewModel.clearPendingBackup();
            showOperation(R.string.backup_file_cancelled, R.color.textSecondary);
            return;
        }
        setBusy(true);
        showOperation(R.string.backup_writing, R.color.textSecondary);
        viewModel.writePending(uri, result -> runOnUiThread(() -> {
            if (result.successful) {
                onBackupWritten(result.displayName);
            } else {
                    setBusy(false);
                    showError(getString(R.string.backup_write_error,
                            safeMessage(result.error)));
            }
        }));
    }

    private void onBackupWritten(String displayName) {
        setBusy(false);
        renderStoredState();
        showOperation(getString(R.string.backup_create_success, displayName), R.color.online);
        Toast.makeText(this, R.string.backup_create_success_short, Toast.LENGTH_LONG).show();
    }

    private void readBackupFile(@Nullable Uri uri) {
        if (uri == null) {
            showOperation(R.string.backup_file_cancelled, R.color.textSecondary);
            return;
        }
        setBusy(true);
        showOperation(R.string.backup_reading, R.color.textSecondary);
        viewModel.read(uri, result -> runOnUiThread(() -> {
            if (result.successful) {
                validateAndConfirm(result.backup, result.displayName);
            } else {
                    setBusy(false);
                    showError(getString(R.string.backup_read_error,
                            safeMessage(result.error)));
            }
        }));
    }

    private void validateAndConfirm(JSONObject backup, String displayName) {
        setBusy(false);
        BackupViewModel.BackupValidation result = viewModel.validate(backup);
        if (!result.valid) {
            showError(result.message);
            return;
        }
        String date = result.createdAtEpochMs > 0L
                ? formatDateTime(result.createdAtEpochMs)
                : getString(R.string.backup_unknown_date);
        String version = result.appVersion.isBlank()
                ? getString(R.string.backup_unknown_version) : result.appVersion;
        String message = getString(R.string.backup_restore_confirmation,
                displayName, date, version, result.zoneCount, result.recordCount);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.backup_restore_dialog_title)
                .setMessage(message)
                .setNegativeButton(R.string.backup_cancel, null)
                .setPositiveButton(R.string.backup_restore_confirm,
                        (dialog, which) -> restoreBackup(backup, displayName))
                .show();
    }

    private void restoreBackup(JSONObject backup, String displayName) {
        setBusy(true);
        showOperation(R.string.backup_restoring, R.color.textSecondary);
        viewModel.restore(backup, displayName)
                .addOnSuccessListener(unused -> {
                    setBusy(false);
                    renderStoredState();
                    showOperation(R.string.backup_restore_success, R.color.online);
                    Toast.makeText(this, R.string.backup_restore_success_short,
                            Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(error -> {
                    setBusy(false);
                    showError(getString(R.string.backup_restore_error, safeMessage(error)));
                });
    }

    private void renderStoredState() {
        BackupViewModel.StoredState state = viewModel.storedState();
        lastBackupValue.setText(formatStoredOperation(state.backupTime, state.backupName));
        lastRestoreValue.setText(formatStoredOperation(state.restoreTime, state.restoreName));
    }

    private String formatStoredOperation(long epoch, String name) {
        if (epoch <= 0L) {
            return getString(R.string.backup_never);
        }
        return name == null || name.isBlank() ? formatDateTime(epoch)
                : getString(R.string.backup_operation_value, formatDateTime(epoch), name);
    }

    private void setBusy(boolean busy) {
        createButton.setEnabled(!busy);
        restoreButton.setEnabled(!busy);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        showOperation(message, R.color.error);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void showOperation(int messageRes, int colorRes) {
        showOperation(getString(messageRes), colorRes);
    }

    private void showOperation(String message, int colorRes) {
        operationStatus.setVisibility(View.VISIBLE);
        operationStatus.setText(message);
        operationStatus.setTextColor(ContextCompat.getColor(this, colorRes));
    }

    private String formatDateTime(long epochMillis) {
        return new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.forLanguageTag("tr-TR"))
                .format(new Date(epochMillis));
    }

    private String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.isBlank()
                ? getString(R.string.backup_unknown_error) : message.trim();
    }

    private int dividerHeightPx() {
        return Math.round(getResources().getDisplayMetrics().density);
    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.backupRoot),
                (view, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });
    }

}
