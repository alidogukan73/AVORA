package com.alidogukan.avora.notifications;

import android.content.Context;
import android.content.SharedPreferences;
import com.alidogukan.avora.R;
import com.alidogukan.avora.language.AvoraLanguageManager;
import com.alidogukan.avora.models.AIDecision;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.Health;
import com.alidogukan.avora.models.Status;
import com.alidogukan.avora.models.WateringHistory;
import com.alidogukan.avora.settings.UnitPreferences;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.time.ZoneId;

/** Converts meaningful weather, device and watering changes into deduplicated AVORA alerts. */
public final class NotificationSignalCoordinator {
    private static final long WEATHER_MAX_AGE_SECONDS = 12L * 60L * 60L;
    private static final long IRRIGATION_AI_MAX_AGE_MILLIS = 30L * 60L * 1000L;
    private static final String AI_STATE_PREFS = "avora_ai_notification_state";
    private static final String DEVICE_CONNECTION_PREFS = "avora_device_connection_state";
    private static final String DEVICE_OFFLINE_CANDIDATE_SINCE = "offline_candidate_since";
    private static final String DEVICE_OFFLINE_CANDIDATE_HEARTBEAT = "offline_candidate_heartbeat";
    private static final String DEVICE_RECOVERY_CANDIDATE_SINCE = "recovery_candidate_since";
    private static final String DEVICE_RECOVERY_CANDIDATE_HEARTBEAT = "recovery_candidate_heartbeat";
    private static final String DEVICE_ERROR_RECOVERY_CANDIDATE_SINCE =
            "device_error_recovery_candidate_since";
    private static final String DEVICE_NEWEST_HEARTBEAT = "newest_heartbeat_epoch";
    private static final Object AI_STATE_LOCK = new Object();
    private static final Object DEVICE_CONNECTION_LOCK = new Object();

    private NotificationSignalCoordinator() { }

    public static void evaluateWeather(Context context, Double temperature, Double rain,
                                       Double wind, String forecastDate) {
        evaluateWeather(context, temperature, rain, wind, forecastDate,
                System.currentTimeMillis() / 1000L);
    }

    public static void evaluateWeather(Context context, Double temperature, Double rain,
                                       Double wind, String forecastDate, long updatedAtEpoch) {
        context = AvoraLanguageManager.localizedContext(context);
        long nowEpoch = System.currentTimeMillis() / 1000L;
        if (!NotificationPolicy.isFreshEpochSeconds(
                updatedAtEpoch, nowEpoch, WEATHER_MAX_AGE_SECONDS)) {
            return;
        }
        double temp = value(temperature);
        double rainfall = value(rain);
        double windSpeed = value(wind);
        String date = forecastDate == null || forecastDate.isBlank()
                ? LocalDate.now().toString() : forecastDate;
        GardenNotificationManager notifications = new GardenNotificationManager(context);
        if (rainfall >= 60D) {
            notifications.publishOnce("WEATHER", "HIGH", "",
                    context.getString(R.string.notification_weather_rain_title),
                    context.getString(R.string.notification_weather_rain_description,
                            Math.round(rainfall)),
                    "weather:rain:" + date);
        } else if (temp >= 35D) {
            notifications.publishOnce("WEATHER", "NORMAL", "",
                    context.getString(R.string.notification_weather_heat_title),
                    context.getString(R.string.notification_weather_heat_description,
                            new UnitPreferences(context).formatTemperature(temp)),
                    "weather:heat:" + date);
        } else if (windSpeed >= 30D) {
            notifications.publishOnce("WEATHER", "NORMAL", "",
                    context.getString(R.string.notification_weather_wind_title),
                    context.getString(R.string.notification_weather_wind_description,
                            Math.round(windSpeed)),
                    "weather:wind:" + date);
        }
    }



