package com.alidogukan.avora.ui;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.alidogukan.avora.R;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Shared, display-only introduction for watering, seedling and fertilizer assistants. */
public final class AssistantIntroCard {
    public enum Kind { WATER, SEEDLING, FERTILIZER }

    private AssistantIntroCard() { }

    public static void bind(Activity activity, Kind kind) {
        int surfaceResource;
        int accentResource;
        int borderResource;
        int title;
        int description;
        int info;
        int imageResource;
        int statusLabel;
        int badgeText;
        int badgeTextColorResource;
        int badgeSurfaceResource;

        switch (kind) {
            case WATER:
                surfaceResource = R.color.aiWaterSurface;
                accentResource = R.color.aiWaterAccent;
                borderResource = R.color.aiWaterBorder;
                title = R.string.ai_tools_watering_assistant;
                description = R.string.assistant_intro_water_description;
                info = R.string.assistant_intro_water_info;
                imageResource = R.drawable.img_water_assistant_robot;
                statusLabel = R.string.reliability_title;
                badgeText = R.string.ai_runtime_waiting_upper;
                badgeTextColorResource = R.color.textSecondary;
                badgeSurfaceResource = R.color.surfaceSoft;
                break;
            case SEEDLING:
                surfaceResource = R.color.aiSeedlingSurface;
                accentResource = R.color.aiSeedlingAccent;
                borderResource = R.color.aiSeedlingBorder;
                title = R.string.ai_tools_seedling_assistant;
                description = R.string.assistant_intro_seedling_description;
                info = R.string.assistant_intro_seedling_info;
                imageResource = R.drawable.img_seedling_assistant_robot;
                statusLabel = R.string.reliability_title;
                badgeText = R.string.ai_runtime_waiting_upper;
                badgeTextColorResource = R.color.textSecondary;
                badgeSurfaceResource = R.color.surfaceSoft;
                break;
            case FERTILIZER:
            default:
                surfaceResource = R.color.aiFertilizerSurface;
                accentResource = R.color.assistantIntroFertilizerText;
                borderResource = R.color.aiFertilizerBorder;
                title = R.string.ai_tools_fertilization_assistant;
                description = R.string.assistant_intro_fertilizer_description;
                info = R.string.assistant_intro_fertilizer_info;
                imageResource = R.drawable.img_fertilizer_assistant_robot;
                statusLabel = R.string.assistant_intro_mode_label;
                badgeText = R.string.assistant_intro_advisory_badge;
                badgeTextColorResource = R.color.assistantIntroFertilizerText;
                badgeSurfaceResource = R.color.warningBackground;
                break;
        }

        int surface = color(activity, surfaceResource);
        int accent = color(activity, accentResource);
        int border = color(activity, borderResource);

        MaterialCardView card = activity.findViewById(R.id.cardAssistantIntro);
        card.setCardBackgroundColor(surface);
        card.setStrokeColor(border);
        ImageView image = activity.findViewById(R.id.imgAssistantIntro);
        image.setImageResource(imageResource);
        TextView heading = activity.findViewById(R.id.txtAssistantIntroTitle);
        heading.setText(title);
        heading.setTextColor(accent);
        ((TextView) activity.findViewById(R.id.txtAssistantIntroDescription))
                .setText(description);
        ((TextView) activity.findViewById(R.id.txtAssistantIntroStatusLabel))
                .setText(statusLabel);

        TextView badge = activity.findViewById(R.id.txtAssistantIntroBadge);
        badge.setText(badgeText);
        badge.setTextColor(color(activity, badgeTextColorResource));
        MaterialCardView badgeCard = activity.findViewById(R.id.cardAssistantIntroBadge);
        badgeCard.setCardBackgroundColor(color(activity, badgeSurfaceResource));

        ImageView infoButton = activity.findViewById(R.id.btnAssistantIntroInfo);
        infoButton.setImageTintList(ColorStateList.valueOf(accent));
        infoButton.setOnClickListener(view -> new MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setMessage(info)
                .setPositiveButton(android.R.string.ok, null)
                .show());
    }

    /**
     * Uses only telemetry freshness and sensor completeness; it does not invent
     * a second score alongside the seedling recommendation health score.
     */
    public static void updateSeedlingReliability(Activity activity, boolean fresh,
                                                 boolean allSensorsAvailable) {
        TextView badge = activity.findViewById(R.id.txtAssistantIntroBadge);
        MaterialCardView badgeCard = activity.findViewById(R.id.cardAssistantIntroBadge);
        if (!fresh) {
            badge.setText(R.string.ai_runtime_waiting_upper);
            badge.setTextColor(color(activity, R.color.textSecondary));
            badgeCard.setCardBackgroundColor(color(activity, R.color.surfaceSoft));
        } else if (allSensorsAvailable) {
            badge.setText(R.string.ai_runtime_high_upper);
            badge.setTextColor(color(activity, R.color.success));
            badgeCard.setCardBackgroundColor(color(activity, R.color.successBackground));
        } else {
            badge.setText(R.string.ai_runtime_medium_upper);
            badge.setTextColor(color(activity, R.color.warning));
            badgeCard.setCardBackgroundColor(color(activity, R.color.warningBackground));
        }
    }

    /** Mirrors the existing selected-zone confidence; never invents a second score. */
    public static void copyWaterConfidence(Activity activity, TextView source,
                                           MaterialCardView sourceBadge) {
        TextView target = activity.findViewById(R.id.txtAssistantIntroBadge);
        MaterialCardView targetBadge = activity.findViewById(R.id.cardAssistantIntroBadge);
        target.setText(source.getText());
        target.setTextColor(source.getCurrentTextColor());
        targetBadge.setCardBackgroundColor(sourceBadge.getCardBackgroundColor());
    }

    private static int color(Activity activity, int resource) {
        return ContextCompat.getColor(activity, resource);
    }
}
