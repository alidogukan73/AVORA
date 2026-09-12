package com.alidogukan.avora.nas;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.concurrent.ExecutionException;

public class NasPhotoBackupWorkerTest {
    @Test
    public void preservesNasFailureCodesThroughTaskWrappers() {
        assertEquals("NAS_SESSION_EXPIRED", NasPhotoBackupWorker.errorCode(
                new ExecutionException(new NasApiException("NAS_SESSION_EXPIRED"))));
        assertEquals("NAS_PHOTO_INVALID", NasPhotoBackupWorker.errorCode(
                new NasApiException("NAS_PHOTO_INVALID")));
    }

    @Test
    public void mapsUnknownFailuresSafely() {
        assertEquals("NAS_PHOTO_BACKUP_FAILED",
                NasPhotoBackupWorker.errorCode(new IllegalStateException("broken")));
    }
}
