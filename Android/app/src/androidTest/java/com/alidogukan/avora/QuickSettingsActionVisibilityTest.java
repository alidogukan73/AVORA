package com.alidogukan.avora;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.alidogukan.avora.activities.SettingsHubActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidJUnit4.class)
public final class QuickSettingsActionVisibilityTest {
    @Test public void editorActionsAreVisibleOnOneRow() {
        try (ActivityScenario<SettingsHubActivity> ignored =
                     ActivityScenario.launch(SettingsHubActivity.class)) {
            onView(withId(R.id.btnSettingsToolbarAction)).perform(click());

            onView(withId(R.id.btnQuickSettingsRestoreDefaults))
                    .check(matches(withText(R.string.settings_quick_restore_defaults)))
                    .check(matches(isCompletelyDisplayed()));
            onView(withId(R.id.btnQuickSettingsCancel))
                    .check(matches(withText(R.string.settings_quick_cancel)))
                    .check(matches(isCompletelyDisplayed()));
            onView(withId(R.id.btnQuickSettingsSave))
                    .check(matches(withText(R.string.settings_quick_save)))
                    .check(matches(isCompletelyDisplayed()));

            AtomicInteger actionTop = new AtomicInteger(Integer.MIN_VALUE);
            onView(withId(R.id.btnQuickSettingsRestoreDefaults)).check((view, error) -> {
                if (error != null) throw error;
                actionTop.set(view.getTop());
            });
            onView(withId(R.id.btnQuickSettingsCancel)).check((view, error) -> {
                if (error != null) throw error;
                assertEquals(actionTop.get(), view.getTop());
            });
            onView(withId(R.id.btnQuickSettingsSave)).check((view, error) -> {
                if (error != null) throw error;
                assertEquals(actionTop.get(), view.getTop());
            });
        }
    }
}
