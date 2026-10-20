package com.example.velocitysuites;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * How every screen fits every phone and tablet, in one place (installed as an ActivityLifecycleCallbacks by the Application):
 * <ul>
 *   <li><b>Comfortable width.</b> On a wide window (a tablet, a phone in landscape) the screen's content is limited to
 *       {@link #MAX_CONTENT_DP} and centred, instead of stretching edge to edge; the window background shows on both sides.
 *       Re-applied when the window is resized (rotation, split screen).</li>
 *   <li><b>System bars, cutouts and the keyboard.</b> The sides, the bottom (gesture bar / 3-button navigation) and the on-screen keyboard
 *       are kept clear, so a bottom bar stays above the navigation bar and a focused field stays above the keyboard. The top is left to each
 *       screen's own header, as before. Screens that already place themselves ({@link #SELF_MANAGED}) are left alone.</li>
 * </ul>
 */
public final class ResponsiveWindow implements Application.ActivityLifecycleCallbacks {

    /** The widest a screen's content gets, in dp. */
    public static final int MAX_CONTENT_DP = 680;

    /** Screens that handle their own insets (full-bleed hero images, a custom bottom bar): the shared handling would double up. */
    private static final java.util.Set<String> SELF_MANAGED = new java.util.HashSet<>(java.util.Arrays.asList(
            "LandingActivity", "MainActivity", "WelcomeActivity", "BookingWizardActivity", "PaymentActivity"));

    private final Map<Activity, ComponentCallbacks> callbacks = Collections.synchronizedMap(new WeakHashMap<>());

    /** Installs the handling on {@code activity} now (the lifecycle callback does this after onCreate; tests call it directly). */
    public static void apply(@NonNull Activity activity) {
        FrameLayout content = activity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return;
        limitWidth(activity, content);
        if (!SELF_MANAGED.contains(activity.getClass().getSimpleName())) installInsets(content);
    }

    /** The content width for a window of {@code windowWidthDp}: the whole window, or {@link #MAX_CONTENT_DP} when it is wider. */
    public static int contentWidthDp(int windowWidthDp) {
        return Math.min(windowWidthDp, MAX_CONTENT_DP);
    }

    private static void limitWidth(Activity activity, FrameLayout content) {
        View root = content.getChildAt(0);
        float density = activity.getResources().getDisplayMetrics().density;
        int windowDp = Math.round(activity.getResources().getConfiguration().screenWidthDp);
        ViewGroup.LayoutParams lp = root.getLayoutParams();
        FrameLayout.LayoutParams params = lp instanceof FrameLayout.LayoutParams ? (FrameLayout.LayoutParams) lp
                : new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        int wanted = windowDp > MAX_CONTENT_DP ? Math.round(MAX_CONTENT_DP * density) : ViewGroup.LayoutParams.MATCH_PARENT;
        int gravity = windowDp > MAX_CONTENT_DP ? Gravity.CENTER_HORIZONTAL | Gravity.TOP : params.gravity;
        if (params.width != wanted || params.gravity != gravity) {
            params.width = wanted;
            params.gravity = gravity;
            root.setLayoutParams(params);
        }
    }

    private static void installInsets(FrameLayout content) {
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, 0, bars.right, Math.max(bars.bottom, ime.bottom));
            return insets;
        });
        ViewCompat.requestApplyInsets(content);
    }

    // ---- lifecycle

    /** Phones (smallest width under 600dp) stay in portrait; tablets rotate freely. */
    public static final int TABLET_MIN_SMALLEST_WIDTH_DP = 600;

    public static boolean isTablet(@NonNull Activity activity) {
        return activity.getResources().getConfiguration().smallestScreenWidthDp >= TABLET_MIN_SMALLEST_WIDTH_DP;
    }

    /** Locks a phone to portrait and leaves a tablet unlocked. */
    public static void applyOrientation(@NonNull Activity activity) {
        activity.setRequestedOrientation(isTablet(activity)
                ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    @Override
    public void onActivityPreCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        applyOrientation(activity);
    }

    @Override
    public void onActivityPostCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        apply(activity);
        ComponentCallbacks cb = new ComponentCallbacks() {
            @Override
            public void onConfigurationChanged(@NonNull Configuration newConfig) {
                FrameLayout content = activity.findViewById(android.R.id.content);
                if (content != null && content.getChildCount() > 0) limitWidth(activity, content);
            }

            @Override
            public void onLowMemory() {
            }
        };
        callbacks.put(activity, cb);
        activity.registerComponentCallbacks(cb);
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        ComponentCallbacks cb = callbacks.remove(activity);
        if (cb != null) activity.unregisterComponentCallbacks(cb);
    }

    @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) { }
    @Override public void onActivityStarted(@NonNull Activity activity) { }
    @Override public void onActivityResumed(@NonNull Activity activity) { }
    @Override public void onActivityPaused(@NonNull Activity activity) { }
    @Override public void onActivityStopped(@NonNull Activity activity) { }
    @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) { }
}
