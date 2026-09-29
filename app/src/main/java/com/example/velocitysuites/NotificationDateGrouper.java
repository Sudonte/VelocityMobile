package com.example.velocitysuites;

import android.content.Context;
import java.util.Calendar;

/**
 * Buckets a notification's raw created_at into "Today"/"Yesterday"/"Earlier" for
 * NotificationAdapter's inline date-group headers. daysBetween() takes both
 * timestamps explicitly (not System.currentTimeMillis() internally) so the
 * calendar-day boundary logic is deterministically unit-testable. A notification
 * with no raw timestamp (createdAtMillis <= 0 - any Notification built by a
 * constructor that predates this field) falls back to "Earlier" rather than
 * mis-bucketing as "Today".
 */
public final class NotificationDateGrouper {

    private NotificationDateGrouper() {
    }

    public static String groupLabel(Context ctx, long createdAtMillis, long nowMillis) {
        if (createdAtMillis <= 0) {
            return ctx.getString(R.string.notif_date_group_earlier);
        }
        int daysAgo = daysBetween(createdAtMillis, nowMillis);
        if (daysAgo == 0) return ctx.getString(R.string.notif_date_group_today);
        if (daysAgo == 1) return ctx.getString(R.string.notif_date_group_yesterday);
        return ctx.getString(R.string.notif_date_group_earlier);
    }

    /**
     * Calendar-day difference in the device's local timezone, not a raw 24h
     * division - "yesterday 11pm" vs "today 1am" is 1 calendar day apart despite
     * being under 24 hours. Package-visible for direct unit testing without a
     * Context. Always non-negative regardless of argument order.
     */
    static int daysBetween(long millisA, long millisB) {
        Calendar earlier = Calendar.getInstance();
        earlier.setTimeInMillis(Math.min(millisA, millisB));
        stripTime(earlier);
        Calendar later = Calendar.getInstance();
        later.setTimeInMillis(Math.max(millisA, millisB));
        stripTime(later);
        long diffMs = later.getTimeInMillis() - earlier.getTimeInMillis();
        return (int) (diffMs / (24L * 60 * 60 * 1000));
    }

    private static void stripTime(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }
}
