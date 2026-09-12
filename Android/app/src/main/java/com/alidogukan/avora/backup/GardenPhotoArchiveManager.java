package com.alidogukan.avora.backup;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;

import com.alidogukan.avora.config.AppInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Creates and safely merges portable ZIP archives for private garden photos. */
public final class GardenPhotoArchiveManager {
    static final String SCHEMA = "avora-photo-archive";
    static final int SCHEMA_VERSION = 1;
    static final String MANIFEST_ENTRY = "avora-photo-archive.json";
    private static final String PHOTO_DIRECTORY_ENTRY = "photos/";
    private static final String PREFS = "garden_photo_archive";
    private static final String KEY_INDEX = "index";
    private static final int BUFFER_SIZE = 32 * 1024;
    private static final int MAX_PHOTOS = 5_000;
    private static final long MAX_PHOTO_BYTES = 20L * 1024L * 1024L;
    private static final long MAX_TOTAL_BYTES = 512L * 1024L * 1024L;
    private static final int MAX_MANIFEST_BYTES = 5 * 1024 * 1024;
    private static final Pattern SAFE_PHOTO_ID = Pattern.compile("[A-Za-z0-9_-]{1,80}");

    private final Context context;
    private final File photoFolder;

    public GardenPhotoArchiveManager(Context context) {
        this.context = context.getApplicationContext();
        this.photoFolder = new File(this.context.getFilesDir(), "garden_photos");
    }

    public int availablePhotoCount() {
        JSONArray index = readIndex();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (int position = 0; position < index.length(); position++) {
            JSONObject item = index.optJSONObject(position);
            if (item == null) continue;
            String id = item.optString("id");
            File file = safeIndexedPhoto(item.optString("local_path"));
            if (isSafePhotoId(id) && seen.add(id) && file != null && file.isFile()) {
                count++;
            }
        }
        return count;
    }

    /** Returns verified, private photo files that are safe to send to the NAS. */
    public List<NasPhotoSource> collectNasPhotos() throws Exception {
        int[] skipped = new int[]{0};
        List<ExportPhoto> photos = collectExportPhotos(skipped);
        List<NasPhotoSource> result = new ArrayList<>(photos.size());
        for (ExportPhoto photo : photos) {
            result.add(new NasPhotoSource(photo.id, photo.file, photo.sizeBytes,
                    photo.sha256, new JSONObject(photo.metadata.toString())));
        }
        return result;
    }

    /** Safely merges downloaded NAS photos without replacing any existing phone file. */
    public ArchiveResult restoreNasPhotos(List<NasPhotoImport> sources) throws Exception {
        if (sources == null || sources.isEmpty()) return new ArchiveResult(0, 0);
        if (sources.size() > MAX_PHOTOS) throw archiveTooLarge();

        List<ImportPhoto> photos = new ArrayList<>(sources.size());
        Map<String, File> files = new HashMap<>();
        Set<String> ids = new HashSet<>();
        long totalBytes = 0L;
        for (NasPhotoImport source : sources) {
            if (source == null || !isSafePhotoId(source.id)
                    || source.file == null || !source.file.isFile()
                    || source.sizeBytes <= 0L || source.sizeBytes > MAX_PHOTO_BYTES
                    || !isSha256(source.sha256) || !ids.add(source.id)) {
                throw invalidArchive();
            }
            totalBytes += source.sizeBytes;
            if (totalBytes > MAX_TOTAL_BYTES) throw archiveTooLarge();
            JSONObject metadata = source.metadata == null
                    ? new JSONObject() : new JSONObject(source.metadata.toString());
            metadata.remove("local_path");
            metadata.put("id", source.id);
            String entry = photoEntry(source.id);
            photos.add(new ImportPhoto(source.id, entry, source.sizeBytes,
                    source.sha256, metadata));
            files.put(entry, source.file);
        }

        ManifestData manifest = new ManifestData(System.currentTimeMillis(),
                AppInfo.DEVICE_ID, totalBytes, photos);
        ExtractedArchive extracted = new ExtractedArchive(new byte[0], files);
        validateExtractedPhotos(extracted, manifest);
        return mergeArchive(extracted, manifest);
    }

