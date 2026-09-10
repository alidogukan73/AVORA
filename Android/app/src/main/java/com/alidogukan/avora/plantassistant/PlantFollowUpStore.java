package com.alidogukan.avora.plantassistant;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Keeps one continuous follow-up chain per zone and season on this phone. */
public final class PlantFollowUpStore {
    private static final String PREFS = "plant_assistant_followups";
    private static final String KEY = "items";
    static final long FOLLOW_UP_DELAY_SECONDS = 3L * 24L * 60L * 60L;
    private static final Object LOCK = new Object();
    private final Context context;

    public PlantFollowUpStore(Context context) {
        this.context = context.getApplicationContext();
    }

    public Result registerAnalysis(String zoneId, String photoId, String title) {
        return registerAnalysis(zoneId, "", photoId, title);
    }

    public Result registerAnalysis(String zoneId, String seasonId,
                                   String photoId, String title) {
        return registerAnalysis(zoneId, seasonId, photoId, title,
                System.currentTimeMillis() / 1000L);
    }

    Result registerAnalysis(String zoneId, String seasonId,
                            String photoId, String title, long nowEpochSeconds) {
        String cleanZoneId = safe(zoneId);
        String cleanSeasonId = safe(seasonId);
        String cleanPhotoId = safe(photoId);
        if (cleanZoneId.isEmpty() || cleanPhotoId.isEmpty()
                || nowEpochSeconds <= 0L) {
            return Result.none();
        }

        synchronized (LOCK) {
            JSONArray items = read();
            try {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    if (knowsPhoto(item, cleanPhotoId)) {
                        return item.optBoolean("completed", false)
                                ? Result.none()
                                : Result.existing(item.optLong("due_at_epoch"));
                    }
                }

                JSONObject newestDue = null;
                JSONObject upcomingTask = null;
                long newestDueAt = Long.MIN_VALUE;
                long upcomingDueAt = Long.MAX_VALUE;
                boolean completedAny = false;
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    if (!sameScope(item, cleanZoneId, cleanSeasonId)
                            || item.optBoolean("completed", false)) continue;
                    long dueAt = item.optLong("due_at_epoch", 0L);
                    if (dueAt > nowEpochSeconds) {
                        if (dueAt < upcomingDueAt) {
                            upcomingDueAt = dueAt;
                            upcomingTask = item;
                        }
                        continue;
                    }
                    if (dueAt <= 0L) continue;
                    item.put("completed", true);
                    item.put("completed_photo_id", cleanPhotoId);
                    item.put("completed_at_epoch", nowEpochSeconds);
                    completedAny = true;
                    if (newestDue == null || dueAt > newestDueAt) {
                        newestDue = item;
                        newestDueAt = dueAt;
                    }
                }

                if (completedAny) {
                    long nextDueAt = upcomingDueAt;
                    if (nextDueAt == Long.MAX_VALUE) {
                        JSONObject task = task(cleanZoneId, cleanSeasonId,
                                cleanPhotoId, title, nowEpochSeconds);
                        items.put(task);
                        nextDueAt = task.optLong("due_at_epoch");
                    } else if (upcomingTask != null) {
                        rememberPhoto(upcomingTask, cleanPhotoId);
                    }
                    save(items);
                    return Result.completed(
                            newestDue == null ? "" : newestDue.optString("title"),
                            nextDueAt);
                }

                if (upcomingDueAt != Long.MAX_VALUE) {
                    rememberPhoto(upcomingTask, cleanPhotoId);
                    save(items);
                    return Result.existing(upcomingDueAt);
                }
                JSONObject task = task(cleanZoneId, cleanSeasonId,
                        cleanPhotoId, title, nowEpochSeconds);
                items.put(task);
                save(items);
                return Result.scheduled(task.optLong("due_at_epoch"));
            } catch (Exception ignored) {
                return Result.none();
            }
        }
    }

    /** Returns only tasks whose three-day waiting period has ended. */
    public List<DueTask> dueUnnotified(long nowEpochSeconds) {
        synchronized (LOCK) {
            ArrayList<DueTask> due = new ArrayList<>();
            JSONArray items = read();
            try {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    long dueAt = item.optLong("due_at_epoch", 0L);
                    if (!item.optBoolean("completed", false)
                            && !item.optBoolean("notified", false)
                            && dueAt > 0L && dueAt <= nowEpochSeconds) {
                        due.add(new DueTask(item.optString("analysis_photo_id"),
                                item.optString("zone_id"), item.optString("season_id"),
                                item.optString("title"), dueAt));
                    }
                }
            } catch (Exception ignored) {
                return new ArrayList<>();
            }
            return due;
        }
    }

    public void markNotified(String analysisPhotoId) {
        String cleanPhotoId = safe(analysisPhotoId);
        if (cleanPhotoId.isEmpty()) return;
        synchronized (LOCK) {
            JSONArray items = read();
            try {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    if (cleanPhotoId.equals(safe(item.optString("analysis_photo_id")))) {
                        item.put("notified", true);
                        item.put("notified_at_epoch", System.currentTimeMillis() / 1000L);
                        save(items);
                        return;
                    }
                }
            } catch (Exception ignored) { }
        }
    }
    private static JSONObject task(String zoneId, String seasonId, String photoId,
                                   String title, long nowEpochSeconds) throws Exception {
        JSONObject task = new JSONObject();
        task.put("zone_id", zoneId);
        task.put("title", safe(title));
        task.put("season_id", seasonId);
        task.put("analysis_photo_id", photoId);
        task.put("created_at_epoch", nowEpochSeconds);
        task.put("due_at_epoch", nowEpochSeconds + FOLLOW_UP_DELAY_SECONDS);
        task.put("completed", false);
        task.put("notified", false);
        return task;
    }

    private static boolean sameScope(JSONObject item, String zoneId, String seasonId) {
        return zoneId.equals(safe(item.optString("zone_id")))
                && seasonId.equals(safe(item.optString("season_id")));
    }

    private static boolean knowsPhoto(JSONObject item, String photoId) {
        if (photoId.equals(safe(item.optString("analysis_photo_id")))) return true;
        JSONArray observed = item.optJSONArray("observed_photo_ids");
        for (int i = 0; observed != null && i < observed.length(); i++) {
            if (photoId.equals(safe(observed.optString(i)))) return true;
        }
        return false;
    }

    private static void rememberPhoto(JSONObject item, String photoId) throws Exception {
        if (item == null || knowsPhoto(item, photoId)) return;
        JSONArray observed = item.optJSONArray("observed_photo_ids");
        if (observed == null) observed = new JSONArray();
        observed.put(photoId);
        item.put("observed_photo_ids", observed);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }


    private JSONArray read() {
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, "[]");
        try {
            return new JSONArray(raw == null ? "[]" : raw);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private void save(JSONArray items) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, items.toString()).apply();
    }

    public static final class DueTask {
        public final String photoId;
        public final String zoneId;
        public final String seasonId;
        public final String title;
        public final long dueAtEpoch;

        DueTask(String photoId, String zoneId, String seasonId,
                String title, long dueAtEpoch) {
            this.photoId = photoId == null ? "" : photoId;
            this.zoneId = zoneId == null ? "" : zoneId;
            this.seasonId = seasonId == null ? "" : seasonId;
            this.title = title == null ? "" : title;
            this.dueAtEpoch = dueAtEpoch;
        }
    }

    public static final class Result {
        public final String type;
        public final long dueAtEpoch;
        public final String previousTitle;

        private Result(String type, long dueAtEpoch, String previousTitle) {
            this.type = type;
            this.dueAtEpoch = dueAtEpoch;
            this.previousTitle = previousTitle;
        }

        static Result scheduled(long dueAtEpoch) {
            return new Result("SCHEDULED", dueAtEpoch, "");
        }

        static Result existing(long dueAtEpoch) {
            return new Result("SCHEDULED_EXISTING", dueAtEpoch, "");
        }

        static Result completed(String previousTitle, long nextDueAtEpoch) {
            return new Result("COMPLETED", nextDueAtEpoch,
                    previousTitle == null ? "" : previousTitle);
        }

        static Result none() {
            return new Result("NONE", 0L, "");
        }
    }
}
