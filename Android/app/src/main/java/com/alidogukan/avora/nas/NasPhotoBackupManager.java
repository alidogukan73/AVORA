package com.alidogukan.avora.nas;

import android.content.Context;

import com.alidogukan.avora.backup.GardenPhotoArchiveManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Performs incremental, non-deleting photo backup and missing-only restore. */
public final class NasPhotoBackupManager {
    private final Context context;
    private final GardenPhotoArchiveManager archiveManager;

    public NasPhotoBackupManager(Context context) {
        this.context = context.getApplicationContext();
        this.archiveManager = new GardenPhotoArchiveManager(this.context);
    }

    public BackupResult backup(String accessToken) throws Exception {
        List<GardenPhotoArchiveManager.NasPhotoSource> local =
                archiveManager.collectNasPhotos();
        List<NasPhotoClient.PhotoRecord> remote = NasPhotoClient.listPhotos(accessToken);
        Map<String, NasPhotoClient.PhotoRecord> remoteById = new HashMap<>();
        for (NasPhotoClient.PhotoRecord item : remote) remoteById.put(item.id, item);

        int uploaded = 0;
        int metadataUpdated = 0;
        int unchanged = 0;
        for (GardenPhotoArchiveManager.NasPhotoSource source : local) {
            NasPhotoClient.PhotoRecord current = remoteById.get(source.id);
            SyncAction action = decideSyncAction(current != null, source.sha256,
                    current == null ? "" : current.sha256,
                    current != null && jsonEquivalent(source.metadata, current.metadata));
            if (action == SyncAction.UPLOAD) {
                current = NasPhotoClient.uploadPhoto(accessToken, source.id, source.file);
                uploaded++;
            }
            if (action == SyncAction.UPLOAD || action == SyncAction.UPDATE_METADATA) {
                current = NasPhotoClient.updateMetadata(
                        accessToken, source.id, source.metadata);
                metadataUpdated++;
            } else if (action == SyncAction.UNCHANGED) {
                unchanged++;
            }
            remoteById.put(source.id, current);
        }
        return new BackupResult(local.size(), remoteById.size(), uploaded,
                metadataUpdated, unchanged, System.currentTimeMillis());
    }

    public Inspection inspect(String accessToken) throws Exception {
        List<GardenPhotoArchiveManager.NasPhotoSource> local =
                archiveManager.collectNasPhotos();
        Set<String> localIds = new HashSet<>();
        for (GardenPhotoArchiveManager.NasPhotoSource source : local) localIds.add(source.id);
        List<NasPhotoClient.PhotoRecord> remote = NasPhotoClient.listPhotos(accessToken);
        int missing = 0;
        long missingBytes = 0L;
        long latestUpdatedAt = 0L;
        for (NasPhotoClient.PhotoRecord item : remote) {
            latestUpdatedAt = Math.max(latestUpdatedAt, item.updatedAt);
            if (!localIds.contains(item.id)) {
                missing++;
                missingBytes += item.sizeBytes;
            }
        }
        return new Inspection(local.size(), remote.size(), missing,
                missingBytes, latestUpdatedAt);
    }

    public RestoreResult restoreMissing(String accessToken) throws Exception {
        List<GardenPhotoArchiveManager.NasPhotoSource> local =
                archiveManager.collectNasPhotos();
        Set<String> localIds = new HashSet<>();
        for (GardenPhotoArchiveManager.NasPhotoSource source : local) localIds.add(source.id);
        List<NasPhotoClient.PhotoRecord> remote = NasPhotoClient.listPhotos(accessToken);
        List<NasPhotoClient.PhotoRecord> missing = new ArrayList<>();
        for (NasPhotoClient.PhotoRecord item : remote) {
            if (!localIds.contains(item.id)) missing.add(item);
        }
        if (missing.isEmpty()) {
            return new RestoreResult(remote.size(), 0, remote.size());
        }

        File staging = new File(context.getCacheDir(), "nas_photo_restore_" + UUID.randomUUID());
        if (!staging.mkdirs()) {
            throw new IllegalStateException("NAS_PHOTO_STORAGE");
        }
        try {
            List<GardenPhotoArchiveManager.NasPhotoImport> imports =
                    new ArrayList<>(missing.size());
            for (NasPhotoClient.PhotoRecord item : missing) {
                File target = new File(staging, item.id + ".jpg");
                NasPhotoClient.downloadPhoto(accessToken, item, target);
                JSONObject metadata = new JSONObject(item.metadata.toString());
                metadata.remove("local_path");
                metadata.put("id", item.id);
                imports.add(new GardenPhotoArchiveManager.NasPhotoImport(
                        item.id, target, item.sizeBytes, item.sha256, metadata));
            }
            GardenPhotoArchiveManager.ArchiveResult result =
                    archiveManager.restoreNasPhotos(imports);
            int existing = remote.size() - missing.size() + result.skippedCount;
            return new RestoreResult(remote.size(), result.photoCount, existing);
        } finally {
            deleteTree(staging);
        }
    }

