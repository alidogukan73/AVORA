package com.alidogukan.avora.activities;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.widget.TextView;
import android.graphics.drawable.ColorDrawable;
import android.widget.CheckBox;
import android.widget.PopupWindow;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.notifications.NotificationSwipeCallback;
import com.alidogukan.avora.R;
import com.alidogukan.avora.notifications.NotificationCenterAdapter;
import com.alidogukan.avora.models.GardenNotification;
import com.alidogukan.avora.ui.PrimaryBottomNavigation;
import com.alidogukan.avora.viewmodels.NotificationCenterViewModel;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Chronological AVORA notification center with state and category filters. */
public class NotificationCenterActivity extends EdgeToEdgeActivity {
    private static final String ALL = "ALL", SAVED = "SAVED", READ = "READ", UNREAD = "UNREAD";
    private NotificationCenterAdapter adapter;
    private TextView summary, empty;
    private NotificationCenterViewModel viewModel;
    private String statusFilter = ALL;
    private final Set<String> categoryFilters = new HashSet<>();
    private final BroadcastReceiver notificationChangedReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (viewModel != null) viewModel.refresh();
                }
            };
    private MaterialButton all, saved, read, unread, category;

    @Override public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_notification_center);

        viewModel = new ViewModelProvider(this).get(NotificationCenterViewModel.class);
        viewModel.getNotifications().observe(this, this::render);
        RecyclerView list = findViewById(R.id.listNotifications);

        adapter = new NotificationCenterAdapter(
                this,
                new NotificationCenterAdapter.Listener() {

                    @Override
                    public void onNotificationClick(
                            GardenNotification value
                    ) {

                        openDetail(value);
                    }

                    @Override
                    public void onSaveClick(
                            GardenNotification value
                    ) {

                        viewModel.setState(
                                value,
                                value.isRead(),
                                !value.isSaved()
                        );

                    }

                    @Override
                    public void onDeleteClick(
                            GardenNotification value
                    ) {

                        confirmDeleteNotification(
                                value
                        );
                    }
                }
        );

        list.setLayoutManager(
                new LinearLayoutManager(this)
        );

        list.setAdapter(adapter);
        NotificationSwipeCallback swipeCallback =
                new NotificationSwipeCallback(
                        adapter
                );

        new ItemTouchHelper(
                swipeCallback
        ).attachToRecyclerView(list);

        summary = findViewById(R.id.txtNotificationSummary);
        empty = findViewById(R.id.txtNotificationEmpty);
        findViewById(R.id.btnNotificationBack)
                .setOnClickListener(v -> finish());

        findViewById(R.id.btnNotificationSettings)
                .setOnClickListener(v ->
                        startActivity(
                                new Intent(
                                        this,
                                        NotificationSettingsActivity.class
                                )
                        )
                );

        findViewById(R.id.btnNotificationClear)
                .setOnClickListener(v -> showClearNotificationsDialog());
        all = findViewById(R.id.btnNotificationFilterAll);
        saved = findViewById(R.id.btnNotificationFilterSaved);
        read = findViewById(R.id.btnNotificationFilterRead);
        unread = findViewById(R.id.btnNotificationFilterUnread);
        category = findViewById(R.id.btnNotificationFilterCategory);
        all.setOnClickListener(v -> selectStatus(ALL));
        saved.setOnClickListener(v -> selectStatus(SAVED));
        read.setOnClickListener(v -> selectStatus(READ));
        unread.setOnClickListener(v -> selectStatus(UNREAD));
        category.setOnClickListener(v -> showCategoryFilter());
        PrimaryBottomNavigation.bind(this, PrimaryBottomNavigation.NOTIFICATIONS);
    }

    @Override
    protected void onStart() {
        super.onStart();

        ContextCompat.registerReceiver(
                this,
                notificationChangedReceiver,
                new IntentFilter(
                        NotificationCenterViewModel.ACTION_NOTIFICATIONS_CHANGED
                ),
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
    }

    @Override
    protected void onStop() {
        unregisterReceiver(notificationChangedReceiver);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.refresh();
    }

    private void render(List<GardenNotification> values) {

        List<GardenNotification> visible =
                new ArrayList<>();

        int unreadCount = 0;

        if (values != null) {

            for (GardenNotification value : values) {

                if (value == null) {
                    continue;
                }

                if (!matches(value)) {
                    continue;
                }

                visible.add(value);

                if (!value.isRead()) {
                    unreadCount++;
                }
            }
        }

        adapter.submitList(visible);

        int shown = visible.size();

        summary.setText(
                unreadCount == 0
                        ? getString(R.string.notification_center_summary_all_read, shown)
                        : getString(R.string.notification_center_summary_unread, shown, unreadCount)
        );

        empty.setVisibility(
                shown == 0
                        ? View.VISIBLE
                        : View.GONE
        );

        empty.setText(
                values == null || values.isEmpty()
                        ? getString(R.string.notification_center_empty)
                        : getString(R.string.notification_center_filtered_empty)
        );

        updateFilterButtons();
    }

    private boolean matches(GardenNotification value) {
        if (SAVED.equals(statusFilter) && !value.isSaved()) return false;
        if (READ.equals(statusFilter) && !value.isRead()) return false;
        if (UNREAD.equals(statusFilter) && value.isRead()) return false;
        return categoryFilters.isEmpty()
                || categoryFilters.contains(viewModel.categoryFor(value.getType()));
    }

    private void selectStatus(String value) {
        statusFilter = value;
        render(currentNotifications());
    }

    private void showClearNotificationsDialog() {
        List<GardenNotification> notifications = currentNotifications();
        int removableCount = 0;
        for (GardenNotification value : notifications) {
            if (value != null && !value.isSaved()) removableCount++;
        }

        if (removableCount == 0) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.notification_center_clear_title)
                    .setMessage(R.string.notification_center_clear_none)
                    .setPositiveButton(R.string.notification_center_action_ok, null)
                    .show();
            return;
        }

        int finalRemovableCount = removableCount;
        new AlertDialog.Builder(this)
                .setTitle(R.string.notification_center_clear_title)
                .setMessage(getString(R.string.notification_center_clear_confirmation, finalRemovableCount))
                .setNegativeButton(R.string.notification_center_action_cancel, null)
                .setPositiveButton(R.string.notification_center_action_clear, (dialog, which) ->
                        viewModel.clearUnsaved(result -> runOnUiThread(() -> {
                            if (result < 0) {
                                new AlertDialog.Builder(this)
                                        .setTitle(R.string.notification_center_clear_failed_title)
                                        .setMessage(R.string.notification_center_clear_failed_message)
                                        .setPositiveButton(R.string.notification_center_action_ok, null)
                                        .show();
                                return;
                            }
                            viewModel.refresh();
                            new AlertDialog.Builder(this)
                                    .setTitle(R.string.notification_center_clear_success_title)
                                    .setMessage(getString(R.string.notification_center_clear_success_message, result))
                                    .setPositiveButton(R.string.notification_center_action_ok, null)
                                    .show();
                        })))
                .show();
    }
    private void showCategoryFilter() {

        final int popupWidth = dp(240);

        LinearLayout container =
                new LinearLayout(this);

        container.setOrientation(
                LinearLayout.VERTICAL
        );

        container.setPadding(
                dp(8),
                dp(8),
                dp(8),
                dp(8)
        );

        container.setBackgroundColor(
                getColor(R.color.card)
        );

        PopupWindow popup =
                new PopupWindow(
                        container,
                        popupWidth,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        true
                );

        popup.setBackgroundDrawable(
                new ColorDrawable(
                        getColor(R.color.card)
                )
        );

        popup.setOutsideTouchable(true);
        popup.setElevation(dp(8));

        /*
         * Tümü:
         * seçildiğinde kategori filtresini tamamen temizler.
         */
        CheckBox all =
                createCategoryCheckBox(
                        getString(R.string.notification_center_filter_all),
                        categoryFilters.isEmpty()
                );

        container.addView(all);

        String[] labels = {
                getString(R.string.notification_center_category_irrigation),
                getString(R.string.notification_center_category_fertilization),
                getString(R.string.notification_center_category_plant_assistant),
                getString(R.string.notification_center_category_weather),
                getString(R.string.notification_center_category_device),
                getString(R.string.notification_center_category_stock)
        };

        String[] keys = {
                "irrigation",
                "fertilization",
                "plant",
                "weather",
                "device",
                "stock"
        };

        CheckBox[] boxes =
                new CheckBox[keys.length];

        for (int i = 0; i < keys.length; i++) {

            final String key =
                    keys[i];

            CheckBox box =
                    createCategoryCheckBox(
                            labels[i],
                            categoryFilters.contains(key)
                    );

            boxes[i] = box;

            box.setOnClickListener(view -> {

                if (box.isChecked()) {

                    categoryFilters.add(key);

                } else {

                    categoryFilters.remove(key);
                }

                /*
                 * Herhangi bir özel kategori seçiliyse
                 * Tümü işaretli olamaz.
                 */
                all.setChecked(
                        categoryFilters.isEmpty()
                );

                render(
                        currentNotifications()
                );
            });

            container.addView(box);
        }

        all.setOnClickListener(view -> {

            /*
             * Tümü = kategori sınırlaması yok.
             */
            categoryFilters.clear();

            for (CheckBox box : boxes) {
                box.setChecked(false);
            }

            all.setChecked(true);

            render(
                    currentNotifications()
            );
        });

        /*
         * Popup'ın sağ kenarı Kategori butonunun
         * sağ kenarıyla aynı hizada olsun.
         */
        int xOffset =
                category.getWidth()
                        - popupWidth;

        popup.showAsDropDown(
                category,
                xOffset,
                dp(4)
        );
    }

    private CheckBox createCategoryCheckBox(
            String text,
            boolean checked
    ) {

        CheckBox box =
                new CheckBox(this);

        box.setText(text);
        box.setChecked(checked);

        box.setTextColor(
                getColor(R.color.textPrimary)
        );

        box.setTextSize(14);

        box.setGravity(
                android.view.Gravity.CENTER_VERTICAL
        );

        box.setButtonTintList(
                android.content.res.ColorStateList.valueOf(
                        getColor(R.color.primary)
                )
        );

        box.setPadding(
                dp(10),
                0,
                dp(10),
                0
        );

        box.setMinHeight(
                dp(44)
        );

        box.setLayoutParams(
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(44)
                )
        );

        return box;
    }

    private void updateFilterButtons() {
        decorate(all, ALL.equals(statusFilter));
        decorate(saved, SAVED.equals(statusFilter));
        decorate(read, READ.equals(statusFilter));
        decorate(unread, UNREAD.equals(statusFilter));
        category.setText(
                categoryFilters.isEmpty()
                        ? getString(R.string.notification_center_filter_category_menu)
                        : getString(R.string.notification_center_filter_category_count, categoryFilters.size())
        );
        decorate(category, !categoryFilters.isEmpty());
    }

    private void decorate(MaterialButton button, boolean active) {
        button.setBackgroundTintList(ColorStateList.valueOf(getColor(active ? R.color.surfaceGreen : R.color.card)));
        button.setStrokeColor(ColorStateList.valueOf(getColor(active ? R.color.primary : R.color.border)));
        button.setTextColor(getColor(active ? R.color.primary : R.color.textPrimary));
    }

    private void openDetail(GardenNotification value) {
        String applicationId = viewModel.fertilizerApplicationId(value.getSource_key());
        if (!applicationId.isBlank()) {
            viewModel.setState(value, true, value.isSaved());
            startActivity(new Intent(this, FertilizerHistoryActivity.class)
                    .putExtra("outcome_application_id", applicationId)
                    .putExtra("zone_id", value.getZone_id()));
            return;
        }
        Intent intent = new Intent(this, NotificationDetailActivity.class);
        intent.putExtra("id", value.getId()).putExtra("type", value.getType()).putExtra("priority", value.getPriority())
                .putExtra("zone_id", value.getZone_id()).putExtra("title", value.getTitle()).putExtra("description", value.getDescription())
                .putExtra("source_key", value.getSource_key())
                .putExtra("created_at_epoch", value.getCreated_at_epoch()).putExtra("read", value.isRead()).putExtra("saved", value.isSaved());
        startActivity(intent);
    }

    private void confirmDeleteNotification(
            GardenNotification value
    ) {

        new AlertDialog.Builder(this)
                .setTitle(R.string.notification_center_delete_title)
                .setMessage(R.string.notification_center_delete_message)
                .setNegativeButton(
                        getString(R.string.notification_center_action_cancel),
                        null
                )
                .setPositiveButton(
                        getString(R.string.notification_center_action_delete),
                        (dialog, which) ->

                                viewModel.delete(
                                        value,
                                        success ->

                                                runOnUiThread(() -> {

                                                    if (!success) {

                                                        new AlertDialog.Builder(this)
                                                                .setTitle(R.string.notification_center_clear_failed_title)
                                                                .setMessage(R.string.notification_center_delete_failed_message)
                                                                .setPositiveButton(
                                                                        getString(R.string.notification_center_action_ok),
                                                                        null
                                                                )
                                                                .show();

                                                        return;
                                                    }

                                                    viewModel.refresh();
                                                })
                                )
                )
                .show();
    }

    private List<GardenNotification> currentNotifications() {
        List<GardenNotification> values = viewModel.getNotifications().getValue();
        return values == null ? new ArrayList<>() : values;
    }

    private int dp(int value) {

        return Math.round(
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}
