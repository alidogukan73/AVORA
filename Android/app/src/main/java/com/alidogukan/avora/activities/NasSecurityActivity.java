package com.alidogukan.avora.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.LinearLayout;
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
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Professional, role-aware control center for AVORA NAS account security. */
public final class NasSecurityActivity extends EdgeToEdgeActivity {
    public static final String EXTRA_OPEN_PENDING_REQUESTS =
            "open_pending_access_requests";
    public static final String EXTRA_OPEN_INACTIVE_ACCOUNTS =
            "open_inactive_accounts";
    public static final String EXTRA_REQUEST_GARDEN_ACCESS =
            "request_garden_access";
    private TextView accountName;
    private TextView accountEmail;
    private TextView accountRole;
    private TextView pendingCount;
    private TextView memberAccessStatus;
    private TextView currentDeviceDetail;
    private TextView otherSessionsStatus;
    private LinearLayout otherSessionsList;
    private TextView operationStatus;
    private TextView lastChecked;
    private View disconnectedCard;
    private View connectedContent;
    private View administratorCard;
    private View memberAccessCard;
    private MaterialButton connectButton;
    private MaterialButton registerButton;
    private MaterialButton createInviteButton;
    private MaterialButton accountsButton;
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
    private AlertDialog loginDialog;
    private AlertDialog registrationDialog;
    private AlertDialog accountsDialog;
    private AlertDialog deleteAccountDialog;
    private TextInputLayout deleteAdminPasswordLayout;
    private TextInputLayout loginEmailLayout;
    private TextInputLayout loginPasswordLayout;
    private TextInputLayout registerInviteLayout;
    private TextInputLayout registerPasswordLayout;
    private boolean openPendingRequestsOnReady;
    private boolean openInactiveAccountsOnReady;
    private boolean requestGardenAccessOnReady;

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_nas_security);
        openPendingRequestsOnReady = state == null
                && getIntent().getBooleanExtra(EXTRA_OPEN_PENDING_REQUESTS, false);
        openInactiveAccountsOnReady = state == null
                && getIntent().getBooleanExtra(EXTRA_OPEN_INACTIVE_ACCOUNTS, false);
        requestGardenAccessOnReady = state == null
                && getIntent().getBooleanExtra(EXTRA_REQUEST_GARDEN_ACCESS, false);
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
        currentDeviceDetail = findViewById(R.id.txtNasSecurityThisDeviceDetail);
        otherSessionsStatus = findViewById(
                R.id.txtNasSecurityOtherSessionsStatus);
        otherSessionsList = findViewById(R.id.layoutNasSecurityOtherSessions);
        memberAccessStatus = findViewById(R.id.txtNasSecurityMemberAccessStatus);
        operationStatus = findViewById(R.id.txtNasSecurityOperationStatus);
        lastChecked = findViewById(R.id.txtNasSecurityLastChecked);
        disconnectedCard = findViewById(R.id.cardNasSecurityDisconnected);
        connectedContent = findViewById(R.id.layoutNasSecurityConnectedContent);
        connectButton = findViewById(R.id.btnNasSecurityConnect);
        registerButton = findViewById(R.id.btnNasSecurityRegister);
        administratorCard = findViewById(R.id.cardNasSecurityAdministrator);
        memberAccessCard = findViewById(R.id.cardNasSecurityMemberAccess);
        createInviteButton = findViewById(R.id.btnNasSecurityCreateInvite);
        accountsButton = findViewById(R.id.btnNasSecurityAccounts);
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
        accountsButton.setOnClickListener(view -> viewModel.loadAccounts());
        requestsButton.setOnClickListener(view -> viewModel.loadPendingRequests());
        connectButton.setOnClickListener(view -> showLoginDialog());
        registerButton.setOnClickListener(view -> showRegistrationDialog());
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
        currentSession = state.session;
        boolean connected = currentSession != null;
        disconnectedCard.setVisibility(connected ? View.GONE : View.VISIBLE);
        connectedContent.setVisibility(connected ? View.VISIBLE : View.GONE);
        progress.setVisibility(state.busy ? View.VISIBLE : View.GONE);
        connectButton.setEnabled(!state.busy);
        registerButton.setEnabled(!state.busy);
        if (currentSession == null) {
            if (state.busy) showBusyStatus(state.action);
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

        int otherSessionCount = renderSessionDevices(state);
        createInviteButton.setEnabled(!state.busy);
        accountsButton.setEnabled(!state.busy);
        requestsButton.setEnabled(!state.busy);
        requestAccessButton.setEnabled(!state.busy);
        boolean canRevoke = state.sessionListStatus == -2 || otherSessionCount > 0;
        revokeSessionsButton.setEnabled(!state.busy && canRevoke);
        revokeSessionsButton.setText(otherSessionCount > 0
                ? getResources().getQuantityString(
                        R.plurals.nas_security_revoke_session_devices,
                        otherSessionCount, otherSessionCount)
                : getString(R.string.data_sync_nas_revoke_others));
        disconnectButton.setEnabled(!state.busy);
        findViewById(R.id.rowNasSecurityPassword).setEnabled(!state.busy);
        findViewById(R.id.rowNasSecurityDetails).setEnabled(!state.busy);
        if (state.busy) showBusyStatus(state.action);
        if (requestGardenAccessOnReady && !state.busy) {
            requestGardenAccessOnReady = false;
            viewModel.requestGardenAccess();
        } else if (openPendingRequestsOnReady && state.isAdministrator() && !state.busy) {
            openPendingRequestsOnReady = false;
            viewModel.loadPendingRequests();
        } else if (openInactiveAccountsOnReady
                && state.isAdministrator() && !state.busy) {
            openInactiveAccountsOnReady = false;
            viewModel.loadAccounts();
        }
    }

    private String pendingLabel(int count) {
        if (count == -1) return getString(R.string.nas_security_pending_checking);
        if (count == -2) return getString(R.string.nas_security_pending_unavailable);
        if (count == 0) return getString(R.string.nas_security_pending_none);
        return getString(R.string.nas_security_pending_count, count);
    }

    private int renderSessionDevices(NasSecurityViewModel.State state) {
        otherSessionsList.removeAllViews();
        currentDeviceDetail.setText(R.string.nas_security_this_device_detail);
        if (state.sessionListStatus == -1) {
            otherSessionsStatus.setVisibility(View.VISIBLE);
            otherSessionsStatus.setText(R.string.nas_security_sessions_checking);
            return 0;
        }
        if (state.sessionListStatus == -2) {
            otherSessionsStatus.setVisibility(View.VISIBLE);
            otherSessionsStatus.setText(R.string.nas_security_sessions_unavailable);
            return 0;
        }
        int count = 0;
        for (NasAuthClient.SessionSummary session : state.activeSessions) {
            if (session.current) {
                if (!session.deviceName.isEmpty()) {
                    currentDeviceDetail.setText(session.deviceName);
                }
                continue;
            }
            otherSessionsList.addView(createSessionRow(session, otherSessionsList));
            count++;
        }
        otherSessionsStatus.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        if (count == 0) {
            otherSessionsStatus.setText(R.string.nas_security_sessions_none);
        }
        return count;
    }

    private View createSessionRow(NasAuthClient.SessionSummary session,
                                  LinearLayout parent) {
        View row = getLayoutInflater().inflate(
                R.layout.item_nas_session, parent, false);
        TextView name = row.findViewById(R.id.txtNasSessionDeviceName);
        TextView status = row.findViewById(R.id.txtNasSessionStatus);
        TextView details = row.findViewById(R.id.txtNasSessionDetails);
        boolean knownDevice = !session.deviceName.isEmpty();
        name.setText(knownDevice
                ? session.deviceName
                : getString(R.string.nas_security_unknown_device));
        status.setText(R.string.nas_security_session_open);
        details.setText(getString(
                knownDevice
                        ? R.string.nas_security_session_detail
                        : R.string.nas_security_legacy_session_detail,
                formatDateTime(session.lastSeenAt * 1000L),
                formatDateTime(session.expiresAt * 1000L)));
        return row;
    }

    private void showBusyStatus(NasSecurityViewModel.Action action) {
        int message;
        switch (action) {
            case LOGIN:
                message = R.string.data_sync_nas_connecting;
                break;
            case REGISTER:
                message = R.string.nas_security_registering;
                break;
            case CREATE_INVITE:
                message = R.string.data_sync_nas_invite_creating;
                break;
            case LOAD_ACCOUNTS:
                message = R.string.nas_security_accounts_loading;
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
            case KEEP_INACTIVE_ACCESS:
                message = R.string.nas_security_inactive_keeping;
                break;
            case REVOKE_DEVICE_ACCESS:
                message = R.string.nas_security_access_revoking;
                break;
            case DISABLE_ACCOUNT:
                message = R.string.nas_security_account_disabling;
                break;
            case RESTORE_ACCOUNT:
                message = R.string.nas_security_account_restoring;
                break;
            case DELETE_ACCOUNT:
                message = R.string.nas_security_account_deleting;
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
            case CONNECTED:
                if (loginDialog != null && loginDialog.isShowing()) loginDialog.dismiss();
                NasSession connected = (NasSession) event.payload;
                Toast.makeText(this, getString(R.string.nas_security_connected_success,
                        connected == null ? "" : connected.user.displayName),
                        Toast.LENGTH_LONG).show();
                break;
            case REGISTERED:
                if (registrationDialog != null && registrationDialog.isShowing()) {
                    registrationDialog.dismiss();
                }
                NasSession registered = (NasSession) event.payload;
                Toast.makeText(this, getString(R.string.data_sync_nas_register_success,
                        registered == null ? "" : registered.user.displayName),
                        Toast.LENGTH_LONG).show();
                break;
            case INVITE_READY:
                showInviteDialog((NasAuthClient.Invite) event.payload);
                break;
            case INVITE_REVOKED:
                if (inviteDialog != null && inviteDialog.isShowing()) inviteDialog.dismiss();
                showSuccess(R.string.data_sync_nas_invite_revoked);
                break;
            case ACCOUNTS_READY:
                showAccounts(NasSecurityViewModel.accountsFrom(event));
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
            case INACTIVE_ACCESS_KEPT:
                if (accountsDialog != null && accountsDialog.isShowing()) {
                    accountsDialog.dismiss();
                }
                showStatus(getString(R.string.nas_security_inactive_kept,
                        String.valueOf(event.payload)), R.color.online);
                viewModel.loadAccounts();
                break;
            case DEVICE_ACCESS_REVOKED:
                if (accountsDialog != null && accountsDialog.isShowing()) {
                    accountsDialog.dismiss();
                }
                showStatus(getString(R.string.nas_security_access_revoked,
                        String.valueOf(event.payload)), R.color.online);
                viewModel.loadAccounts();
                break;
            case ACCOUNT_DISABLED:
                dismissAccountsDialog();
                showStatus(getString(R.string.nas_security_account_disabled_success,
                        String.valueOf(event.payload)), R.color.online);
                viewModel.loadAccounts();
                break;
            case ACCOUNT_RESTORED:
                dismissAccountsDialog();
                showStatus(getString(R.string.nas_security_account_restored_success,
                        String.valueOf(event.payload)), R.color.online);
                viewModel.loadAccounts();
                break;
            case ACCOUNT_DELETED:
                dismissDeleteAccountDialog();
                dismissAccountsDialog();
                showStatus(getString(R.string.nas_security_account_deleted_success,
                        String.valueOf(event.payload)), R.color.online);
                viewModel.loadAccounts();
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
                break;
            case SESSION_EXPIRED:
                Toast.makeText(this, R.string.data_sync_nas_session_expired,
                        Toast.LENGTH_LONG).show();
                setResult(RESULT_OK);
                break;
            case ERROR:
                handleError(event);
                break;
        }
    }

    private void showLoginDialog() {
        View content = getLayoutInflater().inflate(R.layout.dialog_nas_login, null, false);
        loginEmailLayout = content.findViewById(R.id.layoutNasEmail);
        loginPasswordLayout = content.findViewById(R.id.layoutNasPassword);
        TextInputEditText emailInput = content.findViewById(R.id.inputNasEmail);
        TextInputEditText passwordInput = content.findViewById(R.id.inputNasPassword);
        loginDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.data_sync_nas_login_title)
                .setView(content)
                .setNegativeButton(R.string.data_sync_nas_cancel, null)
                .setNeutralButton(R.string.data_sync_nas_register_action,
                        (dialog, which) -> showRegistrationDialog())
                .setPositiveButton(R.string.data_sync_nas_account_connect, null)
                .create();
        loginDialog.setOnShowListener(ignored ->
                loginDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(view -> validateLogin(emailInput, passwordInput)));
        loginDialog.show();
    }

    private void validateLogin(TextInputEditText emailInput,
                               TextInputEditText passwordInput) {
        loginEmailLayout.setError(null);
        loginPasswordLayout.setError(null);
        String email = textOf(emailInput).trim();
        String password = textOf(passwordInput);
        if (email.isEmpty()) {
            loginEmailLayout.setError(getString(R.string.data_sync_nas_error_required));
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            loginEmailLayout.setError(getString(R.string.data_sync_nas_error_email));
        } else if (password.isEmpty()) {
            loginPasswordLayout.setError(getString(R.string.data_sync_nas_error_required));
        } else {
            passwordInput.setText(null);
            setDialogEnabled(loginDialog, false);
            viewModel.login(email, password);
        }
    }

    private void showRegistrationDialog() {
        View content = getLayoutInflater().inflate(R.layout.dialog_nas_register,
                null, false);
        registerInviteLayout = content.findViewById(R.id.layoutNasInviteCode);
        TextInputLayout nameLayout = content.findViewById(R.id.layoutNasRegisterName);
        TextInputLayout emailLayout = content.findViewById(R.id.layoutNasRegisterEmail);
        registerPasswordLayout = content.findViewById(R.id.layoutNasRegisterPassword);
        TextInputLayout confirmLayout = content.findViewById(
                R.id.layoutNasRegisterConfirmPassword);
        TextInputEditText inviteInput = content.findViewById(R.id.inputNasInviteCode);
        TextInputEditText nameInput = content.findViewById(R.id.inputNasRegisterName);
        TextInputEditText emailInput = content.findViewById(R.id.inputNasRegisterEmail);
        TextInputEditText passwordInput = content.findViewById(
                R.id.inputNasRegisterPassword);
        TextInputEditText confirmInput = content.findViewById(
                R.id.inputNasRegisterConfirmPassword);
        registrationDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.data_sync_nas_register_title)
                .setView(content)
                .setNegativeButton(R.string.data_sync_nas_cancel, null)
                .setPositiveButton(R.string.data_sync_nas_register_confirm, null)
                .create();
        registrationDialog.setOnShowListener(ignored ->
                registrationDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(view -> validateRegistration(
                                inviteInput, nameInput, emailInput, passwordInput,
                                confirmInput, nameLayout, emailLayout, confirmLayout)));
        registrationDialog.show();
    }

    private void validateRegistration(TextInputEditText inviteInput,
                                      TextInputEditText nameInput,
                                      TextInputEditText emailInput,
                                      TextInputEditText passwordInput,
                                      TextInputEditText confirmInput,
                                      TextInputLayout nameLayout,
                                      TextInputLayout emailLayout,
                                      TextInputLayout confirmLayout) {
        registerInviteLayout.setError(null);
        nameLayout.setError(null);
        emailLayout.setError(null);
        registerPasswordLayout.setError(null);
        confirmLayout.setError(null);
        String invite = textOf(inviteInput).trim();
        String name = textOf(nameInput).trim();
        String email = textOf(emailInput).trim();
        String password = textOf(passwordInput);
        String confirmation = textOf(confirmInput);
        if (invite.isEmpty()) {
            registerInviteLayout.setError(getString(R.string.data_sync_nas_error_required));
        } else if (name.isEmpty()) {
            nameLayout.setError(getString(R.string.data_sync_nas_error_required));
        } else if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.setError(getString(R.string.data_sync_nas_error_email));
        } else if (password.length() < 12) {
            registerPasswordLayout.setError(getString(R.string.data_sync_nas_password_policy));
        } else if (!password.equals(confirmation)) {
            confirmLayout.setError(getString(R.string.data_sync_nas_password_mismatch));
        } else {
            passwordInput.setText(null);
            confirmInput.setText(null);
            setDialogEnabled(registrationDialog, false);
            viewModel.register(invite, email, name, password);
        }
    }

    private static void setDialogEnabled(AlertDialog dialog, boolean enabled) {
        if (dialog == null || !dialog.isShowing()) return;
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(enabled);
        }
        if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(enabled);
        }
        if (dialog.getButton(AlertDialog.BUTTON_NEUTRAL) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(enabled);
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

    private void showAccounts(List<NasAuthClient.AccountSummary> accounts) {
        if (accounts.isEmpty()) {
            showStatus(getString(R.string.nas_security_accounts_none), R.color.textSecondary);
            return;
        }
        View content = getLayoutInflater().inflate(
                R.layout.dialog_nas_accounts, null, false);
        TextView summary = content.findViewById(R.id.txtNasAccountsSummary);
        LinearLayout accountList = content.findViewById(R.id.layoutNasAccountsList);
        int activeSessionCount = 0;
        for (NasAuthClient.AccountSummary account : accounts) {
            activeSessionCount += account.activeSessions;
            accountList.addView(createAccountRow(account, accountList));
        }
        String accountCount = getResources().getQuantityString(
                R.plurals.nas_security_account_count, accounts.size(), accounts.size());
        String sessionCount = getResources().getQuantityString(
                R.plurals.nas_security_active_sessions,
                activeSessionCount, activeSessionCount);
        summary.setText(getString(
                R.string.nas_security_accounts_summary, accountCount, sessionCount));
        accountsDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.nas_security_accounts_title)
                .setView(content)
                .setPositiveButton(R.string.nas_security_ok, null)
                .create();
        accountsDialog.show();
    }

    private View createAccountRow(NasAuthClient.AccountSummary account,
                                  LinearLayout parent) {
        View row = getLayoutInflater().inflate(
                R.layout.item_nas_account, parent, false);
        MaterialCardView avatarCard = row.findViewById(R.id.cardNasAccountAvatar);
        TextView avatar = row.findViewById(R.id.txtNasAccountAvatar);
        TextView name = row.findViewById(R.id.txtNasAccountName);
        TextView email = row.findViewById(R.id.txtNasAccountEmail);
        TextView role = row.findViewById(R.id.txtNasAccountRole);
        TextView current = row.findViewById(R.id.txtNasAccountCurrent);
        TextView session = row.findViewById(R.id.txtNasAccountSession);
        TextView created = row.findViewById(R.id.txtNasAccountCreated);
        TextView lastActive = row.findViewById(R.id.txtNasAccountLastActive);
        TextView lifecycle = row.findViewById(R.id.txtNasAccountLifecycle);
        MaterialButton review = row.findViewById(R.id.btnNasAccountReview);
        MaterialButton manage = row.findViewById(R.id.btnNasAccountManage);

        boolean administrator = "admin".equals(account.role);
        avatar.setText(initials(account.displayName));
        avatar.setTextColor(ContextCompat.getColor(this,
                administrator ? R.color.primary : R.color.info));
        avatarCard.setCardBackgroundColor(ContextCompat.getColor(this,
                administrator ? R.color.primaryLight : R.color.infoBackground));
        name.setText(account.displayName);
        email.setText(account.email);
        role.setText(administrator
                ? R.string.nas_security_role_administrator_short
                : R.string.nas_security_role_family_short);
        role.setTextColor(ContextCompat.getColor(this,
                administrator ? R.color.primary : R.color.info));
        role.setBackgroundResource(administrator
                ? R.drawable.bg_nas_account_role_admin
                : R.drawable.bg_nas_account_role_family);
        boolean currentAccount = currentSession != null
                && account.id.equals(currentSession.user.id);
        current.setVisibility(currentAccount ? View.VISIBLE : View.GONE);
        created.setText(getString(R.string.nas_security_account_joined,
                formatDate(account.createdAt * 1000L)));
        lastActive.setText(getString(R.string.nas_security_account_last_active,
                formatDateTime(account.lastActiveAt * 1000L)));
        review.setVisibility(account.inactiveAccess ? View.VISIBLE : View.GONE);
        review.setOnClickListener(view -> showInactiveAccessReview(account));

        if (!administrator && account.active) {
            manage.setVisibility(View.VISIBLE);
            manage.setText(R.string.nas_security_account_disable);
            manage.setTextColor(ContextCompat.getColor(this, R.color.primary));
            manage.setOnClickListener(view -> confirmDisableAccount(account));
        } else if (!administrator && account.canRestore) {
            lifecycle.setVisibility(View.VISIBLE);
            lifecycle.setText(getString(R.string.nas_security_account_data_retained,
                    formatDate(account.deleteEligibleAt * 1000L)));
            manage.setVisibility(View.VISIBLE);
            manage.setText(R.string.nas_security_account_restore);
            manage.setTextColor(ContextCompat.getColor(this, R.color.primary));
            manage.setOnClickListener(view -> confirmRestoreAccount(account));
        } else if (!administrator && account.canPermanentlyDelete) {
            lifecycle.setVisibility(View.VISIBLE);
            lifecycle.setText(R.string.nas_security_account_recovery_ended);
            manage.setVisibility(View.VISIBLE);
            manage.setText(R.string.nas_security_account_delete_open);
            manage.setTextColor(ContextCompat.getColor(this, R.color.offline));
            manage.setOnClickListener(view -> showPermanentDeletePasswordDialog(account));
        }

        if (!account.active) {
            session.setText(R.string.nas_security_account_disabled);
            session.setTextColor(ContextCompat.getColor(this, R.color.offline));
        } else if (account.activeSessions == 0) {
            session.setText(R.string.nas_security_no_active_sessions);
            session.setTextColor(ContextCompat.getColor(this, R.color.textTertiary));
        } else {
            String activeSessions = getResources().getQuantityString(
                    R.plurals.nas_security_active_sessions,
                    account.activeSessions, account.activeSessions);
            session.setText(getString(
                    R.string.nas_security_account_session_status, activeSessions));
            session.setTextColor(ContextCompat.getColor(this, R.color.online));
        }
        return row;
    }

    private void confirmDisableAccount(NasAuthClient.AccountSummary account) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.nas_security_account_disable_title)
                .setMessage(getString(R.string.nas_security_account_disable_message,
                        account.displayName))
                .setNegativeButton(R.string.data_sync_nas_cancel, null)
                .setPositiveButton(R.string.nas_security_account_disable_confirm,
                        (dialog, which) -> viewModel.disableAccount(account))
                .show();
    }

    private void confirmRestoreAccount(NasAuthClient.AccountSummary account) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.nas_security_account_restore_title)
                .setMessage(getString(R.string.nas_security_account_restore_message,
                        account.displayName))
                .setNegativeButton(R.string.data_sync_nas_cancel, null)
                .setPositiveButton(R.string.nas_security_account_restore_confirm,
                        (dialog, which) -> viewModel.restoreAccount(account))
                .show();
    }

    private void showPermanentDeletePasswordDialog(NasAuthClient.AccountSummary account) {
        View content = getLayoutInflater().inflate(
                R.layout.dialog_nas_delete_account, null, false);
        TextView message = content.findViewById(R.id.txtNasDeleteAccountMessage);
        deleteAdminPasswordLayout = content.findViewById(
                R.id.layoutNasDeleteAdminPassword);
        TextInputEditText passwordInput = content.findViewById(
                R.id.inputNasDeleteAdminPassword);
        message.setText(getString(R.string.nas_security_account_delete_password_message,
                account.displayName));
        deleteAccountDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.nas_security_account_delete_title)
                .setView(content)
                .setNegativeButton(R.string.data_sync_nas_cancel, null)
                .setPositiveButton(R.string.nas_security_account_delete_continue, null)
                .create();
        deleteAccountDialog.setOnShowListener(ignored ->
                deleteAccountDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(view -> {
                            deleteAdminPasswordLayout.setError(null);
                            String password = textOf(passwordInput);
                            if (password.isEmpty()) {
                                deleteAdminPasswordLayout.setError(getString(
                                        R.string.data_sync_nas_error_required));
                                return;
                            }
                            passwordInput.setText(null);
                            confirmPermanentDelete(account, password);
                        }));
        deleteAccountDialog.show();
    }

    private void confirmPermanentDelete(NasAuthClient.AccountSummary account,
                                        String currentPassword) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.nas_security_account_delete_final_title)
                .setMessage(getString(R.string.nas_security_account_delete_final_message,
                        account.displayName, account.email))
                .setNegativeButton(R.string.data_sync_nas_cancel, null)
                .setPositiveButton(R.string.nas_security_account_delete_confirm,
                        (dialog, which) -> {
                            setDialogEnabled(deleteAccountDialog, false);
                            viewModel.permanentlyDeleteAccount(account, currentPassword);
                        })
                .show();
    }

    private void dismissAccountsDialog() {
        if (accountsDialog != null && accountsDialog.isShowing()) {
            accountsDialog.dismiss();
        }
    }

    private void dismissDeleteAccountDialog() {
        if (deleteAccountDialog != null && deleteAccountDialog.isShowing()) {
            deleteAccountDialog.dismiss();
        }
    }

    private void showInactiveAccessReview(NasAuthClient.AccountSummary account) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.nas_security_inactive_review_title)
                .setMessage(getString(R.string.nas_security_inactive_review_message,
                        account.displayName,
                        formatDateTime(account.lastActiveAt * 1000L)))
                .setNegativeButton(R.string.data_sync_nas_cancel, null)
                .setNeutralButton(R.string.nas_security_inactive_keep,
                        (dialog, which) -> viewModel.keepInactiveAccess(account))
                .setPositiveButton(R.string.nas_security_access_revoke,
                        (dialog, which) -> viewModel.revokeDeviceAccess(account))
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
                        role))
                .setPositiveButton(R.string.nas_security_ok, null)
                .show();
    }

    private void handleError(NasSecurityViewModel.Event event) {
        if (event.action == NasSecurityViewModel.Action.DELETE_ACCOUNT
                && deleteAccountDialog != null && deleteAccountDialog.isShowing()) {
            setDialogEnabled(deleteAccountDialog, true);
            if ("NAS_CURRENT_PASSWORD_INVALID".equals(event.code)) {
                deleteAdminPasswordLayout.setError(getString(
                        R.string.data_sync_nas_current_password_invalid));
                return;
            }
        }
        if ((event.action == NasSecurityViewModel.Action.DISABLE_ACCOUNT
                || event.action == NasSecurityViewModel.Action.RESTORE_ACCOUNT
                || event.action == NasSecurityViewModel.Action.DELETE_ACCOUNT)
                && "NAS_ACCOUNT_STATE_CONFLICT".equals(event.code)) {
            dismissDeleteAccountDialog();
            dismissAccountsDialog();
            showStatus(getString(R.string.nas_security_account_state_changed),
                    R.color.warning);
            viewModel.loadAccounts();
            return;
        }
        if (event.action == NasSecurityViewModel.Action.DELETE_ACCOUNT
                && deleteAccountDialog != null && deleteAccountDialog.isShowing()) {
            deleteAdminPasswordLayout.setError(authenticationError(event.code));
            return;
        }
        if (event.action == NasSecurityViewModel.Action.LOGIN
                && loginDialog != null && loginDialog.isShowing()) {
            setDialogEnabled(loginDialog, true);
            if ("NAS_INVALID_CREDENTIALS".equals(event.code)) {
                loginPasswordLayout.setError(getString(
                        R.string.data_sync_nas_error_credentials));
            } else {
                loginEmailLayout.setError(authenticationError(event.code));
            }
            return;
        }
        if (event.action == NasSecurityViewModel.Action.REGISTER
                && registrationDialog != null && registrationDialog.isShowing()) {
            setDialogEnabled(registrationDialog, true);
            if ("NAS_INVALID_INVITE".equals(event.code)) {
                registerInviteLayout.setError(getString(
                        R.string.data_sync_nas_invite_invalid));
            } else if ("NAS_WEAK_PASSWORD".equals(event.code)) {
                registerPasswordLayout.setError(getString(
                        R.string.data_sync_nas_password_policy));
            } else {
                registerInviteLayout.setError(authenticationError(event.code));
            }
            return;
        }
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

    private String authenticationError(String code) {
        if ("NAS_RATE_LIMITED".equals(code)) {
            return getString(R.string.data_sync_nas_error_rate_limited);
        }
        if ("NAS_TIMEOUT".equals(code) || "NAS_UNAVAILABLE".equals(code)) {
            return getString(R.string.data_sync_nas_error_connection);
        }
        return getString(R.string.data_sync_nas_error_generic);
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

    private static String initials(String displayName) {
        String normalized = displayName == null ? "" : displayName.trim();
        if (normalized.isEmpty()) return "?";
        String[] words = normalized.split("\\s+");
        String first = firstCodePoint(words[0]);
        String last = words.length > 1
                ? firstCodePoint(words[words.length - 1]) : "";
        return (first + last).toUpperCase(Locale.getDefault());
    }

    private static String firstCodePoint(String value) {
        if (value == null || value.isEmpty()) return "";
        int end = value.offsetByCodePoints(0, 1);
        return value.substring(0, end);
    }

    private static String formatDate(long epochMillis) {
        return DateFormat.getDateInstance(
                DateFormat.MEDIUM, Locale.getDefault()).format(new Date(epochMillis));
    }

    private static String formatDateTime(long epochMillis) {
        return new SimpleDateFormat("dd-MM-yyyy HH:mm",
                Locale.getDefault()).format(new Date(epochMillis));
    }
}
