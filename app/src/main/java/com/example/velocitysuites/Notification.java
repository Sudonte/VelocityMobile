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
    /** The exact receipt this notification is about (e.g. "PR-20260925-000501"), or null - see ReceiptTypeMapper's PARTIAL_RECEIPT/FULL_PAYMENT_RECEIPT/OFFICIAL_RECEIPT constants for receiptType's possible values. Backend-authoritative only: never inferred from the message text, never guessed from booking status - see PAYMENT_RECEIPT_HISTORY_BACKEND_SPEC.md Phase 5 §8. Null for every notification created before this metadata existed and for every non-payment notification, which must keep behaving exactly as before. */
    @androidx.annotation.Nullable
    private String receiptNumber;
    @androidx.annotation.Nullable
    private String receiptType;

    public Notification(String id, String title, String message, String timestamp, String type, boolean isRead) {
        this(id, title, message, timestamp, type, isRead, null, null, null);
    }

    public Notification(String id, String title, String message, String timestamp, String type, boolean isRead, String referenceId) {
        this(id, title, message, timestamp, type, isRead, referenceId, null, null);
    }

    public Notification(String id, String title, String message, String timestamp, String type, boolean isRead, String referenceId, List<String> targetAudience, String publishedAt) {
        this(id, title, message, timestamp, type, isRead, referenceId, targetAudience, publishedAt, null, null);
    }

    public Notification(String id, String title, String message, String timestamp, String type, boolean isRead, String referenceId, List<String> targetAudience, String publishedAt,
                         @androidx.annotation.Nullable String receiptNumber, @androidx.annotation.Nullable String receiptType) {
        this.id = id;
        this.title = title;
        this.message = message;
        this.timestamp = timestamp;
        this.type = type;
        this.isRead = isRead;
        this.referenceId = referenceId;
        this.targetAudience = targetAudience;
        this.publishedAt = publishedAt;
        this.receiptNumber = receiptNumber;
        this.receiptType = receiptType;
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
    /** Exact backend receipt_number this notification is about, or null - see this field's own doc above. */
    @androidx.annotation.Nullable
    public String getReceiptNumber() { return receiptNumber; }
    /** PARTIAL_RECEIPT/FULL_PAYMENT_RECEIPT/OFFICIAL_RECEIPT (ReceiptTypeMapper), or null - always paired with a non-null getReceiptNumber(). */
    @androidx.annotation.Nullable
    public String getReceiptType() { return receiptType; }
    /** True once the backend has attached a real, already-issued receipt_number - the single gate every receipt-navigation caller should check before using getReceiptNumber(). */
    public boolean hasStructuredReceipt() {
        return receiptNumber != null && !receiptNumber.trim().isEmpty();
    }

    // Setters
    public void setRead(boolean read) { isRead = read; }
}
