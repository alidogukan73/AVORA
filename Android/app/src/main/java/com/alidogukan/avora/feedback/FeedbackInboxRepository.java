package com.alidogukan.avora.feedback;

import com.alidogukan.avora.config.AppInfo;
import com.alidogukan.avora.superadmin.SuperadminDataRepository;
import com.alidogukan.avora.superadmin.SuperadminDataRepository.CommandResult;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Owner-only, human-readable feedback inbox backed by the audited Pi gateway. */
public final class FeedbackInboxRepository {
    private static final int PAGE_SIZE = 100;
    private final SuperadminDataRepository superadmin = new SuperadminDataRepository();

    public Task<Boolean> isCurrentUserOwner() {
        return superadmin.isCurrentUserOwner();
    }

    public Task<Page> loadPage(PageCursor before) {
        Query query = FirebaseDatabase.getInstance().getReference("feedback_devices")
                .child(AppInfo.DEVICE_ID).child("user_feedback").orderByChild("created_at");
        if (before != null) {
            query = endBefore(query, before.orderValue, before.id);
        }
        return query.limitToLast(PAGE_SIZE + 1).get().continueWith(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                Exception error = task.getException();
                if (error != null) throw error;
                throw new IllegalStateException("Geri bildirimler okunamadı.");
            }
            List<DataSnapshot> snapshots = new ArrayList<>();
            for (DataSnapshot child : task.getResult().getChildren()) {
                snapshots.add(child);
            }
            boolean hasMore = snapshots.size() > PAGE_SIZE;
            int start = hasMore ? snapshots.size() - PAGE_SIZE : 0;
            List<Message> result = new ArrayList<>();
            for (int index = start; index < snapshots.size(); index++) {
                DataSnapshot child = snapshots.get(index);
                Object raw = child.getValue();
                if (!(raw instanceof Map)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> values = new HashMap<>((Map<String, Object>) raw);
                result.add(Message.from(child.getKey(), values));
            }
            result.sort(Comparator.comparingLong(Message::createdAt).reversed()
                    .thenComparing(message -> message.id, Comparator.reverseOrder()));
            PageCursor next = hasMore && start < snapshots.size()
                    ? new PageCursor(snapshots.get(start).getKey(),
                            snapshots.get(start).child("created_at").getValue())
                    : null;
            return new Page(result, next);
        });
    }

    private static Query endBefore(Query query, Object value, String key) {
        if (value == null) return query.endBefore((String) null, key);
        if (value instanceof Boolean) return query.endBefore((Boolean) value, key);
        if (value instanceof Number) return query.endBefore(((Number) value).doubleValue(), key);
        return query.endBefore(String.valueOf(value), key);
    }

    public static final class PageCursor {
        public final String id;
        public final Object orderValue;
        private PageCursor(String id, Object orderValue) {
            this.id = id;
            this.orderValue = orderValue;
        }
    }

    public static final class Page {
        public final List<Message> messages;
        public final PageCursor next;
        private Page(List<Message> messages, PageCursor next) {
            this.messages = messages;
            this.next = next;
        }
    }

    public Task<CommandResult> updateStatus(Message message, String status) {
        if (!"new".equals(status) && !"read".equals(status)
                && !"resolved".equals(status)) {
            return Tasks.forException(new IllegalArgumentException("Geçersiz durum."));
        }
        if (message == null || message.id == null || message.id.isBlank()) {
            return Tasks.forException(new IllegalArgumentException("Geri bildirim bulunamadı."));
        }
        // Email delivery may update this record after the inbox was loaded.
        // Base the audited replacement on a fresh snapshot to avoid false conflicts.
        return FirebaseDatabase.getInstance().getReference("feedback_devices")
                .child(AppInfo.DEVICE_ID).child("user_feedback").child(message.id)
                .get().continueWithTask(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        Exception error = task.getException();
                        return Tasks.forException(error == null
                                ? new IllegalStateException("Geri bildirim okunamadı.") : error);
                    }
                    Object raw = task.getResult().getValue();
                    if (!(raw instanceof Map)) {
                        return Tasks.forException(new IllegalStateException(
                                "Geri bildirim artık mevcut değil."));
                    }
                    try {
                        JSONObject current = new JSONObject((Map<?, ?>) raw);
                        JSONObject replacement = new JSONObject(current.toString());
                        replacement.put("status", status);
                        replacement.put("reviewed_at_epoch", System.currentTimeMillis() / 1000L);
                        return superadmin.update("feedback", message.id,
                                replacement.toString(), current.toString());
                    } catch (Exception error) {
                        return Tasks.forException(error);
                    }
                });
    }

    public Task<CommandResult> delete(Message message) {
        return superadmin.deleteFeedback(message.id);
    }

    public static final class Message {
        public final String id;
        public final String type;
        public final String area;
        public final String subject;
        public final String description;
        public final String contactEmail;
        public final String status;
        public final long createdAt;
        public final String rawJson;

        private Message(String id, String type, String area, String subject,
                        String description, String contactEmail, String status,
                        long createdAt, String rawJson) {
            this.id = id;
            this.type = type;
            this.area = area;
            this.subject = subject;
            this.description = description;
            this.contactEmail = contactEmail;
            this.status = normalizeStatus(status);
            this.createdAt = createdAt;
            this.rawJson = rawJson;
        }

        static Message from(String id, Map<String, Object> values) {
            long created = number(values.get("created_at"));
            if (created > 10_000_000_000L) created /= 1000L;
            return new Message(id, text(values.get("type")),
                    text(values.get("area_label")), text(values.get("subject")),
                    text(values.get("description")), text(values.get("contact_email")),
                    text(values.get("status")), created,
                    new JSONObject(values).toString());
        }

        public boolean matches(String filter) {
            return "all".equals(filter) || status.equals(filter);
        }

        private static String normalizeStatus(String value) {
            if ("read".equalsIgnoreCase(value)) return "read";
            if ("resolved".equalsIgnoreCase(value)) return "resolved";
            return "new";
        }

        private static String text(Object value) {
            return value == null ? "" : String.valueOf(value).trim();
        }

        private static long number(Object value) {
            if (value instanceof Number) return ((Number) value).longValue();
            try { return Long.parseLong(text(value)); }
            catch (NumberFormatException ignored) { return 0L; }
        }

        public long createdAt() { return createdAt; }
    }
}
