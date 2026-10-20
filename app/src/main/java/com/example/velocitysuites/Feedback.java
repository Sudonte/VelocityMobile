package com.example.velocitysuites;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.snackbar.Snackbar;

/**
 * Short success messages ("Booking cancelled", "Payment submitted for verification", "Request sent") as a Snackbar - one place.
 * {@link #later} is for an action that closes its own screen: the message waits for the next screen that comes to the front.
 */
public final class Feedback implements Application.ActivityLifecycleCallbacks {

    private static final long LATER_MAX_AGE_MS = 8_000L;
    @Nullable private static String pending;
    private static long pendingAt;

    /** Shows {@code message} on {@code activity} now (does nothing for a screen that is going away). */
    public static void show(@NonNull Activity activity, @StringRes int message) {
        show(activity, activity.getString(message));
    }

    public static void show(@NonNull Activity activity, @NonNull String message) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        View anchor = activity.findViewById(android.R.id.content);
        if (anchor == null) return;
        Snackbar.make(anchor, message, Snackbar.LENGTH_LONG).show();
    }

    /** Shows {@code message} on the next screen to come to the front (within a few seconds). */
    public static void later(@NonNull String message) {
        pending = message;
        pendingAt = SystemClock.elapsedRealtime();
    }

    /** The message waiting for the next screen, if it has not gone stale; clears it. */
    @Nullable
    static String takePending() {
        String m = pending;
        pending = null;
        if (m == null || SystemClock.elapsedRealtime() - pendingAt > LATER_MAX_AGE_MS) return null;
        return m;
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        String m = takePending();
        if (m != null) {
            View content = activity.findViewById(android.R.id.content);
            if (content != null) content.post(() -> show(activity, m));
        }
    }

    @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) { }
    @Override public void onActivityStarted(@NonNull Activity activity) { }
    @Override public void onActivityPaused(@NonNull Activity activity) { }
    @Override public void onActivityStopped(@NonNull Activity activity) { }
    @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) { }
    @Override public void onActivityDestroyed(@NonNull Activity activity) { }
}
