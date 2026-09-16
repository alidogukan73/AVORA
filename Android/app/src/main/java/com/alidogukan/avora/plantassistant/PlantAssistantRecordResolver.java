package com.alidogukan.avora.plantassistant;

import com.alidogukan.avora.models.GardenPhoto;
import java.util.List;

/** Finds the photo analysis that originally created a garden-health warning. */
public final class PlantAssistantRecordResolver {
    private PlantAssistantRecordResolver() { }

    public static GardenPhoto find(List<GardenPhoto> photos, String zoneId, String seasonId,
                                   String recordId, String issueReason) {
        GardenPhoto selected = null;
        if (photos == null) return null;
        for (GardenPhoto photo : photos) {
            if (photo == null || !clean(zoneId).equals(clean(photo.getZone_id()))) continue;
            if (!clean(seasonId).isEmpty() && !clean(seasonId).equals(clean(photo.getSeason_id()))) continue;
            boolean exact = !clean(recordId).isEmpty() && clean(recordId).equals(clean(photo.getId()));
            String title = clean(photo.getAnalysis_title());
            boolean legacyMatch = clean(recordId).isEmpty() && !title.isEmpty()
                    && clean(issueReason).contains(title);
            if (!exact && !legacyMatch) continue;
            if (selected == null || photo.getCaptured_at_epoch() > selected.getCaptured_at_epoch()) selected = photo;
        }
        return selected;
    }

    /** For old signals, accept only a same-scope analysis captured near that signal. */
    public static PlantAssistantHealthSignal enrich(PlantAssistantHealthSignal signal,
                                                     List<GardenPhoto> photos) {
        if (signal == null || photos == null) return signal;
        if (!clean(signal.getAdvice()).isEmpty() && !clean(signal.getRecordId()).isEmpty()) {
            return signal;
        }
        GardenPhoto best = null;
        long closest = Long.MAX_VALUE;
        for (GardenPhoto photo : photos) {
            if (photo == null || !clean(signal.getZoneId()).equals(clean(photo.getZone_id()))) continue;
            if (!clean(signal.getSeasonId()).isEmpty()
                    && !clean(signal.getSeasonId()).equals(clean(photo.getSeason_id()))) continue;
            boolean exactId = !clean(signal.getRecordId()).isEmpty()
                    && clean(signal.getRecordId()).equals(clean(photo.getId()));
            if (!exactId && !clean(signal.getTitle()).equals(clean(photo.getAnalysis_title()))) continue;
            long delta = Math.abs(signal.getCreatedAtEpoch() - photo.getCaptured_at_epoch());
            if (!exactId && (photo.getCaptured_at_epoch() <= 0L || delta > 24L * 60L * 60L)) continue;
            if (best == null || exactId || delta < closest) {
                best = photo;
                closest = delta;
                if (exactId) break;
            }
        }
        if (best == null) return signal;
        String advice = clean(signal.getAdvice()).isEmpty()
                ? clean(best.getAnalysis_advice()) : signal.getAdvice();
        String id = clean(signal.getRecordId()).isEmpty() ? clean(best.getId()) : signal.getRecordId();
        return new PlantAssistantHealthSignal(signal.getZoneId(), signal.getSeasonId(),
                signal.getUrgency(), signal.getTitle(), advice, id,
                signal.getCreatedAtEpoch());
    }
    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }
}
