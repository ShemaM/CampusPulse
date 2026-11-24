package com.shema.campuspulse;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.shema.campuspulse.data.CampusRepository;
import com.shema.campuspulse.data.NotificationItem;
import com.shema.campuspulse.data.TokenManager;
import com.shema.campuspulse.ui.NotificationAdapter;

import java.util.List;

public class NotificationsActivity extends AppCompatActivity implements NotificationAdapter.OnNotificationClickListener {

    private final CampusRepository repo = CampusRepository.get();
    private NotificationAdapter adapter;
    private TextView unreadCountView;
    private TextView markAllReadBtn;
    private TextView filterAll, filterAcademic, filterCampus;
    private ProgressBar progressBar;
    private View emptyState;
    private RecyclerView recyclerView;
    private int currentFilter = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this, SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        setContentView(R.layout.activity_notifications);

        final View content = findViewById(R.id.notifications_content);
        if (content != null) {
            final int baseTop = content.getPaddingTop();
            final int baseBottom = content.getPaddingBottom();
            ViewCompat.setOnApplyWindowInsetsListener(content, (v, windowInsets) -> {
                Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), baseTop + bars.top,
                        v.getPaddingRight(), baseBottom + bars.bottom);
                return windowInsets;
            });
        }

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        unreadCountView = findViewById(R.id.notifications_unread_count);
        markAllReadBtn = findViewById(R.id.text_mark_all_read);
        filterAll = findViewById(R.id.filter_all);
        filterAcademic = findViewById(R.id.filter_academic);
        filterCampus = findViewById(R.id.filter_campus);
        progressBar = findViewById(R.id.notifications_progress);
        emptyState = findViewById(R.id.notifications_empty_state);
        recyclerView = findViewById(R.id.notifications_recycler);

        adapter = new NotificationAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        filterAll.setOnClickListener(v -> setFilter(0));
        filterAcademic.setOnClickListener(v -> setFilter(1));
        filterCampus.setOnClickListener(v -> setFilter(2));

        markAllReadBtn.setOnClickListener(v -> {
            if (adapter.getUnreadCount() == 0) return;
            repo.markAllNotificationsReadApi(this, () -> {
                adapter.markAllReadLocal();
                updateHeaderCounts();
            });
        });

        loadNotifications();
    }

    private void loadNotifications() {
        boolean isLoggedIn = TokenManager.getAccessToken(this) != null;
        if (!isLoggedIn) {
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            if (emptyState != null) emptyState.setVisibility(View.VISIBLE);
            if (unreadCountView != null) unreadCountView.setText("Guest Mode");
            return;
        }

        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        if (emptyState != null) emptyState.setVisibility(View.GONE);

        repo.fetchNotificationsApi(this, list -> {
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            adapter.submitList(list);
            updateHeaderCounts();
            checkEmptyState();
        }, err -> {
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            checkEmptyState();
            Toast.makeText(this, "Could not load latest notices: " + err, Toast.LENGTH_SHORT).show();
        });
    }

    private void updateHeaderCounts() {
        int unread = adapter.getUnreadCount();
        if (unreadCountView != null) {
            unreadCountView.setText(unread == 0 ? "You're all caught up" : unread + (unread == 1 ? " unread update" : " unread updates"));
        }
        if (markAllReadBtn != null) {
            markAllReadBtn.setText(unread == 0 ? "All read" : "Mark all read");
            markAllReadBtn.setAlpha(unread == 0 ? 0.6f : 1.0f);
        }
    }

    private void checkEmptyState() {
        boolean isEmpty = adapter.getItemCount() == 0;
        if (emptyState != null) {
            emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
        if (recyclerView != null) {
            recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
    }

    private void setFilter(int filter) {
        this.currentFilter = filter;
        TextView[] tabs = {filterAll, filterAcademic, filterCampus};
        for (int i = 0; i < tabs.length; i++) {
            boolean selected = (i == filter);
            tabs[i].setBackgroundResource(selected ? R.drawable.bg_pill_navy : R.drawable.bg_pill_glass);
            tabs[i].setTextColor(getColor(selected ? R.color.white : R.color.usiu_navy));
        }
        adapter.setFilter(filter);
        checkEmptyState();
    }

    @Override
    public void onAction(NotificationItem item) {
        if (item.eventId != null && !item.eventId.isEmpty()) {
            Intent intent = new Intent(this, EventDetailActivity.class);
            intent.putExtra(EventDetailActivity.EXTRA_EVENT_ID, item.eventId);
            startActivity(intent);
        } else if ("View room".equalsIgnoreCase(item.actionLabel)) {
            new AlertDialog.Builder(this)
                    .setTitle("Updated Lab Location")
                    .setMessage(item.message + "\n\nScience Complex, Room B-104")
                    .setPositiveButton("Got it", null)
                    .show();
        } else if ("View timetable".equalsIgnoreCase(item.actionLabel)) {
            finish(); // Returns to main where user can view their schedule / profile
        } else {
            Toast.makeText(this, item.title, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onAcknowledge(NotificationItem item) {
        repo.markNotificationReadApi(this, item.id, this::updateHeaderCounts);
    }

    @Override
    public void onDismiss(NotificationItem item, int position) {
        repo.dismissNotificationApi(this, item.id, () -> {
            adapter.dismissItem(item);
            updateHeaderCounts();
            checkEmptyState();
        });
    }
}
