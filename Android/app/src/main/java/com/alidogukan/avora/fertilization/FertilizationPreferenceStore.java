package com.alidogukan.avora.fertilization;

import android.content.Context;
import android.content.SharedPreferences;

import com.alidogukan.avora.settings.SettingsSyncPolicy;

import java.util.HashMap;
import java.util.Map;

/** User choices that influence ranking, never fertilizer safety gates. */
public final class FertilizationPreferenceStore {
    public static final String PREFER_ORGANIC_INPUTS = "prefer_organic_inputs";
    private static final String PREFS = "avora_fertilization_preferences";
    private final SharedPreferences preferences;

    public FertilizationPreferenceStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean preferOrganicInputs() {
        return preferences.getBoolean(PREFER_ORGANIC_INPUTS, true);
    }

    public void setPreferOrganicInputs(boolean enabled) {
        preferences.edit()
                .putBoolean(PREFER_ORGANIC_INPUTS, enabled)
                .putLong("updated_at", System.currentTimeMillis())
                .apply();
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> values = new HashMap<>();
        values.put(PREFER_ORGANIC_INPUTS, preferOrganicInputs());
        values.put("updated_at", preferences.getLong("updated_at", 0L));
        return values;
    }

    public boolean applyBackup(Map<String, Object> values) {
        if (values == null || values.isEmpty()) return false;
        Object remoteTimeValue = values.get("updated_at");
        long remoteTime = remoteTimeValue instanceof Number
                ? ((Number) remoteTimeValue).longValue() : 0L;
        if (!SettingsSyncPolicy.isCloudValueNewer(
                remoteTime, preferences.getLong("updated_at", 0L))) {
            return false;
        }
        Object enabledValue = values.get(PREFER_ORGANIC_INPUTS);
        boolean enabled = !(enabledValue instanceof Boolean)
                || (Boolean) enabledValue;
        preferences.edit()
                .putBoolean(PREFER_ORGANIC_INPUTS, enabled)
                .putLong("updated_at", remoteTime)
                .apply();
        return true;
    }
}
