package com.alidogukan.avora.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.models.SeedlingBatch;
import com.alidogukan.avora.models.SeedlingDailyLog;
import com.alidogukan.avora.models.SeedlingNodeState;
import com.alidogukan.avora.models.SeedlingRecommendation;
import com.alidogukan.avora.models.SeedlingTelemetry;
import com.alidogukan.avora.viewmodels.SeedlingViewModel;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Records a daily manual observation alongside live, traceable sensor guidance. */
public final class SeedlingDailyLogActivity extends EdgeToEdgeActivity {
    public static final String EXTRA_LOG_ID = "seedling_log_id";

    private SeedlingViewModel viewModel;
    private SeedlingBatch batch;
    private String batchId;
    private String editingLogId = "";
    private SeedlingDailyLog editingLog;
    private String observedNodeId = "";
    private LiveData<SeedlingNodeState> nodeSource;
    private boolean watered;
    private boolean seededFromLatestLog;

    private EditText height;
    private EditText leaves;
    private EditText healthy;
    private EditText note;
    private TextView healthyTotal;
    private TextView wateredValue;
    private TextView warningTitle;
    private TextView warningMessage;
    private TextView adviceTitle;
    private TextView adviceMessage;
    private TextView seasonTransferTitle;
    private TextView targetSeason;
    private TextView toolbarTitle;
    private MaterialCardView warningCard;
    private MaterialCardView adviceCard;
    private MaterialCardView seasonTransferCard;
    private MaterialButton save;

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_seedling_daily_log);
        batchId = getIntent().getStringExtra(SeedlingBatchDetailActivity.EXTRA_BATCH_ID);
        String requestedLogId = getIntent().getStringExtra(EXTRA_LOG_ID);
        editingLogId = requestedLogId == null ? "" : requestedLogId.trim();
        if (batchId == null || batchId.isBlank()) {
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this).get(SeedlingViewModel.class);
        bindViews();
        if (!editingLogId.isBlank()) {
            toolbarTitle.setText(R.string.seedling_log_edit_title);
            save.setText(R.string.seedling_log_update);
            save.setEnabled(false);
        }
        renderWateringStatus();
        findViewById(R.id.btnBack).setOnClickListener(view -> finish());
        findViewById(R.id.btnSeedlingDailyMore)
                .setOnClickListener(view -> showBatchInformation());
        findViewById(R.id.rowSeedlingWateringStatus)
                .setOnClickListener(view -> showWateringPicker());
        seasonTransferCard.setOnClickListener(view ->
                startActivity(new Intent(this, SeasonManagementActivity.class)));
        save.setOnClickListener(view -> submit());

        viewModel.getBatch(batchId).observe(this, value -> {
            batch = value;
            renderBatch();
            observeBatchNode();
        });
        viewModel.getLogs(batchId).observe(this, this::seedLatestObservation);
    }

    private void bindViews() {
        height = findViewById(R.id.inputSeedlingHeight);
        leaves = findViewById(R.id.inputSeedlingLeaves);
        healthy = findViewById(R.id.inputSeedlingHealthy);
        note = findViewById(R.id.inputSeedlingNote);
        healthyTotal = findViewById(R.id.txtSeedlingHealthyTotal);
        wateredValue = findViewById(R.id.txtSeedlingWateredValue);
        warningCard = findViewById(R.id.cardSeedlingDailyWarning);
        adviceCard = findViewById(R.id.cardSeedlingDailyAdvice);
        warningTitle = findViewById(R.id.txtSeedlingDailyWarningTitle);
        warningMessage = findViewById(R.id.txtSeedlingDailyWarningMessage);
        adviceTitle = findViewById(R.id.txtSeedlingDailyAdviceTitle);
        adviceMessage = findViewById(R.id.txtSeedlingDailyAdviceMessage);
        seasonTransferCard = findViewById(R.id.cardSeedlingSeasonTransfer);
        seasonTransferTitle = findViewById(R.id.txtSeedlingSeasonTransferTitle);
        targetSeason = findViewById(R.id.txtSeedlingTargetSeason);
        toolbarTitle = findViewById(R.id.txtSeedlingDailyToolbarTitle);
        save = findViewById(R.id.btnSaveSeedlingLog);
    }

    private void renderBatch() {
        if (batch == null) return;
        if (healthy.getText().toString().isBlank()) {
            healthy.setText(String.valueOf(batch.getHealthy_count()));
        }
        healthyTotal.setText(getString(R.string.seedling_healthy_total, batch.getSeed_count()));
        boolean ready = viewModel.isReady(batch);
        seasonTransferTitle.setText(ready
                ? R.string.seedling_season_transfer_ready
                : R.string.seedling_season_transfer_preparing);
        seasonTransferCard.setAlpha(ready ? 1f : 0.86f);

        Calendar calendar = Calendar.getInstance();
        if (batch.getSowing_date_epoch() > 0L) {
            calendar.setTime(new Date(batch.getSowing_date_epoch() * 1000L));
        }
        String plant = batch.getPlant_type().isBlank()
                ? getString(R.string.seedling_plant_type) : batch.getPlant_type();
        targetSeason.setText(getString(R.string.seedling_target_season_format,
                calendar.get(Calendar.YEAR), plant));
    }

    private void seedLatestObservation(List<SeedlingDailyLog> values) {
        if (seededFromLatestLog || values == null) return;
        if (!editingLogId.isBlank()) {
            for (SeedlingDailyLog value : values) {
                if (editingLogId.equals(value.getLog_id())) {
                    editingLog = value;
                    break;
                }
            }
            if (editingLog == null) {
                Toast.makeText(this, R.string.seedling_log_not_found,
                        Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            seededFromLatestLog = true;
            height.setText(String.format(Locale.getDefault(), "%.1f",
                    editingLog.getHeight_cm()));
            leaves.setText(String.valueOf(editingLog.getLeaf_count()));
            healthy.setText(String.valueOf(editingLog.getHealthy_count()));
            note.setText(editingLog.getNote());
            watered = editingLog.isWatered();
            renderWateringStatus();
            save.setEnabled(true);
            return;
        }
        if (values.isEmpty()) return;
        seededFromLatestLog = true;
        SeedlingDailyLog latest = values.get(0);
        if (height.getText().toString().isBlank()) {
            height.setText(String.format(Locale.getDefault(), "%.1f", latest.getHeight_cm()));
        }
        if (leaves.getText().toString().isBlank()) {
            leaves.setText(String.valueOf(latest.getLeaf_count()));
        }
    }

    private void observeBatchNode() {
        if (batch == null) return;
        String nodeId = batch.getNode_id().isBlank() ? "seedling-001" : batch.getNode_id();
        if (nodeId.equals(observedNodeId)) return;
        if (nodeSource != null) nodeSource.removeObservers(this);
        observedNodeId = nodeId;
        nodeSource = viewModel.getNode(nodeId);
        nodeSource.observe(this, this::renderNode);
    }

    private void renderNode(SeedlingNodeState value) {
        SeedlingTelemetry telemetry = value == null ? null : value.getLatest();
        boolean fresh = telemetry != null && telemetry.isFresh(
                System.currentTimeMillis() / 1000L, 45L);
        renderWarning(fresh, telemetry);
        renderAdvice(fresh, value == null ? null : value.getRecommendation());
    }

    private void renderWarning(boolean fresh, @Nullable SeedlingTelemetry telemetry) {
        warningCard.setVisibility(View.VISIBLE);
        if (!fresh || telemetry == null) {
            warningTitle.setText(R.string.seedling_daily_warning_stale_title);
            warningMessage.setText(R.string.seedling_daily_warning_stale_message);
        } else if (!telemetry.isSoil_moisture_available()) {
            warningTitle.setText(R.string.seedling_daily_warning_soil_unavailable_title);
            warningMessage.setText(R.string.seedling_daily_warning_soil_unavailable_message);
        } else if (telemetry.getSoil_moisture_pct() < 35d) {
            warningTitle.setText(R.string.seedling_daily_warning_dry_title);
            warningMessage.setText(R.string.seedling_daily_warning_dry_message);
        } else if (telemetry.getSoil_moisture_pct() > 85d) {
            warningTitle.setText(R.string.seedling_daily_warning_wet_title);
            warningMessage.setText(R.string.seedling_daily_warning_wet_message);
        } else {
            warningCard.setVisibility(View.GONE);
        }
    }

    private void renderAdvice(boolean fresh, @Nullable SeedlingRecommendation advice) {
        adviceCard.setVisibility(View.VISIBLE);
        if (!fresh || advice == null) {
            adviceTitle.setText(R.string.seedling_daily_advice_waiting_title);
            adviceMessage.setText(R.string.seedling_daily_advice_waiting_message);
            return;
        }
        String action = viewModel.recommendationAction(advice);
        String message = viewModel.recommendationMessage(advice);
        if (action.isBlank() && message.isBlank()) {
            adviceCard.setVisibility(View.GONE);
            return;
        }
        if (action.isBlank()) action = advice.getTitle();
        adviceTitle.setText(getString(R.string.seedling_daily_recommended_action, action));
        adviceMessage.setText(message);
    }

    private void showWateringPicker() {
        String[] options = {
                getString(R.string.seedling_watering_done),
                getString(R.string.seedling_watering_not_done)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.seedling_watering_picker_title)
                .setSingleChoiceItems(options, watered ? 0 : 1, (dialog, which) -> {
                    watered = which == 0;
                    renderWateringStatus();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.settings_cancel, null)
                .show();
    }

    private void renderWateringStatus() {
        wateredValue.setText(watered
                ? R.string.seedling_watering_done : R.string.seedling_watering_not_done);
        wateredValue.setTextColor(ContextCompat.getColor(this,
                watered ? R.color.primary : R.color.textSecondary));
    }

    private void showBatchInformation() {
        if (batch == null) return;
        String area = batch.getArea().isBlank() ? "—" : batch.getArea();
        String node = batch.getNode_id().isBlank() ? "seedling-001" : batch.getNode_id();
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.seedling_batch_information_title)
                .setMessage(getString(R.string.seedling_batch_information, area,
                        batch.getSeed_count(), batch.getTray_cell_count(), node))
                .setPositiveButton(R.string.seedling_close, null)
                .show();
    }

    private void submit() {
        double heightValue = decimal(height);
        int leavesValue = integer(leaves);
        int healthyValue = integer(healthy);
        boolean exceedsBatch = batch != null && healthyValue > batch.getSeed_count();
        if (heightValue < 0d || leavesValue < 0 || healthyValue < 0 || exceedsBatch) {
            Toast.makeText(this, R.string.seedling_invalid_log, Toast.LENGTH_LONG).show();
            return;
        }
        save.setEnabled(false);
        Task<Void> operation = editingLog == null
                ? viewModel.saveDailyLog(batchId, heightValue, leavesValue, healthyValue,
                        watered, note.getText().toString().trim())
                : viewModel.updateDailyLog(editingLog, heightValue, leavesValue, healthyValue,
                        watered, note.getText().toString().trim());
        operation
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, editingLog == null
                                    ? R.string.seedling_log_saved : R.string.seedling_log_updated,
                            Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(error -> {
                    save.setEnabled(true);
                    Toast.makeText(this, editingLog == null
                                    ? R.string.seedling_log_failed
                                    : R.string.seedling_log_update_failed,
                            Toast.LENGTH_LONG).show();
                });
    }

    private static int integer(EditText input) {
        try {
            return Integer.parseInt(input.getText().toString().trim());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static double decimal(EditText input) {
        String value = input.getText().toString().trim().replace(',', '.');
        if (value.isBlank()) return 0d;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return -1d;
        }
    }
}
