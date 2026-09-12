package com.alidogukan.avora.nas;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

/** Authenticated, version-aware storage for JSON documents in a user's NAS space. */
public final class NasDocumentClient {
    private static final int CONNECT_TIMEOUT_MS = 7_000;
    private static final int READ_TIMEOUT_MS = 25_000;
    private static final int MAX_REQUEST_BYTES = 2 * 1024 * 1024;
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024 + 64 * 1024;

    private NasDocumentClient() { }

    public static String backupKey(String deviceId) {
        String key = "backup-" + (deviceId == null ? "" : deviceId.trim());
        if (!key.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}")) {
            throw new IllegalArgumentException("Invalid NAS document key.");
        }
        return key;
    }

    public static Document getDocument(String accessToken, String key) throws NasApiException {
        validateKey(key);
        Response response = request("GET", "/v1/data/documents/" + key, null, accessToken);
        if (response.status == HttpURLConnection.HTTP_NOT_FOUND) return null;
        if (response.status != HttpURLConnection.HTTP_OK) {
            throw apiError(response.status, response.json);
        }
        return parseDocument(response.json.optJSONObject("document"));
    }

    public static Document putDocument(String accessToken, String key, JSONObject data,
                                       int expectedVersion) throws NasApiException {
        validateKey(key);
        if (data == null || expectedVersion < 0) {
            throw new IllegalArgumentException("Document data and version are required.");
        }
        try {
            byte[] payload = new JSONObject()
                    .put("data", data)
                    .put("expected_version", expectedVersion)
                    .toString()
                    .getBytes(StandardCharsets.UTF_8);
            if (payload.length > MAX_REQUEST_BYTES) {
                throw new NasApiException("NAS_BACKUP_TOO_LARGE");
            }
            Response response = request("PUT", "/v1/data/documents/" + key,
                    payload, accessToken);
            if (response.status != HttpURLConnection.HTTP_OK) {
                throw apiError(response.status, response.json);
            }
            return parseDocument(response.json.optJSONObject("document"));
        } catch (NasApiException error) {
            throw error;
        } catch (Exception error) {
            throw new NasApiException("NAS_INVALID_RESPONSE", error);
        }
    }

    private static Response request(String method, String path, byte[] payload,
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
            connection.setRequestProperty("Authorization", "Bearer " + requireToken(accessToken));
            if (payload != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(payload.length);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(payload);
                }
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            byte[] bytes = readAll(stream);
            JSONObject json = bytes.length == 0
                    ? new JSONObject()
                    : new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            return new Response(status, json);
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

    private static Document parseDocument(JSONObject value) throws NasApiException {
        if (value == null) throw new NasApiException("NAS_INVALID_RESPONSE");
        String key = value.optString("key", "");
        int version = value.optInt("version", 0);
        long updatedAt = value.optLong("updated_at", 0L);
        JSONObject data = value.optJSONObject("data");
        if (key.isEmpty() || version < 1 || updatedAt < 1L || data == null) {
            throw new NasApiException("NAS_INVALID_RESPONSE");
        }
        return new Document(key, version, updatedAt, data);
    }

    private static NasApiException apiError(int status, JSONObject response) {
        JSONObject error = response.optJSONObject("error");
        String code = error == null ? "" : error.optString("code", "");
        if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
            return new NasApiException("NAS_SESSION_EXPIRED");
        }
        if (status == HttpURLConnection.HTTP_CONFLICT && "version_conflict".equals(code)) {
            return new NasApiException("NAS_CONFLICT");
        }
        if (status == HttpURLConnection.HTTP_ENTITY_TOO_LARGE) {
            return new NasApiException("NAS_BACKUP_TOO_LARGE");
        }
        return new NasApiException("NAS_HTTP_" + status);
    }

    private static String requireToken(String token) throws NasApiException {
        String value = token == null ? "" : token.trim();
        if (value.isEmpty() || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new NasApiException("NAS_SESSION_EXPIRED");
        }
        return value;
    }

    private static void validateKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}")) {
            throw new IllegalArgumentException("Invalid NAS document key.");
        }
    }

    private static byte[] readAll(InputStream stream) throws IOException, NasApiException {
        if (stream == null) return new byte[0];
        try (InputStream input = stream;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
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

    public static final class Document {
        public final String key;
        public final int version;
        public final long updatedAt;
        public final JSONObject data;

        Document(String key, int version, long updatedAt, JSONObject data) {
            this.key = key;
            this.version = version;
            this.updatedAt = updatedAt;
            this.data = data;
        }
    }

    private static final class Response {
        final int status;
        final JSONObject json;

        Response(int status, JSONObject json) {
            this.status = status;
            this.json = json;
        }
    }
}

