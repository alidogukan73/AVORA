package com.alidogukan.avora.fertilization;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-arms after delivery, reboot, app update, or a clock/time-zone change. */
public final class FertilizerReminderAlarmReceiver extends BroadcastReceiver {
    static final String ACTION_CHECK = "com.alidogukan.avora.CHECK_FERTILIZER_REMINDERS";

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (!ACTION_CHECK.equals(action)
                && !Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                && !Intent.ACTION_TIME_CHANGED.equals(action)
                && !Intent.ACTION_TIMEZONE_CHANGED.equals(action)) return;
        FertilizerReminderScheduler.scheduleAlarm(context);
        FertilizerReminderScheduler.runNow(context);
    }
}
