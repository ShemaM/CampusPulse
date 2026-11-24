package com.shema.campuspulse.data;

import com.google.gson.annotations.SerializedName;

public class NotificationItem {
    @SerializedName("id")
    public int id;

    @SerializedName("title")
    public String title;

    @SerializedName("message")
    public String message;

    @SerializedName("category")
    public String category; // "Academic", "Campus Life", "RSVP", "Saved"

    @SerializedName("source")
    public String source;

    @SerializedName("time_label")
    public String timeLabel;

    @SerializedName("is_priority")
    public boolean isPriority;

    @SerializedName("is_read")
    public boolean isRead;

    @SerializedName("is_dismissed")
    public boolean isDismissed;

    @SerializedName("event_id")
    public String eventId;

    @SerializedName("action_label")
    public String actionLabel;
}
