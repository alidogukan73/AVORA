package com.alidogukan.avora.statistics;

import static org.junit.Assert.*;

import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.Statistics;
import com.alidogukan.avora.models.WateringHistory;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.InternalHelpers;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.snapshot.IndexedNode;
import com.google.firebase.database.snapshot.NodeUtilities;
import org.junit.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class StatisticsCalculatorTest {
    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");
    private static final long NOW = Instant.parse("2026-09-04T09:00:00Z").toEpochMilli();

    @Test public void totalsIncludeMoreThanFiftyRecordsAndPastSeasons() {
        List<WateringHistory> history = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            WateringHistory item = record("zone-002", "2026-08-01T10:00:00+03:00", 60, true);
            item.setSeasonId(i < 60 ? "old-season" : "new-season");
            history.add(item);
        }
        Statistics all = calculate(history, "");
        Statistics selected = calculate(history, "zone-002");
        assertEquals(120, all.getTotalWaterings());
        assertEquals(120, selected.getTotalWaterings());
        assertEquals(7200, selected.getTotalWateringSeconds());
    }

    @Test public void eachZoneFiltersExactIdentityAndAllIncludesUnassignedHistory() {
        List<WateringHistory> history = List.of(
                record("zone-001", "2026-09-04T09:00:00+03:00", 20, true),
                record("zone-002", "2026-09-04T09:00:00+03:00", 40, false),
                record("", "2026-09-04T09:00:00+03:00", 10, true));
        assertEquals(3, calculate(history, "").getTotalWaterings());
        assertEquals(20, calculate(history, "zone-001").getTotalWateringSeconds());
        assertEquals(40, calculate(history, "zone-002").getTotalWateringSeconds());
        assertEquals(0, calculate(history, "zone-003").getTotalWaterings());
    }

    @Test public void completedInterruptedAverageAndSuccessUseSameRecords() {
        Statistics result = calculate(List.of(
                record("zone-001", "2026-09-04T09:00:00+03:00", 20, true),
                record("zone-001", "2026-09-04T10:00:00+03:00", 41, false),
                record("zone-001", "2026-09-04T11:00:00+03:00", 60, true)), "");
        assertEquals(3, result.getTotalWaterings());
        assertEquals(2, result.getCompletedWaterings());
        assertEquals(1, result.getInterruptedWaterings());
        assertEquals(121, result.getTotalWateringSeconds());
        assertEquals(40, result.getAverageDuration());
        assertEquals(67, result.getSuccessRate());
        assertEquals(121, result.getWateringSecondsToday());
    }

    @Test public void latestUsesTimestampNotListPositionOrRecordKey() {
        WateringHistory oldest = record("zone-001", "2026-09-02T09:00:00+03:00", 10, true);
        oldest.setRecordId("z");
        WateringHistory latest = record("zone-001", "2026-09-04T09:00:00+03:00", 90, false);
        latest.setRecordId("a");
        latest.setStopReason("manual_stop");
        Statistics result = calculate(List.of(oldest, latest), "");
        assertEquals(90, result.getLastWateringDuration());
        assertEquals("manual_stop", result.getLastStopReason());
        assertEquals("2026-09-04T06:00:00Z", result.getStatisticsDate());
    }

    @Test public void timestampsWithDifferentOffsetsSortByActualInstant() {
        Statistics result = calculate(List.of(
                record("zone-001", "2026-09-04T10:00:00+03:00", 10, true),
                record("zone-001", "2026-09-04T08:00:00Z", 20, true)), "");
        assertEquals(20, result.getLastWateringDuration());
    }

    @Test public void todayUsesDeviceTimeZoneNotTimestampDatePrefix() {
        Statistics result = calculate(List.of(
                record("zone-001", "2026-09-03T22:30:00Z", 60, true)), "");
        assertEquals(1, result.getWateringsToday());
    }

    @Test public void midnightSpanningWateringBelongsToStartDay() {
        WateringHistory item = record("zone-001", "2026-09-04T00:01:00+03:00", 120, true);
        item.setStartedAt("2026-09-03T23:59:00+03:00");
        assertEquals(0, calculate(List.of(item), "").getWateringsToday());
        assertEquals(1, calculate(List.of(item), "").getTotalWaterings());
    }

    @Test public void dayChangeResetsTodayWithoutNeedingAnotherWatering() {
        List<WateringHistory> history = List.of(
                record("zone-001", "2026-09-04T11:00:00+03:00", 60, true));
        assertEquals(1, calculate(history, "").getWateringsToday());
        Statistics nextDay = StatisticsCalculator.calculate(history, "",
                Instant.parse("2026-09-04T21:01:00Z").toEpochMilli(), ZONE);
        assertEquals(0, nextDay.getWateringsToday());
        assertEquals(0, nextDay.getWateringSecondsToday());
        assertEquals(1, nextDay.getTotalWaterings());
    }

    @Test public void legacyFinishOnlyRecordStillCountsToday() {
        WateringHistory item = record("zone-001", "2026-09-04 10:00:00", 60, true);
        item.setStartedAt(null);
        assertEquals(1, calculate(List.of(item), "").getWateringsToday());
        assertEquals("2026-09-04T07:00:00Z", calculate(List.of(item), "").getStatisticsDate());
    }

    @Test public void missingFinishFallsBackToStartForLastRecord() {
        WateringHistory item = record("zone-001", null, 60, true);
        item.setStartedAt("2026-09-04T10:00:00+03:00");
        assertEquals(1, calculate(List.of(item), "").getWateringsToday());
        assertEquals(60, calculate(List.of(item), "").getLastWateringDuration());
    }

    @Test public void badTimestampsDoNotBecomeTodayOrInventLastDate() {
        WateringHistory item = record("zone-001", "invalid", 60, true);
        item.setStartedAt("");
        Statistics result = calculate(List.of(item), "");
        assertEquals(1, result.getTotalWaterings());
        assertEquals(0, result.getWateringsToday());
        assertEquals("", result.getStatisticsDate());
    }

    @Test public void futureTimestampDoesNotBecomeLatestOrToday() {
        Statistics result = calculate(List.of(
                record("zone-001", "2026-09-04T08:00:00Z", 30, true),
                record("zone-001", "2026-09-04T10:00:00Z", 60, true)), "");
        assertEquals(1, result.getWateringsToday());
        assertEquals(30, result.getLastWateringDuration());
    }

    @Test public void nullAndEmptyInputsAreSafe() {
        assertEquals(0, calculate(null, "").getTotalWaterings());
        assertEquals(0, calculate(Collections.emptyList(), "").getSuccessRate());
        WateringHistory item = record(null, "2026-09-04T08:00:00Z", 30, true);
        assertEquals(1, calculate(Arrays.asList(null, item), null).getTotalWaterings());
    }

    @Test public void missingMoistureDoesNotMasqueradeAsZeroPercent() {
        Statistics result = calculate(List.of(record("zone-001",
                "2026-09-04T08:00:00Z", 30, true)), "");
        assertFalse(result.hasMoistureReadings());
        assertEquals(0, result.getBeforeMoisture()); // old callers remain compatible
    }

    @Test public void realZeroMoistureIsPreservedAndDeltaIsRecomputed() {
        WateringHistory item = record("zone-001", "2026-09-04T08:00:00Z", 30, true);
        item.setMoistureBefore(0);
        item.setMoistureAfter(25);
        item.setMoistureDelta(999); // legacy inconsistent aggregate
        Statistics result = calculate(List.of(item), "");
        assertTrue(result.hasMoistureReadings());
        assertEquals(0, result.getBeforeMoisture());
        assertEquals(25, result.getAfterMoisture());
        assertEquals(25, result.getMoistureDelta());
    }

    @Test public void incompleteAndOutOfRangeMoistureAreNotShown() {
        WateringHistory item = record("zone-001", "2026-09-04T08:00:00Z", 30, true);
        item.setMoistureBefore(10);
        assertFalse(calculate(List.of(item), "").hasMoistureReadings());
        item.setMoistureAfter(101);
        assertFalse(calculate(List.of(item), "").hasMoistureReadings());
        item.setMoistureBefore(-1);
        item.setMoistureAfter(50);
        assertFalse(calculate(List.of(item), "").hasMoistureReadings());
    }

    @Test public void latestMissingReadingDoesNotReuseOlderMoisture() {
        WateringHistory old = record("zone-001", "2026-09-03T08:00:00Z", 30, true);
        old.setMoistureBefore(20);
        old.setMoistureAfter(40);
        WateringHistory latest = record("zone-001", "2026-09-04T08:00:00Z", 60, true);
        assertFalse(calculate(List.of(old, latest), "").hasMoistureReadings());
    }

    @Test public void negativeDurationsNeverLowerTotalsOrShowNegativeTime() {
        Statistics result = calculate(List.of(
                record("zone-001", "2026-09-04T07:00:00Z", 30, true),
                record("zone-001", "2026-09-04T08:00:00Z", -20, false)), "");
        assertEquals(30, result.getTotalWateringSeconds());
        assertEquals(0, result.getLastWateringDuration());
        assertEquals(15, result.getAverageDuration());
    }

    @Test public void hugeDurationsDoNotOverflowIntoNegativeValues() {
        Statistics result = calculate(List.of(
                record("zone-001", "2026-09-04T07:00:00Z", Long.MAX_VALUE, true),
                record("zone-001", "2026-09-04T08:00:00Z", 30, true)), "");
        assertEquals(Long.MAX_VALUE, result.getTotalWateringSeconds());
        assertEquals(Long.MAX_VALUE, result.getWateringSecondsToday());
    }

    @Test public void validSelectedZoneIsKeptDuringReloadAndUpdates() {
        GardenZone zone = zone("zone-002");
        assertEquals("zone-002", StatisticsCalculator.resolveSelectedZone("zone-002", null));
        assertEquals("zone-002", StatisticsCalculator.resolveSelectedZone("zone-002", List.of(zone)));
        assertEquals("", StatisticsCalculator.resolveSelectedZone("", List.of(zone)));
    }

    @Test public void removedDisabledAndInvalidSelectionsReturnToAll() {
        assertEquals("", StatisticsCalculator.resolveSelectedZone("zone-002", List.of(zone("zone-001"))));
        GardenZone disabled = zone("zone-002");
        disabled.setEnabled(false);
        assertEquals("", StatisticsCalculator.resolveSelectedZone("zone-002", List.of(disabled)));
        GardenZone archived = zone("zone-002");
        archived.setLifecycle_status("INACTIVE");
        assertEquals("", StatisticsCalculator.resolveSelectedZone("zone-002", List.of(archived)));
        assertEquals("", StatisticsCalculator.resolveSelectedZone("zone-999", List.of(zone("zone-999"))));
    }

    @Test public void changingZoneDoesNotReusePreviousLastWatering() {
        List<WateringHistory> history = List.of(
                record("zone-001", "2026-09-04T08:00:00Z", 30, true));
        assertEquals(30, calculate(history, "zone-001").getLastWateringDuration());
        Statistics empty = calculate(history, "zone-002");
        assertEquals(0, empty.getTotalWaterings());
        assertEquals("", empty.getStatisticsDate());
        assertFalse(empty.hasMoistureReadings());
    }

    @Test public void calculationDoesNotChangeOriginalHistory() {
        WateringHistory item = record("zone-001", "2026-09-04T08:00:00Z", -1, true);
        item.setMoistureDelta(999);
        List<WateringHistory> immutable = List.of(item);
        calculate(immutable, "");
        assertSame(item, immutable.get(0));
        assertEquals(-1, item.getDuration());
        assertEquals(999, item.getMoistureDelta());
    }

    @Test public void firebaseMappingDistinguishesMissingFromRealZeroMeasurements() {
        WateringHistory absent = snapshot(Map.of("duration", 30L)).getValue(WateringHistory.class);
        assertNotNull(absent);
        assertFalse(absent.hasMoistureReadings());
        WateringHistory zero = snapshot(Map.of("moisture_before", 0L, "moisture_after", 0L))
                .getValue(WateringHistory.class);
        assertNotNull(zero);
        assertTrue(zero.hasMoistureReadings());
        assertEquals(0, zero.getMoistureBefore());
        Statistics noStats = snapshot(Map.of("total_waterings", 1L)).getValue(Statistics.class);
        assertNotNull(noStats);
        assertFalse(noStats.hasMoistureReadings());
        Statistics zeroStats = snapshot(Map.of("before_moisture", 0L, "after_moisture", 0L))
                .getValue(Statistics.class);
        assertNotNull(zeroStats);
        assertTrue(zeroStats.hasMoistureReadings());
    }

    private static Statistics calculate(List<WateringHistory> history, String zone) {
        return StatisticsCalculator.calculate(history, zone, NOW, ZONE);
    }

    private static WateringHistory record(String zone, String date, long seconds, boolean completed) {
        WateringHistory item = new WateringHistory();
        item.setZoneId(zone);
        item.setStartedAt(date);
        item.setFinishedAt(date);
        item.setDuration(seconds);
        item.setCompleted(completed);
        return item;
    }

    private static GardenZone zone(String id) {
        GardenZone zone = new GardenZone();
        zone.setZone_id(id);
        zone.setEnabled(true);
        return zone;
    }

    private static DataSnapshot snapshot(Map<String, Object> value) {
        return InternalHelpers.createDataSnapshot(
                InternalHelpers.createReference(null, new Path("statistics-test")),
                IndexedNode.from(NodeUtilities.NodeFromJSON(value)));
    }
}
