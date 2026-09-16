package com.alidogukan.avora.superadmin;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.alidogukan.avora.config.AppInfo;
import com.alidogukan.avora.firebase.DeviceOwnershipPolicy;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owner-only Android gateway. Mutations are executed by the Raspberry Pi. */
public final class SuperadminDataRepository {
    private static final long COMMAND_TIMEOUT_MILLIS = 60_000L;
    private static final int RECORD_PAGE_SIZE = 60;
    private static final Map<String, String> CATEGORY_PATHS = categoryPaths();
    private static final Map<String, String> CATEGORY_ORDER_FIELDS = categoryOrderFields();

    private final DatabaseReference device = FirebaseDatabase.getInstance()
            .getReference("devices").child(AppInfo.DEVICE_ID);
    private final DatabaseReference superadmin = FirebaseDatabase.getInstance()
            .getReference("superadmin_devices").child(AppInfo.DEVICE_ID);

    public Task<Boolean> isCurrentUserOwner() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return Tasks.forResult(false);
        return user.getIdToken(true).continueWith(task -> task.isSuccessful()
                && task.getResult() != null
                && DeviceOwnershipPolicy.ownsDevice(
                task.getResult().getClaims(), AppInfo.DEVICE_ID));
    }

    public Task<RecordPage> loadRecordPage(String category, PageCursor before) {
        String path = categoryPath(category);
        String orderField = CATEGORY_ORDER_FIELDS.get(category);
        DatabaseReference collection = "feedback".equals(category)
                ? FirebaseDatabase.getInstance().getReference("feedback_devices")
                        .child(AppInfo.DEVICE_ID).child("user_feedback")
                : device.child(path);
        Query query = orderField == null || orderField.isBlank()
                ? collection.orderByKey()
                : collection.orderByChild(orderField);
        if (before != null) {
            query = orderField == null || orderField.isBlank()
                    ? query.endBefore(before.key)
                    : endBefore(query, before.orderValue, before.key);
        }
        return query.limitToLast(RECORD_PAGE_SIZE + 1).get().continueWith(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                Exception error = task.getException();
                if (error != null) throw error;
                throw new IllegalStateException("Kayıtlar okunamadı.");
            }
            List<PageEntry> entries = new ArrayList<>();
            for (DataSnapshot child : task.getResult().getChildren()) {
                Object raw = child.getValue();
                if (!(raw instanceof Map)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> values = new HashMap<>((Map<String, Object>) raw);
                Object ordered = orderField == null || orderField.isBlank()
                        ? null : child.child(orderField).getValue();
                entries.add(new PageEntry(
                        child.getKey(), ordered,
                        recordItem(category, child.getKey(), values)
                ));
            }
            boolean hasMore = entries.size() > RECORD_PAGE_SIZE;
            int firstVisible = hasMore ? 1 : 0;
            List<RecordItem> result = new ArrayList<>();
            for (int index = firstVisible; index < entries.size(); index++) {
                result.add(entries.get(index).record);
            }
            result.sort(Comparator.comparingLong(RecordItem::epoch).reversed()
                    .thenComparing(RecordItem::title, String.CASE_INSENSITIVE_ORDER));
            PageCursor next = null;
            if (hasMore && firstVisible < entries.size()) {
                PageEntry oldestVisible = entries.get(firstVisible);
                next = new PageCursor(oldestVisible.key, oldestVisible.orderValue);
            }
            return new RecordPage(result, next, hasMore);
        });
    }

    public Task<String> loadRecordJson(String category, String recordId) {
        DatabaseReference collection = "feedback".equals(category)
                ? FirebaseDatabase.getInstance().getReference("feedback_devices")
                        .child(AppInfo.DEVICE_ID).child("user_feedback")
                : device.child(categoryPath(category));
        return collection.child(safeId(recordId)).get()
                .continueWith(task -> {
                    if (!task.isSuccessful() || task.getResult() == null
                            || !task.getResult().exists()) {
                        Exception error = task.getException();
                        if (error != null) throw error;
                        throw new IllegalStateException("Kayıt bulunamadı.");
                    }
                    Object value = task.getResult().getValue();
                    if (!(value instanceof Map)) {
                        throw new IllegalStateException("Kayıt düzenlenebilir nesne değil.");
                    }
                    return new JSONObject((Map<?, ?>) value).toString(2);
                });
    }

    public Task<CommandResult> previewDelete(String category, String recordId) {
        return submit("preview_delete", category, recordId, null, null, null, null);
    }

    public Task<CommandResult> delete(
            String category, String recordId, String previewToken
    ) {
        return submit("delete", category, recordId, null, null, previewToken, null);
    }

    public Task<CommandResult> deleteFeedback(String recordId) {
        return submit("delete_feedback", "feedback", recordId, null, null, null, null);
    }

    public Task<CommandResult> update(
            String category, String recordId, String replacementJson,
            String expectedRecordJson
    ) {
        return submit("update", category, recordId, replacementJson, null, null,
                expectedRecordJson);
    }

    public Task<CommandResult> restore(String backupId) {
        return submit("restore", "seasons", "restore", null, backupId, null, null);
    }

    public Task<List<AuditItem>> loadAudit() {
        return superadmin.child("audit")
                .orderByChild("completed_at_epoch")
                .limitToLast(30)
                .get().continueWith(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                Exception error = task.getException();
                if (error != null) throw error;
                throw new IllegalStateException("İşlem geçmişi okunamadı.");
            }
            List<AuditItem> values = new ArrayList<>();
            for (DataSnapshot child : task.getResult().getChildren()) {
                values.add(new AuditItem(
                        string(child.child("operation").getValue()),
                        string(child.child("category").getValue()),
                        string(child.child("record_id").getValue()),
                        longValue(child.child("completed_at_epoch").getValue()),
                        intValue(child.child("affected_count").getValue()),
                        string(child.child("backup_id").getValue())
                ));
            }
            values.sort(Comparator.comparingLong(AuditItem::epoch).reversed());
            return values;
        });
    }

    private Task<CommandResult> submit(
            String operation,
            String category,
            String recordId,
            String replacementJson,
            String backupId,
            String previewToken,
            String expectedRecordJson
    ) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return Tasks.forException(new IllegalStateException(
                    "Superadmin oturumu bulunamadı."));
        }
        if (!"restore".equals(operation)) categoryPath(category);
        String id = UUID.randomUUID().toString();
        Map<String, Object> values = new HashMap<>();
        values.put("id", id);
        values.put("operation", operation);
        values.put("category", category);
        values.put("record_id", safeId(recordId));
        values.put("requested_by_uid", user.getUid());
        values.put("requested_at", ServerValue.TIMESTAMP);
        values.put("expires_at", System.currentTimeMillis() + 300_000L);
        values.put("source", "android");
        values.put("status", "pending");
        if (replacementJson != null) values.put("replacement_json", replacementJson);
        if (expectedRecordJson != null) {
            values.put("expected_record_json", expectedRecordJson);
        }
        if (backupId != null) values.put("backup_id", safeId(backupId));
        if (previewToken != null) values.put("preview_token", safePreviewToken(previewToken));

        DatabaseReference command = superadmin.child("commands").child(id);
        return command.setValue(values).continueWithTask(task -> {
            if (!task.isSuccessful()) {
                Exception error = task.getException();
                return Tasks.forException(error == null
                        ? new IllegalStateException("Yönetim isteği gönderilemedi.") : error);
            }
            return awaitResult(command);
        });
    }

    private Task<CommandResult> awaitResult(DatabaseReference command) {
        TaskCompletionSource<CommandResult> source = new TaskCompletionSource<>();
        AtomicBoolean completed = new AtomicBoolean();
        Handler handler = new Handler(Looper.getMainLooper());
        ValueEventListener listener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                String status = string(snapshot.child("status").getValue());
                if (!"completed".equals(status) && !"failed".equals(status)) return;
                if (!completed.compareAndSet(false, true)) return;
                command.removeEventListener(this);
                String message = string(snapshot.child("result_message").getValue());
                if ("failed".equals(status)) {
                    source.setException(new IllegalStateException(message.isBlank()
                            ? "Yönetim işlemi tamamlanamadı." : message));
                    return;
                }
                Map<String, Integer> groups = new LinkedHashMap<>();
                for (DataSnapshot child : snapshot.child("preview/groups").getChildren()) {
                    groups.put(child.getKey(), intValue(child.getValue()));
                }
                source.setResult(new CommandResult(
                        message,
                        intValue(snapshot.child("affected_count").getValue()),
                        intValue(snapshot.child("preview/affected_count").getValue()),
                        string(snapshot.child("backup_id").getValue()),
                        string(snapshot.child("preview/token").getValue()),
                        groups
                ));
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {
                if (!completed.compareAndSet(false, true)) return;
                source.setException(error.toException());
            }
        };
        command.addValueEventListener(listener);
        handler.postDelayed(() -> {
            if (!completed.compareAndSet(false, true)) return;
            command.removeEventListener(listener);
            source.setException(new IllegalStateException(
                    "Raspberry Pi yanıt vermedi. İşlem durumunu yenileyin."));
        }, COMMAND_TIMEOUT_MILLIS);
        return source.getTask();
    }

    private static RecordItem recordItem(
            String category, String id, Map<String, Object> values
    ) {
        String title;
        String subtitle;
        switch (category) {
            case "zones":
                title = first(values, "area_name", "name", "zone_id");
                subtitle = first(values, "name", "plant_type", "sensor_id");
                break;
            case "seasons":
                title = first(values, "zone_name", "plant_type", "label");
                subtitle = join(first(values, "label"), first(values, "status"));
                break;
            case "seedling_batches":
                title = first(values, "crop_name", "plant_name", "variety", "batch_id");
                subtitle = join(first(values, "variety"), first(values, "status"));
                break;
            case "journal_events":
                title = first(values, "type", "title", "source");
                subtitle = join(first(values, "zone_id"), first(values, "source"));
                break;
            case "photos":
                title = first(values, "analysis_title", "note", "id");
                subtitle = join(first(values, "zone_id"), first(values, "season_id"));
                break;
            case "watering":
                title = "Sulama · " + first(values, "zone_id");
                subtitle = first(values, "status", "decision", "season_id");
                break;
            case "fertilizer":
                title = first(values, "product_name", "fertilizer_name", "zone_id");
                subtitle = join(first(values, "zone_id"), first(values, "source"));
                break;
            case "notifications":
                title = first(values, "title", "type", "message");
                subtitle = join(first(values, "zone_id"), first(values, "status"));
                break;
            case "feedback":
                title = first(values, "subject", "type", "area_label");
                subtitle = join(first(values, "area_label"), first(values, "status"));
                break;
            default:
                title = id;
                subtitle = "";
        }
        if (title.isBlank()) title = id;
        return new RecordItem(id, title, subtitle, epoch(values));
    }

    private static long epoch(Map<String, Object> values) {
        String[] fields = {"updated_at_epoch", "created_at_epoch", "created_at",
                "started_at_epoch", "occurred_at_epoch", "captured_at_epoch",
                "applied_at_epoch"};
        for (String field : fields) {
            long value = longValue(values.get(field));
            if (value > 10_000_000_000L) value /= 1000L;
            if (value > 0L) return value;
        }
        return 0L;
    }

    private static String first(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            String value = string(values.get(key));
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private static String join(String first, String second) {
        if (first.isBlank()) return second;
        if (second.isBlank() || first.equalsIgnoreCase(second)) return first;
        return first + " · " + second;
    }

    private static Map<String, String> categoryPaths() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("zones", "zones");
        values.put("seasons", "garden_journal/seasons");
        values.put("seedling_batches", "seedling/batches");
        values.put("journal_events", "garden_journal/events");
        values.put("photos", "garden_journal/photo_metadata");
        values.put("watering", "watering_history");
        values.put("fertilizer", "fertilizer_history");
        values.put("notifications", "notifications");
        values.put("feedback", "user_feedback");
        return values;
    }

    private static Map<String, String> categoryOrderFields() {
        Map<String, String> values = new HashMap<>();
        values.put("zones", "updated_at_epoch");
        values.put("seasons", "updated_at_epoch");
        values.put("seedling_batches", "updated_at_epoch");
        values.put("journal_events", "occurred_at_epoch");
        values.put("photos", "captured_at_epoch");
        values.put("watering", "");
        values.put("fertilizer", "applied_at_epoch");
        values.put("notifications", "created_at_epoch");
        values.put("feedback", "created_at");
        return values;
    }

    private static String categoryPath(String category) {
        String path = CATEGORY_PATHS.get(category);
        if (path == null) throw new IllegalArgumentException("Geçersiz veri türü.");
        return path;
    }

    private static Query endBefore(Query query, Object value, String key) {
        if (value == null) return query.endBefore((String) null, key);
        if (value instanceof Boolean) {
            return query.endBefore((Boolean) value, key);
        }
        if (value instanceof Number) {
            return query.endBefore(((Number) value).doubleValue(), key);
        }
        return query.endBefore(String.valueOf(value), key);
    }

    private static String safeId(String value) {
        String result = value == null ? "" : value.trim();
        if (result.isBlank() || result.length() > 180
                || result.matches(".*[./#$\\[\\]/].*")) {
            throw new IllegalArgumentException("Geçersiz kayıt kimliği.");
        }
        return result;
    }

    private static String safePreviewToken(String value) {
        String result = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!result.matches("^[0-9a-f]{64}$")) {
            throw new IllegalArgumentException("Silme önizlemesi geçersiz.");
        }
        return result;
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static long longValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private static int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    public static final class RecordItem {
        public final String id;
        public final String title;
        public final String subtitle;
        private final long epoch;

        RecordItem(String id, String title, String subtitle, long epoch) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.epoch = epoch;
        }

        long epoch() { return epoch; }
        String title() { return title; }
    }

    private static final class PageEntry {
        final String key;
        final Object orderValue;
        final RecordItem record;

        PageEntry(String key, Object orderValue, RecordItem record) {
            this.key = key;
            this.orderValue = orderValue;
            this.record = record;
        }
    }

    public static final class PageCursor {
        final String key;
        final Object orderValue;

        PageCursor(String key, Object orderValue) {
            this.key = key;
            this.orderValue = orderValue;
        }
    }

    public static final class RecordPage {
        public final List<RecordItem> records;
        public final PageCursor nextCursor;
        public final boolean hasMore;

        RecordPage(List<RecordItem> records, PageCursor nextCursor, boolean hasMore) {
            this.records = records;
            this.nextCursor = nextCursor;
            this.hasMore = hasMore;
        }
    }

    public static final class CommandResult {
        public final String message;
        public final int affectedCount;
        public final int previewCount;
        public final String backupId;
        public final String previewToken;
        public final Map<String, Integer> previewGroups;

        CommandResult(String message, int affectedCount, int previewCount,
                      String backupId, String previewToken,
                      Map<String, Integer> previewGroups) {
            this.message = message;
            this.affectedCount = affectedCount;
            this.previewCount = previewCount;
            this.backupId = backupId;
            this.previewToken = previewToken;
            this.previewGroups = previewGroups;
        }
    }

    public static final class AuditItem {
        public final String operation;
        public final String category;
        public final String recordId;
        public final long epoch;
        public final int affectedCount;
        public final String backupId;

        AuditItem(String operation, String category, String recordId,
                  long epoch, int affectedCount, String backupId) {
            this.operation = operation;
            this.category = category;
            this.recordId = recordId;
            this.epoch = epoch;
            this.affectedCount = affectedCount;
            this.backupId = backupId;
        }

        long epoch() { return epoch; }
    }
}
