package com.alidogukan.avora.ui;

/** Keeps photo-viewer transformations independent from Android drawing code. */
final class PhotoTransformState {
    static final float MIN_SCALE = 1f;
    static final float MAX_SCALE = 5f;

    private int rotationDegrees;
    private boolean flippedHorizontally;
    private boolean flippedVertically;
    private float scale = MIN_SCALE;

    int rotationDegrees() { return rotationDegrees; }
    boolean isFlippedHorizontally() { return flippedHorizontally; }
    boolean isFlippedVertically() { return flippedVertically; }
    float scale() { return scale; }

    void rotateBy(int degrees) {
        rotationDegrees = ((rotationDegrees + degrees) % 360 + 360) % 360;
        resetZoom();
    }

    void flipHorizontally() {
        flippedHorizontally = !flippedHorizontally;
        resetZoom();
    }

    void flipVertically() {
        flippedVertically = !flippedVertically;
        resetZoom();
    }

    float zoomBy(float factor) {
        float previous = scale;
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale * factor));
        return scale / previous;
    }

    void resetZoom() { scale = MIN_SCALE; }

    void restoreOrientation(int rotation, boolean horizontalFlip, boolean verticalFlip) {
        rotationDegrees = ((rotation % 360) + 360) % 360;
        flippedHorizontally = horizontalFlip;
        flippedVertically = verticalFlip;
        resetZoom();
    }

    void reset() {
        rotationDegrees = 0;
        flippedHorizontally = false;
        flippedVertically = false;
        resetZoom();
    }
}
