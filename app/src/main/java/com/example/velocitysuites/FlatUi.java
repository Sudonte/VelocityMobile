package com.example.velocitysuites;

import android.view.View;

import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

/** Small helpers for the flat (shadow-free) Booking / Reservation look that a theme alone cannot apply. */
public final class FlatUi {

    private FlatUi() {
    }

    /**
     * SwipeRefreshLayout's spinner is a library-owned round view with a built-in drop shadow
     * (it is the layout's first child). Zero its elevation so pull-to-refresh matches the
     * flat screens it sits on.
     */
    public static void removeSpinnerShadow(@Nullable SwipeRefreshLayout layout) {
        if (layout == null || layout.getChildCount() == 0) return;
        View spinner = layout.getChildAt(0);
        // Only the library's own spinner (not the scrolling content) is a leaf image view.
        if (spinner instanceof android.widget.ImageView) {
            spinner.setElevation(0f);
        }
    }
}
