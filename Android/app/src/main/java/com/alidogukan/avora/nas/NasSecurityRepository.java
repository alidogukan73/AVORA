package com.alidogukan.avora.nas;

import android.content.Context;

import com.alidogukan.avora.config.AppInfo;
import com.alidogukan.avora.firebase.FirebaseDeviceAccessManager;
import com.alidogukan.avora.firebase.GardenAccessNotificationClient;
import com.google.android.gms.tasks.Tasks;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** Owns NAS account-security I/O so Activities remain presentation-only. */
public final class NasSecurityRepository {
    private static final long SLIDING_SESSION_SECONDS = 30L * 24L * 60L * 60L;
    private final Context context;
    private final NasSessionStore sessionStore;
    private final NasDeviceIdentity deviceIdentity;

    public NasSecurityRepository(Context context) {
        this.context = context.getApplicationContext();
        sessionStore = new NasSessionStore(this.context);
        deviceIdentity = NasDeviceIdentity.get(this.context);
    }

    public NasSession loadSession() {
        return sessionStore.load();
    }

    public NasSession login(String email, String password) throws Exception {
        return saveSession(NasAuthClient.login(
                email, password, deviceIdentity.id, deviceIdentity.name));
    }

    public NasSession register(String inviteCode, String email,
                               String displayName, String password) throws Exception {
        return saveSession(NasAuthClient.register(
                inviteCode, email, displayName, password,
                deviceIdentity.id, deviceIdentity.name));
    }

    public NasAuthClient.Invite createInvite(NasSession session) throws Exception {
        requireAdministrator(session);
        NasAuthClient.Invite invite = NasAuthClient.createInvite(
                session.accessToken, 24, 1);
        touch(session);
        return invite;
    }

    public void revokeInvite(NasSession session, NasAuthClient.Invite invite)
            throws Exception {
        requireAdministrator(session);
        if (invite == null) throw new IllegalArgumentException("Invite is required");
        NasAuthClient.revokeInvite(session.accessToken, invite.code);
        touch(session);
    }

    public List<NasAuthClient.AccountSummary> accounts(NasSession session)
            throws Exception {
        requireAdministrator(session);
        List<NasAuthClient.AccountSummary> accounts = NasAuthClient.accounts(
                session.accessToken, AppInfo.DEVICE_ID);
        touch(session);
        return accounts;
    }

    public List<NasAuthClient.SessionSummary> sessions(NasSession session)
            throws Exception {
        requireSession(session);
        NasAuthClient.identifyCurrentSession(
                session.accessToken, deviceIdentity.id, deviceIdentity.name);
        List<NasAuthClient.SessionSummary> sessions = NasAuthClient.sessions(
                session.accessToken);
        touch(session);
        return sessions;
    }

    public List<NasAuthClient.AccessRequest> pendingAccessRequests(NasSession session)
            throws Exception {
        requireAdministrator(session);
        List<NasAuthClient.AccessRequest> requests = NasAuthClient.pendingAccessRequests(
                session.accessToken, AppInfo.DEVICE_ID);
        touch(session);
        return requests;
    }

    public NasAuthClient.AccessRequest requestGardenAccess(NasSession session)
            throws Exception {
        requireSession(session);
        String firebaseUid = FirebaseDeviceAccessManager.currentUserId();
        if (firebaseUid.isEmpty()) {
            throw new IllegalStateException("FIREBASE_SESSION_REQUIRED");
        }
        NasAuthClient.AccessRequest request = NasAuthClient.requestDeviceAccess(
                session.accessToken, AppInfo.DEVICE_ID, firebaseUid);
        if (request != null && "pending".equals(request.status)) {
            GardenAccessNotificationClient.notifyOwnerBestEffort(request.id);
        }
        touch(session);
        return request;
    }

    public void approveAccessRequest(NasSession session,
                                     NasAuthClient.AccessRequest request)
            throws Exception {
        requireAdministrator(session);
        if (request == null) throw new IllegalArgumentException("Request is required");
        Tasks.await(FirebaseDeviceAccessManager.grantDeviceAccess(
                        request.firebaseUid,
                        request.userId,
                        request.email,
                        request.displayName),
                20, TimeUnit.SECONDS);
        NasAuthClient.approveAccessRequest(session.accessToken, request.id);
        touch(session);
    }

    public void keepInactiveAccess(NasSession session,
                                   NasAuthClient.AccountSummary account)
            throws Exception {
        requireAdministrator(session);
        requireFamilyAccount(account);
        NasAuthClient.keepInactiveDeviceAccess(
                session.accessToken, account.id, AppInfo.DEVICE_ID);
        touch(session);
    }

