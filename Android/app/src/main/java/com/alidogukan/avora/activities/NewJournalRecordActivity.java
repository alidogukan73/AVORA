package com.alidogukan.avora.activities;

import android.app.DatePickerDialog;
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
import androidx.appcompat.app.AppCompatActivity;
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
    /** Legacy navigation value: photo launches now become observation records with photos attached. */
    public static final String RECORD_TYPE_PHOTO = "photo";
    private static final String[] TYPES = {"observation", "watering", "fertilization", "event"};
    private static final int[] TYPE_CARDS = {R.id.cardRecordObservation, R.id.cardRecordWatering, R.id.cardRecordFertilizer, R.id.cardRecordEvent};
    private final Calendar selectedDateTime = Calendar.getInstance();
    private String zoneId = "";
    private String seasonId = "";
    private String relatedApplicationId = "";
    private String selectedType = TYPES[0];
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
        String initialType = getIntent().getStringExtra(EXTRA_INITIAL_TYPE);
        boolean legacyPhotoLaunch = RECORD_TYPE_PHOTO.equals(initialType);
        dateText = findViewById(R.id.txtNewRecordDate);
        timeText = findViewById(R.id.txtNewRecordTime);
        photoState = findViewById(R.id.txtNewRecordPhotoState);
        noteInput = findViewById(R.id.inputNewRecordNote);
        findViewById(R.id.btnNewRecordBack).setOnClickListener(v -> finish());
        findViewById(R.id.cardNewRecordDate).setOnClickListener(v -> chooseDate());
        findViewById(R.id.cardNewRecordTime).setOnClickListener(v -> chooseTime());
        findViewById(R.id.cardNewRecordPhotoUpload).setOnClickListener(v -> showPhotoSourceDialog());
        findViewById(R.id.btnNewRecordSave).setOnClickListener(v -> save());
        for (int i = 0; i < TYPE_CARDS.length; i++) {
            final int index = i;
            findViewById(TYPE_CARDS[i]).setOnClickListener(v -> selectType(index));
        }
        refreshDateTime();
        selectType(typeIndex(initialType));
        if (legacyPhotoLaunch) {
            photoState.post(this::showPhotoSourceDialog);
        }
    }

    private int typeIndex(String requestedType) {
        if (requestedType == null || requestedType.isBlank()) return 0;
        for (int i = 0; i < TYPES.length; i++) {
            if (TYPES[i].equals(requestedType)) return i;
        }
        return 0;
    }

    private void selectType(int index) {
        selectedType = TYPES[index];
        for (int i = 0; i < TYPE_CARDS.length; i++) {
            MaterialCardView card = findViewById(TYPE_CARDS[i]);
            boolean active = i == index;
            card.setStrokeColor(getColor(active ? R.color.primary : R.color.border));
            card.setStrokeWidth(active ? dp(2) : dp(1));
            card.setCardBackgroundColor(getColor(active ? R.color.surfaceGreen : R.color.card));
        }
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
        View saveButton = findViewById(R.id.btnNewRecordSave);
        saveButton.setEnabled(false);
        viewModel.requireActiveSeasonId(zoneId)
                .addOnSuccessListener(activeSeasonId -> {
                    if (!seasonId.isBlank() && !seasonId.equals(activeSeasonId)) {
                        saveButton.setEnabled(true);
                        Toast.makeText(
                                this,
                                getString(R.string.runtime_season_inactive),
                                Toast.LENGTH_LONG
                        ).show();
                        return;
                    }
                    seasonId = activeSeasonId;
                    String note = noteInput.getText() == null
                            ? "" : noteInput.getText().toString().trim();
                    viewModel.persistRecord(zoneId, seasonId, selectedType, note,
                                    selectedDateTime.getTimeInMillis() / 1000L,
                                    relatedApplicationId, selectedPhotos,
                                    java.util.Collections.emptyList())
                            .addOnSuccessListener(unused -> {
                                clearCapturedPhotos();
                                Toast.makeText(this, R.string.runtime_journal_added, Toast.LENGTH_SHORT).show();
                                finish();
                            })
                            .addOnFailureListener(error -> {
                                saveButton.setEnabled(true);
                                Toast.makeText(this, R.string.runtime_cloud_save_failed, Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(error -> {
                    saveButton.setEnabled(true);
                    Toast.makeText(
                            this,
                            getString(R.string.runtime_start_season_first),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    @Override protected void onDestroy() {
        if (isFinishing()) clearCapturedPhotos();
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
