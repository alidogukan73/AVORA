package com.alidogukan.avora.fertilization;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;

/** Calendar rules shared by the alarm and the reminder evaluator. */
public final class FertilizerReminderTiming {
    private FertilizerReminderTiming() { }

    public static long nextCheckMillis(long nowMillis, ZoneId zone) {
        LocalDate today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate();
        for (int hour : new int[]{8, 12, 18}) {
            long candidate = today.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli();
            if (candidate > nowMillis) return candidate;
        }
        return today.plusDays(1).atTime(8, 0).atZone(zone).toInstant().toEpochMilli();
    }

    public static String reminderSlot(long days, LocalTime time) {
        if (time.isBefore(LocalTime.of(8, 0))) return null;
        if (days == 0L) {
            if (time.isBefore(LocalTime.NOON)) return "morning";
            if (time.isBefore(LocalTime.of(18, 0))) return "noon";
            return "evening";
        }
        if (days == -1L) return "next_day";
        return days == -7L ? "final" : null;
    }

    public static String cycleKey(String zoneId, String seasonId, String stage,
                                  String applicationType, String productId,
                                  long latestApplicationEpoch) {
        // Length prefixes avoid ambiguous identifiers when a value contains a separator.
        StringBuilder key = new StringBuilder("cycle:");
        for (String value : new String[]{zoneId, seasonId, stage, applicationType, productId}) {
            String part = value == null ? "" : value;
            key.append(part.length()).append(':').append(part);
        }
        return key.append(':').append(latestApplicationEpoch).toString();
    }

    /** Keep an outstanding, unscheduled suggestion on its first due date across launches. */
    public static long dueEpoch(long explicitEpoch, long savedEpoch, long nowEpoch) {
        if (explicitEpoch > 0L) return explicitEpoch;
        return savedEpoch > 0L ? savedEpoch : nowEpoch;
    }

    /** Adopt the most recent old reminder so an upgrade does not restart it as "today". */
    public static long legacyDueEpoch(Map<String, ?> state, String zoneId,
                                      String applicationType, long latestApplicationEpoch,
                                      long nowEpoch, ZoneId zone) {
        String prefix = zoneId + ":" + applicationType + ":";
        long latest = 0L;
        for (Map.Entry<String, ?> entry : state.entrySet()) {
            if (!Boolean.TRUE.equals(entry.getValue()) || !entry.getKey().startsWith(prefix)) continue;
            String tail = entry.getKey().substring(prefix.length());
            int separator = tail.indexOf(':');
            if (separator < 0) continue;
            try {
                long epoch = LocalDate.parse(tail.substring(0, separator))
                        .atStartOfDay(zone).toEpochSecond();
                if (epoch > latestApplicationEpoch && epoch <= nowEpoch) latest = Math.max(latest, epoch);
            } catch (java.time.DateTimeException ignored) {
                // Unrelated or invalid old preference keys are not dates.
            }
        }
        return latest;
    }
}
