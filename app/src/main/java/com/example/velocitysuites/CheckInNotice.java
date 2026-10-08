package com.example.velocitysuites;

import android.content.Context;

/** User-facing wording of the advance check-in rule; the date is always computed from {@link CheckInWindow}, never hard-coded. */
public final class CheckInNotice {

    private CheckInNotice() {
    }

    /** "Check-in must be booked at least 2 days (48 hours) in advance. Earliest available check-in: Sat, Oct 10, 2026." */
    public static String notice(Context context) {
        return context.getString(R.string.checkin_advance_notice,
                CheckInWindow.ADVANCE_DAYS, CheckInWindow.ADVANCE_DAYS * 24, CheckInWindow.earliestForDisplay());
    }

    /** Inline error for a check-in that is no longer (or never was) inside the window. */
    public static String outsideWindowError(Context context) {
        return context.getString(R.string.error_checkin_outside_window, CheckInWindow.earliestForDisplay());
    }
}
