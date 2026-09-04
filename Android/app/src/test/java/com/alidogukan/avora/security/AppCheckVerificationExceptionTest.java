package com.alidogukan.avora.security;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import java.net.ConnectException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class AppCheckVerificationExceptionTest {
    @Test
    public void screenshotAttestationFailureIsNotReportedAsTailscaleFailure() {
        Throwable error = new ExecutionException(new Exception(
                "com.google.firebase.FirebaseException: Error returned from API. "
                        + "code: 403 body: App attestation failed."));
        assertTrue(AppCheckVerificationException.isAppCheckFailure(error));
    }

    @Test
    public void tokenFailurePreservesOriginalDiagnosticCause() {
        Exception original = new Exception("Provider rejected this installation");
        AppCheckVerificationException failure = new AppCheckVerificationException(original);
        assertSame(original, failure.getCause());
        assertTrue(AppCheckVerificationException.isAppCheckFailure(
                new ExecutionException(failure)));
    }

    @Test
    public void timeoutDuringTokenAcquisitionIsAFirebaseStageFailure() {
        assertTrue(AppCheckVerificationException.isAppCheckFailure(
                new AppCheckVerificationException(new TimeoutException())));
    }

    @Test
    public void backendConnectionFailureIsNotMisclassified() {
        assertFalse(AppCheckVerificationException.isAppCheckFailure(
                new ConnectException("Failed to connect to 100.97.32.111")));
        assertFalse(AppCheckVerificationException.isAppCheckFailure(new TimeoutException()));
        assertFalse(AppCheckVerificationException.isAppCheckFailure(
                new IllegalStateException("API code: 403")));
    }

    @Test
    public void emptyTokenFailureIsRecognized() {
        assertTrue(AppCheckVerificationException.isAppCheckFailure(
                new IllegalStateException("APP_CHECK_TOKEN_UNAVAILABLE")));
    }

    @Test
    public void nullAndCyclicCauseChainsTerminateSafely() {
        assertFalse(AppCheckVerificationException.isAppCheckFailure(null));
        Exception first = new Exception();
        Exception second = new Exception();
        first.initCause(second);
        second.initCause(first);
        assertFalse(AppCheckVerificationException.isAppCheckFailure(first));
    }
}
