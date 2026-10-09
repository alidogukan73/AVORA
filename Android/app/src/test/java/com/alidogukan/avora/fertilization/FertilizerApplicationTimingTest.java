package com.alidogukan.avora.fertilization;

import static org.junit.Assert.*;
import com.alidogukan.avora.models.FertilizationProfile;
import com.alidogukan.avora.models.FertilizerApplicationSchedule;
import java.util.Map;
import org.junit.Test;

public class FertilizerApplicationTimingTest {
    @Test public void conditionerReceiptIsVisibleWithoutInventingNutritionApplication() {
        FertilizationProfile profile = new FertilizationProfile();
        FertilizerApplicationSchedule conditioner = new FertilizerApplicationSchedule();
        conditioner.setProduct_id("conditioner");
        conditioner.setLast_application_at_epoch(1000);
        conditioner.setNext_application_at_epoch(2000);
        profile.setApplication_schedules(Map.of("CONDITIONER", conditioner));
        assertEquals(1000, FertilizerApplicationTiming.schedule(profile, "CONDITIONER").getLast_application_at_epoch());
        assertEquals(2000, FertilizerApplicationTiming.schedule(profile, "CONDITIONER").getNext_application_at_epoch());
        assertEquals(0, FertilizerApplicationTiming.schedule(profile, "NUTRITION").getLast_application_at_epoch());
        assertTrue(FertilizerApplicationTiming.alreadyRecorded(profile, "CONDITIONER", "conditioner", 1000));
        assertFalse(FertilizerApplicationTiming.alreadyRecorded(profile, "CONDITIONER", "other", 1000));
        assertFalse(FertilizerApplicationTiming.alreadyRecorded(profile, "CONDITIONER", "conditioner", 1100));
        assertFalse(FertilizerApplicationTiming.alreadyRecorded(profile, "NUTRITION", "conditioner", 1000));
    }

    @Test public void legacyNutritionDatesNeverLeakIntoOtherTypes() {
        FertilizationProfile profile = new FertilizationProfile();
        profile.setLast_application_at_epoch(1000);
        profile.setNext_application_at_epoch(2000);
        assertEquals(1000, FertilizerApplicationTiming.schedule(profile, "NUTRITION").getLast_application_at_epoch());
        assertEquals(0, FertilizerApplicationTiming.schedule(profile, "CONDITIONER").getLast_application_at_epoch());
        assertEquals(0, FertilizerApplicationTiming.schedule(null, "ORGANIC").getLast_application_at_epoch());
    }

    @Test public void healthUsesRecordedPlannedProductButNotAnotherProductsRecord() {
        FertilizationProfile profile = new FertilizationProfile();
        profile.setActive_product_id("conditioner");
        profile.setNext_application_at_epoch(1000);
        FertilizerApplicationSchedule conditioner = new FertilizerApplicationSchedule();
        conditioner.setProduct_id("conditioner");
        conditioner.setLast_application_at_epoch(1500);
        conditioner.setNext_application_at_epoch(3000);
        profile.setApplication_schedules(Map.of("CONDITIONER", conditioner));
        assertEquals(3000, FertilizerApplicationTiming.plannedNextApplication(profile));
        profile.setActive_product_id("nutrition");
        assertEquals(1000, FertilizerApplicationTiming.plannedNextApplication(profile));
        conditioner.setNext_application_at_epoch(0);
        profile.setActive_product_id("conditioner");
        assertEquals(0, FertilizerApplicationTiming.plannedNextApplication(profile));
    }
}
