package com.alidogukan.avora.ui;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/** Photo view supporting pinch zoom, panning, rotation and mirroring. */
public final class TransformableImageView extends AppCompatImageView {
    private final Matrix transformMatrix = new Matrix();
    private final PhotoTransformState transformState = new PhotoTransformState();
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private PageSwipeRequestListener pageSwipeRequestListener;

    public TransformableImageView(@NonNull Context context) {
        super(context);
        initialize(context);
    }

    public TransformableImageView(@NonNull Context context,
                                  @Nullable AttributeSet attrs) {
        super(context, attrs);
        initialize(context);
    }

    public TransformableImageView(@NonNull Context context,
                                  @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize(context);
    }

    private void initialize(Context context) {
        super.setScaleType(ScaleType.MATRIX);
        scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
                        disallowParentIntercept(true);
                        return true;
                    }

                    @Override public boolean onScale(ScaleGestureDetector detector) {
                        zoomAt(detector.getScaleFactor(),
                                detector.getFocusX(), detector.getFocusY());
                        return true;
                    }

                    @Override public void onScaleEnd(ScaleGestureDetector detector) {
                        disallowParentIntercept(transformState.scale()
                                > PhotoTransformState.MIN_SCALE);
                    }
                });
        gestureDetector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override public boolean onDown(@NonNull MotionEvent event) {
                        return true;
                    }

                    @Override public boolean onScroll(MotionEvent first, @NonNull MotionEvent current,
                                                      float distanceX, float distanceY) {
                        if (transformState.scale() <= PhotoTransformState.MIN_SCALE) return false;
                        transformMatrix.postTranslate(-distanceX, -distanceY);
                        constrainToViewport();
                        setImageMatrix(transformMatrix);
                        disallowParentIntercept(true);
                        return true;
                    }

                    @Override public boolean onDoubleTap(@NonNull MotionEvent event) {
                        if (transformState.scale() > PhotoTransformState.MIN_SCALE + .01f) {
                            resetZoom();
                        } else {
                            zoomAt(2.5f, event.getX(), event.getY());
                        }
                        return true;
                    }

                    @Override public boolean onFling(MotionEvent first, @NonNull MotionEvent last,
                                                     float velocityX, float velocityY) {
                        if (first == null || pageSwipeRequestListener == null
                                || transformState.scale() > PhotoTransformState.MIN_SCALE + .01f) {
                            return false;
                        }
                        float horizontalTravel = last.getX() - first.getX();
                        float minimumTravel = 48f * getResources().getDisplayMetrics().density;
                        if (Math.abs(horizontalTravel) < minimumTravel
                                || Math.abs(velocityX) <= Math.abs(velocityY)) return false;
                        pageSwipeRequestListener.onPageSwipeRequested(horizontalTravel < 0f ? 1 : -1);
                        return true;
                    }
                });
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        int action = event.getActionMasked();
        if (event.getPointerCount() > 1
                || transformState.scale() > PhotoTransformState.MIN_SCALE) {
            disallowParentIntercept(true);
        }
        if (action == MotionEvent.ACTION_UP) {
            performClick();
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            disallowParentIntercept(false);
        }
        return true;
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        resetZoom();
    }

    public void rotateLeft() {
        transformState.rotateBy(-90);
        rebuildToFit();
    }

    public void rotateRight() {
        transformState.rotateBy(90);
        rebuildToFit();
    }

    public void flipHorizontal() {
        transformState.flipHorizontally();
        rebuildToFit();
    }

    public void flipVertical() {
        transformState.flipVertically();
        rebuildToFit();
    }

    public void zoomIn() { zoomAt(1.35f, getWidth() / 2f, getHeight() / 2f); }
    public void zoomOut() { zoomAt(1f / 1.35f, getWidth() / 2f, getHeight() / 2f); }
    public boolean isZoomed() {
        return transformState.scale() > PhotoTransformState.MIN_SCALE + .01f;
    }

    public int getPhotoRotationDegrees() {
        return transformState.rotationDegrees();
    }

    public boolean isPhotoFlippedHorizontally() {
        return transformState.isFlippedHorizontally();
    }

    public boolean isPhotoFlippedVertically() {
        return transformState.isFlippedVertically();
    }

    public void restoreOrientation(int rotationDegrees,
                                   boolean flippedHorizontally,
                                   boolean flippedVertically) {
        transformState.restoreOrientation(
                rotationDegrees, flippedHorizontally, flippedVertically);
        rebuildToFit();
    }

    public void resetTransform() {
        transformState.reset();
        rebuildToFit();
    }

    public void setPageSwipeRequestListener(@Nullable PageSwipeRequestListener listener) {
        pageSwipeRequestListener = listener;
    }

    private void resetZoom() {
        transformState.resetZoom();
        rebuildToFit();
    }

    private void rebuildToFit() {
        Drawable drawable = getDrawable();
        if (drawable == null || getWidth() <= 0 || getHeight() <= 0
                || drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            return;
        }
        float drawableWidth = drawable.getIntrinsicWidth();
        float drawableHeight = drawable.getIntrinsicHeight();
        RectF source = new RectF(0f, 0f, drawableWidth, drawableHeight);

        transformMatrix.reset();
        transformMatrix.postTranslate(-drawableWidth / 2f, -drawableHeight / 2f);
        transformMatrix.postScale(
                transformState.isFlippedHorizontally() ? -1f : 1f,
                transformState.isFlippedVertically() ? -1f : 1f);
        transformMatrix.postRotate(transformState.rotationDegrees());

        RectF orientedBounds = new RectF(source);
        transformMatrix.mapRect(orientedBounds);
        transformMatrix.postTranslate(-orientedBounds.left, -orientedBounds.top);
        float fitScale = Math.min(
                getWidth() / orientedBounds.width(),
                getHeight() / orientedBounds.height());
        transformMatrix.postScale(fitScale, fitScale);
        transformMatrix.postTranslate(
                (getWidth() - orientedBounds.width() * fitScale) / 2f,
                (getHeight() - orientedBounds.height() * fitScale) / 2f);
        setImageMatrix(transformMatrix);
    }

    private void zoomAt(float requestedFactor, float focusX, float focusY) {
        if (getDrawable() == null) return;
        float appliedFactor = transformState.zoomBy(requestedFactor);
        if (Math.abs(appliedFactor - 1f) < .001f) return;
        transformMatrix.postScale(appliedFactor, appliedFactor, focusX, focusY);
        constrainToViewport();
        setImageMatrix(transformMatrix);
    }

    private void constrainToViewport() {
        Drawable drawable = getDrawable();
        if (drawable == null) return;
        RectF bounds = new RectF(0f, 0f,
                drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        transformMatrix.mapRect(bounds);
        float dx = correction(bounds.left, bounds.right, getWidth());
        float dy = correction(bounds.top, bounds.bottom, getHeight());
        transformMatrix.postTranslate(dx, dy);
    }

    private float correction(float start, float end, float viewportSize) {
        float contentSize = end - start;
        if (contentSize <= viewportSize) return viewportSize / 2f - (start + end) / 2f;
        if (start > 0f) return -start;
        if (end < viewportSize) return viewportSize - end;
        return 0f;
    }

    private void disallowParentIntercept(boolean disallow) {
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(disallow);
    }

    public interface PageSwipeRequestListener {
        void onPageSwipeRequested(int direction);
    }
}
