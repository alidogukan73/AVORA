package com.alidogukan.avora;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.alidogukan.avora.activities.PlantAssistantActivity;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PlantAssistantGrowthOptionTest {
    @Rule
    public final ActivityScenarioRule<PlantAssistantActivity> activityRule =
            new ActivityScenarioRule<>(PlantAssistantActivity.class);

    @Test
    public void growthStatusOptionIsVisibleInAnalysisOptions() {
        onView(withId(R.id.checkDoctorGrowthStatus))
                .perform(scrollTo())
                .check(matches(isDisplayed()))
                .check(matches(withText(R.string.plant_assistant_growth_status_option)));
    }

    @Test
    public void screenCanBeRecreatedWithoutLosingItsMainControls() {
        activityRule.getScenario().recreate();
        onView(withId(R.id.btnAnalyzePlant))
                .perform(scrollTo())
                .check(matches(isDisplayed()));
    }
}
