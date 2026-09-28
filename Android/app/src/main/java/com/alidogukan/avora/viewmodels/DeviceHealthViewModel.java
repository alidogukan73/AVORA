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
import com.alidogukan.avora.models.Status;

/** Lifecycle-aware device health state and device commands. */
public class DeviceHealthViewModel extends AndroidViewModel {
    private final FirebaseRepository repository = new FirebaseRepository();
    private final MediatorLiveData<Health> health = new MediatorLiveData<>();
    private final MediatorLiveData<Status> status = new MediatorLiveData<>();
    private final MediatorLiveData<Boolean> connected = new MediatorLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public DeviceHealthViewModel(@NonNull Application application) {
        super(application);
        LiveData<Health> source = repository.observeHealth(databaseError -> {
            health.setValue(null);
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
        status.addSource(repository.observeStatus(databaseError -> status.setValue(null)), status::setValue);
        connected.addSource(repository.observeFirebaseConnection(databaseError -> connected.setValue(false)),
                value -> connected.setValue(Boolean.TRUE.equals(value)));
    }

    public LiveData<Health> getHealth() { return health; }
    public LiveData<Status> getStatus() { return status; }
    public LiveData<Boolean> getConnection() { return connected; }
    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<String> getError() { return error; }
    public void restartDevice() { repository.restartDevice(); }
}
