package com.alidogukan.avora.activities;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;


import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.models.Statistics;
import com.alidogukan.avora.models.WateringHistory;
import com.alidogukan.avora.viewmodels.StatisticsViewModel;
import com.alidogukan.avora.zones.ZoneChipRenderer;
import com.alidogukan.avora.statistics.StatisticsCalculator;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class StatisticsActivity extends AppCompatActivity {

    private StatisticsViewModel viewModel;
    private List<WateringHistory> wateringHistory;
    private String selectedZoneId = "";
    private static final String STATE_ZONE = "statistics.selected_zone";
    private TextView txtStatisticsDataState;
    private String readError;
    private LocalDate renderedDay;
    private final Handler dateHandler = new Handler(Looper.getMainLooper());
    private final Runnable dateRefresh = new Runnable() {
        @Override public void run() {
            if (!LocalDate.now().equals(renderedDay)) renderSelectedStatistics();
            dateHandler.postDelayed(this, 60_000L);
        }
    };

    // Sulama özeti
    private TextView txtTodayWaterings;
    private TextView txtTotalWaterings;

    // Başarı oranı
    private MaterialCardView cardSuccessRate;
    private TextView txtSuccessRate;
    private TextView txtSuccessDescription;

    // Sulama sonuçları
    private TextView txtCompletedWaterings;
    private TextView txtInterruptedWaterings;

    // Süreler
    private TextView txtAverageDuration;
    private TextView txtLastWateringDuration;
    private TextView txtTotalWateringDuration;

    // Nem değişimi
    private MaterialCardView cardMoistureChange;
    private MaterialCardView cardMoistureDelta;
    private TextView txtBeforeMoisture;
    private TextView txtAfterMoisture;
    private TextView txtMoistureChange;

    // Son durum
    private TextView txtLastStopReason;
    private TextView txtStatisticsDate;


    private MaterialButton btnBack;
    private ChipGroup chipGroupStatisticZones;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_statistics);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.statisticsRoot), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        if (savedInstanceState != null) selectedZoneId = savedInstanceState.getString(STATE_ZONE, "");

        getOnBackPressedDispatcher().addCallback(
                this,
                new androidx.activity.OnBackPressedCallback(true) {

                    @Override
                    public void handleOnBackPressed() {
                        finish();
                    }
                }
        );

        initializeViews();
        initializeViewModel();
        observeViewModel();
        initializeActions();
        renderSelectedStatistics();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString(STATE_ZONE, selectedZoneId);
        super.onSaveInstanceState(state);
    }

    @Override protected void onResume() {
        super.onResume();
        renderSelectedStatistics();
        dateHandler.removeCallbacks(dateRefresh);
        dateHandler.postDelayed(dateRefresh, 60_000L);
    }

    @Override protected void onPause() {
        dateHandler.removeCallbacks(dateRefresh);
        super.onPause();
    }



    /**
     * XML ekranındaki öğeleri Java değişkenlerine bağlar.
     */
    private void initializeViews() {

        btnBack = findViewById(
                R.id.btnBack
        );
        chipGroupStatisticZones =
                findViewById(R.id.chipGroupStatisticZones);

        txtStatisticsDataState = findViewById(R.id.txtStatisticsDataState);

        // Sulama özeti
        txtTodayWaterings =
                findViewById(R.id.txtTodayWaterings);

        txtTotalWaterings =
                findViewById(R.id.txtTotalWaterings);

        // Başarı oranı
        cardSuccessRate =
                findViewById(R.id.cardSuccessRate);

        txtSuccessRate =
                findViewById(R.id.txtSuccessRate);

        txtSuccessDescription =
                findViewById(R.id.txtSuccessDescription);

        // Sulama sonuçları
        txtCompletedWaterings =
                findViewById(R.id.txtCompletedWaterings);

        txtInterruptedWaterings =
                findViewById(R.id.txtInterruptedWaterings);

        // Süre istatistikleri
        txtAverageDuration =
                findViewById(R.id.txtAverageDuration);

        txtLastWateringDuration =
                findViewById(R.id.txtLastWateringDuration);

        txtTotalWateringDuration =
                findViewById(R.id.txtTotalWateringDuration);

        // Nem değişimi
        cardMoistureChange =
                findViewById(R.id.cardMoistureChange);

        cardMoistureDelta =
                findViewById(R.id.cardMoistureDelta);

        txtBeforeMoisture =
                findViewById(R.id.txtBeforeMoisture);

        txtAfterMoisture =
                findViewById(R.id.txtAfterMoisture);

        txtMoistureChange =
                findViewById(R.id.txtMoistureChange);

        // Son durum
        txtLastStopReason =
                findViewById(R.id.txtLastStopReason);

        txtStatisticsDate =
                findViewById(R.id.txtStatisticsDate);
    }


    /**
     * StatisticsViewModel oluşturur.
     */
    private void initializeViewModel() {

        viewModel = new ViewModelProvider(this)
                .get(StatisticsViewModel.class);

    }


    /**
     * Statistics ve hata LiveData değerlerini gözlemler.
     */
    private void observeViewModel() {
        viewModel.getHistory().observe(this, history -> {
            wateringHistory = history;
            renderSelectedStatistics();
        });
        viewModel.getError().observe(this, message -> {
            readError = message;
            renderSelectedStatistics();
        });
    }


    /**
     * Firebase'den gelen bütün istatistikleri ekrana yansıtır.
     */
    private void renderStatistics(Statistics statistics) {

        if (statistics == null) {
            return;
        }

        renderWateringSummary(statistics);
        if (statistics.getTotalWaterings() == 0L) {
            renderEmptySuccessRate();
        } else {
            renderSuccessRate(statistics.getSuccessRate());
        }
        renderWateringResults(statistics);
        renderDurations(statistics);
        renderMoistureChange(statistics);
        renderLastStatus(statistics);
    }


    /**
     * Bugünkü ve toplam sulama sayılarını gösterir.
     */
    private void renderWateringSummary(Statistics statistics) {

        txtTodayWaterings.setText(
                String.valueOf(
                        statistics.getWateringsToday()
                )
        );

        txtTotalWaterings.setText(
                String.valueOf(
                        statistics.getTotalWaterings()
                )
        );
    }


    /**
     * Başarı oranını ve açıklamasını dinamik renklendirir.
     */
    private void renderSuccessRate(long successRate) {

        long safeSuccessRate = Math.max(
                0,
                Math.min(
                        100,
                        successRate
                )
        );

        txtSuccessRate.setText(
                getString(
                        R.string.percentage_format,
                        safeSuccessRate
                )
        );

        int statusColor;
        int backgroundColor;
        int descriptionResource;

        if (safeSuccessRate >= 90) {

            statusColor =
                    color(R.color.online);

            backgroundColor =
                    color(R.color.onlineBackground);

            descriptionResource =
                    R.string.statistics_success_excellent;

        } else if (safeSuccessRate >= 70) {

            statusColor =
                    color(R.color.primary);

            backgroundColor =
                    color(R.color.primaryLight);

            descriptionResource =
                    R.string.statistics_success_good;

        } else {

            statusColor =
                    color(R.color.warning);

            backgroundColor =
                    color(R.color.warningBackground);

            descriptionResource =
                    R.string.statistics_success_warning;
        }

        txtSuccessRate.setTextColor(
                statusColor
        );

        txtSuccessDescription.setText(
                descriptionResource
        );

        txtSuccessDescription.setTextColor(
                statusColor
        );

        cardSuccessRate.setCardBackgroundColor(
                backgroundColor
        );

        cardSuccessRate.setStrokeColor(
                statusColor
        );
    }


    private void renderEmptySuccessRate() {

        int neutralColor =
                color(R.color.textSecondary);

        txtSuccessRate.setText("—");
        txtSuccessRate.setTextColor(
                neutralColor
        );
        txtSuccessDescription.setText(
                R.string.statistics_waiting
        );
        txtSuccessDescription.setTextColor(
                neutralColor
        );
        cardSuccessRate.setCardBackgroundColor(
                color(R.color.surfaceSoft)
        );
        cardSuccessRate.setStrokeColor(
                color(R.color.border)
        );
    }


    /**
     * Tamamlanan ve kesintiye uğrayan işlemleri gösterir.
     */
    private void renderWateringResults(Statistics statistics) {

        txtCompletedWaterings.setText(
                String.valueOf(
                        statistics.getCompletedWaterings()
                )
        );

        txtInterruptedWaterings.setText(
                String.valueOf(
                        statistics.getInterruptedWaterings()
                )
        );
    }


    /**
     * Ortalama, son ve toplam süreleri gösterir.
     */
    private void renderDurations(Statistics statistics) {

        txtAverageDuration.setText(
                formatDuration(
                        statistics.getAverageDuration()
                )
        );

        txtLastWateringDuration.setText(
                formatDuration(
                        statistics.getLastWateringDuration()
                )
        );

        txtTotalWateringDuration.setText(
                formatDuration(
                        statistics.getTotalWateringSeconds()
                )
        );
    }


    /**
     * Nem öncesi, sonrası ve değişimini gösterir.
     */
    private void renderMoistureChange(Statistics statistics) {
        if (!statistics.hasMoistureReadings()) {
            txtBeforeMoisture.setText("—");
            txtAfterMoisture.setText("—");
            txtMoistureChange.setText("—");
            updateMoistureDeltaUi(0);
            return;
        }

        long beforeMoisture =
                statistics.getBeforeMoisture();

        long afterMoisture =
                statistics.getAfterMoisture();

        long moistureDelta =
                statistics.getMoistureDelta();

        txtBeforeMoisture.setText(
                getString(
                        R.string.percentage_format,
                        beforeMoisture
                )
        );

        txtAfterMoisture.setText(
                getString(
                        R.string.percentage_format,
                        afterMoisture
                )
        );

        txtMoistureChange.setText(
                getString(
                        R.string.signed_percentage_format,
                        moistureDelta
                )
        );

        updateMoistureDeltaUi(
                moistureDelta
        );
    }


    /**
     * Nem farkı kartını değere göre renklendirir.
     */
    private void updateMoistureDeltaUi(long moistureDelta) {

        int statusColor;
        int backgroundColor;

        if (moistureDelta > 0) {

            statusColor =
                    color(R.color.moistureIdeal);

            backgroundColor =
                    color(R.color.moistureIdealBackground);

        } else if (moistureDelta < 0) {

            statusColor =
                    color(R.color.moistureLow);

            backgroundColor =
                    color(R.color.moistureLowBackground);

        } else {

            statusColor =
                    color(R.color.textSecondary);

            backgroundColor =
                    color(R.color.surfaceSoft);
        }

        txtMoistureChange.setTextColor(
                statusColor
        );

        cardMoistureDelta.setCardBackgroundColor(
                backgroundColor
        );

        cardMoistureDelta.setStrokeColor(
                statusColor
        );

        cardMoistureChange.setStrokeColor(
                moistureDelta > 0
                        ? color(R.color.moistureIdeal)
                        : color(R.color.border)
        );
    }


    /**
     * Son durdurma nedenini ve tarihi gösterir.
     */
    private void renderLastStatus(Statistics statistics) {

        String stopReason =
                statistics.getLastStopReason();

        String statisticsDate =
                statistics.getStatisticsDate();

        if (
                stopReason == null
                        || stopReason.isBlank()
        ) {

            txtLastStopReason.setText(
                    R.string.statistics_waiting
            );

        } else {

            txtLastStopReason.setText(
                    formatStopReason(stopReason)
            );
        }

        if (
                statisticsDate == null
                        || statisticsDate.isBlank()
        ) {

            txtStatisticsDate.setText("—");

        } else {

            long timestamp = StatisticsCalculator.timestamp(statisticsDate, ZoneId.systemDefault());
            txtStatisticsDate.setText(timestamp <= 0 ? "—" : DateTimeFormatter
                    .ofPattern("dd.MM.yyyy HH:mm", Locale.getDefault())
                    .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())));
        }
    }


    /**
     * Backend durdurma nedenlerini kullanıcı dostu metne çevirir.
     */
    private String formatStopReason(String stopReason) {

        String normalizedReason =
                stopReason
                        .trim()
                        .toLowerCase(Locale.ROOT);

        switch (normalizedReason) {

            case "completed":
            case "duration_completed":
            case "watering_completed":
                return getString(R.string.history_reason_completed);

            case "manual_stop":
            case "manual":
            case "user_stopped":
                return getString(R.string.history_reason_manual_stop);

            case "moisture_reached":
            case "target_reached":
                return getString(R.string.history_reason_target_reached);

            case "manual_mode":
                return getString(R.string.statistics_reason_manual_mode);

            case "error":
                return getString(R.string.statistics_reason_error);

            case "valve_simulation":
                return getString(R.string.statistics_reason_simulation);

            case "shared_pump_busy":
                return getString(R.string.statistics_reason_pump_busy);

            case "zero_duration":
                return getString(R.string.statistics_reason_zero_duration);

            case "system_disabled":
                return getString(R.string.history_reason_system_disabled);

            case "device_offline":
                return getString(R.string.history_reason_device_offline);

            case "safety_timeout":
            case "timeout":
                return getString(R.string.history_reason_timeout);

            default:
                return stopReason
                        .replace("_", " ");
        }
    }


    /**
     * Saniye değerini okunabilir süreye dönüştürür.
     */
    private String formatDuration(long seconds) {

        long safeSeconds = Math.max(
                0,
                seconds
        );

        if (safeSeconds < 60) {

            return getString(
                    R.string.duration_seconds_format,
                    safeSeconds
            );
        }

        long hours =
                safeSeconds / 3600;

        long minutes =
                (safeSeconds % 3600) / 60;

        long remainingSeconds =
                safeSeconds % 60;

        if (hours > 0) {
            return getString(
                    R.string.duration_hours_minutes_format,
                    hours,
                    minutes
            );
        }

        return getString(
                R.string.duration_minutes_seconds_format,
                minutes,
                remainingSeconds
        );
    }


    /**
     * Renk kaynağını güvenli biçimde çözer.
     */
    private int color(int colorResource) {

        return ContextCompat.getColor(
                this,
                colorResource
        );
    }


    private void initializeActions() {
        btnBack.setOnClickListener(view -> finish());
        viewModel.getZones().observe(this, zones -> {
            selectedZoneId = StatisticsCalculator.resolveSelectedZone(selectedZoneId, zones);
            ZoneChipRenderer.render(this, chipGroupStatisticZones, zones, selectedZoneId,
                    R.string.history_zone_all, zoneId -> {
                        selectedZoneId = zoneId;
                        renderSelectedStatistics();
                    });
            renderSelectedStatistics();
        });
    }

    private void renderSelectedStatistics() {
        if (txtTodayWaterings == null) return;
        renderedDay = LocalDate.now();
        Statistics statistics = StatisticsCalculator.calculate(wateringHistory, selectedZoneId,
                System.currentTimeMillis(), ZoneId.systemDefault());
        renderStatistics(statistics);
        if (statistics.getStatisticsDate().isBlank()) txtLastWateringDuration.setText("—");
        if (wateringHistory == null) {
            txtTodayWaterings.setText("—");
            txtTotalWaterings.setText("—");
            txtCompletedWaterings.setText("—");
            txtInterruptedWaterings.setText("—");
            txtAverageDuration.setText("—");
            txtTotalWateringDuration.setText("—");
        }
        boolean failed = readError != null && !readError.isBlank();
        txtStatisticsDataState.setText(failed ? readError : getString(
                wateringHistory == null ? R.string.statistics_loading_records
                : statistics.getTotalWaterings() == 0 ? R.string.statistics_no_records
                : R.string.statistics_record_scope));
        txtStatisticsDataState.setTextColor(color(failed ? R.color.warning : R.color.textSecondary));
    }
}
