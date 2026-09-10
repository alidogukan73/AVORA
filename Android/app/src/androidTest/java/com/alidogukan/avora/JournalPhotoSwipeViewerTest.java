package com.alidogukan.avora;

import static org.junit.Assert.assertEquals;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.swipeLeft;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.alidogukan.avora.activities.JournalRecordDetailActivity;
import com.alidogukan.avora.models.GardenPhoto;
import com.alidogukan.avora.photos.LocalGardenPhotoStore;
import com.alidogukan.avora.ui.TransformableImageView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class JournalPhotoSwipeViewerTest {
    private final List<GardenPhoto> created = new ArrayList<>();
    private LocalGardenPhotoStore store;
    private Context context;
    private String zoneId;
    private String groupId;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        store = new LocalGardenPhotoStore(context);
        long nonce = System.nanoTime();
        zoneId = "zone-swipe-" + nonce;
        groupId = "journal_record_swipe_" + nonce;
        created.add(store.save(bitmap(Color.GREEN), zoneId, "first", groupId));
        created.add(store.save(bitmap(Color.YELLOW), zoneId, "second", groupId));
    }

    @After
    public void tearDown() {
        for (GardenPhoto photo : created) store.delete(photo);
    }

    @Test
    public void openedJournalPhotoSwipesToTheNextPhoto() {
        Intent intent = new Intent(context, JournalRecordDetailActivity.class);
        intent.putExtra("zone_id", zoneId);
        intent.putExtra("photo_group_id", groupId);
        intent.putExtra("title", "Swipe test");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try (ActivityScenario<JournalRecordDetailActivity> ignored =
                     ActivityScenario.launch(intent)) {
            onView(withContentDescription(context.getString(
                    R.string.runtime_open_photo_description, 1, 2))).perform(click());
            onView(withId(R.id.txtGardenPhotoPagerPosition)).check(matches(withText(
                    context.getString(R.string.runtime_photo_swipe_position, 1, 2))));
            onView(withId(R.id.btnPhotoRotateLeft)).check(matches(isDisplayed())).perform(click());
            onView(withId(R.id.btnPhotoRotateRight)).check(matches(isDisplayed())).perform(click());
            onView(withId(R.id.btnPhotoFlipHorizontal)).check(matches(isDisplayed())).perform(click());
            onView(withId(R.id.btnPhotoFlipVertical)).check(matches(isDisplayed())).perform(click());
            onView(withId(R.id.btnPhotoZoomIn)).check(matches(isDisplayed())).perform(click());
            onView(withId(R.id.btnPhotoZoomOut)).check(matches(isDisplayed())).perform(click());
            onView(withId(R.id.btnPhotoReset)).check(matches(isDisplayed())).perform(click());
            onView(withId(R.id.btnPhotoRotateRight)).perform(click());
            onView(withId(R.id.btnGardenPhotoViewerClose)).perform(click());
            GardenPhoto persisted = store.load().stream()
                    .filter(photo -> zoneId.equals(photo.getZone_id())
                            && groupId.equals(photo.getRelated_application_id())
                            && photo.getRotation_degrees() == 90)
                    .findFirst()
                    .orElseThrow();
            assertEquals(90, persisted.getRotation_degrees());
            onView(withContentDescription(context.getString(
                    R.string.runtime_open_photo_description, 1, 2))).perform(click());
            onView(withContentDescription(context.getString(
                    R.string.runtime_photo_page_description, 1, 2))).check((view, error) -> {
                        if (error != null) throw error;
                        assertEquals(90,
                                ((TransformableImageView) view).getPhotoRotationDegrees());
                    });
            onView(withId(R.id.pagerGardenPhotos)).perform(swipeLeft());
            onView(withId(R.id.txtGardenPhotoPagerPosition)).check(matches(withText(
                    context.getString(R.string.runtime_photo_swipe_position, 2, 2))));
        }
    }

    @Test
    public void independentPlantAssistantAnalysesDoNotShareTheViewer() throws Exception {
        GardenPhoto selected = store.save(
                bitmap(Color.BLUE), zoneId, "selected", "plant_assistant");
        GardenPhoto unrelated = store.save(
                bitmap(Color.RED), zoneId, "unrelated", "plant_assistant");
        created.add(selected);
        created.add(unrelated);

        Intent intent = new Intent(context, JournalRecordDetailActivity.class);
        intent.putExtra("zone_id", zoneId);
        intent.putExtra("photo_path", selected.getLocal_path());
        intent.putExtra("photo_group_id", "plant_assistant");
        intent.putExtra("title", "Plant assistant");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try (ActivityScenario<JournalRecordDetailActivity> ignored =
                     ActivityScenario.launch(intent)) {
            onView(withId(R.id.btnRecordDelete)).check(matches(isDisplayed()));
            onView(withContentDescription(context.getString(
                    R.string.runtime_open_photo_description, 1, 1))).perform(click());
            onView(withId(R.id.txtGardenPhotoPagerPosition)).check(matches(withText(
                    context.getString(R.string.runtime_photo_swipe_position, 1, 1))));
        }
    }

    private static Bitmap bitmap(int color) {
        Bitmap result = Bitmap.createBitmap(240, 160, Bitmap.Config.ARGB_8888);
        result.eraseColor(color);
        return result;
    }
}
