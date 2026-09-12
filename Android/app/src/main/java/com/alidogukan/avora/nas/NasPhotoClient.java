package com.alidogukan.avora.nas;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

/** Streams private JPEG photos to and from the authenticated tenant NAS area. */
public final class NasPhotoClient {
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 90_000;
    private static final int MAX_PHOTO_BYTES = 20 * 1024 * 1024;
    private static final int MAX_JSON_BYTES = 8 * 1024 * 1024;
    private static final int MAX_PHOTOS = 5_000;
    private static final String SHA256_PATTERN = "[0-9a-f]{64}";

    private NasPhotoClient() { }

    public static List<PhotoRecord> listPhotos(String accessToken) throws NasApiException {
        JsonResponse response = jsonRequest("GET", "/v1/photos", null, accessToken);
        if (response.status != HttpURLConnection.HTTP_OK) {
            throw apiError(response.status, response.json);
        }
        JSONArray values = response.json.optJSONArray("photos");
        if (values == null || values.length() > MAX_PHOTOS) {
            throw new NasApiException("NAS_INVALID_RESPONSE");
        }
        List<PhotoRecord> records = new ArrayList<>(values.length());
        for (int index = 0; index < values.length(); index++) {
            records.add(parseRecord(values.optJSONObject(index)));
        }
        return records;
    }

