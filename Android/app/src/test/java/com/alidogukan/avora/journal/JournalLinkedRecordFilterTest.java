package com.alidogukan.avora.journal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.alidogukan.avora.models.FertilizerApplication;
import com.alidogukan.avora.models.GardenPhoto;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class JournalLinkedRecordFilterTest {
    @Test public void observationWithoutPhotosDoesNotShowZoneFertilization() {
        assertTrue(select(Collections.singletonList(application("fertilizer", "zone", "season")),
                Collections.emptyList()).isEmpty());
    }

    @Test public void milestonePhotosDoNotLinkTheirGroupToFertilization() {
        assertTrue(select(Arrays.asList(application("fertilizer", "zone", "season"),
                        application("journal_record_event", "zone", "season")),
                Collections.singletonList(photo("journal_record_event", "zone"))).isEmpty());
    }

    @Test public void analysisSourceLabelIsNotAnApplicationLink() {
        assertTrue(select(Collections.singletonList(application("plant_assistant", "zone", "season")),
                Collections.singletonList(photo("plant_assistant", "zone"))).isEmpty());
    }

    @Test public void explicitApplicationLinkSurvivesEvenAtSameTimestamp() {
        FertilizerApplication linked = application("linked", "zone", "season");
        linked.setApplied_at_epoch(1234L);
        GardenPhoto photo = photo("linked", "zone");
        photo.setCaptured_at_epoch(1234L);
        assertEquals(Collections.singletonList(linked), select(Arrays.asList(
                application("unrelated", "zone", "season"), linked), Arrays.asList(photo, photo)));
    }

    @Test public void applicationLinkCannotCrossZoneOrSeason() {
        assertTrue(select(Arrays.asList(application("linked", "other-zone", "season"),
                        application("linked", "zone", "old-season")),
                Collections.singletonList(photo("linked", "zone"))).isEmpty());
        assertTrue(select(Collections.singletonList(application("linked", "zone", "season")),
                Collections.singletonList(photo("linked", "other-zone"))).isEmpty());
    }

    @Test public void explicitMultiSeasonMembershipIsRespected() {
        FertilizerApplication linked = application("linked", "zone", "old-season");
        linked.setSeason_ids(Arrays.asList("old-season", "season"));
        assertEquals(Collections.singletonList(linked), select(Collections.singletonList(linked),
                Collections.singletonList(photo("linked", "zone"))));
    }

    private List<FertilizerApplication> select(List<FertilizerApplication> applications,
                                               List<GardenPhoto> photos) {
        return JournalLinkedRecordFilter.selectFertilizers(applications, photos, "zone", "season");
    }

    private FertilizerApplication application(String id, String zone, String season) {
        FertilizerApplication application = new FertilizerApplication();
        application.setApplication_id(id);
        application.setZone_id(zone);
        application.setSeason_id(season);
        return application;
    }

    private GardenPhoto photo(String applicationId, String zone) {
        GardenPhoto photo = new GardenPhoto();
        photo.setRelated_application_id(applicationId);
        photo.setZone_id(zone);
        return photo;
    }
}
