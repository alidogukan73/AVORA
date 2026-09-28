package com.alidogukan.avora;

import static org.junit.Assert.*;
import android.content.Context;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.alidogukan.avora.activities.DeviceHealthActivity;
import com.alidogukan.avora.config.AppInfo;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import org.junit.*;
import org.junit.runner.RunWith;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Runs only against the opt-in local Firebase test runner; no hardware commands are sent. */
@RunWith(AndroidJUnit4.class)
public final class DeviceHealthActivityTest {
    private DatabaseReference device;
    private Context context;

    @Before public void seed() throws Exception {
        Assume.assumeTrue(InstrumentationRegistry.getInstrumentation() instanceof JournalTestRunner);
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        FirebaseDatabase.getInstance().goOnline();
        device = FirebaseDatabase.getInstance().getReference("devices").child(AppInfo.DEVICE_ID);
        Map<String, Object> values = new HashMap<>();
        values.put("status/online", true);
        values.put("status/last_seen_epoch", now());
        values.put("status/relay", false);
        values.put("status/last_error", "");
        values.put("health/cpu_temperature", 40);
        values.put("health/cpu_usage", 20);
        values.put("health/memory_usage", 30);
        values.put("health/disk_usage", 40);
        values.put("health/wifi_signal", -50);
        values.put("health/updated_at", Instant.ofEpochSecond(now()).toString());
        Tasks.await(device.updateChildren(values), 10, TimeUnit.SECONDS);
    }

    @After public void clean() throws Exception {
        if (device == null) return;
        FirebaseDatabase.getInstance().goOnline();
        Tasks.await(device.removeValue(), 10, TimeUnit.SECONDS);
    }

    @Test public void healthyCachedMetricsNeverOverrideOfflinePi() throws Exception {
        try (ActivityScenario<DeviceHealthActivity> screen = ActivityScenario.launch(DeviceHealthActivity.class)) {
            awaitText(screen, R.id.txtOverallHealthBadge, R.string.health_badge_good);
            Tasks.await(device.child("status/online").setValue(false), 10, TimeUnit.SECONDS);
            awaitText(screen, R.id.txtOverallHealthBadge, R.string.health_badge_offline);
            awaitText(screen, R.id.txtOverallHealth, R.string.health_overall_offline);
            screen.onActivity(activity -> {
                android.widget.LinearLayout rows = activity.findViewById(R.id.layoutDiagnostics);
                StringBuilder text = new StringBuilder();
                for (int i = 0; i < rows.getChildCount(); i++) text.append(((TextView) rows.getChildAt(i)).getText());
                assertTrue(text.toString().contains(context.getString(R.string.diagnostics_pi_error)));
                assertTrue(text.toString().contains(context.getString(R.string.diagnostics_relay_unknown)));
                assertFalse(text.toString().contains(context.getString(R.string.diagnostics_relay_ok)));
                assertFalse(text.toString().contains(context.getString(R.string.diagnostics_error_clear)));
                assertEquals(context.getString(R.string.diagnostics_summary, 0, 7),
                        ((TextView) activity.findViewById(R.id.txtDiagnosticsSummary)).getText().toString());
            });
        }
    }

    @Test public void staleOrMissingHealthAndDisconnectedClientCannotLookHealthy() throws Exception {
        try (ActivityScenario<DeviceHealthActivity> screen = ActivityScenario.launch(DeviceHealthActivity.class)) {
            awaitText(screen, R.id.txtOverallHealthBadge, R.string.health_badge_good);
            Tasks.await(device.child("health/updated_at").setValue(Instant.ofEpochSecond(now() - 86400).toString()), 10, TimeUnit.SECONDS);
            awaitText(screen, R.id.txtOverallHealth, R.string.health_overall_stale);
            Tasks.await(device.child("health").removeValue(), 10, TimeUnit.SECONDS);
            awaitText(screen, R.id.txtLastHealthUpdate, R.string.health_update_waiting);
            awaitText(screen, R.id.txtCpuTemperature, R.string.health_detail_waiting);
            FirebaseDatabase.getInstance().goOffline();
            awaitText(screen, R.id.txtOverallHealth, R.string.health_overall_unverified);
            awaitText(screen, R.id.txtOverallHealthBadge, R.string.health_badge_unverified);
        }
    }

    @Test public void heartbeatExpiresWithoutAnotherFirebaseEvent() throws Exception {
        try (ActivityScenario<DeviceHealthActivity> screen = ActivityScenario.launch(DeviceHealthActivity.class)) {
            awaitText(screen, R.id.txtOverallHealthBadge, R.string.health_badge_good);
            Tasks.await(device.child("status/last_seen_epoch").setValue(now() - 25), 10, TimeUnit.SECONDS);
            awaitText(screen, R.id.txtOverallHealthBadge, R.string.health_badge_offline);
        }
    }

    private void awaitText(ActivityScenario<DeviceHealthActivity> screen, int view, int expected) throws Exception {
        long deadline = System.currentTimeMillis() + 15000;
        String[] actual = {""};
        do {
            screen.onActivity(activity -> actual[0] = ((TextView) activity.findViewById(view)).getText().toString());
            if (context.getString(expected).equals(actual[0])) return;
            Thread.sleep(100);
        } while (System.currentTimeMillis() < deadline);
        assertEquals(context.getString(expected), actual[0]);
    }

    private static long now() { return System.currentTimeMillis() / 1000L; }
}
