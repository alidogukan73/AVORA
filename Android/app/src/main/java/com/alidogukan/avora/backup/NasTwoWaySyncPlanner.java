package com.alidogukan.avora.backup;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

/** Builds a non-destructive union: current Firebase values win, missing NAS values are added. */
public final class NasTwoWaySyncPlanner {
    private NasTwoWaySyncPlanner() { }

    public static Plan plan(JSONObject currentBackup, JSONObject nasBackup) {
        if (currentBackup == null) throw new IllegalArgumentException("Current backup is required.");
        try {
            JSONObject current = new JSONObject(currentBackup.toString());
            JSONObject remote = nasBackup == null ? new JSONObject()
                    : new JSONObject(nasBackup.toString());
            int toCurrent = missingLeafCount(remote.optJSONObject("firebase_data"),
                    current.optJSONObject("firebase_data"));
            int toNas = missingLeafCount(current.optJSONObject("firebase_data"),
                    remote.optJSONObject("firebase_data"));
            mergeMissing(current, remote, "firebase_data");
            mergeMissing(current, remote, "local_preferences");
            AvoraBackupManager.refreshIntegrity(current);
            return new Plan(current, toCurrent, toNas);
        } catch (JSONException error) {
            throw new IllegalArgumentException("Backups could not be merged.", error);
        }
    }

    private static void mergeMissing(JSONObject targetRoot, JSONObject sourceRoot,
                                     String section) throws JSONException {
        JSONObject source = sourceRoot.optJSONObject(section);
        if (source == null) return;
        JSONObject target = targetRoot.optJSONObject(section);
        if (target == null) {
            targetRoot.put(section, new JSONObject(source.toString()));
            return;
        }
        mergeMissingObjects(target, source);
    }

    private static void mergeMissingObjects(JSONObject target, JSONObject source)
            throws JSONException {
        Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object sourceValue = source.opt(key);
            if (!target.has(key) || target.isNull(key)) {
                target.put(key, copy(sourceValue));
                continue;
            }
            Object targetValue = target.opt(key);
            if (targetValue instanceof JSONObject && sourceValue instanceof JSONObject) {
                mergeMissingObjects((JSONObject) targetValue, (JSONObject) sourceValue);
            }
        }
    }

    private static int missingLeafCount(JSONObject source, JSONObject target) {
        if (source == null) return 0;
        int count = 0;
        Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object sourceValue = source.opt(key);
            if (target == null || !target.has(key) || target.isNull(key)) {
                count += leafCount(sourceValue);
            } else if (sourceValue instanceof JSONObject
                    && target.opt(key) instanceof JSONObject) {
                count += missingLeafCount((JSONObject) sourceValue,
                        (JSONObject) target.opt(key));
            }
        }
        return count;
    }

    private static int leafCount(Object value) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            if (object.length() == 0) return 1;
            int count = 0;
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) count += leafCount(object.opt(keys.next()));
            return count;
        }
        if (value instanceof JSONArray) return Math.max(1, ((JSONArray) value).length());
        return 1;
    }

    private static Object copy(Object value) throws JSONException {
        if (value instanceof JSONObject) return new JSONObject(value.toString());
        if (value instanceof JSONArray) return new JSONArray(value.toString());
        return value;
    }

    public static final class Plan {
        public final JSONObject mergedBackup;
        public final int dataMissingOnCurrent;
        public final int dataMissingOnNas;

        Plan(JSONObject mergedBackup, int dataMissingOnCurrent, int dataMissingOnNas) {
            this.mergedBackup = mergedBackup;
            this.dataMissingOnCurrent = dataMissingOnCurrent;
            this.dataMissingOnNas = dataMissingOnNas;
        }
    }
}
