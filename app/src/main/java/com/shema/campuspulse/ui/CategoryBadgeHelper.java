package com.shema.campuspulse.ui;

import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.shema.campuspulse.R;

import java.util.Locale;

/**
 * Institutional category badge styler for USIU-Africa CampusPulse.
 * Applies consistent semantic background tints and legible typography.
 */
public final class CategoryBadgeHelper {

    private CategoryBadgeHelper() {}

    public static void applyCategoryBadge(TextView view, String category) {
        if (view == null) return;

        String cat = category != null ? category.trim() : "Campus";
        view.setText(cat.toUpperCase(Locale.getDefault()));

        float density = view.getResources().getDisplayMetrics().density;
        int cornerRadiusPx = Math.round(8 * density);

        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(cornerRadiusPx);

        pill.setColor(ContextCompat.getColor(view.getContext(), R.color.badge_navy_bg));
        view.setBackground(pill);
        view.setTextColor(ContextCompat.getColor(view.getContext(), R.color.badge_navy_text));
    }
}
