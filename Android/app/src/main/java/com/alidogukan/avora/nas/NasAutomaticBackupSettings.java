package com.alidogukan.avora.nas;

import android.content.Context;
import android.content.SharedPreferences;

/** Small local state store for the opt-in automatic NAS backup job. */
public final class NasAutomaticBackupSettings {
    private static final String PREFERENCES = "avora_nas_automatic_backup";
    private static final String ENABLED = "enabled";
    private static final String LAST_SUCCESS = "last_success";
    private static final String LAST_FAILURE = "last_failure";
    private static final String NOTIFIED_FAILURE = "notified_failure";

    private final SharedPreferences preferences;

    public NasAutomaticBackupSettings(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    public boolean isEnabled() {
        return preferences.getBoolean(ENABLED, false);
    }

    public void setEnabled(boolean enabled) {
        SharedPreferences.Editor editor = preferences.edit().putBoolean(ENABLED, enabled);
        if (!enabled) editor.remove(LAST_FAILURE).remove(NOTIFIED_FAILURE);
        editor.apply();
    }

    public long lastSuccessAt() {
        return preferences.getLong(LAST_SUCCESS, 0L);
    }

    public String lastFailure() {
        return preferences.getString(LAST_FAILURE, "");
    }

    public void recordSuccess(long epochMillis) {
        preferences.edit()
                .putLong(LAST_SUCCESS, epochMillis)
                .remove(LAST_FAILURE)
                .remove(NOTIFIED_FAILURE)
                .apply();
    }

    public boolean recordFailureAndShouldNotify(String code) {
        String safeCode = code == null || code.isBlank() ? "NAS_BACKUP_FAILED" : code;
        String notified = preferences.getString(NOTIFIED_FAILURE, "");
        preferences.edit().putString(LAST_FAILURE, safeCode).apply();
        if (safeCode.equals(notified)) return false;
        preferences.edit().putString(NOTIFIED_FAILURE, safeCode).apply();
        return true;
    }
}
