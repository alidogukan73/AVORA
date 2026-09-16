package com.alidogukan.avora.ui.irrigationassistant;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.alidogukan.avora.R;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.IrrigationTimingPlan;
import com.alidogukan.avora.models.ZoneIrrigationStatus;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static com.alidogukan.avora.ui.irrigationassistant.IrrigationAssistantCodes.AUTO_MODE_DISABLED;
import static com.alidogukan.avora.ui.irrigationassistant.IrrigationAssistantCodes.COOLDOWN_ACTIVE;
import static com.alidogukan.avora.ui.irrigationassistant.IrrigationAssistantCodes.INSUFFICIENT_SENSOR_SAMPLES;
import static com.alidogukan.avora.ui.irrigationassistant.IrrigationAssistantCodes.MOISTURE_BELOW_LIMIT;
import static com.alidogukan.avora.ui.irrigationassistant.IrrigationAssistantCodes.MOISTURE_SUFFICIENT;
import static com.alidogukan.avora.ui.irrigationassistant.IrrigationAssistantCodes.SENSOR_INVALID;
import static com.alidogukan.avora.ui.irrigationassistant.IrrigationAssistantCodes.SENSOR_UNSTABLE;
import static com.alidogukan.avora.ui.irrigationassistant.IrrigationAssistantCodes.SYSTEM_DISABLED;
import static com.alidogukan.avora.ui.irrigationassistant.IrrigationAssistantCodes.VALVE_NOT_PHYSICAL;
import static com.alidogukan.avora.ui.irrigationassistant.IrrigationAssistantCodes.WAITING_FOR_MOISTURE_RECOVERY;
import static com.alidogukan.avora.ui.irrigationassistant.IrrigationAssistantCodes.WEATHER_RAIN_DELAY;
import static com.alidogukan.avora.ui.irrigationassistant.IrrigationAssistantCodes.WEATHER_WIND_DELAY;

/** Seçili bölgenin günlük sulama özetini görünümden bağımsız olarak çizer. */
public final class SelectedZoneSummaryRenderer {

    private final Context context;
    private final LinearLayout container;
    private final IrrigationAssistantFormatter formatter;

    public SelectedZoneSummaryRenderer(
            @NonNull Context context,
            @NonNull LinearLayout container,
            @NonNull IrrigationAssistantFormatter formatter
    ) {
        this.context = context;
        this.container = container;
        this.formatter = formatter;
    }

    public void render(@NonNull List<GardenZone> zones, int selectedIndex) {
        container.removeAllViews();

        if (zones.isEmpty()) {
            addRow(
                    context.getString(R.string.symbol_plant),
                    context.getString(R.string.ai_zone_waiting),
                    R.color.textSecondary
            );
            return;
        }

        int safeIndex = Math.max(0, Math.min(selectedIndex, zones.size() - 1));
        GardenZone zone = zones.get(safeIndex);
        ZoneIrrigationStatus status = zone.getIrrigation_status();
        String emoji = formatter.safeText(
                zone.getEmoji(),
                context.getString(R.string.symbol_plant)
        );
        String name = com.alidogukan.avora.zones.PhysicalZoneIdentity.name(zone);
        String sensorId = formatter.safeText(
                zone.getSensor_id(),
                context.getString(R.string.ai_value_unavailable)
        );
        boolean sensorFresh = zone.isSensor_enabled() && formatter.isZoneFresh(zone);
        String currentMoisture = sensorFresh
                ? context.getString(R.string.ai_selected_zone_moisture_value, zone.getMoisture())
                : context.getString(R.string.ai_value_unavailable);

        addRow(
                context.getString(
                        R.string.ai_selected_zone_title,
                        emoji,
                        name,
                        safeIndex + 1,
                        zones.size()
                ),
                context.getString(R.string.ai_selected_zone_sensor, sensorId),
                R.color.textSecondary
        );
        addRow(
                context.getString(R.string.ai_selected_zone_moisture_title),
                context.getString(
                        R.string.ai_selected_zone_moisture_detail,
                        currentMoisture,
                        zone.getMoisture_limit()
                ),
                sensorFresh ? R.color.online : R.color.warning
        );
        addRow(
                context.getString(R.string.ai_selected_zone_decision_title),
                resolveDecision(zone, status) + "\n" + formatDuration(zone, status),
                resolveDecisionColor(zone, status)
        );
        addTimingPlanRow(status);
    }

