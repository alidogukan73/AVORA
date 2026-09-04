package com.alidogukan.avora.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.LinearLayout;
import androidx.core.content.ContextCompat;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alidogukan.avora.R;
import com.alidogukan.avora.adapters.GardenZoneAdapter;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.ZoneIrrigationStatus;
import com.alidogukan.avora.viewmodels.SensorPointsViewModel;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

public class SensorPointsActivity extends EdgeToEdgeActivity {

    private static final long STATUS_REFRESH_MILLIS =
            5_000L;

    private final Handler statusHandler =
            new Handler(
                    Looper.getMainLooper()
            );

    private final GardenZoneAdapter adapter =
            new GardenZoneAdapter();

    private final List<GardenZone> latestZones =
            new ArrayList<>();

    private TextView txtSensorSummary;
    private TextView txtQueuePumpState;
    private LinearLayout layoutIrrigationQueue;
    private String renderedQueueSignature = "";

    private final Runnable statusUpdater =
            new Runnable() {

                @Override
                public void run() {
                    adapter.refreshStatuses();
                    updateSummary();

                    statusHandler.postDelayed(
                            this,
                            STATUS_REFRESH_MILLIS
                    );
                }
            };

    @Override
    protected void onCreate(
            @Nullable Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_sensor_points
        );

        MaterialButton btnBack =
                findViewById(R.id.btnBack);

        txtSensorSummary =
                findViewById(R.id.txtSensorSummary);

        txtQueuePumpState =
                findViewById(R.id.txtQueuePumpState);

        layoutIrrigationQueue =
                findViewById(R.id.layoutIrrigationQueue);

        RecyclerView recyclerZones =
                findViewById(R.id.recyclerGardenZones);

        recyclerZones.setLayoutManager(
                new LinearLayoutManager(this)
        );
        recyclerZones.setAdapter(adapter);
        recyclerZones.setNestedScrollingEnabled(false);

        adapter.setOnZoneClickListener(
                zone -> {
                    Intent intent = new Intent(
                            this,
                            ZoneDetailActivity.class
                    );
                    intent.putExtra(
                            ZoneDetailActivity.EXTRA_ZONE_ID,
                            zone.getZone_id()
                    );
                    startActivity(intent);
                }
        );

        btnBack.setOnClickListener(
                view -> finish()
        );

        SensorPointsViewModel viewModel =
                new ViewModelProvider(this)
                        .get(
                                SensorPointsViewModel.class
                        );

        viewModel.getSensorPoints().observe(
                this,
                this::renderSensorPoints
        );
        viewModel.getConfiguredZones().observe(
                this,
                this::renderConfiguredZones
        );
    }

    @Override
    protected void onStart() {
        super.onStart();

        statusHandler.post(
                statusUpdater
        );
    }

    @Override
    protected void onStop() {
        statusHandler.removeCallbacks(
                statusUpdater
        );

        super.onStop();
    }

    private void renderSensorPoints(
            List<GardenZone> zones
    ) {
        adapter.submitZones(
                zones == null ? new ArrayList<>() : zones
        );

        updateSummary();
    }

    private void renderConfiguredZones(
            List<GardenZone> zones
    ) {
        latestZones.clear();
        if (zones != null) {
            latestZones.addAll(zones);
        }

        renderIrrigationQueue();
    }

    private void updateSummary() {
        txtSensorSummary.setText(
                getString(
                        R.string.sensor_summary_format,
                        adapter.getConnectedCount(),
                        adapter.getEnabledSensorCount()
                )
        );
    }

    private void renderIrrigationQueue() {
        GardenZone activeZone = null;
        List<GardenZone> queuedZones =
                new ArrayList<>();

        for (GardenZone zone : latestZones) {
            ZoneIrrigationStatus status =
                    zone.getIrrigation_status();
            if (status == null) {
                continue;
            }
            if (status.isWatering_active()) {
                activeZone = zone;
            } else if (status.getQueue_position() > 0) {
                queuedZones.add(zone);
            }
        }

        queuedZones.sort(
                Comparator.comparingInt(
                        zone -> zone.getIrrigation_status()
                                .getQueue_position()
                )
        );

        String queueSignature = queueSignature(
                activeZone,
                queuedZones
        );
        if (queueSignature.equals(renderedQueueSignature)) {
            return;
        }
        renderedQueueSignature = queueSignature;
        layoutIrrigationQueue.removeAllViews();

        txtQueuePumpState.setText(
                activeZone == null
                        ? getString(R.string.queue_pump_idle)
                        : getString(
                                R.string.queue_pump_active,
                                activeZone.getName()
                        )
        );
        txtQueuePumpState.setTextColor(
                ContextCompat.getColor(
                        this,
                        activeZone == null
                                ? R.color.textSecondary
                                : R.color.info
                )
        );

        if (queuedZones.isEmpty()) {
            addQueueEmptyRow();
            return;
        }

        for (GardenZone zone : queuedZones) {
            addQueueZoneRow(zone);
        }
    }

    private String queueSignature(
            GardenZone activeZone,
            List<GardenZone> queuedZones
    ) {
        StringBuilder signature = new StringBuilder();
        signature.append("active:")
                .append(activeZone == null
                        ? ""
                        : activeZone.getZone_id());
        for (GardenZone zone : queuedZones) {
            ZoneIrrigationStatus status = zone.getIrrigation_status();
            signature.append("|queued:")
                    .append(zone.getZone_id())
                    .append(':')
                    .append(status == null ? 0 : status.getQueue_position())
                    .append(':')
                    .append(zone.getMoisture())
                    .append(':')
                    .append(status == null ? 0 : status.getMoisture_deficit());
        }
        return signature.toString();
    }

    private void addQueueEmptyRow() {
        TextView empty = new TextView(this);
        empty.setText(R.string.queue_empty);
        empty.setTextColor(
                ContextCompat.getColor(
                        this,
                        R.color.online
                )
        );
        empty.setTextSize(13f);
        empty.setPadding(0, 10, 0, 6);
        layoutIrrigationQueue.addView(empty);
    }

    private void addQueueZoneRow(GardenZone zone) {
        ZoneIrrigationStatus status =
                zone.getIrrigation_status();

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, 10, 0, 10);
        row.setOnClickListener(
                view -> {
                    Intent intent = new Intent(
                            this,
                            ZoneDetailActivity.class
                    );
                    intent.putExtra(
                            ZoneDetailActivity.EXTRA_ZONE_ID,
                            zone.getZone_id()
                    );
                    startActivity(intent);
                }
        );

        TextView name = new TextView(this);
        name.setText(
                getString(
                        R.string.runtime_zone_sensor_detail,
                        zone.getEmoji() == null
                                ? getString(R.string.symbol_plant)
                                : zone.getEmoji(),
                        com.alidogukan.avora.zones.PhysicalZoneIdentity.name(zone),
                        getString(
                                R.string.queue_zone_detail,
                                zone.getMoisture(),
                                status.getMoisture_deficit()))
        );
        name.setTextColor(
                ContextCompat.getColor(
                        this,
                        R.color.textPrimary
                )
        );
        name.setTextSize(14f);
        name.setLayoutParams(
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                )
        );

        TextView position = new TextView(this);
        position.setText(
                getString(
                        R.string.queue_position,
                        status.getQueue_position()
                )
        );
        position.setTextColor(
                ContextCompat.getColor(
                        this,
                        R.color.accentOrange
                )
        );
        position.setTextSize(13f);

        row.addView(name);
        row.addView(position);
        layoutIrrigationQueue.addView(row);
    }
}
