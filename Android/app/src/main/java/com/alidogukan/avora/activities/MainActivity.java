package com.alidogukan.avora.activities;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.content.Intent;
import android.provider.Settings;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.view.View;
import android.view.ViewStub;
import android.os.SystemClock;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.alidogukan.avora.R;
import com.alidogukan.avora.adapters.HomeZonePagerAdapter;
import com.alidogukan.avora.models.Status;
import com.alidogukan.avora.models.WateringHistory;
import com.alidogukan.avora.models.GardenSeason;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.FertilizationProfile;
import com.alidogukan.avora.models.ZoneIrrigationStatus;
import com.alidogukan.avora.models.WeatherForecast;
import com.alidogukan.avora.health.GardenHealthSummary;
import com.alidogukan.avora.plantassistant.PlantAssistantHomeRecommendation;
import com.alidogukan.avora.viewmodels.MainViewModel;
import com.alidogukan.avora.viewmodels.DisplayUnitsViewModel;
import com.alidogukan.avora.ui.MainMenuBottomSheet;
import com.alidogukan.avora.ui.PrimaryBottomNavigation;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.text.SimpleDateFormat;
import com.alidogukan.avora.season.SeasonDisplayIdentity;
import java.util.Date;
import java.util.Locale;
import java.util.ArrayList;

public class MainActivity extends EdgeToEdgeActivity {

