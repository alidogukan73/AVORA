package com.alidogukan.avora.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.ui.PrimaryBottomNavigation;
import com.alidogukan.avora.viewmodels.GardenSettingsViewModel;
import com.alidogukan.avora.viewmodels.SuperadminDataViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Central settings dashboard. Detailed screens own and save their own values. */
public class SettingsHubActivity extends AppCompatActivity {

    private static final int QUICK_ACTION_COUNT = 4;
    private static final List<String> DEFAULT_QUICK_ACTIONS = Arrays.asList(
            "irrigation", "plants", "notifications", "weather");

    private LinearLayout quickActions;
    private LinearLayout sections;
    private GardenSettingsViewModel viewModel;
    private SuperadminDataViewModel superadminViewModel;
    private boolean superadminVisible;

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings_hub);
        viewModel = new ViewModelProvider(this).get(GardenSettingsViewModel.class);
        superadminViewModel = new ViewModelProvider(this)
                .get(SuperadminDataViewModel.class);
        applyWindowInsets();

        quickActions = findViewById(R.id.layoutSettingsQuickActions);
        sections = findViewById(R.id.layoutSettingsSections);

        findViewById(R.id.btnSettingsToolbarBack).setOnClickListener(view -> finish());
        View toolbarAction = findViewById(R.id.btnSettingsToolbarAction);
        toolbarAction.setVisibility(View.VISIBLE);
        toolbarAction.setOnClickListener(view -> showQuickSettingsEditor());

        PrimaryBottomNavigation.bind(this, PrimaryBottomNavigation.SETTINGS);
        buildQuickActions();
        buildSections();
        superadminViewModel.isCurrentUserOwner()
                .addOnSuccessListener(owner -> {
                    if (isFinishing() || isDestroyed()
                            || superadminVisible == Boolean.TRUE.equals(owner)) return;
                    superadminVisible = Boolean.TRUE.equals(owner);
                    buildSections();
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (quickActions != null) {
            buildQuickActions();
        }
    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settingsHubRoot),
                (view, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });
    }

    private void buildQuickActions() {
        quickActions.removeAllViews();
        for (String id : loadQuickActionIds()) {
            QuickAction action = quickActionFor(id);
            if (action != null) {
                addQuickAction(action.icon, action.title, action.action);
            }
        }
    }

    private void showQuickSettingsEditor() {
        List<String> workingOrder = orderedEditorIds();
        Set<String> selected = new LinkedHashSet<>(loadQuickActionIds());
        View content = LayoutInflater.from(this)
                .inflate(R.layout.dialog_quick_settings_content, null, false);
        LinearLayout editorItems = content.findViewById(R.id.layoutQuickSettingsEditorItems);
        TextView selectionCount = content.findViewById(R.id.txtQuickSettingsSelectionCount);
        MaterialButton restoreDefaults = content.findViewById(
                R.id.btnQuickSettingsRestoreDefaults);
        MaterialButton cancel = content.findViewById(R.id.btnQuickSettingsCancel);
        MaterialButton save = content.findViewById(R.id.btnQuickSettingsSave);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_quick_editor_title)
                .setMessage(R.string.settings_quick_editor_message)
                .setView(content)
                .create();

        Runnable refresh = () -> renderQuickSettingsEditor(
                editorItems, selectionCount, workingOrder, selected);
        content.setTag(R.id.layoutQuickSettingsEditorItems, refresh);
        refresh.run();

        save.setOnClickListener(view -> {
            if (selected.size() != QUICK_ACTION_COUNT) {
                Toast.makeText(this,
                        getString(R.string.settings_quick_exact_count, QUICK_ACTION_COUNT),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            List<String> savedOrder = new ArrayList<>();
            for (String id : workingOrder) {
                if (selected.contains(id)) {
                    savedOrder.add(id);
                }
            }
            saveQuickActionIds(savedOrder);
            buildQuickActions();
            Toast.makeText(this, R.string.settings_quick_saved, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        restoreDefaults.setOnClickListener(view -> {
            workingOrder.clear();
            workingOrder.addAll(allQuickActionIds());
            selected.clear();
            selected.addAll(DEFAULT_QUICK_ACTIONS);
            normalizeEditorOrder(workingOrder, selected);
            refresh.run();
        });
        cancel.setOnClickListener(view -> dialog.dismiss());
        dialog.show();
    }

    private void renderQuickSettingsEditor(LinearLayout container, TextView countView,
                                           List<String> order, Set<String> selected) {
        container.removeAllViews();
        countView.setText(getString(R.string.settings_quick_selected_count,
                selected.size(), QUICK_ACTION_COUNT));
        List<String> selectedOrder = selectedIdsInOrder(order, selected);

        for (String id : order) {
            QuickAction action = quickActionFor(id);
            if (action == null) {
                continue;
            }
            View row = LayoutInflater.from(this)
                    .inflate(R.layout.item_quick_settings_editor, container, false);
            CheckBox checkBox = row.findViewById(R.id.checkQuickSetting);
            TextView title = row.findViewById(R.id.txtQuickSettingEditorTitle);
            ImageButton moveUp = row.findViewById(R.id.btnQuickSettingUp);
            ImageButton moveDown = row.findViewById(R.id.btnQuickSettingDown);
            boolean isSelected = selected.contains(id);
            int selectedIndex = selectedOrder.indexOf(id);

            title.setText(getString(action.title).replace('\n', ' '));
            checkBox.setChecked(isSelected);
            moveUp.setVisibility(isSelected ? View.VISIBLE : View.INVISIBLE);
            moveDown.setVisibility(isSelected ? View.VISIBLE : View.INVISIBLE);
            moveUp.setEnabled(selectedIndex > 0);
            moveDown.setEnabled(selectedIndex >= 0 && selectedIndex < selectedOrder.size() - 1);
            moveUp.setAlpha(moveUp.isEnabled() ? 1f : 0.3f);
            moveDown.setAlpha(moveDown.isEnabled() ? 1f : 0.3f);

            checkBox.setOnClickListener(view -> {
                if (checkBox.isChecked()) {
                    if (selected.size() >= QUICK_ACTION_COUNT) {
                        checkBox.setChecked(false);
                        Toast.makeText(this,
                                getString(R.string.settings_quick_max_count, QUICK_ACTION_COUNT),
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    selected.add(id);
                } else {
                    selected.remove(id);
                }
                normalizeEditorOrder(order, selected);
                editorRefresh(container);
            });
            row.setOnClickListener(view -> checkBox.performClick());
            moveUp.setOnClickListener(view -> {
                moveSelected(order, selected, id, -1);
                editorRefresh(container);
            });
            moveDown.setOnClickListener(view -> {
                moveSelected(order, selected, id, 1);
                editorRefresh(container);
            });
            container.addView(row);
        }
    }

    private void editorRefresh(View container) {
        View content = container;
        while (content != null && content.getTag(R.id.layoutQuickSettingsEditorItems) == null) {
            if (!(content.getParent() instanceof View)) {
                return;
            }
            content = (View) content.getParent();
        }
        if (content != null) {
            Object callback = content.getTag(R.id.layoutQuickSettingsEditorItems);
            if (callback instanceof Runnable) {
                ((Runnable) callback).run();
            }
        }
    }

    private void moveSelected(List<String> order, Set<String> selected,
                              String id, int direction) {
        List<String> selectedOrder = selectedIdsInOrder(order, selected);
        int current = selectedOrder.indexOf(id);
        int target = current + direction;
        if (current < 0 || target < 0 || target >= selectedOrder.size()) {
            return;
        }
        String other = selectedOrder.get(target);
        int firstPosition = order.indexOf(id);
        int secondPosition = order.indexOf(other);
        order.set(firstPosition, other);
        order.set(secondPosition, id);
        normalizeEditorOrder(order, selected);
    }

    private void normalizeEditorOrder(List<String> order, Set<String> selected) {
        List<String> normalized = new ArrayList<>();
        for (String id : order) {
            if (selected.contains(id)) {
                normalized.add(id);
            }
        }
        for (String id : order) {
            if (!selected.contains(id)) {
                normalized.add(id);
            }
        }
        order.clear();
        order.addAll(normalized);
    }

    private List<String> selectedIdsInOrder(List<String> order, Set<String> selected) {
        List<String> result = new ArrayList<>();
        for (String id : order) {
            if (selected.contains(id)) {
                result.add(id);
            }
        }
        return result;
    }

    private List<String> orderedEditorIds() {
        List<String> result = new ArrayList<>(loadQuickActionIds());
        for (String id : allQuickActionIds()) {
            if (!result.contains(id)) {
                result.add(id);
            }
        }
        return result;
    }

    private List<String> loadQuickActionIds() {
        return viewModel.loadQuickActionIds(
                DEFAULT_QUICK_ACTIONS, allQuickActionIds(), QUICK_ACTION_COUNT);
    }

    private void saveQuickActionIds(List<String> ids) {
        viewModel.saveQuickActionIds(ids);
    }

    private List<String> allQuickActionIds() {
        return Arrays.asList(
                "garden", "plants", "sensor", "crop_catalog", "units",
                "irrigation", "fertilization", "weather",
                "notifications", "reminders",
                "device", "nas_security", "sync", "backup", "theme", "language",
                "help", "feedback", "about");
    }

    @Nullable
    private QuickAction quickActionFor(String id) {
        switch (id) {
            case "garden":
                return new QuickAction(R.drawable.ic_home_garden_plan_24,
                        R.string.settings_garden_info_title,
                        () -> open(GardenInfoActivity.class));
            case "irrigation":
                return new QuickAction(R.drawable.ic_water_drop_24,
                        R.string.settings_quick_irrigation,
                        () -> open(IrrigationSettingsActivity.class));
            case "plants":
                return new QuickAction(R.drawable.ic_leaf_24,
                        R.string.settings_quick_plants,
                        () -> open(ZoneManagementActivity.class));
            case "sensor":
                return new QuickAction(R.drawable.ic_device_health_24,
                        R.string.settings_sensor_settings_title,
                        () -> open(SensorSettingsActivity.class));
            case "crop_catalog":
                return new QuickAction(R.drawable.ic_leaf_24,
                        R.string.settings_crop_catalog_title,
                        () -> open(CropCatalogActivity.class));
            case "units":
                return new QuickAction(R.drawable.ic_settings_24,
                        R.string.settings_units_title,
                        () -> open(UnitsSettingsActivity.class));
            case "notifications":
                return new QuickAction(R.drawable.ic_header_notification,
                        R.string.settings_quick_notifications,
                        () -> open(NotificationSettingsActivity.class));
            case "reminders":
                return new QuickAction(R.drawable.ic_history_24,
                        R.string.settings_reminders_title,
                        () -> open(ReminderSettingsActivity.class));
            case "weather":
                return new QuickAction(R.drawable.ic_weather_cloud_24,
                        R.string.settings_quick_weather,
                        () -> open(RainSettingsActivity.class));
            case "fertilization":
                return new QuickAction(R.drawable.ic_leaf_24,
                        R.string.settings_quick_fertilization,
                        () -> open(FertilizationSettingsActivity.class));
            case "device":
                return new QuickAction(R.drawable.ic_device_health_24,
                        R.string.settings_quick_device,
                        () -> open(DeviceInfoActivity.class));
            case "nas_security":
                return new QuickAction(R.drawable.ic_nas_security_shield_24,
                        R.string.settings_nas_account_title,
                        () -> open(NasSecurityActivity.class));
            case "sync":
                return new QuickAction(R.drawable.ic_restart,
                        R.string.settings_quick_data_sync,
                        () -> open(DataSyncActivity.class));
            case "backup":
                return new QuickAction(R.drawable.ic_history_24,
                        R.string.settings_backup_title,
                        () -> open(BackupActivity.class));
            case "theme":
                return new QuickAction(R.drawable.ic_palette_24,
                        R.string.settings_theme_title,
                        () -> open(ThemeSettingsActivity.class));
            case "language":
                return new QuickAction(R.drawable.ic_language_24,
                        R.string.settings_language_title,
                        () -> open(LanguageSettingsActivity.class));
            case "help":
                return new QuickAction(R.drawable.ic_help_24,
                        R.string.settings_help_title,
                        () -> open(HelpCenterActivity.class));
            case "feedback":
                return new QuickAction(R.drawable.ic_feedback_24,
                        R.string.settings_feedback_title,
                        () -> open(FeedbackActivity.class));
            case "about":
                return new QuickAction(R.drawable.ic_info_outline_20,
                        R.string.settings_quick_about,
                        () -> open(AboutActivity.class));
            default:
                return null;
        }
    }

    private void buildSections() {
        sections.removeAllViews();
        addSection(R.string.settings_category_garden_profile,
                item(R.drawable.ic_leaf_24, R.string.settings_garden_info_title,
                        R.string.settings_garden_info_subtitle,
                        () -> open(GardenInfoActivity.class)),
                item(R.drawable.ic_nav_plants, R.string.settings_plants_regions_title,
                        R.string.settings_plants_regions_subtitle,
                        () -> open(ZoneManagementActivity.class)),
                item(R.drawable.ic_device_health_24,
                        R.string.settings_sensor_settings_title,
                        R.string.settings_sensor_settings_subtitle,
                        () -> open(SensorSettingsActivity.class)),
                item(R.drawable.ic_leaf_24, R.string.settings_crop_catalog_title,
                        R.string.settings_crop_catalog_subtitle,
                        () -> open(CropCatalogActivity.class)),
                item(R.drawable.ic_settings_24, R.string.settings_units_title,
                        R.string.settings_units_subtitle,
                        () -> open(UnitsSettingsActivity.class)));

        addSection(R.string.settings_category_irrigation_fertilization,
                item(R.drawable.ic_water_drop_24, R.string.settings_irrigation_menu_title,
                        R.string.settings_irrigation_menu_subtitle,
                        () -> open(IrrigationSettingsActivity.class)),
                item(R.drawable.ic_leaf_24, R.string.settings_fertilization_menu_title,
                        R.string.settings_fertilization_menu_subtitle,
                        () -> open(FertilizationSettingsActivity.class)),
                item(R.drawable.ic_weather_cloud_24, R.string.settings_rain_menu_title,
                        R.string.settings_rain_menu_subtitle,
                        () -> open(RainSettingsActivity.class)));

        addSection(R.string.settings_category_notifications,
                item(R.drawable.ic_header_notification,
                        R.string.settings_notification_preferences_title,
                        R.string.settings_notification_preferences_subtitle,
                        () -> open(NotificationSettingsActivity.class)),
                item(R.drawable.ic_history_24, R.string.settings_reminders_title,
                        R.string.settings_reminders_subtitle,
                        () -> open(ReminderSettingsActivity.class)));

        addSection(R.string.settings_category_device_application,
                item(R.drawable.ic_device_health_24, R.string.settings_device_info_title,
                        R.string.settings_device_info_subtitle,
                        () -> open(DeviceInfoActivity.class)),
                item(R.drawable.ic_nas_security_shield_24,
                        R.string.settings_nas_account_title,
                        R.string.settings_nas_account_subtitle,
                        () -> open(NasSecurityActivity.class)),
                item(R.drawable.ic_restart, R.string.settings_sync_title,
                        R.string.settings_sync_subtitle,
                        () -> open(DataSyncActivity.class)),
                item(R.drawable.ic_history_24, R.string.settings_backup_title,
                        R.string.settings_backup_subtitle,
                        () -> open(BackupActivity.class)),
                item(R.drawable.ic_palette_24, R.string.settings_theme_title,
                        R.string.settings_theme_subtitle,
                        () -> open(ThemeSettingsActivity.class)),
                item(R.drawable.ic_language_24, R.string.settings_language_title,
                        R.string.settings_language_subtitle,
                        () -> open(LanguageSettingsActivity.class)));

        if (superadminVisible) {
            addSection(R.string.settings_superadmin_category,
                    item(R.drawable.ic_feedback_24,
                            R.string.settings_feedback_inbox_title,
                            R.string.settings_feedback_inbox_subtitle,
                            this::openFeedbackInbox),
                    item(R.drawable.ic_nas_security_shield_24,
                            R.string.settings_superadmin_title,
                            R.string.settings_superadmin_subtitle,
                            () -> open(SuperadminDataActivity.class)));
        }

        addSection(R.string.settings_category_support_about,
                item(R.drawable.ic_help_24, R.string.settings_help_title,
                        R.string.settings_help_subtitle,
                        () -> open(HelpCenterActivity.class)),
                item(R.drawable.ic_feedback_24, R.string.settings_feedback_title,
                        R.string.settings_feedback_subtitle,
                        () -> open(FeedbackActivity.class)),
                item(R.drawable.ic_info_outline_20, R.string.settings_about_title,
                        R.string.settings_about_subtitle,
                        () -> open(AboutActivity.class)));
    }

    private void addQuickAction(@DrawableRes int icon, @StringRes int title, Runnable action) {
        View card = LayoutInflater.from(this)
                .inflate(R.layout.item_settings_quick_action, quickActions, false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        if (quickActions.getChildCount() > 0) {
            params.setMarginStart(
                    getResources().getDimensionPixelSize(R.dimen.settings_component_gap));
        }
        card.setLayoutParams(params);
        ((AppCompatImageView) card.findViewById(R.id.imgSettingsQuickIcon))
                .setImageResource(icon);
        ((TextView) card.findViewById(R.id.txtSettingsQuickTitle)).setText(title);
        card.setOnClickListener(view -> action.run());
        quickActions.addView(card);
    }

    private void addSection(@StringRes int title, MenuItem... items) {
        View header = LayoutInflater.from(this)
                .inflate(R.layout.item_settings_section_header, sections, false);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        headerParams.topMargin =
                getResources().getDimensionPixelSize(R.dimen.settings_section_spacing);
        header.setLayoutParams(headerParams);
        ((TextView) header.findViewById(R.id.txtSettingsSectionTitle)).setText(title);
        sections.addView(header);

        for (int index = 0; index < items.length; index++) {
            MenuItem item = items[index];
            View row = LayoutInflater.from(this)
                    .inflate(R.layout.item_settings_navigation_row, sections, false);
            if (index > 0) {
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        getResources().getDimensionPixelSize(R.dimen.settings_row_height));
                rowParams.topMargin =
                        getResources().getDimensionPixelSize(R.dimen.settings_component_gap);
                row.setLayoutParams(rowParams);
            }
            ((AppCompatImageView) row.findViewById(R.id.imgSettingsRowIcon))
                    .setImageResource(item.icon);
            ((TextView) row.findViewById(R.id.txtSettingsRowTitle)).setText(item.title);
            ((TextView) row.findViewById(R.id.txtSettingsRowSubtitle)).setText(item.subtitle);
            row.setOnClickListener(view -> item.action.run());
            sections.addView(row);
        }
    }

    private MenuItem item(@DrawableRes int icon, @StringRes int title,
                          @StringRes int subtitle, Runnable action) {
        return new MenuItem(icon, title, subtitle, action);
    }

    private void openFeedbackInbox() {
        open(FeedbackInboxActivity.class);
    }

    private void open(Class<?> target) {
        startActivity(new Intent(this, target));
    }

    private static final class MenuItem {
        final int icon;
        final int title;
        final int subtitle;
        final Runnable action;

        MenuItem(int icon, int title, int subtitle, Runnable action) {
            this.icon = icon;
            this.title = title;
            this.subtitle = subtitle;
            this.action = action;
        }
    }

    private static final class QuickAction {
        final int icon;
        final int title;
        final Runnable action;

        QuickAction(int icon, int title, Runnable action) {
            this.icon = icon;
            this.title = title;
            this.action = action;
        }
    }
}
