package com.alidogukan.avora.zones;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class ManualWateringDurationPolicyTest {
    @Test
    public void acceptsPreciseDurationsAcrossSafeRange() {
        assertEquals(5, ManualWateringDurationPolicy.fromHoursMinutesAndSeconds(
                0, 0, 5, 14400));
        assertEquals(150, ManualWateringDurationPolicy.fromHoursMinutesAndSeconds(
                0, 2, 30, 14400));
        assertEquals(7500, ManualWateringDurationPolicy.fromHoursMinutesAndSeconds(
                2, 5, 0, 14400));
        assertEquals(43200, ManualWateringDurationPolicy.fromHoursMinutesAndSeconds(
                12, 0, 0, 43200));
    }

    @Test
    public void rejectsUnsafeOrMalformedDurations() {
        assertThrows(IllegalArgumentException.class,
                () -> ManualWateringDurationPolicy.fromHoursMinutesAndSeconds(
                        0, 0, 4, 14400));
        assertThrows(IllegalArgumentException.class,
                () -> ManualWateringDurationPolicy.fromHoursMinutesAndSeconds(
                        4, 0, 1, 14400));
        assertThrows(IllegalArgumentException.class,
                () -> ManualWateringDurationPolicy.fromHoursMinutesAndSeconds(
                        0, 1, 60, 14400));
    }

    @Test
    public void invalidStoredValueFallsBackAndCommandsRemainBounded() {
        assertEquals(14400, ManualWateringDurationPolicy.configuredLimitOrDefault(0));
        assertEquals(125, ManualWateringDurationPolicy.configuredDurationOrDefault(125, 14400));
        assertEquals(5, ManualWateringDurationPolicy.clampToConfiguredLimit(-10, 14400));
        assertEquals(43200, ManualWateringDurationPolicy.clampToConfiguredLimit(99999, 43200));
        assertEquals(30, ManualWateringDurationPolicy.configuredDurationOrDefault(99999, 14400));
    }

    @Test
    public void onlyDurationsBeyondFourHoursNeedSecondConfirmation() {
        assertEquals(false, ManualWateringDurationPolicy.requiresExtendedConfirmation(14400));
        assertEquals(true, ManualWateringDurationPolicy.requiresExtendedConfirmation(14401));
    }
}