    private void addTimingPlanRow(ZoneIrrigationStatus status) {
        IrrigationTimingPlan plan = status == null ? null : status.getTiming_plan();
        if (plan == null || plan.getStatus() == null || "NOT_REQUIRED".equals(plan.getStatus())) {
            return;
        }

        int color = plan.isEmergency() ? R.color.warning : R.color.online;
        String detail;
        switch (plan.getStatus()) {
            case "SCHEDULED":
            case "POSTPONED_BY_TIMING":
                detail = context.getString(
                        R.string.ai_timing_scheduled,
                        formatTimingInstant(plan.getRecommended_at_epoch())
                );
                break;
            case "CRITICAL_IMMEDIATE":
            case "EMERGENCY_READY":
                detail = context.getString(R.string.ai_timing_critical_immediate);
                break;
            case "READY_FOR_RECHECK":
                detail = context.getString(R.string.ai_timing_ready_recheck);
                break;
            case "WEATHER_POSTPONED":
                detail = context.getString(R.string.ai_timing_weather_postponed);
                color = R.color.warning;
                break;
            default:
                detail = context.getString(R.string.ai_timing_immediate);
                break;
        }
        if (plan.isRecheck_before_watering()) {
            detail += "\n" + context.getString(R.string.ai_timing_recheck_note);
        }
        addRow(context.getString(R.string.irrigation_timing_title), detail, color);
    }

    private String formatTimingInstant(long epochSeconds) {
        if (epochSeconds <= 0) {
            return context.getString(R.string.ai_value_unavailable);
        }
        return new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
                .format(new Date(epochSeconds * 1000L));
    }

    private String resolveDecision(GardenZone zone, ZoneIrrigationStatus status) {
        if (!zone.isIrrigation_enabled()) {
            return context.getString(R.string.ai_zone_disabled);
        }
        if (!zone.isSensor_enabled() || !formatter.isZoneFresh(zone)) {
            return context.getString(R.string.ai_zone_waiting);
        }
        if (status != null && status.isWatering_active()) {
            return context.getString(R.string.ai_zone_watering);
        }
        if (status != null && status.hasHardware_ready() && !status.isHardware_ready()) {
            return context.getString(R.string.ai_zone_valve_not_physical);
        }
        if (status != null && status.isWaiting_for_moisture_recovery()) {
            return context.getString(
                    R.string.ai_zone_cycle_limit_reached,
                    status.getCompleted_watering_cycles()
            );
        }
        if (status != null && status.isCooldown_active()) {
            return context.getString(
                    R.string.ai_zone_cooldown,
                    formatter.formatZoneDuration(status.getCooldown_remaining())
            );
        }
        if (status != null && status.getQueue_position() > 0) {
            return context.getString(R.string.ai_zone_queued, status.getQueue_position());
        }
        return formatDecision(zone, status);
    }

    @ColorRes
    private int resolveDecisionColor(GardenZone zone, ZoneIrrigationStatus status) {
        if (!zone.isSensor_enabled() || !formatter.isZoneFresh(zone)) {
            return R.color.warning;
        }
        if (status != null && status.isWatering_active()) {
            return R.color.info;
        }
        if (status != null && status.getQueue_position() > 0) {
            return R.color.accentOrange;
        }
        String reason = status == null ? "" : formatter.safeText(status.getDecision_reason(), "");
        if ((status != null && status.hasHardware_ready() && !status.isHardware_ready())
                || (status != null && (status.isCooldown_active()
                || status.isWaiting_for_moisture_recovery()))
                || isWarningReason(reason)) {
            return R.color.warning;
        }
        if (MOISTURE_SUFFICIENT.equals(reason)) {
            return R.color.online;
        }
        return R.color.textSecondary;
    }

