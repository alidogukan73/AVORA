package com.alidogukan.avora.seedling;

import android.content.Context;
import android.net.Uri;

import com.alidogukan.avora.models.GardenPhoto;
import com.alidogukan.avora.models.SeedlingDailyLog;
import com.alidogukan.avora.models.SeedlingPhotoUpload;
import com.alidogukan.avora.photos.LocalGardenPhotoStore;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Keeps compressed daily seedling photos in the owner's private phone archive. */
public final class SeedlingDailyPhotoStore {
    private static final String LINK_PREFIX = "seedling-photo:";
    private static final int MAX_LONG_EDGE = 1600;
    private static final int JPEG_QUALITY = 82;

    private final LocalGardenPhotoStore localStore;
    private final ExecutorService diskExecutor = Executors.newSingleThreadExecutor();

    public SeedlingDailyPhotoStore(Context context) {
        this.localStore = new LocalGardenPhotoStore(context.getApplicationContext());
    }

    public Task<SeedlingPhotoUpload> save(Uri source, String batchId) {
        if (source == null || safe(batchId).isEmpty()) {
            return Tasks.forException(new IllegalArgumentException("Photo and batch are required"));
        }
        TaskCompletionSource<SeedlingPhotoUpload> result = new TaskCompletionSource<>();
        diskExecutor.execute(() -> {
            final GardenPhoto local;
            try {
                local = localStore.save(source, "seedling:" + batchId, "", "",
                        MAX_LONG_EDGE, JPEG_QUALITY);
                localStore.updateRelatedApplicationId(
                        local.getId(), LINK_PREFIX + local.getId());
                result.trySetResult(new SeedlingPhotoUpload(local, ""));
            } catch (Exception error) {
                result.trySetException(error);
            }
        });
        return result.getTask();
    }

    /** Returns the private local copy without any Firebase download. */
    public Task<GardenPhoto> load(SeedlingDailyLog log) {
        if (log == null || !log.hasPhoto()) {
            return Tasks.forException(new IllegalArgumentException("Photo reference is missing"));
        }
        GardenPhoto local = findLocal(log.getPhoto_id());
        return local == null
                ? Tasks.forException(new IllegalStateException("Photo is not on this phone"))
                : Tasks.forResult(local);
    }

    /** Best-effort cleanup after a record/photo is removed or an upload is rolled back. */
    public void delete(SeedlingDailyLog log) {
        if (log == null) return;
        delete(log.getPhoto_id());
    }

    public void delete(SeedlingPhotoUpload uploaded) {
        if (uploaded == null) return;
        delete(uploaded.getPhotoId());
    }

    private void delete(String photoId) {
        GardenPhoto local = findLocal(photoId);
        if (local != null) localStore.delete(local);
    }

    private GardenPhoto findLocal(String photoId) {
        String wanted = safe(photoId);
        if (wanted.isEmpty()) return null;
        List<GardenPhoto> photos = localStore.load();
        for (GardenPhoto photo : photos) {
            if (wanted.equals(safe(photo.getId()))
                    || (LINK_PREFIX + wanted).equals(
                    safe(photo.getRelated_application_id()))) {
                return photo;
            }
        }
        return null;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public void close() {
        diskExecutor.shutdown();
    }

}
