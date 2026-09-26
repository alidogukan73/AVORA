package com.alidogukan.avora.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class NasTwoWaySyncMergeTest {
    @Test
    public void mergesMissingZonesAndSeasonsWithoutOverwritingCurrentValues() {
        Map<String, Object> current = backup(map(
                "zones", map("zone-1", map("name", "Current")),
                "garden_journal", map("seasons", map(
                        "season-1", map("status", "ACTIVE")))));
        Map<String, Object> remote = backup(map(
                "zones", map(
                        "zone-1", map("name", "Old", "sensor_id", "soil-1"),
                        "zone-2", map("name", "Remote")),
                "garden_journal", map("seasons", map(
                        "season-2", map("status", "CLOSED")))));

        NasTwoWaySyncMerge.Result result = NasTwoWaySyncMerge.plan(current, remote);
        Map<String, Object> firebase = nested(result.mergedBackup, "firebase_data");
        Map<String, Object> zones = nested(firebase, "zones");
        assertEquals("Current", nested(zones, "zone-1").get("name"));
        assertEquals("soil-1", nested(zones, "zone-1").get("sensor_id"));
        assertTrue(zones.containsKey("zone-2"));
        Map<String, Object> seasons = nested(nested(firebase, "garden_journal"), "seasons");
        assertTrue(seasons.containsKey("season-1"));
        assertTrue(seasons.containsKey("season-2"));
        assertTrue(result.dataMissingOnCurrent > 0);
        assertTrue(result.dataMissingOnNas > 0);
    }

    @Test
    public void doesNotMutateEitherInputAndCountsArraysAsLeaves() {
        Map<String, Object> current = backup(map(
                "notifications", map("local", Arrays.asList("a", "b"))));
        Map<String, Object> remote = backup(map(
                "notifications", map("remote", Arrays.asList("c", "d", "e"))));

        NasTwoWaySyncMerge.Result result = NasTwoWaySyncMerge.plan(current, remote);

        assertEquals(3, result.dataMissingOnCurrent);
        assertEquals(2, result.dataMissingOnNas);
        assertFalse(nested(nested(current, "firebase_data"), "notifications")
                .containsKey("remote"));
        assertFalse(nested(nested(remote, "firebase_data"), "notifications")
                .containsKey("local"));
    }

    @Test
    public void emptyNasProducesUploadWithoutRemovingCurrentData() {
        Map<String, Object> current = backup(map(
                "zones", map("zone-1", map("name", "Garden"))));

        NasTwoWaySyncMerge.Result result = NasTwoWaySyncMerge.plan(current, null);

        assertEquals(0, result.dataMissingOnCurrent);
        assertTrue(result.dataMissingOnNas > 0);
        assertTrue(nested(nested(result.mergedBackup, "firebase_data"), "zones")
                .containsKey("zone-1"));
    }

    private static Map<String, Object> backup(Map<String, Object> firebase) {
        return map("schema", "avora-portable-backup",
                "schema_version", 1,
                "device_id", "avora-001",
                "firebase_data", firebase,
                "local_preferences", map());
    }

    private static Map<String, Object> map(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put((String) entries[index], entries[index + 1]);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> parent, String key) {
        return (Map<String, Object>) parent.get(key);
    }
}
