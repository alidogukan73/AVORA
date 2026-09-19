package com.alidogukan.avora.activities;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.alidogukan.avora.models.FertilizerApplication;
import com.alidogukan.avora.models.WateringHistory;

import org.junit.Test;

import java.util.Arrays;

public final class JournalLinkedRecordPolicyTest {
    @Test public void unrelatedFertilizerIsNeverShown() {
        FertilizerApplication item = fertilizer("fert-1", "zone-1", "season-1");
        assertFalse(JournalLinkedRecordPolicy.matchesFertilizer(
                "journal-photo-group", "zone-1", "season-1", item));
        assertFalse(JournalLinkedRecordPolicy.matchesFertilizer(
                "", "zone-1", "season-1", item));
    }

    @Test public void explicitFertilizerLinkRequiresMatchingZoneAndSeason() {
        FertilizerApplication item = fertilizer("fert-1", "zone-1", "season-1");
        assertTrue(JournalLinkedRecordPolicy.matchesFertilizer(
                "fert-1", "zone-1", "season-1", item));
        assertFalse(JournalLinkedRecordPolicy.matchesFertilizer(
                "fert-1", "zone-2", "season-1", item));
        assertFalse(JournalLinkedRecordPolicy.matchesFertilizer(
                "fert-1", "zone-1", "season-2", item));
    }

    @Test public void multiSeasonFertilizerUsesExplicitMembership() {
        FertilizerApplication item = fertilizer("fert-1", "zone-1", "legacy");
        item.setSeason_ids(Arrays.asList("season-1", "season-2"));
        assertTrue(JournalLinkedRecordPolicy.matchesFertilizer(
                "fert-1", "zone-1", "season-2", item));
        assertFalse(JournalLinkedRecordPolicy.matchesFertilizer(
                "fert-1", "zone-1", "legacy", item));
    }

    @Test public void wateringMustBeExplicitlyLinkedAndCompleted() {
        WateringHistory item = new WateringHistory();
        item.setRecordId("water-1");
        item.setZoneId("zone-1");
        item.setSeasonId("season-1");
        item.setCompleted(true);
        assertTrue(JournalLinkedRecordPolicy.matchesWatering(
                "water-1", "zone-1", "season-1", item));
        item.setCompleted(false);
        assertFalse(JournalLinkedRecordPolicy.matchesWatering(
                "water-1", "zone-1", "season-1", item));
    }

    private static FertilizerApplication fertilizer(String id, String zone, String season) {
        FertilizerApplication item = new FertilizerApplication();
        item.setApplication_id(id);
        item.setZone_id(zone);
        item.setSeason_id(season);
        return item;
    }
}
