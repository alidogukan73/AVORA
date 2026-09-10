package com.alidogukan.avora.plantassistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class PlantFollowUpStoreTest {
    private static final String PREFS = "plant_assistant_followups";
    private static final long START = 1_800_000_000L;

    private Context context;
    private PlantFollowUpStore store;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        clear();
        store = new PlantFollowUpStore(context);
    }

    @After
    public void tearDown() {
        clear();
    }

    @Test
    public void earlyAnalysisKeepsOriginalTaskOpenAndDueDateStable() {
        PlantFollowUpStore.Result first = store.registerAnalysis(
                "zone-1", "season-1", "photo-1", "İlk analiz", START);

        PlantFollowUpStore.Result early = store.registerAnalysis(
                "zone-1", "season-1", "photo-2", "Erken analiz",
                first.dueAtEpoch - 1L);

        assertEquals("SCHEDULED", first.type);
        assertEquals("SCHEDULED_EXISTING", early.type);
        assertEquals(first.dueAtEpoch, early.dueAtEpoch);
        assertTrue(store.dueUnnotified(first.dueAtEpoch - 1L).isEmpty());

        List<PlantFollowUpStore.DueTask> due =
                store.dueUnnotified(first.dueAtEpoch);
        assertEquals(1, due.size());
        assertEquals("photo-1", due.get(0).photoId);
        assertEquals("season-1", due.get(0).seasonId);

        PlantFollowUpStore.Result repeatedEarlyPhoto = store.registerAnalysis(
                "zone-1", "season-1", "photo-2", "Erken fotoğraf tekrar",
                first.dueAtEpoch);
        assertEquals("SCHEDULED_EXISTING", repeatedEarlyPhoto.type);
        assertEquals(first.dueAtEpoch, repeatedEarlyPhoto.dueAtEpoch);
        assertEquals("photo-1",
                store.dueUnnotified(first.dueAtEpoch).get(0).photoId);

        PlantFollowUpStore.Result freshPhoto = store.registerAnalysis(
                "zone-1", "season-1", "photo-3", "Gerçek takip",
                first.dueAtEpoch);
        assertEquals("COMPLETED", freshPhoto.type);
        assertEquals("İlk analiz", freshPhoto.previousTitle);
    }

    @Test
    public void dueAnalysisCompletesPreviousTaskAndStartsNextCycle() {
        PlantFollowUpStore.Result first = store.registerAnalysis(
                "zone-1", "season-1", "photo-1", "İlk analiz", START);

        PlantFollowUpStore.Result second = store.registerAnalysis(
                "zone-1", "season-1", "photo-2", "İkinci analiz",
                first.dueAtEpoch);

        assertEquals("COMPLETED", second.type);
        assertEquals("İlk analiz", second.previousTitle);
        assertEquals(first.dueAtEpoch + PlantFollowUpStore.FOLLOW_UP_DELAY_SECONDS,
                second.dueAtEpoch);
        assertTrue(store.dueUnnotified(first.dueAtEpoch).isEmpty());

        List<PlantFollowUpStore.DueTask> next =
                store.dueUnnotified(second.dueAtEpoch);
        assertEquals(1, next.size());
        assertEquals("photo-2", next.get(0).photoId);
        assertEquals("season-1", next.get(0).seasonId);

        PlantFollowUpStore.Result third = store.registerAnalysis(
                "zone-1", "season-1", "photo-3", "Üçüncü analiz",
                second.dueAtEpoch);
        assertEquals("COMPLETED", third.type);
        assertEquals("İkinci analiz", third.previousTitle);
        assertEquals(second.dueAtEpoch + PlantFollowUpStore.FOLLOW_UP_DELAY_SECONDS,
                third.dueAtEpoch);
    }

    @Test
    public void differentOrEmptySeasonCannotCompleteAnotherSeasonsTask() {
        PlantFollowUpStore.Result first = store.registerAnalysis(
                "zone-1", "season-1", "photo-1", "Sezon 1", START);

        PlantFollowUpStore.Result otherSeason = store.registerAnalysis(
                "zone-1", "season-2", "photo-2", "Sezon 2",
                first.dueAtEpoch);
        PlantFollowUpStore.Result emptySeason = store.registerAnalysis(
                "zone-1", "", "photo-3", "Sezonsuz",
                first.dueAtEpoch);

        assertEquals("SCHEDULED", otherSeason.type);
        assertEquals("SCHEDULED", emptySeason.type);
        List<PlantFollowUpStore.DueTask> due =
                store.dueUnnotified(first.dueAtEpoch);
        assertEquals(1, due.size());
        assertEquals("photo-1", due.get(0).photoId);
        assertEquals("season-1", due.get(0).seasonId);
    }

    @Test
    public void samePhotoIsIdempotentEvenAfterDueDate() {
        PlantFollowUpStore.Result first = store.registerAnalysis(
                " zone-1 ", " season-1 ", " photo-1 ", "İlk", START);

        PlantFollowUpStore.Result duplicate = store.registerAnalysis(
                "zone-1", "season-1", "photo-1", "Tekrar",
                first.dueAtEpoch + 60L);

        assertEquals("SCHEDULED_EXISTING", duplicate.type);
        assertEquals(first.dueAtEpoch, duplicate.dueAtEpoch);
        List<PlantFollowUpStore.DueTask> due =
                store.dueUnnotified(first.dueAtEpoch + 60L);
        assertEquals(1, due.size());
        assertEquals("zone-1", due.get(0).zoneId);
        assertEquals("season-1", due.get(0).seasonId);
    }

    private void clear() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().clear().commit();
    }
}
