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

/** Best-effort owner notification after the NAS has accepted an access request. */
public final class GardenAccessNotificationClient {
    private static final Pattern REQUEST_ID =
            Pattern.compile("^[0-9a-fA-F-]{36}$");

    private GardenAccessNotificationClient() { }

    public static Task<Void> notifyOwner(String requestId) {
        String cleanRequestId = safe(requestId);
        if (!REQUEST_ID.matcher(cleanRequestId).matches()) {
            return Tasks.forException(
                    new IllegalArgumentException("Invalid access request id"));
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getUid().isBlank()) {
            return Tasks.forException(
                    new IllegalStateException("Firebase session is required"));
        }
        String firebaseUid = user.getUid();
        Map<String, Object> payload = new HashMap<>();
        payload.put("request_id", cleanRequestId);
        payload.put("firebase_uid", firebaseUid);
        payload.put("requested_at_epoch", ServerValue.TIMESTAMP);
        payload.put("source", "android");
        return FirebaseDatabase.getInstance().getReference("access_request_notifications")
                .child(AppInfo.DEVICE_ID)
                .child(firebaseUid)
                .setValue(payload);
    }

    public static void notifyOwnerBestEffort(String requestId) {
        notifyOwner(requestId).addOnFailureListener(ignored -> {
            // The NAS request remains valid; the administrator can still open it manually.
        });
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
