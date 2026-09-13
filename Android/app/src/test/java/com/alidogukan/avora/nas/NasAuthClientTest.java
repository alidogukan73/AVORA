package com.alidogukan.avora.nas;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NasAuthClientTest {
    @Test
    public void loginAndAuthenticated401AreDistinguished() {
        assertEquals("NAS_INVALID_CREDENTIALS",
                NasAuthClient.mapErrorCode(401, "invalid_credentials", false));
        assertEquals("NAS_SESSION_EXPIRED",
                NasAuthClient.mapErrorCode(401, "invalid_credentials", true));
    }

    @Test
    public void accountSecurityErrorsRemainSpecific() {
        assertEquals("NAS_CURRENT_PASSWORD_INVALID",
                NasAuthClient.mapErrorCode(400, "current_password_invalid", true));
        assertEquals("NAS_WEAK_PASSWORD",
                NasAuthClient.mapErrorCode(400, "weak_password", true));
        assertEquals("NAS_PASSWORD_UNCHANGED",
                NasAuthClient.mapErrorCode(400, "password_unchanged", true));
        assertEquals("NAS_RATE_LIMITED",
                NasAuthClient.mapErrorCode(429, "rate_limited", true));
        assertEquals("NAS_INVALID_INVITE",
                NasAuthClient.mapErrorCode(400, "invalid_invite", false));
        assertEquals("NAS_INVITE_NOT_FOUND",
                NasAuthClient.mapErrorCode(404, "invite_not_found", true));
        assertEquals("NAS_FORBIDDEN",
                NasAuthClient.mapErrorCode(403, "forbidden", true));
        assertEquals("NAS_ACCOUNT_STATE_CONFLICT",
                NasAuthClient.mapErrorCode(409, "account_state_conflict", true));
    }

    @Test
    public void unknownHttpStatusUsesStableFallback() {
        assertEquals("NAS_HTTP_503",
                NasAuthClient.mapErrorCode(503, "server_error", true));
    }
}
