package com.shema.campuspulse.ui;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.gson.JsonObject;
import com.shema.campuspulse.R;
import com.shema.campuspulse.data.ApiClient;
import com.shema.campuspulse.data.CampusRepository;
import com.shema.campuspulse.data.SportsTeam;
import com.shema.campuspulse.data.UserPrefs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MyTeamsActivity extends AppCompatActivity {
    private final List<SportsTeam> teams = new ArrayList<>();
    private final Set<String> favorites = new HashSet<>();
    private LinearLayout list;
    private ProgressBar progress;
    private TextView message;
    private boolean favoritesReady;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.surface_soft));
        root.setPadding(dp(18), dp(18), dp(18), dp(18));

        Button back = new Button(this);
        back.setText("‹   SPORTS DESK");
        back.setAllCaps(false);
        back.setTextColor(ContextCompat.getColor(this, R.color.usiu_navy));
        back.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        back.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        root.addView(back, new LinearLayout.LayoutParams(-1, dp(42)));
        back.setOnClickListener(v -> finish());

        TextView title = new TextView(this);
        title.setText("My Teams");
        title.setTextSize(28);
        title.setTextColor(ContextCompat.getColor(this, R.color.usiu_navy));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView intro = new TextView(this);
        intro.setText("Follow your favourites across every sport.");
        intro.setTextSize(14);
        intro.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        LinearLayout.LayoutParams introParams = new LinearLayout.LayoutParams(-1, -2);
        introParams.bottomMargin = dp(12);
        root.addView(intro, introParams);

        message = new TextView(this);
        message.setTextSize(12);
        message.setTextColor(ContextCompat.getColor(this, R.color.status_danger));
        root.addView(message);

        progress = new ProgressBar(this);
        root.addView(progress, new LinearLayout.LayoutParams(dp(32), dp(32)));

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        loadTeams();
    }

    private void loadTeams() {
        ApiClient.get(this).getSportsTeams().enqueue(new Callback<List<SportsTeam>>() {
            @Override public void onResponse(Call<List<SportsTeam>> call, Response<List<SportsTeam>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    progress.setVisibility(View.GONE);
                    message.setText("Couldn't load the team directory. Check your connection and try again.");
                    return;
                }
                teams.clear();
                teams.addAll(response.body());
                ApiClient.get(MyTeamsActivity.this).getFavoriteTeams().enqueue(new Callback<List<SportsTeam>>() {
                    @Override public void onResponse(Call<List<SportsTeam>> call, Response<List<SportsTeam>> response) {
                        favoritesReady = response.isSuccessful() && response.body() != null;
                        favorites.clear();
                        if (favoritesReady) {
                            for (SportsTeam team : response.body()) favorites.add(team.teamId);
                            message.setText(favorites.isEmpty() ? "No favourites yet. Tap Follow to build your matchday list." : "Your favourites are synced to your account.");
                        } else {
                            message.setText("Sign in to save teams across devices.");
                        }
                        progress.setVisibility(View.GONE);
                        render();
                    }

                    @Override public void onFailure(Call<List<SportsTeam>> call, Throwable error) {
                        progress.setVisibility(View.GONE);
                        message.setText("Sign in to view and manage account favourites.");
                        render();
                    }
                });
            }

            @Override public void onFailure(Call<List<SportsTeam>> call, Throwable error) {
                progress.setVisibility(View.GONE);
                message.setText("Couldn't load the team directory. Check your connection and try again.");
            }
        });
    }

    private void render() {
        list.removeAllViews();
        for (SportsTeam team : teams) {
            MaterialCardView card = new MaterialCardView(this);
            card.setRadius(dp(18));
            card.setCardElevation(dp(1));
            card.setStrokeWidth(dp(1));
            card.setStrokeColor(ContextCompat.getColor(this, R.color.divider));
            card.setCardBackgroundColor(android.graphics.Color.WHITE);

            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(12), dp(12), dp(12));

            LinearLayout textColumn = new LinearLayout(this);
            textColumn.setOrientation(LinearLayout.VERTICAL);
            TextView name = new TextView(this);
            name.setText(team.name);
            name.setTextSize(16);
            name.setTypeface(null, android.graphics.Typeface.BOLD);
            name.setTextColor(ContextCompat.getColor(this, R.color.usiu_navy));
            textColumn.addView(name);
            TextView meta = new TextView(this);
            meta.setText(value(team.sport) + "  ·  " + value(team.competition));
            meta.setTextSize(11);
            meta.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            textColumn.addView(meta);
            row.addView(textColumn, new LinearLayout.LayoutParams(0, -2, 1));

            Button follow = new Button(this);
            boolean isFollowing = favorites.contains(team.teamId);
            follow.setText(isFollowing ? "Following" : "Follow");
            follow.setAllCaps(false);
            follow.setTextSize(12);
            follow.setEnabled(favoritesReady);
            follow.setTextColor(ContextCompat.getColor(this, R.color.usiu_navy));
            row.addView(follow, new LinearLayout.LayoutParams(-2, dp(44)));
            card.addView(row);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
            cardParams.bottomMargin = dp(10);
            list.addView(card, cardParams);

            textColumn.setOnClickListener(v -> TeamProfileActivity.open(this, team.teamId));
            follow.setOnClickListener(v -> toggleFavorite(team));
        }
    }

    private void toggleFavorite(SportsTeam team) {
        boolean wasFollowing = favorites.contains(team.teamId);
        if (!favoritesReady) {
            Toast.makeText(this, "Sign in to follow teams.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (wasFollowing) {
            ApiClient.get(this).unfollowTeam(team.teamId).enqueue(new Callback<Void>() {
                @Override public void onResponse(Call<Void> call, Response<Void> response) {
                    finishFavoriteToggle(team, true, response.isSuccessful());
                }
                @Override public void onFailure(Call<Void> call, Throwable error) {
                    finishFavoriteToggle(team, true, false);
                }
            });
        } else {
            JsonObject body = new JsonObject();
            body.addProperty("team_id", team.teamId);
            ApiClient.get(this).followTeam(body).enqueue(new Callback<SportsTeam>() {
                @Override public void onResponse(Call<SportsTeam> call, Response<SportsTeam> response) {
                    finishFavoriteToggle(team, false, response.isSuccessful());
                }
                @Override public void onFailure(Call<SportsTeam> call, Throwable error) {
                    finishFavoriteToggle(team, false, false);
                }
            });
        }
    }

    private void finishFavoriteToggle(SportsTeam team, boolean wasFollowing, boolean success) {
        if (!success) {
            Toast.makeText(this, "Couldn't update your teams. Check your connection and try again.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (wasFollowing) {
            favorites.remove(team.teamId);
            if (CampusRepository.get().isFollowing(team.teamId)) {
                CampusRepository.get().toggleFollow(team.teamId);
            }
        } else {
            favorites.add(team.teamId);
            if (!CampusRepository.get().isFollowing(team.teamId)) {
                CampusRepository.get().toggleFollow(team.teamId);
            }
        }
        UserPrefs.save(this);
        message.setText(favorites.isEmpty() ? "No favourites yet. Tap Follow to build your matchday list." : "Your favourites are synced to your account.");
        render();
    }

    private String value(String value) { return value == null || value.trim().isEmpty() ? "Campus series" : value; }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
