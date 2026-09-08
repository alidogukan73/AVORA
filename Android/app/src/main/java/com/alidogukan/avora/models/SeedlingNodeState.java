package com.alidogukan.avora.models;
import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public final class SeedlingNodeState {
    private SeedlingTelemetry latest;
    private SeedlingRecommendation recommendation;
    public SeedlingNodeState() { }
    public SeedlingTelemetry getLatest() { return latest; }
    public void setLatest(SeedlingTelemetry v) { latest = v; }
    public SeedlingRecommendation getRecommendation() { return recommendation; }
    public void setRecommendation(SeedlingRecommendation v) { recommendation = v; }
}
