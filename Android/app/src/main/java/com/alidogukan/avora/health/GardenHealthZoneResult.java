package com.alidogukan.avora.health;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Transparent, advisory result for one garden zone. */
public final class GardenHealthZoneResult {
    private final int score;
    private final String reason;
    private final List<GardenHealthIssue> issues;

    public GardenHealthZoneResult(int score, String reason) {
        this(score, reason, Collections.emptyList());
    }

    private GardenHealthZoneResult(int score, String reason, List<GardenHealthIssue> issues) {
        this.score = Math.max(0, Math.min(100, score));
        this.reason = reason == null ? "" : reason;
        this.issues = Collections.unmodifiableList(new ArrayList<>(issues));
    }

    public static GardenHealthZoneResult fromIssues(List<GardenHealthIssue> issues) {
        int score = 100;
        List<String> reasons = new ArrayList<>();
        for (GardenHealthIssue issue : issues) {
            score -= issue.getDeduction();
            reasons.add(issue.getReason());
        }
        String reason = reasons.isEmpty()
                ? "Nem, sensör ve gübreleme planı uygun görünüyor"
                : String.join(" · ", reasons);
        return new GardenHealthZoneResult(score, reason, issues);
    }

    public int getScore() { return score; }
    public String getReason() { return reason; }
    public List<GardenHealthIssue> getIssues() { return issues; }
}
