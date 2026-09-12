package com.alidogukan.avora.notifications;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.alidogukan.avora.config.AppInfo;
import com.alidogukan.avora.firebase.FirebaseWorkerAuthentication;
import com.alidogukan.avora.models.Status;
import com.alidogukan.avora.language.AvoraLanguageManager;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.FirebaseDatabase;

import java.util.concurrent.TimeUnit;

/** Rechecks a suspected Pi outage before a user-visible notification is created. */
public final class DeviceConnectionVerificationWorker extends Worker {
    private static final String UNIQUE_WORK = "avora-device-connection-verification";

    public DeviceConnectionVerificationWorker(@NonNull Context context,
                                              @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    static void schedule(Context context) {
        schedule(context, NotificationPolicy.DEVICE_OFFLINE_CONFIRMATION_MILLIS);
    }

    static void scheduleRecovery(Context context) {
        schedule(context, NotificationPolicy.DEVICE_RECOVERY_CONFIRMATION_MILLIS);
    }

    private static void schedule(Context context, long delayMillis) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(
                DeviceConnectionVerificationWorker.class)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(
                        BackoffPolicy.LINEAR, 30L, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.KEEP, request);
    }

    static void cancel(Context context) {
        WorkManager.getInstance(context.getApplicationContext())
                .cancelUniqueWork(UNIQUE_WORK);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = AvoraLanguageManager.localizedContext(getApplicationContext());
        try {
            if (!FirebaseConnectionProbe.awaitConnected(15, TimeUnit.SECONDS)) {
                return Result.retry();
            }
            if (!FirebaseWorkerAuthentication.awaitAuthorized(20, TimeUnit.SECONDS)) {
                return Result.success();
            }

            DataSnapshot snapshot = Tasks.await(FirebaseDatabase.getInstance()
                            .getReference("devices")
                            .child(AppInfo.DEVICE_ID)
                            .child("status")
                            .get(),
                    20, TimeUnit.SECONDS);
            Status status = snapshot.getValue(Status.class);
            if (status == null) return Result.retry();

            // Do not act on a cache fallback if the Firebase socket was lost
            // while the status request was in flight.
            if (!FirebaseConnectionProbe.awaitConnected(10, TimeUnit.SECONDS)) {
                return Result.retry();
            }

            long nowEpoch = System.currentTimeMillis() / 1000L;
            boolean offline = NotificationPolicy.isDeviceOffline(
                    status.isOnline(), status.getLastSeenEpoch(), nowEpoch,
                    NotificationPolicy.DEVICE_HEARTBEAT_MAX_AGE_SECONDS);
            NotificationSignalCoordinator.evaluateDeviceConnection(context, status);

            boolean alertsEnabled = new NotificationSettingsStore(context)
                    .isCategoryEnabled("DEVICE");
            boolean incidentActive = new GardenNotificationManager(context)
                    .isIncidentActive("device_offline");
            if (NotificationPolicy.shouldRetryDeviceConnectionVerification(
                    alertsEnabled, offline, incidentActive)) {
                return Result.retry();
            }
            return Result.success();
        } catch (Exception ignored) {
            return Result.retry();
        }
    }
}
