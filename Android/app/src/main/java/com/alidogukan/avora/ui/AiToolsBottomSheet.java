package com.alidogukan.avora.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.PopupWindow;

import com.alidogukan.avora.R;
import com.alidogukan.avora.activities.AIAssistantActivity;
import com.alidogukan.avora.activities.FertilizationCalendarActivity;
import com.alidogukan.avora.activities.PlantAssistantActivity;
import com.alidogukan.avora.activities.SeedlingAssistantActivity;

/** AI tools launcher displayed directly above the shared primary navigation. */
public final class AiToolsBottomSheet {
    private static PopupWindow visiblePopup;

    private AiToolsBottomSheet() { }

    public static void show(Activity activity, View anchor, Runnable onDismiss) {
        if (visiblePopup != null && visiblePopup.isShowing()) {
            visiblePopup.dismiss();
            return;
        }

        FrameLayout inflationParent = new FrameLayout(activity);
        View content = LayoutInflater.from(activity).inflate(
                R.layout.bottom_sheet_ai_tools,
                inflationParent,
                false
        );
        int horizontalMargin = dp(activity, 6);
        int popupWidth = activity.getResources().getDisplayMetrics().widthPixels
                - (horizontalMargin * 2);

        content.measure(
                View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );

        PopupWindow popup = new PopupWindow(
                content,
                popupWidth,
                content.getMeasuredHeight(),
                true
        );
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(activity, 10));
        popup.setAnimationStyle(R.style.Animation_Avora_AiToolsPopup);
        popup.setOnDismissListener(() -> {
            if (visiblePopup == popup) visiblePopup = null;
            if (onDismiss != null && !activity.isFinishing()) onDismiss.run();
        });

        bind(activity, content, popup, R.id.cardAiToolsPlant, PlantAssistantActivity.class);
        bind(activity, content, popup, R.id.cardAiToolsSeedling,
                SeedlingAssistantActivity.class);
        bind(activity, content, popup, R.id.cardAiToolsFertilization,
                FertilizationCalendarActivity.class);
        bind(activity, content, popup, R.id.cardAiToolsWatering, AIAssistantActivity.class);

        int[] anchorLocation = new int[2];
        anchor.getLocationOnScreen(anchorLocation);
        int x = horizontalMargin;
        int y = Math.max(dp(activity, 8), anchorLocation[1] - content.getMeasuredHeight());

        visiblePopup = popup;
        popup.showAtLocation(
                activity.getWindow().getDecorView(),
                Gravity.NO_GRAVITY,
                x,
                y
        );
    }

    private static void bind(Activity activity, View root, PopupWindow popup,
                             int id, Class<?> target) {
        root.findViewById(id).setOnClickListener(v -> {
            popup.dismiss();
            activity.startActivity(new Intent(activity, target));
        });
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
