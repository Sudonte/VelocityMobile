package com.example.velocitysuites;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * The words of the shared stay card, in one place so every list says them the same way: the compact one-line dates
 * ("Oct 20 → Oct 21, 2026") and the "1 night · 2 guests" line under them. Dates and nights are kept on their own lines so
 * a number is never split from its word.
 */
final class StayCardText {

    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    private StayCardText() {
    }

    /** "Oct 20 → Oct 21, 2026" within one year, "Dec 30, 2026 → Jan 2, 2027" across two. */
    @NonNull
    static String compactDates(@NonNull LocalDate in, @NonNull LocalDate out) {
        if (in.getYear() == out.getYear()) return MONTH_DAY.format(in) + " → " + FULL.format(out);
        return FULL.format(in) + " → " + FULL.format(out);
    }

    /** The compact dates of a transaction, or its raw dates when they cannot be read, or "" when there are none. */
    @NonNull
    static String dates(@NonNull Booking b) {
        LocalDate in = PaymentDates.parseDate(b.getCheckInDate());
        LocalDate out = PaymentDates.parseDate(b.getCheckOutDate());
        if (in != null && out != null) return compactDates(in, out);
        String a = clean(b.getCheckInDate());
        String c = clean(b.getCheckOutDate());
        if (a.isEmpty() && c.isEmpty()) return "";
        if (c.isEmpty()) return a;
        if (a.isEmpty()) return c;
        return a + " → " + c;
    }

    /** "1 night · 2 guests"; either part is left out when unknown, "" when both are. */
    @NonNull
    static String nightsAndGuests(@NonNull Context context, @NonNull Booking b) {
        LocalDate in = PaymentDates.parseDate(b.getCheckInDate());
        LocalDate out = PaymentDates.parseDate(b.getCheckOutDate());
        String nights = "";
        if (in != null && out != null) {
            int n = (int) Math.max(1, ChronoUnit.DAYS.between(in, out));
            nights = context.getResources().getQuantityString(R.plurals.receipt_nights_count, n, n);
        }
        String guests = b.getGuests() > 0
                ? context.getResources().getQuantityString(R.plurals.guests_count, b.getGuests(), b.getGuests()) : "";
        if (nights.isEmpty()) return guests;
        if (guests.isEmpty()) return nights;
        return context.getString(R.string.stay_card_nights_guests_format, nights, guests);
    }

    /** "Oct 20 → Oct 21, 2026 · 1 night" - the dates, then the length of the stay; either part alone when the other is unknown. */
    @NonNull
    static String datesAndNights(@NonNull Context context, @NonNull Booking b) {
        String dates = dates(b);
        LocalDate in = PaymentDates.parseDate(b.getCheckInDate());
        LocalDate out = PaymentDates.parseDate(b.getCheckOutDate());
        if (in == null || out == null) return dates;
        int n = (int) Math.max(1, ChronoUnit.DAYS.between(in, out));
        String nights = context.getResources().getQuantityString(R.plurals.receipt_nights_count, n, n);
        return dates.isEmpty() ? nights : context.getString(R.string.recent_booking_dates_format, dates, nights);
    }

    /** "Deluxe · 1 guest" / "Deluxe, Executive · 2 guests": the room type(s), then how many guests; either part alone when the other is unknown. */
    @NonNull
    static String roomsAndGuests(@NonNull Context context, @NonNull Booking b) {
        String rooms = TransactionRow.roomTextOf(b).replace(" • ", ", ");
        String guests = b.getGuests() > 0
                ? context.getResources().getQuantityString(R.plurals.guests_count_lower, b.getGuests(), b.getGuests()) : "";
        if (rooms.isEmpty()) return guests;
        if (guests.isEmpty()) return rooms;
        return rooms + " · " + guests;
    }

    @NonNull
    private static String clean(@Nullable String s) {
        return s == null ? "" : s.trim();
    }
}
