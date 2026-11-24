package com.shema.campuspulse.ui;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;
import com.shema.campuspulse.R;
import com.shema.campuspulse.data.ApiClient;
import com.shema.campuspulse.data.Fixture;
import com.shema.campuspulse.data.FixtureScorer;
import com.shema.campuspulse.data.SportsTeam;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FixtureDetailActivity extends AppCompatActivity {
    public static final String EXTRA_FIXTURE_ID = "fixture_id";
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, d MMM yyyy · h:mm a", Locale.getDefault());
    private TextView title, sport, score, date, venue, competition, status, scorers;
    private Button teamButton, opponentButton, standingsButton;
    private Fixture fixture;
    private String opponentTeamId;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String fixtureId = getIntent().getStringExtra(EXTRA_FIXTURE_ID);
        if (fixtureId == null) { finish(); return; }

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(14), dp(18), dp(28));
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.surface_soft));
        scroll.addView(root);

        Button back = new Button(this); back.setText("‹   MATCHDAY"); back.setAllCaps(false);
        back.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); back.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        back.setTextColor(ContextCompat.getColor(this, R.color.usiu_navy)); root.addView(back, new LinearLayout.LayoutParams(-1, dp(42)));
        back.setOnClickListener(v -> finish());

        MaterialCardView hero = new MaterialCardView(this); hero.setRadius(dp(22));
        hero.setCardBackgroundColor(ContextCompat.getColor(this, R.color.usiu_navy));
        hero.setStrokeColor(ContextCompat.getColor(this, R.color.usiu_gold)); hero.setStrokeWidth(dp(1));
        LinearLayout heroContent = new LinearLayout(this); heroContent.setOrientation(LinearLayout.VERTICAL); heroContent.setPadding(dp(18), dp(18), dp(18), dp(18));
        sport = text("SPORT", 10, R.color.usiu_gold_soft, true); heroContent.addView(sport);
        title = text("Match details", 23, R.color.white, true); LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2); titleParams.topMargin = dp(9); heroContent.addView(title, titleParams);
        score = text("vs", 28, R.color.white, true); score.setGravity(Gravity.CENTER); LinearLayout.LayoutParams scoreParams = new LinearLayout.LayoutParams(-1, -2); scoreParams.topMargin = dp(4); heroContent.addView(score, scoreParams);
        status = text("UPCOMING", 10, R.color.usiu_gold_soft, true); status.setGravity(Gravity.CENTER); heroContent.addView(status);
        scorers = text("", 12, R.color.usiu_gold_soft, true); scorers.setGravity(Gravity.CENTER); heroContent.addView(scorers);
        hero.addView(heroContent); root.addView(hero);

        date = addDetail(root, "KICKOFF"); venue = addDetail(root, "VENUE"); competition = addDetail(root, "COMPETITION");
        teamButton = actionButton(root, "View home team"); opponentButton = actionButton(root, "View opponent"); standingsButton = actionButton(root, "View league table");
        teamButton.setOnClickListener(v -> { if (fixture != null) TeamProfileActivity.open(this, fixture.teamId); });
        opponentButton.setOnClickListener(v -> { if (opponentTeamId != null) TeamProfileActivity.open(this, opponentTeamId); });
        standingsButton.setOnClickListener(v -> { if (fixture != null) TeamProfileActivity.openStandings(this, fixture.teamId); });
        setContentView(scroll);

        ApiClient.get(this).getFixture(fixtureId).enqueue(new Callback<Fixture>() {
            @Override public void onResponse(Call<Fixture> call, Response<Fixture> response) {
                if (!response.isSuccessful() || response.body() == null) { Toast.makeText(FixtureDetailActivity.this, "Fixture details are unavailable.", Toast.LENGTH_SHORT).show(); return; }
                fixture = response.body(); render(); resolveOpponentTeam();
            }
            @Override public void onFailure(Call<Fixture> call, Throwable error) { Toast.makeText(FixtureDetailActivity.this, "Couldn't load match details.", Toast.LENGTH_SHORT).show(); }
        });
    }

    private void render() {
        sport.setText((fixture.sport == null ? "SPORT" : fixture.sport).toUpperCase(Locale.getDefault()));
        title.setText(value(fixture.teamName) + "  v  " + value(fixture.opponent));
        boolean complete = "completed".equalsIgnoreCase(fixture.status) && fixture.scoreUsiu != null && fixture.scoreOpponent != null;
        score.setText(complete ? fixture.scoreUsiu + " - " + fixture.scoreOpponent : "VS");
        status.setText(complete ? "FULL TIME" : "UPCOMING");
        date.setText(fixture.startMillis > 0 ? dateFormat.format(new Date(fixture.startMillis)) : "Date to be announced");
        venue.setText(value(fixture.venue));
        competition.setText(value(fixture.league) + (fixture.isDemo ? "  ·  DEMO DATA" : ""));
        teamButton.setText((fixture.homeGame ? "HOME  ·  " : "AWAY  ·  ") + value(fixture.teamName));
        opponentButton.setText("OPPONENT  ·  " + value(fixture.opponent));
        StringBuilder events = new StringBuilder();
        if ("Football".equalsIgnoreCase(fixture.sport) && fixture.scorers != null) {
            for (FixtureScorer item : fixture.scorers) {
                if (item == null || item.playerName == null) continue;
                if (events.length() > 0) events.append("   ·   ");
                events.append(item.playerName).append(", ").append(item.displayMinute == null ? item.minute + "'" : item.displayMinute);
            }
        }
        if (events.length() == 0) { scorers.setVisibility(android.view.View.GONE); }
        else { scorers.setText("SCORERS   ·   " + events); scorers.setVisibility(android.view.View.VISIBLE); }
    }

    private void resolveOpponentTeam() {
        ApiClient.get(this).getSportsTeams().enqueue(new Callback<List<SportsTeam>>() {
            @Override public void onResponse(Call<List<SportsTeam>> call, Response<List<SportsTeam>> response) {
                if (!response.isSuccessful() || response.body() == null) return;
                for (SportsTeam team : response.body()) {
                    if (team.sport != null && team.name != null && team.sport.equalsIgnoreCase(fixture.sport)
                            && team.name.equalsIgnoreCase(fixture.opponent)) { opponentTeamId = team.teamId; break; }
                }
                opponentButton.setEnabled(opponentTeamId != null);
            }
            @Override public void onFailure(Call<List<SportsTeam>> call, Throwable error) { opponentButton.setEnabled(false); }
        });
    }

    private TextView addDetail(LinearLayout root, String label) {
        MaterialCardView card = new MaterialCardView(this); card.setRadius(dp(15)); card.setCardBackgroundColor(android.graphics.Color.WHITE);
        card.setStrokeWidth(dp(1)); card.setStrokeColor(ContextCompat.getColor(this, R.color.divider));
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(14), dp(11), dp(14), dp(11));
        TextView heading = text(label, 9, R.color.gold_accent, true); box.addView(heading);
        TextView body = text("Loading…", 14, R.color.text_primary, false); LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2); bodyParams.topMargin = dp(3); box.addView(body, bodyParams);
        card.addView(box); LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(9); root.addView(card, params); return body;
    }

    private Button actionButton(LinearLayout root, String label) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false); button.setTextColor(ContextCompat.getColor(this, R.color.usiu_navy));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48)); params.topMargin = dp(8); root.addView(button, params); return button;
    }
    private TextView text(String value, int size, int color, boolean bold) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(ContextCompat.getColor(this, color)); if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD); return view; }
    private String value(String value) { return value == null || value.trim().isEmpty() ? "TBC" : value; }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
