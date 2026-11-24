package com.shema.campuspulse.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.shema.campuspulse.R;
import com.shema.campuspulse.data.Event;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RegistrationAdapter extends RecyclerView.Adapter<RegistrationAdapter.EventHolder> {

    public interface Listener {
        void onEventClick(Event event);
        void onActionClick(Event event);
    }

    private final List<Event> events = new ArrayList<>();
    private final Listener listener;
    private final boolean registeredSection;

    private final SimpleDateFormat eyebrowFmt = new SimpleDateFormat("EEE, MMM d", Locale.getDefault());
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm a", Locale.getDefault());

    public RegistrationAdapter(boolean registeredSection, Listener listener) {
        this.registeredSection = registeredSection;
        this.listener = listener;
    }

    public void submit(List<Event> newEvents) {
        events.clear();
        if (newEvents != null) events.addAll(newEvents);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EventHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_registration, parent, false);
        return new EventHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventHolder holder, int position) {
        Event event = events.get(position);

        if (holder.image != null) {
            EventMediaHelper.loadPoster(holder.itemView.getContext(), event, holder.image);
        }

        String title = formatTitle(event.title, position);
        holder.title.setText(title);

        Date start = new Date(event.startMillis);
        Date end = new Date(event.endMillis);

        if (holder.eyebrow != null) {
            String eyebrow = eyebrowFmt.format(start).toUpperCase(Locale.getDefault()) + " \u00B7 " + timeFmt.format(start);
            holder.eyebrow.setText(eyebrow);
        }

        if (holder.venue != null) {
            holder.venue.setText(event.venue == null || event.venue.trim().isEmpty()
                    ? "USIU-Africa Campus" : event.venue);
        }

        if (holder.time != null) {
            String startTime = timeFmt.format(start);
            String endTime = timeFmt.format(end);
            if (startTime.equalsIgnoreCase(endTime) || event.startMillis == event.endMillis) {
                holder.time.setText(startTime);
            } else {
                holder.time.setText(startTime + " - " + endTime);
            }
        }

        if (holder.type != null) {
            CategoryBadgeHelper.applyCategoryBadge(holder.type, event.type);
        }

        if (holder.passCode != null) {
            if (registeredSection) {
                holder.passCode.setText(String.format(Locale.getDefault(), "#USIU-%04d", Math.abs(event.id.hashCode() % 9000) + 1000));
                holder.passCode.setVisibility(View.VISIBLE);
            } else {
                holder.passCode.setVisibility(View.GONE);
            }
        }

        if (holder.status != null) {
            if (registeredSection) {
                holder.status.setText("CONFIRMED PASS");
                holder.status.setBackgroundResource(R.drawable.bg_badge_ok);
                holder.status.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.status_success));
            } else {
                holder.status.setText("SAVED ACTIVITY");
                holder.status.setBackgroundResource(R.drawable.bg_badge_soft);
                holder.status.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.usiu_navy));
            }
        }

        if (holder.action != null) {
            holder.action.setText(registeredSection ? "Cancel RSVP" : "Remove");
            holder.action.setTextColor(ContextCompat.getColor(holder.itemView.getContext(),
                    registeredSection ? R.color.status_danger : R.color.text_secondary));
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onEventClick(event);
        });

        if (holder.action != null) {
            holder.action.setOnClickListener(v -> {
                if (listener != null) listener.onActionClick(event);
            });
        }
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    private String formatTitle(String rawTitle, int position) {
        if (rawTitle == null || rawTitle.trim().isEmpty()) {
            return "Campus Event " + (position + 1);
        }
        String t = rawTitle.trim();
        if (t.contains("http://") || t.contains("https://") || t.contains("127.0.0.1") || t.contains("/api/")) {
            return "Campus Leadership & Tech Session";
        }
        if (t.length() > 4 && t.equals(t.toUpperCase(Locale.ROOT))) {
            String[] words = t.toLowerCase(Locale.ROOT).split("\\s+");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < words.length; i++) {
                String w = words[i];
                if (!w.isEmpty()) {
                    sb.append(Character.toUpperCase(w.charAt(0)));
                    if (w.length() > 1) {
                        sb.append(w.substring(1));
                    }
                }
                if (i < words.length - 1) sb.append(" ");
            }
            return sb.toString();
        }
        return t;
    }

    static class EventHolder extends RecyclerView.ViewHolder {
        ImageView image;
        TextView eyebrow, status, title, venue, time, type, passCode, action;

        EventHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.registration_image);
            eyebrow = itemView.findViewById(R.id.registration_eyebrow);
            status = itemView.findViewById(R.id.registration_status);
            title = itemView.findViewById(R.id.registration_title);
            venue = itemView.findViewById(R.id.registration_venue);
            time = itemView.findViewById(R.id.registration_time);
            type = itemView.findViewById(R.id.registration_type);
            passCode = itemView.findViewById(R.id.registration_pass_code);
            action = itemView.findViewById(R.id.registration_action);
        }
    }
}
