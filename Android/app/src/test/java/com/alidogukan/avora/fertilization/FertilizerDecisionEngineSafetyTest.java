package com.alidogukan.avora.fertilization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.alidogukan.avora.models.FertilizationProfile;
import com.alidogukan.avora.models.FertilizerApplicationSchedule;
import com.alidogukan.avora.models.FertilizerProduct;
import com.alidogukan.avora.models.GardenZone;

import org.junit.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class FertilizerDecisionEngineSafetyTest {

    @Test
    public void stageIncompatibleProductIsNotRecommended() {
        GardenZone zone = zone("ROOTING", 60);
        FertilizerProduct product = readyProduct();
        product.setRecommended_stages(Collections.singletonList("FRUITING"));

        FertilizerAdvice advice = FertilizerDecisionEngine.advise(
                zone, Collections.singletonList(product), null, now()
        );

        assertEquals(FertilizerAdvice.STATUS_TOO_EARLY, advice.getStatus());
        assertTrue(advice.getCandidates().isEmpty());
    }

    @Test
    public void soilPreparationProductIsExcludedDuringFruitFormation() {
        GardenZone zone = zone("FRUITING", 60);
        FertilizerProduct product = readyProduct();
        product.setName("OG Toros Organik Katı Gübre");
        product.setRecommended_stages(
                Collections.singletonList("SOIL_PREPARATION")
        );

        FertilizerAdvice advice = FertilizerDecisionEngine.advise(
                zone, Collections.singletonList(product), null, now()
        );

        assertEquals(FertilizerAdvice.STATUS_TOO_EARLY, advice.getStatus());
        assertTrue(advice.getCandidates().isEmpty());
    }

    @Test
    public void productWithoutPeriodIsExcludedFromAdvice() {
        GardenZone zone = zone("FRUITING", 60);
        FertilizerProduct product = readyProduct();
        product.setRecommended_stages(Collections.emptyList());

        FertilizerAdvice advice = FertilizerDecisionEngine.advise(
                zone, Collections.singletonList(product), null, now()
        );

        assertEquals(FertilizerAdvice.STATUS_TOO_EARLY, advice.getStatus());
        assertTrue(advice.getCandidates().isEmpty());
    }
    @Test
    public void repeatIntervalBlocksAnotherProductOfSameApplicationType() {
        GardenZone zone = zone("FRUITING", 60);
        FertilizerApplicationSchedule schedule =
                new FertilizerApplicationSchedule();
        schedule.setNext_application_at_epoch(now() + 3L * 86400L);
        Map<String, FertilizerApplicationSchedule> schedules = new HashMap<>();
        schedules.put("NUTRITION", schedule);
        zone.getFertilization().setApplication_schedules(schedules);

        FertilizerProduct replacement = readyProduct();
        replacement.setProduct_id("different-product");

        FertilizerAdvice advice = FertilizerDecisionEngine.advise(
                zone, Collections.singletonList(replacement), null, now()
        );

        assertEquals(FertilizerAdvice.STATUS_TOO_EARLY, advice.getStatus());
        assertTrue(advice.getReason().contains("bekleme aralığı"));
        assertTrue(advice.getRecommendation().isAvailable());
        assertTrue(advice.getRecommendation().getWaitDays() > 0L);
        assertFalse(advice.getRecommendation().isApplicationReady());
    }

    @Test
    public void insufficientStockRequiresPreparation() {
        GardenZone zone = zone("FRUITING", 60);
        FertilizerProduct product = readyProduct();
        product.setStock_amount(50.0);

        FertilizerAdvice advice = FertilizerDecisionEngine.advise(
                zone, Collections.singletonList(product), null, now()
        );

        assertEquals(FertilizerAdvice.STATUS_PREPARATION_REQUIRED, advice.getStatus());
        assertTrue(advice.getReason().contains("yetmiyor"));
        assertFalse(advice.getCandidates().isEmpty());
    }

    @Test
    public void missingStockUnitRequiresPreparation() {
        GardenZone zone = zone("FRUITING", 60);
        FertilizerProduct product = readyProduct();
        product.setStock_unit("");

        FertilizerAdvice advice = FertilizerDecisionEngine.advise(
                zone, Collections.singletonList(product), null, now()
        );

        assertEquals(FertilizerAdvice.STATUS_PREPARATION_REQUIRED, advice.getStatus());
        assertTrue(advice.getReason().contains("stok miktarı"));
    }

    @Test
    public void lowMoistureBlocksApplicationEvenWhenProductIsReady() {
        GardenZone zone = zone("FRUITING", 30);

        FertilizerAdvice advice = FertilizerDecisionEngine.advise(
                zone, Collections.singletonList(readyProduct()), null, now()
        );

        assertEquals(FertilizerAdvice.STATUS_WATERING_FIRST, advice.getStatus());
        assertFalse(advice.getCandidates().isEmpty());
    }

    @Test
    public void compatibleProductWithEnoughStockCanBeRecommendedToday() {
        GardenZone zone = zone("FRUITING", 60);

        FertilizerAdvice advice = FertilizerDecisionEngine.advise(
                zone, Collections.singletonList(readyProduct()), null, now()
        );

        assertEquals(FertilizerAdvice.STATUS_TODAY_ADVICE, advice.getStatus());
        assertEquals(1, advice.getCandidates().size());
        assertTrue(advice.getCandidates().get(0).contains("100 g"));
        assertTrue(advice.getRecommendation().isAvailable());
        assertTrue(advice.getRecommendation().isApplicationReady());
        assertEquals("fertilizer-001", advice.getRecommendation().getProductId());
        assertEquals("NUTRITION", advice.getRecommendation().getApplicationType());
    }

    @Test
    public void tankBasedGramDoseUsesSameUnitsAsRecordingSafety() {
        GardenZone zone = zone("FRUITING", 60);
        FertilizerProduct product = readyProduct();
        product.setDosage_unit("g / 100 L su");
        product.setLabel_dosage_min(250.0);
        product.setLabel_dosage_max(250.0);
        product.setStock_amount(500.0);

        FertilizerAdvice advice = FertilizerDecisionEngine.advise(
                zone, Collections.singletonList(product), null, now()
        );

        assertEquals(FertilizerAdvice.STATUS_TODAY_ADVICE, advice.getStatus());
        assertTrue(advice.getCandidates().get(0).contains("250 g"));
    }

    @Test
    public void explicitlyConfiguredProductCanBeRecommendedDuringActiveHarvest() {
        GardenZone zone = zone("HARVEST", 60);
        FertilizerProduct product = harvestProduct();

        FertilizerAdvice advice = FertilizerDecisionEngine.advise(
                zone, Collections.singletonList(product), null, now()
        );

        assertEquals(FertilizerAdvice.STATUS_TODAY_ADVICE, advice.getStatus());
        assertEquals(1, advice.getCandidates().size());
        assertTrue(advice.getReason().contains("Aktif hasatta"));
    }

    @Test
    public void fruitingOnlyProductIsNotRecommendedDuringActiveHarvest() {
        GardenZone zone = zone("HARVEST", 60);

        FertilizerAdvice advice = FertilizerDecisionEngine.advise(
                zone, Collections.singletonList(readyProduct()), null, now()
        );

        assertEquals(FertilizerAdvice.STATUS_TOO_EARLY, advice.getStatus());
        assertTrue(advice.getCandidates().isEmpty());
    }

    @Test
    public void seasonEndNeverProducesFeedingRecommendation() {
        GardenZone zone = zone("SEASON_END", 60);
        zone.getFertilization().setEnabled(false);

        FertilizerAdvice advice = FertilizerDecisionEngine.advise(
                zone, Collections.singletonList(harvestProduct()), null, now()
        );

        assertEquals(FertilizerAdvice.STATUS_SEASON_COMPLETED, advice.getStatus());
        assertTrue(advice.getCandidates().isEmpty());
        assertTrue(advice.getRisks().isEmpty());
    }

    @Test
    public void organicCompatibleProductRanksFirstWhenPreferenceIsEnabled() {
        GardenZone zone = zone("FRUITING", 60);
        FertilizerProduct conventional = readyProduct();
        conventional.setProduct_id("conventional");
        conventional.setName("Conventional 10-5-40");
        conventional.setOrganic_farming_eligible(false);

        FertilizerProduct organicCompatible = readyProduct();
        organicCompatible.setProduct_id("organic-compatible");
        organicCompatible.setName("Organic-compatible 10-5-40");
        organicCompatible.setOrganic_farming_eligible(true);

        FertilizerAdvice advice = FertilizerDecisionEngine.advise(
                zone,
                Arrays.asList(conventional, organicCompatible),
                null,
                Collections.emptyList(),
                now(),
                true
        );

        assertEquals(FertilizerAdvice.STATUS_TODAY_ADVICE, advice.getStatus());
        assertTrue(advice.getCandidates().get(0)
                .contains("Organic-compatible 10-5-40"));
        assertTrue(advice.getCandidates().get(0)
                .contains("Organik tar\u0131ma uygun"));
    }
    @Test
    public void conventionalProductRemainsAvailableBeforeFruitFormation() {
        GardenZone zone = zone("VEGETATIVE", 60);
        FertilizerProduct conventional = readyProduct();
        conventional.setRecommended_stages(Collections.singletonList("VEGETATIVE"));

        FertilizerAdvice advice = FertilizerDecisionEngine.advise(
                zone, Collections.singletonList(conventional), null,
                Collections.emptyList(), now(), true
        );

        assertEquals(FertilizerAdvice.STATUS_TODAY_ADVICE, advice.getStatus());
        assertFalse(advice.getCandidates().isEmpty());
    }

    @Test
    public void conventionalProductIsExcludedAfterFruitFormation() {
        GardenZone zone = zone("FRUITING", 60);
        FertilizerProduct conventional = readyProduct();
        conventional.setOrganic_farming_eligible(false);

        FertilizerAdvice advice = FertilizerDecisionEngine.advise(
                zone, Collections.singletonList(conventional), null,
                Collections.emptyList(), now(), true
        );

        assertEquals(FertilizerAdvice.STATUS_ORGANIC_REQUIRED, advice.getStatus());
        assertTrue(advice.getCandidates().isEmpty());
        assertTrue(advice.getReason().contains("kimyasal i\u00e7erikli"));
    }

    @Test
    public void organicCompatibleProductIsRecommendedAfterFruitFormation() {
        GardenZone zone = zone("FRUITING", 60);
        FertilizerProduct organicCompatible = readyProduct();
        organicCompatible.setOrganic_farming_eligible(true);

        FertilizerAdvice advice = FertilizerDecisionEngine.advise(
                zone, Collections.singletonList(organicCompatible), null,
                Collections.emptyList(), now(), true
        );

        assertEquals(FertilizerAdvice.STATUS_TODAY_ADVICE, advice.getStatus());
        assertFalse(advice.getCandidates().isEmpty());
        assertTrue(advice.getReason().contains("Meyve olu\u015Fumu"));
    }

    @Test
    public void emptyCatalogRequestsOrganicProductAfterFruitFormation() {
        GardenZone zone = zone("FRUITING", 60);

        FertilizerAdvice advice = FertilizerDecisionEngine.advise(
                zone, Collections.emptyList(), null,
                Collections.emptyList(), now(), true
        );

        assertEquals(FertilizerAdvice.STATUS_ORGANIC_REQUIRED, advice.getStatus());
        assertTrue(advice.getCandidates().isEmpty());
    }

    @Test
    public void organicProductInSafeWaitDoesNotRequestAnotherProduct() {
        GardenZone zone = zone("FRUITING", 60);
        FertilizerApplicationSchedule schedule =
                new FertilizerApplicationSchedule();
        schedule.setNext_application_at_epoch(now() + 3L * 86400L);
        Map<String, FertilizerApplicationSchedule> schedules = new HashMap<>();
        schedules.put("NUTRITION", schedule);
        zone.getFertilization().setApplication_schedules(schedules);

        FertilizerProduct organicProduct = readyProduct();
        organicProduct.setOrganic_farming_eligible(true);

        FertilizerAdvice advice = FertilizerDecisionEngine.advise(
                zone, Collections.singletonList(organicProduct), null,
                Collections.emptyList(), now(), true
        );

        assertEquals(FertilizerAdvice.STATUS_TOO_EARLY, advice.getStatus());
        assertTrue(advice.getReason().contains("bekleme aralığı"));
    }
    @Test
    public void futureSameDayApplicationIsNotReadyAtEvaluationTime() {
        long evaluation = java.time.LocalDateTime.of(2024, 1, 11, 10, 0)
                .atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
        GardenZone zone = zone("FRUITING", 60);
        zone.setUpdated_at_epoch(evaluation);
        zone.getFertilization().setPlanting_date("01-01-2024");
        FertilizerApplicationSchedule schedule = new FertilizerApplicationSchedule();
        schedule.setNext_application_at_epoch(evaluation + 3600L);
        Map<String, FertilizerApplicationSchedule> schedules = new HashMap<>();
        schedules.put("NUTRITION", schedule);
        zone.getFertilization().setApplication_schedules(schedules);

        FertilizerAdvice advice = FertilizerDecisionEngine.advise(
                zone, Collections.singletonList(readyProduct()), null, evaluation);

        assertEquals(FertilizerAdvice.STATUS_TOO_EARLY, advice.getStatus());
        assertTrue(advice.getContext().contains("11 günlük"));
        assertTrue(advice.getRecommendation().getWaitDays() > 0L);
    }

    @Test
    public void missingZoneAndCatalogDoNotCrashAdvisor() {
        assertEquals(FertilizerAdvice.STATUS_PLAN_NOT_READY,
                FertilizerDecisionEngine.advise(null, null, null, now()).getStatus());
        assertFalse(FertilizerDecisionEngine.advise(zone("FRUITING", 60), null, null, now())
                .getStatus().isEmpty());
    }
    private static GardenZone zone(String stage, int moisture) {
        FertilizationProfile profile = new FertilizationProfile();
        profile.setEnabled(true);
        profile.setGrowth_stage(stage);
        profile.setArea_m2(20.0);
        profile.setTank_liters(100.0);

        GardenZone zone = new GardenZone();
        zone.setName("Domates");
        zone.setEmoji("🍅");
        zone.setMoisture_limit(40);
        zone.setMoisture(moisture);
        zone.setUpdated_at_epoch(now());
        zone.setFertilization(profile);
        return zone;
    }

    private static FertilizerProduct harvestProduct() {
        FertilizerProduct product = new FertilizerProduct();
        product.setProduct_id("product-gubretas-searius");
        product.setName("GÜBRETAŞ SEARİUS");
        product.setNpk("Deniz yosunu · %1 K₂O");
        product.setApplication_type("BIOSTIMULANT");
        product.setDosage_unit("L/dekar · 1 ton su ile");
        product.setLabel_dosage_min(1.0);
        product.setLabel_dosage_max(1.0);
        product.setRecommended_stages(Collections.singletonList("HARVEST"));
        product.setStock_unit("ml");
        product.setStock_amount(1000.0);
        product.setOrganic_farming_eligible(true);
        product.setEnabled(true);
        return product;
    }

    private static FertilizerProduct readyProduct() {
        FertilizerProduct product = new FertilizerProduct();
        product.setProduct_id("fertilizer-001");
        product.setName("GÜBRETAŞ 10.5.40+ME");
        product.setNpk("10-5-40");
        product.setApplication_type("NUTRITION");
        product.setDosage_unit("kg/dekar · 1 ton su ile");
        product.setLabel_dosage_min(5.0);
        product.setLabel_dosage_max(5.0);
        product.setRecommended_stages(Arrays.asList("FLOWERING", "FRUITING"));
        product.setStock_unit("g");
        product.setStock_amount(500.0);
        product.setOrganic_farming_eligible(true);
        product.setEnabled(true);
        return product;
    }

    private static long now() {
        return Instant.now().getEpochSecond();
    }
}
