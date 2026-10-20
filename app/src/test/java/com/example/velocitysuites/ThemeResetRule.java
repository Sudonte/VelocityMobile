package com.example.velocitysuites;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.test.core.app.ApplicationProvider;

import org.junit.rules.ExternalResource;

import java.util.Collection;

/**
 * Puts the theme back to a clean, system-following state before AND after every Robolectric test, so dark mode switched on by one
 * test (the header toggle, a forced night mode) can never leak into the next one.
 *
 * <p>Resets the two places the app keeps the theme: the saved choice in the "VelocityPrefs" preferences ({@code themeMode}) and
 * AppCompat's process-wide default night mode. AppCompat also remembers every activity it has seen in a static list and recreates
 * all of them when the mode changes - including ones an earlier test left open - so that list is cleared first, and the mode is
 * then changed with nobody to recreate.
 *
 * <p>Use it as {@code @Rule public final ThemeResetRule themeReset = new ThemeResetRule();}.
 */
public class ThemeResetRule extends ExternalResource {

    @Override
    protected void before() {
        reset();
    }

    @Override
    protected void after() {
        reset();
    }

    @SuppressWarnings("unchecked")
    private static void forgetTrackedActivities() {
        try {
            java.lang.reflect.Field f = AppCompatDelegate.class.getDeclaredField("sActivityDelegates");
            f.setAccessible(true);
            ((Collection<Object>) f.get(null)).clear();
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new AssertionError("AppCompatDelegate no longer keeps its delegates in sActivityDelegates - update ThemeResetRule", e);
        }
    }

    public static void reset() {
        forgetTrackedActivities();
        Context app = ApplicationProvider.getApplicationContext();
        app.getSharedPreferences("VelocityPrefs", Context.MODE_PRIVATE).edit().remove("themeMode").commit();
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
}
