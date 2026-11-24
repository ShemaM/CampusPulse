package com.shema.campuspulse.data;

import android.content.Context;
import android.os.Build;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class EventReminderManager {

    public static boolean canScheduleNotifications(Context context) {
        if (context == null) return false;
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public static boolean scheduleReminder(Context context, Event event) {
        if (event == null || !canScheduleNotifications(context)) return false;

        long now = System.currentTimeMillis();
        long timeUntilStart = event.startMillis - now;

        // Requirement 6: Do not schedule if event is less than 1 hour away, already started, or ended
        if (timeUntilStart < 60 * 60 * 1000) {
            return false;
        }

        // Requirement 6: Schedule 24 hours before the event; if less than 24 hours away, schedule one hour before.
        long delay;
        if (timeUntilStart >= 24L * 60 * 60 * 1000) {
            delay = timeUntilStart - (24L * 60 * 60 * 1000);
        } else {
            delay = timeUntilStart - (60 * 60 * 1000);
        }

        if (delay < 0) {
            delay = 0;
        }

        Data data = new Data.Builder()
                .putString("event_id", event.id)
                .putString("event_title", event.title)
                .putString("event_venue", event.venue != null && !event.venue.trim().isEmpty() ? event.venue : "Campus Venue")
                .putLong("event_start", event.startMillis)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(EventReminderWorker.class)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .build();

        // Requirement 7: Use a stable unique WorkManager identifier derived from the event ID
        WorkManager.getInstance(context).enqueueUniqueWork(
                "rsvp_reminder_" + event.id,
                ExistingWorkPolicy.REPLACE,
                request
        );

        return true;
    }

    public static void cancelReminder(Context context, String eventId) {
        if (eventId == null || context == null) return;
        WorkManager.getInstance(context).cancelUniqueWork("rsvp_reminder_" + eventId);
    }
}
