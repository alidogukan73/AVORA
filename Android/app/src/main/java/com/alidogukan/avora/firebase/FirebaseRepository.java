package com.alidogukan.avora.firebase;

import android.annotation.SuppressLint;
import android.util.Log;
import android.content.Context;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import com.alidogukan.avora.fertilization.FertilizerOutcomeFollowUpPolicy;
import com.alidogukan.avora.models.AdaptiveRecommendation;
import com.alidogukan.avora.models.AIDecision;
import com.alidogukan.avora.models.AIExplanation;
import com.alidogukan.avora.models.Command;
import com.alidogukan.avora.models.CropCatalogItem;
import com.alidogukan.avora.models.FertilizerApplication;
import com.alidogukan.avora.models.FertilizerProduct;
import com.alidogukan.avora.models.FertilizerRecommendation;
import com.alidogukan.avora.models.FertilizerStageGuide;
import com.alidogukan.avora.models.GardenEvent;
import com.alidogukan.avora.models.GardenAISummary;
import com.alidogukan.avora.models.GardenNotification;
import com.alidogukan.avora.models.GardenPhoto;
import com.alidogukan.avora.models.GardenSeason;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.Health;
import com.alidogukan.avora.models.DeviceNetworkStatus;
import com.alidogukan.avora.models.NetworkConfigurationRequest;
import com.alidogukan.avora.models.NetworkConfigurationResult;
import com.alidogukan.avora.models.MoisturePrediction;
import com.alidogukan.avora.models.PredictionAccuracy;
import com.alidogukan.avora.models.PredictionValidationStatus;
import com.alidogukan.avora.models.SeasonOutcome;
import com.alidogukan.avora.models.SeasonStatus;
import com.alidogukan.avora.models.ZoneSeasonState;
import com.alidogukan.avora.models.Sensor;
import com.alidogukan.avora.season.SeasonRepository;
import com.alidogukan.avora.season.SeasonScope;
import com.alidogukan.avora.season.SeasonRecordPolicy;
import com.alidogukan.avora.season.ZoneAreaIdentity;
import com.alidogukan.avora.models.SoilLearningProfile;
import com.alidogukan.avora.models.Statistics;
import com.alidogukan.avora.models.Status;
import com.alidogukan.avora.models.UnifiedConfidence;
import com.alidogukan.avora.models.WateringHistory;
import com.alidogukan.avora.models.WeatherDay;
import com.alidogukan.avora.models.WeatherForecast;
import com.alidogukan.avora.models.WeatherLocation;
import com.alidogukan.avora.models.RainSettings;
import com.alidogukan.avora.zones.ZoneCapacityPolicy;
import com.alidogukan.avora.zones.ZoneOperationSafetyPolicy;
import com.alidogukan.avora.models.IrrigationTimingSettings;
import com.alidogukan.avora.models.GardenProfile;
import com.alidogukan.avora.models.DisplayUnitSettings;
import com.alidogukan.avora.models.DeviceInfoSnapshot;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Query;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.messaging.FirebaseMessaging;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class FirebaseRepository {
   private static final String TAG = "FirebaseRepository";
   private static final String DEVICE_ID = "avora-001";
   private final DatabaseReference deviceRef = FirebaseDatabase.getInstance()
         .getReference("devices")
         .child(DEVICE_ID);
   private final DatabaseReference primaryZoneRef;
   private final DatabaseReference statusRef;
   private final DatabaseReference commandsRef;
   private final DatabaseReference historyRef;
   private final DatabaseReference healthRef;
   private final DatabaseReference statisticsRef;
   private final DatabaseReference adaptiveRecommendationRef;
   private final DatabaseReference aiDecisionRef;
   private final DatabaseReference aiExplanationRef;
   private final DatabaseReference gardenAISummaryRef;
   private final DatabaseReference predictionValidationRef;
   private final DatabaseReference moisturePredictionRef;
   private final DatabaseReference predictionAccuracyRef;
   private final DatabaseReference unifiedConfidenceRef;
   private final DatabaseReference soilLearningProfileRef;
   private final DatabaseReference zonesRef;
   private final DatabaseReference fertilizerProductsRef;
   private final DatabaseReference cropCatalogRef;
   private final DatabaseReference journalEventsRef;
   private final DatabaseReference seasonOutcomesRef;
   private final DatabaseReference journalPhotoMetadataRef;
   private final DatabaseReference notificationsRef;
   private final DatabaseReference notificationDeletionsRef;
   private final DatabaseReference notificationSettingsRef;
   private final DatabaseReference pushTokensRef;
   private final SeasonRepository seasonRepository = new SeasonRepository();

   public FirebaseRepository() {
      DatabaseReference zonesRef = this.deviceRef.child("zones");
      this.primaryZoneRef = zonesRef.child("zone-001");
      this.statusRef = this.deviceRef.child("status");
      this.commandsRef = this.deviceRef.child("commands");
      this.historyRef = this.deviceRef.child("watering_history");
      this.healthRef = this.deviceRef.child("health");
      this.statisticsRef = this.deviceRef.child("statistics");
      this.adaptiveRecommendationRef = this.deviceRef.child("adaptive_recommendation");
      this.aiDecisionRef = this.deviceRef.child("ai_decision");
      this.aiExplanationRef = this.deviceRef.child("ai_explanation");
      this.moisturePredictionRef = this.deviceRef.child("moisture_prediction");
      this.predictionAccuracyRef = this.deviceRef.child("prediction_accuracy");
      this.unifiedConfidenceRef = this.deviceRef.child("unified_confidence");
      this.soilLearningProfileRef = this.deviceRef.child("soil_learning_profile");
      this.gardenAISummaryRef = this.deviceRef.child("ai").child("garden_summary");
      this.predictionValidationRef = this.deviceRef.child("ai").child("prediction_validation");
      this.zonesRef = zonesRef;
      this.fertilizerProductsRef = this.deviceRef.child("fertilizer_products");
      this.cropCatalogRef = this.deviceRef.child("crop_catalog");
      this.journalEventsRef = this.deviceRef.child("garden_journal").child("events");
      this.seasonOutcomesRef = this.deviceRef.child("garden_journal").child("season_outcomes");
      this.journalPhotoMetadataRef = this.deviceRef.child("garden_journal").child("photo_metadata");
      this.notificationsRef = this.deviceRef.child("notifications");
      this.notificationDeletionsRef = this.deviceRef.child("notification_deletions");
      this.notificationSettingsRef = this.deviceRef.child("notification_settings");
      this.pushTokensRef = this.deviceRef.child("push_tokens");
   }

   public LiveData<Sensor> observeSensor(Consumer<DatabaseError> errorHandler) {
      return observeModel(
            this.primaryZoneRef,
            Sensor.class,
            "Primary zone sensor",
            errorHandler);
   }

   public LiveData<List<GardenZone>> observeGardenZones() {
      final FirebaseLiveData<List<GardenZone>> liveData = new FirebaseLiveData<>(zonesRef);
      liveData.setEventListener(new ValueEventListener() {
         public void onDataChange(@NonNull DataSnapshot snapshot) {
            List<GardenZone> zones = new ArrayList<>();

            for(DataSnapshot child : snapshot.getChildren()) {
               GardenZone zone = configuredZoneFromSnapshot(child);
               if (zone != null) {
                  zones.add(zone);
               }
            }

            zones.sort(Comparator.comparingInt(GardenZone::getOrder));
            liveData.setValue(zones);
         }

         public void onCancelled(@NonNull DatabaseError error) {
            Log.e("FirebaseRepository", "Garden zones read failed", error.toException());
         }
      });
      return liveData;
   }

   /** Runtime-only children can reappear after deletion; they are not a configured zone. */
   static GardenZone configuredZoneFromSnapshot(DataSnapshot snapshot) {
      if (!isConfiguredZoneSnapshot(snapshot)) return null;
      GardenZone zone = snapshot.getValue(GardenZone.class);
      if (zone != null && (zone.getZone_id() == null || zone.getZone_id().isBlank())) {
         zone.setZone_id(snapshot.getKey());
      }
      return zone;
   }

   /** Uses exactly the same configured-zone boundary as the inventory observers. */
   static GardenZone validateGardenZoneSave(
         DataSnapshot zonesSnapshot, GardenZone candidate, boolean requireAvailableSlot) {
      ZoneCapacityPolicy.validateCandidate(candidate, null);
      List<GardenZone> zones = new ArrayList<>();
      for (DataSnapshot child : zonesSnapshot.getChildren()) {
         GardenZone existing = configuredZoneFromSnapshot(child);
         if (existing != null) zones.add(existing);
      }
      GardenZone storedZone = configuredZoneFromSnapshot(
            zonesSnapshot.child(candidate.getZone_id()));
      if (requireAvailableSlot && storedZone != null
            && !ZoneCapacityPolicy.isInactive(storedZone)) {
         throw new IllegalStateException(ZoneCapacityPolicy.ERROR_ZONE_IN_USE);
      }
      ZoneCapacityPolicy.validateCandidate(candidate, zones);
      return storedZone;
   }

   private static boolean isConfiguredZoneSnapshot(DataSnapshot snapshot) {
      return snapshot != null
            && (snapshot.hasChild("enabled")
            || snapshot.hasChild("lifecycle_status")
            || snapshot.hasChild("area_id")
            || snapshot.hasChild("area_name")
            || snapshot.hasChild("name")
            || snapshot.hasChild("sensor_id")
            || snapshot.hasChild("valve_id")
            || snapshot.hasChild("created_at_epoch")
            || snapshot.hasChild("order"));
   }

   public LiveData<List<GardenPhoto>> observeGardenPhotoMetadata() {
      final FirebaseLiveData<List<GardenPhoto>> liveData =
            new FirebaseLiveData<>(journalPhotoMetadataRef);
      liveData.setEventListener(new ValueEventListener() {
         @Override
         public void onDataChange(@NonNull DataSnapshot snapshot) {
            List<GardenPhoto> photos = new ArrayList<>();
            for (DataSnapshot child : snapshot.getChildren()) {
               GardenPhoto photo = child.getValue(GardenPhoto.class);
               if (photo == null) continue;
               if (photo.getId() == null || photo.getId().isBlank()) {
                  photo.setId(child.getKey());
               }
               photos.add(photo);
            }
            photos.sort((left, right) -> Long.compare(
                  right.getCaptured_at_epoch(), left.getCaptured_at_epoch()));
            liveData.setValue(photos);
         }

         @Override
         public void onCancelled(@NonNull DatabaseError error) {
            Log.e(TAG, "Garden photo metadata read failed", error.toException());
         }
      });
      return liveData;
   }

   private <T> LiveData<T> observeModel(
         Query query,
         Class<T> modelClass,
         String logLabel,
         Consumer<DatabaseError> errorHandler
   ) {
      final FirebaseLiveData<T> liveData = new FirebaseLiveData<>(query);
      liveData.setEventListener(new ValueEventListener() {
         @Override
         public void onDataChange(@NonNull DataSnapshot snapshot) {
            liveData.setValue(snapshot.getValue(modelClass));
         }

         @Override
         public void onCancelled(@NonNull DatabaseError error) {
            Log.e(TAG, logLabel + " read failed", error.toException());
            if (errorHandler != null) errorHandler.accept(error);
         }
      });
      return liveData;
   }

   public LiveData<DeviceInfoSnapshot> observeDeviceInfoSnapshot(
         Consumer<DatabaseError> errorHandler
   ) {
      final FirebaseLiveData<DeviceInfoSnapshot> liveData =
            new FirebaseLiveData<>(this.deviceRef);
      liveData.setEventListener(new ValueEventListener() {
         @Override
         public void onDataChange(@NonNull DataSnapshot snapshot) {
            Status status = snapshot.child("status").getValue(Status.class);
            Health health = snapshot.child("health").getValue(Health.class);
            DataSnapshot network = snapshot.child("network");
            DeviceNetworkStatus networkStatus =
                  network.child("status").getValue(DeviceNetworkStatus.class);
            NetworkConfigurationResult networkResult = network.child("configuration_result")
                  .getValue(NetworkConfigurationResult.class);
            List<GardenZone> zones = new ArrayList<>();
            Set<String> firmwareVersions = new LinkedHashSet<>();
            for (DataSnapshot child : snapshot.child("zones").getChildren()) {
               GardenZone zone = configuredZoneFromSnapshot(child);
               if (zone == null) continue;
               zones.add(zone);
               String firmware = child.child("firmware").getValue(String.class);
               if (zone.isEnabled() && firmware != null && !firmware.isBlank()) {
                  firmwareVersions.add(firmware.trim());
               }
            }
            liveData.setValue(new DeviceInfoSnapshot(
                  status, health, zones, firmwareVersions,
                  networkStatus, networkResult));
         }

         @Override
         public void onCancelled(@NonNull DatabaseError error) {
            Log.e(TAG, "Device snapshot read failed", error.toException());
            if (errorHandler != null) errorHandler.accept(error);
         }
      });
      return liveData;
   }

   public Task<Boolean> authenticateAnonymously() {
      FirebaseAuth auth = FirebaseAuth.getInstance();
      FirebaseUser currentUser = auth.getCurrentUser();
      if (currentUser != null) {
         return refreshAuthenticationClaims(currentUser);
      }
      return auth.signInAnonymously().continueWithTask(task -> {
         if (!task.isSuccessful()) {
            Exception error = task.getException();
            return Tasks.forException(error != null
                  ? error
                  : new IllegalStateException("Firebase authentication failed"));
         }
         FirebaseUser signedInUser = task.getResult().getUser();
         if (signedInUser == null) {
            return Tasks.forException(
                  new IllegalStateException("Firebase user is unavailable")
            );
         }
         return refreshAuthenticationClaims(signedInUser);
      });
   }

   public String getCurrentUserId() {
      FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
      return currentUser == null ? "" : currentUser.getUid();
   }

   private Task<Boolean> refreshAuthenticationClaims(FirebaseUser user) {
      return user.getIdToken(true).continueWith(task -> {
         if (!task.isSuccessful() || task.getResult() == null) return false;
         return DeviceOwnershipPolicy.ownsDevice(
               task.getResult().getClaims(),
               DEVICE_ID
         );
      });
   }

   /**
    * Keeps the token contract consumed by the Raspberry Pi Admin SDK sender.
    * Migrating to FirebaseMessaging.register() requires a coordinated backend switch to FIDs.
    */
   @SuppressWarnings("deprecation")
   public Task<String> getPushToken() {
      return FirebaseMessaging.getInstance().getToken();
   }

   public LiveData<Boolean> observeFirebaseConnection(
         Consumer<DatabaseError> errorHandler
   ) {
      Query connection = FirebaseDatabase.getInstance()
            .getReference(".info/connected");
      return observeModel(
            connection,
            Boolean.class,
            "Firebase connection state",
            errorHandler);
   }

   public LiveData<GardenAISummary> observeGardenAISummary() {
      final FirebaseLiveData<GardenAISummary> liveData = new FirebaseLiveData<>(gardenAISummaryRef);
      liveData.setEventListener(new ValueEventListener() {
         public void onDataChange(@NonNull DataSnapshot snapshot) {
            liveData.setValue(snapshot.getValue(GardenAISummary.class));
         }

         public void onCancelled(@NonNull DatabaseError error) {
            Log.e(TAG, "Garden AI summary read failed", error.toException());
         }
      });
      return liveData;
   }

   public LiveData<GardenZone> observeGardenZone(final String zoneId) {
      DatabaseReference zoneRef = zonesRef.child(zoneId);
      final FirebaseLiveData<GardenZone> liveData = new FirebaseLiveData<>(zoneRef);
      liveData.setEventListener(new ValueEventListener() {
         public void onDataChange(@NonNull DataSnapshot snapshot) {
            GardenZone zone = (GardenZone)snapshot.getValue(GardenZone.class);
            if (zone != null) {
               zone.setZone_id(zoneId);
            }

            liveData.setValue(zone);
         }

         public void onCancelled(@NonNull DatabaseError error) {
            Log.e("FirebaseRepository", "Garden zone read failed", error.toException());
         }
      });
      return liveData;
   }

   public Task<Void> updateGardenZoneSettings(String zoneId, boolean irrigationEnabled, int moistureLimit, int pumpDuration, int cooldownSeconds, int restartDelta, boolean sensorEnabled, int sensorDryRaw, int sensorWetRaw) {
      Map<String, Object> updates = new HashMap<>();
      updates.put("irrigation_enabled", irrigationEnabled);
      updates.put("moisture_limit", moistureLimit);
      updates.put("pump_duration", pumpDuration);
      updates.put("cooldown_seconds", cooldownSeconds);
      updates.put("restart_delta", restartDelta);
      updates.put("sensor_enabled", sensorEnabled);
      updates.put("sensor_calibration_dry_raw", sensorDryRaw);
      updates.put("sensor_calibration_wet_raw", sensorWetRaw);
      updates.put("sensor_config_updated_at_epoch", ServerValue.TIMESTAMP);
      return this.zonesRef.child(zoneId).updateChildren(updates);
   }

   public Task<Void> setZoneIrrigationEnabledForCalibration(
         String zoneId,
         boolean enabled
   ) {
      if (!ZoneCapacityPolicy.isValidZoneId(zoneId)) {
         return Tasks.forException(
               new IllegalArgumentException(ZoneCapacityPolicy.ERROR_INVALID_ZONE));
      }
      Map<String, Object> updates = new HashMap<>();
      updates.put("irrigation_enabled", enabled);
      updates.put("sensor_calibration_session_updated_at_epoch", ServerValue.TIMESTAMP);
      return this.zonesRef.child(zoneId).updateChildren(updates);
   }

   public Task<Void> completeSensorCalibration(
         String zoneId,
         int dryRaw,
         int wetRaw,
         boolean restoreIrrigationEnabled
   ) {
      if (!ZoneCapacityPolicy.isValidZoneId(zoneId)) {
         return Tasks.forException(
               new IllegalArgumentException(ZoneCapacityPolicy.ERROR_INVALID_ZONE));
      }
      if (dryRaw <= wetRaw || dryRaw - wetRaw < 500) {
         return Tasks.forException(
               new IllegalArgumentException("SENSOR_CALIBRATION_INVALID"));
      }
      Map<String, Object> updates = new HashMap<>();
      updates.put("sensor_calibration_dry_raw", dryRaw);
      updates.put("sensor_calibration_wet_raw", wetRaw);
      updates.put("sensor_config_updated_at_epoch", ServerValue.TIMESTAMP);
      updates.put("sensor_calibration_updated_at_epoch", ServerValue.TIMESTAMP);
      updates.put("irrigation_enabled", restoreIrrigationEnabled);
      return this.zonesRef.child(zoneId).updateChildren(updates);
   }

   public Task<Void> saveWeatherLocation(String city, String district) {
      return this.saveWeatherLocation(city, district, (Double)null, (Double)null, "auto");
   }

   public Task<Void> saveWeatherLocation(String city, String district, Double latitude, Double longitude) {
      return this.saveWeatherLocation(city, district, latitude, longitude, "auto");
   }

   public Task<Void> saveWeatherLocation(String city, String district, Double latitude, Double longitude, String forecastSource) {
      Map<String, Object> values = new HashMap<>();
      values.put("weather/location/city", city.trim());
      values.put("weather/location/district", district.trim());
      values.put("weather/location/latitude", latitude);
      values.put("weather/location/longitude", longitude);
      values.put("weather/location/forecast_source", forecastSource == null ? "auto" : forecastSource);
      values.put("weather/location/updated_at_epoch", System.currentTimeMillis() / 1000L);
      return this.deviceRef.updateChildren(values);
   }

   public LiveData<WeatherLocation> observeWeatherLocation() {
      DatabaseReference locationRef = deviceRef.child("weather").child("location");
      final FirebaseLiveData<WeatherLocation> liveData = new FirebaseLiveData<>(locationRef);
      liveData.setEventListener(new ValueEventListener() {
         public void onDataChange(@NonNull DataSnapshot snapshot) {
            liveData.setValue(new WeatherLocation((String)snapshot.child("city").getValue(String.class), (String)snapshot.child("district").getValue(String.class), (Double)snapshot.child("latitude").getValue(Double.class), (Double)snapshot.child("longitude").getValue(Double.class), (String)snapshot.child("forecast_source").getValue(String.class)));
         }

         public void onCancelled(@NonNull DatabaseError error) {
            Log.w("FirebaseRepository", "Weather location could not be read", error.toException());
         }
      });
      return liveData;
   }

   public LiveData<WeatherForecast> observeWeatherForecast() {
      DatabaseReference forecastRef = deviceRef.child("weather").child("forecast");
      final FirebaseLiveData<WeatherForecast> liveData = new FirebaseLiveData<>(forecastRef);
      liveData.setEventListener(new ValueEventListener() {
         public void onDataChange(@NonNull DataSnapshot snapshot) {
            if (!snapshot.exists()) {
               liveData.setValue(null);
            } else {
               List<WeatherDay> days = new ArrayList<>();

               for(DataSnapshot day : snapshot.child("days").getChildren()) {
                  days.add(new WeatherDay((String)day.child("date").getValue(String.class), (Double)day.child("temperature_max").getValue(Double.class), (Double)day.child("temperature_min").getValue(Double.class), (Double)day.child("rain_probability").getValue(Double.class), (Double)day.child("rain_mm").getValue(Double.class), (Double)day.child("wind_max").getValue(Double.class)));
               }

               WeatherForecast weatherForecast = new WeatherForecast((String)snapshot.child("city").getValue(String.class), (String)snapshot.child("district").getValue(String.class), (Double)snapshot.child("today_temperature_max").getValue(Double.class), (Double)snapshot.child("today_rain_probability").getValue(Double.class), (Double)snapshot.child("today_rain_mm").getValue(Double.class), (Double)snapshot.child("today_wind_max").getValue(Double.class), (Double)snapshot.child("tomorrow_temperature_max").getValue(Double.class), (Double)snapshot.child("tomorrow_rain_probability").getValue(Double.class), (Double)snapshot.child("tomorrow_rain_mm").getValue(Double.class), (Double)snapshot.child("tomorrow_wind_max").getValue(Double.class), days, (Long)snapshot.child("today_weather_code").getValue(Long.class), (Long)snapshot.child("tomorrow_weather_code").getValue(Long.class), (Double)snapshot.child("current_temperature").getValue(Double.class), (Double)snapshot.child("current_humidity").getValue(Double.class), (Double)snapshot.child("current_wind").getValue(Double.class), (Double)snapshot.child("current_pressure").getValue(Double.class), (Long)snapshot.child("current_weather_code").getValue(Long.class));
               weatherForecast.setSource((String)snapshot.child("source").getValue(String.class));
               weatherForecast.setUpdatedAtEpoch(Math.round(
                       snapshotNumber(snapshot.child("updated_at_epoch"), 0d)));
               liveData.setValue(weatherForecast);
            }
         }

         public void onCancelled(@NonNull DatabaseError error) {
            Log.w("FirebaseRepository", "Weather forecast could not be read", error.toException());
         }
      });
      return liveData;
   }

   public Task<Void> saveRainSettings(RainSettings settings) {
      Map<String, Object> values = new HashMap<>();
      values.put("weather/irrigation_settings/rain_delay_enabled", settings.isRainDelayEnabled());
      values.put("weather/irrigation_settings/rain_probability_threshold", settings.getRainProbability());
      values.put("weather/irrigation_settings/rain_mm_threshold", settings.getRainMm());
      values.put("weather/irrigation_settings/updated_at_epoch", System.currentTimeMillis() / 1000L);
      return this.deviceRef.updateChildren(values);
   }

   public LiveData<RainSettings> observeRainSettings() {
      DatabaseReference settingsRef = deviceRef.child("weather").child("irrigation_settings");
      FirebaseLiveData<RainSettings> liveData = new FirebaseLiveData<>(settingsRef);
      liveData.setEventListener(new ValueEventListener() {
               @Override
               public void onDataChange(@NonNull DataSnapshot snapshot) {
                  if (!snapshot.exists()) {
                     liveData.setValue(RainSettings.defaults());
                     return;
                  }
                  Boolean enabled = snapshot.child("rain_delay_enabled").getValue(Boolean.class);
                  liveData.setValue(new RainSettings(
                        enabled == null || enabled,
                        snapshotNumber(snapshot.child("rain_probability_threshold"), RainSettings.DEFAULT_RAIN_PROBABILITY),
                        snapshotNumber(snapshot.child("rain_mm_threshold"), RainSettings.DEFAULT_RAIN_MM),
                        Math.round(snapshotNumber(snapshot.child("updated_at_epoch"), 0d))));
               }

               @Override
               public void onCancelled(@NonNull DatabaseError error) {
                  Log.w(TAG, "Rain settings could not be read", error.toException());
               }
            });
      return liveData;
   }

   public Task<Void> saveIrrigationTimingSettings(IrrigationTimingSettings settings) {
      Map<String, Object> values = new HashMap<>();
      values.put("weather/irrigation_settings/smart_timing_enabled", settings.isSmartTimingEnabled());
      values.put("weather/irrigation_settings/garden_environment", settings.getGardenEnvironment());
      values.put("weather/irrigation_settings/irrigation_timing_strategy", settings.getTimingStrategy());
      values.put("weather/irrigation_settings/evening_irrigation_allowed", settings.isEveningIrrigationAllowed());
      values.put("weather/irrigation_settings/max_irrigation_defer_minutes", settings.getMaxIrrigationDeferMinutes());
      values.put("weather/irrigation_settings/critical_moisture_deficit", settings.getCriticalMoistureDeficit());
      values.put("weather/irrigation_settings/timing_recheck_enabled", settings.isTimingRecheckEnabled());
      values.put("weather/irrigation_settings/preferred_start_hour", settings.getPreferredStartHour());
      values.put("weather/irrigation_settings/preferred_end_hour", settings.getPreferredEndHour());
      values.put("weather/irrigation_settings/updated_at_epoch", System.currentTimeMillis() / 1000L);
      return deviceRef.updateChildren(values);
   }

   /**
    * Atomically saves global and timing preferences after synchronizing every active zone.
    * A failed write can no longer leave only half of the irrigation settings updated.
    */
   public Task<Void> saveIrrigationSettingsAndSyncZones(
         long moistureLimit,
         long pumpDuration,
         long cooldownSeconds,
         long restartDelta,
         boolean enabled,
         boolean autoMode,
         IrrigationTimingSettings settings) {
      return this.zonesRef.get().continueWithTask(task -> {
         if (!task.isSuccessful() || task.getResult() == null) {
            Exception error = task.getException();
            return Tasks.forException(error == null
                  ? new IllegalStateException("Bölgeler okunamadı.") : error);
         }

         Map<String, Object> updates = new HashMap<>();
         updates.put("commands/moisture_limit", moistureLimit);
         updates.put("commands/pump_duration", pumpDuration);
         updates.put("commands/cooldown_seconds", cooldownSeconds);
         updates.put("commands/restart_delta", restartDelta);
         updates.put("commands/enabled", enabled);
         updates.put("commands/auto_mode", autoMode);

         for (DataSnapshot zoneSnapshot : task.getResult().getChildren()) {
            String zoneId = zoneSnapshot.getKey();
            if (zoneId == null || zoneId.isBlank()) continue;
            String path = "zones/" + zoneId + "/";
            updates.put(path + "moisture_limit", moistureLimit);
            updates.put(path + "pump_duration", pumpDuration);
            updates.put(path + "cooldown_seconds", cooldownSeconds);
            updates.put(path + "restart_delta", restartDelta);
         }

         String timingPath = "weather/irrigation_settings/";
         updates.put(timingPath + "smart_timing_enabled", settings.isSmartTimingEnabled());
         updates.put(timingPath + "garden_environment", settings.getGardenEnvironment());
         updates.put(timingPath + "irrigation_timing_strategy", settings.getTimingStrategy());
         updates.put(timingPath + "evening_irrigation_allowed",
               settings.isEveningIrrigationAllowed());
         updates.put(timingPath + "max_irrigation_defer_minutes",
               settings.getMaxIrrigationDeferMinutes());
         updates.put(timingPath + "critical_moisture_deficit",
               settings.getCriticalMoistureDeficit());
         updates.put(timingPath + "timing_recheck_enabled",
               settings.isTimingRecheckEnabled());
         updates.put(timingPath + "preferred_start_hour", settings.getPreferredStartHour());
         updates.put(timingPath + "preferred_end_hour", settings.getPreferredEndHour());
         updates.put(timingPath + "updated_at_epoch", System.currentTimeMillis() / 1000L);
         return this.deviceRef.updateChildren(updates);
      });
   }

   public LiveData<IrrigationTimingSettings> observeIrrigationTimingSettings() {
      DatabaseReference settingsRef = deviceRef.child("weather").child("irrigation_settings");
      FirebaseLiveData<IrrigationTimingSettings> liveData = new FirebaseLiveData<>(settingsRef);
      liveData.setEventListener(new ValueEventListener() {
               @Override
               public void onDataChange(@NonNull DataSnapshot snapshot) {
                  IrrigationTimingSettings value = IrrigationTimingSettings.defaults();
                  Boolean smartEnabled = snapshot.child("smart_timing_enabled").getValue(Boolean.class);
                  Boolean eveningAllowed = snapshot.child("evening_irrigation_allowed").getValue(Boolean.class);
                  Boolean recheckEnabled = snapshot.child("timing_recheck_enabled").getValue(Boolean.class);
                  String environment = snapshot.child("garden_environment").getValue(String.class);
                  String strategy = snapshot.child("irrigation_timing_strategy").getValue(String.class);

                  value.setSmartTimingEnabled(smartEnabled == null
                        ? IrrigationTimingSettings.DEFAULT_SMART_TIMING_ENABLED : smartEnabled);
                  value.setGardenEnvironment(environment);
                  value.setTimingStrategy(strategy);
                  value.setEveningIrrigationAllowed(eveningAllowed == null
                        ? IrrigationTimingSettings.DEFAULT_EVENING_ALLOWED : eveningAllowed);
                  value.setMaxIrrigationDeferMinutes((int) Math.round(snapshotNumber(
                        snapshot.child("max_irrigation_defer_minutes"),
                        IrrigationTimingSettings.DEFAULT_MAX_DEFER_MINUTES)));
                  value.setCriticalMoistureDeficit((int) Math.round(snapshotNumber(
                        snapshot.child("critical_moisture_deficit"),
                        IrrigationTimingSettings.DEFAULT_CRITICAL_DEFICIT)));
                  value.setTimingRecheckEnabled(recheckEnabled == null
                        ? IrrigationTimingSettings.DEFAULT_RECHECK_ENABLED : recheckEnabled);
                  value.setPreferredStartHour((int) Math.round(snapshotNumber(
                        snapshot.child("preferred_start_hour"),
                        IrrigationTimingSettings.DEFAULT_START_HOUR)));
                  value.setPreferredEndHour((int) Math.round(snapshotNumber(
                        snapshot.child("preferred_end_hour"),
                        IrrigationTimingSettings.DEFAULT_END_HOUR)));
                  value.setUpdatedAtEpoch(Math.round(snapshotNumber(
                        snapshot.child("updated_at_epoch"), 0d)));
                  liveData.setValue(value);
               }

               @Override
               public void onCancelled(@NonNull DatabaseError error) {
                  Log.w(TAG, "Irrigation timing settings could not be read", error.toException());
               }
            });
      return liveData;
   }

   private static double snapshotNumber(DataSnapshot snapshot, double fallback) {
      Object value = snapshot.getValue();
      return value instanceof Number ? ((Number) value).doubleValue() : fallback;
   }
   public Task<Void> saveGlobalSettingsAndSyncZones(long moistureLimit, long pumpDuration, long cooldownSeconds, long restartDelta, boolean enabled, boolean autoMode) {
      return this.zonesRef.get().continueWithTask((task) -> {
         if (task.isSuccessful() && task.getResult() != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("commands/moisture_limit", moistureLimit);
            updates.put("commands/pump_duration", pumpDuration);
            updates.put("commands/cooldown_seconds", cooldownSeconds);
            updates.put("commands/restart_delta", restartDelta);
            updates.put("commands/enabled", enabled);
            updates.put("commands/auto_mode", autoMode);

            for(DataSnapshot zoneSnapshot : ((DataSnapshot)task.getResult()).getChildren()) {
               String zoneId = zoneSnapshot.getKey();
               if (zoneId != null && !zoneId.isBlank()) {
                  String path = "zones/" + zoneId + "/";
                  updates.put(path + "moisture_limit", moistureLimit);
                  updates.put(path + "pump_duration", pumpDuration);
                  updates.put(path + "cooldown_seconds", cooldownSeconds);
                  updates.put(path + "restart_delta", restartDelta);
               }
            }

            return this.deviceRef.updateChildren(updates);
         } else {
            Exception error = task.getException();
            return Tasks.forException((Exception)(error == null ? new IllegalStateException("Bölgeler okunamadı.") : error));
         }
      });
   }

   public Task<Void> updateGardenZoneValveMode(String zoneId, boolean physical) {
      Map<String, Object> updates = new HashMap<>();
      updates.put("valve_mode", physical ? "PHYSICAL" : "SIMULATION");
      updates.put("valve_mode_updated_at_epoch", ServerValue.TIMESTAMP);
      return this.zonesRef.child(zoneId).updateChildren(updates);
   }

   /** Saves a zone only after validating the shared eight-channel hardware map. */
   public Task<Void> createGardenZone(GardenZone zone) {
      return saveGardenZone(zone, true);
   }

   public Task<Void> saveGardenZone(GardenZone zone) {
      return saveGardenZone(zone, false);
   }

   private Task<Void> saveGardenZone(GardenZone zone, boolean requireAvailableSlot) {
      return this.zonesRef.get().continueWithTask(task -> {
         if (!task.isSuccessful()) {
            Exception error = task.getException();
            return Tasks.forException(error == null
                  ? new IllegalStateException("ZONE_READ_FAILED") : error);
         }
         DataSnapshot zonesSnapshot = task.getResult();
         GardenZone storedZone = validateGardenZoneSave(
               zonesSnapshot, zone, requireAvailableSlot);
         String zoneId = zone.getZone_id();

         boolean initializeWithoutSeason = storedZone == null
               || ZoneCapacityPolicy.isInactive(storedZone);
          boolean hasActiveSeason = storedZone != null && storedZone.getSeason() != null
                && storedZone.getSeason().isActive()
                && !clean(storedZone.getSeason().getActive_season_id()).isEmpty();
         String sensorId = clean(zone.getSensor_id());
         String valveId = clean(zone.getValve_id());
          String previousSensorId = storedZone == null
                ? "" : clean(storedZone.getSensor_id());
          boolean sensorChanged = !sensorId.equalsIgnoreCase(previousSensorId);
         boolean hardwareReady = !sensorId.isEmpty() && !valveId.isEmpty();
         int sensorDryRaw = zone.getSensor_calibration_dry_raw();
         int sensorWetRaw = zone.getSensor_calibration_wet_raw();
         if (sensorDryRaw <= sensorWetRaw) {
            sensorDryRaw = 12650;
            sensorWetRaw = 505;
         }
         long now = System.currentTimeMillis() / 1000L;
         long createdAt = snapshotLong(zonesSnapshot.child(zoneId).child("created_at_epoch"));
         if (initializeWithoutSeason || createdAt <= 0L) createdAt = now;
         String path = "zones/" + zoneId + "/";
         Map<String, Object> updates = new HashMap<>();
         updates.put(path + "zone_id", zoneId);
         updates.put(path + "area_id", clean(zone.getArea_id()));
         updates.put(path + "area_name", clean(zone.getArea_name()));
         updates.put(path + "location_name", clean(zone.getLocation_name()));
         updates.put(path + "area_icon", clean(zone.getArea_icon()));
         updates.put(path + "area_color", clean(zone.getArea_color()));
         updates.put(path + "low_moisture_alert_enabled", zone.isLow_moisture_alert_enabled());
         updates.put(path + "watering_complete_alert_enabled", zone.isWatering_complete_alert_enabled());
         updates.put(path + "name", clean(zone.getName()));
         updates.put(path + "plant_type", clean(zone.getPlant_type()));
         updates.put(path + "emoji", clean(zone.getEmoji()));
         updates.put(path + "sensor_id", sensorId);
         updates.put(path + "sensor_enabled", !sensorId.isEmpty());
          updates.put(path + "sensor_config_updated_at_epoch", now);
         updates.put(path + "sensor_calibration_dry_raw", sensorDryRaw);
         updates.put(path + "sensor_calibration_wet_raw", sensorWetRaw);
         updates.put(path + "valve_id", valveId);
         updates.put(path + "valve_type", valveId.isEmpty() ? "" : "SOLENOID");
         updates.put(path + "valve_mode", valveId.isEmpty() ? "SIMULATION" : "PHYSICAL");
         updates.put(path + "enabled", true);
         updates.put(path + "irrigation_enabled",
               hasActiveSeason && hardwareReady && zone.isIrrigation_enabled());
         updates.put(path + "moisture_limit", zone.getMoisture_limit());
         updates.put(path + "pump_duration", zone.getPump_duration());
         updates.put(path + "cooldown_seconds", zone.getCooldown_seconds());
         updates.put(path + "restart_delta", zone.getRestart_delta());
         updates.put(path + "order", zone.getOrder());
         updates.put(path + "lifecycle_status", hardwareReady
               ? ZoneCapacityPolicy.LIFECYCLE_ACTIVE
               : ZoneCapacityPolicy.LIFECYCLE_HARDWARE_PENDING);
         updates.put(path + "created_at_epoch", createdAt);
         updates.put(path + "archived_at_epoch", 0L);
         updates.put(path + "updated_at_epoch", now);
          if (sensorChanged) {
             updates.put(path + "moisture", 0);
             updates.put(path + "raw", 0);
             updates.put(path + "voltage", 0.0d);
             updates.put(path + "rssi", 0);
             updates.put(path + "firmware", "");
             updates.put(path + "uptime_seconds", 0L);
             updates.put(path + "updated_at", "");
             updates.put(path + "updated_at_epoch", 0L);
          }
         if (initializeWithoutSeason) {
            updates.put(path + "season/active_season_id", "");
            updates.put(path + "season/active_season_ids", null);
            updates.put(path + "season/status", SeasonStatus.CLOSED);
            updates.put(path + "season/label", "");
            updates.put(path + "season/started_at_epoch", 0L);
            updates.put(path + "season/ended_at_epoch", 0L);
            updates.put(path + "season/include_legacy_records", false);
            updates.put(path + "season/updated_at_epoch", now);
            updates.put(path + "ai/season_id", "");
            updates.put(path + "ai/season_status", SeasonStatus.CLOSED);
            updates.put(path + "ai/season_started_at_epoch", 0L);
            updates.put(path + "ai/season_closed_at_epoch", 0L);
         }
         return this.deviceRef.updateChildren(updates);
      });
   }

   /** Removes a disposable empty zone; otherwise archives it without losing history. */
   public Task<Boolean> deactivateGardenZone(String zoneId, boolean hasLocalHistory) {
      if (!ZoneCapacityPolicy.isValidZoneId(zoneId)) {
         return Tasks.forException(
               new IllegalArgumentException(ZoneCapacityPolicy.ERROR_INVALID_ZONE));
      }
      return this.deviceRef.get().continueWithTask(task -> {
         if (!task.isSuccessful()) {
            Exception error = task.getException();
            return Tasks.forException(error == null
                  ? new IllegalStateException("ZONE_READ_FAILED") : error);
         }
         DataSnapshot root = task.getResult();
         DataSnapshot zone = root.child("zones").child(zoneId);
         final ZoneCapacityPolicy.DeactivationAction action;
         try {
            action = ZoneCapacityPolicy.decideDeactivation(
                  zone.exists(),
                  snapshotString(zone.child("season").child("status")),
                  snapshotString(zone.child("season").child("active_season_id")),
                  hasActiveSeasonManifest(root, zone),
                  isZoneIrrigationBusySnapshot(root, zoneId),
                  hasLocalHistory,
                  hasZoneCloudHistory(root, zone));
         } catch (IllegalStateException error) {
            return Tasks.forException(error);
         }
         boolean removeEmpty = action
               == ZoneCapacityPolicy.DeactivationAction.DELETE;
         long now = System.currentTimeMillis() / 1000L;
         if (removeEmpty) {
            String removalId = now + "-" + UUID.randomUUID();
            String recyclePath = "zone_recycle_bin/" + zoneId + "/" + removalId + "/";
            Map<String, Object> removalUpdates = new HashMap<>();
            removalUpdates.put(recyclePath + "zone", zone.getValue());
            removalUpdates.put(recyclePath + "removed_at_epoch", now);
            removalUpdates.put(recyclePath + "reason", "EMPTY_ZONE_REMOVED");
            appendZoneNotificationRemovalUpdates(
                  root, zoneId, now, recyclePath, removalUpdates);
            removalUpdates.put("zones/" + zoneId, null);
            return zoneRemovalResult(
                  this.deviceRef.updateChildren(removalUpdates), true);
         }
         String path = "zones/" + zoneId + "/";
         Map<String, Object> updates = new HashMap<>();
         updates.put(path + "enabled", false);
         updates.put(path + "irrigation_enabled", false);
         updates.put(path + "sensor_enabled", false);
         updates.put(path + "previous_sensor_id", snapshotString(zone.child("sensor_id")));
         updates.put(path + "previous_valve_id", snapshotString(zone.child("valve_id")));
         updates.put(path + "sensor_id", "");
         updates.put(path + "valve_id", "");
         updates.put(path + "valve_type", "");
         updates.put(path + "valve_mode", "SIMULATION");
         updates.put(path + "lifecycle_status", ZoneCapacityPolicy.LIFECYCLE_INACTIVE);
         updates.put(path + "archived_at_epoch", now);
         updates.put(path + "updated_at_epoch", now);
         updates.put(path + "irrigation_status/watering_active", false);
         updates.put(path + "irrigation_status/selected_for_watering", false);
         updates.put(path + "irrigation_status/queue_position", 0);
         return zoneRemovalResult(this.deviceRef.updateChildren(updates), false);
      });
   }

   public Task<Void> updateFertilizationWaterAnalysis(String zoneId, double ph, double ecMs) {
      String path = "zones/" + zoneId + "/fertilization/";
      Map<String, Object> updates = new HashMap<>();
      updates.put(path + "water_ph", ph);
      updates.put(path + "water_ec_ms", ecMs);
      updates.put(path + "water_analysis_updated_at_epoch", System.currentTimeMillis() / 1000L);
      return this.deviceRef.updateChildren(updates);
   }

   public Task<Void> updateFertilizationProfile(String zoneId, boolean enabled, String plantingDate, String growthStage, boolean reminderEnabled, String productId, int intervalDays, long nextApplicationEpoch, double areaM2, double tankLiters) {
      Map<String, Object> updates = new HashMap<>();
      String profilePath = "zones/" + zoneId + "/fertilization/";
      String planId = "plan-" + zoneId;
      long updatedAt = System.currentTimeMillis() / 1000L;
      updates.put(profilePath + "enabled", enabled);
      updates.put(profilePath + "planting_date", plantingDate);
      updates.put(profilePath + "growth_stage", growthStage);
      updates.put(profilePath + "reminder_enabled", reminderEnabled);
      updates.put(profilePath + "active_product_id", productId);
      updates.put(profilePath + "area_m2", areaM2);
      updates.put(profilePath + "tank_liters", tankLiters);
      updates.put(profilePath + "active_plan_id", enabled ? planId : "");
      updates.put(profilePath + "next_application_at_epoch", enabled ? nextApplicationEpoch : 0L);
      updates.put(profilePath + "updated_at_epoch", updatedAt);
      String planPath = "fertilizer_plans/" + planId + "/";
      updates.put(planPath + "plan_id", planId);
      updates.put(planPath + "zone_id", zoneId);
      updates.put(planPath + "product_id", productId);
      updates.put(planPath + "interval_days", intervalDays);
      updates.put(planPath + "area_m2", areaM2);
      updates.put(planPath + "tank_liters", tankLiters);
      updates.put(planPath + "enabled", enabled);
      updates.put(planPath + "next_application_at_epoch", enabled ? nextApplicationEpoch : 0L);
      updates.put(planPath + "updated_at_epoch", updatedAt);
      return this.deviceRef.updateChildren(updates);
   }

   public LiveData<List<FertilizerProduct>> observeFertilizerProducts() {
      final FirebaseLiveData<List<FertilizerProduct>> liveData =
            new FirebaseLiveData<>(fertilizerProductsRef);
      liveData.setEventListener(new ValueEventListener() {
         public void onDataChange(@NonNull DataSnapshot snapshot) {
            List<FertilizerProduct> products = new ArrayList<>();

            for(DataSnapshot child : snapshot.getChildren()) {
               FertilizerProduct product = (FertilizerProduct)child.getValue(FertilizerProduct.class);
               if (product != null) {
                  if (product.getProduct_id() == null || product.getProduct_id().isBlank()) {
                     product.setProduct_id(child.getKey());
                  }

                  products.add(product);
               }
            }

            products.sort(Comparator.comparing((productx) -> productx.getName() == null ? "" : productx.getName(), String.CASE_INSENSITIVE_ORDER));
            liveData.setValue(products);
         }

         public void onCancelled(@NonNull DatabaseError error) {
            Log.e("FirebaseRepository", "Fertilizer products read failed", error.toException());
         }
      });
      return liveData;
   }

   public LiveData<List<CropCatalogItem>> observeCropCatalogItems() {
      final FirebaseLiveData<List<CropCatalogItem>> liveData =
            new FirebaseLiveData<>(cropCatalogRef);
      liveData.setEventListener(new ValueEventListener() {
         public void onDataChange(@NonNull DataSnapshot snapshot) {
            List<CropCatalogItem> items = new ArrayList<>();
            for (DataSnapshot child : snapshot.getChildren()) {
               CropCatalogItem item = child.getValue(CropCatalogItem.class);
               if (item == null) continue;
               if (item.getCrop_id() == null || item.getCrop_id().isBlank()) {
                  item.setCrop_id(child.getKey());
               }
               items.add(item);
            }
            liveData.setValue(items);
         }

         public void onCancelled(@NonNull DatabaseError error) {
            Log.e(TAG, "Crop catalog read failed", error.toException());
         }
      });
      return liveData;
   }

   public Task<Void> saveCropCatalogItem(CropCatalogItem item) {
      if (item == null || item.getCrop_id() == null || item.getCrop_id().isBlank()) {
         return Tasks.forException(new IllegalArgumentException("Crop catalog item is invalid"));
      }
      item.setSource(CropCatalogItem.SOURCE_USER);
      item.setEnabled(true);
      long now = System.currentTimeMillis() / 1000L;
      if (item.getCreated_at_epoch() <= 0L) item.setCreated_at_epoch(now);
      item.setUpdated_at_epoch(now);
      return this.cropCatalogRef.child(item.getCrop_id()).setValue(item);
   }

   public Task<Void> deactivateCropCatalogItem(String cropId) {
      if (cropId == null || cropId.isBlank()) {
         return Tasks.forException(new IllegalArgumentException("Crop id is required"));
      }
      Map<String, Object> updates = new HashMap<>();
      updates.put("enabled", false);
      updates.put("updated_at_epoch", System.currentTimeMillis() / 1000L);
      return this.cropCatalogRef.child(cropId).updateChildren(updates);
   }
   public LiveData<List<FertilizerRecommendation>> observeFertilizerRecommendations() {
      DatabaseReference recommendationsRef =
            deviceRef.child("fertilization").child("recommendations");
      final FirebaseLiveData<List<FertilizerRecommendation>> liveData =
            new FirebaseLiveData<>(recommendationsRef);
      liveData.setEventListener(new ValueEventListener() {
         public void onDataChange(@NonNull DataSnapshot snapshot) {
            List<FertilizerRecommendation> values = new ArrayList<>();

            for(DataSnapshot plant : snapshot.getChildren()) {
               for(DataSnapshot stage : plant.getChildren()) {
                  for(DataSnapshot entry : stage.getChildren()) {
                     FertilizerRecommendation value = (FertilizerRecommendation)entry.getValue(FertilizerRecommendation.class);
                     if (value != null) {
                        value.setPlant_type(plant.getKey());
                        value.setGrowth_stage(stage.getKey());
                        value.setRecommendation_id(entry.getKey());
                        values.add(value);
                     }
                  }
               }
            }

            liveData.setValue(values);
         }

         public void onCancelled(@NonNull DatabaseError error) {
            Log.e("FirebaseRepository", "Fertilizer recommendations read failed", error.toException());
         }
      });
      return liveData;
   }

   public LiveData<List<FertilizerStageGuide>> observeFertilizerStageGuides() {
      DatabaseReference stageGuidesRef =
            deviceRef.child("fertilization").child("stage_guides");
      final FirebaseLiveData<List<FertilizerStageGuide>> liveData =
            new FirebaseLiveData<>(stageGuidesRef);
      liveData.setEventListener(new ValueEventListener() {
         public void onDataChange(@NonNull DataSnapshot snapshot) {
            List<FertilizerStageGuide> values = new ArrayList<>();

            for(DataSnapshot plant : snapshot.getChildren()) {
               for(DataSnapshot stage : plant.getChildren()) {
                  FertilizerStageGuide value = (FertilizerStageGuide)stage.getValue(FertilizerStageGuide.class);
                  if (value != null) {
                     value.setPlant_type(plant.getKey());
                     value.setGrowth_stage(stage.getKey());
                     values.add(value);
                  }
               }
            }

            liveData.setValue(values);
         }

         public void onCancelled(@NonNull DatabaseError error) {
            Log.e("FirebaseRepository", "Fertilizer stage guides read failed", error.toException());
         }
      });
      return liveData;
   }

   public Task<Void> saveFertilizerProduct(FertilizerProduct product) {
      String productId = product.getProduct_id();
      if (productId == null || productId.isBlank()) {
         productId = "product-" + UUID.randomUUID();
         product.setProduct_id(productId);
      }

      product.setUpdated_at_epoch(System.currentTimeMillis() / 1000L);
      return this.fertilizerProductsRef.child(productId).setValue(product);
   }

   public Task<List<String>> findActiveZonesUsingFertilizer(String productId) {
      return this.zonesRef.get().continueWith((task) -> {
         if (!task.isSuccessful()) {
            throw (Exception)(task.getException() == null ? new IllegalStateException("Garden zones could not be read") : task.getException());
         } else {
            List<String> zoneNames = new ArrayList<>();

            for(DataSnapshot child : ((DataSnapshot)task.getResult()).getChildren()) {
               GardenZone zone = (GardenZone)child.getValue(GardenZone.class);
               if (zone != null && zone.getFertilization() != null && zone.getFertilization().isEnabled() && productId.equals(zone.getFertilization().getActive_product_id())) {
                  String name = com.alidogukan.avora.zones.PhysicalZoneIdentity.name(zone);
                  zoneNames.add(name != null && !name.isBlank() ? name : child.getKey());
               }
            }

            return zoneNames;
         }
      });
   }

   public Task<Void> deactivateFertilizerProduct(String productId) {
      Map<String, Object> updates = new HashMap<>();
      updates.put("enabled", false);
      updates.put("updated_at_epoch", System.currentTimeMillis() / 1000L);
      return this.fertilizerProductsRef.child(productId).updateChildren(updates);
   }

   public Task<Void> deleteFertilizerProduct(String productId) {
      return this.fertilizerProductsRef.child(productId).removeValue();
   }



   public LiveData<List<FertilizerApplication>> observeFertilizerHistory() {
      DatabaseReference fertilizerHistoryRef = deviceRef.child("fertilizer_history");
      final FirebaseLiveData<List<FertilizerApplication>> liveData =
            new FirebaseLiveData<>(fertilizerHistoryRef);
      liveData.setEventListener(new ValueEventListener() {
         public void onDataChange(@NonNull DataSnapshot snapshot) {
            List<FertilizerApplication> values = new ArrayList<>();

            for(DataSnapshot child : snapshot.getChildren()) {
               FertilizerApplication value = (FertilizerApplication)child.getValue(FertilizerApplication.class);
               if (value != null) {
                  values.add(value);
               }
            }

            values.sort((left, right) -> Long.compare(right.getApplied_at_epoch(), left.getApplied_at_epoch()));
            liveData.setValue(values);
         }

         public void onCancelled(@NonNull DatabaseError error) {
            Log.e("FirebaseRepository", "Fertilizer history read failed", error.toException());
         }
      });
      return liveData;
   }

   public LiveData<List<WateringHistory>> observeWateringHistory() {
      return observeWateringHistory(null);
   }

   /** Complete history for totals, never the recent-history screen's 50-record window. */
   public LiveData<List<WateringHistory>> observeWateringHistory(Consumer<DatabaseError> errorHandler) {
      final FirebaseLiveData<List<WateringHistory>> liveData =
            new FirebaseLiveData<>(historyRef);
      liveData.setEventListener(new ValueEventListener() {
         public void onDataChange(@NonNull DataSnapshot snapshot) {
            List<WateringHistory> values = new ArrayList<>();

            for(DataSnapshot child : snapshot.getChildren()) {
               WateringHistory value = (WateringHistory)child.getValue(WateringHistory.class);
               if (value != null) {
                  value.setRecordId(child.getKey());
                  values.add(value);
               }
            }

            values.sort((left, right) -> {
               String leftTime = left.getFinishedAt() == null ? "" : left.getFinishedAt();
               String rightTime = right.getFinishedAt() == null ? "" : right.getFinishedAt();
               return rightTime.compareTo(leftTime);
            });
            liveData.setValue(values);
         }

         public void onCancelled(@NonNull DatabaseError error) {
            Log.e("FirebaseRepository", "Watering history read failed", error.toException());
            if (errorHandler != null) errorHandler.accept(error);
         }
      });
      return liveData;
   }



   public static final class BulkFertilizerApplication {
      private final String zoneId;
      private final String zoneName;
      private final double appliedDose;
      private final double areaM2;
      private final double tankLiters;
      private final double recommendedDoseMin;
      private final double recommendedDoseMax;
      private final String applicationMethod;
      private final String notes;
      private final long appliedAt;
      private final String applicationType;
      private final String mixGroupId;
      private final String mixPartnerProductId;
      private final String mixPartnerProductName;
      private final String mixRiskLevel;

      public BulkFertilizerApplication(String zoneId, String zoneName, double appliedDose, double areaM2, double tankLiters, double recommendedDoseMin, double recommendedDoseMax, String applicationMethod, String notes, long appliedAt, String applicationType) {
         this(zoneId, zoneName, appliedDose, areaM2, tankLiters, recommendedDoseMin, recommendedDoseMax, applicationMethod, notes, appliedAt, applicationType, "", "", "", "");
      }

      public BulkFertilizerApplication(String zoneId, String zoneName, double appliedDose, double areaM2, double tankLiters, double recommendedDoseMin, double recommendedDoseMax, String applicationMethod, String notes, long appliedAt, String applicationType, String mixGroupId, String mixPartnerProductId, String mixPartnerProductName, String mixRiskLevel) {
         this.zoneId = zoneId;
         this.zoneName = zoneName;
         this.appliedDose = appliedDose;
         this.areaM2 = areaM2;
         this.tankLiters = tankLiters;
         this.recommendedDoseMin = recommendedDoseMin;
         this.recommendedDoseMax = recommendedDoseMax;
         this.applicationMethod = applicationMethod;
         this.notes = notes;
         this.appliedAt = appliedAt;
         this.applicationType = applicationType;
         this.mixGroupId = mixGroupId == null ? "" : mixGroupId;
         this.mixPartnerProductId = mixPartnerProductId == null ? "" : mixPartnerProductId;
         this.mixPartnerProductName = mixPartnerProductName == null ? "" : mixPartnerProductName;
         this.mixRiskLevel = mixRiskLevel == null ? "" : mixRiskLevel;
      }
   }

   public static final class FertilizerApplicationBatch {
      private final FertilizerProduct product;
      private final List<BulkFertilizerApplication> applications;
      private final String appliedUnit;
      private final boolean deductStock;

      public FertilizerApplicationBatch(FertilizerProduct product, List<BulkFertilizerApplication> applications, String appliedUnit, boolean deductStock) {
         this.product = product;
         this.applications = applications;
         this.appliedUnit = appliedUnit;
         this.deductStock = deductStock;
      }
   }

   public Task<Void> recordFertilizerApplicationSafely(String zoneId, String zoneName, FertilizerProduct product, double appliedDose, String appliedUnit, double areaM2, double tankLiters, double recommendedDoseMin, double recommendedDoseMax, boolean deductStock, String applicationMethod, String notes, long appliedAt, String applicationType) {
      long effectiveAppliedAt = appliedAt > 0L ? appliedAt : System.currentTimeMillis() / 1000L;
      BulkFertilizerApplication application = new BulkFertilizerApplication(zoneId, zoneName, appliedDose, areaM2, tankLiters, recommendedDoseMin, recommendedDoseMax, applicationMethod, notes, effectiveAppliedAt, applicationType);
      List<BulkFertilizerApplication> applications = new ArrayList<>();
      applications.add(application);
      return recordBulkFertilizerApplicationsSafely(product, applications, appliedUnit, deductStock);
   }

   public Task<Void> recordBulkFertilizerApplicationsSafely(FertilizerProduct product, List<BulkFertilizerApplication> applications, String appliedUnit, boolean deductStock) {
      List<FertilizerApplicationBatch> batches = new ArrayList<>();
      batches.add(new FertilizerApplicationBatch(product, applications, appliedUnit, deductStock));
      return recordFertilizerApplicationBatchesSafely(batches);
   }

   public Task<Void> recordFertilizerApplicationBatchesSafely(List<FertilizerApplicationBatch> batches) {
      if (batches == null || batches.isEmpty()) {
         return Tasks.forException(new IllegalArgumentException("Kaydedilecek gübre uygulaması bulunamadı."));
      }
      final List<List<String>> applicationIds = new ArrayList<>();
      for (FertilizerApplicationBatch batch : batches) {
         if (batch == null || batch.product == null || batch.product.getProduct_id() == null || batch.product.getProduct_id().isBlank()) {
            return Tasks.forException(new IllegalArgumentException("Gübre ürünü kimliği eksik."));
         }
         if (batch.applications == null || batch.applications.isEmpty()) {
            return Tasks.forException(new IllegalArgumentException("Kaydedilecek bölge bulunamadı."));
         }
         List<String> batchIds = new ArrayList<>();
         for (BulkFertilizerApplication ignored : batch.applications) {
            batchIds.add("application-" + UUID.randomUUID());
         }
         applicationIds.add(batchIds);
      }
      return runAtomicDeviceUpdate("Gübre uygulaması kaydedilemedi.", root -> {
         long recordedAt = System.currentTimeMillis() / 1000L;
         for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
            FertilizerApplicationBatch batch = batches.get(batchIndex);
            double totalDose = 0.0;
            for (BulkFertilizerApplication application : batch.applications) {
               if (application.appliedDose <= 0.0) {
                  throw new IllegalStateException("Uygulama miktarı sıfırdan büyük olmalıdır.");
               }
               totalDose += application.appliedDose;
            }
            if (batch.deductStock) {
               changeStock(root, batch.product.getProduct_id(), batch.appliedUnit, totalDose, recordedAt);
            }
            for (int index = 0; index < batch.applications.size(); index++) {
               writeFertilizerApplication(root, applicationIds.get(batchIndex).get(index), batch.applications.get(index), batch.product, batch.appliedUnit, batch.deductStock, recordedAt);
            }
         }
      });
   }
   public Task<Void> updateFertilizerApplicationSafely(FertilizerApplication value) {
      if (value == null || value.getApplication_id() == null || value.getApplication_id().isBlank()) {
         return Tasks.forException(new IllegalArgumentException("Gübre uygulama kaydı bulunamadı."));
      }
      return runAtomicDeviceUpdate("Gübre uygulama kaydı güncellenemedi.", root -> {
         MutableData history = root.child("fertilizer_history").child(value.getApplication_id());
         if (history.getValue() == null) {
            throw new IllegalStateException("Düzenlenecek gübre uygulama kaydı artık mevcut değil.");
         }
         String oldZoneId = stringValue(history.child("zone_id"));
         String oldType = normalizedApplicationType(stringValue(history.child("application_type")));
         String oldProductId = stringValue(history.child("product_id"));
         String oldUnit = stringValue(history.child("dose_unit"));
         double oldDose = numberValue(history.child("applied_dose"));
         boolean stockDeducted = booleanValue(history.child("stock_deducted"));
         long recordedAt = System.currentTimeMillis() / 1000L;
         if (stockDeducted) {
            double difference = value.getApplied_dose() - oldDose;
            if (Math.abs(difference) > 0.000001) {
               changeStock(root, oldProductId, oldUnit, difference, recordedAt);
            }
         }
         history.child("applied_dose").setValue(value.getApplied_dose());
         history.child("applied_at_epoch").setValue(value.getApplied_at_epoch());
         history.child("next_application_at_epoch").setValue(value.getNext_application_at_epoch());
         history.child("application_method").setValue(value.getApplication_method());
         history.child("notes").setValue(value.getNotes());
         history.child("outcome_follow_up_due_at_epoch").setValue(
               value.getApplied_at_epoch() + FertilizerOutcomeFollowUpPolicy.FOLLOW_UP_DELAY_SECONDS
         );
         history.child("outcome_observed_at_epoch").setValue(value.getOutcome_observed_at_epoch());
         history.child("outcome_status").setValue(value.getOutcome_status());
         history.child("outcome_vigor_score").setValue(value.getOutcome_vigor_score());
         history.child("outcome_notes").setValue(value.getOutcome_notes());
         history.child("updated_at_epoch").setValue(recordedAt);
         recalculateApplicationSchedule(root, oldZoneId, oldType, recordedAt);
      });
   }

   public Task<Void> deleteFertilizerApplicationSafely(FertilizerApplication target) {
      if (target == null || target.getApplication_id() == null || target.getApplication_id().isBlank()) {
         return Tasks.forException(new IllegalArgumentException("Silinecek gübre uygulama kaydı bulunamadı."));
      }
      return runAtomicDeviceUpdate("Gübre uygulama kaydı silinemedi.", root -> {
         MutableData history = root.child("fertilizer_history").child(target.getApplication_id());
         if (history.getValue() == null) {
            throw new IllegalStateException("Silinecek gübre uygulama kaydı artık mevcut değil.");
         }
         String zoneId = stringValue(history.child("zone_id"));
         String type = normalizedApplicationType(stringValue(history.child("application_type")));
         long recordedAt = System.currentTimeMillis() / 1000L;
         if (booleanValue(history.child("stock_deducted"))) {
            changeStock(root, stringValue(history.child("product_id")), stringValue(history.child("dose_unit")), -numberValue(history.child("applied_dose")), recordedAt);
         }
         history.setValue(null);
         recalculateApplicationSchedule(root, zoneId, type, recordedAt);
      });
   }

   private void writeFertilizerApplication(MutableData root, String applicationId, BulkFertilizerApplication application, FertilizerProduct product, String appliedUnit, boolean stockDeducted, long recordedAt) {
      List<String> seasonIds = ensureActiveSeasonsForWrite(root, application.zoneId, recordedAt);
      String type = normalizedApplicationType(application.applicationType);
      int intervalDays = Math.max(0, product.getMinimum_interval_days());
      long nextAt = intervalDays == 0 ? 0L
            : application.appliedAt + (long)intervalDays * 86400L;
      MutableData history = root.child("fertilizer_history").child(applicationId);
      history.child("season_id").setValue(seasonIds.get(0));
      history.child("season_ids").setValue(new ArrayList<>(seasonIds));
      history.child("application_id").setValue(applicationId);
      history.child("zone_id").setValue(application.zoneId);
      history.child("zone_name").setValue(application.zoneName);
      history.child("product_id").setValue(product.getProduct_id());
      history.child("product_name").setValue(product.getName());
      history.child("applied_dose").setValue(application.appliedDose);
      history.child("dose_unit").setValue(appliedUnit);
      history.child("area_m2").setValue(application.areaM2);
      history.child("tank_liters").setValue(application.tankLiters);
      history.child("recommended_dose_min").setValue(application.recommendedDoseMin);
      history.child("recommended_dose_max").setValue(application.recommendedDoseMax);
      history.child("applied_at_epoch").setValue(application.appliedAt);
      history.child("next_application_at_epoch").setValue(nextAt);
      history.child("outcome_follow_up_due_at_epoch").setValue(
            application.appliedAt + FertilizerOutcomeFollowUpPolicy.FOLLOW_UP_DELAY_SECONDS
      );
      history.child("source").setValue("MANUAL");
      history.child("application_type").setValue(type);
      history.child("application_method").setValue(application.applicationMethod);
      history.child("notes").setValue(application.notes);
      history.child("stock_deducted").setValue(stockDeducted);
      history.child("recorded_at_epoch").setValue(recordedAt);
      if (!application.mixGroupId.isBlank()) {
         history.child("mix_group_id").setValue(application.mixGroupId);
         history.child("mix_partner_product_id").setValue(application.mixPartnerProductId);
         history.child("mix_partner_product_name").setValue(application.mixPartnerProductName);
         history.child("mix_risk_level").setValue(application.mixRiskLevel);
      }
      updateScheduleIfNewer(root, history, recordedAt);
   }

   private void updateScheduleIfNewer(MutableData root, MutableData history, long recordedAt) {
      String zoneId = stringValue(history.child("zone_id"));
      String type = normalizedApplicationType(stringValue(history.child("application_type")));
      long appliedAt = longValue(history.child("applied_at_epoch"));
      MutableData schedule = root.child("zones").child(zoneId).child("fertilization").child("application_schedules").child(type);
      long scheduledAt = longValue(schedule.child("last_application_at_epoch"));
      long scheduledNextAt = longValue(schedule.child("next_application_at_epoch"));
      long candidateNextAt = longValue(history.child("next_application_at_epoch"));
      if (appliedAt < scheduledAt
            || (appliedAt == scheduledAt && candidateNextAt < scheduledNextAt)) {
         return;
      }
      copyHistoryToSchedule(schedule, history, recordedAt);
      if ("NUTRITION".equals(type)) {
         updateNutritionPointers(root, zoneId, history, recordedAt);
      }
   }

   private List<String> ensureActiveSeasonsForWrite(
         MutableData root, String zoneId, long now) {
      String primary = ensureActiveSeasonForWrite(root, zoneId, now);
      LinkedHashSet<String> seasonIds = new LinkedHashSet<>();
      seasonIds.add(primary);
      MutableData active = root.child("zones").child(zoneId)
            .child("season").child("active_season_ids");
      for (MutableData child : active.getChildren()) {
         Object raw = child.getValue();
         if (raw instanceof Boolean && !Boolean.TRUE.equals(raw)) continue;
         String value = raw instanceof String ? ((String) raw).trim() : "";
         if (value.isBlank()) {
            String key = child.getKey();
            value = key == null ? "" : key.trim();
         }
         if (!value.isBlank()) seasonIds.add(value);
      }
      return new ArrayList<>(seasonIds);
   }

   private String ensureActiveSeasonForWrite(MutableData root, String zoneId, long now) {
      if (zoneId == null || zoneId.isBlank()) {
         throw new IllegalStateException("Bölge bilgisi gerekli.");
      }
      MutableData zone = root.child("zones").child(zoneId);
      MutableData state = zone.child("season");
      String status = stringValue(state.child("status"));
      String existingId = stringValue(state.child("active_season_id"));
      if (SeasonStatus.isActive(status) && !existingId.isBlank()) {
         return existingId;
      }
      if (SeasonStatus.isClosed(status)) {
         throw new IllegalStateException("Bu bölgenin sezonu kapalı. Önce yeni sezon başlatın.");
      }

      String seasonId = SeasonScope.createSeasonId(zoneId, now);
      String zoneName = stringValue(zone.child("name"));
      String plantType = stringValue(zone.child("plant_type"));
      String plantingDate = stringValue(zone.child("fertilization").child("planting_date"));
      String year = new java.text.SimpleDateFormat("yyyy", Locale.getDefault())
            .format(new java.util.Date(now * 1000L));
      String label = year + " " + zoneName;

      state.child("active_season_id").setValue(seasonId);
      state.child("active_season_ids").child(seasonId).setValue(true);
      state.child("status").setValue(SeasonStatus.ACTIVE);
      state.child("label").setValue(label);
      state.child("started_at_epoch").setValue(now);
      state.child("ended_at_epoch").setValue(0L);
      state.child("include_legacy_records").setValue(true);
      state.child("updated_at_epoch").setValue(now);

      MutableData manifest = root.child("garden_journal").child("seasons").child(seasonId);
      manifest.child("season_id").setValue(seasonId);
      manifest.child("zone_id").setValue(zoneId);
      manifest.child("zone_name").setValue(zoneName);
      manifest.child("plant_type").setValue(plantType);
      manifest.child("label").setValue(label);
      manifest.child("status").setValue(SeasonStatus.ACTIVE);
      manifest.child("planting_date").setValue(plantingDate);
      manifest.child("started_at_epoch").setValue(now);
      manifest.child("ended_at_epoch").setValue(0L);
      manifest.child("includes_legacy_records").setValue(true);
      manifest.child("created_at_epoch").setValue(now);
      manifest.child("updated_at_epoch").setValue(now);
      zone.child("ai").child("season_id").setValue(seasonId);
      return seasonId;
   }

   private void recalculateApplicationSchedule(MutableData root, String zoneId, String type, long recordedAt) {
      MutableData seasonState = root.child("zones").child(zoneId).child("season");
      String activeSeasonId = stringValue(seasonState.child("active_season_id"));
      boolean includeLegacy = booleanValue(seasonState.child("include_legacy_records"));
      MutableData latest = null;
      long latestAt = Long.MIN_VALUE;
      for (MutableData candidate : root.child("fertilizer_history").getChildren()) {
         if (!zoneId.equals(stringValue(candidate.child("zone_id")))) {
            continue;
         }
         if (!activeSeasonId.isBlank()) {
            if (!fertilizerRecordBelongsToSeason(
                  candidate, activeSeasonId, includeLegacy)) continue;
         }
         if (!type.equals(normalizedApplicationType(stringValue(candidate.child("application_type"))))) {
            continue;
         }
         long candidateAt = longValue(candidate.child("applied_at_epoch"));
         if (candidateAt > latestAt) {
            latest = candidate;
            latestAt = candidateAt;
         }
      }
      MutableData schedule = root.child("zones").child(zoneId).child("fertilization").child("application_schedules").child(type);
      if (latest == null) {
         schedule.setValue(null);
         if ("NUTRITION".equals(type)) {
            clearNutritionPointers(root, zoneId, recordedAt);
         }
         return;
      }
      copyHistoryToSchedule(schedule, latest, recordedAt);
      if ("NUTRITION".equals(type)) {
         updateNutritionPointers(root, zoneId, latest, recordedAt);
      }
   }

   private static boolean fertilizerRecordBelongsToSeason(
         MutableData record, String seasonId, boolean includeLegacy) {
      for (MutableData child : record.child("season_ids").getChildren()) {
         if (seasonId.equals(stringValue(child))) return true;
         if (seasonId.equals(child.getKey()) && booleanValue(child)) return true;
      }
      String primary = stringValue(record.child("season_id"));
      return primary.isBlank() ? includeLegacy : seasonId.equals(primary);
   }

   private void copyHistoryToSchedule(MutableData schedule, MutableData history, long recordedAt) {
      long appliedAt = longValue(history.child("applied_at_epoch"));
      long nextAt = longValue(history.child("next_application_at_epoch"));
      schedule.child("product_id").setValue(stringValue(history.child("product_id")));
      schedule.child("product_name").setValue(stringValue(history.child("product_name")));
      schedule.child("last_application_at_epoch").setValue(appliedAt);
      schedule.child("next_application_at_epoch").setValue(nextAt);
      schedule.child("interval_days").setValue(nextAt <= 0L ? 0L
            : Math.max(0L, (nextAt - appliedAt) / 86400L));
      schedule.child("updated_at_epoch").setValue(recordedAt);
   }

   private void updateNutritionPointers(MutableData root, String zoneId, MutableData history, long recordedAt) {
      long appliedAt = longValue(history.child("applied_at_epoch"));
      long nextAt = longValue(history.child("next_application_at_epoch"));
      MutableData profile = root.child("zones").child(zoneId).child("fertilization");
      profile.child("last_application_at_epoch").setValue(appliedAt);
      profile.child("next_application_at_epoch").setValue(nextAt);
      profile.child("updated_at_epoch").setValue(recordedAt);
      MutableData plan = root.child("fertilizer_plans").child("plan-" + zoneId);
      plan.child("last_application_at_epoch").setValue(appliedAt);
      plan.child("next_application_at_epoch").setValue(nextAt);
      plan.child("updated_at_epoch").setValue(recordedAt);
   }

   private void clearNutritionPointers(MutableData root, String zoneId, long recordedAt) {
      MutableData profile = root.child("zones").child(zoneId).child("fertilization");
      profile.child("last_application_at_epoch").setValue(0L);
      profile.child("next_application_at_epoch").setValue(0L);
      profile.child("updated_at_epoch").setValue(recordedAt);
      MutableData plan = root.child("fertilizer_plans").child("plan-" + zoneId);
      plan.child("last_application_at_epoch").setValue(0L);
      plan.child("next_application_at_epoch").setValue(0L);
      plan.child("updated_at_epoch").setValue(recordedAt);
   }

   private void changeStock(MutableData root, String productId, String appliedUnit, double amountToDeduct, long recordedAt) {
      if (productId == null || productId.isBlank()) {
         throw new IllegalStateException("Stok güncellemesi için ürün kimliği eksik.");
      }
      MutableData product = root.child("fertilizer_products").child(productId);
      if (product.getValue() == null) {
         throw new IllegalStateException("Stok güncellenecek gübre ürünü bulunamadı.");
      }
      String stockUnit = stringValue(product.child("stock_unit"));
      if (stockUnit.isBlank() || appliedUnit == null || !stockUnit.equalsIgnoreCase(appliedUnit)) {
         throw new IllegalStateException("Gübre stok birimi uygulama birimiyle uyuşmuyor.");
      }
      double currentStock = numberValue(product.child("stock_amount"));
      double updatedStock = currentStock - amountToDeduct;
      if (updatedStock < -0.000001) {
         throw new IllegalStateException("Gübre stoğu bu uygulama için yetersiz.");
      }
      product.child("stock_amount").setValue(Math.max(0.0, updatedStock));
      product.child("updated_at_epoch").setValue(recordedAt);
   }

   private Task<Void> runAtomicDeviceUpdate(String fallbackMessage, DeviceMutation mutation) {
      TaskCompletionSource<Void> completion = new TaskCompletionSource<>();
      AtomicReference<String> failure = new AtomicReference<>(fallbackMessage);
      this.deviceRef.runTransaction(new Transaction.Handler() {
         @NonNull
         @Override
         public Transaction.Result doTransaction(@NonNull MutableData currentData) {
            try {
               mutation.apply(currentData);
               return Transaction.success(currentData);
            } catch (RuntimeException error) {
               if (error.getMessage() != null && !error.getMessage().isBlank()) {
                  failure.set(error.getMessage());
               }
               return Transaction.abort();
            }
         }

         @Override
         public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
            if (error != null) {
               completion.setException(error.toException());
            } else if (!committed) {
               completion.setException(new IllegalStateException(failure.get()));
            } else {
               completion.setResult(null);
            }
         }
      });
      return completion.getTask();
   }

   private static String normalizedApplicationType(String value) {
      return value == null || value.isBlank()
            ? "NUTRITION"
            : value.trim().toUpperCase(Locale.ROOT);
   }

   private static boolean isZoneIrrigationBusySnapshot(
         DataSnapshot root, String zoneId) {
      DataSnapshot zone = root.child("zones").child(zoneId);
      DataSnapshot irrigation = zone.child("irrigation_status");
      boolean pending = false;
      for (DataSnapshot item : root.child("irrigation_runtime")
            .child("pending_waterings").getChildren()) {
         if (zoneId.equals(snapshotString(item.child("record").child("zone_id")))) {
            pending = true;
            break;
         }
      }
      boolean hardwareBusy = snapshotBoolean(root.child("status").child("relay"))
            || snapshotBoolean(root.child("status").child("valve_open"))
            || snapshotBoolean(root.child("commands").child("relay"))
            || snapshotBoolean(root.child("irrigation_hardware").child("valve_open"));
      String activeZoneId = firstNonBlankSnapshot(
            snapshotString(root.child("status").child("active_zone_id")),
            snapshotString(root.child("irrigation_runtime").child("active_zone_id")));
      String activeValveId = firstNonBlankSnapshot(
            snapshotString(root.child("status").child("active_valve_id")),
            snapshotString(root.child("irrigation_hardware").child("active_valve_id")));
      return ZoneOperationSafetyPolicy.isTargetBusy(
            zoneId,
            snapshotString(zone.child("valve_id")),
            snapshotBoolean(irrigation.child("watering_active")),
            snapshotBoolean(irrigation.child("selected_for_watering")),
            snapshotLong(irrigation.child("queue_position")),
            pending,
            hardwareBusy,
            activeZoneId,
            activeValveId);
   }

   private static String firstNonBlankSnapshot(String first, String second) {
      return first == null || first.isBlank() ? second : first;
   }

   private static boolean hasActiveSeasonManifest(
         DataSnapshot root, DataSnapshot zoneSnapshot) {
      GardenZone zone = zoneSnapshot.getValue(GardenZone.class);
      if (zone == null) return false;
      ZoneSeasonState current = zone.getSeason();
      if (current == null || !current.isActive()) return false;
      for (DataSnapshot child : root.child("garden_journal")
            .child("seasons").getChildren()) {
         GardenSeason season = child.getValue(GardenSeason.class);
         if (season == null) continue;
         if (season.getSeason_id().isBlank()) {
            season.setSeason_id(child.getKey());
         }
         if (SeasonScope.isCurrentActiveSeason(season, current)
               && ZoneAreaIdentity.belongsToCurrentOrArea(zone, season)) return true;
      }
      return false;
   }

   private static boolean hasZoneCloudHistory(DataSnapshot root, DataSnapshot zone) {
      for (DataSnapshot record : root.child("watering_history").getChildren()) {
         if (recordBelongsToCurrentArea(root, zone, record)
               && SeasonRecordPolicy.hasMeaningfulWatering(
               snapshotLong(record.child("duration")))) return true;
      }
      if (containsCurrentAreaRecord(root, zone, root.child("fertilizer_history"))) return true;

      DataSnapshot journal = root.child("garden_journal");
      for (DataSnapshot record : journal.child("events").getChildren()) {
         if (!recordBelongsToCurrentArea(root, zone, record)) continue;
         if (SeasonRecordPolicy.isFieldJournalEvent(
               snapshotString(record.child("type")),
               snapshotString(record.child("source")),
               snapshotString(record.child("source_key")))) return true;
      }
      if (containsCurrentAreaRecord(root, zone, journal.child("photo_metadata"))) return true;
      for (DataSnapshot outcome : journal.child("season_outcomes").getChildren()) {
         if (!recordBelongsToCurrentArea(root, zone, outcome)) continue;
         if (SeasonRecordPolicy.hasMeaningfulOutcomeValues(
               snapshotString(outcome.child("harvest_amount")),
               snapshotString(outcome.child("yield_note")),
               snapshotString(outcome.child("issues_note")),
               snapshotString(outcome.child("successful_practices")),
               snapshotString(outcome.child("water_summary")),
               snapshotString(outcome.child("fertilizer_summary")),
               snapshotString(outcome.child("next_season_note")))) return true;
      }
      return false;
   }

   private static boolean containsCurrentAreaRecord(
         DataSnapshot root, DataSnapshot zone, DataSnapshot collection) {
      for (DataSnapshot record : collection.getChildren()) {
         if (recordBelongsToCurrentArea(root, zone, record)) return true;
      }
      return false;
   }

   private static boolean recordBelongsToCurrentArea(
         DataSnapshot root, DataSnapshot zone, DataSnapshot record) {
      String zoneId = snapshotString(zone.child("zone_id"));
      if (zoneId.isBlank()) zoneId = snapshotStringKey(zone);
      if (!zoneId.equals(snapshotString(record.child("zone_id")))) return false;

      String areaId = snapshotString(zone.child("area_id"));
      if (areaId.isBlank()) return true;

      java.util.LinkedHashSet<String> seasonIds = new java.util.LinkedHashSet<>();
      String primary = snapshotString(record.child("season_id"));
      if (!primary.isBlank()) seasonIds.add(primary);
      for (DataSnapshot child : record.child("season_ids").getChildren()) {
         Object raw = child.getValue();
         if (raw instanceof Boolean && !Boolean.TRUE.equals(raw)) continue;
         String value = raw instanceof String ? ((String) raw).trim() : "";
         if (value.isBlank()) {
            String key = child.getKey();
            value = key == null ? "" : key.trim();
         }
         if (value != null && !value.isBlank()) seasonIds.add(value);
      }
      for (String seasonId : seasonIds) {
         DataSnapshot manifest = root.child("garden_journal")
               .child("seasons").child(seasonId);
         if (areaId.equals(snapshotString(manifest.child("area_id")))) return true;
      }
      return false;
   }

   private static String snapshotStringKey(DataSnapshot snapshot) {
      String key = snapshot == null ? null : snapshot.getKey();
      return key == null ? "" : key;
   }

   private static boolean containsZoneReference(
         DataSnapshot node, String zoneId) {
      if (!node.exists()) return false;
      if (zoneId.equals(snapshotString(node.child("zone_id")))
            || zoneId.equals(snapshotString(node.child("analysis_zone_id")))) {
         return true;
      }
      for (DataSnapshot child : node.getChildren()) {
         if (containsZoneReference(child, zoneId)) return true;
      }
      return false;
   }

   private static void appendZoneNotificationRemovalUpdates(
         DataSnapshot root,
         String zoneId,
         long removedAtEpoch,
         String recyclePath,
         Map<String, Object> updates) {
      for (DataSnapshot record : root.child("notifications").getChildren()) {
         if (!zoneId.equals(snapshotString(record.child("zone_id")))) continue;
         String id = record.getKey();
         if (id == null || id.isBlank()) continue;
         updates.put(recyclePath + "notifications/" + id, record.getValue());
         updates.put("notifications/" + id, null);
         updates.put("notification_deletions/" + id + "/source_key",
               snapshotString(record.child("source_key")));
         updates.put("notification_deletions/" + id + "/deleted_at_epoch",
               removedAtEpoch);
      }
   }

   private static Task<Boolean> zoneRemovalResult(
         Task<Void> writeTask, boolean removed) {
      return writeTask.continueWith(task -> {
         if (!task.isSuccessful()) {
            Exception error = task.getException();
            throw error == null
                  ? new IllegalStateException("ZONE_WRITE_FAILED")
                  : error;
         }
         return removed;
      });
   }

   private static boolean snapshotBoolean(DataSnapshot data) {
      Boolean value = data.getValue(Boolean.class);
      return Boolean.TRUE.equals(value);
   }

   private static long snapshotLong(DataSnapshot data) {
      Object value = data.getValue();
      return value instanceof Number ? ((Number)value).longValue() : 0L;
   }

   private static String snapshotString(DataSnapshot data) {
      Object value = data.getValue();
      return value == null ? "" : String.valueOf(value).trim();
   }

   private static String clean(String value) {
      return value == null ? "" : value.trim();
   }


   private static String stringValue(MutableData data) {
      Object value = data.getValue();
      return value == null ? "" : String.valueOf(value);
   }

   private static double numberValue(MutableData data) {
      Object value = data.getValue();
      return value instanceof Number ? ((Number)value).doubleValue() : 0.0;
   }

   private static long longValue(MutableData data) {
      Object value = data.getValue();
      return value instanceof Number ? ((Number)value).longValue() : 0L;
   }

   private static boolean booleanValue(MutableData data) {
      Object value = data.getValue();
      return value instanceof Boolean && (Boolean)value;
   }

   @FunctionalInterface
   private interface DeviceMutation {
      void apply(MutableData root);
   }
   public Task<Void> requestZoneValveTest(GardenZone zone, int durationSeconds) {
      Map<String, Object> command = new HashMap<>();
      command.put("requested", true);
      command.put("request_id", UUID.randomUUID().toString());
      command.put("zone_id", zone.getZone_id());
      command.put("valve_id", zone.getValve_id());
      command.put("duration", Math.max(1, Math.min(10800, durationSeconds)));
      command.put("cancel_requested", false);
      command.put("requested_at", ServerValue.TIMESTAMP);
      return this.commandsRef.child("zone_test").setValue(command);
   }

   public Task<Void> cancelZoneValveTest() {
      return this.commandsRef.child("zone_test").child("cancel_requested").setValue(true);
   }

   public Task<Void> requestIrrigationAssistantRestart(String zoneId) {
      Map<String, Object> command = new HashMap<>();
      command.put("requested", true);
      command.put("request_id", UUID.randomUUID().toString());
      command.put("zone_id", zoneId == null ? "" : zoneId.trim());
      command.put("requested_at", ServerValue.TIMESTAMP);
      return this.commandsRef.child("irrigation_assistant_reset").setValue(command);
   }

   public Task<Void> requestNetworkConfiguration(NetworkConfigurationRequest value) {
      if (value == null) {
         return Tasks.forException(new IllegalArgumentException(
               "Network configuration is required"));
      }
      Map<String, Object> command = new HashMap<>();
      command.put("requested", true);
      command.put("request_id", value.requestId);
      command.put("interface", value.interfaceName);
      command.put("mode", value.mode);
      command.put("ip_address", value.ipAddress);
      command.put("prefix_length", value.prefixLength);
      command.put("gateway", value.gateway);
      command.put("primary_dns", value.primaryDns);
      command.put("secondary_dns", value.secondaryDns);
      command.put("requested_at", ServerValue.TIMESTAMP);
      command.put("expires_at", System.currentTimeMillis() + 180_000L);
      command.put("source", "android");
      return commandsRef.child("network_configuration").setValue(command);
   }

   public LiveData<Status> observeStatus(Consumer<DatabaseError> errorHandler) {
      return observeModel(this.statusRef, Status.class, "Device status", errorHandler);
   }

   public LiveData<Command> observeCommands(Consumer<DatabaseError> errorHandler) {
      return observeModel(this.commandsRef, Command.class, "Commands", errorHandler);
   }

   public LiveData<Health> observeHealth(Consumer<DatabaseError> errorHandler) {
      return observeModel(this.healthRef, Health.class, "Device health", errorHandler);
   }

   public LiveData<Statistics> observeStatistics(Consumer<DatabaseError> errorHandler) {
      return observeModel(this.statisticsRef, Statistics.class, "Statistics", errorHandler);
   }

   public LiveData<AdaptiveRecommendation> observeAdaptiveRecommendationData(
         Consumer<DatabaseError> errorHandler) {
      return observeModel(
            this.adaptiveRecommendationRef,
            AdaptiveRecommendation.class,
            "Adaptive recommendation",
            errorHandler);
   }

   public LiveData<AIDecision> observeAIDecision(Consumer<DatabaseError> errorHandler) {
      return observeModel(this.aiDecisionRef, AIDecision.class, "AI decision", errorHandler);
   }

   public LiveData<AIExplanation> observeAIExplanation(
         Consumer<DatabaseError> errorHandler) {
      return observeModel(
            this.aiExplanationRef,
            AIExplanation.class,
            "AI explanation",
            errorHandler);
   }

   public LiveData<Boolean> observeZoneTestActive(Consumer<DatabaseError> errorHandler) {
      return observeModel(
            this.commandsRef.child("zone_test").child("active"),
            Boolean.class,
            "Zone test state",
            errorHandler);
   }

   public LiveData<List<WateringHistory>> observeRecentWateringHistory(
         int limit,
         Consumer<DatabaseError> errorHandler
   ) {
      Query query = this.historyRef.orderByKey().limitToLast(Math.max(1, limit));
      final FirebaseLiveData<List<WateringHistory>> liveData =
            new FirebaseLiveData<>(query);
      liveData.setEventListener(new ValueEventListener() {
         @Override
         public void onDataChange(@NonNull DataSnapshot snapshot) {
            List<WateringHistory> values = new ArrayList<>();
            for (DataSnapshot child : snapshot.getChildren()) {
               WateringHistory item = child.getValue(WateringHistory.class);
               if (item == null) continue;
               item.setRecordId(child.getKey());
               values.add(item);
            }
            java.util.Collections.reverse(values);
            liveData.setValue(values);
         }

         @Override
         public void onCancelled(@NonNull DatabaseError error) {
            Log.e(TAG, "Recent watering history read failed", error.toException());
            if (errorHandler != null) errorHandler.accept(error);
         }
      });
      return liveData;
   }

   /** Application-lifetime status listener used only by the global outage monitor. */
   public void observeApplicationStatus(ValueEventListener listener) {
      this.statusRef.addValueEventListener(listener);
   }

   public void setRelay(boolean value) {
      Map<String, Object> updates = new HashMap<>();
      updates.put("relay", value);
      updates.put("relay_requested_at", ServerValue.TIMESTAMP);
      this.commandsRef.updateChildren(updates);
   }

   public void setAutoMode(boolean value) {
      this.commandsRef.child("auto_mode").setValue(value);
   }

   public void restartDevice() {
      this.commandsRef.child("restart_device").setValue(true);
   }

   public void startManualWatering() {
      Map<String, Object> updates = new HashMap<>();
      updates.put("auto_mode", false);
      updates.put("relay", true);
      updates.put("relay_requested_at", ServerValue.TIMESTAMP);
      this.commandsRef.updateChildren(updates);
   }

   public void stopManualWatering() {
      this.setRelay(false);
   }

   public LiveData<PredictionValidationStatus> observePredictionValidationStatus() {
      final FirebaseLiveData<PredictionValidationStatus> liveData =
            new FirebaseLiveData<>(predictionValidationRef);
      liveData.setEventListener(new ValueEventListener() {
         public void onDataChange(@NonNull DataSnapshot snapshot) {
            PredictionValidationStatus status = (PredictionValidationStatus)snapshot.getValue(PredictionValidationStatus.class);
            liveData.setValue(status);
         }

         public void onCancelled(@NonNull DatabaseError error) {
            Log.e("FirebaseRepository", "Prediction validation observation failed: " + error.getMessage());
         }
      });
      return liveData;
   }

   public LiveData<MoisturePrediction> observeMoisturePrediction() {
      final FirebaseLiveData<MoisturePrediction> liveData =
            new FirebaseLiveData<>(moisturePredictionRef);
      liveData.setEventListener(new ValueEventListener() {
         public void onDataChange(@NonNull DataSnapshot snapshot) {
            MoisturePrediction value = (MoisturePrediction)snapshot.getValue(MoisturePrediction.class);
            liveData.setValue(value);
         }

         public void onCancelled(@NonNull DatabaseError error) {
            Log.e("FirebaseRepository", "Moisture prediction read failed", error.toException());
         }
      });
      return liveData;
   }

   public LiveData<PredictionAccuracy> observePredictionAccuracy() {
      final FirebaseLiveData<PredictionAccuracy> liveData =
            new FirebaseLiveData<>(predictionAccuracyRef);
      liveData.setEventListener(new ValueEventListener() {
         public void onDataChange(@NonNull DataSnapshot snapshot) {
            PredictionAccuracy value = (PredictionAccuracy)snapshot.getValue(PredictionAccuracy.class);
            liveData.setValue(value);
         }

         public void onCancelled(@NonNull DatabaseError error) {
            Log.e("FirebaseRepository", "Prediction accuracy read failed", error.toException());
         }
      });
      return liveData;
   }

   public LiveData<SoilLearningProfile> observeSoilLearningProfile() {
      final FirebaseLiveData<SoilLearningProfile> liveData =
            new FirebaseLiveData<>(soilLearningProfileRef);
      liveData.setEventListener(new ValueEventListener() {
         public void onDataChange(@NonNull DataSnapshot snapshot) {
            SoilLearningProfile profile = (SoilLearningProfile)snapshot.getValue(SoilLearningProfile.class);
            liveData.setValue(profile);
         }

         public void onCancelled(@NonNull DatabaseError error) {
            Log.e("FirebaseRepository", "Soil learning profile read failed", error.toException());
         }
      });
      return liveData;
   }

   public LiveData<UnifiedConfidence> observeUnifiedConfidence() {
      final FirebaseLiveData<UnifiedConfidence> liveData =
            new FirebaseLiveData<>(unifiedConfidenceRef);
      liveData.setEventListener(new ValueEventListener() {
         public void onDataChange(@NonNull DataSnapshot snapshot) {
            UnifiedConfidence value = (UnifiedConfidence)snapshot.getValue(UnifiedConfidence.class);
            liveData.setValue(value);
         }

         public void onCancelled(@NonNull DatabaseError error) {
            Log.e("FirebaseRepository", "Unified confidence read failed", error.toException());
         }
      });
      return liveData;
   }

   public Task<Void> saveGardenEvent(GardenEvent event) {
      if (event == null || event.getId().isBlank()) {
         return Tasks.forException(new IllegalArgumentException("Journal event id is required"));
      }
      if (event.getSeason_id() != null && !event.getSeason_id().isBlank()) {
         return this.journalEventsRef.child(event.getId()).setValue(event);
      }
      return seasonRepository.requireActiveSeasonId(event.getZone_id()).continueWithTask(task -> {
         if (!task.isSuccessful()) return Tasks.forException(task.getException());
         event.setSeason_id(task.getResult());
         return this.journalEventsRef.child(event.getId()).setValue(event);
      });
   }

   public Task<Void> deleteGardenEvent(String eventId) {
      return eventId != null && !eventId.isBlank() ? this.journalEventsRef.child(eventId).removeValue() : Tasks.forResult(null);
   }

   public Task<Void> saveSeasonOutcome(SeasonOutcome outcome) {
      if (outcome == null || outcome.getId().isBlank()) {
         return Tasks.forException(new IllegalArgumentException("Season outcome id is required"));
      }
      if (!outcome.getSeason_id().isBlank()) {
         return this.seasonOutcomesRef.child(outcome.getId()).setValue(outcome);
      }
      return seasonRepository.requireActiveSeasonId(outcome.getZone_id()).continueWithTask(task -> {
         if (!task.isSuccessful()) return Tasks.forException(task.getException());
         outcome.setSeason_id(task.getResult());
         return this.seasonOutcomesRef.child(outcome.getId()).setValue(outcome);
      });
   }

   public Task<Void> saveGardenPhotoMetadata(GardenPhoto photo) {
      if (photo != null && photo.getId() != null && !photo.getId().isBlank()) {
         Task<String> seasonTask;
         if (photo.getSeason_id() != null && !photo.getSeason_id().isBlank()) {
            seasonTask = Tasks.forResult(photo.getSeason_id());
         } else {
            seasonTask = seasonRepository.requireActiveSeasonId(photo.getZone_id());
         }
         return seasonTask.continueWithTask(task -> {
            if (!task.isSuccessful()) return Tasks.forException(task.getException());
            if (photo.getSeason_id() == null || photo.getSeason_id().isBlank()) {
               photo.setSeason_id(task.getResult());
            }
            Map<String, Object> values = new HashMap<>();
            values.put("id", photo.getId());
            values.put("zone_id", photo.getZone_id());
            values.put("season_id", photo.getSeason_id());
            values.put("note", photo.getNote() == null ? "" : photo.getNote());
            values.put("related_application_id", photo.getRelated_application_id() == null ? "" : photo.getRelated_application_id());
            values.put("analysis_title", photo.getAnalysis_title() == null ? "" : photo.getAnalysis_title());
            values.put("analysis_meta", photo.getAnalysis_meta() == null ? "" : photo.getAnalysis_meta());
             values.put("analysis_context", photo.getAnalysis_context() == null ? "" : photo.getAnalysis_context());
             values.put("analysis_advice", photo.getAnalysis_advice() == null ? "" : photo.getAnalysis_advice());
             values.put("analysis_goal", photo.getAnalysis_goal() == null ? "" : photo.getAnalysis_goal());
             values.put("analysis_confidence", photo.getAnalysis_confidence());
             values.put("growth_score", photo.getGrowth_score());
             values.put("growth_stage", photo.getGrowth_stage() == null ? "" : photo.getGrowth_stage());
             values.put("growth_trend", photo.getGrowth_trend() == null ? "" : photo.getGrowth_trend());
             values.put("growth_score_delta", photo.getGrowth_score_delta());
             values.put("growth_signals", photo.getGrowth_signals() == null ? "" : photo.getGrowth_signals());
             values.put("growth_previous_captured_at_epoch", photo.getGrowth_previous_captured_at_epoch());
             values.put("captured_at_epoch", photo.getCaptured_at_epoch());
            values.put("photo_kept_on_owner_phone", true);
            values.put("metadata_updated_at_epoch", System.currentTimeMillis() / 1000L);
            return this.journalPhotoMetadataRef.child(photo.getId()).setValue(values);
         });
      } else {
         return Tasks.forException(new IllegalArgumentException("Photo id is required"));
      }
   }

   public Task<Void> deleteGardenPhotoMetadata(String photoId) {
      return photoId != null && !photoId.isBlank() ? this.journalPhotoMetadataRef.child(photoId).removeValue() : Tasks.forException(new IllegalArgumentException("Photo id is required"));
   }

   public Task<Void> saveGardenNotification(GardenNotification notification) {
      if (notification == null || notification.getId().isBlank()) {
         return Tasks.forException(new IllegalArgumentException("Notification id is required"));
      }
      if (notification.getZone_id().isBlank() || !notification.getSeason_id().isBlank()) {
         return this.notificationsRef.child(notification.getId()).setValue(notification);
      }
      return seasonRepository.requireActiveSeasonId(notification.getZone_id()).continueWithTask(task -> {
         if (!task.isSuccessful()) return Tasks.forException(task.getException());
         notification.setSeason_id(task.getResult());
         return this.notificationsRef.child(notification.getId()).setValue(notification);
      });
   }

   public Task<Void> deleteGardenNotificationsWithTombstones(
           List<GardenNotification> values
   ) {
      if (values == null || values.isEmpty()) {
         return Tasks.forResult(null);
      }

      Map<String, Object> updates = new HashMap<>();

      long deletedAtEpoch =
              System.currentTimeMillis() / 1000L;

      for (GardenNotification value : values) {

         if (value == null
                 || value.getId() == null
                 || value.getId().isBlank()) {
            continue;
         }

         String id = value.getId();
         String sourceKey =
                 value.getSource_key() == null
                         ? ""
                         : value.getSource_key();

         // Gerçek notification kaydını sil.
         updates.put(
                 "notifications/" + id,
                 null
         );

         // Aynı işlem içinde açık silme kaydı oluştur.
         updates.put(
                 "notification_deletions/"
                         + id
                         + "/source_key",
                 sourceKey
         );

         updates.put(
                 "notification_deletions/"
                         + id
                         + "/deleted_at_epoch",
                 deletedAtEpoch
         );
      }

      if (updates.isEmpty()) {
         return Tasks.forResult(null);
      }

      return deviceRef.updateChildren(updates);
   }

   public LiveData<Map<String, String>>
   observeGardenNotificationDeletions() {

      FirebaseLiveData<Map<String, String>> live =
              new FirebaseLiveData<>(notificationDeletionsRef);

      live.setEventListener(
              new ValueEventListener() {

                 @Override
                 public void onDataChange(
                         @NonNull DataSnapshot snapshot
                 ) {

                    Map<String, String> values =
                            new HashMap<>();

                    for (DataSnapshot child
                            : snapshot.getChildren()) {

                       String id = child.getKey();

                       if (id == null || id.isBlank()) {
                          continue;
                       }

                       String sourceKey =
                               child.child("source_key")
                                       .getValue(String.class);

                       values.put(
                               id,
                               sourceKey == null
                                       ? ""
                                       : sourceKey
                       );
                    }

                    live.postValue(values);
                 }

                 @Override
                 public void onCancelled(
                         @NonNull DatabaseError error
                 ) {
                    Log.w(
                            TAG,
                            "Notification deletion observation failed",
                            error.toException()
                    );
                 }
              }
      );

      return live;
   }

   public Task<Void> updateGardenNotificationState(String id, boolean read, boolean saved) {
      if (id != null && !id.isBlank()) {
         Map<String, Object> values = new HashMap<>();
         values.put("read", read);
         values.put("saved", saved);
         return this.notificationsRef.child(id).updateChildren(values);
      } else {
         return Tasks.forResult(null);
      }
   }

   public LiveData<List<GardenNotification>> observeGardenNotifications() {
      final FirebaseLiveData<List<GardenNotification>> live =
            new FirebaseLiveData<>(notificationsRef);
      live.setEventListener(new ValueEventListener() {
         public void onDataChange(@NonNull DataSnapshot snapshot) {
            List<GardenNotification> values = new ArrayList<>();

            for(DataSnapshot child : snapshot.getChildren()) {
               GardenNotification value = (GardenNotification)child.getValue(GardenNotification.class);
               if (value != null) {
                  values.add(value);
               }
            }

            values.sort((a, b) -> Long.compare(b.getCreated_at_epoch(), a.getCreated_at_epoch()));
            live.postValue(values);
         }

         public void onCancelled(@NonNull DatabaseError error) {
         }
      });
      return live;
   }

   public void loadGardenNotifications(final Consumer<List<GardenNotification>> consumer) {
      this.notificationsRef.addListenerForSingleValueEvent(new ValueEventListener() {
         public void onDataChange(@NonNull DataSnapshot snapshot) {
            List<GardenNotification> values = new ArrayList<>();

            for(DataSnapshot child : snapshot.getChildren()) {
               GardenNotification value = (GardenNotification)child.getValue(GardenNotification.class);
               if (value != null && !value.getId().isBlank()) {
                  values.add(value);
               }
            }

            values.sort((a, b) -> Long.compare(b.getCreated_at_epoch(), a.getCreated_at_epoch()));
            consumer.accept(values);
         }

         public void onCancelled(@NonNull DatabaseError error) {
            consumer.accept(new ArrayList<>());
         }
      });
   }

   public Task<Void> saveNotificationSettings(Map<String, Object> values) {
      return values != null && !values.isEmpty() ? this.notificationSettingsRef.setValue(values) : Tasks.forResult(null);
   }

   public void loadNotificationSettings(final Consumer<Map<String, Object>> consumer) {
      this.notificationSettingsRef.addListenerForSingleValueEvent(new ValueEventListener() {
         public void onDataChange(@NonNull DataSnapshot snapshot) {
            Map<String, Object> values = new HashMap<>();

            for(DataSnapshot child : snapshot.getChildren()) {
               values.put(child.getKey(), child.getValue());
            }

            consumer.accept(values);
         }

         public void onCancelled(@NonNull DatabaseError error) {
         }
      });
   }

   public Task<Void> saveFertilizationPreferences(Map<String, Object> values) {
      return values != null && !values.isEmpty()
              ? this.deviceRef.child("settings").child("fertilization").setValue(values)
              : Tasks.forResult(null);
   }

   public void loadFertilizationPreferences(final Consumer<Map<String, Object>> consumer) {
      this.deviceRef.child("settings").child("fertilization")
              .addListenerForSingleValueEvent(new ValueEventListener() {
         public void onDataChange(@NonNull DataSnapshot snapshot) {
            Map<String, Object> values = new HashMap<>();
            for (DataSnapshot child : snapshot.getChildren()) {
               values.put(child.getKey(), child.getValue());
            }
            consumer.accept(values);
         }

         public void onCancelled(@NonNull DatabaseError error) {
            consumer.accept(new HashMap<>());
         }
      });
   }
   public Task<Void> savePushToken(Context context, String token) {
      if (token == null || token.isBlank()) {
         return Tasks.forException(
                 new IllegalArgumentException("Push token is required")
         );
      }

      Map<String, Object> values = new HashMap<>();
      values.put("token", token);
      values.put("updated_at_epoch", ServerValue.TIMESTAMP);
      values.put("platform", "android");

      String key = stableDeviceKey(context);

      return this.pushTokensRef
              .child(key)
              .setValue(values);
   }

   /** Persists structured feedback without exposing Firebase primitives to the UI layer. */
   public Task<Void> submitFeedback(Map<String, Object> values) {
      if (values == null) {
         return Tasks.forException(new IllegalArgumentException("Feedback is required"));
      }
      Map<String, Object> payload = new HashMap<>(values);
      String feedbackId = UUID.randomUUID().toString();
      payload.put("id", feedbackId);
      payload.put("status", "new");
      payload.put("created_at", ServerValue.TIMESTAMP);
      payload.put("device_id", DEVICE_ID);
      payload.put("source", "android");
      FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
      if (user == null) {
         return Tasks.forException(
               new IllegalStateException("Authenticated Firebase user is required")
         );
      }
      payload.put("user_id", user.getUid());
      return deviceRef.child("user_feedback").child(feedbackId).setValue(payload);
   }

   @SuppressLint("HardwareIds")
   private static String stableDeviceKey(Context context) {
      try {
         String androidId = Settings.Secure.getString(
                 context.getContentResolver(),
                 Settings.Secure.ANDROID_ID
         );

         if (androidId == null || androidId.isBlank()) {
            throw new IllegalStateException("ANDROID_ID unavailable");
         }

         byte[] digest = MessageDigest
                 .getInstance("SHA-256")
                 .digest(androidId.getBytes(StandardCharsets.UTF_8));

         StringBuilder key = new StringBuilder("android_");

         for (byte value : digest) {
            key.append(String.format("%02x", value));
         }

         return key.toString();

      } catch (Exception e) {
         return "android_device_unknown";
      }
   }

   public Task<Void> saveGardenProfile(GardenProfile profile) {
      Map<String, Object> values = new HashMap<>();
      values.put("profile/garden_name", profile.getGarden_name());
      values.put("profile/garden_type", profile.getGarden_type());
      values.put("profile/area_square_meters", profile.getArea_square_meters());
      values.put("profile/notes", profile.getNotes());
      values.put("profile/updated_at_epoch", profile.getUpdated_at_epoch());
      return this.deviceRef.updateChildren(values);
   }

   public LiveData<GardenProfile> observeGardenProfile() {
      DatabaseReference profileRef = deviceRef.child("profile");
      FirebaseLiveData<GardenProfile> liveData = new FirebaseLiveData<>(profileRef);
      liveData.setEventListener(new ValueEventListener() {
         public void onDataChange(@NonNull DataSnapshot snapshot) {
            liveData.setValue(snapshot.getValue(GardenProfile.class));
         }

         public void onCancelled(@NonNull DatabaseError error) {
            Log.w(TAG, "Garden profile could not be read", error.toException());
         }
      });
      return liveData;
   }

   public Task<Void> saveDisplayUnitSettings(DisplayUnitSettings settings) {
      Map<String, Object> values = new HashMap<>();
      values.put("profile/display_units/temperature", settings.getTemperature());
      values.put("profile/display_units/area", settings.getArea());
      values.put("profile/display_units/length", settings.getLength());
      values.put("profile/display_units/volume", settings.getVolume());
      values.put("profile/display_units/weight", settings.getWeight());
      values.put("profile/display_units/updated_at_epoch", System.currentTimeMillis() / 1000L);
      return this.deviceRef.updateChildren(values);
   }

   public LiveData<DisplayUnitSettings> observeDisplayUnitSettings() {
      DatabaseReference displayUnitsRef = deviceRef.child("profile").child("display_units");
      FirebaseLiveData<DisplayUnitSettings> liveData = new FirebaseLiveData<>(displayUnitsRef);
      liveData.setEventListener(new ValueEventListener() {
               public void onDataChange(@NonNull DataSnapshot snapshot) {
                  liveData.setValue(snapshot.getValue(DisplayUnitSettings.class));
               }

               public void onCancelled(@NonNull DatabaseError error) {
                  Log.w(TAG, "Display units could not be read", error.toException());
               }
            });
      return liveData;
   }

}
