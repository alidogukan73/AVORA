# Local journal integration tests

These tests use a dedicated demo Realtime Database namespace and a test-only
Application. They do not start AVORA's NAS backup or notification schedulers.
Keep the Android emulator's Wi-Fi and mobile data disabled during the run;
the database connection uses ADB reverse forwarding.

1. Start the Firebase Realtime Database emulator on `127.0.0.1:9000`.
2. In that emulator only, set the rules for `demo-avora-journal-default-rtdb`
   to `{"rules":{".read":true,".write":true}}`.
3. Run `adb -s emulator-5554 reverse tcp:9000 tcp:9000`.
4. From `Android`, build:

   ```powershell
   .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest -PjournalLocalEmulator=true
   ```

5. Install both APKs, then run:

   ```powershell
   adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
   adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
   adb -s emulator-5554 shell am instrument -w -e class com.alidogukan.avora.JournalRecordPersistenceTest,com.alidogukan.avora.NewJournalRecordActivityTest,com.alidogukan.avora.QuickSettingsActionVisibilityTest com.alidogukan.avora.debug.test/com.alidogukan.avora.JournalTestRunner
   ```

The persistence tests save observations without photos and milestones with a
camera-result fixture, recreate the form, verify selected date/time and persisted
metadata, and open the record from the timeline to check its attached photo.
The camera fixture writes a real JPEG to the app's capture URI; camera hardware
and gallery-provider UI are outside this test's scope.

Afterwards, force-stop the debug app, remove the ADB reverse mapping, stop the
database emulator, and restore the Android emulator's previous network settings.
Without `-PjournalLocalEmulator=true`, the normal instrumentation runner is used;
the persistence tests are skipped to prevent writes to a real Firebase project.
