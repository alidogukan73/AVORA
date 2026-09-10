package com.alidogukan.avora.notifications;

import android.content.Context;
import com.alidogukan.avora.models.GardenNotification;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/** Offline-first notification storage; Firebase is used as a backup by the caller. */
public final class LocalGardenNotificationStore {
    private static final String DISMISSED_KEYS = "dismissed_source_keys";
    private static final String PREFS = "avora_notifications";
    private static final String KEY = "items";
    private static final String DELETED_IDS = "deleted_notification_ids";
    private static final String PENDING_DELETIONS = "pending_cloud_deletions";
    private final Context context;
    public LocalGardenNotificationStore(Context context) { this.context = context.getApplicationContext(); }

    public GardenNotification add(
            String type,
            String priority,
            String zoneId,
            String title,
            String description,
            String sourceKey
    ) {
        synchronized (LocalGardenNotificationStore.class) {
            return addUnlocked(type, priority, zoneId, title, description, sourceKey);
        }
    }

    private GardenNotification addUnlocked(
            String type,
            String priority,
            String zoneId,
            String title,
            String description,
            String sourceKey
    ) {

        String notificationId;

        if (sourceKey != null && !sourceKey.isBlank()) {

            notificationId =
                    UUID.nameUUIDFromBytes(
                            sourceKey.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    ).toString();

            for (GardenNotification existing : load()) {

                if (existing != null
                        && notificationId.equals(existing.getId())) {

                    return existing;
                }
            }

        } else {

            notificationId =
                    UUID.randomUUID().toString();
        }

        GardenNotification value =
                new GardenNotification();

        value.setId(notificationId);
        value.setType(type);
        value.setPriority(priority);
        value.setZone_id(zoneId);
        value.setTitle(title);
        value.setDescription(description);
        value.setSource_key(sourceKey);
        value.setCreated_at_epoch(
                System.currentTimeMillis() / 1000L
        );

        JSONArray items = read();

        items.put(
                toJson(value)
        );

        save(items);

        return value;
    }

    public GardenNotification addOnce(
            String type,
            String priority,
            String zoneId,
            String title,
            String description,
            String sourceKey
    ) {
        synchronized (LocalGardenNotificationStore.class) {
            return addOnceUnlocked(type, priority, zoneId, title, description, sourceKey);
        }
    }

    private GardenNotification addOnceUnlocked(
            String type,
            String priority,
            String zoneId,
            String title,
            String description,
            String sourceKey
    ) {
        if (sourceKey != null && !sourceKey.isBlank()) {

            // Kullanıcı bu olayı daha önce sildiyse yeniden üretme.
            if (isDismissed(sourceKey)) {
                return null;
            }

            for (GardenNotification value : load()) {
                if (sourceKey.equals(value.getSource_key())) {
                    return null;
                }
            }
        }

        return add(
                type,
                priority,
                zoneId,
                title,
                description,
                sourceKey
        );
    }

    public void rememberDismissed(
            List<GardenNotification> values
    ) {
        synchronized (LocalGardenNotificationStore.class) {
            rememberDismissedUnlocked(values);
        }
    }

    private void rememberDismissedUnlocked(
            List<GardenNotification> values
    ) {
        if (values == null || values.isEmpty()) {
            return;
        }

        Set<String> sourceKeys =
                new HashSet<>();

        for (GardenNotification value : values) {

            if (value == null) {
                continue;
            }

            String sourceKey =
                    value.getSource_key();

            if (sourceKey != null
                    && !sourceKey.isBlank()) {

                sourceKeys.add(sourceKey);
            }
        }

        rememberDismissedSourceKeys(sourceKeys);
    }

