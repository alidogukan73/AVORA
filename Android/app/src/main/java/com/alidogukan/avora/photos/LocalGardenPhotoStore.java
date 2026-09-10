package com.alidogukan.avora.photos;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import com.alidogukan.avora.models.GardenPhoto;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Stores the private photo archive on this phone; it has no cloud cost. */
public class LocalGardenPhotoStore {
    private static final String PREFS = "garden_photo_archive";
    private static final String KEY_INDEX = "index";
    private final Context context;

    public LocalGardenPhotoStore(Context context) {
        this.context = context.getApplicationContext();
    }

    public GardenPhoto save(Uri source, String zoneId, String note) throws Exception {
        return save(source, zoneId, note, "");
    }

    public GardenPhoto save(Uri source, String zoneId, String note,
                            String relatedApplicationId) throws Exception {
        return save(source, zoneId, note, relatedApplicationId,
                GardenPhotoQualityPolicy.MAX_LONG_EDGE,
                GardenPhotoQualityPolicy.JPEG_QUALITY);
    }

    /** Saves with a caller-selected bound, useful for lightweight cloud-linked photos. */
    public GardenPhoto save(Uri source, String zoneId, String note,
                            String relatedApplicationId, int maxLongEdge,
                            int jpegQuality) throws Exception {
        String id = UUID.randomUUID().toString();
        File folder = new File(context.getFilesDir(), "garden_photos");
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IllegalStateException("Photo folder could not be created");
        }
        File target = new File(folder, id + ".jpg");
        writeOptimizedImage(source, target, maxLongEdge, jpegQuality);
        GardenPhoto photo = new GardenPhoto();
        photo.setId(id);
        photo.setZone_id(zoneId);
        photo.setLocal_path(target.getAbsolutePath());
        photo.setNote(note == null ? "" : note.trim());
        photo.setRelated_application_id(relatedApplicationId == null
                ? "" : relatedApplicationId.trim());
        photo.setCaptured_at_epoch(System.currentTimeMillis() / 1000L);
        JSONArray index = readIndex();
        JSONObject item = new JSONObject();
        item.put("id", photo.getId());
        item.put("zone_id", photo.getZone_id());
        item.put("season_id", safe(photo.getSeason_id()));
        item.put("local_path", photo.getLocal_path());
        item.put("note", photo.getNote());
        item.put("related_application_id", photo.getRelated_application_id());
        item.put("captured_at_epoch", photo.getCaptured_at_epoch());
        index.put(item);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_INDEX, index.toString()).apply();
        return photo;
    }

    /** Saves a photo captured by the in-app camera to the same private archive. */
    public GardenPhoto save(Bitmap bitmap, String zoneId, String note,
                            String relatedApplicationId) throws Exception {
        if (bitmap == null) throw new IllegalArgumentException("Photo is required");
        String id = UUID.randomUUID().toString();
        File folder = new File(context.getFilesDir(), "garden_photos");
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IllegalStateException("Photo folder could not be created");
        }
        File target = new File(folder, id + ".jpg");
        writeOptimizedBitmap(bitmap, target);
        GardenPhoto photo = new GardenPhoto();
        photo.setId(id);
        photo.setZone_id(zoneId);
        photo.setLocal_path(target.getAbsolutePath());
        photo.setNote(note == null ? "" : note.trim());
        photo.setRelated_application_id(relatedApplicationId == null ? "" : relatedApplicationId.trim());
        photo.setCaptured_at_epoch(System.currentTimeMillis() / 1000L);
        JSONArray index = readIndex();
        JSONObject item = new JSONObject();
        item.put("id", photo.getId());
        item.put("zone_id", photo.getZone_id());
        item.put("season_id", safe(photo.getSeason_id()));
        item.put("local_path", photo.getLocal_path());
        item.put("note", photo.getNote());
        item.put("related_application_id", photo.getRelated_application_id());
        item.put("captured_at_epoch", photo.getCaptured_at_epoch());
        index.put(item);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_INDEX, index.toString()).apply();
        return photo;
    }

    public List<GardenPhoto> load() {
        List<GardenPhoto> photos = new ArrayList<>();
        JSONArray index = readIndex();
        JSONArray validIndex = new JSONArray();
        boolean cleaned = false;
        for (int i = index.length() - 1; i >= 0; i--) {
            try {
                JSONObject item = index.getJSONObject(i);
                File file = new File(item.optString("local_path"));
                if (!file.exists()) {
                    cleaned = true;
                    continue;
                }
                GardenPhoto photo = new GardenPhoto();
                photo.setId(item.optString("id"));
                photo.setZone_id(item.optString("zone_id"));
                photo.setSeason_id(item.optString("season_id"));
                photo.setLocal_path(file.getAbsolutePath());
                photo.setNote(item.optString("note"));
                photo.setRelated_application_id(
                        item.optString("related_application_id"));
                photo.setAnalysis_title(item.optString("analysis_title"));
                photo.setAnalysis_meta(item.optString("analysis_meta"));
                photo.setAnalysis_context(item.optString("analysis_context"));
                photo.setAnalysis_advice(item.optString("analysis_advice"));
                photo.setAnalysis_goal(item.optString("analysis_goal"));
                photo.setAnalysis_confidence(item.optInt("analysis_confidence"));
                photo.setGrowth_score(item.optInt("growth_score", -1));
                photo.setGrowth_stage(item.optString("growth_stage"));
                photo.setGrowth_trend(item.optString("growth_trend"));
                photo.setGrowth_score_delta(item.optInt("growth_score_delta"));
                photo.setGrowth_signals(item.optString("growth_signals"));
                photo.setGrowth_previous_captured_at_epoch(
                        item.optLong("growth_previous_captured_at_epoch"));
                photo.setCaptured_at_epoch(item.optLong("captured_at_epoch"));
                photo.setRotation_degrees(normalizeRotation(
                        item.optInt("rotation_degrees")));
                photo.setFlipped_horizontally(
                        item.optBoolean("flipped_horizontally"));
                photo.setFlipped_vertically(
                        item.optBoolean("flipped_vertically"));
                photos.add(photo);
                validIndex.put(item);
            } catch (Exception ignored) { }
        }
        if (cleaned) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_INDEX, validIndex.toString()).apply();
        }
        return photos;
    }

    /** Attaches the final AI assessment to its already archived photo. */
    public GardenPhoto updateAnalysis(String photoId, String title, String meta,
                                      String contextText, String advice,
                                      String analysisGoal, int confidence,
                                      int growthScore, String growthStage,
                                      String growthTrend, int growthScoreDelta,
                                      String growthSignals,
                                      long previousCapturedAtEpoch) {
        if (photoId == null || photoId.isBlank()) return null;
        JSONArray index = readIndex();
        for (int i = 0; i < index.length(); i++) {
            try {
                JSONObject item = index.getJSONObject(i);
                if (!photoId.equals(item.optString("id"))) continue;
                item.put("analysis_title", safe(title));
                item.put("analysis_meta", safe(meta));
                item.put("analysis_context", safe(contextText));
                item.put("analysis_advice", safe(advice));
                item.put("analysis_goal", safe(analysisGoal));
                item.put("analysis_confidence", Math.max(0, Math.min(100, confidence)));
                item.put("growth_score", growthScore);
                item.put("growth_stage", safe(growthStage));
                item.put("growth_trend", safe(growthTrend));
                item.put("growth_score_delta", growthScoreDelta);
                item.put("growth_signals", safe(growthSignals));
                item.put("growth_previous_captured_at_epoch",
                        Math.max(0L, previousCapturedAtEpoch));
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putString(KEY_INDEX, index.toString()).apply();
                for (GardenPhoto photo : load()) {
                    if (photoId.equals(photo.getId())) return photo;
                }
                return null;
            } catch (Exception ignored) { }
        }
        return null;
    }

    /** Groups existing photos so a journal entry can hold several images. */
    public boolean updateRelatedApplicationId(String photoId, String relatedApplicationId) {
        if (photoId == null || photoId.isBlank()) return false;
        JSONArray index = readIndex();
        for (int i = 0; i < index.length(); i++) {
            try {
                JSONObject item = index.getJSONObject(i);
                if (!photoId.equals(item.optString("id"))) continue;
                item.put("related_application_id", safe(relatedApplicationId));
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putString(KEY_INDEX, index.toString()).apply();
                return true;
            } catch (Exception ignored) { }
        }
        return false;
    }

    /** Persists the cloud-resolved season id in the phone-only photo index. */
    public boolean updateSeasonId(String photoId, String seasonId) {
        if (photoId == null || photoId.isBlank()) return false;
        JSONArray index = readIndex();
        for (int i = 0; i < index.length(); i++) {
            try {
                JSONObject item = index.getJSONObject(i);
                if (!photoId.equals(item.optString("id"))) continue;
                item.put("season_id", safe(seasonId));
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putString(KEY_INDEX, index.toString()).apply();
                return true;
            } catch (Exception ignored) { }
        }
        return false;
    }

    /** Keeps viewer orientation on this phone without rewriting the JPEG. */
    public boolean updateViewerOrientation(String photoId, int rotationDegrees,
                                           boolean flippedHorizontally,
                                           boolean flippedVertically) {
        if (photoId == null || photoId.isBlank()) return false;
        JSONArray index = readIndex();
        for (int i = 0; i < index.length(); i++) {
            try {
                JSONObject item = index.getJSONObject(i);
                if (!photoId.equals(item.optString("id"))) continue;
                item.put("rotation_degrees", normalizeRotation(rotationDegrees));
                item.put("flipped_horizontally", flippedHorizontally);
                item.put("flipped_vertically", flippedVertically);
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putString(KEY_INDEX, index.toString()).apply();
                return true;
            } catch (Exception ignored) { }
        }
        return false;
    }

    /** Removes both the private archive record and its private phone copy. */
    public boolean delete(GardenPhoto photo) {
        if (photo == null || photo.getId() == null || photo.getId().isBlank()) {
            return false;
        }
        JSONArray current = readIndex();
        JSONArray remaining = new JSONArray();
        boolean removed = false;
        for (int i = 0; i < current.length(); i++) {
            try {
                JSONObject item = current.getJSONObject(i);
                if (photo.getId().equals(item.optString("id"))) {
                    removed = true;
                } else {
                    remaining.put(item);
                }
            } catch (Exception ignored) { }
        }
        if (!removed) return false;
        File imageFile = new File(photo.getLocal_path());
        if (imageFile.exists() && !imageFile.delete()) return false;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_INDEX, remaining.toString()).apply();
        return true;
    }

    private JSONArray readIndex() {
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_INDEX, "[]");
        try { return new JSONArray(raw); } catch (Exception ignored) { return new JSONArray(); }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static int normalizeRotation(int degrees) {
        return ((degrees % 360) + 360) % 360;
    }

    /** Keeps a useful plant photo while preventing full camera originals filling storage. */
    private void writeOptimizedImage(Uri source, File target) throws Exception {
        writeOptimizedImage(source, target, GardenPhotoQualityPolicy.MAX_LONG_EDGE,
                GardenPhotoQualityPolicy.JPEG_QUALITY);
    }

    private void writeOptimizedImage(Uri source, File target, int maxLongEdge,
                                     int jpegQuality) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = context.getContentResolver().openInputStream(source)) {
            if (input == null) throw new IllegalStateException("Photo could not be read");
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IllegalArgumentException("Selected file is not a readable image");
        }
        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inSampleSize = decodeSampleSize(
                bounds.outWidth, bounds.outHeight, maxLongEdge);
        decode.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap;
        try (InputStream input = context.getContentResolver().openInputStream(source)) {
            if (input == null) throw new IllegalStateException("Photo could not be read");
            bitmap = BitmapFactory.decodeStream(input, null, decode);
        }
        if (bitmap == null) throw new IllegalStateException("Photo could not be decoded");
        Bitmap oriented = applyExifOrientation(bitmap, readExifOrientation(source));
        try {
            writeOptimizedBitmap(oriented, target, maxLongEdge, jpegQuality);
        } finally {
            if (oriented != bitmap && !oriented.isRecycled()) oriented.recycle();
            bitmap.recycle();
        }
    }

    private int readExifOrientation(Uri source) {
        try (InputStream input = context.getContentResolver().openInputStream(source)) {
            if (input == null) return ExifInterface.ORIENTATION_NORMAL;
            return new ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
            );
        } catch (Exception ignored) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    private static Bitmap applyExifOrientation(Bitmap source, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180f);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setRotate(180f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90f);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90f);
                break;
            default:
                return source;
        }
        return Bitmap.createBitmap(
                source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private void writeOptimizedBitmap(Bitmap source, File target) throws Exception {
        writeOptimizedBitmap(source, target, GardenPhotoQualityPolicy.MAX_LONG_EDGE,
                GardenPhotoQualityPolicy.JPEG_QUALITY);
    }

    private void writeOptimizedBitmap(Bitmap source, File target, int maxLongEdge,
                                      int jpegQuality) throws Exception {
        int[] dimensions = scaledDimensions(
                source.getWidth(), source.getHeight(), maxLongEdge);
        Bitmap outputBitmap = source;
        if (dimensions[0] != source.getWidth() || dimensions[1] != source.getHeight()) {
            outputBitmap = Bitmap.createScaledBitmap(
                    source, dimensions[0], dimensions[1], true);
        }
        try (FileOutputStream output = new FileOutputStream(target)) {
            if (!outputBitmap.compress(Bitmap.CompressFormat.JPEG,
                    Math.max(1, Math.min(100, jpegQuality)), output)) {
                throw new IllegalStateException("Photo could not be written");
            }
        } catch (Exception error) {
            if (target.exists()) target.delete();
            throw error;
        } finally {
            if (outputBitmap != source) outputBitmap.recycle();
        }
    }

    private static int decodeSampleSize(int width, int height, int maxLongEdge) {
        int boundedEdge = Math.max(320, maxLongEdge);
        int largest = Math.max(width, height);
        int sample = 1;
        while (largest / (sample * 2) >= boundedEdge) sample *= 2;
        return sample;
    }

    private static int[] scaledDimensions(int width, int height, int maxLongEdge) {
        int boundedEdge = Math.max(320, maxLongEdge);
        int largest = Math.max(width, height);
        if (largest <= boundedEdge) return new int[]{width, height};
        double scale = (double) boundedEdge / largest;
        return new int[]{
                Math.max(1, (int) Math.round(width * scale)),
                Math.max(1, (int) Math.round(height * scale))
        };
    }
}
