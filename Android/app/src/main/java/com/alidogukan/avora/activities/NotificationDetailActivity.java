package com.alidogukan.avora.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.view.View;

import androidx.appcompat.widget.PopupMenu;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.models.GardenNotification;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.SeedlingBatch;
import com.alidogukan.avora.notifications.NotificationActionRouter;
import com.alidogukan.avora.ui.PrimaryBottomNavigation;
import com.alidogukan.avora.viewmodels.NotificationCenterViewModel;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Displays one durable AVORA notification and its read/saved state. */
public class NotificationDetailActivity extends EdgeToEdgeActivity {

    private static final String TYPE_IRRIGATION = "IRRIGATION";
    private static final String TYPE_FERTILIZATION = "FERTILIZATION";
    private static final String TYPE_STOCK = "STOCK";
    private static final String TYPE_PHOTO = "PHOTO_FOLLOW_UP";
    private static final String TYPE_PLANT = "PLANT";
    private static final String TYPE_PLANT_ASSISTANT = "PLANT_ASSISTANT";
    private static final String TYPE_WEATHER = "WEATHER";
    private static final String TYPE_DEVICE = "DEVICE";
    private static final String TYPE_SEEDLING = "SEEDLING";
    private static final String TYPE_SEEDLING_ASSISTANT = "SEEDLING_ASSISTANT";

    private static final String PRIORITY_HIGH = "HIGH";
    private static final String PRIORITY_LOW = "LOW";

    private GardenNotification value;
    private NotificationCenterViewModel viewModel;
    private TextView zoneView;
    private TextView zoneLabelView;

