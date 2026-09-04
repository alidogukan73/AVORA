package com.alidogukan.avora.health;

import static org.junit.Assert.*;
import static com.alidogukan.avora.health.GardenHealthIssue.Target.*;

import com.alidogukan.avora.models.FertilizationProfile;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.SeasonStatus;
import com.alidogukan.avora.models.ZoneIrrigationStatus;
import com.alidogukan.avora.models.ZoneSeasonState;
import com.alidogukan.avora.plantassistant.PlantAssistantHealthSignal;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class GardenHealthNavigationTest {
    private static final long NOW = 2_000_000L;

    @Test
    public void completedLowUrgencyObservationDoesNotReduceHealthScore() {
        GardenHealthZoneResult result = evaluate(zone(), signal("Düşük"));
        assertEquals(100, result.getScore());
        assertTrue(result.getIssues().isEmpty());
    }

    @Test
    public void routineAdvisorObservationKeepsHealthyAreaAt100() {
        com.alidogukan.avora.plantassistant.PlantAssistantResult observation =
                com.alidogukan.avora.plantassistant.PlantAssistantAdvisor.assess(
                        zone(), java.util.Collections.emptyList(), "", null, false, false);
        assertEquals("Gözlem kaydı oluşturuldu", observation.getTitle());
        GardenHealthZoneResult result = evaluate(zone(), new PlantAssistantHealthSignal(
                "zone-001", "season-001", observation.getUrgency(), observation.getTitle(), NOW));
        assertEquals(100, result.getScore());
        assertTrue(result.getIssues().isEmpty());
    }

    @Test
    public void previouslySavedLowUrgencyResultNeedsNoNewAnalysisToClearPenalty() {
        PlantAssistantHealthSignal previous = new PlantAssistantHealthSignal(
                "zone-001", "Düşük", "Gözlem", NOW - 86400L);
        assertEquals(100, evaluate(zone(), previous).getScore());
    }

    @Test
    public void newLowUrgencyResultReplacesPreviousRiskForSameArea() {
        assertEquals(75, evaluate(zone(), signal("Yüksek")).getScore());
        assertEquals(100, evaluate(zone(), signal("Düşük")).getScore());
    }

    @Test
    public void routineObservationDoesNotMaskIndependentMoistureWarning() {
        GardenZone zone = zone();
        zone.setMoisture(30);
        GardenHealthZoneResult result = evaluate(zone, signal("Düşük"));
        assertEquals(80, result.getScore());
        assertSingleIssue(result, IRRIGATION_SETTINGS, 20);
    }

    @Test
    public void gardenSummaryAlsoKeepsCompletedObservationsAt100() {
        GardenHealthSummary summary = GardenHealthCalculator.calculate(
                Arrays.asList(zone()), NOW, signal("Düşük"));
        assertEquals(100, summary.getScore());
        assertFalse(summary.getDetail().contains("Bitki Asistanı"));
    }

    @Test
    public void mediumAndHighUrgencyKeepTheirExistingDeductions() {
        GardenHealthZoneResult medium = evaluate(zone(), signal("Orta"));
        assertEquals(88, medium.getScore());
        assertSingleIssue(medium, PLANT_ASSISTANT, 12);
        GardenHealthZoneResult high = evaluate(zone(), signal("Yüksek"));
        assertEquals(75, high.getScore());
        assertSingleIssue(high, PLANT_ASSISTANT, 25);
    }

    @Test
    public void normalAreaHasNoIssueAndKeeps100() {
        GardenHealthZoneResult result = evaluate(zone(), null);
        assertEquals(100, result.getScore());
        assertTrue(result.getIssues().isEmpty());
    }

    @Test
    public void disabledSensorRoutesToItsSettingsWithoutChangingIt() {
        GardenZone zone = zone();
        zone.setSensor_enabled(false);
        GardenHealthZoneResult result = evaluate(zone, null);
        assertEquals(55, result.getScore());
        assertSingleIssue(result, SENSOR_SETTINGS, 45);
        assertFalse(zone.isSensor_enabled());
    }

    @Test
    public void missingSensorDataRoutesToSensorSettings() {
        GardenZone zone = zone();
        zone.setUpdated_at_epoch(0);
        GardenHealthZoneResult result = evaluate(zone, null);
        assertEquals(55, result.getScore());
        assertSingleIssue(result, SENSOR_SETTINGS, 45);
    }

    @Test
    public void staleSensorRoutesToSensorSettingsAtTheExistingThreshold() {
        GardenZone zone = zone();
        zone.setUpdated_at_epoch(NOW - 900);
        assertEquals(100, evaluate(zone, null).getScore());
        zone.setUpdated_at_epoch(NOW - 901);
        GardenHealthZoneResult result = evaluate(zone, null);
        assertEquals(70, result.getScore());
        assertSingleIssue(result, SENSOR_SETTINGS, 30);
    }

    @Test
    public void lowMoistureRoutesToIrrigationWithoutStartingIt() {
        GardenZone zone = zone();
        zone.setMoisture(30);
        zone.setIrrigation_enabled(false);
        GardenHealthZoneResult result = evaluate(zone, null);
        assertEquals(80, result.getScore());
        assertSingleIssue(result, IRRIGATION_SETTINGS, 20);
        assertFalse(zone.isIrrigation_enabled());
        assertEquals(40, zone.getMoisture_limit());
    }

    @Test
    public void unstableMeasurementRoutesToSensorSettings() {
        GardenZone zone = zone();
        ZoneIrrigationStatus irrigation = new ZoneIrrigationStatus();
        irrigation.setSensor_stable(false);
        zone.setIrrigation_status(irrigation);
        assertTrue(irrigation.hasSensor_stable());
        GardenHealthZoneResult result = evaluate(zone, null);
        assertEquals(80, result.getScore());
        assertSingleIssue(result, SENSOR_SETTINGS, 20);
    }

    @Test
    public void missingLegacyStabilityFieldDoesNotCreateFalseSensorWarning() {
        GardenZone zone = zone();
        ZoneIrrigationStatus irrigation = new ZoneIrrigationStatus();
        zone.setIrrigation_status(irrigation);
        assertFalse(irrigation.hasSensor_stable());
        assertEquals(100, evaluate(zone, null).getScore());
    }

    @Test
    public void dueFertilizationRoutesToFertilizationOnly() {
        GardenZone zone = zone();
        addDueFertilization(zone);
        GardenHealthZoneResult result = evaluate(zone, null);
        assertEquals(90, result.getScore());
        assertSingleIssue(result, FERTILIZATION, 10);
    }

    @Test
    public void sensorProblemDoesNotHideIndependentActions() {
        GardenZone zone = zone();
        zone.setSensor_enabled(false);
        addDueFertilization(zone);
        GardenHealthZoneResult result = evaluate(zone, signal("Orta"));
        assertEquals(33, result.getScore());
        assertEquals(Arrays.asList(SENSOR_SETTINGS, FERTILIZATION, PLANT_ASSISTANT),
                result.getIssues().stream().map(GardenHealthIssue::getTarget)
                        .collect(Collectors.toList()));
    }

    @Test
    public void healthScopeExcludesArchivedAndClosedSeasonZones() {
        GardenZone active = zone();
        GardenZone archived = zone();
        archived.setZone_id("zone-002");
        archived.setEnabled(false);
        GardenZone closed = zone();
        closed.setZone_id("zone-003");
        closed.getSeason().setStatus(SeasonStatus.CLOSED);

        List<GardenZone> all = Arrays.asList(active, archived, closed);
        assertEquals(1, GardenHealthCalculator.activeHealthZones(all).size());
        GardenHealthSummary summary = GardenHealthCalculator.calculate(all, NOW, null);
        assertEquals(100, summary.getScore());
        assertTrue(summary.getDetail().startsWith("1 aktif bölgenin"));
    }

    @Test
    public void summaryHighlightsTheLowestScoringAreaNotTheFirstWarning() {
        GardenZone firstWarning = zone();
        firstWarning.setUpdated_at_epoch(NOW - 901);
        GardenZone worst = zone();
        worst.setZone_id("zone-002");
        worst.setMoisture(0);

        GardenHealthSummary summary = GardenHealthCalculator.calculate(
                Arrays.asList(firstWarning, worst), NOW, null);
        assertEquals(68, summary.getScore());
        assertTrue(summary.getDetail().startsWith("2. Bölge · Nem düşük"));
    }

    @Test
    public void simultaneousProblemsKeepAllDestinationsForTheChoiceDialog() {
        GardenZone zone = zone();
        zone.setUpdated_at_epoch(NOW - 901);
        zone.setMoisture(30);
        addDueFertilization(zone);
        GardenHealthZoneResult result = evaluate(zone, signal("Orta"));
        assertEquals(28, result.getScore());
        List<GardenHealthIssue.Target> targets = result.getIssues().stream()
                .map(GardenHealthIssue::getTarget).collect(Collectors.toList());
        assertEquals(Arrays.asList(SENSOR_SETTINGS, IRRIGATION_SETTINGS, FERTILIZATION, PLANT_ASSISTANT),
                targets);
    }

    @Test
    public void anotherAreasRecommendationDoesNotAffectThisArea() {
        PlantAssistantHealthSignal signal = new PlantAssistantHealthSignal(
                "zone-002", "Yüksek", "Gözlem", NOW);
        assertEquals(100, evaluate(zone(), signal).getScore());
    }

    @Test
    public void expiredFutureAndEmptyRecommendationsDoNotCreateAssistantLinks() {
        for (PlantAssistantHealthSignal signal : Arrays.asList(
                new PlantAssistantHealthSignal("zone-001", "Yüksek", "Eski", NOW - 14L * 86400L - 1L),
                new PlantAssistantHealthSignal("zone-001", "Yüksek", "Gelecek", NOW + 1),
                signal(""))) {
            assertTrue(evaluate(zone(), signal).getIssues().isEmpty());
        }
    }

    @Test
    public void assistantDestinationCarriesTheExactActiveCropSeason() {
        GardenZone zone = zone();
        ZoneSeasonState state = new ZoneSeasonState();
        state.setStatus(SeasonStatus.ACTIVE);
        state.setActive_season_id("tomato");
        state.getActive_season_ids().put("tomato", true);
        state.getActive_season_ids().put("pepper", true);
        zone.setSeason(state);
        PlantAssistantHealthSignal signal = new PlantAssistantHealthSignal(
                "zone-001", "pepper", "Orta", "Biber gözlemi", NOW);
        GardenHealthZoneResult result = evaluate(zone, signal);
        assertEquals(88, result.getScore());
        assertEquals("pepper", result.getIssues().get(0).getSeasonId());
        state.getActive_season_ids().put("pepper", false);
        assertEquals(100, evaluate(zone, signal).getScore());
    }

    @Test
    public void recommendationFromClosedSeasonDoesNotPointAtNewCrop() {
        GardenZone zone = zone();
        ZoneSeasonState state = new ZoneSeasonState();
        state.setStatus(SeasonStatus.ACTIVE);
        state.setActive_season_id("new-season");
        zone.setSeason(state);
        PlantAssistantHealthSignal signal = new PlantAssistantHealthSignal(
                "zone-001", "old-season", "Yüksek", "Eski bitki", NOW);
        assertTrue(evaluate(zone, signal).getIssues().isEmpty());
    }

    @Test
    public void extremeCombinedPenaltiesRemainClampedAtZero() {
        GardenZone zone = zone();
        zone.setMoisture(0);
        zone.setUpdated_at_epoch(NOW - 901);
        zone.setIrrigation_status(new ZoneIrrigationStatus());
        addDueFertilization(zone);
        assertEquals(0, evaluate(zone, signal("Yüksek")).getScore());
    }

    @Test
    public void displayedIssueSnapshotCannotBeModifiedAfterRendering() {
        GardenHealthZoneResult result = evaluate(zone(), signal("Orta"));
        assertThrows(UnsupportedOperationException.class, () -> result.getIssues().clear());
        assertEquals(0, evaluate(null, signal("Düşük")).getScore());
    }

    private static GardenHealthZoneResult evaluate(GardenZone zone, PlantAssistantHealthSignal signal) {
        return GardenHealthCalculator.evaluateZone(zone, NOW, signal);
    }

    private static GardenZone zone() {
        GardenZone zone = new GardenZone();
        zone.setZone_id("zone-001");
        zone.setUpdated_at_epoch(NOW);
        zone.setMoisture(70);
        zone.setMoisture_limit(40);
        ZoneSeasonState season = new ZoneSeasonState();
        season.setStatus(SeasonStatus.ACTIVE);
        season.setActive_season_id("season-001");
        zone.setSeason(season);
        return zone;
    }

    private static PlantAssistantHealthSignal signal(String urgency) {
        return new PlantAssistantHealthSignal("zone-001", urgency, "Gözlem", NOW);
    }

    private static void addDueFertilization(GardenZone zone) {
        FertilizationProfile profile = new FertilizationProfile();
        profile.setEnabled(true);
        profile.setNext_application_at_epoch(NOW);
        zone.setFertilization(profile);
    }

    private static void assertSingleIssue(GardenHealthZoneResult result,
                                         GardenHealthIssue.Target target, int deduction) {
        assertEquals(1, result.getIssues().size());
        assertEquals(target, result.getIssues().get(0).getTarget());
        assertEquals(deduction, result.getIssues().get(0).getDeduction());
    }
}