    public static synchronized void evaluateDeviceConnection(Context context, Status status) {

        if (status == null) {
            return;
        }
        context = AvoraLanguageManager.localizedContext(context);

        if (!acceptDeviceSnapshot(context, status)) {
            return;
        }

        GardenNotificationManager notifications =
                new GardenNotificationManager(context);

        long nowMillis = System.currentTimeMillis();
        long nowEpoch = nowMillis / 1000L;

        long lastSeenEpoch =
                status.getLastSeenEpoch();

        long heartbeatAge = lastSeenEpoch <= 0L
                ? Long.MAX_VALUE
                : Math.max(0L, nowEpoch - lastSeenEpoch);

        boolean deviceOffline = NotificationPolicy.isDeviceOffline(
                status.isOnline(), lastSeenEpoch, nowEpoch,
                NotificationPolicy.DEVICE_HEARTBEAT_MAX_AGE_SECONDS);

        if (deviceOffline) {

            clearRecoveryCandidate(context);

            if (!confirmOfflineObservation(context, lastSeenEpoch, nowMillis)) {
                return;
            }

            String description;

            if (lastSeenEpoch <= 0L) {
                description = context.getString(
                        R.string.notification_device_offline_unknown);
            } else {
                long offlineMinutes =
                        Math.max(1L, heartbeatAge / 60L);

                description = context.getString(
                        R.string.notification_device_offline_minutes,
                        offlineMinutes);
            }

            notifications.publishIncident(
                    "device_offline",
                    "DEVICE",
                    "HIGH",
                    "",
                    context.getString(R.string.notification_device_offline_title),
                    description,
                    "device-offline:heartbeat"
            );

            return;
        }

        clearOfflineCandidate(context);

        if (notifications.isIncidentActive("device_offline")) {

            if (!confirmRecoveryObservation(context, lastSeenEpoch, nowMillis)) {
                return;
            }


            notifications.publishOnce(
                    "DEVICE",
                    "NORMAL",
                    "",
                    context.getString(R.string.notification_device_recovered_title),
                    context.getString(R.string.notification_device_recovered_description),
                    "device-recovered:" + lastSeenEpoch
            );
        }

        clearRecoveryCandidate(context);
        notifications.resetIncident("device_offline");
    }

    /**
     * Seeds a suspected outage after Firebase cache/server reconciliation.
     * A healthy observation never clears a confirmed incident; recovery must
     * pass through evaluateDeviceConnection and its stability window.
     */
    public static synchronized void synchronizeDeviceConnection(Context context, Status status) {
        if (status == null) return;
        if (!acceptDeviceSnapshot(context, status)) return;
        long nowMillis = System.currentTimeMillis();
        long nowEpoch = nowMillis / 1000L;
        boolean offline = NotificationPolicy.isDeviceOffline(
                status.isOnline(), status.getLastSeenEpoch(), nowEpoch,
                NotificationPolicy.DEVICE_HEARTBEAT_MAX_AGE_SECONDS);
        if (offline) {
            clearRecoveryCandidate(context);
            rememberOfflineCandidate(context, status.getLastSeenEpoch(), nowMillis);
        } else {
            clearOfflineCandidate(context);
            if (!new GardenNotificationManager(context)
                    .isIncidentActive("device_offline")) {
                clearRecoveryCandidate(context);
            }
            DeviceConnectionVerificationWorker.cancel(context);
        }
    }


    private static boolean confirmOfflineObservation(Context context,
                                                      long heartbeatEpoch,
                                                      long nowMillis) {
        synchronized (DEVICE_CONNECTION_LOCK) {
            SharedPreferences preferences = context.getSharedPreferences(
                    DEVICE_CONNECTION_PREFS, Context.MODE_PRIVATE);
            long candidateSince = preferences.getLong(
                    DEVICE_OFFLINE_CANDIDATE_SINCE, 0L);
            long candidateHeartbeat = preferences.getLong(
                    DEVICE_OFFLINE_CANDIDATE_HEARTBEAT, Long.MIN_VALUE);
            if (candidateSince <= 0L || candidateHeartbeat != heartbeatEpoch) {
                preferences.edit()
                        .putLong(DEVICE_OFFLINE_CANDIDATE_SINCE, nowMillis)
                        .putLong(DEVICE_OFFLINE_CANDIDATE_HEARTBEAT, heartbeatEpoch)
                        .apply();
                return false;
            }
            return NotificationPolicy.isOfflineConfirmationDue(
                    candidateSince, nowMillis,
                    NotificationPolicy.DEVICE_OFFLINE_CONFIRMATION_MILLIS);
        }
    }

