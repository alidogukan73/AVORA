package com.alidogukan.avora.seedling;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.alidogukan.avora.R;

/** Localized presentation for crop-aware seedling actions. */
public final class SeedlingEnvironmentAdviceText {
    private SeedlingEnvironmentAdviceText() { }

    public static String title(@NonNull Context context,
                               SeedlingEnvironmentAdvice.Advice advice) {
        return context.getString(titleRes(advice.getAction()));
    }

    public static String message(@NonNull Context context,
                                 SeedlingEnvironmentAdvice.Advice advice) {
        return context.getString(messageRes(advice.getAction()));
    }

    public static String issueSummary(@NonNull Context context,
                                      SeedlingEnvironmentAdvice.Advice advice) {
        SeedlingEnvironmentGuide.MetricAssessment issue = advice.getIssue();
        if (issue == null) return context.getString(R.string.seedling_overall_good_message);
        String label = context.getString(metricLabel(issue.getMetric()));
        if (issue.getStatus() == SeedlingEnvironmentGuide.Status.UNAVAILABLE) {
            return context.getString(R.string.seedling_metric_issue_unavailable, label);
        }
        int template = issue.getStatus() == SeedlingEnvironmentGuide.Status.LOW
                ? R.string.seedling_metric_issue_low : R.string.seedling_metric_issue_high;
        return context.getString(template, label, current(context, issue), range(context, issue));
    }

    @StringRes private static int titleRes(SeedlingEnvironmentAdvice.Action action) {
        switch (action) {
            case WARM_AIR: return R.string.seedling_action_warm_air;
            case COOL_AIR: return R.string.seedling_action_cool_air;
            case RAISE_HUMIDITY: return R.string.seedling_action_raise_humidity;
            case LOWER_HUMIDITY: return R.string.seedling_action_lower_humidity;
            case WATER_CAREFULLY: return R.string.seedling_action_water_carefully;
            case STOP_WATERING: return R.string.seedling_action_stop_watering;
            case WARM_ROOTS: return R.string.seedling_action_warm_roots;
            case COOL_ROOTS: return R.string.seedling_action_cool_roots;
            case INCREASE_LIGHT: return R.string.seedling_action_increase_light;
            case REDUCE_LIGHT: return R.string.seedling_action_reduce_light;
            case CHECK_SOIL_SENSOR: return R.string.seedling_action_check_soil_sensor;
            case CHECK_SENSOR: return R.string.seedling_action_check_sensor;
            case KEEP_OBSERVING:
            default: return R.string.seedling_action_keep_observing;
        }
    }

    @StringRes private static int messageRes(SeedlingEnvironmentAdvice.Action action) {
        switch (action) {
            case WARM_AIR: return R.string.seedling_action_warm_air_message;
            case COOL_AIR: return R.string.seedling_action_cool_air_message;
            case RAISE_HUMIDITY: return R.string.seedling_action_raise_humidity_message;
            case LOWER_HUMIDITY: return R.string.seedling_action_lower_humidity_message;
            case WATER_CAREFULLY: return R.string.seedling_action_water_carefully_message;
            case STOP_WATERING: return R.string.seedling_action_stop_watering_message;
            case WARM_ROOTS: return R.string.seedling_action_warm_roots_message;
            case COOL_ROOTS: return R.string.seedling_action_cool_roots_message;
            case INCREASE_LIGHT: return R.string.seedling_action_increase_light_message;
            case REDUCE_LIGHT: return R.string.seedling_action_reduce_light_message;
            case CHECK_SOIL_SENSOR: return R.string.seedling_action_check_soil_sensor_message;
            case CHECK_SENSOR: return R.string.seedling_action_check_sensor_message;
            case KEEP_OBSERVING:
            default: return R.string.seedling_action_keep_observing_message;
        }
    }

    @StringRes private static int metricLabel(SeedlingEnvironmentGuide.Metric metric) {
        switch (metric) {
            case AIR_TEMPERATURE: return R.string.seedling_metric_temperature;
            case AIR_HUMIDITY: return R.string.seedling_metric_humidity;
            case SOIL_MOISTURE: return R.string.seedling_metric_soil_moisture;
            case ROOT_TEMPERATURE: return R.string.seedling_metric_soil_temperature;
            case LIGHT:
            default: return R.string.seedling_metric_light;
        }
    }

    private static String current(Context context,
                                  SeedlingEnvironmentGuide.MetricAssessment item) {
        double value = item.getCurrent() == null ? 0d : item.getCurrent();
        switch (item.getMetric()) {
            case AIR_TEMPERATURE:
            case ROOT_TEMPERATURE:
                return context.getString(R.string.seedling_temperature_value, value);
            case AIR_HUMIDITY:
            case SOIL_MOISTURE:
                return context.getString(R.string.seedling_percent_value, value);
            case LIGHT:
            default:
                return context.getString(R.string.seedling_light_value, value)
                        + " " + context.getString(R.string.seedling_lux_unit);
        }
    }

    private static String range(Context context,
                                SeedlingEnvironmentGuide.MetricAssessment item) {
        SeedlingEnvironmentGuide.Range target = item.getTarget();
        switch (item.getMetric()) {
            case AIR_TEMPERATURE:
            case ROOT_TEMPERATURE:
                return context.getString(R.string.seedling_metric_range_temperature,
                        target.getMinimum(), target.getMaximum());
            case AIR_HUMIDITY:
            case SOIL_MOISTURE:
                return context.getString(R.string.seedling_metric_range_percent,
                        target.getMinimum(), target.getMaximum());
            case LIGHT:
            default:
                return context.getString(R.string.seedling_metric_range_light,
                        target.getMinimum(), target.getMaximum());
        }
    }
}
