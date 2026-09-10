package com.alidogukan.avora.seedling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.alidogukan.avora.models.SeedlingTelemetry;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public final class SeedlingEnvironmentGuideTest {
    @Test public void everyBuiltInCropHasTargetsForEveryNurseryPhaseAndMetric() {
        List<String> crops = Arrays.asList(
                "Domates", "Biber", "Salatalık", "Patlıcan", "Fasulye", "Havuç",
                "Bamya", "Kabak", "Marul", "Soğan", "Patates", "Mısır",
                "Bezelye", "Çilek", "Karpuz", "Kavun");
        List<String> stages = Arrays.asList(
                SeedlingStagePolicy.GERMINATING,
                SeedlingStagePolicy.TRUE_LEAVES,
                SeedlingStagePolicy.HARDENING);

        for (String crop : crops) {
            assertFalse(SeedlingCropCatalog.cropKeyForPlant(crop).isBlank());
            for (String stage : stages) {
                for (SeedlingEnvironmentGuide.Metric metric
                        : SeedlingEnvironmentGuide.Metric.values()) {
                    SeedlingEnvironmentGuide.Range range =
                            SeedlingEnvironmentGuide.targetFor(crop, stage, metric);
                    assertTrue(crop + " " + stage + " " + metric,
                            range.getMinimum() < range.getMaximum());
                }
            }
        }
    }

    @Test public void resolvesTurkishCropNamesToCropSpecificTargets() {
        SeedlingEnvironmentGuide.Range tomato = SeedlingEnvironmentGuide.targetFor(
                "Domates", SeedlingStagePolicy.GERMINATING,
                SeedlingEnvironmentGuide.Metric.ROOT_TEMPERATURE);
        SeedlingEnvironmentGuide.Range lettuce = SeedlingEnvironmentGuide.targetFor(
                "Marul", SeedlingStagePolicy.GERMINATING,
                SeedlingEnvironmentGuide.Metric.ROOT_TEMPERATURE);

        assertEquals(21d, tomato.getMinimum(), 0.01d);
        assertEquals(24d, tomato.getMaximum(), 0.01d);
        assertEquals(16d, lettuce.getMinimum(), 0.01d);
        assertEquals(21d, lettuce.getMaximum(), 0.01d);
    }

    @Test public void stageChangesTomatoTemperatureAndMoistureTargets() {
        SeedlingEnvironmentGuide.Range germination = SeedlingEnvironmentGuide.targetFor(
                "tomato", SeedlingStagePolicy.GERMINATING,
                SeedlingEnvironmentGuide.Metric.AIR_TEMPERATURE);
        SeedlingEnvironmentGuide.Range hardening = SeedlingEnvironmentGuide.targetFor(
                "tomato", SeedlingStagePolicy.HARDENING,
                SeedlingEnvironmentGuide.Metric.AIR_TEMPERATURE);
        SeedlingEnvironmentGuide.Range hardeningMoisture =
                SeedlingEnvironmentGuide.targetFor(
                        "tomato", SeedlingStagePolicy.HARDENING,
                        SeedlingEnvironmentGuide.Metric.SOIL_MOISTURE);

        assertEquals(21d, germination.getMinimum(), 0.01d);
        assertEquals(10d, hardening.getMinimum(), 0.01d);
        assertEquals(45d, hardeningMoisture.getMinimum(), 0.01d);
        assertEquals(65d, hardeningMoisture.getMaximum(), 0.01d);
    }

    @Test public void nightPausesLightWithoutCreatingAnIssue() {
        SeedlingEnvironmentGuide.Assessment result = SeedlingEnvironmentGuide.assess(
                "Domates", SeedlingStagePolicy.TRUE_LEAVES,
                telemetry(22, 60, 60, 22, 50), 23);

        assertEquals(SeedlingEnvironmentGuide.Status.NIGHT,
                result.get(SeedlingEnvironmentGuide.Metric.LIGHT).getStatus());
        assertEquals(SeedlingEnvironmentGuide.Severity.GOOD, result.getSeverity());
        assertTrue(result.getIssues().isEmpty());
    }

    @Test public void daytimeLowLightAppearsAsWarning() {
        SeedlingEnvironmentGuide.Assessment result = SeedlingEnvironmentGuide.assess(
                "Domates", SeedlingStagePolicy.TRUE_LEAVES,
                telemetry(22, 60, 60, 22, 5_000), 12);

        assertEquals(SeedlingEnvironmentGuide.Status.LOW,
                result.get(SeedlingEnvironmentGuide.Metric.LIGHT).getStatus());
        assertEquals(SeedlingEnvironmentGuide.Severity.WARNING, result.getSeverity());
        assertEquals(1, result.getIssues().size());
    }

    @Test public void excessiveRootHeatMakesAssessmentCritical() {
        SeedlingEnvironmentGuide.Assessment result = SeedlingEnvironmentGuide.assess(
                "Domates", SeedlingStagePolicy.GERMINATING,
                telemetry(26, 58, 69, 37, 14_000), 12);

        assertTrue(result.get(SeedlingEnvironmentGuide.Metric.ROOT_TEMPERATURE)
                .isCritical());
        assertEquals(SeedlingEnvironmentGuide.Severity.CRITICAL, result.getSeverity());
    }

    @Test public void failedSoilSensorDoesNotStopOtherReadings() {
        SeedlingTelemetry telemetry = telemetry(22, 60, 60, 22, 14_000);
        telemetry.setSoil_moisture_available(false);
        SeedlingEnvironmentGuide.Assessment result = SeedlingEnvironmentGuide.assess(
                "Domates", SeedlingStagePolicy.TRUE_LEAVES, telemetry, 12);

        assertEquals(SeedlingEnvironmentGuide.Status.UNAVAILABLE,
                result.get(SeedlingEnvironmentGuide.Metric.SOIL_MOISTURE).getStatus());
        assertEquals(SeedlingEnvironmentGuide.Status.NORMAL,
                result.get(SeedlingEnvironmentGuide.Metric.AIR_TEMPERATURE).getStatus());
        assertFalse(result.getIssues().isEmpty());
    }

    private static SeedlingTelemetry telemetry(double temperature, double humidity,
                                                double soilMoisture, double rootTemperature,
                                                double light) {
        SeedlingTelemetry value = new SeedlingTelemetry();
        value.setAir_temperature_c(temperature);
        value.setAir_humidity_pct(humidity);
        value.setSoil_moisture_available(true);
        value.setSoil_moisture_pct(soilMoisture);
        value.setRoot_temperature_c(rootTemperature);
        value.setLight_lux(light);
        return value;
    }
}
