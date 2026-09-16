package com.alidogukan.avora.fertilization;

import com.alidogukan.avora.models.FertilizationProfile;
import com.alidogukan.avora.models.FertilizerApplication;
import com.alidogukan.avora.models.FertilizerProduct;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.WeatherForecast;
import com.alidogukan.avora.settings.DisplayUnitFormatter;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Safe, explainable advisor. It ranks only products already entered by the user. */
public final class FertilizerDecisionEngine {
    private FertilizerDecisionEngine() { }

    public static FertilizerAdvice advise(GardenZone zone, List<FertilizerProduct> products,
                                           WeatherForecast weather, long now) {
        return advise(zone, products, weather, new ArrayList<>(), now);
    }

    public static FertilizerAdvice advise(GardenZone zone, List<FertilizerProduct> products,
                                           WeatherForecast weather,
                                           List<FertilizerApplication> history,
                                           long now) {
        return advise(zone, products, weather, history, now, false);
    }

    public static FertilizerAdvice advise(GardenZone zone, List<FertilizerProduct> products,
                                           WeatherForecast weather,
                                           List<FertilizerApplication> history,
                                           long now,
                                           boolean preferOrganicInputs) {
        return advise(zone, products, weather, history, now, preferOrganicInputs,
                DisplayUnitFormatter.metric());
    }

    public static FertilizerAdvice advise(GardenZone zone, List<FertilizerProduct> products,
                                           WeatherForecast weather,
                                           List<FertilizerApplication> history,
                                           long now,
                                           boolean preferOrganicInputs,
                                           DisplayUnitFormatter units) {
        if (zone == null) {
            return new FertilizerAdvice("Bölge", FertilizerAdvice.STATUS_PLAN_NOT_READY,
                    "Gübreleme önerisi için bir bölge seçin.", "", new ArrayList<>(), new ArrayList<>());
        }
        FertilizationProfile profile = zone.getFertilization();
        if (products == null) products = new ArrayList<>();
        String title = com.alidogukan.avora.zones.PhysicalZoneIdentity.name(zone);
        String normalizedStage = profile == null
                ? "NOT_SET"
                : FertilizerStagePolicy.normalize(profile.getGrowth_stage());
        if (profile == null || "NOT_SET".equals(normalizedStage)) {
            return new FertilizerAdvice(title, FertilizerAdvice.STATUS_PLAN_NOT_READY,
                    "Dikim tarihi ve gelişim dönemi girildiğinde kişisel öneri hazırlanır.",
                    "", new ArrayList<>(),
                    combinedRisks(zone, products, weather, history, now));
        }
        String context = buildContext(zone, profile, weather, now, units);
        if (FertilizerStagePolicy.SEASON_END.equals(normalizedStage)) {
            return new FertilizerAdvice(title, FertilizerAdvice.STATUS_SEASON_COMPLETED,
                    "Besleme gübresi önerilmez. Gelecek sezon için toprak analizi, organik madde ve taban gübresi planını hazırlayın.",
                    context, new ArrayList<>(),
                    new ArrayList<>());
        }
        if (!profile.isEnabled()) {
            return new FertilizerAdvice(title, FertilizerAdvice.STATUS_PLAN_INACTIVE,
                    "Bu bölgenin gübreleme planı kapalı. Önerileri yeniden görmek için planı etkinleştirin.",
                    context, new ArrayList<>(),
                    combinedRisks(zone, products, weather, history, now));
        }
        boolean organicStageGate =
                FertilizerSafetyPolicy.requiresOrganicProduct(profile);
        List<Candidate> candidates = new ArrayList<>();
        for (FertilizerProduct product : products) {
            if (FertilizerSafetyPolicy.isEligible(product, profile)) {
                candidates.add(score(
                        product,
                        profile,
                        remainingDays(profile, product, now),
                        FertilizerPerformanceAdvisor.evaluate(
                                zone, product, history, now
                        ),
                        preferOrganicInputs,
                        units
                ));
            }
        }
        candidates.sort(Comparator.comparingInt(Candidate::score).reversed());

        List<Candidate> stageSuitable = new ArrayList<>();
        List<Candidate> ready = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (candidate.waitDays <= 0L) {
                stageSuitable.add(candidate);
                if (candidate.isReady()) ready.add(candidate);
            }
        }

