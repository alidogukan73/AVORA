package com.alidogukan.avora.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.alidogukan.avora.firebase.FirebaseRepository;
import com.alidogukan.avora.models.GardenNotification;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.SeedlingBatch;
import com.alidogukan.avora.notifications.GardenNotificationManager;
import com.alidogukan.avora.notifications.NotificationSettingsStore;
import com.alidogukan.avora.fertilization.FertilizerOutcomeFollowUpPolicy;
import com.alidogukan.avora.seedling.SeedlingRepository;

import java.util.List;
import java.util.function.Consumer;

/** Single UI boundary for local/cloud notification state and notification actions. */
public final class NotificationCenterViewModel extends AndroidViewModel {
    public static final String ACTION_NOTIFICATIONS_CHANGED =
            GardenNotificationManager.ACTION_NOTIFICATIONS_CHANGED;
    private final GardenNotificationManager manager;
    private final MediatorLiveData<List<GardenNotification>> notifications =
            new MediatorLiveData<>();
    private final LiveData<List<GardenZone>> zones;
    private final SeedlingRepository seedlingRepository;

    public NotificationCenterViewModel(@NonNull Application application) {
        super(application);
        manager = new GardenNotificationManager(application);
        FirebaseRepository repository = new FirebaseRepository();
        seedlingRepository = new SeedlingRepository();
        zones = repository.observeGardenZones();
        notifications.setValue(manager.localNotifications());
        notifications.addSource(repository.observeGardenNotifications(), values -> {
            if (values != null) manager.applyCloudSnapshot(values);
            refresh();
        });
        notifications.addSource(repository.observeGardenNotificationDeletions(), deletions -> {
            if (deletions != null) manager.applyCloudDeletions(deletions);
            refresh();
        });
    }

    public LiveData<List<GardenNotification>> getNotifications() { return notifications; }
    public LiveData<List<GardenZone>> getZones() { return zones; }
    public LiveData<SeedlingBatch> getSeedlingBatch(String batchId) {
        return seedlingRepository.observeBatch(batchId);
    }
    public void refresh() { notifications.setValue(manager.localNotifications()); }
    public GardenNotification find(String id) { return manager.findLocalById(id); }
    public String categoryFor(String type) { return NotificationSettingsStore.categoryFor(type); }
    public String fertilizerApplicationId(String sourceKey) {
        return FertilizerOutcomeFollowUpPolicy.applicationIdFromSource(sourceKey);
    }
    public void setState(GardenNotification value, boolean read, boolean saved) {
        manager.setState(value, read, saved);
        refresh();
    }
    public void clearUnsaved(Consumer<Integer> completed) {
        manager.clearUnsavedNotifications(result -> {
            refresh();
            if (completed != null) completed.accept(result);
        });
    }
    public void delete(GardenNotification value, Consumer<Boolean> completed) {
        manager.deleteNotification(value, result -> {
            refresh();
            if (completed != null) completed.accept(result);
        });
    }
}
