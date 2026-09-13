package com.alidogukan.avora.activities;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.models.CropCatalogItem;
import com.alidogukan.avora.models.GardenSeason;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.SeasonOutcome;
import com.alidogukan.avora.models.SeedlingBatch;
import com.alidogukan.avora.seedling.SeedlingCropCatalog;
import com.alidogukan.avora.models.ZoneSeasonState;
import com.alidogukan.avora.season.SeasonDisplayIdentity;
import com.alidogukan.avora.season.ZoneAreaIdentity;
import com.alidogukan.avora.viewmodels.SeedlingViewModel;
import com.alidogukan.avora.viewmodels.SeasonManagementViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Explicit, non-destructive lifecycle management for every garden zone season. */
public final class SeasonManagementActivity extends EdgeToEdgeActivity {
    public static final String EXTRA_SEEDLING_BATCH_ID = "seedling_transfer_batch_id";
    private static final String STATE_EXPANDED_ARCHIVES = "expanded_archive_zones";

    private SeasonManagementViewModel viewModel;
    private SeedlingBatch transferBatch;
    private boolean transferMode;
    private LinearLayout inactiveZoneContainer;
    private TextView inactiveZonesTitle;
    private TextView inactiveZonesDescription;
    private final List<GardenZone> zones = new ArrayList<>();
    private final List<GardenSeason> seasons = new ArrayList<>();
    private final List<CropCatalogItem> cropCatalogItems = new ArrayList<>();
    private final Set<String> expandedArchiveZones = new LinkedHashSet<>();

