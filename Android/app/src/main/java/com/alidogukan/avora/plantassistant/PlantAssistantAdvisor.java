package com.alidogukan.avora.plantassistant;

import com.alidogukan.avora.fertilization.FertilizerDataFreshnessPolicy;
import com.alidogukan.avora.models.FertilizationProfile;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.WeatherForecast;

import java.util.List;
import com.alidogukan.avora.settings.DisplayUnitFormatter;

/**
 * Explainable field screening. This class deliberately produces a likelihood,
 * never a definitive disease diagnosis and never controls irrigation or dosing.
 */
public final class PlantAssistantAdvisor {
    private PlantAssistantAdvisor() { }

    public static PlantAssistantResult assess(GardenZone zone, List<String> symptoms,
                                           String note, WeatherForecast weather,
                                           boolean hasPhoto, boolean growthStatusRequested) {
        return assess(zone, symptoms, note, weather, hasPhoto,
                growthStatusRequested, DisplayUnitFormatter.metric());
    }

    public static PlantAssistantResult assess(GardenZone zone, List<String> symptoms,
                                           String note, WeatherForecast weather,
                                           boolean hasPhoto, boolean growthStatusRequested,
                                           DisplayUnitFormatter units) {
        int moisture = zone.getMoisture();
        int limit = zone.getMoisture_limit();
        long nowEpoch = System.currentTimeMillis() / 1000L;
        boolean sensorPresent = zone.hasSensorData();
        boolean sensorReady =
                FertilizerDataFreshnessPolicy.isSensorFresh(zone, nowEpoch);
        boolean veryDry = sensorReady && moisture < Math.max(0, limit - 10);
        boolean veryWet = sensorReady && moisture > limit + 20;
        boolean fertilizerDue = isFertilizerDue(zone.getFertilization());
        boolean weatherReady = weather != null
                && FertilizerDataFreshnessPolicy.isWeatherFresh(weather, nowEpoch);
        WeatherForecast trustedWeather = weatherReady ? weather : null;
        double temperature = value(trustedWeather == null ? null : trustedWeather.getCurrentTemperature());
        double humidity = value(trustedWeather == null ? null : trustedWeather.getCurrentHumidity());
        double rain = value(trustedWeather == null ? null : trustedWeather.getTodayRainProbability());
        double wind = value(trustedWeather == null ? null : trustedWeather.getCurrentWind());

        String moistureContext = sensorReady
                ? "Toprak nemi %" + moisture + " (sınır %" + limit + ") · sensör verisi güncel"
                : sensorPresent
                ? "Toprak nemi ölçümü güncel değil · sensör verisi güncel değil"
                : "Toprak nemi bekleniyor · sensör verisi bekleniyor";
        String context = moistureContext
                + weatherContext(trustedWeather, units)
                + " · " + (fertilizerDue ? "gübreleme planı gecikmiş" : "gübreleme planı güncel")
                + " · " + (hasPhoto ? "fotoğraf eklendi" : "fotoğraf eklenmedi");

        if (growthStatusRequested) {
            return result("Bitki gelişimi yapay zekâ ile değerlendiriliyor", "%35", "Düşük", context,
                    "Fotoğraf; bitkinin canlılığı, gelişim evresi, yaprak-gövde dengesi ve görünür stres işaretleri "
                            + "için bahçe verileriyle birlikte inceleniyor.");
        }

        if (hasSymptom(symptoms, "Yaprakta leke / yanıklık", "Leaf spots / scorching")
                && (hasSymptom(symptoms, "Solma", "Wilting")
                || hasSymptom(symptoms, "Yaprak kuruması", "Leaf drying"))) {
            return result("Yayılım gösteren yaprak sorunu ihtimali", "%75", "Yüksek", context,
                    "Lekeli ve solan yapraklar birlikte görüldüğü için aynı bitkinin yakın plan fotoğrafını 24 saat içinde tekrar alın. "
                            + "Hızlı yayılma, küf, çürüme veya gövdede kararma varsa yerel ziraat uzmanına başvurun.");
        }

        if (hasSymptom(symptoms, "Yaprakta leke / yanıklık", "Leaf spots / scorching")) {
            int score = humidity >= 70 || rain >= 50 ? 70 : 52;
            String urgency = score >= 70 ? "Orta" : "Düşük";
            return result("Yaprak hastalığı veya yanık ihtimali", percent(score), urgency, context,
                    "Lekelerin alt ve üst yapraklardaki yayılımını 3 gün izleyin. "
                            + "Yaprakları ıslatmadan sulayın; hızlı yayılma, küf veya çürüme varsa "
                            + "yakın plan fotoğrafla ziraat uzmanına danışın.");
        }

        if (hasSymptom(symptoms, "Meyve çatlaması", "Fruit cracking")) {
            int score = rain >= 40 || veryWet ? 72 : 55;
            return result("Düzensiz su alımı kaynaklı çatlama ihtimali", percent(score), "Orta", context,
                    "Sulamayı ani ve büyük değişimler yerine kısa, dengeli çevrimlerle sürdürün. "
                            + "Yağış sonrası ekstra sulama veya gübre uygulaması yapmadan önce kök bölgesini kontrol edin.");
        }

        if (hasSymptom(symptoms, "Çiçek dökümü", "Flower drop")) {
            int score = temperature >= 32 || wind >= 25 ? 68 : 48;
            return result("Sıcaklık veya çevre stresi ihtimali", percent(score), score >= 65 ? "Orta" : "Düşük", context,
                    "Öğle sıcağında işlem yapmayın. Sabah erken gözlem yapın; toprak nemini dengeli tutun. "
                            + "Çiçek kaybı artarsa fotoğrafla ve son besleme kaydıyla birlikte değerlendirin.");
        }

        if (hasSymptom(symptoms, "Alt yapraklarda sararma", "Yellowing of lower leaves")) {
            if (veryDry) {
                return result("Su stresi ihtimali", "%78", "Orta", context,
                        "Önce normal sulama çevriminin tamamlanmasını bekleyin. Sulama sonrası 24–48 saat gözlem yapın; "
                                + "hemen ek gübre uygulamayın.");
            }
            int score = fertilizerDue ? 68 : 52;
            return result("Besin eksikliği veya doğal yaşlanma ihtimali", percent(score), "Düşük", context,
                    "Alt yapraklardaki damar rengini ve sararmanın yeni yapraklara yayılıp yayılmadığını kaydedin. "
                            + "Gübre önerisini yalnızca ürün etiketi ve toprak/yaprak analiziyle kesinleştirin.");
        }

        if (hasSymptom(symptoms, "Yaprak kuruması", "Leaf drying")
                || hasSymptom(symptoms, "Solma", "Wilting")) {
            int score = veryDry || temperature >= 31 ? 72 : 48;
            return result("Su, kök veya sıcaklık stresi ihtimali", percent(score), score >= 70 ? "Orta" : "Düşük", context,
                    "Kök bölgesinde kuruluk ya da su birikmesi olmadığını kontrol edin. "
                            + "Sıcak saatlerde sulama yerine sistemin planlı çevrimini takip edin ve 24 saat sonra yeniden gözlem yapın.");
        }

        String detail = note == null || note.trim().isEmpty()
                ? "Belirti ayrıntısı girilmedi."
                : "Not: " + note.trim();
        return result("Gözlem kaydı oluşturuldu", hasPhoto ? "%35" : "%20", "Düşük", context,
                detail + " Aynı bölgeyi 3 gün sonra aynı açıdan tekrar fotoğraflayın. "
                        + "Bu sonuç destek amaçlıdır; kesin teşhis değildir.");
    }

