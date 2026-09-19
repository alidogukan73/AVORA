package com.alidogukan.avora.notifications;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.alidogukan.avora.R;
import com.alidogukan.avora.activities.AIAssistantActivity;
import com.alidogukan.avora.activities.DeviceHealthActivity;
import com.alidogukan.avora.activities.FeedbackInboxActivity;
import com.alidogukan.avora.activities.FertilizationCalendarActivity;
import com.alidogukan.avora.activities.FertilizationZoneDetailActivity;
import com.alidogukan.avora.activities.FertilizerHistoryActivity;
import com.alidogukan.avora.activities.FertilizerProductsActivity;
import com.alidogukan.avora.activities.PlantAssistantActivity;
import com.alidogukan.avora.activities.JournalRecordDetailActivity;
import com.alidogukan.avora.activities.SeedlingAssistantActivity;
import com.alidogukan.avora.activities.SeedlingBatchDetailActivity;
import com.alidogukan.avora.activities.NasSecurityActivity;
import com.alidogukan.avora.activities.WateringHistoryActivity;
import com.alidogukan.avora.activities.WeatherForecastActivity;
import com.alidogukan.avora.fertilization.FertilizerOutcomeFollowUpPolicy;
import com.alidogukan.avora.models.GardenNotification;

import java.util.Locale;

/** Maps a durable notification to the screen where its related work can be done. */
public final class NotificationActionRouter {
    public enum Destination {
        NONE,
        PLANT_ASSISTANT,
        PLANT_ANALYSIS_RECORD,
        IRRIGATION_ASSISTANT,
        WATERING_HISTORY,
        FERTILIZATION_ZONE,
        FERTILIZATION_CALENDAR,
        FERTILIZER_HISTORY,
        FERTILIZER_PRODUCTS,
        WEATHER_FORECAST,
        DEVICE_HEALTH,
        SEEDLING_ASSISTANT,
        NAS_SECURITY,
        FEEDBACK_INBOX,
        SEEDLING_BATCH
    }

    private NotificationActionRouter() { }

    public static Destination destinationFor(String type, String sourceKey, String zoneId) {
        String normalizedType = normalize(type);
        String normalizedSource = normalize(sourceKey);
        if (!FertilizerOutcomeFollowUpPolicy.applicationIdFromSource(
                safe(sourceKey)).isBlank()) {
            return Destination.FERTILIZER_HISTORY;
        }
        switch (normalizedType) {
            case "PHOTO_FOLLOW_UP":
                return Destination.PLANT_ASSISTANT;
            case "PLANT":
            case "PLANT_ASSISTANT":
                return analysisPhotoIdFromSource(sourceKey).isBlank()
                        ? Destination.PLANT_ASSISTANT
                        : Destination.PLANT_ANALYSIS_RECORD;
            case "IRRIGATION":
                if (normalizedSource.startsWith("WATERING:")
                        || normalizedSource.startsWith("WATERING-INTERRUPTED:")) {
                    return Destination.WATERING_HISTORY;
                }
                return Destination.IRRIGATION_ASSISTANT;
            case "FERTILIZATION":
                return safe(zoneId).isBlank()
                        ? Destination.FERTILIZATION_CALENDAR
                        : Destination.FERTILIZATION_ZONE;
            case "SEEDLING":
            case "SEEDLING_ASSISTANT":
                return safe(zoneId).isBlank()
                        ? Destination.SEEDLING_ASSISTANT
                        : Destination.SEEDLING_BATCH;
            case "STOCK":
                return Destination.FERTILIZER_PRODUCTS;
            case "WEATHER":
                return Destination.WEATHER_FORECAST;
            case "DEVICE":
                return Destination.DEVICE_HEALTH;
            case "FEEDBACK":
                return Destination.FEEDBACK_INBOX;
            case "ACCESS":
                return Destination.NAS_SECURITY;
            default:
                return Destination.NONE;
        }
    }

    public static Destination destinationFor(GardenNotification value) {
        if (value == null) return Destination.NONE;
        return destinationFor(value.getType(), value.getSource_key(), value.getZone_id());
    }