        List<Candidate> displayed = ready.isEmpty() ? stageSuitable : ready;
        Candidate primaryCandidate = displayed.isEmpty()
                ? (candidates.isEmpty() ? null : candidates.get(0))
                : displayed.get(0);
        FertilizerAdvice.Experience experience = primaryExperience(displayed);
        FertilizerAdvice.Recommendation recommendation =
                primaryRecommendation(primaryCandidate);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < Math.min(3, displayed.size()); i++) {
            result.add(displayed.get(i).display());
        }

        if (candidates.isEmpty() && organicStageGate
                && (products == null || products.isEmpty()
                || hasStageCompatibleConventionalProduct(products, profile))) {
            String period = FertilizerStagePolicy.HARVEST.equals(normalizedStage)
                    ? "Aktif hasat" : "Meyve oluşumu";
            return new FertilizerAdvice(title, FertilizerAdvice.STATUS_ORGANIC_REQUIRED,
                    period + " döneminde kimyasal içerikli ürünler öneri dışında bırakıldı. "
                            + "Bu döneme uygun ve etiketi organik tarımda kullanıma izin veren bir ürün ekleyin.",
                    context, new ArrayList<>(),
                    combinedRisks(zone, products, weather, history, now));
        }
        if (stageSuitable.isEmpty()) {
            long nearest = nearestWait(candidates);
            String reason = nearest == Long.MAX_VALUE
                    ? "Bu dönem için etkin ve uygun ürün bulunamadı."
                    : "Bu uygulama türü için güvenli bekleme aralığı sürüyor. "
                    + "En yakın tekrar uygulama " + nearest
                    + " gün sonra değerlendirilebilir.";
            return new FertilizerAdvice(title, FertilizerAdvice.STATUS_TOO_EARLY, reason, context,
                    new ArrayList<>(),
                    combinedRisks(zone, products, weather, history, now),
                    primaryExperience(candidates), recommendation);
        }
        if (ready.isEmpty()) {
            return new FertilizerAdvice(title, FertilizerAdvice.STATUS_PREPARATION_REQUIRED,
                    preparationReason(stageSuitable.get(0)), context, result,
                    combinedRisks(zone, products, weather, history, now),
                    experience, recommendation);
        }
        if (FertilizerDataFreshnessPolicy.requiresLiveSensor(zone)
                && !FertilizerDataFreshnessPolicy.isSensorFresh(zone, now)) {
            return new FertilizerAdvice(title, FertilizerAdvice.STATUS_REFRESH_DATA,
                    "Güncel toprak nemi alınamadığı için bugün uygulama kararı verilmedi. "
                            + "Sensör verisi yenilendiğinde öneriyi tekrar değerlendirin.",
                    context, result,
                    combinedRisks(zone, products, weather, history, now),
                    experience, recommendation);
        }
        if (FertilizerDataFreshnessPolicy.isSensorFresh(zone, now)
                && zone.getMoisture() < zone.getMoisture_limit()) {
            return new FertilizerAdvice(title, FertilizerAdvice.STATUS_WATERING_FIRST,
                    "Toprak nemi uygulama sınırının altında. Önce güvenli bir sulama yapın; "
                            + "kök bölgesi dengelendikten sonra gübreleme planını yeniden değerlendirin.",
                    context, result,
                    combinedRisks(zone, products, weather, history, now),
                    experience, recommendation);
        }

        String reason;
        if (organicStageGate) {
            reason = FertilizerStagePolicy.HARVEST.equals(normalizedStage)
                    ? "Aktif hasatta yalnız organik tarımda kullanıma uygun ve hasat dönemiyle uyumlu ürünler gösteriliyor."
                    : "Meyve oluşumu başladı. Yalnız organik tarımda kullanıma uygun ve dönemle uyumlu ürünler gösteriliyor.";
        } else {
            reason = FertilizerStagePolicy.HARVEST.equals(normalizedStage)
                    ? "Hasat devam ediyor. Yalnız etiketi aktif hasat dönemini destekleyen ve güvenlik koşulları uygun ürünler gösteriliyor."
                    : "Bugün uygulama planı değerlendirilebilir.";
        }
        if (FertilizerDataFreshnessPolicy.isWeatherFresh(weather, now)
                && weather.getTomorrowTemperatureMax() != null
                && weather.getTomorrowTemperatureMax() >= 35.0) {
            reason += " Sıcaklık yüksek; serin saatleri tercih edin.";
        }
        return new FertilizerAdvice(title, FertilizerAdvice.STATUS_TODAY_ADVICE, reason, context, result,
                combinedRisks(zone, products, weather, history, now),
                experience, recommendation);
    }

    private static boolean hasStageCompatibleConventionalProduct(
            List<FertilizerProduct> products,
            FertilizationProfile profile
    ) {
        if (products == null) return false;
        for (FertilizerProduct product : products) {
            if (product != null && product.isEnabled()
                    && FertilizerApplicationSafety.isStageCompatible(product, profile)
                    && !FertilizerSafetyPolicy.isOrganicCompatible(product)) {
                return true;
            }
        }
        return false;
    }

    private static FertilizerAdvice.Recommendation primaryRecommendation(
            Candidate candidate
    ) {
        if (candidate == null) return FertilizerAdvice.Recommendation.none();
        return new FertilizerAdvice.Recommendation(
                candidate.productId,
                candidate.name,
                candidate.applicationType,
                candidate.role,
                candidate.waitDays,
                candidate.isReady()
        );
    }

    private static FertilizerAdvice.Experience primaryExperience(
            List<Candidate> candidates
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return FertilizerAdvice.Experience.none();
        }
        Candidate candidate = candidates.get(0);
        FertilizerPerformanceAdvisor.Result performance = candidate.performance;
        return new FertilizerAdvice.Experience(
                candidate.productId,
                candidate.name,
                performance.getObservations(),
                performance.getMinimumObservations(),
                performance.hasEvidence(),
                performance.getSuccessScore()
        );
    }

    private static List<String> combinedRisks(
            GardenZone zone,
            List<FertilizerProduct> products,
            WeatherForecast weather,
            List<FertilizerApplication> history,
            long now
    ) {
        List<String> risks = new ArrayList<>(
                FertilizerHistoryRiskAdvisor.assess(
                        zone, products, history, now
                )
        );
        for (String risk : FertilizerRiskAdvisor.assess(
                zone, products, weather, now
        )) {
            if (!risks.contains(risk)) risks.add(risk);
        }
        return risks;
    }

    private static Candidate score(
            FertilizerProduct product,
            FertilizationProfile profile,
            long waitDays,
            FertilizerPerformanceAdvisor.Result performance,
            boolean preferOrganicInputs,
            DisplayUnitFormatter units
    ) {
        String text = (safe(product.getName(), "") + " " + safe(product.getNpk(), "")).toLowerCase(Locale.ROOT);
        FertilizerNutrientProfile nutrients = FertilizerNutrientProfile.from(product);
        String stage = FertilizerStagePolicy.normalize(profile.getGrowth_stage());
        boolean traceElements = hasFunctionalTag(product, "TRACE_ELEMENTS");
        boolean organicMatter = hasFunctionalTag(product, "ORGANIC_MATTER");
        boolean humicFulvic = hasFunctionalTag(product, "HUMIC_FULVIC");
        boolean seaweed = hasFunctionalTag(product, "SEAWEED");
        boolean calciumMagnesium = hasFunctionalTag(product, "CALCIUM_MAGNESIUM");
        boolean aminoAcids = hasFunctionalTag(product, "AMINO_ACIDS");
        boolean soilSupport = organicMatter || humicFulvic;
        boolean biostimulant = seaweed || aminoAcids;

        int score = 45;
        String role = "Destek ürünü";
        if (FertilizerStagePolicy.SOIL_PREPARATION.equals(stage)) {
            if (soilSupport) {
                score = 95; role = "Toprak hazırlığı ve organik madde desteği";
            } else if (nutrients.isBalanced()) {
                score = 68; role = "Toprak analizine göre taban beslemesi";
            }
        } else if ("ROOTING".equals(stage)) {
            if (soilSupport) { score = 92; role = "Kök / toprak desteği"; }
            else if (biostimulant) { score = 80; role = "Köklenme stresine karşı biyostimülant destek"; }
            else if (nutrients.isBalanced()) { score = 72; role = "Dengeli başlangıç desteği"; }
        } else if ("VEGETATIVE".equals(stage)) {
            if (nutrients.isBalanced()) { score = 92; role = "Dengeli ana besleme"; }
            else if (traceElements) { score = 78; role = "Çoklu iz element desteği; ana NPK yerine geçmez"; }
            else if (soilSupport) { score = 74; role = "Kök ve toprak desteği"; }
            else if (biostimulant) { score = 70; role = "Vejetatif gelişim için biyostimülant destek"; }
        } else if ("FLOWERING".equals(stage)) {
            if (calciumMagnesium) { score = 86; role = "Kalsiyum / magnezyum desteği"; }
            else if (nutrients.isPotassiumForward()) { score = 82; role = "Potasyum ağırlıklı destek"; }
            else if (biostimulant) { score = 76; role = "Çiçeklenme stresine karşı biyostimülant destek"; }
            else if (traceElements) { score = 68; role = "Çoklu iz element desteği; ana NPK yerine geçmez"; }
        } else if ("FRUITING".equals(stage)) {
            if (nutrients.isPotassiumForward()) { score = 95; role = "Ana potasyum beslemesi"; }
            else if (calciumMagnesium) { score = 84; role = "Kalsiyum / magnezyum desteği"; }
            else if (biostimulant) { score = 72; role = "Meyve gelişimi için biyostimülant destek"; }
            else if (traceElements) { score = 60; role = "Çoklu iz element desteği; ana NPK yerine geçmez"; }
            else if (soilSupport) { score = 54; role = "İsteğe bağlı toprak desteği; ana besleme değildir"; }
        } else if (FertilizerStagePolicy.HARVEST.equals(stage)) {
            if (biostimulant) {
                score = 86; role = "Hasat ve toplama stresinde biyostimülant desteği";
            } else if (nutrients.isPotassiumForward()) {
                score = 82; role = "Hasat sürecinde potasyum desteği; etiket kısıtlarını doğrulayın";
            } else if (calciumMagnesium) {
                score = 74; role = "Kalsiyum / magnezyum desteği; nem düzenini doğrulayın";
            } else if (soilSupport) {
                score = 62; role = "İsteğe bağlı toprak desteği; ana besleme değildir";
            } else if (traceElements) {
                score = 55; role = "Çoklu iz element desteği; yalnız doğrulanmış ihtiyaçta kullanın";
            } else if (nutrients.isBalanced() || matches(text, "20-20-20", "20.20.20")) {
                score = 35; role = "Azot yükünü artırmadan önce analiz edin";
            } else {
                score = 45; role = "Etiket ve hasat aralığı doğrulanırsa koşullu destek";
            }
        }

        // Legacy products may not yet have structured tags. Name matching is
        // kept only as a compatibility fallback until the product is edited.
        if ("Destek ürünü".equals(role)) {
            if (matches(text, "humik", "fulvik", "leonardit", "super root", "solucan")) {
                score = "ROOTING".equals(stage) ? 92 : 62;
                role = "Kök / toprak desteği";
            } else if (matches(text, "calsimagsi", "kalsiyum", "magsul", "magnezyum")) {
                score = "FLOWERING".equals(stage) ? 86 : 74;
                role = "Kalsiyum / magnezyum desteği";
            } else if (matches(text, "deniz yosunu", "searius", "amino asit")) {
                score = FertilizerStagePolicy.HARVEST.equals(stage) ? 86 : 72;
                role = "Biyostimülant desteği";
            } else if (matches(text, "fertisol", "15-0-5", "15.0.5")) {
                score = "VEGETATIVE".equals(stage) ? 78 : 58;
                role = "Mikro element desteği";
            }
        }
        boolean organicCompatible = FertilizerSafetyPolicy.isOrganicCompatible(product);
        if (preferOrganicInputs && organicCompatible) score += 12;
        if (organicCompatible) role = "Organik tar\u0131ma uygun \u00b7 " + role;
        FertilizerApplicationSafety.Dose requiredDose =
                FertilizerApplicationSafety.calculateDose(product, profile);
        StockReadiness readiness = stockReadiness(product, requiredDose);
        String stock = stockSummary(product, profile);
        if (readiness == StockReadiness.INSUFFICIENT) score -= 55;
        else if (readiness == StockReadiness.STOCK_UNKNOWN
                || readiness == StockReadiness.UNIT_MISMATCH) score -= 25;
        else if (readiness == StockReadiness.DOSE_UNKNOWN) score -= 10;
        score += performance.getRankingAdjustment();
        return new Candidate(product.getProduct_id(), product.getName(),
                applicationType(product),
                Math.max(0, Math.min(100, score)), role, stock,
                doseSummary(product), zoneDoseSummary(product, profile, units), waitDays,
                readiness, performance);
    }

    private static String buildContext(GardenZone zone, FertilizationProfile profile,
                                       WeatherForecast weather, long now,
                                       DisplayUnitFormatter units) {
        List<String> parts = new ArrayList<>();
        long age = plantAge(profile.getPlanting_date(), now);
        if (age >= 0) parts.add(age + " günlük");
        parts.add(stageLabel(profile.getGrowth_stage()));
        if (FertilizerDataFreshnessPolicy.isSensorFresh(zone, now)) {
            parts.add("nem %" + zone.getMoisture());
        }
        if (profile.getLast_application_at_epoch() > 0) {
            long days = Math.max(0, (now - profile.getLast_application_at_epoch()) / 86400L);
            parts.add("son uygulama " + days + " gün önce");
        }
        if (FertilizerDataFreshnessPolicy.isWeatherFresh(weather, now)
                && weather.getTomorrowTemperatureMax() != null) parts.add(
                        "yarın " + units.formatTemperature(
                                weather.getTomorrowTemperatureMax()));
        return String.join(" · ", parts);
    }

    private static long plantAge(String value, long now) {
        try { return ChronoUnit.DAYS.between(LocalDate.parse(value, java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")), dateAt(now)) + 1; }
        catch (Exception ignored) { return -1; }
    }
    private static long remainingDays(FertilizationProfile profile,
                                      FertilizerProduct product, long now) {
        return daysUntil(FertilizerApplicationSafety.nextApplicationAt(
                profile, applicationType(product)), now);
    }
    private static long daysUntil(long epoch, long now) {
        if (epoch <= now) return 0L;
        return Math.max(1L, ChronoUnit.DAYS.between(dateAt(now),
                java.time.Instant.ofEpochSecond(epoch)
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDate()));
    }
    private static LocalDate dateAt(long epoch) {
        return java.time.Instant.ofEpochSecond(epoch)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
    }
    private static long nearestWait(List<Candidate> candidates) {
        long nearest = Long.MAX_VALUE;
        for (Candidate candidate : candidates) {
            if (candidate.waitDays > 0) nearest = Math.min(nearest, candidate.waitDays);
        }
        return nearest;
    }
    private static String applicationType(FertilizerProduct product) {
        String type = safe(product.getApplication_type(), "NUTRITION").trim();
        return type.isEmpty() ? "NUTRITION" : type.toUpperCase(Locale.ROOT);
    }
    private static String doseSummary(FertilizerProduct product) {
        String unit = safe(product.getDosage_unit(), "etikete göre");
        double min = product.getLabel_dosage_min() > 0
                ? product.getLabel_dosage_min() : product.getLabel_dosage();
        double max = product.getLabel_dosage_max();
        if (min <= 0) return "Etiket dozu: kontrol edin";
        String amount = max > min ? trim(min) + "–" + trim(max) : trim(min);
        return "Etiket dozu: " + amount + " " + unit;
    }
    private static String stockSummary(FertilizerProduct product,
                                       FertilizationProfile profile) {
        String stockUnit = safe(product.getStock_unit(), "");
        FertilizerApplicationSafety.Dose need =
                FertilizerApplicationSafety.calculateDose(product, profile);
        if (stockUnit.isBlank()) return "Bilgi girilmedi";
        if (product.getStock_amount() <= 0) {
            return !need.isSupported() ? "Stok yok" : "0 " + stockUnit + " · "
                    + trim(need.getMaxAmount()) + " " + need.getUnit() + " eksik";
        }
        if (!need.isSupported() || !stockUnit.equalsIgnoreCase(need.getUnit())) {
            return product.getLow_stock_threshold() > 0
                    && product.getStock_amount() <= product.getLow_stock_threshold()
                    ? "Düşük stok: " + trim(product.getStock_amount()) + " " + stockUnit
                    : trim(product.getStock_amount()) + " " + stockUnit;
        }
        if (product.getStock_amount() < need.getMaxAmount()) {
            return trim(product.getStock_amount()) + " " + stockUnit + " · "
                    + trim(need.getMaxAmount() - product.getStock_amount()) + " "
                    + need.getUnit() + " eksik";
        }
        int applications = (int) Math.floor(product.getStock_amount() / need.getMaxAmount());
        return trim(product.getStock_amount()) + " " + stockUnit
                + " · yaklaşık " + Math.max(1, applications) + " uygulama yeter";
    }
    private static StockReadiness stockReadiness(FertilizerProduct product,
                                                  FertilizerApplicationSafety.Dose requiredDose) {
        if (requiredDose == null || !requiredDose.isSupported()) {
            return StockReadiness.DOSE_UNKNOWN;
        }
        String stockUnit = safe(product.getStock_unit(), "");
        if (stockUnit.isBlank()) return StockReadiness.STOCK_UNKNOWN;
        if (!stockUnit.equalsIgnoreCase(requiredDose.getUnit())) {
            return StockReadiness.UNIT_MISMATCH;
        }
        return product.getStock_amount() + 0.000001 >= requiredDose.getMaxAmount()
                ? StockReadiness.READY
                : StockReadiness.INSUFFICIENT;
    }

    private static String preparationReason(Candidate candidate) {
        switch (candidate.readiness) {
            case DOSE_UNKNOWN:
                return "Seçilen ürünün etiketteki dozunu ve doz birimini tamamlayın.";
            case STOCK_UNKNOWN:
                return "Seçilen ürünün stok miktarı ve stok birimi girilmeden güvenli uygulama önerilmez.";
            case UNIT_MISMATCH:
                return "Seçilen ürünün stok birimi hesaplanan uygulama birimiyle uyuşmuyor.";
            case INSUFFICIENT:
                return "Seçilen ürünün stoku bu bölge için hesaplanan doza yetmiyor.";
            default:
                return "Uygulama bilgilerini kontrol edin.";
        }
    }

    private static String zoneDoseSummary(FertilizerProduct product,
                                          FertilizationProfile profile,
                                          DisplayUnitFormatter units) {
        FertilizerApplicationSafety.Dose dose =
                FertilizerApplicationSafety.calculateDose(product, profile);
        if (!dose.isSupported()) return "";
        String amount = displayDoseRange(dose.getMinAmount(),
                dose.getMaxAmount(), dose.getUnit(), units);
        String sourceUnit = safe(product.getDosage_unit(), "");
        String method = applicationNote(sourceUnit);
        String scope = dose.isTankBased()
                ? units.formatVolume(profile.getTank_liters()) + " tank için"
                : units.formatArea(profile.getArea_m2()) + " için";
        String areaOrTank = (dose.isTankBased() ? "Tank dozu: " : "Alan dozu: ")
                + amount + " · " + scope;
        String tankMix = tankMixSummary(product, profile, sourceUnit, units);
        if (!tankMix.isBlank()) return areaOrTank + "\n" + tankMix;
        return areaOrTank + (method.isBlank() ? "" : " · " + method);
    }
    private static String tankMixSummary(FertilizerProduct product,
                                         FertilizationProfile profile,
                                         String sourceUnit,
                                         DisplayUnitFormatter units) {
        if (profile.getTank_liters() <= 0 || !sourceUnit.toLowerCase(Locale.ROOT)
                .replace(" ", "").contains("tonsu")) return "";
        double min = product.getLabel_dosage_min() > 0
                ? product.getLabel_dosage_min() : product.getLabel_dosage();
        double max = product.getLabel_dosage_max() > 0
                ? product.getLabel_dosage_max() : min;
        if (min <= 0) return "";
        String normalized = sourceUnit.toLowerCase(Locale.ROOT).replace(" ", "");
        double factor;
        String unit;
        if (normalized.contains("kg/dekar")) {
            // kg/dekar with one ton water equals grams per litre.
            factor = 1.0;
            unit = "g";
        } else if (normalized.contains("l/dekar")) {
            // L/dekar with one ton water equals millilitres per litre.
            factor = 1.0;
            unit = "ml";
        } else {
            return "";
        }
        double tankMin = min * profile.getTank_liters() * factor;
        double tankMax = max * profile.getTank_liters() * factor;
        String amount = displayDoseRange(tankMin, tankMax, unit, units);
        String applicationVolume = profile.getArea_m2() > 0
                ? " · bu bölge için yaklaşık "
                + units.formatVolume(profile.getArea_m2()) + " çözelti uygulayın"
                : "";
        return "Tank karışımı: " + amount + " · "
                + units.formatVolume(profile.getTank_liters())
                + " tank için" + applicationVolume;
    }
    private static String displayDoseRange(double minimum, double maximum,
                                           String unit,
                                           DisplayUnitFormatter units) {
        if ("g".equalsIgnoreCase(unit)) {
            return maximum > minimum
                    ? units.formatWeight(minimum) + "–" + units.formatWeight(maximum)
                    : units.formatWeight(minimum);
        }
        if ("ml".equalsIgnoreCase(unit)) {
            return maximum > minimum
                    ? units.formatVolume(minimum / 1000d) + "–"
                    + units.formatVolume(maximum / 1000d)
                    : units.formatVolume(minimum / 1000d);
        }
        String amount = maximum > minimum
                ? trim(minimum) + "–" + trim(maximum) : trim(minimum);
        return amount + " " + unit;
    }
    private static String applicationNote(String unit) {
        int separator = unit.indexOf('·');
        return separator < 0 ? "" : unit.substring(separator + 1).trim();
    }
    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }
    private static boolean hasFunctionalTag(FertilizerProduct product, String expected) {
        if (product.getFunctional_tags() == null) return false;
        for (String tag : product.getFunctional_tags()) {
            if (expected.equalsIgnoreCase(safe(tag, ""))) return true;
        }
        return false;
    }
    private static boolean matches(String value, String... keys) { for (String key : keys) if (value.contains(key)) return true; return false; }
    private static String stageLabel(String stage) {
        String normalized = FertilizerStagePolicy.normalize(stage);
        if (FertilizerStagePolicy.SEASON_END.equals(normalized)) return "Sezon sonu";
        if (FertilizerStagePolicy.HARVEST.equals(normalized)) return "Aktif hasat";
        if ("FRUITING".equals(normalized)) return "Meyve dönemi";
        if ("FLOWERING".equals(normalized)) return "Çiçeklenme";
        if ("VEGETATIVE".equals(normalized)) return "Vejetatif dönem";
        if ("ROOTING".equals(normalized)) return "Kök gelişimi";
        return "Gelişim dönemi";
    }
    private static String safe(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private enum StockReadiness {
        READY,
        DOSE_UNKNOWN,
        STOCK_UNKNOWN,
        UNIT_MISMATCH,
        INSUFFICIENT
    }

    private static class Candidate {
        final String productId, name, applicationType, role, stock, dose, zoneDose;
        final int score;
        final long waitDays;
        final StockReadiness readiness;
        final FertilizerPerformanceAdvisor.Result performance;

        Candidate(String productId, String name, String applicationType,
                  int score, String role, String stock, String dose,
                  String zoneDose, long waitDays, StockReadiness readiness,
                  FertilizerPerformanceAdvisor.Result performance) {
            this.productId = productId == null ? "" : productId;
            this.name = name;
            this.applicationType = applicationType == null ? "NUTRITION" : applicationType;
            this.score = score;
            this.role = role;
            this.stock = stock;
            this.dose = dose;
            this.zoneDose = zoneDose;
            this.waitDays = waitDays;
            this.readiness = readiness;
            this.performance = performance;
        }

        int score() {
            return score;
        }

        boolean isReady() {
            return waitDays <= 0L && readiness == StockReadiness.READY;
        }

        String display() {
            return stars(score) + " " + name + "\n" + role + "\n" + dose
                    + (zoneDose.isBlank() ? "" : "\n" + zoneDose)
                    + "\nStok: " + stock;
        }

        private static String stars(int value) {
            int count = Math.max(1, Math.min(5, Math.round(value / 20f)));
            return "★★★★★".substring(0, count)
                    + "☆☆☆☆☆".substring(0, 5 - count);
        }
    }
}
