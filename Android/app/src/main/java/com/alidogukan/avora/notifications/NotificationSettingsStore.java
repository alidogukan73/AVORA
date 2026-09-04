package com.alidogukan.avora.notifications;

import android.content.Context;
import android.content.SharedPreferences;

import com.alidogukan.avora.settings.SettingsSyncPolicy;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/** Local and cloud-backed preferences for AVORA notifications and reminders. */
public final class NotificationSettingsStore {
    private static final String PREFS = "avora_notification_settings";
    private static final String[] CATEGORIES = {
            "irrigation", "fertilization", "plant", "weather", "device", "stock"
    };
    private static final String[] REMINDERS = {"irrigation", "fertilization", "plant"};
    private final SharedPreferences prefs;

    public NotificationSettingsStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isCategoryEnabled(String type) {
        return prefs.getBoolean("category_" + categoryFor(type), true);
    }

    public void setCategoryEnabled(String category, boolean enabled) {
        prefs.edit().putBoolean("category_" + category, enabled)
                .putLong("updated_at", System.currentTimeMillis()).apply();
    }

    public boolean isReminderEnabled(String reminder) {
        return prefs.getBoolean("reminder_" + reminder, true);
    }

    public void setReminderEnabled(String reminder, boolean enabled) {
        prefs.edit().putBoolean("reminder_" + reminder, enabled)
                .putLong("updated_at", System.currentTimeMillis()).apply();
    }

    public boolean isQuietHoursEnabled() {
        return prefs.getBoolean("quiet_enabled", false);
    }

    public void setQuietHoursEnabled(boolean enabled) {
        prefs.edit().putBoolean("quiet_enabled", enabled)
                .putLong("updated_at", System.currentTimeMillis()).apply();
    }

    public int quietStartHour() {
        return SettingsSyncPolicy.validHour(prefs.getInt("quiet_start", 22), 22);
    }

    public int quietEndHour() {
        return SettingsSyncPolicy.validHour(prefs.getInt("quiet_end", 7), 7);
    }

    public void setQuietHours(int startHour, int endHour) {
        prefs.edit()
                .putInt("quiet_start", SettingsSyncPolicy.validHour(startHour, 22))
                .putInt("quiet_end", SettingsSyncPolicy.validHour(endHour, 7))
                .putLong("updated_at", System.currentTimeMillis())
                .apply();
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> values = new HashMap<>();
        for (String category : CATEGORIES) {
            values.put("category_" + category, isCategoryEnabled(category));
        }
        for (String reminder : REMINDERS) {
            values.put("reminder_" + reminder, isReminderEnabled(reminder));
        }
        values.put("quiet_enabled", isQuietHoursEnabled());
        values.put("quiet_start", quietStartHour());
        values.put("quiet_end", quietEndHour());
        values.put("updated_at", prefs.getLong("updated_at", 0L));
        return values;
    }

    /** Newer cloud preferences safely replace local values on another device. */
    public boolean applyBackup(Map<String, Object> values) {
        if (values == null || values.isEmpty()) return false;
        long remoteUpdated = number(values.get("updated_at"));
        if (!SettingsSyncPolicy.isCloudValueNewer(
                remoteUpdated, prefs.getLong("updated_at", 0L))) {
            return false;
        }
        SharedPreferences.Editor editor = prefs.edit();
        for (String category : CATEGORIES) {
            editor.putBoolean("category_" + category,
                    bool(values.get("category_" + category), true));
        }
        for (String reminder : REMINDERS) {
            editor.putBoolean("reminder_" + reminder,
                    bool(values.get("reminder_" + reminder), true));
        }
        editor.putBoolean("quiet_enabled", bool(values.get("quiet_enabled"), false));
        editor.putInt("quiet_start", SettingsSyncPolicy.validHour(
                numberOr(values.get("quiet_start"), 22L), 22));
        editor.putInt("quiet_end", SettingsSyncPolicy.validHour(
                numberOr(values.get("quiet_end"), 7L), 7));
        editor.putLong("updated_at", remoteUpdated).apply();
        return true;
    }

    private static boolean bool(Object value, boolean fallback) {
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    private static long number(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private static long numberOr(Object value, long fallback) {
        return value instanceof Number ? ((Number) value).longValue() : fallback;
    }

    /** In-app records remain visible; this only decides whether a phone alert may be shown. */
    public boolean shouldShowPhoneAlert(String type) {
        if (!isCategoryEnabled(type) || !isQuietHoursEnabled()) return isCategoryEnabled(type);
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int start = quietStartHour();
        int end = quietEndHour();
        return !NotificationPolicy.isQuietHour(true, hour, start, end);
    }

    public static String categoryFor(String type) {
        return NotificationPolicy.categoryFor(type);

    }
}