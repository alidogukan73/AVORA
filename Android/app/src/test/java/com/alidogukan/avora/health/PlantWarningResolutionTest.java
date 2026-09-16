package com.alidogukan.avora.health;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.alidogukan.avora.models.GardenPhoto;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.SeasonStatus;
import com.alidogukan.avora.models.ZoneSeasonState;
import com.alidogukan.avora.plantassistant.PlantAssistantHealthSignal;
import com.alidogukan.avora.plantassistant.PlantAssistantRecordResolver;

import org.junit.Test;

import java.util.Arrays;

public final class PlantWarningResolutionTest {
    private static final long NOW = 2_000_000L;

    @Test
    public void newerNormalReadingClosesOldHighMoistureFinding() {
        GardenZone zone = zone(55, NOW);
        PlantAssistantHealthSignal signal = moistureSignal(NOW - 60L);

        assertEquals(100, GardenHealthCalculator.evaluateZone(zone, NOW, signal).getScore());
    }

    @Test
    public void archivedGrowthWarningUsesTheActualMoistureFinding() {
        PlantAssistantHealthSignal oldSignal = new PlantAssistantHealthSignal(
                "zone-001", "season-001", "Orta",
                "Domates Fidesi Gelişim Durumu", "", "", NOW - 60L);
        GardenPhoto photo = photo("photo-growth", "Domates Fidesi Gelişim Durumu", NOW - 65L);
        photo.setAnalysis_advice("Çok yüksek nem oranı ve aşırı toprak nemi kök çürüklüğü riski oluşturabilir");
        PlantAssistantHealthSignal enriched = PlantAssistantRecordResolver.enrich(
                oldSignal, Arrays.asList(photo));

        assertEquals("photo-growth", enriched.getRecordId());
        assertEquals(100, GardenHealthCalculator.evaluateZone(zone(59, NOW), NOW, enriched).getScore());
        assertEquals(100, GardenHealthCalculator.calculate(
                Arrays.asList(zone(59, NOW)), NOW, enriched).getScore());
        assertEquals(88, GardenHealthCalculator.evaluateZone(zone(90, NOW), NOW, enriched).getScore());
    }
    @Test
    public void unrelatedArchivedPhotoCannotSilenceAnActiveWarning() {
        PlantAssistantHealthSignal signal = new PlantAssistantHealthSignal(
                "zone-001", "season-001", "Orta", "Domates Fidesi Gelişim Durumu",
                "", "", NOW - 60L);
        GardenPhoto otherSeason = photo("photo-other", "Domates Fidesi Gelişim Durumu", NOW - 65L);
        otherSeason.setSeason_id("other-season");
        otherSeason.setAnalysis_advice("Aşırı toprak nemi");
        GardenPhoto oldPhoto = photo("photo-old", "Domates Fidesi Gelişim Durumu", NOW - 2L * 86400L);
        oldPhoto.setAnalysis_advice("Aşırı toprak nemi");

        PlantAssistantHealthSignal unchanged = PlantAssistantRecordResolver.enrich(
                signal, Arrays.asList(otherSeason, oldPhoto));
        assertEquals("", unchanged.getAdvice());
        assertEquals(88, GardenHealthCalculator.evaluateZone(zone(59, NOW), NOW, unchanged).getScore());
    }
    @Test
    public void turkishPossessiveHighMoistureTextIsResolved() {
        GardenZone zone = zone(55, NOW);
        PlantAssistantHealthSignal signal = new PlantAssistantHealthSignal(
                "zone-001", "season-001", "Orta", "Dikkat edilmesi gerekenler",
                "Toprak neminin yüksek olduğu görülüyor", "", NOW);

        assertEquals(100, GardenHealthCalculator.evaluateZone(zone, NOW, signal).getScore());
    }
    @Test
    public void highOrNotNewerReadingKeepsFindingOpen() {
        assertEquals(88, GardenHealthCalculator.evaluateZone(
                zone(90, NOW), NOW, moistureSignal(NOW - 60L)).getScore());
        assertEquals(88, GardenHealthCalculator.evaluateZone(
                zone(55, NOW - 61L), NOW, moistureSignal(NOW - 60L)).getScore());
    }

    @Test
    public void issueKeepsExactAnalysisRecordId() {
        GardenHealthZoneResult result = GardenHealthCalculator.evaluateZone(
                zone(55, NOW), NOW,
                new PlantAssistantHealthSignal("zone-001", "season-001", "Orta",
                        "Yaprak lekesi", "Yaprakları kontrol edin", "photo-123", NOW));

        assertEquals("photo-123", result.getIssues().get(0).getRecordId());
    }

    @Test
    public void recordResolverPrefersIdAndSupportsLegacyTitle() {
        GardenPhoto older = photo("photo-old", "Yaprak lekesi", NOW - 10L);
        GardenPhoto exact = photo("photo-123", "Başka başlık", NOW);

        assertEquals("photo-123", PlantAssistantRecordResolver.find(
                Arrays.asList(older, exact), "zone-001", "season-001",
                "photo-123", "Bitki Asistanı: Yaprak lekesi").getId());
        GardenPhoto legacy = PlantAssistantRecordResolver.find(
                Arrays.asList(older, exact), "zone-001", "season-001", "",
                "Bitki Asistanı: Yaprak lekesi. Yapılacak: kontrol edin");
        assertNotNull(legacy);
        assertEquals("photo-old", legacy.getId());
        assertNull(PlantAssistantRecordResolver.find(
                Arrays.asList(older), "zone-001", "other-season", "",
                "Bitki Asistanı: Yaprak lekesi"));
    }

    private static PlantAssistantHealthSignal moistureSignal(long createdAt) {
        return new PlantAssistantHealthSignal("zone-001", "season-001", "Orta",
                "Dikkat edilmesi gerekenler", "Toprak nemi yüksek görünüyor", "", createdAt);
    }

    private static GardenZone zone(int moisture, long updatedAt) {
        GardenZone zone = new GardenZone();
        zone.setZone_id("zone-001");
        zone.setMoisture(moisture);
        zone.setMoisture_limit(40);
        zone.setUpdated_at_epoch(updatedAt);
        ZoneSeasonState season = new ZoneSeasonState();
        season.setStatus(SeasonStatus.ACTIVE);
        season.setActive_season_id("season-001");
        zone.setSeason(season);
        return zone;
    }

    private static GardenPhoto photo(String id, String title, long capturedAt) {
        GardenPhoto photo = new GardenPhoto();
        photo.setId(id);
        photo.setZone_id("zone-001");
        photo.setSeason_id("season-001");
        photo.setAnalysis_title(title);
        photo.setCaptured_at_epoch(capturedAt);
        return photo;
    }
}
