package com.alidogukan.avora.nas;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.concurrent.ExecutionException;

public class NasAutomaticBackupWorkerTest {
    @Test
    public void preservesNasFailureCodesThroughTaskWrappers() {
        assertEquals("NAS_SESSION_EXPIRED", NasAutomaticBackupWorker.errorCode(
                new ExecutionException(new NasApiException("NAS_SESSION_EXPIRED"))));
    }

    @Test
    public void mapsAuthorizationAndUnknownFailuresSafely() {
        assertEquals("FIREBASE_NOT_AUTHORIZED",
                NasAutomaticBackupWorker.errorCode(new SecurityException("denied")));
        assertEquals("NAS_BACKUP_FAILED",
                NasAutomaticBackupWorker.errorCode(new IllegalStateException("broken")));
    }
}
