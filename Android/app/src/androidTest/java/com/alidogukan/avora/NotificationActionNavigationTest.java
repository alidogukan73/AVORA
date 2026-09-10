package com.alidogukan.avora;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.alidogukan.avora.activities.NotificationDetailActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class NotificationActionNavigationTest {
    @Test public void photoFollowUpActionOpensPlantAssistant() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(context, NotificationDetailActivity.class)
                .putExtra("id", "notification-action-" + System.nanoTime())
                .putExtra("type", "PHOTO_FOLLOW_UP")
                .putExtra("priority", "NORMAL")
                .putExtra("zone_id", "zone-002")
                .putExtra("title", "Fotoğraf takip zamanı")
                .putExtra("description", "Yeni bir fotoğraf ekleyin.")
                .putExtra("source_key", "photo_follow_up:test-photo")
                .putExtra("created_at_epoch", System.currentTimeMillis() / 1000L);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try (ActivityScenario<NotificationDetailActivity> ignored =
                     ActivityScenario.launch(intent)) {
            onView(withId(R.id.btnNotificationDetailAction))
                    .check(matches(isDisplayed()))
                    .check(matches(withText(R.string.notification_action_add_photo)))
                    .perform(click());
            onView(withId(R.id.cardDoctorPhotoPicker)).check(matches(isDisplayed()));
        }
    }
}
