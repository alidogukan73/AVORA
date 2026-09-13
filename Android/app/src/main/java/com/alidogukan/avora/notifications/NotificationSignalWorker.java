package com.alidogukan.avora.notifications;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.alidogukan.avora.R;
import com.alidogukan.avora.config.AppInfo;
import com.alidogukan.avora.firebase.FirebaseWorkerAuthentication;
import com.alidogukan.avora.language.AvoraLanguageManager;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.Health;
import com.alidogukan.avora.models.SeedlingBatch;
import com.alidogukan.avora.models.SeedlingNodeState;
import com.alidogukan.avora.models.Status;
import com.alidogukan.avora.models.WateringHistory;
import com.alidogukan.avora.plantassistant.PlantFollowUpStore;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Periodically checks Firebase and due local tasks while AVORA is not open. */
public final class NotificationSignalWorker extends Worker {
    public NotificationSignalWorker(@NonNull Context context,
                                    @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = AvoraLanguageManager.localizedContext(getApplicationContext());
        publishDuePlantFollowUps(context);
        NasInactiveAccessMonitor.check(context);
        NotificationSignalReadPlan plan = NotificationSignalReadPlan.from(
                new NotificationSettingsStore(context));
        GardenNotificationManager notifications = new GardenNotificationManager(context);
        boolean pendingCloudDeletions = notifications.hasPendingCloudDeletions();
        if (!plan.needsFirebase() && !pendingCloudDeletions) return Result.success();

        try {
            if (!FirebaseConnectionProbe.awaitConnected(15, TimeUnit.SECONDS)) {
                return Result.retry();
            }
            if (!FirebaseWorkerAuthentication.awaitAuthorized(20, TimeUnit.SECONDS)) {
                return Result.success();
            }
            if (pendingCloudDeletions) {
                Tasks.await(notifications.syncPendingCloudDeletions(),
                        20, TimeUnit.SECONDS);
            }
            if (!plan.needsFirebase()) return Result.success();

            DatabaseReference deviceRef = FirebaseDatabase.getInstance()
                    .getReference("devices").child(AppInfo.DEVICE_ID);
            Task<DataSnapshot> forecastRead = plan.weather ? deviceRef
                    .child("weather").child("forecast").get() : null;
            Task<DataSnapshot> zonesRead = plan.irrigation
                    ? deviceRef.child("zones").get() : null;
            Task<DataSnapshot> statusRead = plan.device
                    ? deviceRef.child("status").get() : null;
            Task<DataSnapshot> healthRead = plan.device
                    ? deviceRef.child("health").get() : null;
            Task<DataSnapshot> wateringRead = plan.irrigation
                    ? deviceRef.child("watering_history")
                    .orderByKey().limitToLast(50).get() : null;
            Task<DataSnapshot> seedlingBatchesRead = plan.seedling ? deviceRef
                    .child("seedling").child("batches").get() : null;
            Task<DataSnapshot> seedlingNodesRead = plan.seedling ? deviceRef
                    .child("seedling").child("nodes").get() : null;

            List<Task<?>> reads = new ArrayList<>();
            add(reads, forecastRead);
            add(reads, zonesRead);
            add(reads, statusRead);
            add(reads, healthRead);
            add(reads, wateringRead);
            add(reads, seedlingBatchesRead);
            add(reads, seedlingNodesRead);
            Tasks.await(Tasks.whenAll(reads), 20, TimeUnit.SECONDS);

            if (!FirebaseConnectionProbe.awaitConnected(10, TimeUnit.SECONDS)) {
                return Result.retry();
            }
            if (forecastRead != null) {
                DataSnapshot forecast = forecastRead.getResult();
                NotificationSignalCoordinator.evaluateWeather(context,
                        number(forecast.child("tomorrow_temperature_max")),
                        number(forecast.child("tomorrow_rain_probability")),
                        number(forecast.child("tomorrow_wind_max")),
                        LocalDate.now().plusDays(1).toString(),
                        longNumber(forecast.child("updated_at_epoch")));
            }

            ArrayList<GardenZone> zones = new ArrayList<>();
            if (zonesRead != null) {
                for (DataSnapshot child : zonesRead.getResult().getChildren()) {
                    GardenZone zone = child.getValue(GardenZone.class);
                    if (zone != null) {
                        if (zone.getZone_id() == null || zone.getZone_id().isBlank()) {
                            zone.setZone_id(child.getKey());
                        }
                        zones.add(zone);
                    }
                }
                NotificationSignalCoordinator.evaluateIrrigationAi(context, zones);
            }

            ArrayList<SeedlingBatch> seedlingBatches = new ArrayList<>();
            if (seedlingBatchesRead != null) {
                for (DataSnapshot child : seedlingBatchesRead.getResult().getChildren()) {
                    SeedlingBatch batch = child.getValue(SeedlingBatch.class);
                    if (batch == null) continue;
                    if (batch.getBatch_id().isBlank()) batch.setBatch_id(child.getKey());
                    seedlingBatches.add(batch);
                }
            }
            Map<String, SeedlingNodeState> seedlingNodes = new LinkedHashMap<>();
            if (seedlingNodesRead != null) {
                for (DataSnapshot child : seedlingNodesRead.getResult().getChildren()) {
                    SeedlingNodeState node = child.getValue(SeedlingNodeState.class);
                    if (node != null && child.getKey() != null) {
                        seedlingNodes.put(child.getKey(), node);
                    }
                }
            }
            if (plan.seedling) {
                Map<String, Long> latestSeedlingLogs = plan.seedlingLogs
                        ? latestSeedlingLogEpochs(deviceRef, seedlingBatches)
                        : new LinkedHashMap<>();
                SeedlingNotificationCoordinator.evaluate(
                        context, seedlingBatches, seedlingNodes, latestSeedlingLogs);
            }

            Status status = statusRead == null ? null
                    : statusRead.getResult().getValue(Status.class);
            if (statusRead != null && status == null) return Result.retry();
            Health health = healthRead == null ? null
                    : healthRead.getResult().getValue(Health.class);

            if (status != null) {
                long nowEpoch = System.currentTimeMillis() / 1000L;
                boolean deviceOffline = NotificationPolicy.isDeviceOffline(
                        status.isOnline(), status.getLastSeenEpoch(), nowEpoch,
                        NotificationPolicy.DEVICE_HEARTBEAT_MAX_AGE_SECONDS);

                // The snapshot seeds an outage; a live recheck confirms it.
                if (deviceOffline) {
                    NotificationSignalCoordinator.synchronizeDeviceConnection(context, status);
                    DeviceConnectionVerificationWorker.schedule(context);
                } else {
                    NotificationSignalCoordinator.evaluateDeviceConnection(context, status);
                    if (notifications.isIncidentActive("device_offline")) {
                        DeviceConnectionVerificationWorker.scheduleRecovery(context);
                    } else {
                        DeviceConnectionVerificationWorker.cancel(context);
                    }
                }
                NotificationSignalCoordinator.evaluateDevice(context, status, health);
            }

            if (wateringRead != null) {
                ArrayList<WateringHistory> watering = new ArrayList<>();
                for (DataSnapshot child : wateringRead.getResult().getChildren()) {
                    WateringHistory record = child.getValue(WateringHistory.class);
                    if (record != null) {
                        record.setRecordId(child.getKey());
                        watering.add(record);
                    }
                }
                NotificationSignalCoordinator.evaluateWatering(context, watering, zones);
            }
            return Result.success();
        } catch (Exception ignored) {
            return Result.retry();
        }
    }

