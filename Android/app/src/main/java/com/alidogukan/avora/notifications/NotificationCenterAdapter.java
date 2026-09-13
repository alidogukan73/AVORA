package com.alidogukan.avora.notifications;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.AppCompatImageView;

import com.alidogukan.avora.R;
import com.alidogukan.avora.models.GardenNotification;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationCenterAdapter
        extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_NOTIFICATION = 1;

    private static final int ACTION_WIDTH_DP = 184;

    public interface Listener {

        void onNotificationClick(
                GardenNotification value
        );

        void onSaveClick(
                GardenNotification value
        );

        void onDeleteClick(
                GardenNotification value
        );
    }

    private final Context context;
    private final Listener listener;

    private final List<Row> rows =
            new ArrayList<>();

    public NotificationCenterAdapter(
            Context context,
            Listener listener
    ) {
        this.context = context;
        this.listener = listener;
    }

    public void submitList(
            List<GardenNotification> notifications
    ) {

        List<Row> updatedRows = new ArrayList<>();

        String lastDay = "";

        if (notifications != null) {

            for (GardenNotification value : notifications) {

                if (value == null) {
                    continue;
                }

                String day =
                        dayLabel(
                                value.getCreated_at_epoch()
                        );

                if (!day.equals(lastDay)) {

                    updatedRows.add(
                            Row.header(day)
                    );

                    lastDay = day;
                }

                updatedRows.add(
                        Row.notification(value)
                );
            }
        }

        int previousCount = rows.size();
        rows.clear();
        if (previousCount > 0) {
            notifyItemRangeRemoved(0, previousCount);
        }
        rows.addAll(updatedRows);
        if (!rows.isEmpty()) {
            notifyItemRangeInserted(0, rows.size());
        }
    }

    public GardenNotification getNotificationAt(
            int position
    ) {

        if (position < 0
                || position >= rows.size()) {

            return null;
        }

        return rows
                .get(position)
                .notification;
    }

    public boolean isNotificationRow(
            int position
    ) {
        return getNotificationAt(position) != null;
    }

    public View getSwipeForeground(
            RecyclerView.ViewHolder holder
    ) {

        if (!(holder instanceof NotificationHolder)) {
            return null;
        }

        return ((NotificationHolder) holder)
                .foreground;
    }

    public int getActionWidth() {
        return dp(ACTION_WIDTH_DP);
    }

    @Override
    public int getItemViewType(
            int position
    ) {

        return rows
                .get(position)
                .notification == null
                ? TYPE_HEADER
                : TYPE_NOTIFICATION;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {

        if (viewType == TYPE_HEADER) {

            TextView header =
                    new TextView(context);

            header.setTextSize(15);

            header.setTextColor(
                    context.getColor(
                            R.color.textPrimary
                    )
            );

            header.setTypeface(
                    null,
                    Typeface.BOLD
            );

            header.setPadding(
                    0,
                    dp(2),
                    0,
                    dp(6)
            );

            return new HeaderHolder(header);
        }

        /*
         * ROOT
         *
         * Arkada actionContainer,
         * önde foreground kartı bulunuyor.
         */
        FrameLayout root =
                new FrameLayout(context);

        RecyclerView.LayoutParams rootParams =
                new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        rootParams.bottomMargin = dp(8);

        root.setLayoutParams(rootParams);

        // -------------------------------
        // ARKADAKİ BUTONLAR
        // -------------------------------

        LinearLayout actions =
                new LinearLayout(context);

        actions.setOrientation(
                LinearLayout.HORIZONTAL
        );

        actions.setGravity(
                Gravity.CENTER_VERTICAL
        );

        FrameLayout.LayoutParams actionsParams =
                new FrameLayout.LayoutParams(
                        dp(ACTION_WIDTH_DP),
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.END
                );

        actions.setLayoutParams(actionsParams);

        MaterialButton saveButton =
                new MaterialButton(context);

        saveButton.setTextSize(12);
        saveButton.setAllCaps(false);

        saveButton.setTextColor(
                context.getColor(
                        android.R.color.white
                )
        );

        saveButton.setBackgroundTintList(
                ColorStateList.valueOf(
                        context.getColor(
                                R.color.primary
                        )
                )
        );

        MaterialButton deleteButton =
                new MaterialButton(context);

        deleteButton.setText(R.string.notification_center_action_delete);
        deleteButton.setTextSize(12);
        deleteButton.setAllCaps(false);

        deleteButton.setTextColor(
                context.getColor(
                        android.R.color.white
                )
        );

        deleteButton.setBackgroundTintList(
                ColorStateList.valueOf(
                        context.getColor(
                                R.color.error
                        )
                )
        );

        actions.addView(
                saveButton,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1
                )
        );

        actions.addView(
                deleteButton,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1
                )
        );

        root.addView(actions);

        // -------------------------------
        // ÖNDEKİ BİLDİRİM KARTI
        // -------------------------------

        MaterialCardView card =
                new MaterialCardView(context);

        card.setRadius(dp(16));

        card.setStrokeColor(
                context.getColor(
                        R.color.border
                )
        );

        card.setStrokeWidth(dp(1));

        FrameLayout.LayoutParams cardParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        card.setLayoutParams(cardParams);

        LinearLayout row =
                new LinearLayout(context);

        row.setGravity(
                Gravity.CENTER_VERTICAL
        );

        row.setPadding(
                dp(13),
                dp(12),
                dp(13),
                dp(12)
        );

        AppCompatImageView icon =
                new AppCompatImageView(context);

        icon.setScaleType(
                AppCompatImageView.ScaleType.CENTER_INSIDE
        );

        icon.setImageTintList(
                ColorStateList.valueOf(
                        context.getColor(
                                R.color.primary
                        )
                )
        );

        row.addView(
                icon,
                new LinearLayout.LayoutParams(
                        dp(42),
                        dp(48)
                )
        );

        LinearLayout info =
                new LinearLayout(context);

        info.setOrientation(
                LinearLayout.VERTICAL
        );

        TextView title =
                text(
                        14,
                        R.color.textPrimary
                );

        title.setTypeface(
                null,
                Typeface.BOLD
        );

        TextView description =
                text(
                        12,
                        R.color.textSecondary
                );

        description.setMaxLines(2);

        info.addView(title);
        info.addView(description);

        row.addView(
                info,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        TextView time =
                text(
                        11,
                        R.color.textSecondary
                );

        row.addView(time);

        card.addView(row);

        root.addView(card);

        return new NotificationHolder(
                root,
                card,
                icon,
                title,
                description,
                time,
                saveButton,
                deleteButton
        );
    }

    @Override
    public void onBindViewHolder(
            @NonNull RecyclerView.ViewHolder holder,
            int position
    ) {

        Row row =
                rows.get(position);

        if (holder instanceof HeaderHolder) {

            ((HeaderHolder) holder)
                    .textView
                    .setText(row.header);

            return;
        }

        NotificationHolder h =
                (NotificationHolder) holder;

        GardenNotification value =
                row.notification;

        /*
         * Recycle edilen kart açık kalmasın.
         */
        h.foreground.setTranslationX(0f);

        h.foreground.setCardBackgroundColor(
                context.getColor(
                        value.isRead()
                                ? R.color.card
                                : R.color.surfaceGreen
                )
        );

        h.icon.setImageResource(
                iconResource(value)
        );

        h.icon.setImageTintList(
                ColorStateList.valueOf(
                        context.getColor(
                                iconColor(value)
                        )
                )
        );

        h.title.setText(
                value.getTitle()
        );

        h.description.setText(
                value.getDescription()
        );

        h.time.setText(
                new SimpleDateFormat(
                        "HH:mm",
                        Locale.getDefault()
                ).format(
                        new Date(
                                value.getCreated_at_epoch()
                                        * 1000L
                        )
                )
        );

        h.saveButton.setText(
                value.isSaved()
                        ? context.getString(R.string.notification_action_remove_saved)
                        : context.getString(R.string.notification_action_save)
        );

        /*
         * Kart açıkken karta dokunulursa
         * önce kapatılır.
         */
        h.foreground.setOnClickListener(view -> {

            if (h.foreground.getTranslationX() != 0f) {

                h.foreground
                        .animate()
                        .translationX(0f)
                        .setDuration(160)
                        .start();

                return;
            }

            if (listener != null) {

                listener.onNotificationClick(
                        value
                );
            }
        });

        h.saveButton.setOnClickListener(view -> {

            h.foreground
                    .animate()
                    .translationX(0f)
                    .setDuration(160)
                    .start();

            if (listener != null) {

                listener.onSaveClick(
                        value
                );
            }
        });

        h.deleteButton.setOnClickListener(view -> {

            h.foreground
                    .animate()
                    .translationX(0f)
                    .setDuration(160)
                    .start();

            if (listener != null) {

                listener.onDeleteClick(
                        value
                );
            }
        });
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    private TextView text(
            int size,
            int color
    ) {

        TextView view =
                new TextView(context);

        view.setTextSize(size);

        view.setTextColor(
                context.getColor(color)
        );

        return view;
    }

    private String dayLabel(long epoch) {

        java.util.Calendar now =
                java.util.Calendar.getInstance();

        java.util.Calendar notification =
                java.util.Calendar.getInstance();

        notification.setTimeInMillis(
                epoch * 1000L
        );

        if (now.get(java.util.Calendar.YEAR)
                == notification.get(java.util.Calendar.YEAR)
                && now.get(java.util.Calendar.DAY_OF_YEAR)
                == notification.get(java.util.Calendar.DAY_OF_YEAR)) {

            return context.getString(R.string.notification_center_today);
        }

        now.add(
                java.util.Calendar.DAY_OF_YEAR,
                -1
        );

        if (now.get(java.util.Calendar.YEAR)
                == notification.get(java.util.Calendar.YEAR)
                && now.get(java.util.Calendar.DAY_OF_YEAR)
                == notification.get(java.util.Calendar.DAY_OF_YEAR)) {

            return context.getString(R.string.notification_center_yesterday);
        }

        return new SimpleDateFormat(
                "dd MMMM yyyy",
                Locale.getDefault()
        ).format(
                new Date(epoch * 1000L)
        );
    }

    private int iconResource(GardenNotification value) {
        if (value == null) return R.drawable.ic_notification_generic_24;
        String type = normalized(value.getType());
        String sourceKey = normalized(value.getSource_key());

        if ("DEVICE".equals(type)) {
            if (sourceKey.startsWith("DEVICE-RECOVERED:")) {
                return R.drawable.ic_device_connected_24;
            }
            if (sourceKey.startsWith("DEVICE-OFFLINE:")) {
                return R.drawable.ic_device_disconnected_24;
            }
            if (sourceKey.startsWith("DEVICE-ERROR:SENSOR:")) {
                return R.drawable.ic_sensor_warning_24;
            }
            return R.drawable.ic_device_24;
        }
        if ("IRRIGATION".equals(type)) return R.drawable.ic_water_drop_24;
        if ("ACCESS".equals(type)) return R.drawable.ic_nas_security_shield_24;
        if ("FERTILIZATION".equals(type)) return R.drawable.ic_fertilization_24;
        if ("STOCK".equals(type)) return R.drawable.ic_stock_warning_24;
        if ("PHOTO_FOLLOW_UP".equals(type)) return R.drawable.ic_photo_follow_up_24;
        if ("PLANT".equals(type) || "PLANT_ASSISTANT".equals(type)) return R.drawable.ic_plant_assistant_24;
        if ("SEEDLING".equals(type) || "SEEDLING_ASSISTANT".equals(type)) {
            return R.drawable.ic_seedling_plant_type_24;
        }
        if ("WEATHER".equals(type)) {
            return sourceKey.startsWith("WEATHER:HEAT:")
                    ? R.drawable.ic_weather_sunny_24
                    : R.drawable.ic_weather_cloud_24;
        }
        return R.drawable.ic_notification_generic_24;
    }

    private int iconColor(GardenNotification value) {
        if (value == null) return R.color.primary;
        String type = normalized(value.getType());
        String sourceKey = normalized(value.getSource_key());

        if ("DEVICE".equals(type)) {
            if (sourceKey.startsWith("DEVICE-RECOVERED:")) return R.color.primary;
            if (sourceKey.startsWith("DEVICE-OFFLINE:")) return R.color.error;
            if (sourceKey.startsWith("DEVICE-ERROR:")) return R.color.warning;
        }
        if ("STOCK".equals(type)) return R.color.warning;
        if (("SEEDLING".equals(type) || "SEEDLING_ASSISTANT".equals(type))
                && "HIGH".equals(normalized(value.getPriority()))) return R.color.error;
        if ("WEATHER".equals(type) && sourceKey.startsWith("WEATHER:RAIN:")) {
            return R.color.warning;
        }
        return R.color.primary;
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
    private int dp(int value) {

        return Math.round(
                value
                        * context
                        .getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    private static final class Row {

        final String header;

        final GardenNotification notification;

        private Row(
                String header,
                GardenNotification notification
        ) {

            this.header = header;
            this.notification = notification;
        }

        static Row header(
                String title
        ) {

            return new Row(
                    title,
                    null
            );
        }

        static Row notification(
                GardenNotification value
        ) {

            return new Row(
                    null,
                    value
            );
        }
    }

    private static final class HeaderHolder
            extends RecyclerView.ViewHolder {

        final TextView textView;

        HeaderHolder(
                TextView view
        ) {

            super(view);

            textView = view;
        }
    }

    private static final class NotificationHolder
            extends RecyclerView.ViewHolder {

        final MaterialCardView foreground;
        final AppCompatImageView icon;
        final TextView title;
        final TextView description;
        final TextView time;
        final MaterialButton saveButton;
        final MaterialButton deleteButton;

        NotificationHolder(
                View root,
                MaterialCardView foreground,
                AppCompatImageView icon,
                TextView title,
                TextView description,
                TextView time,
                MaterialButton saveButton,
                MaterialButton deleteButton
        ) {

            super(root);

            this.foreground = foreground;

            this.icon = icon;
            this.title = title;
            this.description = description;
            this.time = time;

            this.saveButton = saveButton;
            this.deleteButton = deleteButton;
        }
    }
}
