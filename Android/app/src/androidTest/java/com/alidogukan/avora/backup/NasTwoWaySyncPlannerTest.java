package com.alidogukan.avora.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public final class NasTwoWaySyncPlannerTest {
    @Test public void mergesOnlyMissingValuesInBothDirections() throws Exception {
        JSONObject current = backup("{\"zones\":{\"zone-1\":{\"name\":\"Current\"}},"
                + "\"watering_history\":{\"local\":{\"duration\":10}}}");
        JSONObject remote = backup("{\"zones\":{\"zone-1\":{\"name\":\"Old\","
                + "\"sensor_id\":\"soil-1\"},\"zone-2\":{\"name\":\"Remote\"}},"
                + "\"watering_history\":{\"remote\":{\"duration\":20}}}");

        NasTwoWaySyncPlanner.Plan plan = NasTwoWaySyncPlanner.plan(current, remote);
        JSONObject firebase = plan.mergedBackup.getJSONObject("firebase_data");
        assertEquals("Current", firebase.getJSONObject("zones")
                .getJSONObject("zone-1").getString("name"));
        assertEquals("soil-1", firebase.getJSONObject("zones")
                .getJSONObject("zone-1").getString("sensor_id"));
        assertTrue(firebase.getJSONObject("zones").has("zone-2"));
        assertTrue(firebase.getJSONObject("watering_history").has("local"));
        assertTrue(firebase.getJSONObject("watering_history").has("remote"));
        assertTrue(plan.dataMissingOnCurrent > 0);
        assertTrue(plan.dataMissingOnNas > 0);
    }

    @Test public void neverCopiesRemoteConflictOverCurrentValue() throws Exception {
        JSONObject current = backup("{\"profile\":{\"garden_name\":\"My Garden\"}}");
        JSONObject remote = backup("{\"profile\":{\"garden_name\":\"Old Name\"}}");
        NasTwoWaySyncPlanner.Plan plan = NasTwoWaySyncPlanner.plan(current, remote);
        assertEquals("My Garden", plan.mergedBackup.getJSONObject("firebase_data")
                .getJSONObject("profile").getString("garden_name"));
        assertEquals(0, plan.dataMissingOnCurrent);
        assertEquals(0, plan.dataMissingOnNas);
    }

    @Test public void emptyNasProducesUploadWithoutRemovingCurrentData() throws Exception {
        JSONObject current = backup("{\"zones\":{\"zone-1\":{\"name\":\"Garden\"}}}");
        NasTwoWaySyncPlanner.Plan plan = NasTwoWaySyncPlanner.plan(current, null);
        assertEquals(0, plan.dataMissingOnCurrent);
        assertTrue(plan.dataMissingOnNas > 0);
        assertFalse(plan.mergedBackup.getJSONObject("firebase_data")
                .getJSONObject("zones").isNull("zone-1"));
    }

    private static JSONObject backup(String firebaseJson) throws Exception {
        return new JSONObject().put("schema", "avora-portable-backup")
                .put("schema_version", 1)
                .put("created_at_epoch_ms", 1L)
                .put("firebase_data", new JSONObject(firebaseJson))
                .put("local_preferences", new JSONObject());
    }
}
