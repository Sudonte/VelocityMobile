package com.example.velocitysuites;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;

import androidx.annotation.NonNull;

/**
 * The app's animation policy: short (about 150-250 ms) and never when the guest turned animations off in the system settings
 * ("Remove animations" / animator duration scale 0).
 */
public final class Motion {

    public static final long SHORT_MS = 180L;
    public static final long MEDIUM_MS = 250L;

    private Motion() {
    }

    /** False when the system animator scale is 0 (Remove animations): callers then jump straight to the end state. */
    public static boolean enabled(@NonNull Context c) {
        try {
            ContentResolver cr = c.getContentResolver();
            return Settings.Global.getFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** {@code ms} when animations are on, else 0. */
    public static long duration(@NonNull Context c, long ms) {
        return enabled(c) ? ms : 0L;
    }
}
