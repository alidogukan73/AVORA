package com.alidogukan.avora.photos;

import android.content.Context;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.alidogukan.avora.BuildConfig;

import java.io.File;
import java.io.IOException;

/** Creates private temporary destinations for full-resolution camera captures. */
public final class GardenPhotoCapture {
    private static final long STALE_CAPTURE_AGE_MS = 24L * 60L * 60L * 1000L;

    private GardenPhotoCapture() { }

    public static Target create(Context context) throws IOException {
        File folder = new File(context.getCacheDir(), "camera");
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Camera folder could not be created");
        }
        removeStaleCaptures(folder);
        File file = File.createTempFile("avora_plant_", ".jpg", folder);
        Uri uri = FileProvider.getUriForFile(
                context, BuildConfig.APPLICATION_ID + ".fileprovider", file);
        return new Target(uri, file);
    }

    /** Restores a pending camera destination after Activity/process recreation. */
    public static Target restore(Context context, String absolutePath) throws IOException {
        if (absolutePath == null || absolutePath.isBlank()) return null;
        File folder = new File(context.getCacheDir(), "camera").getCanonicalFile();
        File file = new File(absolutePath).getCanonicalFile();
        File parent = file.getParentFile();
        if (parent == null || !parent.equals(folder) || !file.isFile()) return null;
        Uri uri = FileProvider.getUriForFile(
                context, BuildConfig.APPLICATION_ID + ".fileprovider", file);
        return new Target(uri, file);
    }

    private static void removeStaleCaptures(File folder) {
        File[] files = folder.listFiles();
        if (files == null) return;
        long threshold = System.currentTimeMillis() - STALE_CAPTURE_AGE_MS;
        for (File file : files) {
            if (file.isFile() && file.lastModified() < threshold) file.delete();
        }
    }

    public static final class Target {
        private final Uri uri;
        private final File file;

        private Target(Uri uri, File file) {
            this.uri = uri;
            this.file = file;
        }

        public Uri getUri() { return uri; }
        public String getAbsolutePath() { return file.getAbsolutePath(); }

        public void delete() {
            if (file.exists()) file.delete();
        }
    }
}
