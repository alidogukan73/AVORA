package com.alidogukan.avora.viewmodels;

import android.app.Application;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.alidogukan.avora.firebase.FirebaseRepository;
import com.alidogukan.avora.R;
import com.alidogukan.avora.journal.LocalGardenEventStore;
import com.alidogukan.avora.models.GardenEvent;
import com.alidogukan.avora.models.GardenPhoto;
import com.alidogukan.avora.models.GardenSeason;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.WeatherForecast;
import com.alidogukan.avora.notifications.GardenNotificationManager;
import com.alidogukan.avora.photos.LocalGardenPhotoStore;
import com.alidogukan.avora.plantassistant.PlantAssistantAdvisor;
import com.alidogukan.avora.plantassistant.PlantAssistantRecommendationStore;
import com.alidogukan.avora.plantassistant.PlantAssistantResult;
import com.alidogukan.avora.plantassistant.PlantAssistantVisionClient;
import com.alidogukan.avora.plantassistant.PlantAssistantUrgency;
import com.alidogukan.avora.plantassistant.PlantFollowUpStore;
import com.alidogukan.avora.plantassistant.PlantGrowthAssessment;
import com.alidogukan.avora.plantassistant.PlantGrowthTrendPolicy;
import com.alidogukan.avora.plantassistant.PlantPhotoDecoder;
import com.alidogukan.avora.fertilization.FertilizerDataFreshnessPolicy;
import com.alidogukan.avora.season.SeasonRepository;
import com.alidogukan.avora.season.SeasonScope;
import com.alidogukan.avora.settings.UnitPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/** Analysis and persistence boundary for the plant assistant screen. */
public final class PlantAssistantViewModel extends AndroidViewModel {
    private final FirebaseRepository repository = new FirebaseRepository();
    private final LocalGardenPhotoStore photos;
    private final PlantFollowUpStore followUps;
    private final LocalGardenEventStore events;
    private final GardenNotificationManager notifications;
    private final LiveData<List<GardenZone>> zones = repository.observeGardenZones();
    private final LiveData<List<GardenSeason>> seasons =
            new SeasonRepository().observeAllSeasons();
    private final LiveData<WeatherForecast> weather = repository.observeWeatherForecast();
    private final LiveData<List<GardenPhoto>> photoMetadata =
            repository.observeGardenPhotoMetadata();
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService storageExecutor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<Boolean> analysisInProgress =
            new MutableLiveData<>(false);
    private final Object analysisStateLock = new Object();
    private boolean analysisRunning;
    private int pendingAnalysisOperations;
    private volatile boolean cleared;

    public PlantAssistantViewModel(@NonNull Application application) {
        super(application);
        photos = new LocalGardenPhotoStore(application);
        followUps = new PlantFollowUpStore(application);
        events = new LocalGardenEventStore(application);
        notifications = new GardenNotificationManager(application);
    }

    public LiveData<List<GardenZone>> getZones() { return zones; }
    public LiveData<List<GardenSeason>> getSeasons() { return seasons; }
    public LiveData<WeatherForecast> getWeather() { return weather; }
    public LiveData<List<GardenPhoto>> getPhotoMetadata() { return photoMetadata; }
    private final MutableLiveData<GardenPhoto> completedAnalysisPhoto = new MutableLiveData<>();
    public LiveData<GardenPhoto> getCompletedAnalysisPhoto() { return completedAnalysisPhoto; }
    public void consumeCompletedAnalysisPhoto() { completedAnalysisPhoto.setValue(null); }
    public LiveData<Boolean> getAnalysisInProgress() { return analysisInProgress; }

    public boolean tryBeginAnalysis(int operationCount) {
        if (operationCount <= 0) throw new IllegalArgumentException("operationCount");
        synchronized (analysisStateLock) {
            if (cleared || analysisRunning) return false;
            analysisRunning = true;
            pendingAnalysisOperations = operationCount;
        }
        analysisInProgress.setValue(true);
        return true;
    }

    public void finishAnalysis() {
        synchronized (analysisStateLock) {
            if (!analysisRunning) return;
            analysisRunning = false;
            pendingAnalysisOperations = 0;
        }
        analysisInProgress.setValue(false);
    }

    private void finishAnalysisOperation() {
        boolean finished = false;
        synchronized (analysisStateLock) {
            if (!analysisRunning) return;
            pendingAnalysisOperations--;
            if (pendingAnalysisOperations <= 0) {
                pendingAnalysisOperations = 0;
                analysisRunning = false;
                finished = true;
            }
        }
        if (finished) analysisInProgress.postValue(false);
    }

