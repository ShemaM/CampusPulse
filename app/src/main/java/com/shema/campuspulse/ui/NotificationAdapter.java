package com.shema.campuspulse.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.shema.campuspulse.R;
import com.shema.campuspulse.data.NotificationItem;

import java.util.ArrayList;
import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    public interface OnNotificationClickListener {
        void onAction(NotificationItem item);
        void onAcknowledge(NotificationItem item);
        void onDismiss(NotificationItem item, int position);
    }

    private final List<NotificationItem> fullList = new ArrayList<>();
    private final List<NotificationItem> displayList = new ArrayList<>();
    private int currentFilter = 0; // 0=All, 1=Academic, 2=Campus Life
    private final OnNotificationClickListener listener;

    public NotificationAdapter(OnNotificationClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<NotificationItem> items) {
        fullList.clear();
        if (items != null) {
            fullList.addAll(items);
        }
        applyFilter();
    }

    public void setFilter(int filter) {
        this.currentFilter = filter;
        applyFilter();
    }

    private void applyFilter() {
        displayList.clear();
        for (NotificationItem item : fullList) {
            if (item.isDismissed) continue;
            if (currentFilter == 1) { // Academic only
                if ("Academic".equalsIgnoreCase(item.category)) {
                    displayList.add(item);
                }
            } else if (currentFilter == 2) { // Campus life (non-academic)
                if (!"Academic".equalsIgnoreCase(item.category)) {
                    displayList.add(item);
                }
            } else {
                displayList.add(item);
            }
        }
        notifyDataSetChanged();
    }

    public int getUnreadCount() {
        int count = 0;
        for (NotificationItem item : fullList) {
            if (!item.isDismissed && !item.isRead) count++;
        }
        return count;
    }

    public void markAllReadLocal() {
        for (NotificationItem item : fullList) {
            item.isRead = true;
        }
        notifyDataSetChanged();
    }

    public void dismissItem(NotificationItem item) {
        item.isDismissed = true;
        applyFilter();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        NotificationItem item = displayList.get(position);
        Context ctx = h.itemView.getContext();

        // Card styling: Gold border for priority notices
        if (item.isPriority) {
            h.card.setStrokeColor(ContextCompat.getColor(ctx, R.color.usiu_gold));
            h.card.setStrokeWidth(Math.round(1.5f * ctx.getResources().getDisplayMetrics().density));
        } else {
            h.card.setStrokeColor(ContextCompat.getColor(ctx, R.color.divider));
            h.card.setStrokeWidth(Math.round(1.0f * ctx.getResources().getDisplayMetrics().density));
        }

        // Priority Badge
        if (item.isPriority) {
            h.badge.setVisibility(View.VISIBLE);
            if (item.isRead) {
                h.badge.setText("READ");
                h.badge.setBackgroundResource(R.drawable.bg_badge_ok);
                h.badge.setTextColor(ContextCompat.getColor(ctx, R.color.status_success));
            } else {
                h.badge.setText("PRIORITY NOTICE");
                h.badge.setBackgroundResource(R.drawable.bg_badge_danger);
                h.badge.setTextColor(ContextCompat.getColor(ctx, R.color.status_danger));
            }
        } else {
            h.badge.setVisibility(View.GONE);
        }

        // Source and time
        String timeStr = item.timeLabel != null && !item.timeLabel.isEmpty() ? item.timeLabel : "Today";
        String sourceStr = item.source != null && !item.source.isEmpty() ? item.source : "USIU-Africa";
        h.sourceTime.setText(sourceStr + " · " + timeStr);

        // Unread Dot
        h.dotUnread.setVisibility(item.isRead ? View.GONE : View.VISIBLE);

        // Title and Message
        h.title.setText(item.title);
        h.message.setText(item.message);

        // Action Button (e.g. View event, View room, View timetable)
        if (item.actionLabel != null && !item.actionLabel.isEmpty()) {
            h.btnAction.setVisibility(View.VISIBLE);
            h.btnAction.setText(item.actionLabel);
            h.btnAction.setOnClickListener(v -> {
                if (listener != null) listener.onAction(item);
            });
        } else {
            h.btnAction.setVisibility(View.GONE);
        }

        // Acknowledge Button
        if (item.isPriority && !item.isRead) {
            h.btnAcknowledge.setVisibility(View.VISIBLE);
            h.btnAcknowledge.setOnClickListener(v -> {
                item.isRead = true;
                notifyItemChanged(h.getAdapterPosition());
                if (listener != null) listener.onAcknowledge(item);
            });
        } else {
            h.btnAcknowledge.setVisibility(View.GONE);
        }

        // Dismiss Button
        h.btnDismiss.setOnClickListener(v -> {
            if (listener != null) listener.onDismiss(item, h.getAdapterPosition());
        });
    }

    @Override
    public int getItemCount() {
        return displayList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final TextView badge, sourceTime, title, message, btnDismiss;
        final View dotUnread;
        final MaterialButton btnAction, btnAcknowledge;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card_notification);
            badge = itemView.findViewById(R.id.notif_badge);
            sourceTime = itemView.findViewById(R.id.notif_source_time);
            dotUnread = itemView.findViewById(R.id.notif_dot_unread);
            title = itemView.findViewById(R.id.notif_title);
            message = itemView.findViewById(R.id.notif_message);
            btnAction = itemView.findViewById(R.id.btn_notif_action);
            btnAcknowledge = itemView.findViewById(R.id.btn_notif_acknowledge);
            btnDismiss = itemView.findViewById(R.id.btn_notif_dismiss);
        }
    }
}
