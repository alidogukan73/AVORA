package com.alidogukan.avora.history;

import static org.junit.Assert.*;

import com.alidogukan.avora.R;
import com.alidogukan.avora.models.GardenSeason;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.WateringHistory;
import com.alidogukan.avora.statistics.StatisticsCalculator;
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
import java.util.Locale;
import java.util.Map;

public class WateringHistoryPresentationTest {
    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final long NOW = Instant.parse("2026-09-04T09:00:00Z").toEpochMilli();

    @Test public void completeHistoryRetainsMoreThanFiftyRecordsBeforeFiltering() {
        List<WateringHistory> history = new ArrayList<>();
        for (int i = 0; i < 150; i++) history.add(record(String.valueOf(i),
                i < 80 ? "zone-001" : "zone-002", "2026-08-01T10:00:00+03:00"));
        assertEquals(150, select(history, "").size());
        assertEquals(80, select(history, "zone-001").size());
        assertEquals(70, select(history, "zone-002").size());
    }

    @Test public void allKeepsLegacyRecordsButZoneFilterDoesNotGuessTheirZone() {
        WateringHistory old = record("old", "", "2026-07-01T10:00:00");
        WateringHistory zone = record("zone", "zone-001", "2026-08-01T10:00:00");
        assertEquals(2, select(List.of(old, zone), "").size());
        assertEquals(List.of(zone), select(List.of(old, zone), "zone-001"));
        assertTrue(select(List.of(old, zone), "zone-002").isEmpty());
    }

    @Test public void sortUsesDisplayedStartTimeNotFirebaseKey() {
        WateringHistory newer = record("a", "zone-001", "2026-09-04T08:00:00Z");
        WateringHistory older = record("z", "zone-001", "2026-09-03T08:00:00Z");
        assertEquals(List.of(newer, older), select(List.of(older, newer), ""));
    }

    @Test public void sortComparesInstantsRatherThanOffsetDateStrings() {
        WateringHistory newer = record("a", "", "2026-09-04T08:00:00Z");
        WateringHistory older = record("z", "", "2026-09-04T10:00:00+03:00");
        assertEquals(List.of(newer, older), select(List.of(older, newer), ""));
    }

    @Test public void offsetAndMicrosecondsDisplayInCurrentTimeZone() {
        WateringHistory row = record("a", "", "2026-09-03T22:30:00.123456Z");
        assertEquals("04.09.2026", WateringHistoryPresentation.date(row, ISTANBUL, Locale.US));
        assertEquals("01:30", WateringHistoryPresentation.time(row, ISTANBUL, Locale.US));
        assertEquals("22:30", WateringHistoryPresentation.time(row, ZoneId.of("UTC"), Locale.US));
    }

    @Test public void malformedAndImpossibleDatesAreNotNormalizedToAnotherDay() {
        for (String value : List.of("bad", "2026-02-30T14:00:00", "2026-09-04T25:00:00")) {
            WateringHistory row = record("a", "", value);
            assertEquals("—", WateringHistoryPresentation.date(row, ISTANBUL, Locale.US));
            assertEquals("—", WateringHistoryPresentation.time(row, ISTANBUL, Locale.US));
        }
    }

    @Test public void finishOnlyLegacyRecordHasReadableDate() {
        WateringHistory row = record("a", "", null);
        row.setFinishedAt("2026-09-04 10:25:00");
        assertEquals("04.09.2026", WateringHistoryPresentation.date(row, ISTANBUL, Locale.US));
        assertEquals("10:25", WateringHistoryPresentation.time(row, ISTANBUL, Locale.US));
    }

    @Test public void unknownDatesSortAfterDatedRowsAndTiesAreStable() {
        WateringHistory unknown = record("z", "", "");
        WateringHistory a = record("a", "", "2026-09-04T08:00:00Z");
        WateringHistory b = record("b", "", "2026-09-04T08:00:00Z");
        assertEquals(List.of(b, a, unknown), select(List.of(unknown, a, b), ""));
    }

    @Test public void selectionDoesNotMutateSourceAndIgnoresNullRows() {
        WateringHistory row = record("a", "zone-001", "");
        List<WateringHistory> source = Arrays.asList(null, row);
        assertEquals(List.of(row), select(source, "zone-001"));
        assertNull(source.get(0));
        assertSame(row, source.get(1));
        assertTrue(select(null, null).isEmpty());
    }

    @Test public void missingPartialAndInvalidReadingsStayUnknown() {
        WateringHistory row = record("a", "", "");
        assertNull(WateringHistoryPresentation.before(row));
        assertNull(WateringHistoryPresentation.after(row));
        assertNull(WateringHistoryPresentation.delta(row));
        row.setMoistureBefore(0);
        assertEquals(Long.valueOf(0), WateringHistoryPresentation.before(row));
        assertNull(WateringHistoryPresentation.delta(row));
        row.setMoistureAfter(101);
        assertNull(WateringHistoryPresentation.after(row));
        assertNull(WateringHistoryPresentation.delta(row));
    }

