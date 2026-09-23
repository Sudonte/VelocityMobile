package com.example.velocitysuites;

import android.util.Log;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Single source of truth for parsing backend timestamps and displaying them in
 * Philippine time - Velocity Suites operates in the Philippines, so every
 * transaction/action timestamp is shown in Asia/Manila regardless of the
 * guest's device locale/timezone (the live dashboard clock is the one
 * deliberate exception - see DashboardActivity#updateDateTime(), which is
 * allowed to use the device's own local time since it's a UI convenience,
 * not a transaction record).
 * <p>
 * The backend (Laravel/Carbon) stores and returns timestamps in UTC. This
 * class converts to Asia/Manila (UTC+8) using real java.time zone rules
 * rather than manually adding/subtracting hours, which breaks the moment a
 * timestamp format's assumptions change.
 */
public final class TimeUtils {

    private static final String TAG = "TimeUtils";

    public static final ZoneId ZONE_MANILA = ZoneId.of("Asia/Manila");

    /** "Sep 9, 2026 - 2:37 AM" - the app-wide compact date+time display format. */
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy • h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private TimeUtils() {
    }

    /**
     * Parses a backend timestamp in any of the shapes this API actually sends:
     * ISO-8601 with a "Z"/offset suffix, or Laravel's bare
     * "yyyy-MM-dd'T'HH:mm:ss"/"yyyy-MM-dd HH:mm:ss" (no offset - always UTC
     * per this project's storage convention). Returns null rather than
     * throwing when the value is missing or doesn't match any known shape.
     */
    public static Instant parseInstant(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty()) return null;

        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ignored) {
        }

        // Laravel's default JSON cast has no offset at all - treat as UTC,
        // the app's single storage-timezone convention (see class doc).
        String normalized = value.contains("T") ? value : value.replace(' ', 'T');
        try {
            return java.time.LocalDateTime.parse(normalized).atZone(java.time.ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException e) {
            Log.e(TAG, "Unparseable timestamp: " + raw, e);
            return null;
        }
    }

    /** "Sep 9, 2026 - 2:37 AM" in Asia/Manila, or "N/A" for a null/unparseable/empty timestamp. */
    public static String formatDateTime(String raw) {
        Instant instant = parseInstant(raw);
        if (instant == null) return "N/A";
        return DATE_TIME_FORMAT.format(instant.atZone(ZONE_MANILA));
    }

    /** "Sep 9, 2026" in Asia/Manila, or "N/A" for a null/unparseable/empty timestamp. */
    public static String formatDate(String raw) {
        Instant instant = parseInstant(raw);
        if (instant == null) return "N/A";
        return DATE_FORMAT.format(instant.atZone(ZONE_MANILA));
    }

    /** "2:37 AM" in Asia/Manila, or "N/A" for a null/unparseable/empty timestamp. */
    public static String formatTime(String raw) {
        Instant instant = parseInstant(raw);
        if (instant == null) return "N/A";
        return TIME_FORMAT.format(instant.atZone(ZONE_MANILA));
    }

    /**
     * "Just now" / "5 minutes ago" / "3 hours ago" / "2 days ago", computed
     * against the real elapsed instant (Instant is zone-agnostic, so this is
     * correct no matter what timezone the device is set to). Returns "" for
     * a null/unparseable timestamp - callers that need a fallback should
     * check for that themselves (see ApiMapper#toNotification).
     */
    public static String formatRelative(String raw) {
        Instant instant = parseInstant(raw);
        if (instant == null) return "";
        long diffMs = System.currentTimeMillis() - instant.toEpochMilli();
        if (diffMs < 0) diffMs = 0;
        long minutes = diffMs / (60 * 1000);
        if (minutes < 1) return "Just now";
        if (minutes < 60) return minutes + " minute" + (minutes == 1 ? "" : "s") + " ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + " hour" + (hours == 1 ? "" : "s") + " ago";
        long days = hours / 24;
        if (days < 30) return days + " day" + (days == 1 ? "" : "s") + " ago";
        return formatDate(raw);
    }

    /** ZonedDateTime for the current instant in Asia/Manila - for one-off "now" comparisons against Manila's calendar day. */
    public static ZonedDateTime nowInManila() {
        return ZonedDateTime.now(ZONE_MANILA);
    }
}
