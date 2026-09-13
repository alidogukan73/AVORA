package com.alidogukan.avora.models;

import com.google.firebase.database.IgnoreExtraProperties;

/** Immutable season identity plus the archive summary for one zone. */
@IgnoreExtraProperties
public class GardenSeason {
    private String season_id = "";
    private String zone_id = "";
    private String area_id = "";
    private String area_name = "";
    private String zone_name = "";
    private String plant_type = "";
    private String emoji = "";
    private int ideal_moisture_min;
    private int ideal_moisture_max = 100;
    private String sensor_id = "";
    private boolean sensor_enabled;
    private String valve_id = "";
    private String valve_mode = "";
    private String label = "";
    private String status = SeasonStatus.ACTIVE;
    private String planting_date = "";
    private String source_seedling_batch_id = "";
    private String source_seedling_variety = "";
    private int source_seedling_healthy_count;
    private long started_at_epoch;
    private long ended_at_epoch;
    private boolean includes_legacy_records;
    private int watering_count;
    private long watering_seconds;
    private int fertilizer_application_count;
    private int journal_event_count;
    private int manual_journal_event_count;
    private int photo_count;
    private int plant_assistant_analysis_count;
    private int notification_count;
    private int final_moisture;
    private long final_sensor_updated_at_epoch;
    private String result = "";
    private String harvest_amount = "";
    private String yield_note = "";
    private String issues_note = "";
    private String successful_practices = "";
    private String next_season_note = "";
    private long created_at_epoch;
    private long updated_at_epoch;

    public GardenSeason() { }

    public String getSeason_id() { return season_id; }
    public void setSeason_id(String value) { season_id = safe(value); }
    public String getZone_id() { return zone_id; }
    public void setZone_id(String value) { zone_id = safe(value); }
    public String getArea_id() { return area_id; }
    public void setArea_id(String value) { area_id = safe(value); }
    public String getArea_name() { return area_name; }
    public void setArea_name(String value) { area_name = safe(value); }
    public String getZone_name() { return zone_name; }
    public void setZone_name(String value) { zone_name = safe(value); }
    public String getPlant_type() { return plant_type; }
    public void setPlant_type(String value) { plant_type = safe(value); }
    public String getEmoji() { return emoji; }
    public void setEmoji(String value) { emoji = safe(value); }
    public int getIdeal_moisture_min() { return ideal_moisture_min; }
    public void setIdeal_moisture_min(int value) { ideal_moisture_min = clamp(value); }
    public int getIdeal_moisture_max() { return ideal_moisture_max; }
    public void setIdeal_moisture_max(int value) { ideal_moisture_max = clamp(value); }
    public String getSensor_id() { return sensor_id; }
    public void setSensor_id(String value) { sensor_id = safe(value); }
    public boolean isSensor_enabled() { return sensor_enabled; }
    public void setSensor_enabled(boolean value) { sensor_enabled = value; }
    public String getValve_id() { return valve_id; }
    public void setValve_id(String value) { valve_id = safe(value); }
    public String getValve_mode() { return valve_mode; }
    public void setValve_mode(String value) { valve_mode = safe(value); }
    public String getLabel() { return label; }
    public void setLabel(String value) { label = safe(value); }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = safe(value); }
    public String getPlanting_date() { return planting_date; }
    public void setPlanting_date(String value) { planting_date = safe(value); }
    public String getSource_seedling_batch_id() { return source_seedling_batch_id; }
    public void setSource_seedling_batch_id(String value) {
        source_seedling_batch_id = safe(value);
    }
    public String getSource_seedling_variety() { return source_seedling_variety; }
    public void setSource_seedling_variety(String value) {
        source_seedling_variety = safe(value);
    }
    public int getSource_seedling_healthy_count() {
        return source_seedling_healthy_count;
    }
    public void setSource_seedling_healthy_count(int value) {
        source_seedling_healthy_count = Math.max(0, value);
    }
    public long getStarted_at_epoch() { return started_at_epoch; }
    public void setStarted_at_epoch(long value) { started_at_epoch = Math.max(0L, value); }
    public long getEnded_at_epoch() { return ended_at_epoch; }
    public void setEnded_at_epoch(long value) { ended_at_epoch = Math.max(0L, value); }
    public boolean isIncludes_legacy_records() { return includes_legacy_records; }
    public void setIncludes_legacy_records(boolean value) { includes_legacy_records = value; }
    public int getWatering_count() { return watering_count; }
    public void setWatering_count(int value) { watering_count = Math.max(0, value); }
    public long getWatering_seconds() { return watering_seconds; }
    public void setWatering_seconds(long value) { watering_seconds = Math.max(0L, value); }
    public int getFertilizer_application_count() { return fertilizer_application_count; }
    public void setFertilizer_application_count(int value) { fertilizer_application_count = Math.max(0, value); }
    public int getJournal_event_count() { return journal_event_count; }
    public void setJournal_event_count(int value) { journal_event_count = Math.max(0, value); }
    public int getManual_journal_event_count() { return manual_journal_event_count; }
    public void setManual_journal_event_count(int value) { manual_journal_event_count = Math.max(0, value); }
    public int getPhoto_count() { return photo_count; }
    public void setPhoto_count(int value) { photo_count = Math.max(0, value); }
    public int getPlant_assistant_analysis_count() { return plant_assistant_analysis_count; }
    public void setPlant_assistant_analysis_count(int value) { plant_assistant_analysis_count = Math.max(0, value); }
    public int getNotification_count() { return notification_count; }
    public void setNotification_count(int value) { notification_count = Math.max(0, value); }
    public int getFinal_moisture() { return final_moisture; }
    public void setFinal_moisture(int value) { final_moisture = Math.max(0, Math.min(100, value)); }
    public long getFinal_sensor_updated_at_epoch() { return final_sensor_updated_at_epoch; }
    public void setFinal_sensor_updated_at_epoch(long value) { final_sensor_updated_at_epoch = Math.max(0L, value); }
    public String getResult() { return result; }
    public void setResult(String value) { result = safe(value); }
    public String getHarvest_amount() { return harvest_amount; }
    public void setHarvest_amount(String value) { harvest_amount = safe(value); }
    public String getYield_note() { return yield_note; }
    public void setYield_note(String value) { yield_note = safe(value); }
    public String getIssues_note() { return issues_note; }
    public void setIssues_note(String value) { issues_note = safe(value); }
    public String getSuccessful_practices() { return successful_practices; }
    public void setSuccessful_practices(String value) { successful_practices = safe(value); }
    public String getNext_season_note() { return next_season_note; }
    public void setNext_season_note(String value) { next_season_note = safe(value); }
    public long getCreated_at_epoch() { return created_at_epoch; }
    public void setCreated_at_epoch(long value) { created_at_epoch = Math.max(0L, value); }
    public long getUpdated_at_epoch() { return updated_at_epoch; }
    public void setUpdated_at_epoch(long value) { updated_at_epoch = Math.max(0L, value); }

    private static String safe(String value) { return value == null ? "" : value; }
    private static int clamp(int value) { return Math.max(0, Math.min(100, value)); }
}
