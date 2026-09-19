package com.alidogukan.avora.notifications;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class RemoteNotificationEventTest {
    @Test public void feedbackTargetsInboxWithStableDeduplicationKey() {
        RemoteNotificationEvent event = RemoteNotificationEvent.from(Map.of(
                "event_code", "FEEDBACK_RECEIVED", "event_id", "feedback:report-1"), "fcm-id");
        assertEquals("FEEDBACK", event.type());
        assertEquals("HIGH", event.priority());
        assertEquals("feedback:report-1", event.sourceKey());
        assertEquals("device", NotificationPolicy.categoryFor(event.type()));
        assertEquals(NotificationActionRouter.Destination.FEEDBACK_INBOX,
                NotificationActionRouter.destinationFor(event.type(), event.sourceKey(), ""));
    }


    @Test
    public void sensorFailure_isMappedByAndroidAndKeepsStableEventId() {
        Map<String, String> data = new HashMap<>();
        data.put("event_code", RemoteNotificationEvent.DEVICE_SENSOR_UNAVAILABLE);
        data.put("event_id", "device-error:incident:abc");
        data.put("zone_id", "");

        RemoteNotificationEvent event = RemoteNotificationEvent.from(data, "fcm-id");

        assertEquals("DEVICE", event.type());
        assertEquals("HIGH", event.priority());
        assertEquals("device_error", event.incidentKey());
        assertEquals("device-error:incident:abc", event.sourceKey());
    }

    @Test
    public void wateringEvent_usesEventIdForCrossChannelDeduplication() {
        Map<String, String> data = new HashMap<>();
        data.put("event_code", RemoteNotificationEvent.IRRIGATION_COMPLETED);
        data.put("event_id", "watering:zone-004:record-42");
        data.put("zone_id", "zone-004");
        data.put("duration_seconds", "120");

        RemoteNotificationEvent event = RemoteNotificationEvent.from(data, "fcm-id");

        assertEquals("IRRIGATION", event.type());
        assertEquals("NORMAL", event.priority());
        assertEquals("", event.incidentKey());
        assertEquals("zone-004", event.zoneId());
        assertEquals("watering:zone-004:record-42", event.sourceKey());
    }

    @Test
    public void legacyTextCannotOverrideAndroidLanguage() {
        Map<String, String> data = new HashMap<>();
        data.put("title", "Backend supplied title");
        data.put("description", "Backend supplied description");
        data.put("source_key", "legacy-event-1");

        RemoteNotificationEvent event = RemoteNotificationEvent.from(data, "fcm-id");

        assertEquals("SYSTEM", event.type());
        assertEquals("NORMAL", event.priority());
        assertEquals("legacy-event-1", event.sourceKey());
    }

    @Test
    public void accessRequest_isHighPriorityAndKeepsItsRequestId() {
        Map<String, String> data = new HashMap<>();
        data.put("event_code", RemoteNotificationEvent.GARDEN_ACCESS_REQUEST);
        data.put("event_id", "access-request:3cb18d21-d1db-4c75-a499-b97a8aa43d4e");

        RemoteNotificationEvent event = RemoteNotificationEvent.from(data, "fcm-id");

        assertEquals("ACCESS", event.type());
        assertEquals("HIGH", event.priority());
        assertEquals("access-request:3cb18d21-d1db-4c75-a499-b97a8aa43d4e",
                event.sourceKey());
    }
}
