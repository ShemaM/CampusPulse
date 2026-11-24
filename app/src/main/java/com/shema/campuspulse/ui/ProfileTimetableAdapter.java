package com.shema.campuspulse.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.shema.campuspulse.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ProfileTimetableAdapter extends RecyclerView.Adapter<ProfileTimetableAdapter.Holder> {

    public interface DeleteListener {
        void onDelete(long id);
    }

    private final List<JsonObject> rows = new ArrayList<>();
    private final DeleteListener deleteListener;

    public ProfileTimetableAdapter(DeleteListener listener) {
        this.deleteListener = listener;
    }

    public void submit(JsonArray data) {
        rows.clear();
        if (data != null) {
            for (int i = 0; i < data.size(); i++) {
                if (data.get(i).isJsonObject()) {
                    rows.add(data.get(i).getAsJsonObject());
                }
            }
        }
        rows.sort((a, b) -> {
            String[] order = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};
            int da = Arrays.asList(order).indexOf(str(a, "day_of_week"));
            int db = Arrays.asList(order).indexOf(str(b, "day_of_week"));
            int d = Integer.compare(da, db);
            return d != 0 ? d : str(a, "start_time").compareTo(str(b, "start_time"));
        });
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_profile_timetable_slot, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int p) {
        JsonObject o = rows.get(p);
        String course = str(o, "course_name");
        String day = str(o, "day_of_week");
        h.day.setText(day);
        h.course.setText(course);
        h.time.setText(clock(str(o, "start_time")) + " - " + clock(str(o, "end_time")) + " \u00B7 3 Credits");

        boolean clash = o.has("has_collision") && !o.get("has_collision").isJsonNull() && o.get("has_collision").getAsBoolean();
        h.status.setText(clash ? "\u26A0 Collision" : "\u2713 Clear \u00B7 0 Event Collisions");
        h.collision.setVisibility(clash ? View.VISIBLE : View.GONE);
        if (clash) {
            h.collision.setText("\u26A0 Collision with: " + str(o, "collision_event_title")
                    + "\nEvent: " + str(o, "collision_time") + " \u00B7 " + str(o, "collision_venue")
                    + "\nClass attendance conflict");
        }
        h.delete.setOnClickListener(v -> {
            if (deleteListener != null) {
                deleteListener.onDelete(o.has("id") ? o.get("id").getAsLong() : -1);
            }
        });
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    private static String clock(String t) {
        try {
            String[] p = t.split(":");
            int hh = Integer.parseInt(p[0]);
            return String.format(Locale.getDefault(), "%02d:%s %s", hh % 12 == 0 ? 12 : hh % 12, p[1], hh < 12 ? "AM" : "PM");
        } catch (Exception e) {
            return t;
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        TextView day, course, time, status, collision;
        View delete;

        Holder(View v) {
            super(v);
            day = v.findViewById(R.id.slot_day);
            course = v.findViewById(R.id.slot_course);
            time = v.findViewById(R.id.slot_time);
            status = v.findViewById(R.id.slot_status);
            collision = v.findViewById(R.id.slot_collision);
            delete = v.findViewById(R.id.slot_delete);
        }
    }
}
