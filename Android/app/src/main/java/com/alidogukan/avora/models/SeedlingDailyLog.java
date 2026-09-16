package com.alidogukan.avora.models;
import com.google.firebase.database.Exclude;
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
    private boolean sensor_snapshot_available;
    private boolean sensor_snapshot_fresh;
    private String sensor_node_id = "";
    private double sensor_air_temperature_c;
    private double sensor_air_humidity_pct;
    private double sensor_root_temperature_c;
    private boolean sensor_soil_moisture_available;
    private double sensor_soil_moisture_pct;
    private int sensor_soil_raw;
    private double sensor_light_lux;
    private long sensor_received_at_epoch;
    private long sensor_captured_at_epoch;
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
    public boolean isSensor_snapshot_available() { return sensor_snapshot_available; }
    public void setSensor_snapshot_available(boolean v) { sensor_snapshot_available = v; }
    public boolean isSensor_snapshot_fresh() { return sensor_snapshot_fresh; }
    public void setSensor_snapshot_fresh(boolean v) { sensor_snapshot_fresh = v; }
    public String getSensor_node_id() { return sensor_node_id; }
    public void setSensor_node_id(String v) { sensor_node_id = safe(v); }
    public double getSensor_air_temperature_c() { return sensor_air_temperature_c; }
    public void setSensor_air_temperature_c(double v) { sensor_air_temperature_c = v; }
    public double getSensor_air_humidity_pct() { return sensor_air_humidity_pct; }
    public void setSensor_air_humidity_pct(double v) { sensor_air_humidity_pct = v; }
    public double getSensor_root_temperature_c() { return sensor_root_temperature_c; }
    public void setSensor_root_temperature_c(double v) { sensor_root_temperature_c = v; }
    public boolean isSensor_soil_moisture_available() {
        return sensor_soil_moisture_available;
    }
    public void setSensor_soil_moisture_available(boolean v) {
        sensor_soil_moisture_available = v;
    }
    public double getSensor_soil_moisture_pct() { return sensor_soil_moisture_pct; }
    public void setSensor_soil_moisture_pct(double v) { sensor_soil_moisture_pct = v; }
    public int getSensor_soil_raw() { return sensor_soil_raw; }
    public void setSensor_soil_raw(int v) { sensor_soil_raw = v; }
    public double getSensor_light_lux() { return sensor_light_lux; }
    public void setSensor_light_lux(double v) { sensor_light_lux = v; }
    public long getSensor_received_at_epoch() { return sensor_received_at_epoch; }
    public void setSensor_received_at_epoch(long v) {
        sensor_received_at_epoch = Math.max(0L, v);
    }
    public long getSensor_captured_at_epoch() { return sensor_captured_at_epoch; }
    public void setSensor_captured_at_epoch(long v) {
        sensor_captured_at_epoch = Math.max(0L, v);
    }

    public void captureSensorSnapshot(SeedlingTelemetry telemetry, long capturedAtEpoch,
                                      long freshnessSeconds) {
        sensor_captured_at_epoch = Math.max(0L, capturedAtEpoch);
        if (telemetry == null) {
            sensor_snapshot_available = false;
            sensor_snapshot_fresh = false;
            return;
        }
        sensor_snapshot_available = true;
        sensor_snapshot_fresh = telemetry.isFresh(
                sensor_captured_at_epoch, freshnessSeconds);
        sensor_node_id = safe(telemetry.getNode_id());
        sensor_air_temperature_c = telemetry.getAir_temperature_c();
        sensor_air_humidity_pct = telemetry.getAir_humidity_pct();
        sensor_root_temperature_c = telemetry.getRoot_temperature_c();
        sensor_soil_moisture_available = telemetry.isSoil_moisture_available();
        sensor_soil_moisture_pct = telemetry.getSoil_moisture_pct();
        sensor_soil_raw = telemetry.getSoil_raw();
        sensor_light_lux = telemetry.getLight_lux();
        sensor_received_at_epoch = Math.max(0L, telemetry.getReceived_at_epoch());
    }

    public void copySensorSnapshotFrom(SeedlingDailyLog source) {
        if (source == null) return;
        sensor_snapshot_available = source.sensor_snapshot_available;
        sensor_snapshot_fresh = source.sensor_snapshot_fresh;
        sensor_node_id = source.sensor_node_id;
        sensor_air_temperature_c = source.sensor_air_temperature_c;
        sensor_air_humidity_pct = source.sensor_air_humidity_pct;
        sensor_root_temperature_c = source.sensor_root_temperature_c;
        sensor_soil_moisture_available = source.sensor_soil_moisture_available;
        sensor_soil_moisture_pct = source.sensor_soil_moisture_pct;
        sensor_soil_raw = source.sensor_soil_raw;
        sensor_light_lux = source.sensor_light_lux;
        sensor_received_at_epoch = source.sensor_received_at_epoch;
        sensor_captured_at_epoch = source.sensor_captured_at_epoch;
    }

    @Exclude public SeedlingTelemetry storedSensorSnapshot() {
        if (!sensor_snapshot_available) return null;
        SeedlingTelemetry telemetry = new SeedlingTelemetry();
        telemetry.setNode_id(sensor_node_id);
        telemetry.setAir_temperature_c(sensor_air_temperature_c);
        telemetry.setAir_humidity_pct(sensor_air_humidity_pct);
        telemetry.setRoot_temperature_c(sensor_root_temperature_c);
        telemetry.setSoil_moisture_available(sensor_soil_moisture_available);
        telemetry.setSoil_moisture_pct(sensor_soil_moisture_pct);
        telemetry.setSoil_raw(sensor_soil_raw);
        telemetry.setLight_lux(sensor_light_lux);
        telemetry.setReceived_at_epoch(sensor_received_at_epoch);
        telemetry.setOnline(sensor_snapshot_fresh);
        return telemetry;
    }
    private static String safe(String v) { return v == null ? "" : v.trim(); }
}
