package com.alidogukan.avora.zones;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class ManualWateringDurationPolicyTest {
    @Test
    public void acceptsPreciseDurationsAcrossSafeRange() {
        assertEquals(5, ManualWateringDurationPolicy.fromHoursMinutesAndSeconds(
                0, 0, 5, 18000));
        assertEquals(150, ManualWateringDurationPolicy.fromHoursMinutesAndSeconds(
                0, 2, 30, 18000));
        assertEquals(7500, ManualWateringDurationPolicy.fromHoursMinutesAndSeconds(
                2, 5, 0, 18000));
        assertEquals(18000, ManualWateringDurationPolicy.fromHoursMinutesAndSeconds(
                5, 0, 0, 18000));
    }

    @Test
    public void rejectsUnsafeOrMalformedDurations() {
        assertThrows(IllegalArgumentException.class,
                () -> ManualWateringDurationPolicy.fromHoursMinutesAndSeconds(
                        0, 0, 4, 18000));
        assertThrows(IllegalArgumentException.class,
                () -> ManualWateringDurationPolicy.fromHoursMinutesAndSeconds(
                        5, 0, 1, 18000));
        assertThrows(IllegalArgumentException.class,
                () -> ManualWateringDurationPolicy.fromHoursMinutesAndSeconds(
                        0, 1, 60, 18000));
    }

    @Test
    public void invalidStoredValueFallsBackAndCommandsRemainBounded() {
        assertEquals(18000, ManualWateringDurationPolicy.configuredLimitOrDefault(0));
        assertEquals(125, ManualWateringDurationPolicy.configuredDurationOrDefault(125, 18000));
        assertEquals(5, ManualWateringDurationPolicy.clampToConfiguredLimit(-10, 18000));
        assertEquals(18000, ManualWateringDurationPolicy.clampToConfiguredLimit(99999, 18000));
        assertEquals(30, ManualWateringDurationPolicy.configuredDurationOrDefault(99999, 18000));
    }

    @Test
    public void onlyDurationsBeyondFourHoursNeedSecondConfirmation() {
        assertEquals(false, ManualWateringDurationPolicy.requiresExtendedConfirmation(14400));
        assertEquals(true, ManualWateringDurationPolicy.requiresExtendedConfirmation(14401));
    }
}