    /** Releases the finalization slot when a failed analysis must not alter journal metadata. */
    public void skipAnalysisFinalization() {
        finishAnalysisOperation();
    }

    public List<GardenZone> activeZones(List<GardenZone> values) {
        return SeasonScope.activeSeasonZones(values);
    }

    public boolean isSensorDataCurrent(GardenZone zone) {
        long nowEpoch = System.currentTimeMillis() / 1000L;
        return zone != null && zone.hasSensorData()
                && FertilizerDataFreshnessPolicy.isSensorFresh(zone, nowEpoch);
    }

    public WeatherForecast currentWeatherOrNull(WeatherForecast forecast) {
        long nowEpoch = System.currentTimeMillis() / 1000L;
        return forecast != null
                && FertilizerDataFreshnessPolicy.isWeatherFresh(forecast, nowEpoch)
                ? forecast : null;
    }

    public PlantAssistantResult assess(GardenZone zone, List<String> symptoms,
                                       String note, WeatherForecast weather,
                                       boolean hasPhoto, boolean growthStatusRequested) {
        return PlantAssistantAdvisor.assess(
                zone, symptoms, note, weather, hasPhoto, growthStatusRequested,
                new UnitPreferences(getApplication()).formatter());
    }

    public void saveRecommendation(String zoneId, String seasonId, String urgency,
                                   String title, String advice) {
        PlantAssistantRecommendationStore.save(
                getApplication(), zoneId, seasonId, urgency, title, advice);
    }

    public JSONObject analyzeVision(Bitmap bitmap, JSONObject context) throws Exception {
        return PlantAssistantVisionClient.analyze(bitmap, context);
    }

    public void analyzeVisionAsync(Bitmap bitmap, Uri photoUri,
                                   GardenZone zone, String plantName, List<String> symptoms,
                                   String note, WeatherForecast forecast,
                                   boolean growthStatusRequested,
                                   Consumer<JSONObject> success, Consumer<Throwable> failure) {
        executeSafely(analysisExecutor, () -> {
            Bitmap image = null;
            boolean ownsImage = bitmap == null;
            try {
                image = resolvePhoto(bitmap, photoUri);
                JSONObject payload = new JSONObject();
                payload.put("plant", plantName == null || plantName.isBlank()
                        ? zone.getName() : plantName);
                payload.put("zone", zone.getZone_id());
                boolean sensorCurrent = isSensorDataCurrent(zone);
                payload.put("sensor_data_current", sensorCurrent);
                payload.put("moisture", sensorCurrent ? zone.getMoisture() : JSONObject.NULL);
                payload.put("moisture_limit", zone.getMoisture_limit());
                JSONArray symptomValues = new JSONArray();
                for (String symptom : symptoms) symptomValues.put(symptom);
                payload.put("symptoms", symptomValues);
                payload.put("analysis_goal",
                        growthStatusRequested ? "growth_status" : "health_screening");
                payload.put("note", note);
                WeatherForecast trustedForecast = currentWeatherOrNull(forecast);
                if (trustedForecast != null) {
                    payload.put("temperature", trustedForecast.getCurrentTemperature());
                    payload.put("humidity", trustedForecast.getCurrentHumidity());
                    payload.put("rain_probability", trustedForecast.getTodayRainProbability());
                }
                success.accept(PlantAssistantVisionClient.analyze(image, payload));
            } catch (Exception error) {
                failure.accept(error);
            } finally {
                if (ownsImage && image != null && !image.isRecycled()) image.recycle();
                finishAnalysisOperation();
            }
        }, error -> {
            try {
                failure.accept(error);
            } finally {
                finishAnalysisOperation();
            }
        });
    }

    private Bitmap resolvePhoto(Bitmap bitmap, Uri photoUri) throws Exception {
        if (bitmap != null) return bitmap;
        if (photoUri == null) throw new IllegalStateException("PHOTO_DECODE_FAILED");
        return PlantPhotoDecoder.decode(
                getApplication().getContentResolver(), photoUri, 1600);
    }

    public void loadPhotoPreviewAsync(Uri photoUri,
                                      Consumer<Bitmap> success,
                                      Consumer<Throwable> failure) {
        executeSafely(storageExecutor, () -> {
            try {
                success.accept(PlantPhotoDecoder.decode(
                        getApplication().getContentResolver(), photoUri, 1200));
            } catch (Exception error) {
                failure.accept(error);
            }
        }, failure);
    }

    public String list(JSONArray values) { return PlantAssistantVisionClient.list(values); }

