package com.alidogukan.avora.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.nas.NasAuthClient;
import com.alidogukan.avora.nas.NasSession;
import com.alidogukan.avora.viewmodels.NasSecurityViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Professional, role-aware control center for AVORA NAS account security. */
public final class NasSecurityActivity extends EdgeToEdgeActivity {
    private TextView accountName;
    private TextView accountEmail;
    private TextView accountRole;
    private TextView pendingCount;
    private TextView memberAccessStatus;
    private TextView operationStatus;
    private TextView lastChecked;
    private View administratorCard;
    private View memberAccessCard;
    private MaterialButton createInviteButton;
    private MaterialButton requestsButton;
    private MaterialButton requestAccessButton;
    private MaterialButton revokeSessionsButton;
    private MaterialButton disconnectButton;
    private LinearProgressIndicator progress;
    private NasSecurityViewModel viewModel;
    private NasSession currentSession;
    private AlertDialog inviteDialog;
    private AlertDialog passwordDialog;
    private TextInputLayout currentPasswordLayout;
    private TextInputLayout newPasswordLayout;
    private boolean receivedState;

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_nas_security);
        viewModel = new ViewModelProvider(this).get(NasSecurityViewModel.class);
        bindViews();
        configureToolbar();
        configureActions();
        viewModel.state().observe(this, this::render);
        viewModel.events().observe(this, this::handleEvent);
        viewModel.refresh();
    }

    private void bindViews() {
        accountName = findViewById(R.id.txtNasSecurityAccountName);
        accountEmail = findViewById(R.id.txtNasSecurityAccountEmail);
        accountRole = findViewById(R.id.txtNasSecurityRole);
        pendingCount = findViewById(R.id.txtNasSecurityPendingCount);
        memberAccessStatus = findViewById(R.id.txtNasSecurityMemberAccessStatus);
        operationStatus = findViewById(R.id.txtNasSecurityOperationStatus);
        lastChecked = findViewById(R.id.txtNasSecurityLastChecked);
        administratorCard = findViewById(R.id.cardNasSecurityAdministrator);
        memberAccessCard = findViewById(R.id.cardNasSecurityMemberAccess);
        createInviteButton = findViewById(R.id.btnNasSecurityCreateInvite);
        requestsButton = findViewById(R.id.btnNasSecurityRequests);
        requestAccessButton = findViewById(R.id.btnNasSecurityRequestAccess);
        revokeSessionsButton = findViewById(R.id.btnNasSecurityRevokeSessions);
        disconnectButton = findViewById(R.id.btnNasSecurityDisconnect);
        progress = findViewById(R.id.progressNasSecurity);
    }

    private void configureToolbar() {
        ((TextView) findViewById(R.id.txtSettingsToolbarTitle))
                .setText(R.string.nas_security_title);
        findViewById(R.id.btnSettingsToolbarBack).setOnClickListener(view -> finish());
        findViewById(R.id.btnSettingsToolbarAction).setVisibility(View.GONE);
    }

    private void configureActions() {
        createInviteButton.setOnClickListener(view -> viewModel.createInvite());
        requestsButton.setOnClickListener(view -> viewModel.loadPendingRequests());
        requestAccessButton.setOnClickListener(view -> viewModel.requestGardenAccess());
        revokeSessionsButton.setOnClickListener(view -> confirmRevokeOtherSessions());
        findViewById(R.id.rowNasSecurityPassword)
                .setOnClickListener(view -> showPasswordDialog());
        findViewById(R.id.rowNasSecurityDetails)
                .setOnClickListener(view -> showSecurityDetails());
        disconnectButton.setOnClickListener(view -> confirmDisconnect());
    }

    private void render(NasSecurityViewModel.State state) {
        if (state == null) return;
        boolean initialState = !receivedState;
        receivedState = true;
        currentSession = state.session;
        if (currentSession == null) {
            if (initialState && !state.busy) {
                Toast.makeText(this, R.string.nas_security_not_connected,
                        Toast.LENGTH_LONG).show();
                finish();
            }
            return;
        }

        accountName.setText(currentSession.user.displayName);
        accountEmail.setText(currentSession.user.email);
        accountRole.setText(state.isAdministrator()
                ? R.string.nas_security_role_administrator
                : R.string.nas_security_role_family);
        administratorCard.setVisibility(state.isAdministrator()
                ? View.VISIBLE : View.GONE);
        memberAccessCard.setVisibility(state.isAdministrator()
                ? View.GONE : View.VISIBLE);
        pendingCount.setText(pendingLabel(state.pendingRequestCount));
        lastChecked.setText(getString(R.string.nas_security_last_checked,
                formatDateTime(System.currentTimeMillis())));

        progress.setVisibility(state.busy ? View.VISIBLE : View.GONE);
        createInviteButton.setEnabled(!state.busy);
        requestsButton.setEnabled(!state.busy);
        requestAccessButton.setEnabled(!state.busy);
        revokeSessionsButton.setEnabled(!state.busy);
        disconnectButton.setEnabled(!state.busy);
        findViewById(R.id.rowNasSecurityPassword).setEnabled(!state.busy);
        findViewById(R.id.rowNasSecurityDetails).setEnabled(!state.busy);
        if (state.busy) showBusyStatus(state.action);
    }

    private String pendingLabel(int count) {
        if (count == -1) return getString(R.string.nas_security_pending_checking);
        if (count == -2) return getString(R.string.nas_security_pending_unavailable);
        if (count == 0) return getString(R.string.nas_security_pending_none);
        return getString(R.string.nas_security_pending_count, count);
    }

    private void showBusyStatus(NasSecurityViewModel.Action action) {
        int message;
        switch (action) {
            case CREATE_INVITE:
                message = R.string.data_sync_nas_invite_creating;
                break;
            case LOAD_REQUESTS:
                message = R.string.data_sync_nas_access_loading;
                break;
            case REQUEST_ACCESS:
                message = R.string.data_sync_nas_access_request_sending;
                break;
            case APPROVE_ACCESS:
                message = R.string.data_sync_nas_access_approving;
                break;
            case CHANGE_PASSWORD:
                message = R.string.nas_security_password_changing;
                break;
            case REVOKE_SESSIONS:
                message = R.string.nas_security_sessions_closing;
                break;
            case DISCONNECT:
                message = R.string.nas_security_disconnecting;
                break;
            default:
                return;
        }
        showStatus(getString(message), R.color.textSecondary);
    }

    private void handleEvent(NasSecurityViewModel.Event event) {
        if (event == null || !event.consume()) return;
        switch (event.type) {
            case INVITE_READY:
                showInviteDialog((NasAuthClient.Invite) event.payload);
                break;
            case INVITE_REVOKED:
                if (inviteDialog != null && inviteDialog.isShowing()) inviteDialog.dismiss();
                showSuccess(R.string.data_sync_nas_invite_revoked);
                break;
            case REQUESTS_READY:
                showAccessRequests(NasSecurityViewModel.requestsFrom(event));
                break;
            case ACCESS_REQUESTED:
                NasAuthClient.AccessRequest request =
                        (NasAuthClient.AccessRequest) event.payload;
                boolean approved = request != null && "approved".equals(request.status);
                memberAccessStatus.setText(approved
                        ? R.string.nas_security_access_approved
                        : R.string.nas_security_access_pending);
                showSuccess(approved
                        ? R.string.data_sync_nas_access_already_approved
                        : R.string.data_sync_nas_access_request_pending);
                break;
            case ACCESS_APPROVED:
                showStatus(getString(R.string.data_sync_nas_access_approved,
                        String.valueOf(event.payload)), R.color.online);
                break;
            case PASSWORD_CHANGED:
                if (passwordDialog != null && passwordDialog.isShowing()) {
                    passwordDialog.dismiss();
                }
                showStatus(getString(R.string.data_sync_nas_password_changed,
                        event.count), R.color.online);
                break;
            case SESSIONS_REVOKED:
                showStatus(event.count == 0
                                ? getString(R.string.data_sync_nas_no_other_sessions)
                                : getString(R.string.data_sync_nas_other_sessions_revoked,
                                        event.count),
                        R.color.online);
                break;
            case DISCONNECTED:
                Toast.makeText(this, R.string.nas_security_disconnected,
                        Toast.LENGTH_LONG).show();
                setResult(RESULT_OK);
                finish();
                break;
            case SESSION_EXPIRED:
                Toast.makeText(this, R.string.data_sync_nas_session_expired,
                        Toast.LENGTH_LONG).show();
                setResult(RESULT_OK);
                finish();
                break;
            case ERROR:
                handleError(event);
                break;
        }
    }

    private void showInviteDialog(NasAuthClient.Invite invite) {
        if (invite == null) return;
        View content = getLayoutInflater().inflate(R.layout.dialog_nas_invite,
                null, false);
        ((TextView) content.findViewById(R.id.txtNasInviteCode)).setText(invite.code);
        ((TextView) content.findViewById(R.id.txtNasInviteDetail)).setText(
                getString(R.string.data_sync_nas_invite_detail,
                        formatDateTime(invite.expiresAt * 1000L)));
        inviteDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.data_sync_nas_invite_ready_title)
                .setView(content)
                .setNegativeButton(R.string.data_sync_nas_invite_close, null)
                .setNeutralButton(R.string.data_sync_nas_invite_revoke, null)
                .setPositiveButton(R.string.data_sync_nas_invite_share, null)
                .create();
        inviteDialog.setOnShowListener(ignored -> {
            inviteDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(view -> shareInvite(invite));
            inviteDialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                    .setOnClickListener(view -> confirmInviteRevocation(invite));
        });
        inviteDialog.show();
    }

    private void shareInvite(NasAuthClient.Invite invite) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, getString(
                R.string.data_sync_nas_invite_share_text,
                invite.code, formatDateTime(invite.expiresAt * 1000L)));
        startActivity(Intent.createChooser(share,
                getString(R.string.data_sync_nas_invite_share_chooser)));
    }

    private void confirmInviteRevocation(NasAuthClient.Invite invite) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.data_sync_nas_invite_revoke_title)
                .setMessage(R.string.data_sync_nas_invite_revoke_message)
                .setNegativeButton(R.string.data_sync_nas_cancel, null)
                .setPositiveButton(R.string.data_sync_nas_invite_revoke,
                        (dialog, which) -> viewModel.revokeInvite(invite))
                .show();
    }

    private void showAccessRequests(List<NasAuthClient.AccessRequest> requests) {
        if (requests.isEmpty()) {
            showSuccess(R.string.data_sync_nas_access_none);
            return;
        }
        CharSequence[] labels = new CharSequence[requests.size()];
        for (int index = 0; index < requests.size(); index++) {
            NasAuthClient.AccessRequest request = requests.get(index);
            labels[index] = getString(R.string.data_sync_nas_access_request_item,
                    request.displayName, request.email);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.data_sync_nas_access_requests_title)
                .setItems(labels, (dialog, which) ->
                        confirmAccessRequest(requests.get(which)))
                .setNegativeButton(R.string.data_sync_nas_cancel, null)
                .show();
    }

    private void confirmAccessRequest(NasAuthClient.AccessRequest request) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.data_sync_nas_access_confirm_title)
                .setMessage(getString(R.string.data_sync_nas_access_confirm_message,
                        request.displayName, request.email))
                .setNegativeButton(R.string.data_sync_nas_cancel, null)
                .setPositiveButton(R.string.data_sync_nas_access_confirm,
                        (dialog, which) -> viewModel.approveAccessRequest(request))
                .show();
    }

    private void showPasswordDialog() {
        View content = getLayoutInflater().inflate(
                R.layout.dialog_nas_change_password, null, false);
        currentPasswordLayout = content.findViewById(R.id.layoutNasCurrentPassword);
        newPasswordLayout = content.findViewById(R.id.layoutNasNewPassword);
        TextInputLayout confirmLayout = content.findViewById(
                R.id.layoutNasConfirmPassword);
        TextInputEditText currentInput = content.findViewById(
                R.id.inputNasCurrentPassword);
        TextInputEditText newInput = content.findViewById(R.id.inputNasNewPassword);
        TextInputEditText confirmInput = content.findViewById(
                R.id.inputNasConfirmPassword);
        passwordDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.data_sync_nas_change_password_title)
                .setView(content)
                .setNegativeButton(R.string.data_sync_nas_cancel, null)
                .setPositiveButton(R.string.data_sync_nas_change_password_confirm, null)
                .create();
        passwordDialog.setOnShowListener(ignored ->
                passwordDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(view -> validatePasswordChange(
                                currentPasswordLayout, newPasswordLayout, confirmLayout,
                                currentInput, newInput, confirmInput)));
        passwordDialog.show();
    }

    private void validatePasswordChange(TextInputLayout currentLayout,
                                        TextInputLayout newLayout,
                                        TextInputLayout confirmLayout,
                                        TextInputEditText currentInput,
                                        TextInputEditText newInput,
                                        TextInputEditText confirmInput) {
        currentLayout.setError(null);
        newLayout.setError(null);
        confirmLayout.setError(null);
        String current = textOf(currentInput);
        String next = textOf(newInput);
        String confirmation = textOf(confirmInput);
        if (current.isEmpty()) {
            currentLayout.setError(getString(R.string.data_sync_nas_error_required));
        } else if (next.length() < 12) {
            newLayout.setError(getString(R.string.data_sync_nas_password_policy));
        } else if (!next.equals(confirmation)) {
            confirmLayout.setError(getString(R.string.data_sync_nas_password_mismatch));
        } else if (current.equals(next)) {
            newLayout.setError(getString(R.string.data_sync_nas_password_unchanged));
        } else {
            currentInput.setText(null);
            newInput.setText(null);
            confirmInput.setText(null);
            passwordDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            passwordDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
            viewModel.changePassword(current, next);
        }
    }

    private void confirmRevokeOtherSessions() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.data_sync_nas_revoke_others_title)
                .setMessage(R.string.data_sync_nas_revoke_others_message)
                .setNegativeButton(R.string.data_sync_nas_cancel, null)
                .setPositiveButton(R.string.data_sync_nas_revoke_others_confirm,
                        (dialog, which) -> viewModel.revokeOtherSessions())
                .show();
    }

    private void confirmDisconnect() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.data_sync_nas_disconnect_title)
                .setMessage(R.string.data_sync_nas_disconnect_message)
                .setNegativeButton(R.string.data_sync_nas_cancel, null)
                .setPositiveButton(R.string.data_sync_nas_account_disconnect,
                        (dialog, which) -> viewModel.disconnect())
                .show();
    }

    private void showSecurityDetails() {
        if (currentSession == null) return;
        String role = getString("admin".equals(currentSession.user.role)
                ? R.string.nas_security_role_administrator
                : R.string.nas_security_role_family);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.nas_security_details_title)
                .setMessage(getString(R.string.nas_security_details_message,
                        currentSession.user.email,
                        role,
                        formatDateTime(currentSession.expiresAt * 1000L)))
                .setPositiveButton(R.string.nas_security_ok, null)
                .show();
    }

    private void handleError(NasSecurityViewModel.Event event) {
        if (event.action == NasSecurityViewModel.Action.CHANGE_PASSWORD
                && passwordDialog != null && passwordDialog.isShowing()) {
            passwordDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
            passwordDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
            if ("NAS_CURRENT_PASSWORD_INVALID".equals(event.code)) {
                currentPasswordLayout.setError(getString(
                        R.string.data_sync_nas_current_password_invalid));
                return;
            }
            if ("NAS_WEAK_PASSWORD".equals(event.code)) {
                newPasswordLayout.setError(getString(
                        R.string.data_sync_nas_password_policy));
                return;
            }
            if ("NAS_PASSWORD_UNCHANGED".equals(event.code)) {
                newPasswordLayout.setError(getString(
                        R.string.data_sync_nas_password_unchanged));
                return;
            }
        }
        int message = "NAS_RATE_LIMITED".equals(event.code)
                ? R.string.data_sync_nas_error_rate_limited
                : ("NAS_TIMEOUT".equals(event.code)
                || "NAS_UNAVAILABLE".equals(event.code))
                ? R.string.data_sync_nas_error_connection
                : R.string.data_sync_nas_account_security_error;
        showStatus(getString(message), R.color.warning);
    }

    private void showSuccess(int messageRes) {
        showStatus(getString(messageRes), R.color.online);
    }

    private void showStatus(String message, int colorRes) {
        operationStatus.setVisibility(View.VISIBLE);
        operationStatus.setText(message);
        operationStatus.setTextColor(ContextCompat.getColor(this, colorRes));
    }

    private static String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

    private static String formatDateTime(long epochMillis) {
        return new SimpleDateFormat("dd-MM-yyyy HH:mm",
                Locale.getDefault()).format(new Date(epochMillis));
    }
}
