package com.alidogukan.avora.models;

import com.google.firebase.database.PropertyName;

public class Health {

    private double cpuTemperature;
    private double cpuUsage;
    private double diskUsage;
    private String ipAddress;
    private boolean throttled;
    private double memoryUsage;
    private String updatedAt;
    private long uptimeSeconds;
    private long wifiSignal;
    private long throttledRaw;
    private boolean underVoltageNow;
    private boolean frequencyCappedNow;
    private boolean throttledNow;
    private boolean softTemperatureLimitNow;
    private boolean underVoltageHistory;
    private boolean frequencyCappedHistory;
    private boolean throttledHistory;
    private boolean softTemperatureLimitHistory;
    private boolean esp32NodeOnline;
    private boolean ads1115PrimaryAvailable;
    private boolean ads1115SecondaryAvailable;
    private long ads1115StatusUpdatedAtEpoch;
    private long ads1115StatusRssi;
    private long ads1115StatusUptimeSeconds;
    private String ads1115StatusFirmware;

    public Health() {
        ipAddress = "";
        updatedAt = "";
        firmware = "";
        ads1115StatusFirmware = "";
    }
    @PropertyName("throttled_raw")
    public long getThrottledRaw() {
        return throttledRaw;
    }

    @PropertyName("throttled_raw")
    public void setThrottledRaw(long throttledRaw) {
        this.throttledRaw = throttledRaw;
    }

    @PropertyName("under_voltage_now")
    public boolean isUnderVoltageNow() {
        return underVoltageNow;
    }

    @PropertyName("under_voltage_now")
    public void setUnderVoltageNow(boolean underVoltageNow) {
        this.underVoltageNow = underVoltageNow;
    }

    @PropertyName("frequency_capped_now")
    public boolean isFrequencyCappedNow() {
        return frequencyCappedNow;
    }

    @PropertyName("frequency_capped_now")
    public void setFrequencyCappedNow(boolean frequencyCappedNow) {
        this.frequencyCappedNow = frequencyCappedNow;
    }

    @PropertyName("throttled_now")
    public boolean isThrottledNow() {
        return throttledNow;
    }

    @PropertyName("throttled_now")
    public void setThrottledNow(boolean throttledNow) {
        this.throttledNow = throttledNow;
    }

    @PropertyName("soft_temperature_limit_now")
    public boolean isSoftTemperatureLimitNow() {
        return softTemperatureLimitNow;
    }

    @PropertyName("soft_temperature_limit_now")
    public void setSoftTemperatureLimitNow(
            boolean softTemperatureLimitNow
    ) {
        this.softTemperatureLimitNow =
                softTemperatureLimitNow;
    }

    @PropertyName("under_voltage_history")
    public boolean isUnderVoltageHistory() {
        return underVoltageHistory;
    }

    @PropertyName("under_voltage_history")
    public void setUnderVoltageHistory(
            boolean underVoltageHistory
    ) {
        this.underVoltageHistory =
                underVoltageHistory;
    }

    @PropertyName("frequency_capped_history")
    public boolean isFrequencyCappedHistory() {
        return frequencyCappedHistory;
    }

    @PropertyName("frequency_capped_history")
    public void setFrequencyCappedHistory(
            boolean frequencyCappedHistory
    ) {
        this.frequencyCappedHistory =
                frequencyCappedHistory;
    }

    @PropertyName("throttled_history")
    public boolean isThrottledHistory() {
        return throttledHistory;
    }

    @PropertyName("throttled_history")
    public void setThrottledHistory(
            boolean throttledHistory
    ) {
        this.throttledHistory =
                throttledHistory;
    }

    @PropertyName("soft_temperature_limit_history")
    public boolean isSoftTemperatureLimitHistory() {
        return softTemperatureLimitHistory;
    }

    @PropertyName("soft_temperature_limit_history")
    public void setSoftTemperatureLimitHistory(
            boolean softTemperatureLimitHistory
    ) {
        this.softTemperatureLimitHistory =
                softTemperatureLimitHistory;
    }

    @PropertyName("cpu_temperature")
    public double getCpuTemperature() {
        return cpuTemperature;
    }

    @PropertyName("cpu_temperature")
    public void setCpuTemperature(double cpuTemperature) {
        this.cpuTemperature = cpuTemperature;
    }

