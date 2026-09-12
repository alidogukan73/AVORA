package com.alidogukan.avora.nas;

/** Authenticated NAS session. Passwords never enter this model. */
public final class NasSession {
    public final String accessToken;
    public final long expiresAt;
    public final User user;

    public NasSession(String accessToken, long expiresAt, User user) {
        if (accessToken == null || accessToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Access token is required.");
        }
        if (user == null) {
            throw new IllegalArgumentException("User is required.");
        }
        this.accessToken = accessToken;
        this.expiresAt = expiresAt;
        this.user = user;
    }

    public boolean isActiveAt(long epochSeconds) {
        return expiresAt > epochSeconds && !accessToken.isEmpty();
    }

    public static final class User {
        public final String id;
        public final String email;
        public final String displayName;
        public final String role;

        public User(String id, String email, String displayName, String role) {
            this.id = safe(id);
            this.email = safe(email);
            this.displayName = safe(displayName);
            this.role = safe(role);
        }

        private static String safe(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
