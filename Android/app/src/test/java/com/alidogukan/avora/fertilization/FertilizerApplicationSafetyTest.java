package com.alidogukan.avora.fertilization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.alidogukan.avora.models.FertilizationProfile;
import com.alidogukan.avora.models.FertilizerApplicationSchedule;
import com.alidogukan.avora.models.FertilizerProduct;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class FertilizerApplicationSafetyTest {

    @Test
    public void nonFiniteDoseAndStockAreNeverReady() {
        FertilizationProfile profile = profile(20.0, 100.0, "FRUITING");
        assertFalse(FertilizerApplicationSafety.calculateDose(
                profile, Double.NaN, 5.0, "kg/dekar").isSupported());
        assertFalse(FertilizerApplicationSafety.calculateDose(
                profile, Double.POSITIVE_INFINITY, 5.0, "kg/dekar").isSupported());
        FertilizerProduct product = product("kg/dekar", 5.0);
        product.setStock_amount(Double.POSITIVE_INFINITY);
        assertFalse(FertilizerApplicationSafety.hasEnoughStock(product, 100.0));
        product.setStock_amount(200.0);
        assertFalse(FertilizerApplicationSafety.hasEnoughStock(product, Double.NaN));
    }
    @Test
    public void kilogramPerDecareIsConvertedToGramsForZoneArea() {
        FertilizerProduct product = product("kg/dekar · 1 ton su ile", 5.0);
        FertilizationProfile profile = profile(20.0, 100.0, "FRUITING");

        FertilizerApplicationSafety.Dose dose =
                FertilizerApplicationSafety.calculateDose(product, profile);

        assertTrue(dose.isSupported());
        assertEquals("g", dose.getUnit());
        assertEquals(100.0, dose.getAmount(), 0.0001);
        assertEquals(100.0, dose.getMinAmount(), 0.0001);
        assertEquals(100.0, dose.getMaxAmount(), 0.0001);
        assertFalse(dose.isTankBased());
    }

    @Test
    public void milliliterPerHundredLitersUsesTankVolume() {
        FertilizerProduct product = product("ml / 100 L su · yapraktan", 250.0);
        FertilizationProfile profile = profile(20.0, 60.0, "VEGETATIVE");

        FertilizerApplicationSafety.Dose dose =
                FertilizerApplicationSafety.calculateDose(product, profile);

        assertEquals("ml", dose.getUnit());
        assertEquals(150.0, dose.getAmount(), 0.0001);
        assertTrue(dose.isTankBased());
    }

    @Test
    public void labelRangeIsPreservedForZoneArea() {
        FertilizerProduct product = product("kg/dekar", 5.0);
        product.setLabel_dosage_max(6.0);

        FertilizerApplicationSafety.Dose dose =
                FertilizerApplicationSafety.calculateDose(
                        product, profile(20.0, 100.0, "FRUITING"));

        assertEquals(100.0, dose.getMinAmount(), 0.0001);
        assertEquals(120.0, dose.getMaxAmount(), 0.0001);
        assertEquals("g", dose.getUnit());
    }

    @Test
    public void recommendationDoseUsesSameCentralConversion() {
        FertilizerApplicationSafety.Dose dose =
                FertilizerApplicationSafety.calculateDose(
                        profile(20.0, 60.0, "VEGETATIVE"),
                        100.0, 150.0, "g / 100 L su");

        assertEquals(60.0, dose.getMinAmount(), 0.0001);
        assertEquals(90.0, dose.getMaxAmount(), 0.0001);
        assertEquals("g", dose.getUnit());
        assertTrue(dose.isTankBased());
    }

    @Test
    public void restrictedProductRejectsDifferentGrowthStage() {
        FertilizerProduct product = product("kg/dekar", 3.0);
        product.setRecommended_stages(Arrays.asList("FLOWERING", "FRUITING"));

        assertTrue(FertilizerApplicationSafety.isStageCompatible(
                product, profile(100.0, 100.0, "FRUITING")
        ));
        assertFalse(FertilizerApplicationSafety.isStageCompatible(
                product, profile(100.0, 100.0, "ROOTING")
        ));
    }

    @Test
    public void productWithoutApplicationPeriodIsNotTreatedAsUniversal() {
        FertilizerProduct product = product("kg/dekar", 3.0);

        assertFalse(FertilizerApplicationSafety.isStageCompatible(
                product, profile(100.0, 100.0, "FRUITING")
        ));
    }

    @Test
    public void ogTorosOrganicSolidIsOnlyCompatibleWithSoilPreparation() {
        FertilizerProduct product = product("kg/dekar", 125.0);
        product.setName("OG Toros Organik Katı Gübre");

        assertTrue(FertilizerApplicationSafety.isStageCompatible(
                product, profile(100.0, 100.0, "SOIL_PREPARATION")
        ));
        assertFalse(FertilizerApplicationSafety.isStageCompatible(
                product, profile(100.0, 100.0, "FRUITING")
        ));
        assertFalse(FertilizerApplicationSafety.isStageCompatible(
                product, profile(100.0, 100.0, "HARVEST")
        ));
    }
    @Test
    public void activeHarvestProductIsAllowedWhenPeriodIsExplicit() {
        FertilizerProduct product = product("L/dekar", 1.0);
        product.setRecommended_stages(Collections.singletonList("HARVEST"));

        assertTrue(FertilizerApplicationSafety.isStageCompatible(
                product, profile(100.0, 100.0, "HARVEST")
        ));
    }

    @Test
    public void seasonEndRejectsProductEvenWhenHarvestIsConfigured() {
        FertilizerProduct product = product("L/dekar", 1.0);
        product.setRecommended_stages(Collections.singletonList("HARVEST"));

        assertFalse(FertilizerApplicationSafety.isStageCompatible(
                product, profile(100.0, 100.0, "SEASON_END")
        ));
    }

    @Test
    public void repeatIntervalIsReadPerApplicationType() {
        FertilizationProfile profile = profile(100.0, 100.0, "FRUITING");
        FertilizerApplicationSchedule schedule = new FertilizerApplicationSchedule();
        schedule.setNext_application_at_epoch(2_000L);
        Map<String, FertilizerApplicationSchedule> schedules = new HashMap<>();
        schedules.put("ORGANIC", schedule);
        profile.setApplication_schedules(schedules);

        assertTrue(FertilizerApplicationSafety.isRepeatIntervalBlocked(
                profile, "ORGANIC", 1_999L
        ));
        assertFalse(FertilizerApplicationSafety.isRepeatIntervalBlocked(
                profile, "ORGANIC", 2_000L
        ));
    }

    @Test
    public void stockRequiresMatchingUnitAndEnoughAmount() {
        FertilizerProduct product = product("kg/dekar", 3.0);
        product.setStock_unit("g");
        product.setStock_amount(500.0);

        assertTrue(FertilizerApplicationSafety.isStockUnitCompatible(product, "g"));
        assertFalse(FertilizerApplicationSafety.isStockUnitCompatible(product, "ml"));
        assertTrue(FertilizerApplicationSafety.hasEnoughStock(product, 500.0));
        assertFalse(FertilizerApplicationSafety.hasEnoughStock(product, 500.1));
    }

    @Test
    public void unsupportedUnitDoesNotProduceAnAmount() {
        FertilizerProduct product = product("g / bitki", 10.0);
        FertilizerApplicationSafety.Dose dose =
                FertilizerApplicationSafety.calculateDose(
                        product,
                        profile(20.0, 100.0, "FRUITING")
                );

        assertFalse(dose.isSupported());
    }

    private static FertilizerProduct product(String unit, double dose) {
        FertilizerProduct product = new FertilizerProduct();
        product.setDosage_unit(unit);
        product.setLabel_dosage_min(dose);
        product.setEnabled(true);
        return product;
    }

    private static FertilizationProfile profile(
            double area,
            double tank,
            String stage
    ) {
        FertilizationProfile profile = new FertilizationProfile();
        profile.setEnabled(true);
        profile.setArea_m2(area);
        profile.setTank_liters(tank);
        profile.setGrowth_stage(stage);
        return profile;
    }
}
