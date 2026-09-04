package com.alidogukan.avora.health;

/** A score deduction with an explicit, non-mutating navigation destination. */
public final class GardenHealthIssue {
    public enum Target { SENSOR_SETTINGS, IRRIGATION_SETTINGS, FERTILIZATION, PLANT_ASSISTANT }

    private final String reason;
    private final int deduction;
    private final Target target;
    private final String seasonId;

    public GardenHealthIssue(String reason, int deduction, Target target) {
        this(reason, deduction, target, "");
    }

    public GardenHealthIssue(String reason, int deduction, Target target, String seasonId) {
        this.reason = reason;
        this.deduction = Math.max(0, deduction);
        this.target = target;
        this.seasonId = seasonId == null ? "" : seasonId;
    }

    public String getReason() { return reason; }
    public int getDeduction() { return deduction; }
    public Target getTarget() { return target; }
    public String getSeasonId() { return seasonId; }
}
