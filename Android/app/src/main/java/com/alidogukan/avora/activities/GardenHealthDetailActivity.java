package com.alidogukan.avora.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.health.GardenHealthCalculator;
import com.alidogukan.avora.health.GardenHealthSummary;
import com.alidogukan.avora.health.GardenHealthIssue;
import com.alidogukan.avora.health.GardenHealthZoneResult;
import com.alidogukan.avora.models.GardenPhoto;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.plantassistant.PlantAssistantRecordResolver;
import com.alidogukan.avora.viewmodels.GardenPhotoGalleryViewModel;
import com.alidogukan.avora.viewmodels.MainViewModel;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/** Explains the garden health score. It is advisory only and never controls hardware. */
public class GardenHealthDetailActivity extends AppCompatActivity {
    private TextView summaryScore;
    private TextView summaryTitle;
    private TextView summaryDetail;
    private LinearLayout zoneList;
    private MainViewModel viewModel;
    private GardenPhotoGalleryViewModel photoViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_garden_health_detail);

        View root = findViewById(R.id.gardenHealthDetailRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        findViewById(R.id.btnGardenHealthBack).setOnClickListener(view -> finish());
        summaryScore = findViewById(R.id.txtGardenHealthDetailScore);
        summaryTitle = findViewById(R.id.txtGardenHealthDetailTitle);
        summaryDetail = findViewById(R.id.txtGardenHealthDetailSummary);
        zoneList = findViewById(R.id.layoutGardenHealthZones);

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        photoViewModel = new ViewModelProvider(this).get(GardenPhotoGalleryViewModel.class);
        viewModel.getGardenZones().observe(this, this::render);
    }

    private void render(List<GardenZone> zones) {
        long now = System.currentTimeMillis() / 1000L;
        GardenHealthSummary summary = viewModel.gardenHealth(zones, now);
        List<GardenZone> healthZones = GardenHealthCalculator.activeHealthZones(zones);
        summaryScore.setText(getString(R.string.runtime_health_score_format, summary.getScore()));
        summaryTitle.setText(summary.getTitle());
        summaryDetail.setText(summary.getDetail());
        summaryScore.setTextColor(ContextCompat.getColor(this, colorFor(summary.getScore())));

        zoneList.removeAllViews();
        if (healthZones.isEmpty()) {
            return;
        }
        for (GardenZone zone : healthZones) {
            addZoneCard(zone, now);
        }
    }

    private void addZoneCard(GardenZone zone, long now) {
        GardenHealthZoneResult result = viewModel.gardenHealthForZone(zone, now);
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = dp(10);
        card.setLayoutParams(cardParams);
        card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card));
        card.setStrokeColor(ContextCompat.getColor(this, R.color.divider));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(18));
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView title = new TextView(this);
        title.setText(getString(
                R.string.runtime_icon_label,
                zone.getEmoji() == null
                        ? getString(R.string.symbol_plant)
                        : zone.getEmoji(),
                com.alidogukan.avora.zones.PhysicalZoneIdentity.name(zone)));
        title.setTextColor(ContextCompat.getColor(this, R.color.textPrimary));
        title.setTextSize(16);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView detail = new TextView(this);
        detail.setText(issueSummary(result));
        detail.setTextColor(ContextCompat.getColor(this, R.color.textSecondary));
        detail.setTextSize(13);
        detail.setPadding(0, dp(4), 0, 0);
        text.addView(title);
        text.addView(detail);
        TextView action = new TextView(this);
        action.setText(result.getIssues().size() == 1
                ? targetLabel(result.getIssues().get(0))
                : getString(result.getIssues().isEmpty()
                ? R.string.garden_health_open_journal : R.string.garden_health_choose_issue));
        action.setTextColor(ContextCompat.getColor(this, R.color.primary));
        action.setTextSize(12);
        action.setPadding(0, dp(6), 0, 0);
        text.addView(action);

        TextView score = new TextView(this);
        score.setText(getString(R.string.runtime_health_score_format, result.getScore()));
        score.setTextColor(ContextCompat.getColor(this, colorFor(result.getScore())));
        score.setTextSize(19);
        score.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(text);
        row.addView(score);
        card.addView(row);
        card.setOnClickListener(view -> openZoneIssue(zone, result));
        zoneList.addView(card);
    }

    private String issueSummary(GardenHealthZoneResult result) {
        if (result.getIssues().isEmpty()) return result.getReason();
        List<String> reasons = new ArrayList<>();
        for (GardenHealthIssue issue : result.getIssues()) {
            reasons.add(getString(R.string.garden_health_issue_deduction,
                    issue.getReason(), issue.getDeduction()));
        }
        return String.join(" · ", reasons);
    }

    private void openZoneIssue(GardenZone zone, GardenHealthZoneResult result) {
        List<GardenHealthIssue> issues = result.getIssues();
        if (issues.isEmpty()) {
            Intent intent = new Intent(this, PlantTimelineActivity.class);
            intent.putExtra("zone_id", zone.getZone_id());
            startActivity(intent);
        } else if (issues.size() == 1) {
            openIssue(zone, issues.get(0));
        } else {
            String[] choices = new String[issues.size()];
            for (int i = 0; i < issues.size(); i++) {
                GardenHealthIssue issue = issues.get(i);
                choices[i] = getString(R.string.garden_health_issue_deduction,
                        issue.getReason(), issue.getDeduction()) + "\n" + targetLabel(issue);
            }
            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.garden_health_issue_dialog_title,
                            com.alidogukan.avora.zones.PhysicalZoneIdentity.name(zone)))
                    .setItems(choices, (dialog, which) -> openIssue(zone, issues.get(which)))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }
    }

    private String targetLabel(GardenHealthIssue issue) {
        switch (issue.getTarget()) {
            case PLANT_ASSISTANT: return getString(R.string.garden_health_open_assistant);
            case FERTILIZATION: return getString(R.string.garden_health_open_fertilization);
            case SENSOR_SETTINGS: return getString(R.string.garden_health_open_sensor);
            case IRRIGATION_SETTINGS: return getString(R.string.garden_health_open_irrigation);
            default: throw new IllegalArgumentException("Unknown health issue target");
        }
    }

    private void openIssue(GardenZone zone, GardenHealthIssue issue) {
        Intent intent;
        switch (issue.getTarget()) {
            case PLANT_ASSISTANT:
                GardenPhoto record = PlantAssistantRecordResolver.find(
                        photoViewModel.load(), zone.getZone_id(),
                        issue.getSeasonId(), issue.getRecordId(), issue.getReason());
                if (record != null) {
                    intent = new Intent(this, JournalRecordDetailActivity.class);
                    intent.putExtra("title", record.getAnalysis_title());
                    intent.putExtra("detail", record.getAnalysis_context());
                    intent.putExtra("icon", getString(R.string.symbol_plant));
                    intent.putExtra("time", record.getCaptured_at_epoch());
                    intent.putExtra("zone_id", zone.getZone_id());
                    intent.putExtra("season_id", record.getSeason_id());
                    intent.putExtra("photo_path", record.getLocal_path());
                    intent.putExtra("photo_group_id", record.getRelated_application_id());
                    intent.putExtra("advice", record.getAnalysis_advice());
                } else {
                    intent = new Intent(this, PlantAssistantActivity.class);
                    intent.putExtra("zone_id", zone.getZone_id());
                    if (!issue.getSeasonId().isBlank()) intent.putExtra("season_id", issue.getSeasonId());
                }
                break;
            case FERTILIZATION:
                intent = new Intent(this, FertilizationZoneDetailActivity.class);
                intent.putExtra(FertilizationZoneDetailActivity.EXTRA_ZONE_ID, zone.getZone_id());
                break;
            case SENSOR_SETTINGS:
            case IRRIGATION_SETTINGS:
                intent = new Intent(this, ZoneDetailActivity.class);
                intent.putExtra(ZoneDetailActivity.EXTRA_ZONE_ID, zone.getZone_id());
                intent.putExtra(ZoneDetailActivity.EXTRA_INITIAL_SECTION,
                        issue.getTarget() == GardenHealthIssue.Target.SENSOR_SETTINGS
                                ? ZoneDetailActivity.SECTION_SENSOR : ZoneDetailActivity.SECTION_IRRIGATION);
                break;
            default: throw new IllegalArgumentException("Unknown health issue target");
        }
        // Opening a destination never saves settings or starts a hardware operation.
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (viewModel != null) render(viewModel.getGardenZones().getValue());
    }

    private int colorFor(int score) {
        return score >= 85 ? R.color.primary
                : score >= 65 ? R.color.warning : R.color.moistureLow;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

}
