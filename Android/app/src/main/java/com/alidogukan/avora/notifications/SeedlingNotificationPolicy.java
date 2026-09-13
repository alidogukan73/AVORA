package com.alidogukan.avora.notifications;

import com.alidogukan.avora.models.SeedlingBatch;
import com.alidogukan.avora.models.SeedlingTelemetry;
import com.alidogukan.avora.seedling.SeedlingStagePolicy;
import com.alidogukan.avora.seedling.SeedlingTimeline;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Pure timing and eligibility rules for actionable seedling notifications. */
public final class SeedlingNotificationPolicy {
    // Firmware publishes every 10 seconds. A 90-second grace period tolerates
    // short network gaps without generating advice from genuinely old data.
    public static final long TELEMETRY_MAX_AGE_SECONDS = 90L;

    public enum Milestone { NONE, GERMINATION, FIRST_LEAF, HARDENING, READY }

    private SeedlingNotificationPolicy() { }

    public static boolean isActive(SeedlingBatch batch) {
        return batch != null && "ACTIVE".equalsIgnoreCase(batch.getStatus())
                && !batch.getBatch_id().isBlank();
    }

    public static boolean hasFreshTelemetry(SeedlingTelemetry telemetry, long nowEpoch) {
        return telemetry != null
                && telemetry.isFresh(nowEpoch, TELEMETRY_MAX_AGE_SECONDS);
    }

    public static Milestone dueMilestone(SeedlingBatch batch, LocalDate today, ZoneId zoneId) {
        if (!isActive(batch) || today == null) return Milestone.NONE;
        String stage = SeedlingStagePolicy.normalize(batch.getStage());
        long[] dates = SeedlingTimeline.milestoneEpochs(batch);
        int index;
        Milestone milestone;
        switch (stage) {
            case SeedlingStagePolicy.SOWN:
                index = 1;
                milestone = Milestone.GERMINATION;
                break;
            case SeedlingStagePolicy.GERMINATING:
                index = 2;
                milestone = Milestone.FIRST_LEAF;
                break;
            case SeedlingStagePolicy.COTYLEDON:
            case SeedlingStagePolicy.TRUE_LEAVES:
                index = 3;
                milestone = Milestone.HARDENING;
                break;
            case SeedlingStagePolicy.HARDENING:
                index = 4;
                milestone = Milestone.READY;
                break;
            default:
                return Milestone.NONE;
        }
        if (index >= dates.length || dates[index] <= 0L) return Milestone.NONE;
        ZoneId zone = zoneId == null ? ZoneId.systemDefault() : zoneId;
        LocalDate due = Instant.ofEpochSecond(dates[index]).atZone(zone).toLocalDate();
        return due.isAfter(today) ? Milestone.NONE : milestone;
    }

    public static long milestoneEpoch(SeedlingBatch batch, Milestone milestone) {
        if (batch == null || milestone == null) return 0L;
        long[] dates = SeedlingTimeline.milestoneEpochs(batch);
        switch (milestone) {
            case GERMINATION: return dates[1];
            case FIRST_LEAF: return dates[2];
            case HARDENING: return dates[3];
            case READY: return dates[4];
            default: return 0L;
        }
    }

    public static boolean hasLogToday(long latestLogEpoch, LocalDate today, ZoneId zoneId) {
        if (latestLogEpoch <= 0L || today == null) return false;
        ZoneId zone = zoneId == null ? ZoneId.systemDefault() : zoneId;
        return today.equals(Instant.ofEpochSecond(latestLogEpoch)
                .atZone(zone).toLocalDate());
    }

    public static boolean isReminderDeliveryHour(int localHour) {
        return localHour >= 8 && localHour < 21;
    }

    public static boolean shouldSendDailyCheck(int localHour, int missingLogCount) {
        return localHour >= 9 && localHour < 20 && missingLogCount > 0;
    }
}
