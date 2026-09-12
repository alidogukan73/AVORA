package com.alidogukan.avora.plantassistant;

import android.graphics.Bitmap;
import android.util.Base64;

import com.alidogukan.avora.security.AppCheckRequestAuthenticator;
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

/** HTTPS client. The Gemini key remains only on the Raspberry Pi. */
public final class PlantAssistantVisionClient {
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;
    private PlantAssistantVisionClient() { }
    /** Tailscale Funnel üzerinden Raspberry Pi'deki korumalı görsel analiz servisi. */
    public static final String BASE_URL = "https://avora-pi.tailf335a4.ts.net";
    public static final String ENDPOINT = BASE_URL + "/v1/plant-assistant/analyze";

    public static JSONObject analyze(Bitmap bitmap, JSONObject context) throws Exception {
        if (bitmap == null) throw new IllegalStateException("PHOTO_DECODE_FAILED");
        Bitmap uploadBitmap = scaledForUpload(bitmap);
        HttpURLConnection connection = null;
        try {
            ByteArrayOutputStream image = new ByteArrayOutputStream();
            if (!uploadBitmap.compress(Bitmap.CompressFormat.JPEG, 82, image)) {
                throw new IllegalStateException("PHOTO_ENCODE_FAILED");
            }
            JSONObject request = new JSONObject();
            request.put("mime_type", "image/jpeg");
            request.put("image_base64", Base64.encodeToString(image.toByteArray(), Base64.NO_WRAP));
            request.put("context", context == null ? new JSONObject() : context);
            byte[] payload = request.toString().getBytes(StandardCharsets.UTF_8);
            URL endpoint = new URL(ENDPOINT);
            if (!"https".equalsIgnoreCase(endpoint.getProtocol())) {
                throw new IllegalStateException("VISION_INSECURE_ENDPOINT");
            }
            connection = (HttpURLConnection) endpoint.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(60000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            AppCheckRequestAuthenticator.authorize(connection);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload);
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            byte[] response = readAll(stream);
            JSONObject json = parseResponse(response, status);
            if (status < 200 || status >= 300) {
                String code = json.optString("error", "").trim();
                throw new IllegalStateException(code.isEmpty()
                        ? "VISION_HTTP_" + status : code);
            }
            return json;
        } catch (SocketTimeoutException error) {
            throw new IllegalStateException("VISION_TIMEOUT", error);
        } catch (IOException error) {
            throw new IllegalStateException("VISION_UNAVAILABLE", error);
        } finally {
            if (connection != null) connection.disconnect();
            if (uploadBitmap != bitmap && !uploadBitmap.isRecycled()) uploadBitmap.recycle();
        }
    }

    private static JSONObject parseResponse(byte[] response, int status) {
        if (response == null || response.length == 0) {
            throw new IllegalStateException(status >= 200 && status < 300
                    ? "VISION_INVALID_RESPONSE" : "VISION_HTTP_" + status);
        }
        try {
            return new JSONObject(new String(response, StandardCharsets.UTF_8));
        } catch (Exception error) {
            throw new IllegalStateException(status >= 200 && status < 300
                    ? "VISION_INVALID_RESPONSE" : "VISION_HTTP_" + status, error);
        }
    }

    public static String list(JSONArray values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; values != null && i < values.length(); i++) {
            if (builder.length() > 0) builder.append("\n");
            builder.append("• ").append(values.optString(i));
        }
        return builder.toString();
    }

    /** Reads responses on every supported Android version (API 26+). */
    private static byte[] readAll(InputStream stream) throws Exception {
        if (stream == null) return new byte[0];
        try (InputStream input = stream;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > MAX_RESPONSE_BYTES) {
                    throw new IllegalStateException("VISION_INVALID_RESPONSE");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }
    private static Bitmap scaledForUpload(Bitmap bitmap) {
        int longestSide = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (longestSide <= 1600) return bitmap;
        float scale = 1600f / longestSide;
        return Bitmap.createScaledBitmap(bitmap,
                Math.round(bitmap.getWidth() * scale), Math.round(bitmap.getHeight() * scale), true);
    }
}
