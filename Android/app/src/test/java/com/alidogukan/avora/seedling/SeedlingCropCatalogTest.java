package com.alidogukan.avora.seedling;

import static org.junit.Assert.assertEquals;

import com.alidogukan.avora.models.CropCatalogItem;

import org.junit.Test;

public final class SeedlingCropCatalogTest {
    @Test public void tomatoUsesReferenceCultivarAndDates() {
        CropCatalogItem tomato = new CropCatalogItem(
                "tomato", "Domates", "🍅", "tomato",
                40, 60, CropCatalogItem.SOURCE_SYSTEM, true);
        SeedlingCropCatalog.Profile profile = SeedlingCropCatalog.profileFor(tomato);

        assertEquals("H2274", profile.getVarieties().get(0));
        assertEquals(6, profile.getEmergenceDays());
        assertEquals(17, profile.getTransplantDays());
        assertEquals(42, profile.getReadyDays());
    }

    @Test public void customCropGetsSafeFallbackProfile() {
        CropCatalogItem custom = new CropCatalogItem(
                "user-custom", "Özel Ürün", "🌱", "custom-ozel-urun",
                40, 60, CropCatalogItem.SOURCE_USER, true);
        SeedlingCropCatalog.Profile profile = SeedlingCropCatalog.profileFor(custom);

        assertEquals("Standart", profile.getVarieties().get(0));
        assertEquals(7, profile.getEmergenceDays());
        assertEquals(21, profile.getTransplantDays());
        assertEquals(42, profile.getReadyDays());
    }

    @Test public void resolvesTurkishBatchDisplayNameToItsCropProfile() {
        SeedlingCropCatalog.Profile profile =
                SeedlingCropCatalog.profileForPlant("Domates");

        assertEquals(6, profile.getEmergenceDays());
        assertEquals(6, profile.getFirstLeafAfterEmergenceDays());
        assertEquals(42, profile.getReadyDays());
    }
}
