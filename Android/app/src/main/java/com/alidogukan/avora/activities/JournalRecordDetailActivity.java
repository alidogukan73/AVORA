package com.alidogukan.avora.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.models.FertilizerApplication;
import com.alidogukan.avora.models.GardenPhoto;
import com.alidogukan.avora.models.WateringHistory;
import com.alidogukan.avora.photos.GardenPhotoCapture;
import com.alidogukan.avora.photos.JournalPhotoRecordFilter;
import com.alidogukan.avora.ui.GardenPhotoViewerDialog;
import com.alidogukan.avora.ui.PrimaryBottomNavigation;
import com.alidogukan.avora.viewmodels.PlantJournalViewModel;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;

/** Detail view for one live plant-journal timeline record. */
public class JournalRecordDetailActivity extends EdgeToEdgeActivity {
    public static final String EXTRA_PHOTO_ID = "photo_id";
    private PlantJournalViewModel viewModel;
    private String manualEventId = "", manualEventType = "", zoneId = "", seasonId = "", currentDetail = "";
    private String selectedPhotoPath = "", selectedPhotoId = "", selectedAdvice = "", photoGroupId = "";
    private boolean seasonReadOnly;
    private boolean photoSaving;
    private boolean missingAnalysisFallbackOpened;
    private GardenPhoto selectedPhotoRecord;
    private long recordEpoch;
    private LinearLayout photosLayout, linksLayout;
    private TextView photosTitle, assistantHeading, assistantText;
    private List<FertilizerApplication> fertilizers = new ArrayList<>();
    private List<WateringHistory> wateringRecords = new ArrayList<>();
    private List<GardenPhoto> relatedPhotos = new ArrayList<>();
    private List<GardenPhoto> cloudPhotos = new ArrayList<>();
    private GardenPhotoCapture.Target pendingCameraPhoto;

