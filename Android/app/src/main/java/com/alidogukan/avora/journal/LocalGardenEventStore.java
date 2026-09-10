package com.alidogukan.avora.journal;

import android.content.Context;

import com.alidogukan.avora.models.GardenEvent;
import com.alidogukan.avora.season.SeasonRecordPolicy;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Keeps the garden journal's manual season notes private on this phone. */
public final class LocalGardenEventStore {
    private static final String PREFS = "garden_journal_events";
    private static final String KEY_INDEX = "events";
    private final Context context;

    public LocalGardenEventStore(Context context) {
        this.context = context.getApplicationContext();
    }

    public GardenEvent add(String zoneId, String type, String note) {
        return addInternal(zoneId, type, note, "MANUAL", "");
    }
    public GardenEvent addForSeason(String zoneId, String seasonId, String type, String note) {
        GardenEvent event = addInternal(zoneId, type, note, "MANUAL", "");
        event.setSeason_id(seasonId);
        replaceSeasonId(event.getId(), seasonId);
        return event;
    }

    public GardenEvent addSystemForSeason(String zoneId, String seasonId, String type, String note, String sourceKey) {
        GardenEvent event = addInternal(zoneId, type, note, "SYSTEM", sourceKey);
        event.setSeason_id(seasonId);
        replaceSeasonId(event.getId(), seasonId);
        return event;
    }
    public GardenEvent add(String zoneId, String type, String note, long occurredAtEpoch) {
        GardenEvent event = addInternal(zoneId, type, note, "MANUAL", "");
        if (occurredAtEpoch > 0L) {
            event.setOccurred_at_epoch(occurredAtEpoch);
            replaceTimestamp(event.getId(), occurredAtEpoch);
        }
        return event;
    }

    /** Adds one automatic fact at most once per zone/source key/day. */
    public GardenEvent addAutomaticOncePerDay(String zoneId, String type, String note, String sourceKey) {
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
        String dedupeKey = (sourceKey == null ? type : sourceKey) + ":" + today;
        for (GardenEvent existing : load()) if (zoneId.equals(existing.getZone_id()) && dedupeKey.equals(existing.getSource_key())) return null;
        return addInternal(zoneId, type, note, "AUTO", dedupeKey);
    }

