package com.alidogukan.avora.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class BackupIntegrityTest {
    @Test
    public void objectKeyOrderDoesNotChangeDigest() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("zones", section("zone-001", 40));
        first.put("history", Arrays.asList("watering-001", "watering-002"));

        Map<String, Object> second = new LinkedHashMap<>();
        second.put("history", Arrays.asList("watering-001", "watering-002"));
        second.put("zones", section("zone-001", 40.0));

        assertEquals(BackupIntegrity.sha256(first), BackupIntegrity.sha256(second));
    }

    @Test
    public void missingOrChangedRecordFailsIntegrityCheck() {
        Map<String, Object> backup = new LinkedHashMap<>();
        backup.put("seasons", section("season-001", "ACTIVE"));
        backup.put("watering_history", Arrays.asList("watering-001", "watering-002"));
        String digest = BackupIntegrity.sha256(backup);

        assertTrue(BackupIntegrity.matches(digest, backup));
        backup.put("watering_history", Arrays.asList("watering-001"));
        assertFalse(BackupIntegrity.matches(digest, backup));
        assertFalse(BackupIntegrity.matches("not-a-digest", backup));
    }

    private static Map<String, Object> section(String key, Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(key, value);
        return result;
    }
}
