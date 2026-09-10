package com.alidogukan.avora.notifications;

import android.content.Context;

import com.alidogukan.avora.R;
import com.alidogukan.avora.language.AvoraLanguageManager;
import com.alidogukan.avora.models.SeedlingBatch;
import com.alidogukan.avora.models.SeedlingNodeState;
import com.alidogukan.avora.models.SeedlingTelemetry;
import com.alidogukan.avora.seedling.SeedlingConditionSummary;
import com.alidogukan.avora.seedling.SeedlingEnvironmentGuide;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Converts seedling conditions and due nursery work into deduplicated alerts. */
public final class SeedlingNotificationCoordinator {
    private SeedlingNotificationCoordinator() { }

    public static void evaluate(Context sourceContext,
                                List<SeedlingBatch> batches,
                                Map<String, SeedlingNodeState> nodes,
                                Map<String, Long> latestLogEpochs) {
        Context context = AvoraLanguageManager.localizedContext(sourceContext);
        NotificationSettingsStore settings = new NotificationSettingsStore(context);
        GardenNotificationManager notifications = new GardenNotificationManager(context);
        List<SeedlingBatch> active = activeBatches(batches);
        Map<String, List<SeedlingBatch>> byNode = groupByNode(active);

        if (nodes != null) {
            for (String nodeId : nodes.keySet()) {
                if (!byNode.containsKey(nodeId)) resetNodeIncidents(notifications, nodeId);
            }
        }

        if (!settings.isCategoryEnabled("seedling")) {
            resetAllIncidents(notifications, byNode.keySet());
            return;
        }

        long nowEpoch = System.currentTimeMillis() / 1000L;
        int localHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        evaluateConditions(context, notifications, byNode,
                nodes == null ? new LinkedHashMap<>() : nodes, nowEpoch, localHour);

        if (!settings.isReminderEnabled("seedling") || settings.isQuietNow()
                || !SeedlingNotificationPolicy.isReminderDeliveryHour(localHour)) {
            return;
        }

        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        evaluateMilestones(context, notifications, active, today, zone);
        evaluateDailyChecks(context, notifications, active,
                latestLogEpochs == null ? new LinkedHashMap<>() : latestLogEpochs,
                today, zone, localHour);
    }

    private static void evaluateConditions(Context context,
                                           GardenNotificationManager notifications,
                                           Map<String, List<SeedlingBatch>> byNode,
                                           Map<String, SeedlingNodeState> nodes,
                                           long nowEpoch,
                                           int localHour) {
        for (Map.Entry<String, List<SeedlingBatch>> entry : byNode.entrySet()) {
            String nodeId = entry.getKey();
            List<SeedlingBatch> linked = entry.getValue();
            SeedlingNodeState state = nodes.get(nodeId);
            SeedlingTelemetry telemetry = state == null ? null : state.getLatest();
            String dataIncident = "seedling-data:" + nodeId;
            String warningIncident = "seedling-condition-warning:" + nodeId;
            String criticalIncident = "seedling-condition-critical:" + nodeId;

            if (!SeedlingNotificationPolicy.hasFreshTelemetry(telemetry, nowEpoch)) {
                notifications.resetIncident(warningIncident);
                notifications.resetIncident(criticalIncident);
                SeedlingBatch target = linked.get(0);
                notifications.publishIncident(
                        dataIncident, "SEEDLING", "NORMAL", target.getBatch_id(),
                        context.getString(R.string.notification_seedling_data_missing_title),
                        context.getString(R.string.notification_seedling_data_missing_description,
                                linked.size()),
                        "seedling-batch:" + target.getBatch_id() + ":data:" + nodeId);
                continue;
            }

            notifications.resetIncident(dataIncident);
            ConditionCandidate candidate = mostImportant(linked, telemetry, localHour);
            if (candidate == null
                    || candidate.assessment.getSeverity()
                    == SeedlingEnvironmentGuide.Severity.GOOD) {
                notifications.resetIncident(warningIncident);
                notifications.resetIncident(criticalIncident);
                continue;
            }

            boolean critical = candidate.assessment.getSeverity()
                    == SeedlingEnvironmentGuide.Severity.CRITICAL;
            notifications.resetIncident(critical ? warningIncident : criticalIncident);
            String incident = critical ? criticalIncident : warningIncident;
            int title = critical
                    ? R.string.notification_seedling_condition_critical_title
                    : R.string.notification_seedling_condition_warning_title;
            String description = context.getString(
                    R.string.notification_seedling_condition_description,
                    issueText(context, candidate.assessment));
            if (linked.size() > 1) {
                description += "\n" + context.getString(
                        R.string.notification_seedling_shared_sensor_note, linked.size());
            }
            notifications.publishIncident(
                    incident, "SEEDLING", critical ? "HIGH" : "NORMAL",
                    candidate.batch.getBatch_id(),
                    context.getString(title, candidate.batch.displayName()),
                    description,
                    "seedling-batch:" + candidate.batch.getBatch_id()
                            + ":condition:" + nodeId + ":" + (critical ? "critical" : "warning"));
        }
    }

