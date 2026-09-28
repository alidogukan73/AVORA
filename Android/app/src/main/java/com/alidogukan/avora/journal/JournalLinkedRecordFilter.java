package com.alidogukan.avora.journal;

import com.alidogukan.avora.models.FertilizerApplication;
import com.alidogukan.avora.models.GardenPhoto;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A shared zone or nearby timestamp does not establish a record relationship. */
public final class JournalLinkedRecordFilter {
    private JournalLinkedRecordFilter() { }

    public static List<FertilizerApplication> selectFertilizers(
            List<FertilizerApplication> applications, List<GardenPhoto> recordPhotos,
            String zoneId, String seasonId) {
        List<FertilizerApplication> result = new ArrayList<>();
        String zone = safe(zoneId);
        String season = safe(seasonId);
        if (zone.isEmpty() || applications == null || recordPhotos == null) return result;
        Set<String> relatedIds = new HashSet<>();
        for (GardenPhoto photo : recordPhotos) {
            if (photo == null || !zone.equals(safe(photo.getZone_id()))) continue;
            String id = safe(photo.getRelated_application_id());
            // These values identify photo groups/analysis sources, not applications.
            if (!id.isEmpty() && !id.startsWith("journal_record_") && !"plant_assistant".equals(id)) {
                relatedIds.add(id);
            }
        }
        for (FertilizerApplication application : applications) {
            if (application == null || !zone.equals(safe(application.getZone_id()))) continue;
            if (!season.isEmpty() && !application.belongsToSeason(season)) continue;
            if (relatedIds.remove(safe(application.getApplication_id()))) result.add(application);
        }
        return result;
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
