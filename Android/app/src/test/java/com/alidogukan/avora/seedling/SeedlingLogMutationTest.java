package com.alidogukan.avora.seedling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.alidogukan.avora.models.SeedlingDailyLog;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class SeedlingLogMutationTest {
    @Test public void deletingLatestReturnsNewestRemainingLog() {
        SeedlingDailyLog older = log("log-a", 100L, 9);
        SeedlingDailyLog latest = log("log-b", 200L, 7);

        SeedlingDailyLog remaining = SeedlingRepository.latestExcluding(
                Arrays.asList(older, latest), latest.getLog_id());

        assertEquals("log-a", remaining.getLog_id());
        assertEquals(9, remaining.getHealthy_count());
    }

    @Test public void deletingOnlyLogLeavesNoSummarySource() {
        assertNull(SeedlingRepository.latestExcluding(
                Collections.singletonList(log("log-a", 100L, 9)), "log-a"));
    }

    @Test public void editingOlderLogDoesNotReplaceLatestSummary() {
        List<SeedlingDailyLog> values = Arrays.asList(
                log("log-a", 100L, 9),
                log("log-b", 200L, 7));

        assertFalse(SeedlingRepository.isLatestAfterUpdate(
                values, log("log-a", 100L, 6)));
        assertTrue(SeedlingRepository.isLatestAfterUpdate(
                values, log("log-b", 200L, 5)));
    }

    @Test public void pushIdBreaksEqualTimestampTieDeterministically() {
        SeedlingDailyLog first = log("-push-a", 200L, 9);
        SeedlingDailyLog second = log("-push-b", 200L, 8);

        assertEquals("-push-b", SeedlingRepository.latestExcluding(
                Arrays.asList(first, second), "missing").getLog_id());
    }

    private static SeedlingDailyLog log(String id, long epoch, int healthy) {
        SeedlingDailyLog value = new SeedlingDailyLog();
        value.setLog_id(id);
        value.setBatch_id("batch-1");
        value.setCreated_at_epoch(epoch);
        value.setHealthy_count(healthy);
        return value;
    }
}
