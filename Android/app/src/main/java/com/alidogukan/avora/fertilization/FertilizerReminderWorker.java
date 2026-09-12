package com.alidogukan.avora.fertilization;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import com.alidogukan.avora.notifications.GardenNotificationManager;
import com.alidogukan.avora.notifications.NotificationPolicy;
import com.alidogukan.avora.notifications.NotificationSettingsStore;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.alidogukan.avora.R;
import com.alidogukan.avora.config.AppInfo;
import com.alidogukan.avora.firebase.FirebaseWorkerAuthentication;
import com.alidogukan.avora.language.AvoraLanguageManager;
import com.alidogukan.avora.models.FertilizationProfile;
import com.alidogukan.avora.models.FertilizerApplication;
import com.alidogukan.avora.models.FertilizerApplicationSchedule;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.FertilizerProduct;
import com.alidogukan.avora.models.FertilizerRecommendation;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.FirebaseDatabase;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FertilizerReminderWorker extends Worker {

    private static final String PREFS =
            "fertilizer_reminder_state";
    private static final String AI_STATE_PREFS =
            "fertilizer_ai_notification_state";

    public FertilizerReminderWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters
    ) {
        super(context, parameters);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = AvoraLanguageManager.localizedContext(getApplicationContext());
        NotificationSettingsStore notificationSettings =
                new NotificationSettingsStore(context);
        boolean fertilizationEnabled =
                notificationSettings.isCategoryEnabled("fertilization")
                        && notificationSettings.isReminderEnabled("fertilization");
        boolean stockEnabled = notificationSettings.isCategoryEnabled("stock");
        if (!fertilizationEnabled && !stockEnabled) return Result.success();

        try {
            if (!FirebaseWorkerAuthentication.awaitAuthorized(20, TimeUnit.SECONDS)) {
                return Result.success();
            }
            com.google.firebase.database.DatabaseReference deviceRef =
                    FirebaseDatabase.getInstance()
                            .getReference("devices")
                            .child(AppInfo.DEVICE_ID);
            DataSnapshot productSnapshot = Tasks.await(
                    deviceRef.child("fertilizer_products").get(),
                    20,
                    TimeUnit.SECONDS
            );
            Map<String, FertilizerProduct> products = products(productSnapshot);
            notifyLowStockProducts(context, products);
            if (!fertilizationEnabled) return Result.success();

            DataSnapshot zonesSnapshot = Tasks.await(
                    deviceRef.child("zones").get(),
                    20,
                    TimeUnit.SECONDS
            );
            DataSnapshot recommendationSnapshot = Tasks.await(
                    deviceRef.child("fertilization")
                            .child("recommendations")
                            .get(),
                    20,
                    TimeUnit.SECONDS
            );
            DataSnapshot historySnapshot = Tasks.await(
                    deviceRef.child("fertilizer_history").get(),
                    20,
                    TimeUnit.SECONDS
            );
            List<FertilizerRecommendation> recommendations = new ArrayList<>();
            collectRecommendations(recommendationSnapshot, recommendations, "", "");
            List<FertilizerApplication> history = new ArrayList<>();
            collectHistory(historySnapshot, history);

            for (DataSnapshot child : zonesSnapshot.getChildren()) {
                GardenZone zone = child.getValue(GardenZone.class);
                if (zone != null && (zone.getZone_id() == null
                        || zone.getZone_id().isBlank())) {
                    zone.setZone_id(child.getKey());
                }
                List<FertilizerApplication> zoneHistory = historyForActiveSeason(zone, history);
                FertilizerAdvice advice = zone == null
                        ? null
                        : FertilizerDecisionEngine.advise(
                        zone,
                        new ArrayList<>(products.values()),
                        null,
                        zoneHistory,
                        Instant.now().getEpochSecond(),
                        new FertilizationPreferenceStore(context)
                                .preferOrganicInputs()
                );
                notifyIfDue(context, zone, products, recommendations, advice);
                notifyAiAdvice(context, zone, zoneHistory, advice);
            }
            notifyOutcomeFollowUps(context, history, zonesSnapshot);
            return Result.success();
        } catch (Exception error) {
            return Result.retry();
        }
    }

    private Map<String, FertilizerProduct> products(DataSnapshot snapshot) {
        Map<String, FertilizerProduct> products = new HashMap<>();
        for (DataSnapshot child : snapshot.getChildren()) {
            FertilizerProduct product = child.getValue(FertilizerProduct.class);
            if (product == null) continue;
            if (product.getProduct_id() == null
                    || product.getProduct_id().isBlank()) {
                product.setProduct_id(child.getKey());
            }
            products.put(safe(product.getProduct_id(), child.getKey()), product);
        }
        return products;
    }

    /**
     * Inventory safety is independent from a zone schedule. A product may fall
     * below its threshold after a manual or bulk application even when no next
     * application date exists, so check every enabled product once per worker run.
     */
    private void notifyLowStockProducts(
            Context context,
            Map<String, FertilizerProduct> products
    ) {
        NotificationSettingsStore settings = new NotificationSettingsStore(context);
        if (!settings.isCategoryEnabled("stock") || products == null) return;

        GardenNotificationManager manager = new GardenNotificationManager(context);
        for (Map.Entry<String, FertilizerProduct> entry : products.entrySet()) {
            FertilizerProduct product = entry.getValue();
            if (product == null || !product.isEnabled()
                    || product.getLow_stock_threshold() <= 0.0
                    || product.getStock_amount() > product.getLow_stock_threshold()) {
                continue;
            }
            String productId = safe(product.getProduct_id(), safe(entry.getKey(), "unknown"));
            manager.publishOnce(
                    "STOCK",
                    "HIGH",
                    "",
                    context.getString(
                            R.string.notification_fertilizer_low_stock_title,
                            safe(product.getName(), productId)
                    ),
                    context.getString(
                            R.string.notification_fertilizer_low_stock_description,
                            format(product.getStock_amount()),
                            safe(product.getStock_unit(), ""),
                            format(product.getLow_stock_threshold())
                    ),
                    "low_stock:" + productId + ":" + LocalDate.now()
            );
        }
    }

    private void notifyOutcomeFollowUps(
            Context context,
            List<FertilizerApplication> history,
            DataSnapshot zonesSnapshot
    ) {
        NotificationSettingsStore settings = new NotificationSettingsStore(context);
        if (!settings.isCategoryEnabled("fertilization")
                || !settings.isReminderEnabled("fertilization")) return;

        long now = Instant.now().getEpochSecond();
        GardenNotificationManager manager = new GardenNotificationManager(context);
        for (FertilizerApplication application :
                FertilizerOutcomeFollowUpPolicy.latestDuePerZone(history, now)) {
            String sourceKey = FertilizerOutcomeFollowUpPolicy.sourceKey(application);
            if (sourceKey.isBlank()) continue;

            String zoneId = safe(application.getZone_id(), "");
            GardenZone zone = zonesSnapshot.child(zoneId).getValue(GardenZone.class);
            if (!isActiveSeasonTarget(zone)
                    || !applicationBelongsToActiveSeason(zone, application)) continue;
            String zoneName = zone == null
                    ? safe(application.getZone_name(), zoneId)
                    : com.alidogukan.avora.zones.PhysicalZoneIdentity.name(zone);
            String productName = safe(application.getProduct_name(),
                    context.getString(R.string.fertilizer_outcome_follow_up_product));
            manager.publishOnce(
                    "FERTILIZATION",
                    "NORMAL",
                    zoneId,
                    context.getString(R.string.fertilizer_outcome_follow_up_title),
                    context.getString(
                            R.string.fertilizer_outcome_follow_up_message,
                            zoneName,
                            productName
                    ),
                    sourceKey
            );
        }
    }
    private void notifyIfDue(
            Context context,
            GardenZone zone,
            Map<String, FertilizerProduct> products,
            List<FertilizerRecommendation> recommendations,
            FertilizerAdvice advice
    ) {
        if (!isActiveSeasonTarget(zone)) {
            return;
        }
        FertilizationProfile profile = zone.getFertilization();
        if (profile == null || !profile.isEnabled() || !profile.isReminder_enabled()
                || FertilizerStagePolicy.SEASON_END.equals(
                        FertilizerStagePolicy.normalize(profile.getGrowth_stage())
                )) {
            return;
        }

        FertilizerAdvice.Recommendation next = advice == null
                ? FertilizerAdvice.Recommendation.none()
                : advice.getRecommendation();
        if (!next.isAvailable()) {
            return;
        }
        boolean applicationDecisionReady = FertilizerAdvice.STATUS_TODAY_ADVICE.equals(
                advice.getStatus()
        ) && next.isApplicationReady();
        if (next.getWaitDays() <= 0L && !applicationDecisionReady) {
            return;
        }

        String applicationType = safe(
                next.getApplicationType(),
                "NUTRITION"
        );
        long nextApplicationEpoch = 0L;
        FertilizerApplicationSchedule schedule =
                profile.getApplication_schedules() == null
                        ? null
                        : profile.getApplication_schedules().get(applicationType);
        if (schedule != null) {
            nextApplicationEpoch = schedule.getNext_application_at_epoch();
        } else if ("NUTRITION".equals(applicationType)) {
            nextApplicationEpoch = profile.getNext_application_at_epoch();
        }
        if (nextApplicationEpoch <= 0L && applicationDecisionReady) {
            nextApplicationEpoch = Instant.now().getEpochSecond();
        }
        if (nextApplicationEpoch <= 0L) {
            return;
        }

        notifySchedule(
                context,
                zone,
                profile,
                products,
                recommendations,
                applicationType,
                next.getNeed(),
                next.getProductId(),
                nextApplicationEpoch
        );
    }

    private void notifySchedule(
            Context context,
            GardenZone zone,
            FertilizationProfile profile,
            Map<String, FertilizerProduct> products,
            List<FertilizerRecommendation> recommendations,
            String applicationType,
            String need,
            String productId,
            long nextApplicationEpoch
    ) {
        if (nextApplicationEpoch <= 0L) return;

        LocalDate due = Instant.ofEpochSecond(
                nextApplicationEpoch
        ).atZone(ZoneId.systemDefault()).toLocalDate();
        long days = ChronoUnit.DAYS.between(LocalDate.now(), due);
        String slot = reminderSlot(days, LocalTime.now());
        if (slot == null) return;

        String zoneId = safe(zone.getZone_id(), "unknown");
        String preferenceKey = zoneId + ":" + applicationType + ":" + due + ":" + slot;
        SharedPreferences preferences = context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
        );
        if (preferences.getBoolean(preferenceKey, false)) {
            return;
        }

        String zoneName = com.alidogukan.avora.zones.PhysicalZoneIdentity.name(zone);
        String title = days == 0L
                ? context.getString(R.string.notification_fertilizer_due_today_title)
                : days == -1L
                ? context.getString(R.string.notification_fertilizer_gentle_title)
                : context.getString(R.string.notification_fertilizer_final_title);
        String message = context.getString(
                R.string.notification_fertilizer_waiting_description,
                zoneName,
                safe(need, applicationTypeLabel(context, applicationType))
        );
        FertilizerProduct product = products.get(productId);
        if (product != null) {
            FertilizerApplicationSafety.Dose dose = calculateDose(
                    zone,
                    profile,
                    product,
                    recommendations
            );
            if (!dose.isSupported()) {
                message += context.getString(
                        R.string
                                .fertilizer_notification_measurement_missing
                );
            } else {
                message += context.getString(
                        R.string.fertilizer_notification_dose,
                        product.getName(),
                        format(dose.getMinAmount()),
                        format(dose.getMaxAmount()),
                        dose.getUnit()
                );
                String stockUnit = safe(product.getStock_unit(), "");
                if (stockUnit.isBlank()
                        || !stockUnit.equalsIgnoreCase(dose.getUnit())) {
                    message += context.getString(
                            R.string
                                    .fertilizer_notification_stock_unknown
                    );
                } else if (product.getStock_amount() < dose.getMaxAmount()) {
                    message += context.getString(
                            R.string
                                    .fertilizer_notification_stock_short,
                            format(
                                    dose.getMaxAmount()
                                            - product.getStock_amount()
                            ),
                            dose.getUnit()
                    );
                } else {
                    message += context.getString(
                            R.string.fertilizer_notification_stock_ok
                    );
                }
            }
        }

        NotificationSettingsStore notificationSettings =
                new NotificationSettingsStore(context);

        if (notificationSettings.isCategoryEnabled("fertilization")
                && notificationSettings.isReminderEnabled("fertilization")) {
            new GardenNotificationManager(context).publishOnce(
                    "FERTILIZATION", days <= 0 ? "HIGH" : "NORMAL", zoneId, title, message,
                    "fertilizer_reminder:" + preferenceKey
            );
            preferences.edit().putBoolean(preferenceKey, true).apply();
        }
    }

    private static String reminderSlot(long days, LocalTime time) {
        if (days == 0L) {
            if (time.isBefore(LocalTime.of(8, 0))) return null;
            if (time.isBefore(LocalTime.of(12, 0))) return "morning";
            if (time.isBefore(LocalTime.of(18, 0))) return "noon";
            return "evening";
        }
        if (days == -1L) return "next_day";
        return days == -7L ? "final" : null;
    }

    private static String applicationTypeLabel(Context context, String type) {
        if ("ORGANIC".equals(type)) {
            return context.getString(R.string.notification_fertilizer_type_organic);
        }
        if ("CONDITIONER".equals(type)) {
            return context.getString(R.string.notification_fertilizer_type_conditioner);
        }
        if ("BIOSTIMULANT".equals(type)) {
            return context.getString(R.string.notification_fertilizer_type_biostimulant);
        }
        return context.getString(R.string.notification_fertilizer_type_nutrition);
    }

    private static FertilizerApplicationSafety.Dose calculateDose(
            GardenZone zone,
            FertilizationProfile profile,
            FertilizerProduct product,
            List<FertilizerRecommendation> recommendations
    ) {
        double min = product.getLabel_dosage_min() > 0.0
                ? product.getLabel_dosage_min()
                : product.getLabel_dosage();
        double max = product.getLabel_dosage_max() > 0.0
                ? product.getLabel_dosage_max()
                : product.getLabel_dosage();
        String sourceUnit = safe(product.getDosage_unit(), "");
        for (FertilizerRecommendation recommendation
                : recommendations) {
            if (safe(zone.getPlant_type(), "").equals(
                    safe(recommendation.getPlant_type(), "")
            ) && safe(profile.getGrowth_stage(), "").equals(
                    safe(recommendation.getGrowth_stage(), "")
            ) && safe(product.getProduct_id(), "").equals(
                    safe(recommendation.getProduct_id(), "")
            )) {
                min = recommendation.getDose_min();
                max = recommendation.getDose_max();
                sourceUnit = safe(
                        recommendation.getDose_unit(),
                        sourceUnit
                );
                break;
            }
        }
        return FertilizerApplicationSafety.calculateDose(
                profile, min, max, sourceUnit);
    }

    private static void collectRecommendations(
            DataSnapshot snapshot,
            List<FertilizerRecommendation> output,
            String plantType,
            String growthStage
    ) {
        FertilizerRecommendation direct = null;

        if (snapshot.hasChild("product_id")) {
            direct = snapshot.getValue(
                    FertilizerRecommendation.class
            );
        }

        if (direct != null
                && direct.getProduct_id() != null
                && !direct.getProduct_id().isBlank()) {
            direct.setPlant_type(plantType);
            direct.setGrowth_stage(growthStage);
            output.add(direct);
            return;
        }
        for (DataSnapshot child : snapshot.getChildren()) {
            String nextPlant = plantType;
            String nextStage = growthStage;
            if (plantType.isBlank()) {
                nextPlant = child.getKey();
            } else if (growthStage.isBlank()) {
                nextStage = child.getKey();
            }
            collectRecommendations(child, output, nextPlant, nextStage);
        }
    }

    private static void collectHistory(
            DataSnapshot snapshot,
            List<FertilizerApplication> output
    ) {
        if (snapshot == null || !snapshot.exists()) return;
        if (snapshot.hasChild("applied_at_epoch")
                || snapshot.hasChild("product_id")) {
            FertilizerApplication application = snapshot.getValue(
                    FertilizerApplication.class
            );
            if (application != null) {
                if (application.getApplication_id() == null
                        || application.getApplication_id().isBlank()) {
                    application.setApplication_id(snapshot.getKey());
                }
                output.add(application);
            }
            return;
        }
        for (DataSnapshot child : snapshot.getChildren()) {
            collectHistory(child, output);
        }
    }

    private void notifyAiAdvice(
            Context context,
            GardenZone zone,
            List<FertilizerApplication> history,
            FertilizerAdvice advice
    ) {
        if (!isActiveSeasonTarget(zone) || advice == null) return;
        FertilizationProfile profile = zone.getFertilization();
        if (profile == null || !profile.isEnabled()
                || !profile.isReminder_enabled()) return;
        NotificationSettingsStore settings = new NotificationSettingsStore(context);
        if (!settings.isCategoryEnabled("fertilization")
                || !settings.isReminderEnabled("fertilization")) {
            return;
        }


        String zoneId = safe(zone.getZone_id(), "unknown");
        String stateKey = "fertilizer_ai:" + zoneId;
        SharedPreferences state = context.getSharedPreferences(
                AI_STATE_PREFS,
                Context.MODE_PRIVATE
        );
        if (!NotificationPolicy.isActionableFertilizerAdvice(advice.getStatus())) {
            state.edit().remove(stateKey).apply();
            return;
        }

        String stage = safe(profile.getGrowth_stage(), "");
        String leadingCandidate = advice.getCandidates().isEmpty()
                ? ""
                : safe(advice.getCandidates().get(0), "");
        String legacyFingerprint = stage + "|" + advice.getStatus() + "|" + leadingCandidate;
        long latestApplicationEpoch = latestApplicationEpoch(history, zoneId);
        String fingerprint = legacyFingerprint + "|" + latestApplicationEpoch;
        String previousFingerprint = state.getString(stateKey, "");
        if (fingerprint.equals(previousFingerprint)) return;
        // Existing installations used a fingerprint without the latest application time.
        // Upgrade it silently so updating the app does not replay an old AI suggestion.
        if (legacyFingerprint.equals(previousFingerprint)) {
            state.edit().putString(stateKey, fingerprint).apply();
            return;
        }

        String title;
        String priority = "NORMAL";
        switch (advice.getStatus()) {
            case FertilizerAdvice.STATUS_ORGANIC_REQUIRED:
                title = context.getString(R.string.notification_fertilizer_ai_organic_title);
                priority = "HIGH";
                break;
            case FertilizerAdvice.STATUS_WATERING_FIRST:
                title = context.getString(R.string.notification_fertilizer_ai_water_first_title);
                priority = "HIGH";
                break;
            case FertilizerAdvice.STATUS_PREPARATION_REQUIRED:
                title = context.getString(R.string.notification_fertilizer_ai_preparation_title);
                break;
            default:
                title = context.getString(R.string.notification_fertilizer_ai_ready_title);
                break;
        }
        String zoneName = com.alidogukan.avora.zones.PhysicalZoneIdentity.name(zone);
        new GardenNotificationManager(context).publishOnce(
                "FERTILIZATION",
                priority,
                zoneId,
                title,
                context.getString(
                        R.string.notification_fertilizer_ai_description,
                        zoneName,
                        safe(advice.getReason(), advice.getStatus())
                ),
                "fertilizer_ai:" + zoneId + ":"
                        + Integer.toHexString(fingerprint.hashCode())
        );
        state.edit().putString(stateKey, fingerprint).apply();
    }


    private static boolean isActiveSeasonTarget(GardenZone zone) {
        if (zone == null || zone.getSeason() == null) return false;
        return NotificationPolicy.isActiveSeasonNotificationTarget(
                zone.isEnabled(),
                zone.getSeason().isActive(),
                zone.getSeason().getActive_season_id());
    }

    private static boolean applicationBelongsToActiveSeason(
            GardenZone zone,
            FertilizerApplication application) {
        if (zone == null || zone.getSeason() == null || application == null) return false;
        if (application.hasExplicitSeasonMembership()) {
            for (String seasonId : application.getSeason_ids()) {
                if (zone.getSeason().isSeasonActive(seasonId)) return true;
            }
            return false;
        }
        return NotificationPolicy.recordBelongsToActiveSeason(
                zone.isEnabled(),
                zone.getSeason().isActive(),
                zone.getSeason().getActive_season_id(),
                zone.getSeason().isInclude_legacy_records(),
                application.getSeason_id());
    }

    private static List<FertilizerApplication> historyForActiveSeason(
            GardenZone zone,
            List<FertilizerApplication> history) {
        List<FertilizerApplication> result = new ArrayList<>();
        if (history == null) return result;
        for (FertilizerApplication application : history) {
            if (applicationBelongsToActiveSeason(zone, application)) result.add(application);
        }
        return result;
    }
    private static long latestApplicationEpoch(
            List<FertilizerApplication> history,
            String zoneId
    ) {
        long latest = 0L;
        if (history == null || zoneId == null || zoneId.isBlank()) return latest;
        for (FertilizerApplication application : history) {
            if (application == null || !zoneId.equals(application.getZone_id())) continue;
            latest = Math.max(latest, application.getApplied_at_epoch());
        }
        return latest;
    }

    private static String format(double value) {
        return value == Math.rint(value)
                ? String.format(Locale.getDefault(), "%.0f", value)
                : String.format(Locale.getDefault(), "%.1f", value);
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
