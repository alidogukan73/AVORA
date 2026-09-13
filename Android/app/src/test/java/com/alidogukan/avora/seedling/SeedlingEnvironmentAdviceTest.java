package com.alidogukan.avora.seedling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.alidogukan.avora.models.SeedlingTelemetry;

import org.junit.Test;

public final class SeedlingEnvironmentAdviceTest {
    @Test public void cropAwareLowTemperatureProducesWarmAirAction() {
        SeedlingTelemetry telemetry = telemetry(20d, 60d, 65d, 25d, 15_000d);
        SeedlingEnvironmentGuide.Assessment assessment = SeedlingEnvironmentGuide.assess(
                "Salatalık", SeedlingStagePolicy.GERMINATING, telemetry, 12);

        SeedlingEnvironmentAdvice.Advice advice = SeedlingEnvironmentAdvice.from(assessment);

        assertEquals(SeedlingEnvironmentAdvice.Action.WARM_AIR, advice.getAction());
        assertEquals(SeedlingEnvironmentGuide.Metric.AIR_TEMPERATURE,
                advice.getIssue().getMetric());
    }

    @Test public void criticalIssueWinsOverOrdinaryWarning() {
        SeedlingTelemetry telemetry = telemetry(23d, 40d, 5d, 23d, 15_000d);
        SeedlingEnvironmentGuide.Assessment assessment = SeedlingEnvironmentGuide.assess(
                "Domates", SeedlingStagePolicy.TRUE_LEAVES, telemetry, 12);

        SeedlingEnvironmentAdvice.Advice advice = SeedlingEnvironmentAdvice.from(assessment);

        assertEquals(SeedlingEnvironmentAdvice.Action.WATER_CAREFULLY, advice.getAction());
        assertTrue(advice.getIssue().isCritical());
    }

    @Test public void normalAssessmentKeepsObservation() {
        SeedlingTelemetry telemetry = telemetry(22d, 60d, 60d, 22d, 15_000d);
        SeedlingEnvironmentGuide.Assessment assessment = SeedlingEnvironmentGuide.assess(
                "Domates", SeedlingStagePolicy.TRUE_LEAVES, telemetry, 12);

        assertEquals(SeedlingEnvironmentAdvice.Action.KEEP_OBSERVING,
                SeedlingEnvironmentAdvice.from(assessment).getAction());
    }

    private static SeedlingTelemetry telemetry(double air, double humidity,
                                               double soil, double root, double light) {
        SeedlingTelemetry value = new SeedlingTelemetry();
        value.setAir_temperature_c(air);
        value.setAir_humidity_pct(humidity);
        value.setSoil_moisture_pct(soil);
        value.setSoil_moisture_available(true);
        value.setRoot_temperature_c(root);
        value.setLight_lux(light);
        return value;
    }
}
