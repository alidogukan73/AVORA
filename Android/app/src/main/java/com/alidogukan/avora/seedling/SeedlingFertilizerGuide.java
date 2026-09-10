package com.alidogukan.avora.seedling;

import androidx.annotation.StringRes;

import com.alidogukan.avora.R;

import java.util.Locale;

/** Safe, crop- and nursery-stage-aware fertilizer guidance for seedling batches. */
public final class SeedlingFertilizerGuide {
    private enum Program {
        TRANSPLANT_HIGH,
        TRANSPLANT_LOW,
        LEAFY,
        ONION,
        LEGUME,
        ROOT,
        POTATO,
        CORN,
        STRAWBERRY,
        MODERATE,
        DEFAULT
    }

    private SeedlingFertilizerGuide() { }

    public static Recommendation forPlant(String plantType, String stage) {
        String key = cropKey(plantType);
        Program program = programFor(key);
        String normalizedStage = SeedlingStagePolicy.normalize(stage);
        return new Recommendation(
                fertilizerFor(program, normalizedStage),
                applicationFor(program, normalizedStage),
                cropNoteFor(key, program));
    }

    @StringRes
    private static int fertilizerFor(Program program, String stage) {
        if (SeedlingStagePolicy.SOWN.equals(stage)
                || SeedlingStagePolicy.GERMINATING.equals(stage)) {
            return R.string.seedling_fertilizer_none;
        }
        if (SeedlingStagePolicy.COTYLEDON.equals(stage)) {
            switch (program) {
                case LEGUME: return R.string.seedling_fertilizer_legume;
                case ROOT: return R.string.seedling_fertilizer_root;
                case POTATO: return R.string.seedling_fertilizer_potato;
                case CORN: return R.string.seedling_fertilizer_corn;
                case STRAWBERRY: return R.string.seedling_fertilizer_strawberry;
                default: return R.string.seedling_fertilizer_diluted_complete;
            }
        }
        if (SeedlingStagePolicy.HARDENING.equals(stage)) {
            return R.string.seedling_fertilizer_hardening;
        }
        if (SeedlingStagePolicy.READY.equals(stage)) {
            switch (program) {
                case TRANSPLANT_HIGH:
                    return R.string.seedling_fertilizer_ready_transplant_high;
                case TRANSPLANT_LOW:
                    return R.string.seedling_fertilizer_ready_transplant_low;
                case LEAFY: return R.string.seedling_fertilizer_ready_leafy;
                case ONION: return R.string.seedling_fertilizer_ready_onion;
                case LEGUME: return R.string.seedling_fertilizer_ready_legume;
                case ROOT: return R.string.seedling_fertilizer_ready_root;
                case POTATO: return R.string.seedling_fertilizer_ready_potato;
                case CORN: return R.string.seedling_fertilizer_ready_corn;
                case STRAWBERRY: return R.string.seedling_fertilizer_ready_strawberry;
                case MODERATE: return R.string.seedling_fertilizer_ready_moderate;
                default: return R.string.seedling_fertilizer_ready_default;
            }
        }
        switch (program) {
            case TRANSPLANT_HIGH:
                return R.string.seedling_fertilizer_complete_high;
            case TRANSPLANT_LOW:
                return R.string.seedling_fertilizer_complete_low;
            case LEAFY: return R.string.seedling_fertilizer_leafy;
            case ONION: return R.string.seedling_fertilizer_onion;
            case LEGUME: return R.string.seedling_fertilizer_legume;
            case ROOT: return R.string.seedling_fertilizer_root;
            case POTATO: return R.string.seedling_fertilizer_potato;
            case CORN: return R.string.seedling_fertilizer_corn;
            case STRAWBERRY: return R.string.seedling_fertilizer_strawberry;
            case MODERATE: return R.string.seedling_fertilizer_moderate;
            default: return R.string.seedling_fertilizer_complete_default;
        }
    }

    @StringRes
    private static int applicationFor(Program program, String stage) {
        if (SeedlingStagePolicy.SOWN.equals(stage)) {
            return R.string.seedling_fertilizer_application_sown;
        }
        if (SeedlingStagePolicy.GERMINATING.equals(stage)) {
            return R.string.seedling_fertilizer_application_germinating;
        }
        if (SeedlingStagePolicy.COTYLEDON.equals(stage)) {
            return R.string.seedling_fertilizer_application_first_leaf;
        }
        if (SeedlingStagePolicy.TRUE_LEAVES.equals(stage)) {
            return R.string.seedling_fertilizer_application_true_leaves;
        }
        if (SeedlingStagePolicy.HARDENING.equals(stage)) {
            return R.string.seedling_fertilizer_application_hardening;
        }
        if (program == Program.LEGUME || program == Program.ROOT
                || program == Program.POTATO || program == Program.CORN) {
            return R.string.seedling_fertilizer_application_ready_direct;
        }
        return R.string.seedling_fertilizer_application_ready;
    }

