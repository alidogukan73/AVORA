package com.alidogukan.avora.plantassistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.alidogukan.avora.models.GardenZone;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class PlantAssistantAdvisorTest {

    @Test
    public void missingSensorDataIsNotTreatedAsVeryDry() {
        GardenZone zone = zoneWithMoisture(0, 40, 0L);

        PlantAssistantResult result = PlantAssistantAdvisor.assess(
                zone, Collections.singletonList("Alt yapraklarda sararma"),
                "", null, false, false);

        assertEquals("Besin eksikliği veya doğal yaşlanma ihtimali", result.getTitle());
        assertTrue(result.getContext().contains("sensör verisi bekleniyor"));
    }

    @Test
    public void staleSensorDataIsNotTreatedAsVeryDry() {
        long staleAt = System.currentTimeMillis() / 1000L - 16L * 60L;
        GardenZone zone = zoneWithMoisture(0, 40, staleAt);

        PlantAssistantResult result = PlantAssistantAdvisor.assess(
                zone, Collections.singletonList("Alt yapraklarda sararma"),
                "", null, false, false);

        assertEquals("Besin eksikliği veya doğal yaşlanma ihtimali", result.getTitle());
        assertTrue(result.getContext().contains("sensör verisi güncel değil"));
    }

    @Test
    public void englishLocalizedSymptomsReachSpecificScreeningRule() {
        GardenZone zone = zoneWithMoisture(
                50, 40, System.currentTimeMillis() / 1000L);

        PlantAssistantResult result = PlantAssistantAdvisor.assess(
                zone, Arrays.asList("Leaf spots / scorching", "Wilting"),
                "", null, false, false);

        assertEquals("Yayılım gösteren yaprak sorunu ihtimali", result.getTitle());
        assertEquals("Yüksek", result.getUrgency());
    }

    private static GardenZone zoneWithMoisture(int moisture, int limit, long updatedAt) {
        GardenZone zone = new GardenZone();
        zone.setMoisture(moisture);
        zone.setMoisture_limit(limit);
        zone.setUpdated_at_epoch(updatedAt);
        return zone;
    }
}
