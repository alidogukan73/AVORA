package com.alidogukan.avora.plantassistant;

import android.content.Context;
import android.content.SharedPreferences;

import com.alidogukan.avora.models.GardenPhoto;
import com.alidogukan.avora.photos.LocalGardenPhotoStore;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Stores the most recent actionable plant-assistant recommendation on this phone. */
public final class PlantAssistantRecommendationStore {
    private static final String PREFS = "plant_assistant_recommendation";
    private static final String PREVIOUS_PREFS = "garden_assistant_recommendation";
    private static final String LEGACY_PREFS = "plant_doctor_recommendation";
    private static final String KEY_TITLE = "title";
    private static final String KEY_ADVICE = "advice";
    private static final String KEY_RECORD_ID = "record_id";
    private static final String KEY_ZONE_ID = "zone_id";
    private static final String KEY_SEASON_ID = "season_id";
    private static final String KEY_URGENCY = "urgency";
    private static final String KEY_CREATED_AT = "created_at";
    private static final String KEY_HEALTH_SIGNALS = "health_signals";
    private static final long SIGNAL_RETENTION_SECONDS = 30L * 24L * 60L * 60L;
    private static final Object LOCK = new Object();

    private PlantAssistantRecommendationStore() { }

    public static void save(Context context, String title, String advice) {
        save(context, "", "", title, advice);
    }

    public static void save(
            Context context,
            String zoneId,
            String urgency,
            String title,
            String advice
    ) {
        save(context, zoneId, "", urgency, title, advice);
    }

    public static void save(Context context, String zoneId, String seasonId,
                            String urgency, String title, String advice) {
        save(context, zoneId, seasonId, urgency, title, advice, "");
    }

    public static void save(Context context, String zoneId, String seasonId,
                            String urgency, String title, String advice, String recordId) {
        String cleanAdvice = clean(advice);
        if (cleanAdvice.isEmpty()) return;
        String cleanZoneId = clean(zoneId);
        String cleanSeasonId = clean(seasonId);
        long createdAt = System.currentTimeMillis() / 1000L;
        synchronized (LOCK) {
            SharedPreferences values = preferences(context);
            String signalJson = includeLegacySignal(
                    values.getString(KEY_HEALTH_SIGNALS, "{}"), values, createdAt);
            SharedPreferences.Editor editor = values.edit()
                    .putString(KEY_TITLE, clean(title))
                    .putString(KEY_ADVICE, cleanAdvice)
                    .putString(KEY_ZONE_ID, cleanZoneId)
                    .putString(KEY_SEASON_ID, cleanSeasonId)
                    .putString(KEY_URGENCY, clean(urgency))
                    .putString(KEY_RECORD_ID, clean(recordId))
                    .putLong(KEY_CREATED_AT, createdAt);
            if (!cleanZoneId.isEmpty()) {
                editor.putString(KEY_HEALTH_SIGNALS, updatedSignals(
                        signalJson,
                        cleanZoneId, cleanSeasonId, clean(urgency),
                        clean(title), cleanAdvice, clean(recordId), createdAt));
            }
            editor.apply();
        }
    }

    public static PlantAssistantHealthSignal healthSignal(Context context) {
        SharedPreferences preferences = preferences(context);
        return signalFromLegacy(preferences);
    }

    /** Returns the latest finding for every zone/season, including the old single record. */
    public static List<PlantAssistantHealthSignal> healthSignals(Context context) {
        synchronized (LOCK) {
            SharedPreferences preferences = preferences(context);
            List<PlantAssistantHealthSignal> result = new ArrayList<>();
            PlantAssistantHealthSignal legacy = signalFromLegacy(preferences);
            try {
                JSONObject values = new JSONObject(
                        preferences.getString(KEY_HEALTH_SIGNALS, "{}"));
                Iterator<String> keys = values.keys();
                while (keys.hasNext()) {
                    JSONObject item = values.optJSONObject(keys.next());
                    if (item == null) continue;
                    String zoneId = clean(item.optString(KEY_ZONE_ID));
                    String seasonId = clean(item.optString(KEY_SEASON_ID));
                    String advice = clean(item.optString(KEY_ADVICE));
                    if (advice.isEmpty() && zoneId.equals(legacy.getZoneId())
                            && seasonId.equals(legacy.getSeasonId())) {
                        advice = clean(legacy.getAdvice());
                    }
                    PlantAssistantHealthSignal signal = new PlantAssistantHealthSignal(
                            zoneId, seasonId,
                            clean(item.optString(KEY_URGENCY)),
                            clean(item.optString(KEY_TITLE)),
                            advice,
                            clean(item.optString(KEY_RECORD_ID)),
                            item.optLong(KEY_CREATED_AT, 0L)
                    );
                    if (!signal.getZoneId().isEmpty()) result.add(signal);
                }
            } catch (Exception ignored) { }
            if (!legacy.getZoneId().isEmpty() && !containsScope(
                    result, legacy.getZoneId(), legacy.getSeasonId())) {
                result.add(legacy);
            }
            boolean needsPhotoLookup = false;
            for (PlantAssistantHealthSignal signal : result) {
                if (clean(signal.getAdvice()).isEmpty()) {
                    needsPhotoLookup = true;
                    break;
                }
            }
            if (!needsPhotoLookup) return result;
            List<GardenPhoto> photos = new LocalGardenPhotoStore(context).load();
            List<PlantAssistantHealthSignal> enriched = new ArrayList<>(result.size());
            for (PlantAssistantHealthSignal signal : result) {
                enriched.add(PlantAssistantRecordResolver.enrich(signal, photos));
            }
            return enriched;
        }
    }

