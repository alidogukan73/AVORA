package com.alidogukan.avora.seedling;

import androidx.annotation.StringRes;

import com.alidogukan.avora.R;
import com.alidogukan.avora.models.CropCatalogItem;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Crop-specific sowing-medium and tray guidance shown before a batch is created. */
public final class SeedlingPlantingGuide {
    private static final Guide DEFAULT = guide(
            R.string.seedling_guide_soil_default,
            R.string.seedling_guide_tray_default, true);
    private static final Map<String, Guide> GUIDES = guides();

    private SeedlingPlantingGuide() { }

    public static Guide forCrop(CropCatalogItem crop) {
        if (crop == null) return DEFAULT;
        String key = SeedlingCropCatalog.cropKeyForPlant(crop.getPlant_type());
        Guide value = GUIDES.get(key);
        if (value != null) return value;
        return GUIDES.getOrDefault(
                SeedlingCropCatalog.cropKeyForPlant(crop.getName()), DEFAULT);
    }

    private static Map<String, Guide> guides() {
        Map<String, Guide> values = new LinkedHashMap<>();
        values.put("tomato", guide(R.string.seedling_guide_soil_tomato,
                R.string.seedling_guide_tray_tomato, true));
        values.put("pepper", guide(R.string.seedling_guide_soil_pepper,
                R.string.seedling_guide_tray_pepper, true));
        values.put("cucumber", guide(R.string.seedling_guide_soil_cucumber,
                R.string.seedling_guide_tray_cucumber, true));
        values.put("eggplant", guide(R.string.seedling_guide_soil_eggplant,
                R.string.seedling_guide_tray_eggplant, true));
        values.put("bean", guide(R.string.seedling_guide_soil_bean,
                R.string.seedling_guide_tray_bean, false));
        values.put("carrot", guide(R.string.seedling_guide_soil_carrot,
                R.string.seedling_guide_tray_carrot, false));
        values.put("okra", guide(R.string.seedling_guide_soil_okra,
                R.string.seedling_guide_tray_okra, true));
        values.put("zucchini", guide(R.string.seedling_guide_soil_zucchini,
                R.string.seedling_guide_tray_zucchini, true));
        values.put("lettuce", guide(R.string.seedling_guide_soil_lettuce,
                R.string.seedling_guide_tray_lettuce, true));
        values.put("onion", guide(R.string.seedling_guide_soil_onion,
                R.string.seedling_guide_tray_onion, true));
        values.put("potato", guide(R.string.seedling_guide_soil_potato,
                R.string.seedling_guide_tray_potato, false));
        values.put("corn", guide(R.string.seedling_guide_soil_corn,
                R.string.seedling_guide_tray_corn, false));
        values.put("pea", guide(R.string.seedling_guide_soil_pea,
                R.string.seedling_guide_tray_pea, false));
        values.put("strawberry", guide(R.string.seedling_guide_soil_strawberry,
                R.string.seedling_guide_tray_strawberry, false));
        values.put("watermelon", guide(R.string.seedling_guide_soil_watermelon,
                R.string.seedling_guide_tray_watermelon, true));
        values.put("melon", guide(R.string.seedling_guide_soil_melon,
                R.string.seedling_guide_tray_melon, true));
        return Collections.unmodifiableMap(values);
    }

    private static Guide guide(@StringRes int soilText, @StringRes int trayText,
                               boolean trayCountRequired) {
        return new Guide(soilText, trayText, trayCountRequired);
    }

    public static final class Guide {
        @StringRes private final int soilText;
        @StringRes private final int trayText;
        private final boolean trayCountRequired;

        private Guide(@StringRes int soilText, @StringRes int trayText,
                      boolean trayCountRequired) {
            this.soilText = soilText;
            this.trayText = trayText;
            this.trayCountRequired = trayCountRequired;
        }

        @StringRes public int getSoilText() { return soilText; }
        @StringRes public int getTrayText() { return trayText; }
        public boolean isTrayCountRequired() { return trayCountRequired; }
    }
}
