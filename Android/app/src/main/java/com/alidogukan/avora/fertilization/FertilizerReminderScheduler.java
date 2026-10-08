package com.alidogukan.avora.fertilization;

import android.content.Context;
import android.content.Intent;
import android.app.AlarmManager;
import android.app.PendingIntent;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.OutOfQuotaPolicy;
import com.alidogukan.avora.notifications.NotificationSettingsStore;
import java.time.ZoneId;

import java.util.concurrent.TimeUnit;

public final class FertilizerReminderScheduler {

    private static final String PERIODIC_WORK =
            "fertilizer-reminder-periodic";
    private static final String IMMEDIATE_WORK =
            "fertilizer-reminder-immediate";

    private FertilizerReminderScheduler() {
    }

    public static void schedule(Context context) {
        scheduleAlarm(context);
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

    /** While-idle alarms wake the process; expedited work performs the authenticated read. */
    static void scheduleAlarm(Context context) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        if (alarms == null) return;
        PendingIntent pending = PendingIntent.getBroadcast(context, 0,
                new Intent(context, FertilizerReminderAlarmReceiver.class)
                        .setAction(FertilizerReminderAlarmReceiver.ACTION_CHECK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationSettingsStore settings = new NotificationSettingsStore(context);
        if (!settings.isCategoryEnabled("fertilization")
                || !settings.isReminderEnabled("fertilization")) {
            alarms.cancel(pending);
            return;
        }
        // Inexact by design: no exact-alarm permission or permanent foreground service.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                FertilizerReminderTiming.nextCheckMillis(
                        System.currentTimeMillis(), ZoneId.systemDefault()), pending);
    }

    /** Runs after a reminder alarm or a user-visible settings change. */
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
                ).setConstraints(constraints)
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build();
        manager.enqueueUniqueWork(
                IMMEDIATE_WORK,
                androidx.work.ExistingWorkPolicy.KEEP,
                immediate
        );
    }
}
