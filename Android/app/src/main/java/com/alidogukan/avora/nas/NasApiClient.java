package com.alidogukan.avora.nas;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

/** Minimal HTTPS client used while AVORA data is migrated from Firebase to the NAS. */
public final class NasApiClient {
    public static final String BASE_URL = "https://avora-nas.tailf335a4.ts.net";

    private static final int CONNECT_TIMEOUT_MS = 7_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;

    private NasApiClient() { }

    /** Verifies the public TLS endpoint and rejects incomplete or unexpected health responses. */
    public static Health checkHealth() throws NasApiException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(BASE_URL + "/health").openConnection();
            if (!(connection instanceof HttpsURLConnection)) {
                throw new NasApiException("NAS_INSECURE_ENDPOINT");
            }
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            byte[] body = readAll(stream);
            if (status != HttpURLConnection.HTTP_OK) {
                throw new NasApiException("NAS_HTTP_" + status);
            }

            JSONObject json = new JSONObject(new String(body, StandardCharsets.UTF_8));
            Health health = new Health(
                    json.optString("service", ""),
                    json.optString("version", ""),
                    json.optString("status", ""),
                    json.optBoolean("initialized", false),
                    json.optBoolean("storage_ready", false),
                    json.optLong("time_epoch", 0L));
            if (!health.isReady()) {
                throw new NasApiException("NAS_INVALID_HEALTH");
            }
            return health;
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

    public static final class Health {
        public final String service;
        public final String version;
        public final String status;
        public final boolean initialized;
        public final boolean storageReady;
        public final long timeEpoch;

        Health(String service, String version, String status, boolean initialized,
               boolean storageReady, long timeEpoch) {
            this.service = service;
            this.version = version;
            this.status = status;
            this.initialized = initialized;
            this.storageReady = storageReady;
            this.timeEpoch = timeEpoch;
        }

        public boolean isReady() {
            return "avora-nas-api".equals(service)
                    && "ok".equals(status)
                    && !version.isEmpty()
                    && initialized
                    && storageReady
                    && timeEpoch > 0L;
        }
    }
}
