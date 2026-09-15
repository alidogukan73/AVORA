package com.alidogukan.avora.seedling;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import com.alidogukan.avora.config.AppInfo;
import com.alidogukan.avora.firebase.FirebaseLiveData;
import com.alidogukan.avora.models.SeedlingBatch;
import com.alidogukan.avora.models.SeedlingDailyLog;
import com.alidogukan.avora.models.SeedlingNodeState;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Single persistence boundary for seedling batches, logs and sensor snapshots. */
public final class SeedlingRepository {
    private final DatabaseReference root = FirebaseDatabase.getInstance()
            .getReference("devices").child(AppInfo.DEVICE_ID).child("seedling");

    public LiveData<List<SeedlingBatch>> observeBatches() {
        return observeBatches(() -> { });
    }

    public LiveData<List<SeedlingBatch>> observeBatches(
            @NonNull Runnable errorHandler) {
        DatabaseReference reference = root.child("batches");
        FirebaseLiveData<List<SeedlingBatch>> result = new FirebaseLiveData<>(reference);
        result.setEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<SeedlingBatch> values = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    SeedlingBatch value = child.getValue(SeedlingBatch.class);
                    if (value == null) continue;
                    if (value.getBatch_id().isBlank()) value.setBatch_id(child.getKey());
                    values.add(value);
                }
                values.sort(Comparator.comparingLong(SeedlingBatch::getCreated_at_epoch).reversed());
                result.setValue(values);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                errorHandler.run();
            }
        });
        return result;
    }

    public LiveData<SeedlingBatch> observeBatch(String batchId) {
        return observeBatch(batchId, () -> { });
    }

    public LiveData<SeedlingBatch> observeBatch(
            String batchId, @NonNull Runnable errorHandler) {
        DatabaseReference reference = root.child("batches").child(safeId(batchId));
        FirebaseLiveData<SeedlingBatch> result = new FirebaseLiveData<>(reference);
        result.setEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                SeedlingBatch value = snapshot.getValue(SeedlingBatch.class);
                if (value != null && value.getBatch_id().isBlank()) value.setBatch_id(snapshot.getKey());
                if (value != null) {
                    Map<String, Object> migration = legacyStageDateUpdates(value);
                    if (!migration.isEmpty()) reference.updateChildren(migration);
                }
                result.setValue(value);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                errorHandler.run();
            }
        });
        return result;
    }

    public LiveData<SeedlingNodeState> observeNode(String nodeId) {
        return observeNode(nodeId, () -> { });
    }

    public LiveData<SeedlingNodeState> observeNode(
            String nodeId, @NonNull Runnable errorHandler) {
        DatabaseReference reference = root.child("nodes").child(safeId(nodeId));
        FirebaseLiveData<SeedlingNodeState> result = new FirebaseLiveData<>(reference);
        result.setEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                result.setValue(snapshot.getValue(SeedlingNodeState.class));
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                errorHandler.run();
            }
        });
        return result;
    }

    public LiveData<List<SeedlingDailyLog>> observeLogs(String batchId) {
        return observeLogs(batchId, () -> { });
    }

    public LiveData<List<SeedlingDailyLog>> observeLogs(
            String batchId, @NonNull Runnable errorHandler) {
        DatabaseReference reference = root.child("daily_logs").child(safeId(batchId));
        FirebaseLiveData<List<SeedlingDailyLog>> result = new FirebaseLiveData<>(reference);
        result.setEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<SeedlingDailyLog> values = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    SeedlingDailyLog value = child.getValue(SeedlingDailyLog.class);
                    if (value != null) values.add(value);
                }
                values.sort((left, right) -> compareLogs(right, left));
                result.setValue(values);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                errorHandler.run();
            }
        });
        return result;
    }

    public Task<Boolean> hasDailyLogs(String batchId) {
        String id = safeId(batchId);
        FirebaseDatabase.getInstance().goOnline();
        return root.child("daily_logs").child(id).get().continueWith(task -> {
            if (!task.isSuccessful()) {
                Exception error = task.getException();
                if (error != null) throw error;
                throw new IllegalStateException("Günlük kayıtları kontrol edilemedi.");
            }
            DataSnapshot snapshot = task.getResult();
            return snapshot != null && snapshot.hasChildren();
        });
    }

    public Task<Void> create(SeedlingBatch batch) {
        if (!SeedlingValidation.isValidNewBatch(batch)) {
            return Tasks.forException(new IllegalArgumentException("Fide partisi bilgileri eksik."));
        }
        FirebaseDatabase.getInstance().goOnline();
        String key = root.child("batches").push().getKey();
        if (key == null) return Tasks.forException(new IllegalStateException("Parti kimliği üretilemedi."));
        batch.setBatch_id(key);
        return root.child("batches").child(key).setValue(batchCreateValues(batch));
    }

    /**
     * Creates a bounded Firebase payload without serialising empty lifecycle
     * fields. Empty transfer fields are invalid according to the live schema.
     */
    static Map<String, Object> batchCreateValues(SeedlingBatch batch) {
        Map<String, Object> values = new HashMap<>();
        values.put("batch_id", batch.getBatch_id());
        if (!batch.getCrop_id().isBlank()) values.put("crop_id", batch.getCrop_id());
        values.put("plant_type", batch.getPlant_type());
        values.put("emoji", batch.getEmoji());
        values.put("variety", batch.getVariety());
        values.put("area", batch.getArea());
        values.put("node_id", batch.getNode_id());
        values.put("status", SeedlingBatch.STATUS_ACTIVE);
        values.put("stage", SeedlingStagePolicy.normalize(batch.getStage()));
        values.put("sowing_date_epoch", batch.getSowing_date_epoch());
        values.put("estimated_emergence_epoch", batch.getEstimated_emergence_epoch());
        values.put("estimated_transplant_epoch", batch.getEstimated_transplant_epoch());
        values.put("seed_count", batch.getSeed_count());
        values.put("tray_cell_count", batch.getTray_cell_count());
        values.put("healthy_count", batch.getHealthy_count());
        values.put("created_at_epoch", batch.getCreated_at_epoch());
        values.put("updated_at_epoch", batch.getUpdated_at_epoch());
        return values;
    }

    public Task<Void> saveLog(SeedlingDailyLog log) {
        if (log == null || log.getBatch_id().isBlank()) {
            return Tasks.forException(new IllegalArgumentException("Fide partisi gerekli."));
        }
        final String batchId;
        try {
            batchId = safeId(log.getBatch_id());
        } catch (IllegalArgumentException error) {
            return Tasks.forException(error);
        }
        FirebaseDatabase.getInstance().goOnline();
        return root.child("batches").child(batchId).get().continueWithTask(task -> {
            if (!task.isSuccessful()) return failedRead(task.getException());
            SeedlingBatch batch = task.getResult().getValue(SeedlingBatch.class);
            if (batch == null) {
                return Tasks.forException(new IllegalStateException("Fide partisi bulunamadı."));
            }
            if (!batch.isActive()) return Tasks.forException(new BatchReadOnlyException());
            if (!SeedlingValidation.isValidLog(log, batch)) {
                return Tasks.forException(new IllegalArgumentException("Günlük bilgileri geçersiz."));
            }
            String key = root.child("daily_logs").child(batchId).push().getKey();
            if (key == null) {
                return Tasks.forException(new IllegalStateException("Günlük kimliği üretilemedi."));
            }
            log.setLog_id(key);
            Map<String, Object> updates = new HashMap<>();
            updates.put("daily_logs/" + batchId + "/" + key, log);
            updates.put("batches/" + batchId + "/healthy_count", log.getHealthy_count());
            updates.put("batches/" + batchId + "/updated_at_epoch", log.getCreated_at_epoch());
            return root.updateChildren(updates);
        });
    }

    /** Updates one observation without changing its original observation time. */
    public Task<Void> updateLog(SeedlingDailyLog log) {
        if (log == null) {
            return Tasks.forException(new IllegalArgumentException("Günlük kaydı gerekli."));
        }
        final String batchId;
        final String logId;
        try {
            batchId = safeId(log.getBatch_id());
            logId = safeId(log.getLog_id());
        } catch (IllegalArgumentException error) {
            return Tasks.forException(error);
        }
        FirebaseDatabase.getInstance().goOnline();
        return root.get().continueWithTask(task -> {
            if (!task.isSuccessful()) {
                Exception error = task.getException();
                return Tasks.forException(error == null
                        ? new IllegalStateException("Günlük kaydı kontrol edilemedi.")
                        : error);
            }
            DataSnapshot snapshot = task.getResult();
            SeedlingBatch batch = snapshot.child("batches").child(batchId)
                    .getValue(SeedlingBatch.class);
            if (batch == null) {
                return Tasks.forException(new IllegalStateException("Fide partisi bulunamadı."));
            }
            SeedlingDailyLog persisted = snapshot.child("daily_logs").child(batchId)
                    .child(logId).getValue(SeedlingDailyLog.class);
            if (persisted != null && persisted.getLog_id().isBlank()) {
                persisted.setLog_id(logId);
            }
            if (persisted == null) {
                return Tasks.forException(new IllegalStateException("Günlük kaydı bulunamadı."));
            }
            if (!SeedlingValidation.isValidLogUpdate(log, batch, persisted)) {
                return Tasks.forException(new IllegalArgumentException("Günlük bilgileri geçersiz."));
            }
            List<SeedlingDailyLog> current = logsFrom(
                    snapshot.child("daily_logs").child(batchId));
            Map<String, Object> updates = new HashMap<>();
            updates.put("daily_logs/" + batchId + "/" + logId, log);
            if (isLatestAfterUpdate(current, log)) {
                updates.put("batches/" + batchId + "/healthy_count", log.getHealthy_count());
            }
            updates.put("batches/" + batchId + "/updated_at_epoch",
                    System.currentTimeMillis() / 1000L);
            return root.updateChildren(updates);
        });
    }

    /** Deletes one observation and restores the batch summary from the newest record left. */
    public Task<Void> deleteLog(String batchIdValue, String logIdValue) {
        final String batchId;
        final String logId;
        try {
            batchId = safeId(batchIdValue);
            logId = safeId(logIdValue);
        } catch (IllegalArgumentException error) {
            return Tasks.forException(error);
        }
        FirebaseDatabase.getInstance().goOnline();
        return root.get().continueWithTask(task -> {
            if (!task.isSuccessful()) {
                Exception error = task.getException();
                return Tasks.forException(error == null
                        ? new IllegalStateException("Günlük kaydı kontrol edilemedi.")
                        : error);
            }
            DataSnapshot snapshot = task.getResult();
            DataSnapshot batchSnapshot = snapshot.child("batches").child(batchId);
            DataSnapshot logSnapshot = snapshot.child("daily_logs").child(batchId).child(logId);
            if (!batchSnapshot.exists() || !logSnapshot.exists()) {
                return Tasks.forException(new IllegalStateException("Günlük kaydı bulunamadı."));
            }
            SeedlingBatch persistedBatch = batchSnapshot.getValue(SeedlingBatch.class);
            if (persistedBatch == null || !persistedBatch.isActive()) {
                return Tasks.forException(new BatchReadOnlyException());
            }

            List<SeedlingDailyLog> current = logsFrom(
                    snapshot.child("daily_logs").child(batchId));
            SeedlingDailyLog latestRemaining = latestExcluding(current, logId);
            Long seedCountValue = batchSnapshot.child("seed_count").getValue(Long.class);
            int fallbackHealthy = seedCountValue == null ? 0
                    : (int) Math.max(0L, Math.min(Integer.MAX_VALUE, seedCountValue));

            Map<String, Object> updates = new HashMap<>();
            updates.put("daily_logs/" + batchId + "/" + logId, null);
            updates.put("batches/" + batchId + "/healthy_count",
                    latestRemaining == null
                            ? fallbackHealthy : latestRemaining.getHealthy_count());
            updates.put("batches/" + batchId + "/updated_at_epoch",
                    System.currentTimeMillis() / 1000L);
            return root.updateChildren(updates);
        });
    }

    public Task<Void> advanceStage(String batchId, String stage) {
        return updateStage(batchId, stage, true);
    }

    public Task<Void> updateStage(String batchId, String stage) {
        return updateStage(batchId, stage, false);
    }

    private Task<Void> updateStage(String batchId, String stage, boolean recordReachedAt) {
        long nowEpoch = System.currentTimeMillis() / 1000L;
        return updateActiveBatch(batchId,
                stageUpdateValues(stage, nowEpoch, recordReachedAt));
    }

    static Map<String, Object> stageUpdateValues(String stage, long nowEpoch,
                                                  boolean recordReachedAt) {
        String normalized = SeedlingStagePolicy.normalize(stage);
        Map<String, Object> values = new HashMap<>();
        values.put("stage", normalized);
        values.put("updated_at_epoch", Math.max(0L, nowEpoch));

        int progress = SeedlingStagePolicy.progress(normalized);
        if (progress < SeedlingStagePolicy.progress(SeedlingStagePolicy.READY)) {
            values.put("ready_date_epoch", null);
        }
        if (progress < SeedlingStagePolicy.progress(SeedlingStagePolicy.HARDENING)) {
            values.put("hardening_date_epoch", null);
        }
        if (progress < SeedlingStagePolicy.progress(SeedlingStagePolicy.TRUE_LEAVES)) {
            values.put("true_leaves_date_epoch", null);
        }
        if (progress < SeedlingStagePolicy.progress(SeedlingStagePolicy.COTYLEDON)) {
            values.put("first_leaf_date_epoch", null);
        }
        if (progress < SeedlingStagePolicy.progress(SeedlingStagePolicy.GERMINATING)) {
            values.put("germination_date_epoch", null);
        }

        if (recordReachedAt) {
            if (SeedlingStagePolicy.GERMINATING.equals(normalized)) {
                values.put("germination_date_epoch", nowEpoch);
            } else if (SeedlingStagePolicy.COTYLEDON.equals(normalized)) {
                values.put("first_leaf_date_epoch", nowEpoch);
            } else if (SeedlingStagePolicy.TRUE_LEAVES.equals(normalized)) {
                values.put("true_leaves_date_epoch", nowEpoch);
            } else if (SeedlingStagePolicy.HARDENING.equals(normalized)) {
                values.put("hardening_date_epoch", nowEpoch);
            } else if (SeedlingStagePolicy.READY.equals(normalized)) {
                values.put("ready_date_epoch", nowEpoch);
            }
        }
        return values;
    }

    /**
     * Older batches stored only planned dates. Fill already reached milestones once,
     * without overwriting any real date recorded by a newer app version.
     */
    static Map<String, Object> legacyStageDateUpdates(SeedlingBatch batch) {
        Map<String, Object> values = new HashMap<>();
        if (batch == null) return values;
        int completed = SeedlingTimeline.completedSteps(batch.getStage());
        long changedAt = Math.max(batch.getCreated_at_epoch(), batch.getUpdated_at_epoch());
        long previous = batch.getSowing_date_epoch();

        if (completed >= 2 && batch.getGermination_date_epoch() == null) {
            long reachedAt = reachedDate(batch.getEstimated_emergence_epoch(), changedAt, previous);
            batch.setGermination_date_epoch(reachedAt);
            values.put("germination_date_epoch", reachedAt);
        }
        Long germination = batch.getGermination_date_epoch();
        if (germination != null) previous = germination;

        if (completed >= 3 && batch.getFirst_leaf_date_epoch() == null) {
            long estimate = SeedlingTimeline.milestoneEpochs(batch)[2];
            long reachedAt = reachedDate(estimate, changedAt, previous);
            batch.setFirst_leaf_date_epoch(reachedAt);
            values.put("first_leaf_date_epoch", reachedAt);
        }
        Long firstLeaf = batch.getFirst_leaf_date_epoch();
        if (firstLeaf != null) previous = firstLeaf;

        int progress = SeedlingStagePolicy.progress(batch.getStage());
        if (progress >= SeedlingStagePolicy.progress(SeedlingStagePolicy.TRUE_LEAVES)
                && batch.getTrue_leaves_date_epoch() == null) {
            long estimate = previous + SeedlingCropCatalog
                    .profileForPlant(batch.getPlant_type())
                    .getTrueLeavesAfterFirstLeafDays() * 86_400L;
            long reachedAt = reachedDate(estimate, changedAt, previous);
            batch.setTrue_leaves_date_epoch(reachedAt);
            values.put("true_leaves_date_epoch", reachedAt);
        }
        Long trueLeaves = batch.getTrue_leaves_date_epoch();
        if (trueLeaves != null) previous = trueLeaves;

        if (completed >= 4 && batch.getHardening_date_epoch() == null) {
            long reachedAt = reachedDate(batch.getEstimated_transplant_epoch(), changedAt, previous);
            batch.setHardening_date_epoch(reachedAt);
            values.put("hardening_date_epoch", reachedAt);
        }
        Long hardening = batch.getHardening_date_epoch();
        if (hardening != null) previous = hardening;

        if (completed >= 5 && batch.getReady_date_epoch() == null) {
            long reachedAt = Math.max(previous, changedAt);
            batch.setReady_date_epoch(reachedAt);
            values.put("ready_date_epoch", reachedAt);
        }
        return values;
    }

    private static long reachedDate(long estimate, long changedAt, long previous) {
        long upper = Math.max(previous, changedAt);
        if (estimate <= 0L) return upper;
        return Math.max(previous, Math.min(estimate, upper));
    }

    /** Hides a batch from active tracking without deleting its history. */
    public Task<Void> archiveBatch(String batchId) {
        long now = System.currentTimeMillis() / 1000L;
        return updateActiveBatch(batchId, manualArchiveUpdateValues(now));
    }

    /** Restores only manually archived batches; transferred batches stay linked to their season. */
    public Task<Void> restoreBatch(SeedlingBatch batch) {
        if (batch == null || !batch.isArchived()) {
            return Tasks.forException(new IllegalArgumentException(
                    "Arşivlenmiş fide partisi gerekli."));
        }
        if (batch.isTransferred()) {
            return Tasks.forException(new IllegalStateException(
                    "Sezona aktarılmış fide partisi doğrudan geri alınamaz."));
        }
        final String id;
        try {
            id = safeId(batch.getBatch_id());
        } catch (IllegalArgumentException error) {
            return Tasks.forException(error);
        }
        FirebaseDatabase.getInstance().goOnline();
        return root.child("batches").child(id).get().continueWithTask(task -> {
            if (!task.isSuccessful()) return failedRead(task.getException());
            SeedlingBatch persisted = task.getResult().getValue(SeedlingBatch.class);
            if (persisted == null || !persisted.isArchived()) {
                return Tasks.forException(new IllegalStateException(
                        "Arşivlenmiş fide partisi bulunamadı."));
            }
            if (persisted.isTransferred()) {
                return Tasks.forException(new IllegalStateException(
                        "Sezona aktarılmış fide partisi doğrudan geri alınamaz."));
            }
            return root.child("batches").child(id).updateChildren(
                    restoreUpdateValues(System.currentTimeMillis() / 1000L));
        });
    }

    static Map<String, Object> manualArchiveUpdateValues(long nowEpoch) {
        long now = Math.max(0L, nowEpoch);
        Map<String, Object> values = new HashMap<>();
        values.put("status", SeedlingBatch.STATUS_ARCHIVED);
        values.put("archive_reason", SeedlingBatch.ARCHIVE_REASON_MANUAL);
        values.put("archived_at_epoch", now);
        values.put("updated_at_epoch", now);
        return values;
    }

    static Map<String, Object> restoreUpdateValues(long nowEpoch) {
        long now = Math.max(0L, nowEpoch);
        Map<String, Object> values = new HashMap<>();
        values.put("status", SeedlingBatch.STATUS_ACTIVE);
        values.put("archive_reason", null);
        values.put("archived_at_epoch", 0L);
        values.put("updated_at_epoch", now);
        return values;
    }

    /** Deletes an empty batch, while preserving every batch that owns a daily log. */
    public Task<Void> deleteBatch(String batchId) {
        FirebaseDatabase.getInstance().goOnline();
        final String id;
        try {
            id = safeId(batchId);
        } catch (IllegalArgumentException error) {
            return Tasks.forException(error);
        }
        return root.get().continueWithTask(task -> {
            if (!task.isSuccessful()) {
                Exception error = task.getException();
                return Tasks.forException(error == null
                        ? new IllegalStateException("Günlük kayıtları kontrol edilemedi.")
                        : error);
            }
            DataSnapshot snapshot = task.getResult();
            SeedlingBatch persisted = snapshot.child("batches").child(id)
                    .getValue(SeedlingBatch.class);
            if (persisted == null) {
                return Tasks.forException(new IllegalStateException("Fide partisi bulunamadı."));
            }
            if (persisted.isTransferred()) {
                return Tasks.forException(new BatchTransferredException());
            }
            if (snapshot.child("daily_logs").child(id).hasChildren()) {
                return Tasks.forException(new BatchHasDailyLogsException());
            }
            return root.updateChildren(batchDeletionUpdates(id));
        });
    }

    static Map<String, Object> batchDeletionUpdates(String batchId) {
        String id = safeId(batchId);
        Map<String, Object> updates = new HashMap<>();
        updates.put("batches/" + id, null);
        return updates;
    }

    static SeedlingDailyLog latestExcluding(List<SeedlingDailyLog> values,
                                             String excludedLogId) {
        if (values == null) return null;
        String excluded = excludedLogId == null ? "" : excludedLogId;
        SeedlingDailyLog latest = null;
        for (SeedlingDailyLog value : values) {
            if (value == null || excluded.equals(value.getLog_id())) continue;
            if (latest == null || compareLogs(value, latest) > 0) latest = value;
        }
        return latest;
    }

    static boolean isLatestAfterUpdate(List<SeedlingDailyLog> values,
                                       SeedlingDailyLog updated) {
        if (updated == null) return false;
        SeedlingDailyLog latestOther = latestExcluding(values, updated.getLog_id());
        return latestOther == null || compareLogs(updated, latestOther) >= 0;
    }

    private static List<SeedlingDailyLog> logsFrom(DataSnapshot snapshot) {
        List<SeedlingDailyLog> values = new ArrayList<>();
        if (snapshot == null) return values;
        for (DataSnapshot child : snapshot.getChildren()) {
            SeedlingDailyLog value = child.getValue(SeedlingDailyLog.class);
            if (value == null) continue;
            if (value.getLog_id().isBlank() && child.getKey() != null) {
                value.setLog_id(child.getKey());
            }
            values.add(value);
        }
        return values;
    }

    private static int compareLogs(SeedlingDailyLog left, SeedlingDailyLog right) {
        int epoch = Long.compare(left.getCreated_at_epoch(), right.getCreated_at_epoch());
        if (epoch != 0) return epoch;
        return left.getLog_id().compareTo(right.getLog_id());
    }

    public static final class BatchHasDailyLogsException extends IllegalStateException {
        public BatchHasDailyLogsException() {
            super("Günlük kaydı bulunan fide partisi silinemez.");
        }
    }

    public static final class BatchTransferredException extends IllegalStateException {
        public BatchTransferredException() {
            super("Sezona bağlı fide partisi doğrudan silinemez.");
        }
    }

    public static final class BatchReadOnlyException extends IllegalStateException {
        public BatchReadOnlyException() {
            super("Arşivlenmiş fide partisinde bu işlem yapılamaz.");
        }
    }

    private static Task<Void> failedRead(Exception error) {
        return Tasks.forException(error == null
                ? new IllegalStateException("Fide partisi kontrol edilemedi.") : error);
    }

    private Task<Void> updateActiveBatch(String batchId, Map<String, Object> values) {
        final String id;
        try {
            id = safeId(batchId);
        } catch (IllegalArgumentException error) {
            return Tasks.forException(error);
        }
        FirebaseDatabase.getInstance().goOnline();
        DatabaseReference reference = root.child("batches").child(id);
        return reference.get().continueWithTask(task -> {
            if (!task.isSuccessful()) return failedRead(task.getException());
            SeedlingBatch persisted = task.getResult().getValue(SeedlingBatch.class);
            if (persisted == null) {
                return Tasks.forException(new IllegalStateException("Fide partisi bulunamadı."));
            }
            if (!persisted.isActive()) return Tasks.forException(new BatchReadOnlyException());
            return reference.updateChildren(values);
        });
    }

    private static String safeId(String value) {
        String result = value == null ? "" : value.trim();
        if (result.isBlank() || result.contains("/") || result.contains(".")
                || result.contains("#") || result.contains("$") || result.contains("[")
                || result.contains("]")) throw new IllegalArgumentException("Geçersiz kimlik.");
        return result;
    }
}
