package com.alidogukan.avora.models;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HealthAds1115Test {

    @Test
    public void keepsAdsModulesIndependent() {
        Health health = new Health();
        health.setEsp32NodeOnline(true);
        health.setAds1115PrimaryAvailable(false);
        health.setAds1115SecondaryAvailable(true);
        health.setAds1115StatusUpdatedAtEpoch(1234L);
        health.setAds1115StatusFirmware("2.3.0");

        assertTrue(health.isEsp32NodeOnline());
        assertFalse(health.isAds1115PrimaryAvailable());
        assertTrue(health.isAds1115SecondaryAvailable());
        assertEquals(1234L, health.getAds1115StatusUpdatedAtEpoch());
        assertEquals("2.3.0", health.getAds1115StatusFirmware());
    }
}
