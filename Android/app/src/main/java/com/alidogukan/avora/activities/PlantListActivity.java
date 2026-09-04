package com.alidogukan.avora.activities;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.journal.PlantListOrdering;
import com.alidogukan.avora.journal.PlantListOrdering.Entry;
import com.alidogukan.avora.models.GardenSeason;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.season.SeasonDisplayIdentity;
import com.alidogukan.avora.ui.PrimaryBottomNavigation;
import com.alidogukan.avora.viewmodels.PlantListViewModel;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Plant-focused entry point for each zone's journal and season history. */
public class PlantListActivity extends AppCompatActivity {
    private PlantListViewModel viewModel;
    private final List<GardenZone> latestZones = new ArrayList<>();
    private final List<GardenSeason> latestSeasons = new ArrayList<>();
    private final Handler freshnessHandler = new Handler(Looper.getMainLooper());
    private final Runnable freshnessRefresh = new Runnable() {
        @Override
        public void run() {
            if (!latestZones.isEmpty()) {
                // Connection freshness changes with time even when Firebase has no new event.
                render();
            }
            freshnessHandler.postDelayed(this, 15_000L);
        }
    };
    private LinearLayout list;
    private TextView empty;
    private TextView total;
    private TextView healthy;
    private TextView attention;
    private TextView waiting;
    private int sortMode;

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_plant_list);
        viewModel = new ViewModelProvider(this).get(PlantListViewModel.class);
        applyWindowInsets();
        findViewById(R.id.btnPlantsBack).setOnClickListener(view -> finish());
        PrimaryBottomNavigation.bind(this, PrimaryBottomNavigation.PLANTS);
        list = findViewById(R.id.layoutPlantList);
        empty = findViewById(R.id.txtPlantsEmpty);
        total = findViewById(R.id.txtPlantCount);
        healthy = findViewById(R.id.txtPlantHealthy);
        attention = findViewById(R.id.txtPlantAttention);
        waiting = findViewById(R.id.txtPlantWaiting);
        sortMode = PlantListOrdering.normalizeSortMode(
                viewModel.getSortMode(PlantListOrdering.SORT_SMART));
        findViewById(R.id.btnPlantSort).setOnClickListener(view -> showSortMenu());
        viewModel.getZones().observe(this, this::updateZones);
        viewModel.getSeasons().observe(this, values -> {
            latestSeasons.clear();
            if (values != null) latestSeasons.addAll(values);
            render();
        });
    }

    /** Keeps all journal content below the status bar / camera cutout on every phone. */
    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.plantListRoot), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    private void updateZones(List<GardenZone> zones) {
        latestZones.clear();
        latestZones.addAll(viewModel.activeZones(
                zones == null ? new ArrayList<>() : zones));
        render();
    }

    private void render() {
        List<Entry> visiblePlants = PlantListOrdering.entries(
                latestZones, latestSeasons, sortMode, System.currentTimeMillis() / 1000L,
                getResources().getConfiguration().getLocales().get(0));
        list.removeAllViews();
        boolean hasZones = !visiblePlants.isEmpty();
        empty.setVisibility(hasZones ? View.GONE : View.VISIBLE);
        if (!hasZones) {
            updateOverview(0, 0, 0, 0);
            return;
        }
        int healthyCount = 0;
        int attentionCount = 0;
        int waitingCount = 0;
        for (Entry plant : visiblePlants) {
            GardenZone zone = plant.zone;
            if (!hasCurrentSensorData(zone)) waitingCount++;
            else if (zone.getMoisture() < zone.getMoisture_limit()) attentionCount++;
            else healthyCount++;
            addZoneCard(plant);
        }
        updateOverview(visiblePlants.size(), healthyCount, attentionCount, waitingCount);
    }

    private void updateOverview(int count, int healthyCount, int attentionCount, int waitingCount) {
        total.setText(String.valueOf(count));
        healthy.setText(String.valueOf(healthyCount));
        attention.setText(String.valueOf(attentionCount));
        waiting.setText(String.valueOf(waitingCount));
    }

    private void showSortMenu() {
        String[] choices = {
                getString(R.string.runtime_sort_smart),
                getString(R.string.runtime_sort_needs_attention),
                getString(R.string.runtime_sort_moisture),
                getString(R.string.runtime_sort_recent),
                getString(R.string.runtime_sort_plant_name),
                getString(R.string.runtime_sort_area)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.runtime_plant_sort_title)
                .setSingleChoiceItems(choices, sortMode, (dialog, which) -> {
                    sortMode = which;
                    viewModel.setSortMode(sortMode);
                    render();
                    dialog.dismiss();
                })
                .show();
    }

    private void addZoneCard(Entry plant) {
        GardenZone zone = plant.zone;
        GardenSeason season = plant.season;
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(getColor(R.color.card));
        card.setRadius(dp(16));
        card.setCardElevation(dp(1));
        card.setStrokeColor(getColor(R.color.border));
        card.setStrokeWidth(dp(1));
        LinearLayout.LayoutParams outer = new LinearLayout.LayoutParams(-1, -2);
        outer.bottomMargin = dp(10);
        card.setLayoutParams(outer);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(10), dp(12));

        String cropEmoji = SeasonDisplayIdentity.emoji(season, zone);
        TextView emoji = text(cropEmoji.isBlank()
                ? getString(R.string.symbol_plant) : cropEmoji, 29, R.color.textPrimary);
        emoji.setGravity(Gravity.CENTER);
        emoji.setBackground(roundDrawable(getColor(R.color.surfaceSoft), getColor(R.color.border), dp(12)));
        row.addView(emoji, new LinearLayout.LayoutParams(dp(54), dp(54)));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(10), 0, 0, 0);
        String areaAndCrop = SeasonDisplayIdentity.areaCropName(season, zone);
        TextView title = text(areaAndCrop.isBlank()
                ? safeName(zone) : areaAndCrop, 16, R.color.textPrimary);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView status = text("●  " + status(zone), 12, statusColor(zone));
        TextView meta = text(getString(R.string.runtime_plant_moisture_meta, zone.getMoisture(), lastRecord(zone)), 11, R.color.textSecondary);
        details.addView(title);
        details.addView(status);
        details.addView(meta);
        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));

        TextView badge = text(badge(zone), 11, statusColor(zone));
        badge.setGravity(Gravity.CENTER);
        badge.setTypeface(null, android.graphics.Typeface.BOLD);
        badge.setBackground(roundDrawable(badgeBackground(zone), badgeBackground(zone), dp(12)));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(76), dp(28));
        badgeParams.setMargins(dp(4), 0, dp(4), 0);
        row.addView(badge, badgeParams);

        TextView arrow = text("›", 28, R.color.textSecondary);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(22), dp(48)));
        card.addView(row);
        card.setOnClickListener(view -> {
            Intent intent = new Intent(this, PlantTimelineActivity.class);
            intent.putExtra("zone_id", zone.getZone_id());
            if (season != null) {
                intent.putExtra(PlantTimelineActivity.EXTRA_SEASON_ID, season.getSeason_id());
            }
            startActivity(intent);
        });
        list.addView(card);
    }

    private String safeName(GardenZone zone) {
        return zone.getName() == null || zone.getName().isBlank() ? getString(R.string.runtime_garden_zone) : zone.getName();
    }

    private String status(GardenZone zone) {
        if (!zone.hasSensorData()) return getString(R.string.ai_zone_waiting);
        if (!hasCurrentSensorData(zone)) return getString(R.string.runtime_sensor_stale);
        if (zone.getMoisture() < zone.getMoisture_limit()) return getString(R.string.runtime_moisture_tracking_needed);
        return getString(R.string.runtime_season_tracking_active);
    }

    private String badge(GardenZone zone) {
        if (!zone.hasSensorData()) return getString(R.string.runtime_no_data);
        if (!hasCurrentSensorData(zone)) return getString(R.string.runtime_not_current);
        return zone.getMoisture() < zone.getMoisture_limit() ? getString(R.string.runtime_attention) : getString(R.string.plant_list_healthy);
    }

    private int statusColor(GardenZone zone) {
        if (!zone.hasSensorData()) return R.color.textSecondary;
        if (!hasCurrentSensorData(zone)) return R.color.warning;
        return zone.getMoisture() < zone.getMoisture_limit() ? R.color.warning : R.color.online;
    }

    private int badgeBackground(GardenZone zone) {
        if (!zone.hasSensorData()) return getColor(R.color.neutralBackground);
        if (!hasCurrentSensorData(zone)) return getColor(R.color.warningBackground);
        return zone.getMoisture() < zone.getMoisture_limit()
                ? getColor(R.color.warningBackground) : getColor(R.color.onlineBackground);
    }

    /** Mirrors the dashboard connection threshold; old cached moisture must not look live. */
    private boolean hasCurrentSensorData(GardenZone zone) {
        return PlantListOrdering.hasCurrentSensorData(zone, System.currentTimeMillis() / 1000L);
    }

    private String lastRecord(GardenZone zone) {
        if (!zone.hasSensorData()) return "Son veri yok";
        return "Son veri: " + new SimpleDateFormat("dd-MM-yyyy", Locale.forLanguageTag("tr-TR"))
                .format(new Date(zone.getUpdated_at_epoch() * 1000L));
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(getColor(color));
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

    @Override
    protected void onResume() {
        super.onResume();
        freshnessHandler.removeCallbacks(freshnessRefresh);
        freshnessHandler.post(freshnessRefresh);
    }

    @Override
    protected void onPause() {
        freshnessHandler.removeCallbacks(freshnessRefresh);
        super.onPause();
    }
}
