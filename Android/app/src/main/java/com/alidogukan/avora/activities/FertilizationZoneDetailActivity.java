package com.alidogukan.avora.activities;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.view.LayoutInflater;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.models.FertilizationProfile;
import com.alidogukan.avora.models.FertilizerApplication;
import com.alidogukan.avora.models.FertilizerProduct;
import com.alidogukan.avora.fertilization.FertilizerAdvice;
import com.alidogukan.avora.fertilization.FertilizerExperiencePresenter;
import com.alidogukan.avora.fertilization.FertilizationScheduleView;
import com.alidogukan.avora.models.FertilizerRecommendation;
import com.alidogukan.avora.models.FertilizerStageGuide;
import com.alidogukan.avora.models.GardenSeason;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.WeatherForecast;
import com.alidogukan.avora.season.SeasonDisplayIdentity;
import com.alidogukan.avora.viewmodels.FertilizationZoneDetailViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class FertilizationZoneDetailActivity
        extends EdgeToEdgeActivity {

    public static final String EXTRA_ZONE_ID = "zone_id";

    private static final String[] STAGE_CODES = {
            "NOT_SET",
            "ROOTING",
            "VEGETATIVE",
            "FLOWERING",
            "FRUITING",
            "HARVEST",
            "SEASON_END"
    };
    private static DateTimeFormatter displayDateFormat() {
        return DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.getDefault());
    }


    private FertilizationZoneDetailViewModel viewModel;
    private com.alidogukan.avora.viewmodels.DisplayUnitsViewModel displayUnits;

    private TextInputEditText inputPlantingDate;
    private TextInputEditText inputFertilizationArea;
    private TextInputEditText inputFertilizationTank;
    private MaterialAutoCompleteTextView dropdownGrowthStage;
    private MaterialAutoCompleteTextView dropdownProduct;
    private NestedScrollView scrollFertilizationZone;
    private TextView txtApplicationPreview;
    private TextView txtFertilizationDoseCalculation;
    private TextView txtFertilizerRecommendation;
    private MaterialSwitch switchEnabled;
    private MaterialSwitch switchReminder;
    private MaterialButton btnSave;
    private MaterialCardView cardUnsaved;
    private MaterialCardView cardFertilizerRecommendation;
    private MaterialButton btnUseFertilizerRecommendation;
    private MaterialButton btnRecordFertilizerApplication;
    private MaterialButton btnAdvanceGrowthStage;
    private MaterialButton btnWaterAnalysis;
    private MaterialButton btnZonePhoto;
    private MaterialButton btnZonePlantAssistant;
    private MaterialCardView cardZoneAiAdvice;
    private TextView txtZoneAiAdviceStatus;
    private TextView txtZoneAiAdviceReason;
    private TextView txtZoneAiAdviceContext;
    private TextView txtZoneAiAdviceProducts;
    private MaterialCardView cardZoneAiExperience;
    private TextView txtZoneAiExperience;
    private TextView txtZoneAiAdviceRisks;
    private TextView txtWaterAnalysisSummary;
    private MaterialButton btnDiscardChanges;
    private FertilizationScheduleView fertilizationScheduleView;

    private String zoneId;
    private boolean rendering;
    private boolean saving;
    private boolean remoteLoaded;
    private boolean saveAndExit;
    private boolean originalEnabled;
    private boolean originalReminder = true;
    private String originalPlantingDate = "";
    private String originalStage = "NOT_SET";
    private String selectedStage = "NOT_SET";
    private String originalProductId = "";
    private String selectedProductId = "";
    private long originalLastApplicationAt;
    private long originalNextApplicationAt;
    private double originalAreaM2;
    private double originalTankLiters;
    private String zonePlantType = "";
    private String zoneName = "";
    private GardenZone currentZone;
    private List<GardenSeason> currentSeasons = new ArrayList<>();
    private WeatherForecast currentWeather;
    private List<FertilizerApplication> currentHistory = new ArrayList<>();
    private FertilizerAdvice currentFertilizerAdvice;
    private final List<FertilizerProduct> products =
            new ArrayList<>();
    private final List<FertilizerProduct> allProducts =
            new ArrayList<>();
    private final List<FertilizerRecommendation> recommendations =
            new ArrayList<>();
    private final List<FertilizerStageGuide> stageGuides =
            new ArrayList<>();

    private static class SuggestedApplicationDose {
        final double min;
        final double max;
        final String unit;
        final String note;

        SuggestedApplicationDose(
                double min,
                double max,
                String unit,
                String note
        ) {
            this.min = min;
            this.max = max;
            this.unit = unit;
            this.note = note;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fertilization_zone_detail);

        zoneId = getIntent().getStringExtra(EXTRA_ZONE_ID);
        if (zoneId == null || zoneId.isBlank()) {
            finish();
            return;
        }
        viewModel = new ViewModelProvider(this).get(
                FertilizationZoneDetailViewModel.class);
        displayUnits = new ViewModelProvider(this)
                .get(com.alidogukan.avora.viewmodels.DisplayUnitsViewModel.class);
        viewModel.setZoneId(zoneId);

        bindViews();
        bindActions();
        viewModel.getProducts().observe(
                this,
                this::renderProducts
        );
        viewModel.getRecommendations().observe(
                this,
                this::renderRecommendations
        );
        viewModel.getStageGuides().observe(
                this,
                this::renderStageGuides
        );
        viewModel.getZone().observe(
                this,
                this::renderZone
        );
        viewModel.getSeasons().observe(this, seasons -> {
            currentSeasons = seasons == null ? new ArrayList<>() : seasons;
            if (currentZone != null) renderZoneIdentity(currentZone);
        });
        viewModel.getHistory().observe(this, history -> {
            currentHistory = history == null ? new ArrayList<>() : history;
            renderZoneAiAdvice();
        });
        viewModel.getWeather().observe(this, weather -> {
            currentWeather = weather;
            renderZoneAiAdvice();
        });
    }

    private void bindViews() {
        inputPlantingDate = findViewById(R.id.inputPlantingDate);
        inputFertilizationArea = findViewById(
                R.id.inputFertilizationArea
        );
        inputFertilizationTank = findViewById(
                R.id.inputFertilizationTank
        );
        inputFertilizationArea.setHint(getString(
                R.string.fertilization_area_unit, displayUnits.areaSymbol()));
        inputFertilizationTank.setHint(getString(
                R.string.fertilization_tank_unit, displayUnits.volumeSymbol()));
        dropdownGrowthStage = findViewById(
                R.id.dropdownGrowthStage
        );
        dropdownProduct = findViewById(R.id.dropdownProduct);
        scrollFertilizationZone = findViewById(R.id.scrollFertilizationZone);
        txtApplicationPreview = findViewById(
                R.id.txtApplicationPreview
        );
        // Dose information is now shown under each AI product recommendation.
        txtFertilizationDoseCalculation = null;
        txtFertilizerRecommendation = findViewById(
                R.id.txtFertilizerRecommendation
        );
        switchEnabled = findViewById(R.id.switchPlanEnabled);
        switchReminder = findViewById(R.id.switchReminder);
        btnSave = findViewById(R.id.btnSave);
        cardUnsaved = findViewById(R.id.cardUnsavedChanges);
        cardFertilizerRecommendation = findViewById(
                R.id.cardFertilizerRecommendation
        );
        btnUseFertilizerRecommendation = findViewById(
                R.id.btnUseFertilizerRecommendation
        );
        btnRecordFertilizerApplication = findViewById(
                R.id.btnRecordFertilizerApplication
        );
        btnAdvanceGrowthStage = findViewById(
                R.id.btnAdvanceGrowthStage
        );
        btnWaterAnalysis = findViewById(R.id.btnWaterAnalysis);
        btnZonePhoto = findViewById(R.id.btnZonePhoto);
        btnZonePlantAssistant = findViewById(R.id.btnZonePlantAssistant);
        txtWaterAnalysisSummary = findViewById(
                R.id.txtWaterAnalysisSummary
        );
        fertilizationScheduleView = new FertilizationScheduleView(
                findViewById(android.R.id.content));
        cardZoneAiAdvice = findViewById(R.id.cardZoneAiAdvice);
        txtZoneAiAdviceStatus = findViewById(R.id.txtZoneAiAdviceStatus);
        txtZoneAiAdviceReason = findViewById(R.id.txtZoneAiAdviceReason);
        txtZoneAiAdviceContext = findViewById(R.id.txtZoneAiAdviceContext);
        txtZoneAiAdviceProducts = findViewById(R.id.txtZoneAiAdviceProducts);
        cardZoneAiExperience = findViewById(R.id.cardZoneAiExperience);
        txtZoneAiExperience = findViewById(R.id.txtZoneAiExperience);
        txtZoneAiAdviceRisks = findViewById(R.id.txtZoneAiAdviceRisks);
        btnDiscardChanges = findViewById(R.id.btnDiscardChanges);


        String[] stageLabels = {
                getString(R.string.fertilization_not_set),
                getString(R.string.growth_stage_rooting),
                getString(R.string.growth_stage_vegetative),
                getString(R.string.growth_stage_flowering),
                getString(R.string.growth_stage_fruiting),
                getString(R.string.growth_stage_harvest),
                getString(R.string.growth_stage_season_end)
        };
        dropdownGrowthStage.setSimpleItems(stageLabels);
        dropdownGrowthStage.setOnItemClickListener(
                (parent, view, position, id) -> {
                    selectedStage = STAGE_CODES[position];
                    if (isSeasonEndStage()) {
                        rendering = true;
                        switchEnabled.setChecked(false);
                        switchReminder.setChecked(false);
                        rendering = false;
                    }
                    rebuildProductOptions();
                    updateAdvanceStageButton();
                    updateUnsavedState();
                }
        );
        dropdownProduct.setOnItemClickListener(
                (parent, view, position, id) -> {
                    if (position >= 0 && position < products.size()) {
                        int savedScrollY = scrollFertilizationZone.getScrollY();
                        selectedProductId =
                                products.get(position).getProduct_id();
                        dropdownProduct.clearFocus();
                        updateFertilizerRecommendation();
                        updateApplicationPreview();
                        updateDoseCalculation();
                        updateUnsavedState();
                        restoreScrollPosition(savedScrollY);
                    }
                }
        );

        TextWatcher calculatorWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence value,
                    int start,
                    int count,
                    int after
            ) {}

            @Override
            public void onTextChanged(
                    CharSequence value,
                    int start,
                    int before,
                    int count
            ) {}

            @Override
            public void afterTextChanged(Editable value) {
                if (!rendering) {
                    updateDoseCalculation();
                    updateUnsavedState();
                }
            }
        };
        inputFertilizationArea.addTextChangedListener(
                calculatorWatcher
        );
        inputFertilizationTank.addTextChangedListener(
                calculatorWatcher
        );
    }

    private void restoreScrollPosition(int scrollY) {
        if (scrollFertilizationZone == null) {
            return;
        }
        scrollFertilizationZone.postOnAnimation(() ->
                scrollFertilizationZone.postOnAnimation(() ->
                        scrollFertilizationZone.scrollTo(0, scrollY)
                )
        );
    }

    private void bindActions() {
        findViewById(R.id.btnBack).setOnClickListener(
                view -> requestClose()
        );
        getOnBackPressedDispatcher().addCallback(
                this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        requestClose();
                    }
                }
        );

        inputPlantingDate.setOnClickListener(
                view -> showDatePicker()
        );
        switchEnabled.setOnCheckedChangeListener(
                (button, checked) -> {
                    if (!rendering) {
                        updateUnsavedState();
                    }
                }
        );
        switchReminder.setOnCheckedChangeListener(
                (button, checked) -> {
                    if (!rendering) {
                        updateUnsavedState();
                    }
                }
        );
        btnSave.setOnClickListener(view -> save());
        btnDiscardChanges.setOnClickListener(
                view -> discardUnsavedChanges()
        );
        btnUseFertilizerRecommendation.setOnClickListener(
                view -> useRecommendedProduct()
        );
        btnRecordFertilizerApplication.setOnClickListener(
                view -> showRecordApplicationDialog()
        );
        btnAdvanceGrowthStage.setOnClickListener(
                view -> confirmAdvanceGrowthStage()
        );
        btnWaterAnalysis.setOnClickListener(view -> showWaterAnalysisDialog());
        btnZonePhoto.setOnClickListener(view -> openZonePhotoRecord());
        btnZonePlantAssistant.setOnClickListener(view -> openZonePlantAssistant());
    }

    private void renderZone(GardenZone zone) {
        if (
                zone == null
                        || (remoteLoaded && hasUnsavedChanges())
                        || saving
        ) {
            return;
        }

        renderZoneIdentity(zone);
        zonePlantType = safe(zone.getPlant_type());
        currentZone = zone;

        FertilizationProfile profile = zone.getFertilization();
        if (profile == null) {
            profile = new FertilizationProfile();
        }

        originalEnabled = profile.isEnabled();
        originalReminder = profile.isReminder_enabled();
        originalPlantingDate = safe(profile.getPlanting_date());
        originalStage = safeStage(profile.getGrowth_stage());
        originalProductId = safe(profile.getActive_product_id());
        originalLastApplicationAt =
                profile.getLast_application_at_epoch();
        originalNextApplicationAt =
                profile.getNext_application_at_epoch();
        originalAreaM2 = profile.getArea_m2();
        originalTankLiters = profile.getTank_liters();
        selectedStage = originalStage;
        selectedProductId = originalProductId;

        rendering = true;
        switchEnabled.setChecked(originalEnabled);
        switchReminder.setChecked(originalReminder);
        inputPlantingDate.setText(displayDate(originalPlantingDate));
        inputFertilizationArea.setText(
                originalAreaM2 > 0d ? displayUnits.formatEditableArea(originalAreaM2) : ""
        );
        inputFertilizationTank.setText(
                originalTankLiters > 0d ? displayUnits.formatEditableVolume(originalTankLiters) : ""
        );
        renderWaterAnalysis(profile);
        dropdownGrowthStage.setText(
                stageLabel(originalStage),
                false
        );
        rendering = false;
        remoteLoaded = true;
        rebuildProductOptions();
        updateApplicationSchedules();
        updateUnsavedState();
        updateRecordButton();
        updateAdvanceStageButton();
        renderZoneAiAdvice();
    }

    private void renderZoneIdentity(GardenZone zone) {
        zoneName = SeasonDisplayIdentity.operationalName(zone, currentSeasons);
        ((TextView) findViewById(R.id.txtTitle)).setText(
                SeasonDisplayIdentity.operationalLabel(zone, currentSeasons));
    }

    private void renderWaterAnalysis(FertilizationProfile profile) {
        if (profile.getWater_ph() <= 0.0 && profile.getWater_ec_ms() <= 0.0) {
            txtWaterAnalysisSummary.setText(
                    R.string.fertilization_water_analysis_missing);
            return;
        }
        String ph = profile.getWater_ph() > 0.0 ? "pH "
                + String.format(Locale.getDefault(), "%.1f", profile.getWater_ph())
                : getString(R.string.fertilization_water_ph_missing);
        String ec = profile.getWater_ec_ms() > 0.0 ? "EC "
                + String.format(Locale.getDefault(), "%.2f", profile.getWater_ec_ms())
                + " mS/cm" : getString(R.string.fertilization_water_ec_missing);
        txtWaterAnalysisSummary.setText(getString(
                R.string.fertilization_water_analysis_summary,
                ph,
                ec
        ));
    }

    private void showWaterAnalysisDialog() {
        FertilizationProfile profile = currentZone == null ? null
                : currentZone.getFertilization();
        TextInputEditText phInput = new TextInputEditText(this);
        phInput.setHint(R.string.runtime_ph_hint);
        phInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        TextInputEditText ecInput = new TextInputEditText(this);
        ecInput.setHint(R.string.runtime_ec_hint);
        ecInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (profile != null) {
            if (profile.getWater_ph() > 0) phInput.setText(String.valueOf(profile.getWater_ph()));
            if (profile.getWater_ec_ms() > 0) ecInput.setText(String.valueOf(profile.getWater_ec_ms()));
        }
        android.widget.LinearLayout content = new android.widget.LinearLayout(this);
        content.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, 0, padding, 0);
        content.addView(phInput);
        content.addView(ecInput);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.fertilization_water_analysis_action)
                .setMessage(R.string.runtime_soil_values_message)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.settings_save, (dialog, which) -> {
                    try {
                        double ph = parseOptionalDecimal(textOf(phInput));
                        double ec = parseOptionalDecimal(textOf(ecInput));
                        if (!Double.isFinite(ph) || !Double.isFinite(ec) || ph < 0 || ph > 14 || ec < 0 || ec > 20) throw new IllegalArgumentException();
                        viewModel.updateWaterAnalysis(zoneId, ph, ec)
                                .addOnFailureListener(error -> Toast.makeText(this,
                                        R.string.runtime_water_analysis_save_failed, Toast.LENGTH_LONG).show());
                    } catch (Exception error) {
                        Toast.makeText(this, R.string.runtime_soil_values_invalid,
                                Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    private double parseOptionalDecimal(String value) {
        String normalized = value == null ? "" : value.trim().replace(',', '.');
        return normalized.isEmpty() ? 0.0 : Double.parseDouble(normalized);
    }

    private void openZonePhotoRecord() {
        Intent intent = new Intent(this, NewJournalRecordActivity.class);
        intent.putExtra(NewJournalRecordActivity.EXTRA_ZONE_ID, zoneId);
        intent.putExtra(NewJournalRecordActivity.EXTRA_INITIAL_TYPE,
                NewJournalRecordActivity.RECORD_TYPE_PHOTO);
        startActivity(intent);
    }

    private void openZonePlantAssistant() {
        Intent intent = new Intent(this, PlantAssistantActivity.class);
        intent.putExtra("zone_id", zoneId);
        startActivity(intent);
    }

    private void renderProducts(List<FertilizerProduct> value) {
        allProducts.clear();
        if (value != null) {
            for (FertilizerProduct product : value) {
                if (product.isEnabled()) {
                    allProducts.add(product);
                }
            }
        }
        rebuildProductOptions();
        updateRecordButton();
        renderZoneAiAdvice();
    }

    private void renderZoneAiAdvice() {
        if (cardZoneAiAdvice == null || currentZone == null) {
            return;
        }
        FertilizerAdvice advice = viewModel.advise(
                currentZone, allProducts, currentWeather, currentHistory,
                Instant.now().getEpochSecond()
        );
        currentFertilizerAdvice = advice;
        updateApplicationSchedules();
        cardZoneAiAdvice.setVisibility(View.VISIBLE);
        txtZoneAiAdviceStatus.setText(localizedAdviceStatus(advice.getStatus()));
        txtZoneAiAdviceReason.setText(advice.getReason());

        if (advice.getContext() == null || advice.getContext().isBlank()) {
            txtZoneAiAdviceContext.setVisibility(View.GONE);
        } else {
            txtZoneAiAdviceContext.setVisibility(View.VISIBLE);
            txtZoneAiAdviceContext.setText(advice.getContext());
        }
        if (advice.getCandidates().isEmpty()) {
            if (viewModel.requiresOrganicAi(advice)) {
                txtZoneAiAdviceProducts.setVisibility(View.VISIBLE);
                txtZoneAiAdviceProducts.setText(
                        R.string.fertilizer_organic_ai_loading);
                requestOrganicAiAdvice();
            } else {
                txtZoneAiAdviceProducts.setVisibility(View.GONE);
            }
        } else {
            txtZoneAiAdviceProducts.setVisibility(View.VISIBLE);
            txtZoneAiAdviceProducts.setText(
                    getString(
                            R.string.runtime_two_lines,
                            getString(R.string.fertilizer_recommended_products),
                            String.join("\n\n", advice.getCandidates())));
        }
        FertilizerExperiencePresenter.bind(
                this,
                cardZoneAiExperience,
                txtZoneAiExperience,
                advice.getExperience(),
                currentZone,
                currentHistory
        );
        if (advice.getRisks().isEmpty()) {
            txtZoneAiAdviceRisks.setVisibility(View.GONE);
        } else {
            txtZoneAiAdviceRisks.setVisibility(View.VISIBLE);
            txtZoneAiAdviceRisks.setText(
                    getString(
                            R.string.runtime_heading_bullets,
                            getString(R.string.fertilization_risks_title),
                            String.join("\n• ", advice.getRisks())));
        }
    }


    private void requestOrganicAiAdvice() {
        GardenZone requestedZone = currentZone;
        viewModel.requestOrganicAdvice(requestedZone,
                new FertilizationZoneDetailViewModel.OrganicAdviceCallback() {
                    @Override
                    public void onResult(String content) {
                        if (isFinishing() || isDestroyed()
                                || currentZone != requestedZone) return;
                        txtZoneAiAdviceProducts.setText(getString(
                                R.string.runtime_two_lines,
                                getString(R.string.fertilizer_organic_ai_heading),
                                content));
                    }

                    @Override
                    public void onUnavailable() {
                        if (isFinishing() || isDestroyed()
                                || currentZone != requestedZone) return;
                        txtZoneAiAdviceProducts.setText(
                                R.string.fertilizer_organic_ai_unavailable);
                    }
                });
    }

    private String localizedAdviceStatus(String status) {
        if (status == null) return "";
        switch (status) {
            case FertilizerAdvice.STATUS_ORGANIC_REQUIRED:
                return getString(R.string.runtime_status_organic_required);
            case FertilizerAdvice.STATUS_PREPARATION_REQUIRED:
                return getString(R.string.runtime_status_preparation_required);
            case FertilizerAdvice.STATUS_TOO_EARLY:
                return getString(R.string.runtime_status_too_early);
            case FertilizerAdvice.STATUS_SEASON_COMPLETED:
                return getString(R.string.runtime_status_season_completed);
            case FertilizerAdvice.STATUS_PLAN_NOT_READY:
                return getString(R.string.runtime_status_plan_not_ready);
            case FertilizerAdvice.STATUS_PLAN_INACTIVE:
                return getString(R.string.runtime_status_plan_inactive);
            case FertilizerAdvice.STATUS_REFRESH_DATA:
                return getString(R.string.runtime_status_refresh_data);
            case FertilizerAdvice.STATUS_WATERING_FIRST:
                return getString(R.string.runtime_status_watering_first);
            case FertilizerAdvice.STATUS_TODAY_ADVICE:
                return getString(R.string.runtime_status_today_advice);
            default:
                return status;
        }
    }

    private void renderRecommendations(
            List<FertilizerRecommendation> value
    ) {
        recommendations.clear();
        if (value != null) {
            recommendations.addAll(value);
        }
        updateFertilizerRecommendation();
        updateDoseCalculation();
    }

    private void renderStageGuides(List<FertilizerStageGuide> value) {
        stageGuides.clear();
        if (value != null) {
            stageGuides.addAll(value);
        }
        updateFertilizerRecommendation();
    }

    private void rebuildProductOptions() {
        products.clear();
        if (!isSeasonEndStage()) {
            for (FertilizerProduct product : allProducts) {
                if (viewModel.isEligibleForStage(
                        product,
                        selectedStage
                )) {
                    products.add(product);
                }
            }
        }
        String[] names = new String[products.size()];
        for (int index = 0; index < products.size(); index++) {
            names[index] = products.get(index).getName();
        }
        dropdownProduct.setSimpleItems(names);
        ensureSelectedProduct();
        updateSelectedProductText();
        updateFertilizerRecommendation();
        updateApplicationPreview();
        updateDoseCalculation();
    }



    private boolean isHarvestStage() {
        return viewModel.isHarvestStage(selectedStage);
    }

    private boolean isSeasonEndStage() {
        return viewModel.isSeasonEndStage(selectedStage);
    }

    private void ensureSelectedProduct() {
        if (selectedProduct() != null) {
            return;
        }
        FertilizerRecommendation recommendation = currentRecommendation();
        if (recommendation != null) {
            for (FertilizerProduct product : products) {
                if (Objects.equals(
                        product.getProduct_id(),
                        recommendation.getProduct_id()
                )) {
                    selectedProductId = product.getProduct_id();
                    return;
                }
            }
        }
        if (!products.isEmpty()) {
            selectedProductId = products.get(0).getProduct_id();
        } else {
            selectedProductId = "";
        }
    }

    private void updateFertilizerRecommendation() {
        if (isSeasonEndStage()) {
            txtFertilizerRecommendation.setText(R.string.fertilization_season_end_no_application);
            btnUseFertilizerRecommendation.setVisibility(View.GONE);
            cardFertilizerRecommendation.setVisibility(View.VISIBLE);
            return;
        }
        FertilizerRecommendation recommendation =
                currentRecommendation();
        FertilizerProduct product = recommendation == null
                ? null
                : productById(recommendation.getProduct_id());
        FertilizerProduct selected = selectedProduct();
        FertilizerStageGuide guide = currentStageGuide();
        if ((recommendation == null || product == null)
                && guide == null
                && selected == null) {
            cardFertilizerRecommendation.setVisibility(View.GONE);
            return;
        }

        StringBuilder detail = new StringBuilder();
        if (guide != null) {
            detail.append(getString(
                    R.string.fertilization_stage_focus,
                    guide.getPrimary_focus()
            ));
            detail.append("\n").append(getString(
                    R.string.fertilization_stage_support,
                    guide.getSupport_options()
            ));
            detail.append("\n").append(getString(
                    R.string.fertilization_stage_caution,
                    guide.getCaution()
            ));
        }
        if (recommendation != null && product != null) {
            if (detail.length() > 0) {
                detail.append("\n\n");
            }
            detail.append(getString(
                    R.string.fertilization_recommendation_detail,
                    product.getName(),
                    formatDose(recommendation.getDose_min()),
                    formatDose(recommendation.getDose_max()),
                    recommendation.getDose_unit(),
                    recommendation.getInterval_days()
            ));
            detail.append("\n").append(getString(
                    R.string.fertilization_analysis_warning
            ));
        }
        if (selected != null) {
            FertilizationZoneDetailViewModel.ProductGuidance aiProfile =
                    viewModel.productGuidance(selected);
            if (detail.length() > 0) {
                detail.append("\n\n");
            }
            detail.append(getString(R.string.runtime_product_suitability, aiProfile.suitability))
                    .append("\n").append(getString(R.string.runtime_reason_label, aiProfile.reason));
            if ("FRUITING".equals(selectedStage)) {
                detail.append("\n").append(getString(
                        R.string.runtime_fruit_stage, aiProfile.fruitStageAdvice));
            } else if (isHarvestStage()) {
                detail.append("\n").append(getString(R.string.runtime_active_harvest_note));
            }
            detail.append("\n").append(getString(R.string.runtime_safety_label, aiProfile.safetyNote));

            if (isRepeatBlocked()) {
                detail.append("\n\n").append(getString(
                        R.string.runtime_application_wait_days, repeatDaysRemaining()));
            } else if (originalLastApplicationAt > 0L) {
                long elapsed = Math.max(0L,
                        java.time.temporal.ChronoUnit.DAYS.between(
                                Instant.ofEpochSecond(originalLastApplicationAt)
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDate(),
                                LocalDate.now()
                        ));
                detail.append("\n\n").append(getString(
                        R.string.runtime_last_application_days, elapsed));
            }

            if (selected.getStock_unit() != null
                    && !selected.getStock_unit().isBlank()) {
                detail.append("\n").append(getString(R.string.runtime_stock_value,
                        formatDose(selected.getStock_amount()),
                        selected.getStock_unit()));
            }
        }
        String soilSupport = soilSupportAdvice();
        if (!soilSupport.isBlank()) {
            if (detail.length() > 0) {
                detail.append("\n\n");
            }
            detail.append(getString(
                    R.string.fertilization_soil_support_title
            )).append("\n").append(soilSupport)
                    .append("\n").append(getString(
                            R.string.fertilization_soil_support_safety
                    ));
        }
        txtFertilizerRecommendation.setText(detail.toString());
        btnUseFertilizerRecommendation.setVisibility(
                recommendation == null || product == null
                        || Objects.equals(
                        selectedProductId,
                        recommendation.getProduct_id()
                ) ? View.GONE : View.VISIBLE
        );
        cardFertilizerRecommendation.setVisibility(View.VISIBLE);
    }

    private FertilizerStageGuide currentStageGuide() {
        for (FertilizerStageGuide guide : stageGuides) {
            if (Objects.equals(
                    zonePlantType,
                    guide.getPlant_type()
            ) && Objects.equals(
                    selectedStage,
                    guide.getGrowth_stage()
            )) {
                return guide;
            }
        }
        return null;
    }

    private String soilSupportAdvice() {
        if ("ROOTING".equals(selectedStage)) {
            return getString(R.string.fertilization_soil_support_rooting);
        }
        if ("VEGETATIVE".equals(selectedStage)) {
            return getString(
                    R.string.fertilization_soil_support_vegetative
            );
        }
        if ("FLOWERING".equals(selectedStage)) {
            return getString(
                    R.string.fertilization_soil_support_flowering
            );
        }
        if ("FRUITING".equals(selectedStage)) {
            return getString(
                    R.string.fertilization_soil_support_fruiting
            );
        }

        if ("HARVEST".equals(selectedStage)) {
            return getString(R.string.fertilization_soil_support_harvest);
        }
        if ("SEASON_END".equals(selectedStage)) {
            return getString(R.string.fertilization_soil_support_season_end);
        }
        return "";
    }

    private FertilizerRecommendation currentRecommendation() {
        FertilizerRecommendation first = null;
        for (FertilizerProduct product : products) {
            FertilizerRecommendation candidate =
                    recommendationForProduct(product.getProduct_id());
            if (candidate == null) {
                continue;
            }
            if (Objects.equals(
                    selectedProductId,
                    candidate.getProduct_id()
            )) {
                return candidate;
            }
            if (first == null) {
                first = candidate;
            }
        }
        return first;
    }

    private FertilizerRecommendation recommendationForProduct(
            String productId
    ) {
        for (FertilizerRecommendation recommendation
                : recommendations) {
            if (Objects.equals(
                    zonePlantType,
                    recommendation.getPlant_type()
            ) && Objects.equals(
                    selectedStage,
                    recommendation.getGrowth_stage()
            ) && Objects.equals(
                    productId,
                    recommendation.getProduct_id()
            )) {
                return recommendation;
            }
        }
        return null;
    }

    private void useRecommendedProduct() {
        FertilizerRecommendation recommendation =
                currentRecommendation();
        if (recommendation == null) {
            return;
        }
        selectedProductId = recommendation.getProduct_id();
        updateSelectedProductText();
        updateFertilizerRecommendation();
        updateApplicationPreview();
        updateDoseCalculation();
        updateUnsavedState();
    }

    private FertilizerProduct productById(String productId) {
        for (FertilizerProduct product : allProducts) {
            if (Objects.equals(productId, product.getProduct_id())) {
                return product;
            }
        }
        return null;
    }

    private String formatDose(double value) {
        if (value == Math.rint(value)) {
            return String.format(
                    Locale.getDefault(),
                    "%.0f",
                    value
            );
        }
        return String.format(Locale.getDefault(), "%.1f", value);
    }

    private String editableNumber(double value) {
        return value > 0.0 ? formatDose(value) : "";
    }

    private double currentAreaM2() {
        return displayUnits.areaToSquareMeters(
                parsePositiveNumber(inputFertilizationArea));
    }

    private double currentTankLiters() {
        return displayUnits.volumeToLiters(
                parsePositiveNumber(inputFertilizationTank));
    }

    private double parsePositiveNumber(TextInputEditText input) {
        if (input == null || input.getText() == null) {
            return 0.0;
        }
        try {
            double value = Double.parseDouble(
                    textOf(input)
                            .trim()
                            .replace(',', '.')
            );
            return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private void updateDoseCalculation() {
        if (txtFertilizationDoseCalculation == null) {
            return;
        }
        if (isSeasonEndStage()) {
            txtFertilizationDoseCalculation.setText(R.string.fertilization_season_end_no_application);
            return;
        }
        FertilizerProduct product = selectedProduct();
        if (product == null) {
            txtFertilizationDoseCalculation.setText(
                    R.string.fertilization_dose_missing_product
            );
            return;
        }

        FertilizerRecommendation recommendation =
                recommendationForProduct(product.getProduct_id());
        double doseMin;
        double doseMax;
        String doseUnit;
        if (recommendation != null) {
            doseMin = recommendation.getDose_min();
            doseMax = recommendation.getDose_max();
            doseUnit = safe(recommendation.getDose_unit());
        } else {
            doseMin = product.getLabel_dosage_min() > 0
                    ? product.getLabel_dosage_min()
                    : product.getLabel_dosage();
            doseMax = product.getLabel_dosage_max() > 0
                    ? product.getLabel_dosage_max()
                    : product.getLabel_dosage();
            doseUnit = safe(product.getDosage_unit());
        }
        if (doseMax <= 0.0) {
            doseMax = doseMin;
        }
        if (doseMin <= 0.0) {
            doseMin = doseMax;
        }

        String normalizedUnit = doseUnit
                .toLowerCase(Locale.ROOT)
                .replace(" ", "");
        double resultMin;
        double resultMax;
        String resultUnit;
        String note;
        if (normalizedUnit.contains("kg/dekar")) {
            double area = currentAreaM2();
            if (area <= 0.0) {
                txtFertilizationDoseCalculation.setText(
                        R.string.fertilization_dose_missing_area
                );
                return;
            }
            // 1 kg/dekar equals 1 gram per square metre.
            resultMin = doseMin * area;
            resultMax = doseMax * area;
            resultUnit = "g";
            note = getString(
                    R.string.fertilization_dose_area_note,
                    displayUnits.formatArea(area)
            );
        } else if (normalizedUnit.contains("l/dekar")) {
            double area = currentAreaM2();
            if (area <= 0.0) {
                txtFertilizationDoseCalculation.setText(
                        R.string.fertilization_dose_missing_area
                );
                return;
            }
            // 1 L/dekar equals 1 millilitre per square metre.
            resultMin = doseMin * area;
            resultMax = doseMax * area;
            resultUnit = "ml";
            note = getString(
                    R.string.fertilization_dose_area_note,
                    displayUnits.formatArea(area)
            );
        } else if (normalizedUnit.contains("ml/100l")) {
            double tank = currentTankLiters();
            if (tank <= 0.0) {
                txtFertilizationDoseCalculation.setText(
                        R.string.fertilization_dose_missing_tank
                );
                return;
            }
            resultMin = doseMin * tank / 100.0;
            resultMax = doseMax * tank / 100.0;
            resultUnit = "ml";
            note = getString(
                    R.string.fertilization_dose_tank_note,
                    displayUnits.formatVolume(tank)
            );
        } else {
            txtFertilizationDoseCalculation.setText(
                    R.string.fertilization_dose_unsupported
            );
            return;
        }

        if (Math.abs(resultMin - resultMax) < 0.0001) {
            txtFertilizationDoseCalculation.setText(getString(
                    R.string.fertilization_dose_single,
                    formatDose(resultMin),
                    resultUnit,
                    note
            ));
        } else {
            txtFertilizationDoseCalculation.setText(getString(
                    R.string.fertilization_dose_result,
                    formatDose(resultMin),
                    formatDose(resultMax),
                    resultUnit,
                    note
            ));
        }
        appendStockSufficiency(
                product,
                resultMin,
                resultMax,
                resultUnit
        );
    }

    private void appendStockSufficiency(
            FertilizerProduct product,
            double requiredMin,
            double requiredMax,
            String requiredUnit
    ) {
        String stockUnit = safe(product.getStock_unit());
        if (stockUnit.isBlank()) {
            return;
        }
        String stockMessage;
        if (!stockUnit.equalsIgnoreCase(requiredUnit)) {
            stockMessage = getString(
                    R.string.fertilization_stock_unit_mismatch,
                    stockUnit,
                    requiredUnit
            );
        } else if (product.getStock_amount() < requiredMin) {
            stockMessage = getString(
                    R.string.fertilization_stock_insufficient,
                    formatDose(product.getStock_amount()),
                    stockUnit,
                    formatDose(requiredMin)
            );
        } else {
            double averageDose = (requiredMin + requiredMax) / 2.0;
            int applicationCount = averageDose <= 0.0
                    ? 0
                    : (int) Math.floor(
                    product.getStock_amount() / averageDose
            );
            boolean low = product.getLow_stock_threshold() > 0.0
                    && product.getStock_amount()
                    <= product.getLow_stock_threshold();
            stockMessage = getString(
                    low
                            ? R.string
                            .fertilization_stock_low_for_zone
                            : R.string
                            .fertilization_stock_sufficient,
                    formatDose(product.getStock_amount()),
                    stockUnit,
                    applicationCount
            );
        }
        txtFertilizationDoseCalculation.append(
                "\n" + stockMessage
        );
    }

    private SuggestedApplicationDose suggestedApplicationDose(
            FertilizerProduct product,
            double areaM2,
            double tankLiters
    ) {
        if (product == null) {
            return null;
        }
        FertilizerRecommendation recommendation =
                recommendationForProduct(product.getProduct_id());
        double doseMin;
        double doseMax;
        String doseUnit;
        if (recommendation != null) {
            doseMin = recommendation.getDose_min();
            doseMax = recommendation.getDose_max();
            doseUnit = safe(recommendation.getDose_unit());
        } else {
            doseMin = product.getLabel_dosage_min() > 0.0
                    ? product.getLabel_dosage_min()
                    : product.getLabel_dosage();
            doseMax = product.getLabel_dosage_max() > 0.0
                    ? product.getLabel_dosage_max()
                    : product.getLabel_dosage();
            doseUnit = safe(product.getDosage_unit());
        }
        if (doseMax <= 0.0) {
            doseMax = doseMin;
        }
        if (doseMin <= 0.0) {
            doseMin = doseMax;
        }

        String normalizedUnit = doseUnit
                .toLowerCase(Locale.ROOT)
                .replace(" ", "");
        if (normalizedUnit.contains("kg/dekar") && areaM2 > 0.0) {
            return new SuggestedApplicationDose(
                    doseMin * areaM2,
                    doseMax * areaM2,
                    "g",
                    getString(
                            R.string.fertilization_dose_area_note,
                            displayUnits.formatArea(areaM2)
                    )
            );
        }
        if (normalizedUnit.contains("l/dekar") && areaM2 > 0.0) {
            return new SuggestedApplicationDose(
                    doseMin * areaM2,
                    doseMax * areaM2,
                    "ml",
                    getString(
                            R.string.fertilization_dose_area_note,
                            displayUnits.formatArea(areaM2)
                    )
            );
        }
        if (normalizedUnit.contains("ml/100l")
                && tankLiters > 0.0) {
            return new SuggestedApplicationDose(
                    doseMin * tankLiters / 100.0,
                    doseMax * tankLiters / 100.0,
                    "ml",
                    getString(
                            R.string.fertilization_dose_tank_note,
                            displayUnits.formatVolume(tankLiters)
                    )
            );
        }
        return null;
    }

    private void updateSelectedProductText() {
        FertilizerProduct selected = selectedProduct();
        dropdownProduct.setText(
                selected == null ? "" : selected.getName(),
                false
        );
    }

    private void showDatePicker() {
        LocalDate initial;
        try {
            initial = parseDisplayedDate(safe(
                    textOf(inputPlantingDate)
            ));
        } catch (Exception ignored) {
            initial = LocalDate.now();
        }

        new DatePickerDialog(
                this,
                (picker, year, month, day) -> {
                    inputPlantingDate.setText(
                            LocalDate.of(year, month + 1, day)
                                    .format(displayDateFormat())
                    );
                    updateApplicationPreview();
                    updateUnsavedState();
                },
                initial.getYear(),
                initial.getMonthValue() - 1,
                initial.getDayOfMonth()
        ).show();
    }

    private void save() {
        String plantingDate = currentPlantingDate();
        boolean seasonEndStage = isSeasonEndStage();
        boolean planEnabled = !seasonEndStage && switchEnabled.isChecked();
        boolean remindersEnabled = !seasonEndStage && switchReminder.isChecked();
        if (seasonEndStage) {
            selectedProductId = "";
        }
        if (planEnabled
                && (plantingDate.isBlank()
                || "NOT_SET".equals(selectedStage))) {
            saveAndExit = false;
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.fertilization_missing_title)
                    .setMessage(R.string.fertilization_missing_message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        FertilizerProduct selectedProduct = selectedProduct();
        if (planEnabled && selectedProduct == null) {
            saveAndExit = false;
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.fertilization_missing_title)
                    .setMessage(R.string.fertilization_missing_product)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        int intervalDays = selectedProduct == null
                ? 0
                : selectedProduct.getMinimum_interval_days();
        long nextApplicationEpoch = seasonEndStage
                ? 0L
                : calculateFirstApplicationEpoch(
                        plantingDate,
                        intervalDays
                );
        double areaM2 = currentAreaM2();
        double tankLiters = currentTankLiters();

        saving = true;
        setControlsEnabled(false);
        viewModel.updateProfile(
                zoneId,
                planEnabled,
                plantingDate,
                selectedStage,
                remindersEnabled,
                selectedProductId,
                intervalDays,
                nextApplicationEpoch,
                areaM2,
                tankLiters
        ).addOnSuccessListener(unused -> {
            saving = false;
            originalEnabled = planEnabled;
            originalReminder = remindersEnabled;
            if (seasonEndStage) {
                rendering = true;
                switchEnabled.setChecked(false);
                switchReminder.setChecked(false);
                rendering = false;
            }
            originalPlantingDate = plantingDate;
            originalStage = selectedStage;
            originalProductId = selectedProductId;
            originalNextApplicationAt = nextApplicationEpoch;
            originalAreaM2 = areaM2;
            originalTankLiters = tankLiters;
            updateUnsavedState();
            setControlsEnabled(true);
            Toast.makeText(
                    this,
                    R.string.fertilization_saved,
                    Toast.LENGTH_SHORT
            ).show();
            if (saveAndExit) {
                finish();
            }
        }).addOnFailureListener(error -> {
            saving = false;
            saveAndExit = false;
            setControlsEnabled(true);
            Toast.makeText(
                    this,
                    R.string.fertilization_save_failed,
                    Toast.LENGTH_LONG
            ).show();
        });
    }

    private void updateUnsavedState() {
        boolean changed = hasUnsavedChanges();
        cardUnsaved.setVisibility(changed ? View.VISIBLE : View.GONE);
        btnSave.setEnabled(changed && !saving);
        updateRecordButton();
        updatePlanStatus();
    }

    private void updateRecordButton() {
        if (btnRecordFertilizerApplication == null) {
            return;
        }
        btnRecordFertilizerApplication.setEnabled(
                remoteLoaded
                        && !isSeasonEndStage()
                        && originalEnabled
                        && !saving
                        && !hasUnsavedChanges()
                        && selectedProduct() != null
        );
        btnRecordFertilizerApplication.setText(
                R.string.fertilization_record_application
        );
    }

    private void updateApplicationSchedules() {
        fertilizationScheduleView.renderAdvice(currentFertilizerAdvice);
    }

    private void updatePlanStatus() {
        if (switchEnabled == null) {
            return;
        }
        boolean seasonEnded = isSeasonEndStage();
        fertilizationScheduleView.renderPlanStatus(
                seasonEnded,
                switchEnabled.isChecked(),
                !currentPlantingDate().isBlank()
                        && !"NOT_SET".equals(selectedStage));
    }

    private void discardUnsavedChanges() {
        if (!remoteLoaded || saving) {
            return;
        }
        rendering = true;
        switchEnabled.setChecked(originalEnabled);
        switchReminder.setChecked(originalReminder);
        inputPlantingDate.setText(displayDate(originalPlantingDate));
        inputFertilizationArea.setText(editableNumber(originalAreaM2));
        inputFertilizationTank.setText(editableNumber(originalTankLiters));
        selectedStage = originalStage;
        selectedProductId = originalProductId;
        dropdownGrowthStage.setText(stageLabel(originalStage), false);
        rendering = false;
        rebuildProductOptions();
        updateApplicationSchedules();
        updateAdvanceStageButton();
        updateUnsavedState();
        renderZoneAiAdvice();
    }

    private void updateAdvanceStageButton() {
        if (btnAdvanceGrowthStage == null) {
            return;
        }
        String next = nextStageCode(selectedStage);
        if (next == null) {
            btnAdvanceGrowthStage.setVisibility(View.GONE);
            return;
        }
        btnAdvanceGrowthStage.setText(
                getString(
                        R.string.fertilization_advance_stage_to,
                        stageLabel(next)
                )
        );
        btnAdvanceGrowthStage.setVisibility(View.VISIBLE);
    }

    private void confirmAdvanceGrowthStage() {
        String next = nextStageCode(selectedStage);
        if (next == null) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.fertilization_advance_title)
                .setMessage(
                        getString(
                                R.string.fertilization_advance_message,
                                stageLabel(selectedStage),
                                stageLabel(next)
                        )
                )
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(
                        R.string.fertilization_advance_confirm,
                        (dialog, which) -> {
                            selectedStage = next;
                            selectedProductId = "";
                            dropdownGrowthStage.setText(
                                    stageLabel(next),
                                    false
                            );
                            rebuildProductOptions();
                            updateAdvanceStageButton();
                            updateUnsavedState();
                            Toast.makeText(
                                    this,
                                    R.string.fertilization_advance_notice,
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                )
                .show();
    }

    @Nullable
    private String nextStageCode(String stage) {
        if ("ROOTING".equals(stage)) {
            return "VEGETATIVE";
        }
        if ("VEGETATIVE".equals(stage)) {
            return "FLOWERING";
        }
        if ("FLOWERING".equals(stage)) {
            return "FRUITING";
        }

        if ("FRUITING".equals(stage)) {
            return "HARVEST";
        }
        if ("HARVEST".equals(stage)) {
            return "SEASON_END";
        }
        return null;
    }

    private void showRecordApplicationDialog() {
        FertilizerProduct product = selectedProduct();
        FertilizationProfile activeProfile = currentZone == null
                ? null : currentZone.getFertilization();
        if (product == null || !originalEnabled
                || !viewModel.isEligible(product, activeProfile)) {
            Toast.makeText(this, R.string.fertilizer_application_policy_blocked, Toast.LENGTH_LONG).show();
            return;
        }
        View content = LayoutInflater.from(this).inflate(
                R.layout.dialog_fertilizer_application,
                null,
                false
        );
        TextInputEditText input = content.findViewById(
                R.id.inputAppliedDose
        );
        TextInputEditText inputNotes = content.findViewById(
                R.id.inputApplicationNotes
        );
        TextInputEditText inputApplicationDate = content.findViewById(
                R.id.inputApplicationDate
        );
        MaterialAutoCompleteTextView dropdownMethod =
                content.findViewById(
                        R.id.dropdownApplicationMethod
                );
        MaterialAutoCompleteTextView dropdownApplicationProduct =
                content.findViewById(
                        R.id.dropdownApplicationProduct
                );
        com.google.android.material.checkbox.MaterialCheckBox
                checkDeductStock = content.findViewById(
                R.id.checkDeductStock
        );
        com.google.android.material.checkbox.MaterialCheckBox
                checkSafety = content.findViewById(
                R.id.checkApplicationSafety
        );
        String[] methodLabels = {
                getString(R.string.fertilization_method_drip),
                getString(R.string.fertilization_method_soil),
                getString(R.string.fertilization_method_foliar)
        };
        dropdownMethod.setSimpleItems(methodLabels);
        dropdownMethod.setText(methodLabels[0], false);
        inputApplicationDate.setText(
                LocalDate.now().format(displayDateFormat())
        );
        inputApplicationDate.setOnClickListener(
                view -> showApplicationDatePicker(inputApplicationDate)
        );
        ((TextView) content.findViewById(
                R.id.txtApplicationProduct
        )).setText(applicationTypeLabel(applicationTypeFor(product)));
        TextView doseInformation = content.findViewById(
                R.id.txtApplicationDoseUnit
        );
        List<FertilizerProduct> selectableProducts = new ArrayList<>();
        List<String> productNames = new ArrayList<>();
        for (FertilizerProduct candidate : allProducts) {
            if (viewModel.isEligible(
                    candidate, activeProfile)) {
                selectableProducts.add(candidate);
                productNames.add(candidate.getName());
            }
        }
        final FertilizerProduct[] selectedApplicationProduct = {product};
        dropdownApplicationProduct.setSimpleItems(
                productNames.toArray(new String[0])
        );
        dropdownApplicationProduct.setText(product.getName(), false);
        SuggestedApplicationDose suggestion =
                suggestedApplicationDose(
                        product,
                        originalAreaM2,
                        originalTankLiters
                );
        String appliedUnit;
        if (suggestion == null) {
            appliedUnit = product.getDosage_unit();
            doseInformation.setText(appliedUnit);
            input.setText(formatDose(product.getLabel_dosage()));
        } else {
            appliedUnit = suggestion.unit;
            doseInformation.setText(
                    Math.abs(suggestion.min - suggestion.max) < 0.0001
                            ? getString(
                            R.string
                                    .fertilization_application_suggestion_single,
                            formatDose(suggestion.min),
                            suggestion.unit,
                            suggestion.note
                    )
                            : getString(
                            R.string.fertilization_application_suggestion,
                            formatDose(suggestion.min),
                            formatDose(suggestion.max),
                            suggestion.unit,
                            suggestion.note
                    )
            );
            input.setText(formatDose(
                    (suggestion.min + suggestion.max) / 2.0
            ));
        }
        boolean compatibleStock = product.getStock_amount() > 0.0
                && product.getStock_unit() != null
                && product.getStock_unit().equalsIgnoreCase(
                appliedUnit
        );
        if (compatibleStock) {
            checkDeductStock.setText(getString(
                    R.string.fertilization_deduct_stock,
                    formatDose(product.getStock_amount()),
                    product.getStock_unit()
            ));
            checkDeductStock.setVisibility(View.VISIBLE);
        }
        dropdownApplicationProduct.setOnItemClickListener(
                (parent, view, position, id) -> {
                    FertilizerProduct selected =
                            selectableProducts.get(position);
                    selectedApplicationProduct[0] = selected;
                    ((TextView) content.findViewById(
                            R.id.txtApplicationProduct
                    )).setText(applicationTypeLabel(
                            applicationTypeFor(selected)
                    ));
                    populateApplicationProductFields(
                            selected,
                            input,
                            doseInformation,
                            checkDeductStock
                    );
                }
        );

        androidx.appcompat.app.AlertDialog dialog =
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.fertilization_record_title)
                        .setView(content)
                        .setNegativeButton(
                                android.R.string.cancel,
                                null
                        )
                        .setPositiveButton(
                                R.string.fertilization_record_confirm,
                                null
                        )
                        .create();
        dialog.setOnShowListener(unused ->
                dialog.getButton(
                        androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE
                ).setOnClickListener(view -> {
                    FertilizerProduct productToRecord =
                            selectedApplicationProduct[0];
                    if (!viewModel.isEligible(
                            productToRecord, activeProfile)) {
                        Toast.makeText(this, R.string.fertilizer_application_policy_blocked,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    double dose;
                    try {
                        dose = Double.parseDouble(
                                safe(textOf(input))
                                        .replace(',', '.')
                        );
                    } catch (Exception ignored) {
                        dose = 0.0;
                    }
                    if (!Double.isFinite(dose) || dose <= 0.0) {
                        input.setError(
                                getString(
                                        R.string.fertilization_invalid_dose
                                )
                        );
                        return;
                    }
                    LocalDate applicationDate;
                    try {
                        applicationDate = parseDisplayedDate(safe(
                                textOf(inputApplicationDate)
                        ));
                    } catch (Exception ignored) {
                        inputApplicationDate.setError(getString(
                                R.string.fertilization_application_date_invalid
                        ));
                        return;
                    }
                    if (applicationDate.isAfter(LocalDate.now())) {
                        inputApplicationDate.setError(getString(
                                R.string.fertilization_application_date_future
                        ));
                        return;
                    }
                    if ("NUTRITION".equals(
                            applicationTypeFor(productToRecord)
                    ) && isRepeatBlocked()
                            && !applicationDate.isBefore(
                                    LocalDate.now()
                            )) {
                        LocalDate allowedDate = Instant.ofEpochSecond(
                                originalNextApplicationAt
                        ).atZone(ZoneId.systemDefault()).toLocalDate();
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(
                                        R.string
                                                .fertilization_repeat_blocked_title
                                )
                                .setMessage(
                                        getString(
                                                R.string
                                                        .fertilization_repeat_blocked_message,
                                                allowedDate.format(
                                                        displayDateFormat()
                                                )
                                        )
                                )
                                .setPositiveButton(
                                        android.R.string.ok,
                                        null
                                )
                                .show();
                        return;
                    }
                    long appliedAtEpoch = applicationDate
                            .atStartOfDay(ZoneId.systemDefault())
                            .toEpochSecond();
                    SuggestedApplicationDose activeSuggestion =
                            suggestedApplicationDose(
                                    productToRecord,
                                    originalAreaM2,
                                    originalTankLiters
                            );
                    String activeUnit = activeSuggestion == null
                            ? productToRecord.getDosage_unit()
                            : activeSuggestion.unit;
                    double activeSuggestedMin = activeSuggestion == null
                            ? (productToRecord.getLabel_dosage_min() > 0.0
                            ? productToRecord.getLabel_dosage_min()
                            : productToRecord.getLabel_dosage())
                            : activeSuggestion.min;
                    double activeSuggestedMax = activeSuggestion == null
                            ? (productToRecord.getLabel_dosage_max() > 0.0
                            ? productToRecord.getLabel_dosage_max()
                            : productToRecord.getLabel_dosage())
                            : activeSuggestion.max;
                    if (!checkSafety.isChecked()) {
                        Toast.makeText(
                                this,
                                R.string.fertilization_safety_required,
                                Toast.LENGTH_LONG
                        ).show();
                        return;
                    }
                    String applicationMethod =
                            applicationMethodCode(
                                    textOf(dropdownMethod)
                            );
                    String applicationNotes = safe(
                            textOf(inputNotes)
                    ).trim();
                    if (checkDeductStock.isChecked()
                            && dose > productToRecord.getStock_amount()) {
                        input.setError(getString(
                                R.string
                                        .fertilization_insufficient_stock
                        ));
                        return;
                    }
                    if (activeSuggestedMax > 0.0
                            && dose > activeSuggestedMax) {
                        confirmHighDose(
                                dialog,
                                productToRecord,
                                dose,
                                activeUnit,
                                activeSuggestedMin,
                                activeSuggestedMax,
                                checkDeductStock.isChecked(),
                                applicationMethod,
                                applicationNotes,
                                appliedAtEpoch
                        );
                        return;
                    }
                    recordApplication(
                            dialog,
                            productToRecord,
                            dose,
                            activeUnit,
                            activeSuggestedMin,
                            activeSuggestedMax,
                            checkDeductStock.isChecked(),
                            applicationMethod,
                            applicationNotes,
                            appliedAtEpoch
                    );
                })
        );
        dialog.show();
    }

    private boolean isRepeatBlocked() {
        return originalLastApplicationAt > 0L
                && originalNextApplicationAt
                > System.currentTimeMillis() / 1000L;
    }

    private void populateApplicationProductFields(
            FertilizerProduct product,
            TextInputEditText input,
            TextView doseInformation,
            com.google.android.material.checkbox.MaterialCheckBox
                    checkDeductStock
    ) {
        SuggestedApplicationDose suggestion =
                suggestedApplicationDose(
                        product,
                        originalAreaM2,
                        originalTankLiters
                );
        if (suggestion == null) {
            doseInformation.setText(product.getDosage_unit());
            input.setText(formatDose(product.getLabel_dosage()));
        } else {
            doseInformation.setText(Math.abs(
                    suggestion.min - suggestion.max
            ) < 0.0001
                    ? getString(
                    R.string.fertilization_application_suggestion_single,
                    formatDose(suggestion.min),
                    suggestion.unit,
                    suggestion.note
            )
                    : getString(
                    R.string.fertilization_application_suggestion,
                    formatDose(suggestion.min),
                    formatDose(suggestion.max),
                    suggestion.unit,
                    suggestion.note
            ));
            input.setText(formatDose(
                    (suggestion.min + suggestion.max) / 2.0
            ));
        }
        boolean compatibleStock = product.getStock_amount() > 0.0
                && product.getStock_unit() != null
                && product.getStock_unit().equalsIgnoreCase(
                suggestion == null
                        ? product.getDosage_unit()
                        : suggestion.unit
        );
        if (!compatibleStock) {
            checkDeductStock.setChecked(false);
            checkDeductStock.setVisibility(View.GONE);
            return;
        }
        checkDeductStock.setText(getString(
                R.string.fertilization_deduct_stock,
                formatDose(product.getStock_amount()),
                product.getStock_unit()
        ));
        checkDeductStock.setVisibility(View.VISIBLE);
    }

    private void showApplicationDatePicker(
            TextInputEditText inputApplicationDate
    ) {
        LocalDate selectedDate;
        try {
            selectedDate = parseDisplayedDate(safe(
                    textOf(inputApplicationDate)
            ));
        } catch (Exception ignored) {
            selectedDate = LocalDate.now();
        }
        new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) ->
                        inputApplicationDate.setText(
                                LocalDate.of(
                                        year,
                                        month + 1,
                                        dayOfMonth
                                ).format(displayDateFormat())
                        ),
                selectedDate.getYear(),
                selectedDate.getMonthValue() - 1,
                selectedDate.getDayOfMonth()
        ).show();
    }

    private String applicationMethodCode(String label) {
        if (getString(
                R.string.fertilization_method_foliar
        ).equals(label)) {
            return "FOLIAR";
        }
        if (getString(
                R.string.fertilization_method_soil
        ).equals(label)) {
            return "SOIL";
        }
        return "DRIP";
    }

    private long repeatDaysRemaining() {
        if (!isRepeatBlocked()) {
            return 0L;
        }
        LocalDate today = LocalDate.now();
        LocalDate allowed = Instant.ofEpochSecond(
                originalNextApplicationAt
        ).atZone(ZoneId.systemDefault()).toLocalDate();
        return Math.max(
                1L,
                java.time.temporal.ChronoUnit.DAYS.between(
                        today,
                        allowed
                )
        );
    }

    private void confirmHighDose(
            androidx.appcompat.app.AlertDialog entryDialog,
            FertilizerProduct product,
            double dose,
            String appliedUnit,
            double suggestedMin,
            double suggestedMax,
            boolean deductStock,
            String applicationMethod,
            String applicationNotes,
            long appliedAtEpoch
    ) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.fertilization_high_dose_title)
                .setMessage(
                        getString(
                                R.string.fertilization_high_dose_message,
                                formatDose(dose),
                                formatDose(suggestedMax)
                        )
                )
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(
                        R.string.fertilization_high_dose_confirm,
                        (dialog, which) -> recordApplication(
                                entryDialog,
                                product,
                                dose,
                                appliedUnit,
                                suggestedMin,
                                suggestedMax,
                                deductStock,
                                applicationMethod,
                                applicationNotes,
                                appliedAtEpoch
                        )
                )
                .show();
    }

    private void recordApplication(
            androidx.appcompat.app.AlertDialog dialog,
            FertilizerProduct product,
            double dose,
            String appliedUnit,
            double suggestedMin,
            double suggestedMax,
            boolean deductStock,
            String applicationMethod,
            String applicationNotes,
            long appliedAtEpoch
    ) {
        dialog.getButton(
                androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE
        ).setEnabled(false);
        viewModel.recordApplication(
                zoneId,
                zoneName,
                product,
                dose,
                appliedUnit,
                originalAreaM2,
                originalTankLiters,
                suggestedMin,
                suggestedMax,
                deductStock,
                applicationMethod,
                applicationNotes,
                appliedAtEpoch,
                applicationTypeFor(product)
        ).addOnSuccessListener(result -> {
            dialog.dismiss();
            Toast.makeText(
                    this,
                    R.string.fertilization_application_saved,
                    Toast.LENGTH_LONG
            ).show();
        }).addOnFailureListener(error -> {
            dialog.getButton(
                    androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE
            ).setEnabled(true);
            Toast.makeText(
                    this,
                    R.string.fertilization_application_failed,
                    Toast.LENGTH_LONG
            ).show();
        });
    }

    private boolean hasUnsavedChanges() {
        return remoteLoaded && switchEnabled != null && (
                switchEnabled.isChecked() != originalEnabled
                        || switchReminder.isChecked() != originalReminder
                        || !Objects.equals(
                                currentPlantingDate(),
                                originalPlantingDate
                        )
                        || !Objects.equals(
                                selectedStage,
                                originalStage
                        )
                        || !Objects.equals(
                                selectedProductId,
                                originalProductId
                        )
                        || Math.abs(
                                currentAreaM2() - originalAreaM2
                        ) > 0.0001
                        || Math.abs(
                                currentTankLiters()
                                        - originalTankLiters
                        ) > 0.0001
        );
    }

    private String applicationTypeFor(FertilizerProduct product) {
        String type = safe(product.getApplication_type()).trim();
        if (!type.isBlank()) {
            return type;
        }
        String value = (safe(product.getName()) + " "
                + safe(product.getNpk())).toLowerCase(Locale.ROOT);
        if (value.contains("humik") || value.contains("fulvik")
                || value.contains("leonardit")) {
            return "CONDITIONER";
        }
        if (value.contains("deniz yosunu")
                || value.contains("mikrobiyal")) {
            return "BIOSTIMULANT";
        }
        if (value.contains("organik") || value.contains("kompost")
                || value.contains("solucan")) {
            return "ORGANIC";
        }
        return "NUTRITION";
    }

    private String applicationTypeLabel(String type) {
        if ("ORGANIC".equals(type)) {
            return getString(R.string.fertilizer_type_organic);
        }
        if ("CONDITIONER".equals(type)) {
            return getString(R.string.fertilizer_type_conditioner);
        }
        if ("BIOSTIMULANT".equals(type)) {
            return getString(R.string.fertilizer_type_biostimulant);
        }
        return getString(R.string.fertilizer_type_nutrition);
    }

    private void requestClose() {
        if (!hasUnsavedChanges()) {
            finish();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.zone_unsaved_dialog_title)
                .setMessage(R.string.zone_unsaved_dialog_message)
                .setNegativeButton(
                        R.string.zone_continue_editing,
                        null
                )
                .setNeutralButton(
                        R.string.zone_discard_changes,
                        (dialog, which) -> finish()
                )
                .setPositiveButton(
                        R.string.settings_save_and_exit,
                        (dialog, which) -> {
                            saveAndExit = true;
                            save();
                        }
                )
                .show();
    }

    private void setControlsEnabled(boolean enabled) {
        inputPlantingDate.setEnabled(enabled);
        inputFertilizationArea.setEnabled(enabled);
        inputFertilizationTank.setEnabled(enabled);
        dropdownGrowthStage.setEnabled(enabled);
        dropdownProduct.setEnabled(enabled);
        switchEnabled.setEnabled(enabled);
        switchReminder.setEnabled(enabled);
        btnSave.setEnabled(enabled && hasUnsavedChanges());
        updateRecordButton();
    }

    private String currentPlantingDate() {
        String displayedDate = safe(
                textOf(inputPlantingDate)
        );
        if (displayedDate.isBlank()) {
            return "";
        }
        try {
            return parseDisplayedDate(displayedDate).toString();
        } catch (Exception ignored) {
            return displayedDate;
        }
    }

    private FertilizerProduct selectedProduct() {
        for (FertilizerProduct product : products) {
            if (Objects.equals(
                    product.getProduct_id(),
                    selectedProductId
            )) {
                return product;
            }
        }
        return null;
    }

    private long calculateFirstApplicationEpoch(
            String plantingDate,
            int intervalDays
    ) {
        if (plantingDate.isBlank() || intervalDays < 1) {
            return 0L;
        }
        try {
            return LocalDate.parse(plantingDate)
                    .plusDays(intervalDays)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toEpochSecond();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private void updateApplicationPreview() {
        if (isSeasonEndStage()) {
            txtApplicationPreview.setVisibility(View.VISIBLE);
            txtApplicationPreview.setText(R.string.fertilization_season_end_no_application);
            txtApplicationPreview.setTextColor(getColor(R.color.textSecondary));
            return;
        }
        if (originalLastApplicationAt > 0L) {
            txtApplicationPreview.setVisibility(View.GONE);
            return;
        }
        txtApplicationPreview.setVisibility(View.VISIBLE);
        FertilizerProduct product = selectedProduct();
        long epoch = calculateFirstApplicationEpoch(
                currentPlantingDate(),
                product == null
                        ? 0
                        : product.getMinimum_interval_days()
        );
        if (product == null || epoch <= 0L) {
            txtApplicationPreview.setText(
                    allProducts.isEmpty()
                            ? R.string.fertilization_product_required
                            : R.string.fertilization_no_stage_product
            );
            txtApplicationPreview.setTextColor(
                    getColor(R.color.warning)
            );
            return;
        }

        LocalDate date = java.time.Instant.ofEpochSecond(epoch)
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        boolean overdue = date.isBefore(LocalDate.now());
        txtApplicationPreview.setText(
                overdue
                        ? getString(
                                R.string.fertilization_first_application_overdue,
                                date.format(displayDateFormat())
                        )
                        : getString(
                                R.string.fertilization_first_application,
                                date.format(displayDateFormat()),
                                product.getMinimum_interval_days()
                        )
        );
        txtApplicationPreview.setTextColor(
                getColor(
                        overdue ? R.color.offline : R.color.primary
                )
        );
    }

    private String stageLabel(String code) {
        for (int index = 0; index < STAGE_CODES.length; index++) {
            if (STAGE_CODES[index].equals(code)) {
                android.widget.ListAdapter adapter =
                        dropdownGrowthStage.getAdapter();
                Object item = adapter == null
                        ? null
                        : adapter.getItem(index);
                return item == null
                        ? getString(R.string.fertilization_not_set)
                        : item.toString();
            }
        }
        return getString(R.string.fertilization_not_set);
    }

    private String safeStage(String value) {
        String candidate = safe(value);
        for (String code : STAGE_CODES) {
            if (code.equals(candidate)) {
                return code;
            }
        }
        return "NOT_SET";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String textOf(TextView view) {
        CharSequence text = view == null ? null : view.getText();
        return text == null ? "" : text.toString();
    }

    private LocalDate parseDisplayedDate(String value) {
        try {
            return LocalDate.parse(value, displayDateFormat());
        } catch (Exception ignored) {
            return LocalDate.parse(value);
        }
    }

    private String displayDate(String storageDate) {
        if (storageDate == null || storageDate.isBlank()) {
            return "";
        }
        try {
            return parseDisplayedDate(storageDate)
                    .format(displayDateFormat());
        } catch (Exception ignored) {
            return storageDate;
        }
    }
}
