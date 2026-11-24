package com.shema.campuspulse;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class SystemBars {

    /** Navy strip behind the status bar so scrolling content never runs under the clock. */
    public static void addStatusScrim(Activity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        View scrim = new View(activity);
        scrim.setBackgroundColor(ContextCompat.getColor(activity, R.color.usiu_navy));
        content.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.TOP));

        ViewCompat.setOnApplyWindowInsetsListener(scrim, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            ViewGroup.LayoutParams p = v.getLayoutParams();
            p.height = bars.top;
            v.setLayoutParams(p);
            return insets;
        });
        ViewCompat.requestApplyInsets(scrim);
    }
}