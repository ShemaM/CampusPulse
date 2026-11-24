package com.shema.campuspulse.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.tabs.TabLayout;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.shema.campuspulse.R;
import com.shema.campuspulse.data.ApiClient;
import com.shema.campuspulse.data.Fixture;
import com.shema.campuspulse.data.SportsTeam;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TeamProfileActivity extends AppCompatActivity {
    private static final String EXTRA_TEAM = "sports_team_id";
    private final List<Fixture> fixtures = new ArrayList<>();
    private String teamId;
    private SportsTeam team;
    private JsonObject standings;
    private boolean following;
    private boolean favoriteRequestReady;
    private LinearLayout content;
    private Button followButton;
    private int selectedTab;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, d MMM · h:mm a", Locale.getDefault());

    public static void open(Context context, String teamId) {
        Intent intent = new Intent(context, TeamProfileActivity.class);
        intent.putExtra(EXTRA_TEAM, teamId);
        context.startActivity(intent);
    }

    public static void openStandings(Context context, String teamId) {
        Intent intent = new Intent(context, TeamProfileActivity.class);
        intent.putExtra(EXTRA_TEAM, teamId);
        intent.putExtra("initial_tab", 3);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        teamId = getIntent().getStringExtra(EXTRA_TEAM);
        if (teamId == null) { finish(); return; }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.surface_soft));
        root.setPadding(dp(16), dp(12), dp(16), dp(8));
        Button back = new Button(this);
        back.setText("‹   SPORTS DESK"); back.setAllCaps(false);
        back.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        back.setTextColor(ContextCompat.getColor(this, R.color.usiu_navy));
        back.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        root.addView(back, new LinearLayout.LayoutParams(-1, dp(40)));
        back.setOnClickListener(v -> finish());

        MaterialCardView hero = new MaterialCardView(this);
        hero.setRadius(dp(20)); hero.setCardBackgroundColor(ContextCompat.getColor(this, R.color.usiu_navy));
        hero.setStrokeWidth(dp(1)); hero.setStrokeColor(ContextCompat.getColor(this, R.color.usiu_gold));
        LinearLayout heroContent = new LinearLayout(this);
        heroContent.setGravity(Gravity.CENTER_VERTICAL);
        heroContent.setPadding(dp(18), dp(16), dp(16), dp(16));
        LinearLayout labels = new LinearLayout(this); labels.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = label("TEAM PROFILE", 9, R.color.usiu_gold_soft, true); labels.addView(eyebrow);
        TextView name = label("Loading team…", 23, R.color.white, true); name.setId(android.view.View.generateViewId()); labels.addView(name);
        TextView details = label("Campus competition", 12, R.color.welcome_text_muted, false); details.setId(android.view.View.generateViewId()); labels.addView(details);
        heroContent.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        followButton = new Button(this); followButton.setText("Follow"); followButton.setAllCaps(false);
        followButton.setEnabled(false); heroContent.addView(followButton, new LinearLayout.LayoutParams(-2, dp(46)));
        hero.addView(heroContent); root.addView(hero);
        hero.setTag(new TextView[]{name, details});

        TabLayout tabs = new TabLayout(this);
        tabs.setTabMode(TabLayout.MODE_SCROLLABLE);
        for (String title : new String[]{"Overview", "Fixtures", "Results", "Standings"}) tabs.addTab(tabs.newTab().setText(title));
        LinearLayout.LayoutParams tabsParams = new LinearLayout.LayoutParams(-1, -2);
        tabsParams.topMargin = dp(10); root.addView(tabs, tabsParams);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(8), 0, dp(20)); scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        followButton.setOnClickListener(v -> toggleFollow());
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { selectedTab = tab.getPosition(); render(); }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) { render(); }
        });
        selectedTab = Math.max(0, Math.min(3, getIntent().getIntExtra("initial_tab", 0)));
        if (selectedTab != 0) tabs.getTabAt(selectedTab).select();
        load((TextView[]) hero.getTag());
    }

    private void load(TextView[] labels) {
        ApiClient.get(this).getSportsTeam(teamId).enqueue(new Callback<SportsTeam>() {
            @Override public void onResponse(Call<SportsTeam> call, Response<SportsTeam> response) {
                if (!response.isSuccessful() || response.body() == null) { showError("Team profile unavailable."); return; }
                team = response.body();
                labels[0].setText(team.name);
                labels[1].setText(team.sport + "  ·  " + value(team.competition));
                render();
            }
            @Override public void onFailure(Call<SportsTeam> call, Throwable error) { showError("Couldn't load the team profile. Check your connection."); }
        });
        ApiClient.get(this).getTeamFixtures(teamId, "all").enqueue(new Callback<List<Fixture>>() {
            @Override public void onResponse(Call<List<Fixture>> call, Response<List<Fixture>> response) {
                if (response.isSuccessful() && response.body() != null) { fixtures.clear(); fixtures.addAll(response.body()); render(); }
            }
            @Override public void onFailure(Call<List<Fixture>> call, Throwable error) { showError("Couldn't load team fixtures."); }
        });
        ApiClient.get(this).getTeamStandings(teamId).enqueue(new Callback<JsonObject>() {
            @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> response) { if (response.isSuccessful()) { standings = response.body(); render(); } }
            @Override public void onFailure(Call<JsonObject> call, Throwable error) { }
        });
        ApiClient.get(this).getFavoriteTeams().enqueue(new Callback<List<SportsTeam>>() {
            @Override public void onResponse(Call<List<SportsTeam>> call, Response<List<SportsTeam>> response) {
                favoriteRequestReady = response.isSuccessful() && response.body() != null;
                if (favoriteRequestReady) for (SportsTeam item : response.body()) if (teamId.equals(item.teamId)) following = true;
                updateFollowButton();
            }
            @Override public void onFailure(Call<List<SportsTeam>> call, Throwable error) { favoriteRequestReady = false; updateFollowButton(); }
        });
    }

    private void render() {
        if (content == null || team == null) return;
        content.removeAllViews();
        List<Fixture> completed = new ArrayList<>(), upcoming = new ArrayList<>();
        int wins = 0, draws = 0, losses = 0;
        for (Fixture fixture : fixtures) {
            boolean done = "completed".equalsIgnoreCase(fixture.status)
                    || (fixture.scoreUsiu != null && fixture.scoreOpponent != null && fixture.endMillis < System.currentTimeMillis());
            if (!done) { upcoming.add(fixture); continue; }
            completed.add(fixture);
            Integer ours = teamId.equals(fixture.teamId) ? fixture.scoreUsiu : fixture.scoreOpponent;
            Integer theirs = teamId.equals(fixture.teamId) ? fixture.scoreOpponent : fixture.scoreUsiu;
            if (ours == null || theirs == null) continue;
            if (ours > theirs) wins++; else if (ours.equals(theirs)) draws++; else losses++;
        }
        Collections.sort(upcoming, (a, b) -> Long.compare(a.startMillis, b.startMillis));
        Collections.sort(completed, (a, b) -> Long.compare(b.startMillis, a.startMillis));

        if (selectedTab == 0) {
            addSection("Season record", "W  " + wins + "     D  " + draws + "     L  " + losses);
            addSection("Next fixture", upcoming.isEmpty() ? "No upcoming matches" : summary(upcoming.get(0)));
            addSection("Latest result", completed.isEmpty() ? "No completed results" : summary(completed.get(0)));
            if (!completed.isEmpty()) addForm(completed, 5);
        } else if (selectedTab == 1) {
            if (upcoming.isEmpty()) addSection("Upcoming fixtures", "No upcoming fixtures are scheduled.");
            for (Fixture fixture : upcoming) addFixture(fixture);
        } else if (selectedTab == 2) {
            if (completed.isEmpty()) addSection("Results", "Completed results will appear here.");
            for (Fixture fixture : completed) addFixture(fixture);
        } else {
            if (standings == null || !standings.has("standings")) {
                addSection("Standings", "No completed matches to calculate standings yet.");
            } else {
                if (standings.has("rules_label")) addSection("Table rules", standings.get("rules_label").getAsString());
                JsonArray rows = standings.getAsJsonArray("standings");
                for (int i = 0; i < rows.size(); i++) {
                    JsonObject row = rows.get(i).getAsJsonObject();
                    addSection(row.get("position").getAsString() + "   " + row.get("team").getAsString(),
                            row.get("played").getAsString() + " P   " + row.get("won").getAsString() + " W   "
                                    + row.get("drawn").getAsString() + " D   " + row.get("lost").getAsString() + " L   ·   "
                                    + row.get("points").getAsString() + " pts   ·   " + row.get("score_difference").getAsString() + " diff");
                }
            }
        }
    }

    private void addForm(List<Fixture> results, int limit) {
        StringBuilder form = new StringBuilder();
        for (int i = 0; i < Math.min(limit, results.size()); i++) {
            Fixture f = results.get(i);
            Integer ours = teamId.equals(f.teamId) ? f.scoreUsiu : f.scoreOpponent;
            Integer theirs = teamId.equals(f.teamId) ? f.scoreOpponent : f.scoreUsiu;
            if (ours == null || theirs == null) continue;
            form.append(ours > theirs ? "W  " : ours.equals(theirs) ? "D  " : "L  ");
        }
        if (form.length() > 0) addSection("Recent form", form.toString().trim());
    }

    private void addFixture(Fixture fixture) {
        String score = fixture.scoreUsiu == null || fixture.scoreOpponent == null ? "vs" : fixture.scoreUsiu + " - " + fixture.scoreOpponent;
        String date = fixture.startMillis > 0 ? dateFormat.format(new Date(fixture.startMillis)) : "Date TBC";
        addSection(value(fixture.teamName) + "  " + score + "  " + value(fixture.opponent), date + "  ·  " + value(fixture.venue));
    }

    private String summary(Fixture f) {
        String score = f.scoreUsiu == null || f.scoreOpponent == null ? "vs" : f.scoreUsiu + " - " + f.scoreOpponent;
        return value(f.teamName) + "  " + score + "  " + value(f.opponent) + "\n"
                + (f.startMillis > 0 ? dateFormat.format(new Date(f.startMillis)) : "Date TBC") + "  ·  " + value(f.venue);
    }

    private void addSection(String titleText, String bodyText) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(16)); card.setCardBackgroundColor(android.graphics.Color.WHITE);
        card.setStrokeWidth(dp(1)); card.setStrokeColor(ContextCompat.getColor(this, R.color.divider));
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView title = label(titleText, 13, R.color.usiu_navy, true); box.addView(title);
        TextView body = label(bodyText, 12, R.color.text_secondary, false);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2); bodyParams.topMargin = dp(5); box.addView(body, bodyParams);
        card.addView(box); LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(9); content.addView(card, params);
    }

    private void toggleFollow() {
        if (!favoriteRequestReady) { Toast.makeText(this, "Sign in to follow teams across devices.", Toast.LENGTH_SHORT).show(); return; }
        if (following) {
            ApiClient.get(this).unfollowTeam(teamId).enqueue(new Callback<Void>() {
                @Override public void onResponse(Call<Void> call, Response<Void> response) { if (response.isSuccessful()) { following = false; updateFollowButton(); } }
                @Override public void onFailure(Call<Void> call, Throwable error) { Toast.makeText(TeamProfileActivity.this, "Couldn't update your favourites.", Toast.LENGTH_SHORT).show(); }
            });
        } else {
            JsonObject body = new JsonObject(); body.addProperty("team_id", teamId);
            ApiClient.get(this).followTeam(body).enqueue(new Callback<SportsTeam>() {
                @Override public void onResponse(Call<SportsTeam> call, Response<SportsTeam> response) { if (response.isSuccessful()) { following = true; updateFollowButton(); } }
                @Override public void onFailure(Call<SportsTeam> call, Throwable error) { Toast.makeText(TeamProfileActivity.this, "Couldn't update your favourites.", Toast.LENGTH_SHORT).show(); }
            });
        }
    }

    private void updateFollowButton() { followButton.setText(following ? "Following" : "Follow"); followButton.setEnabled(favoriteRequestReady); }
    private void showError(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    private String value(String value) { return value == null || value.trim().isEmpty() ? "TBC" : value; }
    private TextView label(String text, int size, int color, boolean bold) { TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(ContextCompat.getColor(this, color)); if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD); return view; }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
