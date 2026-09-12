package com.alidogukan.avora.nas;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/** Runs incremental photo backup only on an unmetered connection and healthy battery. */
public final class NasPhotoBackupScheduler {
    private static final String PERIODIC = "avora-nas-photo-backup";
    private static final String IMMEDIATE = "avora-nas-photo-backup-now";

    private NasPhotoBackupScheduler() { }

    public static void schedule(Context context) {
        Constraints constraints = constraints();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                NasPhotoBackupWorker.class, 24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(24, TimeUnit.HOURS)
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniquePeriodicWork(
                        PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    public static void runNow(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(
                NasPhotoBackupWorker.class)
                .setConstraints(constraints())
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(IMMEDIATE, ExistingWorkPolicy.REPLACE, request);
    }

    public static void cancel(Context context) {
        WorkManager manager = WorkManager.getInstance(context.getApplicationContext());
        manager.cancelUniqueWork(PERIODIC);
        manager.cancelUniqueWork(IMMEDIATE);
    }

    private static Constraints constraints() {
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build();
    }
}
