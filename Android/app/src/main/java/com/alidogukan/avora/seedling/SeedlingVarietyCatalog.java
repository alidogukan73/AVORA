package com.alidogukan.avora.seedling;

import androidx.annotation.Nullable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Normalizes and combines built-in and user-created seedling varieties. */
public final class SeedlingVarietyCatalog {
    public static final int MAX_NAME_LENGTH = 80;

    private SeedlingVarietyCatalog() { }

    public static List<String> merge(List<String> builtIns, Collection<String> custom) {
        List<String> result = new ArrayList<>();
        if (builtIns != null) {
            for (String value : builtIns) addUnique(result, value);
        }

        List<String> customValues = new ArrayList<>();
        if (custom != null) {
            for (String value : custom) {
                String normalized = normalize(value);
                if (!normalized.isBlank() && find(result, normalized) == null) {
                    addUnique(customValues, normalized);
                }
            }
        }
        customValues.sort(Collator.getInstance(Locale.forLanguageTag("tr-TR")));
        result.addAll(customValues);
        return result;
    }

    @Nullable
    public static String find(Collection<String> values, String requested) {
        String normalized = normalize(requested);
        if (normalized.isBlank() || values == null) return null;
        for (String value : values) {
            if (normalize(value).equalsIgnoreCase(normalized)) return value;
        }
        return null;
    }

    public static String normalize(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("\\s+", " ");
    }

    private static void addUnique(List<String> target, String value) {
        String normalized = normalize(value);
        if (!normalized.isBlank() && find(target, normalized) == null) {
            target.add(normalized);
        }
    }
}
