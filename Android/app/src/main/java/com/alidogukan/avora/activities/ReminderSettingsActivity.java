package com.alidogukan.avora.activities;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.ui.PrimaryBottomNavigation;
import com.alidogukan.avora.viewmodels.GardenSettingsViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.Locale;

/** Controls scheduled watering, fertilization and plant follow-up reminders. */
public class ReminderSettingsActivity extends AppCompatActivity {
    private GardenSettingsViewModel viewModel;
    private MaterialSwitch irrigationReminder;
    private MaterialSwitch fertilizationReminder;
    private MaterialSwitch plantReminder;
    private MaterialSwitch seedlingReminder;
    private MaterialSwitch quietHoursSwitch;
    private MaterialButton quietStart;
    private MaterialButton quietEnd;
    private View quietHoursLayout;
    private boolean applyingCloudBackup;

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_reminder_settings);
        applyWindowInsets();
        viewModel = new ViewModelProvider(this).get(GardenSettingsViewModel.class);
        configureToolbar();
        bindViews();
        configureActions();
        renderLocalValues();
        restoreCloudBackup();
        PrimaryBottomNavigation.bind(this, PrimaryBottomNavigation.SETTINGS);
    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.reminderSettingsRoot),
                (view, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });
    }

    private void configureToolbar() {
        ((TextView) findViewById(R.id.txtSettingsToolbarTitle))
                .setText(R.string.reminder_settings_title);
        findViewById(R.id.btnSettingsToolbarBack).setOnClickListener(view -> finish());
        findViewById(R.id.btnSettingsToolbarAction).setVisibility(View.GONE);
    }

    private void bindViews() {
        irrigationReminder = findViewById(R.id.switchReminderIrrigation);
        fertilizationReminder = findViewById(R.id.switchReminderFertilization);
        plantReminder = findViewById(R.id.switchReminderPlant);
        seedlingReminder = findViewById(R.id.switchReminderSeedling);
        quietHoursSwitch = findViewById(R.id.switchReminderQuietHours);
        quietStart = findViewById(R.id.btnReminderQuietStart);
        quietEnd = findViewById(R.id.btnReminderQuietEnd);
        quietHoursLayout = findViewById(R.id.layoutReminderQuietHours);
    }

    private void configureActions() {
        irrigationReminder.setOnCheckedChangeListener((button, checked) ->
                saveReminder("irrigation", checked));
        fertilizationReminder.setOnCheckedChangeListener((button, checked) ->
                saveReminder("fertilization", checked));
        plantReminder.setOnCheckedChangeListener((button, checked) ->
                saveReminder("plant", checked));
        seedlingReminder.setOnCheckedChangeListener((button, checked) ->
                saveReminder("seedling", checked));
        quietHoursSwitch.setOnCheckedChangeListener((button, checked) -> {
            quietHoursLayout.setVisibility(checked ? View.VISIBLE : View.GONE);
            if (applyingCloudBackup) return;
            viewModel.setQuietHoursEnabled(checked);
            syncAndSchedule();
        });
        quietStart.setOnClickListener(view -> pickTime(true));
        quietEnd.setOnClickListener(view -> pickTime(false));
    }

    private void renderLocalValues() {
        applyingCloudBackup = true;
        irrigationReminder.setChecked(viewModel.isReminderEnabled("irrigation"));
        fertilizationReminder.setChecked(viewModel.isReminderEnabled("fertilization"));
        plantReminder.setChecked(viewModel.isReminderEnabled("plant"));
        seedlingReminder.setChecked(viewModel.isReminderEnabled("seedling"));
        quietHoursSwitch.setChecked(viewModel.isQuietHoursEnabled());
        quietHoursLayout.setVisibility(viewModel.isQuietHoursEnabled() ? View.VISIBLE : View.GONE);
        renderTimes();
        applyingCloudBackup = false;
    }

    private void restoreCloudBackup() {
        viewModel.loadNotificationSettings(values -> runOnUiThread(() -> {
            if (!viewModel.applyNotificationBackup(values)) return;
            renderLocalValues();
        }));
    }

    private void saveReminder(String key, boolean checked) {
        if (applyingCloudBackup) return;
        viewModel.setReminderEnabled(key, checked);
        syncAndSchedule();
    }

    private void pickTime(boolean start) {
        int current = start ? viewModel.quietStartHour() : viewModel.quietEndHour();
        new TimePickerDialog(this, (dialog, hour, minute) -> {
            viewModel.setQuietHours(start ? hour : viewModel.quietStartHour(),
                    start ? viewModel.quietEndHour() : hour);
            renderTimes();
            syncAndSchedule();
        }, current, 0, true).show();
    }

    private void renderTimes() {
        quietStart.setText(String.format(Locale.getDefault(), "%02d:00", viewModel.quietStartHour()));
        quietEnd.setText(String.format(Locale.getDefault(), "%02d:00", viewModel.quietEndHour()));
    }

    private void syncAndSchedule() {
        viewModel.saveReminderSettings()
                .addOnFailureListener(error -> Toast.makeText(this,
                        R.string.reminder_settings_save_failed, Toast.LENGTH_SHORT).show());
    }
}
