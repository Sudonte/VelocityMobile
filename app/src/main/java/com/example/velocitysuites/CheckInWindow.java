package com.example.velocitysuites;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Calendar;

/**
 * Single source of truth for which check-in dates a guest may pick: today
 * through {@link #MAX_DAYS_AHEAD} days after today, where "today" is always
 * the hotel's local date (Asia/Manila) - never the phone's own time zone - so
 * the app and the server (see the matching rule in the Laravel API's
 * CheckInWindow) agree on which date "today" is. Every date picker that
 * chooses a check-in (wizard step 1, room browsing, the legacy inline form)
 * and every validator must go through this class instead of re-deriving the
 * window.
 */
public final class CheckInWindow {

    public static final ZoneId HOTEL_ZONE = ZoneId.of("Asia/Manila");
    /** The guest may pick today, today+1 or today+2. */
    public static final int MAX_DAYS_AHEAD = 2;

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
        return today(clock);
    }

    public static LocalDate latest(Clock clock) {
        return today(clock).plusDays(MAX_DAYS_AHEAD);
    }

    public static boolean isAllowed(LocalDate date, Clock clock) {
        return date != null && !date.isBefore(earliest(clock)) && !date.isAfter(latest(clock));
    }

    public static boolean isAllowed(LocalDate date) {
        return isAllowed(date, hotelClock());
    }

    /** True when the Calendar's own year/month/day fall inside the window (the wizard stores picked dates as device-zone midnights, so only the date fields matter). */
    public static boolean isAllowed(Calendar picked) {
        return picked != null && isAllowed(toLocalDate(picked));
    }

    public static LocalDate toLocalDate(Calendar c) {
        return LocalDate.of(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    /** Midnight of the given calendar date in the phone's default zone - what DatePickerDialog's min/max millis and the wizard's Calendar fields use. */
    public static Calendar toDeviceMidnight(LocalDate date) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth(), 0, 0, 0);
        return c;
    }

    public static long earliestDeviceMillis() {
        return toDeviceMidnight(earliest(hotelClock())).getTimeInMillis();
    }

    public static long latestDeviceMillis() {
        return toDeviceMidnight(latest(hotelClock())).getTimeInMillis();
    }

    /** UTC-midnight millis, the unit MaterialDatePicker and its validators use. */
    public static long toUtcMillis(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    public static long earliestUtcMillis() {
        return toUtcMillis(earliest(hotelClock()));
    }

    public static long latestUtcMillis() {
        return toUtcMillis(latest(hotelClock()));
    }
}
