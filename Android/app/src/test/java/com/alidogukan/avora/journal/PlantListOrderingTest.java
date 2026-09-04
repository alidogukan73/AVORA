package com.alidogukan.avora.journal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.alidogukan.avora.models.FertilizationProfile;
import com.alidogukan.avora.models.GardenSeason;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.SeasonStatus;
import com.alidogukan.avora.models.ZoneIrrigationStatus;
import com.alidogukan.avora.models.ZoneSeasonState;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class PlantListOrderingTest {
    private static final long NOW = 1000L;
    private static final Locale TURKISH = Locale.forLanguageTag("tr-TR");

    @Test
    public void moistureSortMatchesTheScreenshotLowToHigh() {
        List<GardenZone> zones = Arrays.asList(
                zone(1, "Domates", 100), zone(2, "Biber", 86),
                zone(3, "Salatalık", 75), zone(4, "Patlıcan", 59));
        assertEquals(Arrays.asList("zone-004", "zone-003", "zone-002", "zone-001"),
                ids(sorted(zones, PlantListOrdering.SORT_MOISTURE)));
    }

    @Test
    public void staleAndMissingReadingsDoNotAppearAsDryPlants() {
        GardenZone stale = zone(1, "Eski veri", 0);
        stale.setUpdated_at_epoch(NOW - 91);
        GardenZone missing = zone(2, "Veri yok", 0);
        missing.setUpdated_at_epoch(0);
        GardenZone live = zone(3, "Biber", 80);
        assertEquals(Arrays.asList("zone-003", "zone-001", "zone-002"),
                ids(sorted(Arrays.asList(missing, stale, live), PlantListOrdering.SORT_MOISTURE)));
    }

    @Test
    public void freshnessBoundaryMatchesTheCardStatus() {
        GardenZone zone = zone(1, "Biber", 60);
        zone.setUpdated_at_epoch(NOW - 90);
        assertTrue(PlantListOrdering.hasCurrentSensorData(zone, NOW));
        assertFalse(PlantListOrdering.hasCurrentSensorData(zone, NOW + 1));
        zone.setUpdated_at_epoch(0);
        assertFalse(PlantListOrdering.hasCurrentSensorData(zone, NOW));
    }

    @Test
    public void mostRecentSortUsesNewestSensorTimestampFirst() {
        GardenZone old = zone(1, "Biber", 80);
        old.setUpdated_at_epoch(NOW - 20);
        GardenZone latest = zone(2, "Patlıcan", 10);
        GardenZone missing = zone(3, "Domates", 50);
        missing.setUpdated_at_epoch(0);
        assertEquals(Arrays.asList("zone-002", "zone-001", "zone-003"),
                ids(sorted(Arrays.asList(old, missing, latest), PlantListOrdering.SORT_UPDATED)));
    }

    @Test
    public void nameSortUsesEveryVisibleSeasonInsteadOfThePrimaryZoneCrop() {
        GardenZone shared = zone(1, "Domates", 70);
        GardenZone other = zone(2, "Salatalık", 80);
        GardenSeason tomato = season(shared, "tomato", "Domates");
        GardenSeason pepper = season(shared, "pepper", "Biber");
        GardenSeason eggplant = season(other, "eggplant", "Patlıcan");
        List<PlantListOrdering.Entry> entries = PlantListOrdering.entries(
                Arrays.asList(shared, other), Arrays.asList(tomato, eggplant, pepper),
                PlantListOrdering.SORT_NAME, NOW, TURKISH);
        assertEquals(Arrays.asList("pepper", "tomato", "eggplant"), seasonIds(entries));
    }

    @Test
    public void nameSortRespectsTheTurkishAlphabet() {
        List<GardenZone> zones = Arrays.asList(
                zone(1, "Şalgam", 60), zone(2, "Çilek", 60), zone(3, "Salatalık", 60),
                zone(4, "Domates", 60), zone(5, "Ceviz", 60));
        assertEquals(Arrays.asList("zone-005", "zone-002", "zone-004", "zone-003", "zone-001"),
                ids(sorted(zones, PlantListOrdering.SORT_NAME)));
    }

    @Test
    public void identicalPlantNamesUseAreaNumberAsTieBreaker() {
        assertEquals(Arrays.asList("zone-001", "zone-002", "zone-004"),
                ids(sorted(Arrays.asList(zone(4, "Biber", 60), zone(2, "Biber", 60),
                        zone(1, "Biber", 60)), PlantListOrdering.SORT_NAME)));
    }

    @Test
    public void areaSortOrdersAllEightPhysicalSlotsEvenWithCustomNames() {
        List<GardenZone> zones = new ArrayList<>();
        for (int slot = 8; slot >= 1; slot--) {
            GardenZone zone = zone(slot, "Biber", slot * 10);
            zone.setArea_name(slot == 1 ? "Zeytinlik" : "Arka Bahçe " + slot);
            zone.setOrder(9 - slot);
            zones.add(zone);
        }
        assertEquals(Arrays.asList("zone-001", "zone-002", "zone-003", "zone-004",
                "zone-005", "zone-006", "zone-007", "zone-008"),
                ids(sorted(zones, PlantListOrdering.SORT_AREA)));
        assertEquals("zone-008", zones.get(0).getZone_id());
    }

    @Test
    public void areaSortGroupsSharedCropsAndSortsTheirNamesInsideTheArea() {
        GardenZone first = zone(1, "Domates", 70);
        GardenZone second = zone(2, "Biber", 10);
        GardenSeason tomato = season(first, "tomato", "Domates");
        GardenSeason pepper = season(first, "pepper", "Biber");
        GardenSeason secondPepper = season(second, "second-pepper", "Biber");
        assertEquals(Arrays.asList("pepper", "tomato", "second-pepper"),
                seasonIds(PlantListOrdering.entries(Arrays.asList(second, first),
                        Arrays.asList(secondPepper, tomato, pepper),
                        PlantListOrdering.SORT_AREA, NOW, TURKISH)));
    }

    @Test
    public void closedSeasonsAreNotReintroducedBySorting() {
        GardenZone first = zone(1, "Domates", 70);
        GardenSeason active = season(first, "active", "Domates");
        GardenSeason archived = season(first, "archived", "Biber");
        archived.setStatus("COMPLETED");
        assertEquals(Collections.singletonList("active"),
                seasonIds(PlantListOrdering.entries(Collections.singletonList(first),
                        Arrays.asList(archived, active), PlantListOrdering.SORT_NAME, NOW, TURKISH)));
    }

    @Test
    public void smartAndAttentionSortPutCriticalAndDueWorkBeforeHealthyPlants() {
        GardenZone healthy = zone(1, "Sağlıklı", 70);
        GardenZone due = zone(2, "İşlem zamanı", 80);
        FertilizationProfile profile = new FertilizationProfile();
        profile.setEnabled(true);
        profile.setNext_application_at_epoch(NOW);
        due.setFertilization(profile);
        GardenZone low = zone(3, "Düşük nem", 30);
        GardenZone critical = zone(4, "Kritik", 10);
        GardenZone stale = zone(5, "Eski veri", 0);
        stale.setUpdated_at_epoch(NOW - 91);
        List<GardenZone> zones = Arrays.asList(healthy, due, low, critical, stale);
        for (int mode : new int[]{PlantListOrdering.SORT_SMART, PlantListOrdering.SORT_ATTENTION}) {
            assertEquals(Arrays.asList("zone-004", "zone-003", "zone-002", "zone-001", "zone-005"),
                    ids(sorted(zones, mode)));
        }
    }

    @Test
    public void wateringAndUnstableSensorPrioritiesArePreserved() {
        GardenZone healthy = zone(1, "Sağlıklı", 60);
        GardenZone watering = zone(2, "Sulama", 70);
        ZoneIrrigationStatus active = new ZoneIrrigationStatus();
        active.setSensor_stable(true);
        active.setWatering_active(true);
        watering.setIrrigation_status(active);
        GardenZone unstable = zone(3, "Kararsız sensör", 80);
        ZoneIrrigationStatus unstableStatus = new ZoneIrrigationStatus();
        unstableStatus.setSensor_stable(false);
        unstableStatus.setMoisture_deficit(20);
        unstable.setIrrigation_status(unstableStatus);
        assertEquals(Arrays.asList("zone-003", "zone-002", "zone-001"),
                ids(sorted(Arrays.asList(healthy, watering, unstable), PlantListOrdering.SORT_SMART)));
    }

    @Test
    public void equalReadingsHaveStableOrderAcrossRealtimeRefreshes() {
        List<GardenZone> zones = Arrays.asList(zone(3, "Biber", 60),
                zone(1, "Domates", 60), zone(2, "Patlıcan", 60));
        List<GardenZone> reversed = new ArrayList<>(zones);
        Collections.reverse(reversed);
        for (int mode = PlantListOrdering.SORT_SMART; mode <= PlantListOrdering.SORT_AREA; mode++) {
            assertEquals(ids(sorted(zones, mode)), ids(sorted(reversed, mode)));
        }
    }

    @Test
    public void addingAreaSortKeepsAllSavedPreferenceIdsCompatible() {
        assertEquals(0, PlantListOrdering.SORT_SMART);
        assertEquals(1, PlantListOrdering.SORT_ATTENTION);
        assertEquals(2, PlantListOrdering.SORT_MOISTURE);
        assertEquals(3, PlantListOrdering.SORT_UPDATED);
        assertEquals(4, PlantListOrdering.SORT_NAME);
        assertEquals(5, PlantListOrdering.SORT_AREA);
        for (int mode = 0; mode <= 5; mode++) {
            assertEquals(mode, PlantListOrdering.normalizeSortMode(mode));
        }
        assertEquals(0, PlantListOrdering.normalizeSortMode(-1));
        assertEquals(0, PlantListOrdering.normalizeSortMode(99));
    }

    private static List<PlantListOrdering.Entry> sorted(List<GardenZone> zones, int mode) {
        return PlantListOrdering.entries(zones, Collections.emptyList(), mode, NOW, TURKISH);
    }

    private static List<String> ids(List<PlantListOrdering.Entry> entries) {
        return entries.stream().map(entry -> entry.zone.getZone_id()).collect(Collectors.toList());
    }

    private static List<String> seasonIds(List<PlantListOrdering.Entry> entries) {
        return entries.stream().map(entry -> entry.season.getSeason_id()).collect(Collectors.toList());
    }

    private static GardenZone zone(int slot, String crop, int moisture) {
        GardenZone zone = new GardenZone();
        zone.setZone_id(String.format(Locale.ROOT, "zone-%03d", slot));
        zone.setArea_id("area-" + slot);
        zone.setName(crop);
        zone.setMoisture(moisture);
        zone.setMoisture_limit(40);
        zone.setUpdated_at_epoch(NOW);
        return zone;
    }

    private static GardenSeason season(GardenZone zone, String id, String crop) {
        GardenSeason season = new GardenSeason();
        season.setSeason_id(id);
        season.setZone_id(zone.getZone_id());
        season.setArea_id(zone.getArea_id());
        season.setZone_name(crop);
        season.setStatus(SeasonStatus.ACTIVE);
        ZoneSeasonState state = zone.getSeason();
        if (state == null) {
            state = new ZoneSeasonState();
            state.setStatus(SeasonStatus.ACTIVE);
            state.setActive_season_id(id);
            state.setActive_season_ids(new LinkedHashMap<>());
            zone.setSeason(state);
        }
        state.getActive_season_ids().put(id, true);
        return season;
    }
}
