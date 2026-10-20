package com.example.velocitysuites;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import com.example.velocitysuites.network.CrashLogger;

/**
 * Single startup point for the notification alert system: creates the
 * high-importance channel once (idempotent - safe on every process start)
 * and schedules the background poll worker. Scheduling here rather than
 * only after login means a user who's still signed in from a previous
 * session (the common case - SessionManager's token persists across app
 * restarts) keeps getting background alerts without needing to reach any
 * particular screen first; NotificationPollWorker itself no-ops when
 * nobody's actually logged in.
 *
 * Also the single startup point for the Light/Dark appearance preference
 * (see ThemePreferences) - applied first, before anything else, so the
 * very first Activity created already renders in the guest's saved mode
 * instead of briefly flashing Light before switching to Dark.
 */
public class VelocitySuitesApp extends Application {

    @Override
    public void onCreate() {
        ThemePreferences.applySavedMode(this);
        super.onCreate();
        NotificationHelper.ensureChannel(this);
        NotificationPollWorker.schedule(this);

        // Debug-build-only (see CrashLogger's own doc) - no-ops entirely in release.
        CrashLogger.install(this);
        if (BuildConfig.DEBUG) {
            // Logs (never crashes on) disk/network work on the main thread and leaked Activities/closeables,
            // so lag and leaks show up in logcat under the "StrictMode" tag while testing.
            android.os.StrictMode.setThreadPolicy(new android.os.StrictMode.ThreadPolicy.Builder()
                    .detectAll().penaltyLog().build());
            android.os.StrictMode.setVmPolicy(new android.os.StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects().detectActivityLeaks().penaltyLog().build());
        }
        registerActivityLifecycleCallbacks(new ResponsiveWindow());
        registerActivityLifecycleCallbacks(new Feedback());
        registerActivityLifecycleCallbacks(new TapGuard.Installer()); // content width limit on wide windows, bars and keyboard kept clear
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity activity) {
                CrashLogger.setCurrentActivityName(activity.getClass().getSimpleName());
            }
            @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) { }
            @Override public void onActivityStarted(Activity activity) { }
            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivityStopped(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }
            @Override public void onActivityDestroyed(Activity activity) { }
        });
    }
}
