package com.alidogukan.avora.plantassistant;

import java.util.Locale;

/** Keeps AI urgency values consistent across cards, health scoring and notifications. */
public final class PlantAssistantUrgency {
    private PlantAssistantUrgency() { }

    public static int severity(String urgency) {
        String value = urgency == null
                ? "" : urgency.trim().toLowerCase(Locale.ROOT);
        if (value.equals("yüksek") || value.equals("acil")
                || value.equals("kritik") || value.equals("high")
                || value.equals("critical") || value.equals("urgent")) return 2;
        if (value.equals("orta") || value.equals("medium")
                || value.equals("moderate")) return 1;
        return 0;
    }

    public static boolean isHigh(String urgency) {
        return severity(urgency) >= 2;
    }
}
