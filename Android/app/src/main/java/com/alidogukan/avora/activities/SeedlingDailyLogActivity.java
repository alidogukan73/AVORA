package com.alidogukan.avora.activities;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.models.GardenPhoto;
import com.alidogukan.avora.models.SeedlingBatch;
import com.alidogukan.avora.models.SeedlingDailyLog;
import com.alidogukan.avora.models.SeedlingNodeState;
import com.alidogukan.avora.models.SeedlingTelemetry;
import com.alidogukan.avora.models.SeedlingPhotoUpload;
import com.alidogukan.avora.photos.GardenPhotoCapture;
import com.alidogukan.avora.seedling.SeedlingEnvironmentAdvice;
import com.alidogukan.avora.seedling.SeedlingEnvironmentAdviceText;
import com.alidogukan.avora.seedling.SeedlingEnvironmentGuide;
import com.alidogukan.avora.seedling.SeedlingTelemetryFreshnessTicker;
import com.alidogukan.avora.seedling.SeedlingValidation;
import com.alidogukan.avora.ui.GardenPhotoViewerDialog;
import com.alidogukan.avora.viewmodels.SeedlingViewModel;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Collections;
import java.util.Calendar;
import java.util.List;

/** Records a daily manual observation alongside live, traceable sensor guidance. */
public final class SeedlingDailyLogActivity extends EdgeToEdgeActivity {
    public static final String EXTRA_LOG_ID = "seedling_log_id";
    private static final String STATE_SELECTED_PHOTO = "seedling_selected_photo";
    private static final String STATE_PENDING_CAPTURE = "seedling_pending_capture";
    private static final String STATE_SELECTED_CAPTURE = "seedling_selected_capture";
    private static final String STATE_PHOTO_CHANGED = "seedling_photo_changed";
    private static final String STATE_REMOVE_PHOTO = "seedling_remove_photo";
    private static final String STATE_WATERED = "seedling_watered";
    private static final String STATE_FORM_RESTORED = "seedling_form_restored";

    private SeedlingViewModel viewModel;
    private com.alidogukan.avora.viewmodels.DisplayUnitsViewModel displayUnits;
    private SeedlingBatch batch;
    private String batchId;
    private String editingLogId = "";
    private SeedlingDailyLog editingLog;
    private String observedNodeId = "";
    private LiveData<SeedlingNodeState> nodeSource;
    private SeedlingNodeState latestNodeState;
    private SeedlingTelemetryFreshnessTicker freshnessTicker;
    private boolean watered;
    private boolean seededFromLatestLog;
    private boolean formRestored;
    private boolean photoChanged;
    private boolean removeExistingPhoto;
    private boolean saveCompleted;
    private Uri selectedPhotoUri;
    private GardenPhoto currentPhoto;
    private GardenPhotoCapture.Target pendingCameraPhoto;
    private GardenPhotoCapture.Target selectedCameraPhoto;

    private EditText height;
    private EditText leaves;
    private EditText healthy;
    private EditText note;
    private TextView healthyTotal;
    private TextView heightUnit;
    private TextView wateredValue;
    private TextView warningTitle;
    private TextView warningMessage;
    private TextView adviceTitle;
    private TextView adviceMessage;
    private TextView toolbarTitle;
    private MaterialCardView warningCard;
    private MaterialCardView adviceCard;
    private MaterialButton save;
    private ImageView photoPreview;
    private View photoFrame;
    private View photoHint;
    private View photoActions;

