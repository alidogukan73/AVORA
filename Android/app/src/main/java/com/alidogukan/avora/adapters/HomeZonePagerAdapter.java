package com.alidogukan.avora.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.alidogukan.avora.models.GardenSeason;
import com.alidogukan.avora.models.Status;

import com.alidogukan.avora.R;
import com.alidogukan.avora.season.SeasonDisplayIdentity;
import com.alidogukan.avora.notifications.NotificationPolicy;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.ZoneIrrigationStatus;
import com.alidogukan.avora.zones.PhysicalZoneIdentity;
import com.alidogukan.avora.zones.ZoneCapacityPolicy;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.ArrayList;
import java.util.List;

public class HomeZonePagerAdapter extends RecyclerView.Adapter<HomeZonePagerAdapter.ZoneViewHolder> {

    public interface OnZoneClickListener {
        void onZoneClick(GardenZone zone);
    }

    private final List<GardenZone> zones = new ArrayList<>();
    private final List<GardenSeason> seasons = new ArrayList<>();
    private final OnZoneClickListener listener;
    private Status status;

    public HomeZonePagerAdapter(OnZoneClickListener listener) {
        this.listener = listener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void submitList(List<GardenZone> items) {
        zones.clear();
        zones.addAll(ZoneCapacityPolicy.activeZones(items));
        // The pager exposes a virtual Integer.MAX_VALUE item range. A zone-count
        // change alters every virtual position, so a full invalidation is required.
        notifyDataSetChanged();
    }
    @SuppressLint("NotifyDataSetChanged")
    public void submitSeasons(List<GardenSeason> items) {
        seasons.clear();
        if (items != null) seasons.addAll(items);
        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void submitStatus(Status value) {
        status = value;
        notifyDataSetChanged();
    }


    public int getZoneCount() {
        return zones.size();
    }

    public int toZonePosition(int adapterPosition) {
        return zones.isEmpty() ? 0 : Math.floorMod(adapterPosition, zones.size());
    }

    public int initialAdapterPosition() {
        if (zones.isEmpty()) {
            return 0;
        }
        int middle = Integer.MAX_VALUE / 2;
        return middle - Math.floorMod(middle, zones.size());
    }

    public int nearestAdapterPosition(int currentAdapterPosition, int requestedZonePosition) {
        if (zones.isEmpty()) {
            return 0;
        }
        int count = zones.size();
        int currentZonePosition = toZonePosition(currentAdapterPosition);
        int forward = Math.floorMod(requestedZonePosition - currentZonePosition, count);
        int backward = forward - count;
        return currentAdapterPosition + (Math.abs(backward) < Math.abs(forward) ? backward : forward);
    }

    @NonNull
    @Override
    public ZoneViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_home_zone_page, parent, false);
        int parentWidth = parent.getMeasuredWidth();
        if (parentWidth <= 0) {
            int padding = Math.round(32f * parent.getResources().getDisplayMetrics().density);
            parentWidth = parent.getResources().getDisplayMetrics().widthPixels - padding;
        }
        view.getLayoutParams().width = Math.max(1, parentWidth);
        return new ZoneViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ZoneViewHolder holder, int position) {
        holder.bind(zones.get(toZonePosition(position)));
    }

    @Override
    public int getItemCount() {
        return zones.isEmpty() ? 0 : Integer.MAX_VALUE;
    }

    class ZoneViewHolder extends RecyclerView.ViewHolder {
        private final TextView name;
        private final TextView sensor;
        private final TextView badge;
        private final TextView moisture;
        private final TextView moistureState;
        private final TextView idealState;
        private final TextView idealRange;
        private final TextView wateringValue;
        private final TextView wateringDetail;
        private final CircularProgressIndicator progress;

        ZoneViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.txtPageZoneName);
            sensor = itemView.findViewById(R.id.txtPageZoneSensor);
            badge = itemView.findViewById(R.id.txtPageZoneBadge);
            moisture = itemView.findViewById(R.id.txtPageZoneMoisture);
            moistureState = itemView.findViewById(R.id.txtPageZoneMoistureState);
            idealState = itemView.findViewById(R.id.txtPageZoneIdealState);
            idealRange = itemView.findViewById(R.id.txtPageZoneIdealRange);
            wateringValue = itemView.findViewById(R.id.txtPageZoneLastWaterValue);
            wateringDetail = itemView.findViewById(R.id.txtPageZoneLastWaterDetail);
            progress = itemView.findViewById(R.id.progressPageZoneMoisture);
        }

        void bind(GardenZone zone) {
            Context context = itemView.getContext();
            String emoji = SeasonDisplayIdentity.physicalIcon(zone);
            String zoneName = SeasonDisplayIdentity.operationalName(zone, seasons);
            String crops = SeasonDisplayIdentity.activeCropNames(zone, seasons);
            String sensorId = zone.getSensor_id() == null ? "—" : zone.getSensor_id();

            name.setText(context.getString(
                    R.string.runtime_icon_label,
                    emoji,
                    zoneName));
            sensor.setText(crops.isBlank()
                    ? context.getString(R.string.home_zone_sensor_subtitle, sensorId)
                    : context.getString(R.string.home_zone_crops_sensor_subtitle, crops, sensorId));
            if (itemView instanceof MaterialCardView) {
                try {
                    ((MaterialCardView) itemView).setStrokeColor(
                            Color.parseColor(PhysicalZoneIdentity.color(zone)));
                } catch (IllegalArgumentException ignored) {
                    // PhysicalZoneIdentity already supplies a safe default.
                }
            }

            if (!zone.isSensor_enabled()) {
                bindPaused(context);
            } else if (isConnected(zone)) {
                bindConnected(context, zone);
            } else {
                bindWaiting(context);
            }
            itemView.setOnClickListener(view -> listener.onZoneClick(zone));
        }

        private void bindConnected(Context context, GardenZone zone) {
            int value = Math.max(0, Math.min(100, zone.getMoisture()));
            badge.setText(R.string.sensor_active);
            badge.setTextColor(color(context, R.color.primary));
            moisture.setText(context.getString(R.string.sensor_moisture_format, value));
            progress.setProgress(value);

            int statusColor;
            String statusText;
            if (value < 35) {
                statusColor = color(context, R.color.moistureLow);
                statusText = context.getString(R.string.runtime_moisture_low);
            } else if (value > 70) {
                statusColor = color(context, R.color.info);
                statusText = context.getString(R.string.runtime_moisture_high);
            } else {
                statusColor = color(context, R.color.moistureIdeal);
                statusText = context.getString(R.string.runtime_moisture_ideal);
            }
            moisture.setTextColor(statusColor);
            moistureState.setText(statusText);
            moistureState.setTextColor(statusColor);
            progress.setIndicatorColor(statusColor);
            progress.setTrackColor(color(context, R.color.divider));
            bindIdealRange(context, zone, value, statusColor);
            bindWateringState(context, zone);
        }

        private void bindWaiting(Context context) {
            badge.setText(R.string.ai_runtime_validation_waiting);
            badge.setTextColor(color(context, R.color.textSecondary));
            moisture.setText("—");
            moisture.setTextColor(color(context, R.color.textSecondary));
            moistureState.setText(R.string.home_zone_sensor_waiting);
            moistureState.setTextColor(color(context, R.color.textSecondary));
            progress.setProgress(0);
            progress.setIndicatorColor(color(context, R.color.textSecondary));
            progress.setTrackColor(color(context, R.color.divider));
            idealState.setText(R.string.plant_list_waiting);
            idealRange.setText("—");
            wateringValue.setText(R.string.runtime_cannot_evaluate);
            wateringDetail.setText(R.string.runtime_sensor_no_data);
        }

        private void bindPaused(Context context) {
            badge.setText(R.string.home_zone_sensor_paused_badge);
            badge.setTextColor(color(context, R.color.textSecondary));
            moisture.setText("—");
            moisture.setTextColor(color(context, R.color.textSecondary));
            moistureState.setText(R.string.home_zone_sensor_paused);
            moistureState.setTextColor(color(context, R.color.textSecondary));
            progress.setProgress(0);
            progress.setIndicatorColor(color(context, R.color.textSecondary));
            progress.setTrackColor(color(context, R.color.divider));
            idealState.setText(R.string.runtime_sensor_off);
            idealRange.setText("—");
            wateringValue.setText(R.string.runtime_cannot_evaluate);
            wateringDetail.setText(R.string.runtime_sensor_off);
        }

        private boolean isConnected(GardenZone zone) {
            if (zone.getUpdated_at_epoch() <= 0L) {
                return false;
            }
            long age = Math.max(0L, System.currentTimeMillis() / 1000L - zone.getUpdated_at_epoch());
            return age <= 90L;
        }

        private void bindIdealRange(Context context, GardenZone zone, int value, int statusColor) {
            int lower = Math.max(0, zone.getMoisture_limit());
            int upper = Math.min(100, lower + 20);
            String label = value < lower ? context.getString(R.string.runtime_moisture_low_short)
                    : value > upper ? context.getString(R.string.runtime_moisture_high_short) : context.getString(R.string.runtime_ideal_range);
            idealState.setText(label);
            idealState.setTextColor(statusColor);
            idealRange.setText(context.getString(
                    R.string.home_zone_ideal_range,
                    lower,
                    upper));
            idealRange.setTextColor(color(context, R.color.textSecondary));
        }

        private void bindWateringState(Context context, GardenZone zone) {
            ZoneIrrigationStatus irrigation = zone.getIrrigation_status();
            boolean wateringConfirmed = irrigation != null
                    && NotificationPolicy.isConfirmedWateringState(
                    irrigation.isWatering_active(),
                    zone.getValve_id(),
                    status != null && status.isValveOpen(),
                    status != null && status.isRelay(),
                    status == null ? "" : status.getActiveValveId(),
                    status == null ? 0L : status.getLastSeenEpoch(),
                    System.currentTimeMillis() / 1000L,
                    90L);
            if (wateringConfirmed) {
                wateringValue.setText(R.string.runtime_irrigation_running);
                wateringDetail.setText(R.string.runtime_pump_valve_active);
                wateringValue.setTextColor(color(context, R.color.info));
                return;
            }
            if (irrigation != null && irrigation.isCooldown_active()) {
                int minutes = Math.max(1, (irrigation.getCooldown_remaining() + 59) / 60);
                wateringValue.setText(context.getString(R.string.runtime_wait_minutes, minutes));
                wateringDetail.setText(R.string.runtime_moisture_will_remeasure);
                wateringValue.setTextColor(color(context, R.color.warning));
                return;
            }
            if (zone.getMoisture() < zone.getMoisture_limit()) {
                wateringValue.setText(zone.isIrrigation_enabled()
                        ? R.string.runtime_irrigation_preparing
                        : R.string.runtime_moisture_low_short);
                wateringDetail.setText(zone.isIrrigation_enabled()
                        ? R.string.runtime_automatic_monitoring
                        : R.string.runtime_automatic_off);
                wateringValue.setTextColor(color(context, R.color.warning));
                return;
            }
            wateringValue.setText(R.string.runtime_irrigation_not_needed);
            wateringDetail.setText(zone.isIrrigation_enabled()
                    ? R.string.runtime_automatic_on
                    : R.string.runtime_automatic_off);
            wateringValue.setTextColor(color(context, R.color.primary));
        }

        private int color(Context context, int resource) {
            return ContextCompat.getColor(context, resource);
        }
    }
}
