package com.alidogukan.avora.activities;

import android.os.Build;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.config.AppInfo;
import com.alidogukan.avora.ui.PrimaryBottomNavigation;
import com.alidogukan.avora.viewmodels.FeedbackViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.HashMap;
import java.util.Map;

/** Sends structured user feedback without attaching private garden content. */
public class FeedbackActivity extends AppCompatActivity {
    private static final String[] AREA_CODES = {
            "general", "irrigation_hardware", "plant_assistant",
            "fertilization", "plant_journal", "notifications", "settings"
    };

    private ChipGroup typeGroup;
    private MaterialAutoCompleteTextView areaDropdown;
    private TextInputLayout subjectLayout;
    private TextInputLayout descriptionLayout;
    private TextInputLayout contactLayout;
    private TextInputEditText subjectInput;
    private TextInputEditText descriptionInput;
    private TextInputEditText contactInput;
    private MaterialSwitch diagnosticsSwitch;
    private MaterialButton submitButton;
    private TextView statusView;
    private String[] areaLabels;
    private FeedbackViewModel viewModel;

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_feedback);
        applyWindowInsets();
        bindViews();
        configureToolbar();
        configureAreaDropdown();
        viewModel = new ViewModelProvider(this).get(FeedbackViewModel.class);
        submitButton.setOnClickListener(view -> submitFeedback());
        PrimaryBottomNavigation.bind(this, PrimaryBottomNavigation.SETTINGS);
    }

    private void bindViews() {
        typeGroup = findViewById(R.id.groupFeedbackType);
        areaDropdown = findViewById(R.id.dropdownFeedbackArea);
        subjectLayout = findViewById(R.id.layoutFeedbackSubject);
        descriptionLayout = findViewById(R.id.layoutFeedbackDescription);
        contactLayout = findViewById(R.id.layoutFeedbackContact);
        subjectInput = findViewById(R.id.inputFeedbackSubject);
        descriptionInput = findViewById(R.id.inputFeedbackDescription);
        contactInput = findViewById(R.id.inputFeedbackContact);
        diagnosticsSwitch = findViewById(R.id.switchFeedbackDiagnostics);
        submitButton = findViewById(R.id.btnSubmitFeedback);
        statusView = findViewById(R.id.txtFeedbackStatus);
    }

    private void configureToolbar() {
        ((TextView) findViewById(R.id.txtSettingsToolbarTitle))
                .setText(R.string.settings_feedback_title);
        findViewById(R.id.btnSettingsToolbarBack).setOnClickListener(view -> finish());
        findViewById(R.id.btnSettingsToolbarAction).setVisibility(View.GONE);
    }

    private void configureAreaDropdown() {
        areaLabels = getResources().getStringArray(R.array.feedback_areas);
        areaDropdown.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, areaLabels));
        if (areaLabels.length > 0) areaDropdown.setText(areaLabels[0], false);
    }

    private void submitFeedback() {
        clearErrors();
        String subject = valueOf(subjectInput);
        String description = valueOf(descriptionInput);
        String contact = valueOf(contactInput);
        if (subject.length() < 4) {
            subjectLayout.setError(getString(R.string.feedback_subject_required));
            subjectInput.requestFocus();
            return;
        }
        if (description.length() < 10) {
            descriptionLayout.setError(getString(R.string.feedback_description_required));
            descriptionInput.requestFocus();
            return;
        }
        if (!contact.isEmpty() && !Patterns.EMAIL_ADDRESS.matcher(contact).matches()) {
            contactLayout.setError(getString(R.string.feedback_email_invalid));
            contactInput.requestFocus();
            return;
        }

        String type = selectedType();
        if (type == null) {
            showStatus(R.string.feedback_type_required, false);
            return;
        }

        String areaLabel = areaDropdown.getText() == null
                ? areaLabels[0] : areaDropdown.getText().toString().trim();
        int areaIndex = areaIndex(areaLabel);
        setSending(true);
        viewModel.submit(type, AREA_CODES[areaIndex], areaLabels[areaIndex],
                subject, description, contact,
                diagnosticsSwitch.isChecked() ? diagnostics() : null)
                .addOnCompleteListener(task -> {
            setSending(false);
            if (task.isSuccessful()) {
                clearForm();
                showStatus(R.string.feedback_success, true);
            } else {
                showStatus(R.string.feedback_error, false);
            }
        });
    }

    private Map<String, Object> diagnostics() {
        Map<String, Object> values = new HashMap<>();
        values.put("app_version", AppInfo.APP_VERSION);
        values.put("android_version", Build.VERSION.RELEASE);
        values.put("android_sdk", Build.VERSION.SDK_INT);
        values.put("manufacturer", Build.MANUFACTURER);
        values.put("model", Build.MODEL);
        return values;
    }

    private String selectedType() {
        int selectedId = typeGroup.getCheckedChipId();
        if (selectedId == R.id.chipFeedbackProblem) return "problem";
        if (selectedId == R.id.chipFeedbackSuggestion) return "suggestion";
        if (selectedId == R.id.chipFeedbackQuestion) return "question";
        return null;
    }

    private int areaIndex(String selected) {
        for (int i = 0; i < areaLabels.length; i++) {
            if (areaLabels[i].equals(selected)) return i;
        }
        return 0;
    }

    private void clearErrors() {
        subjectLayout.setError(null);
        descriptionLayout.setError(null);
        contactLayout.setError(null);
        statusView.setVisibility(View.GONE);
    }

    private void clearForm() {
        typeGroup.check(R.id.chipFeedbackProblem);
        areaDropdown.setText(areaLabels[0], false);
        subjectInput.setText("");
        descriptionInput.setText("");
        contactInput.setText("");
    }

    private void setSending(boolean sending) {
        submitButton.setEnabled(!sending);
        submitButton.setText(sending
                ? R.string.feedback_sending : R.string.feedback_submit);
    }

    private void showStatus(int message, boolean success) {
        statusView.setText(message);
        statusView.setTextColor(ContextCompat.getColor(this,
                success ? R.color.primary : R.color.offline));
        statusView.setVisibility(View.VISIBLE);
    }

    private String valueOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.feedbackRoot),
                (view, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });
    }
}
