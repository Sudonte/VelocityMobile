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

    /** The agreement checkbox is enabled only after the guest has opened the Terms. */
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
