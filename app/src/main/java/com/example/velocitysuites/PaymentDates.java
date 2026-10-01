package com.example.velocitysuites;

import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Parsing for the DISPLAY date strings the app keeps on a Booking/payment ("Sep 02, 2026 • 10:24 AM",
 * "Oct 01, 2026", and the two older shapes cached data may still hold). Every formatter is built once:
 * the list screens sort and filter hundreds of rows on the UI thread, and the code this replaces created
 * up to three SimpleDateFormat objects PER COMPARISON - on 400 rows that was thousands of formatter
 * constructions per keystroke.
 * <p>
 * Null/blank/unparseable input is simply "unknown" (0 / null), never an exception - one odd string must
 * not be able to take a screen down.
 */
final class PaymentDates {

    private PaymentDates() {
    }

    private static final DateTimeFormatter[] DATE_TIME = {
            DateTimeFormatter.ofPattern("MMM d, yyyy • h:mm a", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM dd, yyyy h:mm a", Locale.ENGLISH),
    };
    private static final DateTimeFormatter[] DATE_ONLY = {
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH),
    };

    /** Epoch millis of a payment/display date-time string in the device's zone (the zone the old SimpleDateFormat code used), or 0 when unknown. */
    static long parseMillis(@Nullable String text) {
        if (text == null) return 0;
        String s = text.trim();
        if (s.isEmpty()) return 0;
        for (DateTimeFormatter f : DATE_TIME) {
            try {
                return LocalDateTime.parse(s, f).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored) {
                // try the next shape
            }
        }
        LocalDate date = parseDate(s);
        return date != null ? date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() : 0;
    }

    /** A calendar date like "Oct 01, 2026" (the check-in/out display format), or null when unknown. Calendar-date arithmetic, not instants, so a time zone can never shift it by a day. */
    @Nullable
    static LocalDate parseDate(@Nullable String text) {
        if (text == null) return null;
        String s = text.trim();
        if (s.isEmpty()) return null;
        for (DateTimeFormatter f : DATE_ONLY) {
            try {
                return LocalDate.parse(s, f);
            } catch (DateTimeParseException ignored) {
                // try the next shape
            }
        }
        // A date-time string still carries a usable calendar date.
        for (DateTimeFormatter f : DATE_TIME) {
            try {
                return LocalDateTime.parse(s, f).toLocalDate();
            } catch (DateTimeParseException ignored) {
                // give up below
            }
        }
        return null;
    }
}
