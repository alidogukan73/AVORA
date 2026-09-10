package com.alidogukan.avora.seedling;

import androidx.annotation.Nullable;

import com.alidogukan.avora.models.SeedlingTelemetry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Crop and nursery-stage aware target bands for the five NodeMCU readings.
 *
 * <p>Temperature targets are conservative bands derived from university
 * Extension germination and transplant-production guidance. Light guidance
 * is intentionally expressed as an approximate BH1750 lux reference: plant
 * science normally uses PAR/DLI, which a lux sensor cannot measure. Substrate
 * moisture is an AVORA dry/wet calibration percentage, not volumetric water
 * content; its bands implement the Extension guidance "moist, not wet".</p>
 *
 * <p>Primary references:
 * https://yardandgarden.extension.iastate.edu/how-to/germination-requirements-annuals-and-vegetables
 * https://extension.psu.edu/damping-off
 * https://extension.usu.edu/vegetableguide/production/transplant-production
 * https://extension.usu.edu/vegetableguide/tomato-pepper-eggplant/transplant-production</p>
 */
public final class SeedlingEnvironmentGuide {
    public enum Metric {
        AIR_TEMPERATURE,
        AIR_HUMIDITY,
        SOIL_MOISTURE,
        ROOT_TEMPERATURE,
        LIGHT
    }

    public enum Status {
        NORMAL,
        LOW,
        HIGH,
        UNAVAILABLE,
        NIGHT,
        BEFORE_EMERGENCE
    }

    public enum Severity { GOOD, WARNING, CRITICAL }

    private static final Map<String, CropProfile> CROP_PROFILES = cropProfiles();
    private static final CropProfile DEFAULT = profile(
            18, 24, 17, 24, 13, 21, 50, 70, 8_000, 22_000);

    private SeedlingEnvironmentGuide() { }

    public static Assessment assess(String plantType, String stage,
                                    @Nullable SeedlingTelemetry telemetry,
                                    int localHour) {
        CropProfile crop = profileFor(plantType);
        Phase phase = phaseFor(stage);
        EnumMap<Metric, MetricAssessment> metrics = new EnumMap<>(Metric.class);
        metrics.put(Metric.AIR_TEMPERATURE, evaluate(
                Metric.AIR_TEMPERATURE,
                telemetry == null ? null : telemetry.getAir_temperature_c(),
                crop.airFor(phase), 8d));
        metrics.put(Metric.AIR_HUMIDITY, evaluate(
                Metric.AIR_HUMIDITY,
                telemetry == null ? null : telemetry.getAir_humidity_pct(),
                humidityFor(phase), 15d));

        Double soilValue = telemetry == null || !telemetry.isSoil_moisture_available()
                ? null : telemetry.getSoil_moisture_pct();
        metrics.put(Metric.SOIL_MOISTURE, evaluate(
                Metric.SOIL_MOISTURE, soilValue,
                crop.soilMoistureFor(phase), 15d));
        metrics.put(Metric.ROOT_TEMPERATURE, evaluate(
                Metric.ROOT_TEMPERATURE,
                telemetry == null ? null : telemetry.getRoot_temperature_c(),
                crop.rootFor(phase), 7d));

        Range lightRange = crop.lightFor(phase);
        if (SeedlingStagePolicy.SOWN.equals(SeedlingStagePolicy.normalize(stage))) {
            metrics.put(Metric.LIGHT, MetricAssessment.paused(
                    Metric.LIGHT, lightRange, Status.BEFORE_EMERGENCE));
        } else if (!SeedlingRecommendationTiming.shouldEvaluateLight(localHour)) {
            metrics.put(Metric.LIGHT, MetricAssessment.paused(
                    Metric.LIGHT, lightRange, Status.NIGHT));
        } else {
            metrics.put(Metric.LIGHT, evaluate(
                    Metric.LIGHT,
                    telemetry == null ? null : telemetry.getLight_lux(),
                    lightRange, lightRange.getMaximum()));
        }
        return new Assessment(metrics);
    }

