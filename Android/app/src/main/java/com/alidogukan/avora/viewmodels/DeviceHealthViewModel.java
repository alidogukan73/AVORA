package com.alidogukan.avora.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.alidogukan.avora.R;
import com.alidogukan.avora.firebase.FirebaseRepository;
import com.alidogukan.avora.language.AvoraLanguageManager;
import com.alidogukan.avora.models.Health;
import com.alidogukan.avora.models.SeedlingTelemetry;
import com.alidogukan.avora.seedling.SeedlingRepository;

/** Lifecycle-aware device health state and device commands. */
public class DeviceHealthViewModel extends AndroidViewModel {
    private final FirebaseRepository repository = new FirebaseRepository();
    private final MediatorLiveData<Health> health = new MediatorLiveData<>();
    private final LiveData<SeedlingTelemetry> nodeTelemetry;
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> administrator = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> restarting = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> restartMessage = new MutableLiveData<>();
    private int accessCheckGeneration;

    public LiveData<Boolean> getAdministrator() { return administrator; }
    public LiveData<Boolean> getRestarting() { return restarting; }
    public LiveData<Integer> getRestartMessage() { return restartMessage; }
    public void consumeRestartMessage() { restartMessage.setValue(null); }

    public void refreshAdministrator() {
        int generation = ++accessCheckGeneration;
        administrator.setValue(false);
        repository.isCurrentUserDeviceOwner().addOnCompleteListener(result -> {
            if (generation == accessCheckGeneration) administrator.setValue(
                    result.isSuccessful() && Boolean.TRUE.equals(result.getResult()));
        });
    }

    public DeviceHealthViewModel(@NonNull Application application) {
        super(application);
        nodeTelemetry = new SeedlingRepository().observeLatestTelemetry(
                "seedling-001", () -> error.setValue(
                        AvoraLanguageManager.localizedContext(getApplication())
                                .getString(R.string.seedling_read_failed)));
        LiveData<Health> source = repository.observeHealth(databaseError -> {
            loading.setValue(false);
            error.setValue(AvoraLanguageManager.localizedContext(
                    getApplication()).getString(
                    R.string.device_health_read_error));
        });
        health.addSource(source, value -> {
            health.setValue(value);
            error.setValue(null);
            loading.setValue(false);
        });
    }

    public LiveData<Health> getHealth() { return health; }
    public LiveData<SeedlingTelemetry> getNodeTelemetry() { return nodeTelemetry; }
    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<String> getError() { return error; }
    public void restartDevice() {
        if (Boolean.TRUE.equals(restarting.getValue())) return;
        if (!Boolean.TRUE.equals(administrator.getValue())) {
            restartMessage.setValue(R.string.health_restart_admin_required);
            return;
        }
        restarting.setValue(true);
        repository.restartDevice().addOnCompleteListener(result -> {
            restarting.setValue(false);
            if (result.isSuccessful()) {
                restartMessage.setValue(R.string.runtime_restart_sent);
                return;
            }
            String code = result.getException() == null ? "" : result.getException().getMessage();
            int message = R.string.health_restart_failed;
            if (com.alidogukan.avora.device.DeviceRestartPolicy.ADMIN_REQUIRED.equals(code)) {
                administrator.setValue(false);
                message = R.string.health_restart_admin_required;
            } else if (com.alidogukan.avora.device.DeviceRestartPolicy.WATERING_ACTIVE.equals(code)) {
                message = R.string.health_restart_watering_active;
            } else if (com.alidogukan.avora.device.DeviceRestartPolicy.STATUS_UNAVAILABLE.equals(code)) {
                message = R.string.health_restart_status_unavailable;
            } else if (com.alidogukan.avora.device.DeviceRestartPolicy.ALREADY_PENDING.equals(code)) {
                message = R.string.health_restart_pending;
            }
            restartMessage.setValue(message);
        });
    }
}
