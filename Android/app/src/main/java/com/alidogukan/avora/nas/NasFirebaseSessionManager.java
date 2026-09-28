package com.alidogukan.avora.nas;

import com.alidogukan.avora.config.AppInfo;
import com.alidogukan.avora.firebase.DeviceOwnershipPolicy;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;

/** Server-issued, stable NAS administrator identities survive an application reinstall. */
public final class NasFirebaseSessionManager {
    private NasFirebaseSessionManager() { }
    private static final ExecutorService RESTORE_EXECUTOR = Executors.newSingleThreadExecutor();

    public static Task<Void> restoreAdministrator(NasSessionStore store) {
        TaskCompletionSource<Void> result = new TaskCompletionSource<>();
        RESTORE_EXECUTOR.execute(() -> {
            try {
                NasSession session = store.load();
                if (session != null && "admin".equals(session.user.role)) ensureForStoredSession(store, session);
                result.setResult(null);
            }
            catch (Exception error) { result.setException(error); }
        });
        return result.getTask();
    }

    public static String ownerUid(String nasUserId) {
        return "avora_nas_" + UUID.fromString(nasUserId).toString().replace("-", "");
    }

    // Call on a worker thread. Serializes login/refresh across both NAS entry points.
    public static synchronized void ensureForStoredSession(NasSessionStore store, NasSession session) throws Exception {
        if (!isCurrentSession(store, session)) throw new IllegalStateException("NAS_SESSION_CHANGED");
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser current = auth.getCurrentUser();
        if (!"admin".equals(session.user.role)) {
            if (current != null && (current.getUid().startsWith("avora_nas_")
                    || DeviceOwnershipPolicy.ownsDevice(Tasks.await(current.getIdToken(false),
                            20, TimeUnit.SECONDS).getClaims(), AppInfo.DEVICE_ID))) {
                auth.signOut();
                Tasks.await(auth.signInAnonymously(), 20, TimeUnit.SECONDS);
            }
            return;
        }
        String expectedUid = ownerUid(session.user.id);
        try {
            if (current != null && expectedUid.equals(current.getUid())
                    && DeviceOwnershipPolicy.ownsDevice(Tasks.await(current.getIdToken(false),
                            20, TimeUnit.SECONDS).getClaims(), AppInfo.DEVICE_ID)) return;
            NasAuthClient.FirebaseOwnerSession identity = NasAuthClient.firebaseOwnerSession(session.accessToken);
            if (!expectedUid.equals(identity.uid) || !AppInfo.DEVICE_ID.equals(identity.deviceId)) {
                throw new IllegalStateException("NAS_FIREBASE_IDENTITY_UNAVAILABLE");
            }
            FirebaseUser owner = Tasks.await(auth.signInWithCustomToken(identity.token),
                    20, TimeUnit.SECONDS).getUser();
            if (!isCurrentSession(store, session)) {
                if (auth.getCurrentUser() != null && expectedUid.equals(auth.getCurrentUser().getUid())) auth.signOut();
                throw new IllegalStateException("NAS_SESSION_CHANGED");
            }
            if (owner == null || !expectedUid.equals(owner.getUid())
                    || !DeviceOwnershipPolicy.ownsDevice(Tasks.await(owner.getIdToken(true),
                            20, TimeUnit.SECONDS).getClaims(), AppInfo.DEVICE_ID)) {
                auth.signOut();
                throw new IllegalStateException("NAS_FIREBASE_IDENTITY_UNAVAILABLE");
            }
        } catch (Exception error) {
            if ("NAS_SESSION_EXPIRED".equals(error.getMessage()) && isCurrentSession(store, session)) {
                store.clear();
                signOutNasOwner();
            }
            if ("NAS_SESSION_CHANGED".equals(error.getMessage())) throw error;
            if (error instanceof NasApiException) throw error;
            throw new IllegalStateException("NAS_FIREBASE_IDENTITY_UNAVAILABLE");
        }
    }

    public static void signOutNasOwner() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser current = auth.getCurrentUser();
        if (current != null && current.getUid().startsWith("avora_nas_")) auth.signOut();
    }

    private static boolean isCurrentSession(NasSessionStore store, NasSession expected) {
        NasSession current = store.load();
        return current != null && current.accessToken.equals(expected.accessToken);
    }
}
