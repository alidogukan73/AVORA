package com.alidogukan.avora.activities;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.models.SeedlingBatch;
import com.alidogukan.avora.models.SeedlingNodeState;
import com.alidogukan.avora.models.SeedlingRecommendation;
import com.alidogukan.avora.models.SeedlingTelemetry;
import com.alidogukan.avora.ui.AssistantIntroCard;
import com.alidogukan.avora.ui.PrimaryBottomNavigation;
import com.alidogukan.avora.viewmodels.SeedlingViewModel;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Dashboard for advisory seedling monitoring and batch tracking. */
public final class SeedlingAssistantActivity extends EdgeToEdgeActivity {
    private static final int COLLAPSED_BATCH_COUNT = 3;
    private static final int VISIBLE_TASK_COUNT = 3;

    private SeedlingViewModel viewModel;
    private LinearLayout batchContainer;
    private LinearLayout taskContainer;
    private TextView empty;
    private TextView tasksEmpty;
    private TextView activeCount;
    private TextView taskCount;
    private TextView readyCount;
    private TextView viewAllBatches;
    private final List<SeedlingBatch> batches = new ArrayList<>();
    private SeedlingNodeState nodeState;
    private boolean showAllBatches;

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_seedling_assistant);
        AssistantIntroCard.bind(this, AssistantIntroCard.Kind.SEEDLING);
        viewModel = new ViewModelProvider(this).get(SeedlingViewModel.class);
        batchContainer = findViewById(R.id.layoutSeedlingBatches);
        taskContainer = findViewById(R.id.layoutSeedlingTasks);
        empty = findViewById(R.id.txtSeedlingEmpty);
        tasksEmpty = findViewById(R.id.txtSeedlingTasksEmpty);
        activeCount = findViewById(R.id.txtActiveBatchCount);
        taskCount = findViewById(R.id.txtSeedlingTaskCount);
        readyCount = findViewById(R.id.txtReadyBatchCount);
        viewAllBatches = findViewById(R.id.btnViewAllSeedlingBatches);

        findViewById(R.id.btnBack).setOnClickListener(view -> finish());
        findViewById(R.id.btnSeedlingNotifications).setOnClickListener(view ->
                startActivity(new Intent(this, NotificationCenterActivity.class)));
        findViewById(R.id.btnNewSeedlingBatch).setOnClickListener(view ->
                startActivity(new Intent(this, SeedlingBatchEditorActivity.class)));
        viewAllBatches.setOnClickListener(view -> {
            if (batches.size() <= COLLAPSED_BATCH_COUNT) return;
            showAllBatches = !showAllBatches;
            render();
        });
        PrimaryBottomNavigation.bind(this, PrimaryBottomNavigation.HOME);

        viewModel.getBatches().observe(this, values -> {
            batches.clear();
            if (values != null) batches.addAll(values);
            render();
        });
        viewModel.getNode("seedling-001").observe(this, value -> {
            nodeState = value;
            render();
        });
    }

    private void render() {
        renderIntroReliability();
        renderBatches();
        List<SeedlingTask> tasks = buildTasks();
        renderTasks(tasks);

        int active = 0;
        int ready = 0;
        for (SeedlingBatch batch : batches) {
            if (isActive(batch)) active++;
            if (viewModel.isReady(batch)) ready++;
        }
        activeCount.setText(String.valueOf(active));
        readyCount.setText(String.valueOf(ready));
        taskCount.setText(String.valueOf(tasks.size()));
    }

    private void renderIntroReliability() {
        SeedlingTelemetry telemetry = nodeState == null ? null : nodeState.getLatest();
        boolean fresh = telemetry != null
                && telemetry.isFresh(System.currentTimeMillis() / 1000L, 45L);
        boolean complete = telemetry != null && telemetry.isSoil_moisture_available();
        AssistantIntroCard.updateSeedlingReliability(this, fresh, complete);
    }

    private void renderBatches() {
        batchContainer.removeAllViews();
        empty.setVisibility(batches.isEmpty() ? View.VISIBLE : View.GONE);
        viewAllBatches.setVisibility(batches.size() > COLLAPSED_BATCH_COUNT
                ? View.VISIBLE : View.GONE);

        int limit = showAllBatches
                ? batches.size() : Math.min(COLLAPSED_BATCH_COUNT, batches.size());
        for (int index = 0; index < limit; index++) {
            batchContainer.addView(batchCard(batches.get(index)));
        }
        boolean canCollapse = batches.size() > COLLAPSED_BATCH_COUNT;
        viewAllBatches.setText(showAllBatches && canCollapse
                ? R.string.seedling_show_fewer_batches : R.string.seedling_view_all_batches);
    }

    private List<SeedlingTask> buildTasks() {
        List<SeedlingTask> tasks = new ArrayList<>();
        SeedlingTelemetry telemetry = nodeState == null ? null : nodeState.getLatest();
        boolean sensorFresh = telemetry != null
                && telemetry.isFresh(System.currentTimeMillis() / 1000L, 45L);
        SeedlingRecommendation recommendation = nodeState == null
                ? null : nodeState.getRecommendation();
        String recommendationAction = viewModel.recommendationAction(recommendation);

        for (SeedlingBatch batch : batches) {
            if (!isActive(batch)) continue;
            String crop = cropName(batch);
            String label;
            if (!sensorFresh) {
                label = getString(R.string.seedling_task_sensor_check, crop);
            } else if (tasks.isEmpty() && recommendation != null
                    && !"GOOD".equalsIgnoreCase(recommendation.getSeverity())
                    && !recommendationAction.isBlank()) {
                label = recommendationAction;
            } else {
                label = stageTask(batch, crop);
            }
            tasks.add(new SeedlingTask(label, batch));
        }
        return tasks;
    }

    private String stageTask(SeedlingBatch batch, String crop) {
        String stage = batch.getStage() == null ? ""
                : batch.getStage().trim().toUpperCase(Locale.ROOT);
        switch (stage) {
            case "GERMINATING":
                return getString(R.string.seedling_task_germination_check, crop);
            case "COTYLEDON":
            case "TRUE_LEAVES":
                return getString(R.string.seedling_task_leaf_check, crop);
            case "HARDENING":
                return getString(R.string.seedling_task_hardening, crop);
            case "READY":
                return getString(R.string.seedling_task_ready, crop);
            default:
                return getString(R.string.seedling_task_sowing_check, crop);
        }
    }

    private void renderTasks(List<SeedlingTask> tasks) {
        taskContainer.removeAllViews();
        tasksEmpty.setVisibility(tasks.isEmpty() ? View.VISIBLE : View.GONE);
        int limit = Math.min(VISIBLE_TASK_COUNT, tasks.size());
        for (int index = 0; index < limit; index++) {
            taskContainer.addView(taskRow(tasks.get(index)));
        }
    }

    private View batchCard(SeedlingBatch batch) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card));
        card.setStrokeColor(ContextCompat.getColor(this, R.color.border));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(14));
        card.setCardElevation(0f);
        card.setClickable(true);
        card.setFocusable(true);
        LinearLayout.LayoutParams outer = new LinearLayout.LayoutParams(-1, -2);
        outer.topMargin = dp(8);
        card.setLayoutParams(outer);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(9), dp(8), dp(9));

        TextView emoji = text(batch.getEmoji().isBlank() ? "🌱" : batch.getEmoji(),
                30, R.color.textPrimary, Typeface.NORMAL);
        emoji.setGravity(Gravity.CENTER);
        row.addView(emoji, new LinearLayout.LayoutParams(dp(48), dp(56)));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(8), 0, dp(4), 0);
        TextView title = text(batch.displayName(), 16, R.color.textPrimary, Typeface.BOLD);
        title.setMaxLines(1);
        details.addView(title);

        LinearLayout metaRow = new LinearLayout(this);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams metaRowParams = new LinearLayout.LayoutParams(-1, -2);
        metaRowParams.topMargin = dp(3);
        int day = (int) Math.max(1L, (System.currentTimeMillis() / 1000L
                - batch.getSowing_date_epoch()) / 86400L + 1L);
        TextView meta = text(getString(R.string.seedling_batch_detail_meta,
                day, batch.getHealthy_count()), 12, R.color.textSecondary, Typeface.NORMAL);
        meta.setMaxLines(1);
        metaRow.addView(meta, new LinearLayout.LayoutParams(0, -2, 1f));

        int chipColor = stageColor(batch);
        int chipBackground = stageBackground(batch);
        TextView stage = text(viewModel.stageLabel(batch.getStage()), 11,
                chipColor, Typeface.BOLD);
        stage.setGravity(Gravity.CENTER);
        stage.setMaxLines(1);
        stage.setPadding(dp(8), dp(3), dp(8), dp(3));
        stage.setBackground(roundDrawable(chipBackground, chipBackground, dp(12)));
        metaRow.addView(stage, new LinearLayout.LayoutParams(-2, -2));
        details.addView(metaRow, metaRowParams);
        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView actions = text(getString(R.string.symbol_more_options),
                26, R.color.textSecondary, Typeface.NORMAL);
        actions.setGravity(Gravity.CENTER);
        actions.setClickable(true);
        actions.setFocusable(true);
        actions.setContentDescription(getString(
                R.string.seedling_batch_actions_description, batch.displayName()));
        actions.setOnClickListener(view -> showBatchActions(batch));
        row.addView(actions, new LinearLayout.LayoutParams(dp(42), dp(56)));
        card.addView(row);
        card.setContentDescription(getString(R.string.seedling_open_detail)
                + ": " + batch.displayName());
        card.setOnClickListener(view -> openBatch(batch));
        return card;
    }

    private void showBatchActions(SeedlingBatch batch) {
        String[] actions = {
                getString(R.string.seedling_open_detail),
                getString(R.string.seedling_delete_batch)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(batch.displayName())
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        openBatch(batch);
                    } else {
                        requestDeleteBatch(batch);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void requestDeleteBatch(SeedlingBatch batch) {
        viewModel.hasDailyLogs(batch.getBatch_id())
                .addOnSuccessListener(hasLogs -> {
                    if (Boolean.TRUE.equals(hasLogs)) {
                        showDeleteBlocked();
                    } else {
                        confirmDeleteBatch(batch);
                    }
                })
                .addOnFailureListener(error -> Toast.makeText(
                        this,
                        R.string.seedling_delete_check_failed,
                        Toast.LENGTH_LONG
                ).show());
    }

    private void confirmDeleteBatch(SeedlingBatch batch) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.seedling_delete_batch_title,
                        batch.displayName()))
                .setMessage(R.string.seedling_delete_batch_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.seedling_delete_batch, (dialog, which) ->
                        viewModel.deleteBatch(batch.getBatch_id())
                                .addOnSuccessListener(ignored -> Toast.makeText(
                                        this,
                                        R.string.seedling_delete_batch_success,
                                        Toast.LENGTH_SHORT
                                ).show())
                                .addOnFailureListener(error -> {
                                    if (viewModel.isDeletionBlockedByLogs(error)) {
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

    private View taskRow(SeedlingTask task) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(36));
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(getString(R.string.seedling_open_task, task.label));

        TextView bullet = text("•", 19, R.color.primary, Typeface.BOLD);
        bullet.setGravity(Gravity.CENTER);
        row.addView(bullet, new LinearLayout.LayoutParams(dp(22), dp(34)));

        TextView label = text(task.label, 13, R.color.textPrimary, Typeface.NORMAL);
        label.setMaxLines(2);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView arrow = text("›", 24, R.color.textSecondary, Typeface.NORMAL);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(28), dp(34)));
        row.setOnClickListener(view -> openBatch(task.batch));
        return row;
    }

    private void openBatch(SeedlingBatch batch) {
        Intent intent = new Intent(this, SeedlingBatchDetailActivity.class);
        intent.putExtra(SeedlingBatchDetailActivity.EXTRA_BATCH_ID, batch.getBatch_id());
        startActivity(intent);
    }

    private boolean isActive(SeedlingBatch batch) {
        return "ACTIVE".equalsIgnoreCase(batch.getStatus());
    }

    private String cropName(SeedlingBatch batch) {
        return batch.getPlant_type().isBlank() ? batch.displayName() : batch.getPlant_type();
    }

    private int stageColor(SeedlingBatch batch) {
        String stage = batch.getStage() == null ? "" : batch.getStage();
        if ("READY".equalsIgnoreCase(stage)) return R.color.info;
        if ("GERMINATING".equalsIgnoreCase(stage)) return R.color.accentOrange;
        return R.color.primary;
    }

    private int stageBackground(SeedlingBatch batch) {
        String stage = batch.getStage() == null ? "" : batch.getStage();
        if ("READY".equalsIgnoreCase(stage)) return getColor(R.color.infoBackground);
        if ("GERMINATING".equalsIgnoreCase(stage)) return getColor(R.color.warningBackground);
        return getColor(R.color.onlineBackground);
    }

    private TextView text(String value, int size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(ContextCompat.getColor(this, color));
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private GradientDrawable roundDrawable(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class SeedlingTask {
        private final String label;
        private final SeedlingBatch batch;

        private SeedlingTask(String label, SeedlingBatch batch) {
            this.label = label;
            this.batch = batch;
        }
    }
}
