package com.shema.campuspulse.data;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.shema.campuspulse.CampusApplication;
import com.shema.campuspulse.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class EventReminderWorker extends Worker {

    public EventReminderWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String title = getInputData().getString("event_title");
        String venue = getInputData().getString("event_venue");
        long startMillis = getInputData().getLong("event_start", 0L);

        if (title == null) title = "Campus Event";
        if (venue == null) venue = "Campus Venue";

        SimpleDateFormat timeFmt = new SimpleDateFormat("EEE, MMM d 'at' h:mm a", Locale.getDefault());
        String timeStr = startMillis > 0 ? timeFmt.format(new Date(startMillis)) : "Upcoming";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CampusApplication.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_event)
                .setContentTitle("Reminder: " + title)
                .setContentText(venue + " - " + timeStr)
                .setStyle(new NotificationCompat.BigTextStyle().bigText("Your event starts soon at " + venue + " (" + timeStr + "). Don't forget to attend!"))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        try {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());
            int notificationId = (int) System.currentTimeMillis();
            notificationManager.notify(notificationId, builder.build());
        } catch (SecurityException e) {
            // Permission not granted
        }

        return Result.success();
    }
}