    private static void evaluateMilestones(Context context,
                                           GardenNotificationManager notifications,
                                           List<SeedlingBatch> active,
                                           LocalDate today,
                                           ZoneId zone) {
        for (SeedlingBatch batch : active) {
            SeedlingNotificationPolicy.Milestone milestone =
                    SeedlingNotificationPolicy.dueMilestone(batch, today, zone);
            if (milestone == SeedlingNotificationPolicy.Milestone.NONE) continue;
            long dueEpoch = SeedlingNotificationPolicy.milestoneEpoch(batch, milestone);
            notifications.publishOnce(
                    "SEEDLING", "NORMAL", batch.getBatch_id(),
                    context.getString(milestoneTitle(milestone), batch.displayName()),
                    context.getString(R.string.notification_seedling_stage_description,
                            batch.displayName(), formatDate(dueEpoch)),
                    "seedling-batch:" + batch.getBatch_id() + ":stage:"
                            + batch.getStage() + ":" + dueEpoch);
        }
    }

    private static void evaluateDailyChecks(Context context,
                                            GardenNotificationManager notifications,
                                            List<SeedlingBatch> active,
                                            Map<String, Long> latestLogEpochs,
                                            LocalDate today,
                                            ZoneId zone,
                                            int localHour) {
        List<SeedlingBatch> missing = new ArrayList<>();
        for (SeedlingBatch batch : active) {
            long latest = latestLogEpochs.getOrDefault(batch.getBatch_id(), 0L);
            if (!SeedlingNotificationPolicy.hasLogToday(latest, today, zone)) {
                missing.add(batch);
            }
        }
        if (!SeedlingNotificationPolicy.shouldSendDailyCheck(localHour, missing.size())) return;
        String targetBatchId = missing.size() == 1 ? missing.get(0).getBatch_id() : "";
        notifications.publishOnce(
                "SEEDLING", "NORMAL", targetBatchId,
                context.getString(R.string.notification_seedling_daily_title),
                context.getString(R.string.notification_seedling_daily_description,
                        missing.size()),
                "seedling-daily:" + today);
    }

    private static List<SeedlingBatch> activeBatches(List<SeedlingBatch> batches) {
        List<SeedlingBatch> active = new ArrayList<>();
        if (batches == null) return active;
        for (SeedlingBatch batch : batches) {
            if (SeedlingNotificationPolicy.isActive(batch)) active.add(batch);
        }
        return active;
    }

    private static Map<String, List<SeedlingBatch>> groupByNode(List<SeedlingBatch> batches) {
        Map<String, List<SeedlingBatch>> grouped = new LinkedHashMap<>();
        for (SeedlingBatch batch : batches) {
            String nodeId = batch.getNode_id().isBlank() ? "seedling-001" : batch.getNode_id();
            grouped.computeIfAbsent(nodeId, ignored -> new ArrayList<>()).add(batch);
        }
        return grouped;
    }

    private static void resetAllIncidents(GardenNotificationManager notifications,
                                           Iterable<String> nodeIds) {
        for (String nodeId : nodeIds) {
            resetNodeIncidents(notifications, nodeId);
        }
    }

    private static void resetNodeIncidents(GardenNotificationManager notifications,
                                           String nodeId) {
        if (nodeId == null || nodeId.trim().isEmpty()) return;
        notifications.resetIncident("seedling-data:" + nodeId);
        notifications.resetIncident("seedling-condition-warning:" + nodeId);
        notifications.resetIncident("seedling-condition-critical:" + nodeId);
    }

