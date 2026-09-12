package com.alidogukan.avora;

import android.app.Application;

import com.alidogukan.avora.appcheck.AppCheckProviderInstaller;
import com.alidogukan.avora.fertilization.FertilizerReminderScheduler;
import com.alidogukan.avora.language.AvoraLanguageManager;
import com.alidogukan.avora.nas.NasAutomaticBackupScheduler;
import com.alidogukan.avora.nas.NasAutomaticBackupSettings;
import com.alidogukan.avora.nas.NasPhotoBackupScheduler;
import com.alidogukan.avora.nas.NasPhotoBackupSettings;
import com.alidogukan.avora.notifications.DeviceConnectionNotificationMonitor;
import com.alidogukan.avora.notifications.NotificationSignalScheduler;
import com.alidogukan.avora.theme.AvoraThemeManager;
import com.google.firebase.FirebaseApp;

public class AvoraApplication extends Application {

    @SuppressWarnings("FieldCanBeLocal")
    private DeviceConnectionNotificationMonitor deviceConnectionMonitor;

    @Override
    public void onCreate() {
        super.onCreate();

        FirebaseApp.initializeApp(this);
        AppCheckProviderInstaller.install();

        AvoraLanguageManager.applySavedLanguage(this);
        AvoraThemeManager.applySavedTheme(this);

        deviceConnectionMonitor =
                new DeviceConnectionNotificationMonitor(this);

        deviceConnectionMonitor.start();

        // Keep background notification checks scheduled regardless of the entry screen.
        NotificationSignalScheduler.schedule(this);
        FertilizerReminderScheduler.schedule(this);
        if (new NasAutomaticBackupSettings(this).isEnabled()) {
            NasAutomaticBackupScheduler.schedule(this);
        }
        if (new NasPhotoBackupSettings(this).isEnabled()) {
            NasPhotoBackupScheduler.schedule(this);
        }
    }
}
