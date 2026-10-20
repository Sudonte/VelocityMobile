package com.example.velocitysuites.ui;

import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Dialog;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import com.example.velocitysuites.TapGuard;

import org.robolectric.shadows.ShadowDialog;

/** Real touches (down + up through the window, so the app's repeat-tap guard sees them) for the double/triple-tap tests. */
final class TapTestSupport {

    private TapTestSupport() {
    }

    static void layOut(Activity a) {
        View decor = a.getWindow().getDecorView();
        int w = a.getResources().getDisplayMetrics().widthPixels;
        int h = a.getResources().getDisplayMetrics().heightPixels;
        decor.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, w, h);
    }

    /** One tap at the centre of {@code view}, as the screen's window receives it. */
    static void tap(Activity a, View view) {
        TapGuard.installOn(a.getWindow());
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        float x = loc[0] + view.getWidth() / 2f;
        float y = loc[1] + view.getHeight() / 2f;
        float lastRow = a.getResources().getDisplayMetrics().heightPixels - 2f;
        if (y > lastRow) y = Math.max(loc[1] + 1f, lastRow); // a control that sticks out at the bottom is tapped on the part that shows
        long t = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(t, t + 30, MotionEvent.ACTION_UP, x, y, 0);
        a.getWindow().getCallback().dispatchTouchEvent(down);
        a.getWindow().getCallback().dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
        shadowOf(Looper.getMainLooper()).idle();
    }

    static void tapThreeTimes(Activity a, View view) {
        for (int i = 0; i < 3; i++) tap(a, view);
    }

    /** Alert dialogs on screen right now. */
    static int alertsShowing() {
        int n = 0;
        for (Dialog d : ShadowDialog.getShownDialogs()) if (d instanceof AlertDialog && d.isShowing()) n++;
        return n;
    }

    /** Taps the positive button of every alert dialog on screen, three times each (a nervous guest). */
    static void confirmAllThreeTimes(Activity unused) {
        for (Dialog d : new java.util.ArrayList<>(ShadowDialog.getShownDialogs())) {
            if (d instanceof AlertDialog && d.isShowing()) {
                android.widget.Button positive = ((AlertDialog) d).getButton(AlertDialog.BUTTON_POSITIVE);
                if (positive == null) continue;
                for (int i = 0; i < 3; i++) if (positive.isEnabled()) positive.performClick(); // a disabled button takes no taps
            }
        }
        shadowOf(Looper.getMainLooper()).idle();
    }
}
