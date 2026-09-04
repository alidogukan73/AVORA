package com.alidogukan.avora.zones;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.alidogukan.avora.models.GardenZone;
import org.junit.Test;
import java.util.List;

public class ZoneHardwareSelectionTest {
    @Test
    public void initialEditUsesStoredHardware() {
        ZoneHardwareSelection draft = new ZoneHardwareSelection();
        draft.bindZone("zone-004", "soil-006", "valve-004");
        assertEquals("soil-006", draft.getSensorId());
        assertEquals("valve-004", draft.getValveId());
    }

    @Test
    public void sensorChangeSurvivesRepeatedLiveTelemetry() {
        ZoneHardwareSelection draft = original();
        draft.selectSensor("soil-006");
        for (int update = 0; update < 100; update++) {
            draft.bindZone("zone-004", "soil-004", "valve-004");
            assertEquals("soil-006", draft.getSensorId());
        }
        assertEquals("valve-004", draft.getValveId());
    }

    @Test
    public void valveChangeAlsoSurvivesLiveTelemetry() {
        ZoneHardwareSelection draft = original();
        draft.selectValve("valve-007");
        draft.bindZone("zone-004", "soil-004", "valve-004");
        assertEquals("soil-004", draft.getSensorId());
        assertEquals("valve-007", draft.getValveId());
    }

    @Test
    public void explicitUnassignedChoicesAreNotReplacedWithDefaults() {
        ZoneHardwareSelection draft = original();
        draft.selectSensor("");
        draft.selectValve("");
        draft.bindZone("zone-004", "soil-004", "valve-004");
        assertEquals("", draft.getSensorId());
        assertEquals("", draft.getValveId());
    }

    @Test
    public void changingCreateChannelInitializesNewHardwareDefaults() {
        ZoneHardwareSelection draft = original();
        draft.selectSensor("soil-006");
        draft.selectValve("valve-007");
        draft.bindZone("zone-005", "soil-005", "valve-005");
        assertEquals("soil-005", draft.getSensorId());
        assertEquals("valve-005", draft.getValveId());
    }

    @Test
    public void newZoneWithNoAvailableDefaultKeepsUsersLaterSelection() {
        ZoneHardwareSelection draft = new ZoneHardwareSelection();
        draft.bindZone("zone-004", "", "");
        draft.selectSensor("soil-006");
        draft.selectValve("valve-007");
        draft.bindZone("zone-004", "soil-004", "valve-004");
        assertEquals("soil-006", draft.getSensorId());
        assertEquals("valve-007", draft.getValveId());
    }

    @Test
    public void remoteReassignmentDoesNotSilentlyChangeTheUsersDraft() {
        ZoneHardwareSelection draft = original();
        draft.selectSensor("soil-006");
        draft.bindZone("zone-004", "soil-007", "valve-008");
        assertEquals("soil-006", draft.getSensorId());
        assertEquals("valve-004", draft.getValveId());
    }

    @Test
    public void conflictingChoiceRemainsVisibleButCannotBeSaved() {
        ZoneHardwareSelection draft = original();
        draft.selectSensor("soil-006");
        draft.bindZone("zone-004", "soil-004", "valve-004");
        GardenZone anotherZone = new GardenZone();
        anotherZone.setZone_id("zone-005");
        anotherZone.setSensor_id("soil-006");
        GardenZone candidate = new GardenZone();
        candidate.setZone_id("zone-004");
        candidate.setSensor_id(draft.getSensorId());
        candidate.setValve_id(draft.getValveId());

        assertEquals(ZoneCapacityPolicy.ERROR_SENSOR_IN_USE,
                assertThrows(IllegalArgumentException.class, () ->
                        ZoneCapacityPolicy.validateCandidate(
                                candidate, List.of(anotherZone))).getMessage());
        assertEquals("soil-006", draft.getSensorId());
    }

    @Test
    public void nullHardwareIsUnassignedAndIdsAreTrimmed() {
        ZoneHardwareSelection draft = new ZoneHardwareSelection();
        draft.bindZone(" zone-004 ", null, " valve-004 ");
        assertEquals("", draft.getSensorId());
        assertEquals("valve-004", draft.getValveId());
        draft.selectSensor(" soil-006 ");
        draft.selectValve(null);
        draft.bindZone("zone-004", "soil-004", "valve-004");
        assertEquals("soil-006", draft.getSensorId());
        assertEquals("", draft.getValveId());
    }

    private static ZoneHardwareSelection original() {
        ZoneHardwareSelection draft = new ZoneHardwareSelection();
        draft.bindZone("zone-004", "soil-004", "valve-004");
        return draft;
    }
}
