package com.example.velocitysuites;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Single source of truth for "how many nights is this stay" - extracted from the six
 * near-identical copies that had accumulated across BillingSummaryActivity,
 * BookingDetailsActivity (x2), PaymentActivity, PaymentReceiptActivity and
 * BookingAndReservationActivity (all Activities, so none of those copies were
 * JVM-testable - see StayDateCalculatorTest). Every copy used the same formula
 * (checkOut - checkIn in whole days, clamped to a minimum of 1 night) and the same
 * "MMM dd, yyyy" display-date format where a String date was involved; this class
 * doesn't change that formula or format, only consolidates it.
 *
 * <p>Two null/parse-failure behaviors are preserved, matching what each call site
 * actually needs: {@link #nightsBetween} always returns a usable number (1) for
 * feeding straight into a price calculation, while {@link #nightsBetweenOrNull}
 * returns null so a display row can hide itself rather than show a fabricated "1
 * night" for data that was never actually there.
 */
public final class StayDateCalculator {

    /** Every date the app formats/parses a stay's check-in/check-out as (e.g. "May 01, 2025"). */
    private static final String DISPLAY_DATE_PATTERN = "MMM dd, yyyy";

    private StayDateCalculator() {
    }

    /** Whole nights between two Calendar instants, never less than 1. Null-safe: either being null returns 1 (the wizard's own "no dates chosen yet" default). */
    public static long nightsBetween(Calendar checkIn, Calendar checkOut) {
        if (checkIn == null || checkOut == null) return 1;
        long diff = checkOut.getTimeInMillis() - checkIn.getTimeInMillis();
        return Math.max(1, diff / (24L * 60 * 60 * 1000));
    }

    /** Whole nights between two "MMM dd, yyyy" display dates, never less than 1 - returns 1 (not an exception) on a null/unparseable input, since every caller of this overload feeds the result straight into a price calculation. */
    public static long nightsBetween(String checkInDisplay, String checkOutDisplay) {
        Long parsed = parseNights(checkInDisplay, checkOutDisplay);
        return parsed != null ? parsed : 1L;
    }

    /** Same formula as {@link #nightsBetween(String, String)}, but returns null instead of 1 on a null/unparseable input - for a display row that should hide itself rather than show a fabricated night count. */
    public static Long nightsBetweenOrNull(String checkInDisplay, String checkOutDisplay) {
        return parseNights(checkInDisplay, checkOutDisplay);
    }

    private static Long parseNights(String checkInDisplay, String checkOutDisplay) {
        if (checkInDisplay == null || checkOutDisplay == null) return null;
        try {
            SimpleDateFormat format = new SimpleDateFormat(DISPLAY_DATE_PATTERN, Locale.US);
            long diff = format.parse(checkOutDisplay).getTime() - format.parse(checkInDisplay).getTime();
            return Math.max(1, diff / (24L * 60 * 60 * 1000));
        } catch (ParseException | NullPointerException e) {
            return null;
        }
    }
}
