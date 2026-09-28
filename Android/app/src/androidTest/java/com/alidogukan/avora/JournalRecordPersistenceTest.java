package com.alidogukan.avora;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.alidogukan.avora.activities.NewJournalRecordActivity;
import com.alidogukan.avora.activities.PlantTimelineActivity;
import com.alidogukan.avora.config.AppInfo;
import com.alidogukan.avora.journal.LocalGardenEventStore;
import com.alidogukan.avora.models.GardenEvent;
import com.alidogukan.avora.models.GardenPhoto;
import com.alidogukan.avora.photos.LocalGardenPhotoStore;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public final class JournalRecordPersistenceTest {
    private static final String ZONE = "journal-local-test-zone";
    private static final String SEASON = "journal-local-test-season";
    private Context context;
    private DatabaseReference device;
    private Instrumentation.ActivityMonitor camera;

    @Before public void seedLocalEmulator() throws Exception {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getInstrumentation() instanceof JournalTestRunner);
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        FirebaseDatabase.getInstance().goOnline();
        device = FirebaseDatabase.getInstance().getReference("devices").child(AppInfo.DEVICE_ID);
        Map<String, Object> seed = new HashMap<>();
        seed.put("zones/" + ZONE + "/zone_id", ZONE);
        seed.put("zones/" + ZONE + "/enabled", true);
        seed.put("zones/" + ZONE + "/season/active_season_id", SEASON);
        seed.put("zones/" + ZONE + "/season/status", "ACTIVE");
        seed.put("garden_journal/seasons/" + SEASON + "/season_id", SEASON);
        seed.put("garden_journal/seasons/" + SEASON + "/zone_id", ZONE);
        seed.put("garden_journal/seasons/" + SEASON + "/status", "ACTIVE");
        seed.put("garden_journal/seasons/" + SEASON + "/started_at_epoch", 1L);
        Tasks.await(device.updateChildren(seed), 10, TimeUnit.SECONDS);
    }

    @After public void cleanup() throws Exception {
        if (camera != null) InstrumentationRegistry.getInstrumentation().removeMonitor(camera);
        if (context == null) return;
        LocalGardenEventStore events = new LocalGardenEventStore(context);
        for (GardenEvent event : events.load()) if (ZONE.equals(event.getZone_id())) events.delete(event.getId());
        LocalGardenPhotoStore photos = new LocalGardenPhotoStore(context);
        for (GardenPhoto photo : photos.load()) if (ZONE.equals(photo.getZone_id())) photos.delete(photo);
        Tasks.await(device.removeValue(), 10, TimeUnit.SECONDS);
    }

    @Test public void observationWithoutPhotoSavesAndReopens() throws Exception {
        saveAndReopen(false);
    }

    @Test public void milestoneWithPhotoSurvivesRecreationSavesAndReopens() throws Exception {
        saveAndReopen(true);
    }

    @Test public void blankDescriptionStopsBeforeSave() {
        try (ActivityScenario<NewJournalRecordActivity> scenario = ActivityScenario.launch(entryIntent())) {
            onView(withId(R.id.inputNewRecordNote)).perform(scrollTo(), replaceText("   "), closeSoftKeyboard());
            onView(withId(R.id.btnNewRecordSave)).perform(scrollTo(), click());
            onView(withId(R.id.inputNewRecordNote)).check(matches(hasErrorText(context.getString(R.string.journal_note_required))));
            scenario.onActivity(activity -> assertTrue(activity.findViewById(R.id.btnNewRecordSave).isEnabled()));
            assertTrue(new LocalGardenEventStore(context).load().stream().noneMatch(e -> ZONE.equals(e.getZone_id())));
        }
    }

    private Intent entryIntent() {
        return new Intent(context, NewJournalRecordActivity.class)
                .putExtra(NewJournalRecordActivity.EXTRA_ZONE_ID, ZONE)
                .putExtra(NewJournalRecordActivity.EXTRA_SEASON_ID, SEASON);
    }

    private void saveAndReopen(boolean withPhoto) throws Exception {
        String note = withPhoto ? "Journal attached photo check" : "Journal observation check";
        if (withPhoto) installCameraResult();
        GardenEvent saved;
        try (ActivityScenario<NewJournalRecordActivity> scenario = ActivityScenario.launch(entryIntent())) {
            onView(withId(R.id.cardNewRecordDate)).perform(scrollTo(), click());
            onView(isAssignableFrom(android.widget.DatePicker.class)).perform(setDateTime(true));
            onView(withId(android.R.id.button1)).perform(click());
            onView(withId(R.id.cardNewRecordTime)).perform(scrollTo(), click());
            onView(isAssignableFrom(android.widget.TimePicker.class)).perform(setDateTime(false));
            onView(withId(android.R.id.button1)).perform(click());
            if (withPhoto) {
                onView(withId(R.id.cardRecordEvent)).perform(click());
                onView(withText(R.string.runtime_event_harvest)).perform(click());
                onView(withId(R.id.cardNewRecordPhotoUpload)).perform(scrollTo(), click());
                onView(withText(R.string.runtime_take_photo)).perform(click());
            }
            onView(withId(R.id.inputNewRecordNote)).perform(scrollTo(), replaceText(note), closeSoftKeyboard());
            String[] displayed = new String[3];
            scenario.onActivity(activity -> {
                displayed[0] = ((TextView) activity.findViewById(R.id.txtNewRecordDate)).getText().toString();
                displayed[1] = ((TextView) activity.findViewById(R.id.txtNewRecordTime)).getText().toString();
                displayed[2] = ((TextView) activity.findViewById(R.id.txtNewRecordPhotoState)).getText().toString();
            });
            scenario.recreate();
            onView(withId(R.id.inputNewRecordNote)).check(matches(withText(note)));
            onView(withId(R.id.txtNewRecordDate)).check(matches(withText(displayed[0])));
            onView(withId(R.id.txtNewRecordTime)).check(matches(withText(displayed[1])));
            onView(withId(R.id.txtNewRecordPhotoState)).check(matches(withText(displayed[2])));
            if (withPhoto) onView(withId(R.id.txtJournalMilestoneType)).check(matches(withText(R.string.runtime_event_harvest)));
            onView(withId(R.id.btnNewRecordSave)).perform(scrollTo(), click());
            saved = awaitSavedEvent(note);
        }
        assertEquals(withPhoto ? "harvest" : "observation", saved.getType());
        assertEquals(SEASON, saved.getSeason_id());
        java.util.Calendar recorded = java.util.Calendar.getInstance();
        recorded.setTimeInMillis(saved.getOccurred_at_epoch() * 1000L);
        assertEquals(2025, recorded.get(java.util.Calendar.YEAR));
        assertEquals(java.util.Calendar.NOVEMBER, recorded.get(java.util.Calendar.MONTH));
        assertEquals(7, recorded.get(java.util.Calendar.DAY_OF_MONTH));
        assertEquals(10, recorded.get(java.util.Calendar.HOUR_OF_DAY));
        assertEquals(25, recorded.get(java.util.Calendar.MINUTE));
        long photoCount = new LocalGardenPhotoStore(context).load().stream()
                .filter(p -> ZONE.equals(p.getZone_id()) && ("journal_record_" + saved.getId()).equals(p.getRelated_application_id())).count();
        assertEquals(withPhoto ? 1L : 0L, photoCount);
        assertEquals(saved.getType(), Tasks.await(device.child("garden_journal/events").child(saved.getId()).child("type").get(), 10, TimeUnit.SECONDS).getValue(String.class));
        try (ActivityScenario<PlantTimelineActivity> timeline = ActivityScenario.launch(
                new Intent(context, PlantTimelineActivity.class).putExtra("zone_id", ZONE).putExtra("season_id", SEASON))) {
            // Selecting a real timeline card exercises its detail intent and photo-group linkage.
            onView(withText(note)).perform(scrollTo(), click());
            onView(withId(R.id.txtRecordDetail)).check(matches(withText(note)));
            onView(withId(R.id.layoutRecordPhotos)).check(matches(withEffectiveVisibility(withPhoto ? Visibility.VISIBLE : Visibility.GONE)));
        }
    }

    private static androidx.test.espresso.ViewAction setDateTime(boolean date) {
        return new androidx.test.espresso.ViewAction() {
            @Override public org.hamcrest.Matcher<android.view.View> getConstraints() {
                return isAssignableFrom(date ? android.widget.DatePicker.class : android.widget.TimePicker.class);
            }
            @Override public String getDescription() { return "Choose a non-default record date/time"; }
            @Override public void perform(androidx.test.espresso.UiController ui, android.view.View view) {
                if (date) ((android.widget.DatePicker) view).updateDate(2025, java.util.Calendar.NOVEMBER, 7);
                else {
                    ((android.widget.TimePicker) view).setHour(10);
                    ((android.widget.TimePicker) view).setMinute(25);
                }
                ui.loopMainThreadUntilIdle();
            }
        };
    }

    private GardenEvent awaitSavedEvent(String note) throws Exception {
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            for (GardenEvent event : new LocalGardenEventStore(context).load()) {
                if (ZONE.equals(event.getZone_id()) && note.equals(event.getNote())
                        && Tasks.await(device.child("garden_journal/events").child(event.getId()).get(), 5, TimeUnit.SECONDS).exists()) return event;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Journal record was not saved to local store and Firebase emulator");
    }

    private void installCameraResult() {
        camera = new Instrumentation.ActivityMonitor() {
            @Override public Instrumentation.ActivityResult onStartActivity(Intent intent) {
                if (!MediaStore.ACTION_IMAGE_CAPTURE.equals(intent.getAction())) return null;
                Uri output = androidx.core.content.IntentCompat.getParcelableExtra(intent, MediaStore.EXTRA_OUTPUT, Uri.class);
                Bitmap bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(android.graphics.Color.GREEN);
                try (OutputStream stream = context.getContentResolver().openOutputStream(output)) {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream));
                } catch (Exception error) { throw new AssertionError(error); }
                finally { bitmap.recycle(); }
                return new Instrumentation.ActivityResult(Activity.RESULT_OK, new Intent());
            }
        };
        InstrumentationRegistry.getInstrumentation().addMonitor(camera);
    }
}
