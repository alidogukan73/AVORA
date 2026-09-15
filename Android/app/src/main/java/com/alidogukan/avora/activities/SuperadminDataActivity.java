package com.alidogukan.avora.activities;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.superadmin.SuperadminDataRepository.AuditItem;
import com.alidogukan.avora.superadmin.SuperadminDataRepository.CommandResult;
import com.alidogukan.avora.superadmin.SuperadminDataRepository.PageCursor;
import com.alidogukan.avora.superadmin.SuperadminDataRepository.RecordItem;
import com.alidogukan.avora.ui.PrimaryBottomNavigation;
import com.alidogukan.avora.viewmodels.SuperadminDataViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Protected owner console for correcting backed-up AVORA records. */
public final class SuperadminDataActivity extends EdgeToEdgeActivity {
    private static final String[] CATEGORY_IDS = {
            "zones", "seasons", "seedling_batches", "journal_events", "photos",
            "watering", "fertilizer", "notifications", "feedback"
    };

    private SuperadminDataViewModel viewModel;
    private final List<RecordItem> records = new ArrayList<>();
    private MaterialAutoCompleteTextView categoryDropdown;
    private TextInputEditText searchInput;
    private LinearLayout recordsContainer;
    private TextView status;
    private LinearProgressIndicator progress;
    private MaterialButton refreshButton;
    private MaterialButton auditButton;
    private MaterialButton accountsButton;
    private MaterialButton undoButton;
    private MaterialButton loadMoreButton;
    private String[] categoryLabels;
    private String currentCategory = CATEGORY_IDS[0];
    private String lastBackupId = "";
    private PageCursor nextCursor;
    private boolean accessGranted;

