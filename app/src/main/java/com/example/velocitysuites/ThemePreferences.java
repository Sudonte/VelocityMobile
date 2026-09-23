package com.example.velocitysuites;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

/**
 * Persists the guest's chosen Light/Dark appearance and applies it via
 * AppCompatDelegate's day/night mode - the single source of truth for
 * theme selection across the whole app (Profile Management's Appearance
 * toggle is the only place that writes it, but every screen reads the
 * same day/night resources automatically once applied, since AppCompat
 * recreates every open AppCompatActivity when the mode changes).
 *
 * Shares the existing "VelocityPrefs" SharedPreferences file already used
 * throughout the app (see BaseNavigationActivity, ProfileManagementActivity)
 * rather than introducing a second preferences file for one setting.
 */
public final class ThemePreferences {

    private static final String PREFS_NAME = "VelocityPrefs";
    private static final String KEY_THEME_MODE = "themeMode";

    public static final String MODE_LIGHT = "light_mode";
    public static final String MODE_DARK = "dark_mode";

    private ThemePreferences() {
    }

    /** Reads the saved preference; defaults to Light Mode if none has ever been set. */
    public static String getSavedMode(Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_THEME_MODE, MODE_LIGHT);
    }

    public static boolean isDarkMode(Context context) {
        return MODE_DARK.equals(getSavedMode(context));
    }

    /**
     * Saves the choice and applies it immediately via AppCompatDelegate -
     * AppCompat recreates every currently-open AppCompatActivity to pick up
     * the matching values-night/drawable-night resources, so the whole app
     * (not just the current screen) updates without an app restart. Never
     * touches any account/booking/reservation/payment data - purely a
     * local display preference.
     */
    public static void setMode(Context context, String mode) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_THEME_MODE, mode).apply();
        applyMode(mode);
    }

    /** Applies a saved/selected mode to AppCompatDelegate - the actual night-mode switch. */
    public static void applyMode(String mode) {
        AppCompatDelegate.setDefaultNightMode(MODE_DARK.equals(mode)
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
    }

    /**
     * Called once from VelocitySuitesApp#onCreate() before any Activity is
     * created, so the very first screen already renders in the saved mode -
     * avoids the "flash of the wrong theme" the app briefly showing Light
     * before switching to Dark on startup.
     */
    public static void applySavedMode(Context context) {
        applyMode(getSavedMode(context));
    }
}
