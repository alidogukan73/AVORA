package com.alidogukan.avora.models;
import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public final class SeedlingTelemetry {
    public static final long MAX_FUTURE_SKEW_SECONDS = 120L;
    private String node_id = "";
    private String firmware = "";
    private double air_temperature_c;
    private double air_humidity_pct;
    private double root_temperature_c;
    private boolean soil_moisture_available = true;
    private double soil_moisture_pct;
    private int soil_raw;
    private double light_lux;
    private int rssi;
    private long uptime_seconds;
    private long received_at_epoch;
    private boolean online;
    public SeedlingTelemetry() { }
    public String getNode_id() { return node_id; }
    public void setNode_id(String v) { node_id = safe(v); }
    public String getFirmware() { return firmware; }
    public void setFirmware(String v) { firmware = safe(v); }
    public double getAir_temperature_c() { return air_temperature_c; }
    public void setAir_temperature_c(double v) { air_temperature_c = v; }
    public double getAir_humidity_pct() { return air_humidity_pct; }
    public void setAir_humidity_pct(double v) { air_humidity_pct = v; }
    public double getRoot_temperature_c() { return root_temperature_c; }
    public void setRoot_temperature_c(double v) { root_temperature_c = v; }
    public boolean isSoil_moisture_available() { return soil_moisture_available; }
    public void setSoil_moisture_available(boolean v) { soil_moisture_available = v; }
    public double getSoil_moisture_pct() { return soil_moisture_pct; }
    public void setSoil_moisture_pct(double v) { soil_moisture_pct = v; }
    public int getSoil_raw() { return soil_raw; }
    public void setSoil_raw(int v) { soil_raw = v; }
    public double getLight_lux() { return light_lux; }
    public void setLight_lux(double v) { light_lux = v; }
    public int getRssi() { return rssi; }
    public void setRssi(int v) { rssi = v; }
    public long getUptime_seconds() { return uptime_seconds; }
    public void setUptime_seconds(long v) { uptime_seconds = v; }
    public long getReceived_at_epoch() { return received_at_epoch; }
    public void setReceived_at_epoch(long v) { received_at_epoch = v; }
    public boolean isOnline() { return online; }
    public void setOnline(boolean v) { online = v; }
    public boolean isFresh(long nowEpoch, long maximumAgeSeconds) {
        if (!online || received_at_epoch <= 0) return false;
        // The Pi and the phone can differ slightly, but a timestamp far in the
        // future must not keep a disconnected node looking fresh indefinitely.
        if (received_at_epoch > nowEpoch) {
            long latestAllowed = nowEpoch > Long.MAX_VALUE - MAX_FUTURE_SKEW_SECONDS
                    ? Long.MAX_VALUE
                    : nowEpoch + MAX_FUTURE_SKEW_SECONDS;
            return received_at_epoch <= latestAllowed;
        }
        return nowEpoch - received_at_epoch <= Math.max(0L, maximumAgeSeconds);
    }
    private static String safe(String v) { return v == null ? "" : v; }
}
