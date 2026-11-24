package com.shema.campuspulse;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.shema.campuspulse.data.CampusRepository;
import com.shema.campuspulse.data.Event;
import com.shema.campuspulse.data.TokenManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import android.view.LayoutInflater;

public class EventDetailActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "event_id";
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private final CampusRepository repo = CampusRepository.get();
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm a", Locale.getDefault());
    private Event event;
    private String shareText = "";

    private TextView seatsText, seatsBadge, fillValue, fillSub;
    private LinearProgressIndicator progress;
    private CircularProgressIndicator ring;
    private MaterialButton btnSave, btnRsvp;
    private View bottomBar;
    private boolean isRequestInFlight = false;

    @Override
    protected void onResume() {
        super.onResume();
        if (event != null) {
            Event fresh = repo.findEvent(event.id);
            if (fresh != null) {
                event = fresh;
                refreshSeats();
                refreshButtons();
                updateFullCapacitySection();
            }
        }
    }

    private void updateFullCapacitySection() {
        View fullCapSection = findViewById(R.id.full_capacity_section);
        if (fullCapSection == null) return;
        if (event.isFull() && !repo.hasRsvped(event.id)) {
            fullCapSection.setVisibility(View.VISIBLE);
            androidx.recyclerview.widget.RecyclerView recycler = findViewById(R.id.recommendations_recycler);
            android.widget.TextView emptyView = findViewById(R.id.recommendations_empty);
            java.util.List<com.shema.campuspulse.data.Event> recs = repo.getRecommendedEvents(event.id);
            if (recs.isEmpty()) {
                if (recycler != null) recycler.setVisibility(View.GONE);
                if (emptyView != null) emptyView.setVisibility(View.VISIBLE);
            } else {
                if (emptyView != null) emptyView.setVisibility(View.GONE);
                if (recycler != null) {
                    recycler.setVisibility(View.VISIBLE);
                    if (recycler.getLayoutManager() == null) {
                        recycler.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
                    }
                    com.shema.campuspulse.ui.EventAdapter adapter = new com.shema.campuspulse.ui.EventAdapter(recEvent -> {
                        Intent i = new Intent(this, EventDetailActivity.class);
                        i.putExtra(EXTRA_EVENT_ID, recEvent.id);
                        startActivity(i);
                        finish();
                    });
                    recycler.setAdapter(adapter);
                    adapter.submit(recs);
                }
            }
        } else {
            fullCapSection.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(true);
            insetsController.setAppearanceLightNavigationBars(true);
        }
        setContentView(R.layout.activity_event_detail);

        event = repo.findEvent(getIntent().getStringExtra(EXTRA_EVENT_ID));
        if (event == null) {
            finish();
            return;
        }

        View hero = findViewById(R.id.detail_hero);
        bottomBar = findViewById(R.id.detail_bottom);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.detail_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            if (hero != null) {
                hero.setPadding(dp(20), bars.top + dp(4), dp(20), dp(8));
            }
            if (bottomBar != null) {
                bottomBar.setPadding(dp(16), dp(12), dp(16), dp(12) + bars.bottom);
            }
            return insets;
        });

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_share).setOnClickListener(v -> {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, shareText);
            startActivity(Intent.createChooser(send, "Share event"));
        });

        seatsText = findViewById(R.id.detail_seats_text);
        seatsBadge = findViewById(R.id.detail_seats_badge);
        progress = findViewById(R.id.detail_progress);
        ring = findViewById(R.id.tile_ring);
        fillValue = findViewById(R.id.tile_fill_value);
        fillSub = findViewById(R.id.tile_fill_sub);
        btnSave = findViewById(R.id.btn_save);
        btnRsvp = findViewById(R.id.btn_rsvp);

        bindStaticInfo();
        bindPoster();
        bindTiles();
        buildSections();
        refreshSeats();
        refreshButtons();
        refreshMedia();
        updateFullCapacitySection();

        findViewById(R.id.btn_share_media).setOnClickListener(v -> {
            boolean isLoggedIn = TokenManager.getAccessToken(this) != null;
            if (!isLoggedIn) {
                showGuestPrompt("Sign in to Share Media",
                        "Sign in with your student account to share photos and event highlights with the campus community.");
                return;
            }

            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle("Share Event Memory");

            final android.widget.EditText input = new android.widget.EditText(this);
            input.setHint("Enter caption (e.g. Awesome moment at the stage!)");
            input.setPadding(dp(16), dp(16), dp(16), dp(16));
            builder.setView(input);

            builder.setPositiveButton("Share Photo", (dialog, which) -> {
                String caption = input.getText().toString().trim();
                if (!caption.isEmpty()) {
                    repo.addEventMedia(new com.shema.campuspulse.data.EventMedia(
                            "med_" + System.currentTimeMillis(),
                            event.id,
                            caption,
                            "Photo",
                            "res/drawable/ic_event.xml",
                            "You (Attendee/Organizer)",
                            System.currentTimeMillis()
                    ));
                    refreshMedia();
                    toast("Photo shared successfully!");
                }
            });

            builder.setNeutralButton("Share Video", (dialog, which) -> {
                String caption = input.getText().toString().trim();
                if (!caption.isEmpty()) {
                    repo.addEventMedia(new com.shema.campuspulse.data.EventMedia(
                            "med_" + System.currentTimeMillis(),
                            event.id,
                            caption,
                            "Video",
                            "res/drawable/ic_sports.xml",
                            "You (Attendee/Organizer)",
                            System.currentTimeMillis()
                    ));
                    refreshMedia();
                    toast("Video shared successfully!");
                }
            });

            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
            builder.show();
        });

        btnSave.setOnClickListener(v -> {
            boolean isLoggedIn = TokenManager.getAccessToken(this) != null;
            if (!isLoggedIn) {
                showGuestPrompt("Sign in to Save Events",
                        "Sign in with your university account to save events to My Week and receive schedule reminders.");
                return;
            }
            repo.toggleSavedApi(this, event.id, () -> {
                refreshButtons();
                bounce(v);
                toast(repo.isSaved(event.id) ? "Saved to My Week" : "Removed from My Week");
            }, err -> toast(err));
        });

        btnRsvp.setOnClickListener(v -> {
            if (isRequestInFlight) return;
            boolean isLoggedIn = TokenManager.getAccessToken(this) != null;
            if (!isLoggedIn) {
                showGuestPrompt("Sign in to RSVP",
                        "Sign in with your student account to reserve your seat and receive entry check-in details.");
                return;
            }
            if (repo.hasRsvped(event.id)) {
                isRequestInFlight = true;
                btnRsvp.setEnabled(false);
                android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                        .setTitle("Cancel your registration?")
                        .setMessage("Are you sure you no longer want to attend this event? Your seat will become available to another student.")
                        .setNegativeButton("Keep My RSVP", (d, which) -> {
                            isRequestInFlight = false;
                            btnRsvp.setEnabled(true);
                            d.dismiss();
                        })
                        .setPositiveButton("Yes, Cancel RSVP", (d, which) -> {
                            repo.cancelRsvpApi(this, event.id, () -> {
                                isRequestInFlight = false;
                                com.shema.campuspulse.data.EventReminderManager.cancelReminder(this, event.id);
                                refreshSeats();
                                refreshButtons();
                                bounce(v);
                                toast("Registration cancelled.");
                            }, err -> {
                                isRequestInFlight = false;
                                refreshButtons();
                                toast(err);
                            });
                        })
                        .create();
                dialog.setOnCancelListener(d -> {
                    isRequestInFlight = false;
                    btnRsvp.setEnabled(true);
                });
                dialog.show();
            } else {
                isRequestInFlight = true;
                btnRsvp.setEnabled(false);
                repo.rsvpApi(this, event.id, null, null, null, () -> {
                    isRequestInFlight = false;
                    refreshSeats();
                    refreshButtons();
                    bounce(v);
                    com.shema.campuspulse.data.EventReminderManager.cancelReminder(this, event.id);
                    if (com.shema.campuspulse.data.EventReminderManager.canScheduleNotifications(this)) {
                        boolean scheduled = com.shema.campuspulse.data.EventReminderManager.scheduleReminder(this, event);
                        if (scheduled) {
                            toast("Seat reserved and reminder scheduled.");
                        } else {
                            toast("Seat reserved successfully. (Event is starting soon)");
                        }
                    } else {
                        toast("Seat reserved successfully.");
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            boolean permGranted = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED;
                            if (!permGranted) {
                                androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
                            } else {
                                toast("Please enable app notifications in Android settings to receive reminders.");
                            }
                        } else {
                            toast("Please enable app notifications in Android settings to receive reminders.");
                        }
                    }
                }, err -> {
                    isRequestInFlight = false;
                    refreshButtons();
                    toast(err);
                });
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001 && grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            if (event != null && repo.hasRsvped(event.id)) {
                if (com.shema.campuspulse.data.EventReminderManager.canScheduleNotifications(this)) {
                    boolean scheduled = com.shema.campuspulse.data.EventReminderManager.scheduleReminder(this, event);
                    if (scheduled) {
                        toast("Reminder scheduled.");
                    }
                }
            }
        }
    }

    // ---------- Header, poster, tiles ----------

    private void bindStaticInfo() {
        Date start = new Date(event.startMillis);
        Date end = new Date(event.endMillis);
        SimpleDateFormat dateFmt = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault());
        String when = dateFmt.format(start);
        String time = timeFmt.format(start) + " - " + timeFmt.format(end);

        TextView typeView = findViewById(R.id.detail_type);
        if (typeView != null) {
            com.shema.campuspulse.ui.CategoryBadgeHelper.applyCategoryBadge(typeView, event.type);
        }

        TextView titleView = findViewById(R.id.detail_title);
        if (titleView != null) titleView.setText(event.title);

        TextView organizerView = findViewById(R.id.detail_organizer);
        if (organizerView != null) organizerView.setText("Hosted by " + event.organizer);

        TextView dateView = findViewById(R.id.detail_date);
        if (dateView != null) dateView.setText(when);

        TextView timeView = findViewById(R.id.detail_time);
        if (timeView != null) timeView.setText(time);

        TextView venueView = findViewById(R.id.detail_venue);
        if (venueView != null) venueView.setText(event.venue);

        View calBtn = findViewById(R.id.detail_calendar);
        if (calBtn != null) {
            calBtn.setOnClickListener(v -> {
                Intent i = new Intent(Intent.ACTION_INSERT)
                        .setData(CalendarContract.Events.CONTENT_URI)
                        .putExtra(CalendarContract.Events.TITLE, event.title)
                        .putExtra(CalendarContract.Events.EVENT_LOCATION, event.venue + ", USIU-Africa")
                        .putExtra(CalendarContract.Events.DESCRIPTION, event.description)
                        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.startMillis)
                        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endMillis);
                launch(i, "No calendar app found on this device.");
            });
        }

        shareText = event.title + "\n" + when + ", " + time + "\n" + event.venue
                + "\n\nShared from CampusPulse";
    }

    private void applyCategoryStyle(TextView typeView, String type) {
        com.shema.campuspulse.ui.CategoryBadgeHelper.applyCategoryBadge(typeView, type);
    }

    private void bindPoster() {
        View posterCard = findViewById(R.id.poster_card);
        View poster = findViewById(R.id.poster);
        android.widget.ImageView posterImage = findViewById(R.id.poster_image);

        if (posterCard != null) posterCard.setVisibility(View.VISIBLE);
        if (poster != null) poster.setVisibility(View.VISIBLE);
        if (posterImage != null) {
            posterImage.setVisibility(View.VISIBLE);
            com.shema.campuspulse.ui.EventMediaHelper.loadPoster(this, event, posterImage);
        }

        com.google.android.material.button.MaterialButton btnWatchVideo = findViewById(R.id.btn_watch_video);
        String videoUrl = event.getUsableVideoUrl();
        if (videoUrl != null && btnWatchVideo != null) {
            btnWatchVideo.setVisibility(View.VISIBLE);
            btnWatchVideo.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.parse(videoUrl), "video/*");
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl)));
                }
            });
        } else if (btnWatchVideo != null) {
            btnWatchVideo.setVisibility(View.GONE);
        }
    }

    private int[] posterColors(String type) {
        if ("Career".equals(type)) return new int[]{0xFF6B4300, 0xFFC98A00};
        if ("Social".equals(type)) return new int[]{0xFF3B1F6B, 0xFF7B3FA0};
        if ("Sports".equals(type)) return new int[]{0xFF0B5D3B, 0xFF1E8E5A};
        if ("Wellness".equals(type)) return new int[]{0xFF005F6B, 0xFF1AA6A6};
        return new int[]{0xFF002B49, 0xFF0B4A73}; // Academic and default
    }

    private void bindTiles() {
        long now = System.currentTimeMillis();
        String startsValue;
        if (now >= event.startMillis && now <= event.endMillis) {
            startsValue = "Live now";
        } else if (now > event.endMillis) {
            startsValue = "Ended";
        } else {
            long d = (long) Math.floor((event.startMillis - CampusRepository.weekStartMillis())
                    / (double) DAY_MS);
            startsValue = d <= 0 ? "Today" : (d == 1 ? "Tomorrow" : "In " + d + " days");
        }
        ((TextView) findViewById(R.id.tile_starts_value)).setText(startsValue);
        ((TextView) findViewById(R.id.tile_starts_sub)).setText(timeFmt.format(new Date(event.startMillis)));

        long mins = (event.endMillis - event.startMillis) / 60000L;
        ((TextView) findViewById(R.id.tile_dur_value)).setText(durationText(mins));
        ((TextView) findViewById(R.id.tile_dur_sub)).setText("ends " + timeFmt.format(new Date(event.endMillis)));

        TextView audValue = findViewById(R.id.tile_aud_value);
        TextView audSub = findViewById(R.id.tile_aud_sub);
        if (event.schoolCode == null) {
            audValue.setText("Everyone");
            audSub.setText("Open to all students");
        } else {
            audValue.setText(event.schoolCode);
            audSub.setText(event.major == null ? "All majors" : shortMajor(event.major));
        }
    }

    private String durationText(long mins) {
        long h = mins / 60, m = mins % 60;
        if (h == 0) return m + " min";
        if (m == 0) return h + (h == 1 ? " hr" : " hrs");
        return h + " hr " + m + " min";
    }

    private static String shortMajor(String major) {
        int o = major.indexOf('(');
        int c = major.indexOf(')');
        if (o > 0 && c > o) {
            String inside = major.substring(o + 1, c);
            if (inside.length() <= 6) return inside;
            return major.substring(0, o).trim();
        }
        return major;
    }

    // ---------- Sections built from the event's content ----------

    private void buildSections() {
        LinearLayout box = findViewById(R.id.sections);
        box.removeAllViews();

        // About, with Read more
        addHeader(box, "ABOUT THIS EVENT");
        final TextView about = new TextView(this);
        about.setText(event.details.isEmpty() ? event.description : event.details);
        about.setTextSize(15);
        about.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        about.setLineSpacing(0, 1.2f);
        about.setMaxLines(6);
        about.setEllipsize(TextUtils.TruncateAt.END);
        box.addView(about);

        final TextView toggle = new TextView(this);
        toggle.setText("Read more");
        toggle.setTextSize(14);
        toggle.setTypeface(toggle.getTypeface(), android.graphics.Typeface.BOLD);
        toggle.setTextColor(ContextCompat.getColor(this, R.color.usiu_navy));
        toggle.setGravity(Gravity.CENTER_VERTICAL);
        toggle.setMinHeight(dp(48));
        toggle.setClickable(true);
        toggle.setFocusable(true);
        box.addView(toggle);

        about.post(() -> {
            if (about.getLineCount() <= 6 && about.getEllipsize() != null
                    && about.getLayout() != null
                    && about.getLayout().getEllipsisCount(about.getLineCount() - 1) == 0) {
                toggle.setVisibility(View.GONE);
            }
        });
        final boolean[] open = {false};
        toggle.setOnClickListener(v -> {
            open[0] = !open[0];
            about.setMaxLines(open[0] ? Integer.MAX_VALUE : 6);
            toggle.setText(open[0] ? "Show less" : "Read more");
        });

        // What to expect
        if (event.highlights.length > 0) {
            addHeader(box, "WHAT TO EXPECT");
            for (String h : event.highlights) {
                View row = getLayoutInflater().inflate(R.layout.item_bullet, box, false);
                ((TextView) row.findViewById(R.id.bullet_text)).setText(h);
                box.addView(row);
            }
        }

        // Schedule timeline
        if (event.agenda.length > 0) {
            addHeader(box, "SCHEDULE");
            for (int i = 0; i < event.agenda.length; i++) {
                String[] parts = event.agenda[i].split("\\|", 2);
                long minutes = Long.parseLong(parts[0]);
                View row = getLayoutInflater().inflate(R.layout.item_agenda, box, false);
                ((TextView) row.findViewById(R.id.ag_time)).setText(
                        timeFmt.format(new Date(event.startMillis + minutes * 60000L)));
                ((TextView) row.findViewById(R.id.ag_title)).setText(parts.length > 1 ? parts[1] : "");
                if (i == event.agenda.length - 1) {
                    row.findViewById(R.id.ag_line).setVisibility(View.INVISIBLE);
                }
                box.addView(row);
            }
        }

        // Good to know
        if (!event.audience.isEmpty() || !event.bring.isEmpty()) {
            addHeader(box, "GOOD TO KNOW");
            addInfoRow(box, "FOR", event.audience);
            addInfoRow(box, "BRING", event.bring);
        }

        // Contact
        if (!event.contactEmail.isEmpty() || !event.contactPhone.isEmpty()) {
            addHeader(box, "CONTACT THE ORGANIZER");
            View c = getLayoutInflater().inflate(R.layout.item_contact, box, false);
            ((TextView) c.findViewById(R.id.ct_name)).setText(event.contactName);
            ((TextView) c.findViewById(R.id.ct_email)).setText(event.contactEmail);
            ((TextView) c.findViewById(R.id.ct_phone)).setText(event.contactPhone);
            c.findViewById(R.id.btn_email).setOnClickListener(v -> sendEmail());
            c.findViewById(R.id.btn_call).setOnClickListener(v -> dial());
            c.findViewById(R.id.btn_wa).setOnClickListener(v -> whatsApp());
            box.addView(c);

            View spacer = new View(this);
            box.addView(spacer, new LinearLayout.LayoutParams(1, dp(16)));
        }
    }

    private void addHeader(LinearLayout box, String title) {
        View h = getLayoutInflater().inflate(R.layout.item_header, box, false);
        ((TextView) h.findViewById(R.id.header_text)).setText(title);
        h.setPadding(h.getPaddingLeft(), dp(22), h.getPaddingRight(), dp(8));
        box.addView(h);
    }

    private void addInfoRow(LinearLayout box, String label, String value) {
        if (value == null || value.isEmpty()) return;
        View r = getLayoutInflater().inflate(R.layout.item_info_row, box, false);
        ((TextView) r.findViewById(R.id.row_label)).setText(label);
        ((TextView) r.findViewById(R.id.row_value)).setText(value);
        box.addView(r);
    }

    // ---------- Contact actions ----------

    private void sendEmail() {
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + event.contactEmail));
        i.putExtra(Intent.EXTRA_SUBJECT, "Question about " + event.title + " (CampusPulse)");
        launch(i, "No email app found on this device.");
    }

    private void dial() {
        String number = event.contactPhone.replace(" ", "");
        launch(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number)),
                "No phone app found on this device.");
    }

    private void whatsApp() {
        String digits = event.contactPhone.replaceAll("[^0-9]", "");
        String text = Uri.encode("Hi, I have a question about " + event.title + ".");
        launch(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + digits + "?text=" + text)),
                "Could not open WhatsApp.");
    }

    private void launch(Intent i, String errorMessage) {
        try {
            startActivity(i);
        } catch (android.content.ActivityNotFoundException e) {
            toast(errorMessage);
        }
    }

    // ---------- Seats and buttons ----------

    private void refreshSeats() {
        if (seatsText == null || progress == null) return;
        if (event.isUnlimited()) {
            seatsText.setText("Unlimited capacity");
            seatsBadge.setText("Unlimited");
            seatsBadge.setBackgroundResource(R.drawable.bg_badge_ok);
            progress.setMax(100);
            progress.setProgressCompat(0, true);
            if (fillValue != null) fillValue.setText("∞");
            if (fillSub != null) fillSub.setText("Unlimited seats");
            if (ring != null) {
                ring.setMax(100);
                ring.setProgressCompat(0, true);
            }
            int color = R.color.status_success;
            progress.setIndicatorColor(ContextCompat.getColor(this, color));
            if (ring != null) ring.setIndicatorColor(ContextCompat.getColor(this, color));
            return;
        }

        int total = event.totalSeats != null ? event.totalSeats : 100;
        int left = event.seatsLeft != null ? event.seatsLeft : total;
        int taken = total - left;
        seatsText.setText(left + " of " + total + " left");
        progress.setMax(total);
        progress.setProgressCompat(taken, true);

        int pct = Math.round(taken * 100f / total);
        if (fillValue != null) fillValue.setText(pct + "%");
        if (fillSub != null) fillSub.setText(taken + " of " + total + " taken");
        if (ring != null) {
            ring.setMax(100);
            ring.setProgressCompat(pct, true);
        }

        int color;
        if (event.isFull()) {
            seatsBadge.setText("Full");
            seatsBadge.setBackgroundResource(R.drawable.bg_badge_danger);
            color = R.color.status_danger;
        } else if (event.isFillingFast()) {
            seatsBadge.setText("Filling fast");
            seatsBadge.setBackgroundResource(R.drawable.bg_badge_warn);
            color = R.color.usiu_gold;
        } else {
            seatsBadge.setText("Plenty of space");
            seatsBadge.setBackgroundResource(R.drawable.bg_badge_ok);
            color = R.color.status_success;
        }
        progress.setIndicatorColor(ContextCompat.getColor(this, color));
        if (ring != null) ring.setIndicatorColor(ContextCompat.getColor(this, color));
    }

    private void refreshMedia() {
        LinearLayout mediaContainer = findViewById(R.id.media_container);
        mediaContainer.removeAllViews();
        List<com.shema.campuspulse.data.EventMedia> list = repo.getMediaForEvent(event.id);
        if (list.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No photos or videos shared yet. Be the first to share!");
            empty.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            empty.setTextSize(13.5f);
            mediaContainer.addView(empty);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (com.shema.campuspulse.data.EventMedia m : list) {
            View item = inflater.inflate(android.R.layout.simple_list_item_2, mediaContainer, false);
            TextView t1 = item.findViewById(android.R.id.text1);
            TextView t2 = item.findViewById(android.R.id.text2);
            t1.setText("[" + m.mediaType + "] " + m.caption);
            t1.setTextColor(ContextCompat.getColor(this, R.color.usiu_navy));
            t1.setTypeface(null, android.graphics.Typeface.BOLD);
            t2.setText("Shared by " + m.uploaderName);
            t2.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            item.setOnClickListener(v -> toast("Opened " + m.mediaType.toLowerCase() + ": " + m.caption));
            mediaContainer.addView(item);
        }
    }

    private void refreshButtons() {
        btnSave.setText(repo.isSaved(event.id) ? "Saved" : "Save");

        if (repo.hasRsvped(event.id)) {
            btnRsvp.setText("Cancel RSVP");
            btnRsvp.setEnabled(true);
            btnRsvp.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.status_danger)));
            btnRsvp.setTextColor(Color.WHITE);
        } else {
            btnRsvp.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.btn_gold_tint));
            btnRsvp.setTextColor(ContextCompat.getColorStateList(this, R.color.btn_text_gold));
            if (event.isFull()) {
                btnRsvp.setText("Event full");
                btnRsvp.setEnabled(false); // BR-03: RSVP closed, saving still allowed
            } else {
                btnRsvp.setText("RSVP");
                btnRsvp.setEnabled(true);
            }
        }
    }

    private void showGuestPrompt(String title, String message) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Sign In", (dialog, which) -> {
                    startActivity(new Intent(this, LoginActivity.class));
                })
                .setNegativeButton("Continue Exploring", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void bounce(View v) {
        v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(110)
                .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(110));
    }

    private void toast(String message) {
        Snackbar.make(findViewById(R.id.detail_root), message, Snackbar.LENGTH_SHORT)
                .setAnchorView(bottomBar)
                .show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