    private static boolean confirmRecoveryObservation(Context context,
                                                       long heartbeatEpoch,
                                                       long nowMillis) {
        synchronized (DEVICE_CONNECTION_LOCK) {
            SharedPreferences preferences = context.getSharedPreferences(
                    DEVICE_CONNECTION_PREFS, Context.MODE_PRIVATE);
            long candidateSince = preferences.getLong(
                    DEVICE_RECOVERY_CANDIDATE_SINCE, 0L);
            long candidateHeartbeat = preferences.getLong(
                    DEVICE_RECOVERY_CANDIDATE_HEARTBEAT, Long.MIN_VALUE);
            /*
             * A healthy heartbeat normally increases every few seconds. Keep the
             * original recovery window while it moves forward; only a backward
             * snapshot invalidates the observation.
             */
            if (candidateSince <= 0L || heartbeatEpoch < candidateHeartbeat) {
                preferences.edit()
                        .putLong(DEVICE_RECOVERY_CANDIDATE_SINCE, nowMillis)
                        .putLong(DEVICE_RECOVERY_CANDIDATE_HEARTBEAT, heartbeatEpoch)
                        .apply();
                return false;
            }
            if (heartbeatEpoch > candidateHeartbeat) {
                preferences.edit()
                        .putLong(DEVICE_RECOVERY_CANDIDATE_HEARTBEAT, heartbeatEpoch)
                        .apply();
            }
            return NotificationPolicy.isOfflineConfirmationDue(
                    candidateSince, nowMillis,
                    NotificationPolicy.DEVICE_RECOVERY_CONFIRMATION_MILLIS);
        }
    }

    private static void rememberOfflineCandidate(Context context,
                                                 long heartbeatEpoch,
                                                 long nowMillis) {
        synchronized (DEVICE_CONNECTION_LOCK) {
            SharedPreferences preferences = context.getSharedPreferences(
                    DEVICE_CONNECTION_PREFS, Context.MODE_PRIVATE);
            long currentHeartbeat = preferences.getLong(
                    DEVICE_OFFLINE_CANDIDATE_HEARTBEAT, Long.MIN_VALUE);
            if (preferences.getLong(DEVICE_OFFLINE_CANDIDATE_SINCE, 0L) > 0L
                    && currentHeartbeat == heartbeatEpoch) {
                return;
            }
            preferences.edit()
                    .putLong(DEVICE_OFFLINE_CANDIDATE_SINCE, nowMillis)
                    .putLong(DEVICE_OFFLINE_CANDIDATE_HEARTBEAT, heartbeatEpoch)
                    .apply();
        }
    }

