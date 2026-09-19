package com.alidogukan.avora.activities;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.alidogukan.avora.R;
import com.alidogukan.avora.ui.PrimaryBottomNavigation;
import com.google.android.material.textfield.TextInputEditText;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Searchable, offline AVORA guide with direct access to the relevant modules. */
public class HelpCenterActivity extends AppCompatActivity {
    private static final int CATEGORY_ALL = 0;
    private static final int CATEGORY_START = 1;
    private static final int CATEGORY_GARDEN = 2;
    private static final int CATEGORY_IRRIGATION = 3;
    private static final int CATEGORY_AI = 4;
    private static final int CATEGORY_RECORDS = 5;
    private static final int CATEGORY_DEVICE = 6;
    private static final int CATEGORY_ACCOUNT_DATA = 7;

    private final List<HelpEntry> entries = Arrays.asList(
            entry(CATEGORY_START, R.string.help_q_first_setup, R.string.help_a_first_setup),
            entry(CATEGORY_START, R.string.help_q_navigation, R.string.help_a_navigation),
            entry(CATEGORY_START, R.string.help_q_status_meaning, R.string.help_a_status_meaning),
            entry(CATEGORY_START, R.string.help_q_units, R.string.help_a_units),
            entry(CATEGORY_START, R.string.help_q_quick_settings, R.string.help_a_quick_settings),

            entry(CATEGORY_GARDEN, R.string.help_q_zone_management, R.string.help_a_zone_management),
            entry(CATEGORY_GARDEN, R.string.help_q_sensor_mapping, R.string.help_a_sensor_mapping),
            entry(CATEGORY_GARDEN, R.string.help_q_sensor_calibration, R.string.help_a_sensor_calibration),
            entry(CATEGORY_GARDEN, R.string.help_q_season_start_close, R.string.help_a_season_start_close),
            entry(CATEGORY_GARDEN, R.string.help_q_season_history, R.string.help_a_season_history),
            entry(CATEGORY_GARDEN, R.string.help_q_journal_edit, R.string.help_a_journal_edit),

            entry(CATEGORY_IRRIGATION, R.string.help_q_auto_irrigation, R.string.help_a_auto_irrigation),
            entry(CATEGORY_IRRIGATION, R.string.help_q_low_moisture_wait, R.string.help_a_low_moisture_wait),
            entry(CATEGORY_IRRIGATION, R.string.help_q_manual_valve, R.string.help_a_manual_valve),
            entry(CATEGORY_IRRIGATION, R.string.help_q_manual_duration, R.string.help_a_manual_duration),
            entry(CATEGORY_IRRIGATION, R.string.help_q_valve_setup, R.string.help_a_valve_setup),
            entry(CATEGORY_IRRIGATION, R.string.help_q_restart_process, R.string.help_a_restart_process),
            entry(CATEGORY_IRRIGATION, R.string.help_q_weather_irrigation, R.string.help_a_weather_irrigation),
            entry(CATEGORY_IRRIGATION, R.string.help_q_watering_history, R.string.help_a_watering_history),

            entry(CATEGORY_AI, R.string.help_q_ai_tools, R.string.help_a_ai_tools),
            entry(CATEGORY_AI, R.string.help_q_plant_analysis_record, R.string.help_a_plant_analysis_record),
            entry(CATEGORY_AI, R.string.help_q_ai_remote, R.string.help_a_ai_remote),
            entry(CATEGORY_AI, R.string.help_q_ai_diagnosis, R.string.help_a_ai_diagnosis),
            entry(CATEGORY_AI, R.string.help_q_seedling_batches, R.string.help_a_seedling_batches),
            entry(CATEGORY_AI, R.string.help_q_seedling_archive, R.string.help_a_seedling_archive),
            entry(CATEGORY_AI, R.string.help_q_irrigation_learning, R.string.help_a_irrigation_learning),

            entry(CATEGORY_RECORDS, R.string.help_q_journal_auto, R.string.help_a_journal_auto),
            entry(CATEGORY_RECORDS, R.string.help_q_fertilizer_record, R.string.help_a_fertilizer_record),
            entry(CATEGORY_RECORDS, R.string.help_q_fertilizer_stock, R.string.help_a_fertilizer_stock),
            entry(CATEGORY_RECORDS, R.string.help_q_fertilizer_learning, R.string.help_a_fertilizer_learning),
            entry(CATEGORY_RECORDS, R.string.help_q_notifications, R.string.help_a_notifications),
            entry(CATEGORY_RECORDS, R.string.help_q_statistics, R.string.help_a_statistics),

            entry(CATEGORY_DEVICE, R.string.help_q_device_offline, R.string.help_a_device_offline),
            entry(CATEGORY_DEVICE, R.string.help_q_device_health, R.string.help_a_device_health),
            entry(CATEGORY_DEVICE, R.string.help_q_remote_connection, R.string.help_a_remote_connection),
            entry(CATEGORY_DEVICE, R.string.help_q_hardware_safety, R.string.help_a_hardware_safety),

            entry(CATEGORY_ACCOUNT_DATA, R.string.help_q_device_authorization, R.string.help_a_device_authorization),
            entry(CATEGORY_ACCOUNT_DATA, R.string.help_q_family_member, R.string.help_a_family_member),
            entry(CATEGORY_ACCOUNT_DATA, R.string.help_q_sessions, R.string.help_a_sessions),
            entry(CATEGORY_ACCOUNT_DATA, R.string.help_q_sync_backup_difference, R.string.help_a_sync_backup_difference),
            entry(CATEGORY_ACCOUNT_DATA, R.string.help_q_backup, R.string.help_a_backup),
            entry(CATEGORY_ACCOUNT_DATA, R.string.help_q_restore_safety, R.string.help_a_restore_safety),
            entry(CATEGORY_ACCOUNT_DATA, R.string.help_q_feedback, R.string.help_a_feedback));

