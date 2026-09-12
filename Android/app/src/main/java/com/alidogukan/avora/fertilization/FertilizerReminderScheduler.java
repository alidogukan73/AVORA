package com.alidogukan.avora.fertilization;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public final class FertilizerReminderScheduler {

    private static final String PERIODIC_WORK =
            "fertilizer-reminder-periodic";
    private static final String IMMEDIATE_WORK =
            "fertilizer-reminder-immediate";

    private FertilizerReminderScheduler() {
    }

    public static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest periodic =
                new PeriodicWorkRequest.Builder(
                        FertilizerReminderWorker.class,
                        15,
                        TimeUnit.MINUTES
                ).setConstraints(constraints).build();
        WorkManager manager = WorkManager.getInstance(
                context.getApplicationContext()
        );
        manager.enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodic
        );
    }

    /** Runs an immediate refresh only after a user-visible settings change. */
    public static void runNow(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        WorkManager manager = WorkManager.getInstance(
                context.getApplicationContext()
        );
        OneTimeWorkRequest immediate =
                new OneTimeWorkRequest.Builder(
                        FertilizerReminderWorker.class
                ).setConstraints(constraints).build();
        manager.enqueueUniqueWork(
                IMMEDIATE_WORK,
                androidx.work.ExistingWorkPolicy.REPLACE,
                immediate
        );
    }
}
