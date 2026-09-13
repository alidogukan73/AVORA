package com.alidogukan.avora.viewmodels;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import com.alidogukan.avora.R;
import com.alidogukan.avora.crop.CropCatalog;
import com.alidogukan.avora.firebase.FirebaseRepository;
import com.alidogukan.avora.models.CropCatalogItem;
import com.alidogukan.avora.models.SeedlingBatch;
import com.alidogukan.avora.models.SeedlingDailyLog;
import com.alidogukan.avora.models.SeedlingNodeState;
import com.alidogukan.avora.models.GardenPhoto;
import com.alidogukan.avora.models.SeedlingPhotoUpload;
import com.alidogukan.avora.seedling.SeedlingRepository;
import com.alidogukan.avora.seedling.SeedlingStagePolicy;
import com.alidogukan.avora.seedling.SeedlingCropCatalog;
import com.alidogukan.avora.seedling.SeedlingDailyPhotoStore;
import com.alidogukan.avora.seedling.SeedlingVarietyCatalog;
import com.alidogukan.avora.seedling.SeedlingVarietyStore;
import com.alidogukan.avora.season.SeasonRepository;
import com.google.android.gms.tasks.Task;
import java.util.List;

/** Owns all seedling data operations; screens only validate and render input. */
public final class SeedlingViewModel extends AndroidViewModel {
    private final SeedlingRepository repository = new SeedlingRepository();
    private final SeasonRepository seasonRepository = new SeasonRepository();
    private final SeedlingDailyPhotoStore photoStore;
    private final FirebaseRepository firebaseRepository = new FirebaseRepository();
    private final SeedlingVarietyStore varietyStore;
    private final MutableLiveData<OneShotEvent<Integer>> readError = new MutableLiveData<>();
    private final LiveData<List<SeedlingBatch>> batches =
            repository.observeBatches(this::reportReadError);
    private final LiveData<List<CropCatalogItem>> cropCatalogItems =
            firebaseRepository.observeCropCatalogItems();

    public SeedlingViewModel(@NonNull Application application) {
        super(application);
        varietyStore = new SeedlingVarietyStore(application);
        photoStore = new SeedlingDailyPhotoStore(application);
    }

    public LiveData<List<SeedlingBatch>> getBatches() { return batches; }
    public LiveData<OneShotEvent<Integer>> getReadError() { return readError; }
    public LiveData<List<CropCatalogItem>> getCropCatalogItems() { return cropCatalogItems; }
    public List<CropCatalogItem> mergedCrops(List<CropCatalogItem> values) {
        return CropCatalog.merge(values);
    }

    public List<String> varietiesFor(CropCatalogItem crop) {
        List<String> builtIns = SeedlingCropCatalog.profileFor(crop).getVarieties();
        if (crop == null) return builtIns;
        return SeedlingVarietyCatalog.merge(
                builtIns, varietyStore.load(crop.getCrop_id()));
    }

    public String saveVariety(CropCatalogItem crop, String requested) {
        String normalized = SeedlingVarietyCatalog.normalize(requested);
        List<String> current = varietiesFor(crop);
        String existing = SeedlingVarietyCatalog.find(current, normalized);
        if (existing != null) return existing;
        if (crop != null) varietyStore.add(crop.getCrop_id(), normalized);
        return normalized;
    }
    public LiveData<SeedlingBatch> getBatch(String id) {
        return repository.observeBatch(id, this::reportReadError);
    }
    public LiveData<SeedlingNodeState> getNode(String id) {
        return repository.observeNode(id, this::reportReadError);
    }
    public LiveData<List<SeedlingDailyLog>> getLogs(String id) {
        return repository.observeLogs(id, this::reportReadError);
    }
    public Task<Boolean> hasDailyLogs(String id) { return repository.hasDailyLogs(id); }

    private void reportReadError() {
        readError.postValue(new OneShotEvent<>(R.string.seedling_read_failed));
    }

    public Task<Void> createBatch(CropCatalogItem crop, String variety, String area,
                                  int seedCount, int trayCells, long sowingEpoch,
                                  long emergenceEpoch, long transplantEpoch) {
        if (crop == null) {
            return com.google.android.gms.tasks.Tasks.forException(
                    new IllegalArgumentException("Bitki türü gerekli."));
        }
        long now = System.currentTimeMillis() / 1000L;
        SeedlingBatch batch = new SeedlingBatch();
        batch.setCrop_id(crop.getCrop_id());
        batch.setPlant_type(crop.getName());
        batch.setEmoji(crop.getEmoji());
        batch.setVariety(variety);
        batch.setArea(area);
        batch.setNode_id("seedling-001");
        batch.setStatus("ACTIVE");
        batch.setStage(SeedlingStagePolicy.SOWN);
        batch.setSowing_date_epoch(sowingEpoch);
        batch.setEstimated_emergence_epoch(emergenceEpoch);
        batch.setEstimated_transplant_epoch(transplantEpoch);
        batch.setSeed_count(seedCount);
        batch.setTray_cell_count(trayCells);
        batch.setHealthy_count(seedCount);
        batch.setCreated_at_epoch(now);
        batch.setUpdated_at_epoch(now);
        return repository.create(batch);
    }

