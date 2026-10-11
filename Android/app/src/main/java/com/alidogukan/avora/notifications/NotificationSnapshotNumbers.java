package com.alidogukan.avora.notifications;

import com.google.firebase.database.DataSnapshot;

/** Firebase exposes numeric leaves as Long or Double, not a Number model. */
final class NotificationSnapshotNumbers {
    private NotificationSnapshotNumbers() { }

    static Double number(DataSnapshot snapshot) {
        Object value = snapshot.getValue();
        if (!(value instanceof Number)) return null;
        double result = ((Number) value).doubleValue();
        return Double.isFinite(result) ? result : null;
    }

    static long epoch(DataSnapshot snapshot) {
        Object value = snapshot.getValue();
        return value instanceof Number && Double.isFinite(((Number) value).doubleValue())
                ? ((Number) value).longValue() : 0L;
    }
}
