package com.alidogukan.avora.plantassistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.net.URI;

public class PlantAssistantVisionClientContractTest {
    @Test
    public void publicEndpointUsesTlsAndTailscaleDns() {
        URI baseUrl = URI.create(PlantAssistantVisionClient.BASE_URL);

        assertEquals("https", baseUrl.getScheme());
        assertEquals("avora-pi.tailf335a4.ts.net", baseUrl.getHost());
        assertEquals(-1, baseUrl.getPort());
        assertTrue(baseUrl.getPath().isEmpty());
        assertTrue(baseUrl.getQuery() == null);
    }

    @Test
    public void analysisEndpointKeepsExpectedPath() {
        URI endpoint = URI.create(PlantAssistantVisionClient.ENDPOINT);

        assertEquals("https", endpoint.getScheme());
        assertEquals("/v1/plant-assistant/analyze", endpoint.getPath());
    }
}
