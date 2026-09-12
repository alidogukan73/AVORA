package com.alidogukan.avora.notifications;

/** Chooses only the Firebase branches needed by enabled notification types. */
final class NotificationSignalReadPlan {
    final boolean weather;
    final boolean irrigation;
    final boolean device;
    final boolean seedling;
    final boolean seedlingLogs;

    private NotificationSignalReadPlan(boolean weather,
                                       boolean irrigation,
                                       boolean device,
                                       boolean seedling,
                                       boolean seedlingLogs) {
        this.weather = weather;
        this.irrigation = irrigation;
        this.device = device;
        this.seedling = seedling;
        this.seedlingLogs = seedlingLogs;
    }

    static NotificationSignalReadPlan from(NotificationSettingsStore settings) {
        return create(
                settings.isCategoryEnabled("weather"),
                settings.isCategoryEnabled("irrigation"),
                settings.isReminderEnabled("irrigation"),
                settings.isCategoryEnabled("device"),
                settings.isCategoryEnabled("seedling"),
                settings.isReminderEnabled("seedling")
        );
    }

    static NotificationSignalReadPlan create(boolean weatherCategory,
                                             boolean irrigationCategory,
                                             boolean irrigationReminder,
                                             boolean deviceCategory,
                                             boolean seedlingCategory,
                                             boolean seedlingReminder) {
        return new NotificationSignalReadPlan(
                weatherCategory,
                irrigationCategory && irrigationReminder,
                deviceCategory,
                seedlingCategory,
                seedlingCategory && seedlingReminder
        );
    }

    boolean needsFirebase() {
        return weather || irrigation || device || seedling;
    }
}
