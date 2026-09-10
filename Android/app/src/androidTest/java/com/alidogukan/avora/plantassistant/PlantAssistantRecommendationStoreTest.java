package com.alidogukan.avora.plantassistant;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class PlantAssistantRecommendationStoreTest {
    private static final String CURRENT = "plant_assistant_recommendation";
    private static final String PREVIOUS = "garden_assistant_recommendation";
    private static final String LEGACY = "plant_doctor_recommendation";
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        clear();
    }

    @After
    public void tearDown() {
        clear();
    }

    @Test
    public void findingsFromTwoZonesAreStoredIndependently() {
        PlantAssistantRecommendationStore.save(
                context, "zone-a", "season-a", "Yüksek", "A", "Takip A");
        PlantAssistantRecommendationStore.save(
                context, "zone-b", "season-b", "Düşük", "B", "Takip B");

        List<PlantAssistantHealthSignal> values =
                PlantAssistantRecommendationStore.healthSignals(context);
        assertEquals(2, values.size());
        assertEquals("Yüksek", find(values, "zone-a", "season-a").getUrgency());
        assertEquals("Düşük", find(values, "zone-b", "season-b").getUrgency());
    }

    @Test
    public void newerFindingOverwritesOnlyTheSameZoneAndSeason() {
        PlantAssistantRecommendationStore.save(
                context, "zone-a", "season-a", "Yüksek", "Eski", "Takip");
        PlantAssistantRecommendationStore.save(
                context, "zone-a", "season-a", "Düşük", "Yeni", "Normal");

        List<PlantAssistantHealthSignal> values =
                PlantAssistantRecommendationStore.healthSignals(context);
        assertEquals(1, values.size());
        assertEquals("Düşük", values.get(0).getUrgency());
        assertEquals("Yeni", values.get(0).getTitle());
    }

    @Test
    public void firstMultiScopeSavePreservesDifferentLegacyZone() {
        seedLegacy("zone-a", "", "Yüksek", "Eski A");

        PlantAssistantRecommendationStore.save(
                context, "zone-b", "season-b", "Düşük", "Yeni B", "Normal");

        List<PlantAssistantHealthSignal> values =
                PlantAssistantRecommendationStore.healthSignals(context);
        assertEquals(2, values.size());
        assertEquals("Yüksek", find(values, "zone-a", "").getUrgency());
        assertEquals("Düşük", find(values, "zone-b", "season-b").getUrgency());
    }

    @Test
    public void scopedFindingReplacesLegacyFindingForSameZone() {
        seedLegacy("zone-a", "", "Yüksek", "Eski ürün");

        PlantAssistantRecommendationStore.save(
                context, "zone-a", "season-a", "Düşük", "Yeni ürün", "Normal");

        List<PlantAssistantHealthSignal> values =
                PlantAssistantRecommendationStore.healthSignals(context);
        assertEquals(1, values.size());
        assertEquals("Düşük", find(values, "zone-a", "season-a").getUrgency());
    }

    private void seedLegacy(String zoneId, String seasonId,
                            String urgency, String title) {
        context.getSharedPreferences(CURRENT, Context.MODE_PRIVATE).edit()
                .putString("zone_id", zoneId)
                .putString("season_id", seasonId)
                .putString("urgency", urgency)
                .putString("title", title)
                .putString("advice", "Takip")
                .putLong("created_at", System.currentTimeMillis() / 1000L)
                .commit();
    }

    private static PlantAssistantHealthSignal find(
            List<PlantAssistantHealthSignal> values,
            String zoneId,
            String seasonId
    ) {
        for (PlantAssistantHealthSignal value : values) {
            if (zoneId.equals(value.getZoneId())
                    && seasonId.equals(value.getSeasonId())) return value;
        }
        throw new AssertionError("Signal not found: " + zoneId + "/" + seasonId);
    }

    private void clear() {
        context.getSharedPreferences(CURRENT, Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences(PREVIOUS, Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences(LEGACY, Context.MODE_PRIVATE).edit().clear().commit();
    }
}