    private static ConditionCandidate mostImportant(List<SeedlingBatch> batches,
                                                    SeedlingTelemetry telemetry,
                                                    int localHour) {
        ConditionCandidate best = null;
        for (SeedlingBatch batch : batches) {
            SeedlingEnvironmentGuide.Assessment assessment = SeedlingEnvironmentGuide.assess(
                    batch.getPlant_type(), batch.getStage(), telemetry, localHour);
            ConditionCandidate candidate = new ConditionCandidate(batch, assessment);
            if (best == null || rank(candidate.assessment) > rank(best.assessment)) {
                best = candidate;
            }
        }
        return best;
    }

    private static int rank(SeedlingEnvironmentGuide.Assessment assessment) {
        if (assessment.getSeverity() == SeedlingEnvironmentGuide.Severity.CRITICAL) return 2;
        return assessment.getSeverity() == SeedlingEnvironmentGuide.Severity.WARNING ? 1 : 0;
    }

    private static String issueText(Context context,
                                    SeedlingEnvironmentGuide.Assessment assessment) {
        List<String> conditions = new ArrayList<>();
        for (SeedlingEnvironmentGuide.MetricAssessment issue : assessment.getIssues()) {
            String label = context.getString(metricLabel(issue.getMetric()));
            if (issue.getStatus() == SeedlingEnvironmentGuide.Status.UNAVAILABLE) {
                conditions.add(context.getString(
                        R.string.seedling_metric_issue_unavailable, label));
            } else {
                int message = issue.getStatus() == SeedlingEnvironmentGuide.Status.LOW
                        ? R.string.seedling_metric_issue_low
                        : R.string.seedling_metric_issue_high;
                conditions.add(context.getString(message, label,
                        currentText(context, issue), rangeText(context, issue)));
            }
        }
        return SeedlingConditionSummary.bulletList(String.join(" ", conditions));
    }

    private static int metricLabel(SeedlingEnvironmentGuide.Metric metric) {
        switch (metric) {
            case AIR_TEMPERATURE: return R.string.seedling_metric_temperature;
            case AIR_HUMIDITY: return R.string.seedling_metric_humidity;
            case SOIL_MOISTURE: return R.string.seedling_metric_soil_moisture;
            case ROOT_TEMPERATURE: return R.string.seedling_metric_soil_temperature;
            case LIGHT:
            default: return R.string.seedling_metric_light;
        }
    }

    private static String currentText(Context context,
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

    private static String rangeText(Context context,
                                    SeedlingEnvironmentGuide.MetricAssessment item) {
        SeedlingEnvironmentGuide.Range range = item.getTarget();
        switch (item.getMetric()) {
            case AIR_TEMPERATURE:
            case ROOT_TEMPERATURE:
                return context.getString(R.string.seedling_metric_range_temperature,
                        range.getMinimum(), range.getMaximum());
            case AIR_HUMIDITY:
            case SOIL_MOISTURE:
                return context.getString(R.string.seedling_metric_range_percent,
                        range.getMinimum(), range.getMaximum());
            case LIGHT:
            default:
                return context.getString(R.string.seedling_metric_range_light,
                        range.getMinimum(), range.getMaximum());
        }
    }

    private static int milestoneTitle(SeedlingNotificationPolicy.Milestone milestone) {
        switch (milestone) {
            case GERMINATION: return R.string.notification_seedling_germination_title;
            case FIRST_LEAF: return R.string.notification_seedling_first_leaf_title;
            case HARDENING: return R.string.notification_seedling_hardening_title;
            case READY:
            default: return R.string.notification_seedling_ready_title;
        }
    }

    private static String formatDate(long epochSeconds) {
        return new SimpleDateFormat("dd MMMM", Locale.getDefault())
                .format(new Date(epochSeconds * 1000L));
    }

    private static final class ConditionCandidate {
        private final SeedlingBatch batch;
        private final SeedlingEnvironmentGuide.Assessment assessment;

        private ConditionCandidate(SeedlingBatch batch,
                                   SeedlingEnvironmentGuide.Assessment assessment) {
            this.batch = batch;
            this.assessment = assessment;
        }
    }
}
