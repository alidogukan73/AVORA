package com.alidogukan.avora.fertilization;

import com.alidogukan.avora.models.FertilizationProfile;
import com.alidogukan.avora.models.FertilizerApplicationSchedule;

/** Dates belong to an application type; a conditioner is not a nutrition dose. */
public final class FertilizerApplicationTiming {
    private FertilizerApplicationTiming() {}

    public static FertilizerApplicationSchedule schedule(FertilizationProfile profile, String type) {
        if (profile != null && profile.getApplication_schedules() != null) {
            FertilizerApplicationSchedule value = profile.getApplication_schedules().get(type);
            if (value != null) return value;
        }
        FertilizerApplicationSchedule result = new FertilizerApplicationSchedule();
        if (profile != null && "NUTRITION".equals(type)) {
            result.setLast_application_at_epoch(profile.getLast_application_at_epoch());
            result.setNext_application_at_epoch(profile.getNext_application_at_epoch());
        }
        return result;
    }

    public static boolean alreadyRecorded(FertilizationProfile profile, String type,
                                          String productId, long appliedAt) {
        FertilizerApplicationSchedule value = schedule(profile, type);
        return appliedAt > 0 && value.getLast_application_at_epoch() == appliedAt
                && productId != null && productId.equals(value.getProduct_id());
    }

    public static long plannedNextApplication(FertilizationProfile profile) {
        if (profile == null) return 0L;
        String productId = profile.getActive_product_id();
        if (productId != null && !productId.isBlank() && profile.getApplication_schedules() != null) {
            for (FertilizerApplicationSchedule value : profile.getApplication_schedules().values()) {
                if (value != null && productId.equals(value.getProduct_id())
                        && value.getLast_application_at_epoch() > 0L) {
                    return value.getNext_application_at_epoch();
                }
            }
        }
        return profile.getNext_application_at_epoch();
    }
}
