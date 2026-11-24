package com.shema.campuspulse.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.shema.campuspulse.R;
import com.shema.campuspulse.data.Fixture;
import com.shema.campuspulse.data.FixtureScorer;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SportsFixtureAdapter extends RecyclerView.Adapter<SportsFixtureAdapter.FixtureHolder> {

    public interface OnFixtureClickListener { void onFixtureClick(Fixture fixture); }

    private final List<Fixture> fixtures = new ArrayList<>();
    private final OnFixtureClickListener clickListener;

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("EEE, d MMM · h:mm a", Locale.getDefault());

    public SportsFixtureAdapter(OnFixtureClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public void submit(List<Fixture> items) {
        fixtures.clear();
        if (items != null) {
            fixtures.addAll(items);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FixtureHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sports_fixture, parent, false);
        return new FixtureHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FixtureHolder holder, int position) {
        Fixture fixture = fixtures.get(position);
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onFixtureClick(fixture);
        });

        holder.sport.setText(valueOr(fixture.sport, "SPORT").toUpperCase(Locale.getDefault()));
        holder.team.setText(valueOr(fixture.teamName, "USIU-Africa"));
        holder.opponent.setText(valueOr(fixture.opponent, "Opponent"));
        holder.homeAway.setText(fixture.homeGame ? "HOME" : "AWAY");
        holder.teamInitial.setText(initials(valueOr(fixture.teamName, "USIU")));
        holder.opponentInitial.setText(initials(valueOr(fixture.opponent, "OP")));

        holder.date.setText(fixture.startMillis > 0
                ? dateFormat.format(new Date(fixture.startMillis))
                : "Date to be announced");

        setOptionalText(holder.venue, fixture.venue);
        setOptionalText(holder.league, fixture.league);

        boolean hasScore = fixture.scoreUsiu != null && fixture.scoreOpponent != null;
        boolean completed = "completed".equalsIgnoreCase(fixture.status) || hasScore;
        if (completed && hasScore) {
            holder.score.setText(fixture.scoreUsiu + " - " + fixture.scoreOpponent);
            holder.score.setVisibility(View.VISIBLE);
            if (holder.vs != null) holder.vs.setVisibility(View.GONE);
            holder.status.setText("FULL TIME");
            holder.status.setBackgroundResource(R.drawable.bg_badge_ok);
            holder.status.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), R.color.usiu_navy));
        } else {
            holder.score.setVisibility(View.GONE);
            if (holder.vs != null) holder.vs.setVisibility(View.VISIBLE);
            holder.status.setText("UPCOMING");
            holder.status.setBackgroundResource(R.drawable.bg_badge_soft);
            holder.status.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), R.color.usiu_navy));
        }

        String scorerLine = scorerLine(fixture);
        if (scorerLine.isEmpty()) {
            holder.scorers.setVisibility(View.GONE);
        } else {
            holder.scorers.setText(scorerLine);
            holder.scorers.setVisibility(View.VISIBLE);
        }
    }

    private void setOptionalText(TextView view, String value) {
        if (value == null || value.trim().isEmpty()) {
            view.setVisibility(View.GONE);
        } else {
            view.setText(value);
            view.setVisibility(View.VISIBLE);
        }
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private String initials(String name) {
        String[] words = name.trim().split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty() || word.equalsIgnoreCase("of") || word.equalsIgnoreCase("the")) continue;
            result.append(Character.toUpperCase(word.charAt(0)));
            if (result.length() == 2) break;
        }
        return result.length() == 0 ? "CP" : result.toString();
    }

    private String scorerLine(Fixture fixture) {
        if (!"football".equalsIgnoreCase(fixture.sport) || fixture.scorers == null || fixture.scorers.isEmpty()) {
            return "";
        }
        StringBuilder line = new StringBuilder("Goals: ");
        for (FixtureScorer scorer : fixture.scorers) {
            if (scorer == null || scorer.playerName == null || scorer.playerName.trim().isEmpty()) continue;
            if (line.length() > 4) line.append("  ·  ");
            line.append(scorer.playerName.trim()).append(", ");
            if (scorer.displayMinute != null && !scorer.displayMinute.isEmpty()) {
                line.append(scorer.displayMinute);
            } else {
                line.append(scorer.minute).append('\'');
                if (scorer.addedMinute != null && scorer.addedMinute > 0) {
                    line.append('+').append(scorer.addedMinute);
                }
            }
        }
        return line.length() <= 4 ? "" : line.toString();
    }

    @Override
    public int getItemCount() {
        return fixtures.size();
    }

    static class FixtureHolder extends RecyclerView.ViewHolder {
        final TextView sport;
        final TextView status;
        final TextView team;
        final TextView homeAway;
        final TextView opponent;
        final TextView score;
        final TextView vs;
        final TextView date;
        final TextView venue;
        final TextView league;
        final TextView teamInitial;
        final TextView opponentInitial;
        final TextView scorers;

        FixtureHolder(@NonNull View itemView) {
            super(itemView);
            sport = itemView.findViewById(R.id.fixture_sport);
            status = itemView.findViewById(R.id.fixture_status);
            team = itemView.findViewById(R.id.fixture_team);
            homeAway = itemView.findViewById(R.id.fixture_home_away);
            opponent = itemView.findViewById(R.id.fixture_opponent);
            score = itemView.findViewById(R.id.fixture_score);
            vs = itemView.findViewById(R.id.fixture_vs);
            date = itemView.findViewById(R.id.fixture_date);
            venue = itemView.findViewById(R.id.fixture_venue);
            league = itemView.findViewById(R.id.fixture_league);
            teamInitial = itemView.findViewById(R.id.fixture_team_initial);
            opponentInitial = itemView.findViewById(R.id.fixture_opponent_initial);
            scorers = itemView.findViewById(R.id.fixture_scorers);
        }
    }
}
