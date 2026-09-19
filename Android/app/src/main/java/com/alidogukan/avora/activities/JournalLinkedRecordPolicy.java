package com.alidogukan.avora.activities;

import com.alidogukan.avora.models.FertilizerApplication;
import com.alidogukan.avora.models.WateringHistory;

import java.util.List;

/** Allows only records that carry the journal entry's explicit relation id. */
final class JournalLinkedRecordPolicy {
    private JournalLinkedRecordPolicy() {}

    static boolean matchesFertilizer(
            String relationId,
            String zoneId,
            String seasonId,
            FertilizerApplication item
    ) {
        if (item == null || !same(relationId, item.getApplication_id())) return false;
        return same(zoneId, item.getZone_id()) && belongsToSeason(
                seasonId, item.getSeason_id(), item.getSeason_ids());
    }

    static boolean matchesWatering(
            String relationId,
            String zoneId,
            String seasonId,
            WateringHistory item
    ) {
        if (item == null || !item.isCompleted() || !same(relationId, item.getRecordId())) {
            return false;
        }
        return same(zoneId, item.getZoneId()) && belongsToSeason(
                seasonId, item.getSeasonId(), item.getSeasonIds());
    }

    private static boolean belongsToSeason(
            String expectedSeasonId,
            String legacySeasonId,
            List<String> seasonIds
    ) {
        String expected = safe(expectedSeasonId);
        if (expected.isEmpty()) return true;
        if (seasonIds != null && !seasonIds.isEmpty()) {
            for (String value : seasonIds) {
                if (expected.equals(safe(value))) return true;
            }
            return false;
        }
        return expected.equals(safe(legacySeasonId));
    }

    private static boolean same(String left, String right) {
        String normalizedLeft = safe(left);
        return !normalizedLeft.isEmpty() && normalizedLeft.equals(safe(right));
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
