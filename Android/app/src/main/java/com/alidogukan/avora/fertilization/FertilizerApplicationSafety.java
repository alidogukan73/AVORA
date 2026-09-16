package com.alidogukan.avora.fertilization;

import com.alidogukan.avora.models.FertilizationProfile;
import com.alidogukan.avora.models.FertilizerApplicationSchedule;
import com.alidogukan.avora.models.FertilizerProduct;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure validation and calculation rules shared by safe fertilizer recording flows.
 */
public final class FertilizerApplicationSafety {

    private FertilizerApplicationSafety() {
    }

    public static String applicationType(FertilizerProduct product) {
        String value = product == null ? "" : safe(product.getApplication_type());
        return value.isEmpty() ? "NUTRITION" : value.toUpperCase(Locale.ROOT);
    }

    public static boolean isStageCompatible(
            FertilizerProduct product,
            FertilizationProfile profile
    ) {
        if (product == null || profile == null || !profile.isEnabled()) {
            return false;
        }
        if (FertilizerStagePolicy.SEASON_END.equals(
                FertilizerStagePolicy.normalize(profile.getGrowth_stage()))) {
            return false;
        }
        List<String> stages = FertilizerStagePolicy.effectiveStages(product);
        // Missing period information is a safety gap, not permission to use
        // the product throughout the whole season.
        if (stages.isEmpty()) {
            return false;
        }
        String currentStage = FertilizerStagePolicy.normalize(
                profile.getGrowth_stage()
        );
        if (currentStage.isEmpty()) {
            return false;
        }
        for (String stage : stages) {
            String normalized = FertilizerStagePolicy.normalize(stage);
            if (currentStage.equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    public static Dose calculateDose(
            FertilizerProduct product,
            FertilizationProfile profile
    ) {
        if (product == null || profile == null) {
            return Dose.unsupported();
        }
        double labelDose = product.getLabel_dosage_min() > 0.0
                ? product.getLabel_dosage_min()
                : product.getLabel_dosage();
        double labelDoseMax = product.getLabel_dosage_max() > 0.0
                ? product.getLabel_dosage_max()
                : labelDose;
        return calculateDose(
                profile,
                labelDose,
                labelDoseMax,
                product.getDosage_unit()
        );
    }

    public static Dose calculateDose(
            FertilizationProfile profile,
            double labelDoseMin,
            double labelDoseMax,
            String dosageUnit
    ) {
        if (profile == null || !Double.isFinite(labelDoseMin) || labelDoseMin <= 0.0 || !Double.isFinite(labelDoseMax)) {
            return Dose.unsupported();
        }
        double safeMax = labelDoseMax > 0.0
                ? Math.max(labelDoseMin, labelDoseMax)
                : labelDoseMin;

        String unit = normalizeUnit(dosageUnit);
        double area = profile.getArea_m2();
        double tank = profile.getTank_liters();
        if (!Double.isFinite(area) || !Double.isFinite(tank)) {
            return Dose.unsupported();
        }

        if (unit.contains("kg/dekar") && area > 0.0) {
            return new Dose(labelDoseMin * area, safeMax * area, "g", false);
        }
        if (unit.contains("l/dekar") && area > 0.0) {
            return new Dose(labelDoseMin * area, safeMax * area, "ml", false);
        }
        if (unit.contains("g/dekar") && area > 0.0) {
            return new Dose(labelDoseMin * area / 1000.0,
                    safeMax * area / 1000.0, "g", false);
        }
        if (unit.contains("ml/dekar") && area > 0.0) {
            return new Dose(labelDoseMin * area / 1000.0,
                    safeMax * area / 1000.0, "ml", false);
        }
        if (unit.contains("g/100l") && tank > 0.0) {
            return new Dose(labelDoseMin * tank / 100.0,
                    safeMax * tank / 100.0, "g", true);
        }
        if (unit.contains("ml/100l") && tank > 0.0) {
            return new Dose(labelDoseMin * tank / 100.0,
                    safeMax * tank / 100.0, "ml", true);
        }
        if ((unit.contains("g/litresu") || unit.contains("g/lsu")) && tank > 0.0) {
            return new Dose(labelDoseMin * tank, safeMax * tank, "g", true);
        }
        if ((unit.contains("ml/litresu") || unit.contains("ml/lsu")) && tank > 0.0) {
            return new Dose(labelDoseMin * tank, safeMax * tank, "ml", true);
        }
        return Dose.unsupported();
    }

    public static long nextApplicationAt(
            FertilizationProfile profile,
            String applicationType
    ) {
        if (profile == null) {
            return 0L;
        }
        Map<String, FertilizerApplicationSchedule> schedules =
                profile.getApplication_schedules();
        if (schedules != null && !schedules.isEmpty()) {
            FertilizerApplicationSchedule schedule = schedules.get(
                    safe(applicationType).toUpperCase(Locale.ROOT)
            );
            if (schedule != null) {
                return schedule.getNext_application_at_epoch();
            }
        }
        return "NUTRITION".equalsIgnoreCase(applicationType)
                ? profile.getNext_application_at_epoch()
                : 0L;
    }

    public static boolean isRepeatIntervalBlocked(
            FertilizationProfile profile,
            String applicationType,
            long appliedAtEpoch
    ) {
        long nextApplicationAt = nextApplicationAt(profile, applicationType);
        return nextApplicationAt > 0L && appliedAtEpoch < nextApplicationAt;
    }

    public static boolean isStockUnitCompatible(
            FertilizerProduct product,
            String appliedUnit
    ) {
        return product != null
                && !safe(product.getStock_unit()).isEmpty()
                && safe(product.getStock_unit()).equalsIgnoreCase(safe(appliedUnit));
    }

    public static boolean hasEnoughStock(
            FertilizerProduct product,
            double requiredAmount
    ) {
        return product != null
                && Double.isFinite(requiredAmount)
                && requiredAmount > 0.0
                && Double.isFinite(product.getStock_amount())
                && product.getStock_amount() + 0.000001 >= requiredAmount;
    }

    static String normalizeUnit(String value) {
        return safe(value)
                .toLowerCase(Locale.ROOT)
                .replace("ı", "i")
                .replace("İ", "i")
                .replace("·", "")
                .replace(" ", "");
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Dose {
        private final double minAmount;
        private final double maxAmount;
        private final String unit;
        private final boolean tankBased;

        Dose(double minAmount, double maxAmount, String unit, boolean tankBased) {
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
            this.unit = unit;
            this.tankBased = tankBased;
        }

        static Dose unsupported() {
            return new Dose(0.0, 0.0, "", false);
        }

        public double getAmount() {
            return minAmount;
        }

        public double getMinAmount() {
            return minAmount;
        }

        public double getMaxAmount() {
            return maxAmount;
        }

        public String getUnit() {
            return unit;
        }

        public boolean isTankBased() {
            return tankBased;
        }

        public boolean isSupported() {
            return Double.isFinite(minAmount) && Double.isFinite(maxAmount) && minAmount > 0.0 && maxAmount >= minAmount && !unit.isEmpty();
        }
    }
}