    private GardenEvent addInternal(String zoneId, String type, String note, String source, String sourceKey) {
        GardenEvent event = new GardenEvent();
        event.setId(UUID.randomUUID().toString());
        event.setZone_id(zoneId);
        event.setType(type);
        event.setNote(note == null ? "" : note.trim());
        event.setSource(source); event.setSource_key(sourceKey);
        event.setOccurred_at_epoch(System.currentTimeMillis() / 1000L);

        JSONArray index = read();
        JSONObject item = new JSONObject();
        try {
            item.put("id", event.getId());
            item.put("zone_id", event.getZone_id());
            item.put("season_id", event.getSeason_id());
            item.put("type", event.getType());
            item.put("note", event.getNote());
            item.put("source", event.getSource()); item.put("source_key", event.getSource_key());
            item.put("occurred_at_epoch", event.getOccurred_at_epoch());
            index.put(item);
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_INDEX, index.toString()).apply();
        } catch (Exception error) {
            throw new IllegalStateException("Olay kaydedilemedi", error);
        }
        return event;
    }

    private void replaceTimestamp(String id, long occurredAtEpoch) {
        JSONArray index = read();
        for (int i = 0; i < index.length(); i++) {
            try {
                JSONObject item = index.getJSONObject(i);
                if (id.equals(item.optString("id"))) item.put("occurred_at_epoch", occurredAtEpoch);
            } catch (Exception ignored) { }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_INDEX, index.toString()).apply();
    }
    public void replaceSeasonId(String id, String seasonId) {
        JSONArray index = read();
        for (int i = 0; i < index.length(); i++) {
            JSONObject item = index.optJSONObject(i);
            if (item == null || !id.equals(item.optString("id"))) continue;
            try { item.put("season_id", seasonId == null ? "" : seasonId); } catch (Exception ignored) { }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_INDEX, index.toString()).apply();
    }
    public List<GardenEvent> load() {
        List<GardenEvent> events = new ArrayList<>();
        JSONArray index = read();
        for (int indexPosition = 0; indexPosition < index.length(); indexPosition++) {
            try {
                JSONObject item = index.getJSONObject(indexPosition);
                GardenEvent event = new GardenEvent();
                event.setId(item.optString("id"));
                event.setZone_id(item.optString("zone_id"));
                event.setSeason_id(item.optString("season_id"));
                event.setType(item.optString("type"));
                event.setNote(item.optString("note"));
                event.setSource(item.optString("source", "MANUAL")); event.setSource_key(item.optString("source_key"));
                event.setOccurred_at_epoch(item.optLong("occurred_at_epoch"));
                events.add(event);
            } catch (Exception ignored) {
                // A malformed local item must not hide the rest of the journal.
            }
        }
        events.sort(Comparator.comparingLong(GardenEvent::getOccurred_at_epoch).reversed());
        return events;
    }

    /** Edits only a user-created journal note; automated records are never stored here. */
    public boolean update(String id, String type, String note) {
        if (id == null || id.isBlank()) return false;
        JSONArray index = read();
        for (int i = 0; i < index.length(); i++) try {
            JSONObject item = index.getJSONObject(i);
            if (!id.equals(item.optString("id"))) continue;
            item.put("type", type == null ? "Gözlem" : type.trim());
            item.put("note", note == null ? "" : note.trim());
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_INDEX, index.toString()).apply();
            return true;
        } catch (Exception ignored) { }
        return false;
    }

    /** Deletes a manual note locally. The caller also removes its backup copy. */
    public boolean delete(String id) {
        if (id == null || id.isBlank()) return false;
        JSONArray current = read(); JSONArray remaining = new JSONArray(); boolean removed = false;
        for (int i = 0; i < current.length(); i++) try {
            JSONObject item = current.getJSONObject(i);
            if (id.equals(item.optString("id"))) removed = true; else remaining.put(item);
        } catch (Exception ignored) { }
        if (removed) context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_INDEX, remaining.toString()).apply();
        return removed;
    }

    public int removeByZone(String zoneId) {
        if (zoneId == null || zoneId.isBlank()) return 0;
        JSONArray current = read();
        JSONArray remaining = new JSONArray();
        int removed = 0;
        for (int i = 0; i < current.length(); i++) {
            JSONObject item = current.optJSONObject(i);
            if (item == null) continue;
            if (zoneId.equals(item.optString("zone_id"))) {
                removed++;
            } else {
                remaining.put(item);
            }
        }
        if (removed > 0) context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_INDEX, remaining.toString()).apply();
        return removed;
    }

    /** Removes only derived/system events belonging to a deleted empty season. */
    public int removeGeneratedBySeason(String seasonId) {
        if (seasonId == null || seasonId.isBlank()) return 0;
        JSONArray current = read();
        JSONArray remaining = new JSONArray();
        int removed = 0;
        for (int i = 0; i < current.length(); i++) {
            JSONObject item = current.optJSONObject(i);
            if (item == null) continue;
            boolean belongs = seasonId.equals(item.optString("season_id"));
            boolean fieldRecord = SeasonRecordPolicy.isFieldJournalEvent(
                    item.optString("type"),
                    item.optString("source", "MANUAL"),
                    item.optString("source_key")
            );
            if (belongs && !fieldRecord) {
                removed++;
            } else {
                remaining.put(item);
            }
        }
        if (removed > 0) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_INDEX, remaining.toString())
                    .apply();
        }
        return removed;
    }

    public int removeGeneratedForPhotos(
            String seasonId,
            Set<String> photoIds
    ) {
        if (seasonId == null || seasonId.isBlank()
                || photoIds == null || photoIds.isEmpty()) return 0;
        JSONArray current = read();
        JSONArray remaining = new JSONArray();
        int removed = 0;
        for (int i = 0; i < current.length(); i++) {
            JSONObject item = current.optJSONObject(i);
            if (item == null) continue;
            boolean derived = seasonId.equals(item.optString("season_id"))
                    && !SeasonRecordPolicy.isFieldJournalEvent(
                    item.optString("type"),
                    item.optString("source", "MANUAL"),
                    item.optString("source_key"))
                    && referencesAny(
                    item.optString("source_key"),
                    photoIds);
            if (derived) {
                removed++;
            } else {
                remaining.put(item);
            }
        }
        if (removed > 0) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_INDEX, remaining.toString())
                    .apply();
        }
        return removed;
    }

    private static boolean referencesAny(String value, Set<String> ids) {
        String source = value == null ? "" : value;
        for (String id : ids) {
            if (id != null && !id.isBlank() && source.contains(id)) return true;
        }
        return false;
    }

    private JSONArray read() {
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_INDEX, "[]");
        try {
            return new JSONArray(raw == null ? "[]" : raw);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }
}
