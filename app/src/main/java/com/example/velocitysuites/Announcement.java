package com.example.velocitysuites;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;

/**
 * Guest-audience announcement shown in landing.xml's Announcements section -
 * sourced from the exact same centralized App\Models\Announcement rows
 * (Api\CatalogController::announcements(), Announcement::visibleTo('guest'))
 * that also drive the web public Home page and this same guest's
 * Notification-module entries, so there is only ever one record per
 * announcement, never a separate mobile-only copy.
 */
public class Announcement implements Serializable {
    private final String id;
    private final String title;
    private final String content;
    private final String publishedAt;
    private final List<String> targetAudience;
    private final List<String> images;

    public Announcement(String id, String title, String content, String publishedAt, List<String> targetAudience, List<String> images) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.publishedAt = publishedAt;
        this.targetAudience = targetAudience;
        this.images = images;
    }

    public static Announcement fromDto(com.example.velocitysuites.network.dto.AnnouncementDto dto) {
        // dto.published_at is a raw UTC ISO-8601 timestamp - format it to
        // Asia/Manila here (once, at mapping time) rather than showing the
        // unconverted raw string to the guest (see TimeUtils).
        return new Announcement(String.valueOf(dto.id), dto.title, dto.content, TimeUtils.formatDateTime(dto.published_at), dto.target_audience, dto.images);
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getPublishedAt() { return publishedAt; }
    public List<String> getTargetAudience() { return targetAudience; }

    public String getFirstImageUrl() {
        return (images == null || images.isEmpty()) ? null : images.get(0);
    }

    /**
     * "guest" -> "Guest & Mobile App" etc. - matches the web admin panel's
     * Announcement::audienceLabel() labels exactly so the two never drift.
     * Shared by both this class's own display needs and NotificationActivity's
     * announcement-notification detail dialog, rather than duplicating the
     * same switch in two places.
     */
    public static String formatAudienceLabel(String role) {
        if (role == null) return "";
        switch (role) {
            case "guest": return "Guest & Mobile App";
            case "manager": return "Manager";
            case "receptionist": return "Receptionist";
            case "public": return "Public";
            default: return role.substring(0, 1).toUpperCase(Locale.US) + role.substring(1);
        }
    }

    public static String formatAudienceList(List<String> roles) {
        if (roles == null || roles.isEmpty()) return "All Audiences";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < roles.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(formatAudienceLabel(roles.get(i)));
        }
        return sb.toString();
    }
}