    @Test public void deltaUsesActualReadingsInsteadOfStaleStoredDifference() {
        WateringHistory row = record("a", "", "");
        row.setMoistureBefore(50);
        row.setMoistureAfter(30);
        row.setMoistureDelta(999);
        assertEquals(Long.valueOf(-20), WateringHistoryPresentation.delta(row));
    }

    @Test public void meanExcludesUnknownMeasurementsAndKeepsRealZeroChange() {
        WateringHistory unknown = record("a", "", "");
        WateringHistory measured = record("b", "", "");
        measured.setMoistureBefore(10); measured.setMoistureAfter(30);
        WateringHistory zero = record("c", "", "");
        zero.setMoistureBefore(0); zero.setMoistureAfter(0);
        WateringHistoryPresentation.Summary summary = summarize(List.of(unknown, measured, zero));
        assertEquals(3, summary.totals.getTotalWaterings());
        assertEquals(2, summary.measuredCount);
        assertEquals(10.0, summary.averageDelta, 0.001);
    }

    @Test public void noMeasurementsDoNotProduceFakeZeroAverage() {
        assertNull(summarize(Collections.emptyList()).averageDelta);
        assertNull(summarize(List.of(record("a", "", ""))).averageDelta);
    }

    @Test public void summariesMatchStatisticsForIdenticalScope() {
        WateringHistory first = record("a", "zone-001", "2026-09-04T08:00:00Z");
        first.setDuration(80); first.setCompleted(true);
        WateringHistory second = record("b", "zone-001", "2026-09-03T08:00:00Z");
        second.setDuration(40);
        List<WateringHistory> values = List.of(first, second);
        WateringHistoryPresentation.Summary summary = summarize(values);
        assertEquals(2, summary.totals.getTotalWaterings());
        assertEquals(120, summary.totals.getTotalWateringSeconds());
        assertEquals(50, summary.totals.getSuccessRate());
        assertEquals(StatisticsCalculator.calculate(values, "", NOW, ISTANBUL).getSuccessRate(),
                summary.totals.getSuccessRate());
    }

    @Test public void negativeAndOverflowDurationsAreSafe() {
        WateringHistory a = record("a", "", ""); a.setDuration(-5);
        WateringHistory b = record("b", "", ""); b.setDuration(Long.MAX_VALUE);
        WateringHistory c = record("c", "", ""); c.setDuration(100);
        assertEquals(Long.MAX_VALUE, summarize(List.of(a,b,c)).totals.getTotalWateringSeconds());
    }

    @Test public void initialLoadingIsNotShownAsEmptyHistory() {
        assertEquals(WateringHistoryPresentation.State.LOADING,
                WateringHistoryPresentation.state(null, true, false));
        assertEquals(WateringHistoryPresentation.State.LOADING,
                WateringHistoryPresentation.state(Collections.emptyList(), true, false));
    }

    @Test public void failureIsNotShownAsAnEmptySuccessfulRead() {
        assertEquals(WateringHistoryPresentation.State.ERROR,
                WateringHistoryPresentation.state(null, false, true));
        assertEquals(WateringHistoryPresentation.State.ERROR,
                WateringHistoryPresentation.state(Collections.emptyList(), false, true));
        assertEquals(WateringHistoryPresentation.State.EMPTY,
                WateringHistoryPresentation.state(Collections.emptyList(), false, false));
    }

    @Test public void cachedRowsRemainVisibleDuringRetryAndReadFailure() {
        List<WateringHistory> data = List.of(record("a", "", ""));
        assertEquals(WateringHistoryPresentation.State.CONTENT,
                WateringHistoryPresentation.state(data, true, false));
        assertEquals(WateringHistoryPresentation.State.CONTENT,
                WateringHistoryPresentation.state(data, false, true));
    }

    @Test public void changeFromUnknownToRealZeroTriggersRowRebind() {
        WateringHistory old = record("a", "", "");
        WateringHistory next = record("a", "", "");
        assertTrue(WateringHistoryPresentation.sameRecord(old, next));
        assertTrue(WateringHistoryPresentation.sameContent(old, next));
        next.setMoistureBefore(0);
        assertFalse(WateringHistoryPresentation.sameContent(old, next));
    }

    @Test public void seasonMembershipChangeTriggersRowRebind() {
        WateringHistory old = record("a", "zone-001", "");
        WateringHistory next = record("a", "zone-001", "");
        next.setSeasonIds(List.of("season-1"));
        assertFalse(WateringHistoryPresentation.sameContent(old, next));
        next.setSeasonIds(Collections.emptyList()); next.setSeasonId("season-2");
        assertFalse(WateringHistoryPresentation.sameContent(old, next));
    }

    @Test public void missingRecordIdsAreNotTreatedAsOneSharedIdentity() {
        WateringHistory first = record("", "", "");
        WateringHistory second = record(null, "", "");
        assertFalse(WateringHistoryPresentation.sameRecord(first, second));
        assertTrue(WateringHistoryPresentation.sameRecord(first, first));
    }

