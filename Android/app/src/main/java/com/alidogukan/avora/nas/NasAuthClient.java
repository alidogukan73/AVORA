package com.alidogukan.avora.nas;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

/** Password and bearer-token operations for the AVORA NAS API. */
public final class NasAuthClient {
    private static final int CONNECT_TIMEOUT_MS = 7_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;

    private NasAuthClient() { }

    public static NasSession login(String email, String password) throws NasApiException {
        try {
            JSONObject body = new JSONObject()
                    .put("email", safe(email))
                    .put("password", password == null ? "" : password);
            JSONObject response = request("POST", "/v1/auth/login",
                    body.toString().getBytes(StandardCharsets.UTF_8), null);
            return parseSession(response);
        } catch (NasApiException error) {
            throw error;
        } catch (Exception error) {
            throw new NasApiException("NAS_INVALID_RESPONSE", error);
        }
    }

    public static NasSession register(String inviteCode, String email,
                                      String displayName, String password)
            throws NasApiException {
        try {
            JSONObject body = new JSONObject()
                    .put("invite_code", safe(inviteCode))
                    .put("email", safe(email))
                    .put("display_name", safe(displayName))
                    .put("password", password == null ? "" : password);
            JSONObject response = request("POST", "/v1/auth/register",
                    body.toString().getBytes(StandardCharsets.UTF_8), null);
            return parseSession(response);
        } catch (NasApiException error) {
            throw error;
        } catch (Exception error) {
            throw new NasApiException("NAS_INVALID_RESPONSE", error);
        }
    }

    public static Invite createInvite(String accessToken, int validHours,
                                      int maxUses) throws NasApiException {
        try {
            JSONObject body = new JSONObject()
                    .put("valid_hours", validHours)
                    .put("max_uses", maxUses);
            JSONObject response = request("POST", "/v1/admin/invites",
                    body.toString().getBytes(StandardCharsets.UTF_8), accessToken);
            Invite invite = new Invite(
                    response.optString("invite_code", ""),
                    response.optLong("expires_at", 0L),
                    response.optInt("max_uses", 0));
            if (!invite.isValidAt(System.currentTimeMillis() / 1000L)) {
                throw new NasApiException("NAS_INVALID_RESPONSE");
            }
            return invite;
        } catch (NasApiException error) {
            throw error;
        } catch (Exception error) {
            throw new NasApiException("NAS_INVALID_RESPONSE", error);
        }
    }

    public static void revokeInvite(String accessToken, String inviteCode)
            throws NasApiException {
        try {
            JSONObject body = new JSONObject().put("invite_code", safe(inviteCode));
            JSONObject response = request("POST", "/v1/admin/invites/revoke",
                    body.toString().getBytes(StandardCharsets.UTF_8), accessToken);
            if (!response.optBoolean("revoked", false)) {
                throw new NasApiException("NAS_INVALID_RESPONSE");
            }
        } catch (NasApiException error) {
            throw error;
        } catch (Exception error) {
            throw new NasApiException("NAS_INVALID_RESPONSE", error);
        }
    }

    public static AccessRequest requestDeviceAccess(String accessToken,
                                                    String deviceId,
                                                    String firebaseUid)
            throws NasApiException {
        try {
            JSONObject body = new JSONObject()
                    .put("device_id", safe(deviceId))
                    .put("firebase_uid", safe(firebaseUid));
            JSONObject response = request("POST", "/v1/access-requests",
                    body.toString().getBytes(StandardCharsets.UTF_8), accessToken);
            return parseAccessRequest(response.optJSONObject("access_request"));
        } catch (NasApiException error) {
            throw error;
        } catch (Exception error) {
            throw new NasApiException("NAS_INVALID_RESPONSE", error);
        }
    }

    public static List<AccessRequest> pendingAccessRequests(String accessToken,
                                                            String deviceId)
            throws NasApiException {
        try {
            JSONObject response = request("GET",
                    "/v1/admin/access-requests?device_id=" + safe(deviceId),
                    null, accessToken);
            JSONArray values = response.optJSONArray("access_requests");
            if (values == null) throw new NasApiException("NAS_INVALID_RESPONSE");
            List<AccessRequest> result = new ArrayList<>();
            for (int index = 0; index < values.length(); index++) {
                result.add(parseAccessRequest(values.optJSONObject(index)));
            }
            return result;
        } catch (NasApiException error) {
            throw error;
        } catch (Exception error) {
            throw new NasApiException("NAS_INVALID_RESPONSE", error);
        }
    }

