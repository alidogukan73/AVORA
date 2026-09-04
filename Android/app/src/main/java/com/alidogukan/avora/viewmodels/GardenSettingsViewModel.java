package com.alidogukan.avora.viewmodels;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.alidogukan.avora.firebase.FirebaseRepository;
import com.alidogukan.avora.fertilization.FertilizerReminderScheduler;
import com.alidogukan.avora.models.DisplayUnitSettings;
import com.alidogukan.avora.models.GardenProfile;
import com.alidogukan.avora.models.RainSettings;
import com.alidogukan.avora.models.WeatherForecast;
import com.alidogukan.avora.models.WeatherLocation;
import com.alidogukan.avora.notifications.NotificationSettingsStore;
import com.alidogukan.avora.notifications.NotificationPermissionPromptStore;
import com.alidogukan.avora.notifications.NotificationSignalScheduler;
import com.alidogukan.avora.settings.GardenProfileStore;
import com.alidogukan.avora.settings.UnitPreferences;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Shared persistence boundary for garden identity, location and user preferences. */
public final class GardenSettingsViewModel extends AndroidViewModel {
    private static final String HUB_PREFS = "settings_hub_preferences";
    private static final String QUICK_ACTIONS = "quick_actions_order";

    private final FirebaseRepository repository = new FirebaseRepository();
    private final GardenProfileStore profileStore;
    private final UnitPreferences unitPreferences;
    private final NotificationSettingsStore notificationSettings;
    private final NotificationPermissionPromptStore notificationPermissionPrompts;
    private final SharedPreferences hubPreferences;
    private final LiveData<GardenProfile> cloudProfile = repository.observeGardenProfile();
    private final LiveData<WeatherLocation> weatherLocation = repository.observeWeatherLocation();
    private final LiveData<WeatherForecast> weatherForecast = repository.observeWeatherForecast();
    private final LiveData<RainSettings> rainSettings = repository.observeRainSettings();
    private final LiveData<DisplayUnitSettings> cloudUnits =
            repository.observeDisplayUnitSettings();

    public GardenSettingsViewModel(@NonNull Application application) {
        super(application);
        profileStore = new GardenProfileStore(application);
        unitPreferences = new UnitPreferences(application);
        notificationSettings = new NotificationSettingsStore(application);
        notificationPermissionPrompts = new NotificationPermissionPromptStore(application);
        hubPreferences = application.getSharedPreferences(HUB_PREFS, Context.MODE_PRIVATE);
    }

    public GardenProfile loadLocalProfile() { return profileStore.load(); }
    public LiveData<GardenProfile> getCloudProfile() { return cloudProfile; }
    public LiveData<WeatherLocation> getWeatherLocation() { return weatherLocation; }
    public LiveData<WeatherForecast> getWeatherForecast() { return weatherForecast; }
    public LiveData<RainSettings> getRainSettings() { return rainSettings; }
    public LiveData<DisplayUnitSettings> getCloudUnits() { return cloudUnits; }

    public void acceptCloudProfile(GardenProfile profile) { profileStore.save(profile); }

    public Task<Void> saveGardenProfile(GardenProfile profile) {
        profileStore.save(profile);
        return repository.saveGardenProfile(profile);
    }

    public String areaSymbol() { return unitPreferences.areaSymbol(); }
    public double areaFromSquareMeters(double value) {
        return unitPreferences.areaFromSquareMeters(value);
    }
    public double areaToSquareMeters(double value) {
        return unitPreferences.areaToSquareMeters(value);
    }

    public Task<Void> saveWeatherLocation(String city, String district,
                                          Double latitude, Double longitude,
                                          String source) {
        return repository.saveWeatherLocation(city, district, latitude, longitude, source);
    }

    public LocationPoint lastKnownLocation() {
        LocationManager manager = (LocationManager) getApplication()
                .getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) return null;
        try {
            Location location = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (location == null) {
                location = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            return location == null ? null
                    : new LocationPoint(location.getLatitude(), location.getLongitude());
        } catch (SecurityException ignored) {
            return null;
        }
    }

