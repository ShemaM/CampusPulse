package com.shema.campuspulse.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ArrayAdapter;
import android.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.tabs.TabLayout;
import com.shema.campuspulse.R;
import com.shema.campuspulse.data.CampusRepository;
import com.shema.campuspulse.data.Fixture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Calendar;

public class SportsFragment extends Fragment {

    private final CampusRepository repo = CampusRepository.get();

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recycler;
    private TabLayout tabs;
    private ChipGroup filterGroup;
    private FrameLayout contentFrame;
    private View stateContainer;
    private ProgressBar progress;
    private TextView stateTitle;
    private TextView stateMessage;
    private MaterialButton retryButton;

    private SportsFixtureAdapter adapter;

    private String selectedSport = "All";
    private int selectedTab = 0;
    private boolean loading = false;
    private String filterSearch = "";
    private String filterTeam = "";
    private String filterCompetition = "";
    private String filterVenue = "";
    private String filterHomeAway = "All venues";
    private String filterDateFrom = "";
    private String filterDateTo = "";

    public SportsFragment() {
        super(R.layout.fragment_sports);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        swipeRefresh = view.findViewById(R.id.sports_swipe_refresh);
        recycler = view.findViewById(R.id.sports_recycler);
        tabs = view.findViewById(R.id.sports_tabs);
        filterGroup = view.findViewById(R.id.sports_filter_group);
        stateContainer = view.findViewById(R.id.sports_state_container);
        progress = view.findViewById(R.id.sports_progress);
        stateTitle = view.findViewById(R.id.sports_state_title);
        stateMessage = view.findViewById(R.id.sports_state_message);
        retryButton = view.findViewById(R.id.sports_retry_button);

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new SportsFixtureAdapter(fixture -> {
            android.content.Intent intent = new android.content.Intent(requireContext(), FixtureDetailActivity.class);
            intent.putExtra(FixtureDetailActivity.EXTRA_FIXTURE_ID, fixture.id);
            startActivity(intent);
        });
        recycler.setAdapter(adapter);

        view.findViewById(R.id.sports_my_teams).setOnClickListener(v ->
                startActivity(new android.content.Intent(requireContext(), MyTeamsActivity.class)));
        view.findViewById(R.id.sports_advanced_filters).setOnClickListener(v -> showAdvancedFilters());

        tabs.addTab(tabs.newTab().setText("Upcoming"));
        tabs.addTab(tabs.newTab().setText("All fixtures"));
        tabs.addTab(tabs.newTab().setText("Results"));

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                selectedTab = tab.getPosition();
                renderFixtures();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                renderFixtures();
            }
        });

        swipeRefresh.setColorSchemeResources(R.color.usiu_navy, R.color.usiu_gold);
        swipeRefresh.setOnRefreshListener(this::loadFixtures);

        retryButton.setOnClickListener(v -> loadFixtures());

        filterGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                selectedSport = "All";
            } else {
                Chip selected = group.findViewById(checkedIds.get(0));
                if (selected != null) {
                    Object tag = selected.getTag();
                    selectedSport = tag == null ? "All" : tag.toString();
                }
            }
            renderFixtures();
        });

        showLoading();
        loadFixtures();
    }

    private void loadFixtures() {
        if (!isAdded()) return;

        loading = true;
        retryButton.setVisibility(View.GONE);
        stateContainer.setVisibility(View.VISIBLE);
        recycler.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        stateTitle.setText("Loading fixtures");
        stateMessage.setText("Getting the latest campus sports schedule.");
        swipeRefresh.setRefreshing(true);

        repo.fetchFixturesFromApi(requireContext(), () -> {
            if (!isAdded() || getView() == null) return;

            loading = false;
            swipeRefresh.setRefreshing(false);
            progress.setVisibility(View.GONE);

            if (repo.hasFixtureLoadFailed()) {
                adapter.submit(Collections.emptyList());
                stateContainer.setVisibility(View.VISIBLE);
                recycler.setVisibility(View.GONE);
                stateTitle.setText("Couldn't load fixtures");
                stateMessage.setText("Check your connection and try again.");
                retryButton.setVisibility(View.VISIBLE);
                return;
            }

            buildSportFilters();
            renderFixtures();
        });
    }

    private void buildSportFilters() {
        if (!isAdded()) return;

        Set<String> sports = new LinkedHashSet<>();
        for (Fixture fixture : repo.getFixtures()) {
            if (fixture.sport != null && !fixture.sport.trim().isEmpty()) {
                sports.add(fixture.sport.trim());
            }
        }

        filterGroup.removeAllViews();

        addSportChip("All");

        for (String sport : sports) {
            addSportChip(sport);
        }

        selectedSport = "All";
        if (filterGroup.getChildCount() > 0) {
            Chip first = (Chip) filterGroup.getChildAt(0);
            filterGroup.check(first.getId());
        }
    }

    private void addSportChip(String label) {
        Chip chip = new Chip(requireContext());
        chip.setId(View.generateViewId());
        chip.setText(label);
        chip.setTag(label);
        chip.setCheckable(true);
        chip.setClickable(true);
        chip.setEnsureMinTouchTargetSize(true);
        filterGroup.addView(chip);
    }

    private void renderFixtures() {
        if (!isAdded() || getView() == null || loading) return;

        if (repo.hasFixtureLoadFailed()) {
            return;
        }

        List<Fixture> filtered = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (Fixture fixture : repo.getFixtures()) {
            boolean completed = "completed".equalsIgnoreCase(fixture.status)
                    || (fixture.scoreUsiu != null && fixture.scoreOpponent != null && fixture.endMillis <= now);
            if (selectedTab == 2 && !completed) continue;
            if (selectedTab == 0 && (completed || fixture.startMillis <= now)) continue;

            if (!"All".equalsIgnoreCase(selectedSport)
                    && (fixture.sport == null
                    || !fixture.sport.equalsIgnoreCase(selectedSport))) {
                continue;
            }

            if (!matchesText(fixture.teamName, filterTeam) && !matchesText(fixture.opponent, filterTeam)) continue;
            if (!matchesText(fixture.league, filterCompetition)) continue;
            if (!matchesText(fixture.venue, filterVenue)) continue;
            if (!filterSearch.isEmpty() && !matchesText(fixture.teamName, filterSearch)
                    && !matchesText(fixture.opponent, filterSearch)) continue;
            if ("Home".equals(filterHomeAway) && !fixture.homeGame) continue;
            if ("Away".equals(filterHomeAway) && fixture.homeGame) continue;
            if (!withinDateRange(fixture.startMillis)) continue;

            filtered.add(fixture);
        }

        if (selectedTab == 2) {
            filtered.sort((a, b) -> Long.compare(b.startMillis, a.startMillis));
        } else {
            filtered.sort(Comparator.comparingLong(f -> f.startMillis));
        }

        adapter.submit(filtered);

        if (!filtered.isEmpty()) {
            stateContainer.setVisibility(View.GONE);
            recycler.setVisibility(View.VISIBLE);
            return;
        }

        recycler.setVisibility(View.GONE);
        stateContainer.setVisibility(View.VISIBLE);
        progress.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);

        if (repo.getFixtures().isEmpty()) {
            stateTitle.setText("No fixtures yet");
            stateMessage.setText("There are no campus sports fixtures available right now.");
        } else if (selectedTab == 2) {
            stateTitle.setText("No results yet");
            stateMessage.setText("Completed matches with recorded scores will appear here.");
        } else {
            stateTitle.setText("No upcoming matches");
            stateMessage.setText("There are no upcoming fixtures for this selection.");
        }
    }

    private void showAdvancedFilters() {
        LinearLayout form = new LinearLayout(requireContext());
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(0, 0, 0, 0);
        EditText search = filterInput("Search teams or opponents", filterSearch);
        EditText team = filterInput("Team name", filterTeam);
        EditText competition = filterInput("Competition contains…", filterCompetition);
        EditText venue = filterInput("Venue contains…", filterVenue);
        EditText from = filterInput("From date · YYYY-MM-DD", filterDateFrom);
        EditText to = filterInput("To date · YYYY-MM-DD", filterDateTo);
        Spinner homeAway = new Spinner(requireContext());
        ArrayAdapter<String> homeAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                Arrays.asList("All venues", "Home", "Away"));
        homeAway.setAdapter(homeAdapter);
        homeAway.setSelection(Math.max(0, homeAdapter.getPosition(filterHomeAway)));
        for (EditText input : Arrays.asList(search, team, competition, venue, from, to)) form.addView(input);
        form.addView(homeAway);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Filter the fixture list")
                .setView(form)
                .setPositiveButton("Apply filters", null)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Clear all", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String fromText = from.getText().toString().trim();
                String toText = to.getText().toString().trim();
                if (!validDate(fromText) || !validDate(toText)) {
                    android.widget.Toast.makeText(requireContext(), "Use dates in YYYY-MM-DD format.", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!fromText.isEmpty() && !toText.isEmpty() && parseDate(fromText) > parseDate(toText)) {
                    android.widget.Toast.makeText(requireContext(), "The end date must be after the start date.", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                filterSearch = search.getText().toString().trim();
                filterTeam = team.getText().toString().trim();
                filterCompetition = competition.getText().toString().trim();
                filterVenue = venue.getText().toString().trim();
                filterDateFrom = fromText;
                filterDateTo = toText;
                filterHomeAway = String.valueOf(homeAway.getSelectedItem());
                renderFixtures();
                dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                filterSearch = filterTeam = filterCompetition = filterVenue = filterDateFrom = filterDateTo = "";
                filterHomeAway = "All venues";
                selectedSport = "All";
                if (filterGroup.getChildCount() > 0) filterGroup.check(filterGroup.getChildAt(0).getId());
                tabs.getTabAt(0).select();
                renderFixtures();
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private EditText filterInput(String hint, String value) {
        EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setText(value);
        input.setHint(hint);
        input.setTextSize(14);
        input.setPadding(0, dp(8), 0, dp(8));
        return input;
    }

    private boolean validDate(String value) { return value.isEmpty() || parseDate(value) >= 0; }

    private long parseDate(String value) {
        if (value.isEmpty()) return 0;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setLenient(false);
        ParsePosition position = new ParsePosition(0);
        Date parsed = format.parse(value, position);
        return parsed != null && position.getIndex() == value.length() ? parsed.getTime() : -1;
    }

    private boolean withinDateRange(long timestamp) {
        if (!filterDateFrom.isEmpty()) {
            long from = parseDate(filterDateFrom);
            if (timestamp < from) return false;
        }
        if (!filterDateTo.isEmpty()) {
            long to = parseDate(filterDateTo) + 24L * 60 * 60 * 1000;
            if (timestamp >= to) return false;
        }
        return true;
    }

    private boolean matchesText(String value, String query) {
        return query == null || query.isEmpty() || (value != null && value.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)));
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void showLoading() {
        loading = true;
        stateContainer.setVisibility(View.VISIBLE);
        recycler.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        stateTitle.setText("Loading fixtures");
        stateMessage.setText("Getting the latest campus sports schedule.");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        swipeRefresh = null;
        recycler = null;
        tabs = null;
        filterGroup = null;
        stateContainer = null;
        progress = null;
        stateTitle = null;
        stateMessage = null;
        retryButton = null;
        adapter = null;
    }
}
