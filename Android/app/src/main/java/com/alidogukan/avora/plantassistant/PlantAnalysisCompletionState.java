package com.alidogukan.avora.plantassistant;

/** Tracks whether this analysis result was presented on its resumed screen. */
public final class PlantAnalysisCompletionState {
    private boolean screenResumed;
    private boolean resultRendered;
    private boolean resultSeen;

    public synchronized void beginAnalysis() {
        resultRendered = false;
        resultSeen = false;
    }

    public synchronized void setScreenResumed(boolean resumed) {
        screenResumed = resumed;
        if (resumed && resultRendered) resultSeen = true;
    }

    public synchronized void resultRendered() {
        resultRendered = true;
        if (screenResumed) resultSeen = true;
    }

    public synchronized void detachScreen() {
        screenResumed = false;
        resultRendered = false;
    }

    public synchronized boolean shouldNotifyOnCompletion() {
        return !resultSeen;
    }
}
