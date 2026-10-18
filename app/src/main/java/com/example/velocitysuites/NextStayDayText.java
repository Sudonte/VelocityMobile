package com.example.velocitysuites;

import android.content.Context;

import androidx.annotation.NonNull;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * The one short line above the Your Next Stay cards. The selected chip and its badge already say which day and how many
 * stays, so the line only says how far that day is from Today:
 * <ul>
 *   <li>the nearest day that has a stay: "Your next stay · in 2 days" (or "· today", "· tomorrow")</li>
 *   <li>any other day: "Today", "Tomorrow", "Yesterday", "In 5 days", "2 days ago"</li>
 * </ul>
 * Days are plain calendar dates in hotel time, so the words are right whatever the phone's time zone.
 */
final class NextStayDayText {

    private NextStayDayText() {
    }

    @NonNull
    static String line(@NonNull Context c, @NonNull LocalDate selected, @NonNull LocalDate today, boolean nearestStayDay) {
        long diff = ChronoUnit.DAYS.between(today, selected);
        if (nearestStayDay && diff >= 0) {
            if (diff == 0) return c.getString(R.string.next_stay_ctx_next_today);
            if (diff == 1) return c.getString(R.string.next_stay_ctx_next_tomorrow);
            return c.getResources().getQuantityString(R.plurals.next_stay_ctx_next_in_days, (int) diff, (int) diff);
        }
        if (diff == 0) return c.getString(R.string.next_stay_day_today);
        if (diff == 1) return c.getString(R.string.next_stay_day_tomorrow);
        if (diff == -1) return c.getString(R.string.next_stay_day_yesterday);
        if (diff > 1) return c.getResources().getQuantityString(R.plurals.next_stay_ctx_in_days, (int) diff, (int) diff);
        return c.getResources().getQuantityString(R.plurals.next_stay_ctx_days_ago, (int) -diff, (int) -diff);
    }
}
