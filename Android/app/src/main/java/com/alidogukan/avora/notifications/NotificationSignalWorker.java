package com.alidogukan.avora.notifications;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.alidogukan.avora.R;
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
        try {
            Tasks.await(new GardenNotificationManager(context).syncPendingCloudDeletions(),
                    20, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // Local tombstones already prevent deleted alerts from returning.
        }
        try {
            if (!FirebaseConnectionProbe.awaitConnected(15, TimeUnit.SECONDS)) {
                return Result.retry();
            }

            DatabaseReference deviceRef = FirebaseDatabase.getInstance()
                    .getReference("devices").child("avora-001");
            Task<DataSnapshot> forecastRead = deviceRef
                    .child("weather").child("forecast").get();
            Task<DataSnapshot> zonesRead = deviceRef.child("zones").get();
            Task<DataSnapshot> statusRead = deviceRef.child("status").get();
            Task<DataSnapshot> healthRead = deviceRef.child("health").get();
            Task<DataSnapshot> wateringRead = deviceRef.child("watering_history")
                    .orderByKey().limitToLast(50).get();
            Task<DataSnapshot> seedlingBatchesRead = deviceRef
                    .child("seedling").child("batches").get();
            Task<DataSnapshot> seedlingNodesRead = deviceRef
                    .child("seedling").child("nodes").get();

            Tasks.await(Tasks.whenAll(
                    forecastRead,
                    zonesRead,
                    statusRead,
                    healthRead,
                    wateringRead,
                    seedlingBatchesRead,
                    seedlingNodesRead
            ), 20, TimeUnit.SECONDS);

            if (!FirebaseConnectionProbe.awaitConnected(10, TimeUnit.SECONDS)) {
                return Result.retry();
            }
            DataSnapshot forecast = forecastRead.getResult();
            NotificationSignalCoordinator.evaluateWeather(context,
                    number(forecast.child("tomorrow_temperature_max")),
                    number(forecast.child("tomorrow_rain_probability")),
                    number(forecast.child("tomorrow_wind_max")),
                    LocalDate.now().plusDays(1).toString(),
                    longNumber(forecast.child("updated_at_epoch")));
            java.util.ArrayList<GardenZone> zones = new java.util.ArrayList<>();
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

            ArrayList<SeedlingBatch> seedlingBatches = new ArrayList<>();
            for (DataSnapshot child : seedlingBatchesRead.getResult().getChildren()) {
                SeedlingBatch batch = child.getValue(SeedlingBatch.class);
                if (batch == null) continue;
                if (batch.getBatch_id().isBlank()) batch.setBatch_id(child.getKey());
                seedlingBatches.add(batch);
            }
            Map<String, SeedlingNodeState> seedlingNodes = new LinkedHashMap<>();
            for (DataSnapshot child : seedlingNodesRead.getResult().getChildren()) {
                SeedlingNodeState node = child.getValue(SeedlingNodeState.class);
                if (node != null && child.getKey() != null) {
                    seedlingNodes.put(child.getKey(), node);
                }
            }
            Map<String, Long> latestSeedlingLogs = latestSeedlingLogEpochs(
                    deviceRef, seedlingBatches);
            SeedlingNotificationCoordinator.evaluate(
                    context, seedlingBatches, seedlingNodes, latestSeedlingLogs);

            Status status = statusRead.getResult().getValue(Status.class);
            if (status == null) return Result.retry();

            Health health = healthRead.getResult().getValue(Health.class);


            long nowEpoch = System.currentTimeMillis() / 1000L;
            boolean deviceOffline = NotificationPolicy.isDeviceOffline(
                    status.isOnline(), status.getLastSeenEpoch(), nowEpoch,
                    NotificationPolicy.DEVICE_HEARTBEAT_MAX_AGE_SECONDS);

            /*
             * The periodic snapshot only seeds a suspected outage. A separate live
             * verification publishes after the confirmation window.
             */
            if (deviceOffline) {
                NotificationSignalCoordinator.synchronizeDeviceConnection(context, status);
                DeviceConnectionVerificationWorker.schedule(context);
            } else {
                NotificationSignalCoordinator.evaluateDeviceConnection(context, status);
                if (new GardenNotificationManager(context)
                        .isIncidentActive("device_offline")) {
                    DeviceConnectionVerificationWorker.scheduleRecovery(context);
                } else {
                    DeviceConnectionVerificationWorker.cancel(context);
                }
            }

            NotificationSignalCoordinator.evaluateDevice(
                    context,
                    status,
                    health
            );
            java.util.ArrayList<WateringHistory> watering = new java.util.ArrayList<>();
            for (DataSnapshot child : wateringRead.getResult().getChildren()) {
                WateringHistory record = child.getValue(WateringHistory.class);
                if (record != null) {
                    record.setRecordId(child.getKey());
                    watering.add(record);
                }
            }
            NotificationSignalCoordinator.evaluateWatering(context, watering, zones);
            return Result.success();
        } catch (Exception ignored) {
            return Result.retry();
        }
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
