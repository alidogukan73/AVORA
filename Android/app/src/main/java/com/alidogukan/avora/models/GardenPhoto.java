package com.alidogukan.avora.models;

public class GardenPhoto {
    private String id;
    private String zone_id;
    private String season_id;
    private String image_url;
    private String local_path;
    private String note;
    private String related_application_id;
    private String analysis_title;
    private String analysis_meta;
    private String analysis_context;
    private String analysis_advice;
    private String analysis_goal;
    private int analysis_confidence;
    private int growth_score = -1;
    private String growth_stage;
    private String growth_trend;
    private int growth_score_delta;
    private String growth_signals;
    private long growth_previous_captured_at_epoch;
    private long captured_at_epoch;
    private int rotation_degrees;
    private boolean flipped_horizontally;
    private boolean flipped_vertically;

    public GardenPhoto() { }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getZone_id() { return zone_id; }
    public void setZone_id(String zoneId) { zone_id = zoneId; }
    public String getSeason_id() { return season_id; }
    public void setSeason_id(String value) { season_id = value; }
    public String getImage_url() { return image_url; }
    public void setImage_url(String imageUrl) { image_url = imageUrl; }
    public String getLocal_path() { return local_path; }
    public void setLocal_path(String value) { local_path = value; }
    public String getNote() { return note; }
    public void setNote(String value) { note = value; }
    public String getRelated_application_id() {
        return related_application_id;
    }
    public void setRelated_application_id(String value) {
        related_application_id = value;
    }
    public String getAnalysis_title() { return analysis_title; }
    public void setAnalysis_title(String value) { analysis_title = value; }
    public String getAnalysis_meta() { return analysis_meta; }
    public void setAnalysis_meta(String value) { analysis_meta = value; }
    public String getAnalysis_context() { return analysis_context; }
    public void setAnalysis_context(String value) { analysis_context = value; }
    public String getAnalysis_advice() { return analysis_advice; }
    public void setAnalysis_advice(String value) { analysis_advice = value; }
    public String getAnalysis_goal() { return analysis_goal; }
    public void setAnalysis_goal(String value) { analysis_goal = value; }
    public int getAnalysis_confidence() { return analysis_confidence; }
    public void setAnalysis_confidence(int value) { analysis_confidence = value; }
    public int getGrowth_score() { return growth_score; }
    public void setGrowth_score(int value) { growth_score = value; }
    public String getGrowth_stage() { return growth_stage; }
    public void setGrowth_stage(String value) { growth_stage = value; }
    public String getGrowth_trend() { return growth_trend; }
    public void setGrowth_trend(String value) { growth_trend = value; }
    public int getGrowth_score_delta() { return growth_score_delta; }
    public void setGrowth_score_delta(int value) { growth_score_delta = value; }
    public String getGrowth_signals() { return growth_signals; }
    public void setGrowth_signals(String value) { growth_signals = value; }
    public long getGrowth_previous_captured_at_epoch() {
        return growth_previous_captured_at_epoch;
    }
    public void setGrowth_previous_captured_at_epoch(long value) {
        growth_previous_captured_at_epoch = value;
    }
    public long getCaptured_at_epoch() { return captured_at_epoch; }
    public void setCaptured_at_epoch(long value) { captured_at_epoch = value; }
    public int getRotation_degrees() { return rotation_degrees; }
    public void setRotation_degrees(int value) { rotation_degrees = value; }
    public boolean getFlipped_horizontally() { return flipped_horizontally; }
    public void setFlipped_horizontally(boolean value) { flipped_horizontally = value; }
    public boolean getFlipped_vertically() { return flipped_vertically; }
    public void setFlipped_vertically(boolean value) { flipped_vertically = value; }
}
