package com.alidogukan.avora.viewmodels;

import android.app.Application;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.alidogukan.avora.firebase.FirebaseRepository;
import com.alidogukan.avora.journal.LocalGardenEventStore;
import com.alidogukan.avora.journal.LocalSeasonOutcomeStore;
import com.alidogukan.avora.models.FertilizerApplication;
import com.alidogukan.avora.models.GardenEvent;
import com.alidogukan.avora.models.GardenPhoto;
import com.alidogukan.avora.models.GardenSeason;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.SeasonOutcome;
import com.alidogukan.avora.models.WateringHistory;
import com.alidogukan.avora.models.WeatherForecast;
import com.alidogukan.avora.notifications.LocalGardenNotificationStore;
import com.alidogukan.avora.photos.LocalGardenPhotoStore;
import com.alidogukan.avora.season.SeasonRecordPolicy;
import com.alidogukan.avora.season.SeasonRepository;
import com.alidogukan.avora.season.SeasonScope;
import com.alidogukan.avora.models.ZoneSeasonState;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Shared local/cloud consistency boundary for plant journal screens. */
public final class PlantJournalViewModel extends AndroidViewModel {
    private final FirebaseRepository repository = new FirebaseRepository();
    private final SeasonRepository seasons = new SeasonRepository();
    private final LocalGardenEventStore events;
    private final LocalGardenPhotoStore photos;
    private final LocalSeasonOutcomeStore outcomes;
    private final LocalGardenNotificationStore notifications;
    private final LiveData<List<GardenZone>> zones = repository.observeGardenZones();
    private final LiveData<List<FertilizerApplication>> fertilizerHistory =
            repository.observeFertilizerHistory();
    private final LiveData<List<WateringHistory>> wateringHistory =
            repository.observeWateringHistory();
    private final LiveData<WeatherForecast> weather = repository.observeWeatherForecast();

    public PlantJournalViewModel(@NonNull Application application) {
        super(application);
        events = new LocalGardenEventStore(application);
        photos = new LocalGardenPhotoStore(application);
        outcomes = new LocalSeasonOutcomeStore(application);
        notifications = new LocalGardenNotificationStore(application);
    }

    public LiveData<List<GardenZone>> getZones() { return zones; }
    public LiveData<List<FertilizerApplication>> getFertilizerHistory() {
        return fertilizerHistory;
    }
    public LiveData<List<WateringHistory>> getWateringHistory() { return wateringHistory; }
    public LiveData<WeatherForecast> getWeather() { return weather; }
    public LiveData<List<GardenSeason>> getSeasons(String zoneId) {
        return seasons.observeZoneSeasons(zoneId);
    }
    public List<GardenSeason> visibleSeasons(List<GardenSeason> values,
                                             ZoneSeasonState current) {
        List<GardenSeason> result = new ArrayList<>();
        if (values == null) return result;
        for (GardenSeason value : values) {
            if (SeasonScope.isVisibleSeason(value, current)) result.add(value);
        }
        return result;
    }
    public boolean belongsToSeason(String seasonId, long occurredAt, GardenSeason selected) {
        if (selected == null) return false;
        ZoneSeasonState scope = new ZoneSeasonState();
        scope.setActive_season_id(selected.getSeason_id());
        scope.setStatus(selected.getStatus());
        scope.setStarted_at_epoch(selected.getStarted_at_epoch());
        scope.setEnded_at_epoch(selected.getEnded_at_epoch());
        scope.setInclude_legacy_records(selected.isIncludes_legacy_records());
        return SeasonScope.belongsTo(seasonId, occurredAt, scope);
    }
    public Task<String> requireActiveSeasonId(String zoneId) {
        return seasons.requireActiveSeasonId(zoneId);
    }

