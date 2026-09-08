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
            7, 21, "Standart", "Yerel");
    private static final Map<String, Profile> PROFILES = profiles();

    private SeedlingCropCatalog() { }

    public static Profile profileFor(CropCatalogItem crop) {
        if (crop == null) return DEFAULT;
        String type = safe(crop.getPlant_type()).toLowerCase(Locale.ROOT);
        return PROFILES.getOrDefault(type, DEFAULT);
    }

    private static Map<String, Profile> profiles() {
        Map<String, Profile> values = new LinkedHashMap<>();
        values.put("tomato", profile(6, 17,
                "H2274", "Rio Grande", "Marmande", "Cherry"));
        values.put("pepper", profile(8, 24,
                "Kapya", "Çarliston", "Sivri", "Dolmalık"));
        values.put("cucumber", profile(4, 12,
                "Beith Alpha", "Çengelköy", "Kornişon"));
        values.put("eggplant", profile(8, 24,
                "Kemer", "Aydın Siyahı", "Topan"));
        values.put("bean", profile(5, 12,
                "Ayşe Kadın", "Barbunya", "Boncuk"));
        values.put("carrot", profile(10, 28,
                "Nantes", "Chantenay", "Yerli"));
        values.put("okra", profile(7, 21,
                "Sultani", "Bornova", "Akköy"));
        values.put("zucchini", profile(5, 14,
                "Sakız", "Girit", "Dolmalık"));
        values.put("lettuce", profile(5, 16,
                "Yedikule", "Iceberg", "Kıvırcık"));
        values.put("onion", profile(8, 30,
                "Akgün-12", "Kantartopu-3", "Valenciana"));
        values.put("potato", profile(14, 28,
                "Agria", "Marabel", "Melody"));
        values.put("corn", profile(5, 14,
                "Şeker Mısırı", "Cin Mısırı", "At Dişi"));
        values.put("pea", profile(6, 14,
                "Utrillo", "Progress No. 9", "Kelvedon Wonder"));
        values.put("strawberry", profile(14, 45,
                "Albion", "Monterey", "San Andreas"));
        values.put("watermelon", profile(5, 16,
                "Crimson Sweet", "Sugar Baby", "Charleston Gray"));
        values.put("melon", profile(5, 16,
                "Kırkağaç", "Ananas", "Hasanbey"));
        return Collections.unmodifiableMap(values);
    }

    private static Profile profile(int emergenceDays, int transplantDays,
                                   String... varieties) {
        return new Profile(Arrays.asList(varieties), emergenceDays, transplantDays);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Profile {
        private final List<String> varieties;
        private final int emergenceDays;
        private final int transplantDays;

        private Profile(List<String> varieties, int emergenceDays, int transplantDays) {
            this.varieties = Collections.unmodifiableList(varieties);
            this.emergenceDays = emergenceDays;
            this.transplantDays = transplantDays;
        }

        public List<String> getVarieties() { return varieties; }
        public int getEmergenceDays() { return emergenceDays; }
        public int getTransplantDays() { return transplantDays; }
    }
}