    private final ActivityResultLauncher<Intent> credentialLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != Activity.RESULT_OK) {
                            finish();
                            return;
                        }
                        verifyOwnerAndLoad();
                    });

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_superadmin_data);
        viewModel = new ViewModelProvider(this).get(SuperadminDataViewModel.class);
        bindViews();
        configureToolbar();
        configureCategories();
        bindActions();
        PrimaryBottomNavigation.bind(this, PrimaryBottomNavigation.SETTINGS);
        requestDeviceVerification();
    }

    private void bindViews() {
        categoryDropdown = findViewById(R.id.dropdownSuperadminCategory);
        searchInput = findViewById(R.id.inputSuperadminSearch);
        recordsContainer = findViewById(R.id.layoutSuperadminRecords);
        status = findViewById(R.id.txtSuperadminStatus);
        progress = findViewById(R.id.progressSuperadmin);
        refreshButton = findViewById(R.id.btnSuperadminRefresh);
        auditButton = findViewById(R.id.btnSuperadminAudit);
        accountsButton = findViewById(R.id.btnSuperadminAccounts);
        undoButton = findViewById(R.id.btnSuperadminUndo);
        loadMoreButton = findViewById(R.id.btnSuperadminLoadMore);
    }

    private void configureToolbar() {
        ((TextView) findViewById(R.id.txtSettingsToolbarTitle))
                .setText(R.string.settings_superadmin_title);
        findViewById(R.id.btnSettingsToolbarBack).setOnClickListener(view -> finish());
        findViewById(R.id.btnSettingsToolbarAction).setVisibility(View.GONE);
    }

    private void configureCategories() {
        categoryLabels = getResources().getStringArray(R.array.superadmin_category_labels);
        categoryDropdown.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, categoryLabels));
        categoryDropdown.setText(categoryLabels[0], false);
        categoryDropdown.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= CATEGORY_IDS.length) return;
            currentCategory = CATEGORY_IDS[position];
            loadRecords();
        });
    }

    private void bindActions() {
        refreshButton.setOnClickListener(view -> loadRecords());
        auditButton.setOnClickListener(view -> showAudit());
        accountsButton.setOnClickListener(view ->
                startActivity(new Intent(this, NasSecurityActivity.class)));
        undoButton.setOnClickListener(view -> confirmUndo());
        loadMoreButton.setOnClickListener(view -> loadMoreRecords());
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start,
                                                    int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start,
                                                int before, int count) {
                renderRecords();
            }
            @Override public void afterTextChanged(Editable value) { }
        });
        setControlsEnabled(false);
    }

    @SuppressWarnings("deprecation")
    private void requestDeviceVerification() {
        KeyguardManager manager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (manager == null || !manager.isDeviceSecure()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.superadmin_unlock_title)
                    .setMessage(R.string.superadmin_device_lock_required)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                    .setOnCancelListener(dialog -> finish())
                    .show();
            return;
        }
        Intent intent = manager.createConfirmDeviceCredentialIntent(
                getString(R.string.superadmin_unlock_title),
                getString(R.string.superadmin_unlock_message));
        if (intent == null) {
            finish();
            return;
        }
        credentialLauncher.launch(intent);
    }

    private void verifyOwnerAndLoad() {
        setBusy(true, getString(R.string.superadmin_command_waiting));
        viewModel.isCurrentUserOwner().addOnCompleteListener(task -> {
            if (isFinishing() || isDestroyed()) return;
            if (!task.isSuccessful() || !Boolean.TRUE.equals(task.getResult())) {
                setBusy(false, getString(R.string.superadmin_owner_only));
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.superadmin_unlock_title)
                        .setMessage(R.string.superadmin_owner_only)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                        .setOnCancelListener(dialog -> finish())
                        .show();
                return;
            }
            accessGranted = true;
            setControlsEnabled(true);
            loadRecords();
        });
    }

    private void loadRecords() {
        if (!accessGranted) return;
        nextCursor = null;
        records.clear();
        renderRecords();
        setBusy(true, getString(R.string.superadmin_loading));
        viewModel.loadRecordPage(currentCategory, null).addOnCompleteListener(task -> {
            if (isFinishing() || isDestroyed()) return;
            if (!task.isSuccessful() || task.getResult() == null) {
                setBusy(false, getString(R.string.superadmin_load_failed,
                        errorMessage(task.getException())));
                return;
            }
            records.addAll(task.getResult().records);
            nextCursor = task.getResult().nextCursor;
            setBusy(false, getString(R.string.superadmin_count_loaded, records.size()));
            renderRecords();
        });
    }

    private void loadMoreRecords() {
        if (!accessGranted || nextCursor == null) return;
        PageCursor requestedCursor = nextCursor;
        setBusy(true, getString(R.string.superadmin_loading_more));
        viewModel.loadRecordPage(currentCategory, requestedCursor)
                .addOnCompleteListener(task -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (!task.isSuccessful() || task.getResult() == null) {
                        setBusy(false, getString(
                                R.string.superadmin_count_loaded, records.size()));
                        showError(task.getException());
                        return;
                    }
                    for (RecordItem candidate : task.getResult().records) {
                        boolean exists = false;
                        for (RecordItem existing : records) {
                            if (existing.id.equals(candidate.id)) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) records.add(candidate);
                    }
                    nextCursor = task.getResult().nextCursor;
                    setBusy(false, getString(
                            R.string.superadmin_count_loaded, records.size()));
                    renderRecords();
                });
    }

    private void renderRecords() {
        if (recordsContainer == null) return;
        recordsContainer.removeAllViews();
        String query = valueOf(searchInput).toLowerCase(Locale.ROOT);
        int shown = 0;
        for (RecordItem item : records) {
            String searchable = (item.title + " " + item.subtitle + " " + item.id)
                    .toLowerCase(Locale.ROOT);
            if (!query.isBlank() && !searchable.contains(query)) continue;
            View row = LayoutInflater.from(this).inflate(
                    R.layout.item_superadmin_record, recordsContainer, false);
            ((TextView) row.findViewById(R.id.txtSuperadminRecordTitle)).setText(item.title);
            TextView subtitle = row.findViewById(R.id.txtSuperadminRecordSubtitle);
            subtitle.setText(item.subtitle);
            subtitle.setVisibility(item.subtitle.isBlank() ? View.GONE : View.VISIBLE);
            ((TextView) row.findViewById(R.id.txtSuperadminRecordId)).setText(item.id);
            row.findViewById(R.id.btnSuperadminEdit).setOnClickListener(
                    view -> openEditor(item));
            row.findViewById(R.id.btnSuperadminDelete).setOnClickListener(
                    view -> previewDelete(item));
            recordsContainer.addView(row);
            shown++;
        }
        if (shown == 0) {
            TextView empty = new TextView(this);
            empty.setText(R.string.superadmin_empty);
            empty.setTextColor(getColor(R.color.textSecondary));
            empty.setTextSize(14);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(8, 36, 8, 36);
            recordsContainer.addView(empty);
        }
        loadMoreButton.setVisibility(nextCursor == null ? View.GONE : View.VISIBLE);
        loadMoreButton.setEnabled(accessGranted && progress.getVisibility() != View.VISIBLE
                && nextCursor != null);
    }

    private void openEditor(RecordItem item) {
        setBusy(true, getString(R.string.superadmin_loading));
        viewModel.loadRecordJson(currentCategory, item.id).addOnCompleteListener(task -> {
            if (isFinishing() || isDestroyed()) return;
            setBusy(false, getString(R.string.superadmin_count, records.size()));
            if (!task.isSuccessful() || task.getResult() == null) {
                showError(task.getException());
                return;
            }
            View content = LayoutInflater.from(this).inflate(
                    R.layout.dialog_superadmin_edit, null, false);
            TextInputEditText input = content.findViewById(R.id.inputSuperadminJson);
            String expectedRecordJson = task.getResult();
            input.setText(expectedRecordJson);
            AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.superadmin_edit_title) + " · " + item.title)
                    .setView(content)
                    .setNegativeButton(R.string.superadmin_cancel, null)
                    .setPositiveButton(R.string.superadmin_save, null)
                    .create();
            dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(view -> {
                        String json = valueOf(input);
                        try {
                            Object parsed = new JSONTokener(json).nextValue();
                            if (!(parsed instanceof JSONObject)) throw new IllegalArgumentException();
                            json = ((JSONObject) parsed).toString();
                        } catch (Exception error) {
                            input.setError(getString(R.string.superadmin_invalid_json));
                            return;
                        }
                        dialog.dismiss();
                        confirmUpdate(item, json, expectedRecordJson);
                    }));
            dialog.show();
        });
    }

    private void confirmUpdate(
            RecordItem item, String json, String expectedRecordJson
    ) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.superadmin_edit_confirm_title)
                .setMessage(R.string.superadmin_edit_confirm_message)
                .setNegativeButton(R.string.superadmin_cancel, null)
                .setPositiveButton(R.string.superadmin_save, (dialog, which) -> {
                    setBusy(true, getString(R.string.superadmin_command_waiting));
                    viewModel.update(
                                    currentCategory, item.id, json, expectedRecordJson)
                            .addOnCompleteListener(task -> handleMutation(
                                    task, R.string.superadmin_update_success));
                })
                .show();
    }

    private void previewDelete(RecordItem item) {
        setBusy(true, getString(R.string.superadmin_command_waiting));
        viewModel.previewDelete(currentCategory, item.id).addOnCompleteListener(task -> {
            if (isFinishing() || isDestroyed()) return;
            setBusy(false, getString(R.string.superadmin_count, records.size()));
            if (!task.isSuccessful() || task.getResult() == null) {
                showError(task.getException());
                return;
            }
            showDeleteConfirmation(item, task.getResult());
        });
    }

    private void showDeleteConfirmation(RecordItem item, CommandResult preview) {
        if (preview.previewToken == null || preview.previewToken.trim().isEmpty()) {
            showError(new IllegalStateException(
                    "Silme önizlemesi doğrulanamadı. Listeyi yenileyip tekrar deneyin."
            ));
            return;
        }
        View content = LayoutInflater.from(this).inflate(
                R.layout.dialog_superadmin_confirm, null, false);
        TextView summary = content.findViewById(R.id.txtSuperadminConfirmSummary);
        TextInputLayout phraseLayout = content.findViewById(
                R.id.layoutSuperadminConfirmPhrase);
        TextInputEditText phrase = content.findViewById(R.id.inputSuperadminConfirmPhrase);
        StringBuilder groups = new StringBuilder();
        for (Map.Entry<String, Integer> entry : preview.previewGroups.entrySet()) {
            groups.append(getString(R.string.superadmin_preview_group,
                    friendlyGroup(entry.getKey()), entry.getValue()));
        }
        summary.setText(getString(R.string.superadmin_preview_summary,
                item.title, preview.previewCount, groups.toString()));
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.superadmin_preview_title)
                .setView(content)
                .setNegativeButton(R.string.superadmin_cancel, null)
                .setPositiveButton(R.string.superadmin_confirm_delete, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String expected = getString(R.string.superadmin_confirm_word);
                    if (!expected.equalsIgnoreCase(valueOf(phrase))) {
                        phraseLayout.setError(getString(R.string.superadmin_phrase_invalid));
                        return;
                    }
                    dialog.dismiss();
                    setBusy(true, getString(R.string.superadmin_command_waiting));
                    viewModel.delete(currentCategory, item.id, preview.previewToken)
                            .addOnCompleteListener(task -> handleMutation(
                                    task, R.string.superadmin_delete_success));
                }));
        dialog.show();
    }

    private void handleMutation(
            com.google.android.gms.tasks.Task<CommandResult> task,
            int successMessage
    ) {
        if (isFinishing() || isDestroyed()) return;
        if (!task.isSuccessful() || task.getResult() == null) {
            setBusy(false, getString(R.string.superadmin_count, records.size()));
            showError(task.getException());
            return;
        }
        lastBackupId = task.getResult().backupId;
        undoButton.setEnabled(!lastBackupId.isBlank());
        Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show();
        loadRecords();
    }

    private void confirmUndo() {
        if (lastBackupId.isBlank()) return;
        confirmRestore(lastBackupId);
    }

    private void confirmRestore(String backupId) {
        if (backupId == null || backupId.isBlank()) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.superadmin_undo_title)
                .setMessage(R.string.superadmin_undo_message)
                .setNegativeButton(R.string.superadmin_cancel, null)
                .setPositiveButton(R.string.superadmin_restore, (dialog, which) -> {
                    setBusy(true, getString(R.string.superadmin_command_waiting));
                    viewModel.restore(backupId).addOnCompleteListener(task -> {
                        if (isFinishing() || isDestroyed()) return;
                        if (!task.isSuccessful()) {
                            setBusy(false, getString(R.string.superadmin_count, records.size()));
                            showError(task.getException());
                            return;
                        }
                        lastBackupId = "";
                        undoButton.setEnabled(false);
                        Toast.makeText(this, R.string.superadmin_undo_success,
                                Toast.LENGTH_LONG).show();
                        loadRecords();
                    });
                })
                .show();
    }

    private void showAudit() {
        if (!accessGranted) return;
        setBusy(true, getString(R.string.superadmin_loading));
        viewModel.loadAudit().addOnCompleteListener(task -> {
            if (isFinishing() || isDestroyed()) return;
            setBusy(false, getString(R.string.superadmin_count, records.size()));
            if (!task.isSuccessful() || task.getResult() == null) {
                showError(task.getException());
                return;
            }
            List<AuditItem> auditItems = task.getResult();
            if (auditItems.isEmpty()) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.superadmin_audit_title)
                        .setMessage(R.string.superadmin_audit_empty)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                return;
            }
            CharSequence[] rows = new CharSequence[auditItems.size()];
            for (int index = 0; index < auditItems.size(); index++) {
                AuditItem item = auditItems.get(index);
                rows[index] = getString(R.string.superadmin_audit_row,
                        friendlyOperation(item.operation), friendlyGroup(item.category),
                        item.recordId, item.affectedCount,
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                .format(new Date(item.epoch * 1000L)));
            }
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.superadmin_audit_title)
                    .setMessage(R.string.superadmin_audit_restore_hint)
                    .setItems(rows, (dialog, which) ->
                            confirmRestore(auditItems.get(which).backupId))
                    .setNegativeButton(R.string.superadmin_cancel, null)
                    .show();
        });
    }

    private String friendlyGroup(String value) {
        for (int index = 0; index < CATEGORY_IDS.length; index++) {
            if (CATEGORY_IDS[index].equals(value)) return categoryLabels[index];
        }
        if ("events".equals(value)) return categoryLabels[3];
        if ("photo_metadata".equals(value)) return categoryLabels[4];
        if ("season_outcomes".equals(value)) return "Sezon sonuçları";
        if ("seedling".equals(value)) return categoryLabels[2];
        return value.replace('_', ' ');
    }

    private String friendlyOperation(String value) {
        if ("delete".equals(value)) return getString(R.string.superadmin_delete);
        if ("update".equals(value)) return getString(R.string.superadmin_edit);
        if ("restore".equals(value)) return getString(R.string.superadmin_restore);
        return value;
    }

    private void setBusy(boolean busy, String message) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        status.setText(message);
        setControlsEnabled(accessGranted && !busy);
    }

    private void setControlsEnabled(boolean enabled) {
        categoryDropdown.setEnabled(enabled);
        searchInput.setEnabled(enabled);
        refreshButton.setEnabled(enabled);
        auditButton.setEnabled(enabled);
        accountsButton.setEnabled(enabled);
        undoButton.setEnabled(enabled && !lastBackupId.isBlank());
        loadMoreButton.setEnabled(enabled && nextCursor != null);
    }

    private void showError(Exception error) {
        String message = getString(R.string.superadmin_operation_failed,
                errorMessage(error));
        status.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private String errorMessage(Exception error) {
        return error == null || error.getMessage() == null || error.getMessage().isBlank()
                ? getString(R.string.superadmin_unknown_error)
                : error.getMessage();
    }

    private static String valueOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }
}
