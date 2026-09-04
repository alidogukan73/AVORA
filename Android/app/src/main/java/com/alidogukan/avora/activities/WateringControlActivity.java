package com.alidogukan.avora.activities;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.view.View;
import android.view.Gravity;
import androidx.appcompat.widget.AppCompatImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import com.alidogukan.avora.models.GardenSeason;

import com.alidogukan.avora.R;
import com.alidogukan.avora.models.Command;
import com.alidogukan.avora.season.SeasonDisplayIdentity;
import com.alidogukan.avora.models.Status;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.viewmodels.MainViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.card.MaterialCardView;

import java.util.Collections;
import java.util.List;

public class WateringControlActivity extends EdgeToEdgeActivity {
    private static final String VALVE_MODE_PHYSICAL = "PHYSICAL";
    private static final String VALVE_MODE_SIMULATION = "SIMULATION";

    private MainViewModel viewModel;
    private TextView pumpState;
    private TextView pumpDescription;
    private TextView autoDescription;
    private MaterialSwitch autoSwitch;
    private MaterialSwitch pumpSwitch;
    private MaterialButton pumpButton;
    private MaterialCardView pumpStatusCard;
    private MaterialCardView pumpIconCard;
    private AppCompatImageView pumpIcon;
    private LinearLayout manualValves;
    private TextView manualValveSafety;
    private List<GardenZone> zones = Collections.emptyList();
    private List<GardenSeason> seasons = Collections.emptyList();
    private String activeValveId = "";
    private boolean valveOpen;
    private boolean updatingValveSwitch;
    private boolean relayOn;
    private boolean updatingSwitch;
    private boolean updatingPumpSwitch;
    private long lastStatusElapsed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watering_control);

        pumpState = findViewById(R.id.txtControlPumpState);
        pumpDescription = findViewById(
                R.id.txtControlPumpDescription
        );
        autoDescription = findViewById(
                R.id.txtControlAutoDescription
        );
        autoSwitch = findViewById(R.id.switchControlAuto);
        pumpSwitch = findViewById(R.id.switchControlPump);
        pumpButton = findViewById(R.id.btnControlPump);
        pumpStatusCard = findViewById(
                R.id.cardControlPumpStatus
        );
        pumpIconCard = findViewById(
                R.id.cardControlPumpIcon
        );
        pumpIcon = findViewById(R.id.imgControlPump);
        manualValves = findViewById(
                R.id.layoutManualValves
        );
        manualValveSafety = findViewById(
                R.id.txtManualValveSafety
        );
        findViewById(R.id.btnBack).setOnClickListener(
                view -> finish()
        );
        findViewById(R.id.btnValveSetup).setOnClickListener(
                view -> showValveSetup()
        );

        viewModel = new ViewModelProvider(this)
                .get(MainViewModel.class);
        viewModel.getStatus().observe(this, this::renderStatus);
        viewModel.getCommand().observe(this, this::renderCommand);
        viewModel.getGardenZones().observe(
                this,
                items -> {
                    zones = items != null
                            ? items
                            : Collections.emptyList();
                    renderManualValves();
                }
        );
        viewModel.getGardenSeasons().observe(this, items -> {
            seasons = items != null ? items : Collections.emptyList();
            renderManualValves();
        });

        viewModel.getError().observe(this, message -> {
            if (message != null && !message.isBlank()) {
                Toast.makeText(
                        this,
                        message,
                        Toast.LENGTH_LONG
                ).show();
            }
        });

        autoSwitch.setOnCheckedChangeListener(
                (button, checked) -> {
                    if (updatingSwitch) {
                        return;
                    }
                    if (!isDeviceOnline()) {
                        updatingSwitch = true;
                        autoSwitch.setChecked(!checked);
                        updatingSwitch = false;
                        Toast.makeText(
                                this,
                                getString(R.string.runtime_offline_auto_mode),
                                Toast.LENGTH_LONG
                        ).show();
                        return;
                    }

                    // Stopping automatic watering must always be possible.
                    // A valve is required only when starting automatic mode.
                    if (checked && !hasConfiguredPhysicalValve()) {
                        updatingSwitch = true;
                        autoSwitch.setChecked(false);
                        updatingSwitch = false;
                        Toast.makeText(
                                this,
                                getString(R.string.runtime_pump_simulation_protection),
                                Toast.LENGTH_LONG
                        ).show();
                        return;
                    }
                    viewModel.setAutoMode(checked);
                    if (checked) {
                        viewModel.setRelay(false);
                    }
                    renderAuto(checked);
                }
        );

        pumpSwitch.setOnCheckedChangeListener(
                (button, checked) -> {
                    if (updatingPumpSwitch) {
                        return;
                    }

                    if (!checked) {
                        viewModel.setRelay(false);
                        return;
                    }

                    updatingPumpSwitch = true;
                    pumpSwitch.setChecked(false);
                    updatingPumpSwitch = false;

                    if (!isDeviceOnline()) {
                        Toast.makeText(
                                this,
                                getString(R.string.runtime_offline_pump_start),
                                Toast.LENGTH_LONG
                        ).show();
                        return;
                    }

                    new MaterialAlertDialogBuilder(this)
                            .setTitle(
                                    R.string.manual_relay_test_title
                            )
                            .setMessage(
                                    R.string.manual_relay_test_message
                            )
                            .setNegativeButton(
                                    R.string.manual_relay_test_cancel,
                                    null
                            )
                            .setPositiveButton(
                                    R.string.manual_relay_test_confirm,
                                    (dialog, which) ->
                                            viewModel.setRelay(true)
                            )
                            .show();
                }
        );

        pumpButton.setOnClickListener(view -> {
            if (relayOn) {
                viewModel.setRelay(false);
                return;
            }
            if (!isDeviceOnline()) {
                Toast.makeText(
                        this,
                        getString(R.string.runtime_offline_pump_start),
                        Toast.LENGTH_LONG
                ).show();
                return;
            }
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.manual_relay_test_title)
                    .setMessage(R.string.manual_relay_test_message)
                    .setNegativeButton(
                            R.string.manual_relay_test_cancel,
                            null
                    )
                    .setPositiveButton(
                            R.string.manual_relay_test_confirm,
                            (dialog, which) ->
                                    viewModel.setRelay(true)
                    )
                    .show();
        });
    }

    private void renderStatus(Status status) {
        if (status == null) {
            return;
        }
        lastStatusElapsed = SystemClock.elapsedRealtime();
        relayOn = status.isRelay();
        activeValveId = status.getActiveValveId() == null
                ? ""
                : status.getActiveValveId();
        valveOpen = status.isValveOpen()
                && !activeValveId.isBlank();
        renderPump();
        renderManualValves();
    }

    private void renderCommand(Command command) {
        if (command == null) {
            return;
        }
        relayOn = command.isRelay();
        updatingSwitch = true;
        autoSwitch.setChecked(command.isAutoMode());
        updatingSwitch = false;
        renderAuto(command.isAutoMode());
        renderPump();
    }

    private void renderPump() {
        updatingPumpSwitch = true;
        pumpSwitch.setChecked(relayOn);
        updatingPumpSwitch = false;

        int color = ContextCompat.getColor(
                this,
                relayOn ? R.color.online : R.color.textSecondary
        );
        pumpState.setText(
                relayOn
                        ? R.string.pump_running
                        : R.string.pump_stopped
        );
        pumpState.setTextColor(color);
        pumpStatusCard.setStrokeColor(
                relayOn
                        ? ContextCompat.getColor(
                                this,
                                R.color.primary
                        )
                        : ContextCompat.getColor(
                                this,
                                R.color.border
                        )
        );
        pumpStatusCard.setCardBackgroundColor(
                ContextCompat.getColor(
                        this,
                        relayOn
                                ? R.color.surfaceGreen
                                : R.color.surfaceSoft
                )
        );
        pumpIconCard.setCardBackgroundColor(
                ContextCompat.getColor(
                        this,
                        relayOn
                                ? R.color.primaryLight
                                : R.color.offlineBackground
                )
        );
        pumpIcon.setImageTintList(
                ColorStateList.valueOf(
                        ContextCompat.getColor(
                                this,
                                relayOn
                                        ? R.color.primary
                                        : R.color.offline
                        )
                )
        );
        pumpDescription.setText(
                relayOn
                        ? R.string.pump_description_running
                        : R.string.pump_description_idle
        );
        pumpButton.setText(
                relayOn
                        ? getString(R.string.runtime_stop_pump)
                        : getString(R.string.manual_relay_test_button)
        );
        pumpButton.setBackgroundTintList(
                ColorStateList.valueOf(
                        ContextCompat.getColor(
                                this,
                                relayOn
                                        ? R.color.warning
                                        : R.color.primary
                        )
                )
        );
    }

    private void renderAuto(boolean enabled) {
        autoDescription.setText(
                enabled
                        ? R.string.auto_mode_active_description
                        : R.string.auto_mode_inactive_description
        );
    }

    private boolean isDeviceOnline() {
        return lastStatusElapsed > 0L
                && SystemClock.elapsedRealtime()
                - lastStatusElapsed <= 30_000L;
    }

    private void renderManualValves() {
        if (manualValves == null) {
            return;
        }

        updatingValveSwitch = true;
        manualValves.removeAllViews();
        manualValveSafety.setText(
                hasConfiguredPhysicalValve()
                        ? R.string.manual_valves_physical
                        : R.string.manual_valves_simulation
        );

        for (GardenZone zone : zones) {
            View row = getLayoutInflater().inflate(
                    R.layout.item_manual_valve_switch,
                    manualValves,
                    false
            );
            TextView name = row.findViewById(
                    R.id.txtManualValveName
            );
            TextView detail = row.findViewById(
                    R.id.txtManualValveDetail
            );
            MaterialSwitch valveSwitch = row.findViewById(
                    R.id.switchManualValve
            );

            String emoji = SeasonDisplayIdentity.physicalIcon(zone);
            boolean zonePhysical = VALVE_MODE_PHYSICAL.equalsIgnoreCase(
                    zone.getValve_mode()
            );
            name.setText(getString(
                    R.string.runtime_icon_label,
                    emoji,
                    zoneName(zone)));
            detail.setText(
                    getString(
                            R.string.runtime_value_suffix,
                            zone.getValve_id(),
                            zonePhysical
                                    ? getString(R.string.runtime_physical_suffix)
                                    : getString(R.string.runtime_simulation_suffix)
                    )
            );
            String crops = SeasonDisplayIdentity.activeCropNames(zone, seasons);
            if (!crops.isBlank()) {
                detail.setText(getString(R.string.runtime_crops_hardware_detail,
                        crops, detail.getText()));
            }
            boolean thisValveOpen =
                    valveOpen
                            && zone.getValve_id()
                            .equals(activeValveId);
            valveSwitch.setChecked(thisValveOpen);
            valveSwitch.setOnCheckedChangeListener(
                    (button, checked) -> {
                        if (updatingValveSwitch) {
                            return;
                        }

                        if (!checked) {
                            if (relayOn) {
                                viewModel.setRelay(false);
                            }
                            // Always send the cancellation.  A stale screen
                            // state must never leave a manual valve command
                            // running in Firebase.
                            viewModel.closeManualValve();
                            return;
                        }

                        if (valveOpen) {
                            button.setChecked(false);
                            Toast.makeText(
                                    this,
                                    getString(R.string.runtime_close_valve_first),
                                    Toast.LENGTH_LONG
                            ).show();
                            return;
                        }

                        if (!isDeviceOnline()) {
                            button.setChecked(false);
                            Toast.makeText(
                                    this,
                                    getString(R.string.runtime_device_offline_short),
                                    Toast.LENGTH_LONG
                            ).show();
                            return;
                        }
                        // Wait for Raspberry Pi status confirmation before
                        // rendering this switch as open.  This avoids a
                        // locally green switch when a command is rejected.
                        updatingValveSwitch = true;
                        button.setChecked(false);
                        updatingValveSwitch = false;
                        viewModel.openManualValve(zone);
                        Toast.makeText(
                                this,
                                getString(R.string.runtime_valve_command_sent),
                                Toast.LENGTH_SHORT
                        ).show();
                    }
            );
            manualValves.addView(row);
        }
        updatingValveSwitch = false;
    }

    private boolean hasConfiguredPhysicalValve() {
        for (GardenZone zone : zones) {
            if (VALVE_MODE_PHYSICAL.equalsIgnoreCase(
                    zone.getValve_mode()
            )) {
                return true;
            }
        }
        return false;
    }

    private void showValveSetup() {
        if (relayOn || valveOpen) {
            Toast.makeText(
                    this,
                    getString(R.string.runtime_valve_mode_locked),
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources()
                .getDisplayMetrics().density);
        content.setPadding(padding, 0, padding, 0);

        TextView hint = new TextView(this);
        hint.setText(R.string.valve_setup_hint);
        content.addView(hint);

        for (GardenZone zone : zones) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, padding / 2, 0, padding / 2);

            TextView label = new TextView(this);
            label.setLayoutParams(new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            ));
            String valveId = zone.getValve_id() == null
                    ? ""
                    : zone.getValve_id();
            boolean physical = VALVE_MODE_PHYSICAL.equalsIgnoreCase(
                    zone.getValve_mode()
            );
            String wiring = zone.getValve_gpio_bcm() > 0
                    && zone.getValve_gpio_physical_pin() > 0
                    ? getString(R.string.runtime_gpio_pin,
                            zone.getValve_gpio_bcm(),
                            zone.getValve_gpio_physical_pin())
                    : getString(R.string.runtime_connection_loading);
            label.setText(
                    getString(
                            R.string.runtime_three_lines,
                            getString(
                                    R.string.runtime_icon_label,
                                    SeasonDisplayIdentity.operationalEmoji(zone, seasons),
                                    zoneName(zone)),
                            getString(
                                    R.string.runtime_sensor_valve,
                                    valveId,
                                    getString(physical
                                            ? R.string.valve_setup_physical
                                            : R.string.valve_setup_simulation)),
                            wiring)
            );

            MaterialSwitch modeSwitch = new MaterialSwitch(this);
            modeSwitch.setChecked(physical);
            modeSwitch.setEnabled(!valveId.isBlank());
            modeSwitch.setOnCheckedChangeListener(
                    (button, checked) -> {
                        if (checked == physical) {
                            return;
                        }
                        if (!checked) {
                            saveValveMode(zone, false);
                            return;
                        }
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(
                                        R.string.valve_setup_confirm_title
                                )
                                .setMessage(getString(
                                        R.string.valve_setup_confirm_message,
                                        zoneName(zone)
                                ))
                                .setNegativeButton(
                                        android.R.string.cancel,
                                        (dialog, which) ->
                                                modeSwitch.setChecked(false)
                                )
                                .setPositiveButton(
                                        android.R.string.ok,
                                        (dialog, which) ->
                                                saveValveMode(zone, true)
                                )
                                .show();
                    }
            );
            row.addView(label);
            row.addView(modeSwitch);
            content.addView(row);
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.valve_setup_title)
                .setView(content)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void saveValveMode(
            GardenZone zone,
            boolean physical
    ) {
        viewModel.setZoneValvePhysicalMode(zone, physical);
        Toast.makeText(
                this,
                getString(R.string.valve_setup_status_format,
                        zoneName(zone),
                        getString(
                                physical
                                        ? R.string.valve_setup_physical
                                        : R.string.valve_setup_simulation
                        )),
                Toast.LENGTH_SHORT
        ).show();
    }
    private String zoneName(GardenZone zone) {
        return SeasonDisplayIdentity.operationalName(zone, seasons);
    }

}
