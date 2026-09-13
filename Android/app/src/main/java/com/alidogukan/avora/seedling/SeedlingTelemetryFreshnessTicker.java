package com.alidogukan.avora.seedling;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.alidogukan.avora.models.SeedlingTelemetry;

/**
 * Re-evaluates one telemetry reading when it naturally becomes stale.
 *
 * <p>The callback is active only while its owner is started, and it runs only
 * when freshness changes. A new Firebase reading reschedules the single pending
 * check instead of starting another periodic loop.</p>
 */
public final class SeedlingTelemetryFreshnessTicker implements DefaultLifecycleObserver {
    private final LifecycleOwner owner;
    private final long maximumAgeSeconds;
    private final Runnable onFreshnessChanged;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable expiryCheck = this::handleExpiryCheck;

    @Nullable private SeedlingTelemetry telemetry;
    @Nullable private Boolean lastFresh;
    private boolean started;

    public SeedlingTelemetryFreshnessTicker(
            @NonNull LifecycleOwner owner,
            long maximumAgeSeconds,
            @NonNull Runnable onFreshnessChanged
    ) {
        this.owner = owner;
        this.maximumAgeSeconds = Math.max(0L, maximumAgeSeconds);
        this.onFreshnessChanged = onFreshnessChanged;
        owner.getLifecycle().addObserver(this);
    }

    /** Updates the reading after the owner has rendered the matching Firebase state. */
    public void update(@Nullable SeedlingTelemetry value) {
        telemetry = value;
        lastFresh = isFreshNow();
        scheduleExpiryCheck();
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        started = true;
        boolean fresh = isFreshNow();
        boolean changedWhileStopped = lastFresh != null && lastFresh != fresh;
        lastFresh = fresh;
        if (changedWhileStopped) onFreshnessChanged.run();
        scheduleExpiryCheck();
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        started = false;
        handler.removeCallbacks(expiryCheck);
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        started = false;
        handler.removeCallbacks(expiryCheck);
        this.owner.getLifecycle().removeObserver(this);
    }

    private void handleExpiryCheck() {
        if (!started) return;
        boolean fresh = isFreshNow();
        if (lastFresh == null || lastFresh != fresh) {
            lastFresh = fresh;
            onFreshnessChanged.run();
        }
        scheduleExpiryCheck();
    }

    private boolean isFreshNow() {
        return telemetry != null && telemetry.isFresh(
                System.currentTimeMillis() / 1000L,
                maximumAgeSeconds
        );
    }

    private void scheduleExpiryCheck() {
        handler.removeCallbacks(expiryCheck);
        if (!started) return;
        long delay = delayUntilStaleMillis(
                telemetry,
                System.currentTimeMillis() / 1000L,
                maximumAgeSeconds
        );
        if (delay >= 0L) handler.postDelayed(expiryCheck, delay);
    }

    static long delayUntilStaleMillis(@Nullable SeedlingTelemetry value,
                                      long nowEpochSeconds,
                                      long maximumAgeSeconds) {
        long ageLimit = Math.max(0L, maximumAgeSeconds);
        if (value == null || !value.isFresh(nowEpochSeconds, ageLimit)) return -1L;

        long received = value.getReceived_at_epoch();
        long staleAt;
        if (received > Long.MAX_VALUE - ageLimit - 1L) {
            staleAt = Long.MAX_VALUE;
        } else {
            // isFresh is inclusive at maximumAgeSeconds, so the first stale
            // epoch is one second after the freshness window.
            staleAt = received + ageLimit + 1L;
        }
        long seconds = staleAt > nowEpochSeconds ? staleAt - nowEpochSeconds : 1L;
        return seconds > Long.MAX_VALUE / 1000L ? Long.MAX_VALUE : seconds * 1000L;
    }
}
