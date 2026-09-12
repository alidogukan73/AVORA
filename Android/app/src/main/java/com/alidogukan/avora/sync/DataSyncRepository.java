package com.alidogukan.avora.sync;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.alidogukan.avora.config.AppInfo;
import com.alidogukan.avora.firebase.FirebaseDeviceAccessManager;
import com.alidogukan.avora.firebase.FirebaseRepository;
import com.alidogukan.avora.models.Status;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Firebase and local preference implementation for the data-sync screen. */
public final class DataSyncRepository {
    private static final String PREFS = "data_sync_preferences";
    private static final String AUTO_SYNC = "automatic_summary_sync";
    private static final String LAST_SUCCESS = "last_success_epoch_ms";
    private static final String LAST_DEVICE_DATA = "last_device_data_epoch_ms";
    private static final String LAST_SCOPE = "last_verified_scope";
    private final FirebaseDatabase database = FirebaseDatabase.getInstance();
    private final DatabaseReference deviceRef = database.getReference("devices")
            .child(AppInfo.DEVICE_ID);
    private final SharedPreferences preferences;

    public DataSyncRepository(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public LiveData<Boolean> observeConnection(Consumer<String> error) {
        return new FirebaseRepository().observeFirebaseConnection(databaseError -> {
            if (error != null) error.accept(databaseError.getMessage());
        });
    }

    public boolean automaticSyncEnabled() { return preferences.getBoolean(AUTO_SYNC, true); }

    public void setAutomaticSyncEnabled(boolean enabled) {
        preferences.edit().putBoolean(AUTO_SYNC, enabled).apply();
        deviceRef.child("status").keepSynced(enabled);
        deviceRef.child("health").keepSynced(enabled);
        deviceRef.child("zones").keepSynced(enabled);
        deviceRef.child("weather").keepSynced(enabled);
    }

    public void goOnline() { database.goOnline(); }

    public String currentFirebaseUserId() {
        return FirebaseDeviceAccessManager.currentUserId();
    }

    public Task<Void> grantDeviceAccess(String firebaseUid,
                                        String nasUserId,
                                        String email,
                                        String displayName) {
        return FirebaseDeviceAccessManager.grantDeviceAccess(
                firebaseUid, nasUserId, email, displayName);
    }

    public Task<SyncResult> readSummary() {
        List<Task<DataSnapshot>> reads = new ArrayList<>();
        reads.add(readOnce(deviceRef.child("status")));
        reads.add(readOnce(deviceRef.child("health")));
        reads.add(readOnce(deviceRef.child("zones")));
        reads.add(readOnce(deviceRef.child("weather").child("forecast")));
        return Tasks.whenAllSuccess(reads).continueWith(task -> {
            if (!task.isSuccessful()) {
                if (task.getException() != null) throw task.getException();
                throw new IllegalStateException("SYNC_READ_FAILED");
            }
            List<Object> values = task.getResult();
            if (values == null || values.size() < 4) {
                throw new IllegalStateException("SYNC_READ_INCOMPLETE");
            }
            DataSnapshot status = (DataSnapshot) values.get(0);
            DataSnapshot health = (DataSnapshot) values.get(1);
            DataSnapshot zones = (DataSnapshot) values.get(2);
            DataSnapshot weather = (DataSnapshot) values.get(3);
            boolean empty = !status.exists() && !health.exists()
                    && !zones.exists() && !weather.exists();
            return new SyncResult(empty, (int) zones.getChildrenCount(),
                    resolveDeviceEpoch(status, health), status.exists(),
                    health.exists(), weather.exists());
        });
    }

    /**
     * Firebase Database 22.x can assert when get() starts a second wire listen
     * for a reference already covered by keepSynced(true). A single-value
     * listener reuses the active sync tree and removes itself after delivery.
     */
    private static Task<DataSnapshot> readOnce(DatabaseReference reference) {
        TaskCompletionSource<DataSnapshot> result = new TaskCompletionSource<>();
        reference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                result.setResult(snapshot);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                result.setException(error.toException());
            }
        });
        return result.getTask();
    }

    public void rememberSuccess(long deviceEpoch, String scope) {
        preferences.edit()
                .putLong(LAST_SUCCESS, System.currentTimeMillis())
                .putLong(LAST_DEVICE_DATA, deviceEpoch)
                .putString(LAST_SCOPE, scope)
                .apply();
    }

    public StoredState storedState() {
        return new StoredState(preferences.getLong(LAST_SUCCESS, 0L),
                preferences.getLong(LAST_DEVICE_DATA, 0L),
                preferences.getString(LAST_SCOPE, ""));
    }

    private static long resolveDeviceEpoch(DataSnapshot statusSnapshot,
                                           DataSnapshot healthSnapshot) {
        Status status = statusSnapshot.getValue(Status.class);
        if (status != null) {
            long epoch = status.getLastSeenEpoch();
            if (epoch > 0L) return epoch < 100_000_000_000L ? epoch * 1000L : epoch;
            long parsed = parseTimestamp(status.getLastSeen());
            if (parsed > 0L) return parsed;
        }
        return parseTimestamp(healthSnapshot.child("updated_at").getValue(String.class));
    }

    private static long parseTimestamp(String raw) {
        if (raw == null || raw.trim().isEmpty()) return 0L;
        try {
            return Instant.parse(raw.trim()).toEpochMilli();
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(raw.trim()).atZone(ZoneId.systemDefault())
                        .toInstant().toEpochMilli();
            } catch (Exception ignoredAgain) {
                return 0L;
            }
        }
    }

    public static final class SyncResult {
        public final boolean empty;
        public final int zoneCount;
        public final long lastDeviceEpoch;
        public final boolean hasStatus;
        public final boolean hasHealth;
        public final boolean hasWeather;

        SyncResult(boolean empty, int zoneCount, long lastDeviceEpoch,
                   boolean hasStatus, boolean hasHealth, boolean hasWeather) {
            this.empty = empty;
            this.zoneCount = zoneCount;
            this.lastDeviceEpoch = lastDeviceEpoch;
            this.hasStatus = hasStatus;
            this.hasHealth = hasHealth;
            this.hasWeather = hasWeather;
        }
    }

    public static final class StoredState {
        public final long lastSuccess;
        public final long lastDeviceData;
        public final String scope;

        StoredState(long lastSuccess, long lastDeviceData, String scope) {
            this.lastSuccess = lastSuccess;
            this.lastDeviceData = lastDeviceData;
            this.scope = scope == null ? "" : scope;
        }
    }
}