    public static String summary(Context context) {
        SharedPreferences preferences = preferences(context);
        String advice = clean(preferences.getString(KEY_ADVICE, ""));
        if (advice.isEmpty()) return "Şu an öneri yok";
        String title = clean(preferences.getString(KEY_TITLE, ""));
        String text = title.isEmpty() ? advice : title + " · " + advice;
        return text.length() <= 145 ? text : text.substring(0, 142).trim() + "…";
    }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private static String updatedSignals(String raw, String zoneId, String seasonId,
                                         String urgency, String title, String advice,
                                         String recordId, long createdAt) {
        JSONObject values;
        try {
            values = new JSONObject(raw == null ? "{}" : raw);
        } catch (Exception ignored) {
            values = new JSONObject();
        }
        try {
            long oldestAllowed = createdAt - SIGNAL_RETENTION_SECONDS;
            List<String> expired = new ArrayList<>();
            Iterator<String> keys = values.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject item = values.optJSONObject(key);
                if (item == null || item.optLong(KEY_CREATED_AT, 0L) < oldestAllowed) {
                    expired.add(key);
                }
            }
            for (String key : expired) values.remove(key);
            JSONObject item = new JSONObject();
            item.put(KEY_ZONE_ID, zoneId);
            item.put(KEY_SEASON_ID, seasonId);
            item.put(KEY_URGENCY, urgency);
            item.put(KEY_TITLE, title);
            item.put(KEY_ADVICE, advice);
            item.put(KEY_RECORD_ID, recordId);
            item.put(KEY_CREATED_AT, createdAt);
            if (!seasonId.isEmpty()) {
                values.remove(scopeKey(zoneId, ""));
            }
            values.put(scopeKey(zoneId, seasonId), item);
        } catch (Exception ignored) { }
        return values.toString();
    }

    private static String includeLegacySignal(
            String raw,
            SharedPreferences preferences,
            long nowEpoch
    ) {
        JSONObject values;
        try {
            values = new JSONObject(raw == null ? "{}" : raw);
        } catch (Exception ignored) {
            values = new JSONObject();
        }
        PlantAssistantHealthSignal legacy = signalFromLegacy(preferences);
        long createdAt = legacy.getCreatedAtEpoch();
        if (legacy.getZoneId().isEmpty() || createdAt <= 0L || createdAt > nowEpoch
                || nowEpoch - createdAt > SIGNAL_RETENTION_SECONDS) {
            return values.toString();
        }
        String key = scopeKey(legacy.getZoneId(), legacy.getSeasonId());
        if (values.has(key)) return values.toString();
        try {
            JSONObject item = new JSONObject();
            item.put(KEY_ZONE_ID, legacy.getZoneId());
            item.put(KEY_SEASON_ID, legacy.getSeasonId());
            item.put(KEY_URGENCY, legacy.getUrgency());
            item.put(KEY_TITLE, legacy.getTitle());
            item.put(KEY_ADVICE, legacy.getAdvice());
            item.put(KEY_RECORD_ID, legacy.getRecordId());
            item.put(KEY_CREATED_AT, createdAt);
            values.put(key, item);
        } catch (Exception ignored) { }
        return values.toString();
    }

    private static String scopeKey(String zoneId, String seasonId) {
        return zoneId + "\u001f" + seasonId;
    }

    private static boolean containsScope(List<PlantAssistantHealthSignal> values,
                                         String zoneId, String seasonId) {
        for (PlantAssistantHealthSignal value : values) {
            if (zoneId.equals(value.getZoneId())
                    && seasonId.equals(value.getSeasonId())) return true;
        }
        return false;
    }

    private static PlantAssistantHealthSignal signalFromLegacy(
            SharedPreferences preferences) {
        return new PlantAssistantHealthSignal(
                preferences.getString(KEY_ZONE_ID, ""),
                preferences.getString(KEY_SEASON_ID, ""),
                preferences.getString(KEY_URGENCY, ""),
                preferences.getString(KEY_TITLE, ""),
                preferences.getString(KEY_ADVICE, ""),
                preferences.getString(KEY_RECORD_ID, ""),
                preferences.getLong(KEY_CREATED_AT, 0L)
        );
    }

    /** Migrates the previous app storage once, without losing any result. */
    private static SharedPreferences preferences(Context context) {
        SharedPreferences current = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (current.contains(KEY_ADVICE)) return current;

        if (migrate(current, context.getSharedPreferences(PREVIOUS_PREFS, Context.MODE_PRIVATE))) {
            return current;
        }
        migrate(current, context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE));
        return current;
    }

    private static boolean migrate(SharedPreferences current, SharedPreferences previous) {
        String advice = previous.getString(KEY_ADVICE, "");
        if (clean(advice).isEmpty()) return false;

        current.edit()
                .putString(KEY_TITLE, previous.getString(KEY_TITLE, ""))
                .putString(KEY_ADVICE, advice)
                .putString(KEY_ZONE_ID, previous.getString(KEY_ZONE_ID, ""))
                .putString(KEY_SEASON_ID, previous.getString(KEY_SEASON_ID, ""))
                .putString(KEY_URGENCY, previous.getString(KEY_URGENCY, ""))
                .putLong(KEY_CREATED_AT, previous.getLong(KEY_CREATED_AT, 0L))
                .apply();
        return true;
    }
}
