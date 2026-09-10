package com.alidogukan.avora.season;

import com.alidogukan.avora.models.SeasonStatus;
import com.alidogukan.avora.models.GardenSeason;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.ZoneSeasonState;
import com.alidogukan.avora.zones.ZoneCapacityPolicy;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Pure season rules shared by screens, repositories and tests. */
public final class SeasonScope {
    public enum AutoStartedRepairAction {
        NONE,
        DELETE_EMPTY,
        CLOSE_WITH_ARCHIVE
    }

    private SeasonScope() { }

    public static String createSeasonId(String zoneId, long startedAtEpoch) {
        long safeEpoch = startedAtEpoch > 0L ? startedAtEpoch : System.currentTimeMillis() / 1000L;
        String year = new SimpleDateFormat("yyyy", Locale.US).format(new Date(safeEpoch * 1000L));
        return safe(zoneId).replaceAll("[^A-Za-z0-9_-]", "-") + "-" + year + "-" + safeEpoch;
    }

    public static boolean belongsTo(String recordSeasonId, long recordEpoch, ZoneSeasonState season) {
        if (season == null || season.getActive_season_id().isBlank()) return false;
        if (recordSeasonId != null && !recordSeasonId.isBlank()) {
            return season.getActive_season_id().equals(recordSeasonId);
        }
        if (!season.isInclude_legacy_records()) return false;
        if (recordEpoch <= 0L) return true;
        boolean afterStart = season.getStarted_at_epoch() <= 0L || recordEpoch >= season.getStarted_at_epoch();
        boolean beforeEnd = season.getEnded_at_epoch() <= 0L || recordEpoch <= season.getEnded_at_epoch();
        return afterStart && beforeEnd;
    }

    public static boolean canStart(ZoneSeasonState current) {
        return current == null || !SeasonStatus.isActive(current.getStatus());
    }

    public static boolean canClose(ZoneSeasonState current, boolean wateringActive) {
        return current != null && current.isActive() && !wateringActive;
    }

    /**
     * Plant work belongs to a season, not merely to a configured hardware zone.
     * Pre-season zones remain available to Zone and Season Management screens.
     */
    public static boolean hasActiveSeason(GardenZone zone) {
        return ZoneCapacityPolicy.isActive(zone)
                && zone.getSeason() != null
                && zone.getSeason().isActive();
    }

    /** Returns only operational zones whose current season is active. */
    public static List<GardenZone> activeSeasonZones(List<GardenZone> zones) {
        List<GardenZone> result = new ArrayList<>();
        if (zones == null) return result;
        for (GardenZone zone : zones) {
            if (hasActiveSeason(zone)) result.add(zone);
        }
        return result;
    }


    public static boolean isSeasonNotStarted(ZoneSeasonState current) {
        return current != null
                && SeasonStatus.isClosed(current.getStatus())
                && current.getActive_season_id().isBlank()
                && current.getStarted_at_epoch() <= 0L
                && current.getEnded_at_epoch() <= 0L;
    }

    public static boolean isModernAutoBootstrapCandidate(
            ZoneSeasonState current,
            long zoneCreatedAtEpoch
    ) {
        return current != null
                && current.isActive()
                && current.isInclude_legacy_records()
                && zoneCreatedAtEpoch > 0L;
    }

    public static AutoStartedRepairAction autoStartedRepairAction(
            boolean generatedLegacyManifest,
            boolean hasFieldRecords,
            boolean untouchedStage,
            boolean irrigationBusy
    ) {
        if (!generatedLegacyManifest || hasFieldRecords || !untouchedStage || irrigationBusy) {
            return AutoStartedRepairAction.NONE;
        }
        return AutoStartedRepairAction.DELETE_EMPTY;
    }

    public static boolean isCurrentActiveSeason(
            GardenSeason season,
            ZoneSeasonState current
    ) {
        return season != null
                && current != null
                && current.isActive()
                && SeasonStatus.isActive(season.getStatus())
                && current.isSeasonActive(season.getSeason_id());
    }

    public static boolean isRealCompletedArchive(GardenSeason season) {
        if (season == null
                || !SeasonStatus.isClosed(season.getStatus())
                || season.getEnded_at_epoch() <= 0L
                || season.getSeason_id().isBlank()) {
            return false;
        }
        if (!season.isIncludes_legacy_records()) return true;
        return hasRecordedActivity(season);
    }

    public static boolean hasRecordedActivity(GardenSeason season) {
        return season != null && (season.getWatering_count() > 0
                || season.getFertilizer_application_count() > 0
                || season.getManual_journal_event_count() > 0
                || season.getPhoto_count() > 0
                || season.getPlant_assistant_analysis_count() > 0
                || SeasonRecordPolicy.hasMeaningfulOutcomeValues(
                season.getHarvest_amount(),
                season.getYield_note(),
                season.getIssues_note(),
                season.getSuccessful_practices(),
                "", "", season.getNext_season_note()));
    }

    /**
     * Keeps an orphaned ACTIVE manifest from being shown as the current season.
     * An active archive entry is valid only while the zone points to the same id.
     */
    public static boolean isVisibleSeason(
            GardenSeason season,
            ZoneSeasonState current
    ) {
        if (isRealCompletedArchive(season)) return true;
        return isCurrentActiveSeason(season, current);
    }

    /**
     * A newly opened season may be deleted only before any field work starts.
     * Completed and legacy seasons
     * are deliberately excluded from this reversible operation.
     */
    public static boolean canCancelNewSeason(
            ZoneSeasonState current,
            String growthStage,
            boolean hasSeasonRecords,
            boolean irrigationBusy
    ) {
        return current != null
                && current.isActive()
                && !current.isInclude_legacy_records()
                && !hasSeasonRecords
                && !irrigationBusy;
    }

    /**
     * Allows cleanup of a modern season only while it has no field work or
     * meaningful outcome. Active seasons must still be the area's current
     * season and irrigation must be idle; closed empty test seasons can be
     * removed without changing the area's current season.
     */
    public static boolean canDeleteEmptySeason(
            GardenSeason season,
            boolean currentActiveSeason,
            boolean hasSeasonRecords,
            boolean irrigationBusy
    ) {
        if (season == null
                || safe(season.getSeason_id()).isBlank()
                || season.isIncludes_legacy_records()
                || hasSeasonRecords) {
            return false;
        }
        if (SeasonStatus.isClosed(season.getStatus())) return true;
        return SeasonStatus.isActive(season.getStatus())
                && currentActiveSeason
                && !irrigationBusy;
    }

    public static boolean isHarvestStage(String growthStage) {
        String value = safe(growthStage).trim().toUpperCase(Locale.ROOT);
        return "HARVEST".equals(value) || "ACTIVE_HARVEST".equals(value)
                || "HASAT".equals(value) || "AKTIF_HASAT".equals(value);
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