    public static PhotoRecord uploadPhoto(String accessToken, String photoId, File source)
            throws NasApiException {
        validatePhotoId(photoId);
        if (source == null || !source.isFile() || source.length() <= 0L
                || source.length() > MAX_PHOTO_BYTES) {
            throw new NasApiException("NAS_PHOTO_INVALID");
        }
        HttpURLConnection connection = null;
        try {
            connection = open("PUT", "/v1/photos/" + photoId, accessToken);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "image/jpeg");
            connection.setFixedLengthStreamingMode(source.length());
            try (InputStream input = new FileInputStream(source);
                 OutputStream output = connection.getOutputStream()) {
                copy(input, output, MAX_PHOTO_BYTES, null);
            }
            int status = connection.getResponseCode();
            JSONObject response = readJsonResponse(connection, status);
            if (status != HttpURLConnection.HTTP_OK) throw apiError(status, response);
            return parseRecord(response.optJSONObject("photo"));
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

    public static PhotoRecord updateMetadata(String accessToken, String photoId,
                                             JSONObject metadata) throws NasApiException {
        validatePhotoId(photoId);
        if (metadata == null) throw new NasApiException("NAS_PHOTO_INVALID");
        try {
            byte[] payload = new JSONObject()
                    .put("metadata", metadata)
                    .toString()
                    .getBytes(StandardCharsets.UTF_8);
            if (payload.length > 64 * 1024) {
                throw new NasApiException("NAS_PHOTO_INVALID");
            }
            JsonResponse response = jsonRequest("POST",
                    "/v1/photos/" + photoId + "/metadata",
                    payload, accessToken);
            if (response.status != HttpURLConnection.HTTP_OK) {
                throw apiError(response.status, response.json);
            }
            return parseRecord(response.json.optJSONObject("photo"));
        } catch (NasApiException error) {
            throw error;
        } catch (Exception error) {
            throw new NasApiException("NAS_INVALID_RESPONSE", error);
        }
    }

    /** Downloads and verifies a JPEG before exposing it to the restore manager. */
    public static void downloadPhoto(String accessToken, PhotoRecord record, File destination)
            throws NasApiException {
        if (record == null || destination == null) {
            throw new NasApiException("NAS_PHOTO_INVALID");
        }
        validatePhotoId(record.id);
        HttpURLConnection connection = null;
        try {
            connection = open("GET", "/v1/photos/" + record.id, accessToken);
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw apiError(status, readJsonResponse(connection, status));
            }
            String contentType = connection.getContentType();
            if (contentType == null
                    || !"image/jpeg".equalsIgnoreCase(contentType.split(";", 2)[0].trim())) {
                throw new NasApiException("NAS_PHOTO_INVALID");
            }
            long declaredSize = connection.getContentLengthLong();
            if (declaredSize != record.sizeBytes || declaredSize <= 0L
                    || declaredSize > MAX_PHOTO_BYTES) {
                throw new NasApiException("NAS_PHOTO_INVALID");
            }
            String etag = cleanEtag(connection.getHeaderField("ETag"));
            if (!record.sha256.equals(etag)) {
                throw new NasApiException("NAS_PHOTO_INVALID");
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long copied;
            try (InputStream input = connection.getInputStream();
                 OutputStream output = new FileOutputStream(destination)) {
                copied = copy(input, output, MAX_PHOTO_BYTES, digest);
                output.flush();
            }
            if (copied != record.sizeBytes || !record.sha256.equals(hex(digest.digest()))) {
                throw new NasApiException("NAS_PHOTO_INVALID");
            }
        } catch (NasApiException error) {
            if (destination.exists()) destination.delete();
            throw error;
        } catch (SocketTimeoutException error) {
            if (destination.exists()) destination.delete();
            throw new NasApiException("NAS_TIMEOUT", error);
        } catch (IOException error) {
            if (destination.exists()) destination.delete();
            throw new NasApiException("NAS_UNAVAILABLE", error);
        } catch (Exception error) {
            if (destination.exists()) destination.delete();
            throw new NasApiException("NAS_INVALID_RESPONSE", error);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static JsonResponse jsonRequest(String method, String path, byte[] payload,
                                            String accessToken) throws NasApiException {
        HttpURLConnection connection = null;
        try {
            connection = open(method, path, accessToken);
            connection.setRequestProperty("Accept", "application/json");
            if (payload != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(payload.length);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(payload);
                }
            }
            int status = connection.getResponseCode();
            return new JsonResponse(status, readJsonResponse(connection, status));
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

    private static HttpURLConnection open(String method, String path, String accessToken)
            throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                NasApiClient.BASE_URL + path).openConnection();
        if (!(connection instanceof HttpsURLConnection)) {
            connection.disconnect();
            throw new NasApiException("NAS_INSECURE_ENDPOINT");
        }
        connection.setRequestMethod(method);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + requireToken(accessToken));
        return connection;
    }

    private static JSONObject readJsonResponse(HttpURLConnection connection, int status)
            throws Exception {
        InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream();
        byte[] bytes = readAll(stream, MAX_JSON_BYTES);
        return bytes.length == 0 ? new JSONObject()
                : new JSONObject(new String(bytes, StandardCharsets.UTF_8));
    }

    private static PhotoRecord parseRecord(JSONObject value) throws NasApiException {
        if (value == null) throw new NasApiException("NAS_INVALID_RESPONSE");
        String id = value.optString("id", "");
        String sha256 = value.optString("sha256", "").toLowerCase(Locale.US);
        long sizeBytes = value.optLong("size_bytes", -1L);
        JSONObject metadata = value.optJSONObject("metadata");
        long createdAt = value.optLong("created_at", 0L);
        long updatedAt = value.optLong("updated_at", 0L);
        if (!isSafePhotoId(id) || !sha256.matches(SHA256_PATTERN)
                || sizeBytes <= 0L || sizeBytes > MAX_PHOTO_BYTES
                || metadata == null || createdAt <= 0L || updatedAt <= 0L) {
            throw new NasApiException("NAS_INVALID_RESPONSE");
        }
        return new PhotoRecord(id, sha256, sizeBytes, metadata, createdAt, updatedAt);
    }

    private static NasApiException apiError(int status, JSONObject response) {
        JSONObject error = response == null ? null : response.optJSONObject("error");
        String code = error == null ? "" : error.optString("code", "");
        if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
            return new NasApiException("NAS_SESSION_EXPIRED");
        }
        if (status == HttpURLConnection.HTTP_NOT_FOUND) {
            return new NasApiException("NAS_PHOTO_NOT_FOUND");
        }
        if (status == HttpURLConnection.HTTP_ENTITY_TOO_LARGE) {
            return new NasApiException("NAS_PHOTO_TOO_LARGE");
        }
        if (status == HttpURLConnection.HTTP_BAD_REQUEST
                || status == HttpURLConnection.HTTP_UNSUPPORTED_TYPE
                || "invalid_photo".equals(code)) {
            return new NasApiException("NAS_PHOTO_INVALID");
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

    static boolean isSafePhotoId(String photoId) {
        return photoId != null && photoId.matches("[A-Za-z0-9_-]{1,80}");
    }

    private static void validatePhotoId(String photoId) {
        if (!isSafePhotoId(photoId)) throw new IllegalArgumentException("Invalid photo id.");
    }

    private static String cleanEtag(String value) {
        if (value == null) return "";
        String result = value.trim().toLowerCase(Locale.US);
        if (result.startsWith("W/")) result = result.substring(2).trim();
        if (result.length() >= 2 && result.startsWith("\"") && result.endsWith("\"")) {
            result = result.substring(1, result.length() - 1);
        }
        return result;
    }

    private static long copy(InputStream input, OutputStream output, long maximum,
                             MessageDigest digest) throws Exception {
        byte[] buffer = new byte[64 * 1024];
        long total = 0L;
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (count == 0) continue;
            total += count;
            if (total > maximum) throw new NasApiException("NAS_PHOTO_TOO_LARGE");
            if (digest != null) digest.update(buffer, 0, count);
            output.write(buffer, 0, count);
        }
        return total;
    }

    private static byte[] readAll(InputStream stream, int maximum) throws Exception {
        if (stream == null) return new byte[0];
        try (InputStream input = stream;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            copy(input, output, maximum, null);
            return output.toByteArray();
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.US, "%02x", value));
        return result.toString();
    }

    public static final class PhotoRecord {
        public final String id;
        public final String sha256;
        public final long sizeBytes;
        public final JSONObject metadata;
        public final long createdAt;
        public final long updatedAt;

        PhotoRecord(String id, String sha256, long sizeBytes, JSONObject metadata,
                    long createdAt, long updatedAt) {
            this.id = id;
            this.sha256 = sha256;
            this.sizeBytes = sizeBytes;
            this.metadata = metadata;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }
    }

    private static final class JsonResponse {
        final int status;
        final JSONObject json;

        JsonResponse(int status, JSONObject json) {
            this.status = status;
            this.json = json;
        }
    }
}