    private void rememberDismissedSourceKeys(
            Set<String> sourceKeys
    ) {
        if (sourceKeys == null || sourceKeys.isEmpty()) {
            return;
        }

        Set<String> dismissed =
                new HashSet<>(
                        context.getSharedPreferences(
                                PREFS,
                                Context.MODE_PRIVATE
                        ).getStringSet(
                                DISMISSED_KEYS,
                                Collections.emptySet()
                        )
                );

        boolean changed = false;

        for (String sourceKey : sourceKeys) {

            if (sourceKey != null
                    && !sourceKey.isBlank()
                    && dismissed.add(sourceKey)) {

                changed = true;
            }
        }

        if (changed) {
            context.getSharedPreferences(
                            PREFS,
                            Context.MODE_PRIVATE
                    ).edit()
                    .putStringSet(
                            DISMISSED_KEYS,
                            new HashSet<>(dismissed)
                    )
                    .apply();
        }
    }

    private void rememberDeletedIds(
            Set<String> ids
    ) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        Set<String> deleted =
                new HashSet<>(
                        context.getSharedPreferences(
                                PREFS,
                                Context.MODE_PRIVATE
                        ).getStringSet(
                                DELETED_IDS,
                                Collections.emptySet()
                        )
                );

        deleted.addAll(ids);

