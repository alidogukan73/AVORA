package com.alidogukan.avora.backup;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AvoraBackupManagerRestoreContractTest {
    @Test
    public void restoresSeasonAndJournalRecordsThatDoNotDependOnPhotoFiles() {
        assertTrue(AvoraBackupManager.isRestorableGardenJournalSection("seasons"));
        assertTrue(AvoraBackupManager.isRestorableGardenJournalSection("events"));
        assertTrue(AvoraBackupManager.isRestorableGardenJournalSection("season_outcomes"));
    }

    @Test
    public void doesNotRestorePhotoMetadataWithoutPhotoFiles() {
        assertFalse(AvoraBackupManager.isRestorableGardenJournalSection("photo_metadata"));
        assertFalse(AvoraBackupManager.isRestorableGardenJournalSection("photos"));
    }
}
