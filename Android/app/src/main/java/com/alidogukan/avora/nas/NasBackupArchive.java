package com.alidogukan.avora.nas;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Stores one current backup plus seven rotating daily recovery points. */
public final class NasBackupArchive {
    public static final int HISTORY_LIMIT = 7;

    private NasBackupArchive() { }

    public static NasDocumentClient.Document save(String accessToken, String deviceId,
                                                   JSONObject backup)
            throws NasApiException {
        if (backup == null) throw new IllegalArgumentException("Backup is required.");
        String historyKey = historyKey(deviceId, backup.optLong(
                "created_at_epoch_ms", System.currentTimeMillis()));
        putReplacing(accessToken, historyKey, backup);
        return putReplacing(accessToken, NasDocumentClient.backupKey(deviceId), backup);
    }

    public static List<NasDocumentClient.Document> loadAvailable(String accessToken,
                                                                  String deviceId)
            throws NasApiException {
        List<NasDocumentClient.Document> documents = new ArrayList<>();
        for (String key : candidateKeys(deviceId)) {
            NasDocumentClient.Document document = NasDocumentClient.getDocument(
                    accessToken, key);
            if (document != null) documents.add(document);
        }
        return newestUnique(documents);
    }

    public static String historyKey(String deviceId, long epochMillis) {
        long epochDay = Math.floorDiv(epochMillis, TimeUnit.DAYS.toMillis(1));
        int slot = (int) Math.floorMod(epochDay, HISTORY_LIMIT);
        String key = NasDocumentClient.backupKey(deviceId) + "-history-" + slot;
        if (key.length() > 80) {
            throw new IllegalArgumentException("Device identity is too long for NAS history.");
        }
        return key;
    }

    static List<String> candidateKeys(String deviceId) {
        String primary = NasDocumentClient.backupKey(deviceId);
        List<String> keys = new ArrayList<>();
        keys.add(primary);
        for (int slot = 0; slot < HISTORY_LIMIT; slot++) {
            String key = primary + "-history-" + slot;
            if (key.length() > 80) {
                throw new IllegalArgumentException(
                        "Device identity is too long for NAS history.");
            }
            keys.add(key);
        }
        return keys;
    }

    static List<NasDocumentClient.Document> newestUnique(
            List<NasDocumentClient.Document> documents) {
        List<NasDocumentClient.Document> sorted = new ArrayList<>(documents);
        sorted.sort(Comparator
                .comparingLong(NasBackupArchive::createdAt)
                .thenComparingLong(value -> value.updatedAt)
                .reversed());
        List<NasDocumentClient.Document> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (NasDocumentClient.Document document : sorted) {
            long identity = createdAt(document);
            if (identity <= 0L) identity = document.updatedAt * 1000L;
            if (seen.add(identity)) result.add(document);
            if (result.size() == HISTORY_LIMIT) break;
        }
        return result;
    }

    private static long createdAt(NasDocumentClient.Document document) {
        return document == null || document.data == null
                ? 0L : document.data.optLong("created_at_epoch_ms", 0L);
    }

    private static NasDocumentClient.Document putReplacing(
            String accessToken, String key, JSONObject backup) throws NasApiException {
        NasDocumentClient.Document current = NasDocumentClient.getDocument(accessToken, key);
        int expectedVersion = current == null ? 0 : current.version;
        try {
            return NasDocumentClient.putDocument(
                    accessToken, key, backup, expectedVersion);
        } catch (NasApiException error) {
            if (!"NAS_CONFLICT".equals(error.getMessage())) throw error;
            NasDocumentClient.Document refreshed = NasDocumentClient.getDocument(
                    accessToken, key);
            return NasDocumentClient.putDocument(accessToken, key, backup,
                    refreshed == null ? 0 : refreshed.version);
        }
    }
}
