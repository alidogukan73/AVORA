package com.alidogukan.avora.ui;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.alidogukan.avora.R;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Shared, display-only introduction for watering and fertilizer assistants. */
public final class AssistantIntroCard {
    public enum Kind { WATER, FERTILIZER }

    private AssistantIntroCard() { }

    public static void bind(Activity activity, Kind kind) {
        boolean water = kind == Kind.WATER;
        int surface = color(activity, water ? R.color.aiWaterSurface : R.color.aiFertilizerSurface);
        int accent = color(activity, water ? R.color.aiWaterAccent : R.color.assistantIntroFertilizerText);
        int border = color(activity, water ? R.color.aiWaterBorder : R.color.aiFertilizerBorder);
        int title = water ? R.string.ai_tools_watering_assistant
                : R.string.ai_tools_fertilization_assistant;
        int description = water ? R.string.assistant_intro_water_description
                : R.string.assistant_intro_fertilizer_description;
        int info = water ? R.string.assistant_intro_water_info
                : R.string.assistant_intro_fertilizer_info;

        MaterialCardView card = activity.findViewById(R.id.cardAssistantIntro);
        card.setCardBackgroundColor(surface);
        card.setStrokeColor(border);
        ImageView image = activity.findViewById(R.id.imgAssistantIntro);
        image.setImageResource(water ? R.drawable.img_water_assistant_robot
                : R.drawable.img_fertilizer_assistant_robot);
        TextView heading = activity.findViewById(R.id.txtAssistantIntroTitle);
        heading.setText(title);
        heading.setTextColor(accent);
        ((TextView) activity.findViewById(R.id.txtAssistantIntroDescription))
                .setText(description);
        ((TextView) activity.findViewById(R.id.txtAssistantIntroStatusLabel))
                .setText(water ? R.string.reliability_title
                        : R.string.assistant_intro_mode_label);

        TextView badge = activity.findViewById(R.id.txtAssistantIntroBadge);
        badge.setText(water ? R.string.ai_runtime_waiting_upper
                : R.string.assistant_intro_advisory_badge);
        badge.setTextColor(water ? color(activity, R.color.textSecondary) : accent);
        MaterialCardView badgeCard = activity.findViewById(R.id.cardAssistantIntroBadge);
        badgeCard.setCardBackgroundColor(color(activity,
                water ? R.color.surfaceSoft : R.color.warningBackground));

        ImageView infoButton = activity.findViewById(R.id.btnAssistantIntroInfo);
        infoButton.setImageTintList(ColorStateList.valueOf(accent));
        infoButton.setOnClickListener(view -> new MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setMessage(info)
                .setPositiveButton(android.R.string.ok, null)
                .show());
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
