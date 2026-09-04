package com.alidogukan.avora.statistics;

import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.Statistics;
import com.alidogukan.avora.models.WateringHistory;
import com.alidogukan.avora.notifications.NotificationPolicy;
import com.alidogukan.avora.zones.ZoneCapacityPolicy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** All and zone totals use the same complete, recorded history, including past seasons. */
public final class StatisticsCalculator {
    private StatisticsCalculator() { }

    public static Statistics calculate(List<WateringHistory> history, String zoneId,
                                       long nowMillis, ZoneId timeZone) {
        Statistics result = new Statistics();
        if (history == null) return result;
        ZoneId zone = timeZone == null ? ZoneId.systemDefault() : timeZone;
        LocalDate today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate();
        String selection = safe(zoneId);
        long count = 0, completed = 0, duration = 0, todayCount = 0, todaySeconds = 0;
        WateringHistory latest = null;
        long latestTime = 0;
        for (WateringHistory item : history) {
            if (item == null || (!selection.isEmpty() && !selection.equals(safe(item.getZoneId())))) continue;
            count++;
            if (item.isCompleted()) completed++;
            long seconds = Math.max(0L, item.getDuration());
            duration = addSeconds(duration, seconds);
            long started = timestamp(item.getStartedAt(), zone);
            long finished = timestamp(item.getFinishedAt(), zone);
            // Legacy records may have only a finish timestamp.
            long dayTime = started > 0 ? started : finished;
            if (dayTime > 0 && dayTime <= nowMillis
                    && today.equals(Instant.ofEpochMilli(dayTime).atZone(zone).toLocalDate())) {
                todayCount++;
                todaySeconds = addSeconds(todaySeconds, seconds);
            }
            long eventTime = finished > 0 ? finished : started;
            if (eventTime > 0 && eventTime <= nowMillis
                    && (latest == null || eventTime > latestTime
                    || (eventTime == latestTime
                    && safe(item.getRecordId()).compareTo(safe(latest.getRecordId())) > 0))) {
                latest = item;
                latestTime = eventTime;
            }
        }
        result.setTotalWaterings(count);
        result.setCompletedWaterings(completed);
        result.setInterruptedWaterings(count - completed);
        result.setTotalWateringSeconds(duration);
        result.setAverageDuration(count == 0 ? 0 : duration / count);
        result.setSuccessRate(count == 0 ? 0 : Math.round(completed * 100.0 / count));
        result.setWateringsToday(todayCount);
        result.setWateringSecondsToday(todaySeconds);
        if (latest != null) {
            result.setLastWateringDuration(Math.max(0L, latest.getDuration()));
            result.setLastStopReason(latest.getStopReason());
            result.setStatisticsDate(Instant.ofEpochMilli(latestTime).toString());
            if (latest.hasMoistureReadings()
                    && validMoisture(latest.getMoistureBefore()) && validMoisture(latest.getMoistureAfter())) {
                result.setBeforeMoisture(latest.getMoistureBefore());
                result.setAfterMoisture(latest.getMoistureAfter());
                result.setMoistureDelta(latest.getMoistureAfter() - latest.getMoistureBefore());
            }
        }
        return result;
    }

    /** Keep the model and checked chip in agreement when a selected zone is removed. */
    public static String resolveSelectedZone(String selectedZoneId, List<GardenZone> zones) {
        String selected = safe(selectedZoneId);
        if (selected.isEmpty() || zones == null) return selected;
        for (GardenZone zone : zones) {
            if (ZoneCapacityPolicy.isActive(zone) && selected.equals(zone.getZone_id())) return selected;
        }
        return "";
    }

    public static long timestamp(String value, ZoneId zone) {
        return NotificationPolicy.parseTimestampMillis(value == null ? "" : value.trim().replace(' ', 'T'), zone);
    }

    private static boolean validMoisture(long value) { return value >= 0 && value <= 100; }
    private static long addSeconds(long total, long value) {
        return Long.MAX_VALUE - total < value ? Long.MAX_VALUE : total + value;
    }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
