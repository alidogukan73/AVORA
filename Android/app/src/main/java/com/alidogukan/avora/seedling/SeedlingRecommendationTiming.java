package com.alidogukan.avora.seedling;

import com.alidogukan.avora.models.SeedlingRecommendation;

import java.util.Calendar;

/**
 * Keeps time-sensitive light advice out of the UI after the daytime
 * evaluation window. Other seedling advice remains unchanged.
 */
public final class SeedlingRecommendationTiming {
    static final int LIGHT_EVALUATION_START_HOUR = 8;
    static final int LIGHT_EVALUATION_END_HOUR = 18;
    private static final String LOW_LIGHT_REASON = "Işık seviyesi düşük.";
    private static final String LOW_LIGHT_ACTION =
            "Fideleri daha aydınlık konuma alın veya uygun yetiştirme ışığı kullanın.";
    private static final String HIGH_LIGHT_REASON =
            "Işık seviyesi genç fideler için çok yüksek olabilir.";
    private static final String HIGH_LIGHT_ACTION =
            "Yaprak sıcaklığını kontrol edip öğlen gölgeleme uygulayın.";

    private SeedlingRecommendationTiming() { }

    public static String actionForDisplay(SeedlingRecommendation advice, long nowMillis) {
        return actionForHour(advice, localHour(nowMillis));
    }

    public static String messageForDisplay(SeedlingRecommendation advice, long nowMillis) {
        return messageForHour(advice, localHour(nowMillis));
    }

    static String actionForHour(SeedlingRecommendation advice, int hour) {
        if (advice == null) return "";
        String value = advice.getAction();
        if (shouldEvaluateLight(hour)) return value;
        return withoutSentence(withoutSentence(value, LOW_LIGHT_ACTION), HIGH_LIGHT_ACTION);
    }

    static String messageForHour(SeedlingRecommendation advice, int hour) {
        if (advice == null) return "";
        String value = advice.getMessage();
        if (shouldEvaluateLight(hour)) return value;
        return withoutSentence(withoutSentence(value, LOW_LIGHT_REASON), HIGH_LIGHT_REASON);
    }

    static boolean shouldEvaluateLight(int hour) {
        return hour >= LIGHT_EVALUATION_START_HOUR
                && hour < LIGHT_EVALUATION_END_HOUR;
    }

    private static int localHour(long nowMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(nowMillis);
        return calendar.get(Calendar.HOUR_OF_DAY);
    }

    private static String withoutSentence(String value, String sentence) {
        if (value == null || value.isBlank()) return "";
        return value.replace(sentence, "").replaceAll("\\s{2,}", " ").trim();
    }
}
