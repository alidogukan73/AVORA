package com.alidogukan.avora.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.alidogukan.avora.crop.CropCatalog;
import com.alidogukan.avora.firebase.FirebaseRepository;
import com.alidogukan.avora.models.CropCatalogItem;
import com.alidogukan.avora.season.SeasonStartConfiguration;
import com.alidogukan.avora.seedling.SeedlingCropCatalog;
import com.alidogukan.avora.seedling.SeedlingVarietyCatalog;
import com.alidogukan.avora.seedling.SeedlingVarietyStore;
import com.google.android.gms.tasks.Task;

import java.util.List;

/** Owns crop catalogue persistence and protects archived season snapshots from UI edits. */
public final class CropCatalogViewModel extends AndroidViewModel {
    private final FirebaseRepository repository = new FirebaseRepository();
    private final SeedlingVarietyStore varietyStore;
    private final LiveData<List<CropCatalogItem>> userItems =
            repository.observeCropCatalogItems();

    public CropCatalogViewModel(@NonNull Application application) {
        super(application);
        varietyStore = new SeedlingVarietyStore(application);
    }

    public LiveData<List<CropCatalogItem>> getUserItems() {
        return userItems;
    }

    public List<CropCatalogItem> getBuiltInItems() {
        return CropCatalog.builtIns();
    }

    /** Uses the same built-in and user variety list as the seedling batch editor. */
    public List<String> varietiesFor(CropCatalogItem crop) {
        List<String> builtIns = SeedlingCropCatalog.profileFor(crop).getVarieties();
        if (crop == null) return builtIns;
        return SeedlingVarietyCatalog.merge(
                builtIns, varietyStore.load(crop.getCrop_id()));
    }

    /** Returns the canonical display value so callers can distinguish duplicates. */
    public String saveVariety(CropCatalogItem crop, String requested) {
        String normalized = SeedlingVarietyCatalog.normalize(requested);
        String existing = SeedlingVarietyCatalog.find(varietiesFor(crop), normalized);
        if (existing != null) return existing;
        if (crop != null) varietyStore.add(crop.getCrop_id(), normalized);
        return normalized;
    }

    public Task<Void> save(@Nullable CropCatalogItem existing, String name, String emoji,
                           int minimumMoisture, int maximumMoisture) {
        String effectiveEmoji = emoji == null ? "" : emoji.trim();
        if (effectiveEmoji.isBlank()) {
            effectiveEmoji = SeasonStartConfiguration.suggestedCropEmoji(name);
        }
        CropCatalogItem item = existing == null
                ? CropCatalog.newUserItem(name, effectiveEmoji,
                minimumMoisture, maximumMoisture)
                : existing;
        item.setName(name);
        item.setEmoji(effectiveEmoji);
        item.setPlant_type(SeasonStartConfiguration.customPlantType(name));
        item.setIdeal_moisture_min(minimumMoisture);
        item.setIdeal_moisture_max(maximumMoisture);
        return repository.saveCropCatalogItem(item);
    }

    public Task<Void> deactivate(String cropId) {
        return repository.deactivateCropCatalogItem(cropId);
    }
}
