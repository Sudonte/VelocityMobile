package com.example.velocitysuites;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CompoundButton;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.LongSupplier;

/**
 * THE global safe-click guard. It sits on every window (every screen, and every dialog or bottom sheet through {@link SafeDialogs}),
 * so ALL buttons, cards, list rows, menu items, bottom bars and navigation actions are covered - not only the ones whose listener was
 * wrapped by hand. A second tap on the SAME control within {@link #WINDOW_MS} of the first is swallowed whole (down, move and up), so a
 * nervous double or triple tap can never open a screen, dialog or sheet twice or send a request twice.
 *
 * <p>Taps on different controls are never affected (the next control works at once). Controls meant to be tapped quickly in a row are
 * exempt: quantity steppers, switches and check boxes, and text fields.
 */
public final class TapGuard {

    public static final long WINDOW_MS = 600L;

    private static LongSupplier clock = SystemClock::elapsedRealtime;

    private final Window window;
    @Nullable private View lastTarget;
    private long lastAt;
    private boolean swallowing;

    private TapGuard(Window window) {
        this.window = window;
    }

    /** Test hook: a hand-cranked clock. Pass null to restore the real one. */
    public static void setClockForTests(@Nullable LongSupplier c) {
        clock = c != null ? c : SystemClock::elapsedRealtime;
    }

    /** True when this touch event must be swallowed (a repeat tap on the same control). */
    boolean shouldSwallow(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            swallowing = false;
            View root = window.getDecorView();
            View target = root == null ? null : clickableAt(root, event.getRawX(), event.getRawY());
            if (target != null && !isRepeatable(target)) {
                long now = clock.getAsLong();
                if (target == lastTarget && now - lastAt < WINDOW_MS) {
                    swallowing = true;
                    return true; // the repeat tap goes nowhere
                }
                lastTarget = target;
                lastAt = now;
            }
            return false;
        }
        if (swallowing) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) swallowing = false;
            return true;
        }
        return false;
    }

    static boolean isRepeatable(@NonNull View v) {
        if (v instanceof EditText || v instanceof CompoundButton) return true;
        if (Boolean.TRUE.equals(v.getTag(R.id.tapRepeatable))) return true;
        int id = v.getId();
        if (id != View.NO_ID) {
            try {
                String name = v.getResources().getResourceEntryName(id).toLowerCase(java.util.Locale.ROOT);
                return name.contains("plus") || name.contains("minus") || name.contains("increase") || name.contains("decrease")
                        || name.contains("stepper") || name.contains("qty");
            } catch (android.content.res.Resources.NotFoundException ignored) {
                return false;
            }
        }
        return false;
    }

    /** The deepest visible, enabled, clickable view under the screen point, or null. */
    @Nullable
    static View clickableAt(@NonNull View root, float rawX, float rawY) {
        int[] loc = new int[2];
        root.getLocationOnScreen(loc);
        return find(root, rawX - loc[0], rawY - loc[1]);
    }

    @Nullable
    private static View find(View view, float x, float y) {
        if (view.getVisibility() != View.VISIBLE) return null;
        if (x < 0 || y < 0 || x > view.getWidth() || y > view.getHeight()) return null;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View child = group.getChildAt(i);
                float cx = x + group.getScrollX() - child.getLeft() - child.getTranslationX();
                float cy = y + group.getScrollY() - child.getTop() - child.getTranslationY();
                View hit = find(child, cx, cy);
                if (hit != null) return hit;
            }
        }
        return view.isClickable() && view.isEnabled() ? view : null;
    }

    /** Puts the guard on {@code window} (once). */
    public static void installOn(@Nullable Window window) {
        if (window == null) return;
        final Window.Callback current = window.getCallback();
        if (current == null) return;
        if (Proxy.isProxyClass(current.getClass()) && Proxy.getInvocationHandler(current) instanceof Handler) return;
        window.setCallback((Window.Callback) Proxy.newProxyInstance(Window.Callback.class.getClassLoader(),
                new Class<?>[]{Window.Callback.class}, new Handler(current, new TapGuard(window))));
    }

    private static final class Handler implements InvocationHandler {
        private final Window.Callback wrapped;
        private final TapGuard guard;

        Handler(Window.Callback wrapped, TapGuard guard) {
            this.wrapped = wrapped;
            this.guard = guard;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("dispatchTouchEvent".equals(method.getName()) && args != null && args.length == 1 && args[0] instanceof MotionEvent
                    && guard.shouldSwallow((MotionEvent) args[0])) {
                return true;
            }
            try {
                return method.invoke(wrapped, args);
            } catch (InvocationTargetException e) {
                throw e.getCause() != null ? e.getCause() : e;
            }
        }
    }

    /** Installs the guard on every activity window. */
    public static final class Installer implements Application.ActivityLifecycleCallbacks {
        @Override
        public void onActivityPostCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
            installOn(activity.getWindow());
        }

        @Override public void onActivityCreated(@NonNull Activity a, @Nullable Bundle b) { }
        @Override public void onActivityStarted(@NonNull Activity a) { }
        @Override public void onActivityResumed(@NonNull Activity a) { }
        @Override public void onActivityPaused(@NonNull Activity a) { }
        @Override public void onActivityStopped(@NonNull Activity a) { }
        @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) { }
        @Override public void onActivityDestroyed(@NonNull Activity a) { }
    }
}
