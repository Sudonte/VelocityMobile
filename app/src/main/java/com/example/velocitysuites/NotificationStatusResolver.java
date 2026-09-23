package com.example.velocitysuites;

import android.content.Context;

import java.util.Locale;

/**
 * Derives a coarse status pill (Confirmed/Complete/Cancelled/Rejected/Pending) from a
 * notification's title, since the notifications table has no separate status column -
 * the title text itself already encodes the outcome (e.g. "Reservation Confirmed",
 * "Payment Pending Validation"). Returns null when no status is implied (general/system
 * notifications), in which case no status pill should be shown.
 */
public final class NotificationStatusResolver {

    private NotificationStatusResolver() {
    }

    public static Result resolve(Context ctx, Notification notification) {
        String title = notification.getTitle();
        if (title == null) return null;
        String lower = title.toLowerCase(Locale.US);

        if (lower.contains("cancelled")) {
            return new Result(ctx.getString(R.string.status_cancelled), R.color.velocity_red_subtle, R.color.velocity_red_dark);
        }
        if (lower.contains("rejected")) {
            return new Result(ctx.getString(R.string.status_rejected), R.color.velocity_red_subtle, R.color.velocity_red_dark);
        }
        if (lower.contains("pending")) {
            return new Result(ctx.getString(R.string.status_pending_label), R.color.velocity_orange_soft, R.color.velocity_orange_primary);
        }
        if (lower.contains("checked in")) {
            return new Result(ctx.getString(R.string.status_checked_in), R.color.velocity_green_soft, R.color.velocity_green_dark);
        }
        if (lower.contains("checked out")) {
            return new Result(ctx.getString(R.string.status_checked_out), R.color.velocity_green_soft, R.color.velocity_green_dark);
        }
        if (lower.contains("confirmed")) {
            return new Result(ctx.getString(R.string.status_confirmed), R.color.velocity_green_soft, R.color.velocity_green_dark);
        }
        if (lower.contains("complete") || lower.contains("received") || lower.contains("recorded")) {
            return new Result(ctx.getString(R.string.status_fully_paid), R.color.velocity_green_soft, R.color.velocity_green_dark);
        }
        return null;
    }

    public static final class Result {
        public final String label;
        public final int bgColorRes;
        public final int fgColorRes;

        Result(String label, int bgColorRes, int fgColorRes) {
            this.label = label;
            this.bgColorRes = bgColorRes;
            this.fgColorRes = fgColorRes;
        }
    }
}
