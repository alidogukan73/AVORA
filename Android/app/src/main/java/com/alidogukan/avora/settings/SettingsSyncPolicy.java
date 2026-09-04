package com.alidogukan.avora.settings;

/** Pure conflict and boundary rules shared by local-first settings stores. */
public final class SettingsSyncPolicy {
    private SettingsSyncPolicy() {
    }

    /** A cloud snapshot may replace local state only when it is strictly newer. */
    public static boolean isCloudValueNewer(long cloudUpdatedAt, long localUpdatedAt) {
        return cloudUpdatedAt > 0L && cloudUpdatedAt > Math.max(0L, localUpdatedAt);
    }

    /** Keeps quiet-hour values inside Android's valid 24-hour clock range. */
    public static int validHour(long value, int fallback) {
        return value >= 0L && value <= 23L ? (int) value : fallback;
    }
}
