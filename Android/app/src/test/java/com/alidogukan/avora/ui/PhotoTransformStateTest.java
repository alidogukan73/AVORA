package com.alidogukan.avora.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PhotoTransformStateTest {
    @Test public void rotationWrapsInBothDirections() {
        PhotoTransformState state = new PhotoTransformState();
        state.rotateBy(-90);
        assertEquals(270, state.rotationDegrees());
        state.rotateBy(180);
        assertEquals(90, state.rotationDegrees());
    }

    @Test public void scaleIsClampedToViewerLimits() {
        PhotoTransformState state = new PhotoTransformState();
        state.zoomBy(100f);
        assertEquals(PhotoTransformState.MAX_SCALE, state.scale(), .001f);
        state.zoomBy(.001f);
        assertEquals(PhotoTransformState.MIN_SCALE, state.scale(), .001f);
    }

    @Test public void resetClearsRotationFlipsAndZoom() {
        PhotoTransformState state = new PhotoTransformState();
        state.rotateBy(90);
        state.flipHorizontally();
        state.flipVertically();
        state.zoomBy(2f);
        state.reset();
        assertEquals(0, state.rotationDegrees());
        assertFalse(state.isFlippedHorizontally());
        assertFalse(state.isFlippedVertically());
        assertEquals(PhotoTransformState.MIN_SCALE, state.scale(), .001f);
    }

    @Test public void eachFlipCanBeToggledIndependently() {
        PhotoTransformState state = new PhotoTransformState();
        state.flipHorizontally();
        assertTrue(state.isFlippedHorizontally());
        assertFalse(state.isFlippedVertically());
        state.flipVertically();
        assertTrue(state.isFlippedHorizontally());
        assertTrue(state.isFlippedVertically());
    }

    @Test public void savedOrientationIsRestoredWithoutSavedZoom() {
        PhotoTransformState state = new PhotoTransformState();
        state.zoomBy(3f);
        state.restoreOrientation(-90, true, false);
        assertEquals(270, state.rotationDegrees());
        assertTrue(state.isFlippedHorizontally());
        assertFalse(state.isFlippedVertically());
        assertEquals(PhotoTransformState.MIN_SCALE, state.scale(), .001f);
    }
}
