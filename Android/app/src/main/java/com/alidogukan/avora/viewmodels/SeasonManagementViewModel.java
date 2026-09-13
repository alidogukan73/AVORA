package com.alidogukan.avora.viewmodels;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.alidogukan.avora.R;
import com.alidogukan.avora.crop.CropCatalog;
import com.alidogukan.avora.firebase.FirebaseRepository;
import com.alidogukan.avora.journal.LocalGardenEventStore;
import com.alidogukan.avora.journal.LocalSeasonOutcomeStore;
import com.alidogukan.avora.models.CropCatalogItem;
import com.alidogukan.avora.models.GardenEvent;
import com.alidogukan.avora.models.GardenSeason;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.SeasonOutcome;
import com.alidogukan.avora.models.SeedlingBatch;
import com.alidogukan.avora.models.ZoneSeasonState;
import com.alidogukan.avora.season.SeasonRepository;
import com.alidogukan.avora.season.SeasonArchiveRepository;
import com.alidogukan.avora.season.SeasonScope;
import com.alidogukan.avora.season.SeasonStartConfiguration;
import com.alidogukan.avora.zones.ZoneCapacityPolicy;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Owns season lifecycle decisions and side effects; the Activity only renders UI. */
public final class SeasonManagementViewModel extends AndroidViewModel {
    private final FirebaseRepository firebaseRepository;
    private final SeasonRepository seasonRepository;
    private final SeasonArchiveRepository archiveRepository;
    private final LocalSeasonOutcomeStore outcomeStore;
    private final LocalGardenEventStore eventStore;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> bootstrapRequested = new HashSet<>();
    private final Set<String> autoSeasonRepairRequested = new HashSet<>();
    private final MutableLiveData<OneShotEvent<BootstrapNotice>> bootstrapNotices =
            new MutableLiveData<>();
    private final LiveData<List<GardenZone>> zones;
    private final LiveData<List<GardenSeason>> seasons;
    private final LiveData<List<CropCatalogItem>> cropCatalogItems;
    private List<GardenZone> latestZones = new ArrayList<>();
    private boolean bootstrapInProgress;
    private int bootstrapRetryCount;

    public SeasonManagementViewModel(@NonNull Application application) {
        super(application);
        firebaseRepository = new FirebaseRepository();
        seasonRepository = new SeasonRepository();
        archiveRepository = new SeasonArchiveRepository();
        outcomeStore = new LocalSeasonOutcomeStore(application);
        eventStore = new LocalGardenEventStore(application);
        zones = firebaseRepository.observeGardenZones();
        seasons = seasonRepository.observeAllSeasons();
        cropCatalogItems = firebaseRepository.observeCropCatalogItems();
    }

    public LiveData<List<GardenZone>> getZones() {
        return zones;
    }

    public LiveData<List<GardenSeason>> getSeasons() {
        return seasons;
    }

    public LiveData<List<CropCatalogItem>> getCropCatalogItems() {
        return cropCatalogItems;
    }

    public List<CropCatalogItem> mergedCrops(List<CropCatalogItem> values) {
        return CropCatalog.merge(values);
    }
    public List<GardenZone> activeZones(List<GardenZone> values) {
        return ZoneCapacityPolicy.activeZones(values);
    }
    public List<GardenZone> inactiveArchiveZones(
            List<GardenZone> values,
            List<GardenSeason> allSeasons
    ) {
        return archiveRepository.inactiveArchiveZones(values, allSeasons);
    }
    public boolean isInactiveArchiveZone(GardenZone zone) {
        return zone != null
                && ZoneCapacityPolicy.isValidZoneId(zone.getZone_id())
                && ZoneCapacityPolicy.isInactive(zone);
    }
    public boolean isSeasonNotStarted(ZoneSeasonState state) {
        return SeasonScope.isSeasonNotStarted(state);
    }

    public LiveData<OneShotEvent<BootstrapNotice>> getBootstrapNotices() {
        return bootstrapNotices;
    }

    public void synchronizeLegacySeasons(List<GardenZone> values) {
        latestZones = values == null ? new ArrayList<>() : new ArrayList<>(values);
        bootstrapNext();
    }

    private void bootstrapNext() {
        if (bootstrapInProgress) return;
        for (GardenZone zone : latestZones) {
            if (zone == null || safe(zone.getZone_id()).isBlank()
                    || ZoneCapacityPolicy.isInactive(zone)) {
                continue;
            }
            ZoneSeasonState state = zone.getSeason();
            if (SeasonScope.isModernAutoBootstrapCandidate(
                    state, zone.getCreated_at_epoch())
                    && autoSeasonRepairRequested.add(zone.getZone_id())) {
                bootstrapInProgress = true;
                seasonRepository.repairEmptyAutoStartedSeason(zone.getZone_id())
                        .addOnSuccessListener(repaired -> {
                            bootstrapInProgress = false;
                            if (Boolean.TRUE.equals(repaired)) {
                                emitNotice(R.string.season_auto_start_repaired, null);
                            }
                            bootstrapNext();
                        })
                        .addOnFailureListener(error -> {
                            bootstrapInProgress = false;
                            autoSeasonRepairRequested.remove(zone.getZone_id());
                            emitNotice(R.string.season_auto_start_repair_failed, error);
                        });
                return;
            }
            if (state != null && !safe(state.getStatus()).isBlank()) continue;
            if (!bootstrapRequested.add(zone.getZone_id())) continue;

            bootstrapInProgress = true;
            seasonRepository.bootstrapLegacySeason(zone)
                    .addOnSuccessListener(unused -> {
                        bootstrapInProgress = false;
                        bootstrapRetryCount = 0;
                        bootstrapNext();
                    })
                    .addOnFailureListener(error -> {
                        bootstrapInProgress = false;
                        bootstrapRequested.remove(zone.getZone_id());
                        if (transactionWasOverridden(error) && bootstrapRetryCount < 3) {
                            bootstrapRetryCount++;
                            handler.postDelayed(this::bootstrapNext,
                                    500L * bootstrapRetryCount);
                            return;
                        }
                        bootstrapRetryCount = 0;
                        emitNotice(R.string.season_bootstrap_failed,
                                transactionWasOverridden(error) ? null : error);
                    });
            return;
        }
    }

