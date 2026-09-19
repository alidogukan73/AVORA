package com.alidogukan.avora.notifications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.alidogukan.avora.models.GardenNotification;

import org.junit.Test;

public final class NotificationActionRouterTest {
    @Test public void photoFollowUpTargetsPlantAssistant() {
        GardenNotification value = notification(
                "PHOTO_FOLLOW_UP", "NORMAL", "photo_follow_up:photo-1", "zone-002");
        assertEquals(NotificationActionRouter.Destination.PLANT_ASSISTANT,
                NotificationActionRouter.destinationFor(value));
    }


    @Test public void savedPlantAnalysisTargetsItsExactRecord() {
        GardenNotification value = notification(
                "PLANT_ASSISTANT", "NORMAL", "plant_analysis:photo-42", "zone-003");
        assertEquals(NotificationActionRouter.Destination.PLANT_ANALYSIS_RECORD,
                NotificationActionRouter.destinationFor(value));
        assertEquals("photo-42", NotificationActionRouter.analysisPhotoIdFromSource(
                value.getSource_key()));
    }

    @Test public void plantNoticeWithoutRecordIdFallsBackToAssistant() {
        GardenNotification value = notification(
                "PLANT_ASSISTANT", "NORMAL", "plant-daily:zone-003", "zone-003");
        assertEquals(NotificationActionRouter.Destination.PLANT_ASSISTANT,
                NotificationActionRouter.destinationFor(value));
    }

    @Test public void irrigationAdviceTargetsAssistantButCompletedCycleTargetsHistory() {
        GardenNotification advice = notification(
                "IRRIGATION", "HIGH", "low-moisture:zone-002", "zone-002");
        assertEquals(NotificationActionRouter.Destination.IRRIGATION_ASSISTANT,
                NotificationActionRouter.destinationFor(advice));

        GardenNotification completed = notification(
                "IRRIGATION", "NORMAL", "watering:zone-002:record-1", "zone-002");
        assertEquals(NotificationActionRouter.Destination.WATERING_HISTORY,
                NotificationActionRouter.destinationFor(completed));
    }

    @Test public void fertilizerOutcomeKeepsItsTargetedHistoryFlow() {
        GardenNotification value = notification(
                "FERTILIZATION", "NORMAL",
                "fertilizer_outcome_follow_up:application-7", "zone-001");
        assertEquals(NotificationActionRouter.Destination.FERTILIZER_HISTORY,
                NotificationActionRouter.destinationFor(value));
    }

    @Test public void generalSystemNoticeStaysOnDetail() {
        GardenNotification value = notification(
                "SYSTEM", "NORMAL", "remote:unknown:garden", "");
        assertEquals(NotificationActionRouter.Destination.NONE,
                NotificationActionRouter.destinationFor(value));
    }

    @Test public void seedlingAlertsOpenTheirBatchAndDailySummaryOpensAssistant() {
        GardenNotification batch = notification(
                "SEEDLING", "HIGH", "seedling-batch:batch-7:condition:critical",
                "batch-7");
        assertEquals(NotificationActionRouter.Destination.SEEDLING_BATCH,
                NotificationActionRouter.destinationFor(batch));

        GardenNotification summary = notification(
                "SEEDLING", "NORMAL", "seedling-daily:2026-09-10", "");
        assertEquals(NotificationActionRouter.Destination.SEEDLING_ASSISTANT,
                NotificationActionRouter.destinationFor(summary));
    }

    @Test public void gardenAccessRequestTargetsNasSecurity() {
        GardenNotification value = notification(
                "ACCESS", "HIGH", "access-request:request-7", "");
        assertEquals(NotificationActionRouter.Destination.NAS_SECURITY,
                NotificationActionRouter.destinationFor(value));
    }

    @Test public void inactiveFamilyAccessOpensTheAccountReviewFlow() {
        GardenNotification value = notification(
                "ACCESS", "HIGH", "inactive-access:user-7:1789000000", "");
        assertEquals(NotificationActionRouter.Destination.NAS_SECURITY,
                NotificationActionRouter.destinationFor(value));
        assertTrue(NotificationActionRouter.isInactiveAccessReview(
                value.getSource_key()));
        assertFalse(NotificationActionRouter.isInactiveAccessReview(
                "access-request:request-7"));
    }

    private static GardenNotification notification(
            String type, String priority, String sourceKey, String zoneId) {
        GardenNotification value = new GardenNotification();
        value.setType(type);
        value.setPriority(priority);
        value.setSource_key(sourceKey);
        value.setZone_id(zoneId);
        return value;
    }
}
