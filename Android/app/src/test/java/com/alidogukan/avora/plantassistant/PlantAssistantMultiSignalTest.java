package com.alidogukan.avora.plantassistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.alidogukan.avora.health.GardenHealthCalculator;
import com.alidogukan.avora.health.GardenHealthSummary;
import com.alidogukan.avora.health.GardenHealthZoneResult;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.SeasonStatus;
import com.alidogukan.avora.models.WeatherForecast;
import com.alidogukan.avora.models.ZoneSeasonState;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public final class PlantAssistantMultiSignalTest {
    private static final long NOW = 2_000_000_000L;

    @Test
    public void highFindingInOneZoneCannotBeHiddenByNewerLowFindingElsewhere() {
        GardenZone first = zone("zone-001", "season-1", "Alan A");
        GardenZone second = zone("zone-002", "season-2", "Alan B");
        PlantAssistantHealthSignal high = new PlantAssistantHealthSignal(
                "zone-001", "season-1", "Yüksek", "Yakın takip", NOW - 30L);
        PlantAssistantHealthSignal newerLow = new PlantAssistantHealthSignal(
                "zone-002", "season-2", "Düşük", "Normal gözlem", NOW);

        GardenHealthSummary health = GardenHealthCalculator.calculateWithSignals(
                Arrays.asList(first, second), NOW, Arrays.asList(high, newerLow));
        PlantAssistantHomeRecommendation.Recommendation home =
                PlantAssistantHomeRecommendation.evaluateWithSignals(
                        Arrays.asList(first, second), null,
                        Arrays.asList(high, newerLow), NOW);

        assertEquals(84, health.getScore());
        assertEquals("Bahçede uyarı var", health.getTitle());
        assertEquals(PlantAssistantHomeRecommendation.Level.WARNING, home.getLevel());
        assertTrue(home.getMessage().contains("Alan A"));
    }

    @Test
    public void strongestActiveSeasonSignalKeepsItsSeasonDestination() {
        GardenZone zone = zone("zone-001", "tomato", "Sera 1");
        zone.getSeason().getActive_season_ids().put("tomato", true);
        zone.getSeason().getActive_season_ids().put("pepper", true);
        PlantAssistantHealthSignal tomato = new PlantAssistantHealthSignal(
                "zone-001", "tomato", "Orta", "Domates", NOW);
        PlantAssistantHealthSignal pepper = new PlantAssistantHealthSignal(
                "zone-001", "pepper", "Yüksek", "Biber", NOW - 10L);

        GardenHealthZoneResult result =
                GardenHealthCalculator.evaluateZoneWithSignals(
                        zone, NOW, Arrays.asList(tomato, pepper));

        assertEquals(75, result.getScore());
        assertEquals("pepper", result.getIssues().get(0).getSeasonId());
    }

    @Test
    public void legacyUnscopedFindingDoesNotCrossIntoLaterSeason() {
        GardenZone zone = zone("zone-001", "new-season", "Sera 1");
        zone.getSeason().setStarted_at_epoch(NOW - 100L);
        PlantAssistantHealthSignal oldLegacy = new PlantAssistantHealthSignal(
                "zone-001", "", "Yüksek", "Eski ürün", NOW - 101L);
        PlantAssistantHealthSignal currentLegacy = new PlantAssistantHealthSignal(
                "zone-001", "", "Orta", "Güncel ürün", NOW - 50L);

        assertTrue(!oldLegacy.appliesTo(zone, NOW));
        assertTrue(currentLegacy.appliesTo(zone, NOW));
    }

    @Test
    public void inactiveOrExpiredSignalsAreIgnored() {
        GardenZone zone = zone("zone-001", "season-1", "Sera 1");
        PlantAssistantHealthSignal expired = new PlantAssistantHealthSignal(
                "zone-001", "season-1", "Yüksek", "Eski", NOW - 14L * 86400L - 1L);
        PlantAssistantHealthSignal closed = new PlantAssistantHealthSignal(
                "zone-001", "closed-season", "Yüksek", "Kapalı", NOW);

        GardenHealthZoneResult result =
                GardenHealthCalculator.evaluateZoneWithSignals(
                        zone, NOW, Arrays.asList(expired, closed));
        assertEquals(100, result.getScore());
        assertTrue(result.getIssues().isEmpty());
        assertEquals(PlantAssistantHomeRecommendation.Level.NORMAL,
                PlantAssistantHomeRecommendation.evaluateWithSignals(
                        Collections.singletonList(zone), null,
                        Arrays.asList(expired, closed), NOW).getLevel());
    }

    @Test
    public void criticalUrgencyIsConsistentAcrossHomeAndHealth() {
        GardenZone zone = zone("zone-001", "season-1", "Sera 1");
        PlantAssistantHealthSignal critical = new PlantAssistantHealthSignal(
                "zone-001", "season-1", "Critical", "Acil kontrol", NOW);

        assertEquals(75, GardenHealthCalculator.evaluateZoneWithSignals(
                zone, NOW, Collections.singletonList(critical)).getScore());
        assertEquals(PlantAssistantHomeRecommendation.Level.WARNING,
                PlantAssistantHomeRecommendation.evaluateWithSignals(
                        Collections.singletonList(zone), null,
                        Collections.singletonList(critical), NOW).getLevel());
    }

    @Test
    public void legacySignalRequiresAnActiveSeason() {
        GardenZone zone = zone("zone-001", "season-1", "Sera 1");
        zone.getSeason().setStatus(SeasonStatus.CLOSED);
        PlantAssistantHealthSignal legacy = new PlantAssistantHealthSignal(
                "zone-001", "", "Yüksek", "Eski", NOW);

        assertTrue(!legacy.appliesTo(zone, NOW));
    }

    @Test
    public void staleHotWeatherDoesNotCreateAHeatWarning() {
        GardenZone zone = zone("zone-001", "season-1", "Sera 1");
        zone.setMoisture(35);
        WeatherForecast staleWeather = new WeatherForecast(
                "Düzce", "Merkez", 39D, 0D, 0D, 5D);
        staleWeather.setUpdatedAtEpoch(NOW - 6L * 60L * 60L - 1L);

        PlantAssistantHomeRecommendation.Recommendation recommendation =
                PlantAssistantHomeRecommendation.evaluateWithSignals(
                        Collections.singletonList(zone), staleWeather,
                        Collections.emptyList(), NOW);

        assertEquals(PlantAssistantHomeRecommendation.Level.NORMAL,
                recommendation.getLevel());
    }

    @Test
    public void distantFutureSensorTimestampIsTreatedAsMissing() {
        GardenZone zone = zone("zone-001", "season-1", "Sera 1");
        zone.setMoisture(0);
        zone.setUpdated_at_epoch((NOW + 61L) * 1000L);

        PlantAssistantHomeRecommendation.Recommendation recommendation =
                PlantAssistantHomeRecommendation.evaluateWithSignals(
                        Collections.singletonList(zone), null,
                        Collections.emptyList(), NOW);

        assertEquals(PlantAssistantHomeRecommendation.Level.FOLLOW_UP,
                recommendation.getLevel());
        assertTrue(recommendation.getMessage().contains("güncel sensör verisi yok"));
    }

    private static GardenZone zone(String zoneId, String seasonId, String areaName) {
        GardenZone zone = new GardenZone();
        zone.setZone_id(zoneId);
        zone.setArea_name(areaName);
        zone.setUpdated_at_epoch(NOW);
        zone.setMoisture(70);
        zone.setMoisture_limit(40);
        ZoneSeasonState season = new ZoneSeasonState();
        season.setStatus(SeasonStatus.ACTIVE);
        season.setActive_season_id(seasonId);
        season.setStarted_at_epoch(NOW - 86400L);
        zone.setSeason(season);
        return zone;
    }
}
