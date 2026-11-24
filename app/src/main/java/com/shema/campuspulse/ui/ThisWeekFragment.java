package com.shema.campuspulse.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.gson.JsonObject;
import com.shema.campuspulse.EventDetailActivity;
import com.shema.campuspulse.LoginActivity;
import com.shema.campuspulse.R;
import com.shema.campuspulse.SignupActivity;
import com.shema.campuspulse.data.AcademicCalendar;
import com.shema.campuspulse.data.ApiClient;
import com.shema.campuspulse.data.CampusRepository;
import com.shema.campuspulse.data.Event;
import com.shema.campuspulse.data.TokenManager;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ThisWeekFragment extends Fragment {

    private final CampusRepository repo = CampusRepository.get();
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm a", Locale.getDefault());
    private final SimpleDateFormat dayFmt = new SimpleDateFormat("EEE", Locale.getDefault());
    private final SimpleDateFormat dayNumFmt = new SimpleDateFormat("d", Locale.getDefault());

    private View sectionStudentHeader, sectionGuestHeader;
    private View cardGuestBannerSlim, cardGuestFooterCta;
    private TextView textHeaderSubtitle;

    private TextView textGreeting, textWeekProgress;
    private ProgressBar progressSemester;

    private MaterialCardView bannerNow;
    private TextView bannerTitle, bannerSub, bannerVenue;

    private LinearLayout containerThisWeekEvents;

    private Event liveEvent;

    public ThisWeekFragment() {
        super(R.layout.fragment_this_week);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        sectionStudentHeader = view.findViewById(R.id.section_student_header);
        sectionGuestHeader = view.findViewById(R.id.section_guest_header);
        cardGuestBannerSlim = view.findViewById(R.id.card_guest_banner_slim);
        cardGuestFooterCta = view.findViewById(R.id.card_guest_footer_cta);

        textHeaderSubtitle = view.findViewById(R.id.text_header_subtitle);

        textGreeting = view.findViewById(R.id.text_greeting);
        textWeekProgress = view.findViewById(R.id.text_week_progress);
        progressSemester = view.findViewById(R.id.progress_semester);
        if (progressSemester != null) {
            progressSemester.setMax(AcademicCalendar.TOTAL_WEEKS);
        }

        bannerNow = view.findViewById(R.id.banner_now);
        bannerTitle = view.findViewById(R.id.banner_title);
        bannerSub = view.findViewById(R.id.banner_sub);
        bannerVenue = view.findViewById(R.id.banner_venue);

        containerThisWeekEvents = view.findViewById(R.id.container_this_week_events);

        // Guest top inline banner sign-in action
        View btnGuestBannerLogin = view.findViewById(R.id.btn_guest_banner_login);
        if (btnGuestBannerLogin != null) {
            btnGuestBannerLogin.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), LoginActivity.class)));
        }

        // Guest footer action buttons
        MaterialButton btnGuestLogin = view.findViewById(R.id.btn_guest_login);
        if (btnGuestLogin != null) {
            btnGuestLogin.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), LoginActivity.class)));
        }

        MaterialButton btnGuestSignup = view.findViewById(R.id.btn_guest_signup);
        if (btnGuestSignup != null) {
            btnGuestSignup.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), SignupActivity.class)));
        }

        bannerNow.setOnClickListener(v -> {
            if (liveEvent != null) openEvent(liveEvent);
        });

        View btnBannerJoin = view.findViewById(R.id.btn_banner_join);
        if (btnBannerJoin != null) {
            btnBannerJoin.setOnClickListener(v -> {
                if (liveEvent != null) openEvent(liveEvent);
            });
        }

        View btnNotif = view.findViewById(R.id.btn_notifications);
        if (btnNotif != null) {
            btnNotif.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), com.shema.campuspulse.NotificationsActivity.class)));
        }

        View btnSeeAll = view.findViewById(R.id.btn_see_all);
        if (btnSeeAll != null) {
            btnSeeAll.setOnClickListener(v -> navigateToCategory(null));
        }

        // Category discovery pills
        setupCategoryPill(view, R.id.pill_cat_all, null);
        setupCategoryPill(view, R.id.pill_cat_academic, "Academic");
        setupCategoryPill(view, R.id.pill_cat_career, "Career");
        setupCategoryPill(view, R.id.pill_cat_social, "Social");
        setupCategoryPill(view, R.id.pill_cat_sports, "Sports");
        setupCategoryPill(view, R.id.pill_cat_wellness, "Wellness");
    }

    private void setupCategoryPill(View root, int viewId, String categoryFilter) {
        View pill = root.findViewById(viewId);
        if (pill != null) {
            pill.setOnClickListener(v -> navigateToCategory(categoryFilter));
        }
    }

    private void navigateToCategory(String categoryFilter) {
        if (!isAdded() || getView() == null) return;
        NavController nav = Navigation.findNavController(getView());
        if (categoryFilter != null) {
            Bundle args = new Bundle();
            args.putString("filter_type", categoryFilter);
            nav.navigate(R.id.eventsFragment, args);
        } else {
            nav.navigate(R.id.eventsFragment);
        }
    }

    private void openEvent(Event e) {
        Intent i = new Intent(requireContext(), EventDetailActivity.class);
        i.putExtra(EventDetailActivity.EXTRA_EVENT_ID, e.id);
        startActivity(i);
    }

    @Override
    public void onResume() {
        super.onResume();

        boolean isGuest = TokenManager.getAccessToken(requireContext()) == null;
        if (isGuest) {
            if (sectionStudentHeader != null) sectionStudentHeader.setVisibility(View.GONE);
            if (sectionGuestHeader != null) sectionGuestHeader.setVisibility(View.VISIBLE);
            if (cardGuestFooterCta != null) cardGuestFooterCta.setVisibility(View.VISIBLE);
            if (textHeaderSubtitle != null) textHeaderSubtitle.setText("Campus Explorer");
        } else {
            if (sectionStudentHeader != null) sectionStudentHeader.setVisibility(View.VISIBLE);
            if (sectionGuestHeader != null) sectionGuestHeader.setVisibility(View.GONE);
            if (cardGuestFooterCta != null) cardGuestFooterCta.setVisibility(View.GONE);
            if (textHeaderSubtitle != null) textHeaderSubtitle.setText("This Week");

            int weekNum = AcademicCalendar.getCurrentWeekNumber();
            if (textWeekProgress != null) {
                textWeekProgress.setText(AcademicCalendar.getWeekProgressText(weekNum));
            }
            if (progressSemester != null) {
                progressSemester.setProgress(weekNum);
            }
            loadGreeting(weekNum);
        }

        refreshBanner();
        renderThisWeekEvents();

        repo.fetchEventsFromApi(requireContext(), () -> {
            refreshBanner();
            renderThisWeekEvents();
        });
    }

    private void loadGreeting(int weekNum) {
        if (TokenManager.getAccessToken(requireContext()) == null) {
            setGreetingText("Scholar", "US");
            return;
        }
        ApiClient.get(requireContext()).getProfile().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null) {
                    JsonObject profile = response.body();
                    String name = "Scholar";
                    String initials = "U";

                    String firstName = profile.has("first_name") && !profile.get("first_name").isJsonNull()
                            ? profile.get("first_name").getAsString().trim() : null;
                    String lastName = profile.has("last_name") && !profile.get("last_name").isJsonNull()
                            ? profile.get("last_name").getAsString().trim() : null;
                    String username = profile.has("username") && !profile.get("username").isJsonNull()
                            ? profile.get("username").getAsString().trim() : null;

                    if (firstName != null && !firstName.isEmpty()) {
                        name = firstName;
                        initials = firstName.substring(0, 1).toUpperCase(Locale.getDefault());
                        if (lastName != null && !lastName.isEmpty()) {
                            initials += lastName.substring(0, 1).toUpperCase(Locale.getDefault());
                        }
                    } else if (username != null && !username.isEmpty()) {
                        name = username;
                        initials = username.substring(0, Math.min(2, username.length())).toUpperCase(Locale.getDefault());
                    }

                    setGreetingText(name, initials);
                } else {
                    setGreetingText("Scholar", "US");
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                if (isAdded()) {
                    setGreetingText("Scholar", "US");
                }
            }
        });
    }

    private void setGreetingText(String name, String initials) {
        Calendar c = Calendar.getInstance();
        int hour = c.get(Calendar.HOUR_OF_DAY);
        String timeGreeting = "Good morning";
        if (hour >= 12 && hour < 17) {
            timeGreeting = "Good afternoon";
        } else if (hour >= 17) {
            timeGreeting = "Good evening";
        }
        if (textGreeting != null) {
            textGreeting.setText(timeGreeting + ", " + name);
        }
    }

    private void refreshBanner() {
        liveEvent = repo.getHappeningNow();
        if (liveEvent == null) {
            bannerNow.setVisibility(View.GONE);
            return;
        }
        String until = timeFmt.format(new Date(liveEvent.endMillis));
        bannerNow.setVisibility(View.VISIBLE);
        bannerTitle.setText(liveEvent.title);
        bannerSub.setText("Happening right now until " + until);
        if (bannerVenue != null && liveEvent.venue != null) {
            bannerVenue.setText(liveEvent.venue);
        }
    }

    private void renderThisWeekEvents() {
        if (!isAdded() || containerThisWeekEvents == null) return;
        containerThisWeekEvents.removeAllViews();

        long now = System.currentTimeMillis();
        List<Event> allEvents = repo.getEvents();
        LayoutInflater inflater = LayoutInflater.from(requireContext());

        int count = 0;
        for (Event e : allEvents) {
            // Skip the live event if it's already shown in the banner
            if (liveEvent != null && e.id.equals(liveEvent.id)) continue;
            if (e.endMillis < now) continue;

            View row = inflater.inflate(R.layout.item_this_week_event, containerThisWeekEvents, false);

            ImageView evImage = row.findViewById(R.id.row_ev_image);
            TextView evDay = row.findViewById(R.id.row_ev_day);
            TextView evNum = row.findViewById(R.id.row_ev_num);
            TextView evType = row.findViewById(R.id.row_ev_type);
            TextView evSeats = row.findViewById(R.id.row_ev_seats);
            TextView evTime = row.findViewById(R.id.row_ev_time);
            TextView evTitle = row.findViewById(R.id.row_ev_title);
            TextView evVenue = row.findViewById(R.id.row_ev_venue);

            if (evImage != null) {
                EventMediaHelper.loadPoster(requireContext(), e, evImage);
            }

            Date startDate = new Date(e.startMillis);
            Date endDate = new Date(e.endMillis);
            evDay.setText(dayFmt.format(startDate).toUpperCase(Locale.getDefault()));
            evNum.setText(dayNumFmt.format(startDate));

            CategoryBadgeHelper.applyCategoryBadge(evType, e.type);

            if (evSeats != null) {
                if (e.isFull()) {
                    evSeats.setText("Full");
                    evSeats.setBackgroundResource(R.drawable.bg_badge_danger);
                    evSeats.setTextColor(requireContext().getColor(R.color.status_danger));
                    evSeats.setVisibility(View.VISIBLE);
                } else if (e.isFillingFast()) {
                    evSeats.setText("Filling Fast");
                    evSeats.setBackgroundResource(R.drawable.bg_badge_warn);
                    evSeats.setTextColor(requireContext().getColor(R.color.usiu_navy));
                    evSeats.setVisibility(View.VISIBLE);
                } else {
                    evSeats.setVisibility(View.GONE);
                }
            }

            evTime.setText(timeFmt.format(startDate) + " - " + timeFmt.format(endDate));
            evTitle.setText(e.title);
            evVenue.setText(e.venue != null ? e.venue : "USIU-Africa Campus");

            row.setOnClickListener(v -> openEvent(e));
            containerThisWeekEvents.addView(row);

            count++;
            if (count >= 5) break; // Curated selection of 5 events for the week
        }

        if (count == 0) {
            TextView empty = new TextView(requireContext());
            empty.setText("No further scheduled events this week.");
            empty.setTextColor(requireContext().getColor(R.color.text_secondary));
            empty.setTextSize(13);
            empty.setPadding(0, 16, 0, 16);
            containerThisWeekEvents.addView(empty);
        }
    }
}
