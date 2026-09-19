package com.alidogukan.avora.photos;

import com.alidogukan.avora.models.GardenPhoto;

import java.util.ArrayList;
import java.util.List;

/** Selects only the photos that belong to the journal record being viewed. */
public final class JournalPhotoRecordFilter {
    public static boolean isRecordGroup(String value) {
        String group = safe(value);
        return !group.isEmpty() && !"plant_assistant".equals(group);
    }

    private JournalPhotoRecordFilter() { }

    public static List<GardenPhoto> select(List<GardenPhoto> photos,
                                           String zoneId,
                                           String groupId,
                                           String selectedPath) {
        return select(photos, zoneId, "", groupId, selectedPath);
    }

    public static List<GardenPhoto> select(List<GardenPhoto> photos, String zoneId,
                                           String seasonId, String groupId, String selectedPath) {
        List<GardenPhoto> result = new ArrayList<>();
        if (photos == null) return result;
        String targetZone = safe(zoneId);
        String targetGroup = safe(groupId);
        String targetPath = safe(selectedPath);
        boolean groupedJournalRecord = isRecordGroup(targetGroup);

        for (GardenPhoto photo : photos) {
            if (photo == null || !targetZone.equals(safe(photo.getZone_id()))) continue;
            if (!safe(seasonId).isEmpty() && !safe(seasonId).equals(safe(photo.getSeason_id()))) continue;
            if (groupedJournalRecord) {
                if (targetGroup.equals(safe(photo.getRelated_application_id()))) {
                    result.add(photo);
                }
            } else if (!targetPath.isBlank()
                    && targetPath.equals(safe(photo.getLocal_path()))) {
                result.add(photo);
                break;
            }
        }
        return result;
    }

    public static List<GardenPhoto> selectById(List<GardenPhoto> photos,
                                               String zoneId,
                                               String selectedPhotoId) {
        List<GardenPhoto> result = new ArrayList<>();
        String targetZone = safe(zoneId);
        String targetId = safe(selectedPhotoId);
        if (photos == null || targetId.isBlank()) return result;
        for (GardenPhoto photo : photos) {
            if (photo == null || !targetId.equals(safe(photo.getId()))) continue;
            if (!targetZone.isBlank() && !targetZone.equals(safe(photo.getZone_id()))) continue;
            result.add(photo);
            break;
        }
        return result;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