        context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                ).edit()
                .putStringSet(
                        DELETED_IDS,
                        new HashSet<>(deleted)
                )
                .apply();
    }

    private boolean isDeletedId(String id) {

        if (id == null || id.isBlank()) {
            return false;
        }

        Set<String> deleted =
                context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                ).getStringSet(
                        DELETED_IDS,
                        Collections.emptySet()
                );

        return deleted.contains(id);
    }

    private boolean isDismissed(String sourceKey) {
        if (sourceKey == null || sourceKey.isBlank()) {
            return false;
        }

        Set<String> dismissed =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .getStringSet(DISMISSED_KEYS, null);

        return dismissed != null && dismissed.contains(sourceKey);
    }
    public boolean updateState(String id, boolean read, boolean saved) {
        synchronized (LocalGardenNotificationStore.class) {
            return updateStateUnlocked(id, read, saved);
        }
    }

    private boolean updateStateUnlocked(String id, boolean read, boolean saved) {
        JSONArray items = read();
        for (int i = 0; i < items.length(); i++) try { JSONObject item = items.getJSONObject(i); if (id.equals(item.optString("id"))) { item.put("read", read); item.put("saved", saved); save(items); return true; } } catch (Exception ignored) { }
        return false;
    }

    /** Persists the season resolved asynchronously by Firebase without recreating the alert. */
    public boolean updateSeasonId(String id, String seasonId) {
        if (id == null || id.isBlank() || seasonId == null || seasonId.isBlank()) return false;
        synchronized (LocalGardenNotificationStore.class) {
            JSONArray items = read();
            for (int i = 0; i < items.length(); i++) {
                try {
                    JSONObject item = items.getJSONObject(i);
                    if (!id.equals(item.optString("id"))) continue;
                    item.put("season_id", seasonId);
                    save(items);
                    return true;
                } catch (Exception ignored) { }
            }
            return false;
        }
    }

    public int removeAll(List<String> ids) {
        synchronized (LocalGardenNotificationStore.class) {
            return removeAllUnlocked(ids);
        }
    }

    /** Removes local alerts that belonged only to a deleted empty season. */
    public int removeBySeason(String seasonId) {
        if (seasonId == null || seasonId.isBlank()) return 0;
        synchronized (LocalGardenNotificationStore.class) {
            List<GardenNotification> targets = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            for (GardenNotification value : loadUnlocked()) {
                if (value == null
                        || !seasonId.equals(value.getSeason_id())
                        || value.getId() == null
                        || value.getId().isBlank()) {
                    continue;
                }
                targets.add(value);
                ids.add(value.getId());
            }
            if (ids.isEmpty()) return 0;
            rememberDeletedIds(new HashSet<>(ids));
            rememberDismissedUnlocked(targets);
            return removeAllUnlocked(ids);
        }
    }

    public int removeForPhotos(String seasonId, Set<String> photoIds) {
        if (seasonId == null || seasonId.isBlank()
                || photoIds == null || photoIds.isEmpty()) return 0;
        synchronized (LocalGardenNotificationStore.class) {
            List<GardenNotification> targets = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            for (GardenNotification value : loadUnlocked()) {
                if (value == null
                        || !seasonId.equals(value.getSeason_id())
                        || !referencesAny(value.getSource_key(), photoIds)
                        || value.getId() == null
                        || value.getId().isBlank()) {
                    continue;
                }
                targets.add(value);
                ids.add(value.getId());
            }
            if (ids.isEmpty()) return 0;
            rememberDeletedIds(new HashSet<>(ids));
            rememberDismissedUnlocked(targets);
            return removeAllUnlocked(ids);
        }
    }

    private static boolean referencesAny(String value, Set<String> ids) {
        String source = value == null ? "" : value;
        for (String id : ids) {
            if (id != null && !id.isBlank() && source.contains(id)) return true;
        }
        return false;
    }

    private int removeAllUnlocked(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }

        Set<String> idSet =
                new HashSet<>(ids);

        JSONArray current = read();
        JSONArray remaining = new JSONArray();
        int removed = 0;

        for (int i = 0; i < current.length(); i++) {
            try {
                JSONObject item =
                        current.getJSONObject(i);

                String id =
                        item.optString("id");

                if (idSet.contains(id)) {
                    removed++;
                } else {
                    remaining.put(item);
                }

            } catch (Exception ignored) {
            }
        }

        if (removed > 0) {
            save(remaining);
        }

        return removed;
    }

    public List<GardenNotification> load() {
        synchronized (LocalGardenNotificationStore.class) {
            return loadUnlocked();
        }
    }

    private List<GardenNotification> loadUnlocked() {
        List<GardenNotification> values = new ArrayList<>();
        JSONArray items = read();
        for (int i = 0; i < items.length(); i++) {
            try {
                values.add(fromJson(items.getJSONObject(i)));
            } catch (Exception ignored) {
            }
        }
        values.sort(Comparator.comparingLong(GardenNotification::getCreated_at_epoch).reversed());
        return values;
    }

    /** Merges Firebase backup records without discarding newer local-only alerts. */
    public int mergeFromCloud(
            List<GardenNotification> remoteValues
    ) {
        synchronized (LocalGardenNotificationStore.class) {
            return mergeFromCloudUnlocked(remoteValues);
        }
    }

    private int mergeFromCloudUnlocked(
            List<GardenNotification> remoteValues
    ) {

        if (remoteValues == null || remoteValues.isEmpty()) {
            return 0;
        }

        Map<String, GardenNotification> merged =
                new HashMap<>();

        Map<String, GardenNotification> bySourceKey =
                new HashMap<>();

        /*
         * Önce local kayıtları koru.
         *
         * Aynı source_key için birden fazla eski kayıt varsa
         * yalnızca bir tanesi tutulur.
         */
        for (GardenNotification local : load()) {

            if (local == null
                    || local.getId() == null
                    || local.getId().isBlank()) {
                continue;
            }

            String sourceKey =
                    local.getSource_key();

            if (sourceKey != null
                    && !sourceKey.isBlank()) {

                GardenNotification existing =
                        bySourceKey.get(sourceKey);

                if (existing != null) {

                    /*
                     * Aynı olayın eski random-ID ve yeni
                     * deterministic-ID kopyaları varsa
                     * deterministic olanı tercih et.
                     */
                    String expectedId =
                            UUID.nameUUIDFromBytes(
                                    sourceKey.getBytes(
                                            StandardCharsets.UTF_8
                                    )
                            ).toString();

                    if (expectedId.equals(local.getId())) {

                        merged.remove(
                                existing.getId()
                        );

                        merged.put(
                                local.getId(),
                                local
                        );

                        bySourceKey.put(
                                sourceKey,
                                local
                        );
                    }

                    continue;
                }

                bySourceKey.put(
                        sourceKey,
                        local
                );
            }

            merged.put(
                    local.getId(),
                    local
            );
        }

        int changed = 0;

        for (GardenNotification remote : remoteValues) {

            if (remote == null
                    || remote.getId() == null
                    || remote.getId().isBlank()) {
                continue;
            }

            if (isDeletedId(remote.getId())) {
                continue;
            }

            GardenNotification sameId = merged.get(remote.getId());
            if (sameId != null) {
                boolean stateChanged = false;
                if (remote.isRead() && !sameId.isRead()) {
                    sameId.setRead(true);
                    stateChanged = true;
                }
                if (remote.isSaved() && !sameId.isSaved()) {
                    sameId.setSaved(true);
                    stateChanged = true;
                }
                if (stateChanged) {
                    changed++;
                }
                continue;
            }

            String sourceKey =
                    remote.getSource_key();

            if (sourceKey != null
                    && !sourceKey.isBlank()) {

                if (isDismissed(sourceKey)) {
                    continue;
                }

                GardenNotification existing =
                        bySourceKey.get(sourceKey);

                /*
                 * Aynı source_key localde zaten varsa
                 * farklı ID ile gelen cloud kopyasını ekleme.
                 */
                if (existing != null
                        && !remote.getId().equals(
                        existing.getId()
                )) {

                    /*
                     * Cloud state okunmuş/kaydedilmiş bilgisinde
                     * daha güncel olabilir. Bu iki flag'i koru.
                     */
                    boolean stateChanged = false;
                    if (remote.isRead() && !existing.isRead()) {
                        existing.setRead(true);
                        stateChanged = true;
                    }
                    if (remote.isSaved() && !existing.isSaved()) {
                        existing.setSaved(true);
                        stateChanged = true;
                    }
                    if (stateChanged) {
                        changed++;
                    }

                    continue;
                }
            }

            if (!merged.containsKey(
                    remote.getId()
            )) {
                changed++;
            }

            merged.put(
                    remote.getId(),
                    remote
            );

            if (sourceKey != null
                    && !sourceKey.isBlank()) {

                bySourceKey.put(
                        sourceKey,
                        remote
                );
            }
        }

        JSONArray items =
                new JSONArray();

        List<GardenNotification> values =
                new ArrayList<>(
                        merged.values()
                );

        values.sort(
                Comparator.comparingLong(
                        GardenNotification::getCreated_at_epoch
                ).reversed()
        );

        for (GardenNotification value : values) {

            items.put(
                    toJson(value)
            );
        }

        save(items);

        return changed;
    }

    public int applyRemoteDeletions(
            Map<String, String> deletions
    ) {
        synchronized (LocalGardenNotificationStore.class) {
            return applyRemoteDeletionsUnlocked(deletions);
        }
    }

    private int applyRemoteDeletionsUnlocked(
            Map<String, String> deletions
    ) {
        if (deletions == null || deletions.isEmpty()) {
            return 0;
        }

        Set<String> ids =
                new HashSet<>();

        Set<String> sourceKeys =
                new HashSet<>();

        for (Map.Entry<String, String> entry
                : deletions.entrySet()) {

            String id = entry.getKey();
            String sourceKey = entry.getValue();

            if (id != null && !id.isBlank()) {
                ids.add(id);
            }

            if (sourceKey != null
                    && !sourceKey.isBlank()) {

                sourceKeys.add(sourceKey);
            }
        }

        if (ids.isEmpty()) {
            return 0;
        }

        // Silinen bildirim ID'lerini kalıcı olarak hatırla.
        rememberDeletedIds(ids);

        /*
         * Tombstone içindeki source_key'i de hatırla.
         * Bu cihaz bildirimin kendisini hiç görmemiş olsa bile
         * aynı eski olay daha sonra Firebase'den geri gelemez.
         */
        rememberDismissedSourceKeys(sourceKeys);

        List<GardenNotification> localValues =
                load();

        List<GardenNotification> localDismissed =
                new ArrayList<>();

        for (GardenNotification value : localValues) {

            if (value == null
                    || value.getId() == null) {
                continue;
            }

            if (ids.contains(value.getId())) {
                localDismissed.add(value);
            }
        }

        /*
         * Eski tombstone kayıtlarında source_key bulunmaması
         * ihtimaline karşı yerel kayıttan da source_key topla.
         */
        rememberDismissed(localDismissed);

        return removeAll(
                new ArrayList<>(ids)
        );
    }
    public void rememberDeleted(List<GardenNotification> values) {
        if (values == null || values.isEmpty()) return;
        synchronized (LocalGardenNotificationStore.class) {
            Set<String> ids = new HashSet<>();
            for (GardenNotification value : values) {
                if (value != null && value.getId() != null && !value.getId().isBlank()) {
                    ids.add(value.getId());
                }
            }
            rememberDeletedIds(ids);
            rememberDismissed(values);
        }
    }

    public void queuePendingCloudDeletion(List<GardenNotification> values) {
        if (values == null || values.isEmpty()) return;
        synchronized (LocalGardenNotificationStore.class) {
            Map<String, GardenNotification> pending = new HashMap<>();
            for (GardenNotification existing : pendingCloudDeletionsUnlocked()) {
                if (existing != null && existing.getId() != null) {
                    pending.put(existing.getId(), existing);
                }
            }
            for (GardenNotification value : values) {
                if (value != null && value.getId() != null && !value.getId().isBlank()) {
                    pending.put(value.getId(), value);
                }
            }
            JSONArray items = new JSONArray();
            for (GardenNotification value : pending.values()) {
                items.put(toJson(value));
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(PENDING_DELETIONS, items.toString()).apply();
        }
    }

    public List<GardenNotification> pendingCloudDeletions() {
        synchronized (LocalGardenNotificationStore.class) {
            return pendingCloudDeletionsUnlocked();
        }
    }

    private List<GardenNotification> pendingCloudDeletionsUnlocked() {
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(PENDING_DELETIONS, "[]");
        List<GardenNotification> values = new ArrayList<>();
        try {
            JSONArray items = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < items.length(); i++) {
                values.add(fromJson(items.getJSONObject(i)));
            }
        } catch (Exception ignored) { }
        return values;
    }

    public void completePendingCloudDeletions(List<GardenNotification> completed) {
        if (completed == null || completed.isEmpty()) return;
        synchronized (LocalGardenNotificationStore.class) {
            Set<String> completedIds = new HashSet<>();
            for (GardenNotification value : completed) {
                if (value != null && value.getId() != null) completedIds.add(value.getId());
            }
            JSONArray items = new JSONArray();
            for (GardenNotification value : pendingCloudDeletionsUnlocked()) {
                if (value != null && !completedIds.contains(value.getId())) {
                    items.put(toJson(value));
                }
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(PENDING_DELETIONS, items.toString()).apply();
        }
    }
    private JSONObject toJson(GardenNotification v) { try { JSONObject o = new JSONObject(); o.put("id", v.getId()); o.put("type", v.getType()); o.put("priority", v.getPriority()); o.put("zone_id", v.getZone_id()); o.put("season_id", v.getSeason_id()); o.put("title", v.getTitle()); o.put("description", v.getDescription()); o.put("source_key", v.getSource_key()); o.put("created_at_epoch", v.getCreated_at_epoch()); o.put("read", v.isRead()); o.put("saved", v.isSaved()); return o; } catch (Exception e) { throw new IllegalStateException(e); } }
    private GardenNotification fromJson(JSONObject o) { GardenNotification v = new GardenNotification(); v.setId(o.optString("id")); v.setType(o.optString("type", "SYSTEM")); v.setPriority(o.optString("priority", "NORMAL")); v.setZone_id(o.optString("zone_id")); v.setSeason_id(o.optString("season_id")); v.setTitle(o.optString("title")); v.setDescription(o.optString("description")); v.setSource_key(o.optString("source_key")); v.setCreated_at_epoch(o.optLong("created_at_epoch")); v.setRead(o.optBoolean("read")); v.setSaved(o.optBoolean("saved")); return v; }
    private JSONArray read() { String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"); try { return new JSONArray(raw == null ? "[]" : raw); } catch (Exception ignored) { return new JSONArray(); } }
    private void save(JSONArray items) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, items.toString()).apply(); }
}
