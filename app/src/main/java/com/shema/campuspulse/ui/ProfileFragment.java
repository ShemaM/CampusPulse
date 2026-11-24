package com.shema.campuspulse.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.shema.campuspulse.LoginActivity;
import com.shema.campuspulse.R;
import com.shema.campuspulse.SignupActivity;
import com.shema.campuspulse.WelcomeActivity;
import com.shema.campuspulse.data.ApiClient;
import com.shema.campuspulse.data.CampusRepository;
import com.shema.campuspulse.data.TokenManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileFragment extends Fragment {

    private MaterialCardView guestCard, detailsCard, timetableCard, sportsCard;
    private LinearLayout loadingView, errorView;
    private MaterialButton logoutButton, manageTimetableButton, manageTeamsButton;
    private TextView usernameView, emailView, schoolView, majorView, errorMessageView, profileAvatarInitials;
    private TextView timetableStatusView, timetableBadgeView, timetableDescView;
    private TextView sportsBadgeView, sportsDescView;
    private ImageView timetableIconView;
    private ProfileTimetableAdapter timetableAdapter;
    private com.google.gson.JsonArray currentSchedules;
    private boolean requestInProgress = false;

    public ProfileFragment() {
        super(R.layout.fragment_profile);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        guestCard = view.findViewById(R.id.card_profile_guest);
        detailsCard = view.findViewById(R.id.card_profile_details);
        timetableCard = view.findViewById(R.id.card_timetable_defense);
        loadingView = view.findViewById(R.id.profile_loading);
        errorView = view.findViewById(R.id.profile_error);
        logoutButton = view.findViewById(R.id.btn_profile_logout);

        usernameView = view.findViewById(R.id.profile_username);
        emailView = view.findViewById(R.id.profile_email);
        schoolView = view.findViewById(R.id.profile_school);
        majorView = view.findViewById(R.id.profile_major);
        errorMessageView = view.findViewById(R.id.profile_error_message);
        profileAvatarInitials = view.findViewById(R.id.profile_avatar_initials);

        timetableStatusView = view.findViewById(R.id.timetable_defense_status);
        timetableBadgeView = view.findViewById(R.id.timetable_defense_badge);
        timetableDescView = view.findViewById(R.id.timetable_defense_desc);
        manageTimetableButton = view.findViewById(R.id.btn_manage_timetable);
        timetableIconView = view.findViewById(R.id.timetable_status_icon);
        RecyclerView timetableList = view.findViewById(R.id.profile_timetable_list);
        timetableList.setLayoutManager(new LinearLayoutManager(requireContext()));
        timetableAdapter = new ProfileTimetableAdapter(this::deleteSchedule);
        timetableList.setAdapter(timetableAdapter);

        sportsCard = view.findViewById(R.id.card_sports_preferences);
        sportsBadgeView = view.findViewById(R.id.profile_teams_badge);
        sportsDescView = view.findViewById(R.id.profile_teams_desc);
        manageTeamsButton = view.findViewById(R.id.btn_manage_teams);
        if (manageTeamsButton != null) {
            manageTeamsButton.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), MyTeamsActivity.class)));
        }

        if (manageTimetableButton != null) {
            manageTimetableButton.setOnClickListener(v -> showScheduleManagementDialog());
        }

        view.findViewById(R.id.btn_profile_login).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), LoginActivity.class)));

        view.findViewById(R.id.btn_profile_signup).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), SignupActivity.class)));

        view.findViewById(R.id.btn_profile_retry).setOnClickListener(v -> loadProfile());

        logoutButton.setOnClickListener(v -> {
            CampusRepository.get().clearUserState();
            TokenManager.clearTokens(requireContext());
            Intent intent = new Intent(requireContext(), WelcomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        showGuestState();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() == null) return;

        if (TokenManager.getAccessToken(requireContext()) == null) {
            showGuestState();
        } else {
            loadProfile();
            updateSportsPreferencesUI();
        }
    }

    private void loadProfile() {
        if (!isAdded() || getView() == null || requestInProgress) return;

        if (TokenManager.getAccessToken(requireContext()) == null) {
            showGuestState();
            return;
        }

        requestInProgress = true;
        guestCard.setVisibility(View.GONE);
        detailsCard.setVisibility(View.GONE);
        errorView.setVisibility(View.GONE);
        logoutButton.setVisibility(View.GONE);
        loadingView.setVisibility(View.VISIBLE);

        ApiClient.get(requireContext()).getProfile().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (!isAdded() || getView() == null) return;
                requestInProgress = false;

                if (response.isSuccessful() && response.body() != null) {
                    JsonObject profile = response.body();
                    usernameView.setText(readField(profile, "username"));
                    emailView.setText(readField(profile, "email"));
                    schoolView.setText(readField(profile, "school_code"));
                    majorView.setText(readField(profile, "major"));

                    String uname = readField(profile, "username");
                    String initial = "U";
                    if (uname != null && !uname.isEmpty() && !uname.equals("Not provided")) {
                        initial = uname.substring(0, Math.min(2, uname.length())).toUpperCase(java.util.Locale.getDefault());
                    }
                    if (profileAvatarInitials != null) {
                        profileAvatarInitials.setText(initial);
                    }

                    loadingView.setVisibility(View.GONE);
                    errorView.setVisibility(View.GONE);
                    detailsCard.setVisibility(View.VISIBLE);
                    logoutButton.setVisibility(View.VISIBLE);
                    if (sportsCard != null) {
                        sportsCard.setVisibility(View.VISIBLE);
                        updateSportsPreferencesUI();
                    }

                    currentSchedules = profile.has("schedules") && profile.get("schedules").isJsonArray()
                            ? profile.getAsJsonArray("schedules") : null;
                    if (timetableAdapter != null) timetableAdapter.submit(currentSchedules);
                    int scheduleCount = currentSchedules != null ? currentSchedules.size() : 0;
                    int courses = profile.has("total_courses") ? profile.get("total_courses").getAsInt() : scheduleCount;
                    int credits = profile.has("total_credits") ? profile.get("total_credits").getAsInt() : courses * 3;
                    int collisions = profile.has("total_collisions") ? profile.get("total_collisions").getAsInt() : 0;

                    if (timetableCard != null) {
                        timetableCard.setVisibility(View.VISIBLE);
                        if (scheduleCount > 0) {
                            if (timetableStatusView != null) {
                                timetableStatusView.setText("Collision Protection: Active");
                            }
                            if (timetableBadgeView != null) {
                                timetableBadgeView.setText(collisions > 0 ? collisions + " Collision Detected" : "Protection Active · All Clear");
                                timetableBadgeView.setBackgroundResource(collisions > 0 ? R.drawable.bg_badge_soft : R.drawable.bg_badge_ok);
                                timetableBadgeView.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), collisions > 0 ? R.color.gold_accent : R.color.status_success));
                            }
                            if (timetableDescView != null) {
                                timetableDescView.setText(collisions > 0 ? "Schedule Alert: You have an RSVP'd campus event that conflicts with your lecture hours." : "Timetable Safe: All lecture slots are conflict-free across your campus event registrations.");
                            }
                            if (manageTimetableButton != null) {
                                manageTimetableButton.setText("+ Add 3-Credit Course · " + courses + " Courses · " + credits + " Credit Hours");
                            }
                            if (timetableIconView != null) {
                                timetableIconView.setImageResource(R.drawable.ic_shield);
                                timetableIconView.setColorFilter(androidx.core.content.ContextCompat.getColor(requireContext(), collisions > 0 ? R.color.gold_accent : R.color.status_success));
                            }
                        } else {
                            if (timetableStatusView != null) {
                                timetableStatusView.setText("Weekly Schedule Required");
                            }
                            if (timetableBadgeView != null) {
                                timetableBadgeView.setText("Mandatory");
                                timetableBadgeView.setBackgroundResource(R.drawable.bg_badge_soft);
                                timetableBadgeView.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.gold_accent));
                            }
                            if (timetableDescView != null) {
                                timetableDescView.setText("Mandatory requirement: Add your weekly classes to activate real-time collision detection and prevent double-booking.");
                            }
                            if (manageTimetableButton != null) {
                                manageTimetableButton.setText("Add Weekly Class Schedule");
                            }
                            if (timetableIconView != null) {
                                timetableIconView.setImageResource(R.drawable.ic_calendar);
                                timetableIconView.setColorFilter(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.gold_accent));
                            }
                        }
                    }
                } else {
                    showErrorState("Your profile request could not be completed. Please try again.");
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                if (!isAdded() || getView() == null) return;
                requestInProgress = false;
                showErrorState("Unable to connect. Check your internet connection and try again.");
            }
        });
    }

    private String readField(JsonObject profile, String key) {
        if (profile == null || !profile.has(key) || profile.get(key).isJsonNull()) {
            return "Not provided";
        }

        String value = profile.get(key).getAsString().trim();
        return value.isEmpty() ? "Not provided" : value;
    }

    private void showGuestState() {
        requestInProgress = false;
        guestCard.setVisibility(View.VISIBLE);
        detailsCard.setVisibility(View.GONE);
        if (timetableCard != null) timetableCard.setVisibility(View.GONE);
        if (sportsCard != null) sportsCard.setVisibility(View.GONE);
        loadingView.setVisibility(View.GONE);
        errorView.setVisibility(View.GONE);
        logoutButton.setVisibility(View.GONE);
    }

    private void showErrorState(String message) {
        loadingView.setVisibility(View.GONE);
        detailsCard.setVisibility(View.GONE);
        if (timetableCard != null) timetableCard.setVisibility(View.GONE);
        if (sportsCard != null) sportsCard.setVisibility(View.GONE);
        logoutButton.setVisibility(View.VISIBLE);
        errorView.setVisibility(View.VISIBLE);
        errorMessageView.setText(message);
    }

    private void showScheduleManagementDialog() {
        if (!isAdded() || getContext() == null) return;

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext());
        builder.setTitle("Academic Class Timetable");

        StringBuilder sb = new StringBuilder();
        int count = currentSchedules != null ? currentSchedules.size() : 0;
        if (count > 0) {
            sb.append("Synchronized classes for collision defense:\n\n");
            for (int i = 0; i < currentSchedules.size(); i++) {
                JsonObject s = currentSchedules.get(i).getAsJsonObject();
                String course = s.has("course_name") ? s.get("course_name").getAsString() : "Class";
                String day = s.has("day_of_week") ? s.get("day_of_week").getAsString() : "";
                String start = s.has("start_time") ? s.get("start_time").getAsString() : "";
                String end = s.has("end_time") ? s.get("end_time").getAsString() : "";
                if (start.length() >= 5) start = start.substring(0, 5);
                if (end.length() >= 5) end = end.substring(0, 5);
                sb.append("• ").append(course).append(" (").append(day).append(" ").append(start).append("-").append(end).append(")\n");
            }
        } else {
            sb.append("You have not registered any weekly class slots yet.\n\nAdd your recurring class schedule to enable real-time conflict detection across all campus events.");
        }
        builder.setMessage(sb.toString().trim());

        builder.setPositiveButton("+ Add Class", (d, w) -> showAddClassDialog());
        builder.setNegativeButton("Close", (d, w) -> d.dismiss());
        builder.show();
    }

    private void showAddClassDialog() {
        if (!isAdded() || getContext() == null) return;

        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        final android.widget.EditText inputCourse = new android.widget.EditText(requireContext());
        inputCourse.setHint("Course Code (e.g. SWE 3010, APT 2040)");
        layout.addView(inputCourse);

        final android.widget.Spinner daySpinner = new android.widget.Spinner(requireContext());
        String[] days = {"Monday & Wednesday (MW)", "Tuesday & Thursday (TR)", "Friday (Single Block)", "Saturday (Single Block)"};
        daySpinner.setAdapter(new android.widget.ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, days));

        final android.widget.Spinner timeSpinner = new android.widget.Spinner(requireContext());
        final String[][] timeLabels = {
            {"07:00 AM - 08:40 AM","09:00 AM - 10:40 AM","11:00 AM - 12:40 PM","01:20 PM - 03:00 PM","03:30 PM - 05:10 PM","05:30 PM - 07:10 PM","07:20 PM - 09:00 PM"},
            {"07:00 AM - 08:40 AM","09:00 AM - 10:40 AM","11:00 AM - 12:40 PM","01:20 PM - 03:00 PM","03:30 PM - 05:10 PM","05:30 PM - 07:10 PM","07:20 PM - 09:00 PM"},
            {"08:00 AM - 11:00 AM","01:30 PM - 04:50 PM","05:40 PM - 09:00 PM"},
            {"09:00 AM - 12:00 PM","01:30 PM - 04:50 PM"}
        };
        final String[][][] timeValues = {
            {{"07:00:00","08:40:00"},{"09:00:00","10:40:00"},{"11:00:00","12:40:00"},{"13:20:00","15:00:00"},{"15:30:00","17:10:00"},{"17:30:00","19:10:00"},{"19:20:00","21:00:00"}},
            {{"07:00:00","08:40:00"},{"09:00:00","10:40:00"},{"11:00:00","12:40:00"},{"13:20:00","15:00:00"},{"15:30:00","17:10:00"},{"17:30:00","19:10:00"},{"19:20:00","21:00:00"}},
            {{"08:00:00","11:00:00"},{"13:30:00","16:50:00"},{"17:40:00","21:00:00"}},
            {{"09:00:00","12:00:00"},{"13:30:00","16:50:00"}}
        };
        java.util.function.IntConsumer updateTimes = p -> timeSpinner.setAdapter(new android.widget.ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, timeLabels[p]));
        updateTimes.accept(0);
        daySpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View v, int position, long id) { updateTimes.accept(position); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        layout.addView(timeSpinner);

        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Add Class Slot")
                .setView(layout)
                .setPositiveButton("Save Slot", (dialog, which) -> {
                    String course = inputCourse.getText().toString().trim();
                    if (course.isEmpty()) {
                        android.widget.Toast.makeText(requireContext(), "Please enter course code", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int pattern = daySpinner.getSelectedItemPosition();
                    String[] times = timeValues[pattern][timeSpinner.getSelectedItemPosition()];
                    String[] targetDays = pattern == 0 ? new String[]{"MON", "WED"} : pattern == 1 ? new String[]{"TUE", "THU"} : new String[]{pattern == 2 ? "FRI" : "SAT"};
                    for (String day : targetDays) {
                        JsonObject body = new JsonObject();
                        body.addProperty("course_name", course); body.addProperty("day_of_week", day);
                        body.addProperty("start_time", times[0]); body.addProperty("end_time", times[1]);
                        ApiClient.get(requireContext()).addSchedule(body).enqueue(new Callback<JsonObject>() {
                            @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                                if (response.isSuccessful()) { android.widget.Toast.makeText(requireContext(), "Course slot added.", android.widget.Toast.LENGTH_SHORT).show(); loadProfile(); }
                                else android.widget.Toast.makeText(requireContext(), "Could not save class slot. Choose an official time.", android.widget.Toast.LENGTH_SHORT).show();
                            }
                            @Override public void onFailure(Call<JsonObject> call, Throwable t) { android.widget.Toast.makeText(requireContext(), "Network error while saving class slot.", android.widget.Toast.LENGTH_SHORT).show(); }
                        });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteSchedule(long id) {
        if (id < 0 || !isAdded()) return;
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Remove course?")
                .setMessage("Removing this slot also removes its paired weekday slot.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (dialog, which) -> ApiClient.get(requireContext()).deleteSchedule(id).enqueue(new Callback<JsonObject>() {
                    @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                        if (response.isSuccessful()) loadProfile();
                        else if (isAdded()) android.widget.Toast.makeText(requireContext(), "Could not remove class.", android.widget.Toast.LENGTH_SHORT).show();
                    }
                    @Override public void onFailure(Call<JsonObject> call, Throwable error) {
                        if (isAdded()) android.widget.Toast.makeText(requireContext(), "Network error while removing class.", android.widget.Toast.LENGTH_SHORT).show();
                    }
                })).show();
    }

    private void updateSportsPreferencesUI() {
        if (!isAdded() || getContext() == null) return;
        java.util.Set<String> followedIds = CampusRepository.get().getFollowedTeamIds();
        int count = followedIds.size();
        if (sportsBadgeView != null) {
            sportsBadgeView.setText(count == 1 ? "1 Team" : count + " Teams");
        }
        if (sportsDescView != null) {
            if (count > 0) {
                StringBuilder sb = new StringBuilder("Following: ");
                int i = 0;
                for (String id : followedIds) {
                    com.shema.campuspulse.data.Team t = CampusRepository.get().findTeam(id);
                    if (t != null) {
                        if (i > 0) sb.append(", ");
                        sb.append(t.name);
                        i++;
                    }
                }
                if (i == 0) {
                    sb.append(count).append(" campus teams");
                }
                sb.append(". Matchday fixtures and results prioritized.");
                sportsDescView.setText(sb.toString());
            } else {
                sportsDescView.setText("Select university teams to follow for match fixtures, live cheer counts, and tournament results.");
            }
        }
    }

    @Override
    public void onDestroyView() {
        requestInProgress = false;
        guestCard = null;
        detailsCard = null;
        sportsCard = null;
        sportsBadgeView = null;
        sportsDescView = null;
        manageTeamsButton = null;
        timetableCard = null;
        timetableStatusView = null;
        timetableBadgeView = null;
        timetableDescView = null;
        manageTimetableButton = null;
        timetableIconView = null;
        loadingView = null;
        errorView = null;
        logoutButton = null;
        usernameView = null;
        emailView = null;
        schoolView = null;
        majorView = null;
        errorMessageView = null;
        profileAvatarInitials = null;
        super.onDestroyView();
    }
}
