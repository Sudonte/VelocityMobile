package com.example.velocitysuites;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Set;

/**
 * Draws {@link TransactionTimeline} entries as a vertical line with a dot per event, newest on top, the current (newest) step
 * highlighted. Entries whose key is not in {@code seen} (and only when something was shown before) flash briefly so a
 * quiet refresh shows what is new; the highlight is skipped when the system animations are off.
 */
final class TransactionTimelineView {

    private TransactionTimelineView() {
    }

    /** Replaces the content of {@code container}; adds every key shown to {@code seen}. */
    static void render(@NonNull Context c, @NonNull LinearLayout container, @NonNull List<TransactionTimeline.Entry> entries, @NonNull Set<String> seen) {
        boolean flashNew = !seen.isEmpty();
        container.removeAllViews();
        float d = c.getResources().getDisplayMetrics().density;
        if (entries.isEmpty()) {
            TextView none = new TextView(c);
            none.setText(R.string.tl_empty);
            none.setTextSize(13);
            none.setTextColor(c.getColor(R.color.velocity_text_secondary));
            container.addView(none);
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            TransactionTimeline.Entry e = entries.get(i);
            boolean last = i == entries.size() - 1;
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setTag(e.key);

            FrameLayout rail = new FrameLayout(c);
            rail.addView(new View(c) {
                {
                    setBackgroundColor(c.getColor(R.color.velocity_filter_stroke));
                    setVisibility(last ? View.INVISIBLE : View.VISIBLE);
                }
            }, new FrameLayout.LayoutParams(Math.round(2 * d), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL));
            ImageView dot = new ImageView(c);
            int size = Math.round((e.current ? 24 : 20) * d);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(c.getColor(e.current ? R.color.velocity_red_primary : R.color.velocity_red_soft));
            dot.setBackground(bg);
            int pad = Math.round(4 * d);
            dot.setPadding(pad, pad, pad, pad);
            dot.setImageResource(e.icon);
            dot.setColorFilter(c.getColor(e.current ? R.color.white : R.color.velocity_red_primary));
            dot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            rail.addView(dot, new FrameLayout.LayoutParams(size, size, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
            row.addView(rail, new LinearLayout.LayoutParams(Math.round(28 * d), ViewGroup.LayoutParams.MATCH_PARENT));

            LinearLayout text = new LinearLayout(c);
            text.setOrientation(LinearLayout.VERTICAL);
            text.setPadding(Math.round(10 * d), 0, 0, Math.round(14 * d));
            text.addView(line(c, e.title, 13, true, e.current ? R.color.velocity_text_primary : R.color.velocity_text_primary));
            if (e.detail != null) text.addView(line(c, e.detail, 12, false, R.color.velocity_text_secondary));
            text.addView(line(c, e.when, 11, false, R.color.velocity_text_secondary));
            row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            row.setContentDescription(e.title + (e.detail != null ? ", " + e.detail : "") + ", " + e.when);
            container.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (flashNew && !seen.contains(e.key)) flash(c, row);
        }
        for (TransactionTimeline.Entry e : entries) seen.add(e.key);
    }

    private static TextView line(Context c, String text, int sp, boolean bold, int color) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(c.getColor(color));
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    /** A soft red wash that fades out - nothing at all when animations are off. */
    static void flash(Context c, View v) {
        long ms = Motion.duration(c, 1200);
        if (ms == 0) return;
        int base = c.getColor(R.color.velocity_red_soft);
        ValueAnimator a = ValueAnimator.ofInt(255, 0);
        a.setDuration(ms);
        a.addUpdateListener(an -> v.setBackgroundColor(Color.argb((int) an.getAnimatedValue() * Color.alpha(base) / 255, Color.red(base), Color.green(base), Color.blue(base))));
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                v.setBackground(null);
            }
        });
        a.start();
    }

    /** Flashes a changed value (a total, a status) the same way. */
    static void flashChanged(Context c, View v) {
        flash(c, v);
    }
}