    public ArchiveResult exportTo(OutputStream destination) throws Exception {
        if (destination == null) throw new IllegalArgumentException("Hedef dosya açılamadı.");
        int[] skipped = new int[]{0};
        List<ExportPhoto> photos = collectExportPhotos(skipped);
        if (photos.isEmpty()) {
            throw new IllegalStateException("Yedeklenecek fotoğraf bulunamadı.");
        }

        JSONObject manifest = buildManifest(photos);
        ZipOutputStream zip = new ZipOutputStream(destination);
        writeZipEntry(zip, MANIFEST_ENTRY,
                manifest.toString(2).getBytes(StandardCharsets.UTF_8));
        byte[] buffer = new byte[BUFFER_SIZE];
        for (ExportPhoto photo : photos) {
            zip.putNextEntry(new ZipEntry(photo.entryName));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long copied;
            try (InputStream input = new FileInputStream(photo.file)) {
                copied = copy(input, zip, buffer, MAX_PHOTO_BYTES, digest);
            }
            zip.closeEntry();
            if (copied != photo.sizeBytes || !photo.sha256.equals(hex(digest.digest()))) {
                throw new IllegalStateException(
                        "Fotoğraf arşivlenirken dosya değişti. Lütfen yeniden deneyin.");
            }
        }
        zip.finish();
        destination.flush();
        return new ArchiveResult(photos.size(), skipped[0]);
    }

