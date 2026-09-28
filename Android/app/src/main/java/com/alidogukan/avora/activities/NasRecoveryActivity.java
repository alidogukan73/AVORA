package com.alidogukan.avora.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.util.Patterns;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import com.alidogukan.avora.R;
import com.alidogukan.avora.viewmodels.NasRecoveryViewModel;
import com.google.android.material.textfield.TextInputEditText;

public class NasRecoveryActivity extends AppCompatActivity {
    private TextInputEditText email, code, password, repeat;
    private TextView status;
    private NasRecoveryViewModel model;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_nas_recovery);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.nasRecoveryRoot), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ((TextView) findViewById(R.id.txtSettingsToolbarTitle)).setText(R.string.nas_recovery_title);
        findViewById(R.id.btnSettingsToolbarBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSettingsToolbarAction).setVisibility(View.GONE);
        email = findViewById(R.id.recoveryEmail);
        code = findViewById(R.id.recoveryCode);
        password = findViewById(R.id.recoveryPassword);
        repeat = findViewById(R.id.recoveryRepeat);
        status = findViewById(R.id.recoveryStatus);
        if (savedInstanceState == null) email.setText(getIntent().getStringExtra("email"));
        model = new ViewModelProvider(this).get(NasRecoveryViewModel.class);
        findViewById(R.id.recoverySend).setOnClickListener(v -> {
            if (validEmail()) model.request(value(email).trim());
        });
        findViewById(R.id.recoverySubmit).setOnClickListener(v -> submit());
        findViewById(R.id.recoveryDone).setOnClickListener(v -> finish());
        model.state().observe(this, state -> {
            email.setEnabled(!state.busy && !state.complete);
            code.setEnabled(!state.busy);
            password.setEnabled(!state.busy);
            repeat.setEnabled(!state.busy);
            findViewById(R.id.recoverySend).setEnabled(!state.busy && !state.complete);
            findViewById(R.id.recoverySubmit).setEnabled(!state.busy && !state.complete);
            findViewById(R.id.recoveryFields).setVisibility(state.complete ? View.GONE : View.VISIBLE);
            findViewById(R.id.recoveryDone).setVisibility(state.complete ? View.VISIBLE : View.GONE);
            if (state.complete) {
                setResult(RESULT_OK);
                code.setText(null); password.setText(null); repeat.setText(null);
            }
            status.setText(state.busy ? R.string.nas_recovery_busy : state.complete ? R.string.nas_recovery_complete
                    : !state.error.isEmpty() ? errorMessage(state.error)
                    : state.sent ? R.string.nas_recovery_sent : R.string.nas_recovery_message);
        });
    }

    private boolean validEmail() {
        if (Patterns.EMAIL_ADDRESS.matcher(value(email).trim()).matches()) return true;
        email.setError(getString(R.string.data_sync_nas_error_email));
        return false;
    }
    private void submit() {
        if (!validEmail()) return;
        if (value(code).trim().isEmpty()) {
            code.setError(getString(R.string.data_sync_nas_error_required)); return;
        }
        String secret = value(password);
        if (secret.codePointCount(0, secret.length()) < 12
                || secret.codePointCount(0, secret.length()) > 128
                || secret.chars().anyMatch(c -> c < 32)) {
            password.setError(getString(R.string.nas_recovery_password_policy)); return;
        }
        if (!secret.equals(value(repeat))) {
            repeat.setError(getString(R.string.data_sync_nas_password_mismatch)); return;
        }
        password.setText(null); repeat.setText(null);
        model.confirm(value(email).trim(), value(code).trim(), secret);
    }
    private int errorMessage(String error) {
        switch (error) {
            case "NAS_INVALID_RESET_TOKEN": return R.string.nas_recovery_invalid_code;
            case "NAS_RECOVERY_UNAVAILABLE":
            case "NAS_HTTP_404": return R.string.nas_recovery_unavailable;
            case "NAS_RATE_LIMITED": return R.string.nas_recovery_rate_limited;
            case "NAS_WEAK_PASSWORD": return R.string.nas_recovery_password_policy;
            default: return R.string.nas_recovery_network_error;
        }
    }
    private static String value(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }
}
