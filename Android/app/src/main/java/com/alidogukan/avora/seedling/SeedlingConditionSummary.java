package com.alidogukan.avora.seedling;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Formats the backend's evaluated conditions as a compact, readable checklist. */
public final class SeedlingConditionSummary {
    private SeedlingConditionSummary() { }

    public static String bulletList(String message) {
        if (message == null || message.isBlank()) return "";
        Set<String> unique = new LinkedHashSet<>();
        for (String part : message.trim().split("(?<=[.!?])\\s+")) {
            String condition = part == null ? "" : part.trim();
            if (!condition.isBlank()) unique.add(condition);
        }
        if (unique.isEmpty()) return "";
        List<String> lines = new ArrayList<>();
        for (String condition : unique) lines.add("• " + condition);
        return String.join("\n", lines);
    }
}
