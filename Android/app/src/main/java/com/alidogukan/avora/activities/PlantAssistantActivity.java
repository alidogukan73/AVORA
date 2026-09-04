package com.alidogukan.avora.activities;

import android.graphics.Bitmap;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.BuildConfig;
import com.alidogukan.avora.security.AppCheckVerificationException;
import com.alidogukan.avora.models.GardenSeason;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.WeatherForecast;
import com.alidogukan.avora.photos.GardenPhotoCapture;
import com.alidogukan.avora.plantassistant.PlantAssistantResult;
import com.alidogukan.avora.plantassistant.PlantGrowthAssessment;
import com.alidogukan.avora.season.SeasonDisplayIdentity;
import com.alidogukan.avora.ui.PrimaryBottomNavigation;
import com.alidogukan.avora.viewmodels.PlantAssistantViewModel;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/** AI Bitki Asistanı: fotoğraf, belirtiler, sensör ve hava bağlamıyla güvenli ön değerlendirme. */
public class PlantAssistantActivity extends EdgeToEdgeActivity {
    private static final String LOG_TAG = "AVORA-PlantAssistant";
    private PlantAssistantViewModel viewModel;
    private final Map<String, PlantSelection> plants = new HashMap<>();
    private final List<GardenZone> latestZones = new ArrayList<>();
    private final List<GardenSeason> latestSeasons = new ArrayList<>();

    private MaterialAutoCompleteTextView zoneDropdown;
    private MaterialCardView resultCard;
    private TextView title, meta, context, advice;
    private TextView growthScore, growthStage, growthTrend, growthComparison, growthSignals;
    private TextView soilData, weatherTemperatureData, sunData, windData, humidityData;
    private ImageView photoPreview;
    private View photoHintLayout, otherNoteLayout;
    private TextInputEditText generalNote, otherNote;
    private CheckBox growthStatus, yellowing, drying, spot, wilt, pest, flowerDrop, other;
    private String requestedZoneId = "";
    private String requestedSeasonId = "";
    private Uri selectedPhotoUri;
    private Bitmap selectedPhotoBitmap;
    private WeatherForecast currentWeather;
    private boolean selectedPhotoArchived;
    private String archivedPhotoId = "";
    private AnalysisSnapshot pendingAnalysis;
    private boolean awaitingVisionResult;
    private GardenPhotoCapture.Target pendingCameraPhoto;
    private GardenPhotoCapture.Target capturedCameraPhoto;

