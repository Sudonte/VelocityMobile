package com.example.velocitysuites.ui;

import android.app.Application;

/**
 * Bare Application for the Robolectric screen/layout tests (selected with
 * {@code @Config(application = TestApplication.class)}). The real VelocitySuitesApp.onCreate() schedules
 * a WorkManager job and installs the crash logger - neither exists in a JVM test, and neither has
 * anything to do with how a layout measures or a screen binds.
 */
public class TestApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        com.example.velocitysuites.WelcomeEffects.motion = false;
        com.example.velocitysuites.MoneySummaryPanel.motion = false; // the summary slides in and counts up in the app; a test reads its final text
        com.example.velocitysuites.RoomRepository.setMapInlineForTests(true); // lists are mapped in the same call, so a test needs no thread to wait for
    }
}
