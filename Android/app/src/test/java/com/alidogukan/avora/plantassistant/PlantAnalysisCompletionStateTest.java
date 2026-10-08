package com.alidogukan.avora.plantassistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlantAnalysisCompletionStateTest {
    @Test public void resultSeenOnScreenDoesNotCreateAnotherNotification() {
        PlantAnalysisCompletionState state = new PlantAnalysisCompletionState();
        state.setScreenResumed(true);
        state.beginAnalysis();
        state.resultRendered();
        assertFalse(state.shouldNotifyOnCompletion());
    }

    @Test public void foregroundWithoutTheFinalResultIsNotEnoughToSuppress() {
        PlantAnalysisCompletionState state = new PlantAnalysisCompletionState();
        state.setScreenResumed(true);
        state.beginAnalysis();
        assertTrue(state.shouldNotifyOnCompletion());
    }

    @Test public void leavingBeforeCompletionKeepsTheNotification() {
        PlantAnalysisCompletionState state = new PlantAnalysisCompletionState();
        state.setScreenResumed(true);
        state.beginAnalysis();
        state.setScreenResumed(false);
        state.resultRendered();
        assertTrue(state.shouldNotifyOnCompletion());
    }

    @Test public void returningToTheRenderedResultBeforeSavingSuppressesNotification() {
        PlantAnalysisCompletionState state = new PlantAnalysisCompletionState();
        state.beginAnalysis();
        state.resultRendered();
        state.setScreenResumed(true);
        assertFalse(state.shouldNotifyOnCompletion());
    }

    @Test public void leavingAfterSeeingTheResultDoesNotProduceALateDuplicate() {
        PlantAnalysisCompletionState state = new PlantAnalysisCompletionState();
        state.setScreenResumed(true);
        state.beginAnalysis();
        state.resultRendered();
        state.setScreenResumed(false);
        assertFalse(state.shouldNotifyOnCompletion());
    }

    @Test public void nextAnalysisDoesNotInheritThePreviousSeenResult() {
        PlantAnalysisCompletionState state = new PlantAnalysisCompletionState();
        state.setScreenResumed(true);
        state.beginAnalysis();
        state.resultRendered();
        state.beginAnalysis();
        state.setScreenResumed(false);
        state.resultRendered();
        assertTrue(state.shouldNotifyOnCompletion());
    }

    @Test public void recreatedScreenDoesNotPretendAnUnseenResultIsStillRendered() {
        PlantAnalysisCompletionState state = new PlantAnalysisCompletionState();
        state.beginAnalysis();
        state.resultRendered();
        state.detachScreen();
        state.setScreenResumed(true);
        assertTrue(state.shouldNotifyOnCompletion());
    }

    @Test public void screenRecreationDoesNotForgetAnAlreadySeenResult() {
        PlantAnalysisCompletionState state = new PlantAnalysisCompletionState();
        state.setScreenResumed(true);
        state.beginAnalysis();
        state.resultRendered();
        state.detachScreen();
        assertFalse(state.shouldNotifyOnCompletion());
    }
}
