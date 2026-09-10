package com.alidogukan.avora.journal;

import android.content.Context;
import com.alidogukan.avora.models.SeasonOutcome;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Keeps explicit harvest and season-result notes private on this phone. */
public final class LocalSeasonOutcomeStore {
    private static final String PREFS = "avora_season_outcomes";
    private static final String KEY_ITEMS = "items";
    private final Context context;
    public LocalSeasonOutcomeStore(Context context) { this.context = context.getApplicationContext(); }

    public SeasonOutcome add(String zoneId, String result, String harvestAmount, String nextSeasonNote) {
        return add(zoneId, result, harvestAmount, "", "", "", "", "", nextSeasonNote);
    }

    public SeasonOutcome add(String zoneId, String result, String harvestAmount, String yieldNote,
                             String issuesNote, String successfulPractices, String waterSummary,
                             String fertilizerSummary, String nextSeasonNote) {
        SeasonOutcome value = new SeasonOutcome();
        value.setId(UUID.randomUUID().toString()); value.setZone_id(zoneId); value.setResult(result);
        value.setHarvest_amount(harvestAmount); value.setNext_season_note(nextSeasonNote);
        value.setYield_note(yieldNote); value.setIssues_note(issuesNote); value.setSuccessful_practices(successfulPractices);
        value.setWater_summary(waterSummary); value.setFertilizer_summary(fertilizerSummary);
        value.setRecorded_at_epoch(System.currentTimeMillis() / 1000L);
        JSONArray items = read();
        try {
            JSONObject item = new JSONObject();
            item.put("id", value.getId()); item.put("zone_id", value.getZone_id());
            item.put("season_id", value.getSeason_id());
            item.put("result", value.getResult()); item.put("harvest_amount", value.getHarvest_amount());
            item.put("next_season_note", value.getNext_season_note()); item.put("recorded_at_epoch", value.getRecorded_at_epoch());
            item.put("yield_note", value.getYield_note()); item.put("issues_note", value.getIssues_note()); item.put("successful_practices", value.getSuccessful_practices());
            item.put("water_summary", value.getWater_summary()); item.put("fertilizer_summary", value.getFertilizer_summary());
            items.put(item);
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ITEMS, items.toString()).apply();
        } catch (Exception error) { throw new IllegalStateException("Sezon sonucu kaydedilemedi", error); }
        return value;
    }

    /** Persists the exact season outcome produced by the atomic close operation. */
    public SeasonOutcome addForSeason(SeasonOutcome value) {
        if (value == null) throw new IllegalArgumentException("Sezon sonucu gerekli");
        if (value.getId().isBlank()) value.setId(UUID.randomUUID().toString());
        if (value.getRecorded_at_epoch() <= 0L) value.setRecorded_at_epoch(System.currentTimeMillis() / 1000L);
        JSONArray items = read();
        for (int i = 0; i < items.length(); i++) {
            JSONObject current = items.optJSONObject(i);
            if (current != null && value.getId().equals(current.optString("id"))) return value;
        }
        try {
            JSONObject item = new JSONObject();
            item.put("id", value.getId()); item.put("zone_id", value.getZone_id());
            item.put("season_id", value.getSeason_id()); item.put("result", value.getResult());
            item.put("harvest_amount", value.getHarvest_amount());
            item.put("next_season_note", value.getNext_season_note());
            item.put("recorded_at_epoch", value.getRecorded_at_epoch());
            item.put("yield_note", value.getYield_note()); item.put("issues_note", value.getIssues_note());
            item.put("successful_practices", value.getSuccessful_practices());
            item.put("water_summary", value.getWater_summary()); item.put("fertilizer_summary", value.getFertilizer_summary());
            items.put(item);
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ITEMS, items.toString()).apply();
        } catch (Exception error) { throw new IllegalStateException("Sezon sonucu kaydedilemedi", error); }
        return value;
    }

    public List<SeasonOutcome> load() {
        List<SeasonOutcome> values = new ArrayList<>(); JSONArray items = read();
        for (int i = 0; i < items.length(); i++) try {
            JSONObject item = items.getJSONObject(i); SeasonOutcome value = new SeasonOutcome();
            value.setId(item.optString("id")); value.setZone_id(item.optString("zone_id")); value.setResult(item.optString("result"));
            value.setSeason_id(item.optString("season_id"));
            value.setHarvest_amount(item.optString("harvest_amount")); value.setNext_season_note(item.optString("next_season_note"));
            value.setRecorded_at_epoch(item.optLong("recorded_at_epoch"));
            value.setYield_note(item.optString("yield_note")); value.setIssues_note(item.optString("issues_note")); value.setSuccessful_practices(item.optString("successful_practices"));
            value.setWater_summary(item.optString("water_summary")); value.setFertilizer_summary(item.optString("fertilizer_summary")); values.add(value);
        } catch (Exception ignored) { }
        values.sort(Comparator.comparingLong(SeasonOutcome::getRecorded_at_epoch).reversed()); return values;
    }

    public int removeByZone(String zoneId) {
        if (zoneId == null || zoneId.isBlank()) return 0;
        JSONArray current = read();
        JSONArray remaining = new JSONArray();
        int removed = 0;
        for (int i = 0; i < current.length(); i++) {
            JSONObject item = current.optJSONObject(i);
            if (item == null) continue;
            if (zoneId.equals(item.optString("zone_id"))) {
                removed++;
            } else {
                remaining.put(item);
            }
        }
        if (removed > 0) context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ITEMS, remaining.toString()).apply();
        return removed;
    }
    public int removeBySeason(String seasonId) {
        if (seasonId == null || seasonId.isBlank()) return 0;
        JSONArray current = read();
        JSONArray remaining = new JSONArray();
        int removed = 0;
        for (int i = 0; i < current.length(); i++) {
            JSONObject item = current.optJSONObject(i);
            if (item == null) continue;
            if (seasonId.equals(item.optString("season_id"))
                    || seasonId.equals(item.optString("id"))) {
                removed++;
            } else {
                remaining.put(item);
            }
        }
        if (removed > 0) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_ITEMS, remaining.toString())
                    .apply();
        }
        return removed;
    }

    private JSONArray read() {
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ITEMS, "[]");
        try { return new JSONArray(raw == null ? "[]" : raw); } catch (Exception ignored) { return new JSONArray(); }
    }
}
