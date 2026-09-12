package com.alidogukan.avora.nas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class NasBackupArchiveTest {
    @Test
    public void exposesCurrentDocumentAndSevenBoundedHistorySlots() {
        List<String> keys = NasBackupArchive.candidateKeys("avora-001");

        assertEquals(8, keys.size());
        assertEquals("backup-avora-001", keys.get(0));
        assertEquals("backup-avora-001-history-0", keys.get(1));
        assertEquals("backup-avora-001-history-6", keys.get(7));
    }

    @Test
    public void dailySlotRepeatsOnlyAfterSevenDays() {
        long day = TimeUnit.DAYS.toMillis(20_000);
        String today = NasBackupArchive.historyKey("avora-001", day);

        assertNotEquals(today, NasBackupArchive.historyKey(
                "avora-001", day + TimeUnit.DAYS.toMillis(1)));
        assertEquals(today, NasBackupArchive.historyKey(
                "avora-001", day + TimeUnit.DAYS.toMillis(7)));
    }

    @Test
    public void duplicateCurrentAndHistoryCopiesAppearOnceAndNewestComesFirst() {
        List<NasDocumentClient.Document> values = new ArrayList<>();
        values.add(new NasDocumentClient.Document("backup-avora-001", 2, 2, null));
        values.add(new NasDocumentClient.Document(
                "backup-avora-001-history-1", 1, 2, null));
        values.add(new NasDocumentClient.Document(
                "backup-avora-001-history-0", 1, 1, null));

        List<NasDocumentClient.Document> result = NasBackupArchive.newestUnique(values);

        assertEquals(2, result.size());
        assertEquals(2L, result.get(0).updatedAt);
        assertEquals(1L, result.get(1).updatedAt);
    }

    @Test
    public void restoreListNeverExceedsSevenEntries() {
        List<NasDocumentClient.Document> values = new ArrayList<>();
        for (int index = 0; index < 9; index++) {
            values.add(new NasDocumentClient.Document(
                    "backup-" + index, 1, index + 1L, null));
        }

        assertEquals(7, NasBackupArchive.newestUnique(values).size());
    }
}
