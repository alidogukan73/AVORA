package com.alidogukan.avora.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.alidogukan.avora.R;
import com.alidogukan.avora.models.WateringHistory;
import com.alidogukan.avora.models.GardenSeason;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.history.WateringHistoryPresentation;
import com.google.android.material.card.MaterialCardView;

import java.time.ZoneId;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class WateringHistoryAdapter extends ListAdapter<
        WateringHistory,
        WateringHistoryAdapter.HistoryViewHolder
        > {

    private final Map<String, GardenZone> zones = new HashMap<>();
    private final Map<String, GardenSeason> seasons = new HashMap<>();

    public WateringHistoryAdapter() { super(DIFF_CALLBACK); }

    public void setDisplayContext(List<GardenZone> zoneValues, List<GardenSeason> seasonValues) {
        zones.clear();
        seasons.clear();
        if (zoneValues != null) for (GardenZone zone : zoneValues) {
            if (zone != null) zones.put(zone.getZone_id(), zone);
        }
        if (seasonValues != null) for (GardenSeason season : seasonValues) {
            if (season != null) seasons.put(season.getSeason_id(), season);
        }
        if (getItemCount() > 0) notifyItemRangeChanged(0, getItemCount());
    }

    private static final DiffUtil.ItemCallback<WateringHistory>
            DIFF_CALLBACK =
            new DiffUtil.ItemCallback<>() {

                @Override
                public boolean areItemsTheSame(
                        @NonNull WateringHistory oldItem,
                        @NonNull WateringHistory newItem
                ) {

                    return WateringHistoryPresentation.sameRecord(oldItem, newItem);
                }

                @Override
                public boolean areContentsTheSame(
                        @NonNull WateringHistory oldItem,
                        @NonNull WateringHistory newItem
                ) {
                    return WateringHistoryPresentation.sameContent(oldItem, newItem);
                }
            };


    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {

        View view = LayoutInflater
                .from(parent.getContext())
                .inflate(
                        R.layout.item_watering_history,
                        parent,
                        false
                );

        return new HistoryViewHolder(view);
    }


    @Override
    public void onBindViewHolder(
            @NonNull HistoryViewHolder holder,
            int position
    ) {

        holder.bind(
                getItem(position),
                zones,
                seasons
        );
    }


    static class HistoryViewHolder
            extends RecyclerView.ViewHolder {

        private final MaterialCardView cardHistoryItem;
        private final MaterialCardView cardHistoryStatus;
        private final MaterialCardView cardHistoryDelta;

        private final TextView txtHistoryZone;
        private final TextView txtHistoryDate;
        private final TextView txtHistoryTime;
        private final TextView txtHistoryStatus;

        private final TextView txtHistoryMode;
        private final TextView txtHistoryDuration;

        private final TextView txtHistoryMoistureBefore;
        private final TextView txtHistoryMoistureAfter;
        private final TextView txtHistoryMoistureDelta;

        private final TextView txtHistoryStopReason;


        public HistoryViewHolder(
                @NonNull View itemView
        ) {

            super(itemView);

            cardHistoryItem =
                    itemView.findViewById(
                            R.id.cardHistoryItem
                    );

            cardHistoryStatus =
                    itemView.findViewById(
                            R.id.cardHistoryStatus
                    );

            cardHistoryDelta =
                    itemView.findViewById(
                            R.id.cardHistoryDelta
                    );

            txtHistoryDate =
                    itemView.findViewById(
                            R.id.txtHistoryDate
                    );

            txtHistoryZone =
                    itemView.findViewById(
                            R.id.txtHistoryZone
                    );

            txtHistoryTime =
                    itemView.findViewById(
                            R.id.txtHistoryTime
                    );

            txtHistoryStatus =
                    itemView.findViewById(
                            R.id.txtHistoryStatus
                    );

            txtHistoryMode =
                    itemView.findViewById(
                            R.id.txtHistoryMode
                    );

            txtHistoryDuration =
                    itemView.findViewById(
                            R.id.txtHistoryDuration
                    );

            txtHistoryMoistureBefore =
                    itemView.findViewById(
                            R.id.txtHistoryMoistureBefore
                    );

            txtHistoryMoistureAfter =
                    itemView.findViewById(
                            R.id.txtHistoryMoistureAfter
                    );

            txtHistoryMoistureDelta =
                    itemView.findViewById(
                            R.id.txtHistoryMoistureDelta
                    );

            txtHistoryStopReason =
                    itemView.findViewById(
                            R.id.txtHistoryStopReason
                    );
        }


        /**
         * Tek bir sulama kaydını kart görünümüne bağlar.
         */
        public void bind(
                WateringHistory history,
                Map<String, GardenZone> zones,
                Map<String, GardenSeason> seasons
        ) {

            Context context =
                    itemView.getContext();

            txtHistoryZone.setText(WateringHistoryPresentation.label(history, zones, seasons,
                    context.getString(R.string.history_zone_legacy)));
            ZoneId timeZone = ZoneId.systemDefault();
            txtHistoryDate.setText(WateringHistoryPresentation.date(history, timeZone, Locale.getDefault()));
            txtHistoryTime.setText(WateringHistoryPresentation.time(history, timeZone, Locale.getDefault()));

            txtHistoryMode.setText(
                    formatMode(
                            context,
                            history.getMode()
                    )
            );

            txtHistoryDuration.setText(
                    formatDuration(
                            context,
                            history.getDuration()
                    )
            );

            Long before = WateringHistoryPresentation.before(history);
            Long after = WateringHistoryPresentation.after(history);
            Long delta = WateringHistoryPresentation.delta(history);
            txtHistoryMoistureBefore.setText(before == null ? "—"
                    : context.getString(R.string.percentage_format, before));
            txtHistoryMoistureAfter.setText(after == null ? "—"
                    : context.getString(R.string.percentage_format, after));
            txtHistoryMoistureDelta.setText(delta == null ? "—"
                    : context.getString(R.string.signed_percentage_format, delta));

            txtHistoryStopReason.setText(
                    formatStopReason(
                            context,
                            history.getStopReason()
                    )
            );

            updateCompletionUi(
                    context,
                    history
            );

            updateMoistureDeltaUi(
                    context,
                    delta == null ? 0L : delta
            );
        }

        /**
         * Tamamlanma durumuna göre rozet ve kart rengini değiştirir.
         */
        private void updateCompletionUi(Context context, WateringHistory history) {
            WateringHistoryPresentation.Outcome outcome = WateringHistoryPresentation.outcome(history);
            int statusText, statusColor, backgroundColor;
            switch (outcome) {
                case COMPLETED:
                    statusText = R.string.history_status_completed;
                    statusColor = R.color.online;
                    backgroundColor = R.color.onlineBackground;
                    break;
                case SIMULATED:
                    statusText = R.string.history_status_simulated;
                    statusColor = R.color.textSecondary;
                    backgroundColor = R.color.surfaceSoft;
                    break;
                case NOT_STARTED:
                    statusText = R.string.history_status_not_started;
                    statusColor = R.color.warning;
                    backgroundColor = R.color.warningBackground;
                    break;
                case WARNING:
                    statusText = R.string.history_status_warning;
                    statusColor = R.color.warning;
                    backgroundColor = R.color.warningBackground;
                    break;
                default:
                    statusText = R.string.history_status_interrupted;
                    statusColor = R.color.offline;
                    backgroundColor = R.color.offlineBackground;
            }
            txtHistoryStatus.setText(statusText);
            txtHistoryStatus.setTextColor(color(context, statusColor));
            cardHistoryStatus.setCardBackgroundColor(color(context, backgroundColor));
            cardHistoryStatus.setStrokeColor(color(context, statusColor));
            cardHistoryItem.setStrokeColor(color(context,
                    outcome == WateringHistoryPresentation.Outcome.COMPLETED
                    || outcome == WateringHistoryPresentation.Outcome.SIMULATED
                            ? R.color.border : statusColor));
        }


        /**
         * Nem farkına göre değişim kartını renklendirir.
         */
        private void updateMoistureDeltaUi(
                Context context,
                long moistureDelta
        ) {

            int statusColor;
            int backgroundColor;

            if (moistureDelta > 0) {

                statusColor =
                        color(
                                context,
                                R.color.moistureIdeal
                        );

                backgroundColor =
                        color(
                                context,
                                R.color.moistureIdealBackground
                        );

            } else if (moistureDelta < 0) {

                statusColor =
                        color(
                                context,
                                R.color.moistureLow
                        );

                backgroundColor =
                        color(
                                context,
                                R.color.moistureLowBackground
                        );

            } else {

                statusColor =
                        color(
                                context,
                                R.color.textSecondary
                        );

                backgroundColor =
                        color(
                                context,
                                R.color.surfaceSoft
                        );
            }

            txtHistoryMoistureDelta.setTextColor(
                    statusColor
            );

            cardHistoryDelta.setCardBackgroundColor(
                    backgroundColor
            );

            cardHistoryDelta.setStrokeColor(
                    statusColor
            );
        }


        /**
         * AUTO ve MANUAL değerlerini kullanıcı dostu hale getirir.
         */
        private String formatMode(
                Context context,
                String mode
        ) {

            if (
                    mode == null
                            || mode.isBlank()
            ) {

                return context.getString(
                        R.string.history_mode_unknown
                );
            }

            switch (
                    mode.trim()
                            .toUpperCase(Locale.ROOT)
            ) {

                case "AUTO":
                case "AUTOMATIC":
                    return context.getString(
                            R.string.history_mode_auto
                    );

                case "MANUAL":
                    return context.getString(
                            R.string.history_mode_manual
                    );

                default:
                    return mode;
            }
        }


        /**
         * Saniye değerini okunabilir süreye dönüştürür.
         */
        private String formatDuration(
                Context context,
                long seconds
        ) {

            long safeSeconds =
                    Math.max(
                            0,
                            seconds
                    );

            if (safeSeconds < 60) {

                return context.getString(
                        R.string.duration_seconds_format,
                        safeSeconds
                );
            }

            if (safeSeconds >= 3600) {
                return context.getString(R.string.duration_hours_minutes_format,
                        safeSeconds / 3600, (safeSeconds % 3600) / 60);
            }

            long minutes =
                    safeSeconds / 60;

            long remainingSeconds =
                    safeSeconds % 60;

            return context.getString(
                    R.string.duration_minutes_seconds_format,
                    minutes,
                    remainingSeconds
            );
        }


        /**
         * Backend durdurma nedenini kullanıcı dostu metne çevirir.
         */
        private String formatStopReason(Context context, String stopReason) {
            int resource = WateringHistoryPresentation.reasonResource(stopReason);
            return resource == 0 ? stopReason.replace("_", " ") : context.getString(resource);
        }

        private int color(
                Context context,
                int colorResource
        ) {

            return ContextCompat.getColor(
                    context,
                    colorResource
            );
        }
    }
}