    private final ActivityResultLauncher<Uri> camera =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), saved -> {
                GardenPhotoCapture.Target target = pendingCameraPhoto;
                pendingCameraPhoto = null;
                if (!saved || target == null) {
                    if (target != null) target.delete();
                    return;
                }
                discardCapturedCameraPhoto();
                capturedCameraPhoto = target;
                showPhoto(target.getUri());
            });
    private final ActivityResultLauncher<PickVisualMediaRequest> photoPicker =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    discardCapturedCameraPhoto();
                    showPhoto(uri);
                }
            });
    private final ActivityResultLauncher<Intent> journalPhotoPicker =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                String path = result.getData().getStringExtra(GardenPhotoGalleryActivity.EXTRA_SELECTED_PHOTO_PATH);
                String photoId = result.getData().getStringExtra(GardenPhotoGalleryActivity.EXTRA_SELECTED_PHOTO_ID);
                if (path != null && !path.isBlank()) {
                    discardCapturedCameraPhoto();
                    showPhoto(Uri.fromFile(new java.io.File(path)));
                    archivedPhotoId = photoId == null ? "" : photoId;
                    selectedPhotoArchived = !archivedPhotoId.isBlank();
                }
            });

    @Override
    public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_plant_assistant);
        viewModel = new ViewModelProvider(this).get(PlantAssistantViewModel.class);
        PrimaryBottomNavigation.bind(this, PrimaryBottomNavigation.ASSISTANT);
        requestedZoneId = getIntent().getStringExtra("zone_id");
        requestedSeasonId = getIntent().getStringExtra("season_id");
        bindViews();
        bindActions();
        viewModel.getZones().observe(this, items -> {
            latestZones.clear();
            if (items != null) latestZones.addAll(viewModel.activeZones(items));
            renderPlantSelections();
        });
        viewModel.getSeasons().observe(this, items -> {
            latestSeasons.clear();
            if (items != null) latestSeasons.addAll(items);
            renderPlantSelections();
        });
        viewModel.getWeather().observe(this, weather -> {
            currentWeather = weather;
            renderLiveData(selectedZone());
        });
        viewModel.getPhotoMetadata().observe(this, ignored -> { });
    }

    private void bindViews() {
        zoneDropdown = findViewById(R.id.dropdownDoctorZone);
        resultCard = findViewById(R.id.cardDoctorResult);
        title = findViewById(R.id.txtDoctorTitle);
        meta = findViewById(R.id.txtDoctorMeta);
        context = findViewById(R.id.txtDoctorContext);
        advice = findViewById(R.id.txtDoctorAdvice);
        growthScore = findViewById(R.id.txtDoctorGrowthScore);
        growthStage = findViewById(R.id.txtDoctorGrowthStage);
        growthTrend = findViewById(R.id.txtDoctorGrowthTrend);
        growthComparison = findViewById(R.id.txtDoctorGrowthComparison);
        growthSignals = findViewById(R.id.txtDoctorGrowthSignals);
        photoHintLayout = findViewById(R.id.layoutDoctorPhotoHint);
        photoPreview = findViewById(R.id.imgDoctorPhoto);
        generalNote = findViewById(R.id.inputDoctorNote);
        otherNoteLayout = findViewById(R.id.layoutDoctorOtherNote);
        otherNote = findViewById(R.id.inputDoctorOtherNote);
        soilData = findViewById(R.id.txtDoctorSoil);
        weatherTemperatureData = findViewById(R.id.txtDoctorWeatherTemp);
        sunData = findViewById(R.id.txtDoctorSun);
        windData = findViewById(R.id.txtDoctorWind);
        humidityData = findViewById(R.id.txtDoctorHumidity);
        growthStatus = findViewById(R.id.checkDoctorGrowthStatus);
        yellowing = findViewById(R.id.checkDoctorYellowing);
        drying = findViewById(R.id.checkDoctorDrying);
        spot = findViewById(R.id.checkDoctorSpot);
        wilt = findViewById(R.id.checkDoctorWilt);
        pest = findViewById(R.id.checkDoctorPest);
        flowerDrop = findViewById(R.id.checkDoctorFlowerDrop);
        other = findViewById(R.id.checkDoctorOther);
    }

    private void bindActions() {
        findViewById(R.id.btnDoctorBack).setOnClickListener(view -> finish());
        findViewById(R.id.cardDoctorPhotoPicker).setOnClickListener(view -> showPhotoSourceDialog());
        findViewById(R.id.btnAnalyzePlant).setOnClickListener(view -> analyze());
        findViewById(R.id.btnPlantGrowthHistory).setOnClickListener(
                view -> openGrowthHistory());
        other.setOnCheckedChangeListener((button, checked) -> {
            otherNoteLayout.setVisibility(checked ? View.VISIBLE : View.GONE);
            if (!checked) otherNote.setText("");
        });
    }

    private void showPhotoSourceDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.runtime_add_photo)
                .setItems(new String[]{
                        getString(R.string.runtime_take_photo),
                        getString(R.string.runtime_choose_gallery),
                        getString(R.string.runtime_choose_journal)
                }, (dialog, which) -> {
                    if (which == 0) {
                        launchCamera();
                    } else if (which == 1) {
                        photoPicker.launch(new PickVisualMediaRequest.Builder()
                                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
                    } else {
                        Intent intent = new Intent(this, GardenPhotoGalleryActivity.class);
                        PlantSelection selected = selectedPlant();
                        if (selected != null) {
                            intent.putExtra(GardenPhotoGalleryActivity.EXTRA_ZONE_ID, selected.zone.getZone_id());
                            intent.putExtra(GardenPhotoGalleryActivity.EXTRA_SEASON_ID, selected.seasonId());
                        }
                        intent.putExtra(GardenPhotoGalleryActivity.EXTRA_PICK_MODE, true);
                        journalPhotoPicker.launch(intent);
                    }
                })
                .show();
    }

    private void launchCamera() {
        try {
            pendingCameraPhoto = GardenPhotoCapture.create(this);
            camera.launch(pendingCameraPhoto.getUri());
        } catch (Exception error) {
            if (pendingCameraPhoto != null) pendingCameraPhoto.delete();
            pendingCameraPhoto = null;
            toast(getString(R.string.runtime_photo_add_failed));
        }
    }

    private void discardCapturedCameraPhoto() {
        if (capturedCameraPhoto != null) capturedCameraPhoto.delete();
        capturedCameraPhoto = null;
    }

    private void renderPlantSelections() {
        plants.clear();
        List<String> labels = new ArrayList<>();
        PlantSelection requested = null;
        for (GardenZone zone : latestZones) {
            List<GardenSeason> active = SeasonDisplayIdentity.activeSeasons(
                    zone, latestSeasons);
            if (active.isEmpty()) active.add(null);
            for (GardenSeason season : active) {
                PlantSelection selection = new PlantSelection(zone, season, "");
                String label = labelFor(selection);
                if (plants.containsKey(label)) {
                    long started = season == null ? 0L : season.getStarted_at_epoch();
                    label += " · " + (started <= 0L ? selection.seasonId()
                            : DateFormat.getDateInstance(DateFormat.SHORT)
                            .format(new Date(started * 1000L)));
                }
                selection = new PlantSelection(zone, season, label);
                labels.add(label);
                plants.put(label, selection);
                boolean zoneRequested = zone.getZone_id().equals(requestedZoneId);
                boolean seasonRequested = requestedSeasonId != null
                        && !requestedSeasonId.isBlank()
                        && requestedSeasonId.equals(selection.seasonId());
                if (zoneRequested && (seasonRequested
                        || ((requestedSeasonId == null || requestedSeasonId.isBlank())
                        && requested == null))) {
                    requested = selection;
                }
            }
        }
        zoneDropdown.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels));
        zoneDropdown.setHint(labels.isEmpty()
                ? getString(R.string.runtime_no_active_season_zones)
                : null);
        zoneDropdown.setOnItemClickListener((parent, view, position, id) -> {
            requestedZoneId = "";
            requestedSeasonId = "";
            resultCard.setVisibility(View.GONE);
            renderLiveData(selectedZone());
        });
        if (requested != null) {
            zoneDropdown.setText(requested.label, false);
            renderLiveData(requested.zone);
        } else if (labels.isEmpty()) {
            zoneDropdown.setText("", false);
        } else if (selectedZone() == null) {
            zoneDropdown.setText(labels.get(0), false);
            renderLiveData(plants.get(labels.get(0)).zone);
        }
    }

    private void analyze() {
        GardenZone zone = selectedZone();
        List<String> symptoms = selectedSymptoms();
        boolean growthStatusRequested = growthStatus.isChecked();
        if (zone == null) {
            toast(getString(plants.isEmpty()
                    ? R.string.runtime_no_active_season_zones
                    : R.string.runtime_select_zone_first));
            return;
        }
        if (symptoms.isEmpty() && !growthStatusRequested) {
            toast(getString(R.string.runtime_select_symptom));
            return;
        }
        if (growthStatusRequested && !hasPhoto()) {
            toast(getString(R.string.runtime_growth_photo_required));
            return;
        }
        String note = text(generalNote);
        PlantAssistantResult result = viewModel.assess(
                zone, symptoms, note, currentWeather, hasPhoto(), growthStatusRequested);
        awaitingVisionResult = hasPhoto();
        renderHeuristicResult(result, growthStatusRequested);
        if (hasPhoto()) requestVisionAnalysis(zone, symptoms, note, growthStatusRequested);
        savePhotoToArchive(zone, symptoms, note, growthStatusRequested);
    }

    private void renderHeuristicResult(PlantAssistantResult result,
                                       boolean growthStatusRequested) {
        title.setText(result.getTitle());
        meta.setText(getString(R.string.runtime_probability_urgency,
                result.getProbability(), result.getUrgency()));
        context.setText(getString(R.string.runtime_evaluated_context,
                result.getContext()));
        advice.setText(result.getAdvice());
        findViewById(R.id.layoutDoctorGrowthSummary).setVisibility(View.GONE);
        GardenZone zone = selectedZone();
        viewModel.saveRecommendation(
                zone == null ? "" : zone.getZone_id(),
                selectedSeasonId(),
                result.getUrgency(),
                result.getTitle(),
                result.getAdvice()
        );
        resultCard.setVisibility(View.VISIBLE);
        archiveAnalysis(
                result.getTitle(),
                getString(R.string.runtime_probability_urgency,
                        result.getProbability(), result.getUrgency()),
                result.getContext(),
                result.getAdvice(),
                growthStatusRequested ? "growth_status" : "health_screening",
                0, null
        );
    }

    private void requestVisionAnalysis(GardenZone zone, List<String> symptoms, String note,
                                       boolean growthStatusRequested) {
        toast(getString(R.string.runtime_visual_ai_preparing));
        viewModel.analyzeVisionAsync(selectedPhotoBitmap, selectedPhotoUri,
                zone, selectedPlantName(), symptoms, note, currentWeather,
                growthStatusRequested,
                visual -> runOnUiThread(() -> renderVisionResult(visual, growthStatusRequested)),
                error -> runOnUiThread(() -> renderVisionFailure(error)));
    }

    private void renderVisionFailure(Throwable error) {
        awaitingVisionResult = false;
        String detail = error.getMessage();
        if (detail == null || detail.isBlank()) detail = error.getClass().getSimpleName();
        Log.e(LOG_TAG, "Plant vision analysis failed: " + detail, error);
        if (AppCheckVerificationException.isAppCheckFailure(error)) {
            title.setText(R.string.runtime_visual_ai_app_check_title);
            meta.setText(getString(R.string.runtime_visual_ai_app_check_meta,
                    BuildConfig.VERSION_NAME + " / " + BuildConfig.BUILD_TYPE));
            advice.setText("release".equals(BuildConfig.BUILD_TYPE)
                    ? R.string.runtime_visual_ai_app_check_release
                    : R.string.runtime_visual_ai_app_check_test);
        } else if ("VISION_API_KEY_INVALID".equals(detail)) {
            title.setText(R.string.runtime_visual_ai_key_invalid);
            meta.setText("VISION_API_KEY_INVALID");
            advice.setText(R.string.runtime_visual_ai_key_invalid_advice);
        } else if (detail.startsWith("VISION_PROVIDER_ERROR:")) {
            title.setText(R.string.runtime_visual_ai_provider_failed);
            meta.setText(detail);
            advice.setText(R.string.runtime_visual_ai_provider_advice);
        } else {
            title.setText(getString(R.string.runtime_visual_ai_unavailable, detail));
            meta.setText("");
            advice.setText(R.string.runtime_visual_ai_retry);
        }
        resultCard.setVisibility(View.VISIBLE);
        applyPendingAnalysis();
    }

    private void renderVisionResult(JSONObject visual, boolean growthStatusRequested) {
        awaitingVisionResult = false;
        if (!visual.optBoolean("is_plant_photo", true)) {
            title.setText(R.string.runtime_photo_quality_title);
            meta.setText(getString(R.string.runtime_visual_confidence_urgency,
                    visual.optInt("confidence", 0), getString(R.string.runtime_urgency_low)));
            context.setText(R.string.runtime_photo_quality_detail);
            advice.setText(R.string.runtime_photo_quality_advice);
            resultCard.setVisibility(View.VISIBLE);
            findViewById(R.id.layoutDoctorGrowthSummary).setVisibility(View.GONE);
            archiveAnalysis(String.valueOf(title.getText()), String.valueOf(meta.getText()),
                    String.valueOf(context.getText()), String.valueOf(advice.getText()),
                    "", visual.optInt("confidence", 0), null);
            return;
        }
        String findings = visual.optString("visual_findings", getString(R.string.runtime_no_visual_findings));
        String causes = viewModel.list(visual.optJSONArray("possible_causes"));
        String steps = viewModel.list(visual.optJSONArray("next_steps"));
        String redFlags = viewModel.list(visual.optJSONArray("red_flags"));
        viewModel.saveRecommendation(
                selectedZone() == null ? "" : selectedZone().getZone_id(),
                selectedSeasonId(),
                visual.optString("urgency", getString(R.string.runtime_urgency_low)),
                visual.optString("title", getString(R.string.runtime_visual_preassessment)),
                steps.isEmpty() ? findings : steps
        );
        title.setText(visual.optString("title", getString(R.string.runtime_visual_preassessment)));
        meta.setText(getString(R.string.runtime_visual_confidence_urgency,
                visual.optInt("confidence", 0),
                visual.optString("urgency", getString(R.string.runtime_urgency_low))));
        context.setText(causes.isEmpty()
                ? findings
                : getString(
                        R.string.runtime_two_sections,
                        findings,
                        getString(
                                R.string.runtime_two_lines,
                                getString(growthStatusRequested
                                        ? R.string.runtime_growth_factors
                                        : R.string.runtime_possible_causes),
                                causes)));
        List<String> adviceSections = new ArrayList<>();
        if (!steps.isEmpty()) {
            adviceSections.add(getString(
                    R.string.runtime_two_lines,
                    getString(growthStatusRequested
                            ? R.string.runtime_growth_follow_up
                            : R.string.runtime_recommended_observation),
                    steps));
        }
        if (!redFlags.isEmpty()) {
            adviceSections.add(getString(
                    R.string.runtime_two_lines,
                    getString(R.string.runtime_red_flags),
                    redFlags));
        }
        adviceSections.add(visual.optString(
                "disclaimer",
                getString(R.string.runtime_not_diagnosis)));
        advice.setText(String.join("\n\n", adviceSections));
        int confidence = visual.optInt("confidence", 0);
        PlantGrowthAssessment growth = null;
        if (growthStatusRequested) {
            int score = visual.optInt("growth_score", -1);
            if (score >= 0 && score <= 100) {
                GardenZone zone = selectedZone();
                growth = viewModel.evaluateGrowth(
                        zone == null ? "" : zone.getZone_id(), selectedSeasonId(), archivedPhotoId,
                        score, confidence, visual.optString("growth_stage"),
                        viewModel.list(visual.optJSONArray("growth_signals")));
                renderGrowthSummary(growth);
            } else {
                findViewById(R.id.layoutDoctorGrowthSummary).setVisibility(View.GONE);
            }
        } else {
            findViewById(R.id.layoutDoctorGrowthSummary).setVisibility(View.GONE);
        }
        resultCard.setVisibility(View.VISIBLE);
        archiveAnalysis(String.valueOf(title.getText()), String.valueOf(meta.getText()),
                String.valueOf(context.getText()), String.valueOf(advice.getText()),
                growthStatusRequested ? "growth_status" : "health_screening",
                confidence, growth);
    }

    private List<String> selectedSymptoms() {
        List<String> items = new ArrayList<>();
        if (yellowing.isChecked()) items.add(getString(R.string.runtime_symptom_yellowing));
        if (drying.isChecked()) items.add(getString(R.string.runtime_symptom_drying));
        if (spot.isChecked()) items.add(getString(R.string.runtime_symptom_spot));
        if (wilt.isChecked()) items.add(getString(R.string.runtime_symptom_wilt));
        if (pest.isChecked()) items.add(getString(R.string.runtime_symptom_fruit_crack));
        if (flowerDrop.isChecked()) items.add(getString(R.string.runtime_symptom_flower_drop));
        if (other.isChecked()) items.add(text(otherNote).isEmpty() ? getString(R.string.runtime_other_observation) : text(otherNote));
        return items;
    }

    private void renderLiveData(GardenZone zone) {
        if (zone == null) return;
        soilData.setText(getString(R.string.format_assistant_soil_data, "%" + zone.getMoisture()));
        weatherTemperatureData.setText(getString(
                R.string.runtime_two_lines,
                getString(R.string.weather_temperature_label),
                number(currentWeather == null
                        ? null
                        : currentWeather.getCurrentTemperature(), "°C")));
        sunData.setText(getString(R.string.format_assistant_sun_data, sunLabel(currentWeather)));
        windData.setText(getString(R.string.runtime_wind_format,
                number(currentWeather == null ? null : currentWeather.getCurrentWind(), " km/sa")));
        humidityData.setText(getString(R.string.format_assistant_humidity_data, number(currentWeather == null ? null : currentWeather.getCurrentHumidity(), "%")));
    }

    private void savePhotoToArchive(GardenZone zone, List<String> symptoms, String note,
                                    boolean growthStatusRequested) {
        if (!hasPhoto() || selectedPhotoArchived) return;
        selectedPhotoArchived = true;
        List<String> selections = new ArrayList<>(symptoms);
        if (growthStatusRequested) {
            selections.add(0, getString(R.string.runtime_growth_status_selection));
        }
        String archiveNote = getString(R.string.runtime_assistant_archive_note, String.join(", ", selections))
                + (note.isEmpty() ? "" : " · " + note);
        Uri uri = selectedPhotoUri;
        Bitmap bitmap = selectedPhotoBitmap;
        viewModel.archivePhotoAsync(uri, bitmap, zone.getZone_id(), selectedSeasonId(), archiveNote,
                saved -> runOnUiThread(() -> {
                    if (saved != null) {
                        archivedPhotoId = saved.getId();
                        applyPendingAnalysis();
                    }
                    toast(getString(R.string.runtime_photo_analysis_archived));
                }), error -> {
                    selectedPhotoArchived = false;
                    runOnUiThread(() -> toast(getString(R.string.runtime_photo_archive_failed)));
                });
    }

    private void archiveAnalysis(String analysisTitle, String analysisMeta,
                                 String analysisContext, String analysisAdvice,
                                 String analysisGoal, int confidence,
                                 PlantGrowthAssessment growth) {
        if (!hasPhoto()) return;
        GardenZone zone = selectedZone();
        pendingAnalysis = new AnalysisSnapshot(analysisTitle, analysisMeta, analysisContext,
                analysisAdvice, zone == null ? "" : zone.getZone_id(), selectedSeasonId(), analysisGoal,
                confidence, growth);
        applyPendingAnalysis();
    }

    private void applyPendingAnalysis() {
        if (awaitingVisionResult || pendingAnalysis == null || archivedPhotoId.isBlank()) return;
        AnalysisSnapshot snapshot = pendingAnalysis;
        pendingAnalysis = null;
        viewModel.finalizeAnalysisAsync(archivedPhotoId, snapshot.zoneId, snapshot.seasonId, snapshot.title,
                snapshot.meta, snapshot.context, snapshot.advice, snapshot.analysisGoal,
                snapshot.confidence, snapshot.growth,
                error -> runOnUiThread(() -> {
                    Log.w(LOG_TAG, "Plant analysis metadata sync failed", error);
                    toast(getString(R.string.runtime_photo_metadata_sync_failed));
                }));
    }

    private static final class AnalysisSnapshot {
        final String title, meta, context, advice, zoneId, seasonId, analysisGoal;
        final int confidence;
        final PlantGrowthAssessment growth;

        AnalysisSnapshot(String title, String meta, String context, String advice,
                         String zoneId, String seasonId, String analysisGoal, int confidence,
                         PlantGrowthAssessment growth) {
            this.title = title;
            this.meta = meta;
            this.context = context;
            this.advice = advice;
            this.zoneId = zoneId;
            this.seasonId = seasonId;
            this.analysisGoal = analysisGoal;
            this.confidence = confidence;
            this.growth = growth;
        }
    }

    private void openGrowthHistory() {
        GardenZone zone = selectedZone();
        if (zone == null) {
            toast(getString(plants.isEmpty()
                    ? R.string.runtime_no_active_season_zones
                    : R.string.runtime_select_zone_first));
            return;
        }
        Intent intent = new Intent(this, PlantGrowthTrackingActivity.class);
        intent.putExtra(PlantGrowthTrackingActivity.EXTRA_ZONE_ID, zone.getZone_id());
        intent.putExtra(PlantGrowthTrackingActivity.EXTRA_ZONE_LABEL, labelFor(selectedPlant()));
        intent.putExtra(PlantGrowthTrackingActivity.EXTRA_SEASON_ID, selectedSeasonId());
        startActivity(intent);
    }

    private void renderGrowthSummary(PlantGrowthAssessment growth) {
        findViewById(R.id.layoutDoctorGrowthSummary).setVisibility(View.VISIBLE);
        growthScore.setText(getString(R.string.runtime_growth_score_format, growth.getScore()));
        growthStage.setText(getString(R.string.runtime_growth_stage_format,
                growth.getStage().isEmpty()
                        ? getString(R.string.runtime_not_available_short) : growth.getStage()));
        growthTrend.setText(growthTrendLabel(growth.getTrend()));
        int trendColor = growth.isImproving()
                ? R.color.success
                : growth.isDeclining()
                ? R.color.error
                : growth.isStable()
                ? R.color.info : R.color.textSecondary;
        growthTrend.setTextColor(ContextCompat.getColor(this, trendColor));
        growthComparison.setText(growth.isFirstRecord()
                ? getString(R.string.runtime_growth_first_record_detail)
                : getResources().getQuantityString(R.plurals.runtime_growth_delta_format,
                Math.abs(growth.getScoreDelta()), growth.getScoreDelta(),
                formatGrowthDate(growth.getPreviousCapturedAtEpoch())));
        growthSignals.setText(growth.getSignals().isEmpty()
                ? getString(R.string.runtime_growth_no_signals)
                : getString(R.string.runtime_growth_signals_format, growth.getSignals()));
    }

    private String growthTrendLabel(String trend) {
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

    private String formatGrowthDate(long epoch) {
        if (epoch <= 0L) return getString(R.string.runtime_unknown_date);
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(epoch * 1000L));
    }

    private PlantSelection selectedPlant() {
        return plants.get(String.valueOf(zoneDropdown.getText()));
    }
    private GardenZone selectedZone() {
        PlantSelection selected = selectedPlant();
        return selected == null ? null : selected.zone;
    }
    private String selectedSeasonId() {
        PlantSelection selected = selectedPlant();
        return selected == null ? "" : selected.seasonId();
    }
    private String selectedPlantName() {
        PlantSelection selected = selectedPlant();
        return selected == null ? "" : SeasonDisplayIdentity.name(selected.season, selected.zone);
    }
    private boolean hasPhoto() { return selectedPhotoUri != null || selectedPhotoBitmap != null; }
    private String labelFor(PlantSelection plant) {
        return plant == null ? ""
                : SeasonDisplayIdentity.stackedCropAreaLabel(plant.season, plant.zone);
    }
    private String text(TextInputEditText input) { return input.getText() == null ? "" : input.getText().toString().trim(); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
    private String number(Double value, String suffix) { return value == null ? "—" : Math.round(value) + suffix; }
    private String sunLabel(WeatherForecast weather) {
        if (weather == null || weather.getCurrentWeatherCode() == null) return "—";
        return getString(weather.getCurrentWeatherCode() <= 1
                ? R.string.runtime_sun_strong : weather.getCurrentWeatherCode() <= 3
                ? R.string.runtime_sun_medium : R.string.runtime_sun_low);
    }

    private void showPhoto(Uri uri) {
        selectedPhotoArchived = false;
        archivedPhotoId = "";
        pendingAnalysis = null;
        awaitingVisionResult = false;
        selectedPhotoUri = uri;
        selectedPhotoBitmap = null;
        photoPreview.setImageURI(uri);
        photoPreview.setVisibility(View.VISIBLE);
        photoHintLayout.setVisibility(View.GONE);
    }

    private void showPhoto(Bitmap bitmap) {
        selectedPhotoArchived = false;
        archivedPhotoId = "";

        pendingAnalysis = null;
        awaitingVisionResult = false;
        selectedPhotoBitmap = bitmap;
        selectedPhotoUri = null;
        photoPreview.setImageBitmap(bitmap);
        photoPreview.setVisibility(View.VISIBLE);
        photoHintLayout.setVisibility(View.GONE);
    }
    private static final class PlantSelection {
        final GardenZone zone;
        final GardenSeason season;
        final String label;

        PlantSelection(GardenZone zone, GardenSeason season, String label) {
            this.zone = zone;
            this.season = season;
            this.label = label == null ? "" : label;
        }

        String seasonId() {
            return season == null || season.getSeason_id() == null
                    ? "" : season.getSeason_id();
        }
    }

}
