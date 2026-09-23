package com.example.velocitysuites;

import android.os.SystemClock;
import android.view.View;

/**
 * Click-debounce wrapper for outbound navigation listeners that call
 * startActivity() directly (i.e. don't already go through
 * BaseNavigationActivity#openScreen(), which applies its own
 * FLAG_ACTIVITY_CLEAR_TOP|SINGLE_TOP). A fast double-tap on a card/button
 * before the new Activity actually opens would otherwise fire the listener
 * twice and push two stacked instances of the destination screen. A single
 * shared timestamp is intentional - the concern is a physical double-tap on
 * any one control, not independent per-view state.
 */
public final class NavUtils {

    private static final long DEBOUNCE_MS = 600;
    private static long lastClickAtMs = 0L;

    private NavUtils() {}

    public static View.OnClickListener debounce(View.OnClickListener listener) {
        return v -> {
            if (!allowClick()) return;
            listener.onClick(v);
        };
    }

    /**
     * Same debounce check as {@link #debounce}, for navigation methods invoked from
     * several existing click/dialog callbacks rather than one that can be wrapped
     * directly at setOnClickListener() time. Returns true (and starts a fresh
     * debounce window) at most once per DEBOUNCE_MS.
     */
    public static boolean allowClick() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastClickAtMs < DEBOUNCE_MS) return false;
        lastClickAtMs = now;
        return true;
    }
}
