package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.R;
import com.example.velocitysuites.WelcomeActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * The guest header's menu, bell and profile picture: each is at least 48dp in both directions, the three areas never
 * overlap, and a tap anywhere inside one - top, middle, bottom, either edge - triggers that control and no other.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class GuestHeaderTouchTest {

    private final int[] clicks = new int[3];

    private ViewGroup header(Activity activity) {
        ViewGroup host = new android.widget.FrameLayout(activity);
        activity.setContentView(host);
        ViewGroup header = (ViewGroup) LayoutInflater.from(activity).inflate(R.layout.guest_header, host, false);
        host.addView(header);
        int w = View.MeasureSpec.makeMeasureSpec(activity.getResources().getDisplayMetrics().widthPixels, View.MeasureSpec.EXACTLY);
        int h = View.MeasureSpec.makeMeasureSpec(activity.getResources().getDisplayMetrics().heightPixels, View.MeasureSpec.AT_MOST);
        host.measure(w, h);
        host.layout(0, 0, host.getMeasuredWidth(), host.getMeasuredHeight());
        return header;
    }

    private static Rect boundsIn(View root, View v) {
        Rect r = new Rect();
        v.getDrawingRect(r);
        ((ViewGroup) root).offsetDescendantRectToMyCoords(v, r);
        return r;
    }

    private static void tap(View root, int x, int y) {
        long t = SystemClock.uptimeMillis();
        root.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0));
        root.dispatchTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, x, y, 0));
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); // View posts its click
    }

    @Test
    public void menuBellAndProfileAreEachAtLeast48dpAndNeverOverlap() {
        Activity activity = Robolectric.buildActivity(WelcomeActivity.class).setup().get();
        ViewGroup header = header(activity);
        int min = Math.round(48 * activity.getResources().getDisplayMetrics().density);
        View[] controls = {header.findViewById(R.id.menuButtonControl), header.findViewById(R.id.headerNotificationContainer),
                header.findViewById(R.id.headerProfileContainer)};
        Rect[] r = new Rect[3];
        for (int i = 0; i < 3; i++) {
            r[i] = boundsIn(header, controls[i]);
            assertTrue("control " + i + " width " + r[i].width(), r[i].width() >= min);
            assertTrue("control " + i + " height " + r[i].height(), r[i].height() >= min);
        }
        assertFalse("menu/bell overlap", Rect.intersects(r[0], r[1]));
        assertFalse("bell/profile overlap", Rect.intersects(r[1], r[2]));
        assertFalse("menu/profile overlap", Rect.intersects(r[0], r[2]));
    }

    @Test
    public void aTapAnywhereInsideAControlTriggersThatControlOnly() {
        Activity activity = Robolectric.buildActivity(WelcomeActivity.class).setup().get();
        ViewGroup header = header(activity);
        int[] ids = {R.id.menuButtonControl, R.id.headerNotificationContainer, R.id.headerProfileContainer};
        for (int i = 0; i < 3; i++) {
            final int index = i;
            header.findViewById(ids[i]).setOnClickListener(v -> clicks[index]++);
        }
        for (int i = 0; i < 3; i++) {
            Rect r = boundsIn(header, header.findViewById(ids[i]));
            int[][] points = {
                    {r.centerX(), r.top + 2}, {r.centerX(), r.centerY()}, {r.centerX(), r.bottom - 2},
                    {r.left + 2, r.centerY()}, {r.right - 2, r.centerY()},
                    {r.left + 2, r.top + 2}, {r.right - 2, r.bottom - 2}};
            for (int[] p : points) {
                int[] before = clicks.clone();
                tap(header, p[0], p[1]);
                for (int k = 0; k < 3; k++) {
                    assertEquals("tap at " + p[0] + "," + p[1] + " on control " + i + " -> control " + k,
                            before[k] + (k == i ? 1 : 0), clicks[k]);
                }
            }
        }
    }
}
