package com.alidogukan.avora.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GardenPhotoArchiveManagerContractTest {
    @Test
    public void acceptsGeneratedPhotoIdentifiersAndEntryNames() {
        String id = "550e8400-e29b-41d4-a716-446655440000";

        assertTrue(GardenPhotoArchiveManager.isSafePhotoId(id));
        assertEquals("photos/" + id + ".jpg",
                GardenPhotoArchiveManager.photoEntry(id));
        assertTrue(GardenPhotoArchiveManager.isSafePhotoEntry(
                "photos/" + id + ".jpg"));
    }

    @Test
    public void rejectsTraversalAndUnexpectedArchiveEntries() {
        assertFalse(GardenPhotoArchiveManager.isSafePhotoEntry("../photo.jpg"));
        assertFalse(GardenPhotoArchiveManager.isSafePhotoEntry(
                "photos/../../private.jpg"));
        assertFalse(GardenPhotoArchiveManager.isSafePhotoEntry(
                "photos\\photo.jpg"));
        assertFalse(GardenPhotoArchiveManager.isSafePhotoEntry(
                "photos/photo.png"));
        assertFalse(GardenPhotoArchiveManager.isSafePhotoEntry(
                "other/photo.jpg"));
    }

    @Test
    public void rejectsUnsafePhotoIdentifiers() {
        assertFalse(GardenPhotoArchiveManager.isSafePhotoId(""));
        assertFalse(GardenPhotoArchiveManager.isSafePhotoId("../photo"));
        assertFalse(GardenPhotoArchiveManager.isSafePhotoId("photo.jpg"));
        assertFalse(GardenPhotoArchiveManager.isSafePhotoId("space value"));
        assertFalse(GardenPhotoArchiveManager.isSafePhotoId(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
    }
}
