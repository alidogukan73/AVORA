package com.alidogukan.avora.security;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.appcheck.AppCheckToken;
import com.google.firebase.appcheck.FirebaseAppCheck;

import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;

/** Adds Firebase App Check proof to requests sent to AVORA's private backend. */
public final class AppCheckRequestAuthenticator {
    public static final String APP_CHECK_HEADER = "X-Firebase-AppCheck";
    private static final long TOKEN_TIMEOUT_SECONDS = 15L;

    private AppCheckRequestAuthenticator() { }

    public static void authorize(HttpURLConnection connection) throws Exception {
        AppCheckToken result;
        try {
            result = Tasks.await(
                    FirebaseAppCheck.getInstance().getAppCheckToken(false),
                    TOKEN_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw error;
        } catch (Exception error) {
            // Identify the failed stage without bypassing verification or sending the request.
            throw new AppCheckVerificationException(error);
        }
        String appCheckToken = result == null ? "" : safe(result.getToken());
        if (appCheckToken.isEmpty()) {
            throw new AppCheckVerificationException(
                    new IllegalStateException("APP_CHECK_TOKEN_UNAVAILABLE"));
        }
        connection.setRequestProperty(APP_CHECK_HEADER, appCheckToken);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
