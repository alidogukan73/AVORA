package com.alidogukan.avora.seedling;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Persists user-created varieties per crop without adding another cloud listener. */
public final class SeedlingVarietyStore {
    public static final String PREFERENCES_NAME = "avora_seedling_varieties";

    private final SharedPreferences preferences;

    public SeedlingVarietyStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(
                PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public Set<String> load(String cropId) {
        if (cropId == null || cropId.isBlank()) return Collections.emptySet();
        Set<String> stored = preferences.getStringSet(cropId, Collections.emptySet());
        return stored == null ? Collections.emptySet() : new LinkedHashSet<>(stored);
    }

    public void add(String cropId, String variety) {
        String normalized = SeedlingVarietyCatalog.normalize(variety);
        if (cropId == null || cropId.isBlank() || normalized.isBlank()
                || normalized.length() > SeedlingVarietyCatalog.MAX_NAME_LENGTH) {
            return;
        }
        Set<String> values = new LinkedHashSet<>(load(cropId));
        if (SeedlingVarietyCatalog.find(values, normalized) == null) {
            values.add(normalized);
            preferences.edit().putStringSet(cropId, values).apply();
        }
    }
}