    public Task<Void> startSeason(
            GardenZone zone,
            String plantingDate,
            String growthStage,
            String label,
            SeasonStartConfiguration configuration
    ) {
        return seasonRepository.startSeason(
                zone, plantingDate, growthStage, label, configuration);
    }

    public Task<Void> transferSeedlingToSeason(
            GardenZone zone,
            String plantingDate,
            String label,
            CropCatalogItem crop,
            SeedlingBatch batch
    ) {
        String cropName = batch == null ? "" : safe(batch.getPlant_type());
        String plantType = crop == null
                ? SeasonStartConfiguration.customPlantType(cropName)
                : crop.getPlant_type();
        String emoji = batch == null || safe(batch.getEmoji()).isBlank()
                ? (crop == null ? "🌱" : crop.getEmoji())
                : batch.getEmoji();
        // A missing/renamed legacy catalog entry must never disable automatic
        // watering with the old 0..100 fallback.
        int moistureMin = crop == null ? 40 : crop.getIdeal_moisture_min();
        int moistureMax = crop == null ? 60 : crop.getIdeal_moisture_max();
        SeasonStartConfiguration configuration = new SeasonStartConfiguration(
                cropName,
                plantType,
                emoji,
                moistureMin,
                moistureMax
        );
        return seasonRepository.transferSeedlingToSeason(
                zone,
                plantingDate,
                "SEEDLING",
                label,
                configuration,
                batch
        );
    }

    public Task<Void> startSeason(
            GardenZone zone, String plantingDate, String growthStage, String label,
            String cropName, String plantType, String emoji
    ) {
        return startSeason(zone, plantingDate, growthStage, label,
                new SeasonStartConfiguration(cropName, plantType, emoji));
    }

    public Task<Void> startSeason(
            GardenZone zone, String plantingDate, String growthStage, String label,
            String cropName, String plantType, String emoji,
            int idealMoistureMin, int idealMoistureMax
    ) {
        return startSeason(zone, plantingDate, growthStage, label,
                new SeasonStartConfiguration(cropName, plantType, emoji,
                        idealMoistureMin, idealMoistureMax));
    }

    public Task<Boolean> canCancelNewSeason(String zoneId, String seasonId) {
        return seasonRepository.canCancelNewSeason(zoneId, seasonId);
    }

    public Task<Void> cancelNewSeason(String zoneId, String seasonId) {
        return seasonRepository.cancelNewSeason(zoneId, seasonId);
    }

    public Task<Void> closeSeason(
            GardenZone zone,
            GardenSeason season,
            SeasonOutcome outcome
    ) {
        Task<Void> task = seasonRepository.closeSeason(
                zone.getZone_id(), season.getSeason_id(), outcome);
        task.addOnSuccessListener(ignored -> {
            outcomeStore.addForSeason(outcome);
            GardenEvent event = eventStore.addSystemForSeason(
                    zone.getZone_id(),
                    season.getSeason_id(),
                    getApplication().getString(R.string.season_closed_event_title),
                    closeEventNote(outcome),
                    "season_closed:" + season.getSeason_id()
            );
            firebaseRepository.saveGardenEvent(event);
        });
        return task;
    }

    public List<GardenSeason> visibleSeasonsFor(
            GardenZone zone,
            List<GardenSeason> allSeasons
    ) {
        return archiveRepository.visibleFor(zone, allSeasons);
    }

    public List<GardenSeason> completedArchives(
            List<GardenSeason> history,
            boolean requireRecordedActivity
    ) {
        return archiveRepository.completed(history, requireRecordedActivity);
    }

    public boolean hasRecordedArchive(GardenZone zone, List<GardenSeason> allSeasons) {
        return archiveRepository.hasRecorded(zone, allSeasons);
    }

    private String closeEventNote(SeasonOutcome outcome) {
        String note = safe(outcome.getResult());
        if (!safe(outcome.getHarvest_amount()).isBlank()) {
            note += " · " + getApplication().getString(
                    R.string.season_harvest_short, outcome.getHarvest_amount());
        }
        return note;
    }

    private boolean transactionWasOverridden(Exception error) {
        String message = error == null ? "" : safe(error.getMessage()).toLowerCase(Locale.ROOT);
        return message.contains("overridden") || message.contains("subsequent set");
    }

    private void emitNotice(int messageRes, Exception error) {
        bootstrapNotices.setValue(new OneShotEvent<>(new BootstrapNotice(messageRes, error)));
    }

    @Override
    protected void onCleared() {
        handler.removeCallbacksAndMessages(null);
        super.onCleared();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class BootstrapNotice {
        public final int messageRes;
        public final Exception error;

        private BootstrapNotice(int messageRes, Exception error) {
            this.messageRes = messageRes;
            this.error = error;
        }
    }
}
