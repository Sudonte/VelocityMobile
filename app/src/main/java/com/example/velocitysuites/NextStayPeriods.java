package com.example.velocitysuites;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The week and month chips of "Your Next Stay" and the words around them. The windows are the Stay Calendar's own
 * ({@link StayCalendarModel#windowStart}, Sunday-start weeks and calendar months) and the transactions of a window come
 * from {@link StayCalendarModel#transactionsFor}, so the two sections can never disagree. Days are hotel-local dates.
 */
final class NextStayPeriods {

    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_YEAR_SHORT = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_YEAR_LONG = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private NextStayPeriods() {
    }

    /** The first day of every chip: a month back (weeks) / three months back (months) through two / six ahead, widened to cover every stay. */
    @NonNull
    static List<LocalDate> starts(@NonNull StayCalendarModel.Mode mode, @Nullable List<Booking> bookings, @NonNull LocalDate today) {
        boolean weekly = mode == StayCalendarModel.Mode.WEEKLY;
        LocalDate first = today.minus(weekly ? 4 : 3, weekly ? ChronoUnit.WEEKS : ChronoUnit.MONTHS);
        LocalDate last = today.plus(weekly ? 8 : 6, weekly ? ChronoUnit.WEEKS : ChronoUnit.MONTHS);
        if (bookings != null) {
            for (Booking b : bookings) {
                if (b == null || StayCalendarModel.isClosed(b)) continue;
                LocalDate in = PaymentDates.parseDate(b.getCheckInDate());
                LocalDate out = PaymentDates.parseDate(b.getCheckOutDate());
                if (in != null && in.isBefore(first) && in.isAfter(today.minusDays(400))) first = in;
                if (out != null && out.isAfter(last) && out.isBefore(today.plusDays(400))) last = out;
            }
        }
        List<LocalDate> out = new ArrayList<>();
        LocalDate end = StayCalendarModel.windowStart(mode, last);
        for (LocalDate d = StayCalendarModel.windowStart(mode, first); !d.isAfter(end);
             d = weekly ? d.plusWeeks(1) : d.plusMonths(1)) {
            out.add(d);
        }
        return out;
    }

    /** How many weeks (or months) from today's window the window of {@code anchor} is: 0 this one, 1 the next, -1 the last. */
    static int offset(@NonNull StayCalendarModel.Mode mode, @NonNull LocalDate anchor, @NonNull LocalDate today) {
        LocalDate a = StayCalendarModel.windowStart(mode, anchor);
        LocalDate t = StayCalendarModel.windowStart(mode, today);
        return (int) (mode == StayCalendarModel.Mode.WEEKLY ? ChronoUnit.WEEKS.between(t, a) : ChronoUnit.MONTHS.between(t, a));
    }

    static boolean isCurrent(@NonNull StayCalendarModel.Mode mode, @NonNull LocalDate anchor, @NonNull LocalDate today) {
        return offset(mode, anchor, today) == 0;
    }

    /** "Nov 2–8", or "Oct 26–Nov 1" across two months. */
    @NonNull
    static String weekRange(@NonNull LocalDate anyDayOfWeek) {
        LocalDate start = StayCalendarModel.windowStart(StayCalendarModel.Mode.WEEKLY, anyDayOfWeek);
        LocalDate end = start.plusDays(6);
        if (start.getMonth() == end.getMonth()) return MONTH_DAY.format(start) + "–" + end.getDayOfMonth();
        return MONTH_DAY.format(start) + "–" + MONTH_DAY.format(end);
    }

    /** The chip's top line: "This week" / "Next week" / "Last week", else "Nov 2–8"; "This month"-style names are kept for the second line. */
    @NonNull
    static String title(@NonNull Context c, @NonNull StayCalendarModel.Mode mode, @NonNull LocalDate start, @NonNull LocalDate today) {
        if (mode == StayCalendarModel.Mode.WEEKLY) {
            int offset = offset(mode, start, today);
            if (offset == 0) return c.getString(R.string.next_stay_period_this_week);
            if (offset == 1) return c.getString(R.string.next_stay_period_next_week);
            if (offset == -1) return c.getString(R.string.next_stay_period_last_week);
            return weekRange(start);
        }
        return MONTH_YEAR_SHORT.format(start);
    }

    /** The chip's second line: the date range for the named weeks, the year for the others; "This month" and its neighbours for months. */
    @NonNull
    static String subtitle(@NonNull Context c, @NonNull StayCalendarModel.Mode mode, @NonNull LocalDate start, @NonNull LocalDate today) {
        int offset = offset(mode, start, today);
        if (mode == StayCalendarModel.Mode.WEEKLY) {
            return offset >= -1 && offset <= 1 ? weekRange(start) : String.valueOf(start.plusDays(6).getYear());
        }
        if (offset == 0) return c.getString(R.string.next_stay_period_this_month);
        if (offset == 1) return c.getString(R.string.next_stay_period_next_month);
        if (offset == -1) return c.getString(R.string.next_stay_period_last_month);
        return "";
    }

    /** The name of the selected window in the context line: "This week", "Nov 2–8", "October 2026". */
    @NonNull
    static String label(@NonNull Context c, @NonNull StayCalendarModel.Mode mode, @NonNull LocalDate anchor, @NonNull LocalDate today) {
        if (mode == StayCalendarModel.Mode.WEEKLY) return title(c, mode, StayCalendarModel.windowStart(mode, anchor), today);
        return MONTH_YEAR_LONG.format(anchor);
    }

    /** "This week · 3 stays", "October 2026 · 5 stays". */
    @NonNull
    static String contextLine(@NonNull Context c, @NonNull StayCalendarModel.Mode mode, @NonNull LocalDate anchor,
                              @NonNull LocalDate today, int count) {
        return c.getResources().getQuantityString(R.plurals.next_stay_period_context, count, label(c, mode, anchor, today), count);
    }

    /** The jump button's text: "Today", "This week" or "This month". */
    @NonNull
    static String jumpLabel(@NonNull Context c, @NonNull StayCalendarModel.Mode mode) {
        switch (mode) {
            case WEEKLY: return c.getString(R.string.next_stay_period_this_week);
            case MONTHLY: return c.getString(R.string.next_stay_period_this_month);
            default: return c.getString(R.string.next_stay_jump_today);
        }
    }
}