    private MainViewModel viewModel;
    private DisplayUnitsViewModel displayUnits;
    private boolean notificationPermissionChecked;
    private boolean authorizationErrorShown;
    private long lastFirebaseErrorShownAt;
    private static final long AUTHENTICATION_RETRY_DELAY_MILLIS = 10_000L;
    private long connectionStartedElapsedMillis;
    private MaterialCardView cardOnlineStatus;
    private TextView txtOnline;
    private MaterialButton btnMainMenu;
    private TextView txtMainNotificationBadge;
    private final android.content.BroadcastReceiver notificationChangedReceiver =
            new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(
                        android.content.Context context,
                        android.content.Intent intent
                ) {
                    updateNotificationBadge();
                }
            };
    private MaterialCardView cardHomePlantAssistantSummary;
    private AppCompatImageView imgHomePlantAssistantSummary;
    private TextView txtHomePlantAssistantSummary;
    private MaterialCardView cardHomeWateringSummary;
    private MaterialCardView cardHomeFertilizationSummary;
    private TextView txtHomeWateringSummary;
    private MaterialCardView cardHomeHealth;
    private TextView txtHomeFertilizationSummary;
    private TextView imgHomeHealthIcon;
    private TextView txtHomeHealthTitle;
    private TextView txtHomeHealthDetail;
    private TextView txtHomeHealthScore;
    private CircularProgressIndicator progressHomeHealthScore;
    private MaterialCardView cardHomeWeather;
    private TextView txtHomeWeatherIcon;
    private TextView txtHomeWeatherTitle;
    private TextView txtHomeWeatherLocation;
    private TextView txtHomeWeatherTodayIcon;
    private TextView txtHomeWeatherTodayTemperature;
    private TextView txtHomeWeatherTodayRain;
    private TextView txtHomeWeatherTodayWind;
    private TextView txtHomeWeatherTomorrowIcon;
    private TextView txtHomeWeatherTomorrowTemperature;
    private TextView txtHomeWeatherTomorrowRain;
    private TextView txtHomeWeatherTomorrowWind;
    private TextView txtHomeWeatherImpact;
    private TextView txtHomeWeatherImpactIcon;
    private TextView txtHomeWeatherUpdated;
    private RecyclerView recyclerHomeZones;
    private LinearLayout layoutHomeZoneDots;
    private HomeZonePagerAdapter homeZonePagerAdapter;
    private boolean homeZonePagerPositioned = false;
    private List<GardenZone> latestZones;
    private List<GardenSeason> latestSeasons = new ArrayList<>();
    private List<WateringHistory> latestWateringHistory = new ArrayList<>();
    private boolean authenticatedAppInitialized;
    private WeatherForecast latestWeather;

    private static final long CONNECTION_SETTLE_MILLIS = 15_000L;
    private static final long ONLINE_CHECK_INTERVAL_MILLIS = 5_000L;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        // Android exposes the result through the permission state.
                    }
            );

    private enum ConnectionState {
        CONNECTING,
        ONLINE,
        OFFLINE
    }

    private final Handler onlineStatusHandler =
            new Handler(Looper.getMainLooper());

    private Status latestStatus;

    private final Runnable onlineStatusChecker =
            new Runnable() {

                @Override
                public void run() {

                    renderEffectiveOnlineStatus();

                    onlineStatusHandler.postDelayed(
                            this,
                            ONLINE_CHECK_INTERVAL_MILLIS
                    );
                }
            };

    private final Runnable authenticationRetry = () -> {
        if (!authenticatedAppInitialized) authenticateThenInitialize();
    };

    private void scheduleAuthenticationRetry() {
        onlineStatusHandler.removeCallbacks(authenticationRetry);
        onlineStatusHandler.postDelayed(authenticationRetry, AUTHENTICATION_RETRY_DELAY_MILLIS);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        displayUnits = new ViewModelProvider(this).get(DisplayUnitsViewModel.class);
        viewModel.getAuthenticated().observe(this, authenticated -> {
            if (Boolean.TRUE.equals(authenticated)) {
                onlineStatusHandler.removeCallbacks(authenticationRetry);
                authorizationErrorShown = false;
                initializeAuthenticatedApp();
            } else if (Boolean.FALSE.equals(authenticated)) {
                showFirebaseAuthorizationError();
            }
        });
        viewModel.getError().observe(this, message -> {
            if (message == null || message.isBlank()) return;
            long now = System.currentTimeMillis();
            if (now - lastFirebaseErrorShownAt < 15_000L) return;
            lastFirebaseErrorShownAt = now;
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            if (!authenticatedAppInitialized) scheduleAuthenticationRetry();
        });
        authenticateThenInitialize();
    }

    /**
     * Realtime Database rules require an authenticated Firebase user.  The
     * garden has a single trusted mobile app, so it obtains a persisted
     * anonymous Firebase session before attaching any database listeners.
     */
    private void authenticateThenInitialize() {

        viewModel.authenticate();
    }

    private void showFirebaseAuthorizationError() {
        if (authorizationErrorShown) return;
        String authorizationId = viewModel.getDeviceAuthorizationId();
        if (authorizationId == null || authorizationId.isBlank()) {
            Toast.makeText(this, R.string.runtime_firebase_connection_failed,
                    Toast.LENGTH_LONG).show();
            return;
        }

        authorizationErrorShown = true;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.runtime_device_authorization_title)
                .setMessage(getString(
                        R.string.runtime_device_authorization_message,
                        authorizationId))
                .setNeutralButton(R.string.runtime_nas_invite_action,
                        (dialog, which) -> startActivity(
                                new Intent(this, NasSecurityActivity.class)))
                .setNegativeButton(R.string.runtime_close, null)
                .setPositiveButton(R.string.runtime_copy_authorization_id,
                        (dialog, which) -> copyDeviceAuthorizationId(authorizationId))
                .show();
    }

    private void copyDeviceAuthorizationId(String authorizationId) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText(
                getString(R.string.runtime_device_authorization_id_label),
                authorizationId));
        Toast.makeText(this, R.string.runtime_device_authorization_id_copied,
                Toast.LENGTH_SHORT).show();
    }

    private void initializeAuthenticatedApp() {
        if (authenticatedAppInitialized) return;
        authenticatedAppInitialized = true;
        new com.alidogukan.avora.sync.DataSyncRepository(this)
                .applySavedAutomaticSync();

        connectionStartedElapsedMillis = SystemClock.elapsedRealtime();
        PrimaryBottomNavigation.bind(this, PrimaryBottomNavigation.HOME);
        initializeViews();
        initializeViewModel();
        observeViewModel();
        initializeButtons();
        viewModel.initializeNotificationSync();
        requestNotificationPermissionAtStartupIfNeeded();
    }

    private void requestNotificationPermissionAtStartupIfNeeded() {
        if (notificationPermissionChecked
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        notificationPermissionChecked = true;

        boolean granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED;
        long nowMillis = System.currentTimeMillis();
        if (!viewModel.shouldPromptForNotificationPermission(granted, nowMillis)) return;

        boolean promptedBefore = viewModel.wasNotificationPermissionPrompted();
        viewModel.markNotificationPermissionPrompted(nowMillis);
        getWindow().getDecorView().post(() -> {
            if (!promptedBefore) {
                notificationPermissionLauncher.launch(
                        Manifest.permission.POST_NOTIFICATIONS);
            } else if (shouldShowRequestPermissionRationale(
                    Manifest.permission.POST_NOTIFICATIONS)) {
                showNotificationPermissionExplanation();
            } else {
                showNotificationPermissionSettingsGuidance();
            }
        });
    }

    private void showNotificationPermissionExplanation() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.notification_startup_permission_title)
                .setMessage(R.string.notification_startup_permission_message)
                .setNegativeButton(R.string.notification_permission_later, null)
                .setPositiveButton(R.string.notification_allow, (dialog, which) ->
                        notificationPermissionLauncher.launch(
                                Manifest.permission.POST_NOTIFICATIONS))
                .show();
    }

    private void showNotificationPermissionSettingsGuidance() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.notification_startup_permission_title)
                .setMessage(R.string.notification_startup_settings_message)
                .setNegativeButton(R.string.notification_permission_later, null)
                .setPositiveButton(R.string.notification_system_settings, (dialog, which) ->
                        openNotificationSettings())
                .show();
    }

    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNotificationBadge();
        // Assistant findings are saved locally, so no Firebase event is required to refresh.
        if (viewModel != null && txtHomeHealthScore != null) {
            renderHomeHealthSummary(viewModel.getGardenZones().getValue());
            renderHomePlantAssistantRecommendation();
        }
        if (latestWeather != null && txtHomeWeatherTodayTemperature != null) {
            bindWeatherDay(txtHomeWeatherTodayIcon, txtHomeWeatherTodayTemperature,
                    txtHomeWeatherTodayRain, txtHomeWeatherTodayWind,
                    latestWeather.getTodayTemperatureMax(), latestWeather.getTodayRainProbability(),
                    latestWeather.getTodayWindMax(), latestWeather.getTodayWeatherCode());
            bindWeatherDay(txtHomeWeatherTomorrowIcon, txtHomeWeatherTomorrowTemperature,
                    txtHomeWeatherTomorrowRain, txtHomeWeatherTomorrowWind,
                    latestWeather.getTomorrowTemperatureMax(), latestWeather.getTomorrowRainProbability(),
                    latestWeather.getTomorrowWindMax(), latestWeather.getTomorrowWeatherCode());
        }
    }

    private void updateNotificationBadge() {
        if (txtMainNotificationBadge == null) return;
        int unread = viewModel == null ? 0 : viewModel.unreadNotificationCount();
        if (unread <= 0) {
            txtMainNotificationBadge.setVisibility(View.GONE);
        } else {
            txtMainNotificationBadge.setText(unread > 99
                    ? getString(R.string.runtime_badge_overflow)
                    : String.valueOf(unread));
            txtMainNotificationBadge.setVisibility(View.VISIBLE);
        }
    }
    private void initializeViews() {


        txtMainNotificationBadge = findViewById(R.id.txtMainNotificationBadge);
        updateNotificationBadge();
        btnMainMenu = findViewById(
                R.id.btnMainMenu
        );
        findViewById(R.id.btnMainNotifications).setOnClickListener(
                view -> startActivity(new Intent(this, NotificationCenterActivity.class))
        );
        cardHomePlantAssistantSummary = findViewById(
                R.id.cardHomePlantAssistantSummary
        );
        imgHomePlantAssistantSummary = findViewById(
                R.id.imgHomePlantAssistantSummary
        );
        txtHomePlantAssistantSummary = findViewById(
                R.id.txtHomePlantAssistantSummary
        );
        renderHomePlantAssistantRecommendation();
        cardHomeWateringSummary = findViewById(R.id.cardHomeWateringSummary);
        txtHomeWateringSummary = findViewById(R.id.txtHomeWateringSummary);
        cardHomeFertilizationSummary = findViewById(
                R.id.cardHomeFertilizationSummary
        );
        txtHomeFertilizationSummary = findViewById(
                R.id.txtHomeFertilizationSummary
        );
        cardHomeHealth = findViewById(R.id.cardHomeHealth);
        imgHomeHealthIcon = findViewById(R.id.imgHomeHealthIcon);
        txtHomeHealthTitle = findViewById(R.id.txtHomeHealthTitle);
        txtHomeHealthDetail = findViewById(R.id.txtHomeHealthDetail);
        txtHomeHealthScore = findViewById(R.id.txtHomeHealthScore);
        progressHomeHealthScore = findViewById(R.id.progressHomeHealthScore);
        recyclerHomeZones = findViewById(
                R.id.recyclerHomeZones
        );
        layoutHomeZoneDots = findViewById(
                R.id.layoutHomeZoneDots
        );
        initializeHomeZonePager();

        cardOnlineStatus = findViewById(R.id.cardOnlineStatus);
        txtOnline = findViewById(R.id.txtOnline);
        TextView txtDevice = findViewById(R.id.txtDevice);





        txtDevice.setText(
                R.string.default_device_name
        );
    }

    private void initializeViewModel() {
        // ViewModel is created before authentication so Firebase listeners are
        // observed only after a valid session exists.
    }

    private void observeViewModel() {


        viewModel.getStatus().observe(
                this,
                this::renderStatus
        );


        viewModel.getGardenZones().observe(
                this,
                this::renderGardenZones
        );

        viewModel.getGardenSeasons().observe(this, values -> {
            latestSeasons = values == null ? new ArrayList<>() : values;
            if (homeZonePagerAdapter != null) homeZonePagerAdapter.submitSeasons(latestSeasons);
            if (latestZones != null) renderGardenZones(latestZones);
        });


        viewModel.getWeatherForecast().observe(this, this::renderHomeWeather);
        viewModel.getWateringHistory().observe(this, values -> {
            latestWateringHistory = values == null ? new ArrayList<>() : values;
            viewModel.evaluateWateringSignals(latestWateringHistory, latestZones);
        });
    }

    private void ensureHomeWeatherViews() {
        if (cardHomeWeather != null) return;
        ViewStub stub = findViewById(R.id.stubHomeWeather);
        if (stub != null) stub.inflate();
        cardHomeWeather = findViewById(R.id.cardHomeWeather);
        txtHomeWeatherIcon = findViewById(R.id.txtHomeWeatherIcon);
        txtHomeWeatherTitle = findViewById(R.id.txtHomeWeatherTitle);
        txtHomeWeatherLocation = findViewById(R.id.txtHomeWeatherLocation);
        txtHomeWeatherTodayIcon = findViewById(R.id.txtHomeWeatherTodayIcon);
        txtHomeWeatherTodayTemperature = findViewById(R.id.txtHomeWeatherTodayTemperature);
        txtHomeWeatherTodayRain = findViewById(R.id.txtHomeWeatherTodayRain);
        txtHomeWeatherTodayWind = findViewById(R.id.txtHomeWeatherTodayWind);
        txtHomeWeatherTomorrowIcon = findViewById(R.id.txtHomeWeatherTomorrowIcon);
        txtHomeWeatherTomorrowTemperature = findViewById(R.id.txtHomeWeatherTomorrowTemperature);
        txtHomeWeatherTomorrowRain = findViewById(R.id.txtHomeWeatherTomorrowRain);
        txtHomeWeatherTomorrowWind = findViewById(R.id.txtHomeWeatherTomorrowWind);
        txtHomeWeatherImpact = findViewById(R.id.txtHomeWeatherImpact);
        txtHomeWeatherImpactIcon = findViewById(R.id.txtHomeWeatherImpactIcon);
        txtHomeWeatherUpdated = findViewById(R.id.txtHomeWeatherUpdated);
        cardHomeWeather.setOnClickListener(
                view -> startActivity(new Intent(this, WeatherForecastActivity.class))
        );
    }
    private void renderHomeWeather(WeatherForecast forecast) {
        latestWeather = forecast;
        viewModel.evaluateWeatherSignals(forecast);
        renderHomePlantAssistantRecommendation();
        if (forecast == null || forecast.getTomorrowTemperatureMax() == null) {
            if (cardHomeWeather != null) {
                cardHomeWeather.setVisibility(View.GONE);
            }
            return;
        }
        ensureHomeWeatherViews();
        cardHomeWeather.setVisibility(View.VISIBLE);
        String location = forecast.getDistrict().isBlank()
                ? forecast.getCity()
                : forecast.getDistrict() + " / " + forecast.getCity();
        txtHomeWeatherTitle.setText(R.string.home_weather_title);
        txtHomeWeatherLocation.setText(getString(
                R.string.runtime_icon_label,
                getString(R.string.symbol_middle_dot),
                location));
        txtHomeWeatherIcon.setText(getString(R.string.symbol_sun));
        txtHomeWeatherUpdated.setText(
                getString(R.string.runtime_weather_updated, new SimpleDateFormat("HH:mm", Locale.getDefault())
                        .format(new Date())
        ));
        bindWeatherDay(txtHomeWeatherTodayIcon, txtHomeWeatherTodayTemperature,
                txtHomeWeatherTodayRain, txtHomeWeatherTodayWind,
                forecast.getTodayTemperatureMax(), forecast.getTodayRainProbability(),
                forecast.getTodayWindMax(), forecast.getTodayWeatherCode());
        bindWeatherDay(txtHomeWeatherTomorrowIcon, txtHomeWeatherTomorrowTemperature,
                txtHomeWeatherTomorrowRain, txtHomeWeatherTomorrowWind,
                forecast.getTomorrowTemperatureMax(), forecast.getTomorrowRainProbability(),
                forecast.getTomorrowWindMax(), forecast.getTomorrowWeatherCode());
        WeatherImpact impact = weatherGardenImpact(forecast);
        txtHomeWeatherImpactIcon.setText(impact.icon);
        setWeatherImpact(impact.message);
    }

    /** A short advisory-only reading of weather conditions for the garden. */
    private WeatherImpact weatherGardenImpact(WeatherForecast forecast) {
        double todayMax = valueOrZero(forecast.getTodayTemperatureMax());
        double tomorrowMax = valueOrZero(forecast.getTomorrowTemperatureMax());
        double hottest = Math.max(todayMax, tomorrowMax);
        double rain = Math.max(valueOrZero(forecast.getTodayRainProbability()),
                valueOrZero(forecast.getTomorrowRainProbability()));
        double wind = Math.max(valueOrZero(forecast.getTodayWindMax()),
                valueOrZero(forecast.getTomorrowWindMax()));

        // Current weather code is an observation from the provider; it is more
        // meaningful than the daily probability when rain is already falling.
        if (isRainNow(forecast.getCurrentWeatherCode())) {
            return new WeatherImpact(getString(R.string.symbol_rain), getString(R.string.runtime_weather_raining));
        }
        if (rain >= 60D) {
            return new WeatherImpact(getString(R.string.symbol_rain), getString(R.string.runtime_weather_rain_high));
        }
        if (rain >= 30D) {
            return new WeatherImpact(getString(R.string.symbol_rain), getString(R.string.runtime_weather_rain_possible));
        }
        if (hottest >= 35D) {
            return new WeatherImpact(getString(R.string.symbol_sun), getString(R.string.runtime_weather_hot));
        }
        if (wind >= 30D) {
            return new WeatherImpact(getString(R.string.symbol_wind), getString(R.string.runtime_weather_windy));
        }
        if (hottest >= 30D && rain <= 20D) {
            return new WeatherImpact(getString(R.string.symbol_water_drop), getString(R.string.runtime_weather_hot_dry));
        }
        return new WeatherImpact(getString(R.string.symbol_plant), getString(R.string.runtime_weather_balanced));
    }

    private boolean isRainNow(Long weatherCode) {
        if (weatherCode == null) return false;
        long code = weatherCode;
        // Open-Meteo rain/drizzle/showers/thunderstorm ranges; OpenWeather is
        // normalized by the backend into the same representative codes.
        return (code >= 51 && code <= 67) || (code >= 80 && code <= 82) || code >= 95;
    }

    private void setWeatherImpact(String impact) {
        String prefix = getString(R.string.runtime_avora_comment_prefix);
        SpannableString text = new SpannableString(prefix + impact);
        text.setSpan(new StyleSpan(Typeface.BOLD), 0, prefix.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        txtHomeWeatherImpact.setText(text);
    }

    private static final class WeatherImpact {
        private final String icon;
        private final String message;

        private WeatherImpact(String icon, String message) {
            this.icon = icon;
            this.message = message;
        }
    }

    private double valueOrZero(Double value) {
        return value == null ? 0D : value;
    }

    private void bindWeatherDay(TextView icon, TextView temperatureView, TextView rainView,
                                TextView windView, Double temperature, Double rain,
                                Double wind, Long code) {
        icon.setText(weatherIcon(code));
        temperatureView.setText(temperature == null
                ? getString(R.string.placeholder_dash)
                : displayUnits.formatTemperature(temperature));
        rainView.setText(getString(R.string.format_rain_probability, rain == null ? "%—" : "%" + Math.round(rain)));
        String windValue = wind == null
                ? getString(R.string.runtime_weather_missing_value)
                : getString(R.string.runtime_weather_approximate_value, Math.round(wind));
        windView.setText(getString(R.string.runtime_weather_wind_card,
                getString(R.string.symbol_wind),
                windValue));
    }

    private String weatherIcon(Long code) {
        if (code == null) return getString(R.string.symbol_sun);
        if (code >= 95) return getString(R.string.symbol_warning);
        if (code >= 51) return getString(R.string.symbol_rain);
        if (code >= 45) return getString(R.string.symbol_weather_cloud);
        if (code >= 2) return getString(R.string.symbol_weather_cloud);
        return getString(R.string.symbol_sun);
    }

    private void renderStatus(Status status) {

        if (status == null) {
            return;
        }

        if (!viewModel.shouldAcceptStatus(status, latestStatus,
                System.currentTimeMillis() / 1000L)) {
            return;
        }

        latestStatus = status;
        if (homeZonePagerAdapter != null) {
            homeZonePagerAdapter.submitStatus(status);
        }
        renderEffectiveOnlineStatus();
    }


    private void renderGardenZones(List<GardenZone> zones) {
        List<GardenZone> activeZones = viewModel.activeZones(zones);
        latestZones = activeZones;
        viewModel.evaluateWateringSignals(latestWateringHistory, activeZones);
        viewModel.evaluateIrrigationSignals(activeZones);

        if (zones == null) {
            homeZonePagerAdapter.submitList(null);
            homeZonePagerPositioned = false;
            renderHomeZoneDots(0, 0);
            renderHomeWateringSummary(null);
            renderHomeFertilizationSummary(null);
            renderHomeHealthSummary(null);
            return;
        }

        homeZonePagerAdapter.submitList(activeZones);
        if (activeZones.isEmpty()) {
            homeZonePagerPositioned = false;
        } else if (!homeZonePagerPositioned) {
            recyclerHomeZones.scrollToPosition(
                    homeZonePagerAdapter.initialAdapterPosition()
            );
            homeZonePagerPositioned = true;
        }
        renderHomeZoneDots(
                activeZones.size(),
                homeZonePagerAdapter.toZonePosition(
                        currentHomeZonePage()
                )
        );

        renderHomeWateringSummary(activeZones);
        renderHomeFertilizationSummary(activeZones);
        renderHomeHealthSummary(zones);
        renderHomePlantAssistantRecommendation();
        renderHomeAlerts();
    }

    private void renderHomeHealthSummary(List<GardenZone> zones) {
        GardenHealthSummary health = viewModel.gardenHealth(
                zones, System.currentTimeMillis() / 1000L);
        txtHomeHealthTitle.setText(health.getTitle());
        txtHomeHealthDetail.setText(health.getDetail());
        txtHomeHealthScore.setText(String.valueOf(health.getScore()));
        int healthColor = health.getScore() >= 85 ? R.color.primary
                : health.getScore() >= 65 ? R.color.warning : R.color.moistureLow;
        txtHomeHealthScore.setTextColor(color(healthColor));
        progressHomeHealthScore.setProgressCompat(health.getScore(), true);
        progressHomeHealthScore.setIndicatorColor(color(healthColor));
        progressHomeHealthScore.setTrackColor(color(R.color.divider));
        boolean healthNeedsAttention = health.getScore() < 85;
        imgHomeHealthIcon.setText(healthNeedsAttention
                ? R.string.symbol_warning_triangle
                : R.string.symbol_heart);
        imgHomeHealthIcon.setTextColor(color(healthNeedsAttention
                ? R.color.warning
                : healthColor));
        imgHomeHealthIcon.setBackgroundResource(healthNeedsAttention
                ? R.drawable.bg_ai_icon_warning
                : R.drawable.bg_ai_icon);
        cardHomeHealth.setCardBackgroundColor(color(R.color.card));
        cardHomeHealth.setStrokeColor(color(healthNeedsAttention
                ? R.color.warning
                : R.color.border));
        cardHomeHealth.setVisibility(View.VISIBLE);
    }

    private void renderHomeWateringSummary(List<GardenZone> zones) {
        cardHomeWateringSummary.setVisibility(View.VISIBLE);
        if (zones == null || zones.isEmpty()) {
            txtHomeWateringSummary.setText(R.string.home_plan_waiting_for_zones);
            return;
        }

        GardenZone active = null;
        GardenZone queued = null;
        int enabledCount = 0;
        int evaluatedCount = 0;
        int unavailableCount = 0;
        int needsWaterCount = 0;
        int cooldownCount = 0;

        for (GardenZone zone : zones) {
            if (zone == null || !zone.isEnabled()) {
                continue;
            }
            enabledCount++;
            boolean hasCurrentReading = zone.isSensor_enabled()
                    && zone.hasSensorData()
                    && isZoneConnected(zone);
            if (!hasCurrentReading) {
                unavailableCount++;
                continue;
            }
            evaluatedCount++;

            ZoneIrrigationStatus irrigation = zone.getIrrigation_status();
            if (irrigation != null) {
                if (viewModel.isConfirmedWateringState(zone, latestStatus,
                        System.currentTimeMillis() / 1000L)) {
                    active = zone;
                }
                if (irrigation.isCooldown_active()) {
                    cooldownCount++;
                }
                if (irrigation.getQueue_position() > 0 && queued == null) {
                    queued = zone;
                }
            }
            if (zone.isIrrigation_enabled()
                    && zone.getMoisture() < zone.getMoisture_limit()) {
                needsWaterCount++;
            }
        }

        String summary;
        if (active != null) {
            summary = getString(
                    R.string.home_plan_watering_active,
                    safeZoneName(active)
            );
        } else if (queued != null) {
            summary = getString(
                    R.string.home_plan_watering_queued,
                    safeZoneName(queued)
            );
        } else if (needsWaterCount > 0) {
            summary = getResources().getQuantityString(
                    R.plurals.home_plan_watering_needed,
                    needsWaterCount,
                    needsWaterCount
            );
        } else if (cooldownCount > 0) {
            summary = getResources().getQuantityString(
                    R.plurals.home_plan_watering_cooldown,
                    cooldownCount,
                    cooldownCount
            );
        } else if (evaluatedCount > 0) {
            summary = getResources().getQuantityString(
                    R.plurals.home_plan_watering_not_needed,
                    evaluatedCount,
                    evaluatedCount
            );
        } else if (enabledCount > 0) {
            summary = getString(R.string.home_plan_no_current_sensor_data);
        } else {
            summary = getString(R.string.home_plan_no_active_zone);
        }

        if (unavailableCount > 0) {
            summary += " · " + getResources().getQuantityString(
                    R.plurals.home_plan_zone_unavailable,
                    unavailableCount,
                    unavailableCount
            );
        }
        txtHomeWateringSummary.setText(summary);
    }

    private void renderHomeFertilizationSummary(List<GardenZone> zones) {
        cardHomeFertilizationSummary.setVisibility(View.VISIBLE);
        if (zones == null || zones.isEmpty()) {
            txtHomeFertilizationSummary.setText(R.string.home_plan_waiting_for_fertilization);
            return;
        }

        long now = System.currentTimeMillis() / 1000L;
        int enabledZones = 0;
        int dueCount = 0;
        int plannedCount = 0;
        int missingPlanCount = 0;

        for (GardenZone zone : zones) {
            if (zone == null || !zone.isEnabled()) {
                continue;
            }
            enabledZones++;
            FertilizationProfile profile = zone.getFertilization();
            if (profile == null || !profile.isEnabled()
                    || profile.getNext_application_at_epoch() <= 0L) {
                missingPlanCount++;
                continue;
            }
            if (profile.getNext_application_at_epoch() <= now) {
                dueCount++;
            } else {
                plannedCount++;
            }
        }

        String summary;
        if (dueCount > 0) {
            summary = getResources().getQuantityString(
                    R.plurals.home_plan_fertilization_due,
                    dueCount,
                    dueCount
            );
        } else if (plannedCount > 0) {
            summary = getResources().getQuantityString(
                    R.plurals.home_plan_fertilization_upcoming,
                    plannedCount,
                    plannedCount
            );
        } else if (enabledZones == 0) {
            summary = getString(R.string.home_plan_no_active_zone);
        } else {
            summary = getString(R.string.home_plan_no_fertilization_due);
        }

        if (missingPlanCount > 0 && missingPlanCount < enabledZones) {
            summary += " · " + getResources().getQuantityString(
                    R.plurals.home_plan_fertilization_missing,
                    missingPlanCount,
                    missingPlanCount
            );
        }
        txtHomeFertilizationSummary.setText(summary);
    }

    private String safeZoneName(GardenZone zone) {
        String name = SeasonDisplayIdentity.operationalName(zone, latestSeasons);
        if (name.isBlank()) {
            return getString(R.string.home_plan_zone_fallback);
        }
        return name;
    }
    private void initializeHomeZonePager() {
        homeZonePagerAdapter = new HomeZonePagerAdapter(
                zone -> {
                    Intent intent = new Intent(this, PlantTimelineActivity.class);
                    intent.putExtra("zone_id", zone.getZone_id());
                    startActivity(intent);
                }
        );

        LinearLayoutManager layoutManager =
                new LinearLayoutManager(
                        this,
                        LinearLayoutManager.HORIZONTAL,
                        false
                );
        recyclerHomeZones.setLayoutManager(layoutManager);
        recyclerHomeZones.setAdapter(homeZonePagerAdapter);
        new PagerSnapHelper().attachToRecyclerView(
                recyclerHomeZones
        );
        recyclerHomeZones.addOnScrollListener(
                new RecyclerView.OnScrollListener() {
                    @Override
                    public void onScrollStateChanged(
                            @androidx.annotation.NonNull RecyclerView view,
                            int newState
                    ) {
                        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                            renderHomeZoneDots(
                                    homeZonePagerAdapter.getZoneCount(),
                                    homeZonePagerAdapter.toZonePosition(
                                            currentHomeZonePage()
                                    )
                            );
                        }
                    }
                }
        );
    }

    private int currentHomeZonePage() {
        RecyclerView.LayoutManager manager =
                recyclerHomeZones.getLayoutManager();
        if (!(manager instanceof LinearLayoutManager)) {
            return 0;
        }
        int position = (
                (LinearLayoutManager) manager
        ).findFirstCompletelyVisibleItemPosition();
        if (position < 0) {
            position = (
                    (LinearLayoutManager) manager
            ).findFirstVisibleItemPosition();
        }
        return Math.max(0, position);
    }

    private void renderHomeZoneDots(
            int count,
            int selected
    ) {
        layoutHomeZoneDots.removeAllViews();
        if (count <= 0) {
            return;
        }

        TextView page = new TextView(this);
        page.setText(getString(
                R.string.runtime_pair_slash,
                String.valueOf(Math.max(0, selected) + 1),
                String.valueOf(count)));
        page.setTextColor(color(R.color.textPrimary));
        page.setTextSize(12f);
        page.setGravity(android.view.Gravity.CENTER);
        page.setBackgroundResource(R.drawable.bg_chip_outline);

        int width = Math.round(74f * getResources().getDisplayMetrics().density);
        int height = Math.round(24f * getResources().getDisplayMetrics().density);
        page.setLayoutParams(new LinearLayout.LayoutParams(width, height));
        layoutHomeZoneDots.addView(page);
    }

    private String displayZoneName(GardenZone zone) {
        String name = SeasonDisplayIdentity.operationalName(zone, latestSeasons);
        if (name.isBlank()) {
            return getString(R.string.zone_fallback_name);
        }
        return name;
    }

    private boolean isZoneConnected(GardenZone zone) {
        if (zone == null || zone.getUpdated_at_epoch() <= 0L) {
            return false;
        }
        long age = Math.max(
                0L,
                System.currentTimeMillis() / 1000L
                        - zone.getUpdated_at_epoch()
        );
        return age <= 90L;
    }

    private void renderEffectiveOnlineStatus() {

        if (txtOnline == null || cardOnlineStatus == null) {
            return;
        }
        if (homeZonePagerAdapter != null) {
            homeZonePagerAdapter.submitStatus(latestStatus);
        }

        ConnectionState state = getConnectionState();

        updateOnlineUi(state);
        renderHomeAlerts();
    }

    private ConnectionState getConnectionState() {
        if (
                latestStatus == null
        ) {
            long connectionWaitMillis =
                    SystemClock.elapsedRealtime()
                            - connectionStartedElapsedMillis;

            return connectionWaitMillis <= CONNECTION_SETTLE_MILLIS
                    ? ConnectionState.CONNECTING
                    : ConnectionState.OFFLINE;
        }

        if (!isDeviceEffectivelyOnline()
                && SystemClock.elapsedRealtime() - connectionStartedElapsedMillis
                <= CONNECTION_SETTLE_MILLIS) {
            return ConnectionState.CONNECTING;
        }

        return isDeviceEffectivelyOnline()
                ? ConnectionState.ONLINE
                : ConnectionState.OFFLINE;
    }

    private boolean isDeviceEffectivelyOnline() {
        if (latestStatus == null) {
            return false;
        }

        return viewModel.isDeviceEffectivelyOnline(
                latestStatus, System.currentTimeMillis() / 1000L);
    }

    private void renderHomeAlerts() {
        if (txtHomeHealthTitle == null || txtHomeHealthDetail == null) {
            return;
        }

        ArrayList<String> alerts = new ArrayList<>();
        if (getConnectionState() == ConnectionState.OFFLINE) {
            alerts.add(getString(R.string.home_alert_device_offline));
        }

        if (
                latestStatus != null
                        && latestStatus.getLastError() != null
                        && !latestStatus.getLastError().trim().isEmpty()
        ) {
            alerts.add(
                    getString(
                            R.string.home_alert_system_error,
                            latestStatus.getLastError().trim()
                    )
            );
        }

        if (latestZones != null && !latestZones.isEmpty()) {
            int activeSensorCount = 0;
            int unavailableSensorCount = 0;
            for (GardenZone zone : latestZones) {
                if (zone == null || !zone.isEnabled() || !zone.isSensor_enabled()) {
                    continue;
                }
                activeSensorCount++;
                if (!isZoneConnected(zone)) {
                    unavailableSensorCount++;
                    alerts.add(getString(
                            R.string.home_alert_zone_sensor_missing,
                            displayZoneName(zone)
                    ));
                }
            }
            if (activeSensorCount > 0 && unavailableSensorCount == activeSensorCount) {
                alerts.clear();
                alerts.add(getString(R.string.home_alert_no_sensors));
            }
        }

        if (alerts.isEmpty()) {
            renderHomeHealthSummary(latestZones);
            return;
        }

        txtHomeHealthTitle.setText(
                getString(R.string.home_device_alert_count, alerts.size())
        );
        String detail = alerts.get(0);
        if (alerts.size() > 1) {
            detail += getString(
                    R.string.home_device_alert_more,
                    alerts.size() - 1
            );
        }
        txtHomeHealthDetail.setText(detail);
        imgHomeHealthIcon.setText(R.string.symbol_warning_triangle);
        imgHomeHealthIcon.setTextColor(color(R.color.warning));
        imgHomeHealthIcon.setBackgroundResource(R.drawable.bg_ai_icon_warning);
        txtHomeHealthScore.setTextColor(color(R.color.warning));
        progressHomeHealthScore.setIndicatorColor(color(R.color.warning));
        progressHomeHealthScore.setTrackColor(color(R.color.warningBackground));
        cardHomeHealth.setStrokeColor(color(R.color.warning));
        cardHomeHealth.setVisibility(View.VISIBLE);
    }

    private void updateOnlineUi(ConnectionState state) {
        boolean online = state == ConnectionState.ONLINE;
        int textResource;
        int textColor;
        int backgroundColor;

        if (online) {
            textResource = R.string.home_status_connected;
            textColor = color(R.color.online);
            backgroundColor = color(R.color.onlineBackground);
        } else if (state == ConnectionState.CONNECTING) {
            textResource = R.string.status_connecting;
            textColor = color(R.color.warning);
            backgroundColor = color(R.color.warningBackground);
        } else {
            textResource = R.string.status_offline;
            textColor = color(R.color.offline);
            backgroundColor = color(R.color.offlineBackground);
        }

        txtOnline.setText(textResource);

        txtOnline.setTextColor(
                textColor
        );

        cardOnlineStatus.setCardBackgroundColor(
                backgroundColor
        );

        cardOnlineStatus.setStrokeColor(
                textColor
        );

    }




    private void initializeButtons() {

        btnMainMenu.setOnClickListener(
                view -> showMainMenu()
        );



        cardHomeHealth.setOnClickListener(
                view -> startActivity(
                        new Intent(this, GardenHealthDetailActivity.class)
                )
        );

        cardHomeWateringSummary.setOnClickListener(
                view -> startActivity(
                        new Intent(this, AIAssistantActivity.class)
                )
        );

        cardHomePlantAssistantSummary.setOnClickListener(
                view -> startActivity(
                        new Intent(this, PlantAssistantActivity.class)
                )
        );


        cardHomeFertilizationSummary.setOnClickListener(
                view -> startActivity(
                        new Intent(
                                this,
                                FertilizationCalendarActivity.class
                        )
                )
        );






    }


    private void showMainMenu() {

        String tag =
                "MainMenuBottomSheet";

        if (
                getSupportFragmentManager()
                        .findFragmentByTag(tag)
                        != null
        ) {
            return;
        }

        MainMenuBottomSheet bottomSheet =
                new MainMenuBottomSheet();

        bottomSheet.show(
                getSupportFragmentManager(),
                tag
        );
    }
    @Override
    protected void onStart() {

        super.onStart();

        if (!authenticatedAppInitialized) {
            authenticateThenInitialize();
        }

        androidx.core.content.ContextCompat.registerReceiver(
                this,
                notificationChangedReceiver,
                new android.content.IntentFilter(
                        MainViewModel.ACTION_NOTIFICATIONS_CHANGED
                ),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        );

        renderHomePlantAssistantRecommendation();

        onlineStatusHandler.removeCallbacks(
                onlineStatusChecker
        );

        onlineStatusHandler.post(
                onlineStatusChecker
        );
    }

    private void renderHomePlantAssistantRecommendation() {
        if (txtHomePlantAssistantSummary == null) return;
        PlantAssistantHomeRecommendation.Recommendation recommendation =
                viewModel.plantRecommendation(latestZones, latestWeather,
                        System.currentTimeMillis() / 1000L);
        txtHomePlantAssistantSummary.setText(recommendation.getMessage());

        int strokeColor;
        int textColor;
        int iconBackground;
        switch (recommendation.getLevel()) {
            case WARNING:
                strokeColor = R.color.warning;
                textColor = R.color.warning;
                iconBackground = R.drawable.bg_ai_icon_warning;
                break;
            case FOLLOW_UP:
                strokeColor = R.color.info;
                textColor = R.color.info;
                iconBackground = R.drawable.bg_ai_icon_follow_up;
                break;
            case NORMAL:
            default:
                strokeColor = R.color.border;
                textColor = R.color.textSecondary;
                iconBackground = R.drawable.bg_ai_icon;
                break;
        }
        txtHomePlantAssistantSummary.setTextColor(color(textColor));
        if (cardHomePlantAssistantSummary != null) {
            cardHomePlantAssistantSummary.setStrokeColor(color(strokeColor));
        }
        if (imgHomePlantAssistantSummary != null) {
            imgHomePlantAssistantSummary.setBackgroundResource(iconBackground);
        }
    }


    @Override
    protected void onStop() {

        unregisterReceiver(notificationChangedReceiver);

        onlineStatusHandler.removeCallbacks(
                onlineStatusChecker
        );
        onlineStatusHandler.removeCallbacks(authenticationRetry);

        super.onStop();
    }
    private int color(int colorResource) {

        return ContextCompat.getColor(
                this,
                colorResource
        );
    }
}
