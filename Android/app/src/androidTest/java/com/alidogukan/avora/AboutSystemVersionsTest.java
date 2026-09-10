package com.alidogukan.avora;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertTrue;

import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.alidogukan.avora.activities.AboutActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidJUnit4.class)
public final class AboutSystemVersionsTest {
    @Test public void systemCardContainsDeviceVersionsInExpectedOrder() {
        try (ActivityScenario<AboutActivity> ignored =
                     ActivityScenario.launch(AboutActivity.class)) {
            int[] ids = {
                    R.id.txtSystemAppVersion,
                    R.id.txtBackendVersion,
                    R.id.txtEsp32Version,
                    R.id.txtNodeMcuVersion,
                    R.id.txtDeviceId
            };
            AtomicInteger previousTop = new AtomicInteger(Integer.MIN_VALUE);
            for (int id : ids) {
                onView(withId(id)).check((view, error) -> {
                    if (error != null) throw error;
                    View row = (View) view.getParent();
                    assertTrue("System information rows must be ordered",
                            row.getTop() > previousTop.get());
                    previousTop.set(row.getTop());
                });
            }

            onView(withId(R.id.txtNodeMcuVersion))
                    .perform(scrollTo())
                    .check(matches(isCompletelyDisplayed()));
        }
    }
}
