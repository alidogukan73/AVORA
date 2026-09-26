package com.alidogukan.avora.backup;

import android.content.Context;
import android.content.SharedPreferences;

import com.alidogukan.avora.config.AppInfo;
import com.alidogukan.avora.firebase.FirebaseRepository;
import com.alidogukan.avora.language.AvoraLanguageManager;
import com.alidogukan.avora.theme.AvoraThemeManager;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Creates and restores portable AVORA backups.
 * <p>The backup deliberately contains user configuration and records only. Live sensor
 * measurements, device health, forecasts, access tokens and actuator commands never enter the
 * file. Restore is merge-only and finishes with the pump off and automatic irrigation disabled.</p>
 */
public final class AvoraBackupManager {
    public static final String SCHEMA = "avora-portable-backup";
    public static final int SCHEMA_VERSION = 1;

    private static final Set<String> ZONE_FIELDS = new LinkedHashSet<>(Arrays.asList(
            "zone_id", "area_id", "area_name", "location_name", "area_icon", "area_color",
            "low_moisture_alert_enabled", "watering_complete_alert_enabled",
            "name", "plant_type", "emoji", "sensor_id", "sensor_enabled",
            "sensor_calibration_dry_raw", "sensor_calibration_wet_raw",
            "sensor_config_updated_at_epoch", "valve_id", "valve_type", "valve_mode",
            "valve_mode_updated_at_epoch", "valve_gpio_bcm", "valve_gpio_physical_pin",
            "enabled", "irrigation_enabled", "order", "moisture_limit", "pump_duration",
            "manual_watering_duration_seconds", "manual_watering_duration_updated_at_epoch",
            "cooldown_seconds", "restart_delta", "season", "fertilization",
            "lifecycle_status", "created_at_epoch", "archived_at_epoch",
            "previous_sensor_id", "previous_valve_id"
    ));

    private static final Set<String> GLOBAL_IRRIGATION_FIELDS = new LinkedHashSet<>(Arrays.asList(
            "moisture_limit", "pump_duration", "cooldown_seconds", "restart_delta", "enabled"
    ));

    private static final List<String> RECORD_ROOTS = Arrays.asList(
            "fertilizer_products", "fertilizer_plans", "fertilizer_history",
            "watering_history", "notifications", "notification_settings"
    );
    // Photo files are intentionally outside the portable JSON backup. Restoring their metadata
    // alone can create broken references and can also fail newer Firebase validation rules when
    // the source contains legacy photo records.
    private static final List<String> GARDEN_JOURNAL_ROOTS =
            Arrays.asList("seasons", "events", "season_outcomes");

    private static final List<String> LOCAL_PREFERENCE_FILES = Arrays.asList(
            "avora_garden_profile", "avora_display_units", "avora_notification_settings",
            "settings_hub_preferences", "plant_list_preferences", "garden_journal_events",
            "avora_season_outcomes", "avora_notifications", "plant_assistant_recommendation",
            "plant_assistant_followups", "avora_theme_preferences", "avora_language_preferences",
            "avora_fertilization_preferences", "avora_seedling_varieties"
    );

    private final Context context;
    private final DatabaseReference deviceRef;

    public AvoraBackupManager(Context context) {
        this.context = context.getApplicationContext();
        this.deviceRef = FirebaseDatabase.getInstance()
                .getReference("devices")
                .child(AppInfo.DEVICE_ID);
    }

    public Task<JSONObject> createBackup() {
        return deviceRef.get().continueWith(task -> {
            if (!task.isSuccessful()) {
                throw task.getException() == null
                        ? new IllegalStateException("Firebase verileri okunamadı.")
                        : task.getException();
            }
            DataSnapshot device = task.getResult();
            if (device == null || !device.exists()) {
                throw new IllegalStateException("Bu cihaza ait Firebase verisi bulunamadı.");
            }
            return buildBackup(device);
        });
    }

