package com.example.velocitysuites;

import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * Gives a small visible control (the 34dp header icons) a touch area of at least 48dp without changing how it looks:
 * the parent's TouchDelegate forwards taps in the enlarged rectangle to the control. A parent can hold only one
 * TouchDelegate, so several controls in the same header share one composite.
 */
final class TouchTargets {

    private static final int MIN_DP = 48;

    private TouchTargets() { }

    static void ensureMinimumTouchArea(View... views) {
        for (View v : views) {
            if (v == null || !(v.getParent() instanceof ViewGroup)) continue;
            ViewGroup parent = (ViewGroup) v.getParent();
            parent.post(() -> expand(parent, v));
        }
    }

    private static void expand(ViewGroup parent, View target) {
        if (!target.isAttachedToWindow() || target.getWidth() == 0) return;
        float density = target.getResources().getDisplayMetrics().density;
        int min = Math.round(MIN_DP * density);
        Rect r = new Rect();
        target.getHitRect(r);
        int dx = Math.max(0, (min - r.width() + 1) / 2);
        int dy = Math.max(0, (min - r.height() + 1) / 2);
        if (dx == 0 && dy == 0) return;
        r.inset(-dx, -dy);
        Composite composite;
        TouchDelegate existing = parent.getTouchDelegate();
        if (existing instanceof Composite) {
            composite = (Composite) existing;
        } else {
            composite = new Composite(parent);
            parent.setTouchDelegate(composite);
        }
        composite.set(target, new TouchDelegate(r, target));
    }

    private static final class Composite extends TouchDelegate {
        private final List<View> targets = new ArrayList<>();
        private final List<TouchDelegate> delegates = new ArrayList<>();
        private TouchDelegate active;

        Composite(View owner) {
            super(new Rect(), owner);
        }

        void set(View target, TouchDelegate delegate) {
            int i = targets.indexOf(target);
            if (i >= 0) {
                delegates.set(i, delegate);
            } else {
                targets.add(target);
                delegates.add(delegate);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                active = null;
                for (TouchDelegate d : delegates) {
                    if (d.onTouchEvent(MotionEvent.obtain(event))) {
                        active = d;
                        return true;
                    }
                }
                return false;
            }
            return active != null && active.onTouchEvent(event);
        }
    }
}
