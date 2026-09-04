package com.alidogukan.avora.security;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;

/** Token acquisition failed before the private backend request could be sent. */
public final class AppCheckVerificationException extends Exception {
    public AppCheckVerificationException(Throwable cause) {
        super("APP_CHECK_VERIFICATION_FAILED", cause);
    }

    public static boolean isAppCheckFailure(Throwable error) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = error; current != null && seen.add(current);
             current = current.getCause()) {
            if (current instanceof AppCheckVerificationException) return true;
            String message = current.getMessage();
            if (message == null) continue;
            String normalized = message.toLowerCase(Locale.ROOT);
            // Also recognize errors created before stage-specific wrapping was introduced.
            if (normalized.contains("app attestation failed")
                    || normalized.contains("app_check_token_unavailable")) return true;
        }
        return false;
    }
}
