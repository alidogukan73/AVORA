package com.alidogukan.avora.firebase;

import static org.junit.Assert.*;

import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.zones.ZoneCapacityPolicy;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.InternalHelpers;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.snapshot.IndexedNode;
import com.google.firebase.database.snapshot.NodeUtilities;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Real in-memory Firebase snapshots; no Firebase app, network or live data needed. */
public class GardenZoneSaveTest {

    @Test
    public void runtimeOnlyRemainderIsNotAnOccupiedChannel() {
        DataSnapshot root = snapshot(Map.of("zone-003", runtimeData()));
        DataSnapshot remainder = root.child("zone-003");
        // This default was the cause: raw deserialization silently enables a deleted zone.
        assertTrue(remainder.getValue(GardenZone.class).isEnabled());
        assertNull(FirebaseRepository.configuredZoneFromSnapshot(remainder));
        assertNull(FirebaseRepository.validateGardenZoneSave(root, candidate(3), true));
    }

    @Test
    public void absentChannelIsAvailable() {
        DataSnapshot root = snapshot(Map.of());
        assertNull(FirebaseRepository.configuredZoneFromSnapshot(root.child("zone-003")));
        assertNull(FirebaseRepository.validateGardenZoneSave(root, candidate(3), true));
    }

    @Test
    public void createDeleteLateTelemetryAndRecreateCanRepeat() {
        Map<String, Object> database = new HashMap<>();
        database.put("zone-001", configured(1));
        for (int attempt = 0; attempt < 3; attempt++) {
            assertNull(FirebaseRepository.validateGardenZoneSave(
                    snapshot(database), candidate(3), true));
            database.put("zone-003", configured(3));
            assertEquals(ZoneCapacityPolicy.ERROR_ZONE_IN_USE,
                    assertThrows(IllegalStateException.class, () ->
                            FirebaseRepository.validateGardenZoneSave(
                                    snapshot(database), candidate(3), true)).getMessage());
            database.remove("zone-003");
            // A late status publication must not bring the removed configuration back.
            database.put("zone-003", runtimeData());
            assertNull(FirebaseRepository.configuredZoneFromSnapshot(
                    snapshot(database).child("zone-003")));
        }
        assertEquals(configured(1), database.get("zone-001"));
    }

    @Test
    public void activeAndHardwarePendingZonesStillBlockCreation() {
        for (String lifecycle : List.of("ACTIVE", "HARDWARE_PENDING")) {
            Map<String, Object> zone = configured(3);
            zone.put("lifecycle_status", lifecycle);
            DataSnapshot root = snapshot(Map.of("zone-003", zone));
            assertNotNull(FirebaseRepository.configuredZoneFromSnapshot(root.child("zone-003")));
            assertEquals(ZoneCapacityPolicy.ERROR_ZONE_IN_USE,
                    assertThrows(IllegalStateException.class, () ->
                            FirebaseRepository.validateGardenZoneSave(
                                    root, candidate(3), true)).getMessage());
        }
    }

    @Test
    public void archivedChannelCanBeReusedWithoutModifyingItsSnapshot() {
        Map<String, Object> archived = configured(3);
        archived.put("enabled", false);
        archived.put("lifecycle_status", "INACTIVE");
        archived.put("area_id", "old-area");
        archived.put("season", Map.of("status", "CLOSED", "active_season_id", ""));
        DataSnapshot root = snapshot(Map.of("zone-003", archived));
        GardenZone stored = FirebaseRepository.validateGardenZoneSave(root, candidate(3), true);
        assertNotNull(stored);
        assertTrue(ZoneCapacityPolicy.isInactive(stored));
        assertEquals("old-area", stored.getArea_id());
        assertEquals(archived, root.child("zone-003").getValue());
    }