    private String formatDuration(GardenZone zone, ZoneIrrigationStatus status) {
        int configuredSeconds = status != null
                && status.getConfigured_duration_seconds() > 0
                ? status.getConfigured_duration_seconds()
                : zone.getPump_duration();
        int learnedSeconds = status == null ? 0 : status.getLearned_duration_seconds();
        int effectiveSeconds = status != null && status.getEffective_duration_seconds() > 0
                ? status.getEffective_duration_seconds()
                : configuredSeconds;

        if (learnedSeconds > 0
                && status != null
                && status.getAdaptive_watering_count() > 0) {
            return context.getString(
                    R.string.ai_selected_zone_learned_duration,
                    formatter.formatZoneDuration(learnedSeconds),
                    formatter.formatZoneDuration(effectiveSeconds),
                    status.getAdaptive_watering_count(),
                    Math.round(status.getAdaptive_confidence() * 100.0)
            );
        }
        return context.getString(
                R.string.ai_selected_zone_configured_duration,
                formatter.formatZoneDuration(effectiveSeconds)
        );
    }

    private String formatDecision(GardenZone zone, ZoneIrrigationStatus status) {
        if (status == null) {
            return context.getString(R.string.ai_zone_learning);
        }

        String reason = status.getDecision_reason();
        if (MOISTURE_SUFFICIENT.equals(reason)) {
            return context.getString(R.string.ai_zone_moisture_sufficient, zone.getMoisture());
        }
        if (MOISTURE_BELOW_LIMIT.equals(reason)) {
            return context.getString(
                    R.string.ai_zone_moisture_low,
                    status.getMoisture_deficit()
            );
        }
        if (WAITING_FOR_MOISTURE_RECOVERY.equals(reason)) {
            return context.getString(R.string.ai_zone_recovery_waiting);
        }
        if (VALVE_NOT_PHYSICAL.equals(reason)) {
            return context.getString(R.string.ai_zone_valve_not_physical);
        }
        if (SYSTEM_DISABLED.equals(reason)) {
            return context.getString(R.string.ai_zone_system_disabled);
        }
        if (AUTO_MODE_DISABLED.equals(reason)) {
            return context.getString(R.string.ai_zone_auto_mode_disabled);
        }
        if (INSUFFICIENT_SENSOR_SAMPLES.equals(reason)) {
            return context.getString(R.string.ai_zone_samples_collecting);
        }
        if (COOLDOWN_ACTIVE.equals(reason)) {
            return context.getString(R.string.ai_zone_cooldown_without_duration);
        }
        if (WEATHER_RAIN_DELAY.equals(reason)) {
            return context.getString(R.string.ai_zone_weather_rain_delay);
        }
        if (WEATHER_WIND_DELAY.equals(reason)) {
            return context.getString(R.string.ai_zone_weather_wind_delay);
        }
        if (SENSOR_UNSTABLE.equals(reason) || SENSOR_INVALID.equals(reason)) {
            return context.getString(R.string.ai_zone_unstable);
        }
        return context.getString(R.string.ai_zone_learning);
    }

    private boolean isWarningReason(String reason) {
        return MOISTURE_BELOW_LIMIT.equals(reason)
                || WAITING_FOR_MOISTURE_RECOVERY.equals(reason)
                || VALVE_NOT_PHYSICAL.equals(reason)
                || SENSOR_UNSTABLE.equals(reason)
                || SENSOR_INVALID.equals(reason)
                || WEATHER_RAIN_DELAY.equals(reason)
                || WEATHER_WIND_DELAY.equals(reason);
    }

    private void addRow(String title, String detail, @ColorRes int detailColor) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 12, 0, 12);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        titleView.setTextSize(15f);
        titleView.setTypeface(titleView.getTypeface(), Typeface.BOLD);

        TextView detailView = new TextView(context);
        detailView.setText(detail);
        detailView.setTextColor(ContextCompat.getColor(context, detailColor));
        detailView.setTextSize(13f);
        detailView.setPadding(0, 3, 0, 0);

        row.addView(titleView);
        row.addView(detailView);
        container.addView(row);
    }
}
