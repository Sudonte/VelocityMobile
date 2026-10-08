package com.example.velocitysuites;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Calendar;

/**
 * Single source of truth for which check-in / check-out dates a guest may pick (mirrors the backend's
 * App\\Support\\CheckInWindow): the earliest check-in is {@link #MIN_DAYS_AHEAD} days from today, "today" always
 * being the hotel's local date (Asia/Manila - never the phone's own time zone); there is no upper limit; and
 * check-out must be at least one day after check-in. Every date picker that chooses a check-in (wizard step 1,
 * room browsing, the legacy inline form) and every validator goes through this class.
 */
public final class CheckInWindow {

    public static final ZoneId HOTEL_ZONE = ZoneId.of("Asia/Manila");
    /** The earliest check-in is today + 2 days. */
    public static final int MIN_DAYS_AHEAD = 2;

    private CheckInWindow() {
    }

    private static Clock hotelClock() {
        return Clock.system(HOTEL_ZONE);
    }

    /** The hotel's current local date. */
    public static LocalDate today() {
        return today(hotelClock());
    }

    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(HOTEL_ZONE));
    }

    public static LocalDate earliest(Clock clock) {
        return today(clock).plusDays(MIN_DAYS_AHEAD);
    }

    public static LocalDate earliest() {
        return earliest(hotelClock());
    }

    /** The earliest check-out for a given check-in: the next day. */
    public static LocalDate earliestCheckOut(LocalDate checkIn) {
        return checkIn.plusDays(1);
    }

    public static boolean isAllowed(LocalDate date, Clock clock) {
        return date != null && !date.isBefore(earliest(clock));
    }

    public static boolean isAllowed(LocalDate date) {
        return isAllowed(date, hotelClock());
    }

    /** Check-out must be strictly after check-in (at least one night). */
    public static boolean isValidCheckOut(LocalDate checkIn, LocalDate checkOut) {
        return checkIn != null && checkOut != null && !checkOut.isBefore(earliestCheckOut(checkIn));
    }

    /** True when the Calendar's own year/month/day are an allowed check-in (the wizard stores picked dates as device-zone midnights, so only the date fields matter). */
    public static boolean isAllowed(Calendar picked) {
        return picked != null && isAllowed(toLocalDate(picked));
    }

    public static LocalDate toLocalDate(Calendar c) {
        return LocalDate.of(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    /** Midnight of the given calendar date in the phone's default zone - what DatePickerDialog's min millis and the wizard's Calendar fields use. */
    public static Calendar toDeviceMidnight(LocalDate date) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth(), 0, 0, 0);
        return c;
    }

    public static long earliestDeviceMillis() {
        return toDeviceMidnight(earliest(hotelClock())).getTimeInMillis();
    }

    /** UTC-midnight millis, the unit MaterialDatePicker and its validators use. */
    public static long toUtcMillis(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    public static long earliestUtcMillis() {
        return toUtcMillis(earliest(hotelClock()));
    }
}
