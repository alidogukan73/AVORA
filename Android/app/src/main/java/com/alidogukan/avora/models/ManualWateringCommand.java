package com.alidogukan.avora.models;

import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.database.PropertyName;

@IgnoreExtraProperties
public class ManualWateringCommand {
    private boolean requested;
    private boolean active;
    private String requestId = "";
    private String completedRequestId = "";
    private String zoneId = "";
    private String result = "";

    public ManualWateringCommand() {
        // Firebase
    }

    public boolean isRequested() { return requested; }
    public void setRequested(boolean requested) { this.requested = requested; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    @PropertyName("request_id")
    public String getRequestId() { return requestId; }

    @PropertyName("request_id")
    public void setRequestId(String requestId) {
        this.requestId = requestId == null ? "" : requestId;
    }

    @PropertyName("completed_request_id")
    public String getCompletedRequestId() { return completedRequestId; }

    @PropertyName("completed_request_id")
    public void setCompletedRequestId(String completedRequestId) {
        this.completedRequestId = completedRequestId == null ? "" : completedRequestId;
    }

    @PropertyName("zone_id")
    public String getZoneId() { return zoneId; }

    @PropertyName("zone_id")
    public void setZoneId(String zoneId) {
        this.zoneId = zoneId == null ? "" : zoneId;
    }

    public String getResult() { return result; }
    public void setResult(String result) {
        this.result = result == null ? "" : result;
    }
}