    public ValidationResult validate(JSONObject backup) {
        if (backup == null) {
            return ValidationResult.invalid("Yedek dosyası boş.");
        }
        if (!SCHEMA.equals(backup.optString("schema"))) {
            return ValidationResult.invalid("Bu dosya bir AVORA yedeği değil.");
        }
        if (backup.optInt("schema_version", -1) != SCHEMA_VERSION) {
            return ValidationResult.invalid("Yedek sürümü bu uygulamayla uyumlu değil.");
        }
        if (!AppInfo.DEVICE_ID.equals(backup.optString("device_id"))) {
            return ValidationResult.invalid("Yedek başka bir cihaz kimliğine ait.");
        }
        JSONObject firebase = backup.optJSONObject("firebase_data");
        if (firebase == null) {
            return ValidationResult.invalid("Yedek veri bölümü eksik.");
        }
        ValidationResult integrity = validateIntegrity(backup);
        if (integrity != null) {
            return integrity;
        }
        JSONObject zones = firebase.optJSONObject("zones");
        int zoneCount = zones == null ? 0 : zones.length();
        int recordCount = countObject(firebase.optJSONObject("watering_history"))
                + countObject(firebase.optJSONObject("fertilizer_history"))
                + countObject(firebase.optJSONObject("notifications"));
        JSONObject journal = firebase.optJSONObject("garden_journal");
        if (journal != null) {
            for (String section : GARDEN_JOURNAL_ROOTS) {
                recordCount += countObject(journal.optJSONObject(section));
            }
        }
        return ValidationResult.valid(
                backup.optLong("created_at_epoch_ms", 0L),
                backup.optString("app_version", ""), zoneCount, recordCount);
    }

    public Task<Void> restoreBackup(JSONObject backup) {
        ValidationResult validation = validate(backup);
        if (!validation.valid) {
            return com.google.android.gms.tasks.Tasks.forException(
                    new IllegalArgumentException(validation.message));
        }

        Map<String, Object> updates = new HashMap<>();
        try {
            JSONObject firebase = backup.getJSONObject("firebase_data");
            addObject(firebase.optJSONObject("profile"), "profile", updates);
            restoreGlobalIrrigation(firebase.optJSONObject("global_irrigation_settings"), updates);
            restoreWeather(firebase.optJSONObject("weather"), updates);
            restoreZones(firebase.optJSONObject("zones"), updates);
            for (String root : RECORD_ROOTS) {
                addObject(firebase.optJSONObject(root), root, updates);
            }
            restoreGardenJournal(firebase.optJSONObject("garden_journal"), updates);

            // A restored configuration must never execute an old actuator request.
            updates.put("commands/relay", false);
            updates.put("commands/auto_mode", false);
            updates.put("commands/restart_device", false);
            updates.put("commands/zone_test", null);
            updates.put("commands/relay_requested_at", ServerValue.TIMESTAMP);
        } catch (JSONException error) {
            return com.google.android.gms.tasks.Tasks.forException(error);
        }

        // Data Sync can be opened after the cached Firebase token has aged. Refresh and verify
        // the device-owner claim before attempting the single atomic restore update.
        Task<Boolean> authorization = new FirebaseRepository().authenticateAnonymously();
        return authorization.continueWithTask(task -> {
            if (!task.isSuccessful()) {
                Exception error = task.getException();
                return Tasks.forException(error == null
                        ? new IllegalStateException("Firebase cihaz yetkisi doğrulanamadı.")
                        : error);
            }
            if (!Boolean.TRUE.equals(task.getResult())) {
                return Tasks.forException(new SecurityException(
                        "Bu telefonun AVORA cihaz yetkisi bulunmuyor."));
            }
            return deviceRef.updateChildren(updates);
        }).continueWith(task -> {
            if (!task.isSuccessful()) {
                throw task.getException() == null
                        ? new IllegalStateException("Firebase geri yükleme işlemi tamamlanamadı.")
                        : task.getException();
            }
            applyLocalPreferences(backup.optJSONObject("local_preferences"));
            AvoraLanguageManager.applySavedLanguage(context);
            AvoraThemeManager.applySavedTheme(context);
            return null;
        });
    }

    private JSONObject buildBackup(DataSnapshot device) throws JSONException {
        JSONObject firebase = new JSONObject();
        copySnapshot(device.child("profile"), firebase, "profile");
        copyGlobalIrrigation(device.child("commands"), firebase);
        copyWeather(device.child("weather"), firebase);
        copyZones(device.child("zones"), firebase);
        for (String root : RECORD_ROOTS) {
            copySnapshot(device.child(root), firebase, root);
        }
        copyGardenJournal(device.child("garden_journal"), firebase);

        int zoneCount = firebase.optJSONObject("zones") == null
                ? 0 : firebase.optJSONObject("zones").length();
        JSONObject backup = new JSONObject();
        backup.put("schema", SCHEMA);
        backup.put("schema_version", SCHEMA_VERSION);
        backup.put("created_at_epoch_ms", System.currentTimeMillis());
        backup.put("app_version", AppInfo.APP_VERSION);
        backup.put("device_id", AppInfo.DEVICE_ID);
        backup.put("restore_mode", "safe_merge_auto_irrigation_off");
        backup.put("firebase_data", firebase);
        backup.put("local_preferences", createLocalPreferencesSnapshot());
        backup.put("summary", new JSONObject()
                .put("zone_count", zoneCount)
                .put("photo_files_included", false)
                .put("live_device_data_included", false));
        refreshIntegrity(backup);
        return backup;
    }

