package com.alidogukan.avora.activities;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.models.GardenPhoto;
import com.alidogukan.avora.plantassistant.PlantGrowthAssessment;
import com.alidogukan.avora.ui.PrimaryBottomNavigation;
import com.alidogukan.avora.viewmodels.PlantGrowthTrackingViewModel;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/** Shows comparable plant growth assessments for one garden zone. */
public final class PlantGrowthTrackingActivity extends EdgeToEdgeActivity {
    public static final String EXTRA_SEASON_ID = "season_id";
    public static final String EXTRA_ZONE_ID = "zone_id";
    public static final String EXTRA_ZONE_LABEL = "zone_label";

    private PlantGrowthTrackingViewModel viewModel;
    private LinearLayout recordsLayout;
    private View summaryCard;
    private TextView emptyState;
    private TextView summaryScore;
    private TextView summaryTrend;
    private TextView summaryStage;
    private TextView recordCount;
    private String seasonId;
    private String zoneId;

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_plant_growth_tracking);
        PrimaryBottomNavigation.bind(this, PrimaryBottomNavigation.ASSISTANT);
        seasonId = safe(getIntent().getStringExtra(EXTRA_SEASON_ID));
        zoneId = safe(getIntent().getStringExtra(EXTRA_ZONE_ID));
        String zoneLabel = safe(getIntent().getStringExtra(EXTRA_ZONE_LABEL));
        viewModel = new ViewModelProvider(this).get(PlantGrowthTrackingViewModel.class);
        recordsLayout = findViewById(R.id.layoutGrowthRecords);
        summaryCard = findViewById(R.id.cardGrowthSummary);
        emptyState = findViewById(R.id.txtGrowthEmpty);
        summaryScore = findViewById(R.id.txtGrowthSummaryScore);
        summaryTrend = findViewById(R.id.txtGrowthSummaryTrend);
        summaryStage = findViewById(R.id.txtGrowthSummaryStage);
        recordCount = findViewById(R.id.txtGrowthRecordCount);
        findViewById(R.id.btnGrowthBack).setOnClickListener(view -> finish());
        ((TextView) findViewById(R.id.txtGrowthZone)).setText(
                zoneLabel.isEmpty() ? zoneId : zoneLabel);
        render(viewModel.recordsForZone(null, zoneId, seasonId));
        viewModel.getPhotoMetadata().observe(this,
                cloud -> render(viewModel.recordsForZone(cloud, zoneId, seasonId)));
    }

    private void render(List<GardenPhoto> records) {
        recordsLayout.removeAllViews();
        boolean empty = records == null || records.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        summaryCard.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) return;
        GardenPhoto latest = records.get(0);
        summaryScore.setText(getString(
                R.string.runtime_growth_score_format, latest.getGrowth_score()));
        summaryTrend.setText(trendLabel(latest.getGrowth_trend()));
        summaryTrend.setTextColor(trendColor(latest.getGrowth_trend()));
        summaryStage.setText(getString(R.string.runtime_growth_stage_format,
                valueOrDash(latest.getGrowth_stage())));
        recordCount.setText(getResources().getQuantityString(
                R.plurals.runtime_growth_record_count, records.size(), records.size()));
        LayoutInflater inflater = LayoutInflater.from(this);
        for (GardenPhoto photo : records) {
            View item = inflater.inflate(
                    R.layout.item_plant_growth_record, recordsLayout, false);
            bindRecord(item, photo);
            recordsLayout.addView(item);
        }
    }

    private void bindRecord(View item, GardenPhoto photo) {
        ImageView image = item.findViewById(R.id.imgGrowthRecord);
        File local = new File(safe(photo.getLocal_path()));
        if (local.isFile()) {
            image.setImageURI(Uri.fromFile(local));
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            image.setImageResource(R.drawable.ic_plant_assistant_logo);
            image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        }
        ((TextView) item.findViewById(R.id.txtGrowthRecordScore)).setText(
                getString(R.string.runtime_growth_score_format, photo.getGrowth_score()));
        ((TextView) item.findViewById(R.id.txtGrowthRecordDate)).setText(
                formatDate(photo.getCaptured_at_epoch()));
        ((TextView) item.findViewById(R.id.txtGrowthRecordStage)).setText(
                getString(R.string.runtime_growth_stage_format,
                        valueOrDash(photo.getGrowth_stage())));
        TextView trend = item.findViewById(R.id.txtGrowthRecordTrend);
        trend.setText(trendLabel(photo.getGrowth_trend()));
        trend.setTextColor(trendColor(photo.getGrowth_trend()));
        ((TextView) item.findViewById(R.id.txtGrowthRecordComparison)).setText(
                comparisonLabel(photo));
        TextView signals = item.findViewById(R.id.txtGrowthRecordSignals);
        String value = safe(photo.getGrowth_signals());
        signals.setText(value.isEmpty()
                ? getString(R.string.runtime_growth_no_signals)
                : getString(R.string.runtime_growth_signals_format, value));
    }

    private String comparisonLabel(GardenPhoto photo) {
        if (PlantGrowthAssessment.isFirstRecord(photo.getGrowth_trend())) {
            return getString(R.string.runtime_growth_first_record_detail);
        }
        int delta = photo.getGrowth_score_delta();
        return getResources().getQuantityString(R.plurals.runtime_growth_delta_format,
                Math.abs(delta), delta,
                formatDate(photo.getGrowth_previous_captured_at_epoch()));
    }

    private String trendLabel(String trend) {
        if (PlantGrowthAssessment.isImproving(trend)) {
            return getString(R.string.runtime_growth_trend_improving);
        }
        if (PlantGrowthAssessment.isDeclining(trend)) {
            return getString(R.string.runtime_growth_trend_declining);
        }
        if (PlantGrowthAssessment.isStable(trend)) {
            return getString(R.string.runtime_growth_trend_stable);
        }
        return getString(R.string.runtime_growth_trend_first);
    }

    private int trendColor(String trend) {
        int color = PlantGrowthAssessment.isImproving(trend)
                ? R.color.success
                : PlantGrowthAssessment.isDeclining(trend)
                ? R.color.error
                : PlantGrowthAssessment.isStable(trend)
                ? R.color.info : R.color.textSecondary;
        return ContextCompat.getColor(this, color);
    }

    private String formatDate(long epoch) {
        if (epoch <= 0L) return getString(R.string.runtime_unknown_date);
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(epoch * 1000L));
    }

    private String valueOrDash(String value) {
        String safe = safe(value);
        return safe.isEmpty() ? getString(R.string.runtime_not_available_short) : safe;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
