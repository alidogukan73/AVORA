package com.alidogukan.avora.journal;

/** Validation shared by manual journal entry and its tests. */
public final class JournalEntryPolicy {
    private JournalEntryPolicy() { }

    public static boolean isMilestone(String type) {
        return "planting".equals(type) || "flowering".equals(type)
                || "first_product".equals(type) || "harvest".equals(type)
                || "special".equals(type);
    }

    public static boolean isManualEvent(String type) {
        return "observation".equals(type) || "event".equals(type) || isMilestone(type);
    }

    public static boolean validContent(String type, String note, int photoCount) {
        if (photoCount < 0 || photoCount > 5) return false;
        if ("photo".equals(type)) return photoCount > 0;
        return isManualEvent(type) && note != null && !note.trim().isEmpty();
    }

    public static com.alidogukan.avora.models.GardenEvent photoOwner(
            com.alidogukan.avora.models.GardenPhoto photo,
            java.util.List<com.alidogukan.avora.models.GardenEvent> events) {
        if (photo == null || events == null) return null;
        for (com.alidogukan.avora.models.GardenEvent event : events) {
            if (event != null && !event.getId().isBlank() && "MANUAL".equals(event.getSource())
                    && ("journal_record_" + event.getId()).equals(photo.getRelated_application_id())
                    && event.getZone_id().equals(photo.getZone_id())
                    && event.getSeason_id().equals(photo.getSeason_id())) return event;
        }
        return null;
    }

    public static boolean writableSeason(com.alidogukan.avora.models.GardenZone zone,
            com.alidogukan.avora.models.GardenSeason season) {
        return zone != null && season != null && !season.getSeason_id().isBlank()
                && season.getZone_id().equals(zone.getZone_id())
                && !com.alidogukan.avora.zones.ZoneCapacityPolicy.isInactive(zone)
                && com.alidogukan.avora.models.SeasonStatus.isActive(season.getStatus())
                && zone.getSeason() != null && zone.getSeason().isActive()
                && zone.getSeason().isSeasonActive(season.getSeason_id())
                && com.alidogukan.avora.season.ZoneAreaIdentity.belongsToCurrentOrArea(zone, season);
    }

    public static boolean validDate(long timestamp, long seasonStart, long now) {
        return timestamp > 0 && timestamp <= now
                && (seasonStart <= 0 || timestamp >= seasonStart);
    }
}
