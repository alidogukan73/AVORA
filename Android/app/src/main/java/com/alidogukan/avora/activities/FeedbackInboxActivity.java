package com.alidogukan.avora.activities;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alidogukan.avora.R;
import com.alidogukan.avora.adapters.FeedbackInboxAdapter;
import com.alidogukan.avora.feedback.FeedbackInboxRepository.Message;
import com.alidogukan.avora.ui.PrimaryBottomNavigation;
import com.alidogukan.avora.viewmodels.FeedbackInboxViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Professional owner-only inbox; technical JSON remains in Superadmin. */
public final class FeedbackInboxActivity extends EdgeToEdgeActivity {
    private FeedbackInboxViewModel viewModel;
    private FeedbackInboxAdapter adapter;
    private final List<Message> all = new ArrayList<>();
    private String filter = "all";
    private LinearProgressIndicator progress;
    private TextView status;
    private MaterialButton allButton, newButton, readButton, resolvedButton;

    private final ActivityResultLauncher<Intent> credentialLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != Activity.RESULT_OK) { finish(); return; }
                verifyOwner();
            });

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_feedback_inbox);
        viewModel = new ViewModelProvider(this).get(FeedbackInboxViewModel.class);
        progress = findViewById(R.id.progressFeedbackInbox);
        status = findViewById(R.id.txtFeedbackInboxState);
        allButton = findViewById(R.id.btnFeedbackFilterAll);
        newButton = findViewById(R.id.btnFeedbackFilterNew);
        readButton = findViewById(R.id.btnFeedbackFilterRead);
        resolvedButton = findViewById(R.id.btnFeedbackFilterResolved);
        ((TextView) findViewById(R.id.txtSettingsToolbarTitle))
                .setText(R.string.settings_feedback_inbox_title);
        findViewById(R.id.btnSettingsToolbarBack).setOnClickListener(view -> finish());
        findViewById(R.id.btnSettingsToolbarAction).setVisibility(View.GONE);
        RecyclerView list = findViewById(R.id.recyclerFeedbackInbox);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FeedbackInboxAdapter(new FeedbackInboxAdapter.Listener() {
            @Override public void onOpen(Message message) { showMessage(message); }
            @Override public void onDelete(Message message) { confirmDelete(message); }
        });
        list.setAdapter(adapter);
        bindFilter(allButton, "all");
        bindFilter(newButton, "new");
        bindFilter(readButton, "read");
        bindFilter(resolvedButton, "resolved");
        findViewById(R.id.btnFeedbackInboxRefresh).setOnClickListener(view -> load());
        PrimaryBottomNavigation.bind(this, PrimaryBottomNavigation.SETTINGS);
        requestDeviceVerification();
    }

    private void bindFilter(MaterialButton button, String value) {
        button.setOnClickListener(view -> { filter = value; render(); });
    }

    @SuppressWarnings("deprecation")
    private void requestDeviceVerification() {
        KeyguardManager manager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (manager == null || !manager.isDeviceSecure()) {
            new MaterialAlertDialogBuilder(this).setTitle(R.string.superadmin_unlock_title)
                    .setMessage(R.string.superadmin_device_lock_required)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                    .setOnCancelListener(dialog -> finish()).show();
            return;
        }
        Intent intent = manager.createConfirmDeviceCredentialIntent(
                getString(R.string.feedback_inbox_unlock_title),
                getString(R.string.feedback_inbox_unlock_message));
        if (intent == null) { finish(); return; }
        credentialLauncher.launch(intent);
    }

    private void verifyOwner() {
        setBusy(true, R.string.feedback_inbox_verifying);
        viewModel.isCurrentUserOwner().addOnCompleteListener(task -> {
            if (isFinishing() || isDestroyed()) return;
            if (!task.isSuccessful() || !Boolean.TRUE.equals(task.getResult())) {
                new MaterialAlertDialogBuilder(this).setTitle(R.string.feedback_inbox_unlock_title)
                        .setMessage(R.string.superadmin_owner_only)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                        .show();
                return;
            }
            load();
        });
    }

    private void load() {
        setBusy(true, R.string.feedback_inbox_loading);
        viewModel.load().addOnCompleteListener(task -> {
            if (isFinishing() || isDestroyed()) return;
            if (!task.isSuccessful() || task.getResult() == null) {
                setBusy(false, R.string.feedback_inbox_load_failed);
                return;
            }
            all.clear();
            all.addAll(task.getResult());
            setBusy(false, 0);
            render();
        });
    }

    private void render() {
        List<Message> visible = new ArrayList<>();
        int unread = 0;
        for (Message message : all) {
            if ("new".equals(message.status)) unread++;
            if (message.matches(filter)) visible.add(message);
        }
        adapter.submit(visible);
        status.setText(getString(R.string.feedback_inbox_summary, all.size(), unread));
        allButton.setChecked("all".equals(filter));
        newButton.setChecked("new".equals(filter));
        readButton.setChecked("read".equals(filter));
        resolvedButton.setChecked("resolved".equals(filter));
    }

    private void showMessage(Message message) {
        String date = message.createdAt <= 0 ? "—" : DateFormat
                .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(message.createdAt * 1000L));
        String body = getString(R.string.feedback_inbox_detail_format,
                message.area, date, typeLabel(message.type),
                message.contactEmail.isBlank() ? "—" : message.contactEmail,
                message.description);
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(message.subject).setMessage(body)
                .setNegativeButton(R.string.superadmin_cancel, null)
                .setNeutralButton("resolved".equals(message.status)
                        ? R.string.feedback_inbox_reopen : R.string.feedback_inbox_resolve,
                        (ignored, which) -> mutate(message, "resolved".equals(message.status)
                                ? viewModel.reopen(message) : viewModel.resolve(message)))
                .setPositiveButton("new".equals(message.status)
                        ? R.string.feedback_inbox_mark_read : android.R.string.ok,
                        (ignored, which) -> {
                            if ("new".equals(message.status)) mutate(message,
                                    viewModel.markRead(message));
                        });
        dialog.show();
    }

    private void confirmDelete(Message message) {
        new MaterialAlertDialogBuilder(this).setTitle(R.string.feedback_inbox_delete_title)
                .setMessage(getString(R.string.feedback_inbox_delete_message, message.subject))
                .setNegativeButton(R.string.superadmin_cancel, null)
                .setPositiveButton(R.string.superadmin_delete,
                        (dialog, which) -> mutate(message, viewModel.delete(message))).show();
    }

    private void mutate(Message message,
                        com.google.android.gms.tasks.Task<?> operation) {
        setBusy(true, R.string.superadmin_command_waiting);
        operation.addOnCompleteListener(task -> {
            if (isFinishing() || isDestroyed()) return;
            if (!task.isSuccessful()) {
                setBusy(false, R.string.feedback_inbox_operation_failed);
                Toast.makeText(this, R.string.feedback_inbox_operation_failed,
                        Toast.LENGTH_LONG).show();
                return;
            }
            load();
        });
    }

    private String typeLabel(String type) {
        if ("problem".equals(type)) return getString(R.string.feedback_type_problem);
        if ("suggestion".equals(type)) return getString(R.string.feedback_type_suggestion);
        if ("question".equals(type)) return getString(R.string.feedback_type_question);
        return type;
    }

    private void setBusy(boolean busy, int message) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (message != 0) status.setText(message);
    }
}