    public GardenPhoto archivePhoto(Uri uri, Bitmap bitmap, String zoneId,
                                    String seasonId, String note) throws Exception {
        GardenPhoto photo = uri != null
                ? photos.save(uri, zoneId, note, "plant_assistant")
                : bitmap != null
                ? photos.save(bitmap, zoneId, note, "plant_assistant") : null;
        if (photo != null && seasonId != null && !seasonId.isBlank()) {
            photo.setSeason_id(seasonId);
            photos.updateSeasonId(photo.getId(), seasonId);
        }
        return photo;
    }

    public void archivePhotoAsync(Uri uri, Bitmap bitmap, String zoneId, String seasonId, String note,
                                  Consumer<GardenPhoto> success,
                                  Consumer<Throwable> failure) {
        executeSafely(storageExecutor, () -> {
            try {
                success.accept(archivePhoto(uri, bitmap, zoneId, seasonId, note));
            } catch (Exception error) {
                failure.accept(error);
            } finally {
                finishAnalysisOperation();
            }
        }, error -> {
            try {
                failure.accept(error);
            } finally {
                finishAnalysisOperation();
            }
        });
    }

    public void finalizeAnalysisAsync(String photoId, String zoneId, String seasonId, String title,
                                      String meta, String context, String advice,
                                      String analysisGoal, int confidence,
                                      String urgency,
                                      PlantGrowthAssessment growth,
                                      Consumer<Boolean> completion,
                                      Consumer<Throwable> syncFailure) {
        executeSafely(storageExecutor, () -> {
            boolean saved = false;
            GardenPhoto completedPhoto = null;
            try {
                if (!hasLocalPhoto(photoId)) {
                    throw new IllegalStateException("PHOTO_METADATA_NOT_FOUND");
                }
                boolean actionable = "health_screening".equals(analysisGoal)
                        || "growth_status".equals(analysisGoal);
                FollowUpResult followUp = actionable
                        ? registerFollowUp(zoneId, seasonId, photoId, title)
                        : new FollowUpResult("NONE", "");
                String contextText = context;
                if ("SCHEDULED".equals(followUp.type)
                        || "SCHEDULED_EXISTING".equals(followUp.type)) {
                    contextText += "\n\n" + getApplication().getString(
                            R.string.runtime_follow_up_task);
                } else if ("COMPLETED".equals(followUp.type)) {
                    contextText += "\n\n" + getApplication().getString(
                            R.string.runtime_follow_up_comparison, followUp.previousTitle)
                            + "\n" + getApplication().getString(
                            R.string.runtime_follow_up_task);
                }
                GardenPhoto updated = updateAnalysis(photoId, title, meta, contextText, advice,
                        analysisGoal, confidence, growth);
                if (updated == null) {
                    throw new IllegalStateException("PHOTO_METADATA_NOT_FOUND");
                }
                if (actionable) {
                    PlantAssistantRecommendationStore.save(
                            getApplication(), zoneId, seasonId, urgency, title, advice, photoId);
                }
                syncPhoto(updated, syncFailure);
                if (actionable) {
                    publish(isHighUrgency(urgency) ? "HIGH" : "NORMAL",
                            zoneId, seasonId, title,
                            getApplication().getString(
                                    R.string.notification_plant_analysis_saved_description),
                            "plant_analysis:" + photoId);
                }
                if ("SCHEDULED".equals(followUp.type)) {
                    addFollowUpEvent(zoneId, seasonId,
                            getApplication().getString(R.string.runtime_follow_up_photo_title),
                            getApplication().getString(R.string.runtime_follow_up_photo_note),
                            "follow_up_" + photoId);
                } else if ("COMPLETED".equals(followUp.type)) {
                    addFollowUpEvent(zoneId, seasonId,
                            getApplication().getString(R.string.runtime_follow_up_assessment_title),
                            getApplication().getString(R.string.runtime_follow_up_assessment_note),
                            "follow_up_" + photoId);
                    addFollowUpEvent(zoneId, seasonId,
                            getApplication().getString(R.string.runtime_follow_up_photo_title),
                            getApplication().getString(R.string.runtime_follow_up_photo_note),
                            "follow_up_next_" + photoId);
                    publish("NORMAL", zoneId, seasonId, getApplication().getString(
                                    R.string.notification_plant_follow_up_ready_title),
                            getApplication().getString(
                                    R.string.notification_plant_follow_up_ready_description),
                            "follow_up_complete:" + photoId);
                }
                saved = true;
                completedPhoto = updated;
            } catch (Throwable error) {
                if (syncFailure != null) syncFailure.accept(error);
            } finally {
                try {
                    if (completion != null) completion.accept(saved);
                } finally {
                    finishAnalysisOperation();
                    if (completedPhoto != null) completedAnalysisPhoto.postValue(completedPhoto);
                }
            }
        }, error -> {
            try {
                if (syncFailure != null) syncFailure.accept(error);
                if (completion != null) completion.accept(false);
            } finally {
                finishAnalysisOperation();
            }
        });
    }

