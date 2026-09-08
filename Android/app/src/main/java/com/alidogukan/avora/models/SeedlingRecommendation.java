package com.alidogukan.avora.models;
import com.google.firebase.database.IgnoreExtraProperties;
import java.util.ArrayList;
import java.util.List;

@IgnoreExtraProperties
public final class SeedlingRecommendation {
    private int score;
    private String severity = "";
    private String title = "";
    private String message = "";
    private String action = "";
    private List<String> reasons = new ArrayList<>();
    private boolean advisory_only = true;
    private long updated_at_epoch;
    public SeedlingRecommendation() { }
    public int getScore() { return score; }
    public void setScore(int v) { score = Math.max(0, Math.min(100, v)); }
    public String getSeverity() { return severity; }
    public void setSeverity(String v) { severity = safe(v); }
    public String getTitle() { return title; }
    public void setTitle(String v) { title = safe(v); }
    public String getMessage() { return message; }
    public void setMessage(String v) { message = safe(v); }
    public String getAction() { return action; }
    public void setAction(String v) { action = safe(v); }
    public List<String> getReasons() { return reasons; }
    public void setReasons(List<String> v) { reasons = v == null ? new ArrayList<>() : v; }
    public boolean isAdvisory_only() { return advisory_only; }
    public void setAdvisory_only(boolean v) { advisory_only = v; }
    public long getUpdated_at_epoch() { return updated_at_epoch; }
    public void setUpdated_at_epoch(long v) { updated_at_epoch = Math.max(0, v); }
    private static String safe(String v) { return v == null ? "" : v; }
}
