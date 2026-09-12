package com.alidogukan.avora.nas;

import android.content.Context;

import com.alidogukan.avora.config.AppInfo;
import com.alidogukan.avora.firebase.FirebaseDeviceAccessManager;
import com.google.android.gms.tasks.Tasks;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** Owns NAS account-security I/O so Activities remain presentation-only. */
public final class NasSecurityRepository {
    private final Context context;
    private final NasSessionStore sessionStore;

    public NasSecurityRepository(Context context) {
        this.context = context.getApplicationContext();
        sessionStore = new NasSessionStore(this.context);
    }

    public NasSession loadSession() {
        return sessionStore.load();
    }

    public NasAuthClient.Invite createInvite(NasSession session) throws Exception {
        requireAdministrator(session);
        return NasAuthClient.createInvite(session.accessToken, 24, 1);
    }

    public void revokeInvite(NasSession session, NasAuthClient.Invite invite)
            throws Exception {
        requireAdministrator(session);
        if (invite == null) throw new IllegalArgumentException("Invite is required");
        NasAuthClient.revokeInvite(session.accessToken, invite.code);
    }

    public List<NasAuthClient.AccessRequest> pendingAccessRequests(NasSession session)
            throws Exception {
        requireAdministrator(session);
        return NasAuthClient.pendingAccessRequests(
                session.accessToken, AppInfo.DEVICE_ID);
    }

    public NasAuthClient.AccessRequest requestGardenAccess(NasSession session)
            throws Exception {
        requireSession(session);
        String firebaseUid = FirebaseDeviceAccessManager.currentUserId();
        if (firebaseUid.isEmpty()) {
            throw new IllegalStateException("FIREBASE_SESSION_REQUIRED");
        }
        return NasAuthClient.requestDeviceAccess(
                session.accessToken, AppInfo.DEVICE_ID, firebaseUid);
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
    }

    public int changePassword(NasSession session, String currentPassword,
                              String newPassword) throws Exception {
        requireSession(session);
        return NasAuthClient.changePassword(
                session.accessToken, currentPassword, newPassword);
    }

    public int revokeOtherSessions(NasSession session) throws Exception {
        requireSession(session);
        return NasAuthClient.revokeOtherSessions(session.accessToken);
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

    private static void requireAdministrator(NasSession session) {
        requireSession(session);
        if (!"admin".equals(session.user.role)) {
            throw new SecurityException("NAS_FORBIDDEN");
        }
    }

    private static void requireSession(NasSession session) {
        if (session == null || !session.isActiveAt(System.currentTimeMillis() / 1000L)) {
            throw new IllegalStateException("NAS_SESSION_EXPIRED");
        }
    }
}
