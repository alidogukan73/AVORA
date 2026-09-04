package com.alidogukan.avora.health;

import com.alidogukan.avora.models.FertilizationProfile;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.ZoneIrrigationStatus;
import com.alidogukan.avora.plantassistant.PlantAssistantHealthSignal;
import com.alidogukan.avora.season.SeasonScope;

import java.util.ArrayList;
import java.util.List;

import static com.alidogukan.avora.health.GardenHealthIssue.Target.*;

/** Conservative and explainable; this score never controls hardware. */
public final class GardenHealthCalculator {
    private GardenHealthCalculator() { }

    /** The health screen follows the same active-crop boundary as the home screen. */
    public static List<GardenZone> activeHealthZones(List<GardenZone> zones) {
        return SeasonScope.activeSeasonZones(zones);
    }

    public static GardenHealthSummary calculate(List<GardenZone> zones, long now) {
        return calculate(zones, now, null);
    }

    public static GardenHealthSummary calculate(
            List<GardenZone> zones,
            long now,
            PlantAssistantHealthSignal assistantSignal
    ) {
        if (zones == null || zones.isEmpty()) {
            return new GardenHealthSummary(0, "Bahçe verisi bekleniyor",
                    "Bölgeler bağlandığında sağlık özeti hazırlanır.");
        }
        List<GardenZone> healthZones = activeHealthZones(zones);
        int total = 0;
        int count = 0;
        String priority = "";
        int priorityScore = Integer.MAX_VALUE;
        for (GardenZone zone : healthZones) {
            GardenHealthZoneResult result = evaluateZone(zone, now, assistantSignal);
            total += result.getScore();
            count++;
            if (result.getScore() < priorityScore && result.getScore() < 100) {
                priorityScore = result.getScore();
                priority = safeName(zone) + " · " + result.getReason();
            }
        }
        if (count == 0) {
            return new GardenHealthSummary(0, "Aktif bölge yok",
                    "Sağlık özeti için en az bir aktif bölge gerekir.");
        }
        int average = Math.round((float) total / count);
        String title = average >= 85 ? "Bahçe genel olarak iyi durumda"
                : average >= 65 ? "Bahçede uyarı var"
                : "Bahçe kontrolü öneriliyor";
        String detail = priority.isEmpty()
                ? count + " aktif bölgenin nem, sensör ve gübreleme planı uygun görünüyor"
                : priority;
        return new GardenHealthSummary(average, title, detail);
    }

    public static GardenHealthZoneResult evaluateZone(GardenZone zone, long now) {
        return evaluateZone(zone, now, null);
    }

    public static GardenHealthZoneResult evaluateZone(
            GardenZone zone,
            long now,
            PlantAssistantHealthSignal assistantSignal
    ) {
        if (zone == null) return new GardenHealthZoneResult(0, "Bölge verisi yok");
        List<GardenHealthIssue> issues = new ArrayList<>();
        if (!zone.isSensor_enabled()) {
            issues.add(new GardenHealthIssue("Sensör devre dışı", 45, SENSOR_SETTINGS));
        } else if (!zone.hasSensorData()) {
            issues.add(new GardenHealthIssue("Sensör verisi bekleniyor", 45, SENSOR_SETTINGS));
        }

        if (zone.isSensor_enabled() && zone.hasSensorData()) {
            long age = Math.max(0L, now - zone.getUpdated_at_epoch());
            if (age > 15 * 60L) {
                issues.add(new GardenHealthIssue("Sensör verisi güncel değil", 30, SENSOR_SETTINGS));
            }
            if (zone.getMoisture() < zone.getMoisture_limit()) {
                issues.add(new GardenHealthIssue(
                        "Nem düşük: %" + zone.getMoisture() + " / sınır %" + zone.getMoisture_limit(),
                        Math.min(35, 10 + zone.getMoisture_limit() - zone.getMoisture()), IRRIGATION_SETTINGS));
            }
            ZoneIrrigationStatus irrigation = zone.getIrrigation_status();
            if (irrigation != null && irrigation.hasSensor_stable()
                    && !irrigation.isSensor_stable()) {
                issues.add(new GardenHealthIssue("Sensör ölçümü kararsız", 20, SENSOR_SETTINGS));
            }
        }
        FertilizationProfile profile = zone.getFertilization();
        if (profile != null && profile.isEnabled()
                && profile.getNext_application_at_epoch() > 0
                && profile.getNext_application_at_epoch() <= now) {
            issues.add(new GardenHealthIssue("Gübreleme kaydı bekleniyor", 10, FERTILIZATION));
        }
        if (assistantSignal != null && assistantSignal.appliesTo(zone, now)) {
            String urgency = assistantSignal.getUrgency();
            if ("Yüksek".equalsIgnoreCase(urgency)) {
                issues.add(new GardenHealthIssue("Bitki Asistanı: yüksek aciliyet",
                        25, PLANT_ASSISTANT, assistantSignal.getSeasonId()));
            } else if ("Orta".equalsIgnoreCase(urgency)) {
                issues.add(new GardenHealthIssue("Bitki Asistanı: orta aciliyet",
                        12, PLANT_ASSISTANT, assistantSignal.getSeasonId()));
            }
            // A routine, low-urgency observation is not an unresolved health problem.
            // Keep its recommendation/history, but only medium/high findings reduce the score.
        }
        return GardenHealthZoneResult.fromIssues(issues);
    }

    private static String safeName(GardenZone zone) {
        return com.alidogukan.avora.zones.PhysicalZoneIdentity.name(zone);
    }
}