    public static Range targetFor(String plantType, String stage, Metric metric) {
        CropProfile crop = profileFor(plantType);
        Phase phase = phaseFor(stage);
        switch (metric) {
            case AIR_TEMPERATURE: return crop.airFor(phase);
            case AIR_HUMIDITY: return humidityFor(phase);
            case SOIL_MOISTURE: return crop.soilMoistureFor(phase);
            case ROOT_TEMPERATURE: return crop.rootFor(phase);
            case LIGHT:
            default: return crop.lightFor(phase);
        }
    }

    private static MetricAssessment evaluate(Metric metric, @Nullable Double current,
                                             Range target, double criticalDifference) {
        if (current == null || current.isNaN() || current.isInfinite()) {
            return MetricAssessment.unavailable(metric, target);
        }
        if (current < target.minimum) {
            return new MetricAssessment(metric, target, current, Status.LOW,
                    target.minimum - current >= criticalDifference);
        }
        if (current > target.maximum) {
            return new MetricAssessment(metric, target, current, Status.HIGH,
                    current - target.maximum >= criticalDifference);
        }
        return new MetricAssessment(metric, target, current, Status.NORMAL, false);
    }

    private static CropProfile profileFor(String plantType) {
        return CROP_PROFILES.getOrDefault(
                SeedlingCropCatalog.cropKeyForPlant(plantType), DEFAULT);
    }

    private static Phase phaseFor(String stage) {
        String value = SeedlingStagePolicy.normalize(stage);
        if (SeedlingStagePolicy.SOWN.equals(value)
                || SeedlingStagePolicy.GERMINATING.equals(value)) {
            return Phase.GERMINATION;
        }
        if (SeedlingStagePolicy.HARDENING.equals(value)
                || SeedlingStagePolicy.READY.equals(value)) {
            return Phase.HARDENING;
        }
        return Phase.GROWTH;
    }

    private static Range humidityFor(Phase phase) {
        if (phase == Phase.GERMINATION) return range(55, 75);
        if (phase == Phase.HARDENING) return range(45, 65);
        return range(50, 70);
    }

    private static Map<String, CropProfile> cropProfiles() {
        Map<String, CropProfile> values = new java.util.LinkedHashMap<>();
        values.put("tomato", profile(21, 24, 18, 24, 10, 18,
                50, 70, 10_000, 25_000));
        values.put("pepper", profile(21, 24, 18, 24, 16, 21,
                50, 70, 10_000, 25_000));
        values.put("cucumber", profile(24, 27, 21, 25, 16, 22,
                55, 72, 10_000, 25_000));
        values.put("eggplant", profile(21, 24, 21, 27, 16, 22,
                50, 70, 10_000, 25_000));
        values.put("bean", profile(21, 27, 18, 26, 14, 22,
                45, 65, 9_000, 24_000));
        values.put("carrot", profile(10, 27, 13, 22, 9, 18,
                50, 70, 8_000, 22_000));
        values.put("okra", profile(24, 32, 21, 30, 18, 24,
                45, 65, 10_000, 28_000));
        values.put("zucchini", profile(24, 27, 21, 25, 16, 22,
                52, 70, 10_000, 25_000));
        values.put("lettuce", profile(16, 21, 13, 21, 10, 18,
                58, 75, 7_000, 20_000));
        values.put("onion", profile(21, 24, 16, 21, 13, 18,
                55, 72, 8_000, 22_000));
        values.put("potato", profile(16, 21, 15, 22, 10, 18,
                50, 70, 8_000, 22_000));
        values.put("corn", profile(18, 29, 20, 28, 15, 23,
                45, 65, 10_000, 28_000));
        values.put("pea", profile(10, 18, 13, 20, 8, 16,
                48, 68, 8_000, 22_000));
        values.put("strawberry", profile(18, 24, 15, 22, 10, 18,
                58, 75, 8_000, 22_000));
        values.put("watermelon", profile(24, 27, 21, 27, 18, 23,
                55, 72, 10_000, 28_000));
        values.put("melon", profile(24, 27, 21, 27, 18, 23,
                55, 72, 10_000, 28_000));
        return Collections.unmodifiableMap(values);
    }

    private static CropProfile profile(double germinationMin, double germinationMax,
                                       double growthMin, double growthMax,
                                       double hardeningMin, double hardeningMax,
                                       double soilMinimum, double soilMaximum,
                                       double lightMinimum, double lightMaximum) {
        return new CropProfile(
                range(germinationMin, germinationMax),
                range(growthMin, growthMax),
                range(hardeningMin, hardeningMax),
                range(soilMinimum, soilMaximum),
                range(lightMinimum, lightMaximum));
    }