    @Test
    public void legacyAndHardwarelessConfiguredZonesAreNotMistakenForResidue() {
        for (Map<String, Object> minimal : List.<Map<String, Object>>of(
                Map.of("name", "Domates"),
                Map.of("area_name", "3. Bölge"),
                Map.of("enabled", true),
                Map.of("lifecycle_status", "HARDWARE_PENDING"),
                Map.of("sensor_id", "soil-003"),
                Map.of("order", 3))) {
            DataSnapshot root = snapshot(Map.of("zone-003", minimal));
            GardenZone stored = FirebaseRepository.configuredZoneFromSnapshot(root.child("zone-003"));
            assertNotNull(stored);
            assertEquals("zone-003", stored.getZone_id());
            assertEquals(ZoneCapacityPolicy.ERROR_ZONE_IN_USE,
                    assertThrows(IllegalStateException.class, () ->
                            FirebaseRepository.validateGardenZoneSave(
                                    root, candidate(3), true)).getMessage());
        }
    }

    @Test
    public void reusedChannelStillRejectsHardwareAssignedToAnotherActiveZone() {
        DataSnapshot root = snapshot(Map.of(
                "zone-001", configured(1), "zone-003", runtimeData()));
        GardenZone sensorConflict = candidate(3);
        sensorConflict.setSensor_id("soil-001");
        assertEquals(ZoneCapacityPolicy.ERROR_SENSOR_IN_USE,
                assertThrows(IllegalArgumentException.class, () ->
                        FirebaseRepository.validateGardenZoneSave(
                                root, sensorConflict, true)).getMessage());
        GardenZone valveConflict = candidate(3);
        valveConflict.setValve_id("valve-001");
        assertEquals(ZoneCapacityPolicy.ERROR_VALVE_IN_USE,
                assertThrows(IllegalArgumentException.class, () ->
                        FirebaseRepository.validateGardenZoneSave(
                                root, valveConflict, true)).getMessage());
    }

    @Test
    public void editingExistingZoneRemainsAllowed() {
        DataSnapshot root = snapshot(Map.of("zone-003", configured(3)));
        GardenZone stored = FirebaseRepository.validateGardenZoneSave(root, candidate(3), false);
        assertEquals("zone-003", stored.getZone_id());
    }

    @Test
    public void invalidCandidateIsRejectedBeforeLookingUpSnapshotPath() {
        assertEquals(ZoneCapacityPolicy.ERROR_INVALID_ZONE,
                assertThrows(IllegalArgumentException.class, () ->
                        FirebaseRepository.validateGardenZoneSave(
                                snapshot(Map.of()), new GardenZone(), true)).getMessage());
    }

    private static Map<String, Object> runtimeData() {
        return Map.of(
                "moisture", 66L,
                "updated_at_epoch", 12345L,
                "irrigation_status", Map.of("watering_active", false, "queue_position", 0L),
                "ai", Map.of("season_status", "CLOSED"));
    }

    private static Map<String, Object> configured(int slot) {
        Map<String, Object> result = new HashMap<>();
        result.put("zone_id", ZoneCapacityPolicy.zoneId(slot));
        result.put("enabled", true);
        result.put("lifecycle_status", "ACTIVE");
        result.put("sensor_id", ZoneCapacityPolicy.sensorId(slot));
        result.put("valve_id", ZoneCapacityPolicy.valveId(slot));
        return result;
    }

    private static GardenZone candidate(int slot) {
        GardenZone zone = new GardenZone();
        zone.setZone_id(ZoneCapacityPolicy.zoneId(slot));
        zone.setSensor_id(ZoneCapacityPolicy.sensorId(slot));
        zone.setValve_id(ZoneCapacityPolicy.valveId(slot));
        zone.setArea_id("new-area");
        return zone;
    }

    private static DataSnapshot snapshot(Map<String, Object> zones) {
        return InternalHelpers.createDataSnapshot(
                InternalHelpers.createReference(null, new Path("devices/avora-001/zones")),
                IndexedNode.from(NodeUtilities.NodeFromJSON(zones)));
    }
}
