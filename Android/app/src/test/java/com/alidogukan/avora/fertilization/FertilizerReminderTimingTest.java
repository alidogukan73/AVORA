package com.alidogukan.avora.fertilization;

import org.junit.Test;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class FertilizerReminderTimingTest {
    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static long millis(String utc) { return Instant.parse(utc).toEpochMilli(); }

    @Test public void alarmFollowsLocalSlotsAndDoesNotReplayTheCurrentSlot() {
        assertEquals(millis("2026-10-08T05:00:00Z"),
                FertilizerReminderTiming.nextCheckMillis(millis("2026-10-08T04:59:59Z"), ISTANBUL));
        assertEquals(millis("2026-10-08T09:00:00Z"),
                FertilizerReminderTiming.nextCheckMillis(millis("2026-10-08T05:00:00Z"), ISTANBUL));
        assertEquals(millis("2026-10-08T15:00:00Z"),
                FertilizerReminderTiming.nextCheckMillis(millis("2026-10-08T09:00:00Z"), ISTANBUL));
        assertEquals(millis("2026-10-09T05:00:00Z"),
                FertilizerReminderTiming.nextCheckMillis(millis("2026-10-08T20:00:00Z"), ISTANBUL));
    }

    @Test public void nextAlarmUsesCalendarDayAcrossDaylightSavingChange() {
        assertEquals(millis("2026-10-25T07:00:00Z"), FertilizerReminderTiming.nextCheckMillis(
                millis("2026-10-24T20:00:00Z"), ZoneId.of("Europe/Berlin")));
    }

    @Test public void unperformedSuggestionDoesNotMoveToTodayWhenTheAppReopens() {
        long first = Instant.parse("2026-10-07T09:00:00Z").getEpochSecond();
        long saved = FertilizerReminderTiming.dueEpoch(0, 0, first);
        for (int day = 1; day <= 9; day++) {
            assertEquals(first, FertilizerReminderTiming.dueEpoch(0, saved, first + day * 86400));
        }
        assertEquals(first + 86400, FertilizerReminderTiming.dueEpoch(first + 86400, saved, first));
    }

    @Test public void onlyBoundedFollowUpsAreEligibleAndNoneRunOvernight() {
        assertNull(FertilizerReminderTiming.reminderSlot(0, LocalTime.of(7, 59)));
        assertEquals("morning", FertilizerReminderTiming.reminderSlot(0, LocalTime.of(8, 0)));
        assertEquals("noon", FertilizerReminderTiming.reminderSlot(0, LocalTime.NOON));
        assertEquals("evening", FertilizerReminderTiming.reminderSlot(0, LocalTime.of(18, 0)));
        assertEquals("next_day", FertilizerReminderTiming.reminderSlot(-1, LocalTime.NOON));
        assertEquals("final", FertilizerReminderTiming.reminderSlot(-7, LocalTime.NOON));
        for (long days : new long[]{1, -2, -6, -8, -100}) {
            assertNull(FertilizerReminderTiming.reminderSlot(days, LocalTime.NOON));
        }
        assertNull(FertilizerReminderTiming.reminderSlot(-1, LocalTime.of(2, 0)));
        assertNull(FertilizerReminderTiming.reminderSlot(-7, LocalTime.of(2, 0)));
    }

    @Test public void applicationSeasonAndProductChangesStartIndependentCycles() {
        String first = FertilizerReminderTiming.cycleKey("z", "s1", "stage", "N", "p1", 100);
        assertNotEquals(first, FertilizerReminderTiming.cycleKey("z", "s2", "stage", "N", "p1", 100));
        assertNotEquals(first, FertilizerReminderTiming.cycleKey("z", "s1", "stage", "N", "p2", 100));
        assertNotEquals(first, FertilizerReminderTiming.cycleKey("z", "s1", "stage", "N", "p1", 200));
        assertNotEquals(FertilizerReminderTiming.cycleKey("a:b", "c", "", "", "", 0),
                FertilizerReminderTiming.cycleKey("a", "b:c", "", "", "", 0));
    }

    @Test public void upgradingAdoptsLatestReminderWithoutReplayingToday() {
        Map<String, Object> state = new HashMap<>();
        state.put("zone-001:CONDITIONER:2026-10-06:noon", true);
        state.put("zone-001:CONDITIONER:2026-10-07:evening", true);
        state.put("zone-002:CONDITIONER:2026-10-08:morning", true);
        state.put("zone-001:NUTRITION:2026-10-08:morning", true);
        state.put("zone-001:CONDITIONER:2026-10-09:morning", true);
        state.put("zone-001:CONDITIONER:broken:morning", true);
        state.put("zone-001:CONDITIONER:2026-10-08:morning", false);
        long now = Instant.parse("2026-10-08T09:00:00Z").getEpochSecond();
        long old = FertilizerReminderTiming.legacyDueEpoch(state, "zone-001", "CONDITIONER", 0, now, ISTANBUL);
        assertEquals(Instant.parse("2026-10-06T21:00:00Z").getEpochSecond(), old);
        assertEquals(0, FertilizerReminderTiming.legacyDueEpoch(state, "zone-001", "CONDITIONER", old + 1, now, ISTANBUL));
    }
}
