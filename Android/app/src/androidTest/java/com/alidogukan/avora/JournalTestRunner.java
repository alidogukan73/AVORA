package com.alidogukan.avora;

import android.app.Application;
import android.content.Context;
import androidx.test.runner.AndroidJUnitRunner;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.FirebaseDatabase;

/** Opt-in runner (-PjournalLocalEmulator=true). Requires adb reverse tcp:9000 tcp:9000. */
public final class JournalTestRunner extends AndroidJUnitRunner {
    @Override public Application newApplication(ClassLoader loader, String name, Context context)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        // Initialize before FirebaseInitProvider so no production Firebase instance is created.
        Application application = super.newApplication(loader, LocalApplication.class.getName(), context);
        FirebaseApp.initializeApp(application, new FirebaseOptions.Builder()
                    .setApplicationId("1:123456789:android:journaltest")
                    .setApiKey("fake-api-key-for-local-tests")
                    .setProjectId("demo-avora-journal")
                    .setDatabaseUrl("http://127.0.0.1:9000/?ns=demo-avora-journal-default-rtdb")
                    .build());
        com.google.firebase.auth.FirebaseAuth.getInstance();
        return application;
    }

    public static final class LocalApplication extends Application {
        @Override public Context getApplicationContext() { return this; }
    }
}
