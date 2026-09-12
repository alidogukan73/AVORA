package com.alidogukan.avora.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.alidogukan.avora.sync.DataSyncRepository;
import com.google.android.gms.tasks.Task;

/** Lifecycle and command boundary for explicit device summary synchronization. */
public final class DataSyncViewModel extends AndroidViewModel {
    private final DataSyncRepository repository;
    private final MutableLiveData<String> connectionError = new MutableLiveData<>();
    private final LiveData<Boolean> connected;

    public DataSyncViewModel(@NonNull Application application) {
        super(application);
        repository = new DataSyncRepository(application);
        connected = repository.observeConnection(connectionError::setValue);
    }

    public LiveData<Boolean> getConnected() { return connected; }
    public LiveData<String> getConnectionError() { return connectionError; }
    public boolean automaticSyncEnabled() { return repository.automaticSyncEnabled(); }
    public void setAutomaticSyncEnabled(boolean enabled) {
        repository.setAutomaticSyncEnabled(enabled);
    }
    public void goOnline() { repository.goOnline(); }
    public String currentFirebaseUserId() {
        return repository.currentFirebaseUserId();
    }
    public Task<Void> grantDeviceAccess(String firebaseUid, String nasUserId,
                                        String email, String displayName) {
        return repository.grantDeviceAccess(firebaseUid, nasUserId, email, displayName);
    }
    public Task<SyncResult> readSummary() {
        return repository.readSummary().continueWith(task -> {
            DataSyncRepository.SyncResult value = task.getResult();
            return new SyncResult(value.empty, value.zoneCount, value.hasStatus,
                    value.hasHealth, value.hasWeather, value.lastDeviceEpoch);
        });
    }
    public void rememberSuccess(long deviceEpoch, String scope) {
        repository.rememberSuccess(deviceEpoch, scope);
    }
    public StoredState storedState() {
        DataSyncRepository.StoredState value = repository.storedState();
        return new StoredState(value.lastSuccess, value.lastDeviceData, value.scope);
    }

    public static final class SyncResult {
        public final boolean empty;
        public final int zoneCount;
        public final boolean hasStatus;
        public final boolean hasHealth;
        public final boolean hasWeather;
        public final long lastDeviceEpoch;

        SyncResult(boolean empty, int zoneCount, boolean hasStatus, boolean hasHealth,
                   boolean hasWeather, long lastDeviceEpoch) {
            this.empty = empty;
            this.zoneCount = zoneCount;
            this.hasStatus = hasStatus;
            this.hasHealth = hasHealth;
            this.hasWeather = hasWeather;
            this.lastDeviceEpoch = lastDeviceEpoch;
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