    private final ActivityResultLauncher<PickVisualMediaRequest> extraPhotoPicker =
            registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(5), uris -> {
                if (uris == null || uris.isEmpty()) return;
                saveExtraPhotos(uris, null);
            });
    private final ActivityResultLauncher<Uri> extraPhotoCamera =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), saved -> {
                GardenPhotoCapture.Target target = pendingCameraPhoto;
                pendingCameraPhoto = null;
                if (!saved || target == null) {
                    if (target != null) target.delete();
                    return;
                }
                saveExtraPhotos(java.util.Collections.singletonList(target.getUri()), target);
            });
    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_journal_record_detail);
        viewModel = new ViewModelProvider(this).get(PlantJournalViewModel.class);
        PrimaryBottomNavigation.bind(this, PrimaryBottomNavigation.PLANTS);
        bindIntent();
        bindViews();
        if (state != null) {
            try { pendingCameraPhoto = GardenPhotoCapture.restore(this, state.getString("pending_capture")); }
            catch (Exception ignored) { pendingCameraPhoto = null; }
        }
        viewModel.getRecordSaving().observe(this, saving -> {
            photoSaving = Boolean.TRUE.equals(saving);
            findViewById(R.id.btnRecordDelete).setEnabled(!photoSaving);
            findViewById(R.id.btnRecordEdit).setEnabled(!photoSaving);
        });
        viewModel.getRecordSaveResult().observe(this, result -> {
            if (result == null) return;
            viewModel.consumeRecordSaveResult();
            if (result.isSuccessful()) finish();
            else Toast.makeText(this, R.string.runtime_cloud_save_failed, Toast.LENGTH_LONG).show();
        });
        viewModel.getAppendedPhotoResult().observe(this, result -> {
            if (result == null) return;
            viewModel.consumeAppendedPhotoResult();
            if (result.isSuccessful()) {
                photoGroupId = result.getResult();
                getIntent().putExtra("photo_group_id", photoGroupId);
                renderPhotosAndAnalysis();
                Toast.makeText(this, R.string.runtime_photo_added, Toast.LENGTH_SHORT).show();
            } else Toast.makeText(this, R.string.runtime_photo_add_failed, Toast.LENGTH_LONG).show();
        });
        renderStaticDetail();
        renderPhotosAndAnalysis();
        if (!seasonId.isBlank()) viewModel.getSeasons(zoneId).observe(this, seasons -> {
            if (seasons == null) return;
            boolean writable = false;
            for (com.alidogukan.avora.models.GardenSeason season : seasons) {
                if (seasonId.equals(season.getSeason_id())) {
                    writable = com.alidogukan.avora.models.SeasonStatus.isActive(season.getStatus());
                    break;
                }
            }
            seasonReadOnly = !writable;
            renderStaticDetail();
            renderPhotosAndAnalysis();
        });
        viewModel.getFertilizerHistory().observe(this, values -> {
            fertilizers = values == null ? new ArrayList<>() : values;
            renderLinks();
        });
        viewModel.getWateringHistory().observe(this, values -> {
            wateringRecords = values == null ? new ArrayList<>() : values;
            renderLinks();
        });
        viewModel.getPhotoMetadata().observe(this, values -> {
            cloudPhotos = values == null ? new ArrayList<>() : values;
            if (openAssistantWhenRequestedAnalysisIsMissing()) return;
            renderPhotosAndAnalysis();
        });
    }

    private void bindIntent() {
        manualEventId = safe(getIntent().getStringExtra("manual_event_id"));
        manualEventType = safe(getIntent().getStringExtra("manual_event_type"));
        zoneId = safe(getIntent().getStringExtra("zone_id"));
        seasonId = safe(getIntent().getStringExtra("season_id"));
        currentDetail = safe(getIntent().getStringExtra("detail"));
        selectedPhotoPath = safe(getIntent().getStringExtra("photo_path"));
        selectedPhotoId = safe(getIntent().getStringExtra(EXTRA_PHOTO_ID));
        photoGroupId = safe(getIntent().getStringExtra("photo_group_id"));
        if (photoGroupId.isBlank() && !manualEventId.isBlank()) photoGroupId = "journal_record_" + manualEventId;
        selectedAdvice = safe(getIntent().getStringExtra("advice"));
        seasonReadOnly = getIntent().getBooleanExtra("season_read_only", false);
        recordEpoch = getIntent().getLongExtra("time", System.currentTimeMillis() / 1000L);
    }

    private void bindViews() {
        photosLayout = findViewById(R.id.layoutRecordPhotos);
        linksLayout = findViewById(R.id.layoutRecordLinks);
        photosTitle = findViewById(R.id.txtPhotosTitle);
        assistantHeading = findViewById(R.id.txtAssistantHeading);
        assistantText = findViewById(R.id.txtRecordAssistant);
        findViewById(R.id.btnRecordBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnRecordEdit).setOnClickListener(v -> editManualRecord());
        findViewById(R.id.btnRecordDelete).setOnClickListener(v -> confirmDelete());
    }

    private void renderStaticDetail() {
        String title = safe(getIntent().getStringExtra("title"));
        String icon = safe(getIntent().getStringExtra("icon"));
        ((TextView) findViewById(R.id.txtRecordTitle)).setText(title.isBlank() ? getString(R.string.runtime_record_default) : title);
        ((TextView) findViewById(R.id.txtRecordDetail)).setText(currentDetail.isBlank() ? getString(R.string.runtime_no_description) : currentDetail);
        ((TextView) findViewById(R.id.txtRecordIcon)).setText(icon.isBlank() ? "•" : icon);
        ((TextView) findViewById(R.id.txtRecordDate)).setText(dateTime(recordEpoch));
        boolean editable = !seasonReadOnly && !manualEventId.isBlank();
        findViewById(R.id.btnRecordEdit).setVisibility(editable ? View.VISIBLE : View.GONE);
        updateDeleteAction();
    }

    private void renderPhotosAndAnalysis() {
        List<GardenPhoto> available = combinedPhotos();
        List<GardenPhoto> related = selectedPhotoId.isBlank()
                ? JournalPhotoRecordFilter.select(
                        available, zoneId, seasonId, photoGroupId, selectedPhotoPath)
                : JournalPhotoRecordFilter.selectById(
                        available, zoneId, selectedPhotoId);
        relatedPhotos = related;
        photosLayout.removeAllViews();
        if (related.isEmpty() && manualEventId.isBlank()) {
            photosTitle.setVisibility(View.GONE);
            photosLayout.setVisibility(View.GONE);
        } else {
            photosTitle.setVisibility(View.VISIBLE);
            photosLayout.setVisibility(View.VISIBLE);
            photosTitle.setText(getResources().getQuantityString(
                    R.plurals.runtime_record_photos_title, related.size(), related.size()));
            int visiblePhotoCount = 0;
            for (GardenPhoto photo : related) {
                if (hasLocalImage(photo)) visiblePhotoCount++;
            }
            int visibleIndex = 0;
            for (GardenPhoto photo : related) {
                if (!hasLocalImage(photo)) continue;
                addPhoto(photo, visibleIndex++, visiblePhotoCount);
            }
            boolean canAddPhoto = selectedPhotoId.isBlank()
                    && !seasonReadOnly && related.size() < 5;
            if (canAddPhoto) addPhotoAddTile();
            photosTitle.setVisibility(visiblePhotoCount > 0 || canAddPhoto
                    ? View.VISIBLE : View.GONE);
            photosLayout.setVisibility(visiblePhotoCount > 0 || canAddPhoto
                    ? View.VISIBLE : View.GONE);
        }
        GardenPhoto analyzed = related.isEmpty() ? null : related.get(0);
        selectedPhotoRecord = analyzed;
        if (!selectedPhotoId.isBlank() && analyzed != null) {
            recordEpoch = analyzed.getCaptured_at_epoch();
            currentDetail = safe(analyzed.getNote());
            ((TextView) findViewById(R.id.txtRecordDate)).setText(dateTime(recordEpoch));
            ((TextView) findViewById(R.id.txtRecordDetail)).setText(currentDetail.isBlank()
                    ? getString(R.string.runtime_no_description) : currentDetail);
        }
        updateDeleteAction();
        String advice = !selectedAdvice.isBlank() ? selectedAdvice : analyzed == null ? "" : safe(analyzed.getAnalysis_advice());
        String title = analyzed == null ? "" : safe(analyzed.getAnalysis_title());
        boolean hasAdvice = !advice.isBlank() || !title.isBlank();
        assistantHeading.setVisibility(hasAdvice ? View.VISIBLE : View.GONE);
        findViewById(R.id.cardRecordAssistant).setVisibility(hasAdvice ? View.VISIBLE : View.GONE);
        assistantText.setText(title.isBlank()
                ? advice
                : getString(R.string.runtime_two_sections, title, advice));
        findViewById(R.id.txtFollowupHeading).setVisibility(View.GONE);
        findViewById(R.id.cardRecordFollowup).setVisibility(View.GONE);
    }

    private boolean openAssistantWhenRequestedAnalysisIsMissing() {
        if (selectedPhotoId.isBlank() || missingAnalysisFallbackOpened) return false;
        if (!JournalPhotoRecordFilter.selectById(
                combinedPhotos(), zoneId, selectedPhotoId).isEmpty()) return false;
        missingAnalysisFallbackOpened = true;
        Intent fallback = new Intent(this, PlantAssistantActivity.class);
        fallback.putExtra("zone_id", zoneId);
        fallback.putExtra("season_id", seasonId);
        startActivity(fallback);
        finish();
        return true;
    }

    private List<GardenPhoto> combinedPhotos() {
        Map<String, GardenPhoto> combined = new LinkedHashMap<>();
        for (GardenPhoto photo : cloudPhotos) {
            if (photo != null && !safe(photo.getId()).isBlank()) {
                combined.put(photo.getId(), photo);
            }
        }
        for (GardenPhoto photo : viewModel.loadPhotos()) {
            if (photo != null && !safe(photo.getId()).isBlank()) {
                combined.put(photo.getId(), photo);
            }
        }
        return new ArrayList<>(combined.values());
    }

    private boolean hasLocalImage(GardenPhoto photo) {
        return photo != null && !safe(photo.getLocal_path()).isBlank()
                && new File(photo.getLocal_path()).exists();
    }

    private void addPhoto(GardenPhoto photo, int position, int total) {
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImageURI(Uri.fromFile(new File(photo.getLocal_path())));
        image.setContentDescription(getString(
                R.string.runtime_open_photo_description, position + 1, total));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(108), dp(108));
        params.setMarginEnd(dp(8));
        image.setLayoutParams(params);
        image.setOnClickListener(v -> showPhoto(photo));
        photosLayout.addView(image);
    }

    private void addPhotoAddTile() {
        TextView add = new TextView(this);
        add.setText(R.string.runtime_add_photo_tile);
        add.setTextSize(12); add.setGravity(Gravity.CENTER); add.setTextColor(getColor(R.color.primary));
        add.setBackgroundColor(getColor(R.color.surfaceGreen));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(108), dp(108));
        params.setMarginEnd(dp(8)); add.setLayoutParams(params);
        add.setOnClickListener(v -> showExtraPhotoSourceDialog());
        photosLayout.addView(add);
    }

    private void showExtraPhotoSourceDialog() {
        if (seasonReadOnly || photoSaving) return;
        int remaining = 5 - relatedPhotos.size();
        if (remaining <= 0) { Toast.makeText(this, R.string.runtime_record_photo_limit, Toast.LENGTH_SHORT).show(); return; }
        new MaterialAlertDialogBuilder(this).setTitle(R.string.runtime_add_photo)
                .setItems(new String[]{
                        getString(R.string.runtime_take_photo),
                        getString(R.string.runtime_choose_gallery)
                }, (dialog, which) -> {
                    if (which == 0) launchExtraPhotoCamera();
                    else extraPhotoPicker.launch(new PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
                }).show();
    }

    private void launchExtraPhotoCamera() {
        try {
            pendingCameraPhoto = GardenPhotoCapture.create(this);
            extraPhotoCamera.launch(pendingCameraPhoto.getUri());
        } catch (Exception error) {
            if (pendingCameraPhoto != null) pendingCameraPhoto.delete();
            pendingCameraPhoto = null;
            Toast.makeText(this, R.string.runtime_photo_add_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void saveExtraPhotos(List<Uri> uris, GardenPhotoCapture.Target capture) {
        if (seasonReadOnly || photoSaving || uris == null || uris.isEmpty()) {
            if (capture != null) capture.delete();
            return;
        }
        int remaining = 5 - relatedPhotos.size();
        if (remaining <= 0) {
            if (capture != null) capture.delete();
            Toast.makeText(this, R.string.runtime_record_photo_limit, Toast.LENGTH_SHORT).show();
            return;
        }
        photoSaving = true;
        findViewById(R.id.btnRecordDelete).setEnabled(false);
        viewModel.appendRecordPhotos(selectedPhotoRecord, photoGroupId, zoneId, seasonId,
                currentDetail, recordEpoch, uris.subList(0, Math.min(remaining, uris.size())))
                .addOnCompleteListener(result -> {
                    if (capture != null) capture.delete();
                });
    }

    private void showPhoto(GardenPhoto photo) {
        GardenPhotoViewerDialog.show(this, relatedPhotos, photo.getId());
    }

    @Override protected void onDestroy() {
        if (isFinishing() && pendingCameraPhoto != null) pendingCameraPhoto.delete();
        pendingCameraPhoto = null;
        super.onDestroy();
    }

    private void renderLinks() {
        linksLayout.removeAllViews();
        int count = 0;
        for (FertilizerApplication item : fertilizers) {
            if (!JournalLinkedRecordPolicy.matchesFertilizer(
                    photoGroupId, zoneId, seasonId, item)) continue;
            addLinkedCard("🌿", getString(R.string.notification_category_fertilization), safe(item.getProduct_name()) + " · " + trimNumber(item.getApplied_dose()) + " " + safe(item.getDose_unit()), item.getApplied_at_epoch());
            if (++count == 2) return;
        }
        for (WateringHistory item : wateringRecords) {
            if (!JournalLinkedRecordPolicy.matchesWatering(
                    photoGroupId, zoneId, seasonId, item)) continue;
            long when = parseWateringTime(item.getFinishedAt());
            addLinkedCard(getString(R.string.symbol_water_drop), getString(R.string.notification_category_irrigation), getString(R.string.runtime_duration_seconds, item.getDuration()), when);
            if (++count == 2) return;
        }
        if (count == 0) {
            TextView empty = new TextView(this);
            empty.setText(R.string.runtime_no_linked_records);
            empty.setTextColor(getColor(R.color.textSecondary));
            empty.setTextSize(12);
            empty.setPadding(dp(6), dp(10), dp(6), dp(6));
            linksLayout.addView(empty);
        }
    }


    private void addLinkedCard(String icon, String title, String detail, long epoch) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(12));
        card.setCardBackgroundColor(getColor(R.color.card));
        card.setStrokeColor(getColor(R.color.border));
        card.setStrokeWidth(dp(1));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.topMargin = dp(8);
        card.setLayoutParams(cardParams);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        TextView mark = new TextView(this); mark.setText(icon); mark.setTextSize(19); mark.setGravity(Gravity.CENTER);
        row.addView(mark, new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout info = new LinearLayout(this); info.setOrientation(LinearLayout.VERTICAL);
        TextView heading = new TextView(this); heading.setText(getString(R.string.runtime_title_datetime, title, dateTime(epoch))); heading.setTextColor(getColor(R.color.textPrimary)); heading.setTextSize(12); heading.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView text = new TextView(this); text.setText(detail); text.setTextColor(getColor(R.color.textSecondary)); text.setTextSize(12);
        info.addView(heading); info.addView(text);
        row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = new TextView(this); arrow.setText("›"); arrow.setTextSize(28); arrow.setTextColor(getColor(R.color.textSecondary));
        row.addView(arrow, new LinearLayout.LayoutParams(dp(22), -2));
        card.addView(row);
        card.setOnClickListener(v -> openLinkedRecord(title, detail, icon, epoch));
        linksLayout.addView(card);
    }

    private void openLinkedRecord(String title, String detail, String icon, long epoch) {
        Intent intent = new Intent(this, JournalRecordDetailActivity.class);
        intent.putExtra("title", title); intent.putExtra("detail", detail); intent.putExtra("icon", icon); intent.putExtra("time", epoch); intent.putExtra("zone_id", zoneId);
        intent.putExtra("season_id", seasonId);
        intent.putExtra("season_read_only", seasonReadOnly);
        startActivity(intent);
    }

    private long parseWateringTime(String value) {
        if (value == null || value.isBlank()) return 0L;
        String[] patterns = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "dd-MM-yyyy HH:mm", "dd.MM.yyyy HH:mm"};
        for (String pattern : patterns) {
            try {
                java.util.Date parsed = new SimpleDateFormat(pattern, Locale.US).parse(value);
                if (parsed != null) {
                    return parsed.getTime() / 1000L;
                }
            } catch (Exception ignored) {
            }
        }
        return 0L;
    }

    private void editManualRecord() {
        if (seasonReadOnly || photoSaving) return;
        EditText input = new EditText(this);
        input.setText(currentDetail);
        input.setMinLines(3);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.fertilizer_history_edit).setView(input)
                .setNegativeButton(R.string.settings_quick_cancel, null)
                .setPositiveButton(R.string.settings_quick_save, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(-1).setOnClickListener(v -> {
            String note = String.valueOf(input.getText()).trim();
            if (note.isBlank()) { input.setError(getString(R.string.journal_note_required)); return; }
            dialog.getButton(-1).setEnabled(false);
            viewModel.updateEvent(manualEventId, zoneId, seasonId, manualEventType, note, recordEpoch)
                    .addOnSuccessListener(unused -> dialog.dismiss())
                    .addOnFailureListener(error -> {
                        dialog.getButton(-1).setEnabled(true);
                    });
        }));
        dialog.show();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("pending_capture", pendingCameraPhoto == null ? "" : pendingCameraPhoto.getAbsolutePath());
        super.onSaveInstanceState(state);
    }

    private void updateDeleteAction() {
        boolean userRecord = !seasonReadOnly
                && (!manualEventId.isBlank() || selectedPhotoRecord != null);
        findViewById(R.id.btnRecordDelete).setVisibility(userRecord ? View.VISIBLE : View.GONE);
    }

    private void confirmDelete() {
        if (seasonReadOnly || photoSaving) return;
        if (manualEventId.isBlank() && selectedPhotoRecord == null) return;
        String message = !manualEventId.isBlank()
                ? getString(R.string.runtime_delete_user_record_message)
                : getString(R.string.runtime_delete_photo_record_message);
        new MaterialAlertDialogBuilder(this).setTitle(R.string.runtime_delete_record_title).setMessage(message)
                .setNegativeButton(R.string.manual_relay_test_cancel, null).setPositiveButton(R.string.notification_center_action_delete, (d, w) -> {
                    viewModel.deleteRecord(manualEventId, zoneId, seasonId, selectedPhotoRecord,
                            selectedPhotoId.isBlank() ? photoGroupId : "");
                }).show();
    }

    private String dateTime(long epoch) { return new SimpleDateFormat("dd MMMM yyyy · HH:mm", Locale.getDefault()).format(new Date(Math.max(epoch, 1L) * 1000L)); }
    private String trimNumber(double value) { return Math.abs(value - Math.rint(value)) < 0.01 ? String.valueOf((long) value) : String.format(Locale.US, "%.1f", value); }
    private String safe(String value) { return value == null ? "" : value.trim(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