    /** Re-signs a backup after a safe, in-memory merge. */
    static void refreshIntegrity(JSONObject backup) throws JSONException {
        backup.remove("integrity");
        Map<String, Object> integrityPayload = objectMap(backup);
        backup.put("integrity", new JSONObject()
                .put("algorithm", BackupIntegrity.ALGORITHM)
                .put("content_sha256", BackupIntegrity.sha256(integrityPayload)));
    }

    /** Returns an invalid result, or {@code null} when integrity is valid/not present. */
    private ValidationResult validateIntegrity(JSONObject backup) {
        // Backups created before integrity metadata was introduced remain restorable.
        if (!backup.has("integrity")) {
            return null;
        }
        JSONObject integrity = backup.optJSONObject("integrity");
        if (integrity == null) {
            return ValidationResult.invalid("Yedek bütünlük bilgisi geçersiz.");
        }
        if (!BackupIntegrity.ALGORITHM.equals(integrity.optString("algorithm"))) {
            return ValidationResult.invalid("Yedek bütünlük yöntemi desteklenmiyor.");
        }
        try {
            Map<String, Object> payload = objectMap(backup);
            payload.remove("integrity");
            if (!BackupIntegrity.matches(
                    integrity.optString("content_sha256"), payload)) {
                return ValidationResult.invalid(
                        "Yedek bütünlük kontrolünden geçemedi; dosya eksik veya değişmiş.");
            }
        } catch (JSONException error) {
            return ValidationResult.invalid("Yedek bütünlük bilgisi okunamadı.");
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> objectMap(JSONObject value) throws JSONException {
        Object converted = javaValue(value);
        if (!(converted instanceof Map)) {
            throw new JSONException("JSON object expected");
        }
        return (Map<String, Object>) converted;
    }

    private void copyGlobalIrrigation(DataSnapshot commands, JSONObject firebase)
            throws JSONException {
        JSONObject values = new JSONObject();
        for (String field : GLOBAL_IRRIGATION_FIELDS) {
            DataSnapshot child = commands.child(field);
            if (child.exists()) {
                values.put(field, jsonValue(child.getValue()));
            }
        }
        if (values.length() > 0) {
            firebase.put("global_irrigation_settings", values);
        }
    }

    private void copyWeather(DataSnapshot weather, JSONObject firebase) throws JSONException {
        JSONObject values = new JSONObject();
        if (weather.child("location").exists()) {
            values.put("location", jsonValue(weather.child("location").getValue()));
        }
        if (weather.child("irrigation_settings").exists()) {
            values.put("irrigation_settings",
                    jsonValue(weather.child("irrigation_settings").getValue()));
        }
        if (values.length() > 0) {
            firebase.put("weather", values);
        }
    }

    private void copyZones(DataSnapshot zones, JSONObject firebase) throws JSONException {
        JSONObject result = new JSONObject();
        for (DataSnapshot zone : zones.getChildren()) {
            if (!validFirebaseKey(zone.getKey())) {
                continue;
            }
            JSONObject safeZone = new JSONObject();
            for (String field : ZONE_FIELDS) {
                DataSnapshot value = zone.child(field);
                if (value.exists()) {
                    safeZone.put(field, jsonValue(value.getValue()));
                }
            }
            if (safeZone.length() > 0) {
                result.put(zone.getKey(), safeZone);
            }
        }
        if (result.length() > 0) {
            firebase.put("zones", result);
        }
    }

    private void copyGardenJournal(DataSnapshot source, JSONObject firebase)
            throws JSONException {
        JSONObject journal = new JSONObject();
        for (String section : GARDEN_JOURNAL_ROOTS) {
            copySnapshot(source.child(section), journal, section);
        }
        if (journal.length() > 0) {
            firebase.put("garden_journal", journal);
        }
    }

    private void copySnapshot(DataSnapshot source, JSONObject target, String name)
            throws JSONException {
        if (source.exists()) {
            Object value = jsonValue(source.getValue());
            if (value != JSONObject.NULL) {
                target.put(name, value);
            }
        }
    }

    private JSONObject createLocalPreferencesSnapshot() throws JSONException {
        JSONObject result = new JSONObject();
        for (String preferenceFile : LOCAL_PREFERENCE_FILES) {
            Map<String, ?> values = context
                    .getSharedPreferences(preferenceFile, Context.MODE_PRIVATE)
                    .getAll();
            if (values.isEmpty()) {
                continue;
            }
            JSONObject file = new JSONObject();
            for (Map.Entry<String, ?> entry : values.entrySet()) {
                file.put(entry.getKey(), encodePreference(entry.getValue()));
            }
            result.put(preferenceFile, file);
        }
        return result;
    }

    private JSONObject encodePreference(Object value) throws JSONException {
        JSONObject encoded = new JSONObject();
        if (value instanceof Boolean) {
            encoded.put("type", "boolean").put("value", value);
        } else if (value instanceof Integer) {
            encoded.put("type", "integer").put("value", value);
        } else if (value instanceof Long) {
            encoded.put("type", "long").put("value", value);
        } else if (value instanceof Float) {
            encoded.put("type", "float").put("value", ((Float) value).doubleValue());
        } else if (value instanceof Set) {
            JSONArray items = new JSONArray();
            for (Object item : (Set<?>) value) {
                if (item instanceof String) {
                    items.put(item);
                }
            }
            encoded.put("type", "string_set").put("value", items);
        } else {
            encoded.put("type", "string").put("value", value == null ? "" : value.toString());
        }
        return encoded;
    }

    private void applyLocalPreferences(JSONObject localPreferences) throws JSONException {
        if (localPreferences == null) {
            return;
        }
        for (String preferenceFile : LOCAL_PREFERENCE_FILES) {
            JSONObject values = localPreferences.optJSONObject(preferenceFile);
            if (values == null) {
                continue;
            }
            SharedPreferences.Editor editor = context
                    .getSharedPreferences(preferenceFile, Context.MODE_PRIVATE)
                    .edit();
            for (java.util.Iterator<String> keys = values.keys(); keys.hasNext(); ) {
                String key = keys.next();
                JSONObject encoded = values.optJSONObject(key);
                if (encoded == null) {
                    continue;
                }
                String type = encoded.optString("type");
                switch (type) {
                    case "boolean":
                        editor.putBoolean(key, encoded.optBoolean("value"));
                        break;
                    case "integer":
                        editor.putInt(key, encoded.optInt("value"));
                        break;
                    case "long":
                        editor.putLong(key, encoded.optLong("value"));
                        break;
                    case "float":
                        editor.putFloat(key, (float) encoded.optDouble("value"));
                        break;
                    case "string_set":
                        JSONArray array = encoded.optJSONArray("value");
                        Set<String> set = new HashSet<>();
                        if (array != null) {
                            for (int index = 0; index < array.length(); index++) {
                                set.add(array.optString(index));
                            }
                        }
                        editor.putStringSet(key, set);
                        break;
                    case "string":
                        editor.putString(key, encoded.optString("value"));
                        break;
                    default:
                        break;
                }
            }
            editor.apply();
        }
    }

    private void restoreGlobalIrrigation(JSONObject values, Map<String, Object> updates)
            throws JSONException {
        if (values == null) {
            return;
        }
        for (String field : GLOBAL_IRRIGATION_FIELDS) {
            if (values.has(field)) {
                updates.put("commands/" + field, javaValue(values.get(field)));
            }
        }
    }

    private void restoreWeather(JSONObject weather, Map<String, Object> updates)
            throws JSONException {
        if (weather == null) {
            return;
        }
        addObject(weather.optJSONObject("location"), "weather/location", updates);
        addObject(weather.optJSONObject("irrigation_settings"),
                "weather/irrigation_settings", updates);
    }

    private void restoreZones(JSONObject zones, Map<String, Object> updates) throws JSONException {
        if (zones == null) {
            return;
        }
        for (java.util.Iterator<String> keys = zones.keys(); keys.hasNext(); ) {
            String zoneId = keys.next();
            if (!validFirebaseKey(zoneId)) {
                continue;
            }
            JSONObject zone = zones.optJSONObject(zoneId);
            if (zone == null) {
                continue;
            }
            for (String field : ZONE_FIELDS) {
                if (!zone.has(field)) {
                    continue;
                }
                Object value = zone.get(field);
                String path = "zones/" + zoneId + "/" + field;
                if (value instanceof JSONObject) {
                    addObject((JSONObject) value, path, updates);
                } else if (value instanceof JSONArray) {
                    updates.put(path, javaValue(value));
                } else if (value != JSONObject.NULL) {
                    updates.put(path, value);
                }
            }
        }
    }

    private void restoreGardenJournal(JSONObject journal, Map<String, Object> updates)
            throws JSONException {
        if (journal == null) {
            return;
        }
        for (String section : GARDEN_JOURNAL_ROOTS) {
            addObject(journal.optJSONObject(section), "garden_journal/" + section, updates);
        }
    }

    static boolean isRestorableGardenJournalSection(String section) {
        return GARDEN_JOURNAL_ROOTS.contains(section);
    }

    private void addObject(JSONObject object, String prefix, Map<String, Object> updates)
            throws JSONException {
        if (object == null) {
            return;
        }
        for (java.util.Iterator<String> keys = object.keys(); keys.hasNext(); ) {
            String key = keys.next();
            if (!validFirebaseKey(key)) {
                continue;
            }
            Object value = object.get(key);
            String path = prefix + "/" + key;
            if (value instanceof JSONObject) {
                addObject((JSONObject) value, path, updates);
            } else if (value instanceof JSONArray) {
                updates.put(path, javaValue(value));
            } else if (value != JSONObject.NULL) {
                updates.put(path, value);
            }
        }
    }

    static Object jsonValue(Object value) throws JSONException {
        if (value == null) {
            return JSONObject.NULL;
        }
        if (value instanceof Map) {
            JSONObject object = new JSONObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (entry.getKey() != null) {
                    object.put(entry.getKey().toString(), jsonValue(entry.getValue()));
                }
            }
            return object;
        }
        if (value instanceof List) {
            JSONArray array = new JSONArray();
            for (Object item : (List<?>) value) {
                array.put(jsonValue(item));
            }
            return array;
        }
        if (value instanceof Boolean || value instanceof Number || value instanceof String) {
            return value;
        }
        return value.toString();
    }

    private static Object javaValue(Object value) throws JSONException {
        if (value == JSONObject.NULL) {
            return null;
        }
        if (value instanceof JSONObject) {
            Map<String, Object> map = new HashMap<>();
            JSONObject object = (JSONObject) value;
            for (java.util.Iterator<String> keys = object.keys(); keys.hasNext(); ) {
                String key = keys.next();
                if (validFirebaseKey(key)) {
                    map.put(key, javaValue(object.get(key)));
                }
            }
            return map;
        }
        if (value instanceof JSONArray) {
            List<Object> list = new ArrayList<>();
            JSONArray array = (JSONArray) value;
            for (int index = 0; index < array.length(); index++) {
                list.add(javaValue(array.get(index)));
            }
            return list;
        }
        return value;
    }

    private static boolean validFirebaseKey(String key) {
        return key != null && !key.isBlank()
                && key.indexOf('/') < 0 && key.indexOf('.') < 0 && key.indexOf('#') < 0
                && key.indexOf('$') < 0 && key.indexOf('[') < 0 && key.indexOf(']') < 0;
    }

    private static int countObject(JSONObject object) {
        return object == null ? 0 : object.length();
    }

    public static final class ValidationResult {
        public final boolean valid;
        public final String message;
        public final long createdAtEpochMs;
        public final String appVersion;
        public final int zoneCount;
        public final int recordCount;

        private ValidationResult(boolean valid, String message, long createdAtEpochMs,
                                 String appVersion, int zoneCount, int recordCount) {
            this.valid = valid;
            this.message = message;
            this.createdAtEpochMs = createdAtEpochMs;
            this.appVersion = appVersion;
            this.zoneCount = zoneCount;
            this.recordCount = recordCount;
        }

        static ValidationResult invalid(String message) {
            return new ValidationResult(false, message, 0L, "", 0, 0);
        }

        static ValidationResult valid(long createdAtEpochMs, String appVersion,
                                      int zoneCount, int recordCount) {
            return new ValidationResult(true, "", createdAtEpochMs,
                    appVersion, zoneCount, recordCount);
        }
    }
}
