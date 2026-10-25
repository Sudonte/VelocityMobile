package com.example.velocitysuites;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.Locale;

/**
 * Counts failed sign-ins per email on THIS device so the app can offer "Reset your password" after the third one. Only the server's
 * single {@code INVALID_CREDENTIALS} answer counts (wrong email or wrong password - the app cannot tell which, and does not need to, so
 * an address with no account behaves exactly like one with an account); a lost connection, a locked or blocked account never counts.
 * The count forgets itself 15 minutes after the last failure, and is cleared by a successful sign-in or a completed password reset.
 */
public final class FailedSignIns {

    /** The failure that sends the guest to Reset password. */
    public static final int REDIRECT_AT = 3;
    public static final long WINDOW_MS = 15 * 60 * 1000L;

    private static final String FILE = "VelocityFailedSignIns";

    /** Time source, replaceable in tests. */
    public interface Clock {
        long now();
    }

    private static Clock clock = System::currentTimeMillis;

    private FailedSignIns() {
    }

    @VisibleForTesting
    public static void setClockForTests(@Nullable Clock replacement) {
        clock = replacement != null ? replacement : System::currentTimeMillis;
    }

    /** Records one failed sign-in for this email and returns how many recent failures that makes (this one included). */
    public static int record(@NonNull Context context, @Nullable String email) {
        String key = key(email);
        SharedPreferences prefs = prefs(context);
        int count = recent(prefs, key) + 1;
        prefs.edit().putInt("n:" + key, count).putLong("t:" + key, clock.now()).apply();
        return count;
    }

    /** The recent failures for this email (0 once the 15 minutes have passed since the last one). */
    public static int count(@NonNull Context context, @Nullable String email) {
        return recent(prefs(context), key(email));
    }

    /** A successful sign-in or a completed reset forgets the failures for this email. */
    public static void clear(@NonNull Context context, @Nullable String email) {
        String key = key(email);
        prefs(context).edit().remove("n:" + key).remove("t:" + key).apply();
    }

    private static int recent(SharedPreferences prefs, String key) {
        long last = prefs.getLong("t:" + key, 0L);
        if (last == 0L || clock.now() - last >= WINDOW_MS) return 0;
        return prefs.getInt("n:" + key, 0);
    }

    private static String key(@Nullable String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
