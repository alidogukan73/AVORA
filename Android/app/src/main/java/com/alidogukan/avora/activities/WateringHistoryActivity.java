package com.alidogukan.avora.activities;

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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alidogukan.avora.R;
import com.alidogukan.avora.adapters.WateringHistoryAdapter;
import com.alidogukan.avora.history.WateringHistoryPresentation;
import com.alidogukan.avora.models.GardenSeason;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.WateringHistory;
import com.alidogukan.avora.statistics.StatisticsCalculator;
import com.alidogukan.avora.viewmodels.WateringHistoryViewModel;
import com.alidogukan.avora.zones.ZoneChipRenderer;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;

import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

public class WateringHistoryActivity extends AppCompatActivity {
    private static final String STATE_ZONE = "history.selected_zone";
    private WateringHistoryViewModel viewModel;
    private WateringHistoryAdapter adapter;
    private RecyclerView recyclerHistory;
    private LinearLayout layoutLoading, layoutEmpty;
    private TextView txtHistoryEmptyDescription, txtHistoryDataState;
    private TextView txtHistoryStatCount, txtHistoryStatDuration, txtHistoryStatSuccess, txtHistoryStatDelta;
    private MaterialButton btnHistoryRetry;
    private ChipGroup chipGroupZones;
    private List<WateringHistory> allHistory;
    private List<GardenZone> latestZones;
    private List<GardenSeason> latestSeasons = Collections.emptyList();
    private String selectedZoneId = "";
    private String readError;
    private boolean loading = true;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_watering_history);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.historyRoot), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        if (savedInstanceState != null) selectedZoneId = savedInstanceState.getString(STATE_ZONE, "");
        initializeViews();
        adapter = new WateringHistoryAdapter();
        adapter.setStateRestorationPolicy(RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY);
        recyclerHistory.setLayoutManager(new LinearLayoutManager(this));
        recyclerHistory.setAdapter(adapter);
        recyclerHistory.setHasFixedSize(true);
        // Live zone/season metadata can refresh without changing the records.
        // Avoid RecyclerView's default cross-fade, which makes the whole
        // history appear to blink during those metadata updates.
        recyclerHistory.setItemAnimator(null);
        viewModel = new ViewModelProvider(this).get(WateringHistoryViewModel.class);
        findViewById(R.id.btnBack).setOnClickListener(view -> finish());
        btnHistoryRetry.setOnClickListener(view -> viewModel.retry());
        observeViewModel();
        renderHistory(false);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString(STATE_ZONE, selectedZoneId);
        super.onSaveInstanceState(state);
    }

    @Override protected void onResume() {
        super.onResume();
        // Reformat dates if the device time zone or locale changed while away.
        if (adapter != null) {
            adapter.setDisplayContext(latestZones, latestSeasons);
            renderHistory(false);
        }
    }

    private void initializeViews() {
        recyclerHistory = findViewById(R.id.recyclerHistory);
        layoutLoading = findViewById(R.id.layoutLoading);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        txtHistoryEmptyDescription = findViewById(R.id.txtHistoryEmptyDescription);
        chipGroupZones = findViewById(R.id.chipGroupZones);
        txtHistoryStatCount = findViewById(R.id.txtHistoryStatCount);
        txtHistoryStatDuration = findViewById(R.id.txtHistoryStatDuration);
        txtHistoryStatSuccess = findViewById(R.id.txtHistoryStatSuccess);
        txtHistoryStatDelta = findViewById(R.id.txtHistoryStatDelta);
        txtHistoryDataState = findViewById(R.id.txtHistoryDataState);
        btnHistoryRetry = findViewById(R.id.btnHistoryRetry);
    }

    private void observeViewModel() {
        viewModel.getHistory().observe(this, history -> {
            allHistory = history;
            renderHistory(false);
        });
        viewModel.getLoading().observe(this, value -> {
            loading = Boolean.TRUE.equals(value);
            renderHistory(false);
        });
        viewModel.getError().observe(this, message -> {
            readError = message;
            if (message != null && !message.isBlank()) {
                ((com.google.android.material.appbar.AppBarLayout) findViewById(R.id.historyAppBar))
                        .setExpanded(true);
            }
            renderHistory(false);
        });
        viewModel.getZones().observe(this, zones -> {
            latestZones = zones;
            String resolved = StatisticsCalculator.resolveSelectedZone(selectedZoneId, zones);
            boolean selectionChanged = !resolved.equals(selectedZoneId);
            selectedZoneId = resolved;
            adapter.setDisplayContext(latestZones, latestSeasons);
            ZoneChipRenderer.render(this, chipGroupZones, latestZones, selectedZoneId,
                    R.string.history_zone_all, zoneId -> {
                        selectedZoneId = zoneId;
                        renderHistory(true);
                    });
            renderHistory(selectionChanged);
        });
        viewModel.getSeasons().observe(this, seasons -> {
            latestSeasons = seasons == null ? Collections.emptyList() : seasons;
            adapter.setDisplayContext(latestZones, latestSeasons);
        });
    }

    private void renderHistory(boolean resetScroll) {
        List<WateringHistory> visible = allHistory == null ? null
                : WateringHistoryPresentation.select(allHistory, selectedZoneId, ZoneId.systemDefault());
        adapter.submitList(visible == null ? Collections.emptyList() : visible,
                resetScroll ? () -> recyclerHistory.scrollToPosition(0) : null);
        renderStatistics(visible);
        boolean failed = readError != null && !readError.isBlank();
        WateringHistoryPresentation.State state = WateringHistoryPresentation.state(visible, loading, failed);
        recyclerHistory.setVisibility(state == WateringHistoryPresentation.State.CONTENT ? View.VISIBLE : View.GONE);
        layoutLoading.setVisibility(state == WateringHistoryPresentation.State.LOADING ? View.VISIBLE : View.GONE);
        layoutEmpty.setVisibility(state == WateringHistoryPresentation.State.EMPTY ? View.VISIBLE : View.GONE);
        txtHistoryEmptyDescription.setText(selectedZoneId.isEmpty()
                ? R.string.history_empty_description : R.string.history_empty_zone_description);
        btnHistoryRetry.setVisibility(failed ? View.VISIBLE : View.GONE);
        btnHistoryRetry.setEnabled(!loading);
        String status;
        if (failed) {
            status = allHistory == null ? readError : getString(R.string.history_read_error_cached, readError);
        } else if (loading) {
            status = getString(R.string.history_loading);
        } else {
            status = getString(selectedZoneId.isEmpty()
                    ? R.string.history_all_record_scope : R.string.history_zone_record_scope);
        }
        txtHistoryDataState.setText(status);
        txtHistoryDataState.setTextColor(ContextCompat.getColor(this,
                failed ? R.color.warning : R.color.textSecondary));
    }

    private void renderStatistics(List<WateringHistory> items) {
        WateringHistoryPresentation.Summary summary = WateringHistoryPresentation.summarize(
                items, System.currentTimeMillis(), ZoneId.systemDefault());
        txtHistoryStatCount.setText(items == null ? getString(R.string.history_stat_count_unknown)
                : getString(R.string.history_stat_count, summary.totals.getTotalWaterings()));
        txtHistoryStatDuration.setText(getString(R.string.history_stat_duration,
                items == null ? "—" : formatTotalDuration(summary.totals.getTotalWateringSeconds())));
        txtHistoryStatSuccess.setText(summary.totals.getTotalWaterings() == 0
                ? getString(R.string.history_stat_success_unknown)
                : getString(R.string.history_stat_success, summary.totals.getSuccessRate()));
        txtHistoryStatDelta.setText(summary.averageDelta == null
                ? getString(R.string.history_stat_delta_unknown)
                : getString(R.string.history_stat_delta, summary.averageDelta));
    }

    private String formatTotalDuration(long seconds) {
        long value = Math.max(0, seconds);
        if (value >= 3600) return getString(R.string.duration_hours_minutes_format,
                value / 3600, (value % 3600) / 60);
        if (value >= 60) return getString(R.string.duration_minutes_seconds_format, value / 60, value % 60);
        return getString(R.string.duration_seconds_format, value);
    }
}