    private static void clearOfflineCandidate(Context context) {
        synchronized (DEVICE_CONNECTION_LOCK) {
            context.getSharedPreferences(DEVICE_CONNECTION_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .remove(DEVICE_OFFLINE_CANDIDATE_SINCE)
                    .remove(DEVICE_OFFLINE_CANDIDATE_HEARTBEAT)
                    .apply();
        }
    }

    private static void clearRecoveryCandidate(Context context) {
        synchronized (DEVICE_CONNECTION_LOCK) {
            context.getSharedPreferences(DEVICE_CONNECTION_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .remove(DEVICE_RECOVERY_CANDIDATE_SINCE)
                    .remove(DEVICE_RECOVERY_CANDIDATE_HEARTBEAT)
                    .apply();
        }
    }

    private static boolean acceptDeviceSnapshot(Context context, Status status) {
        synchronized (DEVICE_CONNECTION_LOCK) {
            SharedPreferences preferences = context.getSharedPreferences(
                    DEVICE_CONNECTION_PREFS, Context.MODE_PRIVATE);
            long newestHeartbeat = preferences.getLong(DEVICE_NEWEST_HEARTBEAT, 0L);
            long incomingHeartbeat = status.getLastSeenEpoch();
            long nowEpoch = System.currentTimeMillis() / 1000L;
            if (!NotificationPolicy.shouldAcceptDeviceSnapshot(
                    incomingHeartbeat, newestHeartbeat, nowEpoch)) {
                return false;
            }
            if (incomingHeartbeat > newestHeartbeat
                    || newestHeartbeat > nowEpoch + 5L * 60L) {
                preferences.edit()
                        .putLong(DEVICE_NEWEST_HEARTBEAT, incomingHeartbeat)
                        .apply();
            }
            return true;
        }
    }

    public static void evaluateDevice(Context context, Status status, Health health) {

        if (status == null) {
            return;
        }
        context = AvoraLanguageManager.localizedContext(context);

        long nowEpoch =
                System.currentTimeMillis() / 1000L;

        long lastSeenEpoch =
                status.getLastSeenEpoch();

        long heartbeatAge =
                lastSeenEpoch <= 0L
                        ? Long.MAX_VALUE
                        : Math.max(
                        0L,
                        nowEpoch - lastSeenEpoch
                );

        boolean deviceOffline =
                !status.isOnline()
                        || heartbeatAge
                        > NotificationPolicy.DEVICE_HEARTBEAT_MAX_AGE_SECONDS;

        if (deviceOffline) {

            GardenNotificationManager notifications =
                    new GardenNotificationManager(context);

            clearDeviceErrorRecoveryCandidate(context);

            notifications.resetIncident(
                    "device_error"
            );

            return;
        }

        GardenNotificationManager notifications =
                new GardenNotificationManager(context);

        long nowMillis = System.currentTimeMillis();

        String error = status.getLastError();

        if (error != null && !error.isBlank()) {

            clearDeviceErrorRecoveryCandidate(context);

            boolean sensorFailure = isSensorFailure(error);
            String incidentId = status.getErrorIncidentId();

            String errorKind = sensorFailure ? "sensor" : "system";
            String stableSource =
                    incidentId == null || incidentId.isBlank()
                            ? "device-error:" + errorKind + ":legacy"
                            : "device-error:incident:" + incidentId;

            notifications.publishIncident(
                    "device_error",
                    "DEVICE",
                    "HIGH",
                    "",
                    sensorFailure
                            ? context.getString(R.string.notification_sensor_unavailable_title)
                            : context.getString(R.string.notification_device_warning_title),
                    sensorFailure
                            ? context.getString(
                                    R.string.notification_sensor_unavailable_description)
                            : context.getString(
                                    R.string.notification_device_warning_description),
                    stableSource
            );

        } else {
            if (!notifications.isIncidentActive("device_error")) {
                clearDeviceErrorRecoveryCandidate(context);
            } else if (confirmDeviceErrorRecovery(context, nowMillis)) {
                notifications.resetIncident("device_error");
                clearDeviceErrorRecoveryCandidate(context);
            }
        }

        if (health != null) {

            boolean warning =
                    health.isUnderVoltageNow()
                            || health.isThrottledNow()
                            || health.isFrequencyCappedNow()
                            || health.getCpuTemperature() >= 80D
                            || health.getDiskUsage() >= 90D;

            if (warning) {

                notifications.publishIncident(
                        "device_health",
                        "DEVICE",
                        "HIGH",
                        "",
                        context.getString(R.string.notification_device_health_title),
                        context.getString(R.string.notification_device_health_description),
                        "device:health"
                );

            } else {
                notifications.resetIncident("device_health");
            }
        }
    }

    private static boolean isSensorFailure(String error) {
        String normalized = error.toLowerCase(Locale.ROOT);
        return normalized.contains("sensor") || normalized.contains("sensör")
                || normalized.contains("wireless") || normalized.contains("mqtt")
                || normalized.contains("soil moisture") || normalized.contains("measurement")
                || normalized.contains("ölçüm");
    }

    private static boolean confirmDeviceErrorRecovery(
            Context context,
            long nowMillis
    ) {
        synchronized (DEVICE_CONNECTION_LOCK) {
            SharedPreferences preferences = context.getSharedPreferences(
                    DEVICE_CONNECTION_PREFS,
                    Context.MODE_PRIVATE
            );
            long candidateSince = preferences.getLong(
                    DEVICE_ERROR_RECOVERY_CANDIDATE_SINCE,
                    0L
            );
            if (candidateSince <= 0L || nowMillis < candidateSince) {
                preferences.edit()
                        .putLong(DEVICE_ERROR_RECOVERY_CANDIDATE_SINCE, nowMillis)
                        .apply();
                return false;
            }
            return NotificationPolicy.isOfflineConfirmationDue(
                    candidateSince,
                    nowMillis,
                    NotificationPolicy.DEVICE_ERROR_RECOVERY_CONFIRMATION_MILLIS
            );
        }
    }

    private static void clearDeviceErrorRecoveryCandidate(Context context) {
        synchronized (DEVICE_CONNECTION_LOCK) {
            context.getSharedPreferences(DEVICE_CONNECTION_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .remove(DEVICE_ERROR_RECOVERY_CANDIDATE_SINCE)
                    .apply();
        }
    }

    /**
     * Reports a fresh, actionable per-zone AI decision only once per watering episode.
     * Learning, waiting and low-confidence intermediate states stay visible in the
     * assistant but deliberately do not become phone notifications.
     */
    public static void evaluateIrrigationAi(Context context, List<GardenZone> zones) {
        evaluateLowMoisture(context, zones);
        context = AvoraLanguageManager.localizedContext(context);
        NotificationSettingsStore settings = new NotificationSettingsStore(context);
        if (zones == null || !settings.isCategoryEnabled("irrigation")
                || !settings.isReminderEnabled("irrigation")) return;

        GardenNotificationManager notifications = new GardenNotificationManager(context);
        SharedPreferences preferences = context.getSharedPreferences(
                AI_STATE_PREFS, Context.MODE_PRIVATE);
        long nowMillis = System.currentTimeMillis();

        synchronized (AI_STATE_LOCK) {
            SharedPreferences.Editor editor = preferences.edit();
            for (GardenZone zone : zones) {
                if (zone == null) continue;
                String zoneId = safe(zone.getZone_id(), safe(zone.getSensor_id(), "unknown"));
                String stateKey = "irrigation_active:" + zoneId;
                AIDecision decision = zone.getAi() == null ? null : zone.getAi().getDecision();
                String updatedAt = decision == null ? "" : safe(decision.getUpdatedAt(), "");
                if (updatedAt.isBlank() && zone.getAi() != null) {
                    updatedAt = safe(zone.getAi().getUpdatedAt(), "");
                }
                boolean actionable = isActiveSeasonTarget(zone) && decision != null
                        && NotificationPolicy.shouldNotifyIrrigationAi(
                        zone.isEnabled(), zone.isIrrigation_enabled(),
                        decision.isShouldWater(), updatedAt, nowMillis,
                        IRRIGATION_AI_MAX_AGE_MILLIS);

                if (!actionable) {
                    editor.remove(stateKey);
                    continue;
                }
                if (preferences.getBoolean(stateKey, false)) continue;

                String zoneName = com.alidogukan.avora.zones.PhysicalZoneIdentity.name(zone);
                String detail = firstNonBlank(
                        decision.getDecisionMessage(),
                        decision.getPrimaryReason(),
                        context.getString(R.string.notification_irrigation_ai_description,
                                zoneName));
                long decisionTime = NotificationPolicy.parseTimestampMillis(
                        updatedAt, ZoneId.systemDefault());
                notifications.publishOnce(
                        "IRRIGATION",
                        isHighSeverity(decision.getSeverity()) ? "HIGH" : "NORMAL",
                        zoneId,
                        context.getString(R.string.notification_irrigation_ai_title, zoneName),
                        detail,
                        "irrigation_ai:" + zoneId + ":" + decisionTime
                );
                editor.putBoolean(stateKey, true);
            }
            editor.apply();
        }
    }

    public static void evaluateLowMoisture(Context context, List<GardenZone> zones) {
        context = AvoraLanguageManager.localizedContext(context);
        NotificationSettingsStore settings = new NotificationSettingsStore(context);
        if (zones == null || !settings.isCategoryEnabled("irrigation")
                || !settings.isReminderEnabled("irrigation")) return;

        long nowEpoch = System.currentTimeMillis() / 1000L;
        GardenNotificationManager notifications = new GardenNotificationManager(context);
        for (GardenZone zone : zones) {
            if (zone == null || zone.getSeason() == null) continue;
            boolean actionable = NotificationPolicy.shouldNotifyLowMoisture(
                    zone.isEnabled(),
                    zone.getSeason().isActive(),
                    zone.getSeason().getActive_season_id(),
                    zone.isSensor_enabled(),
                    zone.isLow_moisture_alert_enabled(),
                    zone.getMoisture(),
                    zone.getMoisture_limit(),
                    zone.getUpdated_at_epoch(),
                    nowEpoch,
                    20L * 60L);
            String zoneId = safe(zone.getZone_id(), safe(zone.getSensor_id(), "unknown"));
            String incidentKey = "zone_low_moisture:" + zoneId;
            if (!actionable) {
                notifications.resetIncident(incidentKey);
                continue;
            }

            String zoneName = com.alidogukan.avora.zones.PhysicalZoneIdentity.name(zone);
            notifications.publishIncident(
                    incidentKey,
                    "IRRIGATION",
                    "HIGH",
                    zoneId,
                    context.getString(R.string.notification_low_moisture_title, zoneName),
                    context.getString(R.string.notification_low_moisture_description,
                            zone.getMoisture(), zone.getMoisture_limit()),
                    "low-moisture:" + zoneId
            );
        }
    }

    private static boolean isHighSeverity(String severity) {
        if (severity == null) return false;
        String normalized = severity.trim().toUpperCase(Locale.ROOT);
        return "HIGH".equals(normalized) || "CRITICAL".equals(normalized)
                || "ERROR".equals(normalized);
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        return fallback;
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** Sends only newly completed cycles; opening a journal never replays old watering alerts. */
    public static void evaluateWatering(
            Context context,
            List<WateringHistory> records,
            List<GardenZone> zones) {
        context = AvoraLanguageManager.localizedContext(context);
        if (records == null || zones == null
                || !new NotificationSettingsStore(context).isReminderEnabled("irrigation")) {
            return;
        }
        long now = System.currentTimeMillis();
        GardenNotificationManager notifications = new GardenNotificationManager(context);
        for (WateringHistory record : records) {
            if (record == null) continue;
            if (!recordBelongsToCurrentSeason(record, zones)) continue;
            GardenZone targetZone = findZone(record.getZoneId(), zones);
            if (targetZone == null) continue;
            long completedAt = parseTime(record.getFinishedAt());
            if (completedAt <= 0L || now < completedAt
                    || now - completedAt > 20L * 60L * 1000L) continue;
            String id = record.getRecordId() == null || record.getRecordId().isBlank()
                    ? record.getFinishedAt() : record.getRecordId();
            String zoneId = record.getZoneId() == null ? "" : record.getZoneId();
            if (record.isCompleted() && targetZone.isWatering_complete_alert_enabled()) {
                notifications.publishOnce("IRRIGATION", "NORMAL", zoneId,
                        context.getString(R.string.notification_watering_completed_title),
                        context.getString(R.string.notification_watering_completed_description,
                                record.getDuration()),
                        "watering:" + zoneId + ":" + id);
            } else if (NotificationPolicy.shouldNotifyInterruptedWatering(
                    false, record.getDuration(), record.getStopReason())) {
                notifications.publishOnce("IRRIGATION", "HIGH", zoneId,
                        context.getString(R.string.notification_watering_interrupted_title),
                        context.getString(R.string.notification_watering_interrupted_description,
                                record.getDuration()),
                        "watering-interrupted:" + zoneId + ":" + id);
            }
        }
    }

    private static boolean isActiveSeasonTarget(GardenZone zone) {
        if (zone == null || zone.getSeason() == null) return false;
        return NotificationPolicy.isActiveSeasonNotificationTarget(
                zone.isEnabled(),
                zone.getSeason().isActive(),
                zone.getSeason().getActive_season_id());
    }

    private static boolean recordBelongsToCurrentSeason(
            WateringHistory record,
            List<GardenZone> zones) {
        String zoneId = record.getZoneId() == null ? "" : record.getZoneId().trim();
        for (GardenZone zone : zones) {
            if (zone == null || zone.getZone_id() == null
                    || !zoneId.equals(zone.getZone_id().trim())
                    || zone.getSeason() == null) continue;
            return NotificationPolicy.recordBelongsToActiveSeason(
                    zone.isEnabled(),
                    zone.getSeason().isActive(),
                    zone.getSeason().getActive_season_id(),
                    zone.getSeason().isInclude_legacy_records(),
                    record.getSeasonId());
        }
        return false;
    }

    private static GardenZone findZone(String zoneId, List<GardenZone> zones) {
        String targetId = zoneId == null ? "" : zoneId.trim();
        for (GardenZone zone : zones) {
            if (zone != null && zone.getZone_id() != null
                    && targetId.equals(zone.getZone_id().trim())) return zone;
        }
        return null;
    }

    private static long parseTime(String raw) {
        if (raw == null || raw.isBlank()) return 0L;
        return NotificationPolicy.parseTimestampMillis(raw, ZoneId.systemDefault());
    }

    private static double value(Double number) {
        return number == null ? 0D : number;
    }
}