    public ArchiveInspection inspect(InputStream source) throws Exception {
        if (source == null) throw new IllegalArgumentException("Seçilen ZIP dosyası açılamadı.");
        try (ZipInputStream zip = new ZipInputStream(source)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            long total = 0L;
            int entries = 0;
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_PHOTOS + 2) throw invalidArchive();
                String name = entry.getName();
                if (entry.isDirectory()) {
                    if (!PHOTO_DIRECTORY_ENTRY.equals(name)) throw unsafeArchive();
                    zip.closeEntry();
                    continue;
                }
                if (MANIFEST_ENTRY.equals(name)) {
                    byte[] bytes = readBytes(zip, buffer, MAX_MANIFEST_BYTES);
                    ManifestData data = validateManifest(new JSONObject(
                            new String(bytes, StandardCharsets.UTF_8)));
                    return new ArchiveInspection(data.createdAtEpochMs,
                            data.sourceDeviceId, data.photos.size(), data.totalBytes);
                }
                if (!isSafePhotoEntry(name)) throw unsafeArchive();
                total += drain(zip, buffer, MAX_PHOTO_BYTES);
                if (total > MAX_TOTAL_BYTES) throw archiveTooLarge();
                zip.closeEntry();
            }
        }
        throw new IllegalArgumentException("Fotoğraf ZIP bilgisi bulunamadı.");
    }

    public ArchiveResult restoreFrom(InputStream source) throws Exception {
        if (source == null) throw new IllegalArgumentException("Seçilen ZIP dosyası açılamadı.");
        File staging = new File(context.getCacheDir(),
                "photo_restore_" + UUID.randomUUID());
        if (!staging.mkdirs()) {
            throw new IllegalStateException("Fotoğraf geri yükleme alanı hazırlanamadı.");
        }
        try {
            ExtractedArchive extracted = extractArchive(source, staging);
            ManifestData manifest = validateManifest(new JSONObject(
                    new String(extracted.manifestBytes, StandardCharsets.UTF_8)));
            validateExtractedPhotos(extracted, manifest);
            return mergeArchive(extracted, manifest);
        } finally {
            deleteTree(staging);
        }
    }

    private List<ExportPhoto> collectExportPhotos(int[] skipped) throws Exception {
        JSONArray index = readIndex();
        List<ExportPhoto> photos = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int position = 0; position < index.length(); position++) {
            JSONObject source = index.optJSONObject(position);
            if (source == null) {
                skipped[0]++;
                continue;
            }
            String id = source.optString("id");
            File file = safeIndexedPhoto(source.optString("local_path"));
            if (!isSafePhotoId(id) || !ids.add(id) || file == null || !file.isFile()
                    || file.length() <= 0L || file.length() > MAX_PHOTO_BYTES
                    || !isReadableJpeg(file)) {
                skipped[0]++;
                continue;
            }
            JSONObject metadata = new JSONObject(source.toString());
            metadata.remove("local_path");
            metadata.put("id", id);
            photos.add(new ExportPhoto(id, photoEntry(id), file, file.length(), sha256(file),
                    metadata));
            if (photos.size() > MAX_PHOTOS) throw archiveTooLarge();
        }
        return photos;
    }

    private JSONObject buildManifest(List<ExportPhoto> photos) throws Exception {
        JSONArray records = new JSONArray();
        long totalBytes = 0L;
        for (ExportPhoto photo : photos) {
            records.put(new JSONObject()
                    .put("id", photo.id)
                    .put("entry", photo.entryName)
                    .put("size_bytes", photo.sizeBytes)
                    .put("sha256", photo.sha256)
                    .put("metadata", photo.metadata));
            totalBytes += photo.sizeBytes;
        }
        return new JSONObject()
                .put("schema", SCHEMA)
                .put("schema_version", SCHEMA_VERSION)
                .put("created_at_epoch_ms", System.currentTimeMillis())
                .put("app_version", AppInfo.APP_VERSION)
                .put("source_device_id", AppInfo.DEVICE_ID)
                .put("photo_count", photos.size())
                .put("total_photo_bytes", totalBytes)
                .put("photos", records);
    }

    private ExtractedArchive extractArchive(InputStream source, File staging) throws Exception {
        Map<String, File> files = new HashMap<>();
        Set<String> entries = new HashSet<>();
        byte[] manifestBytes = null;
        byte[] buffer = new byte[BUFFER_SIZE];
        long totalBytes = 0L;
        int entryCount = 0;
        try (ZipInputStream zip = new ZipInputStream(source)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entryCount > MAX_PHOTOS + 2 || !entries.add(entry.getName())) {
                    throw invalidArchive();
                }
                String name = entry.getName();
                if (entry.isDirectory()) {
                    if (!PHOTO_DIRECTORY_ENTRY.equals(name)) throw unsafeArchive();
                    zip.closeEntry();
                    continue;
                }
                if (MANIFEST_ENTRY.equals(name)) {
                    manifestBytes = readBytes(zip, buffer, MAX_MANIFEST_BYTES);
                    totalBytes += manifestBytes.length;
                } else {
                    if (!isSafePhotoEntry(name)) throw unsafeArchive();
                    String id = photoIdFromEntry(name);
                    File target = new File(staging, id + ".jpg");
                    try (OutputStream output = new FileOutputStream(target)) {
                        totalBytes += copy(zip, output, buffer, MAX_PHOTO_BYTES, null);
                    }
                    files.put(name, target);
                }
                if (totalBytes > MAX_TOTAL_BYTES) throw archiveTooLarge();
                zip.closeEntry();
            }
        }
        if (manifestBytes == null) {
            throw new IllegalArgumentException("Fotoğraf ZIP bilgisi bulunamadı.");
        }
        return new ExtractedArchive(manifestBytes, files);
    }

    private ManifestData validateManifest(JSONObject manifest) throws Exception {
        if (!SCHEMA.equals(manifest.optString("schema"))
                || manifest.optInt("schema_version", -1) != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Bu dosya uyumlu bir AVORA fotoğraf ZIP'i değil.");
        }
        JSONArray records = manifest.optJSONArray("photos");
        if (records == null || records.length() <= 0 || records.length() > MAX_PHOTOS
                || manifest.optInt("photo_count", -1) != records.length()) {
            throw invalidArchive();
        }
        Set<String> ids = new HashSet<>();
        Set<String> entries = new HashSet<>();
        List<ImportPhoto> photos = new ArrayList<>();
        long totalBytes = 0L;
        for (int position = 0; position < records.length(); position++) {
            JSONObject record = records.optJSONObject(position);
            if (record == null) throw invalidArchive();
            String id = record.optString("id");
            String entry = record.optString("entry");
            String digest = record.optString("sha256").toLowerCase(Locale.US);
            long size = record.optLong("size_bytes", -1L);
            JSONObject metadata = record.optJSONObject("metadata");
            if (!isSafePhotoId(id) || !entry.equals(photoEntry(id))
                    || !isSha256(digest) || size <= 0L || size > MAX_PHOTO_BYTES
                    || metadata == null || !id.equals(metadata.optString("id"))
                    || !ids.add(id) || !entries.add(entry)) {
                throw invalidArchive();
            }
            totalBytes += size;
            if (totalBytes > MAX_TOTAL_BYTES) throw archiveTooLarge();
            JSONObject cleanMetadata = new JSONObject(metadata.toString());
            cleanMetadata.remove("local_path");
            cleanMetadata.put("id", id);
            photos.add(new ImportPhoto(id, entry, size, digest, cleanMetadata));
        }
        long declaredTotal = manifest.optLong("total_photo_bytes", -1L);
        if (declaredTotal != totalBytes) throw invalidArchive();
        return new ManifestData(manifest.optLong("created_at_epoch_ms", 0L),
                manifest.optString("source_device_id", ""), totalBytes, photos);
    }

    private void validateExtractedPhotos(ExtractedArchive extracted, ManifestData manifest)
            throws Exception {
        if (extracted.files.size() != manifest.photos.size()) throw invalidArchive();
        for (ImportPhoto photo : manifest.photos) {
            File file = extracted.files.get(photo.entryName);
            if (file == null || file.length() != photo.sizeBytes
                    || !photo.sha256.equals(sha256(file)) || !isReadableJpeg(file)) {
                throw new IllegalArgumentException(
                        "Fotoğraf ZIP'i eksik, bozuk veya değiştirilmiş.");
            }
        }
    }

    private ArchiveResult mergeArchive(ExtractedArchive extracted, ManifestData manifest)
            throws Exception {
        if (!photoFolder.exists() && !photoFolder.mkdirs()) {
            throw new IllegalStateException("Fotoğraf klasörü hazırlanamadı.");
        }
        JSONArray current = readIndex();
        JSONArray merged = new JSONArray();
        Set<String> importIds = new HashSet<>();
        for (ImportPhoto photo : manifest.photos) importIds.add(photo.id);
        Set<String> existingIds = new HashSet<>();
        for (int position = 0; position < current.length(); position++) {
            JSONObject item = current.optJSONObject(position);
            if (item == null) continue;
            String id = item.optString("id");
            File existing = safeIndexedPhoto(item.optString("local_path"));
            if (importIds.contains(id) && (existing == null || !existing.isFile())) {
                continue;
            }
            merged.put(item);
            if (isSafePhotoId(id) && existing != null && existing.isFile()) {
                existingIds.add(id);
            }
        }

        List<File> createdFiles = new ArrayList<>();
        int restored = 0;
        int skipped = 0;
        try {
            for (ImportPhoto photo : manifest.photos) {
                if (existingIds.contains(photo.id)) {
                    skipped++;
                    continue;
                }
                File target = new File(photoFolder, photo.id + ".jpg");
                if (target.exists()) {
                    if (!photo.sha256.equals(sha256(target)) || !isReadableJpeg(target)) {
                        skipped++;
                        continue;
                    }
                } else {
                    copyAtomically(extracted.files.get(photo.entryName), target, photo.sha256);
                    createdFiles.add(target);
                }
                JSONObject metadata = new JSONObject(photo.metadata.toString());
                metadata.put("local_path", target.getAbsolutePath());
                merged.put(metadata);
                existingIds.add(photo.id);
                restored++;
            }
            SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            if (!preferences.edit().putString(KEY_INDEX, merged.toString()).commit()) {
                throw new IllegalStateException("Fotoğraf dizini kaydedilemedi.");
            }
            return new ArchiveResult(restored, skipped);
        } catch (Exception error) {
            for (File created : createdFiles) {
                if (created.exists()) created.delete();
            }
            throw error;
        }
    }

    private void copyAtomically(File source, File target, String expectedSha256) throws Exception {
        File temporary = new File(photoFolder, ".restore-" + UUID.randomUUID() + ".tmp");
        try {
            byte[] buffer = new byte[BUFFER_SIZE];
            try (InputStream input = new FileInputStream(source);
                 OutputStream output = new FileOutputStream(temporary)) {
                copy(input, output, buffer, MAX_PHOTO_BYTES, null);
                output.flush();
            }
            if (!expectedSha256.equals(sha256(temporary)) || !isReadableJpeg(temporary)) {
                throw new IllegalArgumentException("Geri yüklenen fotoğraf doğrulanamadı.");
            }
            if (!temporary.renameTo(target)) {
                throw new IllegalStateException("Fotoğraf güvenli biçimde yerleştirilemedi.");
            }
        } finally {
            if (temporary.exists()) temporary.delete();
        }
    }

    private JSONArray readIndex() {
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_INDEX, "[]");
        try {
            return new JSONArray(raw);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private File safeIndexedPhoto(String path) {
        if (path == null || path.isBlank()) return null;
        try {
            File folder = photoFolder.getCanonicalFile();
            File candidate = new File(path).getCanonicalFile();
            return folder.equals(candidate.getParentFile()) ? candidate : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    static boolean isSafePhotoId(String id) {
        return id != null && SAFE_PHOTO_ID.matcher(id).matches();
    }

    static String photoEntry(String id) {
        return PHOTO_DIRECTORY_ENTRY + id + ".jpg";
    }

    static boolean isSafePhotoEntry(String entry) {
        if (entry == null || !entry.startsWith(PHOTO_DIRECTORY_ENTRY)
                || !entry.endsWith(".jpg") || entry.indexOf('\\') >= 0
                || entry.contains("..")) {
            return false;
        }
        return isSafePhotoId(photoIdFromEntry(entry)) && entry.equals(
                photoEntry(photoIdFromEntry(entry)));
    }

    private static String photoIdFromEntry(String entry) {
        return entry.substring(PHOTO_DIRECTORY_ENTRY.length(), entry.length() - 4);
    }

    private static boolean isSha256(String value) {
        if (value == null || value.length() != 64) return false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!((character >= '0' && character <= '9')
                    || (character >= 'a' && character <= 'f'))) return false;
        }
        return true;
    }

    private static boolean isReadableJpeg(File file) {
        try (InputStream input = new FileInputStream(file)) {
            if (input.read() != 0xff || input.read() != 0xd8) return false;
        } catch (Exception error) {
            return false;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        return bounds.outWidth > 0 && bounds.outHeight > 0;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream input = new FileInputStream(file)) {
            copy(input, null, buffer, MAX_PHOTO_BYTES, digest);
        }
        return hex(digest.digest());
    }

    private static long copy(InputStream input, OutputStream output, byte[] buffer,
                             long maxBytes, MessageDigest digest) throws Exception {
        long total = 0L;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) continue;
            total += read;
            if (total > maxBytes) throw archiveTooLarge();
            if (digest != null) digest.update(buffer, 0, read);
            if (output != null) output.write(buffer, 0, read);
        }
        return total;
    }

    private static long drain(InputStream input, byte[] buffer, long maxBytes)
            throws Exception {
        return copy(input, null, buffer, maxBytes, null);
    }

    private static byte[] readBytes(InputStream input, byte[] buffer, int maxBytes)
            throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(input, output, buffer, maxBytes, null);
        return output.toByteArray();
    }

    private static void writeZipEntry(ZipOutputStream zip, String name, byte[] content)
            throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.US, "%02x", value));
        return result.toString();
    }

    private static IllegalArgumentException invalidArchive() {
        return new IllegalArgumentException("Fotoğraf ZIP yapısı geçersiz.");
    }

    private static IllegalArgumentException unsafeArchive() {
        return new IllegalArgumentException("Fotoğraf ZIP'i güvenli olmayan bir dosya yolu içeriyor.");
    }

    private static IllegalArgumentException archiveTooLarge() {
        return new IllegalArgumentException("Fotoğraf ZIP'i izin verilen boyutu aşıyor.");
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteTree(child);
        }
        file.delete();
    }

    public static final class NasPhotoSource {
        public final String id;
        public final File file;
        public final long sizeBytes;
        public final String sha256;
        public final JSONObject metadata;

        NasPhotoSource(String id, File file, long sizeBytes,
                       String sha256, JSONObject metadata) {
            this.id = id;
            this.file = file;
            this.sizeBytes = sizeBytes;
            this.sha256 = sha256;
            this.metadata = metadata;
        }
    }

    public static final class NasPhotoImport {
        public final String id;
        public final File file;
        public final long sizeBytes;
        public final String sha256;
        public final JSONObject metadata;

        public NasPhotoImport(String id, File file, long sizeBytes,
                              String sha256, JSONObject metadata) {
            this.id = id == null ? "" : id.trim();
            this.file = file;
            this.sizeBytes = sizeBytes;
            this.sha256 = sha256 == null ? "" : sha256.trim().toLowerCase(Locale.US);
            this.metadata = metadata;
        }
    }

    public static final class ArchiveInspection {
        public final long createdAtEpochMs;
        public final String sourceDeviceId;
        public final int photoCount;
        public final long totalPhotoBytes;

        ArchiveInspection(long createdAtEpochMs, String sourceDeviceId,
                          int photoCount, long totalPhotoBytes) {
            this.createdAtEpochMs = createdAtEpochMs;
            this.sourceDeviceId = sourceDeviceId == null ? "" : sourceDeviceId;
            this.photoCount = photoCount;
            this.totalPhotoBytes = totalPhotoBytes;
        }
    }

    public static final class ArchiveResult {
        public final int photoCount;
        public final int skippedCount;

        ArchiveResult(int photoCount, int skippedCount) {
            this.photoCount = photoCount;
            this.skippedCount = skippedCount;
        }
    }

    private static final class ExportPhoto {
        final String id;
        final String entryName;
        final File file;
        final long sizeBytes;
        final String sha256;
        final JSONObject metadata;

        ExportPhoto(String id, String entryName, File file, long sizeBytes,
                    String sha256, JSONObject metadata) {
            this.id = id;
            this.entryName = entryName;
            this.file = file;
            this.sizeBytes = sizeBytes;
            this.sha256 = sha256;
            this.metadata = metadata;
        }
    }

    private static final class ImportPhoto {
        final String id;
        final String entryName;
        final long sizeBytes;
        final String sha256;
        final JSONObject metadata;

        ImportPhoto(String id, String entryName, long sizeBytes,
                    String sha256, JSONObject metadata) {
            this.id = id;
            this.entryName = entryName;
            this.sizeBytes = sizeBytes;
            this.sha256 = sha256;
            this.metadata = metadata;
        }
    }

    private static final class ManifestData {
        final long createdAtEpochMs;
        final String sourceDeviceId;
        final long totalBytes;
        final List<ImportPhoto> photos;

        ManifestData(long createdAtEpochMs, String sourceDeviceId,
                     long totalBytes, List<ImportPhoto> photos) {
            this.createdAtEpochMs = createdAtEpochMs;
            this.sourceDeviceId = sourceDeviceId;
            this.totalBytes = totalBytes;
            this.photos = photos;
        }
    }

    private static final class ExtractedArchive {
        final byte[] manifestBytes;
        final Map<String, File> files;

        ExtractedArchive(byte[] manifestBytes, Map<String, File> files) {
            this.manifestBytes = manifestBytes;
            this.files = files;
        }
    }
}
