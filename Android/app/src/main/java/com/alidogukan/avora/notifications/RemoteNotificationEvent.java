package com.alidogukan.avora.notifications;

import android.content.Context;

import com.alidogukan.avora.R;

import java.util.Locale;
import java.util.Map;

/**
 * Language-neutral notification contract received from the garden device.
 * The Raspberry Pi sends only an event code, a stable event id and event facts;
 * Android owns category, priority and all user-facing text.
 */
public final class RemoteNotificationEvent {
    public static final String DEVICE_SENSOR_UNAVAILABLE = "DEVICE_SENSOR_UNAVAILABLE";
    public static final String DEVICE_WARNING = "DEVICE_WARNING";
    public static final String IRRIGATION_STARTED = "IRRIGATION_STARTED";
    public static final String IRRIGATION_COMPLETED = "IRRIGATION_COMPLETED";
    public static final String IRRIGATION_INTERRUPTED = "IRRIGATION_INTERRUPTED";
    public static final String FEEDBACK_RECEIVED = "FEEDBACK_RECEIVED";
    public static final String GARDEN_ACCESS_REQUEST = "GARDEN_ACCESS_REQUEST";

    private final String code;
    private final String eventId;
    private final String zoneId;
    private final long durationSeconds;

    private RemoteNotificationEvent(
            String code,
            String eventId,
            String zoneId,
            long durationSeconds
    ) {
        this.code = normalize(code);
        this.eventId = safe(eventId);
        this.zoneId = safe(zoneId);
        this.durationSeconds = Math.max(0L, durationSeconds);
    }

    public static RemoteNotificationEvent from(
            Map<String, String> data,
            String fallbackEventId
    ) {
        Map<String, String> values = data == null ? java.util.Collections.emptyMap() : data;
        String eventId = firstNonBlank(
                values.get("event_id"),
                values.get("source_key"),
                fallbackEventId
        );
        return new RemoteNotificationEvent(
                values.get("event_code"),
                eventId,
                values.get("zone_id"),
                parseLong(values.get("duration_seconds"))
        );
    }

    public String type() {
        switch (code) {
            case IRRIGATION_STARTED:
            case IRRIGATION_COMPLETED:
            case IRRIGATION_INTERRUPTED:
                return "IRRIGATION";
            case DEVICE_SENSOR_UNAVAILABLE:
            case DEVICE_WARNING:
                return "DEVICE";
            case GARDEN_ACCESS_REQUEST:
                return "ACCESS";
            case FEEDBACK_RECEIVED:
                return "FEEDBACK";
            default:
                return "SYSTEM";
        }
    }

    public String priority() {
        switch (code) {
            case DEVICE_SENSOR_UNAVAILABLE:
            case DEVICE_WARNING:
            case IRRIGATION_INTERRUPTED:
            case GARDEN_ACCESS_REQUEST:
            case FEEDBACK_RECEIVED:
                return "HIGH";
            default:
                return "NORMAL";
        }
    }

    public String incidentKey() {
        switch (code) {
            case DEVICE_SENSOR_UNAVAILABLE:
            case DEVICE_WARNING:
                return "device_error";
            default:
                return "";
        }
    }

    public String sourceKey() {
        if (!eventId.isBlank()) return eventId;
        return "remote:" + (code.isBlank() ? "UNKNOWN" : code)
                + ":" + (zoneId.isBlank() ? "garden" : zoneId);
    }

    public String zoneId() {
        return zoneId;
    }

    public String title(Context context) {
        switch (code) {
            case DEVICE_SENSOR_UNAVAILABLE:
                return context.getString(R.string.notification_sensor_unavailable_title);
            case DEVICE_WARNING:
                return context.getString(R.string.notification_device_warning_title);
            case IRRIGATION_STARTED:
                return context.getString(R.string.notification_watering_started_title);
            case IRRIGATION_COMPLETED:
                return context.getString(R.string.notification_watering_completed_title);
            case IRRIGATION_INTERRUPTED:
                return context.getString(R.string.notification_watering_interrupted_title);
            case GARDEN_ACCESS_REQUEST:
                return context.getString(R.string.notification_access_request_title);
            case FEEDBACK_RECEIVED:
                return context.getString(R.string.notification_feedback_title);
            default:
                return context.getString(R.string.notification_remote_fallback_title);
        }
    }

    public String description(Context context) {
        switch (code) {
            case DEVICE_SENSOR_UNAVAILABLE:
                return context.getString(R.string.notification_sensor_unavailable_description);
            case DEVICE_WARNING:
                return context.getString(R.string.notification_device_warning_description);
            case IRRIGATION_STARTED:
                return context.getString(
                        R.string.notification_watering_started_description,
                        durationSeconds);
            case IRRIGATION_COMPLETED:
                return context.getString(
                        R.string.notification_watering_completed_description,
                        durationSeconds);
            case IRRIGATION_INTERRUPTED:
                return context.getString(
                        R.string.notification_watering_interrupted_description,
                        durationSeconds);
            case GARDEN_ACCESS_REQUEST:
                return context.getString(R.string.notification_access_request_description);
            case FEEDBACK_RECEIVED:
                return context.getString(R.string.notification_feedback_description);
            default:
                return context.getString(R.string.notification_remote_fallback_description);
        }
    }

    private static String normalize(String value) {
        return safe(value).toUpperCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(safe(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
