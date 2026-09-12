package com.alidogukan.avora.firebase;

import com.alidogukan.avora.config.AppInfo;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Owner-only Firebase access grants for invited NAS family members. */
public final class FirebaseDeviceAccessManager {
    private static final Pattern FIREBASE_UID =
            Pattern.compile("^[A-Za-z0-9_-]{16,128}$");
    private static final Pattern NAS_USER_ID =
            Pattern.compile("^[0-9a-fA-F-]{36}$");

    private FirebaseDeviceAccessManager() { }

    public static String currentUserId() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user == null ? "" : user.getUid();
    }

    public static Task<Void> grantDeviceAccess(String firebaseUid,
                                               String nasUserId,
                                               String email,
                                               String displayName) {
        FirebaseUser owner = FirebaseAuth.getInstance().getCurrentUser();
        if (owner == null) {
            return Tasks.forException(
                    new IllegalStateException("Firebase owner session is required"));
        }
        String uid = safe(firebaseUid);
        String userId = safe(nasUserId);
        String cleanEmail = safe(email);
        String cleanName = safe(displayName);
        if (!FIREBASE_UID.matcher(uid).matches()
                || !NAS_USER_ID.matcher(userId).matches()
                || cleanEmail.isEmpty() || cleanEmail.length() > 254
                || cleanName.isEmpty() || cleanName.length() > 120) {
            return Tasks.forException(
                    new IllegalArgumentException("Invalid access request"));
        }

        Map<String, Object> values = new HashMap<>();
        values.put("approved", true);
        values.put("firebase_uid", uid);
        values.put("nas_user_id", userId);
        values.put("email", cleanEmail);
        values.put("display_name", cleanName);
        values.put("approved_by", owner.getUid());
        values.put("approved_at", ServerValue.TIMESTAMP);
        return FirebaseDatabase.getInstance().getReference("device_access")
                .child(AppInfo.DEVICE_ID)
                .child(uid)
                .setValue(values);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
