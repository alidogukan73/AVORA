package com.alidogukan.avora.models;

import com.google.firebase.database.PropertyName;

public class Statistics {

    private Long afterMoisture;
    private long averageDuration;
    private Long beforeMoisture;
    private long completedWaterings;
    private long interruptedWaterings;
    private String lastStopReason;
    private long lastWateringDuration;
    private long moistureDelta;
    private String statisticsDate;
    private long successRate;
    private long totalWateringSeconds;
    private long totalWaterings;
    private long wateringSecondsToday;
    private long wateringsToday;

    /**
     * Firebase Realtime Database nesneyi oluşturabilsin diye
     * boş constructor zorunludur.
     */
    public Statistics() {
        lastStopReason = "";
        statisticsDate = "";
    }

    @PropertyName("after_moisture")
    public long getAfterMoisture() {
        return afterMoisture == null ? 0L : afterMoisture;
    }

    @PropertyName("after_moisture")
    public void setAfterMoisture(long afterMoisture) {
        this.afterMoisture = afterMoisture;
    }

    public boolean hasMoistureReadings() {
        return beforeMoisture != null && afterMoisture != null;
    }

    @PropertyName("average_duration")
    public long getAverageDuration() {
        return averageDuration;
    }

    @PropertyName("average_duration")
    public void setAverageDuration(long averageDuration) {
        this.averageDuration = averageDuration;
    }

    @PropertyName("before_moisture")
    public long getBeforeMoisture() {
        return beforeMoisture == null ? 0L : beforeMoisture;
    }

    @PropertyName("before_moisture")
    public void setBeforeMoisture(long beforeMoisture) {
        this.beforeMoisture = beforeMoisture;
    }

    @PropertyName("completed_waterings")
    public long getCompletedWaterings() {
        return completedWaterings;
    }

    @PropertyName("completed_waterings")
    public void setCompletedWaterings(long completedWaterings) {
        this.completedWaterings = completedWaterings;
    }

    @PropertyName("interrupted_waterings")
    public long getInterruptedWaterings() {
        return interruptedWaterings;
    }

    @PropertyName("interrupted_waterings")
    public void setInterruptedWaterings(long interruptedWaterings) {
        this.interruptedWaterings = interruptedWaterings;
    }

    @PropertyName("last_stop_reason")
    public String getLastStopReason() {
        return lastStopReason;
    }

    @PropertyName("last_stop_reason")
    public void setLastStopReason(String lastStopReason) {
        this.lastStopReason = lastStopReason;
    }

    @PropertyName("last_watering_duration")
    public long getLastWateringDuration() {
        return lastWateringDuration;
    }

    @PropertyName("last_watering_duration")
    public void setLastWateringDuration(long lastWateringDuration) {
        this.lastWateringDuration = lastWateringDuration;
    }

    @PropertyName("moisture_delta")
    public long getMoistureDelta() {
        return moistureDelta;
    }

    @PropertyName("moisture_delta")
    public void setMoistureDelta(long moistureDelta) {
        this.moistureDelta = moistureDelta;
    }

    @PropertyName("statistics_date")
    public String getStatisticsDate() {
        return statisticsDate;
    }

    @PropertyName("statistics_date")
    public void setStatisticsDate(String statisticsDate) {
        this.statisticsDate = statisticsDate;
    }

    @PropertyName("success_rate")
    public long getSuccessRate() {
        return successRate;
    }

    @PropertyName("success_rate")
    public void setSuccessRate(long successRate) {
        this.successRate = successRate;
    }

    @PropertyName("total_watering_seconds")
    public long getTotalWateringSeconds() {
        return totalWateringSeconds;
    }

    @PropertyName("total_watering_seconds")
    public void setTotalWateringSeconds(long totalWateringSeconds) {
        this.totalWateringSeconds = totalWateringSeconds;
    }

    @PropertyName("total_waterings")
    public long getTotalWaterings() {
        return totalWaterings;
    }

    @PropertyName("total_waterings")
    public void setTotalWaterings(long totalWaterings) {
        this.totalWaterings = totalWaterings;
    }

    @PropertyName("watering_seconds_today")
    public long getWateringSecondsToday() {
        return wateringSecondsToday;
    }

    @PropertyName("watering_seconds_today")
    public void setWateringSecondsToday(long wateringSecondsToday) {
        this.wateringSecondsToday = wateringSecondsToday;
    }

    @PropertyName("waterings_today")
    public long getWateringsToday() {
        return wateringsToday;
    }

    @PropertyName("waterings_today")
    public void setWateringsToday(long wateringsToday) {
        this.wateringsToday = wateringsToday;
    }
}
