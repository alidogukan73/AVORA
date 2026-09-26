package com.alidogukan.avora.backup;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Computes a stable digest for nested JSON-compatible backup values. */
final class BackupIntegrity {
    static final String ALGORITHM = "SHA-256";

    private BackupIntegrity() {
    }

    static String sha256(Object value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            appendCanonical(digest, value);
            return hex(digest.digest());
        } catch (Exception error) {
            throw new IllegalStateException("Yedek bütünlük özeti oluşturulamadı.", error);
        }
    }

    static boolean matches(String expected, Object value) {
        if (expected == null || !expected.matches("[0-9a-f]{64}")) {
            return false;
        }
        byte[] left = expected.getBytes(StandardCharsets.US_ASCII);
        byte[] right = sha256(value).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(left, right);
    }

    private static void appendCanonical(MessageDigest digest, Object value) {
        if (value == null) {
            update(digest, "z;");
            return;
        }
        if (value instanceof Map) {
            update(digest, "o{");
            Map<?, ?> map = (Map<?, ?>) value;
            List<String> keys = new ArrayList<>();
            for (Object key : map.keySet()) {
                if (key != null) keys.add(key.toString());
            }
            Collections.sort(keys);
            for (String key : keys) {
                appendText(digest, "k", key);
                appendCanonical(digest, map.get(key));
            }
            update(digest, "}");
            return;
        }
        if (value instanceof List) {
            update(digest, "a[");
            for (Object item : (List<?>) value) appendCanonical(digest, item);
            update(digest, "]");
            return;
        }
        if (value instanceof Boolean) {
            update(digest, Boolean.TRUE.equals(value) ? "b1;" : "b0;");
            return;
        }
        if (value instanceof Number) {
            BigDecimal number = new BigDecimal(value.toString()).stripTrailingZeros();
            appendText(digest, "n", number.toPlainString());
            return;
        }
        appendText(digest, "s", value.toString());
    }

    private static void appendText(MessageDigest digest, String type, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        update(digest, type + bytes.length + ":");
        digest.update(bytes);
        update(digest, ";");
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.US_ASCII));
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }
}
