package com.alidogukan.avora.activities;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.TextView;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alidogukan.avora.R;
import com.alidogukan.avora.adapters.DecisionStepAdapter;
import com.alidogukan.avora.models.AIDecision;
import com.alidogukan.avora.models.AdaptiveRecommendation;
import com.alidogukan.avora.models.AIExplanation;
import com.alidogukan.avora.ui.DecisionFlowFactory;
import com.alidogukan.avora.ui.PrimaryBottomNavigation;
import com.alidogukan.avora.ui.AssistantIntroCard;
import com.alidogukan.avora.ui.irrigationassistant.IrrigationAssistantFormatter;
import com.alidogukan.avora.ui.irrigationassistant.PredictionValidationRenderer;
import com.alidogukan.avora.ui.irrigationassistant.SelectedZoneSummaryRenderer;
import com.alidogukan.avora.viewmodels.MainViewModel;
import com.alidogukan.avora.models.PredictionValidationStatus;
import com.alidogukan.avora.models.MoisturePrediction;
import com.alidogukan.avora.models.PredictionAccuracy;
import com.alidogukan.avora.models.UnifiedConfidence;
import com.alidogukan.avora.models.SoilLearningProfile;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.ZoneAIState;
import com.alidogukan.avora.ui.irrigationassistant.ZoneAIStateFreshnessChecker;
import com.alidogukan.avora.models.WeatherForecast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static com.alidogukan.avora.ui.irrigationassistant.IrrigationAssistantCodes.*;

@SuppressWarnings("SpellCheckingInspection")
public class AIAssistantActivity extends EdgeToEdgeActivity {

    private static final String TAG = "AIAssistantActivity";

    private DecisionStepAdapter decisionStepAdapter;

    private MaterialCardView cardAIDecision;
    private MaterialCardView cardAIZoneSummary;
    private MaterialCardView cardAIReasons;
    private MaterialCardView cardAIDecisionFlow;
    private MaterialCardView cardAIProgress;
    private MaterialCardView cardAINextStep;
    private MaterialCardView cardAIAdaptiveRecommendation;
    private MaterialCardView cardAIWeatherGuidance;
    private MaterialCardView cardAITechnicalSummary;
    private MaterialCardView cardAISeverityBadge;
    private MaterialButton btnBack;
    private MaterialButton btnAIAdvancedDetails;
    private MaterialCardView cardMoisturePrediction;
    private MaterialCardView cardMoisturePredictionStatusBadge;
    private MaterialCardView cardPredictionAccuracy;
    private MaterialCardView cardPredictionAccuracyStatusBadge;
    private MaterialCardView cardUnifiedConfidence;
    private MaterialCardView cardUnifiedConfidenceStatusBadge;
    private SelectedZoneSummaryRenderer selectedZoneSummaryRenderer;

    private TextView txtAIDecisionTitle;
    private TextView txtAIAnalysisScope;
    private TextView txtAIDecisionSummary;
    private TextView txtAISeverityBadge;

    private TextView txtAIReasonOne;
    private TextView txtAIReasonTwo;
    private TextView txtAIReasonThree;

    private TextView txtAIProgressPercent;
    private TextView txtAILearningStage;
    private TextView txtAILearningDescription;
    private TextView txtAILearningSensorCount;
    private TextView txtAILearningSensorStatus;
    private TextView txtAILearningWateringCount;
    private TextView txtAILearningWateringStatus;

    private TextView txtAINextStep;
    private TextView txtAIWeatherGuidanceTitle;
    private TextView txtAIWeatherGuidanceSummary;
    private TextView txtAIWeatherGuidanceSafety;
    private TextView txtAIAdaptiveRecommendationTitle;
    private TextView txtAIAdaptiveRecommendationDetail;
    private TextView txtAIConfidence;
    private TextView txtAISoilClassification;
    private TextView txtAITrendClassification;
    private TextView txtAIUpdatedAt;
    private TextView txtAISoilRetention;
    private TextView txtAIAverageDrying;
    private TextView txtAIDryingStatus;
    private TextView txtAIWateringEfficiency;
    private TextView txtAIWateringEfficiencyStatus;

    private TextView txtMoisturePredictionStatus;
    private TextView txtPredictionCurrentMoisture;
    private TextView txtMoisturePredictionTitle;
    private TextView txtMoisturePredictionZone;
    private TextView txtPredictionMoistureLimit;

    private TextView txtPredictionOneHour;
    private TextView txtPredictionThreeHours;
    private TextView txtPredictionSixHours;

    private TextView txtPredictionTimeUntilLimit;
    private TextView txtMoisturePredictionConfidence;
    private TextView txtPredictionLimitReachedAt;
    private TextView txtMoisturePredictionUpdatedAt;

    private TextView txtPredictionAccuracyStatus;
    private TextView txtPredictionAccuracyPercent;
    private TextView txtPredictionConfidenceMultiplier;

    private TextView txtPredictionCount;
    private TextView txtSuccessfulPredictions;
    private TextView txtPredictionAverageError;

    private TextView txtPredictionMinimumError;
    private TextView txtPredictionMaximumError;
    private TextView txtPredictionAccuracyUpdatedAt;
    private TextView txtUnifiedConfidenceStatus;
    private TextView txtUnifiedOverallConfidence;
    private TextView txtUnifiedConfidenceLevel;

    private TextView txtUnifiedSoilConfidence;
    private TextView txtUnifiedPredictionAccuracy;
    private TextView txtUnifiedSensorConfidence;

    private TextView txtUnifiedTrendConfidence;
    private TextView txtUnifiedWeightedScore;
    private TextView txtUnifiedConfidenceUpdatedAt;


    private LinearProgressIndicator progressAILearningSensor;
    private LinearProgressIndicator progressAILearningWatering;
    private LinearProgressIndicator progressPredictionAccuracy;
    private LinearProgressIndicator progressUnifiedConfidence;

    private final View[] progressSegments = new View[5];

    private MainViewModel viewModel;
    private IrrigationAssistantFormatter assistantFormatter;
    private PredictionValidationRenderer predictionValidationRenderer;

    private ValueAnimator progressAnimator;
    private int currentLearningProgress = 0;
    private boolean advancedDetailsVisible = false;
    private boolean learningDataCollectionCompleted = false;
    private int profileLearningStage = 0;
    private final List<GardenZone> predictionZones = new ArrayList<>();
    private int selectedPredictionZoneIndex = 0;
    private MoisturePrediction latestMoisturePrediction;
    private AIDecision fallbackAIDecision;
    private AdaptiveRecommendation fallbackAdaptiveRecommendation;
    private AIExplanation fallbackAIExplanation;
    private PredictionValidationStatus fallbackPredictionValidationStatus;
    private PredictionAccuracy fallbackPredictionAccuracy;
    private UnifiedConfidence fallbackUnifiedConfidence;
    private SoilLearningProfile fallbackSoilLearningProfile;
    private String requestedZoneId;
    private WeatherForecast latestWeatherForecast;
    private com.alidogukan.avora.viewmodels.DisplayUnitsViewModel displayUnits;
    private float predictionSwipeStartX;
    private float predictionSwipeStartY;
    private boolean predictionHorizontalSwipe;

    @Override
    protected void onCreate(
            @Nullable Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_assistant);

        assistantFormatter = new IrrigationAssistantFormatter(this);
        PrimaryBottomNavigation.bind(this, PrimaryBottomNavigation.ASSISTANT);
        requestedZoneId = getIntent() == null ? null : getIntent().getStringExtra("zone_id");

        initializeViews();
        initializeRecyclerView();
        initializeViewModel();
        initializeListeners();
        observeViewModel();

        /*
         * Firebase verisi gelmeden önce başlangıç görünümü.
         */
        renderLearningProgressImmediately(0);

