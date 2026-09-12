package com.alidogukan.avora.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.alidogukan.avora.config.AppInfo;
import com.alidogukan.avora.models.GardenPhoto;
import com.alidogukan.avora.photos.LocalGardenPhotoStore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class GardenPhotoArchiveManagerTest {
    private Context context;
    private LocalGardenPhotoStore store;
    private GardenPhotoArchiveManager archiveManager;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        clearPhotoArchive();
        store = new LocalGardenPhotoStore(context);
        archiveManager = new GardenPhotoArchiveManager(context);
    }

    @After
    public void tearDown() {
        clearPhotoArchive();
    }

    @Test
    public void exportAndRestorePreservesMetadataWithoutCreatingDuplicates() throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(24, 16, Bitmap.Config.ARGB_8888);
        GardenPhoto original;
        try {
            original = store.save(bitmap, "zone-1", "İlk fide fotoğrafı", "daily-1");
        } finally {
            bitmap.recycle();
        }
        assertTrue(store.updateSeasonId(original.getId(), "season-1"));
        assertTrue(store.updateViewerOrientation(original.getId(), 90, true, false));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        GardenPhotoArchiveManager.ArchiveResult exported = archiveManager.exportTo(output);
        byte[] archive = output.toByteArray();

        assertEquals(1, exported.photoCount);
        assertEquals(0, exported.skippedCount);
        GardenPhotoArchiveManager.ArchiveInspection inspection = archiveManager.inspect(
                new ByteArrayInputStream(archive));
        assertEquals(1, inspection.photoCount);
        assertEquals(AppInfo.DEVICE_ID, inspection.sourceDeviceId);

        List<GardenPhoto> saved = store.load();
        assertEquals(1, saved.size());
        assertTrue(store.delete(saved.get(0)));
        assertTrue(store.load().isEmpty());

        GardenPhotoArchiveManager.ArchiveResult restored = archiveManager.restoreFrom(
                new ByteArrayInputStream(archive));
        assertEquals(1, restored.photoCount);
        assertEquals(0, restored.skippedCount);

        List<GardenPhoto> restoredPhotos = store.load();
        assertEquals(1, restoredPhotos.size());
        GardenPhoto restoredPhoto = restoredPhotos.get(0);
        assertEquals(original.getId(), restoredPhoto.getId());
        assertEquals("zone-1", restoredPhoto.getZone_id());
        assertEquals("season-1", restoredPhoto.getSeason_id());
        assertEquals("İlk fide fotoğrafı", restoredPhoto.getNote());
        assertEquals("daily-1", restoredPhoto.getRelated_application_id());
        assertEquals(90, restoredPhoto.getRotation_degrees());
        assertTrue(restoredPhoto.getFlipped_horizontally());
        assertFalse(restoredPhoto.getFlipped_vertically());
        assertTrue(new File(restoredPhoto.getLocal_path()).isFile());

        GardenPhotoArchiveManager.ArchiveResult duplicate = archiveManager.restoreFrom(
                new ByteArrayInputStream(archive));
        assertEquals(0, duplicate.photoCount);
        assertEquals(1, duplicate.skippedCount);
        assertEquals(1, store.load().size());
    }

    private void clearPhotoArchive() {
        context.getSharedPreferences("garden_photo_archive", Context.MODE_PRIVATE)
                .edit().clear().commit();
        deleteTree(new File(context.getFilesDir(), "garden_photos"));
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteTree(child);
        }
        file.delete();
    }
}
