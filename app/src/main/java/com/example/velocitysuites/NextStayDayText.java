package com.example.velocitysuites;

import android.content.Context;

import androidx.annotation.NonNull;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * The one context line above the Your Next Stay cards: "Tue, Oct 20 · Your next stay · In 2 days · 1 stay". The date,
 * how far that day is from Today (Today / Tomorrow / Yesterday / In N days / N days ago, prefixed "Your next stay · "
 * when it is the nearest day that has a stay), and how many stays are on it. Days are plain calendar dates in hotel
 * time, so the words are right whatever the phone's time zone.
 */
final class NextStayDayText {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH);

    private NextStayDayText() {
    }

    /** "Today", "Tomorrow", "Yesterday", "In 2 days", "3 days ago"; "Your next stay · In 2 days" for the nearest later stay day. */
    @NonNull
    static String relative(@NonNull Context c, @NonNull LocalDate selected, @NonNull LocalDate today, boolean nearestStayDay) {
        long diff = ChronoUnit.DAYS.between(today, selected);
        String when;
        if (diff == 0) when = c.getString(R.string.next_stay_day_today);
        else if (diff == 1) when = c.getString(R.string.next_stay_day_tomorrow);
        else if (diff == -1) when = c.getString(R.string.next_stay_day_yesterday);
        else if (diff > 1) when = c.getResources().getQuantityString(R.plurals.next_stay_ctx_in_days, (int) diff, (int) diff);
        else when = c.getResources().getQuantityString(R.plurals.next_stay_ctx_days_ago, (int) -diff, (int) -diff);
        if (nearestStayDay && diff > 0) return c.getString(R.string.next_stay_ctx_next_prefix, when);
        return when;
    }

    /** The whole line. */
    @NonNull
    static String line(@NonNull Context c, @NonNull LocalDate selected, @NonNull LocalDate today, boolean nearestStayDay, int stays) {
        String count = stays == 0 ? c.getString(R.string.next_stay_ctx_none)
                : c.getResources().getQuantityString(R.plurals.summary_stays_count, stays, stays);
        return c.getString(R.string.next_stay_ctx_format, DATE.format(selected), relative(c, selected, today, nearestStayDay), count);
    }
}
