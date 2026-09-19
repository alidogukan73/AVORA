package com.alidogukan.avora.device;

import com.alidogukan.avora.models.Status;

/** Fail-closed preflight for an operator-requested device restart. */
public final class DeviceRestartPolicy {
    public static final String ADMIN_REQUIRED = "RESTART_ADMIN_REQUIRED";
    public static final String STATUS_UNAVAILABLE = "RESTART_STATUS_UNAVAILABLE";
    public static final String WATERING_ACTIVE = "RESTART_WATERING_ACTIVE";
    public static final String ALREADY_PENDING = "RESTART_ALREADY_PENDING";

    private DeviceRestartPolicy() { }

    public static String failure(Status status, boolean hardwareBusy,
                                 boolean wateringRequested, boolean restartPending,
                                 long nowSeconds) {
        if (status == null || !status.isOnline() || status.getLastSeenEpoch() <= 0
                || nowSeconds - status.getLastSeenEpoch() > 90
                || status.getLastSeenEpoch() > nowSeconds + 10) {
            return STATUS_UNAVAILABLE;
        }
        if (status.isRelay() || status.isValveOpen() || hardwareBusy || wateringRequested
                || "WATERING".equals(status.getWateringState())) {
            return WATERING_ACTIVE;
        }
        return restartPending ? ALREADY_PENDING : null;
    }
}
