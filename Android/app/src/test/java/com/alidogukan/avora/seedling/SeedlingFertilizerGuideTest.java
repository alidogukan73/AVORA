package com.alidogukan.avora.seedling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.alidogukan.avora.R;
import com.alidogukan.avora.crop.CropCatalog;
import com.alidogukan.avora.models.CropCatalogItem;

import org.junit.Test;

public final class SeedlingFertilizerGuideTest {
    private static final String[] STAGES = {
            SeedlingStagePolicy.SOWN,
            SeedlingStagePolicy.GERMINATING,
            SeedlingStagePolicy.COTYLEDON,
            SeedlingStagePolicy.TRUE_LEAVES,
            SeedlingStagePolicy.HARDENING,
            SeedlingStagePolicy.READY
    };

    @Test public void everyBuiltInCropHasGuidanceForEveryNurseryStage() {
        for (CropCatalogItem crop : CropCatalog.builtIns()) {
            for (String stage : STAGES) {
                SeedlingFertilizerGuide.Recommendation value =
                        SeedlingFertilizerGuide.forPlant(crop.getName(), stage);
                assertNotEquals(crop.getName() + " " + stage,
                        0, value.getFertilizerText());
                assertNotEquals(crop.getName() + " " + stage,
                        0, value.getApplicationText());
                assertNotEquals(crop.getName() + " " + stage,
                        0, value.getCropNoteText());
            }
        }
    }

    @Test public void sowingAndGerminationDoNotRecommendFertilizer() {
        assertEquals(R.string.seedling_fertilizer_none,
                guide("Domates", SeedlingStagePolicy.SOWN).getFertilizerText());
        assertEquals(R.string.seedling_fertilizer_none,
                guide("Biber", SeedlingStagePolicy.GERMINATING).getFertilizerText());
    }

    @Test public void trueLeafFeedingDiffersByCropGroup() {
        assertEquals(R.string.seedling_fertilizer_complete_high,
                guide("Domates", SeedlingStagePolicy.TRUE_LEAVES)
                        .getFertilizerText());
        assertEquals(R.string.seedling_fertilizer_complete_low,
                guide("Salatalık", SeedlingStagePolicy.TRUE_LEAVES)
                        .getFertilizerText());
        assertEquals(R.string.seedling_fertilizer_legume,
                guide("Fasulye", SeedlingStagePolicy.TRUE_LEAVES)
                        .getFertilizerText());
        assertEquals(R.string.seedling_fertilizer_root,
                guide("Havuç", SeedlingStagePolicy.TRUE_LEAVES)
                        .getFertilizerText());
    }

    @Test public void commonTurkishUserCropsReceiveSpecificProfiles() {
        assertEquals(R.string.seedling_fertilizer_crop_brassica,
                guide("Brokoli", SeedlingStagePolicy.TRUE_LEAVES)
                        .getCropNoteText());
        assertEquals(R.string.seedling_fertilizer_crop_parsley,
                guide("Maydanoz", SeedlingStagePolicy.TRUE_LEAVES)
                        .getCropNoteText());
    }

    @Test public void hardeningNeverIncreasesFertilizer() {
        assertEquals(R.string.seedling_fertilizer_hardening,
                guide("Domates", SeedlingStagePolicy.HARDENING)
                        .getFertilizerText());
        assertEquals(R.string.seedling_fertilizer_application_hardening,
                guide("Karpuz", SeedlingStagePolicy.HARDENING)
                        .getApplicationText());
    }

    private static SeedlingFertilizerGuide.Recommendation guide(
            String crop, String stage) {
        return SeedlingFertilizerGuide.forPlant(crop, stage);
    }
}
