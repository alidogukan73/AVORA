package com.alidogukan.avora.season;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import com.alidogukan.avora.config.AppInfo;

import com.alidogukan.avora.firebase.FirebaseLiveData;
import com.alidogukan.avora.models.GardenSeason;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.SeasonOutcome;
import com.alidogukan.avora.models.SeasonStatus;
import com.alidogukan.avora.models.SeedlingBatch;
import com.alidogukan.avora.models.ZoneSeasonState;
import com.alidogukan.avora.zones.ZoneCapacityPolicy;
import com.alidogukan.avora.zones.ZoneOperationSafetyPolicy;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Owns the lifecycle of zone seasons. All lifecycle mutations are atomic and
 * preserve hardware configuration, inventory, calibration and historical data.
 */
public final class SeasonRepository {
    private final DatabaseReference deviceRef = FirebaseDatabase.getInstance()
            .getReference("devices")
            .child(AppInfo.DEVICE_ID);
    private final DatabaseReference seasonsRef = deviceRef
            .child("garden_journal")
            .child("seasons");

    public LiveData<List<GardenSeason>> observeAllSeasons() {
        FirebaseLiveData<List<GardenSeason>> liveData = new FirebaseLiveData<>(seasonsRef);
        liveData.setEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<GardenSeason> values = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    GardenSeason value = child.getValue(GardenSeason.class);
                    if (value == null) continue;
                    if (value.getSeason_id().isBlank()) {
                        value.setSeason_id(child.getKey());
                    }
                    values.add(value);
                }
                values.sort(Comparator.comparingLong(GardenSeason::getStarted_at_epoch).reversed());
                liveData.setValue(values);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                liveData.setValue(new ArrayList<>());
            }
        });
        return liveData;
    }

    public LiveData<List<GardenSeason>> observeZoneSeasons(String zoneId) {
        com.google.firebase.database.Query query = seasonsRef
                .orderByChild("zone_id")
                .equalTo(safe(zoneId));
        FirebaseLiveData<List<GardenSeason>> liveData = new FirebaseLiveData<>(query);
        liveData.setEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<GardenSeason> values = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    GardenSeason value = child.getValue(GardenSeason.class);
                    if (value == null) continue;
                    if (value.getSeason_id().isBlank()) value.setSeason_id(child.getKey());
                    values.add(value);
                }
                values.sort(Comparator.comparingLong(GardenSeason::getStarted_at_epoch).reversed());
                liveData.setValue(values);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                liveData.setValue(new ArrayList<>());
            }
        });
        return liveData;
    }

    /** Creates the one-time compatibility season only when no season state exists. */
    public Task<Void> bootstrapLegacySeason(GardenZone zone) {
        if (zone == null || safe(zone.getZone_id()).isBlank()) {
            return Tasks.forException(new IllegalArgumentException("Bölge bilgisi gerekli."));
        }
        long now = nowEpoch();
        String zoneId = zone.getZone_id();

        /*
         * Do not run a transaction on the complete device root here. The Raspberry Pi
         * continuously updates sensor and health children below that root; Firebase
         * therefore cancels a root transaction as OVERRIDDEN_BY_SET and the UI remains
         * in PREPARING forever. A single multi-location update still creates the state,
         * manifest and AI link atomically, while unrelated live measurements may continue.
         */
        return deviceRef.get().continueWithTask(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                Exception error = task.getException();
                return Tasks.forException(error == null
                        ? new IllegalStateException("İlk sezon hazırlanamadı.")
                        : error);
            }

            DataSnapshot root = task.getResult();
            DataSnapshot zoneData = root.child("zones").child(zoneId);
            String existingStatus = stringValue(zoneData.child("season").child("status"));
            if (!existingStatus.isBlank()) return Tasks.forResult(null);

            String plantingDate = stringValue(zoneData
                    .child("fertilization")
                    .child("planting_date"));
            long startedAt = inferLegacySeasonStart(root, zoneId, now);
            String seasonId = SeasonScope.createSeasonId(zoneId, startedAt);
            String label = seasonLabel(zone, startedAt);
            String statePath = "zones/" + zoneId + "/season/";
            String manifestPath = "garden_journal/seasons/" + seasonId + "/";

            Map<String, Object> updates = new HashMap<>();
            updates.put(statePath + "active_season_id", seasonId);
            updates.put(statePath + "status", SeasonStatus.ACTIVE);
            updates.put(statePath + "label", label);
            updates.put(statePath + "started_at_epoch", startedAt);
            updates.put(statePath + "ended_at_epoch", 0L);
            updates.put(statePath + "include_legacy_records", true);
            updates.put(statePath + "updated_at_epoch", now);

            updates.put(manifestPath + "season_id", seasonId);
            updates.put(manifestPath + "zone_id", zoneId);
            updates.put(manifestPath + "area_id", ZoneAreaIdentity.effective(zone));
            updates.put(manifestPath + "area_name", safe(zone.getArea_name()));
            updates.put(manifestPath + "zone_name", safe(zone.getName()));
            updates.put(manifestPath + "plant_type", safe(zone.getPlant_type()));
            updates.put(manifestPath + "emoji", safe(zone.getEmoji()));
            updates.put(manifestPath + "sensor_id", safe(zone.getSensor_id()));
            updates.put(manifestPath + "sensor_enabled", zone.isSensor_enabled());
            updates.put(manifestPath + "valve_id", safe(zone.getValve_id()));
            updates.put(manifestPath + "valve_mode", safe(zone.getValve_mode()));
            updates.put(manifestPath + "label", label);
            updates.put(manifestPath + "status", SeasonStatus.ACTIVE);
            updates.put(manifestPath + "planting_date", plantingDate);
            updates.put(manifestPath + "started_at_epoch", startedAt);
            updates.put(manifestPath + "ended_at_epoch", 0L);
            updates.put(manifestPath + "includes_legacy_records", true);
            updates.put(manifestPath + "created_at_epoch", now);
            updates.put(manifestPath + "updated_at_epoch", now);

            updates.put("zones/" + zoneId + "/ai/season_id", seasonId);
            updates.put("zones/" + zoneId + "/ai/season_status", SeasonStatus.ACTIVE);
            updates.put("zones/" + zoneId + "/ai/season_started_at_epoch", startedAt);
            updates.put("zones/" + zoneId + "/ai/season_closed_at_epoch", 0L);
            return deviceRef.updateChildren(updates);
        });
    }

    /**
     * Repairs the old compatibility behavior that could create an active legacy
     * season immediately after a modern zone was added. Empty generated seasons
     * are removed; generated seasons that already own field records are closed
     * and preserved as archives so they can no longer block zone removal.
     */
    public Task<Boolean> repairEmptyAutoStartedSeason(String zoneId) {
        if (safe(zoneId).isBlank()) return Tasks.forResult(false);
        return deviceRef.get().continueWithTask(task -> {
            if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) {
                Exception error = task.getException();
                return Tasks.forException(error == null
                        ? new IllegalStateException("Sezon verileri okunamadı.")
                        : error);
            }

            DataSnapshot root = task.getResult();
            DataSnapshot zoneData = root.child("zones").child(zoneId);
            ZoneSeasonState state = zoneData.child("season").getValue(ZoneSeasonState.class);
            long zoneCreatedAt = longValue(zoneData.child("created_at_epoch"));
            if (!SeasonScope.isModernAutoBootstrapCandidate(state, zoneCreatedAt)) {
                return Tasks.forResult(false);
            }

            String seasonId = state.getActive_season_id();
            DataSnapshot manifest = root.child("garden_journal")
                    .child("seasons")
                    .child(seasonId);
            boolean generatedLegacyManifest = manifest.exists()
                    && SeasonStatus.isActive(stringValue(manifest.child("status")))
                    && booleanValue(manifest.child("includes_legacy_records"))
                    && !manifest.child("cancellation_snapshot").exists();

            SeasonCounts counts = calculateCounts(root, zoneId, state, false);
            boolean hasFieldRecords = counts.wateringCount > 0
                    || counts.fertilizerCount > 0
                    || counts.eventCount > 0
                    || counts.photoCount > 0
                    || counts.analysisCount > 0;
            String growthStage = stringValue(zoneData
                    .child("fertilization")
                    .child("growth_stage"));
            boolean untouchedStage = growthStage.isBlank()
                    || "SOIL_PREPARATION".equalsIgnoreCase(growthStage);
            SeasonScope.AutoStartedRepairAction repairAction =
                    SeasonScope.autoStartedRepairAction(
                            generatedLegacyManifest,
                            hasFieldRecords,
                            untouchedStage,
                            isZoneIrrigationBusy(root, zoneId)
                    );
            if (repairAction == SeasonScope.AutoStartedRepairAction.NONE) {
                return Tasks.forResult(false);
            }

            long now = nowEpoch();
            String zonePath = "zones/" + zoneId + "/";
            String manifestPath = "garden_journal/seasons/" + seasonId + "/";
            Map<String, Object> updates = new HashMap<>();
            updates.put(zonePath + "season/active_season_ids", null);
            updates.put(zonePath + "season/active_season_id", "");
            updates.put(zonePath + "season/status", SeasonStatus.CLOSED);
            updates.put(zonePath + "season/label", "");
            updates.put(zonePath + "season/started_at_epoch", 0L);
            updates.put(zonePath + "season/ended_at_epoch", 0L);
            updates.put(zonePath + "season/include_legacy_records", false);
            updates.put(zonePath + "season/updated_at_epoch", now);
            updates.put(zonePath + "irrigation_enabled", false);
            updates.put(zonePath + "fertilization/enabled", false);
            updates.put(zonePath + "fertilization/reminder_enabled", false);
            updates.put(zonePath + "fertilization/next_application_at_epoch", 0L);
            updates.put(zonePath + "fertilization/updated_at_epoch", now);
            updates.put(zonePath + "ai/season_id", "");
            updates.put(zonePath + "ai/season_status", SeasonStatus.CLOSED);
            updates.put(zonePath + "ai/season_started_at_epoch", 0L);
            updates.put(zonePath + "ai/season_closed_at_epoch", 0L);
            updates.put(zonePath + "ai/updated_at_epoch", now);
            putCancelledIrrigationState(updates, zonePath, now);

            if (repairAction == SeasonScope.AutoStartedRepairAction.DELETE_EMPTY) {
                updates.put("garden_journal/seasons/" + seasonId, null);
            } else {
                updates.put(manifestPath + "status", SeasonStatus.CLOSED);
                updates.put(manifestPath + "ended_at_epoch", now);
                updates.put(manifestPath + "watering_count", counts.wateringCount);
                updates.put(manifestPath + "watering_seconds", counts.wateringSeconds);
                updates.put(manifestPath + "fertilizer_application_count",
                        counts.fertilizerCount);
                updates.put(manifestPath + "journal_event_count", counts.eventCount);
                updates.put(manifestPath + "manual_journal_event_count",
                        counts.eventCount);
                updates.put(manifestPath + "photo_count", counts.photoCount);
                updates.put(manifestPath + "plant_assistant_analysis_count",
                        counts.analysisCount);
                updates.put(manifestPath + "notification_count",
                        counts.notificationCount);
                updates.put(manifestPath + "final_moisture",
                        (int) longValue(zoneData.child("moisture")));
                updates.put(manifestPath + "final_sensor_updated_at_epoch",
                        longValue(zoneData.child("updated_at_epoch")));
                updates.put(manifestPath + "updated_at_epoch", now);
            }

            return deviceRef.updateChildren(updates).continueWithTask(writeTask -> {
                if (writeTask.isSuccessful()) return Tasks.forResult(true);
                Exception error = writeTask.getException();
                return Tasks.forException(error == null
                        ? new IllegalStateException("Eski sezon kaydı düzeltilemedi.")
                        : error);
            });
        });
    }

    public Task<Void> startSeason(
            GardenZone zone,
            String plantingDate,
            String growthStage,
            String requestedLabel
    ) {
        return startSeason(
                zone,
                plantingDate,
                growthStage,
                requestedLabel,
                SeasonStartConfiguration.fromZone(zone)
        );
    }

    public Task<Void> startSeason(
            GardenZone zone,
            String plantingDate,
            String growthStage,
            String requestedLabel,
            SeasonStartConfiguration requestedConfiguration
    ) {
        if (zone == null || safe(zone.getZone_id()).isBlank()) {
            return Tasks.forException(new IllegalArgumentException("Bölge bilgisi gerekli."));
        }
        SeasonStartConfiguration configuration = requestedConfiguration == null
                ? SeasonStartConfiguration.fromZone(zone)
                : requestedConfiguration;
        if (!configuration.isValid()) {
            return Tasks.forException(new IllegalArgumentException("Yeni sezon için ürün bilgisi gerekli."));
        }
        FirebaseDatabase.getInstance().goOnline();
        return deviceRef.get()
                .continueWithTask(readTask -> {
                    if (!readTask.isSuccessful() || readTask.getResult() == null) {
                        Exception error = readTask.getException();
                        return Tasks.forException(error == null
                                ? new IllegalStateException("Yeni sezon için bölge verileri okunamadı.")
                                : error);
                    }
                    return startSeasonFromSnapshot(
                            zone,
                            safe(plantingDate),
                            growthStage,
                            requestedLabel,
                            configuration,
                            readTask.getResult(),
                            null
                    );
                });
    }

    /**
     * Starts the selected garden season and archives its source seedling batch in
     * the same multi-location write. Retrying the same transfer reuses its season ID.
     */
    public Task<Void> transferSeedlingToSeason(
            GardenZone zone,
            String plantingDate,
            String growthStage,
            String requestedLabel,
            SeasonStartConfiguration requestedConfiguration,
            SeedlingBatch batch
    ) {
        if (zone == null || safe(zone.getZone_id()).isBlank()) {
            return Tasks.forException(new IllegalArgumentException("Bölge bilgisi gerekli."));
        }
        if (batch == null || safe(batch.getBatch_id()).isBlank()) {
            return Tasks.forException(new IllegalArgumentException("Fide partisi gerekli."));
        }
        SeasonStartConfiguration configuration = requestedConfiguration == null
                ? SeasonStartConfiguration.fromZone(zone)
                : requestedConfiguration;
        if (!configuration.isValid()) {
            return Tasks.forException(new IllegalArgumentException(
                    "Yeni sezon için ürün bilgisi gerekli."));
        }
        FirebaseDatabase.getInstance().goOnline();
        return deviceRef.get().continueWithTask(readTask -> {
            if (!readTask.isSuccessful() || readTask.getResult() == null) {
                Exception error = readTask.getException();
                return Tasks.forException(error == null
                        ? new IllegalStateException("Fide aktarımı için veriler okunamadı.")
                        : error);
            }
            return startSeasonFromSnapshot(
                    zone,
                    safe(plantingDate),
                    growthStage,
                    requestedLabel,
                    configuration,
                    readTask.getResult(),
                    batch
            );
        });
    }

    /**
     * Reverses a seedling transfer only while its linked season is still untouched.
     * The season manifest and generated records are removed in the same atomic write
     * that returns the source batch to active seedling tracking.
     */
    public Task<Void> undoSeedlingTransfer(SeedlingBatch batch) {
        if (batch == null || safe(batch.getBatch_id()).isBlank()) {
            return Tasks.forException(new IllegalArgumentException("Fide partisi gerekli."));
        }
        if (!batch.isTransferred()) {
            return Tasks.forException(new IllegalStateException(
                    "Yalnızca sezona aktarılmış fide partisi geri alınabilir."));
        }
        FirebaseDatabase.getInstance().goOnline();
        String batchId = safe(batch.getBatch_id());
        return deviceRef.get().continueWithTask(readTask -> {
            if (!readTask.isSuccessful() || readTask.getResult() == null
                    || !readTask.getResult().exists()) {
                Exception error = readTask.getException();
                return Tasks.forException(error == null
                        ? new IllegalStateException("Sezona aktarım bilgileri okunamadı.")
                        : error);
            }
            return undoSeedlingTransferFromSnapshot(batchId, readTask.getResult());
        });
    }

    private Task<Void> undoSeedlingTransferFromSnapshot(
            String batchId,
            DataSnapshot root
    ) {
        DataSnapshot batchData = root.child("seedling").child("batches").child(batchId);
        SeedlingBatch persistedBatch = batchData.getValue(SeedlingBatch.class);
        if (persistedBatch == null) {
            return Tasks.forException(new IllegalStateException("Fide partisi bulunamadı."));
        }
        persistedBatch.setBatch_id(batchId);
        if (!persistedBatch.isTransferred()) {
            return Tasks.forException(new IllegalStateException(
                    "Bu fide partisi artık sezona bağlı değil."));
        }

        String seasonId = safe(persistedBatch.getTransferred_season_id());
        String zoneId = safe(persistedBatch.getTransferred_zone_id());
        if (seasonId.isBlank() || zoneId.isBlank()) {
            return Tasks.forException(new IllegalStateException(
                    "Fide partisinin sezon bağlantısı eksik."));
        }

        DataSnapshot zoneData = root.child("zones").child(zoneId);
        ZoneSeasonState state = zoneData.child("season").getValue(ZoneSeasonState.class);
        DataSnapshot manifest = root.child("garden_journal").child("seasons").child(seasonId);
        GardenSeason targetSeason = manifest.getValue(GardenSeason.class);
        GardenZone persistedZone = zoneData.getValue(GardenZone.class);
        if (state == null || persistedZone == null || targetSeason == null
                || !state.isSeasonActive(seasonId)
                || !SeasonStatus.isActive(targetSeason.getStatus())) {
            return Tasks.forException(new IllegalStateException(
                    "Bağlı sezon artık aktif olmadığı için aktarım geri alınamaz."));
        }
        if (!batchId.equals(safe(targetSeason.getSource_seedling_batch_id()))) {
            return Tasks.forException(new IllegalStateException(
                    "Sezon ile fide partisi bağlantısı doğrulanamadı."));
        }
        if (targetSeason.getSeason_id().isBlank()) targetSeason.setSeason_id(seasonId);
        if (!ZoneAreaIdentity.belongsToCurrentOrArea(persistedZone, targetSeason)) {
            return Tasks.forException(new IllegalStateException(
                    "Bağlı sezon artık bu bahçe alanına ait değil."));
        }

        ZoneSeasonState targetScope = seasonScopeFor(targetSeason);
        SeasonCounts counts = calculateCounts(root, zoneId, targetScope, false);
        boolean hasSeasonRecords = counts.wateringCount > 0
                || counts.fertilizerCount > 0
                || counts.eventCount > 0
                || counts.photoCount > 0
                || counts.analysisCount > 0;
        if (hasSeasonRecords) {
            return Tasks.forException(new IllegalStateException(
                    "Bu sezonda işlem kaydı oluştuğu için aktarım geri alınamaz. Sezonu normal şekilde kapatın."));
        }
        if (isZoneIrrigationBusy(root, zoneId)) {
            return Tasks.forException(new IllegalStateException(
                    "Sulama veya vana işlemi sürerken aktarım geri alınamaz."));
        }

        long now = nowEpoch();
        String zonePath = "zones/" + zoneId + "/";
        Map<String, Object> updates = new HashMap<>();
        List<GardenSeason> remaining = remainingActiveSeasons(root, persistedZone, seasonId);
        putSeasonState(updates, zonePath, remaining, now);
        if (remaining.isEmpty()) {
            DataSnapshot cancellationSnapshot = manifest.child("cancellation_snapshot");
            if (!cancellationSnapshot.exists()) {
                return Tasks.forException(new IllegalStateException(
                        "Önceki bahçe ayarları bulunamadığı için aktarım güvenle geri alınamıyor."));
            }
            restorePriorSeasonOrClose(updates, zonePath, cancellationSnapshot, state, now);
            restoreSnapshotObject(
                    updates, zonePath + "fertilization",
                    cancellationSnapshot.child("fertilization"));
            restoreSnapshotObject(
                    updates, zonePath + "ai", cancellationSnapshot.child("ai"));
            restoreZoneFields(updates, zonePath, cancellationSnapshot.child("zone"));
            putCancelledIrrigationState(updates, zonePath, now);
        } else {
            GardenSeason primary = remaining.get(0);
            updates.put(zonePath + "plant_type", primary.getPlant_type());
            updates.put(zonePath + "emoji", primary.getEmoji());
            updates.put(zonePath + "moisture_limit",
                    SharedIrrigationCompatibility.commonMinimumOrFallback(
                            remaining, persistedZone.getMoisture_limit()));
            updates.put(zonePath + "ai/season_id", primary.getSeason_id());
            updates.put(zonePath + "ai/season_status", SeasonStatus.ACTIVE);
            updates.put(zonePath + "ai/season_started_at_epoch",
                    primary.getStarted_at_epoch());
            updates.put(zonePath + "ai/season_closed_at_epoch", 0L);
            updates.put(zonePath + "ai/updated_at_epoch", now);
        }

        updates.put("garden_journal/seasons/" + seasonId, null);
        putEmptySeasonGeneratedRecordCleanup(updates, root, seasonId);
        putSeedlingTransferUndo(updates, batchId, now);
        return deviceRef.updateChildren(updates);
    }

    static void putSeedlingTransferUndo(
            Map<String, Object> updates,
            String batchId,
            long nowEpoch
    ) {
        long now = Math.max(0L, nowEpoch);
        String path = "seedling/batches/" + safe(batchId) + "/";
        updates.put(path + "status", SeedlingBatch.STATUS_ACTIVE);
        updates.put(path + "archive_reason", null);
        updates.put(path + "archived_at_epoch", 0L);
        updates.put(path + "transferred_season_id", null);
        updates.put(path + "transferred_zone_id", null);
        updates.put(path + "transferred_at_epoch", null);
        updates.put(path + "updated_at_epoch", now);
        updates.put("seedling/transfer_claims/" + safe(batchId), null);
    }

    /**
     * Starts a season with one atomic multi-location update. A transaction on the
     * complete device root cannot be used because Raspberry Pi measurements update
     * that root continuously and Firebase cancels the transaction as overridden.
     */
    private Task<Void> startSeasonFromSnapshot(
            GardenZone zone,
            String plantingDate,
            String growthStage,
            String requestedLabel,
            SeasonStartConfiguration configuration,
            DataSnapshot root,
            SeedlingBatch requestedBatch
    ) {
        String zoneId = zone.getZone_id();
        DataSnapshot zoneData = root.child("zones").child(zoneId);
        if (!zoneData.exists()) {
            return Tasks.forException(new IllegalStateException("Bölge bulunamadı."));
        }

        GardenZone persistedZone = zoneData.getValue(GardenZone.class);
        if (ZoneCapacityPolicy.isInactive(persistedZone)) {
            return Tasks.forException(new IllegalStateException(
                    "Kullanım dışı bölge için yeni sezon başlatılamaz. Bölgeyi önce yeniden etkinleştirin."
            ));
        }

        ZoneSeasonState current = zoneData.child("season").getValue(ZoneSeasonState.class);
        List<GardenSeason> activeSeasons = activeSeasonsForArea(root, persistedZone);
        SharedIrrigationCompatibility.Result compatibility =
                SharedIrrigationCompatibility.evaluate(activeSeasons, configuration);
        if (!compatibility.isCompatible()) {
            return Tasks.forException(new IllegalStateException(
                    "SHARED_IRRIGATION_INCOMPATIBLE"
            ));
        }
        boolean firstActiveSeason = current == null || !current.isActive();

        SeedlingBatch persistedBatch = null;
        if (requestedBatch != null) {
            String requestedBatchId = safe(requestedBatch.getBatch_id());
            DataSnapshot claimData = root.child("seedling").child("transfer_claims")
                    .child(requestedBatchId);
            if (claimData.exists()) {
                String claimedZone = stringValue(claimData.child("zone_id"));
                String claimedSeason = stringValue(claimData.child("season_id"));
                String requestedSeason = seedlingSeasonId(zoneId, requestedBatchId);
                if (!zoneId.equals(claimedZone) || !requestedSeason.equals(claimedSeason)) {
                    return Tasks.forException(new IllegalStateException(
                            "Bu fide partisi başka bir bahçe sezonu için ayrılmış."));
                }
            }
            DataSnapshot batchData = root.child("seedling").child("batches")
                    .child(requestedBatchId);
            persistedBatch = batchData.getValue(SeedlingBatch.class);
            if (persistedBatch == null) {
                return Tasks.forException(new IllegalStateException(
                        "Aktarılacak fide partisi bulunamadı."));
            }
            if (persistedBatch.isTransferred()) {
                if (sameSeedlingTransferTarget(persistedBatch, zoneId)) {
                    return Tasks.forResult(null);
                }
                return Tasks.forException(new IllegalStateException(
                        "Bu fide partisi başka bir bahçe sezonuna aktarılmış."));
            }
            if (!persistedBatch.isActive()
                    || !"READY".equalsIgnoreCase(safe(persistedBatch.getStage()))) {
                return Tasks.forException(new IllegalStateException(
                        "Yalnızca dikime hazır ve aktif fide partileri sezona aktarılabilir."));
            }
            persistedBatch.setBatch_id(safe(requestedBatch.getBatch_id()));
        }

        long now = nowEpoch();
        String seasonId = persistedBatch == null
                ? SeasonScope.createSeasonId(zoneId, now)
                : seedlingSeasonId(zoneId, persistedBatch.getBatch_id());
        String label = safe(requestedLabel).trim();
        if (label.isBlank()) label = seasonLabel(zone, now);
        String zonePath = "zones/" + zoneId + "/";
        String manifestPath = "garden_journal/seasons/" + seasonId + "/";
        Map<String, Object> updates = new HashMap<>();

        updates.put(zonePath + "plant_type", configuration.getPlantType());
        updates.put(zonePath + "emoji", configuration.getEmoji());
        updates.put(zonePath + "moisture_limit", compatibility.getCommonMin());

        putNewSeasonState(updates, zonePath, current, seasonId, label, now);
        putNewSeasonManifest(
                updates,
                manifestPath,
                zoneData,
                configuration,
                seasonId,
                label,
                plantingDate,
                growthStage,
                now
        );
        if (firstActiveSeason) {
            putNewSeasonFertilization(updates, zonePath, plantingDate, growthStage, now);
            putNewSeasonIrrigationState(updates, zonePath, now);
            putNewSeasonAiState(updates, zonePath, seasonId, now);
        } else {
            updates.put(zonePath + "ai/season_id", seasonId);
            updates.put(zonePath + "ai/updated_at_epoch", now);
        }
        if (persistedBatch != null) {
            putSeedlingTransfer(
                    updates,
                    manifestPath,
                    persistedBatch,
                    zoneId,
                    seasonId,
                    now
            );
        }
        return deviceRef.updateChildren(updates);
    }

    static String seedlingSeasonId(String zoneId, String batchId) {
        return safe(zoneId).replaceAll("[^A-Za-z0-9_-]", "-")
                + "-seedling-"
                + safe(batchId).replaceAll("[^A-Za-z0-9_-]", "-");
    }

    static boolean sameSeedlingTransferTarget(SeedlingBatch batch, String zoneId) {
        if (batch == null || !batch.isTransferred()) return false;
        String requestedZoneId = safe(zoneId);
        return requestedZoneId.equals(safe(batch.getTransferred_zone_id()))
                && seedlingSeasonId(requestedZoneId, batch.getBatch_id()).equals(
                        safe(batch.getTransferred_season_id()));
    }

    static void putSeedlingTransfer(
            Map<String, Object> updates,
            String manifestPath,
            SeedlingBatch batch,
            String zoneId,
            String seasonId,
            long nowEpoch
    ) {
        long now = Math.max(0L, nowEpoch);
        String batchPath = "seedling/batches/" + safe(batch.getBatch_id()) + "/";
        updates.put(manifestPath + "source_seedling_batch_id", batch.getBatch_id());
        updates.put(manifestPath + "source_seedling_variety", safe(batch.getVariety()));
        updates.put(manifestPath + "source_seedling_healthy_count", batch.getHealthy_count());

        updates.put(batchPath + "status", SeedlingBatch.STATUS_ARCHIVED);
        updates.put(batchPath + "archive_reason", SeedlingBatch.ARCHIVE_REASON_TRANSFERRED);
        updates.put(batchPath + "archived_at_epoch", now);
        updates.put(batchPath + "transferred_season_id", seasonId);
        updates.put(batchPath + "transferred_zone_id", safe(zoneId));
        updates.put(batchPath + "transferred_at_epoch", now);
        updates.put(batchPath + "updated_at_epoch", now);
        String claimPath = "seedling/transfer_claims/" + safe(batch.getBatch_id()) + "/";
        updates.put(claimPath + "batch_id", batch.getBatch_id());
        updates.put(claimPath + "zone_id", safe(zoneId));
        updates.put(claimPath + "season_id", seasonId);
    }

    private static void putNewSeasonState(
            Map<String, Object> updates,
            String zonePath,
            ZoneSeasonState current,
            String seasonId,
            String label,
            long now
    ) {
        String state = zonePath + "season/";
        if (current != null && current.isActive()) {
            if (current.getActive_season_ids() != null) {
                for (Map.Entry<String, Boolean> entry : current.getActive_season_ids().entrySet()) {
                    if (Boolean.TRUE.equals(entry.getValue())) {
                        updates.put(state + "active_season_ids/" + entry.getKey(), true);
                    }
                }
            }
            if (!current.getActive_season_id().isBlank()) {
                updates.put(state + "active_season_ids/" + current.getActive_season_id(), true);
            }
        }
        updates.put(state + "active_season_ids/" + seasonId, true);
        updates.put(state + "active_season_id", seasonId);
        updates.put(state + "status", SeasonStatus.ACTIVE);
        updates.put(state + "label", label);
        updates.put(state + "started_at_epoch", now);
        updates.put(state + "ended_at_epoch", 0L);
        updates.put(state + "include_legacy_records", false);
        updates.put(state + "updated_at_epoch", now);
    }

    private static void putNewSeasonManifest(
            Map<String, Object> updates,
            String path,
            DataSnapshot zoneData,
            SeasonStartConfiguration configuration,
            String seasonId,
            String label,
            String plantingDate,
            String growthStage,
            long now
    ) {
        updates.put(path + "season_id", seasonId);
        updates.put(path + "zone_id", safe(zoneData.getKey()));
        GardenZone storedZone = zoneData.getValue(GardenZone.class);
        updates.put(path + "area_id", ZoneAreaIdentity.effective(storedZone));
        updates.put(path + "area_name",
                storedZone == null ? "" : safe(storedZone.getArea_name()));
        updates.put(path + "zone_name", configuration.getCropName());
        updates.put(path + "plant_type", configuration.getPlantType());
        updates.put(path + "emoji", configuration.getEmoji());
        updates.put(path + "ideal_moisture_min", configuration.getIdealMoistureMin());
        updates.put(path + "ideal_moisture_max", configuration.getIdealMoistureMax());
        updates.put(path + "sensor_id", stringValue(zoneData.child("sensor_id")));
        updates.put(path + "sensor_enabled", booleanValue(zoneData.child("sensor_enabled")));
        updates.put(path + "valve_id", stringValue(zoneData.child("valve_id")));
        updates.put(path + "valve_mode", stringValue(zoneData.child("valve_mode")));
        updates.put(path + "label", label);
        updates.put(path + "status", SeasonStatus.ACTIVE);
        updates.put(path + "planting_date", plantingDate);
        updates.put(path + "growth_stage", normalizedGrowthStage(growthStage));
        updates.put(path + "started_at_epoch", now);
        updates.put(path + "ended_at_epoch", 0L);
        updates.put(path + "includes_legacy_records", false);
        updates.put(path + "created_at_epoch", now);
        updates.put(path + "updated_at_epoch", now);
        putCancellationSnapshot(updates, path, zoneData);
    }

    private static List<GardenSeason> activeSeasonsForArea(
            DataSnapshot root,
            GardenZone zone
    ) {
        List<GardenSeason> result = new ArrayList<>();
        ZoneSeasonState current = zone == null
                ? null
                : zone.getSeason();
        for (DataSnapshot child : root.child("garden_journal").child("seasons").getChildren()) {
            GardenSeason season = child.getValue(GardenSeason.class);
            if (season == null) continue;
            if (season.getSeason_id().isBlank()) season.setSeason_id(safe(child.getKey()));
            if (SeasonScope.isCurrentActiveSeason(season, current)
                    && ZoneAreaIdentity.belongsToCurrentOrArea(zone, season)) {
                result.add(season);
            }
        }
        return result;
    }

    /**
     * Keeps only the pre-season configuration needed to undo an untouched new season.
     * Historical records are never copied, moved or deleted by this snapshot.
     */
    private static void putCancellationSnapshot(
            Map<String, Object> updates,
            String manifestPath,
            DataSnapshot zoneData
    ) {
        String path = manifestPath + "cancellation_snapshot/";
        updates.put(path + "season", zoneData.child("season").getValue());
        updates.put(path + "fertilization", zoneData.child("fertilization").getValue());
        updates.put(path + "ai", zoneData.child("ai").getValue());

        String[] fields = {
                "area_name", "location_name", "area_icon", "area_color",
                "low_moisture_alert_enabled", "watering_complete_alert_enabled",
                "name", "plant_type", "emoji", "sensor_id", "sensor_enabled",
                "sensor_config_updated_at_epoch", "irrigation_enabled", "moisture",
                "moisture_limit", "raw", "voltage", "rssi", "updated_at_epoch"
        };
        for (String field : fields) {
            updates.put(path + "zone/" + field, zoneData.child(field).getValue());
        }
    }

    private static void putNewSeasonFertilization(
            Map<String, Object> updates,
            String zonePath,
            String plantingDate,
            String growthStage,
            long now
    ) {
        String path = zonePath + "fertilization/";
        updates.put(path + "planting_date", plantingDate);
        updates.put(path + "growth_stage", normalizedGrowthStage(growthStage));
        updates.put(path + "enabled", false);
        updates.put(path + "reminder_enabled", false);
        updates.put(path + "active_plan_id", "");
        updates.put(path + "active_product_id", "");
        updates.put(path + "next_application_at_epoch", 0L);
        updates.put(path + "last_application_at_epoch", 0L);
        updates.put(path + "application_schedules", null);
        updates.put(path + "updated_at_epoch", now);
    }

    private static void putNewSeasonIrrigationState(
            Map<String, Object> updates,
            String zonePath,
            long now
    ) {
        String path = zonePath + "irrigation_status/";
        updates.put(path + "decision", "WAIT");
        updates.put(path + "decision_reason", "NEW_SEASON_STARTED");
        updates.put(path + "cooldown_active", false);
        updates.put(path + "cooldown_remaining", 0);
        updates.put(path + "queue_position", 0);
        updates.put(path + "selected_for_watering", false);
        updates.put(path + "watering_active", false);
        updates.put(path + "completed_watering_cycles", 0);
        updates.put(path + "waiting_for_moisture_recovery", false);
        updates.put(path + "updated_at_epoch", now);
    }

    private static void putNewSeasonAiState(
            Map<String, Object> updates,
            String zonePath,
            String seasonId,
            long now
    ) {
        String path = zonePath + "ai/";
        updates.put(path + "decision", null);
        updates.put(path + "explanation", null);
        updates.put(path + "moisture_prediction", null);
        updates.put(path + "prediction_accuracy", null);
        updates.put(path + "confidence", null);
        updates.put(path + "adaptive_recommendation", null);
        updates.put(path + "prediction_validation", null);
        updates.put(path + "season_id", seasonId);
        updates.put(path + "season_status", SeasonStatus.ACTIVE);
        updates.put(path + "season_started_at_epoch", now);
        updates.put(path + "season_closed_at_epoch", 0L);
        updates.put(path + "learning_status", "LEARNING");
        updates.put(path + "updated_at_epoch", now);
    }
    public Task<Boolean> canCancelNewSeason(String zoneId) {
        return canCancelNewSeason(zoneId, "");
    }

    public Task<Boolean> canCancelNewSeason(String zoneId, String seasonId) {
        if (safe(zoneId).isBlank()) return Tasks.forResult(false);
        return deviceRef.get().continueWith(task -> {
            if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) {
                return false;
            }
            return evaluateCancellation(zoneId, seasonId, task.getResult()).allowed;
        });
    }

    public Task<Void> cancelNewSeason(String zoneId) {
        return cancelNewSeason(zoneId, "");
    }

    public Task<Void> cancelNewSeason(String zoneId, String seasonId) {
        if (safe(zoneId).isBlank()) {
            return Tasks.forException(new IllegalArgumentException("Bölge bilgisi gerekli."));
        }
        FirebaseDatabase.getInstance().goOnline();
        return deviceRef.get()
                .continueWithTask(readTask -> {
                    if (!readTask.isSuccessful() || readTask.getResult() == null
                            || !readTask.getResult().exists()) {
                        Exception error = readTask.getException();
                        return Tasks.forException(error == null
                                ? new IllegalStateException("Sezon verileri okunamadı.")
                                : error);
                    }
                    return cancelNewSeasonFromSnapshot(zoneId, seasonId, readTask.getResult());
                });
    }

    public Task<EmptySeasonDeletionStatus> inspectEmptySeason(
            String zoneId,
            String seasonId,
            Set<String> localPhotoIds
    ) {
        if (safe(zoneId).isBlank() || safe(seasonId).isBlank()) {
            return Tasks.forException(new IllegalArgumentException(
                    "Bölge ve sezon bilgisi gerekli."));
        }
        return deviceRef.get().continueWith(task -> {
            if (!task.isSuccessful() || task.getResult() == null
                    || !task.getResult().exists()) {
                Exception error = task.getException();
                if (error != null) throw error;
                throw new IllegalStateException("Sezon verileri okunamadı.");
            }
            return evaluateEmptySeasonDeletion(
                    zoneId,
                    seasonId,
                    task.getResult(),
                    localPhotoIds
            );
        });
    }

    public Task<Boolean> canDeleteEmptySeason(String zoneId, String seasonId) {
        return inspectEmptySeason(zoneId, seasonId, new LinkedHashSet<>())
                .continueWith(task -> task.isSuccessful()
                        && task.getResult() != null
                        && task.getResult().canDelete());
    }

    public Task<Void> deleteEmptySeason(
            String zoneId,
            String seasonId,
            Set<String> localPhotoIds
    ) {
        if (safe(zoneId).isBlank() || safe(seasonId).isBlank()) {
            return Tasks.forException(new IllegalArgumentException(
                    "Bölge ve sezon bilgisi gerekli."));
        }
        FirebaseDatabase.getInstance().goOnline();
        return deviceRef.get().continueWithTask(task -> {
            if (!task.isSuccessful() || task.getResult() == null
                    || !task.getResult().exists()) {
                Exception error = task.getException();
                return Tasks.forException(error == null
                        ? new IllegalStateException("Sezon verileri okunamadı.")
                        : error);
            }
            DataSnapshot root = task.getResult();
            EmptySeasonDeletionStatus check = evaluateEmptySeasonDeletion(
                    zoneId,
                    seasonId,
                    root,
                    localPhotoIds
            );
            if (!check.canDelete()) {
                return Tasks.forException(new IllegalStateException(check.getReason()));
            }

            GardenSeason target = root.child("garden_journal")
                    .child("seasons")
                    .child(seasonId)
                    .getValue(GardenSeason.class);
            if (target != null && SeasonStatus.isActive(target.getStatus())) {
                return cancelNewSeasonFromSnapshot(zoneId, seasonId, root);
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("garden_journal/seasons/" + seasonId, null);
            putEmptySeasonGeneratedRecordCleanup(updates, root, seasonId);
            return deviceRef.updateChildren(updates);
        });
    }

    public Task<Void> deleteEmptySeason(String zoneId, String seasonId) {
        return deleteEmptySeason(zoneId, seasonId, new LinkedHashSet<>());
    }

    public Task<EmptySeasonDeletionStatus> cleanupMissingPhotoRecords(
            String zoneId,
            String seasonId,
            Set<String> localPhotoIds
    ) {
        if (safe(zoneId).isBlank() || safe(seasonId).isBlank()) {
            return Tasks.forException(new IllegalArgumentException(
                    "Bölge ve sezon bilgisi gerekli."));
        }
        FirebaseDatabase.getInstance().goOnline();
        return deviceRef.get().continueWithTask(task -> {
            if (!task.isSuccessful() || task.getResult() == null
                    || !task.getResult().exists()) {
                Exception error = task.getException();
                return Tasks.forException(error == null
                        ? new IllegalStateException("Sezon verileri okunamadı.")
                        : error);
            }
            DataSnapshot root = task.getResult();
            EmptySeasonDeletionStatus status = evaluateEmptySeasonDeletion(
                    zoneId,
                    seasonId,
                    root,
                    localPhotoIds
            );
            if (!status.canCleanMissingPhotos()) {
                return Tasks.forException(new IllegalStateException(
                        "Temizlenecek eksik fotoğraf kaydı bulunamadı."));
            }

            Map<String, Object> updates = new HashMap<>();
            Set<String> missingIds = status.getMissingPhotoIds();
            for (String photoId : missingIds) {
                updates.put("garden_journal/photo_metadata/" + photoId, null);
            }
            putPhotoDerivedRecordCleanup(updates, root, seasonId, missingIds);
            return deviceRef.updateChildren(updates)
                    .continueWithTask(write -> {
                        if (!write.isSuccessful()) {
                            return Tasks.forException(write.getException());
                        }
                        return inspectEmptySeason(
                                zoneId,
                                seasonId,
                                localPhotoIds
                        );
                    });
        });
    }

    private Task<Void> cancelNewSeasonFromSnapshot(
            String zoneId, String requestedSeasonId, DataSnapshot root) {
        CancellationCheck check = evaluateCancellation(zoneId, requestedSeasonId, root);
        if (!check.allowed) {
            return Tasks.forException(new IllegalStateException(check.message));
        }

        DataSnapshot zoneData = root.child("zones").child(zoneId);
        ZoneSeasonState state = zoneData.child("season").getValue(ZoneSeasonState.class);
        if (state == null) {
            return Tasks.forException(new IllegalStateException("İptal edilecek sezon bulunamadı."));
        }

        String seasonId = requestedSeasonId == null || requestedSeasonId.isBlank()
                ? state.getActive_season_id() : requestedSeasonId.trim();
        GardenZone persistedZone = zoneData.getValue(GardenZone.class);
        if (persistedZone == null) {
            return Tasks.forException(new IllegalStateException("Bölge bilgisi bulunamadı."));
        }
        long now = nowEpoch();
        String zonePath = "zones/" + zoneId + "/";
        Map<String, Object> updates = new HashMap<>();

        List<GardenSeason> remaining = remainingActiveSeasons(root, persistedZone, seasonId);
        putSeasonState(updates, zonePath, remaining, now);
        if (remaining.isEmpty()) {
            updates.put(zonePath + "irrigation_enabled", false);
            updates.put(zonePath + "fertilization/enabled", false);
            updates.put(zonePath + "fertilization/reminder_enabled", false);
            updates.put(zonePath + "fertilization/next_application_at_epoch", 0L);
            updates.put(zonePath + "fertilization/updated_at_epoch", now);
            updates.put(zonePath + "ai/season_id", "");
            updates.put(zonePath + "ai/season_status", SeasonStatus.CLOSED);
            updates.put(zonePath + "ai/season_started_at_epoch", 0L);
            updates.put(zonePath + "ai/season_closed_at_epoch", 0L);
            updates.put(zonePath + "ai/updated_at_epoch", now);
            putCancelledIrrigationState(updates, zonePath, now);
        } else {
            GardenSeason primary = remaining.get(0);
            updates.put(zonePath + "plant_type", primary.getPlant_type());
            updates.put(zonePath + "emoji", primary.getEmoji());
            updates.put(zonePath + "moisture_limit",
                    SharedIrrigationCompatibility.commonMinimumOrFallback(
                            remaining, persistedZone.getMoisture_limit()));
            updates.put(zonePath + "ai/season_id", primary.getSeason_id());
            updates.put(zonePath + "ai/season_status", SeasonStatus.ACTIVE);
            updates.put(zonePath + "ai/season_started_at_epoch",
                    primary.getStarted_at_epoch());
            updates.put(zonePath + "ai/season_closed_at_epoch", 0L);
            updates.put(zonePath + "ai/updated_at_epoch", now);
        }

        // Only the untouched, currently active manifest is removed. Closed archives remain intact.
        updates.put("garden_journal/seasons/" + seasonId, null);
        putEmptySeasonGeneratedRecordCleanup(updates, root, seasonId);
        return deviceRef.updateChildren(updates);
    }

    private static EmptySeasonDeletionStatus evaluateEmptySeasonDeletion(
            String zoneId,
            String seasonId,
            DataSnapshot root,
            Set<String> localPhotoIds
    ) {
        DataSnapshot manifest = root.child("garden_journal")
                .child("seasons")
                .child(seasonId);
        GardenSeason target = manifest.getValue(GardenSeason.class);
        if (target == null) {
            return EmptySeasonDeletionStatus.blocked(
                    "Silinecek sezon bulunamadı.");
        }
        if (target.getSeason_id().isBlank()) target.setSeason_id(seasonId);
        if (!safe(zoneId).equals(safe(target.getZone_id()))) {
            return EmptySeasonDeletionStatus.blocked(
                    "Sezon bu bölgeye ait değil.");
        }

        DataSnapshot zoneData = root.child("zones").child(zoneId);
        GardenZone persistedZone = zoneData.getValue(GardenZone.class);
        ZoneSeasonState current = zoneData.child("season")
                .getValue(ZoneSeasonState.class);
        boolean currentActive = persistedZone != null
                && current != null
                && current.isActive()
                && current.isSeasonActive(seasonId)
                && ZoneAreaIdentity.belongsToCurrentOrArea(
                        persistedZone,
                        target
                );

        ZoneSeasonState targetScope = seasonScopeFor(target);
        SeasonCounts counts = calculateCounts(root, zoneId, targetScope, true);
        Set<String> validLocalIds = localPhotoIds == null
                ? new LinkedHashSet<>() : new LinkedHashSet<>(localPhotoIds);
        Set<String> missingPhotoIds = new LinkedHashSet<>();
        int localPhotoCount = 0;
        int missingPhotoAnalysisCount = 0;
        for (DataSnapshot photo : root.child("garden_journal")
                .child("photo_metadata")
                .getChildren()) {
            if (!zoneId.equals(stringValue(photo.child("zone_id")))
                    || !belongs(photo, targetScope)) {
                continue;
            }
            String photoId = safe(photo.getKey());
            if (!photoId.isBlank() && validLocalIds.contains(photoId)) {
                localPhotoCount++;
                continue;
            }
            if (!photoId.isBlank()) missingPhotoIds.add(photoId);
            if (!stringValue(photo.child("analysis_title")).isBlank()) {
                missingPhotoAnalysisCount++;
            }
        }
        SeasonOutcome outcome = root.child("garden_journal")
                .child("season_outcomes")
                .child(seasonId)
                .getValue(SeasonOutcome.class);
        boolean meaningfulOutcome = SeasonRecordPolicy.hasMeaningfulOutcome(outcome)
                || SeasonRecordPolicy.hasMeaningfulOutcomeValues(
                        target.getHarvest_amount(),
                        target.getYield_note(),
                        target.getIssues_note(),
                        target.getSuccessful_practices(),
                        "",
                        "",
                        target.getNext_season_note()
                );
        boolean hasRecords = counts.wateringCount > 0
                || counts.fertilizerCount > 0
                || counts.eventCount > 0
                || counts.photoCount > 0
                || meaningfulOutcome;
        boolean irrigationBusy = SeasonStatus.isActive(target.getStatus())
                && persistedZone != null
                && isZoneIrrigationBusy(root, zoneId);

        boolean canDelete = SeasonScope.canDeleteEmptySeason(
                target,
                currentActive,
                hasRecords,
                irrigationBusy
        );
        boolean stateAllowsCleanup = !target.isIncludes_legacy_records()
                && (SeasonStatus.isClosed(target.getStatus())
                || (SeasonStatus.isActive(target.getStatus())
                && currentActive
                && !irrigationBusy));
        String reason;
        if (canDelete) {
            reason = "";
        } else if (!missingPhotoIds.isEmpty()) {
            reason = "Fotoğraf dosyası bu telefonda bulunmayan kayıtlar var.";
        } else if (target.isIncludes_legacy_records()) {
            reason = "Eski kayıtları içeren sezon silinemez.";
        } else if (counts.wateringCount > 0
                || counts.fertilizerCount > 0
                || counts.eventCount > 0
                || localPhotoCount > 0
                || meaningfulOutcome) {
            reason = "Bu sezonda korunması gereken işlem kayıtları var.";
        } else if (irrigationBusy) {
            reason = "Sulama, pompa veya vana çalışırken sezon silinemez.";
        } else {
            reason = "Yalnızca boş etkin veya boş tamamlanmış sezon silinebilir.";
        }
        return new EmptySeasonDeletionStatus(
                canDelete,
                stateAllowsCleanup && !missingPhotoIds.isEmpty(),
                reason,
                counts.wateringCount,
                counts.fertilizerCount,
                counts.eventCount,
                localPhotoCount,
                missingPhotoIds,
                missingPhotoAnalysisCount,
                meaningfulOutcome
        );
    }

    private static void putPhotoDerivedRecordCleanup(
            Map<String, Object> updates,
            DataSnapshot root,
            String seasonId,
            Set<String> photoIds
    ) {
        for (DataSnapshot event : root.child("garden_journal")
                .child("events")
                .getChildren()) {
            if (!seasonId.equals(stringValue(event.child("season_id")))
                    || SeasonRecordPolicy.isFieldJournalEvent(
                    stringValue(event.child("type")),
                    stringValue(event.child("source")),
                    stringValue(event.child("source_key")))
                    || !referencesAnyPhoto(
                    stringValue(event.child("source_key")),
                    photoIds)) {
                continue;
            }
            String key = safe(event.getKey());
            if (!key.isBlank()) updates.put("garden_journal/events/" + key, null);
        }
        long deletedAtEpoch = nowEpoch();
        for (DataSnapshot notification : root.child("notifications").getChildren()) {
            if (!seasonId.equals(stringValue(notification.child("season_id")))
                    || !referencesAnyPhoto(
                    stringValue(notification.child("source_key")),
                    photoIds)) {
                continue;
            }
            String key = safe(notification.getKey());
            if (key.isBlank()) continue;
            updates.put("notifications/" + key, null);
            updates.put(
                    "notification_deletions/" + key + "/source_key",
                    stringValue(notification.child("source_key"))
            );
            updates.put(
                    "notification_deletions/" + key + "/deleted_at_epoch",
                    deletedAtEpoch
            );
        }
    }

    private static boolean referencesAnyPhoto(
            String sourceKey,
            Set<String> photoIds
    ) {
        String source = safe(sourceKey);
        if (source.isBlank() || photoIds == null || photoIds.isEmpty()) {
            return false;
        }
        for (String photoId : photoIds) {
            if (!safe(photoId).isBlank() && source.contains(photoId)) return true;
        }
        return false;
    }

    private static void putEmptySeasonGeneratedRecordCleanup(
            Map<String, Object> updates,
            DataSnapshot root,
            String seasonId
    ) {
        updates.put("garden_journal/season_outcomes/" + seasonId, null);
        for (DataSnapshot event : root.child("garden_journal")
                .child("events")
                .getChildren()) {
            if (!seasonId.equals(stringValue(event.child("season_id")))) continue;
            if (SeasonRecordPolicy.isFieldJournalEvent(
                    stringValue(event.child("type")),
                    stringValue(event.child("source")),
                    stringValue(event.child("source_key")))) {
                continue;
            }
            String key = safe(event.getKey());
            if (!key.isBlank()) updates.put("garden_journal/events/" + key, null);
        }
        for (DataSnapshot notification : root.child("notifications").getChildren()) {
            if (!seasonId.equals(stringValue(notification.child("season_id")))) {
                continue;
            }
            String key = safe(notification.getKey());
            if (!key.isBlank()) updates.put("notifications/" + key, null);
        }
    }

    private static CancellationCheck evaluateCancellation(
            String zoneId, String requestedSeasonId, DataSnapshot root) {
        DataSnapshot zoneData = root.child("zones").child(zoneId);
        ZoneSeasonState state = zoneData.child("season").getValue(ZoneSeasonState.class);
        if (state == null || !state.isActive() || safe(state.getActive_season_id()).isBlank()) {
            return CancellationCheck.blocked("İptal edilecek aktif sezon bulunamadı.");
        }
        String seasonId = safe(requestedSeasonId).isBlank()
                ? state.getActive_season_id() : safe(requestedSeasonId);
        if (!state.isSeasonActive(seasonId)) {
            return CancellationCheck.blocked("Silinecek aktif sezon bulunamadı.");
        }

        DataSnapshot manifest = root.child("garden_journal")
                .child("seasons")
                .child(seasonId);

        GardenSeason targetSeason = manifest.getValue(GardenSeason.class);
        if (targetSeason == null) {
            return CancellationCheck.blocked("İptal edilecek sezon kaydı bulunamadı.");
        }
        if (!targetSeason.getSource_seedling_batch_id().isBlank()) {
            return CancellationCheck.blocked(
                    "Bu sezon bir fide partisinden oluşturulduğu için silinemez; sezonu kapatın.");
        }
        if (targetSeason.getSeason_id().isBlank()) {
            targetSeason.setSeason_id(safe(manifest.getKey()));
        }
        GardenZone persistedZone = zoneData.getValue(GardenZone.class);
        if (persistedZone == null
                || !SeasonStatus.isActive(targetSeason.getStatus())
                || !ZoneAreaIdentity.belongsToCurrentOrArea(persistedZone, targetSeason)) {
            return CancellationCheck.blocked("Silinecek aktif sezon bulunamadı.");
        }
        ZoneSeasonState targetScope = seasonScopeFor(targetSeason);
        SeasonCounts counts = calculateCounts(root, zoneId, targetScope, false);
        boolean hasSeasonRecords = counts.wateringCount > 0
                || counts.fertilizerCount > 0
                || counts.eventCount > 0
                || counts.photoCount > 0
                || counts.analysisCount > 0;
        boolean irrigationBusy = isZoneIrrigationBusy(root, zoneId);
        if (SeasonScope.canCancelNewSeason(
                targetScope,
                "",
                hasSeasonRecords,
                irrigationBusy
        )) {
            return CancellationCheck.allowed();
        }
        if (targetSeason.isIncludes_legacy_records()) {
            return CancellationCheck.blocked("Arşivlenmiş veya eski kayıtları içeren sezon silinemez.");
        }
        if (hasSeasonRecords) {
            return CancellationCheck.blocked(
                    "Bu sezonda işlem kaydı bulunduğu için sezon silinemez; sezonu kapatın."
            );
        }
        return CancellationCheck.blocked(
                "Sulama, pompa, vana veya sulama kuyruğu çalışırken sezon iptal edilemez."
        );
    }

    private static boolean isZoneIrrigationBusy(DataSnapshot root, String zoneId) {
        DataSnapshot zone = root.child("zones").child(zoneId);
        DataSnapshot irrigation = zone.child("irrigation_status");
        boolean pending = hasPendingWateringForZone(root, zoneId);
        boolean hardwareBusy = booleanValue(root.child("status").child("relay"))
                || booleanValue(root.child("status").child("valve_open"))
                || booleanValue(root.child("commands").child("relay"))
                || booleanValue(root.child("irrigation_hardware").child("valve_open"));
        String activeZoneId = firstNonBlank(
                stringValue(root.child("status").child("active_zone_id")),
                stringValue(root.child("irrigation_runtime").child("active_zone_id")));
        String activeValveId = firstNonBlank(
                stringValue(root.child("status").child("active_valve_id")),
                stringValue(root.child("irrigation_hardware").child("active_valve_id")));
        return ZoneOperationSafetyPolicy.isTargetBusy(
                zoneId,
                stringValue(zone.child("valve_id")),
                booleanValue(irrigation.child("watering_active")),
                booleanValue(irrigation.child("selected_for_watering")),
                longValue(irrigation.child("queue_position")),
                pending,
                hardwareBusy,
                activeZoneId,
                activeValveId);
    }

    private static boolean hasPendingWateringForZone(DataSnapshot root, String zoneId) {
        for (DataSnapshot pending : root.child("irrigation_runtime")
                .child("pending_waterings").getChildren()) {
            DataSnapshot record = pending.child("record");
            if (zoneId.equals(stringValue(record.child("zone_id")))) return true;
        }
        return false;
    }

    private static String firstNonBlank(String first, String second) {
        return safe(first).isBlank() ? safe(second) : safe(first);
    }

    private static void restorePriorSeasonOrClose(
            Map<String, Object> updates,
            String zonePath,
            DataSnapshot snapshot,
            ZoneSeasonState cancelledState,
            long now
    ) {
        DataSnapshot previous = snapshot.child("season");
        if (previous.exists() && previous.getValue() != null) {
            updates.put(zonePath + "season", previous.getValue());
            return;
        }
        String path = zonePath + "season/";
        updates.put(path + "active_season_id", "");
        updates.put(path + "status", SeasonStatus.CLOSED);
        updates.put(path + "label", cancelledState.getLabel());
        updates.put(path + "started_at_epoch", 0L);
        updates.put(path + "ended_at_epoch", 0L);
        updates.put(path + "include_legacy_records", false);
        updates.put(path + "updated_at_epoch", now);
    }

    private static void restoreSnapshotObject(
            Map<String, Object> updates,
            String targetPath,
            DataSnapshot snapshot
    ) {
        updates.put(targetPath, snapshot.exists() ? snapshot.getValue() : null);
    }

    private static void restoreZoneFields(
            Map<String, Object> updates,
            String zonePath,
            DataSnapshot snapshot
    ) {
        String[] fields = {
                "area_name", "location_name", "area_icon", "area_color",
                "low_moisture_alert_enabled", "watering_complete_alert_enabled",
                "name", "plant_type", "emoji", "sensor_id", "sensor_enabled",
                "sensor_config_updated_at_epoch", "irrigation_enabled", "moisture",
                "moisture_limit", "raw", "voltage",
                "rssi", "updated_at_epoch"
        };
        for (String field : fields) {
            updates.put(zonePath + field,
                    snapshot.hasChild(field) ? snapshot.child(field).getValue() : null);
        }
    }

    private static void putCancelledIrrigationState(
            Map<String, Object> updates,
            String zonePath,
            long now
    ) {
        String path = zonePath + "irrigation_status/";
        updates.put(path + "decision", "WAIT");
        updates.put(path + "decision_reason", "SEASON_CANCELLED");
        updates.put(path + "cooldown_active", false);
        updates.put(path + "cooldown_remaining", 0);
        updates.put(path + "queue_position", 0);
        updates.put(path + "selected_for_watering", false);
        updates.put(path + "watering_active", false);
        updates.put(path + "waiting_for_moisture_recovery", false);
        updates.put(path + "updated_at_epoch", now);
    }
    public Task<Void> closeSeason(String zoneId, SeasonOutcome outcome) {
        return closeSeason(zoneId, "", outcome);
    }

    public Task<Void> closeSeason(String zoneId, String requestedSeasonId, SeasonOutcome outcome) {
        if (safe(zoneId).isBlank()) {
            return Tasks.forException(new IllegalArgumentException("Bölge bilgisi gerekli."));
        }
        long now = nowEpoch();
        return deviceRef.get().continueWithTask(task -> {
            if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) {
                Exception error = task.getException();
                return Tasks.forException(error == null
                        ? new IllegalStateException("Sezon verileri okunamadı.")
                        : error);
            }
            return closeSeasonFromSnapshot(zoneId, requestedSeasonId, outcome, now, task.getResult());
        });
    }

    private Task<Void> closeSeasonFromSnapshot(
            String zoneId,
            String requestedSeasonId,
            SeasonOutcome outcome,
            long now,
            DataSnapshot root
    ) {
        DataSnapshot zoneData = root.child("zones").child(zoneId);
        ZoneSeasonState state = zoneData.child("season").getValue(ZoneSeasonState.class);
        String seasonId = safe(requestedSeasonId).isBlank()
                ? safe(state == null ? "" : state.getActive_season_id())
                : safe(requestedSeasonId);
        if (state == null || !state.isActive() || seasonId.isBlank()
                || !state.isSeasonActive(seasonId)) {
            return Tasks.forException(new IllegalStateException("Kapatılacak aktif sezon bulunamadı."));
        }

        GardenZone persistedZone = zoneData.getValue(GardenZone.class);
        DataSnapshot manifestData = root.child("garden_journal")
                .child("seasons")
                .child(seasonId);
        GardenSeason targetSeason = manifestData.getValue(GardenSeason.class);
        if (persistedZone == null || targetSeason == null
                || !SeasonStatus.isActive(targetSeason.getStatus())
                || !ZoneAreaIdentity.belongsToCurrentOrArea(persistedZone, targetSeason)) {
            return Tasks.forException(new IllegalStateException("Kapatılacak aktif sezon bulunamadı."));
        }

        if (isZoneIrrigationBusy(root, zoneId)) {
            return Tasks.forException(new IllegalStateException(
                    "Sulama, pompa, vana veya sulama kuyruğu çalışırken sezon kapatılamaz."
            ));
        }

        ZoneSeasonState targetScope = seasonScopeFor(targetSeason);
        boolean pendingFinalMeasurement = hasPendingWateringForSeason(root, zoneId, seasonId);
        SeasonCounts counts = calculateCounts(root, zoneId, targetScope, pendingFinalMeasurement);
        String manifest = "garden_journal/seasons/" + seasonId + "/";
        String zone = "zones/" + zoneId + "/";
        Map<String, Object> updates = new HashMap<>();

        updates.put(manifest + "snapshots/ai", zoneData.child("ai").getValue());
        updates.put(manifest + "snapshots/irrigation_status",
                zoneData.child("irrigation_status").getValue());
        updates.put(manifest + "snapshots/fertilization",
                zoneData.child("fertilization").getValue());
        putSnapshotSetting(updates, manifest, zoneData, "area_m2");
        putSnapshotSetting(updates, manifest, zoneData, "tank_volume_l");
        putSnapshotSetting(updates, manifest, zoneData, "moisture_limit");
        putSnapshotSetting(updates, manifest, zoneData, "pump_duration");
        putSnapshotSetting(updates, manifest, zoneData, "cooldown_seconds");
        putSnapshotSetting(updates, manifest, zoneData, "restart_delta");
        updates.put(manifest + "snapshots/zone_settings/captured_at_epoch", now);

        updates.put(manifest + "status", SeasonStatus.CLOSED);
        updates.put(manifest + "ended_at_epoch", now);
        updates.put(manifest + "watering_count", counts.wateringCount);
        updates.put(manifest + "watering_seconds", counts.wateringSeconds);
        updates.put(manifest + "fertilizer_application_count", counts.fertilizerCount);
        updates.put(manifest + "journal_event_count", counts.eventCount);
        updates.put(manifest + "manual_journal_event_count", counts.eventCount);
        updates.put(manifest + "photo_count", counts.photoCount);
        updates.put(manifest + "plant_assistant_analysis_count", counts.analysisCount);
        updates.put(manifest + "notification_count", counts.notificationCount);
        updates.put(manifest + "pending_final_measurement", pendingFinalMeasurement);
        updates.put(manifest + "final_moisture", (int) longValue(zoneData.child("moisture")));
        updates.put(manifest + "final_sensor_updated_at_epoch",
                longValue(zoneData.child("updated_at_epoch")));
        putOutcome(updates, manifest, outcome, seasonId, zoneId, now);
        updates.put(manifest + "updated_at_epoch", now);

        List<GardenSeason> remaining = remainingActiveSeasons(root, persistedZone, seasonId);
        putSeasonState(updates, zone, remaining, now);
        if (remaining.isEmpty()) {
            updates.put(zone + "irrigation_enabled", false);
            updates.put(zone + "fertilization/enabled", false);
            updates.put(zone + "fertilization/reminder_enabled", false);
            updates.put(zone + "fertilization/next_application_at_epoch", 0L);
            updates.put(zone + "fertilization/updated_at_epoch", now);
            updates.put(zone + "ai/season_id", "");
            updates.put(zone + "ai/season_status", SeasonStatus.CLOSED);
            updates.put(zone + "ai/season_closed_at_epoch", now);
            updates.put(zone + "ai/updated_at_epoch", now);
        } else {
            GardenSeason primary = remaining.get(0);
            updates.put(zone + "moisture_limit",
                    SharedIrrigationCompatibility.commonMinimumOrFallback(
                            remaining, persistedZone.getMoisture_limit()));
            updates.put(zone + "plant_type", primary.getPlant_type());
            updates.put(zone + "emoji", primary.getEmoji());
            updates.put(zone + "ai/season_id", primary.getSeason_id());
            updates.put(zone + "ai/season_status", SeasonStatus.ACTIVE);
            updates.put(zone + "ai/season_closed_at_epoch", 0L);
            updates.put(zone + "ai/updated_at_epoch", now);
        }

        String outcomePath = "garden_journal/season_outcomes/" + seasonId + "/";
        putOutcome(updates, outcomePath, outcome, seasonId, zoneId, now);
        updates.put(outcomePath + "id", seasonId);
        return deviceRef.updateChildren(updates);
    }

    private static void putSnapshotSetting(
            Map<String, Object> updates,
            String manifest,
            DataSnapshot zoneData,
            String key
    ) {
        updates.put(manifest + "snapshots/zone_settings/" + key,
                zoneData.child(key).getValue());
    }

    private static void putSeasonState(
            Map<String, Object> updates,
            String zone,
            List<GardenSeason> remaining,
            long now
    ) {
        if (remaining.isEmpty()) {
            updates.put(zone + "season/active_season_ids", null);
            updates.put(zone + "season/active_season_id", "");
            updates.put(zone + "season/status", SeasonStatus.CLOSED);
            updates.put(zone + "season/label", "");
            updates.put(zone + "season/started_at_epoch", 0L);
            updates.put(zone + "season/ended_at_epoch", now);
            updates.put(zone + "season/include_legacy_records", false);
            updates.put(zone + "season/updated_at_epoch", now);
            return;
        }

        GardenSeason primary = remaining.get(0);
        Map<String, Boolean> activeSeasonIds = new HashMap<>();
        for (GardenSeason season : remaining) {
            activeSeasonIds.put(season.getSeason_id(), true);
        }
        updates.put(zone + "season/active_season_ids", activeSeasonIds);
        updates.put(zone + "season/active_season_id", primary.getSeason_id());
        updates.put(zone + "season/status", SeasonStatus.ACTIVE);
        updates.put(zone + "season/label", primary.getLabel());
        updates.put(zone + "season/started_at_epoch", primary.getStarted_at_epoch());
        updates.put(zone + "season/ended_at_epoch", 0L);
        updates.put(zone + "season/include_legacy_records", primary.isIncludes_legacy_records());
        updates.put(zone + "season/updated_at_epoch", now);
    }

    private static List<GardenSeason> remainingActiveSeasons(
            DataSnapshot root,
            GardenZone zone,
            String excludedSeasonId
    ) {
        List<GardenSeason> result = activeSeasonsForArea(root, zone);
        result.removeIf(season -> safe(season.getSeason_id()).equals(safe(excludedSeasonId)));
        result.sort(Comparator.comparingLong(GardenSeason::getStarted_at_epoch).reversed());
        return result;
    }

    private static ZoneSeasonState seasonScopeFor(GardenSeason season) {
        ZoneSeasonState scope = new ZoneSeasonState();
        scope.setActive_season_id(season.getSeason_id());
        scope.setStatus(SeasonStatus.ACTIVE);
        scope.setStarted_at_epoch(season.getStarted_at_epoch());
        scope.setEnded_at_epoch(season.getEnded_at_epoch());
        scope.setInclude_legacy_records(season.isIncludes_legacy_records());
        return scope;
    }

    public Task<String> resolveActiveSeasonId(String zoneId) {
        if (safe(zoneId).isBlank()) return Tasks.forResult("");
        return deviceRef.child("zones").child(zoneId).child("season").get()
                .continueWith(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) return "";
                    ZoneSeasonState state = task.getResult().getValue(ZoneSeasonState.class);
                    return state != null && state.isActive() ? state.getActive_season_id() : "";
                });
    }


    /**
     * Returns an active season id. Existing installations are bootstrapped once;
     * a deliberately closed season is never reopened implicitly.
     */
    /** Checks the requested crop, including secondary active seasons in the same zone. */
    public Task<GardenSeason> requireWritableJournalSeason(String zoneId, String seasonId) {
        if (safe(zoneId).isBlank() || safe(seasonId).isBlank()) {
            return Tasks.forException(new IllegalStateException("JOURNAL_SEASON_INACTIVE"));
        }
        return deviceRef.child("zones").child(zoneId).get().continueWithTask(zoneTask -> {
            if (!zoneTask.isSuccessful()) return Tasks.forException(zoneTask.getException());
            GardenZone zone = zoneTask.getResult().getValue(GardenZone.class);
            if (zone != null && zone.getZone_id().isBlank()) zone.setZone_id(zoneId);
            return seasonsRef.child(seasonId).get().continueWith(manifestTask -> {
                if (!manifestTask.isSuccessful()) throw manifestTask.getException();
                GardenSeason season = manifestTask.getResult().getValue(GardenSeason.class);
                if (season != null && season.getSeason_id().isBlank()) season.setSeason_id(seasonId);
                if (!com.alidogukan.avora.journal.JournalEntryPolicy.writableSeason(zone, season)) {
                    throw new IllegalStateException("JOURNAL_SEASON_INACTIVE");
                }
                return season;
            });
        });
    }

    public Task<String> requireActiveSeasonId(String zoneId) {
        if (safe(zoneId).isBlank()) {
            return Tasks.forException(new IllegalArgumentException("Bölge bilgisi gerekli."));
        }
        return deviceRef.child("zones").child(zoneId).get().continueWithTask(task -> {
            if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) {
                Exception error = task.getException();
                return Tasks.forException(error == null
                        ? new IllegalStateException("Bölge bulunamadı.")
                        : error);
            }
            DataSnapshot snapshot = task.getResult();
            GardenZone zone = snapshot.getValue(GardenZone.class);
            if (zone == null) {
                return Tasks.forException(new IllegalStateException("Bölge bilgisi okunamadı."));
            }
            if (safe(zone.getZone_id()).isBlank()) zone.setZone_id(zoneId);
            if (ZoneCapacityPolicy.isInactive(zone)) {
                return Tasks.forException(new IllegalStateException(
                        "Kullanım dışı bölgeye kayıt eklenemez."
                ));
            }
            ZoneSeasonState state = snapshot.child("season").getValue(ZoneSeasonState.class);
            if (state != null && state.isActive()) {
                return Tasks.forResult(state.getActive_season_id());
            }
            if (state != null && SeasonStatus.isClosed(state.getStatus())) {
                return Tasks.forException(new IllegalStateException(
                        "Bu bölgenin sezonu kapalı. Önce yeni sezon başlatın."
                ));
            }
            return bootstrapLegacySeason(zone)
                    .continueWithTask(ignored -> resolveActiveSeasonId(zoneId));
        });
    }
    private static void putOutcome(
            Map<String, Object> updates,
            String path,
            SeasonOutcome outcome,
            String seasonId,
            String zoneId,
            long now
    ) {
        updates.put(path + "season_id", seasonId);
        updates.put(path + "zone_id", zoneId);
        updates.put(path + "result", outcome == null ? "" : outcome.getResult());
        updates.put(path + "harvest_amount", outcome == null ? "" : outcome.getHarvest_amount());
        updates.put(path + "yield_note", outcome == null ? "" : outcome.getYield_note());
        updates.put(path + "issues_note", outcome == null ? "" : outcome.getIssues_note());
        updates.put(path + "successful_practices",
                outcome == null ? "" : outcome.getSuccessful_practices());
        updates.put(path + "next_season_note",
                outcome == null ? "" : outcome.getNext_season_note());
        updates.put(path + "water_summary", outcome == null ? "" : outcome.getWater_summary());
        updates.put(path + "fertilizer_summary",
                outcome == null ? "" : outcome.getFertilizer_summary());
        updates.put(path + "recorded_at_epoch", now);
    }

    private static SeasonCounts calculateCounts(MutableData root, String zoneId, ZoneSeasonState state) {
        SeasonCounts counts = new SeasonCounts();
        for (MutableData record : root.child("watering_history").getChildren()) {
            if (!zoneId.equals(stringValue(record.child("zone_id")))) continue;
            if (!belongs(record, state)) continue;
            long duration = Math.max(0L, longValue(record.child("duration")));
            if (!SeasonRecordPolicy.hasMeaningfulWatering(duration)) continue;
            counts.wateringCount++;
            counts.wateringSeconds += duration;
        }
        for (MutableData record : root.child("fertilizer_history").getChildren()) {
            if (!zoneId.equals(stringValue(record.child("zone_id")))) continue;
            if (belongs(record, state)) counts.fertilizerCount++;
        }
        for (MutableData record : root.child("garden_journal").child("events").getChildren()) {
            if (!zoneId.equals(stringValue(record.child("zone_id")))) continue;
            if (!belongs(record, state)) continue;
            if (!SeasonRecordPolicy.isFieldJournalEvent(
                    stringValue(record.child("type")),
                    stringValue(record.child("source")),
                    stringValue(record.child("source_key")))) continue;
            counts.eventCount++;
            String type = stringValue(record.child("type")).toUpperCase(Locale.ROOT);
            if (type.contains("AI") || type.contains("ASSISTANT") || type.contains("ANALYSIS")) {
                counts.analysisCount++;
            }
        }
        for (MutableData record : root.child("garden_journal").child("photo_metadata").getChildren()) {
            if (!zoneId.equals(stringValue(record.child("zone_id")))) continue;
            if (!belongs(record, state)) continue;
            counts.photoCount++;
            if (!stringValue(record.child("analysis_title")).isBlank()) counts.analysisCount++;
        }
        for (MutableData record : root.child("notifications").getChildren()) {
            if (!zoneId.equals(stringValue(record.child("zone_id")))) continue;
            if (belongs(record, state)) counts.notificationCount++;
        }
        return counts;
    }

    private static SeasonCounts calculateCounts(
            DataSnapshot root,
            String zoneId,
            ZoneSeasonState state,
            boolean includePending
    ) {
        SeasonCounts counts = new SeasonCounts();
        for (DataSnapshot record : root.child("watering_history").getChildren()) {
            if (!zoneId.equals(stringValue(record.child("zone_id"))) || !belongs(record, state)) continue;
            long duration = Math.max(0L, longValue(record.child("duration")));
            if (!SeasonRecordPolicy.hasMeaningfulWatering(duration)) continue;
            counts.wateringCount++;
            counts.wateringSeconds += duration;
        }
        if (includePending) {
            for (DataSnapshot pending : root.child("irrigation_runtime")
                    .child("pending_waterings").getChildren()) {
                DataSnapshot record = pending.child("record");
                if (!zoneId.equals(stringValue(record.child("zone_id")))) continue;
                if (!belongs(record, state)) continue;
                long duration = Math.max(0L, longValue(record.child("duration")));
                if (!SeasonRecordPolicy.hasMeaningfulWatering(duration)) continue;
                counts.wateringCount++;
                counts.wateringSeconds += duration;
            }
        }
        for (DataSnapshot record : root.child("fertilizer_history").getChildren()) {
            if (zoneId.equals(stringValue(record.child("zone_id"))) && belongs(record, state)) {
                counts.fertilizerCount++;
            }
        }
        for (DataSnapshot record : root.child("garden_journal").child("events").getChildren()) {
            if (!zoneId.equals(stringValue(record.child("zone_id"))) || !belongs(record, state)) continue;
            if (!SeasonRecordPolicy.isFieldJournalEvent(
                    stringValue(record.child("type")),
                    stringValue(record.child("source")),
                    stringValue(record.child("source_key")))) continue;
            counts.eventCount++;
            String type = stringValue(record.child("type")).toUpperCase(Locale.ROOT);
            if (type.contains("AI") || type.contains("ASSISTANT") || type.contains("ANALYSIS")) {
                counts.analysisCount++;
            }
        }
        for (DataSnapshot record : root.child("garden_journal")
                .child("photo_metadata").getChildren()) {
            if (!zoneId.equals(stringValue(record.child("zone_id"))) || !belongs(record, state)) continue;
            counts.photoCount++;
            if (!stringValue(record.child("analysis_title")).isBlank()) counts.analysisCount++;
        }
        for (DataSnapshot record : root.child("notifications").getChildren()) {
            if (zoneId.equals(stringValue(record.child("zone_id"))) && belongs(record, state)) {
                counts.notificationCount++;
            }
        }
        return counts;
    }

    private static boolean belongs(DataSnapshot record, ZoneSeasonState state) {
        if (containsSeasonId(record, state.getActive_season_id())) return true;
        String recordSeasonId = stringValue(record.child("season_id"));
        if (!recordSeasonId.isBlank()) return state.getActive_season_id().equals(recordSeasonId);
        return state.isInclude_legacy_records();
    }

    private static boolean belongs(MutableData record, ZoneSeasonState state) {
        if (containsSeasonId(record, state.getActive_season_id())) return true;
        String recordSeasonId = stringValue(record.child("season_id"));
        if (!recordSeasonId.isBlank()) return state.getActive_season_id().equals(recordSeasonId);
        return state.isInclude_legacy_records();
    }

    private static boolean containsSeasonId(DataSnapshot record, String seasonId) {
        String expected = safe(seasonId);
        if (expected.isBlank()) return false;
        for (DataSnapshot child : record.child("season_ids").getChildren()) {
            if (expected.equals(stringValue(child))) return true;
            if (expected.equals(safe(child.getKey())) && booleanValue(child)) return true;
        }
        return false;
    }

    private static boolean containsSeasonId(MutableData record, String seasonId) {
        String expected = safe(seasonId);
        if (expected.isBlank()) return false;
        for (MutableData child : record.child("season_ids").getChildren()) {
            if (expected.equals(stringValue(child))) return true;
            if (expected.equals(safe(child.getKey())) && booleanValue(child)) return true;
        }
        return false;
    }

    private static boolean hasPendingWateringForSeason(
            MutableData root,
            String zoneId,
            String seasonId
    ) {
        for (MutableData pending : root.child("irrigation_runtime")
                .child("pending_waterings").getChildren()) {
            MutableData record = pending.child("record");
            if (!zoneId.equals(stringValue(record.child("zone_id")))) continue;
            String pendingSeasonId = stringValue(record.child("season_id"));
            if (containsSeasonId(record, seasonId)
                    || pendingSeasonId.isBlank() || pendingSeasonId.equals(seasonId)) return true;
        }
        return false;
    }

    private static boolean hasPendingWateringForSeason(
            DataSnapshot root,
            String zoneId,
            String seasonId
    ) {
        for (DataSnapshot pending : root.child("irrigation_runtime")
                .child("pending_waterings").getChildren()) {
            DataSnapshot record = pending.child("record");
            if (!zoneId.equals(stringValue(record.child("zone_id")))) continue;
            String pendingSeasonId = stringValue(record.child("season_id"));
            if (containsSeasonId(record, seasonId)
                    || pendingSeasonId.isBlank() || pendingSeasonId.equals(seasonId)) return true;
        }
        return false;
    }

    private static long inferLegacySeasonStart(MutableData root, String zoneId, long fallback) {
        long earliest = fallback;
        for (MutableData record : root.child("fertilizer_history").getChildren()) {
            if (!zoneId.equals(stringValue(record.child("zone_id")))) continue;
            long epoch = longValue(record.child("applied_at_epoch"));
            if (epoch > 0L) earliest = Math.min(earliest, epoch);
        }
        for (MutableData record : root.child("garden_journal").child("events").getChildren()) {
            if (!zoneId.equals(stringValue(record.child("zone_id")))) continue;
            long epoch = longValue(record.child("occurred_at_epoch"));
            if (epoch > 0L) earliest = Math.min(earliest, epoch);
        }
        for (MutableData record : root.child("garden_journal").child("photo_metadata").getChildren()) {
            if (!zoneId.equals(stringValue(record.child("zone_id")))) continue;
            long epoch = longValue(record.child("captured_at_epoch"));
            if (epoch > 0L) earliest = Math.min(earliest, epoch);
        }
        return earliest;
    }

    private static long inferLegacySeasonStart(DataSnapshot root, String zoneId, long fallback) {
        long earliest = fallback;
        for (DataSnapshot record : root.child("fertilizer_history").getChildren()) {
            if (!zoneId.equals(stringValue(record.child("zone_id")))) continue;
            long epoch = longValue(record.child("applied_at_epoch"));
            if (epoch > 0L) earliest = Math.min(earliest, epoch);
        }
        for (DataSnapshot record : root.child("garden_journal").child("events").getChildren()) {
            if (!zoneId.equals(stringValue(record.child("zone_id")))) continue;
            long epoch = longValue(record.child("occurred_at_epoch"));
            if (epoch > 0L) earliest = Math.min(earliest, epoch);
        }
        for (DataSnapshot record : root.child("garden_journal").child("photo_metadata").getChildren()) {
            if (!zoneId.equals(stringValue(record.child("zone_id")))) continue;
            long epoch = longValue(record.child("captured_at_epoch"));
            if (epoch > 0L) earliest = Math.min(earliest, epoch);
        }
        return earliest;
    }

    private static String seasonLabel(GardenZone zone, long epoch) {
        String year = new java.text.SimpleDateFormat("yyyy", Locale.getDefault())
                .format(new java.util.Date(epoch * 1000L));
        return year + " Sezonu";
    }

    private static String normalizedGrowthStage(String value) {
        String normalized = safe(value).trim().toUpperCase(Locale.ROOT);
        return normalized.isBlank() ? "NOT_SET" : normalized;
    }

    private static long nowEpoch() {
        return System.currentTimeMillis() / 1000L;
    }

    private static String stringValue(MutableData data) {
        Object value = data.getValue();
        return value == null ? "" : String.valueOf(value);
    }

    private static long longValue(MutableData data) {
        Object value = data.getValue();
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private static boolean booleanValue(MutableData data) {
        Object value = data.getValue();
        return value instanceof Boolean && (Boolean) value;
    }

    private static String stringValue(DataSnapshot data) {
        Object value = data.getValue();
        return value == null ? "" : String.valueOf(value);
    }

    private static long longValue(DataSnapshot data) {
        Object value = data.getValue();
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private static boolean booleanValue(DataSnapshot data) {
        Object value = data.getValue();
        return value instanceof Boolean && (Boolean) value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public static final class EmptySeasonDeletionStatus {
        private final boolean canDelete;
        private final boolean canCleanMissingPhotos;
        private final String reason;
        private final int wateringCount;
        private final int fertilizerCount;
        private final int journalCount;
        private final int localPhotoCount;
        private final Set<String> missingPhotoIds;
        private final int missingPhotoAnalysisCount;
        private final boolean meaningfulOutcome;

        private EmptySeasonDeletionStatus(
                boolean canDelete,
                boolean canCleanMissingPhotos,
                String reason,
                int wateringCount,
                int fertilizerCount,
                int journalCount,
                int localPhotoCount,
                Set<String> missingPhotoIds,
                int missingPhotoAnalysisCount,
                boolean meaningfulOutcome
        ) {
            this.canDelete = canDelete;
            this.canCleanMissingPhotos = canCleanMissingPhotos;
            this.reason = safe(reason);
            this.wateringCount = Math.max(0, wateringCount);
            this.fertilizerCount = Math.max(0, fertilizerCount);
            this.journalCount = Math.max(0, journalCount);
            this.localPhotoCount = Math.max(0, localPhotoCount);
            this.missingPhotoIds = missingPhotoIds == null
                    ? new LinkedHashSet<>()
                    : new LinkedHashSet<>(missingPhotoIds);
            this.missingPhotoAnalysisCount = Math.max(
                    0,
                    missingPhotoAnalysisCount
            );
            this.meaningfulOutcome = meaningfulOutcome;
        }

        static EmptySeasonDeletionStatus blocked(String reason) {
            return new EmptySeasonDeletionStatus(
                    false,
                    false,
                    reason,
                    0,
                    0,
                    0,
                    0,
                    new LinkedHashSet<>(),
                    0,
                    false
            );
        }

        public boolean canDelete() { return canDelete; }
        public boolean canCleanMissingPhotos() { return canCleanMissingPhotos; }
        public String getReason() { return reason; }
        public int getWateringCount() { return wateringCount; }
        public int getFertilizerCount() { return fertilizerCount; }
        public int getJournalCount() { return journalCount; }
        public int getLocalPhotoCount() { return localPhotoCount; }
        public int getMissingPhotoCount() { return missingPhotoIds.size(); }
        public Set<String> getMissingPhotoIds() {
            return new LinkedHashSet<>(missingPhotoIds);
        }
        public int getMissingPhotoAnalysisCount() {
            return missingPhotoAnalysisCount;
        }
        public boolean hasMeaningfulOutcome() { return meaningfulOutcome; }

        public EmptySeasonDeletionStatus withLocalRecords(
                int localJournalCount,
                int validLocalPhotoCount,
                boolean localMeaningfulOutcome
        ) {
            int mergedJournalCount = Math.max(
                    journalCount,
                    Math.max(0, localJournalCount)
            );
            int mergedLocalPhotoCount = Math.max(
                    localPhotoCount,
                    Math.max(0, validLocalPhotoCount)
            );
            boolean mergedOutcome = meaningfulOutcome || localMeaningfulOutcome;
            boolean locallyBlocked = mergedJournalCount > 0
                    || mergedLocalPhotoCount > 0
                    || mergedOutcome;
            return new EmptySeasonDeletionStatus(
                    canDelete && !locallyBlocked,
                    canCleanMissingPhotos,
                    locallyBlocked
                            ? "Bu sezonda telefonda korunması gereken kayıtlar var."
                            : reason,
                    wateringCount,
                    fertilizerCount,
                    mergedJournalCount,
                    mergedLocalPhotoCount,
                    missingPhotoIds,
                    missingPhotoAnalysisCount,
                    mergedOutcome
            );
        }
    }

    private static final class CancellationCheck {
        final boolean allowed;
        final String message;

        private CancellationCheck(boolean allowed, String message) {
            this.allowed = allowed;
            this.message = message;
        }

        static CancellationCheck allowed() {
            return new CancellationCheck(true, "");
        }

        static CancellationCheck blocked(String message) {
            return new CancellationCheck(false, message);
        }
    }
    private static final class SeasonCounts {
        int wateringCount;
        long wateringSeconds;
        int fertilizerCount;
        int eventCount;
        int photoCount;
        int analysisCount;
        int notificationCount;
    }
}