    private final ActivityResultLauncher<Uri> camera =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), saved -> {
                GardenPhotoCapture.Target target = pendingCameraPhoto;
                pendingCameraPhoto = null;
                if (!saved || target == null) {
                    if (target != null) target.delete();
                    return;
                }
                discardSelectedCameraPhoto();
                selectedCameraPhoto = target;
                selectPhoto(target.getUri());
            });

    private final ActivityResultLauncher<PickVisualMediaRequest> photoPicker =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri == null) return;
                discardSelectedCameraPhoto();
                selectPhoto(uri);
            });

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_seedling_daily_log);
        batchId = getIntent().getStringExtra(SeedlingBatchDetailActivity.EXTRA_BATCH_ID);
        String requestedLogId = getIntent().getStringExtra(EXTRA_LOG_ID);
        editingLogId = requestedLogId == null ? "" : requestedLogId.trim();
        if (batchId == null || batchId.isBlank()) {
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this).get(SeedlingViewModel.class);
        displayUnits = new ViewModelProvider(this)
                .get(com.alidogukan.avora.viewmodels.DisplayUnitsViewModel.class);
        viewModel.getReadError().observe(this, event -> {
            if (event == null) return;
            Integer message = event.consume();
            if (message != null) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
        freshnessTicker = new SeedlingTelemetryFreshnessTicker(
                this, 45L, () -> {
                    if (editingLogId.isBlank()) renderNode(latestNodeState);
                    else renderStoredSensorSnapshot(editingLog);
                });
        bindViews();
        save.setEnabled(false);
        if (state != null) {
            formRestored = state.getBoolean(STATE_FORM_RESTORED, true);
            watered = state.getBoolean(STATE_WATERED, false);
            restorePhotoState(state);
        }
        if (!editingLogId.isBlank()) {
            toolbarTitle.setText(R.string.seedling_log_edit_title);
            save.setText(R.string.seedling_log_update);
            save.setEnabled(false);
        }
        renderWateringStatus();
        findViewById(R.id.btnBack).setOnClickListener(view -> finish());
        findViewById(R.id.btnSeedlingDailyMore)
                .setOnClickListener(view -> showBatchInformation());
        findViewById(R.id.rowSeedlingWateringStatus)
                .setOnClickListener(view -> showWateringPicker());
        photoFrame.setOnClickListener(view -> openPhotoOrPicker());
        findViewById(R.id.btnSeedlingDailyPhotoChange)
                .setOnClickListener(view -> showPhotoSourceDialog());
        findViewById(R.id.btnSeedlingDailyPhotoDelete)
                .setOnClickListener(view -> removePhoto());
        save.setOnClickListener(view -> submit());

        viewModel.getBatch(batchId).observe(this, value -> {
            batch = value;
            renderBatch();
            observeBatchNode();
        });
        viewModel.getLogs(batchId).observe(this, this::seedLatestObservation);
    }

    private void bindViews() {
        height = findViewById(R.id.inputSeedlingHeight);
        heightUnit = findViewById(R.id.txtSeedlingHeightUnit);
        heightUnit.setText(displayUnits.lengthSymbol());
        leaves = findViewById(R.id.inputSeedlingLeaves);
        healthy = findViewById(R.id.inputSeedlingHealthy);
        note = findViewById(R.id.inputSeedlingNote);
        healthyTotal = findViewById(R.id.txtSeedlingHealthyTotal);
        wateredValue = findViewById(R.id.txtSeedlingWateredValue);
        warningCard = findViewById(R.id.cardSeedlingDailyWarning);
        adviceCard = findViewById(R.id.cardSeedlingDailyAdvice);
        warningTitle = findViewById(R.id.txtSeedlingDailyWarningTitle);
        warningMessage = findViewById(R.id.txtSeedlingDailyWarningMessage);
        adviceTitle = findViewById(R.id.txtSeedlingDailyAdviceTitle);
        adviceMessage = findViewById(R.id.txtSeedlingDailyAdviceMessage);
        toolbarTitle = findViewById(R.id.txtSeedlingDailyToolbarTitle);
        save = findViewById(R.id.btnSaveSeedlingLog);
        photoPreview = findViewById(R.id.imgSeedlingDailyPhoto);
        photoFrame = findViewById(R.id.frameSeedlingDailyPhoto);
        photoHint = findViewById(R.id.layoutSeedlingDailyPhotoHint);
        photoActions = findViewById(R.id.layoutSeedlingDailyPhotoActions);
    }

    private void renderBatch() {
        if (batch == null) {
            save.setEnabled(false);
            return;
        }
        if (healthy.getText().toString().isBlank()) {
            healthy.setText(String.valueOf(batch.getHealthy_count()));
        }
        healthyTotal.setText(getString(R.string.seedling_healthy_total, batch.getSeed_count()));
        save.setEnabled(canSaveCurrentLog());
    }

    private void seedLatestObservation(List<SeedlingDailyLog> values) {
        if (values == null) return;
        if (!editingLogId.isBlank()) {
            editingLog = null;
            for (SeedlingDailyLog value : values) {
                if (editingLogId.equals(value.getLog_id())) {
                    editingLog = value;
                    break;
                }
            }
            if (editingLog == null) {
                Toast.makeText(this, R.string.seedling_log_not_found,
                        Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            stopObservingBatchNode();
            renderStoredSensorSnapshot(editingLog);
            if (seededFromLatestLog) {
                save.setEnabled(canSaveCurrentLog());
                return;
            }
            seededFromLatestLog = true;
            if (!formRestored) {
                height.setText(displayUnits.formatEditableLength(editingLog.getHeight_cm()));
                leaves.setText(String.valueOf(editingLog.getLeaf_count()));
                healthy.setText(String.valueOf(editingLog.getHealthy_count()));
                note.setText(editingLog.getNote());
                watered = editingLog.isWatered();
                renderWateringStatus();
            }
            if (!photoChanged && !removeExistingPhoto) loadExistingPhoto(editingLog);
            save.setEnabled(canSaveCurrentLog());
            return;
        }
        if (seededFromLatestLog) return;
        if (formRestored) {
            seededFromLatestLog = true;
            return;
        }
        if (values.isEmpty()) return;
        seededFromLatestLog = true;
        SeedlingDailyLog latest = values.get(0);
        if (height.getText().toString().isBlank()) {
            height.setText(displayUnits.formatEditableLength(latest.getHeight_cm()));
        }
        if (leaves.getText().toString().isBlank()) {
            leaves.setText(String.valueOf(latest.getLeaf_count()));
        }
    }

    private void observeBatchNode() {
        if (batch == null) return;
        if (!editingLogId.isBlank()) {
            stopObservingBatchNode();
            renderStoredSensorSnapshot(editingLog);
            return;
        }
        if (!viewModel.shouldObserveLiveTelemetry(batch)) {
            stopObservingBatchNode();
            renderArchivedSensorState();
            return;
        }
        String nodeId = batch.getNode_id().isBlank() ? "seedling-001" : batch.getNode_id();
        if (nodeId.equals(observedNodeId)) return;
        if (nodeSource != null) nodeSource.removeObservers(this);
        observedNodeId = nodeId;
        latestNodeState = null;
        freshnessTicker.update(null);
        renderNode(null);
        nodeSource = viewModel.getNode(nodeId);
        nodeSource.observe(this, value -> {
            latestNodeState = value;
            freshnessTicker.update(value == null ? null : value.getLatest());
            renderNode(value);
        });
    }

    private void stopObservingBatchNode() {
        if (nodeSource != null) nodeSource.removeObservers(this);
        nodeSource = null;
        observedNodeId = "";
        latestNodeState = null;
        freshnessTicker.update(null);
    }

    private void renderArchivedSensorState() {
        warningCard.setVisibility(View.VISIBLE);
        warningTitle.setText(R.string.seedling_archived_status);
        warningMessage.setText(R.string.seedling_archived_telemetry_disabled);
        adviceCard.setVisibility(View.GONE);
    }

    private void renderNode(SeedlingNodeState value) {
        if (batch != null
                && !viewModel.shouldObserveLiveTelemetry(batch)) {
            renderArchivedSensorState();
            return;
        }
        SeedlingTelemetry telemetry = value == null ? null : value.getLatest();
        long nowMillis = System.currentTimeMillis();
        boolean fresh = telemetry != null && telemetry.isFresh(
                nowMillis / 1000L, 45L);
        SeedlingEnvironmentGuide.Assessment assessment =
                SeedlingEnvironmentGuide.assess(
                        batch == null ? "" : batch.getPlant_type(),
                        batch == null ? "" : batch.getStage(),
                        fresh ? telemetry : null,
                        localHour(nowMillis));
        renderWarning(fresh, assessment);
        renderAdvice(fresh, assessment);
    }

    private void renderStoredSensorSnapshot(@Nullable SeedlingDailyLog log) {
        if (log == null) {
            warningCard.setVisibility(View.VISIBLE);
            warningTitle.setText(R.string.seedling_daily_snapshot_loading_title);
            warningMessage.setText(R.string.seedling_daily_snapshot_loading_message);
            adviceCard.setVisibility(View.GONE);
            return;
        }
        SeedlingTelemetry telemetry = log.storedSensorSnapshot();
        if (telemetry == null) {
            warningCard.setVisibility(View.VISIBLE);
            warningTitle.setText(R.string.seedling_daily_snapshot_missing_title);
            warningMessage.setText(R.string.seedling_daily_snapshot_missing_message);
            adviceCard.setVisibility(View.GONE);
            return;
        }
        long capturedMillis = Math.max(0L, log.getSensor_captured_at_epoch()) * 1000L;
        boolean freshAtCapture = log.isSensor_snapshot_fresh();
        SeedlingEnvironmentGuide.Assessment assessment = SeedlingEnvironmentGuide.assess(
                batch == null ? "" : batch.getPlant_type(),
                batch == null ? "" : batch.getStage(),
                freshAtCapture ? telemetry : null, localHour(capturedMillis));
        renderWarning(freshAtCapture, assessment);
        renderAdvice(freshAtCapture, assessment);
    }

    private void renderWarning(boolean fresh,
                               SeedlingEnvironmentGuide.Assessment assessment) {
        warningCard.setVisibility(View.VISIBLE);
        if (!fresh) {
            warningTitle.setText(R.string.seedling_daily_warning_stale_title);
            warningMessage.setText(R.string.seedling_daily_warning_stale_message);
            return;
        }
        SeedlingEnvironmentAdvice.Advice advice =
                SeedlingEnvironmentAdvice.from(assessment);
        if (!advice.needsAction()) {
            warningCard.setVisibility(View.GONE);
            return;
        }
        warningTitle.setText(R.string.seedling_environment_warning_title);
        warningMessage.setText(SeedlingEnvironmentAdviceText.issueSummary(this, advice));
    }

    private void renderAdvice(boolean fresh,
                              SeedlingEnvironmentGuide.Assessment assessment) {
        adviceCard.setVisibility(View.VISIBLE);
        if (!fresh) {
            adviceTitle.setText(R.string.seedling_daily_advice_waiting_title);
            adviceMessage.setText(R.string.seedling_daily_advice_waiting_message);
            return;
        }
        SeedlingEnvironmentAdvice.Advice advice =
                SeedlingEnvironmentAdvice.from(assessment);
        String action = SeedlingEnvironmentAdviceText.title(this, advice);
        adviceTitle.setText(getString(R.string.seedling_daily_recommended_action, action));
        adviceMessage.setText(SeedlingEnvironmentAdviceText.message(this, advice));
    }

    private static int localHour(long nowMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(nowMillis);
        return calendar.get(Calendar.HOUR_OF_DAY);
    }

    private void showWateringPicker() {
        String[] options = {
                getString(R.string.seedling_watering_done),
                getString(R.string.seedling_watering_not_done)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.seedling_watering_picker_title)
                .setSingleChoiceItems(options, watered ? 0 : 1, (dialog, which) -> {
                    watered = which == 0;
                    renderWateringStatus();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.settings_cancel, null)
                .show();
    }

    private void renderWateringStatus() {
        wateredValue.setText(watered
                ? R.string.seedling_watering_done : R.string.seedling_watering_not_done);
        wateredValue.setTextColor(ContextCompat.getColor(this,
                watered ? R.color.primary : R.color.textSecondary));
    }

    private void showBatchInformation() {
        if (batch == null) return;
        String area = batch.getArea().isBlank() ? "—" : batch.getArea();
        String node = batch.getNode_id().isBlank() ? "seedling-001" : batch.getNode_id();
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.seedling_batch_information_title)
                .setMessage(getString(R.string.seedling_batch_information, area,
                        batch.getSeed_count(), batch.getTray_cell_count(), node))
                .setPositiveButton(R.string.seedling_close, null)
                .show();
    }

    private void submit() {
        double displayedHeight = decimal(height);
        double heightValue = displayUnits.lengthToCentimeters(displayedHeight);
        int leavesValue = integer(leaves);
        int healthyValue = integer(healthy);
        String noteValue = note.getText().toString().trim();
        boolean exceedsBatch = batch != null && healthyValue > batch.getSeed_count();
        boolean editing = !editingLogId.isBlank();
        boolean maySave = editing
                ? viewModel.canEditDailyLog(batch, editingLog)
                : batch != null && batch.isActive();
        if (!maySave
                || !Double.isFinite(heightValue)
                || heightValue < 0d || heightValue > SeedlingValidation.MAX_HEIGHT_CM
                || leavesValue < 0 || leavesValue > SeedlingValidation.MAX_LEAF_COUNT
                || healthyValue < 0 || exceedsBatch
                || noteValue.length() > SeedlingValidation.MAX_NOTE_LENGTH) {
            Toast.makeText(this, R.string.seedling_invalid_log, Toast.LENGTH_LONG).show();
            return;
        }
        save.setEnabled(false);
        if (photoChanged && selectedPhotoUri != null) {
            Toast.makeText(this, R.string.seedling_daily_photo_uploading,
                    Toast.LENGTH_SHORT).show();
            viewModel.saveDailyPhoto(selectedPhotoUri, batchId)
                    .addOnSuccessListener(uploaded -> persistLog(heightValue, leavesValue,
                            healthyValue, uploaded))
                    .addOnFailureListener(error -> {
                        save.setEnabled(canSaveCurrentLog());
                        Toast.makeText(this, R.string.runtime_photo_add_failed,
                                Toast.LENGTH_LONG).show();
                    });
            return;
        }
        persistLog(heightValue, leavesValue, healthyValue, null);
    }

    private void persistLog(double heightValue, int leavesValue, int healthyValue,
                            @Nullable SeedlingPhotoUpload uploaded) {
        // Keep the pre-update record stable. The Firebase observer may replace
        // editingLog with the updated value before the success listener runs.
        final SeedlingDailyLog previousLog = editingLog;
        final boolean editing = previousLog != null;
        String photoId = uploaded == null ? retainedPhotoId() : uploaded.getPhotoId();
        String storagePath = uploaded == null
                ? retainedPhotoStoragePath() : uploaded.getStoragePath();
        Task<Void> operation = !editing
                ? viewModel.saveDailyLog(batchId, heightValue, leavesValue, healthyValue,
                        watered, note.getText().toString().trim(), photoId, storagePath,
                        latestNodeState == null ? null : latestNodeState.getLatest())
                : viewModel.updateDailyLog(previousLog, heightValue, leavesValue, healthyValue,
                        watered, note.getText().toString().trim(), photoId, storagePath);
        operation
                .addOnSuccessListener(unused -> {
                    if (previousLog != null && previousLog.hasPhoto()
                            && (removeExistingPhoto || uploaded != null)) {
                        viewModel.deleteDailyPhoto(previousLog);
                    }
                    saveCompleted = true;
                    discardSelectedCameraPhoto();
                    Toast.makeText(this, !editing
                                    ? R.string.seedling_log_saved : R.string.seedling_log_updated,
                            Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(error -> {
                    if (uploaded != null) viewModel.deleteDailyPhoto(uploaded);
                    save.setEnabled(canSaveCurrentLog());
                    Toast.makeText(this, !editing
                                    ? R.string.seedling_log_failed
                                    : R.string.seedling_log_update_failed,
                            Toast.LENGTH_LONG).show();
                });
    }

    private boolean canSaveCurrentLog() {
        if (editingLogId.isBlank()) return batch != null && batch.isActive();
        return viewModel.canEditDailyLog(batch, editingLog);
    }

    private String retainedPhotoId() {
        return editingLog != null && editingLog.hasPhoto() && !removeExistingPhoto
                ? editingLog.getPhoto_id() : "";
    }

    private String retainedPhotoStoragePath() {
        return editingLog != null && editingLog.hasPhoto() && !removeExistingPhoto
                ? editingLog.getPhoto_storage_path() : "";
    }

    private void showPhotoSourceDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.runtime_add_photo)
                .setItems(new String[]{
                        getString(R.string.runtime_take_photo),
                        getString(R.string.runtime_choose_gallery)
                }, (dialog, which) -> {
                    if (which == 0) launchCamera();
                    else photoPicker.launch(new PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                            .build());
                })
                .show();
    }

    private void launchCamera() {
        try {
            pendingCameraPhoto = GardenPhotoCapture.create(this);
            camera.launch(pendingCameraPhoto.getUri());
        } catch (Exception error) {
            if (pendingCameraPhoto != null) pendingCameraPhoto.delete();
            pendingCameraPhoto = null;
            Toast.makeText(this, R.string.runtime_photo_add_failed,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void selectPhoto(Uri uri) {
        selectedPhotoUri = uri;
        photoChanged = true;
        removeExistingPhoto = false;
        currentPhoto = null;
        photoPreview.setImageURI(null);
        photoPreview.setImageURI(uri);
        photoPreview.setVisibility(View.VISIBLE);
        photoHint.setVisibility(View.GONE);
        photoActions.setVisibility(View.VISIBLE);
    }

    private void removePhoto() {
        discardSelectedCameraPhoto();
        selectedPhotoUri = null;
        currentPhoto = null;
        photoChanged = true;
        removeExistingPhoto = editingLog != null && editingLog.hasPhoto();
        renderEmptyPhoto();
    }

    private void openPhotoOrPicker() {
        if (currentPhoto != null && selectedPhotoUri == null && !removeExistingPhoto) {
            GardenPhotoViewerDialog.show(this,
                    Collections.singletonList(currentPhoto), currentPhoto.getId());
        } else if (selectedPhotoUri != null) {
            Toast.makeText(this, R.string.seedling_daily_photo_required_for_view,
                    Toast.LENGTH_SHORT).show();
        } else {
            showPhotoSourceDialog();
        }
    }

    private void loadExistingPhoto(SeedlingDailyLog log) {
        if (log == null || !log.hasPhoto()) {
            renderEmptyPhoto();
            return;
        }
        viewModel.loadDailyPhoto(log)
                .addOnSuccessListener(photo -> {
                    if (photoChanged || removeExistingPhoto || isFinishing()) return;
                    currentPhoto = photo;
                    photoPreview.setImageURI(Uri.fromFile(
                            new java.io.File(photo.getLocal_path())));
                    photoPreview.setVisibility(View.VISIBLE);
                    photoHint.setVisibility(View.GONE);
                    photoActions.setVisibility(View.VISIBLE);
                })
                .addOnFailureListener(error -> {
                    if (!photoChanged && !isFinishing()) {
                        Toast.makeText(this, R.string.seedling_daily_photo_load_failed,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void renderEmptyPhoto() {
        photoPreview.setImageDrawable(null);
        photoPreview.setVisibility(View.GONE);
        photoHint.setVisibility(View.VISIBLE);
        photoActions.setVisibility(View.GONE);
    }

    private void discardSelectedCameraPhoto() {
        if (selectedCameraPhoto != null) selectedCameraPhoto.delete();
        selectedCameraPhoto = null;
    }

    private void restorePhotoState(Bundle state) {
        photoChanged = state.getBoolean(STATE_PHOTO_CHANGED, false);
        removeExistingPhoto = state.getBoolean(STATE_REMOVE_PHOTO, false);
        pendingCameraPhoto = restoreCapture(state.getString(STATE_PENDING_CAPTURE));
        selectedCameraPhoto = restoreCapture(state.getString(STATE_SELECTED_CAPTURE));
        String selected = state.getString(STATE_SELECTED_PHOTO, "");
        if (!selected.isBlank()) {
            selectedPhotoUri = selectedCameraPhoto == null
                    ? Uri.parse(selected) : selectedCameraPhoto.getUri();
            selectPhoto(selectedPhotoUri);
        } else if (removeExistingPhoto) {
            renderEmptyPhoto();
        }
    }

    @Nullable private GardenPhotoCapture.Target restoreCapture(String path) {
        try {
            return GardenPhotoCapture.restore(this, path);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean(STATE_PHOTO_CHANGED, photoChanged);
        state.putBoolean(STATE_REMOVE_PHOTO, removeExistingPhoto);
        state.putBoolean(STATE_WATERED, watered);
        state.putBoolean(STATE_FORM_RESTORED, true);
        state.putString(STATE_SELECTED_PHOTO,
                selectedPhotoUri == null ? "" : selectedPhotoUri.toString());
        state.putString(STATE_PENDING_CAPTURE, pendingCameraPhoto == null
                ? "" : pendingCameraPhoto.getAbsolutePath());
        state.putString(STATE_SELECTED_CAPTURE, selectedCameraPhoto == null
                ? "" : selectedCameraPhoto.getAbsolutePath());
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        if (isFinishing() && !saveCompleted) {
            if (pendingCameraPhoto != null) pendingCameraPhoto.delete();
            discardSelectedCameraPhoto();
        }
        super.onDestroy();
    }

    private static int integer(EditText input) {
        try {
            return Integer.parseInt(input.getText().toString().trim());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static double decimal(EditText input) {
        String value = input.getText().toString().trim().replace(',', '.');
        if (value.isBlank()) return 0d;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return -1d;
        }
    }
}
