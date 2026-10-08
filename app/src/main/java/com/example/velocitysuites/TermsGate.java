package com.example.velocitysuites;

import androidx.annotation.StringRes;

/**
 * The view-then-agree rules shared by every Terms and Policy consent (Booking, Reservation, Registration):
 * the box unlocks only after the Terms have been opened, and the main action unlocks only once the guest has
 * checked the box themselves. Kept free of Android views so the rules are unit-testable.
 */
public final class TermsGate {

    private TermsGate() {
    }

    /** Slack, in px, for sub-pixel rounding when judging whether the bottom of the document is visible. */
    private static final int END_SLACK_PX = 8;

    /**
     * True once the whole document has been seen: the guest scrolled to the end, or it fits on screen without
     * scrolling (then opening it is reading it).
     */
    public static boolean hasReachedEnd(int scrollY, int viewportHeight, int contentHeight) {
        if (viewportHeight <= 0 || contentHeight <= 0) return false; // not laid out yet
        return contentHeight <= viewportHeight || scrollY + viewportHeight >= contentHeight - END_SLACK_PX;
    }

    /** The agreement checkbox is enabled only after the guest has read the Terms to the end. */
    public static boolean isCheckboxEnabled(boolean viewed) {
        return viewed;
    }

    /** The main action (Confirm Booking / Confirm Reservation / Sign Up Now) needs both: opened and checked. */
    public static boolean isActionEnabled(boolean viewed, boolean accepted) {
        return viewed && accepted;
    }

    /** What is still missing, as the helper line under the main action; 0 when nothing is. */
    @StringRes
    public static int missingHintRes(boolean viewed, boolean accepted) {
        if (!viewed) return R.string.terms_missing_open;
        if (!accepted) return R.string.terms_missing_check;
        return 0;
    }
}
