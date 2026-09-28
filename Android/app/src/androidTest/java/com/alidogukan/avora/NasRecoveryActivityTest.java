package com.alidogukan.avora;

import static org.junit.Assert.*;
import android.content.Intent;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.alidogukan.avora.activities.NasRecoveryActivity;
import com.google.android.material.textfield.TextInputEditText;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Local UI checks: no requests are sent to a real NAS. */
@RunWith(AndroidJUnit4.class)
public class NasRecoveryActivityTest {
    private ActivityScenario<NasRecoveryActivity> launch() {
        return ActivityScenario.launch(new Intent(InstrumentationRegistry.getInstrumentation()
                .getTargetContext(), NasRecoveryActivity.class).putExtra("email", "owner@example.com"));
    }
    @Test public void emailIsPreservedButSecretsAreNotRestoredAfterRecreation() throws Exception {
        try (ActivityScenario<NasRecoveryActivity> scenario = launch()) {
            if ("true".equals(InstrumentationRegistry.getArguments().getString("captureRecovery"))) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                android.graphics.Bitmap screenshot = InstrumentationRegistry.getInstrumentation()
                        .getUiAutomation().takeScreenshot();
                java.io.File target = new java.io.File(InstrumentationRegistry.getInstrumentation()
                        .getTargetContext().getExternalFilesDir(null), "nas-recovery.png");
                try (java.io.FileOutputStream stream = new java.io.FileOutputStream(target)) {
                    assertNotNull(screenshot);
                    screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream);
                }
            }
            scenario.onActivity(a -> {
                ((TextInputEditText) a.findViewById(R.id.recoveryEmail)).setText("second@example.com");
                ((TextInputEditText) a.findViewById(R.id.recoveryCode)).setText("one-time-code");
                ((TextInputEditText) a.findViewById(R.id.recoveryPassword)).setText("Test-password-2026");
                ((TextInputEditText) a.findViewById(R.id.recoveryRepeat)).setText("Test-password-2026");
            });
            scenario.recreate();
            scenario.onActivity(a -> {
                assertEquals("second@example.com", ((TextView) a.findViewById(R.id.recoveryEmail)).getText().toString());
                for (int id : new int[]{R.id.recoveryCode, R.id.recoveryPassword, R.id.recoveryRepeat}) {
                    assertEquals("", ((TextView) a.findViewById(id)).getText().toString());
                }
            });
        }
    }
    @Test public void invalidEmailAndMissingCodeStopRequests() {
        try (ActivityScenario<NasRecoveryActivity> scenario = launch()) {
            scenario.onActivity(a -> {
                TextInputEditText email = a.findViewById(R.id.recoveryEmail);
                email.setText("invalid");
                a.findViewById(R.id.recoverySend).performClick();
                assertNotNull(email.getError());
                email.setText("owner@example.com");
                a.findViewById(R.id.recoverySubmit).performClick();
                assertNotNull(((TextInputEditText) a.findViewById(R.id.recoveryCode)).getError());
                assertTrue(a.findViewById(R.id.recoverySend).isEnabled());
            });
        }
    }
    @Test public void weakPasswordAndMismatchStopRequests() {
        try (ActivityScenario<NasRecoveryActivity> scenario = launch()) {
            scenario.onActivity(a -> {
                ((TextInputEditText) a.findViewById(R.id.recoveryCode)).setText("one-time-code");
                TextInputEditText password = a.findViewById(R.id.recoveryPassword);
                TextInputEditText repeat = a.findViewById(R.id.recoveryRepeat);
                password.setText("short");
                repeat.setText("short");
                a.findViewById(R.id.recoverySubmit).performClick();
                assertNotNull(password.getError());
                password.setText("Test-password-2026");
                repeat.setText("Different-password-2026");
                a.findViewById(R.id.recoverySubmit).performClick();
                assertNotNull(repeat.getError());
                assertTrue(a.findViewById(R.id.recoverySubmit).isEnabled());
            });
        }
    }
}