    public boolean isCategoryEnabled(String category) {
        return notificationSettings.isCategoryEnabled(category);
    }
    public void setCategoryEnabled(String category, boolean enabled) {
        notificationSettings.setCategoryEnabled(category, enabled);
    }
    public boolean isReminderEnabled(String reminder) {
        return notificationSettings.isReminderEnabled(reminder);
    }
    public void setReminderEnabled(String reminder, boolean enabled) {
        notificationSettings.setReminderEnabled(reminder, enabled);
    }
    public boolean isQuietHoursEnabled() { return notificationSettings.isQuietHoursEnabled(); }
    public void setQuietHoursEnabled(boolean enabled) {
        notificationSettings.setQuietHoursEnabled(enabled);
    }
    public int quietStartHour() { return notificationSettings.quietStartHour(); }
    public int quietEndHour() { return notificationSettings.quietEndHour(); }
    public void setQuietHours(int startHour, int endHour) {
        notificationSettings.setQuietHours(startHour, endHour);
    }
    public boolean applyNotificationBackup(Map<String, Object> values) {
        boolean applied = notificationSettings.applyBackup(values);
        if (applied) {
            FertilizerReminderScheduler.schedule(getApplication());
            NotificationSignalScheduler.schedule(getApplication());
        }
        return applied;
    }
    public void loadNotificationSettings(Consumer<Map<String, Object>> consumer) {
        repository.loadNotificationSettings(consumer);
    }
    public Task<Void> saveNotificationSettings() {
        return repository.saveNotificationSettings(notificationSettings.snapshot());
    }
    public Task<Void> saveCategorySettings() {
        NotificationSignalScheduler.schedule(getApplication());
        return saveNotificationSettings();
    }
    public Task<Void> saveReminderSettings() {
        FertilizerReminderScheduler.schedule(getApplication());
        NotificationSignalScheduler.schedule(getApplication());
        return saveNotificationSettings();
    }
    public Task<Void> saveRainSettings(RainSettings values, boolean weatherEnabled) {
        notificationSettings.setCategoryEnabled("weather", weatherEnabled);
        NotificationSignalScheduler.schedule(getApplication());
        return Tasks.whenAll(repository.saveRainSettings(values), saveNotificationSettings());
    }

    public boolean hasLocalUnitChoice() { return unitPreferences.hasSavedValues(); }
    public DisplayUnitSettings loadUnits() { return unitPreferences.load(); }
    public void acceptCloudUnits(DisplayUnitSettings settings) { unitPreferences.save(settings); }
    public Task<Void> saveUnits(DisplayUnitSettings settings) {
        unitPreferences.save(settings);
        return repository.saveDisplayUnitSettings(settings);
    }

    public void markPhonePermissionRequested() {
        notificationPermissionPrompts.markPrompted(System.currentTimeMillis());
    }
    public boolean wasPhonePermissionRequested() {
        return notificationPermissionPrompts.wasPrompted();
    }

    public List<String> loadQuickActionIds(List<String> defaults, List<String> allowed,
                                           int maximum) {
        String stored = hubPreferences.getString(QUICK_ACTIONS, "");
        List<String> result = new ArrayList<>();
        if (stored != null && !stored.isBlank()) {
            Set<String> allowedSet = new LinkedHashSet<>(allowed);
            for (String value : stored.split("\\|")) {
                String id = value.trim();
                if (allowedSet.contains(id) && !result.contains(id)) result.add(id);
                if (result.size() == maximum) break;
            }
        }
        for (String fallback : defaults) {
            if (result.size() == maximum) break;
            if (allowed.contains(fallback) && !result.contains(fallback)) result.add(fallback);
        }
        return result;
    }

    public void saveQuickActionIds(List<String> ids) {
        hubPreferences.edit().putString(QUICK_ACTIONS, String.join("|", ids)).apply();
    }

    public static final class LocationPoint {
        public final double latitude;
        public final double longitude;
        LocationPoint(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }
}
