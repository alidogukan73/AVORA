package com.alidogukan.avora.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Map;

public final class NasTwoWaySyncPlannerTest {
    @Test
    public void mergesOnlyMissingValuesAndPreservesCurrentConflicts() throws Exception {
        JSONObject current = backup("{\"zones\":{\"zone-1\":{\"name\":\"Current\"}},"
                + "\"garden_journal\":{\"seasons\":{\"season-1\":{\"status\":\"ACTIVE\"}}}}", 1L);
        JSONObject remote = backup("{\"zones\":{\"zone-1\":{\"name\":\"Old\","
                + "\"sensor_id\":\"soil-1\"},\"zone-2\":{\"name\":\"Remote\"}},"
                + "\"garden_journal\":{\"seasons\":{\"season-2\":{\"status\":\"CLOSED\"}}}}", 2L);

        NasTwoWaySyncPlanner.Plan plan = NasTwoWaySyncPlanner.plan(current, remote);
        JSONObject firebase = plan.mergedBackup.getJSONObject("firebase_data");
        assertEquals("Current", firebase.getJSONObject("zones")
                .getJSONObject("zone-1").getString("name"));
        assertEquals("soil-1", firebase.getJSONObject("zones")
                .getJSONObject("zone-1").getString("sensor_id"));
        assertTrue(firebase.getJSONObject("zones").has("zone-2"));
        JSONObject seasons = firebase.getJSONObject("garden_journal")
                .getJSONObject("seasons");
        assertTrue(seasons.has("season-1"));
        assertTrue(seasons.has("season-2"));
        assertTrue(plan.dataMissingOnCurrent > 0);
        assertTrue(plan.dataMissingOnNas > 0);
    }

    @Test
    public void mergedBackupIsResignedAndTamperingBreaksItsDigest() throws Exception {
        JSONObject current = backup("{\"zones\":{\"zone-1\":{\"name\":\"Garden\"}}}", 1L);
        JSONObject remote = backup("{\"watering_history\":{\"remote\":{\"duration\":20}}}", 2L);

        JSONObject merged = NasTwoWaySyncPlanner.plan(current, remote).mergedBackup;
        JSONObject integrity = merged.getJSONObject("integrity");
        JSONObject unsigned = new JSONObject(merged.toString());
        unsigned.remove("integrity");
        Map<String, Object> payload = AvoraBackupManager.objectMap(unsigned);
        assertEquals(BackupIntegrity.ALGORITHM, integrity.getString("algorithm"));
        assertTrue(BackupIntegrity.matches(integrity.getString("content_sha256"), payload));

        payload.put("device_id", "tampered-device");
        assertFalse(BackupIntegrity.matches(integrity.getString("content_sha256"), payload));
    }

    @Test
    public void emptyNasProducesUploadWithoutRemovingCurrentData() throws Exception {
        JSONObject current = backup("{\"zones\":{\"zone-1\":{\"name\":\"Garden\"}}}", 1L);

        NasTwoWaySyncPlanner.Plan plan = NasTwoWaySyncPlanner.plan(current, null);

        assertEquals(0, plan.dataMissingOnCurrent);
        assertTrue(plan.dataMissingOnNas > 0);
        assertFalse(plan.mergedBackup.getJSONObject("firebase_data")
                .getJSONObject("zones").isNull("zone-1"));
    }

    private static JSONObject backup(String firebaseJson, long createdAt) throws Exception {
        JSONObject value = new JSONObject()
                .put("schema", "avora-portable-backup")
                .put("schema_version", 1)
                .put("created_at_epoch_ms", createdAt)
                .put("app_version", "3.7-test")
                .put("device_id", "avora-001")
                .put("firebase_data", new JSONObject(firebaseJson))
                .put("local_preferences", new JSONObject());
        AvoraBackupManager.refreshIntegrity(value);
        return value;
    }
}