    public Task<Void> saveDailyLog(String batchId, double height, int leaves,
                                   int healthy, boolean watered, String note,
                                   String photoId, String photoStoragePath) {
        SeedlingDailyLog log = new SeedlingDailyLog();
        log.setBatch_id(batchId);
        log.setHeight_cm(height);
        log.setLeaf_count(leaves);
        log.setHealthy_count(healthy);
        log.setWatered(watered);
        log.setNote(note);
        log.setPhoto_id(photoId);
        log.setPhoto_storage_path(photoStoragePath);
        log.setCreated_at_epoch(System.currentTimeMillis() / 1000L);
        return repository.saveLog(log);
    }

    public Task<Void> updateDailyLog(SeedlingDailyLog existing, double height, int leaves,
                                     int healthy, boolean watered, String note,
                                     String photoId, String photoStoragePath) {
        if (existing == null) {
            return com.google.android.gms.tasks.Tasks.forException(
                    new IllegalArgumentException("Günlük kaydı gerekli."));
        }
        SeedlingDailyLog log = new SeedlingDailyLog();
        log.setLog_id(existing.getLog_id());
        log.setBatch_id(existing.getBatch_id());
        log.setHeight_cm(height);
        log.setLeaf_count(leaves);
        log.setHealthy_count(healthy);
        log.setWatered(watered);
        log.setNote(note);
        log.setPhoto_id(photoId);
        log.setPhoto_storage_path(photoStoragePath);
        log.setCreated_at_epoch(existing.getCreated_at_epoch());
        return repository.updateLog(log);
    }

    public Task<Void> deleteDailyLog(String batchId, String logId) {
        return repository.deleteLog(batchId, logId);
    }

    public Task<SeedlingPhotoUpload> saveDailyPhoto(Uri source, String batchId) {
        return photoStore.save(source, batchId);
    }

    public Task<GardenPhoto> loadDailyPhoto(SeedlingDailyLog log) {
        return photoStore.load(log);
    }

    public void deleteDailyPhoto(SeedlingDailyLog log) {
        photoStore.delete(log);
    }

    public void deleteDailyPhoto(SeedlingPhotoUpload uploaded) {
        photoStore.delete(uploaded);
    }

    public Task<Void> advanceStage(SeedlingBatch batch) {
        return repository.advanceStage(batch.getBatch_id(),
                SeedlingStagePolicy.next(batch.getStage()));
    }

    public Task<Void> setStage(String batchId, String stage) {
        return repository.updateStage(batchId, stage);
    }

    public Task<Void> deleteBatch(String batchId) {
        return repository.deleteBatch(batchId);
    }

    public Task<Void> archiveBatch(String batchId) {
        return repository.archiveBatch(batchId);
    }

    public Task<Void> restoreBatch(SeedlingBatch batch) {
        return repository.restoreBatch(batch);
    }

    public Task<Void> undoSeasonTransfer(SeedlingBatch batch) {
        return seasonRepository.undoSeedlingTransfer(batch);
    }

    public boolean isDeletionBlockedByLogs(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SeedlingRepository.BatchHasDailyLogsException
                    || current instanceof SeedlingRepository.BatchTransferredException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public boolean canAdvance(SeedlingBatch batch) {
        return batch != null && SeedlingStagePolicy.canAdvance(batch.getStage());
    }

    public boolean canRetreat(SeedlingBatch batch) {
        return batch != null && SeedlingStagePolicy.canRetreat(batch.getStage());
    }

    public String previousStage(SeedlingBatch batch) {
        return batch == null
                ? SeedlingStagePolicy.SOWN
                : SeedlingStagePolicy.previous(batch.getStage());
    }

    public boolean isReady(SeedlingBatch batch) {
        return batch != null && SeedlingStagePolicy.READY.equals(
                SeedlingStagePolicy.normalize(batch.getStage()));
    }

    public boolean isActive(SeedlingBatch batch) {
        return batch != null && batch.isActive();
    }

    public boolean isArchived(SeedlingBatch batch) {
        return batch != null && batch.isArchived();
    }

    public int progress(SeedlingBatch batch) {
        return batch == null ? 1 : SeedlingStagePolicy.progress(batch.getStage());
    }

    @Override protected void onCleared() {
        photoStore.close();
        super.onCleared();
    }

    public String stageLabel(String stage) {
        switch (SeedlingStagePolicy.normalize(stage)) {
            case SeedlingStagePolicy.GERMINATING:
                return getApplication().getString(R.string.seedling_stage_germination);
            case SeedlingStagePolicy.COTYLEDON:
                return getApplication().getString(R.string.seedling_stage_first_leaf);
            case SeedlingStagePolicy.TRUE_LEAVES:
                return getApplication().getString(R.string.seedling_stage_true_leaves);
            case SeedlingStagePolicy.HARDENING:
                return getApplication().getString(R.string.seedling_stage_hardening);
            case SeedlingStagePolicy.READY:
                return getApplication().getString(R.string.seedling_stage_ready_plain);
            default:
                return getApplication().getString(R.string.seedling_stage_sowing);
        }
    }

}
