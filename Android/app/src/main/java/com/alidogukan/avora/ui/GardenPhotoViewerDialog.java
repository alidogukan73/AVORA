package com.alidogukan.avora.ui;

import android.app.Activity;
import android.app.Dialog;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.alidogukan.avora.R;
import com.alidogukan.avora.models.GardenPhoto;
import com.alidogukan.avora.photos.LocalGardenPhotoStore;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Full-screen, swipeable viewer for photos belonging to one journal record. */
public final class GardenPhotoViewerDialog {
    private GardenPhotoViewerDialog() { }

    public static void show(Activity activity, List<GardenPhoto> source, String selectedId) {
        List<GardenPhoto> photos = availablePhotos(source);
        if (photos.isEmpty()) return;
        int initialPosition = initialPosition(photos, selectedId);
        LocalGardenPhotoStore photoStore = new LocalGardenPhotoStore(activity);

        Dialog dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_garden_photo_viewer);
        RecyclerView pager = dialog.findViewById(R.id.pagerGardenPhotos);
        TextView position = dialog.findViewById(R.id.txtGardenPhotoPagerPosition);
        LinearLayoutManager layoutManager = new LinearLayoutManager(
                activity, RecyclerView.HORIZONTAL, false);
        pager.setLayoutManager(layoutManager);
        PageRequest openPage = requestedPosition -> {
            int target = Math.max(0, Math.min(photos.size() - 1, requestedPosition));
            pager.smoothScrollToPosition(target);
            updatePosition(activity, position, target, photos.size());
        };
        pager.setAdapter(new PhotoPagerAdapter(activity, photos, openPage));
        pager.setItemAnimator(null);
        new PagerSnapHelper().attachToRecyclerView(pager);
        pager.scrollToPosition(initialPosition);
        updatePosition(activity, position, initialPosition, photos.size());
        pager.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return;
                int current = layoutManager.findFirstVisibleItemPosition();
                if (current >= 0) updatePosition(activity, position, current, photos.size());
            }
        });
        GestureDetector pageGesture = new GestureDetector(activity,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override public boolean onDown(@NonNull MotionEvent event) { return true; }

                    @Override public boolean onFling(MotionEvent first, @NonNull MotionEvent last,
                                                     float velocityX, float velocityY) {
                        TransformableImageView image = currentImage(pager, layoutManager);
                        if (first == null || (image != null && image.isZoomed())
                                || Math.abs(velocityX) <= Math.abs(velocityY)) return false;
                        float distance = last.getX() - first.getX();
                        if (Math.abs(distance) < activity.getResources()
                                .getDisplayMetrics().density * 48f) return false;
                        int current = layoutManager.findFirstVisibleItemPosition();
                        if (current >= 0) openPage.open(current + (distance < 0f ? 1 : -1));
                        return false;
                    }
                });
        pager.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override public boolean onInterceptTouchEvent(@NonNull RecyclerView recyclerView,
                                                           @NonNull MotionEvent event) {
                pageGesture.onTouchEvent(event);
                return false;
            }
        });
        dialog.findViewById(R.id.btnGardenPhotoViewerClose)
                .setOnClickListener(view -> dialog.dismiss());
        dialog.findViewById(R.id.btnPhotoRotateLeft).setOnClickListener(view ->
                withCurrentOrientation(pager, layoutManager, photos, photoStore,
                        TransformableImageView::rotateLeft));
        dialog.findViewById(R.id.btnPhotoRotateRight).setOnClickListener(view ->
                withCurrentOrientation(pager, layoutManager, photos, photoStore,
                        TransformableImageView::rotateRight));
        dialog.findViewById(R.id.btnPhotoFlipHorizontal).setOnClickListener(view ->
                withCurrentOrientation(pager, layoutManager, photos, photoStore,
                        TransformableImageView::flipHorizontal));
        dialog.findViewById(R.id.btnPhotoFlipVertical).setOnClickListener(view ->
                withCurrentOrientation(pager, layoutManager, photos, photoStore,
                        TransformableImageView::flipVertical));
        dialog.findViewById(R.id.btnPhotoZoomOut).setOnClickListener(view ->
                withCurrentImage(pager, layoutManager, TransformableImageView::zoomOut));
        dialog.findViewById(R.id.btnPhotoReset).setOnClickListener(view ->
                withCurrentOrientation(pager, layoutManager, photos, photoStore,
                        TransformableImageView::resetTransform));
        dialog.findViewById(R.id.btnPhotoZoomIn).setOnClickListener(view ->
                withCurrentImage(pager, layoutManager, TransformableImageView::zoomIn));
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private static List<GardenPhoto> availablePhotos(List<GardenPhoto> source) {
        List<GardenPhoto> result = new ArrayList<>();
        if (source == null) return result;
        for (GardenPhoto photo : source) {
            if (photo == null || photo.getLocal_path() == null) continue;
            if (new File(photo.getLocal_path()).isFile()) result.add(photo);
        }
        return result;
    }

    private static int initialPosition(List<GardenPhoto> photos, String selectedId) {
        for (int index = 0; index < photos.size(); index++) {
            if (safe(selectedId).equals(safe(photos.get(index).getId()))) return index;
        }
        return 0;
    }

    private static void updatePosition(Activity activity, TextView target,
                                       int position, int total) {
        target.setText(activity.getString(
                R.string.runtime_photo_swipe_position, position + 1, total));
    }

    private static String safe(String value) { return value == null ? "" : value; }

    private static void withCurrentImage(RecyclerView pager,
                                         LinearLayoutManager layoutManager,
                                         PhotoAction action) {
        TransformableImageView image = currentImage(pager, layoutManager);
        if (image != null) action.apply(image);
    }

    private static void withCurrentOrientation(RecyclerView pager,
                                               LinearLayoutManager layoutManager,
                                               List<GardenPhoto> photos,
                                               LocalGardenPhotoStore photoStore,
                                               PhotoAction action) {
        PhotoPagerAdapter.PhotoHolder holder = currentHolder(pager, layoutManager);
        if (holder == null) return;
        int current = holder.getBindingAdapterPosition();
        if (current == RecyclerView.NO_POSITION || current >= photos.size()) return;
        action.apply(holder.image);
        GardenPhoto photo = photos.get(current);
        photo.setRotation_degrees(holder.image.getPhotoRotationDegrees());
        photo.setFlipped_horizontally(holder.image.isPhotoFlippedHorizontally());
        photo.setFlipped_vertically(holder.image.isPhotoFlippedVertically());
        photoStore.updateViewerOrientation(
                photo.getId(),
                photo.getRotation_degrees(),
                photo.getFlipped_horizontally(),
                photo.getFlipped_vertically());
    }

    private static TransformableImageView currentImage(RecyclerView pager,
                                                        LinearLayoutManager layoutManager) {
        PhotoPagerAdapter.PhotoHolder holder = currentHolder(pager, layoutManager);
        return holder == null ? null : holder.image;
    }

    private static PhotoPagerAdapter.PhotoHolder currentHolder(
            RecyclerView pager, LinearLayoutManager layoutManager) {
        int current = layoutManager.findFirstCompletelyVisibleItemPosition();
        if (current < 0) current = layoutManager.findFirstVisibleItemPosition();
        RecyclerView.ViewHolder holder = pager.findViewHolderForAdapterPosition(current);
        if (holder instanceof PhotoPagerAdapter.PhotoHolder) {
            return (PhotoPagerAdapter.PhotoHolder) holder;
        }
        return null;
    }

    private interface PhotoAction {
        void apply(TransformableImageView image);
    }

    private static final class PhotoPagerAdapter
            extends RecyclerView.Adapter<PhotoPagerAdapter.PhotoHolder> {
        private final Activity activity;
        private final List<GardenPhoto> photos;
        private final PageRequest pageRequest;

        PhotoPagerAdapter(Activity activity, List<GardenPhoto> photos,
                          PageRequest pageRequest) {
            this.activity = activity;
            this.photos = photos;
            this.pageRequest = pageRequest;
            setHasStableIds(true);
        }

        @NonNull
        @Override
        public PhotoHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_garden_photo_page, parent, false);
            return new PhotoHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PhotoHolder holder, int position) {
            GardenPhoto photo = photos.get(position);
            holder.image.setImageDrawable(null);
            holder.image.setImageURI(Uri.fromFile(new File(photo.getLocal_path())));
            holder.image.restoreOrientation(
                    photo.getRotation_degrees(),
                    photo.getFlipped_horizontally(),
                    photo.getFlipped_vertically());
            holder.image.setContentDescription(activity.getString(
                    R.string.runtime_photo_page_description, position + 1, photos.size()));
            holder.image.setPageSwipeRequestListener(direction -> {
                int current = holder.getBindingAdapterPosition();
                if (current != RecyclerView.NO_POSITION) pageRequest.open(current + direction);
            });
        }

        @Override public void onViewRecycled(@NonNull PhotoHolder holder) {
            holder.image.setPageSwipeRequestListener(null);
            holder.image.setImageDrawable(null);
        }

        @Override public long getItemId(int position) {
            return safe(photos.get(position).getId()).hashCode();
        }

        @Override public int getItemCount() { return photos.size(); }

        static final class PhotoHolder extends RecyclerView.ViewHolder {
            final TransformableImageView image;

            PhotoHolder(View itemView) {
                super(itemView);
                image = itemView.findViewById(R.id.imgGardenPhotoPage);
            }
        }
    }

    private interface PageRequest {
        void open(int position);
    }
}
