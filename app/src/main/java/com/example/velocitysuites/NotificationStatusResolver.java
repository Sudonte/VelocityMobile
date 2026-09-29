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

    /**
     * The pure, Context-free decision this whole class exists to make - separated from
     * resolve() purely so it's JVM-unit-testable (this project has no Robolectric, so
     * anything touching Context/R.string directly can't be - see
     * NotificationStatusResolverTest, and PaymentStatusResolver's identical pattern).
     * resolve() below is a thin Context-string/color lookup over this result; keep the
     * two in lockstep if either changes.
     */
    public enum StatusKey {
        CANCELLED, REJECTED, PENDING, CHECKED_IN, CHECKED_OUT, CONFIRMED, FULLY_PAID
    }

    public static StatusKey resolveKey(String title) {
        if (title == null) return null;
        String lower = title.toLowerCase(Locale.US);

        if (lower.contains("cancelled")) return StatusKey.CANCELLED;
        if (lower.contains("rejected")) return StatusKey.REJECTED;
        if (lower.contains("pending")) return StatusKey.PENDING;
        if (lower.contains("checked in")) return StatusKey.CHECKED_IN;
        if (lower.contains("checked out")) return StatusKey.CHECKED_OUT;
        if (lower.contains("confirmed")) return StatusKey.CONFIRMED;
        if (lower.contains("complete") || lower.contains("received") || lower.contains("recorded")) return StatusKey.FULLY_PAID;
        return null;
    }

    public static Result resolve(Context ctx, Notification notification) {
        StatusKey key = resolveKey(notification.getTitle());
        if (key == null) return null;

        switch (key) {
            case CANCELLED:
                return new Result(ctx.getString(R.string.status_cancelled), R.color.velocity_red_subtle, R.color.velocity_red_dark);
            case REJECTED:
                return new Result(ctx.getString(R.string.status_rejected), R.color.velocity_red_subtle, R.color.velocity_red_dark);
            case PENDING:
                return new Result(ctx.getString(R.string.status_pending_label), R.color.velocity_orange_soft, R.color.velocity_orange_primary);
            case CHECKED_IN:
                return new Result(ctx.getString(R.string.status_checked_in), R.color.velocity_green_soft, R.color.velocity_green_dark);
            case CHECKED_OUT:
                return new Result(ctx.getString(R.string.status_checked_out), R.color.velocity_green_soft, R.color.velocity_green_dark);
            case CONFIRMED:
                return new Result(ctx.getString(R.string.status_confirmed), R.color.velocity_green_soft, R.color.velocity_green_dark);
            case FULLY_PAID:
            default:
                return new Result(ctx.getString(R.string.status_fully_paid), R.color.velocity_green_soft, R.color.velocity_green_dark);
        }
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
