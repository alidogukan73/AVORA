package com.alidogukan.avora.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.ui.PrimaryBottomNavigation;
import com.alidogukan.avora.viewmodels.AppearanceSettingsViewModel;
import com.google.android.material.radiobutton.MaterialRadioButton;

/** Controls AVORA's app language without changing stored garden data. */
public class LanguageSettingsActivity extends AppCompatActivity {
    private MaterialRadioButton systemLanguage;
    private MaterialRadioButton turkishLanguage;
    private MaterialRadioButton englishLanguage;
    private TextView activeLanguage;
    private AppearanceSettingsViewModel viewModel;
    private boolean binding;

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_language_settings);
        viewModel = new ViewModelProvider(this).get(AppearanceSettingsViewModel.class);
        applyWindowInsets();

        systemLanguage = findViewById(R.id.radioLanguageSystem);
        turkishLanguage = findViewById(R.id.radioLanguageTurkish);
        englishLanguage = findViewById(R.id.radioLanguageEnglish);
        activeLanguage = findViewById(R.id.txtActiveLanguage);

        ((TextView) findViewById(R.id.txtSettingsToolbarTitle))
                .setText(R.string.settings_language_title);
        findViewById(R.id.btnSettingsToolbarBack).setOnClickListener(view -> finish());
        findViewById(R.id.btnSettingsToolbarAction).setVisibility(View.GONE);
        PrimaryBottomNavigation.bind(this, PrimaryBottomNavigation.SETTINGS);

        configureOptions();
        bindSelection();
    }

    private void configureOptions() {
        systemLanguage.setOnClickListener(
                view -> select(AppearanceSettingsViewModel.LANGUAGE_SYSTEM));
        turkishLanguage.setOnClickListener(
                view -> select(AppearanceSettingsViewModel.LANGUAGE_TURKISH));
        ((View) systemLanguage.getParent()).setOnClickListener(
                view -> select(AppearanceSettingsViewModel.LANGUAGE_SYSTEM));
        ((View) turkishLanguage.getParent()).setOnClickListener(
                view -> select(AppearanceSettingsViewModel.LANGUAGE_TURKISH));

        boolean englishAvailable = viewModel.englishAvailable();
        englishLanguage.setEnabled(englishAvailable);
        ((View) englishLanguage.getParent()).setEnabled(englishAvailable);
        if (englishAvailable) {
            englishLanguage.setOnClickListener(
                    view -> select(AppearanceSettingsViewModel.LANGUAGE_ENGLISH));
            ((View) englishLanguage.getParent()).setOnClickListener(
                    view -> select(AppearanceSettingsViewModel.LANGUAGE_ENGLISH));
        }
    }

    private void bindSelection() {
        binding = true;
        String mode = viewModel.languageMode();
        systemLanguage.setChecked(AppearanceSettingsViewModel.LANGUAGE_SYSTEM.equals(mode));
        turkishLanguage.setChecked(AppearanceSettingsViewModel.LANGUAGE_TURKISH.equals(mode));
        englishLanguage.setChecked(AppearanceSettingsViewModel.LANGUAGE_ENGLISH.equals(mode));
        activeLanguage.setText(getString(R.string.language_active_format,
                getString(labelFor(mode))));
        binding = false;
    }

    private void select(String mode) {
        if (binding) return;
        viewModel.setLanguageMode(mode);
        bindSelection();
    }

    private int labelFor(String mode) {
        if (AppearanceSettingsViewModel.LANGUAGE_TURKISH.equals(mode)) {
            return R.string.language_option_turkish;
        }
        if (AppearanceSettingsViewModel.LANGUAGE_ENGLISH.equals(mode)) {
            return R.string.language_option_english;
        }
        return R.string.language_option_system;
    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.languageSettingsRoot),
                (view, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });
    }
}
