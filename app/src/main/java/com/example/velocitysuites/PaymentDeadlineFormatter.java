package com.example.velocitysuites;

import android.content.Context;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Formats Booking#getPaymentDeadline() (ISO-8601 UTC, from Reservation::
 * payment_deadline - backend) into the "Pay by ..." chip shown on a still-
 * unpaid reservation's card. Only ever non-null while the 2-day/48-hour
 * rule actually applies (see that accessor's docblock) - a paid/converted
 * or tomorrow-check-in reservation always has payment_deadline == null, and
 * an already-expired one has been flipped to Cancelled server-side by then
 * (ReservationWorkflowService::expireUnpaid()), so this only ever needs to
 * render a deadline that's still in the future.
 */
public final class PaymentDeadlineFormatter {

    // Backend sends Carbon::toIso8601String() - always UTC with a numeric
    // offset (+00:00) rather than a literal "Z" suffix, unlike some other
    // timestamps elsewhere in this app - stripped below before parsing.
    private static final SimpleDateFormat ISO_UTC = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
    private static final SimpleDateFormat DISPLAY = new SimpleDateFormat("MMM dd, h:mm a", Locale.US);

    static {
        ISO_UTC.setTimeZone(TimeZone.getTimeZone("UTC"));
        DISPLAY.setTimeZone(TimeZone.getDefault());
    }

    private PaymentDeadlineFormatter() {
    }

    public static boolean hasActiveDeadline(Booking booking) {
        return booking != null && !booking.isHasBooking() && parse(booking.getPaymentDeadline()) != null;
    }

    /**
     * "Payment Due In: X Day(s) Y Hr(s)" - always recomputed from the real
     * server deadline at render time (every screen showing this already
     * re-renders on onResume()/refresh), so it never "resets" independently
     * of the actual deadline the way a local countdown timer would.
     */
    public static String formatChipText(Context ctx, Booking booking) {
        Date deadline = parse(booking.getPaymentDeadline());
        if (deadline == null) return "";

        long remainingMs = deadline.getTime() - System.currentTimeMillis();
        if (remainingMs <= 0) return ctx.getString(R.string.payment_due_in_expiring);

        long totalHours = (remainingMs + 59 * 60 * 1000) / (60 * 60 * 1000); // round up to the next hour
        long days = totalHours / 24;
        long hours = totalHours % 24;

        if (days > 0) {
            return ctx.getString(R.string.payment_due_in_days_hours_format,
                    days, days == 1 ? "" : "s", hours, hours == 1 ? "" : "s");
        }
        if (hours > 0) {
            return ctx.getString(R.string.payment_due_in_hours_format, hours, hours == 1 ? "" : "s");
        }
        return ctx.getString(R.string.payment_due_in_expiring);
    }

    private static Date parse(String iso) {
        if (iso == null || iso.isEmpty()) return null;
        try {
            String cleaned = iso.replace("Z", "");
            int plusIdx = cleaned.indexOf('+', 10); // skip the date's own '-' separators
            if (plusIdx > 0) cleaned = cleaned.substring(0, plusIdx);
            if (cleaned.length() > 19) cleaned = cleaned.substring(0, 19);
            return ISO_UTC.parse(cleaned);
        } catch (ParseException e) {
            return null;
        }
    }
}
