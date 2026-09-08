package com.alidogukan.avora.models;
import com.google.firebase.database.IgnoreExtraProperties;

/** One traceable tray/lot from sowing until transfer to a garden season. */
@IgnoreExtraProperties
public final class SeedlingBatch {
    private String batch_id = "";
    private String plant_type = "";
    private String emoji = "🌱";
    private String variety = "";
    private String area = "";
    private String node_id = "seedling-001";
    private String status = "ACTIVE";
    private String stage = "SOWN";
    private long sowing_date_epoch;
    private long estimated_emergence_epoch;
    private long estimated_transplant_epoch;
    private Long germination_date_epoch;
    private Long first_leaf_date_epoch;
    private Long hardening_date_epoch;
    private Long ready_date_epoch;
    private int seed_count;
    private int tray_cell_count;
    private int healthy_count;
    private long created_at_epoch;
    private long updated_at_epoch;

    public SeedlingBatch() { }
    public String getBatch_id() { return batch_id; }
    public void setBatch_id(String v) { batch_id = safe(v); }
    public String getPlant_type() { return plant_type; }
    public void setPlant_type(String v) { plant_type = safe(v); }
    public String getEmoji() { return emoji; }
    public void setEmoji(String v) { emoji = safe(v); }
    public String getVariety() { return variety; }
    public void setVariety(String v) { variety = safe(v); }
    public String getArea() { return area; }
    public void setArea(String v) { area = safe(v); }
    public String getNode_id() { return node_id; }
    public void setNode_id(String v) { node_id = safe(v); }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = safe(v); }
    public String getStage() { return stage; }
    public void setStage(String v) { stage = safe(v); }
    public long getSowing_date_epoch() { return sowing_date_epoch; }
    public void setSowing_date_epoch(long v) { sowing_date_epoch = positive(v); }
    public long getEstimated_emergence_epoch() { return estimated_emergence_epoch; }
    public void setEstimated_emergence_epoch(long v) { estimated_emergence_epoch = positive(v); }
    public long getEstimated_transplant_epoch() { return estimated_transplant_epoch; }
    public void setEstimated_transplant_epoch(long v) { estimated_transplant_epoch = positive(v); }
    public Long getGermination_date_epoch() { return germination_date_epoch; }
    public void setGermination_date_epoch(Long v) { germination_date_epoch = nullablePositive(v); }
    public Long getFirst_leaf_date_epoch() { return first_leaf_date_epoch; }
    public void setFirst_leaf_date_epoch(Long v) { first_leaf_date_epoch = nullablePositive(v); }
    public Long getHardening_date_epoch() { return hardening_date_epoch; }
    public void setHardening_date_epoch(Long v) { hardening_date_epoch = nullablePositive(v); }
    public Long getReady_date_epoch() { return ready_date_epoch; }
    public void setReady_date_epoch(Long v) { ready_date_epoch = nullablePositive(v); }
    public int getSeed_count() { return seed_count; }
    public void setSeed_count(int v) { seed_count = positive(v); }
    public int getTray_cell_count() { return tray_cell_count; }
    public void setTray_cell_count(int v) { tray_cell_count = positive(v); }
    public int getHealthy_count() { return healthy_count; }
    public void setHealthy_count(int v) { healthy_count = positive(v); }
    public long getCreated_at_epoch() { return created_at_epoch; }
    public void setCreated_at_epoch(long v) { created_at_epoch = positive(v); }
    public long getUpdated_at_epoch() { return updated_at_epoch; }
    public void setUpdated_at_epoch(long v) { updated_at_epoch = positive(v); }
    public String displayName() {
        String name = plant_type.isBlank() ? "Fide partisi" : plant_type;
        return variety.isBlank() ? name : name + " – " + variety;
    }
    private static String safe(String v) { return v == null ? "" : v.trim(); }
    private static int positive(int v) { return Math.max(0, v); }
    private static long positive(long v) { return Math.max(0L, v); }
    private static Long nullablePositive(Long v) { return v == null || v <= 0L ? null : v; }
}
