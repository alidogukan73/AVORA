package com.alidogukan.avora.photos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.alidogukan.avora.models.GardenPhoto;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public final class JournalPhotoRecordFilterTest {
    @Test
    public void plantAssistantLabelDoesNotMergeIndependentAnalyses() {
        GardenPhoto first = photo("one", "zone-1", "plant_assistant", "/one.jpg");
        GardenPhoto selected = photo("two", "zone-1", "plant_assistant", "/two.jpg");
        GardenPhoto third = photo("three", "zone-1", "plant_assistant", "/three.jpg");

        List<GardenPhoto> result = JournalPhotoRecordFilter.select(
                Arrays.asList(first, selected, third),
                "zone-1",
                "plant_assistant",
                "/two.jpg"
        );

        assertEquals(1, result.size());
        assertSame(selected, result.get(0));
    }

    @Test
    public void journalRecordGroupKeepsAllAndOnlyItsOwnPhotos() {
        GardenPhoto first = photo("one", "zone-1", "journal_record_a", "/one.jpg");
        GardenPhoto second = photo("two", "zone-1", "journal_record_a", "/two.jpg");
        GardenPhoto otherGroup = photo("three", "zone-1", "journal_record_b", "/three.jpg");
        GardenPhoto otherZone = photo("four", "zone-2", "journal_record_a", "/four.jpg");

        List<GardenPhoto> result = JournalPhotoRecordFilter.select(
                Arrays.asList(first, second, otherGroup, otherZone),
                "zone-1",
                "journal_record_a",
                "/one.jpg"
        );

        assertEquals(Arrays.asList(first, second), result);
    }

    private static GardenPhoto photo(String id, String zone, String group, String path) {
        GardenPhoto result = new GardenPhoto();
        result.setId(id);
        result.setZone_id(zone);
        result.setRelated_application_id(group);
        result.setLocal_path(path);
        return result;
    }
    @Test public void exactPhotoIdSelectsOnlyRequestedAnalysis() {
        GardenPhoto first = photo("photo-1", "zone-1", "plant_assistant", "/first.jpg");
        GardenPhoto second = photo("photo-2", "zone-1", "plant_assistant", "/second.jpg");
        List<GardenPhoto> selected = JournalPhotoRecordFilter.selectById(
                Arrays.asList(first, second), "zone-1", "photo-2");
        assertEquals(1, selected.size());
        assertEquals("photo-2", selected.get(0).getId());
    }

    @Test public void exactPhotoIdRejectsAnotherZone() {
        GardenPhoto photo = photo("photo-1", "zone-2", "plant_assistant", "/first.jpg");
        assertTrue(JournalPhotoRecordFilter.selectById(
                Arrays.asList(photo), "zone-1", "photo-1").isEmpty());
    }


    @Test public void explicitApplicationKeepsAttachmentsWithoutMergingSeasons() {
        GardenPhoto first = photo("one", "zone-1", "fertilizer-123", "/one.jpg");
        GardenPhoto second = photo("two", "zone-1", "fertilizer-123", "/two.jpg");
        GardenPhoto archived = photo("old", "zone-1", "fertilizer-123", "/old.jpg");
        first.setSeason_id("pepper"); second.setSeason_id("pepper");
        archived.setSeason_id("tomato");
        assertEquals(Arrays.asList(first, second), JournalPhotoRecordFilter.select(
                Arrays.asList(first, second, archived), "zone-1", "pepper", "fertilizer-123", "/one.jpg"));
    }

    @Test public void sourceLabelIsNotAGroupForAppendingOrDeleting() {
        org.junit.Assert.assertFalse(JournalPhotoRecordFilter.isRecordGroup("plant_assistant"));
        org.junit.Assert.assertFalse(JournalPhotoRecordFilter.isRecordGroup(null));
        org.junit.Assert.assertTrue(JournalPhotoRecordFilter.isRecordGroup("journal_record_1"));
        org.junit.Assert.assertTrue(JournalPhotoRecordFilter.isRecordGroup("fertilizer-123"));
    }
}