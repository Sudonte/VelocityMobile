package com.example.velocitysuites;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Base of every screen: never starts the same screen twice. A second {@code startActivity} with an equal intent (same target, same
 * extras) within {@link #WINDOW_MS} is dropped, whichever tap, menu item or callback asked for it; every other launch gets
 * {@code FLAG_ACTIVITY_SINGLE_TOP} when it targets a screen already on top, so the side menu cannot stack duplicates.
 */
public abstract class SafeActivity extends AppCompatActivity {

    static final long WINDOW_MS = 800L;

    @Nullable private Intent lastLaunch;
    private long lastLaunchAt;

    @Override
    public void startActivity(Intent intent, @Nullable Bundle options) {
        if (intent != null && isRepeat(intent)) return;
        super.startActivity(intent, options);
    }

    private boolean isRepeat(Intent intent) {
        long now = SystemClock.elapsedRealtime();
        boolean repeat = lastLaunch != null && now - lastLaunchAt < WINDOW_MS && sameLaunch(lastLaunch, intent);
        if (!repeat) {
            lastLaunch = new Intent(intent);
            lastLaunchAt = now;
        }
        return repeat;
    }

    /** Equal when it asks for the same component/action/data with equal extras. */
    static boolean sameLaunch(Intent a, Intent b) {
        return a.filterEquals(b) && extrasEqual(a.getExtras(), b.getExtras());
    }

    private static boolean extrasEqual(@Nullable Bundle x, @Nullable Bundle y) {
        if (x == null || x.isEmpty()) return y == null || y.isEmpty();
        if (y == null || x.size() != y.size()) return false;
        for (String key : x.keySet()) {
            Object vx = x.get(key);
            Object vy = y.get(key);
            if (vx == null ? vy != null : !vx.equals(vy)) return false;
        }
        return true;
    }
}
