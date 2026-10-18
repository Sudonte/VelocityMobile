package com.example.velocitysuites;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The short time on a notification row, in normal case: "Just now", "4m ago", "3h ago", "Yesterday", "Oct 18" (and
 * "Oct 18, 2025" for an earlier year). Days are Asia/Manila calendar days, the app's one clock. The clock is a parameter so
 * the boundaries are unit-testable.
 */
final class NotificationTimeText {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");
    private static final DateTimeFormatter SAME_YEAR = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter OTHER_YEAR = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    private NotificationTimeText() {
    }

    /** @param fallback shown when the notification has no instant at all (the oldest shape a row can have); may be null */
    @NonNull
    static String of(@NonNull Context context, long createdAtMillis, long nowMillis, @Nullable String fallback) {
        if (createdAtMillis <= 0) return fallback == null ? "" : fallback;
        long minutes = Math.max(0, nowMillis - createdAtMillis) / 60_000L;
        if (minutes < 1) return context.getString(R.string.notif_time_just_now);
        if (minutes < 60) return context.getString(R.string.notif_time_minutes_format, (int) minutes);
        long hours = minutes / 60;
        if (hours < 24) return context.getString(R.string.notif_time_hours_format, (int) hours);
        LocalDate then = Instant.ofEpochMilli(createdAtMillis).atZone(MANILA).toLocalDate();
        LocalDate today = Instant.ofEpochMilli(nowMillis).atZone(MANILA).toLocalDate();
        if (then.equals(today.minusDays(1))) return context.getString(R.string.notif_time_yesterday);
        return then.getYear() == today.getYear() ? SAME_YEAR.format(then) : OTHER_YEAR.format(then);
    }
}
