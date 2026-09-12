package com.alidogukan.avora.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.alidogukan.avora.fertilization.FertilizationRepository;
import com.alidogukan.avora.fertilization.FertilizationPreferenceStore;
import com.alidogukan.avora.fertilization.FertilizerReminderScheduler;
import com.alidogukan.avora.models.FertilizerProduct;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.notifications.NotificationSettingsStore;
import com.alidogukan.avora.notifications.NotificationSignalScheduler;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.List;
import java.util.Map;

public final class FertilizationSettingsViewModel extends AndroidViewModel {
    private final FertilizationRepository repository = new FertilizationRepository();
    private final LiveData<List<GardenZone>> zones = repository.observeZones();
    private final LiveData<List<FertilizerProduct>> products = repository.observeProducts();
    private final MutableLiveData<Map<String, Object>> notificationBackup =
            new MutableLiveData<>();
    private final MutableLiveData<Map<String, Object>> preferenceBackup =
            new MutableLiveData<>();
    private final NotificationSettingsStore notificationSettings;
    private final FertilizationPreferenceStore preferences;

    public FertilizationSettingsViewModel(@NonNull Application application) {
        super(application);
        notificationSettings = new NotificationSettingsStore(application);
        preferences = new FertilizationPreferenceStore(application);
        repository.loadNotificationSettings(notificationBackup::postValue);
        repository.loadPreferences(preferenceBackup::postValue);
    }

    public boolean preferOrganicInputs() { return preferences.preferOrganicInputs(); }
    public boolean isCategoryEnabled(String category) {
        return notificationSettings.isCategoryEnabled(category);
    }
    public boolean applyNotificationBackup(Map<String, Object> values) {
        boolean applied = notificationSettings.applyBackup(values);
        if (applied) {
            FertilizerReminderScheduler.schedule(getApplication());
            FertilizerReminderScheduler.runNow(getApplication());
            NotificationSignalScheduler.schedule(getApplication());
        }
        return applied;
    }
    public boolean applyPreferenceBackup(Map<String, Object> values) {
        return preferences.applyBackup(values);
    }

    public LiveData<List<GardenZone>> getZones() { return zones; }
    public LiveData<List<FertilizerProduct>> getProducts() { return products; }
    public LiveData<Map<String, Object>> getNotificationBackup() {
        return notificationBackup;
    }
    public LiveData<Map<String, Object>> getPreferenceBackup() {
        return preferenceBackup;
    }

    public Task<Void> save(boolean preferOrganic, boolean reminders, boolean stockWarnings) {
        preferences.setPreferOrganicInputs(preferOrganic);
        notificationSettings.setCategoryEnabled("fertilization", reminders);
        notificationSettings.setCategoryEnabled("stock", stockWarnings);
        FertilizerReminderScheduler.schedule(getApplication());
        FertilizerReminderScheduler.runNow(getApplication());
        NotificationSignalScheduler.schedule(getApplication());
        return Tasks.whenAll(
                repository.saveNotificationSettings(notificationSettings.snapshot()),
                repository.savePreferences(preferences.snapshot())
        );
    }
}
