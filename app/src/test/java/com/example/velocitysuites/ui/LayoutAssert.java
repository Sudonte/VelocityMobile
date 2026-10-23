package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;
import android.text.Layout;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Measures a view at the screen width and checks that nothing runs past the screen edge, no text is cut off (ellipsized, or taller than
 * its box) and no two text views that share a non-flowing parent sit on top of each other.
 */
final class LayoutAssert {

    private LayoutAssert() {
    }

    static void assertFits(String tag, View root, int screenWidth) {
        root.measure(View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        root.layout(0, 0, screenWidth, root.getMeasuredHeight());
        check(tag, root, 0, 0, screenWidth);
    }

    private static String idName(View v) {
        if (v.getId() == View.NO_ID) return "";
        try {
            return "#" + v.getResources().getResourceEntryName(v.getId());
        } catch (android.content.res.Resources.NotFoundException e) {
            return "#generated" + v.getId();
        }
    }

    private static void check(String tag, View v, int offX, int offY, int screenWidth) {
        if (v.getVisibility() != View.VISIBLE) return;
        int left = offX + v.getLeft();
        int right = offX + v.getRight();
        String name = v.getClass().getSimpleName() + idName(v);
        assertTrue(tag + ": " + name + " runs past the screen edge (" + left + ".." + right + " of " + screenWidth + ")", left >= 0 && right <= screenWidth);
        if (v instanceof TextView && v.getWidth() > 0) {
            TextView t = (TextView) v;
            Layout layout = t.getLayout();
            if (layout != null && t.getText().length() > 0) {
                for (int line = 0; line < layout.getLineCount(); line++) {
                    assertEquals(tag + ": " + name + " has cut-off text '" + t.getText() + "'", 0, layout.getEllipsisCount(line));
                }
                int needed = layout.getHeight() + t.getCompoundPaddingTop() + t.getCompoundPaddingBottom();
                assertTrue(tag + ": " + name + " is shorter (" + t.getHeight() + ") than its text (" + needed + "): '" + t.getText() + "'", t.getHeight() >= needed - 1);
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            boolean flows = g instanceof android.widget.LinearLayout || g instanceof android.widget.ScrollView
                    || g instanceof androidx.core.widget.NestedScrollView || g instanceof androidx.recyclerview.widget.RecyclerView;
            for (int i = 0; i < g.getChildCount(); i++) {
                View a = g.getChildAt(i);
                if (a.getVisibility() != View.VISIBLE) continue;
                check(tag, a, left - g.getScrollX(), offY + v.getTop() - g.getScrollY(), screenWidth);
                if (flows || !(a instanceof TextView)) continue;
                for (int j = i + 1; j < g.getChildCount(); j++) {
                    View b = g.getChildAt(j);
                    if (b.getVisibility() != View.VISIBLE || !(b instanceof TextView)) continue;
                    Rect ra = new Rect(a.getLeft(), a.getTop(), a.getRight(), a.getBottom());
                    Rect rb = new Rect(b.getLeft(), b.getTop(), b.getRight(), b.getBottom());
                    assertTrue(tag + ": text views overlap inside " + name, !Rect.intersects(ra, rb));
                }
            }
        }
    }
}
