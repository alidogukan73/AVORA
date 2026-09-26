package com.alidogukan.avora.backup;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.Map;

/** Builds a non-destructive union: current Firebase values win, missing NAS values are added. */
public final class NasTwoWaySyncPlanner {
    private NasTwoWaySyncPlanner() { }

    public static Plan plan(JSONObject currentBackup, JSONObject nasBackup) {
        if (currentBackup == null) throw new IllegalArgumentException("Current backup is required.");
        try {
            Map<String, Object> current = AvoraBackupManager.objectMap(currentBackup);
            Map<String, Object> remote = nasBackup == null
                    ? Collections.emptyMap() : AvoraBackupManager.objectMap(nasBackup);
            NasTwoWaySyncMerge.Result merged = NasTwoWaySyncMerge.plan(current, remote);
            Object json = AvoraBackupManager.jsonValue(merged.mergedBackup);
            if (!(json instanceof JSONObject)) {
                throw new JSONException("Merged backup is not a JSON object.");
            }
            JSONObject mergedBackup = (JSONObject) json;
            AvoraBackupManager.refreshIntegrity(mergedBackup);
            return new Plan(mergedBackup, merged.dataMissingOnCurrent,
                    merged.dataMissingOnNas);
        } catch (JSONException error) {
            throw new IllegalArgumentException("Backups could not be merged.", error);
        }
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
