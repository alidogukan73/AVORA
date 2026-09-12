package com.alidogukan.avora.notifications;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.app.TaskStackBuilder;
import com.alidogukan.avora.R;
import com.alidogukan.avora.activities.NotificationDetailActivity;
import com.alidogukan.avora.language.AvoraLanguageManager;
import com.alidogukan.avora.firebase.FirebaseRepository;
import com.alidogukan.avora.models.GardenNotification;
import com.alidogukan.avora.activities.MainActivity;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

/** Single entry point for all future AVORA notification sources. */
public final class GardenNotificationManager {
    private static final String PHONE_CHANNEL = "avora_garden_alerts";
    private static final String PHONE_CHANNEL_URGENT = "avora_garden_alerts_urgent";
    private static final String INCIDENT_PREFS = "avora_notification_incidents";
    public static final String ACTION_NOTIFICATIONS_CHANGED = "com.alidogukan.avora.NOTIFICATIONS_CHANGED";
    private static final Object INCIDENT_LOCK = new Object();
    private final Context context;
    private final LocalGardenNotificationStore store;
    private final FirebaseRepository repository;
    public GardenNotificationManager(Context context) {
        this.context = AvoraLanguageManager.localizedContext(
                context.getApplicationContext());
        store = new LocalGardenNotificationStore(this.context);
        repository = new FirebaseRepository();
    }

    public GardenNotification publish(String type, String priority, String zoneId,
                                      String title, String description, String sourceKey) {
        if (!new NotificationSettingsStore(context).isCategoryEnabled(type)) return null;

        GardenNotification value =
                store.add(type, priority, zoneId, title, description, sourceKey);

        persistNotification(value);

        notifyNotificationsChanged();

        showPhoneAlert(value);

        return value;
    }
    public GardenNotification publishOnce(String type, String priority, String zoneId,
                                          String title, String description, String sourceKey) {
        return publishOnce(type, priority, zoneId, "", title, description, sourceKey);
    }

    public GardenNotification publishOnce(String type, String priority, String zoneId,
                                          String seasonId, String title,
                                          String description, String sourceKey) {
        if (!new NotificationSettingsStore(context).isCategoryEnabled(type)) return null;

        GardenNotification value =
                store.addOnce(type, priority, zoneId, title, description, sourceKey);

        if (value != null) {
            if (seasonId != null && !seasonId.isBlank()) {
                value.setSeason_id(seasonId);
                store.updateSeasonId(value.getId(), value.getSeason_id());
            }
            persistNotification(value);

            notifyNotificationsChanged();

            showPhoneAlert(value);
        }

        return value;
    }

    private void persistNotification(GardenNotification value) {
        repository.saveGardenNotification(value).addOnSuccessListener(ignored -> {
            if (store.updateSeasonId(value.getId(), value.getSeason_id())) {
                notifyNotificationsChanged();
            }
        });
    }

    /**
     * Publishes exactly one alert for a continuing incident. State is persisted so
     * the foreground listener, WorkManager and FCM cannot produce a second alert.
     * A confirmed recovery must reset the incident before it can be reported again.
     */
    public GardenNotification publishIncident(String incidentKey, String type, String priority,
                                               String zoneId, String title, String description,
                                               String sourceKey) {
        if (incidentKey == null || incidentKey.isBlank()
                || !new NotificationSettingsStore(context).isCategoryEnabled(type)) return null;
        synchronized (INCIDENT_LOCK) {
            SharedPreferences preferences = context.getSharedPreferences(INCIDENT_PREFS, Context.MODE_PRIVATE);
            long now = System.currentTimeMillis();
            boolean active = preferences.getBoolean("active:" + incidentKey, false);
            if (active) return null;

            String effectiveSource = NotificationPolicy.incidentSourceKey(
                    incidentKey,
                    sourceKey,
                    now
            );

            GardenNotification value = publishOnce(
                    type, priority, zoneId, title, description, effectiveSource);
            if (value != null) {
                preferences.edit()
                        .putBoolean("active:" + incidentKey, true)
                        .putLong("last:" + incidentKey, now)
                        .putString("source:" + incidentKey, effectiveSource)
                        .apply();
            }
            return value;
        }
    }