    private static boolean isFertilizerDue(FertilizationProfile profile) {
        long now = System.currentTimeMillis() / 1000L;
        return profile != null && profile.isEnabled()
                && profile.getNext_application_at_epoch() > 0L
                && profile.getNext_application_at_epoch() <= now;
    }

    private static boolean hasSymptom(List<String> symptoms, String... localizedValues) {
        if (symptoms == null || symptoms.isEmpty() || localizedValues == null) return false;
        for (String symptom : symptoms) {
            if (symptom == null) continue;
            for (String localizedValue : localizedValues) {
                if (localizedValue != null && localizedValue.equalsIgnoreCase(symptom.trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String weatherContext(WeatherForecast weather,
                                         DisplayUnitFormatter units) {
        if (weather == null || weather.getCurrentTemperature() == null) return " · hava verisi bekleniyor";
        return " · hava " + units.formatTemperature(weather.getCurrentTemperature())
                + (weather.getCurrentHumidity() == null ? "" : " / nem %" + Math.round(weather.getCurrentHumidity()))
                + (weather.getTodayRainProbability() == null ? "" : " / yağış %" + Math.round(weather.getTodayRainProbability()));
    }

    private static double value(Double value) { return value == null ? -1d : value; }
    private static String percent(int value) { return "%" + value; }

    private static PlantAssistantResult result(String title, String probability, String urgency,
                                            String context, String advice) {
        return new PlantAssistantResult(title, probability, urgency, context, advice);
    }
}
