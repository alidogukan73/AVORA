package com.alidogukan.avora.zones;

/** Unsaved hardware choices belong to the editor, not the live telemetry snapshot. */
public final class ZoneHardwareSelection {
    private String zoneId = "";
    private String sensorId = "";
    private String valveId = "";
    private boolean initialized;

    /** A different physical channel gets its own defaults; repeated snapshots keep the draft. */
    public void bindZone(String zoneId, String initialSensorId, String initialValveId) {
        String nextZoneId = safe(zoneId);
        if (initialized && this.zoneId.equals(nextZoneId)) return;
        this.zoneId = nextZoneId;
        sensorId = safe(initialSensorId);
        valveId = safe(initialValveId);
        initialized = true;
    }

    public void selectSensor(String sensorId) {
        this.sensorId = safe(sensorId);
    }

    public void selectValve(String valveId) {
        this.valveId = safe(valveId);
    }

    public String getSensorId() { return sensorId; }
    public String getValveId() { return valveId; }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
