package com.alidogukan.avora.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.alidogukan.avora.backup.AvoraBackupManager;
import com.alidogukan.avora.firebase.GardenAccessNotificationClient;
import com.alidogukan.avora.nas.NasAutomaticBackupScheduler;
import com.alidogukan.avora.nas.NasAutomaticBackupSettings;
import com.alidogukan.avora.nas.NasPhotoBackupManager;
import com.alidogukan.avora.nas.NasPhotoBackupScheduler;
import com.alidogukan.avora.nas.NasPhotoBackupSettings;
import com.alidogukan.avora.nas.NasSession;
import com.alidogukan.avora.nas.NasSessionStore;
import com.alidogukan.avora.sync.DataSyncRepository;
import com.google.android.gms.tasks.Task;

import org.json.JSONObject;

/** Lifecycle and command boundary for explicit device summary synchronization. */
public final class DataSyncViewModel extends AndroidViewModel {
    private final DataSyncRepository repository;
    private final NasSessionStore nasSessionStore;
    private final NasAutomaticBackupSettings nasAutomaticBackupSettings;
    private final NasPhotoBackupSettings nasPhotoBackupSettings;
    private final AvoraBackupManager nasBackupManager;
    private final NasPhotoBackupManager nasPhotoBackupManager;
    private final MutableLiveData<String> connectionError = new MutableLiveData<>();
    private final LiveData<Boolean> connected;

    public DataSyncViewModel(@NonNull Application application) {
        super(application);
        repository = new DataSyncRepository(application);
        nasSessionStore = new NasSessionStore(application);
        nasAutomaticBackupSettings = new NasAutomaticBackupSettings(application);
        nasPhotoBackupSettings = new NasPhotoBackupSettings(application);
        nasBackupManager = new AvoraBackupManager(application);
        nasPhotoBackupManager = new NasPhotoBackupManager(application);
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
    public void notifyGardenAccessRequest(String requestId) {
        GardenAccessNotificationClient.notifyOwnerBestEffort(requestId);
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
    public boolean automaticNasBackupEnabled() {
        return nasAutomaticBackupSettings.isEnabled();
    }
    public void setAutomaticNasBackupEnabled(boolean enabled) {
        nasAutomaticBackupSettings.setEnabled(enabled);
        if (enabled) {
            NasAutomaticBackupScheduler.schedule(getApplication());
            NasAutomaticBackupScheduler.runNow(getApplication());
        } else {
            NasAutomaticBackupScheduler.cancel(getApplication());
        }
    }
    public boolean automaticNasPhotoBackupEnabled() {
        return nasPhotoBackupSettings.isEnabled();
    }
    public void setAutomaticNasPhotoBackupEnabled(boolean enabled) {
        nasPhotoBackupSettings.setEnabled(enabled);
        if (enabled) {
            NasPhotoBackupScheduler.schedule(getApplication());
            NasPhotoBackupScheduler.runNow(getApplication());
        } else {
            NasPhotoBackupScheduler.cancel(getApplication());
        }
    }
    public void refreshAutomaticNasSchedules() {
        if (automaticNasBackupEnabled()) {
            NasAutomaticBackupScheduler.schedule(getApplication());
            NasAutomaticBackupScheduler.runNow(getApplication());
        }
        if (automaticNasPhotoBackupEnabled()) {
            NasPhotoBackupScheduler.schedule(getApplication());
            NasPhotoBackupScheduler.runNow(getApplication());
        }
    }
    public void disableAutomaticNasSchedules() {
        setAutomaticNasBackupEnabled(false);
        setAutomaticNasPhotoBackupEnabled(false);
    }
    public NasSession loadNasSession() { return nasSessionStore.load(); }
    public void saveNasSession(NasSession session) { nasSessionStore.save(session); }
    public void clearNasSession() { nasSessionStore.clear(); }
    public Task<JSONObject> createNasBackup() { return nasBackupManager.createBackup(); }
    public BackupValidation validateNasBackup(JSONObject backup) {
        AvoraBackupManager.ValidationResult value = nasBackupManager.validate(backup);
        return new BackupValidation(value.valid, value.message, value.createdAtEpochMs,
                value.appVersion, value.zoneCount, value.recordCount);
    }
    public PhotoInspection inspectNasPhotos(String accessToken) throws Exception {
        NasPhotoBackupManager.Inspection value = nasPhotoBackupManager.inspect(accessToken);
        return new PhotoInspection(value.localCount, value.remoteCount,
                value.missingOnPhoneCount, value.missingBytes,
                value.latestUpdatedAtEpochSeconds);
    }
    public PhotoBackupResult backupNasPhotos(String accessToken) throws Exception {
        NasPhotoBackupManager.BackupResult value = nasPhotoBackupManager.backup(accessToken);
        return new PhotoBackupResult(value.localCount, value.remoteCount,
                value.uploadedCount, value.metadataUpdatedCount, value.unchangedCount,
                value.completedAtEpochMs);
    }
    public PhotoRestoreResult restoreMissingNasPhotos(String accessToken) throws Exception {
        NasPhotoBackupManager.RestoreResult value =
                nasPhotoBackupManager.restoreMissing(accessToken);
        return new PhotoRestoreResult(value.remoteCount, value.restoredCount,
                value.existingCount);
    }
    public void recordNasPhotoBackupSuccess(long epochMillis, int remoteCount) {
        nasPhotoBackupSettings.recordSuccess(epochMillis, remoteCount);
    }

    public static final class BackupValidation {
        public final boolean valid;
        public final String message;
        public final long createdAtEpochMs;
        public final String appVersion;
        public final int zoneCount;
        public final int recordCount;

        BackupValidation(boolean valid, String message, long createdAtEpochMs,
                         String appVersion, int zoneCount, int recordCount) {
            this.valid = valid;
            this.message = message;
            this.createdAtEpochMs = createdAtEpochMs;
            this.appVersion = appVersion;
            this.zoneCount = zoneCount;
            this.recordCount = recordCount;
        }
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

    public static final class PhotoInspection {
        public final int localCount;
        public final int remoteCount;
        public final int missingOnPhoneCount;
        public final long missingBytes;
        public final long latestUpdatedAtEpochSeconds;

        PhotoInspection(int localCount, int remoteCount, int missingOnPhoneCount,
                        long missingBytes, long latestUpdatedAtEpochSeconds) {
            this.localCount = localCount;
            this.remoteCount = remoteCount;
            this.missingOnPhoneCount = missingOnPhoneCount;
            this.missingBytes = missingBytes;
            this.latestUpdatedAtEpochSeconds = latestUpdatedAtEpochSeconds;
        }
    }

    public static final class PhotoBackupResult {
        public final int localCount;
        public final int remoteCount;
        public final int uploadedCount;
        public final int metadataUpdatedCount;
        public final int unchangedCount;
        public final long completedAtEpochMs;

        PhotoBackupResult(int localCount, int remoteCount, int uploadedCount,
                          int metadataUpdatedCount, int unchangedCount,
                          long completedAtEpochMs) {
            this.localCount = localCount;
            this.remoteCount = remoteCount;
            this.uploadedCount = uploadedCount;
            this.metadataUpdatedCount = metadataUpdatedCount;
            this.unchangedCount = unchangedCount;
            this.completedAtEpochMs = completedAtEpochMs;
        }
    }

    public static final class PhotoRestoreResult {
        public final int remoteCount;
        public final int restoredCount;
        public final int existingCount;

        PhotoRestoreResult(int remoteCount, int restoredCount, int existingCount) {
            this.remoteCount = remoteCount;
            this.restoredCount = restoredCount;
            this.existingCount = existingCount;
        }
    }
}
