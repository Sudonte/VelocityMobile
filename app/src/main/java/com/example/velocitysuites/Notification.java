package com.example.velocitysuites;

import java.io.Serializable;
import java.util.List;

public class Notification implements Serializable {
    public static final String TYPE_BOOKING = "Booking";
    public static final String TYPE_PAYMENT = "Payment";
    public static final String TYPE_CHECK_IN = "CheckIn";
    public static final String TYPE_PROMOTION = "Promotion";
    public static final String TYPE_SMS = "SMS";
    public static final String TYPE_SYSTEM = "System";
    public static final String TYPE_ANNOUNCEMENT = "Announcement";

    private String id;
    private String title;
    private String message;
    private String timestamp;
    private String type;
    private boolean isRead;
    private String referenceId;
    private List<String> targetAudience;
    private String publishedAt;

    public Notification(String id, String title, String message, String timestamp, String type, boolean isRead) {
        this(id, title, message, timestamp, type, isRead, null, null, null);
    }

    public Notification(String id, String title, String message, String timestamp, String type, boolean isRead, String referenceId) {
        this(id, title, message, timestamp, type, isRead, referenceId, null, null);
    }

    public Notification(String id, String title, String message, String timestamp, String type, boolean isRead, String referenceId, List<String> targetAudience, String publishedAt) {
        this.id = id;
        this.title = title;
        this.message = message;
        this.timestamp = timestamp;
        this.type = type;
        this.isRead = isRead;
        this.referenceId = referenceId;
        this.targetAudience = targetAudience;
        this.publishedAt = publishedAt;
    }

    // Getters
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getTimestamp() { return timestamp; }
    public String getType() { return type; }
    public boolean isRead() { return isRead; }
    /** The linked Booking/Reservation id (same underlying row - see Booking.isHasBooking()), or null for general/system notifications. */
    public String getReferenceId() { return referenceId; }
    /** Roles this announcement was sent to (e.g. ["guest","manager"]), or null for non-announcement notifications. */
    public List<String> getTargetAudience() { return targetAudience; }
    /** Absolute "MMM dd, yyyy at h:mm a" formatted publish date/time, distinct from the relative getTimestamp(). */
    public String getPublishedAt() { return publishedAt; }

    // Setters
    public void setRead(boolean read) { isRead = read; }
}