    public int revokeDeviceAccess(NasSession session,
                                  NasAuthClient.AccountSummary account)
            throws Exception {
        requireAdministrator(session);
        requireFamilyAccount(account);
        Tasks.await(FirebaseDeviceAccessManager.removeDeviceAccess(account.firebaseUid),
                20, TimeUnit.SECONDS);
        NasAuthClient.DeviceAccessRevocation revoked =
                NasAuthClient.revokeDeviceAccess(
                        session.accessToken, account.id, AppInfo.DEVICE_ID);
        if (!account.firebaseUid.equals(revoked.firebaseUid)) {
            throw new IllegalStateException("NAS_INVALID_RESPONSE");
        }
        touch(session);
        return revoked.revokedSessions;
    }

    public NasAuthClient.AccountDisableResult disableAccount(
            NasSession session, NasAuthClient.AccountSummary account)
            throws Exception {
        requireAdministrator(session);
        requireActiveFamilyAccount(account);
        if (!account.firebaseUid.isEmpty()) {
            Tasks.await(FirebaseDeviceAccessManager.removeDeviceAccess(
                    account.firebaseUid), 20, TimeUnit.SECONDS);
        }
        NasAuthClient.AccountDisableResult result = NasAuthClient.disableAccount(
                session.accessToken, account.id);
        touch(session);
        return result;
    }

    public void restoreAccount(NasSession session,
                               NasAuthClient.AccountSummary account)
            throws Exception {
        requireAdministrator(session);
        if (account == null || !"user".equals(account.role)
                || account.active || !account.canRestore) {
            throw new IllegalArgumentException("Restorable family account is required");
        }
        NasAuthClient.restoreAccount(session.accessToken, account.id);
        touch(session);
    }

    public void permanentlyDeleteAccount(
            NasSession session,
            NasAuthClient.AccountSummary account,
            String currentPassword) throws Exception {
        requireAdministrator(session);
        if (account == null || !"user".equals(account.role)
                || account.active || !account.canPermanentlyDelete) {
            throw new IllegalArgumentException("Deletable family account is required");
        }
        if (!account.firebaseUid.isEmpty()) {
            Tasks.await(FirebaseDeviceAccessManager.removeDeviceAccess(
                    account.firebaseUid), 20, TimeUnit.SECONDS);
        }
        NasAuthClient.permanentlyDeleteAccount(
                session.accessToken, account.id, currentPassword);
        touch(session);
    }

    public int changePassword(NasSession session, String currentPassword,
                              String newPassword) throws Exception {
        requireSession(session);
        int revoked = NasAuthClient.changePassword(
                session.accessToken, currentPassword, newPassword);
        touch(session);
        return revoked;
    }

    public int revokeOtherSessions(NasSession session) throws Exception {
        requireSession(session);
        int revoked = NasAuthClient.revokeOtherSessions(session.accessToken);
        touch(session);
        return revoked;
    }

    public void expireLocalSession() {
        sessionStore.clear();
    }

    public void disconnect(NasSession session) {
        new NasAutomaticBackupSettings(context).setEnabled(false);
        NasAutomaticBackupScheduler.cancel(context);
        new NasPhotoBackupSettings(context).setEnabled(false);
        NasPhotoBackupScheduler.cancel(context);
        sessionStore.clear();
        if (session == null) return;
        try {
            NasAuthClient.logout(session.accessToken);
        } catch (Exception ignored) {
            // Local access is already removed; the remote token expires automatically.
        }
    }

    private NasSession saveSession(NasSession session) {
        sessionStore.save(session);
        if (new NasAutomaticBackupSettings(context).isEnabled()) {
            NasAutomaticBackupScheduler.schedule(context);
        }
        if (new NasPhotoBackupSettings(context).isEnabled()) {
            NasPhotoBackupScheduler.schedule(context);
        }
        return session;
    }

    private static void requireAdministrator(NasSession session) {
        requireSession(session);
        if (!"admin".equals(session.user.role)) {
            throw new SecurityException("NAS_FORBIDDEN");
        }
    }

    private static void requireSession(NasSession session) {
        if (session == null || session.accessToken.isEmpty()) {
            throw new IllegalStateException("NAS_SESSION_EXPIRED");
        }
    }

    private static void requireFamilyAccount(NasAuthClient.AccountSummary account) {
        if (account == null || !"user".equals(account.role)
                || !"approved".equals(account.accessStatus)
                || account.firebaseUid.isEmpty()) {
            throw new IllegalArgumentException("Approved family access is required");
        }
    }

    private static void requireActiveFamilyAccount(
            NasAuthClient.AccountSummary account) {
        if (account == null || !"user".equals(account.role) || !account.active) {
            throw new IllegalArgumentException("Active family account is required");
        }
    }

    private void touch(NasSession session) {
        sessionStore.refresh(session, SLIDING_SESSION_SECONDS);
    }
}
