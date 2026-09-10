package com.alidogukan.avora.season;

import com.alidogukan.avora.models.GardenSeason;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.SeasonStatus;
import com.alidogukan.avora.models.ZoneSeasonState;
import com.alidogukan.avora.zones.PhysicalZoneIdentity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Resolves a season's immutable display identity without borrowing a later crop. */
public final class SeasonDisplayIdentity {
    private static final String DEFAULT_EMOJI = "🌱";

    private SeasonDisplayIdentity() { }

    public static String name(GardenSeason season, GardenZone currentZone) {
        if (season != null) {
            String archivedName = safe(season.getZone_name());
            if (!archivedName.isBlank()) return archivedName;
            String archivedPlantType = safe(season.getPlant_type());
            if (!archivedPlantType.isBlank()) return archivedPlantType;
        }
        String currentName = safe(currentZone == null ? "" : currentZone.getName());
        return currentName;
    }

    public static String emoji(GardenSeason season, GardenZone currentZone) {
        if (season == null) {
            String currentEmoji = safe(currentZone == null ? "" : currentZone.getEmoji());
            return currentEmoji.isBlank() ? DEFAULT_EMOJI : currentEmoji;
        }

        String archivedEmoji = safe(season.getEmoji());
        if (!archivedEmoji.isBlank()) return archivedEmoji;

        String archivedIdentity = firstNonBlank(
                season.getZone_name(),
                season.getPlant_type(),
                season.getLabel()
        );
        return SeasonStartConfiguration.suggestedCropEmoji(archivedIdentity);
    }

    /** Physical-area context used to distinguish identical crops. */
    public static String areaName(GardenSeason season, GardenZone currentZone) {
        if (currentZone != null) return PhysicalZoneIdentity.name(currentZone);
        String archivedArea = safe(season == null ? "" : season.getArea_name());
        return archivedArea.isBlank() ? PhysicalZoneIdentity.name(null) : archivedArea;
    }

    /** Crop and physical area without an icon, suitable for compact card titles. */
    public static String cropAreaName(GardenSeason season, GardenZone currentZone) {
        String crop = safe(name(season, currentZone));
        String area = safe(areaName(season, currentZone));
        if (crop.isBlank()) return area;
        if (area.isBlank()) return crop;
        return crop + " · " + area;
    }

    /** Physical area first, followed by the crop, for the plant journal list. */
    public static String areaCropName(GardenSeason season, GardenZone currentZone) {
        String crop = safe(name(season, currentZone));
        String area = safe(areaName(season, currentZone));
        if (crop.isBlank()) return area;
        if (area.isBlank()) return crop;
        return area + " · " + crop;
    }

    /** Crop and physical area with the crop icon, suitable for selectors. */
    public static String cropAreaLabel(GardenSeason season, GardenZone currentZone) {
        String icon = safe(emoji(season, currentZone));
        String label = cropAreaName(season, currentZone);
        return icon.isBlank() ? label : icon + " " + label;
    }

    /** Crop on the first line and physical area on the second, suitable for narrow selectors. */
    public static String stackedCropAreaLabel(GardenSeason season, GardenZone currentZone) {
        String crop = safe(name(season, currentZone));
        String area = safe(areaName(season, currentZone));
        String icon = safe(emoji(season, currentZone));
        String cropLine = icon.isBlank() ? crop : icon + " " + crop;
        if (cropLine.isBlank()) return area;
        if (area.isBlank()) return cropLine;
        return cropLine + "\n" + area;
    }

    /** Returns the currently active crop seasons for one physical growing area. */
    public static List<GardenSeason> activeSeasons(
            GardenZone zone,
            List<GardenSeason> allSeasons
    ) {
        List<GardenSeason> result = new ArrayList<>();
        if (zone == null || allSeasons == null) return result;
        ZoneSeasonState current = zone.getSeason();
        if (current == null || !current.isActive()) return result;
        for (GardenSeason season : allSeasons) {
            if (season == null
                    || !SeasonStatus.isActive(season.getStatus())
                    || !current.isSeasonActive(season.getSeason_id())
                    || !ZoneAreaIdentity.belongsToCurrentOrArea(zone, season)) {
                continue;
            }
            result.add(season);
        }
        String primary = safe(current.getActive_season_id());
        result.sort((left, right) -> {
            boolean leftPrimary = primary.equals(safe(left.getSeason_id()));
            boolean rightPrimary = primary.equals(safe(right.getSeason_id()));
            if (leftPrimary != rightPrimary) return leftPrimary ? -1 : 1;
            int started = Long.compare(
                    left.getStarted_at_epoch(), right.getStarted_at_epoch());
            if (started != 0) return started;
            return name(left, zone).compareToIgnoreCase(name(right, zone));
        });
        return result;
    }

