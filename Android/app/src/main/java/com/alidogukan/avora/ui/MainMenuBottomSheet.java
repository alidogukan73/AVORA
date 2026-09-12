package com.alidogukan.avora.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.alidogukan.avora.R;
import com.alidogukan.avora.activities.AIAssistantActivity;
import com.alidogukan.avora.activities.FertilizationCalendarActivity;
import com.alidogukan.avora.activities.PlantAssistantActivity;
import com.alidogukan.avora.activities.SeasonManagementActivity;
import com.alidogukan.avora.activities.SeedlingAssistantActivity;
import com.alidogukan.avora.activities.StatisticsActivity;
import com.alidogukan.avora.activities.WateringHistoryActivity;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Compact navigation dashboard shown from the three-dot menu. */
public class MainMenuBottomSheet extends BottomSheetDialogFragment {
    private boolean navigationInProgress;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_main_menu, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bind(view, R.id.menuCardPlantAssistant, PlantAssistantActivity.class);
        bind(view, R.id.menuCardSeedlingAssistant, SeedlingAssistantActivity.class);
        bind(view, R.id.menuCardFertilization, FertilizationCalendarActivity.class);
        bind(view, R.id.menuCardAIAssistant, AIAssistantActivity.class);
        bind(view, R.id.menuCardStatistics, StatisticsActivity.class);
        bind(view, R.id.menuCardHistory, WateringHistoryActivity.class);
        bind(view, R.id.menuCardSeasonManagement, SeasonManagementActivity.class);
        view.findViewById(R.id.menuCardSecureExit)
                .setOnClickListener(ignored -> confirmSecureExit());
    }

    private void bind(View root, int id, Class<?> screen) {
        MaterialCardView card = root.findViewById(id);
        card.setOnClickListener(view -> openActivity(screen));
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setDismissWithAnimation(true);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnShowListener(ignored -> {
            FrameLayout sheet = dialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (sheet == null) return;
            sheet.setBackgroundResource(android.R.color.transparent);
            BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(sheet);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
            behavior.setFitToContents(true);
            behavior.setDraggable(true);
        });
        return dialog;
    }

    private void openActivity(Class<?> screen) {
        if (navigationInProgress) return;
        navigationInProgress = true;
        startActivity(new Intent(requireContext(), screen));
        dismissAllowingStateLoss();
    }

    private void confirmSecureExit() {
        if (navigationInProgress) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.app_secure_exit_confirm_title)
                .setMessage(R.string.app_secure_exit_confirm_message)
                .setNegativeButton(R.string.app_secure_exit_cancel, null)
                .setPositiveButton(R.string.app_secure_exit_confirm, (dialog, which) -> {
                    navigationInProgress = true;
                    Activity host = getActivity();
                    dismissAllowingStateLoss();
                    if (host != null) {
                        host.finishAndRemoveTask();
                    }
                })
                .show();
    }
}
