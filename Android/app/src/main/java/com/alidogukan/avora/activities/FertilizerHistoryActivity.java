package com.alidogukan.avora.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.app.DatePickerDialog;

import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alidogukan.avora.R;
import com.alidogukan.avora.adapters.FertilizerHistoryAdapter;
import com.alidogukan.avora.models.FertilizerApplication;
import com.alidogukan.avora.models.GardenSeason;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.season.SeasonDisplayIdentity;
import com.alidogukan.avora.zones.ZoneChipRenderer;
import com.alidogukan.avora.viewmodels.FertilizerHistoryViewModel;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class FertilizerHistoryActivity extends EdgeToEdgeActivity {

    private FertilizerHistoryViewModel viewModel;
    private com.alidogukan.avora.viewmodels.DisplayUnitsViewModel displayUnits;
    private final FertilizerHistoryAdapter adapter =
            new FertilizerHistoryAdapter();
    private TextView empty;
    private RecyclerView recycler;
    private TextView count;
    private TextView products;
    private TextView last;
    private TextView usageSummary;
    private TextView outcomeSummary;
    private List<FertilizerApplication> allValues =
            Collections.emptyList();
    private List<GardenZone> latestZones = Collections.emptyList();
    private List<GardenSeason> latestSeasons = Collections.emptyList();
    private String selectedZoneId = "";
    private String pendingOutcomeApplicationId = "";
    private boolean pendingOutcomeOpened;
    private List<FertilizerApplication> visibleValues =
            Collections.emptyList();
    private final ActivityResultLauncher<Intent> exportLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK
                                && result.getData() != null) {
                            exportCsv(result.getData().getData());
                        }
                    }
            );

    @Override
    protected void onResume() {
        super.onResume();
        if (displayUnits != null) {
            adapter.notifyDataSetChanged();
            renderStatistics(visibleValues);
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fertilizer_history);
        viewModel = new ViewModelProvider(this).get(FertilizerHistoryViewModel.class);
        displayUnits = new ViewModelProvider(this)
                .get(com.alidogukan.avora.viewmodels.DisplayUnitsViewModel.class);
        pendingOutcomeApplicationId = safe(
                getIntent().getStringExtra("outcome_application_id")
        );
        String notificationId = safe(
                getIntent().getStringExtra("notification_id")
        );

        if (!notificationId.isBlank()) {
            viewModel.markNotificationRead(notificationId);
        }
        findViewById(R.id.btnBack).setOnClickListener(
                view -> finish()
        );
        empty = findViewById(R.id.txtHistoryEmpty);
        count = findViewById(R.id.txtFertilizerHistoryCount);
        products = findViewById(
                R.id.txtFertilizerHistoryProducts
        );
        last = findViewById(R.id.txtFertilizerHistoryLast);
        usageSummary = findViewById(
                R.id.txtFertilizerUsageSummary
        );
        outcomeSummary = findViewById(
                R.id.txtFertilizerOutcomeSummary
        );
        recycler = findViewById(
                R.id.recyclerFertilizerHistory
        );
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);
        adapter.setOnApplicationClickListener(
                this::showApplicationActions
        );
        findViewById(R.id.btnExportFertilizerHistory)
                .setOnClickListener(view -> requestExport());
        viewModel.getHistory().observe(
                this,
                this::render
        );
        ChipGroup zoneGroup = findViewById(
                R.id.chipGroupFertilizerZones
        );
        viewModel.getZones().observe(this, zones -> {
            latestZones = zones == null ? Collections.emptyList() : zones;
            renderZoneFilters(zoneGroup);
        });
        viewModel.getSeasons().observe(this, seasons -> {
            latestSeasons = seasons == null ? Collections.emptyList() : seasons;
            renderZoneFilters(zoneGroup);
        });
    }
    private void renderZoneFilters(ChipGroup zoneGroup) {
        java.util.Map<String, String> labels = new java.util.HashMap<>();
        for (GardenZone zone : latestZones) {
            if (zone == null || zone.getZone_id() == null) continue;
            labels.put(zone.getZone_id(),
                    SeasonDisplayIdentity.operationalLabel(zone, latestSeasons));
        }
        ZoneChipRenderer.render(
                this,
                zoneGroup,
                latestZones,
                selectedZoneId,
                R.string.history_zone_all,
                labels,
                zoneId -> {
                    selectedZoneId = zoneId;
                    applyFilter();
                }
        );
    }


    private void render(List<FertilizerApplication> values) {
        allValues = values == null
                ? Collections.emptyList()
                : values;
        applyFilter();
        openPendingOutcomeIfReady();
    }

    private void openPendingOutcomeIfReady() {
        if (pendingOutcomeOpened || pendingOutcomeApplicationId.isBlank()) return;
        for (FertilizerApplication value : allValues) {
            if (value != null && pendingOutcomeApplicationId.equals(
                    safe(value.getApplication_id()))) {
                pendingOutcomeOpened = true;
                recycler.post(() -> showOutcomeDialog(value));
                return;
            }
        }
    }

    private void showApplicationActions(FertilizerApplication value) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.fertilizer_history_action_title)
                .setMessage(value.getProduct_name())
                .setNegativeButton(android.R.string.cancel, null)
                .setItems(new String[]{
                                getString(R.string.fertilizer_outcome_action),
                                getString(R.string.fertilizer_history_edit),
                                getString(R.string.fertilizer_history_delete)
                        },
                        (dialog, which) -> {
                            if (which == 0) {
                                showOutcomeDialog(value);
                            } else if (which == 1) {
                                showEditDialog(value);
                            } else {
                                confirmDelete(value);
                            }
                        })
                .show();
    }

    private void showOutcomeDialog(FertilizerApplication value) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources()
                .getDisplayMetrics().density);
        content.setPadding(padding, 0, padding, 0);

        EditText dateInput = new EditText(this);
        dateInput.setHint(R.string.fertilizer_outcome_date_hint);
        dateInput.setFocusable(false);
        LocalDate initialDate = value.getOutcome_observed_at_epoch() > 0
                ? Instant.ofEpochSecond(value.getOutcome_observed_at_epoch())
                .atZone(ZoneId.systemDefault()).toLocalDate()
                : LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        dateInput.setText(initialDate.format(formatter));
        dateInput.setOnClickListener(view -> new DatePickerDialog(
                this,
                (picker, year, month, day) -> dateInput.setText(
                        LocalDate.of(year, month + 1, day).format(formatter)
                ),
                initialDate.getYear(), initialDate.getMonthValue() - 1,
                initialDate.getDayOfMonth()
        ).show());

        Spinner status = new Spinner(this);
        String[] statuses = getResources().getStringArray(
                R.array.fertilizer_outcome_statuses
        );
        status.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, statuses));
        if ("UNCHANGED".equals(value.getOutcome_status())) {
            status.setSelection(1);
        } else if ("ISSUE".equals(value.getOutcome_status())) {
            status.setSelection(2);
        }

        Spinner vigor = new Spinner(this);
        String[] vigorValues = getResources().getStringArray(
                R.array.fertilizer_outcome_vigor_scores
        );
        vigor.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, vigorValues));
        if (value.getOutcome_vigor_score() > 0
                && value.getOutcome_vigor_score() <= 5) {
            vigor.setSelection(value.getOutcome_vigor_score());
        }
        EditText notes = new EditText(this);
        notes.setHint(R.string.fertilizer_outcome_notes_hint);
        notes.setMinLines(2);
        notes.setText(value.getOutcome_notes());
        content.addView(dateInput);
        content.addView(status);
        content.addView(vigor);
        content.addView(notes);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.fertilizer_outcome_dialog_title)
                .setMessage(R.string.fertilizer_outcome_dialog_message)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.settings_save, (dialog, which) -> {
                    try {
                        LocalDate observed = LocalDate.parse(
                                dateInput.getText().toString(), formatter);
                        String[] values = {"IMPROVED", "UNCHANGED", "ISSUE"};
                        value.setOutcome_status(values[status.getSelectedItemPosition()]);
                        value.setOutcome_vigor_score(
                                Math.max(0, vigor.getSelectedItemPosition()));
                        value.setOutcome_notes(notes.getText().toString().trim());
                        value.setOutcome_observed_at_epoch(observed.atStartOfDay(
                                ZoneId.systemDefault()).toEpochSecond());
                        viewModel.update(value)
                                .addOnSuccessListener(result -> showPhotoPrompt(value))
                                .addOnFailureListener(error -> showHistoryError(
                                        R.string.fertilizer_history_update_failed,
                                        error
                                ));
                    } catch (Exception ignored) {
                        android.widget.Toast.makeText(this,
                                R.string.fertilizer_outcome_invalid_date,
                                android.widget.Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private void showPhotoPrompt(FertilizerApplication value) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.runtime_application_outcome_saved)
                .setMessage(R.string.runtime_history_photo_prompt)
                .setNegativeButton(R.string.runtime_not_now, null)
                .setPositiveButton(R.string.runtime_add_photo, (dialog, which) -> {
                    Intent intent = new Intent(this, NewJournalRecordActivity.class);
                    intent.putExtra(NewJournalRecordActivity.EXTRA_ZONE_ID,
                            value.getZone_id());
                    intent.putExtra(NewJournalRecordActivity.EXTRA_INITIAL_TYPE,
                            NewJournalRecordActivity.RECORD_TYPE_PHOTO);
                    intent.putExtra(NewJournalRecordActivity.EXTRA_RELATED_APPLICATION_ID,
                            value.getApplication_id());
                    startActivity(intent);
                })
                .show();
    }

    private void showEditDialog(FertilizerApplication value) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources()
                .getDisplayMetrics().density);
        content.setPadding(padding, 0, padding, 0);
        EditText inputDate = new EditText(this);
        inputDate.setHint(R.string.fertilization_application_date);
        inputDate.setFocusable(false);
        LocalDate date = Instant.ofEpochSecond(
                value.getApplied_at_epoch()
        ).atZone(ZoneId.systemDefault()).toLocalDate();
        inputDate.setText(date.format(
                DateTimeFormatter.ofPattern("dd-MM-yyyy")
        ));
        inputDate.setOnClickListener(view -> new DatePickerDialog(
                this,
                (picker, year, month, day) -> inputDate.setText(
                        LocalDate.of(year, month + 1, day).format(
                                DateTimeFormatter.ofPattern("dd-MM-yyyy")
                        )
                ),
                date.getYear(), date.getMonthValue() - 1,
                date.getDayOfMonth()
        ).show());
        EditText inputDose = new EditText(this);
        inputDose.setHint(R.string.fertilization_applied_dose);
        inputDose.setInputType(
                android.text.InputType.TYPE_CLASS_NUMBER
                        | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        );
        inputDose.setText(String.valueOf(value.getApplied_dose()));
        EditText inputNotes = new EditText(this);
        inputNotes.setHint(R.string.fertilization_application_notes);
        inputNotes.setText(value.getNotes());
        content.addView(inputDate);
        content.addView(inputDose);
        content.addView(inputNotes);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.fertilizer_history_edit)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.settings_save,
                        (dialog, which) -> {
                            try {
                                double dose = Double.parseDouble(
                                        inputDose.getText().toString()
                                                .replace(',', '.')
                                );
                                LocalDate selected = LocalDate.parse(
                                        inputDate.getText().toString(),
                                        DateTimeFormatter.ofPattern(
                                                "dd-MM-yyyy"
                                        )
                                );
                                if (!Double.isFinite(dose) || dose <= 0.0 || selected.isAfter(LocalDate.now())) {
                                    return;
                                }
                                long intervalSeconds = Math.max(
                                        86400L,
                                        value.getNext_application_at_epoch()
                                                - value
                                                .getApplied_at_epoch()
                                );
                                value.setApplied_dose(dose);
                                long appliedAt = selected
                                        .atStartOfDay(
                                                ZoneId.systemDefault()
                                        ).toEpochSecond();
                                value.setApplied_at_epoch(appliedAt);
                                value.setNext_application_at_epoch(
                                        appliedAt + intervalSeconds
                                );
                                value.setNotes(inputNotes.getText()
                                        .toString().trim());
                                viewModel.update(value)
                                        .addOnSuccessListener(result ->
                                                android.widget.Toast.makeText(
                                                        this,
                                                        R.string
                                                                .fertilizer_history_updated,
                                                        android.widget.Toast.LENGTH_LONG
                                                ).show()
                                        )
                                        .addOnFailureListener(error ->
                                                showHistoryError(
                                                        R.string
                                                                .fertilizer_history_update_failed,
                                                        error
                                                )
                                        );
                            } catch (Exception ignored) {
                                android.widget.Toast.makeText(
                                        this,
                                        getString(R.string.runtime_valid_date_amount),
                                        android.widget.Toast.LENGTH_LONG
                                ).show();
                            }
                        })
                .show();
    }

    private void confirmDelete(FertilizerApplication value) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.fertilizer_history_delete)
                .setMessage(
                        R.string.fertilizer_history_delete_confirm
                )
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(
                        R.string.fertilizer_history_delete,
                        (dialog, which) -> viewModel
                                .delete(value)
                                .addOnSuccessListener(result ->
                                        android.widget.Toast.makeText(
                                                this,
                                                R.string
                                                        .fertilizer_history_deleted,
                                                android.widget.Toast.LENGTH_LONG
                                        ).show()
                                )
                                .addOnFailureListener(error ->
                                        showHistoryError(
                                                R.string
                                                        .fertilizer_history_delete_failed,
                                                error
                                        )
                                )
                )
                .show();
    }

    private void applyFilter() {
        List<FertilizerApplication> visible = new ArrayList<>();
        for (FertilizerApplication value : allValues) {
            if (selectedZoneId.isEmpty()
                    || selectedZoneId.equals(value.getZone_id())) {
                visible.add(value);
            }
        }
        adapter.submitList(visible);
        visibleValues = new ArrayList<>(visible);
        boolean isEmpty = visible.isEmpty();
        empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        recycler.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        renderStatistics(visible);
    }

    private void renderStatistics(
            List<FertilizerApplication> values
    ) {
        count.setText(
                getString(
                        R.string.fertilizer_history_count,
                        values.size()
                )
        );
        Set<String> productIds = new HashSet<>();
        long latest = 0L;
        for (FertilizerApplication value : values) {
            if (value.getProduct_id() != null
                    && !value.getProduct_id().isBlank()) {
                productIds.add(value.getProduct_id());
            }
            latest = Math.max(latest, value.getApplied_at_epoch());
        }
        products.setText(
                getString(
                        R.string.fertilizer_history_products,
                        productIds.size()
                )
        );
        if (latest == 0L) {
            last.setText(R.string.fertilizer_history_last_empty);
        } else {
            String date = Instant.ofEpochSecond(latest)
                    .atZone(ZoneId.systemDefault())
                    .format(
                            DateTimeFormatter.ofPattern(
                                    "dd-MM-yyyy",
                                    Locale.forLanguageTag("tr-TR")
                            )
                    );
            last.setText(
                    getString(
                            R.string.fertilizer_history_last,
                            date
                    )
            );
        }
        renderUsageSummary(values);
        renderOutcomeSummary(values);
    }

    private void renderUsageSummary(
            List<FertilizerApplication> values
    ) {
        double totalGram = 0.0;
        double totalMilliliter = 0.0;
        double last30Gram = 0.0;
        double last30Milliliter = 0.0;
        int drip = 0;
        int soil = 0;
        int foliar = 0;
        long thirtyDaysAgo = Instant.now()
                .minusSeconds(30L * 24L * 60L * 60L)
                .getEpochSecond();
        for (FertilizerApplication value : values) {
            boolean gram = "g".equalsIgnoreCase(
                    safe(value.getDose_unit())
            );
            boolean milliliter = "ml".equalsIgnoreCase(
                    safe(value.getDose_unit())
            );
            if (gram) {
                totalGram += value.getApplied_dose();
                if (value.getApplied_at_epoch() >= thirtyDaysAgo) {
                    last30Gram += value.getApplied_dose();
                }
            } else if (milliliter) {
                totalMilliliter += value.getApplied_dose();
                if (value.getApplied_at_epoch() >= thirtyDaysAgo) {
                    last30Milliliter += value.getApplied_dose();
                }
            }
            String method = safe(value.getApplication_method());
            switch (method) {
                case "FOLIAR":
                    foliar++;
                    break;
                case "SOIL":
                    soil++;
                    break;
                case "DRIP":
                    drip++;
                    break;
            }

        }
        if (totalGram == 0.0 && totalMilliliter == 0.0
                && drip == 0 && soil == 0 && foliar == 0) {
            usageSummary.setText(
                    R.string.fertilizer_history_usage_empty
            );
            return;
        }
        usageSummary.setText(
                getString(
                        R.string.runtime_three_lines,
                        getString(
                                R.string.fertilizer_history_usage_total,
                                displayUnits.formatWeight(totalGram),
                                displayUnits.formatVolume(totalMilliliter / 1000d)),
                        getString(
                                R.string.fertilizer_history_usage_30d,
                                displayUnits.formatWeight(last30Gram),
                                displayUnits.formatVolume(last30Milliliter / 1000d)),
                        getString(
                                R.string.fertilizer_history_usage_methods,
                                drip,
                                soil,
                                foliar))
        );
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void showHistoryError(int messageResource, Exception error) {
        String detail = error == null ? "" : safe(error.getMessage()).trim();
        if (detail.isEmpty()) {
            detail = getString(R.string.fertilization_application_failed);
        }
        android.widget.Toast.makeText(
                this,
                getString(messageResource, detail),
                android.widget.Toast.LENGTH_LONG
        ).show();
    }

    private void renderOutcomeSummary(
            List<FertilizerApplication> values
    ) {
        FertilizerHistoryViewModel.OutcomeSummary summary =
                viewModel.summarizeOutcomes(values);
        if (summary.observed == 0) {
            outcomeSummary.setText(R.string.fertilizer_outcome_learning_empty);
            return;
        }

        String learning = summary.learnedPairs == 0
                ? getString(R.string.fertilizer_outcome_learning_overview_progress,
                Math.min(summary.strongestPair, summary.target), summary.target)
                : getString(R.string.fertilizer_outcome_learning_overview_ready,
                summary.learnedPairs);
        String statistics = getString(
                R.string.fertilizer_outcome_stats,
                summary.observed,
                summary.improved,
                summary.unchanged,
                summary.issue
        );
        if (summary.vigorCount > 0) {
            statistics += getString(
                    R.string.fertilizer_outcome_vigor,
                    String.format(Locale.getDefault(), "%.1f", summary.averageVigor)
            );
        }
        if (summary.learnedPairs > 0) {
            statistics += getString(summary.issue > summary.improved
                    ? R.string.fertilizer_outcome_trend_issue
                    : summary.improved > summary.issue
                    ? R.string.fertilizer_outcome_trend_positive
                    : R.string.fertilizer_outcome_trend_mixed);
        }
        outcomeSummary.setText(getString(
                R.string.runtime_two_lines,
                learning,
                statistics));
    }
    private String formatAmount(double value) {
        return value == Math.rint(value)
                ? String.format(Locale.getDefault(), "%.0f", value)
                : String.format(Locale.getDefault(), "%.1f", value);
    }

    private void requestExport() {
        if (visibleValues.isEmpty()) {
            android.widget.Toast.makeText(
                    this,
                    R.string.fertilizer_history_export_empty,
                    android.widget.Toast.LENGTH_LONG
            ).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("text/csv");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(
                Intent.EXTRA_TITLE,
                "AVORA-gubre-gecmisi.csv"
        );
        exportLauncher.launch(intent);
    }

    private void exportCsv(Uri uri) {
        if (uri == null) {
            return;
        }
        StringBuilder csvContents = new StringBuilder();
        csvContents.append('\uFEFF').append(getString(R.string.runtime_csv_header));
        csvContents.append('\n');
        try {
            for (FertilizerApplication value : visibleValues) {
                String date = value.getApplied_at_epoch() <= 0L
                        ? ""
                        : Instant.ofEpochSecond(
                                value.getApplied_at_epoch()
                        ).atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern(
                                        "dd-MM-yyyy HH:mm",
                                        Locale.getDefault()
                                ));
                csvContents.append(String.join(",",
                        csv(date),
                        csv(value.getZone_name()),
                        csv(value.getProduct_name()),
                        csv(formatAmount(value.getApplied_dose())),
                        csv(value.getDose_unit()),
                        csv(methodLabel(value.getApplication_method())),
                        csv(formatAmount(value.getArea_m2())),
                        csv(formatAmount(value.getTank_liters())),
                        csv(value.getNotes()),
                        csv(outcomeLabel(value.getOutcome_status())),
                        csv(value.getOutcome_vigor_score() > 0
                                ? String.valueOf(value.getOutcome_vigor_score()) : ""),
                        csv(value.getOutcome_notes())
                )).append('\n');
            }
        } catch (Exception error) {
            android.widget.Toast.makeText(
                    this,
                    R.string.fertilizer_history_export_failed,
                    android.widget.Toast.LENGTH_LONG
            ).show();
            return;
        }
        viewModel.writeCsv(uri, csvContents.toString(), successful ->
                runOnUiThread(() -> android.widget.Toast.makeText(
                        this,
                        successful ? R.string.fertilizer_history_export_success
                                : R.string.fertilizer_history_export_failed,
                        android.widget.Toast.LENGTH_LONG
                ).show()));
    }

    private String methodLabel(String method) {
        if (method == null) return "";
        switch (method) {
            case "FOLIAR":
                return getString(R.string.fertilization_method_foliar);
            case "SOIL":
                return getString(R.string.fertilization_method_soil);
            case "DRIP":
                return getString(R.string.fertilization_method_drip);
            default:
                return "";
        }
    }

    private String outcomeLabel(String status) {
        if (status == null) return "";
        switch (status) {
            case "IMPROVED":
                return getString(R.string.runtime_outcome_improved);
            case "UNCHANGED":
                return getString(R.string.runtime_outcome_unchanged);
            case "ISSUE":
                return getString(R.string.runtime_outcome_issue);
            default:
                return "";
        }
    }

    private String csv(String value) {
        String safeValue = value == null ? "" : value;
        return "\"" + safeValue.replace("\"", "\"\"") + "\"";
    }
}
