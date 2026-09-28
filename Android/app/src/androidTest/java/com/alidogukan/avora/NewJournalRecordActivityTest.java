package com.alidogukan.avora;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.Intent;
import android.view.ViewGroup;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.alidogukan.avora.activities.NewJournalRecordActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class NewJournalRecordActivityTest {
    @Test
    public void onlyTwoTypesAndAllFiveMilestonesRemainSelectable() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (ActivityScenario<NewJournalRecordActivity> scenario = ActivityScenario.launch(
                new Intent(context, NewJournalRecordActivity.class))) {
            scenario.onActivity(activity -> assertEquals(2, ((ViewGroup) activity
                    .findViewById(R.id.cardRecordObservation).getParent()).getChildCount()));
            onView(withText(R.string.record_type_observation)).check(matches(isDisplayed()));
            onView(withText(R.string.journal_milestone)).check(matches(isDisplayed()));
            onView(withId(R.id.txtJournalMilestoneType))
                    .check(matches(withEffectiveVisibility(GONE)));
            int[] milestones = {R.string.runtime_event_planting, R.string.runtime_event_flowering,
                    R.string.runtime_event_first_product, R.string.runtime_event_harvest,
                    R.string.runtime_event_special};
            for (int milestone : milestones) {
                onView(withId(R.id.cardRecordObservation)).perform(click());
                onView(withId(R.id.cardRecordEvent)).perform(click());
                for (int option : milestones) onView(withText(option)).check(matches(isDisplayed()));
                onView(withText(milestone)).perform(click());
                onView(withId(R.id.txtJournalMilestoneType)).check(matches(withText(milestone)))
                        .check(matches(isDisplayed()));
                onView(withText(R.string.journal_milestone)).check(matches(isDisplayed()));
            }
            onView(withId(R.id.cardNewRecordPhotoUpload)).perform(scrollTo(), click());
            onView(withText(R.string.runtime_take_photo)).check(matches(isDisplayed()));
            onView(withText(R.string.runtime_choose_gallery)).check(matches(isDisplayed()));
            pressBack();
            onView(withId(R.id.txtJournalMilestoneType)).check(matches(withText(R.string.runtime_event_special)));
            onView(withId(R.id.cardRecordObservation)).perform(scrollTo(), click());
            onView(withId(R.id.txtJournalMilestoneType)).check(matches(withEffectiveVisibility(GONE)));
            onView(withId(R.id.cardRecordEvent)).perform(click());
            pressBack();
            onView(withId(R.id.txtJournalMilestoneType)).check(matches(withEffectiveVisibility(GONE)));
        }
    }
}
