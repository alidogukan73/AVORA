package com.alidogukan.avora.activities;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.models.SeedlingBatch;
import com.alidogukan.avora.models.SeedlingDailyLog;
import com.alidogukan.avora.models.SeedlingNodeState;
import com.alidogukan.avora.models.SeedlingRecommendation;
import com.alidogukan.avora.models.SeedlingTelemetry;
import com.alidogukan.avora.seedling.SeedlingConditionSummary;
import com.alidogukan.avora.seedling.SeedlingEnvironmentGuide;
import com.alidogukan.avora.seedling.SeedlingFertilizerGuide;
import com.alidogukan.avora.seedling.SeedlingTimeline;
import com.alidogukan.avora.ui.GardenPhotoViewerDialog;
import com.alidogukan.avora.viewmodels.SeedlingViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Live seedling conditions, explainable advice and growth history. */
public final class SeedlingBatchDetailActivity extends EdgeToEdgeActivity {
    public static final String EXTRA_BATCH_ID = "seedling_batch_id";

    private SeedlingViewModel viewModel;
    private SeedlingBatch batch;
    private String batchId;
    private String observedNodeId = "";
    private LiveData<SeedlingNodeState> nodeSource;
    private SeedlingNodeState latestNodeState;

    private TextView emoji;
    private TextView name;
    private TextView meta;
    private TextView stage;
    private TextView noTelemetry;
    private TextView airTemp;
    private TextView airHumidity;
    private TextView soilMoisture;
    private TextView rootTemp;
    private TextView light;
    private TextView targetContext;
    private TextView airTempTarget;
    private TextView airHumidityTarget;
    private TextView soilMoistureTarget;
    private TextView rootTempTarget;
    private TextView lightTarget;
    private TextView airTempState;
    private TextView airHumidityState;
    private TextView soilMoistureState;
    private TextView rootTempState;
    private TextView lightState;
    private MaterialCardView airTempCard;
    private MaterialCardView airHumidityCard;
    private MaterialCardView soilMoistureCard;
    private MaterialCardView rootTempCard;
    private MaterialCardView lightCard;
    private ImageView airTempIcon;
    private ImageView airHumidityIcon;
    private ImageView soilMoistureIcon;
    private ImageView rootTempIcon;
    private ImageView lightIcon;
    private TextView overallIcon;
    private TextView overallTitle;
    private TextView overallMessage;
    private TextView adviceTitle;
    private TextView adviceMessage;
    private TextView fertilizerContext;
    private TextView fertilizerType;
    private TextView fertilizerApplication;
    private TextView fertilizerCropNote;
    private TextView logsEmpty;
    private TextView[] stageNodes;
    private TextView[] stageDates;
    private View[] stageLines;
    private LinearLayout logs;
    private View advance;
    private MaterialCardView overallCard;
    private MaterialCardView adviceCard;
    private boolean hasDailyLogs;
    private boolean stageUpdateInProgress;

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_seedling_batch_detail);
        batchId = getIntent().getStringExtra(EXTRA_BATCH_ID);
        if (batchId == null || batchId.isBlank()) {
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this).get(SeedlingViewModel.class);
        bindViews();
        findViewById(R.id.btnBack).setOnClickListener(view -> finish());
        findViewById(R.id.btnSeedlingMore).setOnClickListener(view -> showBatchActions());
        findViewById(R.id.btnSeedlingDailyLog).setOnClickListener(view -> {
            Intent intent = new Intent(this, SeedlingDailyLogActivity.class);
            intent.putExtra(EXTRA_BATCH_ID, batchId);
            startActivity(intent);
        });
        advance.setOnClickListener(view -> confirmAdvanceStage());

        viewModel.getBatch(batchId).observe(this, value -> {
            if (value == null) {
                finish();
                return;
            }
            batch = value;
            renderBatch();
            observeBatchNode();
            if (latestNodeState != null) renderNode(latestNodeState);
        });
        viewModel.getLogs(batchId).observe(this, this::renderLogs);
    }

    private void bindViews() {
        emoji = findViewById(R.id.txtSeedlingBatchEmoji);
        name = findViewById(R.id.txtSeedlingBatchName);
        meta = findViewById(R.id.txtSeedlingBatchMeta);
        stage = findViewById(R.id.txtSeedlingStage);
        airTemp = findViewById(R.id.txtSeedlingAirTemp);
        airHumidity = findViewById(R.id.txtSeedlingAirHumidity);
        soilMoisture = findViewById(R.id.txtSeedlingSoilMoisture);
        rootTemp = findViewById(R.id.txtSeedlingRootTemp);
        light = findViewById(R.id.txtSeedlingLight);
        targetContext = findViewById(R.id.txtSeedlingTargetContext);
        airTempTarget = findViewById(R.id.txtSeedlingAirTempTarget);
        airHumidityTarget = findViewById(R.id.txtSeedlingAirHumidityTarget);
        soilMoistureTarget = findViewById(R.id.txtSeedlingSoilMoistureTarget);
        rootTempTarget = findViewById(R.id.txtSeedlingRootTempTarget);
        lightTarget = findViewById(R.id.txtSeedlingLightTarget);
        airTempState = findViewById(R.id.txtSeedlingAirTempState);
        airHumidityState = findViewById(R.id.txtSeedlingAirHumidityState);
        soilMoistureState = findViewById(R.id.txtSeedlingSoilMoistureState);
        rootTempState = findViewById(R.id.txtSeedlingRootTempState);
        lightState = findViewById(R.id.txtSeedlingLightState);
        airTempCard = findViewById(R.id.cardSeedlingAirTemp);
        airHumidityCard = findViewById(R.id.cardSeedlingAirHumidity);
        soilMoistureCard = findViewById(R.id.cardSeedlingSoilMoisture);
        rootTempCard = findViewById(R.id.cardSeedlingRootTemp);
        lightCard = findViewById(R.id.cardSeedlingLight);
        airTempIcon = findViewById(R.id.imgSeedlingAirTemp);
        airHumidityIcon = findViewById(R.id.imgSeedlingAirHumidity);
        soilMoistureIcon = findViewById(R.id.imgSeedlingSoilMoisture);
        rootTempIcon = findViewById(R.id.imgSeedlingRootTemp);
        lightIcon = findViewById(R.id.imgSeedlingLight);
        noTelemetry = findViewById(R.id.txtSeedlingNoTelemetry);
        overallCard = findViewById(R.id.cardSeedlingOverallStatus);
        adviceCard = findViewById(R.id.cardSeedlingAdvice);
        overallIcon = findViewById(R.id.txtSeedlingOverallIcon);
        overallTitle = findViewById(R.id.txtSeedlingOverallTitle);
        overallMessage = findViewById(R.id.txtSeedlingOverallMessage);
        adviceTitle = findViewById(R.id.txtSeedlingAdviceTitle);
        adviceMessage = findViewById(R.id.txtSeedlingAdviceMessage);
        fertilizerContext = findViewById(R.id.txtSeedlingFertilizerContext);
        fertilizerType = findViewById(R.id.txtSeedlingFertilizerType);
        fertilizerApplication = findViewById(R.id.txtSeedlingFertilizerApplication);
        fertilizerCropNote = findViewById(R.id.txtSeedlingFertilizerCropNote);
        logsEmpty = findViewById(R.id.txtSeedlingLogsEmpty);
        logs = findViewById(R.id.layoutSeedlingLogs);
        advance = findViewById(R.id.btnSeedlingAdvance);
        stageNodes = new TextView[] {
                findViewById(R.id.txtSeedlingStageNode0),
                findViewById(R.id.txtSeedlingStageNode1),
                findViewById(R.id.txtSeedlingStageNode2),
                findViewById(R.id.txtSeedlingStageNode3),
                findViewById(R.id.txtSeedlingStageNode4)
        };
        stageDates = new TextView[] {
                findViewById(R.id.txtSeedlingStageDate0),
                findViewById(R.id.txtSeedlingStageDate1),
                findViewById(R.id.txtSeedlingStageDate2),
                findViewById(R.id.txtSeedlingStageDate3),
                findViewById(R.id.txtSeedlingStageDate4)
        };
        stageLines = new View[] {
                findViewById(R.id.viewSeedlingStageLine0),
                findViewById(R.id.viewSeedlingStageLine1),
                findViewById(R.id.viewSeedlingStageLine2),
                findViewById(R.id.viewSeedlingStageLine3)
        };
    }

    private void observeBatchNode() {
        if (batch == null) return;
        String nodeId = batch.getNode_id().isBlank() ? "seedling-001" : batch.getNode_id();
        if (nodeId.equals(observedNodeId)) return;
        if (nodeSource != null) nodeSource.removeObservers(this);
        observedNodeId = nodeId;
        latestNodeState = null;
        renderNode(null);
        nodeSource = viewModel.getNode(nodeId);
        nodeSource.observe(this, this::renderNode);
    }

    private void renderBatch() {
        if (batch == null) return;
        emoji.setText(batch.getEmoji().isBlank() ? "🌱" : batch.getEmoji());
        name.setText(batch.displayName());
        int day = (int) Math.max(1L, (System.currentTimeMillis() / 1000L
                - batch.getSowing_date_epoch()) / 86_400L + 1L);
        meta.setText(getString(R.string.seedling_batch_detail_meta,
                day, batch.getHealthy_count()));
        stage.setText(viewModel.stageLabel(batch.getStage()));
        targetContext.setText(getString(R.string.seedling_target_context,
                batch.getPlant_type(), viewModel.stageLabel(batch.getStage())));
        renderTimeline();
        renderFertilizerGuide();

        boolean enabled = !stageUpdateInProgress && viewModel.canAdvance(batch);
        advance.setEnabled(enabled);
        if (advance instanceof TextView) {
            ((TextView) advance).setText(enabled
                    ? R.string.seedling_advance_stage : R.string.seedling_stage_complete);
        }
    }

    private void renderFertilizerGuide() {
        if (batch == null) return;
        String stageLabel = viewModel.stageLabel(batch.getStage());
        SeedlingFertilizerGuide.Recommendation recommendation =
                SeedlingFertilizerGuide.forPlant(
                        batch.getPlant_type(), batch.getStage());
        fertilizerContext.setText(getString(R.string.seedling_fertilizer_context,
                batch.getPlant_type(), stageLabel));
        fertilizerType.setText(recommendation.getFertilizerText());
        fertilizerApplication.setText(recommendation.getApplicationText());
        fertilizerCropNote.setText(recommendation.getCropNoteText());
    }

    private void renderTimeline() {
        int completed = SeedlingTimeline.completedSteps(batch.getStage());
        for (int index = 0; index < stageNodes.length; index++) {
            boolean done = index < completed;
            stageNodes[index].setBackgroundResource(done
                    ? R.drawable.bg_seedling_stage_done
                    : R.drawable.bg_seedling_stage_pending);
            stageNodes[index].setText(done ? "✓" : "");
        }
        for (int index = 0; index < stageLines.length; index++) {
            stageLines[index].setBackgroundColor(ContextCompat.getColor(this,
                    index < completed - 1 ? R.color.primary : R.color.divider));
        }
        long[] milestones = SeedlingTimeline.milestoneEpochs(batch);
        for (int index = 0; index < stageDates.length; index++) {
            stageDates[index].setText(dayMonth(milestones[index]));
        }
    }

    private void showBatchActions() {
        if (batch == null) return;
        boolean canRetreat = viewModel.canRetreat(batch);
        List<String> actions = new ArrayList<>();
        actions.add(getString(R.string.seedling_batch_information_title));
        if (canRetreat) actions.add(getString(R.string.seedling_previous_stage));
        actions.add(getString(R.string.seedling_delete_batch));
        int previousIndex = canRetreat ? 1 : -1;
        int deleteIndex = actions.size() - 1;
        new MaterialAlertDialogBuilder(this)
                .setTitle(batch.displayName())
                .setItems(actions.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) {
                        showBatchInformation();
                    } else if (which == previousIndex) {
                        confirmPreviousStage();
                    } else if (which == deleteIndex) {
                        requestDeleteBatch();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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

    private void confirmPreviousStage() {
        SeedlingBatch target = batch;
        if (target == null || !viewModel.canRetreat(target)) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.seedling_previous_stage_title)
                .setMessage(getString(R.string.seedling_previous_stage_message,
                        target.displayName()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.seedling_previous_stage, (dialog, which) ->
                        updateStage(viewModel.previousStage(target),
                                R.string.seedling_stage_reverted))
                .show();
    }

    private void requestDeleteBatch() {
        if (hasDailyLogs) {
            showDeleteBlocked();
            return;
        }
        confirmDeleteBatch();
    }

    private void confirmDeleteBatch() {
        SeedlingBatch target = batch;
        if (target == null) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.seedling_delete_batch_title,
                        target.displayName()))
                .setMessage(R.string.seedling_delete_batch_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.seedling_delete_batch, (dialog, which) ->
                        viewModel.deleteBatch(target.getBatch_id())
                                .addOnSuccessListener(ignored -> {
                                    Toast.makeText(
                                            this,
                                            R.string.seedling_delete_batch_success,
                                            Toast.LENGTH_SHORT
                                    ).show();
                                    finish();
                                })
                                .addOnFailureListener(error -> {
                                    if (viewModel.isDeletionBlockedByLogs(error)) {
                                        hasDailyLogs = true;
                                        showDeleteBlocked();
                                    } else {
                                        Toast.makeText(
                                                this,
                                                R.string.seedling_delete_batch_failed,
                                                Toast.LENGTH_LONG
                                        ).show();
                                    }
                                }))
                .show();
    }

    private void showDeleteBlocked() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.seedling_delete_blocked_title)
                .setMessage(R.string.seedling_delete_blocked_message)
                .setPositiveButton(R.string.seedling_close, null)
                .show();
    }

    private void confirmAdvanceStage() {
        SeedlingBatch target = batch;
        if (stageUpdateInProgress || target == null || !viewModel.canAdvance(target)) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.seedling_advance_stage_title)
                .setMessage(getString(R.string.seedling_advance_stage_message,
                        target.displayName()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.seedling_advance_stage_confirm,
                        (dialog, which) -> advanceStage())
                .show();
    }

    private void advanceStage() {
        if (stageUpdateInProgress || batch == null || !viewModel.canAdvance(batch)) return;
        String previousStage = batch.getStage();
        stageUpdateInProgress = true;
        advance.setEnabled(false);
        viewModel.advanceStage(batch)
                .addOnSuccessListener(ignored -> {
                    stageUpdateInProgress = false;
                    renderBatch();
                    showAdvanceUndo(previousStage);
                })
                .addOnFailureListener(error -> {
                    stageUpdateInProgress = false;
                    renderBatch();
                    Toast.makeText(this, R.string.seedling_stage_update_failed,
                            Toast.LENGTH_LONG).show();
                });
    }

    private void showAdvanceUndo(String previousStage) {
        Snackbar snackbar = Snackbar.make(
                findViewById(R.id.seedlingDetailRoot),
                R.string.seedling_stage_advanced,
                Snackbar.LENGTH_LONG
        );
        snackbar.setAnchorView(findViewById(R.id.layoutSeedlingDetailActions));
        snackbar.setAction(R.string.seedling_undo,
                view -> updateStage(previousStage, R.string.seedling_stage_reverted));
        snackbar.show();
    }

    private void updateStage(String stage, int successMessage) {
        if (stageUpdateInProgress || batch == null) return;
        stageUpdateInProgress = true;
        advance.setEnabled(false);
        viewModel.setStage(batchId, stage)
                .addOnSuccessListener(ignored -> {
                    stageUpdateInProgress = false;
                    renderBatch();
                    Snackbar.make(
                                    findViewById(R.id.seedlingDetailRoot),
                                    successMessage,
                                    Snackbar.LENGTH_SHORT)
                            .setAnchorView(findViewById(R.id.layoutSeedlingDetailActions))
                            .show();
                })
                .addOnFailureListener(error -> {
                    stageUpdateInProgress = false;
                    renderBatch();
                    Toast.makeText(this, R.string.seedling_stage_update_failed,
                            Toast.LENGTH_LONG).show();
                });
    }

    private void renderNode(SeedlingNodeState value) {
        latestNodeState = value;
        SeedlingTelemetry telemetry = value == null ? null : value.getLatest();
        long nowMillis = System.currentTimeMillis();
        boolean fresh = telemetry != null && telemetry.isFresh(nowMillis / 1000L, 45L);
        noTelemetry.setVisibility(fresh ? View.GONE : View.VISIBLE);
        if (fresh) {
            airTemp.setText(getString(R.string.seedling_temperature_value,
                    telemetry.getAir_temperature_c()));
            airHumidity.setText(getString(R.string.seedling_percent_value,
                    telemetry.getAir_humidity_pct()));
            soilMoisture.setText(telemetry.isSoil_moisture_available()
                    ? getString(R.string.seedling_percent_value,
                            telemetry.getSoil_moisture_pct())
                    : getString(R.string.seedling_metric_empty));
            rootTemp.setText(getString(R.string.seedling_temperature_value,
                    telemetry.getRoot_temperature_c()));
            light.setText(getString(R.string.seedling_light_value,
                    telemetry.getLight_lux()));
        } else {
            String empty = getString(R.string.seedling_metric_empty);
            airTemp.setText(empty);
            airHumidity.setText(empty);
            soilMoisture.setText(empty);
            rootTemp.setText(empty);
            light.setText(empty);
        }

        SeedlingEnvironmentGuide.Assessment assessment =
                SeedlingEnvironmentGuide.assess(
                        batch == null ? "" : batch.getPlant_type(),
                        batch == null ? "" : batch.getStage(),
                        fresh ? telemetry : null,
                        localHour(nowMillis));
        renderMetricCards(assessment, fresh);

        SeedlingRecommendation advice = value == null ? null : value.getRecommendation();
        renderOverallStatus(fresh, assessment);
        renderAdvice(fresh, advice);
    }

    private void renderMetricCards(SeedlingEnvironmentGuide.Assessment assessment,
                                   boolean fresh) {
        renderMetricCard(airTempCard, airTempIcon, airTempTarget, airTempState,
                assessment.get(SeedlingEnvironmentGuide.Metric.AIR_TEMPERATURE),
                fresh, R.color.primary);
        renderMetricCard(airHumidityCard, airHumidityIcon,
                airHumidityTarget, airHumidityState,
                assessment.get(SeedlingEnvironmentGuide.Metric.AIR_HUMIDITY),
                fresh, R.color.info);
        renderMetricCard(soilMoistureCard, soilMoistureIcon,
                soilMoistureTarget, soilMoistureState,
                assessment.get(SeedlingEnvironmentGuide.Metric.SOIL_MOISTURE),
                fresh, R.color.primary);
        renderMetricCard(rootTempCard, rootTempIcon, rootTempTarget, rootTempState,
                assessment.get(SeedlingEnvironmentGuide.Metric.ROOT_TEMPERATURE),
                fresh, R.color.primary);
        renderMetricCard(lightCard, lightIcon, lightTarget, lightState,
                assessment.get(SeedlingEnvironmentGuide.Metric.LIGHT),
                fresh, R.color.primary);
    }

    private void renderMetricCard(MaterialCardView card, ImageView icon,
                                  TextView target, TextView state,
                                  SeedlingEnvironmentGuide.MetricAssessment result,
                                  boolean fresh, int normalIconColor) {
        target.setText(targetText(result));
        int color = normalIconColor;
        int background = R.color.surfaceGreen;
        int stateText = R.string.seedling_metric_state_normal;
        switch (result.getStatus()) {
            case LOW:
                color = result.isCritical() ? R.color.offline : R.color.warning;
                background = result.isCritical()
                        ? R.color.offlineBackground : R.color.warningBackground;
                stateText = R.string.seedling_metric_state_low;
                break;
            case HIGH:
                color = result.isCritical() ? R.color.offline : R.color.warning;
                background = result.isCritical()
                        ? R.color.offlineBackground : R.color.warningBackground;
                stateText = R.string.seedling_metric_state_high;
                break;
            case UNAVAILABLE:
                color = fresh ? R.color.warning : R.color.textSecondary;
                background = fresh ? R.color.warningBackground : R.color.card;
                stateText = fresh
                        ? R.string.seedling_metric_state_sensor_unavailable
                        : R.string.seedling_metric_state_waiting;
                break;
            case NIGHT:
                color = R.color.textSecondary;
                background = R.color.card;
                stateText = R.string.seedling_metric_state_night;
                break;
            case BEFORE_EMERGENCE:
                color = R.color.textSecondary;
                background = R.color.card;
                stateText = R.string.seedling_metric_state_before_emergence;
                break;
            case NORMAL:
            default:
                break;
        }
        int resolvedColor = ContextCompat.getColor(this, color);
        card.setCardBackgroundColor(ContextCompat.getColor(this, background));
        card.setStrokeColor(resolvedColor);
        icon.setImageTintList(ColorStateList.valueOf(resolvedColor));
        state.setText(stateText);
        state.setTextColor(resolvedColor);
    }

    private String targetText(SeedlingEnvironmentGuide.MetricAssessment result) {
        SeedlingEnvironmentGuide.Range range = result.getTarget();
        switch (result.getMetric()) {
            case AIR_TEMPERATURE:
            case ROOT_TEMPERATURE:
                return getString(R.string.seedling_metric_target_temperature,
                        range.getMinimum(), range.getMaximum());
            case AIR_HUMIDITY:
            case SOIL_MOISTURE:
                return getString(R.string.seedling_metric_target_percent,
                        range.getMinimum(), range.getMaximum());
            case LIGHT:
            default:
                return getString(R.string.seedling_metric_target_light,
                        range.getMinimum(), range.getMaximum());
        }
    }

    private void renderOverallStatus(boolean fresh,
                                     SeedlingEnvironmentGuide.Assessment assessment) {
        int color;
        int background;
        String icon;
        if (!fresh) {
            color = R.color.warning;
            background = R.color.warningBackground;
            icon = "…";
            overallTitle.setText(R.string.seedling_overall_waiting);
            overallMessage.setText(R.string.seedling_overall_waiting_message);
        } else if (assessment.getSeverity()
                == SeedlingEnvironmentGuide.Severity.CRITICAL) {
            color = R.color.offline;
            background = R.color.offlineBackground;
            icon = "!";
            setOverallTitleWithCount(R.string.seedling_overall_critical,
                    assessment.getIssues().size());
            setOverallConditionMessage(assessment);
        } else if (assessment.getSeverity()
                == SeedlingEnvironmentGuide.Severity.WARNING) {
            color = R.color.warning;
            background = R.color.warningBackground;
            icon = "!";
            setOverallTitleWithCount(R.string.seedling_overall_warning,
                    assessment.getIssues().size());
            setOverallConditionMessage(assessment);
        } else {
            color = R.color.primary;
            background = R.color.surfaceGreen;
            icon = "✓";
            overallTitle.setText(R.string.seedling_overall_good);
            overallMessage.setText(R.string.seedling_overall_good_message);
        }
        int resolvedColor = ContextCompat.getColor(this, color);
        overallCard.setCardBackgroundColor(ContextCompat.getColor(this, background));
        overallCard.setStrokeColor(resolvedColor);
        overallIcon.setText(icon);
        overallTitle.setTextColor(resolvedColor);
        ViewCompat.setBackgroundTintList(overallIcon,
                ColorStateList.valueOf(resolvedColor));
    }

    private void setOverallTitleWithCount(int title, int count) {
        overallTitle.setText(getString(R.string.seedling_overall_title_with_count,
                getString(title), count));
    }

    private void setOverallConditionMessage(
            SeedlingEnvironmentGuide.Assessment assessment) {
        List<String> conditions = new ArrayList<>();
        for (SeedlingEnvironmentGuide.MetricAssessment issue
                : assessment.getIssues()) {
            String label = getString(metricLabel(issue.getMetric()));
            if (issue.getStatus() == SeedlingEnvironmentGuide.Status.UNAVAILABLE) {
                conditions.add(getString(R.string.seedling_metric_issue_unavailable,
                        label));
                continue;
            }
            int message = issue.getStatus() == SeedlingEnvironmentGuide.Status.LOW
                    ? R.string.seedling_metric_issue_low
                    : R.string.seedling_metric_issue_high;
            conditions.add(getString(message, label, currentText(issue),
                    rangeText(issue)));
        }
        String bullets = SeedlingConditionSummary.bulletList(
                String.join(" ", conditions));
        overallMessage.setText(getString(R.string.seedling_overall_conditions, bullets));
    }

    private int metricLabel(SeedlingEnvironmentGuide.Metric metric) {
        switch (metric) {
            case AIR_TEMPERATURE: return R.string.seedling_metric_temperature;
            case AIR_HUMIDITY: return R.string.seedling_metric_humidity;
            case SOIL_MOISTURE: return R.string.seedling_metric_soil_moisture;
            case ROOT_TEMPERATURE: return R.string.seedling_metric_soil_temperature;
            case LIGHT:
            default: return R.string.seedling_metric_light;
        }
    }

    private String currentText(SeedlingEnvironmentGuide.MetricAssessment item) {
        double value = item.getCurrent() == null ? 0d : item.getCurrent();
        switch (item.getMetric()) {
            case AIR_TEMPERATURE:
            case ROOT_TEMPERATURE:
                return getString(R.string.seedling_temperature_value, value);
            case AIR_HUMIDITY:
            case SOIL_MOISTURE:
                return getString(R.string.seedling_percent_value, value);
            case LIGHT:
            default:
                return getString(R.string.seedling_light_value, value)
                        + " " + getString(R.string.seedling_lux_unit);
        }
    }

    private String rangeText(SeedlingEnvironmentGuide.MetricAssessment item) {
        SeedlingEnvironmentGuide.Range range = item.getTarget();
        switch (item.getMetric()) {
            case AIR_TEMPERATURE:
            case ROOT_TEMPERATURE:
                return getString(R.string.seedling_metric_range_temperature,
                        range.getMinimum(), range.getMaximum());
            case AIR_HUMIDITY:
            case SOIL_MOISTURE:
                return getString(R.string.seedling_metric_range_percent,
                        range.getMinimum(), range.getMaximum());
            case LIGHT:
            default:
                return getString(R.string.seedling_metric_range_light,
                        range.getMinimum(), range.getMaximum());
        }
    }

    private int localHour(long nowMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(nowMillis);
        return calendar.get(Calendar.HOUR_OF_DAY);
    }

    private void renderAdvice(boolean fresh, @Nullable SeedlingRecommendation advice) {
        adviceCard.setVisibility(View.VISIBLE);
        if (!fresh || advice == null) {
            adviceTitle.setText(R.string.seedling_advice_waiting);
            adviceMessage.setText("");
            return;
        }
        String action = viewModel.recommendationAction(advice);
        String message = viewModel.recommendationMessage(advice);
        if (action.isBlank() && message.isBlank()) {
            adviceCard.setVisibility(View.GONE);
            return;
        }
        if (action.isBlank()) action = advice.getTitle();
        adviceTitle.setText(getString(R.string.seedling_advice_title_format, action));
        adviceMessage.setText(message);
    }

    private void renderLogs(List<SeedlingDailyLog> values) {
        logs.removeAllViews();
        boolean empty = values == null || values.isEmpty();
        hasDailyLogs = !empty;
        logsEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) return;
        for (SeedlingDailyLog value : values) {
            logs.addView(logRow(value));
        }
    }

    private View logRow(SeedlingDailyLog value) {
        HorizontalScrollView swipe = new SwipeActionsView();
        swipe.setHorizontalScrollBarEnabled(false);
        swipe.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout.LayoutParams swipeParams = new LinearLayout.LayoutParams(-1, -2);
        swipeParams.topMargin = dp(8);
        swipe.setLayoutParams(swipeParams);

        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.HORIZONTAL);
        rail.setGravity(Gravity.CENTER_VERTICAL);

        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card));
        card.setStrokeColor(ContextCompat.getColor(this, R.color.border));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(14));
        int cardWidth = logs.getWidth() > 0
                ? logs.getWidth()
                : getResources().getDisplayMetrics().widthPixels - dp(24);
        card.setLayoutParams(new LinearLayout.LayoutParams(cardWidth, -2));
        card.setContentDescription(getString(R.string.seedling_log_swipe_hint,
                date(value.getCreated_at_epoch())));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        details.addView(text(date(value.getCreated_at_epoch()), 14,
                R.color.textPrimary, Typeface.BOLD));
        details.addView(text(getResources().getQuantityString(R.plurals.seedling_log_meta,
                value.getLeaf_count(), value.getHeight_cm(), value.getLeaf_count(),
                value.getHealthy_count()), 12, R.color.textSecondary, Typeface.NORMAL));
        details.addView(text(getString(value.isWatered() ? R.string.seedling_watered_yes
                : R.string.seedling_watered_no), 12, value.isWatered()
                ? R.color.info : R.color.textSecondary, Typeface.NORMAL));
        if (!value.getNote().isBlank()) {
            details.addView(text(value.getNote(), 12,
                    R.color.textSecondary, Typeface.NORMAL));
        }
        content.addView(details);
        if (value.hasPhoto()) content.addView(logPhoto(value));
        card.addView(content);

        int actionWidth = dp(82);
        MaterialButton edit = logActionButton(
                R.string.seedling_log_edit, R.color.primary, actionWidth);
        MaterialButton delete = logActionButton(
                R.string.seedling_log_delete, R.color.offline, actionWidth);
        edit.setOnClickListener(view -> {
            swipe.smoothScrollTo(0, 0);
            editLog(value);
        });
        delete.setOnClickListener(view -> {
            swipe.smoothScrollTo(0, 0);
            confirmDeleteLog(value);
        });

        rail.addView(card);
        rail.addView(edit);
        rail.addView(delete);
        swipe.addView(rail, new HorizontalScrollView.LayoutParams(-2, -2));

        int revealWidth = actionWidth * 2;
        swipe.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                swipe.post(() -> swipe.smoothScrollTo(
                        swipe.getScrollX() >= revealWidth / 3 ? revealWidth : 0, 0));
                if (event.getActionMasked() == MotionEvent.ACTION_UP) view.performClick();
            }
            return false;
        });
        return swipe;
    }

    private MaterialButton logActionButton(int textRes, int colorRes, int width) {
        MaterialButton button = new MaterialButton(this);
        button.setText(textRes);
        button.setTextColor(Color.WHITE);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setCornerRadius(dp(12));
        button.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, colorRes)));
        button.setLayoutParams(new LinearLayout.LayoutParams(width, -1));
        return button;
    }

    private void editLog(SeedlingDailyLog value) {
        Intent intent = new Intent(this, SeedlingDailyLogActivity.class);
        intent.putExtra(EXTRA_BATCH_ID, batchId);
        intent.putExtra(SeedlingDailyLogActivity.EXTRA_LOG_ID, value.getLog_id());
        startActivity(intent);
    }

    private void confirmDeleteLog(SeedlingDailyLog value) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.seedling_log_delete_title)
                .setMessage(getString(R.string.seedling_log_delete_message,
                        date(value.getCreated_at_epoch())))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.seedling_log_delete_confirm,
                        (dialog, which) -> deleteLog(value))
                .show();
    }

    private void deleteLog(SeedlingDailyLog value) {
        viewModel.deleteDailyLog(batchId, value.getLog_id())
                .addOnSuccessListener(ignored -> {
                    viewModel.deleteDailyPhoto(value);
                    Toast.makeText(this, R.string.seedling_log_deleted,
                            Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(error -> Toast.makeText(
                        this, R.string.seedling_log_delete_failed, Toast.LENGTH_LONG).show());
    }

    private ImageView logPhoto(SeedlingDailyLog value) {
        ImageView image = new ImageView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(76), dp(76));
        params.leftMargin = dp(10);
        image.setLayoutParams(params);
        image.setBackgroundResource(R.drawable.bg_doctor_photo_placeholder);
        image.setPadding(dp(3), dp(3), dp(3), dp(3));
        image.setScaleType(ImageView.ScaleType.CENTER);
        image.setImageResource(R.drawable.ic_seedling_plant_type_24);
        image.setContentDescription(getString(R.string.seedling_daily_photo_description));
        viewModel.loadDailyPhoto(value).addOnSuccessListener(photo -> {
            if (isFinishing()) return;
            image.setPadding(0, 0, 0, 0);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setImageURI(android.net.Uri.fromFile(
                    new java.io.File(photo.getLocal_path())));
            image.setOnClickListener(view -> GardenPhotoViewerDialog.show(
                    this, java.util.Collections.singletonList(photo), photo.getId()));
        });
        return image;
    }

    private String date(long epoch) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                .format(new Date(epoch * 1000L));
    }

    private String dayMonth(long epoch) {
        if (epoch <= 0L) return "—";
        return new SimpleDateFormat("dd.MM", Locale.getDefault())
                .format(new Date(epoch * 1000L));
    }

    private TextView text(String value, int size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(ContextCompat.getColor(this, color));
        view.setTypeface(Typeface.DEFAULT, style);
        view.setPadding(0, dp(3), 0, dp(2));
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class SwipeActionsView extends HorizontalScrollView {
        SwipeActionsView() {
            super(SeedlingBatchDetailActivity.this);
        }

        @Override public boolean performClick() {
            super.performClick();
            return true;
        }
    }
}
