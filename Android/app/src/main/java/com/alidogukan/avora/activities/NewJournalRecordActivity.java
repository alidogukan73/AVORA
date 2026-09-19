package com.alidogukan.avora.activities;

import android.app.DatePickerDialog;
import android.content.Intent;
import com.alidogukan.avora.models.GardenSeason;
import android.app.TimePickerDialog;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.photos.GardenPhotoCapture;
import com.alidogukan.avora.viewmodels.PlantJournalViewModel;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Manual season record entry point for a single plant journal. */
public final class NewJournalRecordActivity extends EdgeToEdgeActivity {
    public static final String EXTRA_ZONE_ID = "zone_id";
    public static final String EXTRA_SEASON_ID = "season_id";
    public static final String EXTRA_INITIAL_TYPE = "initial_record_type";
    public static final String EXTRA_RELATED_APPLICATION_ID = "related_application_id";
    public static final String RECORD_TYPE_PHOTO = "photo";
    private static final String[] TYPES = {"observation", "photo", "event"};
    private static final int[] TYPE_CARDS = {R.id.cardRecordObservation, R.id.cardRecordPhoto, R.id.cardRecordEvent};
    private final Calendar selectedDateTime = Calendar.getInstance();
    private String zoneId = "";
    private String seasonId = "";
    private String relatedApplicationId = "";
    private String selectedType = TYPES[0];
    private String milestoneType = "special";
    private GardenSeason currentSeason;
    private static final int MAX_PHOTOS_PER_RECORD = 5;
    private final List<Uri> selectedPhotos = new ArrayList<>();
    private final List<GardenPhotoCapture.Target> capturedPhotos = new ArrayList<>();
    private GardenPhotoCapture.Target pendingCameraPhoto;
    private TextView dateText, timeText, photoState;
    private TextInputEditText noteInput;
    private PlantJournalViewModel viewModel;

