package com.alidogukan.avora.activities;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Applies one consistent safe area to screens that do not need custom inset handling.
 * This keeps toolbars below status-bar/camera cutouts and content above navigation bars.
 */
public abstract class EdgeToEdgeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
    }

    @Override
    public void setContentView(int layoutResId) {
        super.setContentView(layoutResId);
        applyContentInsets();
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        applyContentInsets();
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        super.setContentView(view, params);
        applyContentInsets();
    }

    private void applyContentInsets() {
        View content = findViewById(android.R.id.content);
        if (content == null) return;

        ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
            Insets safeArea = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(
                    safeArea.left,
                    safeArea.top,
                    safeArea.right,
                    safeArea.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(content);
    }
}