    public Task<SeasonDeletionStatus> inspectEmptySeason(
            String zoneId,
            String seasonId
    ) {
        LocalSeasonRecords local = localSeasonRecords(seasonId);
        return seasons.inspectEmptySeason(
                zoneId,
                seasonId,
                local.photoIds
        ).continueWith(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                Exception error = task.getException();
                if (error != null) throw error;
                throw new IllegalStateException("Sezon kontrol edilemedi.");
            }
            return SeasonDeletionStatus.from(
                    task.getResult().withLocalRecords(
                            local.journalCount,
                            local.photoIds.size(),
                            local.meaningfulOutcome
                    )
            );
        });
    }

    public Task<Void> deleteEmptySeason(String zoneId, String seasonId) {
        LocalSeasonRecords local = localSeasonRecords(seasonId);
        if (local.journalCount > 0
                || !local.photoIds.isEmpty()
                || local.meaningfulOutcome) {
            return Tasks.forException(new IllegalStateException(
                    "Bu sezonda telefonda kayıt bulunduğu için sezon silinemez."));
        }
        return seasons.deleteEmptySeason(zoneId, seasonId, local.photoIds)
                .addOnSuccessListener(ignored -> {
                    events.removeGeneratedBySeason(seasonId);
                    outcomes.removeBySeason(seasonId);
                    notifications.removeBySeason(seasonId);
                });
    }

    public Task<SeasonDeletionStatus>
    cleanupMissingPhotoRecords(String zoneId, String seasonId) {
        LocalSeasonRecords local = localSeasonRecords(seasonId);
        return seasons.inspectEmptySeason(
                zoneId,
                seasonId,
                local.photoIds
        ).continueWithTask(inspect -> {
            if (!inspect.isSuccessful() || inspect.getResult() == null) {
                Exception error = inspect.getException();
                return Tasks.forException(error == null
                        ? new IllegalStateException("Sezon kontrol edilemedi.")
                        : error);
            }
            Set<String> missingIds = inspect.getResult().getMissingPhotoIds();
            if (missingIds.isEmpty()) {
                return Tasks.forException(new IllegalStateException(
                        "Temizlenecek eksik fotoğraf kaydı bulunamadı."));
            }
            return seasons.cleanupMissingPhotoRecords(
                    zoneId,
                    seasonId,
                    local.photoIds
            ).continueWith(cleanup -> {
                if (!cleanup.isSuccessful() || cleanup.getResult() == null) {
                    Exception error = cleanup.getException();
                    if (error != null) throw error;
                    throw new IllegalStateException(
                            "Eksik fotoğraf kayıtları temizlenemedi.");
                }
                events.removeGeneratedForPhotos(seasonId, missingIds);
                notifications.removeForPhotos(seasonId, missingIds);
                LocalSeasonRecords after = localSeasonRecords(seasonId);
                return SeasonDeletionStatus.from(
                        cleanup.getResult().withLocalRecords(
                                after.journalCount,
                                after.photoIds.size(),
                                after.meaningfulOutcome
                        )
                );
            });
        });
    }

    public static final class SeasonDeletionStatus {
        private final boolean canDelete;
        private final boolean canCleanMissingPhotos;
        private final String reason;
        private final int wateringCount;
        private final int fertilizerCount;
        private final int journalCount;
        private final int localPhotoCount;
        private final int missingPhotoCount;
        private final int missingPhotoAnalysisCount;
        private final boolean meaningfulOutcome;

        private SeasonDeletionStatus(
                boolean canDelete,
                boolean canCleanMissingPhotos,
                String reason,
                int wateringCount,
                int fertilizerCount,
                int journalCount,
                int localPhotoCount,
                int missingPhotoCount,
                int missingPhotoAnalysisCount,
                boolean meaningfulOutcome
        ) {
            this.canDelete = canDelete;
            this.canCleanMissingPhotos = canCleanMissingPhotos;
            this.reason = reason == null ? "" : reason;
            this.wateringCount = wateringCount;
            this.fertilizerCount = fertilizerCount;
            this.journalCount = journalCount;
            this.localPhotoCount = localPhotoCount;
            this.missingPhotoCount = missingPhotoCount;
            this.missingPhotoAnalysisCount = missingPhotoAnalysisCount;
            this.meaningfulOutcome = meaningfulOutcome;
        }

        private static SeasonDeletionStatus from(
                SeasonRepository.EmptySeasonDeletionStatus source
        ) {
            return new SeasonDeletionStatus(
                    source.canDelete(),
                    source.canCleanMissingPhotos(),
                    source.getReason(),
                    source.getWateringCount(),
                    source.getFertilizerCount(),
                    source.getJournalCount(),
                    source.getLocalPhotoCount(),
                    source.getMissingPhotoCount(),
                    source.getMissingPhotoAnalysisCount(),
                    source.hasMeaningfulOutcome()
            );
        }

        public boolean canDelete() { return canDelete; }
        public boolean canCleanMissingPhotos() { return canCleanMissingPhotos; }
        public String getReason() { return reason; }
        public int getWateringCount() { return wateringCount; }
        public int getFertilizerCount() { return fertilizerCount; }
        public int getJournalCount() { return journalCount; }
        public int getLocalPhotoCount() { return localPhotoCount; }
        public int getMissingPhotoCount() { return missingPhotoCount; }
        public int getMissingPhotoAnalysisCount() {
            return missingPhotoAnalysisCount;
        }
        public boolean hasMeaningfulOutcome() { return meaningfulOutcome; }
    }

    private LocalSeasonRecords localSeasonRecords(String seasonId) {
        LocalSeasonRecords result = new LocalSeasonRecords();
        if (seasonId == null || seasonId.isBlank()) {
            result.journalCount = 1;
            return result;
        }
        for (GardenEvent event : events.load()) {
            if (event == null || !seasonId.equals(event.getSeason_id())) continue;
            if (SeasonRecordPolicy.isFieldJournalEvent(
                    event.getType(),
                    event.getSource(),
                    event.getSource_key())) {
                result.journalCount++;
            }
        }
        for (GardenPhoto photo : photos.load()) {
            if (photo != null
                    && seasonId.equals(photo.getSeason_id())
                    && photo.getId() != null
                    && !photo.getId().isBlank()) {
                result.photoIds.add(photo.getId());
            }
        }
        for (SeasonOutcome outcome : outcomes.load()) {
            if (outcome == null
                    || (!seasonId.equals(outcome.getSeason_id())
                    && !seasonId.equals(outcome.getId()))) {
                continue;
            }
            if (SeasonRecordPolicy.hasMeaningfulOutcome(outcome)) {
                result.meaningfulOutcome = true;
            }
        }
        return result;
    }

    private static final class LocalSeasonRecords {
        int journalCount;
        final Set<String> photoIds = new LinkedHashSet<>();
        boolean meaningfulOutcome;
    }

    public List<GardenEvent> loadEvents() { return events.load(); }
    public List<GardenPhoto> loadPhotos() { return photos.load(); }

    public Task<Void> persistRecord(String zoneId, String seasonId, String type,
                                    String note, long occurredAtEpoch,
                                    String relatedApplicationId,
                                    List<Uri> selectedPhotos,
                                    List<Bitmap> selectedBitmaps) {
        List<Task<?>> writes = new ArrayList<>();
        try {
            boolean hasPhoto = (selectedPhotos != null && !selectedPhotos.isEmpty())
                    || (selectedBitmaps != null && !selectedBitmaps.isEmpty());
            if (hasPhoto) {
                String groupId = relatedApplicationId == null || relatedApplicationId.isBlank()
                        ? "journal_record_" + UUID.randomUUID() : relatedApplicationId;
                if (selectedPhotos != null) {
                    for (Uri uri : selectedPhotos) {
                        writes.add(savePhoto(photos.save(uri, zoneId, note, groupId), seasonId));
                    }
                }
                if (selectedBitmaps != null) {
                    for (Bitmap bitmap : selectedBitmaps) {
                        writes.add(savePhoto(photos.save(bitmap, zoneId, note, groupId), seasonId));
                    }
                }
            } else if (!"Fotoğraf".equals(type)) {
                GardenEvent event = events.add(zoneId, type, note, occurredAtEpoch);
                event.setSeason_id(seasonId);
                events.replaceSeasonId(event.getId(), seasonId);
                writes.add(repository.saveGardenEvent(event));
            }
            return Tasks.whenAll(writes);
        } catch (Exception error) {
            return Tasks.forException(error);
        }
    }

    public String ensureJournalPhotoGroup(GardenPhoto firstPhoto, String currentGroup) {
        if (currentGroup != null && currentGroup.startsWith("journal_record_")) {
            return currentGroup;
        }
        String group = "journal_record_" + UUID.randomUUID();
        if (firstPhoto != null) photos.updateRelatedApplicationId(firstPhoto.getId(), group);
        return group;
    }

    public GardenPhoto addPhoto(Uri uri, String zoneId, String note,
                                String groupId, String seasonId) throws Exception {
        GardenPhoto photo = photos.save(uri, zoneId, note, groupId);
        savePhoto(photo, seasonId);
        return photo;
    }

    public GardenPhoto addPhoto(Bitmap bitmap, String zoneId, String note,
                                String groupId, String seasonId) throws Exception {
        GardenPhoto photo = photos.save(bitmap, zoneId, note, groupId);
        savePhoto(photo, seasonId);
        return photo;
    }

    private Task<Void> savePhoto(GardenPhoto photo, String seasonId) {
        if (seasonId != null && !seasonId.isBlank()) {
            photo.setSeason_id(seasonId);
            photos.updateSeasonId(photo.getId(), seasonId);
        }
        return repository.saveGardenPhotoMetadata(photo).addOnSuccessListener(unused ->
                photos.updateSeasonId(photo.getId(), photo.getSeason_id()));
    }

    public boolean updateEvent(String id, String zoneId, String seasonId,
                               String type, String note, long epoch) {
        if (!events.update(id, type, note)) return false;
        GardenEvent event = new GardenEvent();
        event.setId(id);
        event.setZone_id(zoneId);
        event.setSeason_id(seasonId);
        event.setType(type);
        event.setNote(note);
        event.setOccurred_at_epoch(epoch);
        repository.saveGardenEvent(event);
        return true;
    }

    public void deleteEvent(String id) {
        if (events.delete(id)) repository.deleteGardenEvent(id);
    }

    public void deletePhotoRecord(GardenPhoto selected, String groupId) {
        List<GardenPhoto> targets = new ArrayList<>();
        if (groupId != null && groupId.startsWith("journal_record_")) {
            for (GardenPhoto photo : photos.load()) {
                if (groupId.equals(photo.getRelated_application_id())) targets.add(photo);
            }
        } else if (selected != null) {
            targets.add(selected);
        }
        for (GardenPhoto photo : targets) {
            if (photos.delete(photo)) repository.deleteGardenPhotoMetadata(photo.getId());
        }
    }

    public Task<GardenEvent> addAutomaticEvent(String zoneId, String seasonId, String type,
                                                String note, String sourceKey) {
        GardenEvent event = events.addAutomaticOncePerDay(zoneId, type, note, sourceKey);
        if (event == null) return Tasks.forResult(null);
        event.setSeason_id(seasonId);
        events.replaceSeasonId(event.getId(), seasonId);
        return repository.saveGardenEvent(event).continueWith(task -> {
            if (!task.isSuccessful() && task.getException() != null) throw task.getException();
            return event;
        });
    }

    public Task<Void> addEventForSeason(String zoneId, String seasonId,
                                        String type, String note) {
        GardenEvent event = events.addForSeason(zoneId, seasonId, type, note);
        return repository.saveGardenEvent(event).addOnSuccessListener(unused ->
                events.replaceSeasonId(event.getId(), seasonId));
    }
}
