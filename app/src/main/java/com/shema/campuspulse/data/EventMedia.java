package com.shema.campuspulse.data;

/** Represents post-event photos and videos shared by organizers or attendees. */
public class EventMedia {
    public final String id;
    public final String eventId;
    public final String caption;
    public final String mediaType; // "Photo" or "Video"
    public final String mediaUrl;
    public final String uploaderName;
    public final long timestamp;

    public EventMedia(String id, String eventId, String caption, String mediaType,
                      String mediaUrl, String uploaderName, long timestamp) {
        this.id = id;
        this.eventId = eventId;
        this.caption = caption;
        this.mediaType = mediaType;
        this.mediaUrl = mediaUrl;
        this.uploaderName = uploaderName;
        this.timestamp = timestamp;
    }
}