    static SyncAction decideSyncAction(boolean remoteExists, String localSha,
                                       String remoteSha, boolean metadataEqual) {
        if (!remoteExists || localSha == null || !localSha.equals(remoteSha)) {
            return SyncAction.UPLOAD;
        }
        return metadataEqual ? SyncAction.UNCHANGED : SyncAction.UPDATE_METADATA;
    }

    enum SyncAction { UPLOAD, UPDATE_METADATA, UNCHANGED }

    static boolean jsonEquivalent(Object left, Object right) {
        if (left == right) return true;
        if (left == null || right == null || left == JSONObject.NULL || right == JSONObject.NULL) {
            return (left == null || left == JSONObject.NULL)
                    && (right == null || right == JSONObject.NULL);
        }
        if (left instanceof JSONObject && right instanceof JSONObject) {
            JSONObject leftObject = (JSONObject) left;
            JSONObject rightObject = (JSONObject) right;
            if (leftObject.length() != rightObject.length()) return false;
            Iterator<String> keys = leftObject.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!rightObject.has(key)
                        || !jsonEquivalent(leftObject.opt(key), rightObject.opt(key))) return false;
            }
            return true;
        }
        if (left instanceof JSONArray && right instanceof JSONArray) {
            JSONArray leftArray = (JSONArray) left;
            JSONArray rightArray = (JSONArray) right;
            if (leftArray.length() != rightArray.length()) return false;
            for (int index = 0; index < leftArray.length(); index++) {
                if (!jsonEquivalent(leftArray.opt(index), rightArray.opt(index))) return false;
            }
            return true;
        }
        if (left instanceof Number && right instanceof Number) {
            return left.toString().equals(right.toString());
        }
        return left.equals(right);
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteTree(child);
        }
        file.delete();
    }

    public static final class BackupResult {
        public final int localCount;
        public final int remoteCount;
        public final int uploadedCount;
        public final int metadataUpdatedCount;
        public final int unchangedCount;
        public final long completedAtEpochMs;

        BackupResult(int localCount, int remoteCount, int uploadedCount,
                     int metadataUpdatedCount, int unchangedCount,
                     long completedAtEpochMs) {
            this.localCount = localCount;
            this.remoteCount = remoteCount;
            this.uploadedCount = uploadedCount;
            this.metadataUpdatedCount = metadataUpdatedCount;
            this.unchangedCount = unchangedCount;
            this.completedAtEpochMs = completedAtEpochMs;
        }
    }

    public static final class Inspection {
        public final int localCount;
        public final int remoteCount;
        public final int missingOnPhoneCount;
        public final long missingBytes;
        public final long latestUpdatedAtEpochSeconds;

        Inspection(int localCount, int remoteCount, int missingOnPhoneCount,
                   long missingBytes, long latestUpdatedAtEpochSeconds) {
            this.localCount = localCount;
            this.remoteCount = remoteCount;
            this.missingOnPhoneCount = missingOnPhoneCount;
            this.missingBytes = missingBytes;
            this.latestUpdatedAtEpochSeconds = latestUpdatedAtEpochSeconds;
        }
    }

    public static final class RestoreResult {
        public final int remoteCount;
        public final int restoredCount;
        public final int existingCount;

        RestoreResult(int remoteCount, int restoredCount, int existingCount) {
            this.remoteCount = remoteCount;
            this.restoredCount = restoredCount;
            this.existingCount = existingCount;
        }
    }
}
