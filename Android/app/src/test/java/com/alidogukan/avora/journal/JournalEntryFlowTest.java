package com.alidogukan.avora.journal;

import com.alidogukan.avora.models.GardenEvent;
import com.alidogukan.avora.models.GardenPhoto;
import com.alidogukan.avora.models.GardenSeason;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.SeasonStatus;
import com.alidogukan.avora.models.ZoneSeasonState;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class JournalEntryFlowTest {
    private GardenZone zone() {
        GardenZone zone = new GardenZone();
        zone.setZone_id("zone1");
        ZoneSeasonState state = new ZoneSeasonState();
        state.setStatus(SeasonStatus.ACTIVE);
        state.setActive_season_id("tomato");
        HashMap<String, Boolean> active = new HashMap<>();
        active.put("tomato", true);
        active.put("pepper", true);
        state.setActive_season_ids(active);
        zone.setSeason(state);
        return zone;
    }
    private GardenSeason season(String id) {
        GardenSeason season = new GardenSeason();
        season.setZone_id("zone1");
        season.setSeason_id(id);
        season.setStatus(SeasonStatus.ACTIVE);
        return season;
    }
    @Test public void permitsSecondaryCropWithoutSwitchingToPrimaryCrop() {
        assertTrue(JournalEntryPolicy.writableSeason(zone(), season("pepper")));
        assertTrue(JournalEntryPolicy.writableSeason(zone(), season("tomato")));
    }
    @Test public void rejectsClosedAndDetachedSeasons() {
        GardenSeason selected = season("pepper");
        selected.setStatus(SeasonStatus.CLOSED);
        assertFalse(JournalEntryPolicy.writableSeason(zone(), selected));
        assertFalse(JournalEntryPolicy.writableSeason(zone(), season("old-crop")));
        selected.setStatus(SeasonStatus.ACTIVE);
        selected.setZone_id("zone2");
        assertFalse(JournalEntryPolicy.writableSeason(zone(), selected));
    }
    @Test public void rejectsInactiveZoneAndExplicitlyRemovedCrop() {
        GardenZone zone = zone();
        zone.getSeason().getActive_season_ids().put("pepper", false);
        assertFalse(JournalEntryPolicy.writableSeason(zone, season("pepper")));
        zone.setLifecycle_status("INACTIVE");
        assertFalse(JournalEntryPolicy.writableSeason(zone, season("tomato")));
    }
    @Test public void supportsLegacyPrimarySeasonButNotUnlistedSecondaryCrop() {
        GardenZone zone = zone();
        zone.getSeason().setActive_season_ids(Collections.emptyMap());
        assertTrue(JournalEntryPolicy.writableSeason(zone, season("tomato")));
        assertFalse(JournalEntryPolicy.writableSeason(zone, season("pepper")));
        assertFalse(JournalEntryPolicy.writableSeason(null, season("tomato")));
        assertFalse(JournalEntryPolicy.writableSeason(zone, null));
    }
    @Test public void photoDoesNotMakeAnEmptyObservationValid() {
        assertFalse(JournalEntryPolicy.validContent("observation", "  ", 1));
        assertFalse(JournalEntryPolicy.validContent("harvest", null, 2));
        assertTrue(JournalEntryPolicy.validContent("flowering", "First bloom", 0));
        assertTrue(JournalEntryPolicy.validContent("observation", "New leaves", 5));
    }
    @Test public void photoOnlyRecordRequiresPhotosAndAllTypesRespectTheLimit() {
        assertFalse(JournalEntryPolicy.validContent("photo", "A photo", 0));
        assertTrue(JournalEntryPolicy.validContent("photo", "", 1));
        assertFalse(JournalEntryPolicy.validContent("photo", "", 6));
        assertFalse(JournalEntryPolicy.validContent("special", "A note", 6));
        assertFalse(JournalEntryPolicy.validContent("watering", "Watered", 1));
        assertFalse(JournalEntryPolicy.validContent("fertilization", "Fed", 0));
        assertFalse(JournalEntryPolicy.validContent("unknown", "Note", 0));
    }
    private GardenEvent event() {
        GardenEvent event = new GardenEvent();
        event.setId("entry-1"); event.setZone_id("zone1");
        event.setSeason_id("pepper"); event.setType("observation");
        return event;
    }
    private GardenPhoto photo() {
        GardenPhoto photo = new GardenPhoto();
        photo.setId("photo-1"); photo.setZone_id("zone1");
        photo.setSeason_id("pepper"); photo.setRelated_application_id("journal_record_entry-1");
        return photo;
    }
    @Test public void attachedPhotoOpensItsOwningNote() {
        GardenEvent note = event();
        assertSame(note, JournalEntryPolicy.photoOwner(photo(), Arrays.asList(null, note)));
    }
    @Test public void matchingGroupCannotCrossZonesOrSeasons() {
        GardenPhoto photo = photo();
        photo.setSeason_id("tomato");
        assertNull(JournalEntryPolicy.photoOwner(photo, Collections.singletonList(event())));
        photo.setSeason_id("pepper"); photo.setZone_id("zone2");
        assertNull(JournalEntryPolicy.photoOwner(photo, Collections.singletonList(event())));
    }
    @Test public void standaloneAndOperationPhotosDoNotBecomeManualAttachments() {
        GardenPhoto photo = photo();
        photo.setRelated_application_id("fertilizer-1");
        assertNull(JournalEntryPolicy.photoOwner(photo, Collections.singletonList(event())));
        GardenEvent automated = event(); automated.setSource("AUTO");
        assertNull(JournalEntryPolicy.photoOwner(photo(), Collections.singletonList(automated)));
        assertNull(JournalEntryPolicy.photoOwner(photo(), Collections.emptyList()));
    }
}