    private boolean hasLocalPhoto(String photoId) {
        if (photoId == null || photoId.isBlank()) return false;
        for (GardenPhoto photo : photos.load()) {
            if (photo != null && photoId.equals(photo.getId())) return true;
        }
        return false;
    }

    private void executeSafely(ExecutorService executor, Runnable task,
                               Consumer<Throwable> rejection) {
        if (cleared) return;
        try {
            executor.execute(task);
        } catch (RejectedExecutionException error) {
            if (!cleared && rejection != null) rejection.accept(error);
        }
    }

    private static boolean isHighUrgency(String urgency) {
        return PlantAssistantUrgency.isHigh(urgency);
    }

    public FollowUpResult registerFollowUp(String zoneId, String seasonId,
                                           String photoId, String title) {
        PlantFollowUpStore.Result value = followUps.registerAnalysis(zoneId, seasonId, photoId, title);
        return new FollowUpResult(value.type, value.previousTitle);
    }

    public GardenPhoto updateAnalysis(String photoId, String title, String meta,
                                      String context, String advice,
                                      String analysisGoal, int confidence,
                                      PlantGrowthAssessment growth) {
        PlantGrowthAssessment value = growth == null
                ? new PlantGrowthAssessment(-1, confidence, "", "", 0, "", 0L)
                : growth;
        return photos.updateAnalysis(photoId, title, meta, context, advice,
                analysisGoal, confidence, value.getScore(), value.getStage(),
                value.getTrend(), value.getScoreDelta(), value.getSignals(),
                value.getPreviousCapturedAtEpoch());
    }

    public PlantGrowthAssessment evaluateGrowth(String zoneId, String seasonId, String photoId,
                                                 int score, int confidence,
                                                 String stage, String signals) {
        PlantGrowthTrendPolicy.Result comparison = PlantGrowthTrendPolicy.compare(
                combinedPhotoMetadata(), zoneId, seasonId, photoId, score);
        return new PlantGrowthAssessment(score, confidence, stage, comparison.trend,
                comparison.scoreDelta, signals, comparison.previousCapturedAtEpoch);
    }

    private List<GardenPhoto> combinedPhotoMetadata() {
        Map<String, GardenPhoto> combined = new LinkedHashMap<>();
        List<GardenPhoto> cloud = photoMetadata.getValue();
        if (cloud != null) {
            for (GardenPhoto photo : cloud) {
                if (photo != null && photo.getId() != null) combined.put(photo.getId(), photo);
            }
        }
        for (GardenPhoto local : photos.load()) {
            if (local == null || local.getId() == null) continue;
            combined.putIfAbsent(local.getId(), local);
        }
        return new ArrayList<>(combined.values());
    }

    public void syncPhoto(GardenPhoto photo, Consumer<Throwable> failure) {
        if (photo == null) return;
        repository.saveGardenPhotoMetadata(photo).addOnSuccessListener(unused ->
                photos.updateSeasonId(photo.getId(), photo.getSeason_id()))
                .addOnFailureListener(error -> {
                    if (failure != null) failure.accept(error);
                });
    }

    public void publish(String priority, String zoneId, String seasonId, String title,
                        String description, String sourceKey) {
        notifications.publishOnce("PLANT_ASSISTANT", priority, zoneId, seasonId,
                title, description, sourceKey);
    }

    public void addFollowUpEvent(String zoneId, String seasonId,
                                 String type, String note, String sourceKey) {
        GardenEvent event = events.addAutomaticOncePerDay(zoneId, type, note, sourceKey);
        if (event != null && seasonId != null && !seasonId.isBlank()) {
            event.setSeason_id(seasonId);
            events.replaceSeasonId(event.getId(), seasonId);
        }
        if (event != null) repository.saveGardenEvent(event);
    }

    public static final class FollowUpResult {
        public final String type;
        public final String previousTitle;

        FollowUpResult(String type, String previousTitle) {
            this.type = type == null ? "" : type;
            this.previousTitle = previousTitle == null ? "" : previousTitle;
        }
    }

    @Override protected void onCleared() {
        cleared = true;
        synchronized (analysisStateLock) {
            analysisRunning = false;
            pendingAnalysisOperations = 0;
        }
        analysisExecutor.shutdownNow();
        storageExecutor.shutdownNow();
    }
}