    private final ActivityResultLauncher<PickVisualMediaRequest> photoPicker =
            registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS_PER_RECORD), uris -> {
                if (uris == null || uris.isEmpty()) return;
                clearCapturedPhotos();
                selectedPhotos.clear();
                selectedPhotos.addAll(uris.subList(0, Math.min(MAX_PHOTOS_PER_RECORD, uris.size())));
                showSelectedPhotoState();
            });

    private final ActivityResultLauncher<Uri> photoCamera =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), saved -> {
                GardenPhotoCapture.Target target = pendingCameraPhoto;
                pendingCameraPhoto = null;
                if (!saved || target == null) {
                    if (target != null) target.delete();
                    return;
                }
                if (selectedPhotos.size() >= MAX_PHOTOS_PER_RECORD) {
                    target.delete();
                    Toast.makeText(this, R.string.runtime_photo_limit, Toast.LENGTH_SHORT).show();
                    return;
                }
                capturedPhotos.add(target);
                selectedPhotos.add(target.getUri());
                showSelectedPhotoState();
            });

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_new_journal_record);

        viewModel = new ViewModelProvider(this).get(PlantJournalViewModel.class);
        zoneId = getIntent().getStringExtra(EXTRA_ZONE_ID);
        if (zoneId == null) zoneId = "";
        seasonId = getIntent().getStringExtra(EXTRA_SEASON_ID);
        if (seasonId == null) seasonId = "";
        relatedApplicationId = getIntent().getStringExtra(EXTRA_RELATED_APPLICATION_ID);
        if (relatedApplicationId == null) relatedApplicationId = "";
        if (state != null) seasonId = state.getString("record_season", seasonId);
        String initialType = getIntent().getStringExtra(EXTRA_INITIAL_TYPE);
        if ("watering".equals(initialType) || "fertilization".equals(initialType)) {
            Intent intent = new Intent(this, "watering".equals(initialType)
                    ? AIAssistantActivity.class : FertilizationZoneDetailActivity.class);
            intent.putExtra("zone_id", zoneId);
            startActivity(intent);
            finish();
            return;
        }
        viewModel.getSeasons(zoneId).observe(this, values -> {
            currentSeason = null;
            if (values == null) return;
            for (GardenSeason value : values) {
                if ((seasonId.isBlank() || seasonId.equals(value.getSeason_id()))
                        && com.alidogukan.avora.models.SeasonStatus.ACTIVE.equals(value.getStatus())) {
                    currentSeason = value;
                    seasonId = value.getSeason_id();
                    break;
                }
            }
        });
        if (viewModel.isJournalMilestone(initialType)) milestoneType = initialType;
        dateText = findViewById(R.id.txtNewRecordDate);
        timeText = findViewById(R.id.txtNewRecordTime);
        photoState = findViewById(R.id.txtNewRecordPhotoState);
        noteInput = findViewById(R.id.inputNewRecordNote);
        findViewById(R.id.btnNewRecordBack).setOnClickListener(v -> requestExit());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { requestExit(); }
        });
        findViewById(R.id.cardNewRecordDate).setOnClickListener(v -> chooseDate());
        findViewById(R.id.cardNewRecordTime).setOnClickListener(v -> chooseTime());
        findViewById(R.id.cardNewRecordPhotoUpload).setOnClickListener(v -> showPhotoSourceDialog());
        findViewById(R.id.btnNewRecordSave).setOnClickListener(v -> save());
        for (int i = 0; i < TYPE_CARDS.length; i++) {
            final int index = i;
            findViewById(TYPE_CARDS[i]).setOnClickListener(v -> {
                if (index == 2) chooseMilestone();
                else selectType(index);
            });
        }
        if (state != null) {
            selectedDateTime.setTimeInMillis(state.getLong("record_time", selectedDateTime.getTimeInMillis()));
            milestoneType = state.getString("milestone_type", milestoneType);
            initialType = state.getString("record_type", initialType);
            seasonId = state.getString("record_season", seasonId);
            ArrayList<String> photos = state.getStringArrayList("record_photos");
            if (photos != null) for (String uri : photos) selectedPhotos.add(Uri.parse(uri));
            ArrayList<String> captures = state.getStringArrayList("record_captures");
            if (captures != null) for (String path : captures) {
                GardenPhotoCapture.Target capture = restoreCapture(path);
                if (capture != null) capturedPhotos.add(capture);
            }
            pendingCameraPhoto = restoreCapture(state.getString("pending_capture"));
        }
        refreshDateTime();
        selectType(typeIndex(initialType));
        if (!selectedPhotos.isEmpty()) showSelectedPhotoState();
        viewModel.getRecordSaving().observe(this, saving -> setFormEnabled(!Boolean.TRUE.equals(saving)));
        viewModel.getRecordSaveResult().observe(this, result -> {
            if (result == null) return;
            viewModel.consumeRecordSaveResult();
            if (result.isSuccessful()) {
                clearCapturedPhotos();
                Toast.makeText(this, R.string.runtime_journal_added, Toast.LENGTH_SHORT).show();
                finish();
            } else {
                String message = result.getException() == null ? "" : result.getException().getMessage();
                int error = "JOURNAL_SEASON_INACTIVE".equals(message) ? R.string.runtime_season_inactive
                        : "JOURNAL_DATE_INVALID".equals(message) ? R.string.journal_date_invalid
                        : R.string.runtime_cloud_save_failed;
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private int typeIndex(String requestedType) {
        if (viewModel.isJournalMilestone(requestedType)) return 2;
        if (requestedType == null || requestedType.isBlank()) return 0;
        for (int i = 0; i < TYPES.length; i++) {
            if (TYPES[i].equals(requestedType)) return i;
        }
        return 0;
    }

    private void selectType(int index) {
        selectedType = index == 2 ? milestoneType : TYPES[index];
        TextView label = findViewById(R.id.txtJournalMilestoneType);
        String[] codes = {"planting", "flowering", "first_product", "harvest", "special"};
        int[] labels = {R.string.runtime_event_planting, R.string.runtime_event_flowering,
                R.string.runtime_event_first_product, R.string.runtime_event_harvest, R.string.runtime_event_special};
        for (int i = 0; i < codes.length; i++) {
            if (codes[i].equals(milestoneType)) label.setText(labels[i]);
        }
        for (int i = 0; i < TYPE_CARDS.length; i++) {
            MaterialCardView card = findViewById(TYPE_CARDS[i]);
            boolean active = i == index;
            card.setStrokeColor(getColor(active ? R.color.primary : R.color.border));
            card.setStrokeWidth(active ? dp(2) : dp(1));
            card.setCardBackgroundColor(getColor(active ? R.color.surfaceGreen : R.color.card));
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putLong("record_time", selectedDateTime.getTimeInMillis());
        state.putString("record_type", selectedType);
        state.putString("milestone_type", milestoneType);
        state.putString("record_season", seasonId);
        ArrayList<String> photos = new ArrayList<>();
        for (Uri uri : selectedPhotos) photos.add(uri.toString());
        state.putStringArrayList("record_photos", photos);
        ArrayList<String> captures = new ArrayList<>();
        for (GardenPhotoCapture.Target photo : capturedPhotos) captures.add(photo.getAbsolutePath());
        state.putStringArrayList("record_captures", captures);
        state.putString("pending_capture", pendingCameraPhoto == null ? "" : pendingCameraPhoto.getAbsolutePath());
        super.onSaveInstanceState(state);
    }

    private void chooseMilestone() {
        String[] types = {"planting", "flowering", "first_product", "harvest", "special"};
        String[] labels = {getString(R.string.runtime_event_planting), getString(R.string.runtime_event_flowering),
                getString(R.string.runtime_event_first_product), getString(R.string.runtime_event_harvest),
                getString(R.string.runtime_event_special)};
        new MaterialAlertDialogBuilder(this).setTitle(R.string.journal_milestone)
                .setItems(labels, (dialog, which) -> {
                    milestoneType = types[which];
                    selectType(2);
                }).show();
    }

    private void chooseDate() {
        new DatePickerDialog(this, (view, year, month, day) -> {
            selectedDateTime.set(year, month, day);
            refreshDateTime();
        }, selectedDateTime.get(Calendar.YEAR), selectedDateTime.get(Calendar.MONTH), selectedDateTime.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void chooseTime() {
        new TimePickerDialog(this, (view, hour, minute) -> {
            selectedDateTime.set(Calendar.HOUR_OF_DAY, hour);
            selectedDateTime.set(Calendar.MINUTE, minute);
            selectedDateTime.set(Calendar.SECOND, 0);
            selectedDateTime.set(Calendar.MILLISECOND, 0);
            refreshDateTime();
        }, selectedDateTime.get(Calendar.HOUR_OF_DAY), selectedDateTime.get(Calendar.MINUTE), true).show();
    }

    private void showPhotoSourceDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.runtime_add_photo)
                .setItems(new String[]{
                        getString(R.string.runtime_take_photo),
                        getString(R.string.runtime_choose_gallery)
                }, (dialog, which) -> {
                    if (which == 0) {
                        launchCamera();
                    } else {
                        choosePhotoFromGallery();
                    }
                })
                .show();
    }

    private void choosePhotoFromGallery() {
        photoPicker.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
    }

    private void launchCamera() {
        if (selectedPhotos.size() >= MAX_PHOTOS_PER_RECORD) {
            Toast.makeText(this, R.string.runtime_photo_limit, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            pendingCameraPhoto = GardenPhotoCapture.create(this);
            photoCamera.launch(pendingCameraPhoto.getUri());
        } catch (Exception error) {
            if (pendingCameraPhoto != null) pendingCameraPhoto.delete();
            pendingCameraPhoto = null;
            Toast.makeText(this, R.string.runtime_photo_add_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void showSelectedPhotoState() {
        if (photoState == null) return;
        int count = selectedPhotos.size();
        photoState.setText(getString(
                R.string.runtime_icon_label,
                getString(R.string.symbol_check),
                getResources().getQuantityString(
                        R.plurals.runtime_photos_selected_limit,
                        count,
                        count)));
    }

    private void refreshDateTime() {
        dateText.setText(new SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(selectedDateTime.getTime()));
        timeText.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(selectedDateTime.getTime()));
    }

    private void save() {
        if (zoneId.isBlank()) { Toast.makeText(this, R.string.runtime_zone_not_found, Toast.LENGTH_SHORT).show(); return; }
        String note = noteInput.getText() == null ? "" : noteInput.getText().toString().trim();
        if (viewModel.isManualJournalEvent(selectedType) && note.isBlank()) {
            noteInput.setError(getString(R.string.journal_note_required));
            noteInput.requestFocus();
            return;
        }
        if (currentSeason == null) {
            Toast.makeText(this, R.string.runtime_start_season_first, Toast.LENGTH_LONG).show();
            return;
        }
        if (!viewModel.validJournalDate(selectedDateTime.getTimeInMillis() / 1000L,
                currentSeason.getStarted_at_epoch(), System.currentTimeMillis() / 1000L)) {
            Toast.makeText(this, R.string.journal_date_invalid, Toast.LENGTH_LONG).show();
            return;
        }
        boolean hasPhoto = !selectedPhotos.isEmpty();
        if (RECORD_TYPE_PHOTO.equals(selectedType) && !hasPhoto) {
            Toast.makeText(this, R.string.runtime_photo_required, Toast.LENGTH_SHORT).show();
            return;
        }
        seasonId = currentSeason.getSeason_id();
        final String recordType = selectedType;
        final long recordTime = selectedDateTime.getTimeInMillis() / 1000L;
        final List<Uri> recordPhotos = new ArrayList<>(selectedPhotos);
        viewModel.persistRecord(zoneId, seasonId, recordType, note, recordTime,
                relatedApplicationId, recordPhotos, java.util.Collections.emptyList());
    }

    private GardenPhotoCapture.Target restoreCapture(String path) {
        try { return GardenPhotoCapture.restore(this, path); }
        catch (Exception ignored) { return null; }
    }

    private void requestExit() {
        if (Boolean.TRUE.equals(viewModel.getRecordSaving().getValue())) {
            Toast.makeText(this, R.string.journal_save_in_progress, Toast.LENGTH_SHORT).show();
        } else finish();
    }

    private void setFormEnabled(boolean enabled) {
        int[] controls = {R.id.btnNewRecordSave, R.id.cardNewRecordDate, R.id.cardNewRecordTime,
                R.id.cardNewRecordPhotoUpload, R.id.inputNewRecordNote};
        for (int id : controls) findViewById(id).setEnabled(enabled);
        for (int id : TYPE_CARDS) findViewById(id).setEnabled(enabled);
    }

    @Override protected void onDestroy() {
        if (isFinishing() && !Boolean.TRUE.equals(viewModel.getRecordSaving().getValue())) clearCapturedPhotos();
        super.onDestroy();
    }

    private void clearCapturedPhotos() {
        for (GardenPhotoCapture.Target target : capturedPhotos) target.delete();
        capturedPhotos.clear();
        if (pendingCameraPhoto != null) pendingCameraPhoto.delete();
        pendingCameraPhoto = null;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
