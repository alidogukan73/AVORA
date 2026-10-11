package com.alidogukan.avora.notifications;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;

import java.util.concurrent.TimeUnit;

/**
 * Schedules the periodic background notification safety scan.
 */
public final class NotificationSignalScheduler {

    private static final String PERIODIC =
            "avora-notification-signals-v2";

    private NotificationSignalScheduler() {
    }

    /** Refresh after an alarm or package update even if an old scan is in backoff. */
    public static void runNow(Context context) {
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                "avora-notification-signals-immediate", ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(NotificationSignalWorker.class)
                        .setConstraints(new Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .build());
    }

    public static void schedule(Context context) {
        WorkManager manager =
                WorkManager.getInstance(
                        context.getApplicationContext()
                );

        // The old numeric-reader failure could leave this job in five-hour
        // exponential backoff. Retire only that job and start a fresh cadence.
        manager.cancelUniqueWork("avora-notification-signals");

        manager.enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                new PeriodicWorkRequest.Builder(
                        NotificationSignalWorker.class,
                        15,
                        TimeUnit.MINUTES
                )
                        .setConstraints(new Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build())
                        .build()
        );
    }
}
