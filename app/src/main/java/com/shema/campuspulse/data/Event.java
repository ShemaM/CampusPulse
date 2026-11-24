package com.shema.campuspulse.data;

import com.google.gson.annotations.SerializedName;

public class Event {
    @SerializedName(value = "id", alternate = {"event_id"})
    public String id;

    @SerializedName("title")
    public String title;

    @SerializedName("description")
    public String description;

    @SerializedName("school_code")
    public String schoolCode;   // null = campus-wide (BR-01)

    @SerializedName("major")
    public String major;        // null = all majors in that school

    @SerializedName(value = "type", alternate = {"event_type"})
    public String type;         // Academic, Career, Social, Sports, Wellness

    @SerializedName(value = "startMillis", alternate = {"start_millis"})
    public long startMillis;

    @SerializedName(value = "endMillis", alternate = {"end_millis"})
    public long endMillis;

    @SerializedName("venue")
    public String venue;

    @SerializedName(value = "totalSeats", alternate = {"total_seats"})
    public Integer totalSeats; // Nullable for unlimited capacity

    @SerializedName(value = "seatsLeft", alternate = {"seats_left"})
    public Integer seatsLeft; // Nullable for unlimited capacity

    @SerializedName("organizer")
    public String organizer;

    @SerializedName("details")
    public String details = "";

    @SerializedName("highlights")
    public String[] highlights = new String[0];

    @SerializedName("agenda")
    public String[] agenda = new String[0];   // "minutes from start|label"

    @SerializedName("audience")
    public String audience = "";

    @SerializedName("bring")
    public String bring = "";

    @SerializedName("contact_name")
    public String contactName = "";

    @SerializedName("contact_email")
    public String contactEmail = "";

    @SerializedName("contact_phone")
    public String contactPhone = "";

    @SerializedName(value = "isSaved", alternate = {"is_saved"})
    public boolean isSaved;

    @SerializedName(value = "isRsvped", alternate = {"is_rsvped"})
    public boolean isRsvped;

    @SerializedName(value = "startTime", alternate = {"start_time"})
    public String startTime;

    @SerializedName(value = "endTime", alternate = {"end_time"})
    public String endTime;

    @SerializedName("image")
    public String image;

    @SerializedName(value = "imageUrl", alternate = {"image_url"})
    public String imageUrl;

    @SerializedName("video")
    public String video;

    public Event() {}

    public Event(String id, String title, String description, String schoolCode,
                 String major, String type, long startMillis, long endMillis,
                 String venue, Integer totalSeats, Integer seatsLeft, String organizer) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.schoolCode = schoolCode;
        this.major = major;
        this.type = type;
        this.startMillis = startMillis;
        this.endMillis = endMillis;
        this.venue = venue;
        this.totalSeats = totalSeats;
        this.seatsLeft = seatsLeft;
        this.organizer = organizer;
    }

    public boolean isUnlimited() {
        return totalSeats == null;
    }

    /** FR-17: "Filling fast" when 80% or more of seats are taken. */
    public boolean isFillingFast() {
        if (totalSeats == null || seatsLeft == null) return false;
        return seatsLeft > 0 && seatsLeft <= totalSeats * 0.2;
    }

    /** BR-03: RSVP closes at 100% capacity. */
    public boolean isFull() {
        if (totalSeats == null) return false; // Unlimited events are never full
        return seatsLeft != null && seatsLeft <= 0;
    }

    public String getUsableMediaUrl() {
        if (imageUrl != null && !imageUrl.trim().isEmpty()) {
            return imageUrl;
        }
        if (image != null && !image.trim().isEmpty()) {
            if (image.startsWith("http://") || image.startsWith("https://")) {
                return image;
            }
            if (image.startsWith("/")) {
                return "http://10.0.2.2:8000" + image;
            }
            return "http://10.0.2.2:8000/media/" + image;
        }
        return null;
    }

    public String getUsableVideoUrl() {
        if (video != null && !video.trim().isEmpty()) {
            if (video.startsWith("http://") || video.startsWith("https://")) {
                return video;
            }
            if (video.startsWith("/")) {
                return "http://10.0.2.2:8000" + video;
            }
            return "http://10.0.2.2:8000/media/" + video;
        }
        return null;
    }
}