    private LinearLayout faqContainer;
    private TextView emptyView;
    private TextInputEditText searchInput;
    private int selectedCategory = CATEGORY_ALL;
    private int expandedQuestion;

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_help_center);
        applyWindowInsets();

        faqContainer = findViewById(R.id.layoutHelpFaqs);
        emptyView = findViewById(R.id.txtHelpEmpty);
        searchInput = findViewById(R.id.inputHelpSearch);

        ((TextView) findViewById(R.id.txtSettingsToolbarTitle))
                .setText(R.string.settings_help_title);
        findViewById(R.id.btnSettingsToolbarBack).setOnClickListener(view -> finish());
        findViewById(R.id.btnSettingsToolbarAction).setVisibility(View.GONE);
        PrimaryBottomNavigation.bind(this, PrimaryBottomNavigation.SETTINGS);

        bindCategories();
        bindSearch();
        renderFaqs();
    }

    private void bindCategories() {
        findViewById(R.id.chipHelpAll).setOnClickListener(view -> selectCategory(CATEGORY_ALL));
        findViewById(R.id.chipHelpStart).setOnClickListener(view -> selectCategory(CATEGORY_START));
        findViewById(R.id.chipHelpGarden).setOnClickListener(view -> selectCategory(CATEGORY_GARDEN));
        findViewById(R.id.chipHelpIrrigation).setOnClickListener(view -> selectCategory(CATEGORY_IRRIGATION));
        findViewById(R.id.chipHelpAi).setOnClickListener(view -> selectCategory(CATEGORY_AI));
        findViewById(R.id.chipHelpRecords).setOnClickListener(view -> selectCategory(CATEGORY_RECORDS));
        findViewById(R.id.chipHelpDevice).setOnClickListener(view -> selectCategory(CATEGORY_DEVICE));
        findViewById(R.id.chipHelpAccountData).setOnClickListener(view -> selectCategory(CATEGORY_ACCOUNT_DATA));
    }

    private void selectCategory(int category) {
        selectedCategory = category;
        expandedQuestion = 0;
        renderFaqs();
    }

    private void bindSearch() {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                expandedQuestion = 0;
                renderFaqs();
            }
            @Override public void afterTextChanged(Editable value) { }
        });
    }


    private void renderFaqs() {
        faqContainer.removeAllViews();
        String query = normalized(searchInput.getText() == null
                ? "" : searchInput.getText().toString());
        List<HelpEntry> visible = new ArrayList<>();
        for (HelpEntry value : entries) {
            if (selectedCategory != CATEGORY_ALL && value.category != selectedCategory) continue;
            String searchable = normalized(getString(value.question) + " " + getString(value.answer));
            if (!query.isEmpty() && !searchable.contains(query)) continue;
            visible.add(value);
        }

        for (HelpEntry value : visible) {
            View row = LayoutInflater.from(this)
                    .inflate(R.layout.item_help_faq, faqContainer, false);
            TextView question = row.findViewById(R.id.txtHelpFaqQuestion);
            TextView answer = row.findViewById(R.id.txtHelpFaqAnswer);
            ImageView arrow = row.findViewById(R.id.imgHelpFaqArrow);
            boolean expanded = expandedQuestion == value.question;
            question.setText(value.question);
            answer.setText(value.answer);
            answer.setVisibility(expanded ? View.VISIBLE : View.GONE);
            arrow.setRotation(expanded ? 180f : 0f);
            row.setOnClickListener(view -> {
                expandedQuestion = expandedQuestion == value.question ? 0 : value.question;
                renderFaqs();
            });
            faqContainer.addView(row);
        }
        emptyView.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private String normalized(String value) {
        String plain = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('ı', 'i');
        return plain.toLowerCase(Locale.forLanguageTag("tr-TR")).trim();
    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.helpCenterRoot),
                (view, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });
    }

    private static HelpEntry entry(int category, @StringRes int question, @StringRes int answer) {
        return new HelpEntry(category, question, answer);
    }

    private static final class HelpEntry {
        final int category;
        @StringRes final int question;
        @StringRes final int answer;

        HelpEntry(int category, int question, int answer) {
            this.category = category;
            this.question = question;
            this.answer = answer;
        }
    }
}
