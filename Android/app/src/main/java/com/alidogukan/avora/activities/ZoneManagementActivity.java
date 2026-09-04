package com.alidogukan.avora.activities;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.models.CropCatalogItem;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.zones.PhysicalZoneIdentity;
import com.alidogukan.avora.viewmodels.ZoneManagementViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/** Adds, maps, edits and safely archives up to eight garden zones. */
public final class ZoneManagementActivity extends EdgeToEdgeActivity {
    private ZoneManagementViewModel viewModel;
    private final List<GardenZone> zones = new ArrayList<>();
    private List<CropCatalogItem> crops = new ArrayList<>();
    private LinearLayout zoneContainer;
    private TextView capacity;
    private MaterialButton addButton;

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_zone_management);
        zoneContainer = findViewById(R.id.layoutManagedZones);
        capacity = findViewById(R.id.txtZoneCapacity);
        addButton = findViewById(R.id.btnAddZone);
        viewModel = new ViewModelProvider(this).get(ZoneManagementViewModel.class);
        findViewById(R.id.btnBack).setOnClickListener(view -> finish());
        findViewById(R.id.btnOpenSeasonManagement).setOnClickListener(view ->
                startActivity(new Intent(this, SeasonManagementActivity.class)));
        addButton.setOnClickListener(view -> showNewZoneEditor());

        crops = viewModel.mergedCrops(null);
        viewModel.getZones().observe(this, value -> {
            zones.clear();
            if (value != null) zones.addAll(value);
            render();
        });
        viewModel.getCropCatalogItems().observe(this, value -> {
            crops = viewModel.mergedCrops(value);
        });
    }

    private void showNewZoneEditor() {
        if (viewModel.availableSlots(zones).isEmpty()) {
            Toast.makeText(this, R.string.zone_management_capacity_full, Toast.LENGTH_LONG).show();
            return;
        }
        startActivity(new Intent(this, ZoneEditorActivity.class));
    }

    private void showZoneEditor(GardenZone zone) {
        if (zone == null || safe(zone.getZone_id()).isEmpty()) return;
        Intent intent = new Intent(this, ZoneEditorActivity.class);
        intent.putExtra(ZoneEditorActivity.EXTRA_ZONE_ID, zone.getZone_id());
        startActivity(intent);
    }

    private void render() {
        zoneContainer.removeAllViews();
        int activeCount = viewModel.activeCount(zones);
        capacity.setText(getString(R.string.zone_management_capacity, activeCount,
                ZoneManagementViewModel.MAX_ZONES));
        addButton.setEnabled(activeCount < ZoneManagementViewModel.MAX_ZONES);

        for (int slot = 1; slot <= ZoneManagementViewModel.MAX_ZONES; slot++) {
            String zoneId = viewModel.zoneId(slot);
            GardenZone zone = findZone(zoneId);
            if (zone == null || viewModel.isInactive(zone)) continue;
            addZoneCard(zone, slot);
        }
    }

    private void addZoneCard(@Nullable GardenZone zone, int slot) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surfaceElevated));
        try {
            card.setStrokeColor(Color.parseColor(zone == null
                    ? PhysicalZoneIdentity.DEFAULT_COLOR
                    : PhysicalZoneIdentity.color(zone)));
        } catch (IllegalArgumentException ignored) {
            card.setStrokeColor(ContextCompat.getColor(this, R.color.border));
        }
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(18));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(10);
        card.setLayoutParams(cardParams);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(15), dp(14), dp(15), dp(14));

        boolean missing = zone == null;
        boolean inactive = !missing && viewModel.isInactive(zone);
        String title;
        String status;
        String detail;
        if (missing) {
            title = getString(R.string.zone_management_empty_slot_title, slot);
            status = getString(R.string.zone_management_status_available);
            detail = getString(R.string.zone_management_empty_slot_detail);
        } else {
            title = symbol(zone) + " " + safeName(zone, slot);
            if (inactive) {
                status = getString(R.string.zone_management_status_inactive);
            } else if (isHardwareReady(zone)) {
                status = getString(R.string.zone_management_status_active);
            } else {
                status = getString(R.string.zone_management_status_hardware_pending);
            }
            detail = getString(R.string.zone_management_mapping,
                    hardwareLabel(zone.getSensor_id()), hardwareLabel(zone.getValve_id()));
        }

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView titleView = text(title, 16, R.color.textPrimary, Typeface.BOLD);
        TextView statusView = text(status, 11,
                inactive || missing ? R.color.textSecondary : R.color.primary, Typeface.BOLD);
        statusView.setGravity(Gravity.END);
        heading.addView(titleView, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        heading.addView(statusView);
        content.addView(heading);

        TextView idView = text(getString(R.string.zone_management_channel,
                viewModel.zoneId(slot)), 11, R.color.textSecondary, Typeface.NORMAL);
        idView.setPadding(0, dp(3), 0, 0);
        content.addView(idView);
        TextView detailView = text(detail, 12, R.color.textSecondary, Typeface.NORMAL);
        detailView.setPadding(0, dp(5), 0, dp(10));
        content.addView(detailView);

        if (missing || inactive) {
            MaterialButton configure = button(missing
                    ? R.string.zone_management_add_this
                    : R.string.zone_management_reuse);
            configure.setOnClickListener(view -> showEditor(zone, slot));
            content.addView(configure);
        } else {
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            MaterialButton edit = button(R.string.zone_management_edit);
            MaterialButton deactivate = outlinedDeactivateButton();
            edit.setOnClickListener(view -> showZoneEditor(zone));
            deactivate.setOnClickListener(view -> confirmDeactivate(zone));
            LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(
                    0, dp(48), 1f);
            LinearLayout.LayoutParams deactivateParams = new LinearLayout.LayoutParams(
                    0, dp(48), 1f);
            deactivateParams.setMarginStart(dp(8));
            actions.addView(edit, editParams);
            actions.addView(deactivate, deactivateParams);
            content.addView(actions);
        }

        card.addView(content);
        zoneContainer.addView(card);
    }

    private void showEditor(@Nullable GardenZone existing, int slot) {
        showEditor(existing, slot, null);
    }

    private void showEditor(
            @Nullable GardenZone existing,
            int slot,
            @Nullable List<Integer> selectableSlots
    ) {
        boolean channelSelectable = selectableSlots != null && !selectableSlots.isEmpty();
        List<ZoneChannelOption> channelOptions = new ArrayList<>();
        if (channelSelectable) {
            for (int availableSlot : selectableSlots) {
                channelOptions.add(new ZoneChannelOption(
                        availableSlot,
                        getString(
                                R.string.zone_management_channel_option,
                                availableSlot,
                                viewModel.zoneId(availableSlot)
                        )
                ));
            }
        } else {
            channelOptions.add(new ZoneChannelOption(
                    slot,
                    getString(
                            R.string.zone_management_channel_option,
                            slot,
                            viewModel.zoneId(slot)
                    )
            ));
        }

        List<CropCatalogItem> editorCrops = new ArrayList<>(crops);
        int cropPosition = cropPosition(editorCrops, existing);
        if (cropPosition < 0 && existing != null) {
            CropCatalogItem legacy = new CropCatalogItem("current-zone-product",
                    cropDisplayName(existing), symbol(existing), safe(existing.getPlant_type()),
                    existing.getMoisture_limit(), Math.min(100, existing.getMoisture_limit() + 20),
                    CropCatalogItem.SOURCE_USER, true);
            editorCrops.add(0, legacy);
            cropPosition = 0;
        }
        if (cropPosition < 0) cropPosition = 0;

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(4), dp(4), dp(4), 0);

        Spinner channelSpinner = spinner(channelOptions);
        if (channelSelectable) {
            form.addView(label(R.string.zone_management_channel_select));
            form.addView(channelSpinner, fieldParams());
        }

        Spinner cropSpinner = spinner(editorCrops);
        form.addView(label(R.string.zone_management_product));
        form.addView(cropSpinner, fieldParams());
        cropSpinner.setSelection(cropPosition);

        EditText name = new EditText(this);
        name.setHint(R.string.zone_management_name_hint);
        name.setSingleLine(true);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if (existing != null && !viewModel.isInactive(existing)) {
            name.setText(PhysicalZoneIdentity.name(existing));
        }
        form.addView(label(R.string.zone_management_name));
        form.addView(name, fieldParams());

        List<HardwareOption> sensorOptions = hardwareOptions(true, existing);
        Spinner sensorSpinner = spinner(sensorOptions);
        sensorSpinner.setSelection(optionPosition(sensorOptions,
                existing == null ? "" : existing.getSensor_id()));
        form.addView(label(R.string.zone_management_sensor));
        form.addView(sensorSpinner, fieldParams());

        List<HardwareOption> valveOptions = hardwareOptions(false, existing);
        Spinner valveSpinner = spinner(valveOptions);
        valveSpinner.setSelection(optionPosition(valveOptions,
                existing == null ? "" : existing.getValve_id()));
        form.addView(label(R.string.zone_management_valve));
        form.addView(valveSpinner, fieldParams());

        SwitchCompat irrigation = new SwitchCompat(this);
        irrigation.setText(R.string.zone_management_auto_irrigation);
        irrigation.setChecked(existing != null && existing.isIrrigation_enabled()
                && !viewModel.isInactive(existing));
        irrigation.setPadding(0, dp(8), 0, dp(4));
        form.addView(irrigation);

        TextView hardwareNote = text(getString(R.string.zone_management_hardware_note), 12,
                R.color.textSecondary, Typeface.NORMAL);
        hardwareNote.setPadding(0, dp(4), 0, 0);
        form.addView(hardwareNote);

        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int maxFormHeight = Math.min(dp(420), Math.round(screenHeight * 0.48f));
        DialogFormScrollView formScroll = new DialogFormScrollView(this, maxFormHeight);
        formScroll.setFillViewport(false);
        formScroll.setVerticalScrollBarEnabled(true);
        formScroll.addView(form, new NestedScrollView.LayoutParams(
                NestedScrollView.LayoutParams.MATCH_PARENT,
                NestedScrollView.LayoutParams.WRAP_CONTENT));

        MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null || viewModel.isInactive(existing)
                        ? R.string.zone_management_add_dialog
                        : R.string.zone_management_edit_dialog)
                .setMessage(channelSelectable
                        ? getString(R.string.zone_management_multi_crop_channel_hint)
                        : getString(R.string.zone_management_dialog_channel,
                                viewModel.zoneId(slot)))
                .setView(formScroll)
                .setNegativeButton(R.string.settings_cancel, null)
                .setPositiveButton(R.string.settings_save, null);
        if (channelSelectable) {
            dialogBuilder.setNeutralButton(R.string.zone_management_add_crop_to_active,
                    (ignored, which) -> startActivity(
                            new Intent(this, SeasonManagementActivity.class)));
        }
        AlertDialog dialog = dialogBuilder.create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                    CropCatalogItem crop = editorCrops.get(cropSpinner.getSelectedItemPosition());
                    HardwareOption sensor = sensorOptions.get(sensorSpinner.getSelectedItemPosition());
                    HardwareOption valve = valveOptions.get(valveSpinner.getSelectedItemPosition());
                    int selectedSlot = channelOptions
                            .get(channelSpinner.getSelectedItemPosition()).slot;
                    GardenZone selectedExisting = channelSelectable
                            ? findZone(viewModel.zoneId(selectedSlot))
                            : existing;
                    GardenZone candidate = viewModel.createCandidate(
                            selectedExisting, selectedSlot, crop,
                            value(name), sensor.id, valve.id, irrigation.isChecked());
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    viewModel.saveZone(candidate, channelSelectable)
                            .addOnSuccessListener(unused -> {
                                boolean ready = !sensor.id.isEmpty() && !valve.id.isEmpty();
                                Toast.makeText(this, ready
                                                ? R.string.zone_management_saved
                                                : R.string.zone_management_saved_pending,
                                        Toast.LENGTH_LONG).show();
                                dialog.dismiss();
                            })
                            .addOnFailureListener(error -> {
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                Toast.makeText(this, friendlyError(error), Toast.LENGTH_LONG).show();
                            });
                }));
        dialog.show();
    }

    /** Keeps dialog actions visible while allowing the form to scroll on short screens. */
    private static final class DialogFormScrollView extends NestedScrollView {
        private final int maxHeight;

        DialogFormScrollView(Context context, int maxHeight) {
            super(context);
            this.maxHeight = maxHeight;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int parentLimit = View.MeasureSpec.getSize(heightMeasureSpec);
            int heightLimit = parentLimit > 0
                    ? Math.min(parentLimit, maxHeight)
                    : maxHeight;
            int limitedHeight = View.MeasureSpec.makeMeasureSpec(
                    heightLimit,
                    View.MeasureSpec.AT_MOST);
            super.onMeasure(widthMeasureSpec, limitedHeight);
        }
    }

    private void confirmDeactivate(GardenZone zone) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.zone_management_deactivate_title)
                .setMessage(getString(R.string.zone_management_deactivate_message,
                        safeName(zone, zone.getOrder())))
                .setNegativeButton(R.string.settings_cancel, null)
                .setPositiveButton(R.string.zone_management_deactivate, (dialog, which) ->
                        viewModel.deactivateZone(zone)
                                .addOnSuccessListener(deleted -> {
                                    Toast.makeText(this, Boolean.TRUE.equals(deleted)
                                                    ? R.string.zone_management_deleted_empty
                                                    : R.string.zone_management_deactivated,
                                            Toast.LENGTH_LONG).show();
                                })
                                .addOnFailureListener(error -> Toast.makeText(this,
                                        friendlyError(error), Toast.LENGTH_LONG).show()))
                .show();
    }

    private List<HardwareOption> hardwareOptions(boolean sensor, @Nullable GardenZone current) {
        List<HardwareOption> result = new ArrayList<>();
        result.add(new HardwareOption("", getString(R.string.zone_management_unassigned)));
        String currentId = current == null ? "" : sensor
                ? safe(current.getSensor_id()) : safe(current.getValve_id());
        for (int slot = 1; slot <= ZoneManagementViewModel.MAX_ZONES; slot++) {
            String id = sensor ? viewModel.sensorId(slot) : viewModel.valveId(slot);
            if (id.equalsIgnoreCase(currentId) || !assignedToOther(id, sensor, current)) {
                result.add(new HardwareOption(id, id));
            }
        }
        return result;
    }

    private boolean assignedToOther(String id, boolean sensor, @Nullable GardenZone current) {
        String currentZoneId = current == null ? "" : safe(current.getZone_id());
        for (GardenZone zone : zones) {
            if (zone == null || viewModel.isInactive(zone)
                    || currentZoneId.equals(safe(zone.getZone_id()))) continue;
            String assigned = sensor ? safe(zone.getSensor_id()) : safe(zone.getValve_id());
            if (id.equalsIgnoreCase(assigned)) return true;
        }
        return false;
    }

    private int cropPosition(List<CropCatalogItem> items, @Nullable GardenZone zone) {
        if (zone == null || viewModel.isInactive(zone)) return 0;
        for (int index = 0; index < items.size(); index++) {
            if (safe(items.get(index).getPlant_type())
                    .equalsIgnoreCase(safe(zone.getPlant_type()))) return index;
        }
        return -1;
    }

    private int optionPosition(List<HardwareOption> items, String id) {
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).id.equalsIgnoreCase(safe(id))) return index;
        }
        return 0;
    }

    private GardenZone findZone(String zoneId) {
        for (GardenZone zone : zones) {
            if (zone != null && zoneId.equals(zone.getZone_id())) return zone;
        }
        return null;
    }

    private boolean isHardwareReady(GardenZone zone) {
        return !safe(zone.getSensor_id()).isEmpty() && !safe(zone.getValve_id()).isEmpty();
    }

    private String friendlyError(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        String code = current.getMessage() == null ? "" : current.getMessage();
        switch (code) {
            case ZoneManagementViewModel.ERROR_SENSOR_IN_USE:
                return getString(R.string.zone_management_error_sensor_used);
            case ZoneManagementViewModel.ERROR_VALVE_IN_USE:
                return getString(R.string.zone_management_error_valve_used);
            case ZoneManagementViewModel.ERROR_IRRIGATION_BUSY:
                return getString(R.string.zone_management_error_irrigation_busy);
            case ZoneManagementViewModel.ERROR_ACTIVE_SEASON:
                return getString(R.string.zone_management_error_active_season);
            case ZoneManagementViewModel.ERROR_ZONE_IN_USE:
                return getString(R.string.zone_management_error_zone_used);
            case ZoneManagementViewModel.ERROR_INVALID_ZONE:
            case ZoneManagementViewModel.ERROR_SENSOR_INVALID:
            case ZoneManagementViewModel.ERROR_VALVE_INVALID:
                return getString(R.string.zone_management_error_invalid_channel);
            default:
                return getString(R.string.zone_management_error_save, code);
        }
    }

    private MaterialButton button(int textRes) {
        MaterialButton button = new MaterialButton(this);
        button.setText(textRes);
        button.setAllCaps(false);
        return button;
    }

    private MaterialButton outlinedDeactivateButton() {
        MaterialButton button = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(R.string.zone_management_deactivate);
        button.setAllCaps(false);
        return button;
    }

    private TextView label(int textRes) {
        TextView label = text(getString(textRes), 12, R.color.textPrimary, Typeface.BOLD);
        label.setPadding(0, dp(8), 0, 0);
        return label;
    }

    private <T> Spinner spinner(List<T> items) {
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, items));
        return spinner;
    }

    private LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        params.bottomMargin = dp(3);
        return params;
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(ContextCompat.getColor(this, color));
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private static String hardwareLabel(String value) {
        String clean = safe(value);
        return clean.isEmpty() ? "—" : clean;
    }

    private String safeName(GardenZone zone, int slot) {
        return zone == null ? PhysicalZoneIdentity.defaultName(slot)
                : PhysicalZoneIdentity.name(zone);
    }

    private String cropDisplayName(GardenZone zone) {
        String cropName = safe(zone == null ? "" : zone.getName());
        return cropName.isEmpty() ? safe(zone == null ? "" : zone.getPlant_type()) : cropName;
    }

    private static String symbol(GardenZone zone) {
        return PhysicalZoneIdentity.icon(zone);
    }

    private static String value(EditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class HardwareOption {
        final String id;
        final String label;

        HardwareOption(String id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final class ZoneChannelOption {
        final int slot;
        final String label;

        ZoneChannelOption(int slot, String label) {
            this.slot = slot;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
