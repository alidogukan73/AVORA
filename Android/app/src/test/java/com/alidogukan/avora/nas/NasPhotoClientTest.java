package com.alidogukan.avora.nas;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NasPhotoClientTest {
    @Test
    public void acceptsOnlyPathSafePhotoIdentifiers() {
        assertTrue(NasPhotoClient.isSafePhotoId(
                "550e8400-e29b-41d4-a716-446655440000"));
        assertTrue(NasPhotoClient.isSafePhotoId("daily_photo-1"));
        assertFalse(NasPhotoClient.isSafePhotoId("../photo"));
        assertFalse(NasPhotoClient.isSafePhotoId("photo.jpg"));
        assertFalse(NasPhotoClient.isSafePhotoId("photo value"));
        assertFalse(NasPhotoClient.isSafePhotoId(""));
    }
}