    /** A recovered signal starts a fresh incident, so a later outage is reported immediately. */
    public void resetIncident(String incidentKey) {
        if (incidentKey == null || incidentKey.isBlank()) return;
        synchronized (INCIDENT_LOCK) {
            context.getSharedPreferences(INCIDENT_PREFS, Context.MODE_PRIVATE).edit()
                    .remove("active:" + incidentKey)
                    .remove("last:" + incidentKey)
                    .remove("source:" + incidentKey)
                    .apply();
        }
    }

    public boolean isIncidentActive(String incidentKey) {
        if (incidentKey == null || incidentKey.isBlank()) {
            return false;
        }

        synchronized (INCIDENT_LOCK) {
            return context
                    .getSharedPreferences(INCIDENT_PREFS, Context.MODE_PRIVATE)
                    .getBoolean("active:" + incidentKey, false);
        }
    }


    /** Called by FCM so every remote event is localized and deduplicated in one place. */
    public GardenNotification receiveRemote(RemoteNotificationEvent event) {
        if (event == null) return null;
        String incidentKey = event.incidentKey();
        if (!incidentKey.isBlank()) {
            return publishIncident(
                    incidentKey,
                    event.type(),
                    event.priority(),
                    event.zoneId(),
                    event.title(context),
                    event.description(context),
                    event.sourceKey()
            );
        }
        return publishOnce(
                event.type(),
                event.priority(),
                event.zoneId(),
                event.title(context),
                event.description(context),
                event.sourceKey()
        );
    }
    public void setState(GardenNotification value, boolean read, boolean saved) {
        if (value == null) return;

        if (store.updateState(value.getId(), read, saved)) {
            value.setRead(read);
            value.setSaved(saved);

            repository.updateGardenNotificationState(
                    value.getId(),
                    read,
                    saved
            );

            notifyNotificationsChanged();
        }
    }
    public List<GardenNotification> localNotifications() { return store.load(); }

    public GardenNotification findLocalById(String id) {

        if (id == null || id.isBlank()) {
            return null;
        }

        for (GardenNotification value : store.load()) {

            if (value != null && id.equals(value.getId())) {
                return value;
            }
        }

        return null;
    }

    public void markRead(String id) {

        GardenNotification value = findLocalById(id);

        if (value == null || value.isRead()) {
            return;
        }

        setState(
                value,
                true,
                value.isSaved()
        );
    }
    public void deleteNotification(
            GardenNotification value,
            Consumer<Boolean> completed
    ) {
        if (value == null || value.getId() == null || value.getId().isBlank()) {
            if (completed != null) completed.accept(false);
            return;
        }

        List<GardenNotification> removable = new ArrayList<>();
        removable.add(value);
        List<String> ids = new ArrayList<>();
        ids.add(value.getId());

        // The UI is offline-first. A cloud failure must never make a deleted alert reappear.
        store.rememberDeleted(removable);
        store.queuePendingCloudDeletion(removable);
        store.removeAll(ids);
        NotificationManagerCompat.from(context).cancel(value.getId().hashCode());
        notifyNotificationsChanged();
        if (completed != null) completed.accept(true);

        repository.deleteGardenNotificationsWithTombstones(removable)
                .addOnSuccessListener(unused ->
                        store.completePendingCloudDeletions(removable));
    }

    public void clearUnsavedNotifications(Consumer<Integer> completed) {
        List<GardenNotification> current = store.load();
        List<GardenNotification> removable = new ArrayList<>();
        List<String> ids = new ArrayList<>();

        for (GardenNotification value : current) {
            if (value != null && !value.isSaved()
                    && value.getId() != null && !value.getId().isBlank()) {
                removable.add(value);
                ids.add(value.getId());
            }
        }

        if (ids.isEmpty()) {
            if (completed != null) completed.accept(0);
            return;
        }

        store.rememberDeleted(removable);
        store.queuePendingCloudDeletion(removable);
        int removed = store.removeAll(ids);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        for (GardenNotification value : removable) {
            notificationManager.cancel(value.getId().hashCode());
        }
        notifyNotificationsChanged();
        if (completed != null) completed.accept(removed);

        repository.deleteGardenNotificationsWithTombstones(removable)
                .addOnSuccessListener(unused ->
                        store.completePendingCloudDeletions(removable));
    }

    public boolean hasPendingCloudDeletions() {
        return !store.pendingCloudDeletions().isEmpty();
    }

