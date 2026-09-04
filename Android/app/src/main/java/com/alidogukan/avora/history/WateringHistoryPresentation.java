package com.alidogukan.avora.history;

import com.alidogukan.avora.R;
import com.alidogukan.avora.models.GardenSeason;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.Statistics;
import com.alidogukan.avora.models.WateringHistory;
import com.alidogukan.avora.season.SeasonDisplayIdentity;
import com.alidogukan.avora.statistics.StatisticsCalculator;
import com.alidogukan.avora.zones.PhysicalZoneIdentity;
import com.alidogukan.avora.zones.ZoneCapacityPolicy;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Read-only rules shared by history cards, summaries and regression tests. */
public final class WateringHistoryPresentation {
    public enum State { LOADING, CONTENT, EMPTY, ERROR }
    public enum Outcome { COMPLETED, SIMULATED, NOT_STARTED, WARNING, INTERRUPTED }
    private WateringHistoryPresentation() { }

    public static List<WateringHistory> select(List<WateringHistory> history, String zoneId, ZoneId zone) {
        List<WateringHistory> result = new ArrayList<>();
        String selected = safe(zoneId);
        if (history != null) for (WateringHistory item : history) {
            if (item != null && (selected.isEmpty() || selected.equals(safe(item.getZoneId())))) {
                result.add(item);
            }
        }
        result.sort((left, right) -> {
            int time = Long.compare(timestamp(right, zone), timestamp(left, zone));
            return time != 0 ? time : safe(right.getRecordId()).compareTo(safe(left.getRecordId()));
        });
        return result;
    }

    /** The card displays the start time; old finish-only records remain readable. */
    public static long timestamp(WateringHistory item, ZoneId zone) {
        if (item == null) return 0;
        long started = StatisticsCalculator.timestamp(item.getStartedAt(), zone);
        return started > 0 ? started : StatisticsCalculator.timestamp(item.getFinishedAt(), zone);
    }

    public static String date(WateringHistory item, ZoneId zone, Locale locale) {
        return formatDate(item, zone, locale, "dd.MM.yyyy");
    }
    public static String time(WateringHistory item, ZoneId zone, Locale locale) {
        return formatDate(item, zone, locale, "HH:mm");
    }
    private static String formatDate(WateringHistory item, ZoneId zone, Locale locale, String pattern) {
        long time = timestamp(item, zone);
        return time <= 0 ? "—" : DateTimeFormatter.ofPattern(pattern, locale)
                .format(Instant.ofEpochMilli(time).atZone(zone));
    }

    public static Long before(WateringHistory item) {
        return item != null && item.hasMoistureBefore() && valid(item.getMoistureBefore())
                ? item.getMoistureBefore() : null;
    }
    public static Long after(WateringHistory item) {
        return item != null && item.hasMoistureAfter() && valid(item.getMoistureAfter())
                ? item.getMoistureAfter() : null;
    }
    public static Long delta(WateringHistory item) {
        Long before = before(item), after = after(item);
        return before == null || after == null ? null : after - before;
    }
    private static boolean valid(long value) { return value >= 0 && value <= 100; }

    /** Same count/duration/success rules as the Statistics screen; unknown readings are excluded. */
    public static Summary summarize(List<WateringHistory> history, long now, ZoneId zone) {
        Statistics totals = StatisticsCalculator.calculate(history, "", now, zone);
        long measured = 0;
        double deltaSum = 0;
        if (history != null) for (WateringHistory item : history) {
            Long change = delta(item);
            if (change != null) { measured++; deltaSum += change; }
        }
        return new Summary(totals, measured, measured == 0 ? null : deltaSum / measured);
    }

    public static final class Summary {
        public final Statistics totals;
        public final long measuredCount;
        public final Double averageDelta;
        private Summary(Statistics totals, long measuredCount, Double averageDelta) {
            this.totals = totals;
            this.measuredCount = measuredCount;
            this.averageDelta = averageDelta;
        }
    }

    public static State state(List<WateringHistory> visible, boolean loading, boolean failed) {
        if (visible != null && !visible.isEmpty()) return State.CONTENT;
        if (loading) return State.LOADING;
        if (failed) return State.ERROR;
        return visible == null ? State.LOADING : State.EMPTY;
    }

    public static boolean sameRecord(WateringHistory left, WateringHistory right) {
        return left == right || (!safe(left.getRecordId()).isEmpty()
                && Objects.equals(left.getRecordId(), right.getRecordId()));
    }

