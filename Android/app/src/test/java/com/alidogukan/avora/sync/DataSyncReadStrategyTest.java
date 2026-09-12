package com.alidogukan.avora.sync;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Guards the Firebase read pattern that previously crashed after access approval. */
public final class DataSyncReadStrategyTest {
    @Test
    public void keptSyncedPathsUseSingleValueListenersInsteadOfGet() throws Exception {
        Path sourceFile = Paths.get(
                "src/main/java/com/alidogukan/avora/sync/DataSyncRepository.java");
        if (!Files.exists(sourceFile)) {
            sourceFile = Paths.get(
                    "app/src/main/java/com/alidogukan/avora/sync/DataSyncRepository.java");
        }

        assertTrue("DataSyncRepository source not found: " + sourceFile.toAbsolutePath(),
                Files.exists(sourceFile));
        String source = Files.readString(sourceFile, StandardCharsets.UTF_8);

        assertTrue(source.contains("addListenerForSingleValueEvent"));
        assertFalse(source.contains("deviceRef.child(\"status\").get()"));
        assertFalse(source.contains("deviceRef.child(\"health\").get()"));
        assertFalse(source.contains("deviceRef.child(\"zones\").get()"));
        assertFalse(source.contains(
                "deviceRef.child(\"weather\").child(\"forecast\").get()"));
    }
}
