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
import java.util.Locale;

/** Detail view for one live plant-journal timeline record. */
public class JournalRecordDetailActivity extends EdgeToEdgeActivity {
    private PlantJournalViewModel viewModel;
    private String manualEventId = "", manualEventType = "", zoneId = "", seasonId = "", currentDetail = "";
    private String selectedPhotoPath = "", selectedAdvice = "", photoGroupId = "";
    private boolean seasonReadOnly;
    private GardenPhoto selectedPhotoRecord;
    private long recordEpoch;
    private LinearLayout photosLayout, linksLayout;
    private TextView photosTitle, assistantHeading, assistantText;
    private List<FertilizerApplication> fertilizers = new ArrayList<>();
    private List<WateringHistory> wateringRecords = new ArrayList<>();
    private List<GardenPhoto> relatedPhotos = new ArrayList<>();
    private GardenPhotoCapture.Target pendingCameraPhoto;

    private final ActivityResultLauncher<PickVisualMediaRequest> extraPhotoPicker =
            registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(5), uris -> {
                if (uris == null || uris.isEmpty()) return;
                saveExtraPhotos(uris);
            });
    private final ActivityResultLauncher<Uri> extraPhotoCamera =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), saved -> {
                GardenPhotoCapture.Target target = pendingCameraPhoto;
                pendingCameraPhoto = null;
                if (!saved || target == null) {
                    if (target != null) target.delete();
                    return;
                }
                try {
                    saveExtraPhotos(java.util.Collections.singletonList(target.getUri()));
                } finally {
                    target.delete();
                }
            });
    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_journal_record_detail);
        viewModel = new ViewModelProvider(this).get(PlantJournalViewModel.class);
        PrimaryBottomNavigation.bind(this, PrimaryBottomNavigation.PLANTS);
        bindIntent();
        bindViews();
        renderStaticDetail();
        renderPhotosAndAnalysis();
        viewModel.getFertilizerHistory().observe(this, values -> {
            fertilizers = values == null ? new ArrayList<>() : values;
            renderLinks();
        });
        viewModel.getWateringHistory().observe(this, values -> {
            wateringRecords = values == null ? new ArrayList<>() : values;
            renderLinks();
        });
    }

    private void bindIntent() {
        manualEventId = safe(getIntent().getStringExtra("manual_event_id"));
        manualEventType = safe(getIntent().getStringExtra("manual_event_type"));
        zoneId = safe(getIntent().getStringExtra("zone_id"));
        seasonId = safe(getIntent().getStringExtra("season_id"));
        currentDetail = safe(getIntent().getStringExtra("detail"));
        selectedPhotoPath = safe(getIntent().getStringExtra("photo_path"));
        photoGroupId = safe(getIntent().getStringExtra("photo_group_id"));
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
        List<GardenPhoto> related = JournalPhotoRecordFilter.select(
                viewModel.loadPhotos(),
                zoneId,
                photoGroupId,
                selectedPhotoPath
        );
        relatedPhotos = related;
        photosLayout.removeAllViews();
        if (related.isEmpty()) {
            photosTitle.setVisibility(View.GONE);
            photosLayout.setVisibility(View.GONE);
        } else {
            photosTitle.setVisibility(View.VISIBLE);
            photosLayout.setVisibility(View.VISIBLE);
            photosTitle.setText(getResources().getQuantityString(
                    R.plurals.runtime_record_photos_title, related.size(), related.size()));
            for (int index = 0; index < related.size(); index++) {
                addPhoto(related.get(index), index, related.size());
            }
            if (!seasonReadOnly && related.size() < 5) addPhotoAddTile();
        }
        GardenPhoto analyzed = related.isEmpty() ? null : related.get(0);
        selectedPhotoRecord = analyzed;
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
        if (seasonReadOnly) return;
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

    private void saveExtraPhotos(List<Uri> uris) {
        if (selectedPhotoRecord == null) return;
        int remaining = 5 - relatedPhotos.size();
        if (remaining <= 0) return;
        try {
            photoGroupId = viewModel.ensureJournalPhotoGroup(
                    selectedPhotoRecord, photoGroupId);
            if (uris != null) {
                for (int i = 0; i < Math.min(remaining, uris.size()); i++) {
                    viewModel.addPhoto(uris.get(i), zoneId, currentDetail,
                            photoGroupId, seasonId);
                }
            }
            renderPhotosAndAnalysis();
            Toast.makeText(this, R.string.runtime_photo_added, Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(this, R.string.runtime_photo_add_failed, Toast.LENGTH_SHORT).show();
        }
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
            if (!zoneId.equals(item.getZone_id()) || isSameRecord(item.getApplied_at_epoch())) continue;
            addLinkedCard("🌿", getString(R.string.notification_category_fertilization), safe(item.getProduct_name()) + " · " + trimNumber(item.getApplied_dose()) + " " + safe(item.getDose_unit()), item.getApplied_at_epoch());
            if (++count == 2) return;
        }
        for (WateringHistory item : wateringRecords) {
            long when = parseWateringTime(item.getFinishedAt());
            if (!zoneId.equals(item.getZoneId()) || !item.isCompleted() || isSameRecord(when)) continue;
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

    private boolean isSameRecord(long time) { return time > 0L && Math.abs(time - recordEpoch) < 90L; }

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
        if (seasonReadOnly) return;
        EditText input = new EditText(this); input.setText(currentDetail); input.setMinLines(3);
        new MaterialAlertDialogBuilder(this).setTitle(R.string.fertilizer_history_edit).setView(input).setNegativeButton(R.string.settings_quick_cancel, null).setPositiveButton(R.string.settings_quick_save, (d, w) -> {
            String newNote = String.valueOf(input.getText());
            if (viewModel.updateEvent(manualEventId, zoneId, seasonId,
                    manualEventType, newNote, recordEpoch)) finish();
        }).show();
    }

    private void updateDeleteAction() {
        boolean userRecord = !seasonReadOnly
                && (!manualEventId.isBlank() || selectedPhotoRecord != null);
        findViewById(R.id.btnRecordDelete).setVisibility(userRecord ? View.VISIBLE : View.GONE);
    }

    private void confirmDelete() {
        if (seasonReadOnly) return;
        if (manualEventId.isBlank() && selectedPhotoRecord == null) return;
        String message = !manualEventId.isBlank()
                ? getString(R.string.runtime_delete_user_record_message)
                : getString(R.string.runtime_delete_photo_record_message);
        new MaterialAlertDialogBuilder(this).setTitle(R.string.runtime_delete_record_title).setMessage(message)
                .setNegativeButton(R.string.manual_relay_test_cancel, null).setPositiveButton(R.string.notification_center_action_delete, (d, w) -> {
                    if (!manualEventId.isBlank()) {
                        viewModel.deleteEvent(manualEventId);
                    } else if (selectedPhotoRecord != null) {
                        viewModel.deletePhotoRecord(selectedPhotoRecord, photoGroupId);
                    }
                    finish();
                }).show();
    }

    private String dateTime(long epoch) { return new SimpleDateFormat("dd MMMM yyyy · HH:mm", Locale.getDefault()).format(new Date(Math.max(epoch, 1L) * 1000L)); }
    private String trimNumber(double value) { return Math.abs(value - Math.rint(value)) < 0.01 ? String.valueOf((long) value) : String.format(Locale.US, "%.1f", value); }
    private String safe(String value) { return value == null ? "" : value.trim(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