    @Override
    public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_notification_detail);

        viewModel = new ViewModelProvider(this).get(NotificationCenterViewModel.class);

        GardenNotification intentValue =
                notificationFromIntent();

        GardenNotification currentValue =
                viewModel.find(intentValue.getId());

        value = currentValue != null
                ? currentValue
                : intentValue;
        bindViews();
        renderNotification();
        observeZoneName();
        configureActions();

        viewModel.setState(value, true, value.isSaved());
        value.setRead(true);
        PrimaryBottomNavigation.bind(this, PrimaryBottomNavigation.NOTIFICATIONS);
    }

    private GardenNotification notificationFromIntent() {

        GardenNotification notification =
                new GardenNotification();

        notification.setId(
                safe(getIntent().getStringExtra("id"))
        );

        notification.setType(
                safe(getIntent().getStringExtra("type"))
        );

        notification.setPriority(
                safe(getIntent().getStringExtra("priority"))
        );

        notification.setZone_id(
                safe(getIntent().getStringExtra("zone_id"))
        );

        notification.setTitle(
                safe(getIntent().getStringExtra("title"))
        );

        notification.setDescription(
                safe(getIntent().getStringExtra("description"))
        );

        notification.setSource_key(
                safe(getIntent().getStringExtra("source_key"))
        );

        notification.setCreated_at_epoch(
                getIntent().getLongExtra(
                        "created_at_epoch",
                        0L
                )
        );

        notification.setRead(
                getIntent().getBooleanExtra(
                        "read",
                        false
                )
        );

        notification.setSaved(
                getIntent().getBooleanExtra(
                        "saved",
                        false
                )
        );

        return notification;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private void bindViews() {
        zoneView = findViewById(R.id.txtNotificationDetailZone);
        zoneLabelView = findViewById(R.id.txtNotificationDetailZoneLabel);
    }

    private void renderNotification() {
        ((TextView) findViewById(R.id.txtNotificationDetailIcon)).setText(icon(value.getType()));
        ((TextView) findViewById(R.id.txtNotificationDetailTitle)).setText(value.getTitle());
        ((TextView) findViewById(R.id.txtNotificationDetailTime)).setText(formatDate(value.getCreated_at_epoch()));
        ((TextView) findViewById(R.id.txtNotificationDetailCategory)).setText(categoryLabel(value.getType()));

        TextView priorityView = findViewById(R.id.txtNotificationDetailPriority);
        priorityView.setText(priorityLabel(value.getPriority()));
        priorityView.setTextColor(getColor(priorityColor(value.getPriority())));

        boolean seedling = isSeedlingNotification();
        zoneLabelView.setText(seedling
                ? R.string.notification_detail_seedling_batch
                : R.string.notification_detail_zone);
        zoneView.setText(value.getZone_id().isBlank()
                ? (seedling ? R.string.notification_detail_all_seedling_batches
                        : R.string.notification_detail_general)
                : (seedling ? R.string.notification_detail_seedling_loading
                        : R.string.notification_detail_zone_loading));
        ((TextView) findViewById(R.id.txtNotificationDetailDescription)).setText(value.getDescription());
    }

    private void observeZoneName() {
        String zoneId = value.getZone_id();
        if (zoneId.isBlank()) return;

        if (isSeedlingNotification()) {
            viewModel.getSeedlingBatch(zoneId).observe(this, batch -> {
                if (batch == null) {
                    zoneView.setText(R.string.notification_detail_seedling_missing);
                    return;
                }
                String emoji = batch.getEmoji().isBlank() ? "🌱" : batch.getEmoji();
                zoneView.setText((emoji + " " + batch.displayName()).trim());
            });
            return;
        }

        viewModel.getZones().observe(this, zones -> {
            GardenZone zone = findZone(zones, zoneId);
            if (zone == null) {
                zoneView.setText(readableZoneFallback(zoneId));
                return;
            }
            String name = com.alidogukan.avora.zones.PhysicalZoneIdentity.name(zone);
            String emoji = zone.getEmoji() == null ? "" : zone.getEmoji().trim();
            String label = (emoji + " " + name).trim();
            zoneView.setText(label.isBlank() ? readableZoneFallback(zoneId) : label);
        });
    }

    private GardenZone findZone(List<GardenZone> zones, String zoneId) {
        if (zones == null) return null;
        for (GardenZone zone : zones) {
            if (zone != null && zoneId.equals(zone.getZone_id())) return zone;
        }
        return null;
    }

    private String readableZoneFallback(String zoneId) {
        String digits = zoneId.replaceAll("\\D+", "");
        if (!digits.isBlank()) {
            try {
                return getString(R.string.notification_detail_zone_number, Integer.parseInt(digits));
            } catch (NumberFormatException ignored) {
                // Use the general label below when a legacy id cannot be parsed.
            }
        }
        return getString(R.string.notification_detail_general);
    }

    private void configureActions() {
        findViewById(R.id.btnNotificationDetailBack)
                .setOnClickListener(view -> finish());

        findViewById(R.id.btnNotificationDetailMenu)
                .setOnClickListener(this::showNotificationMenu);

        MaterialButton actionButton = findViewById(R.id.btnNotificationDetailAction);
        Intent action = NotificationActionRouter.createIntent(this, value);
        int label = NotificationActionRouter.actionLabel(value);
        boolean visible = action != null && label != 0;
        actionButton.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            actionButton.setText(label);
            actionButton.setOnClickListener(view -> openRelatedAction());
        }
    }

    private boolean openRelatedAction() {
        Intent action = NotificationActionRouter.createIntent(this, value);
        if (action == null) return false;
        startActivity(action);
        return true;
    }

    private void showNotificationMenu(View anchor) {
        PopupMenu popupMenu = new PopupMenu(this, anchor);

        String title = value.isSaved()
                ? getString(R.string.notification_action_remove_saved)
                : getString(R.string.notification_action_save);

        popupMenu.getMenu().add(title);

        popupMenu.setOnMenuItemClickListener(item -> {
            value.setSaved(!value.isSaved());

            viewModel.setState(
                    value,
                    true,
                    value.isSaved()
            );

            return true;
        });

        popupMenu.show();
    }

    private String formatDate(long epochSeconds) {
        long timestamp = epochSeconds > 0L ? epochSeconds * 1000L : System.currentTimeMillis();
        return new SimpleDateFormat("dd MMMM yyyy · HH:mm", Locale.getDefault())
                .format(new Date(timestamp));
    }

    private String categoryLabel(String type) {
        if (TYPE_IRRIGATION.equalsIgnoreCase(type)) return getString(R.string.notification_category_irrigation);
        if (TYPE_FERTILIZATION.equalsIgnoreCase(type)) return getString(R.string.notification_category_fertilization);
        if (TYPE_STOCK.equalsIgnoreCase(type)) return getString(R.string.notification_category_stock);
        if (TYPE_PHOTO.equalsIgnoreCase(type)) return getString(R.string.notification_category_photo);
        if (TYPE_PLANT.equalsIgnoreCase(type) || TYPE_PLANT_ASSISTANT.equalsIgnoreCase(type)) return getString(R.string.notification_category_plant_assistant);
        if (TYPE_WEATHER.equalsIgnoreCase(type)) return getString(R.string.notification_category_weather);
        if (TYPE_DEVICE.equalsIgnoreCase(type)) return getString(R.string.notification_category_device);
        if (TYPE_SEEDLING.equalsIgnoreCase(type)
                || TYPE_SEEDLING_ASSISTANT.equalsIgnoreCase(type)) {
            return getString(R.string.notification_category_seedling);
        }
        return getString(R.string.notification_category_system);
    }

    private String priorityLabel(String priority) {
        if (PRIORITY_HIGH.equalsIgnoreCase(priority)) return getString(R.string.notification_priority_high);
        if (PRIORITY_LOW.equalsIgnoreCase(priority)) return getString(R.string.notification_priority_low);
        return getString(R.string.notification_priority_normal);
    }

    private int priorityColor(String priority) {
        if (PRIORITY_HIGH.equalsIgnoreCase(priority)) return R.color.error;
        if (PRIORITY_LOW.equalsIgnoreCase(priority)) return R.color.primary;
        return R.color.warning;
    }

    private String icon(String type) {
        if (TYPE_IRRIGATION.equalsIgnoreCase(type)) return getString(R.string.symbol_water_drop);
        if (TYPE_FERTILIZATION.equalsIgnoreCase(type)) return getString(R.string.symbol_plant);
        if (TYPE_STOCK.equalsIgnoreCase(type)) return getString(R.string.symbol_warning);
        if (TYPE_PHOTO.equalsIgnoreCase(type)) return getString(R.string.symbol_camera);
        if (TYPE_PLANT.equalsIgnoreCase(type) || TYPE_PLANT_ASSISTANT.equalsIgnoreCase(type)) return getString(R.string.symbol_sparkle);
        if (TYPE_WEATHER.equalsIgnoreCase(type)) return getString(R.string.symbol_sun);
        if (TYPE_DEVICE.equalsIgnoreCase(type)) return getString(R.string.symbol_notification);
        if (TYPE_SEEDLING.equalsIgnoreCase(type)
                || TYPE_SEEDLING_ASSISTANT.equalsIgnoreCase(type)) return "🌱";
        return getString(R.string.symbol_bullet);
    }

    private boolean isSeedlingNotification() {
        return TYPE_SEEDLING.equalsIgnoreCase(value.getType())
                || TYPE_SEEDLING_ASSISTANT.equalsIgnoreCase(value.getType());
    }
}
