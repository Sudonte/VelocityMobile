package com.example.velocitysuites.ui;

import android.app.Application;

/**
 * Bare Application for the Robolectric screen/layout tests (selected with
 * {@code @Config(application = TestApplication.class)}). The real VelocitySuitesApp.onCreate() schedules
 * a WorkManager job and installs the crash logger - neither exists in a JVM test, and neither has
 * anything to do with how a layout measures or a screen binds.
 */
public class TestApplication extends Application {
}
