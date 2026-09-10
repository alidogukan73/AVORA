package com.alidogukan.avora;

import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.alidogukan.avora.seedling.SeedlingVarietyStore;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class SeedlingVarietyStoreTest {
    @Test public void addedVarietySurvivesStoreRecreation() {
        Context context = ApplicationProvider.getApplicationContext();
        String cropId = "test-crop-" + System.nanoTime();
        try {
            new SeedlingVarietyStore(context).add(cropId, "Anadolu F1");
            assertTrue(new SeedlingVarietyStore(context)
                    .load(cropId).contains("Anadolu F1"));
        } finally {
            context.getSharedPreferences(
                    SeedlingVarietyStore.PREFERENCES_NAME,
                    Context.MODE_PRIVATE).edit().remove(cropId).commit();
        }
    }
}
