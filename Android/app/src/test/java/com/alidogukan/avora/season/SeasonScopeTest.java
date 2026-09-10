package com.alidogukan.avora.season;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.alidogukan.avora.models.GardenSeason;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.SeasonStatus;
import com.alidogukan.avora.models.ZoneSeasonState;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class SeasonScopeTest {

    @Test
    public void taggedRecordBelongsOnlyToItsSeason() {
        ZoneSeasonState active = season("zone-001-2026-100", true, 100L, 0L);

        assertTrue(SeasonScope.belongsTo("zone-001-2026-100", 150L, active));
        assertFalse(SeasonScope.belongsTo("zone-001-2025-10", 150L, active));
    }

    @Test
    public void legacyRecordIsAcceptedOnlyByLegacySeasonAndDateWindow() {
        ZoneSeasonState legacy = season("zone-001-2026-100", true, 100L, 200L);
        ZoneSeasonState fresh = season("zone-001-2027-300", false, 300L, 0L);

        assertTrue(SeasonScope.belongsTo("", 150L, legacy));
        assertFalse(SeasonScope.belongsTo("", 99L, legacy));
        assertFalse(SeasonScope.belongsTo("", 201L, legacy));
        assertFalse(SeasonScope.belongsTo("", 350L, fresh));
    }

    @Test
    public void seasonCannotCloseWhileWateringIsActive() {
        ZoneSeasonState active = season("zone-001-2026-100", false, 100L, 0L);

        assertTrue(SeasonScope.canClose(active, false));
        assertFalse(SeasonScope.canClose(active, true));
        assertFalse(SeasonScope.canStart(active));
    }

    @Test
    public void closedSeasonAllowsNewSeasonWithoutChangingHarvestSemantics() {
        ZoneSeasonState closed = season("zone-001-2026-100", false, 100L, 200L);
        closed.setStatus(SeasonStatus.CLOSED);

        assertTrue(SeasonScope.canStart(closed));
        assertTrue(SeasonScope.isHarvestStage("ACTIVE_HARVEST"));
        assertTrue(SeasonScope.isHarvestStage("hasat"));
    }

    @Test
    public void untouchedSoilPreparationSeasonCanBeCancelled() {
        ZoneSeasonState active = season("zone-001-2026-100", false, 100L, 0L);

        assertTrue(SeasonScope.canCancelNewSeason(
                active,
                "SOIL_PREPARATION",
                false,
                false
        ));
    }

    @Test
    public void seasonDeletionDependsOnRecordedWorkRatherThanSelectedStage() {
        ZoneSeasonState active = season("zone-001-2026-100", false, 100L, 0L);
        ZoneSeasonState legacy = season("zone-001-2026-90", true, 90L, 0L);
        ZoneSeasonState closed = season("zone-001-2026-80", false, 80L, 120L);
        closed.setStatus(SeasonStatus.CLOSED);

        assertTrue(SeasonScope.canCancelNewSeason(active, "SEEDLING", false, false));
        assertFalse(SeasonScope.canCancelNewSeason(active, "SOIL_PREPARATION", true, false));
        assertFalse(SeasonScope.canCancelNewSeason(active, "SOIL_PREPARATION", false, true));
        assertFalse(SeasonScope.canCancelNewSeason(legacy, "SOIL_PREPARATION", false, false));
        assertFalse(SeasonScope.canCancelNewSeason(closed, "SOIL_PREPARATION", false, false));
    }

    @Test
    public void emptySeasonCleanupProtectsRecordsAndLegacyArchives() {
        GardenSeason active = activeSeason("active-empty");
        GardenSeason closed = completedSeason(false);
        GardenSeason legacy = completedSeason(true);

        assertTrue(SeasonScope.canDeleteEmptySeason(active, true, false, false));
        assertFalse(SeasonScope.canDeleteEmptySeason(active, false, false, false));
        assertFalse(SeasonScope.canDeleteEmptySeason(active, true, false, true));
        assertTrue(SeasonScope.canDeleteEmptySeason(closed, false, false, true));
        assertFalse(SeasonScope.canDeleteEmptySeason(closed, false, true, false));
        assertFalse(SeasonScope.canDeleteEmptySeason(legacy, false, false, false));
    }

    @Test
    public void closedEmptyStateMeansSeasonHasNotStarted() {
        ZoneSeasonState waiting = new ZoneSeasonState();
        waiting.setStatus(SeasonStatus.CLOSED);

        assertTrue(SeasonScope.isSeasonNotStarted(waiting));

        waiting.setEnded_at_epoch(100L);
        assertFalse(SeasonScope.isSeasonNotStarted(waiting));
    }

    @Test
    public void plantWorkRequiresAnActiveZoneSeason() {
        GardenZone active = zone("zone-001", true, season("season-1", false, 100L, 0L));
        GardenZone notStarted = zone("zone-002", true, null);
        GardenZone blankSeasonId = zone("zone-003", true, season("", false, 100L, 0L));
        GardenZone disabled = zone("zone-004", false, season("season-4", false, 100L, 0L));

        assertTrue(SeasonScope.hasActiveSeason(active));
        assertFalse(SeasonScope.hasActiveSeason(notStarted));
        assertFalse(SeasonScope.hasActiveSeason(blankSeasonId));
        assertFalse(SeasonScope.hasActiveSeason(disabled));
    }

    @Test
    public void activeSeasonZonesExcludePreSeasonZonesAndPreserveOrder() {
        GardenZone first = zone("zone-001", true, season("season-1", false, 100L, 0L));
        GardenZone waiting = zone("zone-002", true, null);
        GardenZone second = zone("zone-003", true, season("season-3", false, 300L, 0L));

        List<GardenZone> result = SeasonScope.activeSeasonZones(
                Arrays.asList(first, waiting, second));

        assertEquals(Arrays.asList(first, second), result);
        assertTrue(SeasonScope.activeSeasonZones(null).isEmpty());
    }

    @Test
    public void onlyModernLegacyBootstrapIsRepairCandidate() {
        ZoneSeasonState automatic = season("zone-006-2026-100", true, 100L, 0L);
        ZoneSeasonState userStarted = season("zone-006-2026-101", false, 101L, 0L);

        assertTrue(SeasonScope.isModernAutoBootstrapCandidate(automatic, 90L));
        assertFalse(SeasonScope.isModernAutoBootstrapCandidate(automatic, 0L));
        assertFalse(SeasonScope.isModernAutoBootstrapCandidate(userStarted, 90L));

        automatic.setStatus(SeasonStatus.CLOSED);
        assertFalse(SeasonScope.isModernAutoBootstrapCandidate(automatic, 90L));
    }

    @Test
    public void automaticLegacyRepairTouchesOnlyEmptyUntouchedCandidates() {
        assertEquals(
                SeasonScope.AutoStartedRepairAction.DELETE_EMPTY,
                SeasonScope.autoStartedRepairAction(true, false, true, false));
        assertEquals(
                SeasonScope.AutoStartedRepairAction.NONE,
                SeasonScope.autoStartedRepairAction(true, true, true, false));
        assertEquals(
                SeasonScope.AutoStartedRepairAction.NONE,
                SeasonScope.autoStartedRepairAction(true, false, false, false));
        assertEquals(
                SeasonScope.AutoStartedRepairAction.NONE,
                SeasonScope.autoStartedRepairAction(false, false, true, false));
        assertEquals(
                SeasonScope.AutoStartedRepairAction.NONE,
                SeasonScope.autoStartedRepairAction(true, false, true, true));
    }

    @Test
    public void emptyAutomaticLegacyClosureIsNotARealArchive() {
        GardenSeason automatic = completedSeason(true);

        assertFalse(SeasonScope.isRealCompletedArchive(automatic));

        automatic.setWatering_count(1);
        assertTrue(SeasonScope.isRealCompletedArchive(automatic));
    }

    @Test
    public void userStartedCompletedSeasonRemainsAnArchive() {
        GardenSeason userStarted = completedSeason(false);

        assertTrue(SeasonScope.isRealCompletedArchive(userStarted));
    }

    @Test
    public void completedTestSeasonWithoutFieldRecordsHasNoRecordedActivity() {
        GardenSeason testSeason = completedSeason(false);

        assertFalse(SeasonScope.hasRecordedActivity(testSeason));
        testSeason.setJournal_event_count(1);
        assertFalse(SeasonScope.hasRecordedActivity(testSeason));


        testSeason.setManual_journal_event_count(1);
        assertTrue(SeasonScope.hasRecordedActivity(testSeason));
    }
    @Test
    public void onlyTheManifestReferencedByZoneCanBeCurrent() {
        ZoneSeasonState active = season("zone-006-2026-200", false, 200L, 0L);
        GardenSeason current = activeSeason("zone-006-2026-200");
        GardenSeason orphan = activeSeason("zone-006-2026-100");

        assertTrue(SeasonScope.isCurrentActiveSeason(current, active));
        assertFalse(SeasonScope.isCurrentActiveSeason(orphan, active));
        assertTrue(SeasonScope.isVisibleSeason(current, active));
        assertFalse(SeasonScope.isVisibleSeason(orphan, active));

        active.setStatus(SeasonStatus.CLOSED);
        assertFalse(SeasonScope.isVisibleSeason(current, active));
    }

    @Test
    public void realCompletedArchiveRemainsVisibleWithoutAnActiveZoneSeason() {
        assertTrue(SeasonScope.isVisibleSeason(completedSeason(false), null));
    }

    private static GardenSeason activeSeason(String id) {
        GardenSeason value = new GardenSeason();
        value.setSeason_id(id);
        value.setStatus(SeasonStatus.ACTIVE);
        return value;
    }
    private static GardenSeason completedSeason(boolean includesLegacy) {
        GardenSeason value = new GardenSeason();
        value.setSeason_id("zone-006-2026-100");
        value.setStatus(SeasonStatus.CLOSED);
        value.setEnded_at_epoch(200L);
        value.setIncludes_legacy_records(includesLegacy);
        return value;
    }

    private static ZoneSeasonState season(
            String id,
            boolean includesLegacy,
            long startedAt,
            long endedAt
    ) {
        ZoneSeasonState value = new ZoneSeasonState();
        value.setActive_season_id(id);
        value.setStatus(SeasonStatus.ACTIVE);
        value.setInclude_legacy_records(includesLegacy);
        value.setStarted_at_epoch(startedAt);
        value.setEnded_at_epoch(endedAt);
        return value;
    }

    private static GardenZone zone(String id, boolean enabled, ZoneSeasonState season) {
        GardenZone value = new GardenZone();
        value.setZone_id(id);
        value.setEnabled(enabled);
        value.setSeason(season);
        return value;
    }
}