    private static void add(List<Task<?>> tasks, Task<?> task) {
        if (task != null) tasks.add(task);
    }

    private void publishDuePlantFollowUps(Context context) {
        NotificationSettingsStore settings = new NotificationSettingsStore(context);
        if (!settings.isReminderEnabled("plant")) return;
        PlantFollowUpStore followUps = new PlantFollowUpStore(context);
        GardenNotificationManager notifications = new GardenNotificationManager(context);
        for (PlantFollowUpStore.DueTask task
                : followUps.dueUnnotified(System.currentTimeMillis() / 1000L)) {
            if (notifications.publishOnce("PHOTO_FOLLOW_UP", "NORMAL", task.zoneId,
                    task.seasonId,
                    context.getString(R.string.notification_photo_follow_up_title),
                    context.getString(R.string.notification_photo_follow_up_description),
                    "photo_follow_up:" + task.photoId) != null) {
                followUps.markNotified(task.photoId);
            }
        }
    }

    private Double number(DataSnapshot value) {
        Number number = value.getValue(Number.class);
        return number == null ? null : number.doubleValue();
    }

    private long longNumber(DataSnapshot value) {
        Number number = value.getValue(Number.class);
        return number == null ? 0L : number.longValue();
    }

    private Map<String, Long> latestSeedlingLogEpochs(
            DatabaseReference deviceRef, List<SeedlingBatch> batches) throws Exception {
        Map<String, Long> values = new LinkedHashMap<>();
        List<String> batchIds = new ArrayList<>();
        List<Task<DataSnapshot>> reads = new ArrayList<>();
        for (SeedlingBatch batch : batches) {
            if (!SeedlingNotificationPolicy.isActive(batch)) continue;
            batchIds.add(batch.getBatch_id());
            reads.add(deviceRef.child("seedling").child("daily_logs")
                    .child(batch.getBatch_id()).orderByKey().limitToLast(1).get());
        }
        if (reads.isEmpty()) return values;
        Tasks.await(Tasks.whenAll(reads), 15, TimeUnit.SECONDS);
        for (int index = 0; index < reads.size(); index++) {
            long latest = 0L;
            for (DataSnapshot child : reads.get(index).getResult().getChildren()) {
                Number createdAt = child.child("created_at_epoch").getValue(Number.class);
                if (createdAt != null) latest = Math.max(latest, createdAt.longValue());
            }
            values.put(batchIds.get(index), latest);
        }
        return values;
    }
}
