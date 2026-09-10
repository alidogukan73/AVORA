package com.alidogukan.avora.seedling;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

public final class SeedlingVarietyCatalogTest {
    @Test public void mergeKeepsBuiltInsFirstAndAddsUniqueCustomValues() {
        List<String> merged = SeedlingVarietyCatalog.merge(
                Arrays.asList("H2274", "Rio Grande"),
                new LinkedHashSet<>(Arrays.asList(
                        " Anadolu F1 ", "rio grande", "  Pembe   Köy  ")));

        assertEquals(Arrays.asList(
                "H2274", "Rio Grande", "Anadolu F1", "Pembe Köy"), merged);
    }

    @Test public void findMatchesWithoutCaseOrOuterWhitespace() {
        assertEquals("Kapya", SeedlingVarietyCatalog.find(
                Arrays.asList("Kapya", "Çarliston"), "  kapya "));
    }
}
