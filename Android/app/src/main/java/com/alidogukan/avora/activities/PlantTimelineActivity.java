package com.alidogukan.avora.activities;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.alidogukan.avora.R;
import com.alidogukan.avora.models.GardenEvent;
import com.alidogukan.avora.models.GardenPhoto;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.GardenSeason;
import com.alidogukan.avora.models.FertilizerApplication;
import com.alidogukan.avora.models.SeasonStatus;
import com.alidogukan.avora.models.WateringHistory;
import com.alidogukan.avora.models.WeatherForecast;
import com.alidogukan.avora.season.SeasonDisplayIdentity;
import com.alidogukan.avora.models.ZoneSeasonState;
import com.alidogukan.avora.viewmodels.PlantJournalViewModel;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Per-plant season timeline. It combines manual notes and archived analysis photos. */
public class PlantTimelineActivity extends EdgeToEdgeActivity {
    public static final String EXTRA_ZONE_ID = "zone_id";
    public static final String EXTRA_SEASON_ID = "season_id";
    public static final String EXTRA_INITIAL_TAB = "initial_tab";
    public static final String TAB_COMPARE = "compare";

    private PlantJournalViewModel viewModel;
    private String zoneId = "";
    private GardenZone zone;
    private LinearLayout entries;
    private TextView title, season, status, emoji, month, empty, planting;
    private final List<TimelineItem> items = new ArrayList<>();
    private List<FertilizerApplication> fertilizerApplications = new ArrayList<>();
    private List<WateringHistory> wateringHistory = new ArrayList<>();
    private List<GardenSeason> seasons = new ArrayList<>();
    private List<GardenSeason> observedSeasons = new ArrayList<>();
    private WeatherForecast weatherForecast;
    private String activeFilter = "all";
    private String activeTab = "timeline";
    private String requestedSeasonId = "";
    private String selectedSeasonId = "";
    private boolean seasonSelectionInitialized;
    private boolean zoneSnapshotLoaded;
    private TextView tabTimeline, tabPhotos, tabNotes, tabCompare;
    private int selectedYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
    private final int[] filterIds = {R.id.filterTimelineAll, R.id.filterTimelineWatering, R.id.filterTimelineFertilizer, R.id.filterTimelineAnalysis, R.id.filterTimelineEvent};
    private final String[] filterValues = {"all", "watering", "fertilizer", "analysis", "event"};

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_plant_timeline);

        viewModel = new ViewModelProvider(this).get(PlantJournalViewModel.class);
        zoneId = getIntent().getStringExtra(EXTRA_ZONE_ID); if (zoneId == null) zoneId = "";
        String requested = getIntent().getStringExtra(EXTRA_SEASON_ID);
        requestedSeasonId = requested == null ? "" : requested.trim();
        if (TAB_COMPARE.equals(getIntent().getStringExtra(EXTRA_INITIAL_TAB))) {
            activeTab = TAB_COMPARE;
        }
        if (state != null) {
            requestedSeasonId = state.getString("journal_season", requestedSeasonId);
            activeTab = state.getString("journal_tab", activeTab);
            activeFilter = state.getString("journal_filter", activeFilter);
        }
        title = findViewById(R.id.txtTimelineTitle); season = findViewById(R.id.txtTimelineSeason); planting = findViewById(R.id.txtTimelinePlanting); status = findViewById(R.id.txtTimelineStatus); emoji = findViewById(R.id.txtTimelineEmoji); month = findViewById(R.id.txtTimelineMonth); empty = findViewById(R.id.txtTimelineEmpty); entries = findViewById(R.id.layoutTimelineEvents);
        tabTimeline = findViewById(R.id.tabTimeline); tabPhotos = findViewById(R.id.tabPhotos); tabNotes = findViewById(R.id.tabNotes); tabCompare = findViewById(R.id.tabCompare);
        findViewById(R.id.btnTimelineBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnTimelineMenu).setOnClickListener(this::showTimelineMenu);
        findViewById(R.id.btnTimelineAdd).setOnClickListener(v -> showNewRecordTypes());
        season.setOnClickListener(v -> showSeasonPicker());
        tabTimeline.setOnClickListener(v -> { activeTab = "timeline"; render(); });
        tabPhotos.setOnClickListener(v -> { activeTab = "photos"; render(); });
        tabNotes.setOnClickListener(v -> { activeTab = "notes"; render(); });
        tabCompare.setOnClickListener(v -> { activeTab = "compare"; render(); });
        for (int i = 0; i < filterIds.length; i++) { final String value = filterValues[i]; findViewById(filterIds[i]).setOnClickListener(v -> { activeFilter = value; render(); }); }
        viewModel.getZones().observe(this, zones -> {
            zone = null;
            if (zones != null) {
                for (GardenZone value : zones) {
                    if (zoneId.equals(value.getZone_id())) { zone = value; break; }
                }
            }
            zoneSnapshotLoaded = true;
            refreshVisibleSeasons();
            selectInitialSeason();
            render();
        });
        viewModel.getFertilizerHistory().observe(this, values -> { fertilizerApplications = values == null ? new ArrayList<>() : values; loadItems(); render(); });
        viewModel.getWateringHistory().observe(this, values -> { wateringHistory = values == null ? new ArrayList<>() : values; loadItems(); render(); });
        viewModel.getWeather().observe(this, value -> { weatherForecast = value; addAutomaticSignals(); loadItems(); render(); });
        viewModel.getSeasons(zoneId).observe(this, values -> {
            observedSeasons = values == null ? new ArrayList<>() : new ArrayList<>(values);
            refreshVisibleSeasons();
            if (zoneSnapshotLoaded) selectInitialSeason();
            render();
        });
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("journal_season", selectedSeasonId);
        state.putString("journal_tab", activeTab);
        state.putString("journal_filter", activeFilter);
        super.onSaveInstanceState(state);
    }

    @Override protected void onResume() { super.onResume(); loadItems(); render(); }

    private final java.util.Map<String, GardenEvent> photoOwners = new java.util.HashMap<>();

    private GardenEvent photoOwner(GardenPhoto photo) {
        return viewModel.photoOwner(photo, photoOwners.get(photo.getRelated_application_id()));
    }

    private void loadItems() {
        items.clear();
        photoOwners.clear();
        Set<String> journalPhotoGroups = new HashSet<>();
        for (GardenEvent event : viewModel.loadEvents()) {
            if (!zoneId.equals(event.getZone_id())) continue;
            items.add(TimelineItem.event(event));
            photoOwners.put("journal_record_" + event.getId(), event);
        }
        for (GardenPhoto photo : viewModel.loadPhotos()) {
            if (!zoneId.equals(photo.getZone_id())) continue;
            String groupId = photo.getRelated_application_id();
            if (com.alidogukan.avora.photos.JournalPhotoRecordFilter.isRecordGroup(groupId)
                    && !journalPhotoGroups.add(groupId + "\n" + photo.getSeason_id())) continue;
            items.add(TimelineItem.photo(photo));
        }
        for (FertilizerApplication application : fertilizerApplications) if (zoneId.equals(application.getZone_id())) items.add(TimelineItem.fertilizer(application));
        for (WateringHistory watering : wateringHistory) if (zoneId.equals(watering.getZoneId()) && watering.isCompleted()) items.add(TimelineItem.watering(watering));
        items.sort(Comparator.comparingLong(TimelineItem::time).reversed());
    }

    private void render() {
        addAutomaticSignals();
        GardenSeason selected = selectedSeason();
        boolean canAdd = selected != null && SeasonStatus.isActive(selected.getStatus());
        findViewById(R.id.btnTimelineAdd).setEnabled(canAdd);
        findViewById(R.id.btnTimelineAdd).setAlpha(canAdd ? 1f : 0.4f);
        String cropName = SeasonDisplayIdentity.name(selected, zone);
        if (cropName.isBlank()) cropName = getString(R.string.runtime_plant_default);
        String areaName = SeasonDisplayIdentity.areaName(selected, zone);
        title.setText(getString(R.string.runtime_timeline_named_title, cropName));
        String seasonHeader = seasonHeader(areaName, selected);
        season.setText(seasonHeader);
        season.setContentDescription(getString(
                R.string.runtime_timeline_season_selector_description, seasonHeader));
        planting.setText(getString(
                R.string.runtime_icon_label,
                "●",
                plantingDateText()));
        status.setText(getString(
                R.string.runtime_icon_label,
                "●",
                liveSeasonStatus()));
        emoji.setText(SeasonDisplayIdentity.emoji(selected, zone));
        month.setText(tabHeading());
        month.setVisibility("compare".equals(activeTab) ? View.VISIBLE : View.GONE);
        entries.removeAllViews(); int shown = 0; String lastMonthKey = "";
        if ("timeline".equals(activeTab) && "all".equals(activeFilter)
                && selected != null
                && !selected.getSource_seedling_batch_id().isBlank()) {
            entries.addView(sourceSeedlingCard(selected));
            shown++;
        }
        if ("compare".equals(activeTab)) {
            shown = renderComparison();
        } else {
            for (TimelineItem item : items) if (recordBelongsToSelectedSeason(item) && visibleInTab(item)) {
                String monthKey = monthKey(item.time());
                if (!monthKey.equals(lastMonthKey)) {
                    TextView monthHeader = text(monthHeading(item.time()), 20, R.color.textPrimary);
                    monthHeader.setTypeface(null, android.graphics.Typeface.BOLD);
                    LinearLayout.LayoutParams monthParams = new LinearLayout.LayoutParams(-1, -2);
                    monthParams.topMargin = shown == 0 ? dp(4) : dp(16);
                    monthParams.bottomMargin = dp(9);
                    monthHeader.setLayoutParams(monthParams);
                    entries.addView(monthHeader);
                    lastMonthKey = monthKey;
                }
                entries.addView(card(item));
                shown++;
            }
        }
        empty.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
        empty.setText("compare".equals(activeTab)
                ? R.string.runtime_compare_empty : R.string.runtime_view_empty);
        updateTabStyle(tabTimeline, "timeline"); updateTabStyle(tabPhotos, "photos"); updateTabStyle(tabNotes, "notes"); updateTabStyle(tabCompare, "compare");
        for (int i = 0; i < filterIds.length; i++) {
            MaterialCardView filter = findViewById(filterIds[i]); boolean filterSelected = filterValues[i].equals(activeFilter);
            filter.setCardBackgroundColor(getColor(filterSelected ? R.color.surfaceGreen : R.color.card));
            filter.setStrokeColor(getColor(filterSelected ? R.color.primary : R.color.border));
        }
    }

    private View sourceSeedlingCard(GardenSeason selected) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(16));
        card.setCardBackgroundColor(getColor(R.color.surfaceGreen));
        card.setStrokeColor(getColor(R.color.primary));
        card.setStrokeWidth(dp(1));
        card.setClickable(true);
        card.setFocusable(true);
        LinearLayout.LayoutParams outer = new LinearLayout.LayoutParams(-1, -2);
        outer.bottomMargin = dp(12);
        card.setLayoutParams(outer);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView heading = text(
                getString(R.string.seedling_source_history_title),
                15,
                R.color.primary
        );
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(heading);
        String variety = selected.getSource_seedling_variety().isBlank()
                ? getString(R.string.seedling_transfer_variety_unspecified)
                : selected.getSource_seedling_variety();
        TextView detail = text(
                getString(
                        R.string.seedling_source_history_summary,
                        variety,
                        selected.getSource_seedling_healthy_count()
                ),
                12,
                R.color.textSecondary
        );
        detail.setPadding(0, dp(4), 0, 0);
        content.addView(detail);
        card.addView(content);
        card.setContentDescription(getString(
                R.string.seedling_source_history_open_description));
        card.setOnClickListener(view -> {
            Intent intent = new Intent(this, SeedlingBatchDetailActivity.class);
            intent.putExtra(
                    SeedlingBatchDetailActivity.EXTRA_BATCH_ID,
                    selected.getSource_seedling_batch_id()
            );
            startActivity(intent);
        });
        return card;
    }

    private String monthKey(long epoch) {
        return new SimpleDateFormat("yyyy-MM", Locale.US).format(new Date(epoch * 1000L));
    }

    private String monthHeading(long epoch) {
        return new SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                .format(new Date(epoch * 1000L));
    }
    private String tabHeading() {
        if ("photos".equals(activeTab)) return getString(R.string.runtime_growth_photos);
        if ("notes".equals(activeTab)) return getString(R.string.runtime_notes_observations);
        if ("compare".equals(activeTab)) return getString(R.string.runtime_photo_comparison);
        return new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(new Date());
    }

    private String plantingDateText() {
        GardenSeason selected = selectedSeason();
        String configuredDate = selected == null ? "" : selected.getPlanting_date();
        if ((configuredDate == null || configuredDate.isBlank())
                && selected != null && SeasonStatus.isActive(selected.getStatus())
                && zone != null && zone.getFertilization() != null) {
            configuredDate = zone.getFertilization().getPlanting_date();
        }
        if (configuredDate != null && !configuredDate.isBlank()) {
            return getString(R.string.runtime_planting_done, formatPlantingDate(configuredDate));
        }
        if (selected == null && zone != null && zone.getFertilization() != null) {
            configuredDate = zone.getFertilization().getPlanting_date();
            if (configuredDate != null && !configuredDate.isBlank()) {
                return getString(R.string.runtime_planting_done, formatPlantingDate(configuredDate));
            }
        }
        for (TimelineItem item : items) {
            if (recordBelongsToSelectedSeason(item)
                    && item.event != null && item.event.getType() != null
                    && item.event.getType().toLowerCase(Locale.ROOT).contains("dikim")) {
                return getString(R.string.runtime_planting_done,
                        new SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
                                .format(new Date(item.time() * 1000L)));
            }
        }
        return getString(R.string.runtime_planting_missing);
    }

    private String formatPlantingDate(String value) {
        try {
            LocalDate date = LocalDate.parse(value);
            return date.format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.getDefault()));
        } catch (Exception ignored) {
            return value;
        }
    }

    private String liveSeasonStatus() {
        GardenSeason selected = selectedSeason();
        if (selected != null) {
            if (SeasonStatus.isClosed(selected.getStatus())) return getString(R.string.season_closed_event_title);
            if (SeasonStatus.PLANNED.equals(selected.getStatus())) return getString(R.string.runtime_planned_season);
            if (zone != null && !zone.isEnabled()) return getString(R.string.runtime_zone_inactive);
            return getString(R.string.runtime_season_ongoing);
        }
        for (TimelineItem item : items) {
            if (recordBelongsToSelectedSeason(item) && item.event != null
                    && item.event.getType() != null
                    && item.event.getType().toLowerCase(Locale.ROOT).contains("hasat")) {
                return getString(R.string.season_closed_event_title);
            }
        }
        int currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        if (selectedYear < currentYear) return getString(R.string.runtime_past_season);
        if (selectedYear > currentYear) return getString(R.string.runtime_planned_season);
        if (zone != null && !zone.isEnabled()) return getString(R.string.runtime_zone_inactive);
        return getString(R.string.runtime_season_ongoing);
    }
    private boolean visibleInTab(TimelineItem item) {
        if ("photos".equals(activeTab)) return item.photo != null;
        if ("notes".equals(activeTab)) return item.event != null && "MANUAL".equals(item.event.getSource());
        if (item.photo != null && photoOwner(item.photo) != null
                && !"analysis".equals(activeFilter)) return false;
        return item.matches(activeFilter);
    }

    private void updateTabStyle(TextView tab, String value) {
        boolean selected = value.equals(activeTab);
        tab.setTextColor(getColor(selected ? R.color.primary : R.color.textSecondary));
        tab.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    private int renderComparison() {
        List<TimelineItem> photos = new ArrayList<>();
        for (TimelineItem item : items) if (item.photo != null && recordBelongsToSelectedSeason(item)) photos.add(item);
        if (photos.size() < 2) return 0;
        TimelineItem newest = photos.get(0), previous = photos.get(1);
        MaterialCardView card = new MaterialCardView(this); card.setRadius(dp(16)); card.setCardBackgroundColor(getColor(R.color.card)); card.setStrokeColor(getColor(R.color.border)); card.setStrokeWidth(dp(1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(9); card.setLayoutParams(params);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(14), dp(14), dp(14), dp(14));
        TextView heading = text(getString(R.string.runtime_compare_heading), 15, R.color.textPrimary); heading.setTypeface(null, android.graphics.Typeface.BOLD); content.addView(heading);
        LinearLayout images = new LinearLayout(this); images.setOrientation(LinearLayout.HORIZONTAL); images.setPadding(0, dp(12), 0, 0);
        addComparisonImage(images, previous, getString(R.string.runtime_previous)); addComparisonImage(images, newest, getString(R.string.runtime_new)); content.addView(images);
        TextView detail = text(getString(R.string.runtime_compare_detail), 12, R.color.textSecondary); detail.setPadding(0, dp(12), 0, 0); content.addView(detail);
        card.addView(content); card.setOnClickListener(v -> openRecord(newest)); entries.addView(card); return 1;
    }

    private void addComparisonImage(LinearLayout parent, TimelineItem item, String label) {
        LinearLayout holder = new LinearLayout(this); holder.setOrientation(LinearLayout.VERTICAL); holder.setGravity(Gravity.CENTER); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); if (parent.getChildCount() > 0) lp.setMarginStart(dp(10)); holder.setLayoutParams(lp);
        ImageView image = new ImageView(this); image.setScaleType(ImageView.ScaleType.CENTER_CROP); image.setImageURI(android.net.Uri.fromFile(new File(item.photo.getLocal_path()))); holder.addView(image, new LinearLayout.LayoutParams(-1, dp(132)));
        TextView caption = text(label + " · " + new SimpleDateFormat("dd MMM", Locale.getDefault()).format(new Date(item.time() * 1000L)), 11, R.color.textSecondary); caption.setGravity(Gravity.CENTER); caption.setPadding(0, dp(6), 0, 0); holder.addView(caption); parent.addView(holder);
    }
    private void addAutomaticSignals() {
        GardenSeason active = activeSeason();
        if (zone == null || active == null) return;
        String type = "", note = "", sourceKey = "";
        if (zone.hasSensorData() && zone.getMoisture() <= zone.getMoisture_limit() - 10) {
            type = "moisture_risk";
            note = getString(R.string.runtime_signal_moisture_note);
            sourceKey = "moisture_risk";
        }
        if (type.isBlank() && weatherForecast != null) {
            Double temperature = weatherForecast.getTodayTemperatureMax(), rain = weatherForecast.getTodayRainProbability(), wind = weatherForecast.getTodayWindMax();
            if (temperature != null && temperature >= 34) {
                type = "hot_weather";
                note = getString(R.string.runtime_signal_hot_note);
                sourceKey = "hot_weather";
            } else if (rain != null && rain >= 70) {
                type = "rain_weather";
                note = getString(R.string.runtime_signal_rain_note);
                sourceKey = "rain_weather";
            } else if (wind != null && wind >= 35) {
                type = "wind_weather";
                note = getString(R.string.runtime_signal_wind_note);
                sourceKey = "wind_weather";
            }
        }
        if (type.isBlank()) return;
        viewModel.addAutomaticEvent(zoneId, active.getSeason_id(), type, note, sourceKey)
                .addOnSuccessListener(created -> {
                    if (created != null) { loadItems(); render(); }
                });
    }
    private View card(TimelineItem item) {
        LinearLayout timelineRow = new LinearLayout(this);
        timelineRow.setOrientation(LinearLayout.HORIZONTAL);
        timelineRow.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams outer = new LinearLayout.LayoutParams(-1, -2);
        outer.bottomMargin = dp(7);
        timelineRow.setLayoutParams(outer);

        LinearLayout dateColumn = new LinearLayout(this);
        dateColumn.setOrientation(LinearLayout.VERTICAL);
        dateColumn.setGravity(Gravity.CENTER);
        TextView date = text(new SimpleDateFormat("dd", Locale.forLanguageTag("tr-TR"))
                .format(new Date(item.time() * 1000L)), 14, R.color.textPrimary);
        date.setGravity(Gravity.CENTER);
        date.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView time = text(new SimpleDateFormat("HH:mm", Locale.forLanguageTag("tr-TR"))
                .format(new Date(item.time() * 1000L)), 9, R.color.textSecondary);
        time.setGravity(Gravity.CENTER);
        dateColumn.addView(date);
        dateColumn.addView(time);
        timelineRow.addView(dateColumn, new LinearLayout.LayoutParams(dp(46), dp(68)));

        FrameLayout rail = new FrameLayout(this);
        LinearLayout.LayoutParams railParams = new LinearLayout.LayoutParams(dp(34), dp(68));
        rail.setLayoutParams(railParams);
        View connector = new View(this);
        connector.setBackgroundColor(getColor(R.color.accentLight));
        FrameLayout.LayoutParams connectorParams = new FrameLayout.LayoutParams(dp(2), -1, Gravity.CENTER_HORIZONTAL);
        rail.addView(connector, connectorParams);
        TextView type = text(item.icon(this), 17, R.color.primary);
        type.setGravity(Gravity.CENTER);
        type.setBackground(timelineDotBackground());
        FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(dp(32), dp(32), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        dotParams.topMargin = dp(18);
        rail.addView(type, dotParams);
        timelineRow.addView(rail);

        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(14));
        card.setCardBackgroundColor(getColor(R.color.card));
        card.setStrokeColor(getColor(R.color.border));
        card.setStrokeWidth(dp(1));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(0, -2, 1);
        card.setLayoutParams(cardParams);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(10), dp(10));
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        TextView heading = text(item.title(this), 14, R.color.textPrimary);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView detail = text(item.detail(this), 12, R.color.textSecondary);
        info.addView(heading);
        info.addView(detail);
        row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        if (item.photo != null && item.photo.getLocal_path() != null && new File(item.photo.getLocal_path()).exists()) {
            ImageView picture = new ImageView(this);
            picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
            picture.setImageURI(android.net.Uri.fromFile(new File(item.photo.getLocal_path())));
            row.addView(picture, new LinearLayout.LayoutParams(dp(48), dp(48)));
        }
        card.addView(row);
        card.setOnClickListener(v -> openRecord(item));
        timelineRow.addView(card);
        return timelineRow;
    }

    private GradientDrawable timelineDotBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(getColor(R.color.card));
        background.setStroke(dp(1), getColor(R.color.accentLight));
        return background;
    }
    private TextView text(String s, int size, int color) { TextView view = new TextView(this); view.setText(s); view.setTextSize(size); view.setTextColor(getColor(color)); return view; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void openRecord(TimelineItem item) {
        if (item.photo != null) {
            GardenEvent owner = photoOwner(item.photo);
            if (owner != null) { openRecord(TimelineItem.event(owner)); return; }
        }
        Intent intent = new Intent(this, JournalRecordDetailActivity.class);
        GardenSeason selected = selectedSeason();
        intent.putExtra("title", item.title(this));
        intent.putExtra("detail", item.detail(this));
        intent.putExtra("icon", item.icon(this));
        intent.putExtra("time", item.time());
        intent.putExtra("zone_id", zoneId);
        intent.putExtra("season_id", item.seasonIdFor(selectedSeasonId));
        intent.putExtra("season_read_only",
                selected != null && SeasonStatus.isClosed(selected.getStatus()));
        if (item.event != null && "MANUAL".equals(item.event.getSource())) {
            intent.putExtra("manual_event_id", item.event.getId());
            intent.putExtra("manual_event_type", item.event.getType());
            intent.putExtra("photo_group_id", "journal_record_" + item.event.getId());
        }
        if (item.photo != null) {
            intent.putExtra("photo_path", item.photo.getLocal_path());
            intent.putExtra("photo_group_id", item.photo.getRelated_application_id());
            intent.putExtra("advice", item.photo.getAnalysis_advice());
        }
        startActivity(intent);
    }
    private int yearOf(long epoch) { java.util.Calendar calendar = java.util.Calendar.getInstance(); calendar.setTimeInMillis(epoch * 1000L); return calendar.get(java.util.Calendar.YEAR); }
    private void showSeasonPicker() {
        PopupMenu menu = new PopupMenu(this, season);
        if (!seasons.isEmpty()) {
            for (GardenSeason value : seasons) {
                String seasonLabel = value.getLabel().isBlank()
                        ? yearOf(value.getStarted_at_epoch()) + " Sezonu"
                        : value.getLabel();
                String crop = SeasonDisplayIdentity.emoji(value, zone)
                        + " "
                        + SeasonDisplayIdentity.name(value, zone);
                String label = crop.trim() + " · " + seasonLabel;
                menu.getMenu().add(label).setIntent(
                        new Intent().putExtra("season_id", value.getSeason_id())
                );
            }
            menu.setOnMenuItemClickListener(item -> {
                Intent metadata = item.getIntent();
                selectedSeasonId = metadata == null ? "" : metadata.getStringExtra("season_id");
                GardenSeason selected = selectedSeason();
                if (selected != null) selectedYear = yearOf(selected.getStarted_at_epoch());
                render();
                return true;
            });
        } else {
            java.util.TreeSet<Integer> years = new java.util.TreeSet<>(java.util.Collections.reverseOrder());
            years.add(selectedYear);
            for (TimelineItem item : items) years.add(yearOf(item.time()));
            for (Integer year : years) menu.getMenu().add(String.valueOf(year));
            menu.setOnMenuItemClickListener(item -> {
                selectedYear = Integer.parseInt(String.valueOf(item.getTitle()));
                render();
                return true;
            });
        }
        menu.show();
    }

    private void showTimelineMenu(View anchor) {
        GardenSeason selected = selectedSeason();
        PopupMenu menu = new PopupMenu(this, anchor);
        android.view.MenuItem delete = menu.getMenu().add(
                R.string.season_delete_empty_confirm
        );
        delete.setEnabled(selected != null
                && !selected.getSeason_id().isBlank());
        menu.setOnMenuItemClickListener(item -> {
            if (selected == null || selected.getSeason_id().isBlank()) return false;
            inspectSelectedSeasonForDeletion(anchor, selected);
            return true;
        });
        menu.show();
    }

    private void inspectSelectedSeasonForDeletion(
            View anchor,
            GardenSeason selected
    ) {
        anchor.setEnabled(false);
        viewModel.inspectEmptySeason(zoneId, selected.getSeason_id())
                .addOnSuccessListener(check -> {
                    anchor.setEnabled(true);
                    if (isFinishing() || isDestroyed()) return;
                    if (check.canDelete()) {
                        showDeleteEmptySeasonDialog(selected);
                    } else {
                        showSeasonDeletionBlockers(selected, check);
                    }
                })
                .addOnFailureListener(error -> {
                    anchor.setEnabled(true);
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(
                            this,
                            R.string.season_delete_empty_check_failed,
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void showSeasonDeletionBlockers(
            GardenSeason selected,
            PlantJournalViewModel.SeasonDeletionStatus check
    ) {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.season_delete_blocked_title)
                .setMessage(buildSeasonDeletionMessage(check))
                .setNegativeButton(R.string.season_cancel, null);
        if (check.canCleanMissingPhotos()) {
            dialog.setPositiveButton(
                    R.string.season_delete_cleanup_action,
                    (ignored, which) -> confirmMissingPhotoCleanup(selected, check)
            );
        }
        dialog.show();
    }

    private String buildSeasonDeletionMessage(
            PlantJournalViewModel.SeasonDeletionStatus check
    ) {
        List<String> lines = new ArrayList<>();
        int missingPhotos = check.getMissingPhotoCount();
        if (missingPhotos > 0) {
            lines.add("• " + getResources().getQuantityString(
                    R.plurals.season_delete_missing_photos,
                    missingPhotos,
                    missingPhotos
            ));
        }
        int missingAnalyses = check.getMissingPhotoAnalysisCount();
        if (missingAnalyses > 0) {
            lines.add("• " + getResources().getQuantityString(
                    R.plurals.season_delete_missing_analyses,
                    missingAnalyses,
                    missingAnalyses
            ));
        }
        addCountLine(lines, R.plurals.season_delete_local_photos,
                check.getLocalPhotoCount());
        addCountLine(lines, R.plurals.season_delete_watering_records,
                check.getWateringCount());
        addCountLine(lines, R.plurals.season_delete_fertilizer_records,
                check.getFertilizerCount());
        addCountLine(lines, R.plurals.season_delete_journal_records,
                check.getJournalCount());
        if (check.hasMeaningfulOutcome()) {
            lines.add("• " + getString(R.string.season_delete_outcome_record));
        }
        if (lines.isEmpty() && !check.getReason().isBlank()) {
            lines.add(check.getReason());
        }
        if (check.canCleanMissingPhotos()) {
            lines.add("");
            lines.add(getString(R.string.season_delete_cleanup_explanation));
        }
        return String.join("\n", lines);
    }

    private void addCountLine(List<String> lines, int pluralId, int count) {
        if (count <= 0) return;
        lines.add("• " + getResources().getQuantityString(
                pluralId,
                count,
                count
        ));
    }

    private void confirmMissingPhotoCleanup(
            GardenSeason selected,
            PlantJournalViewModel.SeasonDeletionStatus check
    ) {
        int missingCount = check.getMissingPhotoCount();
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.season_delete_cleanup_confirm_title)
                .setMessage(getResources().getQuantityString(
                        R.plurals.season_delete_cleanup_confirm_message,
                        missingCount,
                        missingCount
                ))
                .setNegativeButton(R.string.season_cancel, null)
                .setPositiveButton(R.string.season_delete_cleanup_action,
                        (ignored, which) -> cleanupMissingPhotoRecords(
                                selected,
                                missingCount
                        ))
                .show();
    }

    private void cleanupMissingPhotoRecords(
            GardenSeason selected,
            int missingCount
    ) {
        viewModel.cleanupMissingPhotoRecords(zoneId, selected.getSeason_id())
                .addOnSuccessListener(check -> {
                    if (isFinishing() || isDestroyed()) return;
                    loadItems();
                    render();
                    Toast.makeText(
                            this,
                            getResources().getQuantityString(
                                    R.plurals.season_delete_cleanup_success,
                                    missingCount,
                                    missingCount
                            ),
                            Toast.LENGTH_LONG
                    ).show();
                    if (check.canDelete()) {
                        showDeleteEmptySeasonDialog(selected);
                    } else {
                        showSeasonDeletionBlockers(selected, check);
                    }
                })
                .addOnFailureListener(error -> {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(
                            this,
                            error == null || error.getMessage() == null
                                    || error.getMessage().isBlank()
                                    ? getString(R.string.season_delete_cleanup_failed)
                                    : error.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void showDeleteEmptySeasonDialog(GardenSeason selected) {
        String crop = SeasonDisplayIdentity.name(selected, zone);
        if (crop.isBlank()) crop = getString(R.string.runtime_plant_default);
        String label = selected.getLabel().isBlank()
                ? getString(
                        R.string.runtime_timeline_season_year,
                        yearOf(selected.getStarted_at_epoch())
                )
                : selected.getLabel().trim();
        String seasonId = selected.getSeason_id();
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(
                        R.string.season_delete_empty_dialog_title,
                        crop + " · " + label
                ))
                .setMessage(R.string.season_delete_empty_dialog_message)
                .setNegativeButton(R.string.season_cancel, null)
                .setPositiveButton(R.string.season_delete_empty_confirm, (dialog, which) ->
                        viewModel.deleteEmptySeason(zoneId, seasonId)
                                .addOnSuccessListener(ignored -> {
                                    observedSeasons.removeIf(value ->
                                            value != null
                                                    && seasonId.equals(value.getSeason_id()));
                                    selectedSeasonId = "";
                                    seasonSelectionInitialized = false;
                                    refreshVisibleSeasons();
                                    selectInitialSeason();
                                    render();
                                    Toast.makeText(
                                            this,
                                            R.string.season_delete_empty_success,
                                            Toast.LENGTH_LONG
                                    ).show();
                                })
                                .addOnFailureListener(error -> Toast.makeText(
                                        this,
                                        error == null || error.getMessage() == null
                                                || error.getMessage().isBlank()
                                                ? getString(R.string.season_delete_empty_failed)
                                                : error.getMessage(),
                                        Toast.LENGTH_LONG
                                ).show()))
                .show();
    }


    private void refreshVisibleSeasons() {
        ZoneSeasonState current = zone == null ? null : zone.getSeason();
        seasons = viewModel.visibleSeasons(
                observedSeasons,
                current,
                requestedSeasonId
        );
    }

    private void selectInitialSeason() {
        if (seasonSelectionInitialized && selectedSeason() != null) return;
        GardenSeason choice = null;
        if (!requestedSeasonId.isBlank()) {
            for (GardenSeason value : seasons) {
                if (requestedSeasonId.equals(value.getSeason_id())) {
                    choice = value;
                    break;
                }
            }
        }
        if (choice == null) {
            for (GardenSeason value : seasons) {
                if (SeasonStatus.isActive(value.getStatus())) { choice = value; break; }
            }
        }
        if (choice == null && !seasons.isEmpty()) choice = seasons.get(0);
        if (choice != null) {
            selectedSeasonId = choice.getSeason_id();
            selectedYear = yearOf(choice.getStarted_at_epoch());
            seasonSelectionInitialized = true;
        }
    }

    private GardenSeason selectedSeason() {
        for (GardenSeason value : seasons) {
            if (selectedSeasonId.equals(value.getSeason_id())) return value;
        }
        return null;
    }

    private GardenSeason activeSeason() {
        GardenSeason selected = selectedSeason();
        if (selected != null && SeasonStatus.isActive(selected.getStatus())) return selected;
        for (GardenSeason value : seasons) {
            if (SeasonStatus.isActive(value.getStatus())) return value;
        }
        return null;
    }

    private String seasonHeader(String zoneName, GardenSeason selected) {
        if (selected == null) {
            return getString(R.string.runtime_timeline_season_header, zoneName,
                    getString(R.string.runtime_timeline_season_year, selectedYear));
        }
        String label = selected.getLabel().isBlank()
                ? getString(R.string.runtime_timeline_season_year, selectedYear)
                : selected.getLabel().trim();
        String normalizedZone = zoneName == null ? "" : zoneName.trim();
        // Compatibility with the first season format: "2026 Domates".
        if (!normalizedZone.isBlank() && (label.endsWith(" " + normalizedZone)
                || label.equalsIgnoreCase(normalizedZone))) {
            label = getString(R.string.runtime_timeline_season_year, selectedYear);
        }
        return getString(R.string.runtime_timeline_season_header, zoneName, label);
    }

    private boolean recordBelongsToSelectedSeason(TimelineItem item) {
        GardenSeason selected = selectedSeason();
        if (selected == null) return yearOf(item.time()) == selectedYear;
        if (item.fertilizer != null
                && item.fertilizer.belongsToSeason(selected.getSeason_id())) {
            return true;
        }
        if (item.watering != null
                && item.watering.getSeasonIds().contains(selected.getSeason_id())) {
            return true;
        }
        return viewModel.belongsToSeason(item.seasonId(), item.time(), selected);
    }
    private com.google.android.material.bottomsheet.BottomSheetDialog journalEntrySheet;

    @Override protected void onDestroy() {
        if (journalEntrySheet != null) journalEntrySheet.dismiss();
        super.onDestroy();
    }

    private void showNewRecordTypes() {
        if (journalEntrySheet != null && journalEntrySheet.isShowing()) return;
        GardenSeason active = selectedSeason();
        if (active == null || !SeasonStatus.isActive(active.getStatus())) {
            Toast.makeText(this, R.string.runtime_start_season_first, Toast.LENGTH_LONG).show();
            return;
        }
        journalEntrySheet = com.alidogukan.avora.ui.JournalEntryBottomSheet.show(this,
                action -> openJournalAction(action, active));
    }

    private void openJournalAction(String type, GardenSeason active) {
        Class<?> screen = "photo_growth".equals(type) ? PlantAssistantActivity.class
                : "watering".equals(type) ? AIAssistantActivity.class
                : "fertilization".equals(type) ? FertilizationZoneDetailActivity.class
                : NewJournalRecordActivity.class;
        Intent intent = new Intent(this, screen);
        intent.putExtra("zone_id", zoneId);
        intent.putExtra("season_id", active.getSeason_id());
        intent.putExtra(NewJournalRecordActivity.EXTRA_INITIAL_TYPE, type);
        if ("photo_growth".equals(type)) {
            intent.putExtra(PlantAssistantActivity.EXTRA_JOURNAL_GROWTH, true);
        }
        startActivity(intent);
    }

    private String eventTypeLabel(String type) {
        if ("planting".equals(type)) return getString(R.string.runtime_event_planting);
        if ("observation".equals(type)) return getString(R.string.runtime_event_note);
        if ("flowering".equals(type)) return getString(R.string.runtime_event_flowering);
        if ("first_product".equals(type)) return getString(R.string.runtime_event_first_product);
        if ("harvest".equals(type)) return getString(R.string.runtime_event_harvest);
        if ("special".equals(type)) return getString(R.string.runtime_event_special);
        if ("photo_growth".equals(type)) return getString(R.string.runtime_event_growth_photo);
        return type;
    }

    private static final class TimelineItem {
        final GardenEvent event; final GardenPhoto photo; final FertilizerApplication fertilizer; final WateringHistory watering;
        private TimelineItem(GardenEvent e, GardenPhoto p, FertilizerApplication f, WateringHistory w) { event = e; photo = p; fertilizer = f; watering = w; }
        static TimelineItem event(GardenEvent v) { return new TimelineItem(v, null, null, null); } static TimelineItem photo(GardenPhoto v) { return new TimelineItem(null, v, null, null); } static TimelineItem fertilizer(FertilizerApplication v) { return new TimelineItem(null, null, v, null); } static TimelineItem watering(WateringHistory v) { return new TimelineItem(null, null, null, v); }
        long time() { if (event != null) return event.getOccurred_at_epoch(); if (photo != null) return photo.getCaptured_at_epoch(); if (fertilizer != null) return fertilizer.getApplied_at_epoch(); return parseTime(watering.getFinishedAt()); }
        String seasonId() { if (event != null) return event.getSeason_id(); if (photo != null) return photo.getSeason_id(); if (fertilizer != null) return fertilizer.getSeason_id(); return watering.getSeasonId(); }
        String seasonIdFor(String selectedSeasonId) {
            if (fertilizer != null && fertilizer.belongsToSeason(selectedSeasonId)) return selectedSeasonId;
            if (watering != null && watering.getSeasonIds().contains(selectedSeasonId)) return selectedSeasonId;
            return seasonId();
        }
        String title(android.content.Context context) {
            if (fertilizer != null) return context.getString(R.string.notification_category_fertilization);
            if (watering != null) return context.getString(R.string.notification_category_irrigation);
            if (photo != null) return photo.getAnalysis_title() == null || photo.getAnalysis_title().isBlank()
                    ? context.getString(R.string.runtime_growth_photo) : photo.getAnalysis_title();
            String raw = event.getType();
            if (raw == null || raw.isBlank()) return context.getString(R.string.runtime_garden_record);
            if ("planting".equals(raw) || "Dikim yapıldı".equals(raw)) return context.getString(R.string.runtime_event_planting);
            if ("observation".equals(raw) || "Gözlem / not".equals(raw)) return context.getString(R.string.runtime_event_note);
            if ("flowering".equals(raw) || "Çiçeklenme dönemi başladı".equals(raw)) return context.getString(R.string.runtime_event_flowering);
            if ("first_product".equals(raw) || "İlk ürün".equals(raw)) return context.getString(R.string.runtime_event_first_product);
            if ("harvest".equals(raw) || "Hasat".equals(raw)) return context.getString(R.string.runtime_event_harvest);
            if ("special".equals(raw) || "Özel olay".equals(raw)) return context.getString(R.string.runtime_event_special);
            if ("Takip fotoğrafı önerisi".equals(raw)) return context.getString(R.string.runtime_follow_up_photo_title);
            if ("Takip değerlendirmesi".equals(raw)) return context.getString(R.string.runtime_follow_up_assessment_title);
            if ("moisture_risk".equals(raw) || "Nem riski".equals(raw)) return context.getString(R.string.runtime_signal_moisture_title);
            if ("hot_weather".equals(raw) || "Sıcak hava uyarısı".equals(raw)) return context.getString(R.string.runtime_signal_hot_title);
            if ("rain_weather".equals(raw) || "Yağış uyarısı".equals(raw)) return context.getString(R.string.runtime_signal_rain_title);
            if ("wind_weather".equals(raw) || "Kuvvetli rüzgar".equals(raw)) return context.getString(R.string.runtime_signal_wind_title);
            return raw;
        }
        String detail(android.content.Context context) {
            if (fertilizer != null) return fertilizer.getProduct_name() + " · "
                    + fertilizer.getApplied_dose() + " " + fertilizer.getDose_unit();
            if (watering != null) return context.getString(
                    R.string.runtime_duration_seconds, watering.getDuration());
            if (photo != null) return photo.getNote() == null || photo.getNote().isBlank()
                    ? context.getString(R.string.runtime_growth_photo_added) : photo.getNote();
            String type = event.getType() == null ? "" : event.getType();
            if ("Takip fotoğrafı önerisi".equals(type)) return context.getString(R.string.runtime_follow_up_photo_note);
            if ("Takip değerlendirmesi".equals(type)) return context.getString(R.string.runtime_follow_up_assessment_note);
            if ("moisture_risk".equals(type) || "Nem riski".equals(type)) return context.getString(R.string.runtime_signal_moisture_note);
            if ("hot_weather".equals(type) || "Sıcak hava uyarısı".equals(type)) return context.getString(R.string.runtime_signal_hot_note);
            if ("rain_weather".equals(type) || "Yağış uyarısı".equals(type)) return context.getString(R.string.runtime_signal_rain_note);
            if ("wind_weather".equals(type) || "Kuvvetli rüzgar".equals(type)) return context.getString(R.string.runtime_signal_wind_note);
            return event.getNote() == null || event.getNote().isBlank()
                    ? context.getString(R.string.runtime_journal_added) : event.getNote();
        }
        String icon(android.content.Context context) { if (fertilizer != null) return context.getString(R.string.symbol_plant); if (watering != null) return context.getString(R.string.symbol_water_drop); if (photo != null) return context.getString(R.string.symbol_leaf); String type = event.getType().toLowerCase(Locale.ROOT); return type.contains("gübre") ? context.getString(R.string.symbol_plant) : type.contains("sula") ? context.getString(R.string.symbol_water_drop) : type.contains("analiz") ? context.getString(R.string.symbol_sparkle) : context.getString(R.string.symbol_bullet); }
        boolean matches(String filter) { if ("all".equals(filter)) return true; if ("watering".equals(filter)) return watering != null || event != null && event.getType().toLowerCase(Locale.ROOT).contains("sula"); if ("fertilizer".equals(filter)) return fertilizer != null || event != null && event.getType().toLowerCase(Locale.ROOT).contains("gübre"); if ("analysis".equals(filter)) return photo != null || event != null && event.getType().toLowerCase(Locale.ROOT).contains("analiz"); return event != null && !event.getType().toLowerCase(Locale.ROOT).contains("sula") && !event.getType().toLowerCase(Locale.ROOT).contains("gübre") && !event.getType().toLowerCase(Locale.ROOT).contains("analiz"); }
        private static long parseTime(String value) { if (value == null) return 0L; String[] formats = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "dd-MM-yyyy HH:mm"}; for (String format : formats) try { return new SimpleDateFormat(format, Locale.US).parse(value).getTime() / 1000L; } catch (Exception ignored) { } return 0L; }
    }
}