    @PropertyName("cpu_usage")
    public double getCpuUsage() {
        return cpuUsage;
    }

    @PropertyName("cpu_usage")
    public void setCpuUsage(double cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    @PropertyName("disk_usage")
    public double getDiskUsage() {
        return diskUsage;
    }

    @PropertyName("disk_usage")
    public void setDiskUsage(double diskUsage) {
        this.diskUsage = diskUsage;
    }

    @PropertyName("ip_address")
    public String getIpAddress() {
        return ipAddress;
    }

    @PropertyName("ip_address")
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    @PropertyName("is_throttled")
    public boolean isThrottled() {
        return throttled;
    }

    @PropertyName("is_throttled")
    public void setThrottled(boolean throttled) {
        this.throttled = throttled;
    }

    @PropertyName("memory_usage")
    public double getMemoryUsage() {
        return memoryUsage;
    }

    @PropertyName("memory_usage")
    public void setMemoryUsage(double memoryUsage) {
        this.memoryUsage = memoryUsage;
    }

    @PropertyName("updated_at")
    public String getUpdatedAt() {
        return updatedAt;
    }

    @PropertyName("updated_at")
    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    @PropertyName("uptime_seconds")
    public long getUptimeSeconds() {
        return uptimeSeconds;
    }

    @PropertyName("uptime_seconds")
    public void setUptimeSeconds(long uptimeSeconds) {
        this.uptimeSeconds = uptimeSeconds;
    }

    @PropertyName("wifi_signal")
    public long getWifiSignal() {
        return wifiSignal;
    }

    @PropertyName("wifi_signal")
    public void setWifiSignal(long wifiSignal) {
        this.wifiSignal = wifiSignal;
    }

    @PropertyName("esp32_node_online")
    public boolean isEsp32NodeOnline() {
        return esp32NodeOnline;
    }

    @PropertyName("esp32_node_online")
    public void setEsp32NodeOnline(boolean esp32NodeOnline) {
        this.esp32NodeOnline = esp32NodeOnline;
    }

    @PropertyName("ads1115_primary_available")
    public boolean isAds1115PrimaryAvailable() {
        return ads1115PrimaryAvailable;
    }

    @PropertyName("ads1115_primary_available")
    public void setAds1115PrimaryAvailable(boolean available) {
        ads1115PrimaryAvailable = available;
    }

    @PropertyName("ads1115_secondary_available")
    public boolean isAds1115SecondaryAvailable() {
        return ads1115SecondaryAvailable;
    }

    @PropertyName("ads1115_secondary_available")
    public void setAds1115SecondaryAvailable(boolean available) {
        ads1115SecondaryAvailable = available;
    }

    @PropertyName("ads1115_status_updated_at_epoch")
    public long getAds1115StatusUpdatedAtEpoch() {
        return ads1115StatusUpdatedAtEpoch;
    }

    @PropertyName("ads1115_status_updated_at_epoch")
    public void setAds1115StatusUpdatedAtEpoch(long epoch) {
        ads1115StatusUpdatedAtEpoch = epoch;
    }

    @PropertyName("ads1115_status_rssi")
    public long getAds1115StatusRssi() {
        return ads1115StatusRssi;
    }

    @PropertyName("ads1115_status_rssi")
    public void setAds1115StatusRssi(long rssi) {
        ads1115StatusRssi = rssi;
    }

    @PropertyName("ads1115_status_uptime_seconds")
    public long getAds1115StatusUptimeSeconds() {
        return ads1115StatusUptimeSeconds;
    }

    @PropertyName("ads1115_status_uptime_seconds")
    public void setAds1115StatusUptimeSeconds(long uptimeSeconds) {
        ads1115StatusUptimeSeconds = uptimeSeconds;
    }

    @PropertyName("ads1115_status_firmware")
    public String getAds1115StatusFirmware() {
        return ads1115StatusFirmware;
    }

    @PropertyName("ads1115_status_firmware")
    public void setAds1115StatusFirmware(String firmware) {
        ads1115StatusFirmware = firmware == null ? "" : firmware;
    }

    private String firmware;

    @PropertyName("firmware")
    public String getFirmware() {
        return firmware;
    }

    @PropertyName("firmware")
    public void setFirmware(String firmware) {
        this.firmware = firmware;
    }
}
