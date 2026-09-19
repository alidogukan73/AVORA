package com.alidogukan.avora.device;

import com.alidogukan.avora.models.Status;
import org.junit.Test;
import static org.junit.Assert.*;

public class DeviceRestartPolicyTest {
    private Status idle() {
        Status status = new Status();
        status.setOnline(true);
        status.setLastSeenEpoch(1000);
        return status;
    }

    @Test public void idleOnlineDeviceCanRestart() {
        assertNull(DeviceRestartPolicy.failure(idle(), false, false, false, 1000));
    }

    @Test public void missingOfflineAndStaleStatusFailClosed() {
        assertEquals(DeviceRestartPolicy.STATUS_UNAVAILABLE,
                DeviceRestartPolicy.failure(null, false, false, false, 1000));
        Status status = idle();
        status.setOnline(false);
        assertNotNull(DeviceRestartPolicy.failure(status, false, false, false, 1000));
        assertNotNull(DeviceRestartPolicy.failure(idle(), false, false, false, 1091));
        assertNotNull(DeviceRestartPolicy.failure(idle(), false, false, false, 989));
    }

    @Test public void pumpValveAndWateringStateBlockRestart() {
        Status status = idle();
        status.setRelay(true);
        assertEquals(DeviceRestartPolicy.WATERING_ACTIVE,
                DeviceRestartPolicy.failure(status, false, false, false, 1000));
        status.setRelay(false);
        status.setValveOpen(true);
        assertNotNull(DeviceRestartPolicy.failure(status, false, false, false, 1000));
        status.setValveOpen(false);
        status.setWateringState("WATERING");
        assertNotNull(DeviceRestartPolicy.failure(status, false, false, false, 1000));
    }

    @Test public void hardwareAndPendingWateringAlsoBlockRestart() {
        assertEquals(DeviceRestartPolicy.WATERING_ACTIVE,
                DeviceRestartPolicy.failure(idle(), true, false, false, 1000));
        assertEquals(DeviceRestartPolicy.WATERING_ACTIVE,
                DeviceRestartPolicy.failure(idle(), false, true, false, 1000));
    }

    @Test public void duplicateRestartIsRejected() {
        assertEquals(DeviceRestartPolicy.ALREADY_PENDING,
                DeviceRestartPolicy.failure(idle(), false, false, true, 1000));
    }

    @Test public void cooldownAndIdleManualModeDoNotBlockMaintenance() {
        Status status = idle();
        status.setWateringState("COOLDOWN");
        assertNull(DeviceRestartPolicy.failure(status, false, false, false, 1000));
        status.setWateringState("MANUAL");
        assertNull(DeviceRestartPolicy.failure(status, false, false, false, 1000));
    }
}