    public static boolean sameContent(WateringHistory left, WateringHistory right) {
        return Objects.equals(left.getStartedAt(), right.getStartedAt())
                && Objects.equals(left.getFinishedAt(), right.getFinishedAt())
                && left.getDuration() == right.getDuration()
                && Objects.equals(before(left), before(right))
                && Objects.equals(after(left), after(right))
                && left.isCompleted() == right.isCompleted()
                && Objects.equals(left.getStopReason(), right.getStopReason())
                && Objects.equals(left.getMode(), right.getMode())
                && Objects.equals(left.getZoneId(), right.getZoneId())
                && Objects.equals(left.getSeasonId(), right.getSeasonId())
                && Objects.equals(left.getSeasonIds(), right.getSeasonIds());
    }

    /** Never relabel an old watering with a later season's crop. */
    public static String label(WateringHistory item, Map<String, GardenZone> zones,
                               Map<String, GardenSeason> seasons, String legacyLabel) {
        String zoneId = safe(item.getZoneId());
        if (zoneId.isEmpty()) return legacyLabel;
        GardenZone current = zones.get(zoneId);
        String area = current == null ? "" : PhysicalZoneIdentity.name(current);
        String archivedArea = "";
        Set<String> crops = new LinkedHashSet<>();
        Set<String> seasonIds = new LinkedHashSet<>();
        if (!safe(item.getSeasonId()).isEmpty()) seasonIds.add(safe(item.getSeasonId()));
        if (item.getSeasonIds() != null) for (String id : item.getSeasonIds()) {
            if (!safe(id).isEmpty()) seasonIds.add(safe(id));
        }
        for (String id : seasonIds) {
            GardenSeason season = seasons.get(id);
            if (season == null || !zoneId.equals(safe(season.getZone_id()))) continue;
            if (archivedArea.isEmpty()) archivedArea = safe(season.getArea_name());
            String crop = SeasonDisplayIdentity.name(season, null);
            if (!crop.isBlank()) crops.add(SeasonDisplayIdentity.emoji(season, null) + " " + crop);
        }
        if (!archivedArea.isEmpty()) area = archivedArea;
        if (area.isEmpty() && ZoneCapacityPolicy.isValidZoneId(zoneId)) {
            GardenZone identity = new GardenZone();
            identity.setZone_id(zoneId);
            area = PhysicalZoneIdentity.name(identity);
        }
        if (area.isEmpty()) area = zoneId;
        return crops.isEmpty() ? area : area + " · " + String.join(" + ", crops);
    }

    public static Outcome outcome(WateringHistory item) {
        if (item.isCompleted()) return Outcome.COMPLETED;
        switch (safe(item.getStopReason()).toUpperCase(Locale.ROOT)) {
            case "VALVE_SIMULATION": return Outcome.SIMULATED;
            case "SHARED_PUMP_BUSY":
            case "ZERO_DURATION": return Outcome.NOT_STARTED;
            case "SAFETY_TIMEOUT":
            case "TIMEOUT":
            case "MOISTURE_REACHED":
            case "TARGET_REACHED": return Outcome.WARNING;
            default: return Outcome.INTERRUPTED;
        }
    }

    public static int reasonResource(String reason) {
        switch (safe(reason).toUpperCase(Locale.ROOT)) {
            case "": return R.string.history_default_stop_reason;
            case "COMPLETED":
            case "DURATION_COMPLETED":
            case "WATERING_COMPLETED": return R.string.history_reason_completed;
            case "MANUAL_STOP":
            case "MANUAL":
            case "USER_STOPPED": return R.string.history_reason_manual_stop;
            case "MOISTURE_REACHED":
            case "TARGET_REACHED": return R.string.history_reason_target_reached;
            case "SYSTEM_DISABLED": return R.string.history_reason_system_disabled;
            case "DEVICE_OFFLINE": return R.string.history_reason_device_offline;
            case "SAFETY_TIMEOUT":
            case "TIMEOUT": return R.string.history_reason_timeout;
            case "MANUAL_MODE": return R.string.history_reason_manual_mode;
            case "ERROR": return R.string.history_reason_error;
            case "VALVE_SIMULATION": return R.string.history_reason_simulation;
            case "SHARED_PUMP_BUSY": return R.string.history_reason_pump_busy;
            case "ZERO_DURATION": return R.string.history_reason_zero_duration;
            default: return 0;
        }
    }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
