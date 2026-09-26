package com.alidogukan.avora.ui;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.graphics.Insets;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.alidogukan.avora.R;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.function.Consumer;

/** Journal entries and care shortcuts; choosing a row never starts physical equipment. */
public final class JournalEntryBottomSheet {
    private JournalEntryBottomSheet() { }

    public static BottomSheetDialog show(Activity activity, Consumer<String> onAction) {
        BottomSheetDialog dialog = new BottomSheetDialog(
                activity, R.style.ThemeOverlay_AVORA_JournalEntrySheet);
        LayoutInflater inflater = LayoutInflater.from(dialog.getContext());
        View content = inflater.inflate(R.layout.bottom_sheet_journal_entry,
                new FrameLayout(dialog.getContext()), false);
        LinearLayout records = content.findViewById(R.id.layoutJournalRecordActions);
        LinearLayout care = content.findViewById(R.id.layoutJournalCareActions);
        boolean[] navigating = {false};
        Consumer<String> choose = action -> {
            if (navigating[0]) return;
            navigating[0] = true;
            dialog.dismiss();
            onAction.accept(action);
        };
        addAction(inflater, records, "photo_growth", R.string.journal_growth_title,
                R.string.journal_growth_description, R.drawable.ic_journal_camera_24,
                R.color.surfaceGreen, R.color.primary, choose);
        addAction(inflater, records, "observation", R.string.runtime_event_note,
                R.string.journal_observation_description, R.drawable.ic_journal_note_24,
                R.color.surfaceSoft, R.color.textSecondary, choose);
        addAction(inflater, care, "watering", R.string.journal_manual_watering,
                R.string.journal_watering_description, R.drawable.ic_journal_water_24,
                R.color.infoBackground, R.color.info, choose);
        addAction(inflater, care, "fertilization", R.string.notification_category_fertilization,
                R.string.journal_fertilization_description, R.drawable.ic_journal_fertilizer_24,
                R.color.surfaceGreen, R.color.primary, choose);
        content.findViewById(R.id.btnJournalSheetClose).setOnClickListener(v -> dialog.dismiss());
        ViewCompat.setAccessibilityHeading(content.findViewById(R.id.txtJournalSheetTitle), true);
        ViewCompat.setAccessibilityHeading(content.findViewById(R.id.txtJournalCareHeading), true);
        ViewCompat.setAccessibilityPaneTitle(content, activity.getString(R.string.journal_add_title));
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, 0, bars.right, bars.bottom);
            return windowInsets;
        });
        dialog.setContentView(content);
        dialog.setDismissWithAnimation(true);
        BottomSheetBehavior<FrameLayout> behavior = dialog.getBehavior();
        behavior.setMaxWidth(dp(activity, 560));
        behavior.setMaxHeight(Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.9f));
        behavior.setFitToContents(true);
        behavior.setSkipCollapsed(true);
        dialog.setOnShowListener(ignored -> {
            FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                sheet.setBackgroundResource(R.drawable.bg_journal_sheet);
                sheet.setClipToOutline(true);
            }
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            ViewCompat.requestApplyInsets(content);
        });
        dialog.show();
        return dialog;
    }

    private static void addAction(LayoutInflater inflater, LinearLayout parent, String action,
                                  int title, int description, int icon, int surface, int tint,
                                  Consumer<String> onAction) {
        View row = inflater.inflate(R.layout.item_journal_entry_action, parent, false);
        ((TextView) row.findViewById(R.id.txtJournalActionTitle)).setText(title);
        ((TextView) row.findViewById(R.id.txtJournalActionDescription)).setText(description);
        ImageView image = row.findViewById(R.id.imgJournalActionIcon);
        image.setImageResource(icon);
        image.setImageTintList(ColorStateList.valueOf(parent.getContext().getColor(tint)));
        image.setBackgroundTintList(ColorStateList.valueOf(parent.getContext().getColor(surface)));
        row.setContentDescription(parent.getContext().getString(title) + ". "
                + parent.getContext().getString(description));
        ViewCompat.setAccessibilityDelegate(row, new AccessibilityDelegateCompat() {
            @Override public void onInitializeAccessibilityNodeInfo(
                    View host, AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(android.widget.Button.class.getName());
            }
        });
        row.setOnClickListener(v -> onAction.accept(action));
        parent.addView(row);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
