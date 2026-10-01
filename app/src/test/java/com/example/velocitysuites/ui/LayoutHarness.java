package com.example.velocitysuites.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.example.velocitysuites.R;

/**
 * JVM-only (Robolectric) helpers for checking how a layout actually lays out - real inflation with the
 * app's real theme and resources, then a real measure/layout pass at a chosen screen width - so a layout
 * claim ("one row", "nothing overlaps", "fits a 320dp phone") is tested instead of assumed. No device or
 * emulator is involved.
 */
final class LayoutHarness {

    private LayoutHarness() {
    }

    /** The app's own theme (Material DayNight) over the Robolectric application context. */
    static Context themedContext() {
        Context app = ApplicationProvider.getApplicationContext();
        return new ContextThemeWrapper(app, R.style.Theme_VelocitySuites);
    }

    /** Same, but with a system font scale - large-text accessibility settings are where rows break first. */
    static Context themedContext(float fontScale) {
        // Scale first, theme second: createConfigurationContext() on an already-themed wrapper
        // drops the theme, and Material components refuse to inflate without it.
        Context app = ApplicationProvider.getApplicationContext();
        Configuration config = new Configuration(app.getResources().getConfiguration());
        config.fontScale = fontScale;
        return new ContextThemeWrapper(app.createConfigurationContext(config), R.style.Theme_VelocitySuites);
    }

    /** A parent the same width as a phone screen, so RecyclerView-style item inflation gets the right LayoutParams. */
    static FrameLayout parentOfWidth(Context context, int widthDp) {
        FrameLayout parent = new FrameLayout(context);
        int widthPx = dp(context, widthDp);
        parent.setLayoutParams(new ViewGroup.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT));
        return parent;
    }

    /** Measures and lays out {@code view} exactly as a RecyclerView row of that width would be: width fixed, height wrap_content. */
    static void layoutAtWidth(View view, Context context, int widthDp) {
        int widthPx = dp(context, widthDp);
        view.measure(
                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    static int dp(Context context, float dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    /** {@code child}'s bounds in {@code root}'s coordinate space, in px. */
    static Rect boundsIn(View root, View child) {
        Rect rect = new Rect(0, 0, child.getWidth(), child.getHeight());
        ((ViewGroup) root).offsetDescendantRectToMyCoords(child, rect);
        return rect;
    }

    /** True when the two views' rectangles share any area (touching edges do not count). */
    static boolean overlaps(View root, View a, View b) {
        return Rect.intersects(boundsIn(root, a), boundsIn(root, b));
    }

    /** One line per view in the tree: id, class, visibility, bounds (dp) and text - printed in failures so a geometry regression is readable. */
    static String dump(Context context, View root) {
        StringBuilder sb = new StringBuilder();
        dump(context, root, root, 0, sb);
        return sb.toString();
    }

    private static void dump(Context context, View root, View view, int depth, StringBuilder sb) {
        for (int i = 0; i < depth; i++) sb.append("  ");
        String id;
        try {
            id = view.getId() == View.NO_ID ? "-" : context.getResources().getResourceEntryName(view.getId());
        } catch (Exception e) {
            id = "#" + view.getId();
        }
        float density = context.getResources().getDisplayMetrics().density;
        Rect r = view == root ? new Rect(0, 0, view.getWidth(), view.getHeight()) : boundsIn(root, view);
        sb.append(id).append(' ').append(view.getClass().getSimpleName())
                .append(view.getVisibility() == View.VISIBLE ? "" : " [" + (view.getVisibility() == View.GONE ? "GONE" : "INVISIBLE") + "]")
                .append(String.format(" x=%.0f y=%.0f w=%.0f h=%.0f (dp)", r.left / density, r.top / density, r.width() / density, r.height() / density));
        if (view instanceof TextView && view.getVisibility() == View.VISIBLE) {
            TextView tv = (TextView) view;
            sb.append(" lines=").append(tv.getLineCount()).append(" \"").append(tv.getText()).append('"');
        }
        sb.append('\n');
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                dump(context, root, group.getChildAt(i), depth + 1, sb);
            }
        }
    }
}
