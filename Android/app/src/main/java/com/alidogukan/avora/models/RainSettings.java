package com.alidogukan.avora.models;

/** User-controlled, safety-bounded rain influence for automatic irrigation. */
public final class RainSettings {
    public static final boolean DEFAULT_RAIN_DELAY_ENABLED = true;
    public static final double DEFAULT_RAIN_PROBABILITY = 80d;
    public static final double DEFAULT_RAIN_MM = 2d;

    private final boolean rainDelayEnabled;
    private final double rainProbability;
    private final double rainMm;
    private final long updatedAtEpoch;

    public RainSettings(boolean rainDelayEnabled, double rainProbability,
                        double rainMm, long updatedAtEpoch) {
        this.rainDelayEnabled = rainDelayEnabled;
        this.rainProbability = clamp(rainProbability, 50d, 100d,
                DEFAULT_RAIN_PROBABILITY);
        this.rainMm = clamp(rainMm, 0.5d, 10d, DEFAULT_RAIN_MM);
        this.updatedAtEpoch = Math.max(0L, updatedAtEpoch);
    }

    public static RainSettings defaults() {
        return new RainSettings(DEFAULT_RAIN_DELAY_ENABLED,
                DEFAULT_RAIN_PROBABILITY, DEFAULT_RAIN_MM, 0L);
    }

    public boolean isRainDelayEnabled() { return rainDelayEnabled; }
    public double getRainProbability() { return rainProbability; }
    public double getRainMm() { return rainMm; }
    public long getUpdatedAtEpoch() { return updatedAtEpoch; }

    private static double clamp(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) return fallback;
        return Math.max(min, Math.min(max, value));
    }
}