    @StringRes
    private static int cropNoteFor(String key, Program fallback) {
        switch (key) {
            case "tomato": return R.string.seedling_fertilizer_crop_tomato;
            case "pepper": return R.string.seedling_fertilizer_crop_pepper;
            case "cucumber": return R.string.seedling_fertilizer_crop_cucumber;
            case "eggplant": return R.string.seedling_fertilizer_crop_eggplant;
            case "bean": return R.string.seedling_fertilizer_crop_bean;
            case "carrot": return R.string.seedling_fertilizer_crop_carrot;
            case "okra": return R.string.seedling_fertilizer_crop_okra;
            case "zucchini": return R.string.seedling_fertilizer_crop_zucchini;
            case "lettuce": return R.string.seedling_fertilizer_crop_lettuce;
            case "onion": return R.string.seedling_fertilizer_crop_onion;
            case "potato": return R.string.seedling_fertilizer_crop_potato;
            case "corn": return R.string.seedling_fertilizer_crop_corn;
            case "pea": return R.string.seedling_fertilizer_crop_pea;
            case "strawberry": return R.string.seedling_fertilizer_crop_strawberry;
            case "watermelon": return R.string.seedling_fertilizer_crop_watermelon;
            case "melon": return R.string.seedling_fertilizer_crop_melon;
            case "broccoli": return R.string.seedling_fertilizer_crop_brassica;
            case "parsley": return R.string.seedling_fertilizer_crop_parsley;
            default:
                switch (fallback) {
                    case LEGUME: return R.string.seedling_fertilizer_crop_legume_default;
                    case LEAFY: return R.string.seedling_fertilizer_crop_leafy_default;
                    case ONION: return R.string.seedling_fertilizer_crop_allium_default;
                    default: return R.string.seedling_fertilizer_crop_default;
                }
        }
    }

    private static Program programFor(String key) {
        switch (key) {
            case "tomato":
            case "pepper":
            case "eggplant":
            case "broccoli":
                return Program.TRANSPLANT_HIGH;
            case "cucumber":
            case "zucchini":
            case "watermelon":
            case "melon":
                return Program.TRANSPLANT_LOW;
            case "lettuce":
            case "parsley":
                return Program.LEAFY;
            case "onion": return Program.ONION;
            case "bean":
            case "pea":
                return Program.LEGUME;
            case "carrot": return Program.ROOT;
            case "potato": return Program.POTATO;
            case "corn": return Program.CORN;
            case "strawberry": return Program.STRAWBERRY;
            case "okra": return Program.MODERATE;
            default: return Program.DEFAULT;
        }
    }

    private static String cropKey(String value) {
        String normalized = safe(value).toLowerCase(Locale.forLanguageTag("tr-TR"));
        switch (normalized) {
            case "domates": return "tomato";
            case "biber": return "pepper";
            case "salatalık": case "salatalik": case "hıyar": case "hiyar":
                return "cucumber";
            case "patlıcan": case "patlican": return "eggplant";
            case "fasulye": case "nohut": case "mercimek": return "bean";
            case "havuç": case "havuc": return "carrot";
            case "bamya": return "okra";
            case "kabak": return "zucchini";
            case "marul": case "ıspanak": case "ispanak": case "roka":
            case "pazı": case "pazi": return "lettuce";
            case "soğan": case "sogan": case "sarımsak": case "sarimsak":
                return "onion";
            case "patates": return "potato";
            case "mısır": case "misir": return "corn";
            case "bezelye": return "pea";
            case "çilek": case "cilek": return "strawberry";
            case "karpuz": return "watermelon";
            case "kavun": return "melon";
            case "brokoli": case "karnabahar": case "lahana": return "broccoli";
            case "maydanoz": return "parsley";
            default: return normalized.toLowerCase(Locale.ROOT);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Recommendation {
        @StringRes private final int fertilizerText;
        @StringRes private final int applicationText;
        @StringRes private final int cropNoteText;

        private Recommendation(@StringRes int fertilizerText,
                               @StringRes int applicationText,
                               @StringRes int cropNoteText) {
            this.fertilizerText = fertilizerText;
            this.applicationText = applicationText;
            this.cropNoteText = cropNoteText;
        }

        @StringRes public int getFertilizerText() { return fertilizerText; }
        @StringRes public int getApplicationText() { return applicationText; }
        @StringRes public int getCropNoteText() { return cropNoteText; }
    }
}