    public static AccessRequest approveAccessRequest(String accessToken,
                                                     String requestId)
            throws NasApiException {
        try {
            JSONObject body = new JSONObject().put("request_id", safe(requestId));
            JSONObject response = request("POST",
                    "/v1/admin/access-requests/approve",
                    body.toString().getBytes(StandardCharsets.UTF_8), accessToken);
            return parseAccessRequest(response.optJSONObject("access_request"));
        } catch (NasApiException error) {
            throw error;
        } catch (Exception error) {
            throw new NasApiException("NAS_INVALID_RESPONSE", error);
        }
    }

    private static NasSession parseSession(JSONObject response) throws NasApiException {
        String token = response.optString("access_token", "");
        long expiresAt = response.optLong("expires_at", 0L);
        NasSession.User user = parseUser(response.optJSONObject("user"));
        NasSession session = new NasSession(token, expiresAt, user);
        if (!session.isActiveAt(System.currentTimeMillis() / 1000L)) {
            throw new NasApiException("NAS_INVALID_SESSION");
        }
        return session;
    }

    public static NasSession.User currentUser(String accessToken) throws NasApiException {
        JSONObject response = request("GET", "/v1/me", null, accessToken);
        try {
            return parseUser(response.optJSONObject("user"));
        } catch (Exception error) {
            throw new NasApiException("NAS_INVALID_RESPONSE", error);
        }
    }

    public static void logout(String accessToken) throws NasApiException {
        request("POST", "/v1/auth/logout", new byte[0], accessToken);
    }

    public static int changePassword(String accessToken, String currentPassword,
                                     String newPassword) throws NasApiException {
        try {
            JSONObject body = new JSONObject()
                    .put("current_password", currentPassword == null ? "" : currentPassword)
                    .put("new_password", newPassword == null ? "" : newPassword);
            JSONObject response = request("POST", "/v1/account/password",
                    body.toString().getBytes(StandardCharsets.UTF_8), accessToken);
            if (!response.optBoolean("password_changed", false)) {
                throw new NasApiException("NAS_INVALID_RESPONSE");
            }
            return Math.max(0, response.optInt("revoked_sessions", 0));
        } catch (NasApiException error) {
            throw error;
        } catch (Exception error) {
            throw new NasApiException("NAS_INVALID_RESPONSE", error);
        }
    }

    public static int revokeOtherSessions(String accessToken) throws NasApiException {
        JSONObject response = request("POST", "/v1/account/sessions/revoke-others",
                new byte[0], accessToken);
        return Math.max(0, response.optInt("revoked_sessions", 0));
    }

