package com.shema.campuspulse.ui;

import android.content.Intent;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.shema.campuspulse.LoginActivity;
import com.shema.campuspulse.R;
import com.shema.campuspulse.data.CampusRepository;
import com.shema.campuspulse.data.Event;
import com.shema.campuspulse.data.TokenManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EventAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnEventClick {
        void onEventClick(Event event);
    }

    private static final int TYPE_EVENT = 0;
    private static final int TYPE_HEADER = 1;

    private final List<Object> rows = new ArrayList<>();
    private final OnEventClick listener;
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm a", Locale.getDefault());
    private final SimpleDateFormat longDayFmt = new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault());

    public EventAdapter(OnEventClick listener) {
        this.listener = listener;
    }

    public void submit(List<Event> events) {
        rows.clear();
        rows.addAll(events);
        notifyDataSetChanged();
    }

    public void submitGrouped(List<Event> events) {
        rows.clear();
        String lastDay = "";
        for (Event e : events) {
            String day = longDayFmt.format(new Date(e.startMillis));
            if (!day.equals(lastDay)) {
                rows.add(day);
                lastDay = day;
            }
            rows.add(e);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position) instanceof String ? TYPE_HEADER : TYPE_EVENT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(inf.inflate(R.layout.item_header, parent, false));
        }
        return new EventHolder(inf.inflate(R.layout.item_event, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).title.setText((String) rows.get(position));
            return;
        }

        EventHolder h = (EventHolder) holder;
        Event e = (Event) rows.get(position);

        EventMediaHelper.loadPoster(h.itemView.getContext(), e, h.image);

        h.title.setText(e.title);
        h.venue.setText(e.venue);
        CategoryBadgeHelper.applyCategoryBadge(h.type, e.type);

        Date start = new Date(e.startMillis);
        Date end = new Date(e.endMillis);
        String range = timeFmt.format(start) + " - " + timeFmt.format(end);
        h.time.setText(range);

        SimpleDateFormat dayNumFmt = new SimpleDateFormat("d", Locale.getDefault());
        SimpleDateFormat monthFmt = new SimpleDateFormat("MMM", Locale.getDefault());
        h.dayNum.setText(dayNumFmt.format(start));
        h.month.setText(monthFmt.format(start).toUpperCase(Locale.getDefault()));

        String seatsText;
        if (e.isFull()) {
            seatsText = "Full";
            h.seats.setBackgroundResource(R.drawable.bg_badge_danger);
            h.seats.setTextColor(androidx.core.content.ContextCompat.getColor(h.itemView.getContext(), R.color.status_danger));
        } else if (e.isFillingFast() || (e.seatsLeft != null && e.totalSeats != null && e.seatsLeft < (e.totalSeats * 0.4))) {
            seatsText = "Filling Fast";
            h.seats.setBackgroundResource(R.drawable.bg_badge_warn);
            h.seats.setTextColor(androidx.core.content.ContextCompat.getColor(h.itemView.getContext(), R.color.usiu_navy));
        } else {
            seatsText = "RSVP Open";
            h.seats.setBackgroundResource(R.drawable.bg_badge_ok);
            h.seats.setTextColor(androidx.core.content.ContextCompat.getColor(h.itemView.getContext(), R.color.status_success));
        }
        h.seats.setText(seatsText);

        // AC-03: one clear summary sentence for TalkBack
        String summary = e.title + ". " + longDayFmt.format(start) + ", " + range
                + ". At " + e.venue + ". " + seatsText + ".";
        h.itemView.setContentDescription(summary);
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onEventClick(e);
        });

        // Bookmark: save straight from the list (Requires sign in)
        final CampusRepository repo = CampusRepository.get();
        applySaveIcon(h.save, repo.isSaved(e.id), e.title);
        h.save.setOnClickListener(v -> {
            boolean isLoggedIn = TokenManager.getAccessToken(v.getContext()) != null;
            if (!isLoggedIn) {
                Toast.makeText(v.getContext(), "Sign in to save events to your schedule", Toast.LENGTH_SHORT).show();
                v.getContext().startActivity(new Intent(v.getContext(), LoginActivity.class));
                return;
            }
            repo.toggleSavedApi(v.getContext(), e.id, () -> {
                applySaveIcon(h.save, repo.isSaved(e.id), e.title);
                v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                v.animate().scaleX(1.3f).scaleY(1.3f).setDuration(110)
                        .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(110));
            }, err -> {
                applySaveIcon(h.save, repo.isSaved(e.id), e.title);
                Toast.makeText(v.getContext(), err, Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void applySaveIcon(ImageButton b, boolean saved, String title) {
        b.setImageResource(saved ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_border);
        b.setColorFilter(androidx.core.content.ContextCompat.getColor(b.getContext(),
                saved ? R.color.gold_accent : R.color.usiu_navy));
        b.setContentDescription(saved
                ? "Remove " + title + " from My Week"
                : "Save " + title + " to My Week");
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class EventHolder extends RecyclerView.ViewHolder {
        TextView title, venue, time, seats, type, dayNum, month;
        ImageView image;
        ImageButton save;

        EventHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.ev_title);
            venue = itemView.findViewById(R.id.ev_venue);
            time = itemView.findViewById(R.id.ev_time);
            seats = itemView.findViewById(R.id.ev_seats);
            type = itemView.findViewById(R.id.ev_type);
            dayNum = itemView.findViewById(R.id.ev_num);
            month = itemView.findViewById(R.id.ev_day);
            image = itemView.findViewById(R.id.ev_image);
            save = itemView.findViewById(R.id.ev_save);
        }
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        TextView title;

        HeaderHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.header_text);
        }
    }
}
