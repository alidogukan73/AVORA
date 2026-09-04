package com.alidogukan.avora.models;
import com.google.firebase.database.IgnoreExtraProperties;
@IgnoreExtraProperties
public class ZoneIrrigationStatus {

    private String decision;
    private String decision_reason;
    private boolean cooldown_active;
    private int cooldown_remaining;
    private int queue_position;
    private boolean selected_for_watering;
    private boolean watering_active;
    private Boolean sensor_stable;
    private int moisture_deficit;
    private Boolean hardware_ready;
    private int completed_watering_cycles;
    private boolean waiting_for_moisture_recovery;
    private String weather_adjustment;
    private int configured_duration_seconds;
    private int learned_duration_seconds;
    private int effective_duration_seconds;
    private String duration_source;
    private String duration_adjustment_reason;
    private double adaptive_confidence;
    private int adaptive_watering_count;
    private String adaptive_recommendation_type;
    private boolean adaptive_applied;
    private IrrigationTimingPlan timing_plan;

    public ZoneIrrigationStatus() {
        // Required by Firebase.
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getDecision_reason() {
        return decision_reason;
    }

    public void setDecision_reason(String decisionReason) {
        this.decision_reason = decisionReason;
    }

    public boolean isCooldown_active() {
        return cooldown_active;
    }

    public void setCooldown_active(boolean cooldownActive) {
        this.cooldown_active = cooldownActive;
    }

    public int getCooldown_remaining() {
        return cooldown_remaining;
    }

    public void setCooldown_remaining(int cooldownRemaining) {
        this.cooldown_remaining = cooldownRemaining;
    }

    public int getQueue_position() {
        return queue_position;
    }

    public void setQueue_position(int queuePosition) {
        this.queue_position = queuePosition;
    }

    public boolean isSelected_for_watering() {
        return selected_for_watering;
    }

    public void setSelected_for_watering(
            boolean selectedForWatering
    ) {
        this.selected_for_watering = selectedForWatering;
    }

    public boolean isWatering_active() {
        return watering_active;
    }

    public void setWatering_active(boolean wateringActive) {
        this.watering_active = wateringActive;
    }

    public boolean isSensor_stable() {
        return Boolean.TRUE.equals(sensor_stable);
    }

    public boolean hasSensor_stable() {
        return sensor_stable != null;
    }

    public void setSensor_stable(Boolean sensorStable) {
        this.sensor_stable = sensorStable;
    }

    public int getMoisture_deficit() {
        return moisture_deficit;
    }

    public void setMoisture_deficit(int moistureDeficit) {
        this.moisture_deficit = moistureDeficit;
    }

    public boolean isHardware_ready() {
        return Boolean.TRUE.equals(hardware_ready);
    }

    public boolean hasHardware_ready() {
        return hardware_ready != null;
    }

    public void setHardware_ready(Boolean hardwareReady) {
        this.hardware_ready = hardwareReady;
    }

    public int getCompleted_watering_cycles() {
        return completed_watering_cycles;
    }

    public void setCompleted_watering_cycles(int completedWateringCycles) {
        this.completed_watering_cycles = Math.max(0, completedWateringCycles);
    }

    public boolean isWaiting_for_moisture_recovery() {
        return waiting_for_moisture_recovery;
    }

    public void setWaiting_for_moisture_recovery(boolean waitingForMoistureRecovery) {
        this.waiting_for_moisture_recovery = waitingForMoistureRecovery;
    }

    public String getWeather_adjustment() {
        return weather_adjustment;
    }

    public void setWeather_adjustment(String weatherAdjustment) {
        this.weather_adjustment = weatherAdjustment;
    }
    public int getConfigured_duration_seconds() {
        return configured_duration_seconds;
    }

    public void setConfigured_duration_seconds(int value) {
        configured_duration_seconds = Math.max(0, value);
    }

    public int getLearned_duration_seconds() {
        return learned_duration_seconds;
    }

    public void setLearned_duration_seconds(int value) {
        learned_duration_seconds = Math.max(0, value);
    }

    public int getEffective_duration_seconds() {
        return effective_duration_seconds;
    }

    public void setEffective_duration_seconds(int value) {
        effective_duration_seconds = Math.max(0, value);
    }

    public String getDuration_source() {
        return duration_source;
    }

    public void setDuration_source(String value) {
        duration_source = value;
    }

    public String getDuration_adjustment_reason() {
        return duration_adjustment_reason;
    }

    public void setDuration_adjustment_reason(String value) {
        duration_adjustment_reason = value;
    }

    public double getAdaptive_confidence() {
        return adaptive_confidence;
    }

    public void setAdaptive_confidence(double value) {
        adaptive_confidence = Math.max(0.0, Math.min(1.0, value));
    }

    public int getAdaptive_watering_count() {
        return adaptive_watering_count;
    }

    public void setAdaptive_watering_count(int value) {
        adaptive_watering_count = Math.max(0, value);
    }

    public String getAdaptive_recommendation_type() {
        return adaptive_recommendation_type;
    }

    public void setAdaptive_recommendation_type(String value) {
        adaptive_recommendation_type = value;
    }

    public boolean isAdaptive_applied() {
        return adaptive_applied;
    }

    public void setAdaptive_applied(boolean value) {
        adaptive_applied = value;
    }

    public IrrigationTimingPlan getTiming_plan() {
        return timing_plan;
    }

    public void setTiming_plan(IrrigationTimingPlan value) {
        timing_plan = value;
    }
}