    private static JSONObject request(String method, String path, byte[] payload,
                                      String accessToken) throws NasApiException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(NasApiClient.BASE_URL + path)
                    .openConnection();
            if (!(connection instanceof HttpsURLConnection)) {
                throw new NasApiException("NAS_INSECURE_ENDPOINT");
            }
            connection.setRequestMethod(method);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            if (accessToken != null && !accessToken.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            }
            if (payload != null) {
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(payload.length);
                if (payload.length > 0) {
                    connection.setRequestProperty("Content-Type", "application/json");
                }
                try (OutputStream output = connection.getOutputStream()) {
                    if (payload.length > 0) output.write(payload);
                }
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            byte[] responseBytes = readAll(stream);
            JSONObject response = responseBytes.length == 0
                    ? new JSONObject()
                    : new JSONObject(new String(responseBytes, StandardCharsets.UTF_8));
            if (status < 200 || status >= 300) {
                throw apiError(status, response,
                        accessToken != null && !accessToken.isEmpty());
            }
            return response;
        } catch (NasApiException error) {
            throw error;
        } catch (SocketTimeoutException error) {
            throw new NasApiException("NAS_TIMEOUT", error);
        } catch (IOException error) {
            throw new NasApiException("NAS_UNAVAILABLE", error);
        } catch (Exception error) {
            throw new NasApiException("NAS_INVALID_RESPONSE", error);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static NasApiException apiError(int status, JSONObject response,
                                            boolean authenticatedRequest) {
        JSONObject error = response.optJSONObject("error");
        String code = error == null ? "" : error.optString("code", "");
        return new NasApiException(mapErrorCode(status, code, authenticatedRequest));
    }

    static String mapErrorCode(int status, String serverCode,
                               boolean authenticatedRequest) {
        String code = serverCode == null ? "" : serverCode;
        if (status == HttpURLConnection.HTTP_UNAUTHORIZED
                || "invalid_credentials".equals(code)) {
            return authenticatedRequest
                    ? "NAS_SESSION_EXPIRED"
                    : "NAS_INVALID_CREDENTIALS";
        }
        if (status == 429 || "rate_limited".equals(code)) {
            return "NAS_RATE_LIMITED";
        }
        if ("current_password_invalid".equals(code)) {
            return "NAS_CURRENT_PASSWORD_INVALID";
        }
        if ("weak_password".equals(code)) {
            return "NAS_WEAK_PASSWORD";
        }
        if ("password_unchanged".equals(code)) {
            return "NAS_PASSWORD_UNCHANGED";
        }
        if ("invalid_invite".equals(code)) {
            return "NAS_INVALID_INVITE";
        }
        if ("invite_not_found".equals(code)) {
            return "NAS_INVITE_NOT_FOUND";
        }
        if ("forbidden".equals(code) || status == HttpURLConnection.HTTP_FORBIDDEN) {
            return "NAS_FORBIDDEN";
        }
        return "NAS_HTTP_" + status;
    }

    public static final class Invite {
        public final String code;
        public final long expiresAt;
        public final int maxUses;

        Invite(String code, long expiresAt, int maxUses) {
            this.code = code == null ? "" : code;
            this.expiresAt = expiresAt;
            this.maxUses = maxUses;
        }

        public boolean isValidAt(long epochSeconds) {
            return code.startsWith("avora_") && code.length() >= 24
                    && expiresAt > epochSeconds && maxUses > 0;
        }
    }

    private static AccessRequest parseAccessRequest(JSONObject value)
            throws NasApiException {
        if (value == null) throw new NasApiException("NAS_INVALID_RESPONSE");
        AccessRequest result = new AccessRequest(
                value.optString("id", ""),
                value.optString("device_id", ""),
                value.optString("user_id", ""),
                value.optString("firebase_uid", ""),
                value.optString("email", ""),
                value.optString("display_name", ""),
                value.optString("status", ""),
                value.optLong("created_at", 0L));
        if (!result.isValid()) throw new NasApiException("NAS_INVALID_RESPONSE");
        return result;
    }

    public static final class AccessRequest {
        public final String id;
        public final String deviceId;
        public final String userId;
        public final String firebaseUid;
        public final String email;
        public final String displayName;
        public final String status;
        public final long createdAt;

        AccessRequest(String id, String deviceId, String userId,
                      String firebaseUid, String email, String displayName,
                      String status, long createdAt) {
            this.id = safe(id);
            this.deviceId = safe(deviceId);
            this.userId = safe(userId);
            this.firebaseUid = safe(firebaseUid);
            this.email = safe(email);
            this.displayName = safe(displayName);
            this.status = safe(status);
            this.createdAt = createdAt;
        }

        boolean isValid() {
            return !id.isEmpty() && !deviceId.isEmpty() && !userId.isEmpty()
                    && firebaseUid.length() >= 16 && !displayName.isEmpty()
                    && ("pending".equals(status) || "approved".equals(status));
        }
    }

    private static NasSession.User parseUser(JSONObject user) throws NasApiException {
        if (user == null) throw new NasApiException("NAS_INVALID_RESPONSE");
        NasSession.User result = new NasSession.User(
                user.optString("id", ""),
                user.optString("email", ""),
                user.optString("display_name", ""),
                user.optString("role", ""));
        if (result.id.isEmpty() || result.email.isEmpty() || result.displayName.isEmpty()) {
            throw new NasApiException("NAS_INVALID_RESPONSE");
        }
        return result;
    }

    private static byte[] readAll(InputStream stream) throws IOException, NasApiException {
        if (stream == null) return new byte[0];
        try (InputStream input = stream;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > MAX_RESPONSE_BYTES) {
                    throw new NasApiException("NAS_RESPONSE_TOO_LARGE");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