    @Nullable
    public static Intent createIntent(Context context, GardenNotification value) {
        if (context == null || value == null) return null;
        Destination destination = destinationFor(value);
        Intent intent;
        switch (destination) {
            case PLANT_ASSISTANT:
                intent = new Intent(context, PlantAssistantActivity.class);
                break;
            case PLANT_ANALYSIS_RECORD:
                intent = new Intent(context, JournalRecordDetailActivity.class)
                        .putExtra(JournalRecordDetailActivity.EXTRA_PHOTO_ID,
                                analysisPhotoIdFromSource(value.getSource_key()))
                        .putExtra("title", value.getTitle())
                        .putExtra("detail", value.getDescription())
                        .putExtra("icon", "✦")
                        .putExtra("time", value.getCreated_at_epoch());
                break;
            case IRRIGATION_ASSISTANT:
                intent = new Intent(context, AIAssistantActivity.class);
                break;
            case WATERING_HISTORY:
                intent = new Intent(context, WateringHistoryActivity.class);
                break;
            case FERTILIZATION_ZONE:
                intent = new Intent(context, FertilizationZoneDetailActivity.class);
                break;
            case FERTILIZATION_CALENDAR:
                intent = new Intent(context, FertilizationCalendarActivity.class);
                break;
            case FERTILIZER_HISTORY:
                intent = new Intent(context, FertilizerHistoryActivity.class)
                        .putExtra("outcome_application_id",
                                FertilizerOutcomeFollowUpPolicy.applicationIdFromSource(
                                        value.getSource_key()));
                break;
            case FERTILIZER_PRODUCTS:
                intent = new Intent(context, FertilizerProductsActivity.class);
                break;
            case WEATHER_FORECAST:
                intent = new Intent(context, WeatherForecastActivity.class);
                break;
            case DEVICE_HEALTH:
                intent = new Intent(context, DeviceHealthActivity.class);
                break;
            case SEEDLING_ASSISTANT:
                intent = new Intent(context, SeedlingAssistantActivity.class);
                break;
            case FEEDBACK_INBOX:
                intent = new Intent(context, FeedbackInboxActivity.class);
                break;
            case NAS_SECURITY:
                intent = new Intent(context, NasSecurityActivity.class);
                if (isInactiveAccessReview(value.getSource_key())) {
                    intent.putExtra(
                            NasSecurityActivity.EXTRA_OPEN_INACTIVE_ACCOUNTS, true);
                } else {
                    intent.putExtra(
                            NasSecurityActivity.EXTRA_OPEN_PENDING_REQUESTS, true);
                }
                break;
            case SEEDLING_BATCH:
                intent = new Intent(context, SeedlingBatchDetailActivity.class)
                        .putExtra(SeedlingBatchDetailActivity.EXTRA_BATCH_ID,
                                safe(value.getZone_id()));
                break;
            default:
                return null;
        }
        intent.putExtra("notification_id", value.getId());
        if (!safe(value.getZone_id()).isBlank()) {
            intent.putExtra("zone_id", value.getZone_id());
        }
        if (!safe(value.getSeason_id()).isBlank()) {
            intent.putExtra("season_id", value.getSeason_id());
        }
        return intent;
    }

    @StringRes
    public static int actionLabel(GardenNotification value) {
        switch (destinationFor(value)) {
            case FEEDBACK_INBOX:
                return R.string.notification_action_open_feedback;
            case PLANT_ASSISTANT:
                return "PHOTO_FOLLOW_UP".equals(normalize(value == null ? "" : value.getType()))
                        ? R.string.notification_action_add_photo
                        : R.string.notification_action_open_plant_assistant;
            case PLANT_ANALYSIS_RECORD:
                return R.string.notification_action_open_plant_analysis;
            case IRRIGATION_ASSISTANT:
                return R.string.notification_action_open_irrigation;
            case WATERING_HISTORY:
                return R.string.notification_action_open_watering_history;
            case FERTILIZATION_ZONE:
            case FERTILIZATION_CALENDAR:
                return R.string.notification_action_open_fertilization;
            case FERTILIZER_HISTORY:
                return R.string.notification_action_open_fertilizer_follow_up;
            case FERTILIZER_PRODUCTS:
                return R.string.notification_action_open_stock;
            case WEATHER_FORECAST:
                return R.string.notification_action_open_weather;
            case DEVICE_HEALTH:
                return R.string.notification_action_open_device_health;
            case SEEDLING_ASSISTANT:
                return R.string.notification_action_open_seedling_assistant;
            case SEEDLING_BATCH:
                return R.string.notification_action_open_seedling_batch;
            case NAS_SECURITY:
                return isInactiveAccessReview(value == null
                        ? "" : value.getSource_key())
                        ? R.string.notification_action_review_inactive_access
                        : R.string.notification_action_open_access_requests;
            default:
                return 0;
        }
    }

    private static String normalize(String value) {
        return safe(value).toUpperCase(Locale.ROOT);
    }

    static String analysisPhotoIdFromSource(String sourceKey) {
        String value = safe(sourceKey);
        String[] prefixes = {"plant_analysis:", "follow_up_complete:"};
        for (String prefix : prefixes) {
            if (value.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return safe(value.substring(prefix.length()));
            }
        }
        return "";
    }

    static boolean isInactiveAccessReview(String sourceKey) {
        return normalize(sourceKey).startsWith("INACTIVE-ACCESS:");
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
