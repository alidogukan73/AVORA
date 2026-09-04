package com.alidogukan.avora.plantassistant;

import android.content.Context;
import android.content.SharedPreferences;

/** Stores the most recent actionable plant-assistant recommendation on this phone. */
public final class PlantAssistantRecommendationStore {
    private static final String PREFS = "plant_assistant_recommendation";
    private static final String PREVIOUS_PREFS = "garden_assistant_recommendation";
    private static final String LEGACY_PREFS = "plant_doctor_recommendation";
    private static final String KEY_TITLE = "title";
    private static final String KEY_ADVICE = "advice";
    private static final String KEY_ZONE_ID = "zone_id";
    private static final String KEY_SEASON_ID = "season_id";
    private static final String KEY_URGENCY = "urgency";
    private static final String KEY_CREATED_AT = "created_at";

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
        String cleanAdvice = clean(advice);
        if (cleanAdvice.isEmpty()) return;
        preferences(context)
                .edit()
                .putString(KEY_TITLE, clean(title))
                .putString(KEY_ADVICE, cleanAdvice)
                .putString(KEY_ZONE_ID, clean(zoneId))
                .putString(KEY_SEASON_ID, clean(seasonId))
                .putString(KEY_URGENCY, clean(urgency))
                .putLong(KEY_CREATED_AT, System.currentTimeMillis() / 1000L)
                .apply();
    }

    public static PlantAssistantHealthSignal healthSignal(Context context) {
        SharedPreferences preferences = preferences(context);
        return new PlantAssistantHealthSignal(
                preferences.getString(KEY_ZONE_ID, ""),
                preferences.getString(KEY_SEASON_ID, ""),
                preferences.getString(KEY_URGENCY, ""),
                preferences.getString(KEY_TITLE, ""),
                preferences.getLong(KEY_CREATED_AT, 0L)
        );
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
