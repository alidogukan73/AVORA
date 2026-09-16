package com.alidogukan.avora.plantassistant;

import com.alidogukan.avora.models.GardenZone;

/** A recent, advisory-only Plant Doctor finding for one garden zone. */
public final class PlantAssistantHealthSignal {
    private final String zoneId;
    private final String seasonId;
    private final String urgency;
    private final String title;
    private final String advice;
    private final String recordId;
    private final long createdAtEpoch;

    public PlantAssistantHealthSignal(String zoneId, String urgency, String title, long createdAtEpoch) {
        this(zoneId, "", urgency, title, createdAtEpoch);
    }

    public PlantAssistantHealthSignal(String zoneId, String seasonId, String urgency,
                                      String title, long createdAtEpoch) {
        this(zoneId, seasonId, urgency, title, "", createdAtEpoch);
    }

    public PlantAssistantHealthSignal(String zoneId, String seasonId, String urgency,
                                      String title, String advice, long createdAtEpoch) {
        this(zoneId, seasonId, urgency, title, advice, "", createdAtEpoch);
    }

    public PlantAssistantHealthSignal(String zoneId, String seasonId, String urgency,
                                      String title, String advice, String recordId,
                                      long createdAtEpoch) {
        this.zoneId = zoneId == null ? "" : zoneId;
        this.seasonId = seasonId == null ? "" : seasonId;
        this.urgency = urgency == null ? "" : urgency;
        this.title = title == null ? "" : title;
        this.advice = advice == null ? "" : advice;
        this.recordId = recordId == null ? "" : recordId;
        this.createdAtEpoch = createdAtEpoch;
    }

    public String getZoneId() { return zoneId; }
    public String getSeasonId() { return seasonId; }
    public String getUrgency() { return urgency; }
    public String getTitle() { return title; }
    public String getAdvice() { return advice; }
    public String getRecordId() { return recordId; }

    public long getCreatedAtEpoch() { return createdAtEpoch; }

    public boolean appliesTo(GardenZone zone, long nowEpoch) {
        if (zone == null || !isRecent(nowEpoch) || !zoneId.equals(zone.getZone_id())) return false;
        if (!seasonId.isEmpty()) {
            return zone.getSeason() != null && zone.getSeason().isActive()
                    && zone.getSeason().isSeasonActive(seasonId);
        }
        // Old saved recommendations had no season ID. Keep one only when it was
        // created during the current season, never carry it into a later crop.
        if (zone.getSeason() == null || !zone.getSeason().isActive()) return false;
        long seasonStartedAt = zone.getSeason() == null
                ? 0L : zone.getSeason().getStarted_at_epoch();
        return seasonStartedAt <= 0L || createdAtEpoch >= seasonStartedAt;
    }

    public boolean isRecent(long nowEpoch) {
        return createdAtEpoch > 0 && nowEpoch >= createdAtEpoch
                && nowEpoch - createdAtEpoch <= 14L * 24L * 60L * 60L;
    }
}
