package com.alidogukan.avora.nas;

import org.junit.Test;
import static org.junit.Assert.*;

public class NasFirebaseSessionManagerTest {
    @Test public void reinstallUsesSameServerIdentity() {
        String id = "b05cbe3d-b3d5-47ee-90d3-d9b413555633";
        assertEquals("avora_nas_b05cbe3db3d547ee90d3d9b413555633", NasFirebaseSessionManager.ownerUid(id));
        assertEquals(NasFirebaseSessionManager.ownerUid(id), NasFirebaseSessionManager.ownerUid(id.toUpperCase()));
        assertNotEquals(NasFirebaseSessionManager.ownerUid(id),
                NasFirebaseSessionManager.ownerUid("d05cbe3d-b3d5-47ee-90d3-d9b413555633"));
    }
    @Test public void unavailableIdentityHasActionableError() {
        assertEquals("NAS_FIREBASE_IDENTITY_UNAVAILABLE",
                NasAuthClient.mapErrorCode(503, "firebase_identity_unavailable", true));
    }
}
