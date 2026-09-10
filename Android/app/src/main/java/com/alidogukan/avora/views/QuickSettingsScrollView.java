package com.alidogukan.avora.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ScrollView;

import androidx.annotation.Nullable;

/** Keeps the quick-settings dialog actions visible on short phone screens. */
public final class QuickSettingsScrollView extends ScrollView {

    // MaterialAlertDialog adds its title, message, content padding and window insets
    // outside this view. Keeping the list compact prevents the whole dialog from
    // being vertically centred beyond the visible window on high-density phones.
    private static final int MAX_CONTENT_HEIGHT_DP = 260;
    private static final int MIN_CONTENT_HEIGHT_DP = 96;
    private static final int RESERVED_DIALOG_HEIGHT_DP = 360;

    public QuickSettingsScrollView(Context context) {
        super(context);
    }

    public QuickSettingsScrollView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public QuickSettingsScrollView(Context context, @Nullable AttributeSet attrs,
                                   int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int contentLimit = calculateContentLimit();
        int parentMode = View.MeasureSpec.getMode(heightMeasureSpec);
        int parentSize = View.MeasureSpec.getSize(heightMeasureSpec);
        if (parentMode != View.MeasureSpec.UNSPECIFIED && parentSize > 0) {
            contentLimit = Math.min(contentLimit, parentSize);
        }
        int limitedHeight = View.MeasureSpec.makeMeasureSpec(
                Math.max(1, contentLimit),
                View.MeasureSpec.AT_MOST);
        super.onMeasure(widthMeasureSpec, limitedHeight);
    }

    private int calculateContentLimit() {
        float density = getResources().getDisplayMetrics().density;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int minimum = Math.round(MIN_CONTENT_HEIGHT_DP * density);
        int maximum = Math.round(MAX_CONTENT_HEIGHT_DP * density);
        int reserved = Math.round(RESERVED_DIALOG_HEIGHT_DP * density);
        int available = screenHeight - reserved;
        return Math.max(minimum, Math.min(maximum, available));
    }
}
