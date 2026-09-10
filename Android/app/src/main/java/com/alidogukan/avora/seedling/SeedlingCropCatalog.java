package com.alidogukan.avora.seedling;

import com.alidogukan.avora.models.CropCatalogItem;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Seedling-only crop metadata. Crop identity still comes from the shared
 * {@link com.alidogukan.avora.crop.CropCatalog}; this class only adds cultivar
 * choices and estimated nursery milestones.
 */
public final class SeedlingCropCatalog {
    private static final Profile DEFAULT = profile(
            7, 6, 5, 3, 21, "Standart", "Yerel");
    private static final Map<String, Profile> PROFILES = profiles();
    private static final Locale TURKISH = Locale.forLanguageTag("tr-TR");

    private SeedlingCropCatalog() { }

    public static Profile profileFor(CropCatalogItem crop) {
        if (crop == null) return DEFAULT;
        Profile byType = PROFILES.get(cropKeyForPlant(crop.getPlant_type()));
        return byType == null ? profileForPlant(crop.getName()) : byType;
    }

    /** Resolves both Firebase's display names and the catalog's stable crop types. */
    public static Profile profileForPlant(String plantTypeOrName) {
        return PROFILES.getOrDefault(cropKeyForPlant(plantTypeOrName), DEFAULT);
    }

    private static Map<String, Profile> profiles() {
        Map<String, Profile> values = new LinkedHashMap<>();
        values.put("tomato", profile(6, 6, 3, 2, 25,
                "H2274", "Rio Grande", "Marmande", "Cherry"));
        values.put("pepper", profile(8, 7, 5, 4, 25,
                "Kapya", "Çarliston", "Sivri", "Dolmalık"));
        values.put("cucumber", profile(4, 4, 2, 2, 13,
                "Beith Alpha", "Çengelköy", "Kornişon"));
        values.put("eggplant", profile(8, 7, 5, 4, 25,
                "Kemer", "Aydın Siyahı", "Topan"));
        values.put("bean", profile(5, 4, 2, 1, 9,
                "Ayşe Kadın", "Barbunya", "Boncuk"));
        values.put("carrot", profile(10, 7, 6, 5, 14,
                "Nantes", "Chantenay", "Yerli"));
        values.put("okra", profile(7, 6, 5, 3, 21,
                "Sultani", "Bornova", "Akköy"));
        values.put("zucchini", profile(5, 4, 3, 2, 11,
                "Sakız", "Girit", "Dolmalık"));
        values.put("lettuce", profile(5, 5, 3, 3, 19,
                "Yedikule", "Iceberg", "Kıvırcık"));
        values.put("onion", profile(8, 7, 8, 7, 33,
                "Akgün-12", "Kantartopu-3", "Valenciana"));
        values.put("potato", profile(14, 5, 5, 4, 7,
                "Agria", "Marabel", "Melody"));
        values.put("corn", profile(5, 4, 3, 2, 7,
                "Şeker Mısırı", "Cin Mısırı", "At Dişi"));
        values.put("pea", profile(6, 4, 2, 2, 14,
                "Utrillo", "Progress No. 9", "Kelvedon Wonder"));
        values.put("strawberry", profile(14, 10, 11, 10, 11,
                "Albion", "Monterey", "San Andreas"));
        values.put("watermelon", profile(5, 4, 4, 3, 9,
                "Crimson Sweet", "Sugar Baby", "Charleston Gray"));
        values.put("melon", profile(5, 4, 4, 3, 9,
                "Kırkağaç", "Ananas", "Hasanbey"));
        return Collections.unmodifiableMap(values);
    }

    private static Profile profile(int emergenceDays, int firstLeafAfterEmergenceDays,
                                   int trueLeavesAfterFirstLeafDays,
                                   int hardeningAfterTrueLeavesDays,
                                   int readyAfterHardeningDays,
                                   String... varieties) {
        return new Profile(Arrays.asList(varieties), emergenceDays,
                firstLeafAfterEmergenceDays, trueLeavesAfterFirstLeafDays,
                hardeningAfterTrueLeavesDays, readyAfterHardeningDays);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    /** Stable key shared by timeline and environment target catalogs. */
    public static String cropKeyForPlant(String value) {
        String normalized = safe(value).toLowerCase(TURKISH);
        switch (normalized) {
            case "domates": return "tomato";
            case "biber": return "pepper";
            case "salatalık": return "cucumber";
            case "patlıcan": return "eggplant";
            case "fasulye": return "bean";
            case "havuç": return "carrot";
            case "bamya": return "okra";
            case "kabak": return "zucchini";
            case "marul": return "lettuce";
            case "soğan": return "onion";
            case "patates": return "potato";
            case "mısır": return "corn";
            case "bezelye": return "pea";
            case "çilek": return "strawberry";
            case "karpuz": return "watermelon";
            case "kavun": return "melon";
            default: return normalized.toLowerCase(Locale.ROOT);
        }
    }

    public static final class Profile {
        // Representative estimates within Extension guidance; real conditions vary.
        private final List<String> varieties;
        private final int emergenceDays;
        private final int firstLeafAfterEmergenceDays;
        private final int trueLeavesAfterFirstLeafDays;
        private final int hardeningAfterTrueLeavesDays;
        private final int readyAfterHardeningDays;

        private Profile(List<String> varieties, int emergenceDays,
                        int firstLeafAfterEmergenceDays,
                        int trueLeavesAfterFirstLeafDays,
                        int hardeningAfterTrueLeavesDays,
                        int readyAfterHardeningDays) {
            this.varieties = Collections.unmodifiableList(varieties);
            this.emergenceDays = emergenceDays;
            this.firstLeafAfterEmergenceDays = firstLeafAfterEmergenceDays;
            this.trueLeavesAfterFirstLeafDays = trueLeavesAfterFirstLeafDays;
            this.hardeningAfterTrueLeavesDays = hardeningAfterTrueLeavesDays;
            this.readyAfterHardeningDays = readyAfterHardeningDays;
        }

        public List<String> getVarieties() { return varieties; }
        public int getEmergenceDays() { return emergenceDays; }
        public int getFirstLeafAfterEmergenceDays() {
            return firstLeafAfterEmergenceDays;
        }
        public int getTrueLeavesAfterFirstLeafDays() {
            return trueLeavesAfterFirstLeafDays;
        }
        public int getHardeningAfterTrueLeavesDays() {
            return hardeningAfterTrueLeavesDays;
        }
        public int getReadyAfterHardeningDays() { return readyAfterHardeningDays; }
        public int getTransplantDays() {
            return emergenceDays + firstLeafAfterEmergenceDays
                    + trueLeavesAfterFirstLeafDays + hardeningAfterTrueLeavesDays;
        }
        public int getReadyDays() {
            return getTransplantDays() + readyAfterHardeningDays;
        }
    }
}
