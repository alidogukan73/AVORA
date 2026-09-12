package com.alidogukan.avora.nas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.net.URI;

public class NasApiClientContractTest {
    @Test
    public void publicEndpointUsesTlsAndTailscaleDns() {
        URI endpoint = URI.create(NasApiClient.BASE_URL);

        assertEquals("https", endpoint.getScheme());
        assertTrue(endpoint.getHost().endsWith(".ts.net"));
        assertTrue(endpoint.getPath().isEmpty());
        assertTrue(endpoint.getQuery() == null);
    }

    @Test
    public void publicEndpointTargetsDedicatedNasMachine() {
        assertEquals("avora-nas.tailf335a4.ts.net", URI.create(NasApiClient.BASE_URL).getHost());
    }
}