    @Test public void archivedSeasonNameDoesNotBorrowCurrentCrop() {
        WateringHistory row = record("a", "zone-001", "");
        row.setSeasonId("old");
        GardenZone current = zone("zone-001", "Yeni alan", "Biber");
        GardenSeason archived = season("old", "zone-001", "Eski alan", "Domates", "🍅");
        assertEquals("Eski alan · 🍅 Domates", WateringHistoryPresentation.label(row,
                Map.of("zone-001", current), Map.of("old", archived), "Eski kayıt"));
    }

    @Test public void multipleSeasonsUseOnlyRecordedMembershipWithoutDuplicates() {
        WateringHistory row = record("a", "zone-001", "");
        row.setSeasonId("one"); row.setSeasonIds(List.of("one", "two", "foreign"));
        Map<String,GardenSeason> seasons = Map.of(
                "one", season("one", "zone-001", "1. Bölge", "Domates", "🍅"),
                "two", season("two", "zone-001", "1. Bölge", "Biber", "🌶"),
                "foreign", season("foreign", "zone-002", "2. Bölge", "Salatalık", "🥒"));
        assertEquals("1. Bölge · 🍅 Domates + 🌶 Biber",
                WateringHistoryPresentation.label(row, Map.of(), seasons, "Eski kayıt"));
    }

    @Test public void noSeasonUsesPhysicalIdentityRatherThanCurrentCrop() {
        WateringHistory row = record("a", "zone-002", "");
        assertEquals("Arka bahçe", WateringHistoryPresentation.label(row,
                Map.of("zone-002", zone("zone-002", "Arka bahçe", "Biber")), Map.of(), "Eski kayıt"));
        assertEquals("2. Bölge", WateringHistoryPresentation.label(row, Map.of(), Map.of(), "Eski kayıt"));
        row.setZoneId("");
        assertEquals("Eski kayıt", WateringHistoryPresentation.label(row, Map.of(), Map.of(), "Eski kayıt"));
    }

    @Test public void simulationAndUnstartedAttemptsAreNotLabeledAsInterruptedWatering() {
        WateringHistory row = record("a", "", "");
        row.setStopReason("VALVE_SIMULATION");
        assertEquals(WateringHistoryPresentation.Outcome.SIMULATED, WateringHistoryPresentation.outcome(row));
        row.setStopReason("SHARED_PUMP_BUSY");
        assertEquals(WateringHistoryPresentation.Outcome.NOT_STARTED, WateringHistoryPresentation.outcome(row));
        row.setStopReason("ERROR");
        assertEquals(WateringHistoryPresentation.Outcome.INTERRUPTED, WateringHistoryPresentation.outcome(row));
        row.setCompleted(true);
        assertEquals(WateringHistoryPresentation.Outcome.COMPLETED, WateringHistoryPresentation.outcome(row));
    }

    @Test public void backendReasonCodesHaveReadableResources() {
        assertEquals(R.string.history_reason_manual_mode, WateringHistoryPresentation.reasonResource("manual_mode"));
        assertEquals(R.string.history_reason_error, WateringHistoryPresentation.reasonResource("ERROR"));
        assertEquals(R.string.history_reason_simulation, WateringHistoryPresentation.reasonResource("VALVE_SIMULATION"));
        assertEquals(R.string.history_reason_pump_busy, WateringHistoryPresentation.reasonResource("SHARED_PUMP_BUSY"));
        assertEquals(R.string.history_reason_zero_duration, WateringHistoryPresentation.reasonResource("ZERO_DURATION"));
        assertEquals(0, WateringHistoryPresentation.reasonResource("new_reason"));
    }

    @Test public void firebasePreservesPartialReadingsIncludingZero() {
        WateringHistory row = InternalHelpers.createDataSnapshot(
                InternalHelpers.createReference(null, new Path("history-test")),
                IndexedNode.from(NodeUtilities.NodeFromJSON(Map.of("moisture_before", 0L))))
                .getValue(WateringHistory.class);
        assertNotNull(row);
        assertTrue(row.hasMoistureBefore());
        assertFalse(row.hasMoistureAfter());
        assertEquals(Long.valueOf(0), WateringHistoryPresentation.before(row));
        assertNull(WateringHistoryPresentation.after(row));
    }

    private static List<WateringHistory> select(List<WateringHistory> values, String zone) {
        return WateringHistoryPresentation.select(values, zone, ISTANBUL);
    }
    private static WateringHistoryPresentation.Summary summarize(List<WateringHistory> values) {
        return WateringHistoryPresentation.summarize(values, NOW, ISTANBUL);
    }
    private static WateringHistory record(String id, String zone, String date) {
        WateringHistory row = new WateringHistory();
        row.setRecordId(id); row.setZoneId(zone); row.setStartedAt(date);
        return row;
    }
    private static GardenZone zone(String id, String area, String crop) {
        GardenZone zone = new GardenZone();
        zone.setZone_id(id); zone.setArea_name(area); zone.setName(crop);
        return zone;
    }
    private static GardenSeason season(String id, String zone, String area, String crop, String emoji) {
        GardenSeason season = new GardenSeason();
        season.setSeason_id(id); season.setZone_id(zone); season.setArea_name(area);
        season.setZone_name(crop); season.setEmoji(emoji); season.setStatus("CLOSED");
        return season;
    }
}