    public Task<Void> syncPendingCloudDeletions() {
        List<GardenNotification> pending = store.pendingCloudDeletions();
        if (pending.isEmpty()) return Tasks.forResult(null);
        return repository.deleteGardenNotificationsWithTombstones(pending)
                .addOnSuccessListener(unused ->
                        store.completePendingCloudDeletions(pending));
    }

    /** Restores backed-up history on another phone, then keeps any local-only alerts. */
    public void restoreCloudBackup(Consumer<Integer> completed) {
        repository.loadGardenNotifications(values -> {
            int imported = store.mergeFromCloud(values);

            if (imported > 0) {
                notifyNotificationsChanged();
            }

            if (completed != null) {
                completed.accept(imported);
            }
        });
    }

    /** Phone alerts are optional; the durable in-app record is always written first. */
    private void showPhoneAlert(GardenNotification value) {
        if (value == null || !new NotificationSettingsStore(context).shouldShowPhoneAlert(value.getType())) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        Intent intent = new Intent(context, NotificationDetailActivity.class)
                .putExtra("id", value.getId()).putExtra("type", value.getType()).putExtra("priority", value.getPriority())
                .putExtra("zone_id", value.getZone_id()).putExtra("title", value.getTitle()).putExtra("description", value.getDescription())
                .putExtra("source_key", value.getSource_key())
                .putExtra("created_at_epoch", value.getCreated_at_epoch()).putExtra("read", value.isRead()).putExtra("saved", value.isSaved());
        Intent mainIntent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addNextIntent(mainIntent);
        stackBuilder.addNextIntent(intent);

        PendingIntent pending = stackBuilder.getPendingIntent(
                value.getId().hashCode(),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        boolean urgent =
                "HIGH".equalsIgnoreCase(value.getPriority());

        ensurePhoneChannels();

        String channelId = urgent
                ? PHONE_CHANNEL_URGENT
                : PHONE_CHANNEL;

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(R.drawable.ic_avora_notification_small)
                        .setColor(
                                ContextCompat.getColor(
                                        context,
                                        R.color.homeGardenPlanIcon
                                )
                        )
                        .setContentTitle(value.getTitle())
                        .setContentText(value.getDescription())
                        .setStyle(
                                new NotificationCompat.BigTextStyle()
                                        .bigText(value.getDescription())
                        )
                        .setAutoCancel(true)
                        .setContentIntent(pending);

        NotificationManagerCompat.from(context).notify(
                value.getId().hashCode(),
                builder.build()
        );
    }

    private void ensurePhoneChannels() {

        NotificationManager manager =
                (NotificationManager) context.getSystemService(
                        Context.NOTIFICATION_SERVICE
                );

        if (manager == null) {
            return;
        }

        NotificationChannel normal =
                new NotificationChannel(
                        PHONE_CHANNEL,
                        context.getString(R.string.notification_channel_normal_name),
                        NotificationManager.IMPORTANCE_DEFAULT
                );

        normal.setDescription(context.getString(
                R.string.notification_channel_normal_description));

        manager.createNotificationChannel(normal);

        NotificationChannel urgent =
                new NotificationChannel(
                        PHONE_CHANNEL_URGENT,
                        context.getString(R.string.notification_channel_urgent_name),
                        NotificationManager.IMPORTANCE_HIGH
                );

        urgent.setDescription(context.getString(
                R.string.notification_channel_urgent_description));

        urgent.enableVibration(true);

        manager.createNotificationChannel(urgent);
    }

    private void notifyNotificationsChanged() {
        Intent intent = new Intent(ACTION_NOTIFICATIONS_CHANGED);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    public void applyCloudSnapshot(
            List<GardenNotification> values
    ) {
        int imported = store.mergeFromCloud(values);

        if (imported > 0) {
            notifyNotificationsChanged();
        }
    }

    public void applyCloudDeletions(
            java.util.Map<String, String> deletions
    ) {
        if (deletions == null || deletions.isEmpty()) {
            return;
        }

        int removed =
                store.applyRemoteDeletions(deletions);

        NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(context);

        for (String id : deletions.keySet()) {

            if (id != null && !id.isBlank()) {
                notificationManager.cancel(
                        id.hashCode()
                );
            }
        }

        if (removed > 0) {
            notifyNotificationsChanged();
        }
    }
}
