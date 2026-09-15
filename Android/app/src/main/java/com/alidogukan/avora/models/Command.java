package com.alidogukan.avora.models;

import com.google.firebase.database.PropertyName;
import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Command {

    private boolean autoMode;
    private boolean relay;
    private boolean enabled;

    private long moistureLimit;
    private long pumpDuration;
    private long restartDelta;
    private long cooldownSeconds;
    private ManualWateringCommand manualWatering;

    /**
     * Firebase Realtime Database için boş constructor zorunludur.
     */
    public Command() {
        // Firebase
    }

    @PropertyName("auto_mode")
    public boolean isAutoMode() {
        return autoMode;
    }

    @PropertyName("auto_mode")
    public void setAutoMode(boolean autoMode) {
        this.autoMode = autoMode;
    }

    @PropertyName("relay")
    public boolean isRelay() {
        return relay;
    }

    @PropertyName("relay")
    public void setRelay(boolean relay) {
        this.relay = relay;
    }

    @PropertyName("enabled")
    public boolean isEnabled() {
        return enabled;
    }

    @PropertyName("enabled")
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @PropertyName("moisture_limit")
    public long getMoistureLimit() {
        return moistureLimit;
    }

    @PropertyName("moisture_limit")
    public void setMoistureLimit(long moistureLimit) {
        this.moistureLimit = moistureLimit;
    }

    @PropertyName("pump_duration")
    public long getPumpDuration() {
        return pumpDuration;
    }

    @PropertyName("pump_duration")
    public void setPumpDuration(long pumpDuration) {
        this.pumpDuration = pumpDuration;
    }

    @PropertyName("restart_delta")
    public long getRestartDelta() {
        return restartDelta;
    }

    @PropertyName("restart_delta")
    public void setRestartDelta(long restartDelta) {
        this.restartDelta = restartDelta;
    }

    @PropertyName("cooldown_seconds")
    public long getCooldownSeconds() {
        return cooldownSeconds;
    }

    @PropertyName("cooldown_seconds")
    public void setCooldownSeconds(long cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    @PropertyName("manual_watering")
    public ManualWateringCommand getManualWatering() {
        return manualWatering;
    }

    @PropertyName("manual_watering")
    public void setManualWatering(ManualWateringCommand manualWatering) {
        this.manualWatering = manualWatering;
    }
}