    private static Range range(double minimum, double maximum) {
        return new Range(minimum, maximum);
    }

    private enum Phase { GERMINATION, GROWTH, HARDENING }

    public static final class Range {
        private final double minimum;
        private final double maximum;

        private Range(double minimum, double maximum) {
            this.minimum = minimum;
            this.maximum = maximum;
        }

        public double getMinimum() { return minimum; }
        public double getMaximum() { return maximum; }
    }

    public static final class MetricAssessment {
        private final Metric metric;
        private final Range target;
        private final Double current;
        private final Status status;
        private final boolean critical;

        private MetricAssessment(Metric metric, Range target, @Nullable Double current,
                                 Status status, boolean critical) {
            this.metric = metric;
            this.target = target;
            this.current = current;
            this.status = status;
            this.critical = critical;
        }

        private static MetricAssessment unavailable(Metric metric, Range target) {
            return new MetricAssessment(metric, target, null, Status.UNAVAILABLE, false);
        }

        private static MetricAssessment paused(Metric metric, Range target, Status status) {
            return new MetricAssessment(metric, target, null, status, false);
        }

        public Metric getMetric() { return metric; }
        public Range getTarget() { return target; }
        @Nullable public Double getCurrent() { return current; }
        public Status getStatus() { return status; }
        public boolean isCritical() { return critical; }
        public boolean needsAttention() {
            return status == Status.LOW || status == Status.HIGH
                    || status == Status.UNAVAILABLE;
        }
    }

    public static final class Assessment {
        private final Map<Metric, MetricAssessment> metrics;
        private final List<MetricAssessment> issues;
        private final Severity severity;

        private Assessment(Map<Metric, MetricAssessment> metrics) {
            this.metrics = Collections.unmodifiableMap(new EnumMap<>(metrics));
            List<MetricAssessment> found = new ArrayList<>();
            boolean hasCritical = false;
            for (Metric metric : Metric.values()) {
                MetricAssessment item = metrics.get(metric);
                if (item != null && item.needsAttention()) {
                    found.add(item);
                    hasCritical |= item.isCritical();
                }
            }
            issues = Collections.unmodifiableList(found);
            severity = hasCritical ? Severity.CRITICAL
                    : found.isEmpty() ? Severity.GOOD : Severity.WARNING;
        }

        public MetricAssessment get(Metric metric) { return metrics.get(metric); }
        public List<MetricAssessment> getIssues() { return issues; }
        public Severity getSeverity() { return severity; }
    }

    private static final class CropProfile {
        private final Range germination;
        private final Range growth;
        private final Range hardening;
        private final Range soilMoisture;
        private final Range light;

        private CropProfile(Range germination, Range growth, Range hardening,
                            Range soilMoisture, Range light) {
            this.germination = germination;
            this.growth = growth;
            this.hardening = hardening;
            this.soilMoisture = soilMoisture;
            this.light = light;
        }

        private Range airFor(Phase phase) {
            if (phase == Phase.GERMINATION) return germination;
            return phase == Phase.HARDENING ? hardening : growth;
        }

        private Range rootFor(Phase phase) {
            if (phase == Phase.GERMINATION) return germination;
            if (phase == Phase.HARDENING) {
                return range(Math.max(8, hardening.minimum),
                        Math.min(24, hardening.maximum + 2));
            }
            return range(Math.max(13, growth.minimum),
                    Math.min(27, growth.maximum + 2));
        }

        private Range soilMoistureFor(Phase phase) {
            if (phase == Phase.GERMINATION) {
                return range(Math.min(65, soilMoisture.minimum + 5),
                        Math.min(80, soilMoisture.maximum + 5));
            }
            if (phase == Phase.HARDENING) {
                return range(Math.max(35, soilMoisture.minimum - 5),
                        Math.max(55, soilMoisture.maximum - 5));
            }
            return soilMoisture;
        }

        private Range lightFor(Phase phase) {
            if (phase == Phase.HARDENING) {
                return range(light.minimum, light.maximum + 5_000);
            }
            return light;
        }
    }
}
