package com.alidogukan.avora.nas;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NasPhotoBackupManagerTest {
    @Test
    public void uploadsNewAndChangedPhotoFiles() {
        assertEquals(NasPhotoBackupManager.SyncAction.UPLOAD,
                NasPhotoBackupManager.decideSyncAction(
                        false, "local", "", false));
        assertEquals(NasPhotoBackupManager.SyncAction.UPLOAD,
                NasPhotoBackupManager.decideSyncAction(
                        true, "new-hash", "old-hash", true));
    }

    @Test
    public void updatesOnlyMetadataWhenFileIsUnchanged() {
        assertEquals(NasPhotoBackupManager.SyncAction.UPDATE_METADATA,
                NasPhotoBackupManager.decideSyncAction(
                        true, "same-hash", "same-hash", false));
    }

    @Test
    public void skipsACompletelyUnchangedPhoto() {
        assertEquals(NasPhotoBackupManager.SyncAction.UNCHANGED,
                NasPhotoBackupManager.decideSyncAction(
                        true, "same-hash", "same-hash", true));
    }
}
