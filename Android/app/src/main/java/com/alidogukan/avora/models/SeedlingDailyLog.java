package com.alidogukan.avora.models;
import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public final class SeedlingDailyLog {
    private String log_id = "";
    private String batch_id = "";
    private double height_cm;
    private int leaf_count;
    private int healthy_count;
    private boolean watered;
    private String note = "";
    private String photo_id = "";
    private String photo_storage_path = "";
    private long created_at_epoch;
    public SeedlingDailyLog() { }
    public String getLog_id() { return log_id; }
    public void setLog_id(String v) { log_id = safe(v); }
    public String getBatch_id() { return batch_id; }
    public void setBatch_id(String v) { batch_id = safe(v); }
    public double getHeight_cm() { return height_cm; }
    public void setHeight_cm(double v) { height_cm = Math.max(0, v); }
    public int getLeaf_count() { return leaf_count; }
    public void setLeaf_count(int v) { leaf_count = Math.max(0, v); }
    public int getHealthy_count() { return healthy_count; }
    public void setHealthy_count(int v) { healthy_count = Math.max(0, v); }
    public boolean isWatered() { return watered; }
    public void setWatered(boolean v) { watered = v; }
    public String getNote() { return note; }
    public void setNote(String v) { note = safe(v); }
    public String getPhoto_id() { return photo_id; }
    public void setPhoto_id(String v) { photo_id = safe(v); }
    public String getPhoto_storage_path() { return photo_storage_path; }
    public void setPhoto_storage_path(String v) { photo_storage_path = safe(v); }
    public boolean hasPhoto() {
        return !photo_id.isBlank();
    }
    public long getCreated_at_epoch() { return created_at_epoch; }
    public void setCreated_at_epoch(long v) { created_at_epoch = Math.max(0, v); }
    private static String safe(String v) { return v == null ? "" : v.trim(); }
}
