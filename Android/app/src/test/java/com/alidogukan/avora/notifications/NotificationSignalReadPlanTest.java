package com.alidogukan.avora.notifications;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NotificationSignalReadPlanTest {
    @Test
    public void disabledCategoriesNeedNoFirebaseReads() {
        NotificationSignalReadPlan plan = NotificationSignalReadPlan.create(
                false, false, true, false, false, true);

        assertFalse(plan.needsFirebase());
    }

    @Test
    public void irrigationNeedsBothCategoryAndReminder() {
        NotificationSignalReadPlan categoryOff = NotificationSignalReadPlan.create(
                false, false, true, false, false, false);
        NotificationSignalReadPlan reminderOff = NotificationSignalReadPlan.create(
                false, true, false, false, false, false);
        NotificationSignalReadPlan enabled = NotificationSignalReadPlan.create(
                false, true, true, false, false, false);

        assertFalse(categoryOff.irrigation);
        assertFalse(reminderOff.irrigation);
        assertTrue(enabled.irrigation);
    }

    @Test
    public void seedlingConditionsDoNotRequireDailyReminder() {
        NotificationSignalReadPlan plan = NotificationSignalReadPlan.create(
                false, false, false, false, true, false);

        assertTrue(plan.seedling);
        assertFalse(plan.seedlingLogs);
    }

    @Test
    public void seedlingLogsRequireCategoryAndReminder() {
        NotificationSignalReadPlan plan = NotificationSignalReadPlan.create(
                false, false, false, false, true, true);

        assertTrue(plan.seedling);
        assertTrue(plan.seedlingLogs);
    }
}
