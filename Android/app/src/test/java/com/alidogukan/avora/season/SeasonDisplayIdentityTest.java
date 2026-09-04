package com.alidogukan.avora.season;

import static org.junit.Assert.assertEquals;

import com.alidogukan.avora.models.GardenSeason;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.SeasonStatus;
import com.alidogukan.avora.models.ZoneSeasonState;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;

public final class SeasonDisplayIdentityTest {
    @Test
    public void legacyCucumberArchiveDoesNotBorrowCurrentCropEmoji() {
        GardenZone current = zone("Domates", "🍅");
        GardenSeason cucumberArchive = season("Salatalık", "", "");

        assertEquals("Salatalık",
                SeasonDisplayIdentity.name(cucumberArchive, current));
        assertEquals("🥒",
                SeasonDisplayIdentity.emoji(cucumberArchive, current));
    }

    @Test
    public void unknownArchiveUsesNeutralSeedlingInsteadOfCurrentCrop() {
        GardenZone current = zone("Biber", "🌶️");
        GardenSeason archive = season("Deneme ürünü", "", "");

        assertEquals("🌱", SeasonDisplayIdentity.emoji(archive, current));
    }

    @Test
    public void archivedEmojiRemainsAuthoritative() {
        GardenZone current = zone("Biber", "🌶️");
        GardenSeason archive = season("Özel salatalık", "", "🪴");

        assertEquals("🪴", SeasonDisplayIdentity.emoji(archive, current));
    }

    @Test
    public void liveZoneIdentityIsUsedOnlyWithoutASeason() {
        GardenZone current = zone("Domates", "🍅");

        assertEquals("Domates", SeasonDisplayIdentity.name(null, current));
        assertEquals("🍅", SeasonDisplayIdentity.emoji(null, current));
    }

    @Test
    public void sharedAreaShowsEveryActiveCropButOnlyOnce() {
        GardenZone current = zone("Domates", "🍅");
        current.setZone_id("zone-001");
        current.setArea_id("area-one");
        ZoneSeasonState state = new ZoneSeasonState();
        state.setStatus(SeasonStatus.ACTIVE);
        state.setActive_season_id("tomato-season");
        LinkedHashMap<String, Boolean> ids = new LinkedHashMap<>();
        ids.put("tomato-season", true);
        ids.put("pepper-season", true);
        state.setActive_season_ids(ids);
        current.setSeason(state);

        GardenSeason pepper = activeSeason(
                "pepper-season", "Biber", "🌶️", "area-one", 200L);
        GardenSeason tomato = activeSeason(
                "tomato-season", "Domates", "🍅", "area-one", 100L);

        assertEquals("1. Bölge", SeasonDisplayIdentity.operationalName(
                current, Arrays.asList(pepper, tomato)));
        assertEquals("Domates + Biber", SeasonDisplayIdentity.activeCropNames(
                current, Arrays.asList(pepper, tomato)));
        assertEquals("🌿 1. Bölge · 🍅🌶️ Domates + Biber",
                SeasonDisplayIdentity.operationalLabel(current, Arrays.asList(pepper, tomato)));
        assertEquals("🍅🌶️", SeasonDisplayIdentity.operationalEmoji(
                current, Arrays.asList(pepper, tomato)));
        assertEquals(2, SeasonDisplayIdentity.activeSeasons(
                current, Arrays.asList(pepper, tomato)).size());
    }

    @Test
    public void identicalCropNamesIncludeTheirPhysicalArea() {
        GardenZone first = zone("Biber", "🌶️");
        first.setZone_id("zone-001");
        first.setArea_name("Ön Bahçe");
        GardenZone second = zone("Biber", "🌶️");
        second.setZone_id("zone-004");
        second.setArea_name("Sera");

        assertEquals("Biber · Ön Bahçe",
                SeasonDisplayIdentity.cropAreaName(null, first));
        assertEquals("Biber · Sera",
                SeasonDisplayIdentity.cropAreaName(null, second));
        assertEquals("🌶️ Biber · Ön Bahçe",
                SeasonDisplayIdentity.cropAreaLabel(null, first));
        assertEquals("🌶️ Biber\nÖn Bahçe",
                SeasonDisplayIdentity.stackedCropAreaLabel(null, first));
    }

    @Test
    public void journalTitleShowsNumberedAreaBeforeSeasonCrop() {
        GardenZone current = zone("Domates", "🍅");
        current.setZone_id("zone-004");
        GardenSeason eggplant = season("Patlıcan", "Patlıcan", "🍆");

        assertEquals("4. Bölge · Patlıcan",
                SeasonDisplayIdentity.areaCropName(eggplant, current));
    }

    @Test
    public void journalTitlesDistinguishIdenticalCropsByAreaFirst() {
        GardenZone first = zone("Biber", "🌶️");
        first.setArea_name("Ön Bahçe");
        GardenZone second = zone("Biber", "🌶️");
        second.setArea_name("Sera");

        assertEquals("Ön Bahçe · Biber",
                SeasonDisplayIdentity.areaCropName(null, first));
        assertEquals("Sera · Biber",
                SeasonDisplayIdentity.areaCropName(null, second));
    }

    @Test
    public void journalTitleWithoutCropHasNoTrailingSeparator() {
        GardenZone current = zone("", "");
        current.setZone_id("zone-003");

        assertEquals("3. Bölge", SeasonDisplayIdentity.areaCropName(null, current));
    }

    private static GardenZone zone(String name, String emoji) {
        GardenZone zone = new GardenZone();
        zone.setName(name);
        zone.setEmoji(emoji);
        return zone;
    }

    private static GardenSeason season(String name, String plantType, String emoji) {
        GardenSeason season = new GardenSeason();
        season.setZone_name(name);
        season.setPlant_type(plantType);
        season.setEmoji(emoji);
        return season;
    }

    private static GardenSeason activeSeason(String id, String name, String emoji,
                                             String areaId, long startedAt) {
        GardenSeason season = season(name, name, emoji);
        season.setSeason_id(id);
        season.setZone_id("zone-001");
        season.setArea_id(areaId);
        season.setStatus(SeasonStatus.ACTIVE);
        season.setStarted_at_epoch(startedAt);
        return season;
    }
}
