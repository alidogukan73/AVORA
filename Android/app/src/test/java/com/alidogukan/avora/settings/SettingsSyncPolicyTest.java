package com.alidogukan.avora.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SettingsSyncPolicyTest {
    @Test
    public void onlyStrictlyNewerCloudValueCanReplaceLocalState() {
        assertTrue(SettingsSyncPolicy.isCloudValueNewer(101L, 100L));
        assertFalse(SettingsSyncPolicy.isCloudValueNewer(100L, 100L));
        assertFalse(SettingsSyncPolicy.isCloudValueNewer(99L, 100L));
        assertFalse(SettingsSyncPolicy.isCloudValueNewer(0L, 0L));
    }

    @Test
    public void quietHoursStayInsideClockRange() {
        assertEquals(0, SettingsSyncPolicy.validHour(0L, 22));
        assertEquals(23, SettingsSyncPolicy.validHour(23L, 7));
        assertEquals(22, SettingsSyncPolicy.validHour(-1L, 22));
        assertEquals(7, SettingsSyncPolicy.validHour(24L, 7));
    }
}