    private LinearLayout zoneContainer;
    private TextView emptyView;
    private String lastRenderSignature = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_season_management);

        String transferBatchId = getIntent().getStringExtra(EXTRA_SEEDLING_BATCH_ID);
        transferMode = transferBatchId != null && !transferBatchId.isBlank();
        findViewById(R.id.btnBack).setOnClickListener(view -> finish());
        zoneContainer = findViewById(R.id.layoutSeasonZones);
        emptyView = findViewById(R.id.txtSeasonEmpty);
        inactiveZoneContainer = findViewById(R.id.layoutInactiveSeasonZones);
        inactiveZonesTitle = findViewById(R.id.txtInactiveSeasonZonesTitle);
        inactiveZonesDescription = findViewById(R.id.txtInactiveSeasonZonesDescription);
        viewModel = new ViewModelProvider(this).get(SeasonManagementViewModel.class);
        cropCatalogItems.addAll(viewModel.mergedCrops(null));
        if (transferMode) {
            ((TextView) findViewById(R.id.txtSeasonManagementTitle))
                    .setText(R.string.seedling_transfer_screen_title);
            ((TextView) findViewById(R.id.txtSeasonManagementSubtitle))
                    .setText(R.string.seedling_transfer_screen_subtitle);
            ((TextView) findViewById(R.id.txtSeasonManagementInfoTitle))
                    .setText(R.string.seedling_transfer_info_title);
            ((TextView) findViewById(R.id.txtSeasonManagementInfoBody))
                    .setText(R.string.seedling_transfer_info_body);
            SeedlingViewModel seedlingViewModel =
                    new ViewModelProvider(this).get(SeedlingViewModel.class);
            seedlingViewModel.getBatch(transferBatchId).observe(this, value -> {
                if (value == null) {
                    Toast.makeText(this, R.string.seedling_transfer_batch_missing,
                            Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                if (value.isTransferred()) {
                    transferBatch = value;
                    return;
                }
                if (!value.isActive() || !seedlingViewModel.isReady(value)) {
                    Toast.makeText(this, R.string.seedling_transfer_batch_not_ready,
                            Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                transferBatch = value;
                lastRenderSignature = "";
                renderIfChanged();
            });
        }
        if (savedInstanceState != null) {
            ArrayList<String> expanded = savedInstanceState.getStringArrayList(
                    STATE_EXPANDED_ARCHIVES);
            if (expanded != null) expandedArchiveZones.addAll(expanded);
        }

        viewModel.getZones().observe(this, values -> {
            zones.clear();
            if (values != null) zones.addAll(values);
            viewModel.synchronizeLegacySeasons(values);
            renderIfChanged();
        });
        viewModel.getSeasons().observe(this, values -> {
            seasons.clear();
            if (values != null) seasons.addAll(values);
            renderIfChanged();
        });
        viewModel.getCropCatalogItems().observe(this, values -> {
            cropCatalogItems.clear();
            cropCatalogItems.addAll(viewModel.mergedCrops(values));
        });
        viewModel.getBootstrapNotices().observe(this, event -> {
            if (event == null) return;
            SeasonManagementViewModel.BootstrapNotice notice = event.consume();
            if (notice == null) return;
            lastRenderSignature = "";
            if (notice.error == null) {
                Toast.makeText(this, notice.messageRes, Toast.LENGTH_LONG).show();
            } else {
                showError(notice.error, notice.messageRes);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!zones.isEmpty()) {
            lastRenderSignature = "";
            renderIfChanged();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putStringArrayList(
                STATE_EXPANDED_ARCHIVES,
                new ArrayList<>(expandedArchiveZones)
        );
        super.onSaveInstanceState(outState);
    }

    private void renderIfChanged() {
        String signature = renderSignature();
        if (signature.equals(lastRenderSignature)) return;
        lastRenderSignature = signature;
        render();
    }

    private void render() {
        zoneContainer.removeAllViews();
        inactiveZoneContainer.removeAllViews();

        List<GardenZone> activeZones = viewModel.activeZones(zones);
        List<GardenZone> inactiveZones =
                viewModel.inactiveArchiveZones(zones, seasons);

        emptyView.setVisibility(activeZones.isEmpty() ? View.VISIBLE : View.GONE);
        emptyView.setText(zones.isEmpty()
                ? R.string.season_management_no_zones
                : R.string.season_management_no_active_zones);
        for (GardenZone zone : activeZones) {
            zoneContainer.addView(createZoneCard(zone));
        }

        int inactiveVisibility = !transferMode && !inactiveZones.isEmpty()
                ? View.VISIBLE : View.GONE;
        inactiveZonesTitle.setVisibility(inactiveVisibility);
        inactiveZonesDescription.setVisibility(inactiveVisibility);
        inactiveZoneContainer.setVisibility(inactiveVisibility);
        if (!inactiveZones.isEmpty()) {
            inactiveZonesTitle.setText(getString(
                    R.string.season_management_inactive_title,
                    inactiveZones.size()
            ));
            for (GardenZone zone : inactiveZones) {
                inactiveZoneContainer.addView(createInactiveZoneCard(zone));
            }
        }
    }

    /**
     * The garden-zones listener also receives frequent sensor measurements. None of those
     * measurements changes this screen, so they must not recreate every MaterialButton.
     */
    private String renderSignature() {
        StringBuilder value = new StringBuilder(256);
        for (GardenZone zone : zones) {
            if (zone == null) continue;
            ZoneSeasonState state = zone.getSeason();
            value.append("Z|")
                    .append(safe(zone.getZone_id())).append('|')
                    .append(safe(zone.getArea_name())).append('|')
                    .append(safe(zone.getName())).append('|')
                    .append(safe(zone.getEmoji())).append('|')
                    .append(zone.isEnabled()).append('|')
                    .append(safe(zone.getLifecycle_status())).append('|')
                    .append(zone.getFertilization() == null
                            ? ""
                            : safe(zone.getFertilization().getGrowth_stage()))
                    .append('|');
            if (state == null) {
                value.append("NO_SEASON");
            } else {
                value.append(safe(state.getStatus())).append('|')
                        .append(safe(state.getActive_season_id())).append('|')
                        .append(safe(state.getLabel())).append('|')
                        .append(state.getStarted_at_epoch()).append('|')
                        .append(state.getEnded_at_epoch());
            }
            value.append(';');
        }

        List<GardenSeason> stableSeasons = new ArrayList<>(seasons);
        stableSeasons.sort(Comparator
                .comparing((GardenSeason season) -> safe(season.getZone_id()))
                .thenComparing(season -> safe(season.getSeason_id())));
        for (GardenSeason season : stableSeasons) {
            if (season == null) continue;
            value.append("S|")
                    .append(safe(season.getZone_id())).append('|')
                    .append(safe(season.getSeason_id())).append('|')
                    .append(safe(season.getZone_name())).append('|')
                    .append(safe(season.getPlant_type())).append('|')
                    .append(safe(season.getEmoji())).append('|')
                    .append(safe(season.getStatus())).append('|')
                    .append(safe(season.getResult())).append('|')
                    .append(season.getEnded_at_epoch()).append('|')
                    .append(season.getWatering_count()).append('|')
                    .append(season.getFertilizer_application_count()).append('|')
                    .append(season.getJournal_event_count()).append('|')
                    .append(season.getManual_journal_event_count()).append('|')
                    .append(season.getPhoto_count()).append('|')
                    .append(season.getPlant_assistant_analysis_count()).append(';');
        }
        if (transferMode) {
            value.append("T|");
            if (transferBatch != null) {
                value.append(transferBatch.getBatch_id()).append('|')
                        .append(transferBatch.getStatus()).append('|')
                        .append(transferBatch.getStage());
            }
        }
        return value.toString();
    }

    private View createZoneCard(GardenZone zone) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card));
        card.setStrokeColor(ContextCompat.getColor(this, R.color.border));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(20));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.topMargin = dp(10);
        card.setLayoutParams(cardParams);

        LinearLayout content = vertical();
        content.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.addView(content);

        LinearLayout heading = horizontal();
        TextView name = text(zoneLabel(zone), 18, R.color.textPrimary, Typeface.BOLD);
        heading.addView(name, weighted());
        ZoneSeasonState state = zone.getSeason();
        List<GardenSeason> history = seasonsFor(zone);
        List<GardenSeason> activeSeasons = activeSeasons(history);
        boolean preparing = state == null || blank(state.getStatus());
        boolean notStarted = viewModel.isSeasonNotStarted(state);
        boolean active = !activeSeasons.isEmpty();
        int badgeText = preparing
                ? R.string.season_status_preparing
                : (notStarted
                        ? R.string.season_status_not_started
                        : (active ? R.string.season_status_active : R.string.season_status_closed));
        int badgeColor = active ? R.color.online : R.color.textSecondary;
        TextView badge = text(
                getString(badgeText),
                10,
                badgeColor,
                Typeface.BOLD
        );
        badge.setPadding(dp(10), dp(5), dp(10), dp(5));
        heading.addView(badge);
        content.addView(heading);

        TextView label = text(currentSeasonSummary(zone), 13, R.color.textSecondary, Typeface.NORMAL);
        label.setPadding(0, dp(7), 0, 0);
        content.addView(label);

        if (!transferMode) {
            content.addView(createHistoryDropdown(zone, history, false));
            for (GardenSeason season : activeSeasons) {
                content.addView(createActiveSeasonRow(zone, season));
            }
        }

        MaterialButton action = new MaterialButton(
                this,
                null,
                com.google.android.material.R.attr.materialButtonStyle
        );
        action.setText(transferMode
                ? R.string.seedling_transfer_here
                : (preparing
                        ? R.string.season_prepare_action
                        : (active ? R.string.season_add_crop_action
                                : R.string.season_start_action)));
        boolean actionEnabled = !preparing && (!transferMode || transferBatch != null);
        action.setEnabled(actionEnabled);
        action.setAlpha(actionEnabled ? 1f : 0.65f);
        action.setAllCaps(false);
        action.setTextSize(14);
        action.setTypeface(action.getTypeface(), Typeface.BOLD);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
        );
        actionParams.topMargin = dp(13);
        action.setLayoutParams(actionParams);
        action.setOnClickListener(view -> {
            if (preparing) return;
            showStartDialog(zone, action);
        });
        content.addView(action);

        MaterialButton archive = new MaterialButton(
                this,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
        );
        archive.setText(R.string.season_archive_action);
        archive.setAllCaps(false);
        archive.setTextSize(14);
        archive.setTypeface(archive.getTypeface(), Typeface.BOLD);
        LinearLayout.LayoutParams archiveParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
        );
        archiveParams.topMargin = dp(8);
        archive.setLayoutParams(archiveParams);
        GardenSeason latestCompleted = latestCompletedSeason(history);
        if (!transferMode && latestCompleted != null) {
            archive.setOnClickListener(view -> openSeasonArchive(zone, latestCompleted));
            content.addView(archive);
        }
        return card;
    }

    private View createActiveSeasonRow(GardenZone zone, GardenSeason season) {
        LinearLayout row = horizontal();
        row.setPadding(0, dp(10), 0, 0);
        String crop = archiveZoneLabel(zone, season);
        TextView details = text(
                getString(
                        R.string.season_active_crop_summary,
                        crop,
                        formatEpoch(season.getStarted_at_epoch())
                ),
                13,
                R.color.textPrimary,
                Typeface.BOLD
        );
        row.addView(details, weighted());

        MaterialButton delete = new MaterialButton(
                this,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
        );
        delete.setText(R.string.season_delete_empty_action);
        delete.setAllCaps(false);
        delete.setTextSize(12);
        delete.setVisibility(View.GONE);
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(44)
        );
        deleteParams.setMarginStart(dp(6));
        delete.setLayoutParams(deleteParams);
        delete.setOnClickListener(view -> showCancelNewSeasonDialog(
                zone,
                season,
                delete
        ));
        row.addView(delete);

        MaterialButton close = new MaterialButton(
                this,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
        );
        close.setText(R.string.season_close_short_action);
        close.setAllCaps(false);
        close.setTextSize(12);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(44)
        );
        closeParams.setMarginStart(dp(6));
        close.setLayoutParams(closeParams);
        close.setOnClickListener(view -> showCloseDialog(zone, season, close));
        row.addView(close);

        String expectedSeasonId = safe(season.getSeason_id());
        viewModel.canCancelNewSeason(zone.getZone_id(), expectedSeasonId)
                .addOnSuccessListener(canCancel -> {
                    ZoneSeasonState latest = zone.getSeason();
                    boolean stillActive = latest != null
                            && latest.isSeasonActive(expectedSeasonId);
                    delete.setVisibility(
                            Boolean.TRUE.equals(canCancel) && stillActive
                                    ? View.VISIBLE
                                    : View.GONE
                    );
                })
                .addOnFailureListener(error -> delete.setVisibility(View.GONE));
        return row;
    }

    private View createInactiveZoneCard(GardenZone zone) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card));
        card.setStrokeColor(ContextCompat.getColor(this, R.color.border));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(20));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.topMargin = dp(10);
        card.setLayoutParams(cardParams);

        LinearLayout content = vertical();
        content.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.addView(content);
        LinearLayout heading = horizontal();
        heading.addView(text(zoneLabel(zone), 18, R.color.textPrimary, Typeface.BOLD), weighted());
        TextView badge = text(
                getString(R.string.season_status_zone_inactive),
                10,
                R.color.textSecondary,
                Typeface.BOLD
        );
        badge.setPadding(dp(10), dp(5), dp(10), dp(5));
        heading.addView(badge);
        content.addView(heading);

        TextView label = text(currentSeasonSummary(zone), 13, R.color.textSecondary, Typeface.NORMAL);
        label.setPadding(0, dp(7), 0, 0);
        content.addView(label);

        List<GardenSeason> history = seasonsFor(zone);
        content.addView(createHistoryDropdown(zone, history, true));

        MaterialButton archive = new MaterialButton(
                this,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
        );
        archive.setText(R.string.season_archive_action);
        archive.setAllCaps(false);
        archive.setTextSize(14);
        archive.setTypeface(archive.getTypeface(), Typeface.BOLD);
        LinearLayout.LayoutParams archiveParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
        );
        archiveParams.topMargin = dp(13);
        archive.setLayoutParams(archiveParams);
        GardenSeason latestCompleted = latestCompletedSeason(history, true);
        if (latestCompleted != null) {
            archive.setOnClickListener(view -> openSeasonArchive(zone, latestCompleted));
            content.addView(archive);
        }

        MaterialButton reactivate = new MaterialButton(this);
        reactivate.setText(R.string.season_reactivate_zone_action);
        reactivate.setAllCaps(false);
        reactivate.setTextSize(14);
        reactivate.setTypeface(reactivate.getTypeface(), Typeface.BOLD);
        LinearLayout.LayoutParams reactivateParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
        );
        reactivateParams.topMargin = dp(8);
        reactivate.setLayoutParams(reactivateParams);
        reactivate.setOnClickListener(view -> startActivity(
                new Intent(this, ZoneManagementActivity.class)
        ));
        content.addView(reactivate);
        return card;
    }

    private void openSeasonArchive(GardenZone zone, GardenSeason latest) {
        Intent intent = new Intent(this, PlantTimelineActivity.class);
        intent.putExtra(PlantTimelineActivity.EXTRA_ZONE_ID, zone.getZone_id());
        if (!blank(latest.getSeason_id())) {
            intent.putExtra(PlantTimelineActivity.EXTRA_SEASON_ID, latest.getSeason_id());
        }
        startActivity(intent);
    }

    private String currentSeasonSummary(GardenZone zone) {
        List<GardenSeason> active = activeSeasons(seasonsFor(zone));
        if (!active.isEmpty()) {
            return getResources().getQuantityString(
                    R.plurals.season_active_crop_count,
                    active.size(),
                    active.size()
            );
        }
        ZoneSeasonState state = zone.getSeason();
        if (state == null || blank(state.getStatus())) {
            return getString(R.string.season_management_preparing);
        }
        return getString(R.string.season_not_started_summary);
    }

    private String historySummary(List<GardenSeason> history) {
        return historySummary(history, false);
    }

    private String historySummary(List<GardenSeason> history, boolean requireRecordedActivity) {
        List<GardenSeason> completed =
                viewModel.completedArchives(history, requireRecordedActivity);
        int closed = completed.size();
        GardenSeason latestClosed = null;
        for (GardenSeason season : completed) {
            if (latestClosed == null || season.getEnded_at_epoch() > latestClosed.getEnded_at_epoch()) {
                latestClosed = season;
            }
        }
        if (closed == 0) return getString(R.string.season_history_empty);
        String latest = latestClosed == null || blank(latestClosed.getResult())
                ? getString(R.string.season_result_not_entered)
                : latestClosed.getResult();
        String historyText = getResources().getQuantityString(
                R.plurals.season_history_count,
                closed,
                closed,
                latest
        );
        String crops = SeasonDisplayIdentity.archiveCropSummary(completed, 2);
        return crops.isBlank()
                ? historyText
                : getString(R.string.season_history_with_crops, crops, historyText);
    }

    private View createHistoryDropdown(
            GardenZone zone,
            List<GardenSeason> history,
            boolean requireRecordedActivity
    ) {
        List<GardenSeason> completed =
                viewModel.completedArchives(history, requireRecordedActivity);
        if (completed.isEmpty()) {
            TextView empty = text(
                    getString(R.string.season_history_empty),
                    12,
                    R.color.textSecondary,
                    Typeface.NORMAL
            );
            empty.setPadding(0, dp(8), 0, 0);
            return empty;
        }

        String areaKey = ZoneAreaIdentity.effective(zone);
        boolean initiallyExpanded = expandedArchiveZones.contains(areaKey);
        MaterialCardView box = new MaterialCardView(this);
        box.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surfaceSoft));
        box.setStrokeColor(ContextCompat.getColor(
                this,
                initiallyExpanded ? R.color.online : R.color.border
        ));
        box.setStrokeWidth(dp(1));
        box.setRadius(dp(13));
        LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        boxParams.topMargin = dp(8);
        box.setLayoutParams(boxParams);

        LinearLayout body = vertical();
        box.addView(body);
        LinearLayout header = horizontal();
        header.setMinimumHeight(dp(42));
        header.setPadding(dp(12), dp(7), dp(10), dp(7));
        TextView summary = text(
                historySummary(history, requireRecordedActivity),
                12,
                R.color.textSecondary,
                Typeface.NORMAL
        );
        header.addView(summary, weighted());
        TextView arrow = text(
                getString(initiallyExpanded
                        ? R.string.season_history_arrow_up
                        : R.string.season_history_arrow_down),
                17,
                R.color.online,
                Typeface.BOLD
        );
        arrow.setPadding(dp(8), 0, 0, 0);
        header.addView(arrow);
        body.addView(header);

        LinearLayout list = vertical();
        list.setPadding(dp(12), 0, dp(12), dp(7));
        list.setVisibility(initiallyExpanded ? View.VISIBLE : View.GONE);
        List<GardenSeason> newestFirst = new ArrayList<>(completed);
        newestFirst.sort(Comparator
                .comparingLong(GardenSeason::getEnded_at_epoch)
                .reversed()
                .thenComparing(GardenSeason::getSeason_id));
        for (int i = 0; i < newestFirst.size(); i++) {
            if (i > 0) {
                View divider = new View(this);
                divider.setBackgroundColor(ContextCompat.getColor(this, R.color.border));
                divider.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(1)
                ));
                list.addView(divider);
            }
            list.addView(createArchivedSeasonRow(zone, newestFirst.get(i)));
        }
        body.addView(list);

        updateHistoryDropdownAccessibility(
                header,
                summary.getText().toString(),
                initiallyExpanded
        );
        header.setClickable(true);
        header.setFocusable(true);
        header.setOnClickListener(view -> {
            boolean expand = list.getVisibility() != View.VISIBLE;
            list.setVisibility(expand ? View.VISIBLE : View.GONE);
            arrow.setText(expand
                    ? R.string.season_history_arrow_up
                    : R.string.season_history_arrow_down);
            box.setStrokeColor(ContextCompat.getColor(
                    this,
                    expand ? R.color.online : R.color.border
            ));
            if (expand) {
                expandedArchiveZones.add(areaKey);
            } else {
                expandedArchiveZones.remove(areaKey);
            }
            updateHistoryDropdownAccessibility(
                    header,
                    summary.getText().toString(),
                    expand
            );
        });
        return box;
    }

    private View createArchivedSeasonRow(GardenZone zone, GardenSeason season) {
        LinearLayout row = horizontal();
        row.setMinimumHeight(dp(58));
        row.setPadding(0, dp(7), 0, dp(7));

        LinearLayout labels = vertical();
        String crop = archivedCropLabel(season);
        labels.addView(text(crop, 13, R.color.textPrimary, Typeface.BOLD));
        String result = blank(season.getResult())
                ? getString(R.string.season_result_not_entered)
                : season.getResult();
        labels.addView(text(
                getString(
                        R.string.season_archive_row_details,
                        formatEpoch(season.getStarted_at_epoch()),
                        formatEpoch(season.getEnded_at_epoch()),
                        result
                ),
                11,
                R.color.textSecondary,
                Typeface.NORMAL
        ));
        row.addView(labels, weighted());
        TextView arrow = text(
                getString(R.string.symbol_chevron_right),
                22,
                R.color.textSecondary,
                Typeface.NORMAL
        );
        arrow.setPadding(dp(8), 0, 0, 0);
        row.addView(arrow);
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(getString(
                R.string.season_archive_open_description,
                crop
        ));
        row.setOnClickListener(view -> openSeasonArchive(zone, season));
        return row;
    }

    private String archivedCropLabel(GardenSeason season) {
        String crop = SeasonDisplayIdentity.name(season, null);
        if (blank(crop)) crop = getString(R.string.season_archive_crop_unknown);
        String icon = SeasonDisplayIdentity.emoji(season, null);
        return blank(icon) ? crop : icon + " " + crop;
    }

    private void updateHistoryDropdownAccessibility(
            View header,
            String summary,
            boolean expanded
    ) {
        header.setContentDescription(getString(
                expanded
                        ? R.string.season_history_collapse_description
                        : R.string.season_history_expand_description,
                summary
        ));
    }

    private GardenSeason latestCompletedSeason(List<GardenSeason> history) {
        return latestCompletedSeason(history, false);
    }

    private GardenSeason latestCompletedSeason(
            List<GardenSeason> history,
            boolean requireRecordedActivity) {
        GardenSeason latest = null;
        for (GardenSeason season :
                viewModel.completedArchives(history, requireRecordedActivity)) {
            if (latest == null || season.getEnded_at_epoch() > latest.getEnded_at_epoch()) {
                latest = season;
            }
        }
        return latest;
    }

    private List<GardenSeason> seasonsFor(GardenZone zone) {
        return viewModel.visibleSeasonsFor(zone, seasons);
    }

    private static List<GardenSeason> activeSeasons(List<GardenSeason> history) {
        List<GardenSeason> result = new ArrayList<>();
        if (history == null) return result;
        for (GardenSeason season : history) {
            if (season != null && com.alidogukan.avora.models.SeasonStatus.isActive(
                    season.getStatus())) result.add(season);
        }
        return result;
    }

    private void showStartDialog(GardenZone zone, MaterialButton action) {
        if (transferMode) {
            showSeedlingTransferDialog(zone, action);
            return;
        }
        LinearLayout form = dialogForm();

        Spinner crop = spinnerWithLabel(form, R.string.season_start_crop_label);
        List<CropCatalogItem> cropChoices = new ArrayList<>(cropCatalogItems.isEmpty()
                ? viewModel.mergedCrops(null)
                : cropCatalogItems);
        List<String> cropLabels = new ArrayList<>();
        for (CropCatalogItem item : cropChoices) cropLabels.add(item.toString());
        crop.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                cropLabels
        ));
        int initialCropPosition = cropPosition(
                cropChoices,
                zone.getPlant_type(),
                zone.getName()
        );
        crop.setSelection(initialCropPosition);
        EditText date = field(R.string.season_start_planting_date_hint, InputType.TYPE_CLASS_DATETIME);
        date.setText(new SimpleDateFormat(getString(R.string.date_format_dmy), Locale.getDefault()).format(new Date()));
        date.setFocusable(false);
        date.setClickable(true);
        date.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.ic_menu_my_calendar, 0);
        date.setCompoundDrawablePadding(dp(8));
        date.setOnClickListener(view -> showDatePicker(date));
        EditText label = field(R.string.season_start_label_hint, InputType.TYPE_CLASS_TEXT);
        label.setText(getString(
                R.string.season_default_label,
                new SimpleDateFormat(getString(R.string.date_format_year), Locale.getDefault()).format(new Date())
        ));
        form.addView(date);
        form.addView(label);

        Spinner stage = spinnerWithLabel(form, R.string.season_start_stage_label);
        stage.setAdapter(ArrayAdapter.createFromResource(
                this,
                R.array.season_growth_stage_labels,
                android.R.layout.simple_spinner_dropdown_item
        ));

        String valve = blank(zone.getValve_id())
                ? getString(R.string.season_start_no_valve)
                : zone.getValve_id();
        TextView valveNote = text(
                getString(R.string.season_start_valve_note, valve),
                12,
                R.color.textSecondary,
                Typeface.NORMAL
        );
        valveNote.setPadding(0, dp(2), 0, dp(8));
        form.addView(valveNote);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.season_start_dialog_title, com.alidogukan.avora.zones.PhysicalZoneIdentity.name(zone)))
                .setMessage(R.string.season_shared_operations_message)
                .setView(scroll)
                .setNegativeButton(R.string.season_cancel, null)
                .setPositiveButton(R.string.season_start_action, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    if (!validDate(value(date))) {
                        Toast.makeText(this, R.string.season_invalid_date, Toast.LENGTH_LONG).show();
                        return;
                    }
                    int cropIndex = Math.min(crop.getSelectedItemPosition(), cropChoices.size() - 1);
                    CropCatalogItem selectedCrop = cropChoices.get(cropIndex);
                    String cropName = selectedCrop.getName();
                    if (blank(cropName)) {
                        Toast.makeText(this, R.string.season_crop_required, Toast.LENGTH_LONG).show();
                        return;
                    }
                    String plantType = selectedCrop.getPlant_type();
                    String emoji = selectedCrop.getEmoji();

                    String[] codes = getResources().getStringArray(R.array.season_growth_stage_codes);
                    String code = codes[Math.min(stage.getSelectedItemPosition(), codes.length - 1)];
                    setBusy(action, true);
                    dialog.dismiss();
                    viewModel.startSeason(
                                    zone,
                                    value(date),
                                    code,
                                    value(label),
                                    cropName,
                                    plantType,
                                    emoji,
                                    selectedCrop.getIdeal_moisture_min(),
                                    selectedCrop.getIdeal_moisture_max()
                            )
                            .addOnSuccessListener(result -> {
                                setBusy(action, false);
                                Toast.makeText(this, R.string.season_started_success, Toast.LENGTH_LONG).show();
                            })
                            .addOnFailureListener(error -> {
                                setBusy(action, false);
                                showError(error, R.string.season_start_failed);
                            });
                }));
        dialog.show();
    }

    private void showSeedlingTransferDialog(GardenZone zone, MaterialButton action) {
        SeedlingBatch target = transferBatch;
        if (target == null || !target.isActive()) return;

        LinearLayout form = dialogForm();
        String variety = blank(target.getVariety())
                ? getString(R.string.seedling_transfer_variety_unspecified)
                : target.getVariety();
        TextView summary = text(
                getString(
                        R.string.seedling_transfer_summary,
                        target.getEmoji(),
                        target.getPlant_type(),
                        variety,
                        target.getHealthy_count()
                ),
                14,
                R.color.textPrimary,
                Typeface.NORMAL
        );
        summary.setPadding(0, 0, 0, dp(12));
        form.addView(summary);

        EditText date = field(
                R.string.season_start_planting_date_hint,
                InputType.TYPE_CLASS_DATETIME
        );
        date.setText(new SimpleDateFormat(
                getString(R.string.date_format_dmy),
                Locale.getDefault()
        ).format(new Date()));
        date.setFocusable(false);
        date.setClickable(true);
        date.setCompoundDrawablesWithIntrinsicBounds(
                0, 0, android.R.drawable.ic_menu_my_calendar, 0);
        date.setCompoundDrawablePadding(dp(8));
        date.setOnClickListener(view -> showDatePicker(date));

        EditText label = field(
                R.string.season_start_label_hint,
                InputType.TYPE_CLASS_TEXT
        );
        label.setText(getString(
                R.string.seedling_transfer_default_label,
                new SimpleDateFormat(
                        getString(R.string.date_format_year),
                        Locale.getDefault()
                ).format(new Date()),
                target.displayName()
        ));
        form.addView(date);
        form.addView(label);

        CropCatalogItem crop = cropFor(target);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(
                        R.string.seedling_transfer_dialog_title,
                        com.alidogukan.avora.zones.PhysicalZoneIdentity.name(zone)
                ))
                .setMessage(R.string.seedling_transfer_dialog_message)
                .setView(scroll)
                .setNegativeButton(R.string.season_cancel, null)
                .setPositiveButton(R.string.seedling_transfer_confirm, null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
                        .setOnClickListener(view -> {
                            if (!validDate(value(date))) {
                                Toast.makeText(this, R.string.season_invalid_date,
                                        Toast.LENGTH_LONG).show();
                                return;
                            }
                            setBusy(action, true);
                            dialog.dismiss();
                            viewModel.transferSeedlingToSeason(
                                            zone,
                                            value(date),
                                            value(label),
                                            crop,
                                            target
                                    )
                                    .addOnSuccessListener(result -> {
                                        setBusy(action, false);
                                        Toast.makeText(
                                                this,
                                                R.string.seedling_transfer_success,
                                                Toast.LENGTH_LONG
                                        ).show();
                                        finish();
                                    })
                                    .addOnFailureListener(error -> {
                                        setBusy(action, false);
                                        showError(
                                                error,
                                                R.string.seedling_transfer_failed
                                        );
                                    });
                        }));
        dialog.show();
    }

    private CropCatalogItem cropFor(SeedlingBatch batch) {
        if (batch == null) return null;
        String cropId = safe(batch.getCrop_id());
        if (!cropId.isBlank()) {
            for (CropCatalogItem item : cropCatalogItems) {
                if (item != null && cropId.equals(safe(item.getCrop_id()))) return item;
            }
        }
        String batchKey = SeedlingCropCatalog.cropKeyForPlant(batch.getPlant_type());
        for (CropCatalogItem item : cropCatalogItems) {
            if (item == null) continue;
            String catalogValue = safe(item.getPlant_type()).isBlank()
                    ? item.getName() : item.getPlant_type();
            if (batchKey.equals(SeedlingCropCatalog.cropKeyForPlant(catalogValue))) {
                return item;
            }
        }
        return null;
    }

    private Spinner spinnerWithLabel(LinearLayout form, int labelRes) {
        TextView label = text(getString(labelRes), 13, R.color.textSecondary, Typeface.BOLD);
        label.setPadding(0, dp(5), 0, dp(3));
        form.addView(label);
        Spinner spinner = new Spinner(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
        );
        params.bottomMargin = dp(10);
        spinner.setLayoutParams(params);
        form.addView(spinner);
        return spinner;
    }

    private static int cropPosition(
            List<CropCatalogItem> choices,
            String currentCode,
            String currentName
    ) {
        String expected = safe(currentCode).trim();
        String expectedName = safe(currentName).trim();
        for (int i = 0; i < choices.size(); i++) {
            CropCatalogItem item = choices.get(i);
            if (safe(item.getPlant_type()).equalsIgnoreCase(expected)
                    || safe(item.getCrop_id()).equalsIgnoreCase(expected)
                    || safe(item.getName()).equalsIgnoreCase(expectedName)) {
                return i;
            }
        }
        return 0;
    }

    private void showCancelNewSeasonDialog(
            GardenZone zone,
            GardenSeason season,
            MaterialButton action
    ) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(
                        R.string.season_delete_empty_dialog_title,
                        archiveZoneLabel(zone, season)
                ))
                .setMessage(R.string.season_delete_empty_dialog_message)
                .setNegativeButton(R.string.season_cancel, null)
                .setPositiveButton(R.string.season_delete_empty_confirm, (dialog, which) -> {
                    setBusy(action, true);
                    viewModel.cancelNewSeason(zone.getZone_id(), season.getSeason_id())
                            .addOnSuccessListener(ignored -> {
                                setBusy(action, false);
                                lastRenderSignature = "";
                                renderIfChanged();
                                Toast.makeText(
                                        this,
                                        R.string.season_delete_empty_success,
                                        Toast.LENGTH_LONG
                                ).show();
                            })
                            .addOnFailureListener(error -> {
                                setBusy(action, false);
                                showError(error, R.string.season_delete_empty_failed);
                            });
                })
                .show();
    }

    private void showCloseDialog(GardenZone zone, GardenSeason season, MaterialButton action) {
        LinearLayout form = dialogForm();
        Spinner result = new Spinner(this);
        ArrayAdapter<CharSequence> resultAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.season_result_labels,
                android.R.layout.simple_spinner_dropdown_item
        );
        result.setAdapter(resultAdapter);
        EditText harvest = field(R.string.season_harvest_amount_hint, InputType.TYPE_CLASS_TEXT);
        EditText yield = field(R.string.season_yield_note_hint, multiline());
        EditText issues = field(R.string.season_issues_hint, multiline());
        EditText practices = field(R.string.season_practices_hint, multiline());
        EditText next = field(R.string.season_next_note_hint, multiline());
        form.addView(result);
        form.addView(harvest);
        form.addView(yield);
        form.addView(issues);
        form.addView(practices);
        form.addView(next);

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.season_close_dialog_title, archiveZoneLabel(zone, season)))
                .setMessage(R.string.season_close_dialog_message)
                .setView(form)
                .setNegativeButton(R.string.season_cancel, null)
                .setPositiveButton(R.string.season_close_action, (dialog, which) -> {
                    SeasonOutcome outcome = new SeasonOutcome();
                    outcome.setId(season.getSeason_id());
                    outcome.setSeason_id(season.getSeason_id());
                    outcome.setZone_id(zone.getZone_id());
                    outcome.setResult(String.valueOf(result.getSelectedItem()));
                    outcome.setHarvest_amount(value(harvest));
                    outcome.setYield_note(value(yield));
                    outcome.setIssues_note(value(issues));
                    outcome.setSuccessful_practices(value(practices));
                    outcome.setNext_season_note(value(next));
                    outcome.setRecorded_at_epoch(System.currentTimeMillis() / 1000L);
                    setBusy(action, true);
                    viewModel.closeSeason(zone, season, outcome)
                            .addOnSuccessListener(ignored -> {
                                setBusy(action, false);
                                Toast.makeText(this, R.string.season_closed_success, Toast.LENGTH_LONG).show();
                            })
                            .addOnFailureListener(error -> {
                                setBusy(action, false);
                                showError(error, R.string.season_close_failed);
                            });
                })
                .show();
    }

    private void setBusy(MaterialButton action, boolean busy) {
        if (busy) {
            if (action.getTag() == null) {
                action.setTag(action.getText());
            }
            action.setEnabled(false);
            action.setText(R.string.season_operation_wait);
            return;
        }

        action.setEnabled(true);
        Object originalText = action.getTag();
        if (originalText instanceof CharSequence) {
            action.setText((CharSequence) originalText);
        }
        action.setTag(null);
    }

    private LinearLayout dialogForm() {
        LinearLayout form = vertical();
        int padding = dp(20);
        form.setPadding(padding, dp(4), padding, 0);
        return form;
    }

    private EditText field(int hintRes, int inputType) {
        EditText input = new EditText(this);
        input.setHint(hintRes);
        input.setInputType(inputType);
        input.setTextColor(ContextCompat.getColor(this, R.color.textPrimary));
        input.setHintTextColor(ContextCompat.getColor(this, R.color.textSecondary));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(10);
        input.setLayoutParams(params);
        return input;
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return layout;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(android.view.Gravity.CENTER_VERTICAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return layout;
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(ContextCompat.getColor(this, color));
        view.setTypeface(view.getTypeface(), style);
        return view;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private String zoneLabel(GardenZone zone) {
        return com.alidogukan.avora.zones.PhysicalZoneIdentity.name(zone);
    }

    private String archiveZoneLabel(GardenZone zone, GardenSeason season) {
        if (season == null) return zoneLabel(zone);
        String emoji = SeasonDisplayIdentity.emoji(season, zone);
        String name = SeasonDisplayIdentity.name(season, zone);
        return (emoji.isEmpty() ? "" : emoji + " ") + name;
    }

    private String formatEpoch(long epoch) {
        if (epoch <= 0L) return getString(R.string.season_date_unknown);
        return new SimpleDateFormat(getString(R.string.date_format_dmy), Locale.getDefault()).format(new Date(epoch * 1000L));
    }

    private void showDatePicker(EditText target) {
        Calendar selected = Calendar.getInstance();
        Date parsed = parseSupportedDate(value(target));
        if (parsed != null) selected.setTime(parsed);
        new DatePickerDialog(
                this,
                (picker, year, month, day) -> target.setText(String.format(
                        Locale.getDefault(),
                        "%02d-%02d-%04d",
                        day,
                        month + 1,
                        year
                )),
                selected.get(Calendar.YEAR),
                selected.get(Calendar.MONTH),
                selected.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private Date parseSupportedDate(String rawDate) {
        if (blank(rawDate)) return null;
        String[] patterns = {"dd-MM-yyyy", "yyyy-MM-dd", "dd.MM.yyyy"};
        for (String pattern : patterns) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.getDefault());
            format.setLenient(false);
            try {
                return format.parse(rawDate.trim());
            } catch (Exception ignored) {
                // Try the next supported legacy format.
            }
        }
        return null;
    }

    private boolean validDate(String date) {
        return parseSupportedDate(date) != null;
    }

    private void showError(Exception error, int fallback) {
        String message = error == null || blank(error.getMessage()) ? getString(fallback) : error.getMessage();
        if ("SHARED_IRRIGATION_INCOMPATIBLE".equals(message)) {
            message = getString(R.string.season_shared_irrigation_incompatible);
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int multiline() {
        return InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE;
    }

    private static String value(EditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
