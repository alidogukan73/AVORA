package com.alidogukan.avora.viewmodels;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

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
import com.alidogukan.avora.plantassistant.PlantFollowUpStore;
import com.alidogukan.avora.plantassistant.PlantGrowthAssessment;
import com.alidogukan.avora.plantassistant.PlantGrowthTrendPolicy;
import com.alidogukan.avora.season.SeasonRepository;
import com.alidogukan.avora.season.SeasonScope;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

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

    public List<GardenZone> activeZones(List<GardenZone> values) {
        return SeasonScope.activeSeasonZones(values);
    }

    public PlantAssistantResult assess(GardenZone zone, List<String> symptoms,
                                       String note, WeatherForecast weather,
                                       boolean hasPhoto, boolean growthStatusRequested) {
        return PlantAssistantAdvisor.assess(
                zone, symptoms, note, weather, hasPhoto, growthStatusRequested);
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
        executor.execute(() -> {
            try {
                Bitmap image = resolvePhoto(bitmap, photoUri);
                JSONObject payload = new JSONObject();
                payload.put("plant", plantName == null || plantName.isBlank()
                        ? zone.getName() : plantName);
                payload.put("zone", zone.getZone_id());
                payload.put("moisture", zone.getMoisture());
                payload.put("moisture_limit", zone.getMoisture_limit());
                JSONArray symptomValues = new JSONArray();
                for (String symptom : symptoms) symptomValues.put(symptom);
                payload.put("symptoms", symptomValues);
                payload.put("analysis_goal",
                        growthStatusRequested ? "growth_status" : "health_screening");
                payload.put("note", note);
                if (forecast != null) {
                    payload.put("temperature", forecast.getCurrentTemperature());
                    payload.put("humidity", forecast.getCurrentHumidity());
                    payload.put("rain_probability", forecast.getTodayRainProbability());
                }
                success.accept(PlantAssistantVisionClient.analyze(image, payload));
            } catch (Exception error) {
                failure.accept(error);
            }
        });
    }

    private Bitmap resolvePhoto(Bitmap bitmap, Uri photoUri) throws Exception {
        if (bitmap != null) return bitmap;
        if (photoUri == null) throw new IllegalStateException("PHOTO_DECODE_FAILED");
        try (InputStream stream = getApplication()
                .getContentResolver().openInputStream(photoUri)) {
            Bitmap decoded = BitmapFactory.decodeStream(stream);
            if (decoded == null) throw new IllegalStateException("PHOTO_DECODE_FAILED");
            return decoded;
        }
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
        executor.execute(() -> {
            try {
                success.accept(archivePhoto(uri, bitmap, zoneId, seasonId, note));
            } catch (Exception error) {
                failure.accept(error);
            }
        });
    }

    public void finalizeAnalysisAsync(String photoId, String zoneId, String seasonId, String title,
                                      String meta, String context, String advice,
                                      String analysisGoal, int confidence,
                                      PlantGrowthAssessment growth,
                                      Consumer<Throwable> syncFailure) {
        executor.execute(() -> {
            FollowUpResult followUp = registerFollowUp(zoneId, seasonId, photoId, title);
            String contextText = context;
            if ("SCHEDULED".equals(followUp.type)
                    || "SCHEDULED_EXISTING".equals(followUp.type)) {
                contextText += "\n\n" + getApplication().getString(
                        R.string.runtime_follow_up_task);
            } else if ("COMPLETED".equals(followUp.type)) {
                contextText += "\n\n" + getApplication().getString(
                        R.string.runtime_follow_up_comparison, followUp.previousTitle);
            }
            GardenPhoto updated = updateAnalysis(photoId, title, meta, contextText, advice,
                    analysisGoal, confidence, growth);
            if (updated == null) return;
            syncPhoto(updated, syncFailure);
            publish("HIGH", zoneId, title, getApplication().getString(
                    R.string.notification_plant_analysis_saved_description),
                    "plant_analysis:" + photoId);
            if ("SCHEDULED".equals(followUp.type)) {
                addFollowUpEvent(zoneId, seasonId, "Takip fotoğrafı önerisi",
                        "Bitki Asistanı analizinden 3 gün sonra aynı bölgeden yeni fotoğraf ekleyin.",
                        "follow_up_" + photoId);
            } else if ("COMPLETED".equals(followUp.type)) {
                addFollowUpEvent(zoneId, seasonId, "Takip değerlendirmesi",
                        "Yeni analiz, önceki Bitki Asistanı analiziyle karşılaştırılmak üzere kaydedildi.",
                        "follow_up_" + photoId);
                publish("NORMAL", zoneId, getApplication().getString(
                                R.string.notification_plant_follow_up_ready_title),
                        getApplication().getString(
                                R.string.notification_plant_follow_up_ready_description),
                        "follow_up_complete:" + photoId);
            }
        });
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

    public void publish(String priority, String zoneId, String title,
                        String description, String sourceKey) {
        notifications.publishOnce("PLANT_ASSISTANT", priority, zoneId,
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
        executor.shutdown();
    }
}
