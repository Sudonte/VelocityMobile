package com.example.velocitysuites;

import android.app.Application;

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
    }
}
