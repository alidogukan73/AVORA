package com.alidogukan.avora.nas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class NasDocumentClientTest {
    @Test
    public void backupKeyUsesDeviceIdentity() {
        assertEquals("backup-avora-001", NasDocumentClient.backupKey("avora-001"));
    }

    @Test
    public void backupKeyRejectsPathCharacters() {
        assertThrows(IllegalArgumentException.class,
                () -> NasDocumentClient.backupKey("../another-user"));
    }

    @Test
    public void backupKeyRejectsOverlongIdentity() {
        StringBuilder identity = new StringBuilder();
        for (int index = 0; index < 80; index++) identity.append('a');
        assertThrows(IllegalArgumentException.class,
                () -> NasDocumentClient.backupKey(identity.toString()));
    }

    @Test
    public void serviceEndpointRemainsHttps() {
        assertEquals(true, NasApiClient.BASE_URL.startsWith("https://"));
    }
}
