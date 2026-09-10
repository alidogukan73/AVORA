package com.alidogukan.avora.seedling;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.alidogukan.avora.crop.CropCatalog;
import com.alidogukan.avora.models.CropCatalogItem;

import org.junit.Test;

public final class SeedlingPlantingGuideTest {
    @Test public void everyBuiltInCropHasCompleteGuidance() {
        for (CropCatalogItem crop : CropCatalog.builtIns()) {
            SeedlingPlantingGuide.Guide guide = SeedlingPlantingGuide.forCrop(crop);
            assertNotEquals(crop.getName(), 0, guide.getSoilText());
            assertNotEquals(crop.getName(), 0, guide.getTrayText());
        }
    }

    @Test public void tomatoUsesTrayWhileCarrotAndPotatoDoNotRequireOne() {
        assertTrue(SeedlingPlantingGuide.forCrop(find("tomato")).isTrayCountRequired());
        assertFalse(SeedlingPlantingGuide.forCrop(find("carrot")).isTrayCountRequired());
        assertFalse(SeedlingPlantingGuide.forCrop(find("potato")).isTrayCountRequired());
    }

    @Test public void unknownUserCropGetsSafeTrayBasedFallback() {
        CropCatalogItem crop = new CropCatalogItem(
                "user-special", "Özel Ürün", "🌱", "custom-special",
                40, 60, CropCatalogItem.SOURCE_USER, true);
        assertTrue(SeedlingPlantingGuide.forCrop(crop).isTrayCountRequired());
    }

    private static CropCatalogItem find(String id) {
        for (CropCatalogItem crop : CropCatalog.builtIns()) {
            if (id.equals(crop.getCrop_id())) return crop;
        }
        throw new AssertionError("Missing crop: " + id);
    }
}
