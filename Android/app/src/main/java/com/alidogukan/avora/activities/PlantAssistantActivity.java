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
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
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
import com.google.android.material.button.MaterialButton;
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
    private static final String STATE_SELECTED_PLANT = "plant_selected_key";
    private static final String STATE_REQUESTED_ZONE = "plant_requested_zone";
    private static final String STATE_REQUESTED_SEASON = "plant_requested_season";
    private static final String STATE_SELECTED_PHOTO = "plant_selected_photo";
    private static final String STATE_PHOTO_ARCHIVED = "plant_photo_archived";
    private static final String STATE_ARCHIVED_PHOTO_ID = "plant_archived_photo_id";
    private static final String STATE_PENDING_CAPTURE = "plant_pending_capture";
    private static final String STATE_CAPTURED_PHOTO = "plant_captured_photo";
    private PlantAssistantViewModel viewModel;
    private com.alidogukan.avora.viewmodels.DisplayUnitsViewModel displayUnits;
    private final Map<String, PlantSelection> plants = new HashMap<>();
    private final List<GardenZone> latestZones = new ArrayList<>();
    private final List<GardenSeason> latestSeasons = new ArrayList<>();

    private MaterialAutoCompleteTextView zoneDropdown;
    private MaterialCardView resultCard;
    private View photoPickerCard;
    private MaterialButton analyzeButton;
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
    private String selectedPlantKey = "";
    private boolean zonesLoaded;
    private boolean seasonsLoaded;
    private AnalysisRequest activeAnalysis;
    private long analysisSequence;
    private long photoPreviewSequence;
    private boolean destroyed;
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
        displayUnits = new ViewModelProvider(this)
                .get(com.alidogukan.avora.viewmodels.DisplayUnitsViewModel.class);
        PrimaryBottomNavigation.bind(this, PrimaryBottomNavigation.ASSISTANT);
        requestedZoneId = safe(getIntent().getStringExtra("zone_id"));
        requestedSeasonId = safe(getIntent().getStringExtra("season_id"));
        bindViews();
        bindActions();
        if (state != null) restoreInstanceState(state);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { requestExit(); }
        });
        viewModel.getZones().observe(this, items -> {
            latestZones.clear();
            if (items != null) latestZones.addAll(viewModel.activeZones(items));
            zonesLoaded = true;
            renderPlantSelections();
        });
        viewModel.getSeasons().observe(this, items -> {
            latestSeasons.clear();
            if (items != null) latestSeasons.addAll(items);
            seasonsLoaded = true;
            renderPlantSelections();
        });
        viewModel.getWeather().observe(this, weather -> {
            currentWeather = weather;
            renderLiveData(selectedZone());
        });
        viewModel.getPhotoMetadata().observe(this, ignored -> { });
        viewModel.getAnalysisInProgress().observe(this, running ->
                setAnalysisControlsEnabled(!Boolean.TRUE.equals(running)));
    }

    private void bindViews() {
        zoneDropdown = findViewById(R.id.dropdownDoctorZone);
        resultCard = findViewById(R.id.cardDoctorResult);
        photoPickerCard = findViewById(R.id.cardDoctorPhotoPicker);
        analyzeButton = findViewById(R.id.btnAnalyzePlant);
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
        findViewById(R.id.btnDoctorBack).setOnClickListener(view -> requestExit());
        findViewById(R.id.btnPlantAssistantIntroInfo).setOnClickListener(
                view -> showAssistantInformation());
        photoPickerCard.setOnClickListener(view -> showPhotoSourceDialog());
        analyzeButton.setOnClickListener(view -> analyze());
        findViewById(R.id.btnPlantGrowthHistory).setOnClickListener(
                view -> openGrowthHistory());
        other.setOnCheckedChangeListener((button, checked) -> {
            otherNoteLayout.setVisibility(checked ? View.VISIBLE : View.GONE);
            if (!checked) otherNote.setText("");
        });
    }

    private void showAssistantInformation() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.fertilization_zone_assistant_action)
                .setMessage(R.string.assistant_intro_plant_info)
                .setPositiveButton(android.R.string.ok, null)
                .show();
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
        if (!zonesLoaded || !seasonsLoaded) return;
        if (activeAnalysis != null) return;
        String previousSelectionKey = selectedPlantKey;
        plants.clear();
        List<String> labels = new ArrayList<>();
        PlantSelection requested = null;
        PlantSelection requestedZoneFallback = null;
        PlantSelection retained = null;
        for (GardenZone zone : latestZones) {
            List<GardenSeason> active = SeasonDisplayIdentity.activeSeasons(
                    zone, latestSeasons);
            for (GardenSeason season : active) {
                PlantSelection selection = new PlantSelection(zone, season, "");
                String label = labelFor(selection);
                if (plants.containsKey(label)) {
                    long started = season == null ? 0L : season.getStarted_at_epoch();
                    label += " · " + (started <= 0L ? selection.seasonId()
                            : DateFormat.getDateInstance(DateFormat.SHORT)
                            .format(new Date(started * 1000L)));
                }
                if (plants.containsKey(label)) {
                    String suffix = selection.seasonId().isBlank()
                            ? safe(zone.getZone_id()) : selection.seasonId();
                    label += " · " + suffix;
                }
                String uniqueLabel = label;
                int duplicateIndex = 2;
                while (plants.containsKey(uniqueLabel)) {
                    uniqueLabel = label + " (" + duplicateIndex++ + ")";
                }
                label = uniqueLabel;
                selection = new PlantSelection(zone, season, label);
                labels.add(label);
                plants.put(label, selection);
                if (selectionKey(selection).equals(previousSelectionKey)) {
                    retained = selection;
                }
                boolean zoneRequested = safe(zone.getZone_id()).equals(requestedZoneId);
                boolean seasonRequested = !requestedSeasonId.isBlank()
                        && requestedSeasonId.equals(selection.seasonId());
                if (zoneRequested && requestedZoneFallback == null) {
                    requestedZoneFallback = selection;
                }
                if (zoneRequested && (seasonRequested
                        || (requestedSeasonId.isBlank() && requested == null))) {
                    requested = selection;
                }
            }
        }
        zoneDropdown.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels));
        zoneDropdown.setHint(labels.isEmpty()
                ? getString(R.string.runtime_no_active_season_zones)
                : null);
        zoneDropdown.setOnItemClickListener((parent, view, position, id) -> {
            PlantSelection selection = position >= 0 && position < labels.size()
                    ? plants.get(labels.get(position)) : selectedPlant();
            String newKey = selectionKey(selection);
            boolean changed = !selectedPlantKey.isBlank()
                    && !selectedPlantKey.equals(newKey);
            selectedPlantKey = newKey;
            requestedZoneId = "";
            requestedSeasonId = "";
            resultCard.setVisibility(View.GONE);
            if (changed) clearSelectedPhoto();
            renderLiveData(selection == null ? null : selection.zone);
        });
        boolean requestedSelectionUnavailable = !requestedZoneId.isBlank()
                && requested == null
                && (!requestedSeasonId.isBlank() || requestedZoneFallback == null);
        if (requestedSelectionUnavailable) {
            requestedZoneId = "";
            requestedSeasonId = "";
            if (!previousSelectionKey.isBlank()) clearSelectedPhoto();
            resultCard.setVisibility(View.GONE);
            clearLiveData();
            zoneDropdown.setText("", false);
            selectedPlantKey = "";
            toast(getString(R.string.runtime_requested_plant_unavailable));
            return;
        }
        PlantSelection target = requested != null
                ? requested : requestedZoneFallback != null ? requestedZoneFallback : retained;
        if (target == null && !labels.isEmpty()) target = plants.get(labels.get(0));
        if (target == null) {
            if (!previousSelectionKey.isBlank()) clearSelectedPhoto();
            resultCard.setVisibility(View.GONE);
            clearLiveData();
            zoneDropdown.setText("", false);
            selectedPlantKey = "";
            return;
        }
        String targetKey = selectionKey(target);
        if (!previousSelectionKey.isBlank() && !previousSelectionKey.equals(targetKey)) {
            clearSelectedPhoto();
            resultCard.setVisibility(View.GONE);
        }
        zoneDropdown.setText(target.label, false);
        selectedPlantKey = targetKey;
        renderLiveData(target.zone);
    }

    private void analyze() {
        if (activeAnalysis != null) {
            toast(getString(R.string.runtime_plant_analysis_in_progress));
            return;
        }
        PlantSelection selection = selectedPlant();
        GardenZone zone = selection == null ? null : selection.zone;
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
        AnalysisRequest request = new AnalysisRequest(
                ++analysisSequence,
                zone,
                selection == null ? "" : selection.seasonId(),
                selection == null ? "" : SeasonDisplayIdentity.name(selection.season, zone),
                symptoms,
                note,
                currentWeather,
                selectedPhotoUri,
                selectedPhotoBitmap,
                selectedPhotoArchived ? archivedPhotoId : "",
                growthStatusRequested
        );
        PlantAssistantResult result = viewModel.assess(
                zone, symptoms, note, currentWeather, request.hasPhoto(), growthStatusRequested);
        if (request.hasPhoto()) {
            int operationCount = request.archiveComplete ? 2 : 3;
            if (!viewModel.tryBeginAnalysis(operationCount)) {
                toast(getString(R.string.runtime_plant_analysis_in_progress));
                return;
            }
            activeAnalysis = request;
            setAnalysisControlsEnabled(false);
        }
        renderHeuristicResult(request, result);
        if (!request.hasPhoto()) return;
        requestVisionAnalysis(request);
        if (!request.archiveComplete) savePhotoToArchive(request);
    }

    private void renderHeuristicResult(AnalysisRequest request,
                                       PlantAssistantResult result) {
        title.setText(result.getTitle());
        meta.setText(getString(R.string.runtime_probability_urgency,
                result.getProbability(), result.getUrgency()));
        context.setText(getString(R.string.runtime_evaluated_context,
                result.getContext()));
        advice.setText(result.getAdvice());
        findViewById(R.id.layoutDoctorGrowthSummary).setVisibility(View.GONE);
        if (!request.hasPhoto()) {
            viewModel.saveRecommendation(
                    request.zoneId,
                    request.seasonId,
                    result.getUrgency(),
                    result.getTitle(),
                    result.getAdvice()
            );
        }
        resultCard.setVisibility(View.VISIBLE);
        archiveAnalysis(request,
                result.getTitle(),
                getString(R.string.runtime_probability_urgency,
                        result.getProbability(), result.getUrgency()),
                result.getContext(),
                result.getAdvice(),
                request.growthStatusRequested ? "growth_status" : "health_screening",
                result.getUrgency(), 0, null
        );
    }

    private void requestVisionAnalysis(AnalysisRequest request) {
        toast(getString(R.string.runtime_visual_ai_preparing));
        viewModel.analyzeVisionAsync(request.photoBitmap, request.photoUri,
                request.zone, request.plantName, request.symptoms, request.note, request.weather,
                request.growthStatusRequested,
                visual -> runOnUiThread(() -> {
                    if (activeAnalysis != request) return;
                    if (destroyed) {
                        request.visionComplete = true;
                        request.pendingAnalysis = null;
                        finishAnalysisWhenReady(request);
                    } else {
                        renderVisionResult(request, visual);
                    }
                }),
                error -> runOnUiThread(() -> {
                    if (activeAnalysis != request) return;
                    if (destroyed) {
                        request.visionComplete = true;
                        request.pendingAnalysis = null;
                        finishAnalysisWhenReady(request);
                    } else {
                        renderVisionFailure(request, error);
                    }
                }));
    }

    private void renderVisionFailure(AnalysisRequest request, Throwable error) {
        request.visionComplete = true;
        String detail = error.getMessage();
        if (detail == null || detail.isBlank()) detail = error.getClass().getSimpleName();
        Log.e(LOG_TAG, "Plant vision analysis failed: " + detail, error);
        if (AppCheckVerificationException.isAppCheckFailure(error)
                || "UNAUTHORIZED".equals(detail)) {
            title.setText(R.string.runtime_visual_ai_app_check_title);
            meta.setText(getString(R.string.runtime_visual_ai_app_check_meta,
                    BuildConfig.VERSION_NAME + " / " + BuildConfig.BUILD_TYPE));
            advice.setText("release".equals(BuildConfig.BUILD_TYPE)
                    ? R.string.runtime_visual_ai_app_check_release
                    : R.string.runtime_visual_ai_app_check_test);
        } else if ("VISION_API_KEY_INVALID".equals(detail)) {
            title.setText(R.string.runtime_visual_ai_key_invalid);
            meta.setText(detail);
            advice.setText(R.string.runtime_visual_ai_key_invalid_advice);
        } else if ("VISION_NOT_CONFIGURED".equals(detail)) {
            title.setText(R.string.runtime_visual_ai_not_configured_title);
            meta.setText(detail);
            advice.setText(R.string.runtime_visual_ai_not_configured_advice);
        } else if (isPhotoInputError(detail)) {
            title.setText(R.string.runtime_photo_processing_failed_title);
            meta.setText(detail);
            advice.setText(R.string.runtime_photo_processing_failed_advice);
        } else if (detail.startsWith("VISION_PROVIDER_ERROR:")
                || "VISION_TIMEOUT".equals(detail)
                || "VISION_PROVIDER_TIMEOUT".equals(detail)
                || "VISION_PROVIDER_UNAVAILABLE".equals(detail)
                || "VISION_INVALID_RESPONSE".equals(detail)) {
            title.setText(R.string.runtime_visual_ai_provider_failed);
            meta.setText(detail);
            advice.setText(R.string.runtime_visual_ai_provider_advice);
        } else {
            title.setText(getString(R.string.runtime_visual_ai_unavailable, detail));
            meta.setText("");
            advice.setText(R.string.runtime_visual_ai_retry);
        }
        resultCard.setVisibility(View.VISIBLE);
        request.pendingAnalysis = null;
        finishAnalysisWhenReady(request);
    }

    private static boolean isPhotoInputError(String detail) {
        return "PHOTO_DECODE_FAILED".equals(detail)
                || "PHOTO_ENCODE_FAILED".equals(detail)
                || "INVALID_IMAGE".equals(detail)
                || "IMAGE_TOO_LARGE".equals(detail)
                || "REQUEST_TOO_LARGE".equals(detail)
                || "UNSUPPORTED_IMAGE_TYPE".equals(detail);
    }

    private void renderVisionResult(AnalysisRequest request, JSONObject visual) {
        request.visionComplete = true;
        if (!visual.optBoolean("is_plant_photo", true)) {
            title.setText(R.string.runtime_photo_quality_title);
            meta.setText(getString(R.string.runtime_visual_confidence_urgency,
                    visual.optInt("confidence", 0), getString(R.string.runtime_urgency_low)));
            context.setText(R.string.runtime_photo_quality_detail);
            advice.setText(R.string.runtime_photo_quality_advice);
            resultCard.setVisibility(View.VISIBLE);
            findViewById(R.id.layoutDoctorGrowthSummary).setVisibility(View.GONE);
            archiveAnalysis(request,
                    String.valueOf(title.getText()), String.valueOf(meta.getText()),
                    String.valueOf(context.getText()), String.valueOf(advice.getText()),
                    "", getString(R.string.runtime_urgency_low),
                    visual.optInt("confidence", 0), null);
            return;
        }
        String findings = visual.optString("visual_findings", getString(R.string.runtime_no_visual_findings));
        String causes = viewModel.list(visual.optJSONArray("possible_causes"));
        String steps = viewModel.list(visual.optJSONArray("next_steps"));
        String redFlags = viewModel.list(visual.optJSONArray("red_flags"));
        String urgency = visual.optString(
                "urgency", getString(R.string.runtime_urgency_low));
        title.setText(visual.optString("title", getString(R.string.runtime_visual_preassessment)));
        meta.setText(getString(R.string.runtime_visual_confidence_urgency,
                visual.optInt("confidence", 0),
                urgency));
        context.setText(causes.isEmpty()
                ? findings
                : getString(
                        R.string.runtime_two_sections,
                        findings,
                        getString(
                                R.string.runtime_two_lines,
                                getString(request.growthStatusRequested
                                        ? R.string.runtime_growth_factors
                                        : R.string.runtime_possible_causes),
                                causes)));
        List<String> adviceSections = new ArrayList<>();
        if (!steps.isEmpty()) {
            adviceSections.add(getString(
                    R.string.runtime_two_lines,
                    getString(request.growthStatusRequested
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
        if (request.growthStatusRequested) {
            int score = visual.optInt("growth_score", -1);
            if (score >= 0 && score <= 100) {
                growth = viewModel.evaluateGrowth(
                        request.zoneId, request.seasonId, request.archiveId,
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
        archiveAnalysis(request,
                String.valueOf(title.getText()), String.valueOf(meta.getText()),
                String.valueOf(context.getText()), String.valueOf(advice.getText()),
                request.growthStatusRequested ? "growth_status" : "health_screening",
                urgency, confidence, growth);
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
        if (zone == null) {
            clearLiveData();
            return;
        }
        boolean sensorCurrent = viewModel.isSensorDataCurrent(zone);
        WeatherForecast currentForecast = viewModel.currentWeatherOrNull(currentWeather);
        soilData.setText(getString(R.string.format_assistant_soil_data,
                sensorCurrent ? "%" + zone.getMoisture() : "—"));
        weatherTemperatureData.setText(getString(
                R.string.runtime_two_lines,
                getString(R.string.weather_temperature_label),
                currentForecast == null || currentForecast.getCurrentTemperature() == null
                        ? "—" : displayUnits.formatTemperature(
                        currentForecast.getCurrentTemperature())));
        sunData.setText(getString(R.string.format_assistant_sun_data, sunLabel(currentForecast)));
        windData.setText(getString(R.string.runtime_wind_format,
                number(currentForecast == null ? null : currentForecast.getCurrentWind(), " km/sa")));
        humidityData.setText(getString(R.string.format_assistant_humidity_data,
                number(currentForecast == null ? null : currentForecast.getCurrentHumidity(), "%")));
    }

    private void clearLiveData() {
        soilData.setText(getString(R.string.format_assistant_soil_data, "—"));
        weatherTemperatureData.setText(getString(
                R.string.runtime_two_lines,
                getString(R.string.weather_temperature_label), "—"));
        sunData.setText(getString(R.string.format_assistant_sun_data, "—"));
        windData.setText(getString(R.string.runtime_wind_format, "—"));
        humidityData.setText(getString(R.string.format_assistant_humidity_data, "—"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (displayUnits != null) renderLiveData(selectedZone());
    }

    private void savePhotoToArchive(AnalysisRequest request) {
        List<String> selections = new ArrayList<>(request.symptoms);
        if (request.growthStatusRequested) {
            selections.add(0, getString(R.string.runtime_growth_status_selection));
        }
        String archiveNote = getString(R.string.runtime_assistant_archive_note, String.join(", ", selections))
                + (request.note.isEmpty() ? "" : " · " + request.note);
        viewModel.archivePhotoAsync(
                request.photoUri,
                request.photoBitmap,
                request.zoneId,
                request.seasonId,
                archiveNote,
                saved -> runOnUiThread(() -> {
                    if (activeAnalysis != request) return;
                    if (saved == null || safe(saved.getId()).isBlank()) {
                        request.archiveFailed = true;
                        if (!destroyed) {
                            selectedPhotoArchived = false;
                            toast(getString(R.string.runtime_photo_archive_failed));
                        }
                    } else {
                        request.archiveId = saved.getId();
                        request.archiveComplete = true;
                        if (!destroyed) {
                            archivedPhotoId = saved.getId();
                            selectedPhotoArchived = true;
                        }
                    }
                    finishAnalysisWhenReady(request);
                }), error -> runOnUiThread(() -> {
                    if (activeAnalysis != request) return;
                    request.archiveFailed = true;
                    Log.w(LOG_TAG, "Plant photo archive failed", error);
                    if (!destroyed) {
                        selectedPhotoArchived = false;
                        toast(getString(R.string.runtime_photo_archive_failed));
                    }
                    finishAnalysisWhenReady(request);
                }));
    }

    private void archiveAnalysis(AnalysisRequest request,
                                 String analysisTitle, String analysisMeta,
                                 String analysisContext, String analysisAdvice,
                                 String analysisGoal, String urgency, int confidence,
                                 PlantGrowthAssessment growth) {
        if (!request.hasPhoto()) return;
        request.pendingAnalysis = new AnalysisSnapshot(
                analysisTitle, analysisMeta, analysisContext, analysisAdvice,
                analysisGoal, urgency, confidence, growth);
        finishAnalysisWhenReady(request);
    }

    private void finishAnalysisWhenReady(AnalysisRequest request) {
        if (activeAnalysis != request || !request.visionComplete
                || request.completionStarted) return;
        if (request.archiveFailed) {
            request.completionStarted = true;
            request.pendingAnalysis = null;
            viewModel.skipAnalysisFinalization();
            completeAnalysis(request, false);
            return;
        }
        if (!request.archiveComplete || request.archiveId.isBlank()) return;
        AnalysisSnapshot snapshot = request.pendingAnalysis;
        request.pendingAnalysis = null;
        request.completionStarted = true;
        if (snapshot == null) {
            viewModel.skipAnalysisFinalization();
            if (!request.startedArchived && !destroyed) {
                toast(getString(R.string.runtime_photo_archived_analysis_failed));
            }
            completeAnalysis(request, false);
            return;
        }
        viewModel.finalizeAnalysisAsync(
                request.archiveId, request.zoneId, request.seasonId, snapshot.title,
                snapshot.meta, snapshot.context, snapshot.advice, snapshot.analysisGoal,
                snapshot.confidence, snapshot.urgency, snapshot.growth,
                saved -> runOnUiThread(() -> completeAnalysis(request, saved)),
                error -> runOnUiThread(() -> {
                    Log.w(LOG_TAG, "Plant analysis metadata sync failed", error);
                    if (!destroyed) toast(getString(R.string.runtime_photo_metadata_sync_failed));
                }));
    }

    private void completeAnalysis(AnalysisRequest request, boolean analysisSaved) {
        if (activeAnalysis != request) return;
        activeAnalysis = null;
        if (destroyed) return;
        setAnalysisControlsEnabled(true);
        if (analysisSaved) {
            toast(getString(request.startedArchived
                    ? R.string.runtime_analysis_archived
                    : R.string.runtime_photo_analysis_archived));
        }
    }

    private static final class AnalysisSnapshot {
        final String title, meta, context, advice, analysisGoal, urgency;
        final int confidence;
        final PlantGrowthAssessment growth;

        AnalysisSnapshot(String title, String meta, String context, String advice,
                         String analysisGoal, String urgency, int confidence,
                         PlantGrowthAssessment growth) {
            this.title = title;
            this.meta = meta;
            this.context = context;
            this.advice = advice;
            this.analysisGoal = analysisGoal;
            this.urgency = urgency;
            this.confidence = confidence;
            this.growth = growth;
        }
    }

    private static final class AnalysisRequest {
        final long id;
        final GardenZone zone;
        final String zoneId, seasonId, plantName, note;
        final List<String> symptoms;
        final WeatherForecast weather;
        final Uri photoUri;
        final Bitmap photoBitmap;
        final boolean growthStatusRequested;
        boolean visionComplete;
        boolean archiveComplete;
        boolean archiveFailed;
        boolean completionStarted;
        final boolean startedArchived;
        String archiveId;
        AnalysisSnapshot pendingAnalysis;

        AnalysisRequest(long id, GardenZone zone, String seasonId, String plantName,
                        List<String> symptoms, String note, WeatherForecast weather,
                        Uri photoUri, Bitmap photoBitmap, String archiveId,
                        boolean growthStatusRequested) {
            this.id = id;
            this.zone = zone;
            this.zoneId = safe(zone == null ? "" : zone.getZone_id());
            this.seasonId = safe(seasonId);
            this.plantName = safe(plantName);
            this.symptoms = new ArrayList<>(symptoms);
            this.note = safe(note);
            this.weather = weather;
            this.photoUri = photoUri;
            this.photoBitmap = photoBitmap;
            this.archiveId = safe(archiveId);
            this.archiveComplete = !this.archiveId.isBlank();
            this.startedArchived = this.archiveComplete;
            this.growthStatusRequested = growthStatusRequested;
        }

        boolean hasPhoto() {
            return photoUri != null || photoBitmap != null;
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
        final long previewSequence = ++photoPreviewSequence;
        releaseSelectedPhotoBitmap();
        selectedPhotoArchived = false;
        archivedPhotoId = "";
        selectedPhotoUri = uri;
        selectedPhotoBitmap = null;
        photoPreview.setImageDrawable(null);
        photoPreview.setVisibility(View.GONE);
        photoHintLayout.setVisibility(View.VISIBLE);
        viewModel.loadPhotoPreviewAsync(uri,
                bitmap -> runOnUiThread(() -> {
                    if (destroyed || previewSequence != photoPreviewSequence
                            || !uri.equals(selectedPhotoUri)) {
                        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                        return;
                    }
                    selectedPhotoBitmap = bitmap;
                    photoPreview.setImageBitmap(bitmap);
                    photoPreview.setVisibility(View.VISIBLE);
                    photoHintLayout.setVisibility(View.GONE);
                }),
                error -> runOnUiThread(() -> {
                    if (destroyed || previewSequence != photoPreviewSequence
                            || !uri.equals(selectedPhotoUri)) return;
                    Log.w(LOG_TAG, "Plant photo preview failed", error);
                    clearSelectedPhoto();
                    toast(getString(R.string.runtime_photo_preview_failed));
                }));
    }

    private void showPhoto(Bitmap bitmap) {
        releaseSelectedPhotoBitmap();
        selectedPhotoArchived = false;
        archivedPhotoId = "";
        selectedPhotoBitmap = bitmap;
        selectedPhotoUri = null;
        photoPreview.setImageBitmap(bitmap);
        photoPreview.setVisibility(View.VISIBLE);
        photoHintLayout.setVisibility(View.GONE);
    }

    private void clearSelectedPhoto() {
        if (activeAnalysis != null) return;
        photoPreviewSequence++;
        discardCapturedCameraPhoto();
        releaseSelectedPhotoBitmap();
        selectedPhotoUri = null;
        selectedPhotoArchived = false;
        archivedPhotoId = "";
        photoPreview.setImageDrawable(null);
        photoPreview.setVisibility(View.GONE);
        photoHintLayout.setVisibility(View.VISIBLE);
    }

    private void releaseSelectedPhotoBitmap() {
        if (selectedPhotoBitmap != null && !selectedPhotoBitmap.isRecycled()) {
            selectedPhotoBitmap.recycle();
        }
        selectedPhotoBitmap = null;
    }

    private void setAnalysisControlsEnabled(boolean enabled) {
        zoneDropdown.setEnabled(enabled);
        photoPickerCard.setEnabled(enabled);
        analyzeButton.setEnabled(enabled);
        generalNote.setEnabled(enabled);
        otherNote.setEnabled(enabled);
        growthStatus.setEnabled(enabled);
        yellowing.setEnabled(enabled);
        drying.setEnabled(enabled);
        spot.setEnabled(enabled);
        wilt.setEnabled(enabled);
        pest.setEnabled(enabled);
        flowerDrop.setEnabled(enabled);
        other.setEnabled(enabled);
        findViewById(R.id.btnPlantGrowthHistory).setEnabled(enabled);
        findViewById(R.id.navPrimaryHome).setEnabled(enabled);
        findViewById(R.id.navPrimaryPlants).setEnabled(enabled);
        findViewById(R.id.navPrimaryAssistant).setEnabled(enabled);
        findViewById(R.id.navPrimaryNotifications).setEnabled(enabled);
        findViewById(R.id.navPrimarySettings).setEnabled(enabled);
        if (enabled) renderPlantSelections();
    }

    private void restoreInstanceState(Bundle state) {
        selectedPlantKey = safe(state.getString(STATE_SELECTED_PLANT));
        if (selectedPlantKey.isBlank()) {
            requestedZoneId = safe(state.getString(STATE_REQUESTED_ZONE, requestedZoneId));
            requestedSeasonId = safe(state.getString(STATE_REQUESTED_SEASON, requestedSeasonId));
        } else {
            requestedZoneId = "";
            requestedSeasonId = "";
        }
        pendingCameraPhoto = restoreCapture(state.getString(STATE_PENDING_CAPTURE));
        capturedCameraPhoto = restoreCapture(state.getString(STATE_CAPTURED_PHOTO));
        String photo = safe(state.getString(STATE_SELECTED_PHOTO));
        if (!photo.isBlank()) {
            try {
                showPhoto(Uri.parse(photo));
                selectedPhotoArchived = state.getBoolean(STATE_PHOTO_ARCHIVED, false);
                archivedPhotoId = safe(state.getString(STATE_ARCHIVED_PHOTO_ID));
            } catch (Exception error) {
                Log.w(LOG_TAG, "Saved plant photo could not be restored", error);
                clearSelectedPhoto();
            }
        }
    }

    private GardenPhotoCapture.Target restoreCapture(String path) {
        try {
            return GardenPhotoCapture.restore(this, path);
        } catch (Exception error) {
            Log.w(LOG_TAG, "Camera capture could not be restored", error);
            return null;
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString(STATE_SELECTED_PLANT, selectedPlantKey);
        outState.putString(STATE_REQUESTED_ZONE, requestedZoneId);
        outState.putString(STATE_REQUESTED_SEASON, requestedSeasonId);
        outState.putString(STATE_SELECTED_PHOTO,
                selectedPhotoUri == null ? "" : selectedPhotoUri.toString());
        outState.putBoolean(STATE_PHOTO_ARCHIVED, selectedPhotoArchived);
        outState.putString(STATE_ARCHIVED_PHOTO_ID, archivedPhotoId);
        outState.putString(STATE_PENDING_CAPTURE, pendingCameraPhoto == null
                ? "" : pendingCameraPhoto.getAbsolutePath());
        outState.putString(STATE_CAPTURED_PHOTO, capturedCameraPhoto == null
                ? "" : capturedCameraPhoto.getAbsolutePath());
        super.onSaveInstanceState(outState);
    }

    private void requestExit() {
        if (activeAnalysis != null
                || Boolean.TRUE.equals(viewModel.getAnalysisInProgress().getValue())) {
            toast(getString(R.string.runtime_plant_analysis_in_progress));
            return;
        }
        finish();
    }

    private static String selectionKey(PlantSelection selection) {
        if (selection == null) return "";
        return safe(selection.zone == null ? "" : selection.zone.getZone_id())
                + "\n" + safe(selection.seasonId());
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        photoPreviewSequence++;
        boolean hadActiveAnalysis = activeAnalysis != null;
        if (hadActiveAnalysis && !isChangingConfigurations()) {
            activeAnalysis = null;
        }
        if (isFinishing() && !hadActiveAnalysis) {
            if (pendingCameraPhoto != null) pendingCameraPhoto.delete();
            pendingCameraPhoto = null;
            discardCapturedCameraPhoto();
            releaseSelectedPhotoBitmap();
        }
        super.onDestroy();
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
