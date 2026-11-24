package com.shema.campuspulse.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.shema.campuspulse.EventDetailActivity;
import com.shema.campuspulse.LoginActivity;
import com.shema.campuspulse.R;
import com.shema.campuspulse.SignupActivity;
import com.shema.campuspulse.data.CampusRepository;
import com.shema.campuspulse.data.Event;
import com.shema.campuspulse.data.TokenManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MyWeekFragment extends Fragment {

    private CampusRepository repository;

    private SwipeRefreshLayout swipeRefresh;
    private View guestView;
    private View errorView;
    private ProgressBar loadingView;
    private TextView errorMessage;

    private TabLayout tabs;
    private RecyclerView recycler;
    private View emptyView;
    private TextView emptyTitle;
    private TextView emptySubtitle;
    private MaterialButton btnEmptyBrowse;

    private RegistrationAdapter registeredAdapter;
    private RegistrationAdapter savedAdapter;

    private final List<Event> registeredEvents = new ArrayList<>();
    private final List<Event> savedEvents = new ArrayList<>();

    private boolean requestInProgress = false;
    private int currentTab = 0; // 0 = Confirmed, 1 = Saved

    public MyWeekFragment() {
        super(R.layout.fragment_my_week);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository = CampusRepository.get();

        swipeRefresh = view.findViewById(R.id.registrations_swipe_refresh);
        guestView = view.findViewById(R.id.registrations_guest);
        errorView = view.findViewById(R.id.registrations_error);
        loadingView = view.findViewById(R.id.registrations_loading);
        errorMessage = view.findViewById(R.id.registrations_error_message);

        tabs = view.findViewById(R.id.my_week_tabs);
        recycler = view.findViewById(R.id.my_week_recycler);
        emptyView = view.findViewById(R.id.view_my_week_empty);
        emptyTitle = view.findViewById(R.id.empty_title);
        emptySubtitle = view.findViewById(R.id.empty_subtitle);
        btnEmptyBrowse = view.findViewById(R.id.btn_empty_browse);

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));

        registeredAdapter = new RegistrationAdapter(true, new RegistrationAdapter.Listener() {
            @Override
            public void onEventClick(Event event) {
                openEvent(event);
            }

            @Override
            public void onActionClick(Event event) {
                confirmCancelRegistration(event);
            }
        });

        savedAdapter = new RegistrationAdapter(false, new RegistrationAdapter.Listener() {
            @Override
            public void onEventClick(Event event) {
                openEvent(event);
            }

            @Override
            public void onActionClick(Event event) {
                confirmRemoveSaved(event);
            }
        });

        recycler.setAdapter(registeredAdapter);

        // Setup tabs
        tabs.removeAllTabs();
        tabs.addTab(tabs.newTab().setText("Confirmed Passes"));
        tabs.addTab(tabs.newTab().setText("Saved Activities"));

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                updateListForCurrentTab();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) { }

            @Override
            public void onTabReselected(TabLayout.Tab tab) { }
        });

        btnEmptyBrowse.setOnClickListener(v -> {
            NavController nav = Navigation.findNavController(view);
            nav.navigate(R.id.eventsFragment);
        });

        view.findViewById(R.id.btn_registrations_login).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), LoginActivity.class)));

        view.findViewById(R.id.btn_registrations_signup).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), SignupActivity.class)));

        view.findViewById(R.id.btn_registrations_retry).setOnClickListener(v ->
                loadRegistrations());

        swipeRefresh.setOnRefreshListener(this::loadRegistrations);

        showGuestState();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (getView() == null) return;

        if (TokenManager.getAccessToken(requireContext()) == null) {
            showGuestState();
        } else {
            loadRegistrations();
        }
    }

    private void loadRegistrations() {
        if (!isAdded() || getView() == null || requestInProgress) return;

        if (TokenManager.getAccessToken(requireContext()) == null) {
            showGuestState();
            return;
        }

        requestInProgress = true;

        guestView.setVisibility(View.GONE);
        recycler.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
        errorView.setVisibility(View.GONE);
        loadingView.setVisibility(View.VISIBLE);

        swipeRefresh.setRefreshing(false);

        repository.fetchEventsFromApi(requireContext(), () -> {
            if (!isAdded() || getView() == null) return;

            requestInProgress = false;
            swipeRefresh.setRefreshing(false);
            loadingView.setVisibility(View.GONE);

            if (repository.hasEventLoadFailed()) {
                showErrorState("We couldn't load your registrations. Check your connection and try again.");
            } else {
                renderRegistrations();
            }
        });
    }

    private void renderRegistrations() {
        if (getView() == null) return;

        registeredEvents.clear();
        savedEvents.clear();

        for (Event event : repository.getEvents()) {
            if (repository.hasRsvped(event.id)) {
                registeredEvents.add(event);
            }
            if (repository.isSaved(event.id)) {
                savedEvents.add(event);
            }
        }

        Comparator<Event> byStartTime = Comparator.comparingLong(event -> event.startMillis);
        registeredEvents.sort(byStartTime);
        savedEvents.sort(byStartTime);

        registeredAdapter.submit(registeredEvents);
        savedAdapter.submit(savedEvents);

        // Update tab titles with counts
        TabLayout.Tab tab0 = tabs.getTabAt(0);
        if (tab0 != null) {
            tab0.setText("Confirmed (" + registeredEvents.size() + ")");
        }
        TabLayout.Tab tab1 = tabs.getTabAt(1);
        if (tab1 != null) {
            tab1.setText("Saved (" + savedEvents.size() + ")");
        }

        guestView.setVisibility(View.GONE);
        loadingView.setVisibility(View.GONE);
        errorView.setVisibility(View.GONE);

        updateListForCurrentTab();
    }

    private void updateListForCurrentTab() {
        if (getView() == null) return;

        if (currentTab == 0) {
            recycler.setAdapter(registeredAdapter);
            if (registeredEvents.isEmpty()) {
                recycler.setVisibility(View.GONE);
                emptyView.setVisibility(View.VISIBLE);
                emptyTitle.setText("No Confirmed Passes");
                emptySubtitle.setText("You haven't reserved any event seats yet. Discover upcoming campus workshops, talks, and sports fixtures.");
            } else {
                emptyView.setVisibility(View.GONE);
                recycler.setVisibility(View.VISIBLE);
            }
        } else {
            recycler.setAdapter(savedAdapter);
            if (savedEvents.isEmpty()) {
                recycler.setVisibility(View.GONE);
                emptyView.setVisibility(View.VISIBLE);
                emptyTitle.setText("No Saved Activities");
                emptySubtitle.setText("You haven't bookmarked any events yet. Save events to quickly track them throughout your semester.");
            } else {
                emptyView.setVisibility(View.GONE);
                recycler.setVisibility(View.VISIBLE);
            }
        }
    }

    private void openEvent(Event event) {
        if (event == null || !isAdded()) return;

        Intent intent = new Intent(requireContext(), EventDetailActivity.class);
        intent.putExtra(EventDetailActivity.EXTRA_EVENT_ID, event.id);
        startActivity(intent);
    }

    private void confirmCancelRegistration(Event event) {
        if (!isAdded()) return;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Cancel RSVP?")
                .setMessage("Are you sure you want to release your seat for " + event.title + "? Your place will be given to another student.")
                .setNegativeButton("Keep My Seat", (d, w) -> d.dismiss())
                .setPositiveButton("Release Seat", (dialog, which) -> {
                    if (!isAdded()) return;

                    repository.cancelRsvpApi(requireContext(), event.id,
                            () -> {
                                if (!isAdded() || getView() == null) return;
                                com.shema.campuspulse.data.EventReminderManager.cancelReminder(requireContext(), event.id);
                                Toast.makeText(requireContext(), "Registration cancelled.", Toast.LENGTH_SHORT).show();
                                renderRegistrations();
                            },
                            message -> {
                                if (!isAdded()) return;
                                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                            });
                })
                .show();
    }

    private void confirmRemoveSaved(Event event) {
        if (!isAdded()) return;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Remove Saved Event?")
                .setMessage("Remove " + event.title + " from your saved activities?")
                .setNegativeButton("Keep Saved", (d, w) -> d.dismiss())
                .setPositiveButton("Remove", (dialog, which) -> {
                    if (!isAdded()) return;

                    repository.toggleSavedApi(requireContext(), event.id,
                            () -> {
                                if (!isAdded() || getView() == null) return;
                                Toast.makeText(requireContext(), "Removed from saved events.", Toast.LENGTH_SHORT).show();
                                renderRegistrations();
                            },
                            message -> {
                                if (!isAdded()) return;
                                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                            });
                })
                .show();
    }

    private void showGuestState() {
        requestInProgress = false;

        if (guestView == null) return;

        guestView.setVisibility(View.VISIBLE);
        recycler.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
        loadingView.setVisibility(View.GONE);
        errorView.setVisibility(View.GONE);
        swipeRefresh.setRefreshing(false);
    }

    private void showErrorState(String message) {
        if (errorView == null) return;

        guestView.setVisibility(View.GONE);
        recycler.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
        loadingView.setVisibility(View.GONE);
        errorView.setVisibility(View.VISIBLE);
        errorMessage.setText(message);
    }

    @Override
    public void onDestroyView() {
        requestInProgress = false;

        swipeRefresh = null;
        guestView = null;
        errorView = null;
        loadingView = null;
        errorMessage = null;

        tabs = null;
        recycler = null;
        emptyView = null;
        emptyTitle = null;
        emptySubtitle = null;
        btnEmptyBrowse = null;

        registeredAdapter = null;
        savedAdapter = null;

        super.onDestroyView();
    }
}
