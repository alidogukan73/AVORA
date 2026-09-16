package com.alidogukan.avora.feedback;

import com.alidogukan.avora.config.AppInfo;
import com.alidogukan.avora.superadmin.SuperadminDataRepository;
import com.alidogukan.avora.superadmin.SuperadminDataRepository.CommandResult;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Owner-only, human-readable feedback inbox backed by the audited Pi gateway. */
public final class FeedbackInboxRepository {
    private final SuperadminDataRepository superadmin = new SuperadminDataRepository();

    public Task<Boolean> isCurrentUserOwner() {
        return superadmin.isCurrentUserOwner();
    }

    public Task<List<Message>> load() {
        return FirebaseDatabase.getInstance().getReference("devices")
                .child(AppInfo.DEVICE_ID).child("user_feedback")
                .orderByChild("created_at").limitToLast(200).get()
                .continueWith(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        Exception error = task.getException();
                        if (error != null) throw error;
                        throw new IllegalStateException("Geri bildirimler okunamadı.");
                    }
                    List<Message> result = new ArrayList<>();
                    for (DataSnapshot child : task.getResult().getChildren()) {
                        Object raw = child.getValue();
                        if (!(raw instanceof Map)) continue;
                        @SuppressWarnings("unchecked")
                        Map<String, Object> values = new HashMap<>((Map<String, Object>) raw);
                        result.add(Message.from(child.getKey(), values));
                    }
                    result.sort(Comparator.comparingLong(Message::createdAt).reversed());
                    return result;
                });
    }

    public Task<CommandResult> updateStatus(Message message, String status) {
        if (!"new".equals(status) && !"read".equals(status)
                && !"resolved".equals(status)) {
            return Tasks.forException(new IllegalArgumentException("Geçersiz durum."));
        }
        try {
            JSONObject replacement = new JSONObject(message.rawJson);
            replacement.put("status", status);
            replacement.put("reviewed_at_epoch", System.currentTimeMillis() / 1000L);
            return superadmin.update("feedback", message.id,
                    replacement.toString(), message.rawJson);
        } catch (Exception error) {
            return Tasks.forException(error);
        }
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