        startEntranceAnimations();
    }

    /**
     * XML ekranındaki tüm öğeleri Java tarafına bağlar.
     */
    private void initializeViews() {
        AssistantIntroCard.bind(this, AssistantIntroCard.Kind.WATER);

        cardAIDecision =
                findViewById(R.id.cardAIDecision);

        cardAIZoneSummary =
                findViewById(R.id.cardAIZoneSummary);

        cardAIReasons =
                findViewById(R.id.cardAIReasons);

        cardAIDecisionFlow =
                findViewById(R.id.cardAIDecisionFlow);

        cardAIProgress =
                findViewById(R.id.cardAIProgress);

        cardAINextStep =
                findViewById(R.id.cardAINextStep);
        cardAIWeatherGuidance =
                findViewById(R.id.cardAIWeatherGuidance);

        cardAIAdaptiveRecommendation =
                findViewById(R.id.cardAIAdaptiveRecommendation);

        cardAITechnicalSummary =
                findViewById(R.id.cardAITechnicalSummary);

        cardAISeverityBadge =
                findViewById(R.id.cardAISeverityBadge);

        cardUnifiedConfidence =
                findViewById(
                        R.id.cardUnifiedConfidence
                );

        cardUnifiedConfidenceStatusBadge =
                findViewById(
                        R.id.cardUnifiedConfidenceStatusBadge
                );

        selectedZoneSummaryRenderer = new SelectedZoneSummaryRenderer(
                this,
                findViewById(R.id.layoutAIZoneSummary),
                assistantFormatter
        );


        btnBack =
                findViewById(R.id.btnBack);

        btnAIAdvancedDetails =
                findViewById(R.id.btnAIAdvancedDetails);

        txtAIDecisionTitle =
                findViewById(R.id.txtAIDecisionTitle);

        txtAIAnalysisScope =
                findViewById(R.id.txtAIAnalysisScope);

        txtAIDecisionSummary =
                findViewById(R.id.txtAIDecisionSummary);

        txtAISeverityBadge =
                findViewById(R.id.txtAISeverityBadge);

        txtAIReasonOne =
                findViewById(R.id.txtAIReasonOne);

        txtAIReasonTwo =
                findViewById(R.id.txtAIReasonTwo);

        txtAIReasonThree =
                findViewById(R.id.txtAIReasonThree);

        txtAIProgressPercent =
                findViewById(R.id.txtAIProgressPercent);

        txtAILearningStage =
                findViewById(R.id.txtAILearningStage);

        txtAILearningDescription =
                findViewById(R.id.txtAILearningDescription);

        txtAILearningSensorCount =
                findViewById(R.id.txtAILearningSensorCount);

        txtAILearningSensorStatus =
                findViewById(R.id.txtAILearningSensorStatus);

        txtAILearningWateringCount =
                findViewById(R.id.txtAILearningWateringCount);

        txtAILearningWateringStatus =
                findViewById(R.id.txtAILearningWateringStatus);


        progressAILearningSensor =
                findViewById(R.id.progressAILearningSensor);

        progressAILearningWatering =
                findViewById(R.id.progressAILearningWatering);

        progressUnifiedConfidence =
                findViewById(
                        R.id.progressUnifiedConfidence
                );


        txtAINextStep =
                findViewById(R.id.txtAINextStep);
        txtAIWeatherGuidanceTitle =
                findViewById(R.id.txtAIWeatherGuidanceTitle);
        txtAIWeatherGuidanceSummary =
                findViewById(R.id.txtAIWeatherGuidanceSummary);
        txtAIWeatherGuidanceSafety =
                findViewById(R.id.txtAIWeatherGuidanceSafety);

        txtAIAdaptiveRecommendationTitle =
                findViewById(R.id.txtAIAdaptiveRecommendationTitle);

        txtAIAdaptiveRecommendationDetail =
                findViewById(R.id.txtAIAdaptiveRecommendationDetail);

        txtAIConfidence =
                findViewById(R.id.txtAIConfidence);

        txtAISoilClassification =
                findViewById(R.id.txtAISoilClassification);

        txtAITrendClassification =
                findViewById(R.id.txtAITrendClassification);

        txtAIUpdatedAt =
                findViewById(R.id.txtAIUpdatedAt);

        txtAISoilRetention =
                findViewById(R.id.txtAISoilRetention);

        txtAIAverageDrying =
                findViewById(R.id.txtAIAverageDrying);

        txtAIDryingStatus =
                findViewById(
                        R.id.txtAIDryingStatus
                );

        txtAIWateringEfficiency =
                findViewById(R.id.txtAIWateringEfficiency);

        txtAIWateringEfficiencyStatus =
                findViewById(
                        R.id.txtAIWateringEfficiencyStatus
                );

        predictionValidationRenderer =
                new PredictionValidationRenderer(
                        findViewById(android.R.id.content),
                        assistantFormatter
                );
        progressPredictionAccuracy =
                findViewById(
                        R.id.progressPredictionAccuracy
                );

        cardPredictionAccuracy =
                findViewById(
                        R.id.cardPredictionAccuracy
                );

        cardPredictionAccuracyStatusBadge =
                findViewById(
                        R.id.cardPredictionAccuracyStatusBadge
                );

        cardMoisturePrediction =
                findViewById(
                        R.id.cardMoisturePrediction
                );

        cardMoisturePredictionStatusBadge =
                findViewById(
                        R.id.cardMoisturePredictionStatusBadge
                );

        txtMoisturePredictionTitle =
                findViewById(R.id.txtMoisturePredictionTitle);

        txtMoisturePredictionZone =
                findViewById(R.id.txtMoisturePredictionZone);

        txtMoisturePredictionStatus =
                findViewById(
                        R.id.txtMoisturePredictionStatus
                );

        txtPredictionCurrentMoisture =
                findViewById(
                        R.id.txtPredictionCurrentMoisture
                );

        txtPredictionMoistureLimit =
                findViewById(
                        R.id.txtPredictionMoistureLimit
                );

        txtPredictionOneHour =
                findViewById(
                        R.id.txtPredictionOneHour
                );

        txtPredictionThreeHours =
                findViewById(
                        R.id.txtPredictionThreeHours
                );

        txtPredictionSixHours =
                findViewById(
                        R.id.txtPredictionSixHours
                );

        txtPredictionTimeUntilLimit =
                findViewById(
                        R.id.txtPredictionTimeUntilLimit
                );

        txtMoisturePredictionConfidence =
                findViewById(
                        R.id.txtMoisturePredictionConfidence
                );

        txtPredictionLimitReachedAt =
                findViewById(
                        R.id.txtPredictionLimitReachedAt
                );
        txtPredictionAccuracyStatus =
                findViewById(
                        R.id.txtPredictionAccuracyStatus
                );

        txtPredictionAccuracyPercent =
                findViewById(
                        R.id.txtPredictionAccuracyPercent
                );

        txtPredictionConfidenceMultiplier =
                findViewById(
                        R.id.txtPredictionConfidenceMultiplier
                );

        txtPredictionCount =
                findViewById(
                        R.id.txtPredictionCount
                );

        txtSuccessfulPredictions =
                findViewById(
                        R.id.txtSuccessfulPredictions
                );

        txtPredictionAverageError =
                findViewById(
                        R.id.txtPredictionAverageError
                );

        txtPredictionMinimumError =
                findViewById(
                        R.id.txtPredictionMinimumError
                );

        txtPredictionMaximumError =
                findViewById(
                        R.id.txtPredictionMaximumError
                );

        txtPredictionAccuracyUpdatedAt =
                findViewById(
                        R.id.txtPredictionAccuracyUpdatedAt
                );

        txtMoisturePredictionUpdatedAt =
                findViewById(
                        R.id.txtMoisturePredictionUpdatedAt
                );
        txtUnifiedConfidenceStatus =
                findViewById(
                        R.id.txtUnifiedConfidenceStatus
                );

        txtUnifiedOverallConfidence =
                findViewById(
                        R.id.txtUnifiedOverallConfidence
                );

        txtUnifiedConfidenceLevel =
                findViewById(
                        R.id.txtUnifiedConfidenceLevel
                );

        txtUnifiedSoilConfidence =
                findViewById(
                        R.id.txtUnifiedSoilConfidence
                );

        txtUnifiedPredictionAccuracy =
                findViewById(
                        R.id.txtUnifiedPredictionAccuracy
                );

        txtUnifiedSensorConfidence =
                findViewById(
                        R.id.txtUnifiedSensorConfidence
                );

        txtUnifiedTrendConfidence =
                findViewById(
                        R.id.txtUnifiedTrendConfidence
                );

        txtUnifiedWeightedScore =
                findViewById(
                        R.id.txtUnifiedWeightedScore
                );

        txtUnifiedConfidenceUpdatedAt =
                findViewById(
                        R.id.txtUnifiedConfidenceUpdatedAt
                );

        initializeProgressSegments();
    }

    /**
     * XML'deki 10 ilerleme segmentini bir diziye bağlar.
     * <p>
     * Böylece her segment için ayrı ayrı kod yazmak yerine
     * döngüyle aktif veya pasif görünüm uygulanabilir.
     */
    private void initializeProgressSegments() {

        progressSegments[0] =
                findViewById(R.id.segmentAIProgress1);

        progressSegments[1] =
                findViewById(R.id.segmentAIProgress2);

        progressSegments[2] =
                findViewById(R.id.segmentAIProgress3);

        progressSegments[3] =
                findViewById(R.id.segmentAIProgress4);

        progressSegments[4] =
                findViewById(R.id.segmentAIProgress5);

    }

    /**
     * AI karar akışı RecyclerView yapısını hazırlar.
     */
    private void initializeRecyclerView() {

        RecyclerView recyclerAIDecisionFlow =
                findViewById(R.id.recyclerAIDecisionFlow);

        decisionStepAdapter =
                new DecisionStepAdapter();

        recyclerAIDecisionFlow.setLayoutManager(
                new LinearLayoutManager(this)
        );

        recyclerAIDecisionFlow.setAdapter(
                decisionStepAdapter
        );

        // Karar verileri sık güncellenebilir. Satır değişim animasyonları
        // teknik kartın yanıp sönüyormuş gibi görünmesine neden olmamalı.
        recyclerAIDecisionFlow.setItemAnimator(null);

        /*
         * RecyclerView, NestedScrollView içinde bulunduğu için
         * kendi kaydırma davranışı kapatılır.
         */
        recyclerAIDecisionFlow.setNestedScrollingEnabled(false);
    }

    /**
     * Activity için ViewModel nesnesini oluşturur.
     */
    private void initializeViewModel() {

        viewModel =
                new ViewModelProvider(this)
                        .get(MainViewModel.class);
        displayUnits = new ViewModelProvider(this)
                .get(com.alidogukan.avora.viewmodels.DisplayUnitsViewModel.class);
    }

    /**
     * Buton tıklama işlemlerini tanımlar.
     */
    private void initializeListeners() {

        btnBack.setOnClickListener(
                view ->
                        getOnBackPressedDispatcher()
                                .onBackPressed()
        );

        btnAIAdvancedDetails.setOnClickListener(
                view -> setAdvancedDetailsVisible(
                        !advancedDetailsVisible
                )
        );

        findViewById(R.id.btnAIManualWatering).setOnClickListener(
                view -> startActivity(
                        new Intent(this, WateringControlActivity.class)
                )
        );

        attachZoneSwipeListener(cardAIZoneSummary);
        attachZoneSwipeListener(cardMoisturePrediction);
    }

    private void attachZoneSwipeListener(View target) {
        target.setOnTouchListener(
                (view, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        predictionSwipeStartX = event.getX();
                        predictionSwipeStartY = event.getY();
                        predictionHorizontalSwipe = false;
                        return true;
                    }

                    if (event.getAction() == MotionEvent.ACTION_MOVE) {
                        float distance = event.getX() - predictionSwipeStartX;
                        float verticalDistance = event.getY() - predictionSwipeStartY;
                        float touchSlop = ViewConfiguration.get(this)
                                .getScaledTouchSlop();

                        if (!predictionHorizontalSwipe
                                && Math.abs(verticalDistance) > touchSlop
                                && Math.abs(verticalDistance) > Math.abs(distance)) {
                            view.getParent().requestDisallowInterceptTouchEvent(false);
                            return false;
                        }

                        if (Math.abs(distance) > touchSlop
                                && Math.abs(distance) > Math.abs(verticalDistance)) {
                            predictionHorizontalSwipe = true;
                            view.getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        return true;
                    }

                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        float distance = event.getX() - predictionSwipeStartX;
                        view.getParent().requestDisallowInterceptTouchEvent(false);
                        if (predictionHorizontalSwipe && Math.abs(distance) >= 44f) {
                            movePredictionZone(distance < 0 ? 1 : -1);
                            view.performClick();
                            return true;
                        }
                        return false;
                    }

                    if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                        view.getParent().requestDisallowInterceptTouchEvent(false);
                        return false;
                    }
                    return true;
                }
        );
    }

    /**
     * Firebase verilerini taşıyan LiveData nesnelerini gözlemler.
     */
    private void setAdvancedDetailsVisible(
            boolean visible
    ) {
        advancedDetailsVisible = visible;

        boolean hasZoneAI = hasSelectedZoneAIData();
        cardAIProgress.setVisibility(visible && hasZoneAI ? View.VISIBLE : View.GONE);
        cardAIReasons.setVisibility(visible && hasZoneAI ? View.VISIBLE : View.GONE);
        cardAIDecisionFlow.setVisibility(visible && hasZoneAI ? View.VISIBLE : View.GONE);
        cardMoisturePrediction.setVisibility(
                visible && hasReadyPredictionForSelectedZone()
                        ? View.VISIBLE
                        : View.GONE
        );
        cardPredictionAccuracy.setVisibility(visible && hasZoneAI ? View.VISIBLE : View.GONE);
        cardUnifiedConfidence.setVisibility(visible && hasZoneAI ? View.VISIBLE : View.GONE);
        predictionValidationRenderer.setVisible(visible && hasZoneAI);
        cardAITechnicalSummary.setVisibility(visible && hasZoneAI ? View.VISIBLE : View.GONE);

        btnAIAdvancedDetails.setText(
                visible
                        ? R.string.ai_technical_details_hide
                        : R.string.ai_technical_details_show
        );
    }

    private boolean hasSelectedZoneAIData() {
        if (predictionZones.isEmpty()) {
            return false;
        }
        GardenZone zone = predictionZones.get(selectedPredictionZoneIndex);
        return zone != null && zone.getAi() != null;
    }



    private boolean hasReadyPredictionForSelectedZone() {
        if (!hasSelectedZoneAIData()) {
            return false;
        }
        MoisturePrediction prediction = predictionZones
                .get(selectedPredictionZoneIndex)
                .getAi()
                .getMoisturePrediction();
        return prediction != null
                && READY.equalsIgnoreCase(
                assistantFormatter.safeText(prediction.getPrediction_status(), "")
        );
    }

    private void observeViewModel() {

        viewModel.getAIDecision().observe(
                this,
                decision -> {
                    fallbackAIDecision = decision;
                    renderSelectedZoneAI();
                }
        );

        viewModel.getAdaptiveRecommendation().observe(
                this,
                recommendation -> {
                    fallbackAdaptiveRecommendation = recommendation;
                    renderSelectedZoneAI();
                }
        );

        viewModel.getAIExplanation().observe(
                this,
                explanation -> {
                    fallbackAIExplanation = explanation;
                    renderSelectedZoneAI();
                }
        );

        viewModel.getPredictionValidationStatus().observe(
                this,
                status -> {
                    fallbackPredictionValidationStatus = status;
                    renderSelectedZoneAI();
                }
        );

        viewModel.getMoisturePrediction().observe(
                this,
                this::renderMoisturePrediction
        );

        viewModel.getPredictionAccuracy().observe(
                this,
                accuracy -> {
                    fallbackPredictionAccuracy = accuracy;
                    renderSelectedZoneAI();
                }
        );

        viewModel.getUnifiedConfidence().observe(
                this,
                confidence -> {
                    fallbackUnifiedConfidence = confidence;
                    renderSelectedZoneAI();
                }
        );

        viewModel.getSoilLearningProfile().observe(
                this,
                profile -> {
                    fallbackSoilLearningProfile = profile;
                    renderSelectedZoneAI();
                }
        );

        viewModel.getGardenZones().observe(
                this,
                this::renderZoneDecisionSummary
        );


        viewModel.getWeatherForecast().observe(
                this,
                forecast -> {
                    latestWeatherForecast = forecast;
                    renderWeatherGuidance();
                }
        );
    }

    private void renderZoneDecisionSummary(
            List<GardenZone> zones
    ) {
        List<GardenZone> incomingZones = zones == null
                ? new ArrayList<>()
                : new ArrayList<>(zones);
        updatePredictionZones(incomingZones);
        renderWeatherGuidance();
    }

    /** Raspberry Pi'nin güvenli hava politikasını kullanıcıya açıklar. */
    private void renderWeatherGuidance() {
        if (latestWeatherForecast == null) {
            cardAIWeatherGuidance.setVisibility(View.GONE);
            return;
        }

        if (!assistantFormatter.isWeatherForecastFresh(latestWeatherForecast)) {
            txtAIWeatherGuidanceTitle.setText(R.string.ai_weather_stale_title);
            txtAIWeatherGuidanceSummary.setText(R.string.ai_weather_stale_summary);
            txtAIWeatherGuidanceSafety.setText(R.string.ai_weather_stale_safety);
            cardAIWeatherGuidance.setVisibility(View.VISIBLE);
            return;
        }

        Double temperature = latestWeatherForecast.getCurrentTemperature();
        Double todayMaximum = latestWeatherForecast.getTodayTemperatureMax();
        if (todayMaximum != null
                && (temperature == null || todayMaximum > temperature)) {
            temperature = todayMaximum;
        }
        Double rain = latestWeatherForecast.getTodayRainProbability();
        Double rainMm = latestWeatherForecast.getTodayRainMm();
        Double wind = latestWeatherForecast.getTodayWindMax();

        GardenZone selectedZone = predictionZones.isEmpty()
                ? null
                : predictionZones.get(selectedPredictionZoneIndex);
        boolean selectedZoneDry = selectedZone != null
                && selectedZone.isIrrigation_enabled()
                && selectedZone.isSensor_enabled()
                && assistantFormatter.isZoneFresh(selectedZone)
                && selectedZone.getMoisture() <= selectedZone.getMoisture_limit();
        String selectedZoneName = selectedZone == null
                ? getString(R.string.ai_selected_zone_fallback)
                : assistantFormatter.safeText(selectedZone.getName(), selectedZone.getZone_id());

        String temperatureText = temperature == null
                ? getString(R.string.ai_weather_temperature_missing)
                : displayUnits.formatTemperature(temperature);
        String rainText = rain == null
                ? ""
                : getString(R.string.ai_weather_rain_suffix, Math.round(rain));
        String windText = wind == null
                ? ""
                : getString(R.string.ai_weather_wind_suffix, Math.round(wind));
        String weatherLine = getString(
                R.string.ai_weather_today_line,
                temperatureText,
                rainText,
                windText
        );

        boolean rainDelay = rain != null
                && rainMm != null
                && rain >= 80d
                && rainMm >= 2d;
        boolean windDelay = wind != null && wind >= 35d;
        boolean heatAdjustment = temperature != null && temperature >= 35d;

        if (rainDelay) {
            txtAIWeatherGuidanceTitle.setText(R.string.ai_weather_rain_delay_title);
            txtAIWeatherGuidanceSummary.setText(
                    getString(R.string.ai_weather_rain_delay_summary, weatherLine)
            );
            txtAIWeatherGuidanceSafety.setText(R.string.ai_weather_rain_delay_safety);
        } else if (windDelay) {
            txtAIWeatherGuidanceTitle.setText(R.string.ai_weather_wind_delay_title);
            txtAIWeatherGuidanceSummary.setText(
                    getString(R.string.ai_weather_wind_delay_summary, weatherLine)
            );
            txtAIWeatherGuidanceSafety.setText(R.string.ai_weather_wind_delay_safety);
        } else if (heatAdjustment) {
            txtAIWeatherGuidanceTitle.setText(R.string.ai_weather_heat_title);
            txtAIWeatherGuidanceSummary.setText(selectedZoneDry
                    ? getString(
                    R.string.ai_weather_selected_heat_dry_summary,
                    weatherLine,
                    selectedZoneName
            )
                    : getString(R.string.ai_weather_heat_normal_summary, weatherLine));
            txtAIWeatherGuidanceSafety.setText(R.string.ai_weather_heat_safety);
        } else {
            txtAIWeatherGuidanceTitle.setText(R.string.ai_weather_neutral_title);
            txtAIWeatherGuidanceSummary.setText(selectedZoneDry
                    ? getString(
                    R.string.ai_weather_selected_neutral_dry_summary,
                    weatherLine,
                    selectedZoneName
            )
                    : getString(R.string.ai_weather_neutral_summary, weatherLine));
            txtAIWeatherGuidanceSafety.setText(R.string.ai_weather_neutral_safety);
        }

        cardAIWeatherGuidance.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (displayUnits != null) renderWeatherGuidance();
    }
    private void renderSoilLearningProfile(
            SoilLearningProfile profile
    ) {

        if (profile == null) {
            return;
        }


        txtAILearningSensorCount.setText(
                getString(
                        R.string.runtime_pair_slash,
                        String.valueOf(profile.getSensor_history_count()),
                        String.valueOf(profile.getSensor_history_count()
                                + profile.getRemaining_sensor_samples())
                )
        );


        txtAILearningWateringCount.setText(
                getString(
                        R.string.runtime_pair_slash,
                        String.valueOf(profile.getWatering_count_analyzed()),
                        String.valueOf(profile.getWatering_count_analyzed()
                                + profile.getRemaining_auto_waterings())
                )
        );
        String sensorStatus =
                getLearningStatus(
                        profile.getSensor_history_count(),
                        profile.getSensor_history_count()
                                +
                                profile.getRemaining_sensor_samples()
                );


        String wateringStatus =
                getLearningStatus(
                        profile.getWatering_count_analyzed(),
                        profile.getWatering_count_analyzed()
                                +
                                profile.getRemaining_auto_waterings()
                );

        learningDataCollectionCompleted =
                getString(R.string.ai_runtime_completed_upper).equals(sensorStatus)
                        && getString(R.string.ai_runtime_completed_upper).equals(wateringStatus);
        profileLearningStage = profile.getLearning_stage();
        updateLearningStage(currentLearningProgress);

        renderLearningItemStatus(
                txtAILearningSensorStatus,
                sensorStatus
        );

        renderLearningItemStatus(
                txtAILearningWateringStatus,
                wateringStatus
        );

        Log.d(
                TAG,
                "AI Learning Sensor="
                        + sensorStatus
                        + " Watering="
                        + wateringStatus
        );
        // -------------------------------------------------
        // AI Learning progress hesapları
        // -------------------------------------------------

        int sensorCompleted =
                profile.getSensor_history_count();

        int sensorTotal =
                profile.getSensor_history_count()
                        +
                        profile.getRemaining_sensor_samples();


        int sensorProgress = 0;

        if (sensorTotal > 0) {

            sensorProgress =
                    (sensorCompleted * 100)
                            /
                            sensorTotal;
        }


        progressAILearningSensor.setProgressCompat(
                sensorProgress,
                true
        );


        // -------------------------------------------------

        int wateringCompleted =
                profile.getWatering_count_analyzed();

        int wateringTotal =
                profile.getWatering_count_analyzed()
                        +
                        profile.getRemaining_auto_waterings();


        int wateringProgress = 0;

        if (wateringTotal > 0) {

            wateringProgress =
                    (wateringCompleted * 100)
                            /
                            wateringTotal;
        }


        progressAILearningWatering.setProgressCompat(
                wateringProgress,
                true
        );

        txtAILearningStage.setText(
                getString(R.string.ai_runtime_learning_stage, profile.getLearning_stage())
        );

        txtAILearningDescription.setText(
                profile.getNext_milestone_text()
        );

        double retention =
                profile.getEstimated_water_retention_minutes();


        if (retention > 0) {

            if (retention >= 60) {

                double hours =
                        retention / 60.0;

                txtAISoilRetention.setText(
                        String.format(
                                Locale.getDefault(),
                                getString(R.string.ai_runtime_decimal_hours),
                                hours
                        )
                );

            } else {

                txtAISoilRetention.setText(
                        String.format(
                                Locale.getDefault(),
                                getString(R.string.ai_runtime_minutes),
                                retention
                        )
                );
            }

        }
        else {

            txtAISoilRetention.setText(
                    getString(R.string.ai_runtime_waiting)
            );

        }


        double dryingRate =
                profile.getAverage_drying_rate_per_minute();


        if (dryingRate > 0) {

            txtAIAverageDrying.setText(
                    String.format(
                            Locale.getDefault(),
                            getString(R.string.ai_runtime_drying_rate_minute),
                            dryingRate
                    )
            );

            txtAIDryingStatus.setText(
                    getString(R.string.ai_runtime_fast_drying)
            );

        }
        else if (dryingRate < 0) {

            txtAIAverageDrying.setText(
                    String.format(
                            Locale.getDefault(),
                            getString(R.string.ai_runtime_drying_rate_minute),
                            dryingRate
                    )
            );

            txtAIDryingStatus.setText(
                    getString(R.string.ai_runtime_moisture_rising)
            );

        }
        else {

            txtAIAverageDrying.setText(
                    getString(R.string.ai_runtime_zero_drying_rate)
            );

            txtAIDryingStatus.setText(
                    getString(R.string.ai_runtime_stabil)
            );
        }


        double efficiency =
                profile.getIrrigation_efficiency();


        txtAIWateringEfficiency.setText(
                formatEfficiency(
                        efficiency
                )
        );


        String efficiencyStatus =
                formatEfficiencyStatus(
                        efficiency
                );


        txtAIWateringEfficiencyStatus.setText(
                efficiencyStatus
        );


        applyEfficiencyStatusStyle(
                efficiencyStatus
        );

    }

    private String getLearningStatus(
            int completed,
            int total
    ) {

        if (total <= 0) {
            return getString(R.string.ai_runtime_waiting_upper);
        }


        if (completed >= total) {

            return getString(R.string.ai_runtime_completed_upper);

        }


        if (completed > 0) {

            return getString(R.string.ai_runtime_learning_upper);

        }


        return getString(R.string.ai_runtime_not_started_upper);
    }

    private void renderLearningItemStatus(
            TextView statusView,
            String status
    ) {
        statusView.setText(status);

        int color = getString(R.string.ai_runtime_completed_upper).equals(status)
                ? R.color.primary
                : R.color.textSecondary;
        statusView.setTextColor(ContextCompat.getColor(this, color));
    }

    private String formatEfficiency(
            double efficiency
    ) {

        if (
                Double.isNaN(efficiency)
                        || Double.isInfinite(efficiency)
                        || efficiency <= 0
        ) {
            return assistantFormatter.unavailableValue();
        }


        return String.format(
                Locale.getDefault(),
                getString(R.string.ai_runtime_drying_rate_second),
                efficiency
        );
    }
    private void renderUnifiedConfidence(
            UnifiedConfidence confidence
    ) {

        if (confidence == null) {
            renderUnifiedConfidenceEmpty();
            return;
        }

        String status =
                assistantFormatter.safeText(
                        confidence.getStatus(),
                        INSUFFICIENT_DATA
                )
                        .trim()
                        .toUpperCase(Locale.ROOT);

        double overallConfidence =
                assistantFormatter.normalizePercent(
                        confidence.getOverall_confidence()
                );

        if (INSUFFICIENT_DATA.equals(status)) {

            renderUnifiedConfidenceLearning(
                    confidence
            );

            return;
        }

        progressUnifiedConfidence.setVisibility(
                View.VISIBLE
        );

        txtUnifiedOverallConfidence.setText(
                formatPercent(
                        overallConfidence
                )
        );

        txtUnifiedConfidenceLevel.setText(
                formatUnifiedConfidenceLevel(
                        confidence.getConfidence_level()
                )
        );

        txtUnifiedSoilConfidence.setText(
                formatPercent(
                        confidence.getSoil_learning_confidence()
                )
        );

        txtUnifiedPredictionAccuracy.setText(
                formatPercent(
                        confidence.getPrediction_accuracy()
                )
        );

        txtUnifiedSensorConfidence.setText(
                formatPercent(
                        confidence.getSensor_confidence()
                )
        );

        txtUnifiedTrendConfidence.setText(
                formatPercent(
                        confidence.getTrend_confidence()
                )
        );

        txtUnifiedWeightedScore.setText(
                formatPercent(
                        confidence.getWeighted_score()
                )
        );

        txtUnifiedConfidenceUpdatedAt.setText(
                getString(
                        R.string.ai_runtime_last_updated_prefix,
                        formatPredictionDateTime(confidence.getGenerated_at())
                )
        );

        progressUnifiedConfidence.setProgressCompat(
                (int) Math.round(overallConfidence),
                true
        );

        applyUnifiedConfidenceStatusStyle(
                status,
                overallConfidence
        );
    }

    private void renderUnifiedConfidenceLearning(
            UnifiedConfidence confidence
    ) {

        txtUnifiedConfidenceStatus.setText(
                getString(R.string.ai_runtime_learning_upper)
        );

        txtUnifiedOverallConfidence.setText(
                assistantFormatter.unavailableValue()
        );

        txtUnifiedConfidenceLevel.setText(
                getString(R.string.ai_runtime_data_waiting)
        );

        txtUnifiedSoilConfidence.setText(
                assistantFormatter.unavailableValue()
        );

        txtUnifiedPredictionAccuracy.setText(
                assistantFormatter.unavailableValue()
        );

        txtUnifiedSensorConfidence.setText(
                formatPercent(
                        confidence.getSensor_confidence()
                )
        );

        txtUnifiedTrendConfidence.setText(
                formatPercent(
                        confidence.getTrend_confidence()
                )
        );

        txtUnifiedWeightedScore.setText(
                assistantFormatter.unavailableValue()
        );

        txtUnifiedConfidenceUpdatedAt.setText(
                getString(
                        R.string.ai_runtime_last_updated_prefix,
                        formatPredictionDateTime(confidence.getGenerated_at())
                )
        );

        progressUnifiedConfidence.setProgressCompat(
                0,
                false
        );

        progressUnifiedConfidence.setVisibility(
                View.INVISIBLE
        );

        applyUnifiedConfidenceWaitingStyle();
    }

    private void renderUnifiedConfidenceEmpty() {

        txtUnifiedConfidenceStatus.setText(
                getString(R.string.ai_runtime_waiting_upper)
        );

        txtUnifiedOverallConfidence.setText(
                assistantFormatter.unavailableValue()
        );

        txtUnifiedConfidenceLevel.setText(
                assistantFormatter.unavailableValue()
        );

        txtUnifiedSoilConfidence.setText(
                assistantFormatter.unavailableValue()
        );

        txtUnifiedPredictionAccuracy.setText(
                assistantFormatter.unavailableValue()
        );

        txtUnifiedSensorConfidence.setText(
                assistantFormatter.unavailableValue()
        );

        txtUnifiedTrendConfidence.setText(
                assistantFormatter.unavailableValue()
        );

        txtUnifiedWeightedScore.setText(
                assistantFormatter.unavailableValue()
        );

        txtUnifiedConfidenceUpdatedAt.setText(
                getString(R.string.ai_runtime_last_updated_unavailable)
        );

        progressUnifiedConfidence.setProgressCompat(
                0,
                false
        );

        progressUnifiedConfidence.setVisibility(
                View.INVISIBLE
        );

        applyUnifiedConfidenceWaitingStyle();
    }

    private String formatUnifiedConfidenceLevel(
            String confidenceLevel
    ) {

        if (
                confidenceLevel == null
                        || confidenceLevel.trim().isEmpty()
        ) {
            return getString(R.string.ai_runtime_waiting);
        }

        switch (
                confidenceLevel
                        .trim()
                        .toUpperCase(Locale.ROOT)
        ) {

            case VERY_HIGH:
                return getString(R.string.ai_runtime_very_high);

            case HIGH:
                return getString(R.string.ai_runtime_high);

            case MEDIUM:
                return getString(R.string.ai_runtime_medium);

            case LOW:
                return getString(R.string.ai_runtime_low);

            case VERY_LOW:
                return getString(R.string.ai_runtime_very_low);

            case INSUFFICIENT_DATA:
                return getString(R.string.ai_runtime_data_waiting);

            default:
                return getString(R.string.ai_runtime_waiting);
        }
    }

    private void applyUnifiedConfidenceStatusStyle(
            String status,
            double overallConfidence
    ) {

        if (
                READY.equals(status)
                        && overallConfidence >= 70.0
        ) {

            txtUnifiedConfidenceStatus.setText(
                    getString(R.string.ai_runtime_high_upper)
            );

            applyUnifiedConfidenceReadyStyle();

        } else if (
                READY.equals(status)
                        && overallConfidence >= 40.0
        ) {

            txtUnifiedConfidenceStatus.setText(
                    getString(R.string.ai_runtime_medium_upper)
            );

            applyUnifiedConfidenceMediumStyle();

        } else if (
                READY.equals(status)
        ) {

            txtUnifiedConfidenceStatus.setText(
                    getString(R.string.ai_runtime_low_upper)
            );

            applyUnifiedConfidenceLowStyle();

        } else if (
                INSUFFICIENT_DATA.equals(status)
        ) {

            txtUnifiedConfidenceStatus.setText(
                    getString(R.string.ai_runtime_learning_upper)
            );

            applyUnifiedConfidenceWaitingStyle();

        } else {

            txtUnifiedConfidenceStatus.setText(
                    getString(R.string.ai_runtime_waiting_upper)
            );

            applyUnifiedConfidenceWaitingStyle();
        }
    }

    private void applyUnifiedConfidenceReadyStyle() {

        int online =
                ContextCompat.getColor(
                        this,
                        R.color.online
                );

        int onlineBackground =
                ContextCompat.getColor(
                        this,
                        R.color.onlineBackground
                );

        cardUnifiedConfidenceStatusBadge
                .setCardBackgroundColor(
                        onlineBackground
                );

        cardUnifiedConfidenceStatusBadge
                .setStrokeColor(
                        online
                );

        txtUnifiedConfidenceStatus.setTextColor(
                online
        );
        AssistantIntroCard.copyWaterConfidence(this, txtUnifiedConfidenceStatus,
                cardUnifiedConfidenceStatusBadge);
    }

    private void applyUnifiedConfidenceMediumStyle() {

        int warning =
                ContextCompat.getColor(
                        this,
                        R.color.warning
                );

        int warningBackground =
                ContextCompat.getColor(
                        this,
                        R.color.warningBackground
                );

        cardUnifiedConfidenceStatusBadge
                .setCardBackgroundColor(
                        warningBackground
                );

        cardUnifiedConfidenceStatusBadge
                .setStrokeColor(
                        warning
                );

        txtUnifiedConfidenceStatus.setTextColor(
                warning
        );
        AssistantIntroCard.copyWaterConfidence(this, txtUnifiedConfidenceStatus,
                cardUnifiedConfidenceStatusBadge);
    }

    private void applyUnifiedConfidenceLowStyle() {

        int offline =
                ContextCompat.getColor(
                        this,
                        R.color.offline
                );

        int offlineBackground =
                ContextCompat.getColor(
                        this,
                        R.color.offlineBackground
                );

        cardUnifiedConfidenceStatusBadge
                .setCardBackgroundColor(
                        offlineBackground
                );

        cardUnifiedConfidenceStatusBadge
                .setStrokeColor(
                        offline
                );

        txtUnifiedConfidenceStatus.setTextColor(
                offline
        );
        AssistantIntroCard.copyWaterConfidence(this, txtUnifiedConfidenceStatus,
                cardUnifiedConfidenceStatusBadge);
    }

    private void applyUnifiedConfidenceWaitingStyle() {

        int primary =
                ContextCompat.getColor(
                        this,
                        R.color.primary
                );

        int primaryLight =
                ContextCompat.getColor(
                        this,
                        R.color.primaryLight
                );

        cardUnifiedConfidenceStatusBadge
                .setCardBackgroundColor(
                        primaryLight
                );

        cardUnifiedConfidenceStatusBadge
                .setStrokeColor(
                        primary
                );

        txtUnifiedConfidenceStatus.setTextColor(
                primary
        );
        AssistantIntroCard.copyWaterConfidence(this, txtUnifiedConfidenceStatus,
                cardUnifiedConfidenceStatusBadge);
    }


    /**
     * Firebase'den gelen AI karar bilgisini ekrana yansıtır.
     */
    private void renderAIDecision(
            AIDecision decision
    ) {

        if (decision == null) {
            return;
        }

        txtAIDecisionTitle.setText(
                assistantFormatter.safeText(
                        decision.getDecisionTitle(),
                        getString(R.string.ai_runtime_decision_preparing)
                )
        );

        String sensorId = assistantFormatter.safeText(
                decision.getAnalysisSensorId(),
                getString(R.string.ai_runtime_main_sensor)
        );
        txtAIAnalysisScope.setText(
                getString(R.string.ai_runtime_analysis_sensor, sensorId)
        );

        txtAIConfidence.setText(
                formatConfidence(
                        decision.getConfidenceLevel(),
                        decision.getConfidence()
                )
        );

        txtAISoilClassification.setText(
                formatSoilClassification(
                        decision.getSoilClassification()
                )
        );

        txtAITrendClassification.setText(
                formatTrendClassification(
                        decision.getTrendClassification()
                )
        );

        txtAIUpdatedAt.setText(
                formatUpdatedAt(
                        decision.getUpdatedAt()
                )
        );

        applyDecisionStyle(
                decision.getDecisionCode(),
                decision.getSeverity()
        );
    }

    private void renderAdaptiveRecommendation(
            AdaptiveRecommendation recommendation
    ) {
        if (
                recommendation == null
                        || !recommendation.isShouldApply()
        ) {
            cardAIAdaptiveRecommendation.setVisibility(View.GONE);
            return;
        }

        boolean increase = INCREASE_PUMP_DURATION.equals(
                recommendation.getRecommendationType()
        );

        txtAIAdaptiveRecommendationTitle.setText(
                increase
                        ? R.string.ai_adaptive_runtime_increase
                        : R.string.ai_adaptive_runtime_decrease
        );

        txtAIAdaptiveRecommendationDetail.setText(
                getString(
                        R.string.ai_adaptive_runtime_detail,
                        assistantFormatter.formatZoneDuration(
                        (int) recommendation.getCurrentPumpDurationSeconds()
                        ),
                        assistantFormatter.formatZoneDuration(
                        (int) recommendation.getRecommendedPumpDurationSeconds()
                        ),
                        recommendation.getWateringCountAnalyzed(),
                        Math.round(recommendation.getConfidence() * 100.0)
                )
        );

        cardAIAdaptiveRecommendation.setVisibility(View.VISIBLE);
    }

    /**
     * Firebase'den gelen AI açıklama bilgisini ekrana yansıtır.
     */
    private void renderAIExplanation(
            AIExplanation explanation
    ) {

        if (explanation == null) {
            return;
        }

        txtAIDecisionSummary.setText(
                assistantFormatter.safeText(
                        explanation.getSummary(),
                        getString(R.string.ai_runtime_explanation_preparing)
                )
        );

        List<String> reasonLines =
                explanation.getReasonLines();

        renderReasonLine(
                txtAIReasonOne,
                reasonLines,
                0
        );

        renderReasonLine(
                txtAIReasonTwo,
                reasonLines,
                1
        );

        renderReasonLine(
                txtAIReasonThree,
                reasonLines,
                2
        );

        int targetProgress =
                assistantFormatter.clampProgress(
                        explanation.getProgressPercent()
                );

        animateLearningProgress(
                targetProgress
        );

        txtAINextStep.setText(
                assistantFormatter.safeText(
                        explanation.getNextStep(),
                        getString(R.string.ai_runtime_new_data_waiting)
                )
        );

        if (decisionStepAdapter != null) {

            decisionStepAdapter.submitList(
                    DecisionFlowFactory.create(explanation)
            );
        }
    }

    /**
     * AI nedenleri bölümündeki satırı güvenli biçimde gösterir.
     * <p>
     * İlgili indeks boşsa TextView gizlenir.
     */
    private void renderReasonLine(
            TextView textView,
            List<String> reasonLines,
            int index
    ) {

        if (
                reasonLines == null
                        || index < 0
                        || index >= reasonLines.size()
        ) {

            textView.setVisibility(View.GONE);
            return;
        }

        String value =
                reasonLines.get(index);

        if (
                value == null
                        || value.trim().isEmpty()
        ) {

            textView.setVisibility(View.GONE);
            return;
        }

        textView.setVisibility(View.VISIBLE);
        textView.setText(value.trim());
    }

    /**
     * Öğrenme yüzdesini eski değerden yeni değere animasyonlu şekilde taşır.
     */
    private void animateLearningProgress(
            int targetProgress
    ) {

        targetProgress =
                assistantFormatter.clampProgress(targetProgress);

        if (progressAnimator != null) {
            progressAnimator.cancel();
        }

        if (currentLearningProgress == targetProgress) {

            renderLearningProgressImmediately(
                    targetProgress
            );

            return;
        }

        progressAnimator =
                ValueAnimator.ofInt(
                        currentLearningProgress,
                        targetProgress
                );

        progressAnimator.setDuration(500L);

        progressAnimator.addUpdateListener(
                animation -> {

                    int animatedProgress =
                            (int) animation.getAnimatedValue();

                    renderLearningProgressImmediately(
                            animatedProgress
                    );
                }
        );

        progressAnimator.start();
    }

    /**
     * Yüzde, segmentler, öğrenme aşaması ve açıklama metnini
     * verilen yüzdeye göre aynı anda günceller.
     */
    private void renderLearningProgressImmediately(
            int progress
    ) {

        int safeProgress =
                assistantFormatter.clampProgress(progress);

        currentLearningProgress =
                safeProgress;

        txtAIProgressPercent.setText(
                getString(
                        R.string.runtime_percentage_value,
                        String.valueOf(safeProgress)
                )
        );

        updateProgressSegments(
                safeProgress
        );

        updateLearningStage(
                safeProgress
        );
    }

    /**
     * 10 parçalı ilerleme göstergesini yüzdeye göre doldurur.
     * <p>
     * Örnek:
     * %40 = 4 aktif segment
     * %75 = 8 aktif segment
     */
    private void updateProgressSegments(
            int progress
    ) {

        int activeSegmentCount;

        if (progress <= 0) {

            activeSegmentCount = 0;

        } else {

            activeSegmentCount =
                    (int) Math.ceil(
                            progress / 20.0
                    );
        }

        activeSegmentCount =
                Math.max(
                        0,
                        Math.min(
                                activeSegmentCount,
                                progressSegments.length
                        )
                );

        for (
                int index = 0;
                index < progressSegments.length;
                index++
        ) {

            View segment =
                    progressSegments[index];

            if (segment == null) {
                continue;
            }

            if (index < activeSegmentCount) {

                segment.setBackgroundResource(
                        R.drawable.bg_ai_progress_segment_active
                );

            } else {

                segment.setBackgroundResource(
                        R.drawable.bg_ai_progress_segment_inactive
                );
            }
        }
    }

    /**
     * Öğrenme yüzdesine göre aşama ve açıklama metnini hesaplar.
     * <p>
     * %1–20   = Aşama 1
     * %21–40  = Aşama 2
     * %41–60  = Aşama 3
     * %61–80  = Aşama 4
     * %81–100 = Aşama 5
     */
    private void updateLearningStage(
            int progress
    ) {

        int learningStage;
        String description;

        if (profileLearningStage > 0) {
            learningStage = profileLearningStage;
            description = getProfileLearningDescription(
                    profileLearningStage,
                    learningDataCollectionCompleted,
                    progress
            );

        } else if (progress <= 20) {

            learningStage = 1;
            description = getString(R.string.ai_runtime_collecting_sensor_data);

        } else if (progress <= 40) {

            learningStage = 2;
            description = getString(R.string.ai_runtime_learning_sensor_behavior);

        } else if (progress <= 60) {

            learningStage = 3;
            description = getString(R.string.ai_runtime_building_soil_model);

        } else if (progress <= 80) {

            learningStage = 4;
            description = getString(R.string.ai_runtime_analyzing_watering_history);

        } else {

            learningStage = 5;

            if (progress >= 100) {

                description = getString(R.string.ai_runtime_learning_completed);

            } else {

                description = getString(R.string.ai_runtime_validating_model);
            }
        }

        txtAILearningStage.setText(
                getString(R.string.ai_runtime_learning_stage, learningStage)
        );

        txtAILearningDescription.setText(
                description
        );
    }

    /**
     * İlerleme değerini 0–100 sınırında tutar.
     */
    private String getProfileLearningDescription(
            int stage,
            boolean basicRecordsCompleted,
            int progress
    ) {
        switch (stage) {
            case 1:
                return getString(R.string.ai_runtime_collecting_sensor_data);
            case 2:
                return basicRecordsCompleted
                        ? getString(R.string.ai_runtime_records_complete_validating)
                        : getString(R.string.ai_runtime_observing_soil_change);
            case 3:
                return getString(R.string.ai_runtime_collecting_watering_results);
            case 4:
                return getString(R.string.ai_runtime_analyzing_soil_behavior);
            case 5:
                return progress >= 100
                        ? getString(R.string.ai_runtime_soil_profile_ready)
                        : getString(R.string.ai_runtime_increasing_learning_confidence);
            default:
                return getString(R.string.ai_runtime_learning_continues);
        }
    }

    /**
     * Güven seviyesini kullanıcı dostu Türkçe metne dönüştürür.
     */
    private String formatConfidence(
            String confidenceLevel,
            double confidence
    ) {

        String level;

        if (HIGH.equals(confidenceLevel)) {

            level = getString(R.string.ai_runtime_high_upper);

        } else if (MEDIUM.equals(confidenceLevel)) {

            level = getString(R.string.ai_runtime_medium_upper);

        } else {

            level = getString(R.string.ai_runtime_low_upper);
        }

        long percent =
                Math.round(
                        confidence * 100
                );

        /*
         * Backend bazı durumlarda güven değerini doğrudan yüzde
         * olarak gönderebilir. Böyle bir durumda ikinci kez
         * 100 ile çarpılmasını engeller.
         */
        if (confidence > 1.0) {

            percent =
                    Math.round(confidence);
        }

        percent =
                Math.max(
                        0,
                        Math.min(
                                percent,
                                100
                        )
                );

        return getString(
                R.string.ai_confidence_value,
                level,
                (int) percent
        );
    }

    /**
     * Backend toprak sınıflandırmasını Türkçeleştirir.
     */
    private String formatSoilClassification(
            String classification
    ) {

        if (classification == null) {
            return getString(R.string.ai_runtime_unknown);
        }

        switch (classification) {

            case HIGH_WATER_RETENTION:
                return getString(R.string.ai_runtime_retains_water_well);

            case SLOW_DRYING:
                return getString(R.string.ai_runtime_dries_slowly);

            case BALANCED:
                return getString(R.string.ai_runtime_balanced);

            case FAST_DRYING:
                return getString(R.string.ai_runtime_dries_fast);

            case VERY_FAST_DRYING:
                return getString(R.string.ai_runtime_dries_very_fast);

            default:
                return getString(R.string.ai_runtime_unknown);
        }
    }

    /**
     * Backend nem trendi bilgisini Türkçeleştirir.
     */
    private String formatTrendClassification(
            String classification
    ) {

        if (classification == null) {
            return getString(R.string.ai_runtime_waiting);
        }

        switch (classification) {

            case STABLE:
                return getString(R.string.ai_runtime_stable);

            case RISING:
                return getString(R.string.ai_runtime_rising);

            case SLOW_DRYING:
                return getString(R.string.ai_runtime_slow_drying);

            case NORMAL_DRYING:
                return getString(R.string.ai_runtime_normal_drying);

            case FAST_DRYING:
                return getString(R.string.ai_runtime_fast_drying);

            case VERY_FAST_DRYING:
                return getString(R.string.ai_runtime_very_fast_drying);

            case INSUFFICIENT_DATA:
                return getString(R.string.ai_runtime_collecting_data);

            default:
                return getString(R.string.ai_runtime_waiting);
        }
    }

    /**
     * ISO tarih biçiminden saat bilgisini alır.
     * <p>
     * Örnek:
     * 2026-07-19T18:45:30
     * sonuç: 18:45
     */
    private String formatUpdatedAt(
            String updatedAt
    ) {

        if (
                updatedAt == null
                        || updatedAt.trim().isEmpty()
        ) {

            return getString(R.string.ai_runtime_waiting);
        }

        try {

            int separatorIndex =
                    updatedAt.indexOf("T");

            if (
                    separatorIndex < 0
                            || updatedAt.length()
                            < separatorIndex + 6
            ) {

                return getString(R.string.ai_runtime_waiting);
            }

            return updatedAt.substring(
                    separatorIndex + 1,
                    separatorIndex + 6
            );

        } catch (RuntimeException exception) {

            return getString(R.string.ai_runtime_update_waiting);
        }
    }

    /**
     * AI kararına göre sağ üst durum rozetinin rengini ve metnini ayarlar.
     */
    private void applyDecisionStyle(
            String decisionCode,
            String severity
    ) {

        int textColorRes;
        int backgroundColorRes;
        String badgeText;

        if (LEARNING.equals(decisionCode)) {

            textColorRes = R.color.primary;
            backgroundColorRes = R.color.primaryLight;
            badgeText = getString(R.string.ai_runtime_learning_upper);

        } else if (SENSOR_UNSTABLE.equals(decisionCode)) {

            textColorRes = R.color.warning;
            backgroundColorRes = R.color.warningBackground;
            badgeText = getString(R.string.ai_runtime_badge_sensor);

        } else if (WATERING_RECOMMENDED.equals(decisionCode)) {

            textColorRes = R.color.warning;
            backgroundColorRes = R.color.warningBackground;
            badgeText = getString(R.string.ai_runtime_badge_watering);

        } else if (NO_ACTION_REQUIRED.equals(decisionCode)) {

            textColorRes = R.color.online;
            backgroundColorRes = R.color.onlineBackground;
            badgeText = getString(R.string.ai_runtime_badge_suitable);

        } else if (SYSTEM_DISABLED.equals(decisionCode)) {

            textColorRes = R.color.offline;
            backgroundColorRes = R.color.offlineBackground;
            badgeText = getString(R.string.ai_runtime_badge_closed);

        } else if (MANUAL_MODE.equals(decisionCode)) {

            textColorRes = R.color.info;
            backgroundColorRes = R.color.infoBackground;
            badgeText = getString(R.string.ai_runtime_badge_manual);

        } else if (INCREASE_PUMP_DURATION.equals(decisionCode)) {

            textColorRes = R.color.warning;
            backgroundColorRes = R.color.warningBackground;
            badgeText = getString(R.string.ai_runtime_badge_increase);

        } else if (DECREASE_PUMP_DURATION.equals(decisionCode)) {

            textColorRes = R.color.info;
            backgroundColorRes = R.color.infoBackground;
            badgeText = getString(R.string.ai_runtime_badge_decrease);

        } else if (CRITICAL.equals(severity)) {

            textColorRes = R.color.offline;
            backgroundColorRes = R.color.offlineBackground;
            badgeText = getString(R.string.ai_runtime_badge_critical);

        } else {

            textColorRes = R.color.primary;
            backgroundColorRes = R.color.primaryLight;
            badgeText = getString(R.string.ai_runtime_badge_info);
        }

        int textColor =
                ContextCompat.getColor(
                        this,
                        textColorRes
                );

        int backgroundColor =
                ContextCompat.getColor(
                        this,
                        backgroundColorRes
                );

        txtAISeverityBadge.setText(
                badgeText
        );

        txtAISeverityBadge.setTextColor(
                textColor
        );

        cardAISeverityBadge.setStrokeColor(
                textColor
        );

        cardAISeverityBadge.setCardBackgroundColor(
                backgroundColor
        );
    }

    /**
     * Kartların ekran açılırken sırayla görünmesini sağlar.
     */
    private void startEntranceAnimations() {

        View[] cards = {
                cardAIDecision,
                cardAIZoneSummary,
                cardAINextStep,
                cardAIProgress,
                btnAIAdvancedDetails
        };

        for (
                int index = 0;
                index < cards.length;
                index++
        ) {

            View card =
                    cards[index];

            if (card == null) {
                continue;
            }

            card.setAlpha(0f);
            card.setTranslationY(28f);

            card.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(index * 90L)
                    .setDuration(320L)
                    .start();
        }
    }

    /**
     * Boş Firebase metinlerine karşı güvenli değer döndürür.
     */
    private void updatePredictionZones(List<GardenZone> zones) {
        String selectedZoneId = requestedZoneId != null && !requestedZoneId.trim().isEmpty()
                ? requestedZoneId.trim()
                : predictionZones.isEmpty()
                ? ""
                : predictionZones.get(selectedPredictionZoneIndex).getZone_id();
        predictionZones.clear();

        if (zones != null) {
            predictionZones.addAll(viewModel.activeZones(zones));
            predictionZones.sort(Comparator.comparingInt(GardenZone::getOrder));
        }

        selectedPredictionZoneIndex = 0;
        boolean requestedZoneFound = false;
        for (int index = 0; index < predictionZones.size(); index++) {
            if (selectedZoneId.equals(predictionZones.get(index).getZone_id())) {
                selectedPredictionZoneIndex = index;
                requestedZoneFound = true;
                break;
            }
        }
        if (requestedZoneFound) {
            requestedZoneId = null;
        }
        renderSelectedZoneAI();
    }

    private void movePredictionZone(int direction) {
        if (predictionZones.isEmpty()) {
            return;
        }
        selectedPredictionZoneIndex = (selectedPredictionZoneIndex + direction
                + predictionZones.size()) % predictionZones.size();
        renderSelectedZoneAI();
    }

    private void renderSelectedZoneAI() {
        selectedZoneSummaryRenderer.render(
                predictionZones,
                selectedPredictionZoneIndex
        );

        if (predictionZones.isEmpty()) {
            txtMoisturePredictionTitle.setText(R.string.ai_moisture_prediction_default_title);
            txtMoisturePredictionZone.setText(R.string.runtime_no_active_season_zones);
            renderAIDecision(fallbackAIDecision);
            renderAdaptiveRecommendation(fallbackAdaptiveRecommendation);
            renderAIExplanation(fallbackAIExplanation);
            renderMoisturePredictionData(latestMoisturePrediction);
            renderPredictionAccuracy(fallbackPredictionAccuracy);
            renderUnifiedConfidence(fallbackUnifiedConfidence);
            predictionValidationRenderer.render(fallbackPredictionValidationStatus);
            renderSoilLearningProfile(fallbackSoilLearningProfile);
            setAdvancedDetailsVisible(advancedDetailsVisible);
            renderWeatherGuidance();
            return;
        }

        GardenZone zone = predictionZones.get(selectedPredictionZoneIndex);
        String emoji = assistantFormatter.safeText(zone.getEmoji(), getString(R.string.symbol_plant));
        String name = com.alidogukan.avora.zones.PhysicalZoneIdentity.name(zone);
        txtMoisturePredictionTitle.setText(getString(
                R.string.ai_moisture_prediction_zone_title,
                emoji,
                name
        ));
        txtMoisturePredictionZone.setText(getString(
                R.string.ai_zone_swipe_position,
                selectedPredictionZoneIndex + 1,
                predictionZones.size()
        ));

        ZoneAIState zoneAI = zone.getAi();
        if (!isCurrentZoneAI(zone, zoneAI)) {
            renderZoneAIWaiting(zone);
            setAdvancedDetailsVisible(advancedDetailsVisible);
            renderWeatherGuidance();
            return;
        }

        cardAINextStep.setVisibility(View.VISIBLE);
        renderAIDecision(zoneAI.getDecision());
        renderAdaptiveRecommendation(zoneAI.getAdaptiveRecommendation());
        renderAIExplanation(zoneAI.getExplanation());
        renderMoisturePredictionData(zoneAI.getMoisturePrediction());
        renderPredictionAccuracy(zoneAI.getPredictionAccuracy());
        renderUnifiedConfidence(zoneAI.getConfidence());
        predictionValidationRenderer.render(zoneAI.getPredictionValidation());
        renderSoilLearningProfile(zoneAI.getLearningProfile());
        setAdvancedDetailsVisible(advancedDetailsVisible);
        renderWeatherGuidance();
    }

    private boolean isCurrentZoneAI(GardenZone zone, ZoneAIState zoneAI) {
        if (zone == null || zoneAI == null) return false;
        String zoneId = assistantFormatter.safeText(zone.getZone_id(), "").trim();
        String sensorId = assistantFormatter.safeText(zone.getSensor_id(), "").trim();
        String aiZoneId = assistantFormatter.safeText(zoneAI.getZoneId(), "").trim();
        String aiSensorId = assistantFormatter.safeText(zoneAI.getSensorId(), "").trim();
        return !zoneId.isEmpty()
                && !sensorId.isEmpty()
                && zoneId.equals(aiZoneId)
                && sensorId.equals(aiSensorId)
                && ZoneAIStateFreshnessChecker.isFresh(
                        zoneAI.getUpdatedAt(),
                        java.time.LocalDateTime.now()
                );
    }

    private void renderZoneAIWaiting(GardenZone zone) {
        String sensorId = assistantFormatter.safeText(
                zone.getSensor_id(),
                getString(R.string.ai_zone_data_waiting)
        );
        txtAIDecisionTitle.setText(R.string.ai_zone_decision_preparing);
        txtAIAnalysisScope.setText(getString(R.string.ai_zone_analysis_scope, sensorId));
        txtAIDecisionSummary.setText(R.string.ai_zone_data_waiting);
        txtAINextStep.setText(R.string.ai_zone_prediction_preparing);
        txtAIConfidence.setText(formatConfidence(LOW, 0.0));
        txtAIReasonOne.setVisibility(View.GONE);
        txtAIReasonTwo.setVisibility(View.GONE);
        txtAIReasonThree.setVisibility(View.GONE);
        cardAIAdaptiveRecommendation.setVisibility(View.GONE);
        cardAIReasons.setVisibility(View.GONE);
        cardAIProgress.setVisibility(View.GONE);
        renderZonePredictionWaiting(zone);
        renderPredictionAccuracy(null);
        renderUnifiedConfidence(null);
        predictionValidationRenderer.render(null);
    }

    private void renderZonePredictionWaiting(GardenZone zone) {
        txtMoisturePredictionStatus.setText(R.string.ai_zone_learning_status);
        txtPredictionCurrentMoisture.setText(formatMoistureValue(zone.getMoisture()));
        txtPredictionMoistureLimit.setText(formatMoistureValue(zone.getMoisture_limit()));
        txtPredictionOneHour.setText(assistantFormatter.unavailableValue());
        txtPredictionThreeHours.setText(assistantFormatter.unavailableValue());
        txtPredictionSixHours.setText(assistantFormatter.unavailableValue());
        txtPredictionTimeUntilLimit.setText(R.string.ai_zone_prediction_preparing);
        txtMoisturePredictionConfidence.setText(R.string.ai_zone_low_confidence);
        txtPredictionLimitReachedAt.setText(R.string.ai_zone_limit_time_waiting);
        txtMoisturePredictionUpdatedAt.setText(R.string.ai_zone_prediction_not_ready);
        applyMoisturePredictionWaitingStyle();
    }

    private void renderMoisturePrediction(
            MoisturePrediction prediction
    ) {
        latestMoisturePrediction = prediction;
        renderSelectedZoneAI();
    }

    private void renderMoisturePredictionData(
            MoisturePrediction prediction
    ) {

        if (prediction == null) {
            renderMoisturePredictionEmpty();
            return;
        }

        String predictionStatus =
                assistantFormatter.safeText(
                        prediction.getPrediction_status(),
                        INSUFFICIENT_DATA
                )
                        .trim()
                        .toUpperCase(Locale.ROOT);

        if (INSUFFICIENT_DATA.equals(predictionStatus)) {

            renderMoisturePredictionLearning(
                    prediction
            );

            return;
        }

        txtPredictionCurrentMoisture.setText(
                formatMoistureValue(
                        prediction.getCurrent_moisture()
                )
        );

        txtPredictionMoistureLimit.setText(
                formatMoistureValue(
                        prediction.getMoisture_limit()
                )
        );

        txtPredictionOneHour.setText(
                formatMoistureValue(
                        prediction.getPredicted_moisture_1_hour()
                )
        );

        txtPredictionThreeHours.setText(
                formatMoistureValue(
                        prediction.getPredicted_moisture_3_hours()
                )
        );

        txtPredictionSixHours.setText(
                formatMoistureValue(
                        prediction.getPredicted_moisture_6_hours()
                )
        );

        txtPredictionTimeUntilLimit.setText(
                formatMinutesUntilLimit(
                        prediction.getEstimated_minutes_until_limit()
                )
        );

        txtMoisturePredictionConfidence.setText(
                formatConfidence(
                        prediction.getConfidence_level(),
                        prediction.getConfidence()
                )
        );

        txtPredictionLimitReachedAt.setText(
                getString(
                        R.string.ai_runtime_prediction_limit_prefix,
                        formatPredictionDateTime(
                                prediction.getEstimated_limit_reached_at())
                )
        );

        txtMoisturePredictionUpdatedAt.setText(
                getString(
                        R.string.ai_runtime_last_updated_prefix,
                        formatPredictionDateTime(prediction.getGenerated_at())
                )
        );

        applyMoisturePredictionStatusStyle(
                predictionStatus
        );
    }

    private void renderPredictionAccuracy(
            PredictionAccuracy accuracy
    ) {

        if (accuracy == null) {
            renderPredictionAccuracyEmpty();
            return;
        }

        String status =
                assistantFormatter.safeText(
                        accuracy.getStatus(),
                        INSUFFICIENT_DATA
                )
                        .trim()
                        .toUpperCase(Locale.ROOT);

        long predictionCount =
                Math.max(
                        0,
                        accuracy.getPrediction_count()
                );

        long successfulPredictions =
                Math.max(
                        0,
                        accuracy.getSuccessful_predictions()
                );

        if (
                INSUFFICIENT_DATA.equals(status)
                        || predictionCount == 0
        ) {

            renderPredictionAccuracyLearning(
                    accuracy
            );

            return;
        }

        double accuracyPercent =
                assistantFormatter.normalizePercent(
                        accuracy.getAccuracy_percent()
                );

        txtPredictionAccuracyPercent.setText(
                formatPercent(
                        accuracyPercent
                )
        );

        txtPredictionConfidenceMultiplier.setText(
                formatMultiplier(
                        accuracy.getConfidence_multiplier()
                )
        );

        txtPredictionCount.setText(
                String.valueOf(
                        predictionCount
                )
        );

        txtSuccessfulPredictions.setText(
                String.valueOf(
                        successfulPredictions
                )
        );

        txtPredictionAverageError.setText(
                formatErrorValue(
                        accuracy.getAverage_error()
                )
        );

        txtPredictionMinimumError.setText(
                formatErrorValue(
                        accuracy.getMinimum_error()
                )
        );

        txtPredictionMaximumError.setText(
                formatErrorValue(
                        accuracy.getMaximum_error()
                )
        );

        txtPredictionAccuracyUpdatedAt.setText(
                getString(
                        R.string.ai_runtime_last_updated_prefix,
                        formatPredictionDateTime(accuracy.getGenerated_at())
                )
        );

        progressPredictionAccuracy.setVisibility(
                View.VISIBLE
        );

        int progress =
                (int) Math.round(
                        accuracyPercent
                );

        progressPredictionAccuracy.setProgressCompat(
                progress,
                true
        );

        progressPredictionAccuracy.setVisibility(
                View.INVISIBLE
        );

        applyPredictionAccuracyStatusStyle(
                status,
                predictionCount
        );
    }

    private void renderPredictionAccuracyLearning(
            PredictionAccuracy accuracy
    ) {

        txtPredictionAccuracyStatus.setText(
                getString(R.string.ai_runtime_learning_upper)
        );

        txtPredictionAccuracyPercent.setText(
                assistantFormatter.unavailableValue()
        );

        txtPredictionConfidenceMultiplier.setText(
                formatMultiplier(
                        accuracy.getConfidence_multiplier()
                )
        );

        txtPredictionCount.setText(
                String.valueOf(
                        Math.max(
                                0,
                                accuracy.getPrediction_count()
                        )
                )
        );

        txtSuccessfulPredictions.setText(
                String.valueOf(
                        Math.max(
                                0,
                                accuracy.getSuccessful_predictions()
                        )
                )
        );

        txtPredictionAverageError.setText(
                assistantFormatter.unavailableValue()
        );

        txtPredictionMinimumError.setText(
                assistantFormatter.unavailableValue()
        );

        txtPredictionMaximumError.setText(
                assistantFormatter.unavailableValue()
        );

        txtPredictionAccuracyUpdatedAt.setText(
                getString(
                        R.string.ai_runtime_last_updated_prefix,
                        formatPredictionDateTime(accuracy.getGenerated_at())
                )
        );

        progressPredictionAccuracy.setProgressCompat(
                0,
                false
        );

        progressPredictionAccuracy.setVisibility(
                View.INVISIBLE
        );
        applyPredictionAccuracyWaitingStyle();
    }
    private void renderPredictionAccuracyEmpty() {

        txtPredictionAccuracyStatus.setText(
                getString(R.string.ai_runtime_waiting_upper)
        );

        txtPredictionAccuracyPercent.setText(
                assistantFormatter.unavailableValue()
        );

        txtPredictionConfidenceMultiplier.setText(
                assistantFormatter.unavailableValue()
        );

        txtPredictionCount.setText(
                "0"
        );

        txtSuccessfulPredictions.setText(
                "0"
        );

        txtPredictionAverageError.setText(
                assistantFormatter.unavailableValue()
        );

        txtPredictionMinimumError.setText(
                assistantFormatter.unavailableValue()
        );

        txtPredictionMaximumError.setText(
                assistantFormatter.unavailableValue()
        );

        txtPredictionAccuracyUpdatedAt.setText(
                getString(R.string.ai_runtime_last_updated_unavailable)
        );

        progressPredictionAccuracy.setProgressCompat(
                0,
                false
        );

        progressPredictionAccuracy.setVisibility(
                View.INVISIBLE
        );

        applyPredictionAccuracyWaitingStyle();
    }

    private String formatPercent(
            double percent
    ) {

        return String.format(
                Locale.getDefault(),
                getString(R.string.ai_runtime_moisture_percent),
                assistantFormatter.normalizePercent(percent)
        );
    }

    private String formatEfficiencyStatus(
            double efficiency
    ) {

        if (efficiency <= 0) {

            return getString(R.string.ai_runtime_learning);

        }


        if (efficiency < 0.02) {

            return getString(R.string.ai_runtime_low);

        }


        if (efficiency < 0.05) {

            return getString(R.string.ai_runtime_normal);

        }


        return getString(R.string.ai_runtime_good);
    }

    private void applyEfficiencyStatusStyle(
            String status
    ) {

        if (getString(R.string.ai_runtime_good).equals(status)) {

            txtAIWateringEfficiencyStatus
                    .setTextColor(
                            ContextCompat.getColor(
                                    this,
                                    R.color.online
                            )
                    );


        } else if (getString(R.string.ai_runtime_normal).equals(status)) {

            txtAIWateringEfficiencyStatus
                    .setTextColor(
                            ContextCompat.getColor(
                                    this,
                                    R.color.warning
                            )
                    );


        } else if (getString(R.string.ai_runtime_low).equals(status)) {

            txtAIWateringEfficiencyStatus
                    .setTextColor(
                            ContextCompat.getColor(
                                    this,
                                    R.color.offline
                            )
                    );


        } else {

            txtAIWateringEfficiencyStatus
                    .setTextColor(
                            ContextCompat.getColor(
                                    this,
                                    R.color.primary
                            )
                    );
        }
    }

    private String formatErrorValue(
            double error
    ) {

        if (
                Double.isNaN(error)
                        || Double.isInfinite(error)
                        || error < 0
        ) {
            return assistantFormatter.unavailableValue();
        }

        return String.format(
                Locale.getDefault(),
                getString(R.string.ai_runtime_error_percent),
                error
        );
    }

    private String formatMultiplier(
            double multiplier
    ) {

        if (
                Double.isNaN(multiplier)
                        || Double.isInfinite(multiplier)
                        || multiplier < 0
        ) {
            return assistantFormatter.unavailableValue();
        }

        return String.format(
                Locale.getDefault(),
                getString(R.string.ai_runtime_multiplier),
                multiplier
        );
    }

    private void applyPredictionAccuracyStatusStyle(
            String status,
            long predictionCount
    ) {

        if (
                READY.equals(status)
                        && predictionCount > 0
        ) {

            txtPredictionAccuracyStatus.setText(
                    getString(R.string.ai_runtime_ready_upper)
            );

            applyPredictionAccuracyReadyStyle();

        } else if (
                INSUFFICIENT_DATA.equals(status)
                        || predictionCount == 0
        ) {

            txtPredictionAccuracyStatus.setText(
                    getString(R.string.ai_runtime_learning_upper)
            );

            applyPredictionAccuracyWaitingStyle();

        } else {

            txtPredictionAccuracyStatus.setText(
                    getString(R.string.ai_runtime_waiting_upper)
            );

            applyPredictionAccuracyWaitingStyle();
        }
    }

    private void applyPredictionAccuracyReadyStyle() {

        int online =
                ContextCompat.getColor(
                        this,
                        R.color.online
                );

        int onlineBackground =
                ContextCompat.getColor(
                        this,
                        R.color.onlineBackground
                );

        cardPredictionAccuracyStatusBadge
                .setCardBackgroundColor(
                        onlineBackground
                );

        cardPredictionAccuracyStatusBadge
                .setStrokeColor(
                        online
                );

        txtPredictionAccuracyStatus.setTextColor(
                online
        );
    }

    private void applyPredictionAccuracyWaitingStyle() {

        int primary =
                ContextCompat.getColor(
                        this,
                        R.color.primary
                );

        int primaryLight =
                ContextCompat.getColor(
                        this,
                        R.color.primaryLight
                );

        cardPredictionAccuracyStatusBadge
                .setCardBackgroundColor(
                        primaryLight
                );

        cardPredictionAccuracyStatusBadge
                .setStrokeColor(
                        primary
                );

        txtPredictionAccuracyStatus.setTextColor(
                primary
        );
    }



    private void renderMoisturePredictionLearning(
            MoisturePrediction prediction
    ) {

        txtMoisturePredictionStatus.setText(
                getString(R.string.ai_runtime_learning_upper)
        );

        txtPredictionCurrentMoisture.setText(
                formatMoistureValue(
                        prediction.getCurrent_moisture()
                )
        );

        txtPredictionMoistureLimit.setText(
                formatMoistureValue(
                        prediction.getMoisture_limit()
                )
        );

        txtPredictionOneHour.setText(assistantFormatter.unavailableValue());
        txtPredictionThreeHours.setText(assistantFormatter.unavailableValue());
        txtPredictionSixHours.setText(assistantFormatter.unavailableValue());

        txtPredictionTimeUntilLimit.setText(
                getString(R.string.ai_runtime_sufficient_data_waiting)
        );

        txtMoisturePredictionConfidence.setText(
                formatConfidence(
                        prediction.getConfidence_level(),
                        prediction.getConfidence()
                )
        );

        txtPredictionLimitReachedAt.setText(
                getString(R.string.ai_runtime_prediction_limit_unavailable)
        );

        txtMoisturePredictionUpdatedAt.setText(
                getString(
                        R.string.ai_runtime_last_updated_prefix,
                        formatPredictionDateTime(prediction.getGenerated_at())
                )
        );

        applyMoisturePredictionWaitingStyle();
    }

    private void renderMoisturePredictionEmpty() {

        txtMoisturePredictionStatus.setText(
                getString(R.string.ai_runtime_waiting_upper)
        );

        txtPredictionCurrentMoisture.setText(
                assistantFormatter.unavailableValue()
        );

        txtPredictionMoistureLimit.setText(
                assistantFormatter.unavailableValue()
        );

        txtPredictionOneHour.setText(
                assistantFormatter.unavailableValue()
        );

        txtPredictionThreeHours.setText(
                assistantFormatter.unavailableValue()
        );

        txtPredictionSixHours.setText(
                assistantFormatter.unavailableValue()
        );

        txtPredictionTimeUntilLimit.setText(
                getString(R.string.ai_runtime_calculating)
        );

        txtMoisturePredictionConfidence.setText(
                assistantFormatter.unavailableValue()
        );

        txtPredictionLimitReachedAt.setText(
                getString(R.string.ai_runtime_prediction_limit_unavailable)
        );

        txtMoisturePredictionUpdatedAt.setText(
                getString(R.string.ai_runtime_last_updated_unavailable)
        );

        applyMoisturePredictionWaitingStyle();
    }

    private String formatMoistureValue(
            double moisture
    ) {

        double safeMoisture =
                Math.max(
                        0.0,
                        Math.min(
                                moisture,
                                100.0
                        )
                );

        return String.format(
                Locale.getDefault(),
                getString(R.string.ai_runtime_moisture_percent),
                safeMoisture
        );
    }

    private String formatMinutesUntilLimit(
            double estimatedMinutes
    ) {

        if (
                Double.isNaN(estimatedMinutes)
                        || Double.isInfinite(estimatedMinutes)
                        || estimatedMinutes <= 0
        ) {
            return getString(R.string.ai_runtime_cannot_calculate);
        }

        long totalMinutes =
                Math.round(
                        estimatedMinutes
                );

        long days =
                totalMinutes / 1440;

        long hours =
                (
                        totalMinutes % 1440
                ) / 60;

        long minutes =
                totalMinutes % 60;

        if (days > 0) {

            return String.format(
                    Locale.getDefault(),
                    getString(R.string.ai_runtime_days_hours),
                    days,
                    hours
            );
        }

        if (hours > 0) {

            return String.format(
                    Locale.getDefault(),
                    getString(R.string.ai_runtime_hours_minutes),
                    hours,
                    minutes
            );
        }

        return String.format(
                Locale.getDefault(),
                getString(R.string.ai_runtime_minutes_only),
                minutes
        );
    }

    private String formatPredictionDateTime(
            String isoDateTime
    ) {

        if (
                isoDateTime == null
                        || isoDateTime.trim().isEmpty()
        ) {
            return assistantFormatter.unavailableValue();
        }

        try {

            java.time.LocalDateTime dateTime =
                    java.time.LocalDateTime.parse(
                            isoDateTime.trim()
                    );

            return dateTime.format(
                    java.time.format.DateTimeFormatter
                            .ofPattern(
                                    "dd-MM-yyyy HH:mm",
                                    Locale.getDefault()
                            )
            );

        } catch (Exception exception) {

            Log.w(
                    TAG,
                    "Moisture prediction date could not be formatted.",
                    exception
            );

            return assistantFormatter.unavailableValue();
        }
    }

    private void applyMoisturePredictionStatusStyle(
            String predictionStatus
    ) {

        if (READY.equals(predictionStatus)) {

            txtMoisturePredictionStatus.setText(
                    getString(R.string.ai_runtime_ready_upper)
            );

            applyMoisturePredictionReadyStyle();

        } else if (
                INSUFFICIENT_DATA.equals(
                        predictionStatus
                )
        ) {

            txtMoisturePredictionStatus.setText(
                    getString(R.string.ai_runtime_learning_upper)
            );

            applyMoisturePredictionWaitingStyle();

        } else {

            txtMoisturePredictionStatus.setText(
                    getString(R.string.ai_runtime_waiting_upper)
            );

            applyMoisturePredictionWaitingStyle();
        }
    }

    private void applyMoisturePredictionReadyStyle() {

        int online =
                ContextCompat.getColor(
                        this,
                        R.color.online
                );

        int onlineBackground =
                ContextCompat.getColor(
                        this,
                        R.color.onlineBackground
                );

        cardMoisturePredictionStatusBadge
                .setCardBackgroundColor(
                        onlineBackground
                );

        cardMoisturePredictionStatusBadge
                .setStrokeColor(
                        online
                );

        txtMoisturePredictionStatus.setTextColor(
                online
        );
    }

    private void applyMoisturePredictionWaitingStyle() {

        int primary =
                ContextCompat.getColor(
                        this,
                        R.color.primary
                );

        int primaryLight =
                ContextCompat.getColor(
                        this,
                        R.color.primaryLight
                );

        cardMoisturePredictionStatusBadge
                .setCardBackgroundColor(
                        primaryLight
                );

        cardMoisturePredictionStatusBadge
                .setStrokeColor(
                        primary
                );

        txtMoisturePredictionStatus.setTextColor(
                primary
        );
    }
    @Override
    protected void onDestroy() {

        if (progressAnimator != null) {

            progressAnimator.cancel();
            progressAnimator = null;
        }

        super.onDestroy();
    }
}
