package com.shema.campuspulse.ui;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.shema.campuspulse.R;
import com.shema.campuspulse.data.Event;

public final class EventMediaHelper {

    private EventMediaHelper() {}

    public static int getFallbackPosterRes(String category) {
        if ("Career".equalsIgnoreCase(category)) {
            return R.drawable.poster_career;
        } else if ("Sports".equalsIgnoreCase(category)) {
            return R.drawable.bg_sports_hero;
        } else if ("Social".equalsIgnoreCase(category) || "Campus Life".equalsIgnoreCase(category)) {
            return R.drawable.poster_campus_life;
        } else if ("Wellness".equalsIgnoreCase(category)) {
            return R.drawable.poster_wellness;
        }
        return R.drawable.poster_academic;
    }

    public static void loadPoster(Context context, Event event, ImageView imageView) {
        if (imageView == null || context == null) return;
        String mediaUrl = event != null ? event.getUsableMediaUrl() : null;
        int fallbackRes = getFallbackPosterRes(event != null ? event.type : null);

        imageView.setVisibility(View.VISIBLE);
        Glide.with(context).clear(imageView);

        if (mediaUrl != null && !mediaUrl.trim().isEmpty()) {
            Glide.with(context)
                    .load(mediaUrl)
                    .placeholder(fallbackRes)
                    .error(fallbackRes)
                    .centerCrop()
                    .into(imageView);
        } else {
            imageView.setImageResource(fallbackRes);
        }
    }
}
