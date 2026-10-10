package com.example.velocitysuites;

import androidx.annotation.Nullable;

import com.google.gson.Gson;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Answers "is this different from what the screen is already showing?" so a silent refresh can stay silent.
 * <p>
 * A screen that polls every 30 seconds must not rebuild itself every 30 seconds: rebuilding a list collapses the
 * card the guest has opened, resets what they selected, and restarts animations - all for data that is identical.
 * The screen passes whatever it renders from (its bookings, its notifications...) to {@link #accept}; the gate
 * says true only when that content differs from the last content it accepted, and only then does the screen redraw.
 * <p>
 * "Content" is compared field by field via the objects' JSON form, so a field added to a model later is covered
 * without anyone remembering to extend an equals() - the models here are plain data holders with no cycles.
 * If an object ever could not be turned into JSON the gate reports a change: redrawing needlessly is harmless,
 * ignoring a real change is not.
 */
public final class ChangeGate {

    private static final Gson GSON = new Gson();

    @Nullable
    private String last;

    /**
     * Records {@code content} as what is now on screen.
     *
     * @return true if it differs from the previously accepted content (always true the first time, and after
     * {@link #forget()}); false if it is the same - the screen can skip redrawing.
     */
    public boolean accept(Object... content) {
        String now = fingerprint(content);
        boolean changed = now == null || !now.equals(last);
        last = now;
        return changed;
    }

    /** The next {@link #accept} reports a change whatever it is given - used when the screen was redrawn by other means. */
    public void forget() {
        last = null;
    }

    /**
     * Today's date on this device, to pass to {@link #accept} alongside the data. Several screens sort bookings into
     * upcoming / active / completed by comparing their dates with today, so the same data draws differently once
     * midnight passes: including the day makes a screen that is left open roll forward by itself.
     */
    public static String today() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
    }

    @Nullable
    static String fingerprint(Object... content) {
        try {
            byte[] json = GSON.toJson(content).getBytes(StandardCharsets.UTF_8);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException | RuntimeException | StackOverflowError e) {
            return null; // unknown -> treated as changed
        }
    }
}
