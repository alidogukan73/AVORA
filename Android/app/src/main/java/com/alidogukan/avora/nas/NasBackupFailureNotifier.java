package com.alidogukan.avora.nas;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;

import com.alidogukan.avora.R;
import com.alidogukan.avora.activities.DataSyncActivity;
import com.alidogukan.avora.activities.MainActivity;

/** Shows one actionable local alert per automatic-backup failure incident. */
final class NasBackupFailureNotifier {
    private static final String CHANNEL = "avora_nas_backup";
    private static final int NOTIFICATION_ID = 0x4e4153;
    private static final int PHOTO_NOTIFICATION_ID = NOTIFICATION_ID + 1;
    private final Context context;

    NasBackupFailureNotifier(Context context) {
        this.context = context.getApplicationContext();
    }

    void show(String code) {
        show(code, false);
    }

    void showPhoto(String code) {
        show(code, true);
    }

    void cancel() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID);
    }

    void cancelPhoto() {
        NotificationManagerCompat.from(context).cancel(PHOTO_NOTIFICATION_ID);
    }

    private void show(String code, boolean photoBackup) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        ensureChannel();
        Intent main = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        Intent dataSync = new Intent(context, DataSyncActivity.class);
        PendingIntent pending = TaskStackBuilder.create(context)
                .addNextIntent(main)
                .addNextIntent(dataSync)
                .getPendingIntent(photoBackup ? PHOTO_NOTIFICATION_ID : NOTIFICATION_ID,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_avora_notification_small)
                .setColor(ContextCompat.getColor(context, R.color.warning))
                .setContentTitle(context.getString(photoBackup
                        ? R.string.data_sync_nas_photo_auto_failure_title
                        : R.string.data_sync_nas_auto_failure_title))
                .setContentText(message(code, photoBackup))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(message(code, photoBackup)))
                .setAutoCancel(true)
                .setContentIntent(pending);
        NotificationManagerCompat.from(context).notify(
                photoBackup ? PHOTO_NOTIFICATION_ID : NOTIFICATION_ID, builder.build());
    }

    private String message(String code, boolean photoBackup) {
        if ("NAS_SESSION_EXPIRED".equals(code)) {
            return context.getString(R.string.data_sync_nas_auto_failure_session);
        }
        if ("FIREBASE_NOT_AUTHORIZED".equals(code)) {
            return context.getString(R.string.data_sync_nas_auto_failure_firebase);
        }
        if (photoBackup) {
            return context.getString(R.string.data_sync_nas_photo_auto_failure_message);
        }
        return context.getString(R.string.data_sync_nas_auto_failure_connection);
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(
                Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL,
                context.getString(R.string.data_sync_nas_auto_channel),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(
                R.string.data_sync_nas_auto_channel_description));
        manager.createNotificationChannel(channel);
    }
}
