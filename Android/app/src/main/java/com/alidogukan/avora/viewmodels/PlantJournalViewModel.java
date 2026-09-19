package com.alidogukan.avora.viewmodels;

import android.app.Application;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.alidogukan.avora.journal.JournalEntryPolicy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private final ExecutorService recordExecutor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<Boolean> recordSaving = new MutableLiveData<>(false);
    private final MutableLiveData<Task<String>> appendedPhotoResult = new MutableLiveData<>();
    public LiveData<Task<String>> getAppendedPhotoResult() { return appendedPhotoResult; }
    public void consumeAppendedPhotoResult() { appendedPhotoResult.setValue(null); }
    private final MutableLiveData<Task<Void>> recordSaveResult = new MutableLiveData<>();
    public LiveData<Boolean> getRecordSaving() { return recordSaving; }
    public LiveData<Task<Void>> getRecordSaveResult() { return recordSaveResult; }
    public void consumeRecordSaveResult() { recordSaveResult.setValue(null); }
    public GardenEvent photoOwner(GardenPhoto photo, GardenEvent candidate) {
        return JournalEntryPolicy.photoOwner(photo, java.util.Collections.singletonList(candidate));
    }
    private final LocalGardenEventStore events;
    private final LocalGardenPhotoStore photos;
    private final LocalSeasonOutcomeStore outcomes;
    private final LocalGardenNotificationStore notifications;
    private final LiveData<List<GardenZone>> zones = repository.observeGardenZones();
    private final LiveData<List<FertilizerApplication>> fertilizerHistory =
            repository.observeFertilizerHistory();
    private final LiveData<List<WateringHistory>> wateringHistory =
            repository.observeWateringHistory();
    private final LiveData<List<GardenPhoto>> photoMetadata =
            repository.observeGardenPhotoMetadata();
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
    public LiveData<List<GardenPhoto>> getPhotoMetadata() { return photoMetadata; }
    public LiveData<WeatherForecast> getWeather() { return weather; }
    public LiveData<List<GardenSeason>> getSeasons(String zoneId) {
        return seasons.observeZoneSeasons(zoneId);
    }
    public List<GardenSeason> visibleSeasons(List<GardenSeason> values,
                                             ZoneSeasonState current,
                                             String requestedSeasonId) {
        List<GardenSeason> result = new ArrayList<>();
        if (values == null) return result;
        for (GardenSeason value : values) {
            if (SeasonScope.isVisibleInPlantJournal(
                    value,
                    current,
                    requestedSeasonId
            )) result.add(value);
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
    public boolean isJournalMilestone(String type) {
        return com.alidogukan.avora.journal.JournalEntryPolicy.isMilestone(type);
    }
    public boolean isManualJournalEvent(String type) {
        return com.alidogukan.avora.journal.JournalEntryPolicy.isManualEvent(type);
    }
    public boolean validJournalDate(long timestamp, long seasonStart, long now) {
        return com.alidogukan.avora.journal.JournalEntryPolicy.validDate(timestamp, seasonStart, now);
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

    public void persistRecord(String zoneId, String seasonId, String type,
                              String note, long occurredAtEpoch, String relatedApplicationId,
                              List<Uri> selectedPhotos, List<Bitmap> selectedBitmaps) {
        if (Boolean.TRUE.equals(recordSaving.getValue())) return;
        List<Uri> uris = selectedPhotos == null ? new ArrayList<>() : new ArrayList<>(selectedPhotos);
        List<Bitmap> bitmaps = selectedBitmaps == null ? new ArrayList<>() : new ArrayList<>(selectedBitmaps);
        recordSaving.setValue(true);
        recordSaveResult.setValue(null);
        List<GardenPhoto> preparedPhotos = new ArrayList<>();
        GardenEvent[] preparedEvent = {null};
        Task<Void> save = seasons.requireWritableJournalSeason(zoneId, seasonId).continueWithTask(check -> {
            if (!check.isSuccessful()) return Tasks.forException(check.getException());
            GardenSeason season = check.getResult();
            if (!JournalEntryPolicy.validContent(type, note, uris.size() + bitmaps.size())) {
                return Tasks.forException(new IllegalArgumentException("JOURNAL_CONTENT_INVALID"));
            }
            if (!JournalEntryPolicy.validDate(occurredAtEpoch, season.getStarted_at_epoch(),
                    System.currentTimeMillis() / 1000L)) {
                return Tasks.forException(new IllegalArgumentException("JOURNAL_DATE_INVALID"));
            }
            return prepareRecord(() -> {
                // Prepare every photo before issuing any cloud write.
                String group = relatedApplicationId == null || relatedApplicationId.isBlank()
                        ? "journal_record_" + UUID.randomUUID() : relatedApplicationId;
                if (JournalEntryPolicy.isManualEvent(type)) {
                    GardenEvent event = events.add(zoneId, type, note, occurredAtEpoch);
                    event.setSeason_id(seasonId);
                    events.replaceSeasonId(event.getId(), seasonId);
                    preparedEvent[0] = event;
                    group = "journal_record_" + event.getId();
                }
                for (Uri uri : uris) prepareRecordPhoto(photos.save(uri, zoneId, note, group),
                        seasonId, occurredAtEpoch, preparedPhotos);
                for (Bitmap bitmap : bitmaps) prepareRecordPhoto(photos.save(bitmap, zoneId, note, group),
                        seasonId, occurredAtEpoch, preparedPhotos);
                return null;
            }).continueWithTask(preparation -> preparation.isSuccessful()
                    ? repository.saveJournalRecord(preparedEvent[0], preparedPhotos)
                    : Tasks.forException(preparation.getException()));
        });
        save.addOnCompleteListener(result -> {
            if (!result.isSuccessful()) {
                if (preparedEvent[0] != null) events.delete(preparedEvent[0].getId());
                for (GardenPhoto photo : preparedPhotos) photos.delete(photo);
            }
            recordSaving.setValue(false);
            recordSaveResult.setValue(result);
        });
    }

    private <T> Task<T> prepareRecord(java.util.concurrent.Callable<T> work) {
        com.google.android.gms.tasks.TaskCompletionSource<T> source = new com.google.android.gms.tasks.TaskCompletionSource<>();
        try {
            recordExecutor.execute(() -> {
                try { source.setResult(work.call()); }
                catch (Exception error) { source.setException(error); }
            });
        } catch (java.util.concurrent.RejectedExecutionException error) {
            source.setException(error);
        }
        return source.getTask();
    }

    private void prepareRecordPhoto(GardenPhoto photo, String seasonId, long timestamp,
                                    List<GardenPhoto> prepared) {
        prepared.add(photo);
        photo.setSeason_id(seasonId);
        photo.setCaptured_at_epoch(timestamp);
        photos.updateSeasonId(photo.getId(), seasonId);
        photos.updateCapturedAt(photo.getId(), timestamp);
    }

    public Task<String> appendRecordPhotos(GardenPhoto first, String currentGroup,
                                           String zoneId, String seasonId, String note,
                                           long timestamp, List<Uri> selected) {
        if (Boolean.TRUE.equals(recordSaving.getValue())) {
            return Tasks.forException(new IllegalStateException("JOURNAL_SAVE_IN_PROGRESS"));
        }
        recordSaving.setValue(true);
        appendedPhotoResult.setValue(null);
        String group = !com.alidogukan.avora.photos.JournalPhotoRecordFilter.isRecordGroup(currentGroup)
                ? "journal_record_" + UUID.randomUUID() : currentGroup;
        List<Uri> uris = new ArrayList<>(selected);
        List<GardenPhoto> prepared = new ArrayList<>();
        String previousGroup = first == null ? "" : first.getRelated_application_id();
        Task<String> task = seasons.requireWritableJournalSeason(zoneId, seasonId).continueWithTask(check -> {
            if (!check.isSuccessful()) return Tasks.forException(check.getException());
            java.util.Set<String> existing = new java.util.HashSet<>();
            List<GardenPhoto> candidates = new ArrayList<>(photos.load());
            if (photoMetadata.getValue() != null) candidates.addAll(photoMetadata.getValue());
            for (GardenPhoto photo : candidates) {
                if (zoneId.equals(photo.getZone_id()) && seasonId.equals(photo.getSeason_id())
                        && (group.equals(photo.getRelated_application_id())
                        || first != null && first.getId().equals(photo.getId()))) existing.add(photo.getId());
            }
            if (uris.isEmpty() || existing.size() + uris.size() > 5) {
                return Tasks.forException(new IllegalArgumentException("JOURNAL_PHOTO_LIMIT"));
            }
            return prepareRecord(() -> {
                for (Uri uri : uris) prepareRecordPhoto(photos.save(uri, zoneId, note, group),
                        seasonId, timestamp, prepared);
                return null;
            }).continueWithTask(preparation -> {
                if (!preparation.isSuccessful()) return Tasks.forException(preparation.getException());
                List<GardenPhoto> writes = new ArrayList<>(prepared);
                if (first != null && !group.equals(previousGroup)) {
                    first.setRelated_application_id(group);
                    writes.add(first);
                }
                return repository.saveJournalRecord(null, writes).continueWith(commit -> {
                    if (!commit.isSuccessful()) throw commit.getException();
                    if (first != null) photos.updateRelatedApplicationId(first.getId(), group);
                    return group;
                });
            });
        });
        Task<String> completed = task.continueWith(result -> {
            if (!result.isSuccessful()) {
                if (first != null) first.setRelated_application_id(previousGroup);
                for (GardenPhoto photo : prepared) photos.delete(photo);
                throw result.getException();
            }
            return result.getResult();
        });
        completed.addOnCompleteListener(result -> {
            recordSaving.setValue(false);
            appendedPhotoResult.setValue(result);
        });
        return completed;
    }

    public Task<Void> updateEvent(String id, String zoneId, String seasonId,
                                  String type, String note, long epoch) {
        if (Boolean.TRUE.equals(recordSaving.getValue())) return Tasks.forException(
                new IllegalStateException("JOURNAL_SAVE_IN_PROGRESS"));
        GardenEvent target = null;
        for (GardenEvent value : events.load()) {
            if (id.equals(value.getId()) && zoneId.equals(value.getZone_id())
                    && seasonId.equals(value.getSeason_id()) && "MANUAL".equals(value.getSource())) target = value;
        }
        if (target == null || note == null || note.trim().isEmpty()) {
            return Tasks.forException(new IllegalArgumentException("JOURNAL_CONTENT_INVALID"));
        }
        GardenEvent event = target;
        event.setNote(note.trim());
        return trackMutation(seasons.requireWritableJournalSeason(zoneId, seasonId)
                .continueWithTask(check -> check.isSuccessful() ? repository.saveGardenEvent(event)
                        : Tasks.forException(check.getException()))
                .addOnSuccessListener(unused -> events.update(id, event.getType(), note)));
    }

    public Task<Void> deleteRecord(String eventId, String zoneId, String seasonId,
                                    GardenPhoto selected, String groupId) {
        if (Boolean.TRUE.equals(recordSaving.getValue())) return Tasks.forException(
                new IllegalStateException("JOURNAL_SAVE_IN_PROGRESS"));
        java.util.Map<String, GardenPhoto> candidates = new java.util.LinkedHashMap<>();
        List<GardenPhoto> cloud = photoMetadata.getValue();
        if (cloud != null) for (GardenPhoto photo : cloud) candidates.put(photo.getId(), photo);
        for (GardenPhoto photo : photos.load()) candidates.put(photo.getId(), photo);
        if (selected != null) candidates.putIfAbsent(selected.getId(), selected);
        List<GardenPhoto> targets = new ArrayList<>();
        for (GardenPhoto photo : candidates.values()) {
            boolean linked = com.alidogukan.avora.photos.JournalPhotoRecordFilter.isRecordGroup(groupId)
                    ? groupId.equals(photo.getRelated_application_id())
                    : selected != null && selected.getId().equals(photo.getId());
            if (linked && zoneId.equals(photo.getZone_id()) && seasonId.equals(photo.getSeason_id())) targets.add(photo);
        }
        return trackMutation(seasons.requireWritableJournalSeason(zoneId, seasonId).continueWithTask(check ->
                check.isSuccessful() ? repository.deleteJournalRecord(eventId, targets)
                        : Tasks.forException(check.getException())).addOnSuccessListener(unused -> {
                    if (eventId != null && !eventId.isBlank()) events.delete(eventId);
                    for (GardenPhoto photo : targets) photos.delete(photo);
                }));
    }

    private Task<Void> trackMutation(Task<Void> task) {
        recordSaving.setValue(true);
        recordSaveResult.setValue(null);
        task.addOnCompleteListener(result -> {
            recordSaving.setValue(false);
            recordSaveResult.setValue(result);
        });
        return task;
    }

    @Override protected void onCleared() {
        recordExecutor.shutdown();
        super.onCleared();
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