    /** Active crop names shown without duplicating their shared physical area. */
    public static String activeCropNames(
            GardenZone zone,
            List<GardenSeason> allSeasons
    ) {
        List<String> names = new ArrayList<>();
        for (GardenSeason season : activeSeasons(zone, allSeasons)) {
            addUnique(names, name(season, zone));
        }
        return String.join(" + ", names);
    }

    /**
     * Compact, newest-first crop list for a season archive card. Archived
     * identities are used deliberately so a later crop in the area cannot
     * rename an older season.
     */
    public static String archiveCropSummary(
            List<GardenSeason> archivedSeasons,
            int maxVisible
    ) {
        if (archivedSeasons == null || archivedSeasons.isEmpty()) return "";
        int limit = Math.max(1, maxVisible);
        List<GardenSeason> newestFirst = new ArrayList<>();
        for (GardenSeason season : archivedSeasons) {
            if (season != null) newestFirst.add(season);
        }
        newestFirst.sort((left, right) -> {
            int ended = Long.compare(
                    right.getEnded_at_epoch(), left.getEnded_at_epoch());
            if (ended != 0) return ended;
            int started = Long.compare(
                    right.getStarted_at_epoch(), left.getStarted_at_epoch());
            if (started != 0) return started;
            return safe(name(left, null)).compareToIgnoreCase(
                    safe(name(right, null)));
        });

        Set<String> uniqueNames = new LinkedHashSet<>();
        List<String> labels = new ArrayList<>();
        for (GardenSeason season : newestFirst) {
            String crop = safe(name(season, null));
            if (crop.isBlank()
                    || !uniqueNames.add(crop.toLowerCase(Locale.ROOT))) {
                continue;
            }
            String icon = safe(emoji(season, null));
            labels.add(icon.isBlank() ? crop : icon + " " + crop);
        }
        if (labels.isEmpty()) return "";

        int visible = Math.min(limit, labels.size());
        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < visible; i++) {
            if (i > 0) summary.append(", ");
            summary.append(labels.get(i));
        }
        int hidden = labels.size() - visible;
        if (hidden > 0) summary.append(" +").append(hidden);
        return summary.toString();
    }

    /** Stable physical-area name; it never changes when its crops change. */
    public static String operationalName(
            GardenZone zone,
            List<GardenSeason> allSeasons
    ) {
        return PhysicalZoneIdentity.name(zone);
    }

    /** Compact crop icon used next to a shared physical area. */
    public static String operationalEmoji(
            GardenZone zone,
            List<GardenSeason> allSeasons
    ) {
        Set<String> emojis = new LinkedHashSet<>();
        for (GardenSeason season : activeSeasons(zone, allSeasons)) {
            emojis.add(emoji(season, zone));
        }
        if (emojis.isEmpty()) emojis.add(emoji(null, zone));
        return String.join("", emojis);
    }

    public static String physicalIcon(GardenZone zone) {
        return PhysicalZoneIdentity.icon(zone);
    }

    /** Full label for selectors where the crop icons and names share one text field. */
    public static String operationalLabel(
            GardenZone zone,
            List<GardenSeason> allSeasons
    ) {
        String physicalName = operationalName(zone, allSeasons);
        String cropNames = activeCropNames(zone, allSeasons);
        if (cropNames.isBlank()) return physicalIcon(zone) + " " + physicalName;
        return physicalIcon(zone) + " " + physicalName + " · " + operationalEmoji(zone, allSeasons)
                + " " + cropNames;
    }

    private static void addUnique(List<String> values, String value) {
        String clean = safe(value);
        if (clean.isBlank()) return;
        for (String existing : values) {
            if (existing.equalsIgnoreCase(clean)) return;
        }
        values.add(clean);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            String clean = safe(value);
            if (!clean.isBlank()) return clean;
        }
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
