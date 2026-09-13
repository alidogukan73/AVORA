package com.alidogukan.avora.seedling;

import androidx.annotation.Nullable;

/** Selects one deterministic, highest-priority action from a crop-aware assessment. */
public final class SeedlingEnvironmentAdvice {
    public enum Action {
        KEEP_OBSERVING,
        WARM_AIR,
        COOL_AIR,
        RAISE_HUMIDITY,
        LOWER_HUMIDITY,
        WATER_CAREFULLY,
        STOP_WATERING,
        WARM_ROOTS,
        COOL_ROOTS,
        INCREASE_LIGHT,
        REDUCE_LIGHT,
        CHECK_SOIL_SENSOR,
        CHECK_SENSOR
    }

    private SeedlingEnvironmentAdvice() { }

    public static Advice from(SeedlingEnvironmentGuide.Assessment assessment) {
        if (assessment == null || assessment.getIssues().isEmpty()) {
            return new Advice(Action.KEEP_OBSERVING, null);
        }
        SeedlingEnvironmentGuide.MetricAssessment primary = null;
        int bestScore = Integer.MIN_VALUE;
        for (SeedlingEnvironmentGuide.MetricAssessment issue : assessment.getIssues()) {
            int score = priority(issue);
            if (score > bestScore) {
                bestScore = score;
                primary = issue;
            }
        }
        return new Advice(actionFor(primary), primary);
    }

    private static int priority(SeedlingEnvironmentGuide.MetricAssessment issue) {
        int severity = issue.isCritical() ? 1_000 : 0;
        int metric;
        switch (issue.getMetric()) {
            case SOIL_MOISTURE: metric = 50; break;
            case AIR_TEMPERATURE: metric = 40; break;
            case ROOT_TEMPERATURE: metric = 30; break;
            case AIR_HUMIDITY: metric = 20; break;
            case LIGHT:
            default: metric = 10; break;
        }
        return severity + metric;
    }

    private static Action actionFor(@Nullable SeedlingEnvironmentGuide.MetricAssessment issue) {
        if (issue == null) return Action.KEEP_OBSERVING;
        if (issue.getStatus() == SeedlingEnvironmentGuide.Status.UNAVAILABLE) {
            return issue.getMetric() == SeedlingEnvironmentGuide.Metric.SOIL_MOISTURE
                    ? Action.CHECK_SOIL_SENSOR : Action.CHECK_SENSOR;
        }
        boolean low = issue.getStatus() == SeedlingEnvironmentGuide.Status.LOW;
        switch (issue.getMetric()) {
            case AIR_TEMPERATURE: return low ? Action.WARM_AIR : Action.COOL_AIR;
            case AIR_HUMIDITY: return low ? Action.RAISE_HUMIDITY : Action.LOWER_HUMIDITY;
            case SOIL_MOISTURE: return low ? Action.WATER_CAREFULLY : Action.STOP_WATERING;
            case ROOT_TEMPERATURE: return low ? Action.WARM_ROOTS : Action.COOL_ROOTS;
            case LIGHT: return low ? Action.INCREASE_LIGHT : Action.REDUCE_LIGHT;
            default: return Action.KEEP_OBSERVING;
        }
    }

    public static final class Advice {
        private final Action action;
        @Nullable private final SeedlingEnvironmentGuide.MetricAssessment issue;

        private Advice(Action action,
                       @Nullable SeedlingEnvironmentGuide.MetricAssessment issue) {
            this.action = action;
            this.issue = issue;
        }

        public Action getAction() { return action; }
        @Nullable public SeedlingEnvironmentGuide.MetricAssessment getIssue() { return issue; }
        public boolean needsAction() { return action != Action.KEEP_OBSERVING; }
    }
}
