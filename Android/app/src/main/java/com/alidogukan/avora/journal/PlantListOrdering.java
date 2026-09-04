package com.alidogukan.avora.journal;

import com.alidogukan.avora.models.FertilizationProfile;
import com.alidogukan.avora.models.GardenSeason;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.ZoneIrrigationStatus;
import com.alidogukan.avora.season.SeasonDisplayIdentity;
import com.alidogukan.avora.zones.PhysicalZoneIdentity;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Sorts the actual journal cards, including separate crops sharing one area. */
public final class PlantListOrdering {
    // Persisted preference IDs: append new modes, never renumber existing ones.
    public static final int SORT_SMART = 0;
    public static final int SORT_ATTENTION = 1;
    public static final int SORT_MOISTURE = 2;
    public static final int SORT_UPDATED = 3;
    public static final int SORT_NAME = 4;
    public static final int SORT_AREA = 5;
    private static final long CURRENT_SENSOR_SECONDS = 90L;

    private PlantListOrdering() { }

    public static int normalizeSortMode(int mode) {
        return mode >= SORT_SMART && mode <= SORT_AREA ? mode : SORT_SMART;
    }

    public static List<Entry> entries(List<GardenZone> zones, List<GardenSeason> seasons,
                                      int mode, long nowEpoch, Locale locale) {
        List<Entry> entries = new ArrayList<>();
        for (GardenZone zone : zones) {
            if (zone == null) continue;
            List<GardenSeason> active = SeasonDisplayIdentity.activeSeasons(zone, seasons);
            if (active.isEmpty()) entries.add(new Entry(zone, null));
            else for (GardenSeason season : active) entries.add(new Entry(zone, season));
        }
        entries.sort(comparator(mode, nowEpoch, locale));
        return entries;
    }

    private static Comparator<Entry> comparator(int mode, long nowEpoch, Locale locale) {
        Collator collator = Collator.getInstance(locale);
        collator.setStrength(Collator.PRIMARY);
        Comparator<Entry> crop = (left, right) -> collator.compare(
                SeasonDisplayIdentity.name(left.season, left.zone),
                SeasonDisplayIdentity.name(right.season, right.zone));
        Comparator<Entry> area = Comparator.<Entry>comparingInt(entry -> areaOrder(entry.zone))
                .thenComparing((left, right) -> collator.compare(
                        PhysicalZoneIdentity.name(left.zone), PhysicalZoneIdentity.name(right.zone)))
                .thenComparing(entry -> safe(entry.zone.getZone_id()));
        Comparator<Entry> stable = area.thenComparing(crop)
                .thenComparing(entry -> safe(entry.season == null ? "" : entry.season.getSeason_id()));
        Comparator<Entry> moisture = Comparator.comparingInt(entry ->
                hasCurrentSensorData(entry.zone, nowEpoch) ? entry.zone.getMoisture() : Integer.MAX_VALUE);
        switch (normalizeSortMode(mode)) {
            case SORT_AREA:
                return stable;
            case SORT_NAME:
                return crop.thenComparing(stable);
            case SORT_MOISTURE:
                return moisture.thenComparing(stable);
            case SORT_UPDATED:
                return Comparator.<Entry>comparingLong(entry -> entry.zone.getUpdated_at_epoch())
                        .reversed().thenComparing(stable);
            case SORT_ATTENTION:
            case SORT_SMART:
            default:
                return Comparator.<Entry>comparingInt(entry -> smartPriority(entry.zone, nowEpoch))
                        .thenComparing(moisture).thenComparing(stable);
        }
    }

    private static int areaOrder(GardenZone zone) {
        int slot = PhysicalZoneIdentity.slot(zone);
        return slot > 0 ? slot : Integer.MAX_VALUE;
    }

    public static boolean hasCurrentSensorData(GardenZone zone, long nowEpoch) {
        if (zone == null || !zone.hasSensorData()) return false;
        return Math.max(0L, nowEpoch - zone.getUpdated_at_epoch()) <= CURRENT_SENSOR_SECONDS;
    }

    /** Preserve the existing order: critical, attention, due action, healthy, stale. */
    private static int smartPriority(GardenZone zone, long nowEpoch) {
        if (!hasCurrentSensorData(zone, nowEpoch)) return 4;
        ZoneIrrigationStatus irrigation = zone.getIrrigation_status();
        int criticalLimit = Math.max(5, zone.getMoisture_limit() - 20);
        if (zone.getMoisture() <= criticalLimit
                || (irrigation != null && !irrigation.isSensor_stable()
                && irrigation.getMoisture_deficit() > 15)) return 0;
        if (zone.getMoisture() < zone.getMoisture_limit()) return 1;
        FertilizationProfile profile = zone.getFertilization();
        if (profile != null && profile.isEnabled() && profile.isReminder_enabled()
                && profile.getNext_application_at_epoch() > 0L
                && profile.getNext_application_at_epoch() <= nowEpoch) return 2;
        if (irrigation != null && (irrigation.isWatering_active()
                || irrigation.isSelected_for_watering())) return 2;
        return 3;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Entry {
        public final GardenZone zone;
        public final GardenSeason season;

        private Entry(GardenZone zone, GardenSeason season) {
            this.zone = zone;
            this.season = season;
        }
    }
}
