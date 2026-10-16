package com.example.velocitysuites;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/**
 * What the Dashboard's Stay Calendar shows: every non-cancelled, not-yet-finished transaction placed on its
 * check-in day and its check-out day, grouped by date, plus the Daily / Weekly / Monthly window used to list
 * them. Pure data and rules (days are hotel-local calendar days, passed in), so it is unit-testable.
 *
 * Several transactions on the same day are all kept - nothing is collapsed to "the" transaction of a day.
 * Check-in and check-out use the hotel's posted times (2:00 PM / 12:00 PM), shown as HH:MM:SS.
 */
public final class StayCalendarModel {

    public enum Mode { DAILY, WEEKLY, MONTHLY }

    public static final LocalTime CHECK_IN_TIME = LocalTime.of(14, 0, 0);
    public static final LocalTime CHECK_OUT_TIME = LocalTime.of(12, 0, 0);

    private static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.ENGLISH);

    /** "02:00:00 PM". */
    public static String formatTime(LocalTime time) {
        return time.format(HMS);
    }

    @Nullable
    private static java.time.LocalDateTime hotelTime(@Nullable String raw) {
        java.time.Instant instant = TimeUtils.parseInstant(raw);
        return instant == null ? null : instant.atZone(TimeUtils.ZONE_MANILA).toLocalDateTime();
    }

    /** One transaction on one day: either its arrival or its departure. */
    public static final class Entry {
        public final Booking booking;
        public final LocalDate date;
        public final boolean checkIn;
        /** The recorded moment the receptionist did it (hotel time), or null when it has not happened yet. */
        @Nullable public final java.time.LocalDateTime actual;

        Entry(Booking booking, LocalDate date, boolean checkIn, @Nullable java.time.LocalDateTime actual) {
            this.booking = booking;
            this.date = actual != null ? actual.toLocalDate() : date;
            this.checkIn = checkIn;
            this.actual = actual;
        }

        /** True once this check-in / check-out really happened - its time is then the recorded one, not the posted 2 PM / 12 PM. */
        public boolean isActual() {
            return actual != null;
        }

        public LocalTime time() {
            if (actual != null) return actual.toLocalTime();
            return checkIn ? CHECK_IN_TIME : CHECK_OUT_TIME;
        }

        /** "02:00:00 PM" - hours, minutes and seconds. */
        public String timeText() {
            return time().format(HMS);
        }
    }

    private final TreeMap<LocalDate, List<Entry>> byDate = new TreeMap<>();

    public StayCalendarModel(@Nullable List<Booking> bookings) {
        if (bookings == null) return;
        for (Booking b : bookings) {
            if (b == null) continue;
            if (TransactionCategorizer.categorize(b) == TransactionCategorizer.Category.CANCELLED) continue;
            String status = b.getStatus() == null ? "" : b.getStatus();
            boolean finished = "Checked-Out".equalsIgnoreCase(status) || "Completed".equalsIgnoreCase(status);
            LocalDate in = PaymentDates.parseDate(b.getCheckInDate());
            LocalDate out = PaymentDates.parseDate(b.getCheckOutDate());
            if (in == null || out == null) continue;
            // A stay that has happened shows the moments the receptionist recorded (to the second); one that has not
            // yet falls back to the hotel's posted times.
            java.time.LocalDateTime actualIn = hotelTime(b.getCheckedInAtRaw());
            java.time.LocalDateTime actualOut = hotelTime(b.getCheckedOutAtRaw());
            if (finished && actualIn == null && actualOut == null) continue; // finished with nothing recorded: nothing to show
            boolean inHouse = "Checked-In".equalsIgnoreCase(status);
            if (actualIn != null || !inHouse) add(new Entry(b, in, true, actualIn));
            add(new Entry(b, out, false, finished ? actualOut : null));
        }
        Comparator<Entry> byTime = Comparator.comparing((Entry e) -> e.time())
                .thenComparing(e -> e.booking.getDisplayId() == null ? "" : e.booking.getDisplayId());
        for (List<Entry> day : byDate.values()) Collections.sort(day, byTime);
    }

    private void add(Entry e) {
        byDate.computeIfAbsent(e.date, d -> new ArrayList<>()).add(e);
    }

    /** True when at least one transaction arrives or leaves on that day (the dot on the calendar). */
    public boolean hasEntries(@NonNull LocalDate day) {
        return byDate.containsKey(day);
    }

    /** Every entry from start to end, both included, in date then time order. */
    @NonNull
    public List<Entry> entriesBetween(@NonNull LocalDate start, @NonNull LocalDate end) {
        List<Entry> out = new ArrayList<>();
        for (List<Entry> day : byDate.subMap(start, true, end, true).values()) out.addAll(day);
        return out;
    }

    /** First day of the window for the chosen mode: the day itself, its Sunday-start week (the grid's first column), or its month. */
    @NonNull
    public static LocalDate windowStart(@NonNull Mode mode, @NonNull LocalDate anchor) {
        switch (mode) {
            case WEEKLY: return anchor.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SUNDAY));
            case MONTHLY: return anchor.withDayOfMonth(1);
            default: return anchor;
        }
    }

    /** Last day of the window (inclusive). */
    @NonNull
    public static LocalDate windowEnd(@NonNull Mode mode, @NonNull LocalDate anchor) {
        switch (mode) {
            case WEEKLY: return windowStart(Mode.WEEKLY, anchor).plusDays(6);
            case MONTHLY: return anchor.withDayOfMonth(anchor.lengthOfMonth());
            default: return anchor;
        }
    }

    @NonNull
    public List<Entry> entriesFor(@NonNull Mode mode, @NonNull LocalDate anchor) {
        return entriesBetween(windowStart(mode, anchor), windowEnd(mode, anchor));
    }
}
