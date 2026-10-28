package com.example.velocitysuites;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

/**
 * Draws the {@link MoneySummary}: Grand total, Remaining balance and Amount to pay, bold, on a tinted surface. On the details screens it is
 * pinned at the bottom (above the navigation bar, always visible while the page scrolls; a tap scrolls to the Bill); on a receipt, on
 * screen and in the PDF, it is the same box at the end of the page.
 *
 * <p>Amounts use tabular figures so the digits line up. The panel slides in once and its amounts count up once on the first open; both are
 * skipped when the system has animations turned off (reduced motion).
 */
public final class MoneySummaryPanel {

    /** Tests switch the motion off so the text they read is always the final text. */
    public static boolean motion = true;

    private static final long SLIDE_MS = 260;
    private static final long COUNT_MS = 650;

    private MoneySummaryPanel() {
    }

    // ---- the pinned panel of the details screens ----

    /**
     * Fills {@code host} (a FrameLayout pinned under the scrolling page). A cancelled or rejected transaction shows a slim notice instead of
     * the amounts. {@code onTap} (may be null) runs when the panel is tapped.
     */
    static void bindPinned(@NonNull Context c, @NonNull FrameLayout host, @NonNull MoneySummary summary, @Nullable Runnable onTap) {
        boolean first = host.getTag() == null;
        host.removeAllViews();
        host.setVisibility(View.VISIBLE);
        View panel = summary.isCancelled() ? notice(c, summary) : build(c, summary, true);
        panel.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        host.addView(panel);
        host.setTag(Boolean.TRUE);
        if (onTap != null && !summary.isCancelled()) {
            panel.setClickable(true);
            panel.setOnClickListener(v -> onTap.run());
            ViewCompat.replaceAccessibilityAction(panel, AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                    c.getString(R.string.money_summary_hint), null);
        }
        if (first && motion && ValueAnimator.areAnimatorsEnabled()) slideIn(c, panel);
    }

    /** The box at the end of a receipt (on screen and in the PDF). */
    @NonNull
    static View box(@NonNull Context c, @NonNull MoneySummary summary) {
        View box = build(c, summary, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 8);
        lp.bottomMargin = dp(c, 8);
        box.setLayoutParams(lp);
        return box;
    }

    /** Scrolls the page so {@code target} (a view inside it) is at the top, with a little room above. */
    static void scrollTo(@NonNull androidx.core.widget.NestedScrollView scroller, @NonNull View target) {
        View content = scroller.getChildAt(0);
        int y = 0;
        View v = target;
        while (v != null && v != content && v.getParent() instanceof View) {
            y += v.getTop();
            v = (View) v.getParent();
        }
        scroller.smoothScrollTo(0, Math.max(0, y - dp(scroller.getContext(), 8)));
    }

    // ---- building ----

    private static View notice(Context c, MoneySummary summary) {
        TextView tv = new TextView(c);
        tv.setText(summary.amountToPay);
        tv.setTextSize(14f);
        tv.setGravity(Gravity.CENTER);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(ContextCompat.getColor(c, R.color.velocity_text_secondary));
        tv.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        tv.setBackground(surface(c, R.color.velocity_card_surface, true));
        tv.setElevation(dp(c, 8));
        return tv;
    }

    private static View build(Context c, MoneySummary s, boolean pinned) {
        LinearLayout outer = new LinearLayout(c);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setGravity(Gravity.CENTER_HORIZONTAL);
        outer.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        outer.setBackground(surface(c, R.color.velocity_red_soft, pinned));
        if (pinned) outer.setElevation(dp(c, 8));

        // centered column; on a wide screen it stops at a readable width
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        boolean wide = c.getResources().getDisplayMetrics().widthPixels > dp(c, 560);
        col.setLayoutParams(new LinearLayout.LayoutParams(wide ? dp(c, 520) : ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        boolean stack = c.getResources().getConfiguration().fontScale >= 1.5f;

        TextView total = amount(c, MoneyFormat.format(s.grandTotal), 16, R.color.velocity_text_primary);
        TextView remaining = amount(c, MoneyFormat.format(s.remaining), 16, R.color.velocity_text_primary);
        col.addView(row(c, c.getString(R.string.review_grand_total_label), 14, false, total, stack));
        col.addView(row(c, c.getString(R.string.money_remaining_balance), 14, false, remaining, stack));

        View rule = new View(c);
        LinearLayout.LayoutParams ruleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1)));
        ruleLp.topMargin = dp(c, 6);
        ruleLp.bottomMargin = dp(c, 6);
        rule.setLayoutParams(ruleLp);
        rule.setBackgroundColor(ContextCompat.getColor(c, R.color.velocity_divider_hairline));
        col.addView(rule);

        int payColor = s.isSettled() ? R.color.velocity_success_text : R.color.velocity_action_text;
        TextView pay = amount(c, "", 22, payColor);
        setPayText(pay, s.amountToPay.substring(0, s.amountEnd), s.amountToPay.substring(s.amountEnd));
        col.addView(row(c, c.getString(R.string.money_amount_to_pay), 16, true, pay, stack));
        if (pinned && s.kind == MoneySummary.Kind.AT_CHECK_IN) {
            // payment at check-in is optional now: the whole balance is due at check-out, and may be paid earlier
            TextView note = new TextView(c);
            note.setText(R.string.money_optional_check_in_note);
            note.setTextSize(12);
            note.setGravity(Gravity.END);
            note.setTextColor(ContextCompat.getColor(c, R.color.velocity_text_secondary));
            col.addView(note);
        }
        outer.addView(col);

        // the figures count up once on the first open (the final text is already in place when motion is off)
        if (pinned && motion && ValueAnimator.areAnimatorsEnabled()) countUp(s, total, remaining, pay);
        return outer;
    }

    /** "₱9,800.00 · at check-in": the amount large and bold, the words after it smaller. */
    private static void setPayText(TextView tv, String amountPart, String suffix) {
        SpannableString text = new SpannableString(amountPart + suffix);
        if (!suffix.isEmpty()) {
            text.setSpan(new AbsoluteSizeSpan(14, true), amountPart.length(), text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        tv.setText(text);
    }

    private static LinearLayout row(Context c, String label, int labelSp, boolean labelBold, TextView value, boolean stack) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(stack ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        row.setGravity(stack ? Gravity.START : Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(c, 2), 0, dp(c, 2));
        TextView l = new TextView(c);
        l.setText(label);
        l.setTextSize(labelSp);
        l.setTextColor(ContextCompat.getColor(c, R.color.velocity_text_primary));
        if (labelBold) l.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams llp = stack ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        l.setLayoutParams(llp);
        row.addView(l);
        LinearLayout.LayoutParams vlp = stack ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (!stack) vlp.setMarginStart(dp(c, 12));
        value.setLayoutParams(vlp);
        value.setGravity(Gravity.END);
        row.addView(value);
        return row;
    }

    private static TextView amount(Context c, String text, int sp, int colorRes) {
        TextView tv = new TextView(c);
        tv.setText(text);
        tv.setTextSize(sp);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(ContextCompat.getColor(c, colorRes));
        tv.setFontFeatureSettings("tnum");
        return tv;
    }

    private static GradientDrawable surface(Context c, int colorRes, boolean topRoundedOnly) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(ContextCompat.getColor(c, colorRes));
        float r = dp(c, 16);
        if (topRoundedOnly) d.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        else d.setCornerRadius(r);
        d.setStroke(Math.max(1, dp(c, 1)), ContextCompat.getColor(c, R.color.velocity_divider_hairline));
        return d;
    }

    // ---- motion ----

    private static void slideIn(Context c, View panel) {
        panel.setAlpha(0f);
        panel.setTranslationY(dp(c, 56));
        panel.animate().alpha(1f).translationY(0f).setDuration(SLIDE_MS).start();
    }

    private static void countUp(MoneySummary s, TextView total, TextView remaining, TextView pay) {
        // only an Amount to pay that starts with its own amount counts up ("Pay at the hotel" and "Pay now: ..." stay as they are)
        boolean payCounts = s.kind == MoneySummary.Kind.AT_CHECK_IN || s.kind == MoneySummary.Kind.AT_CHECK_OUT || s.kind == MoneySummary.Kind.AT_FRONT_DESK;
        String suffix = s.amountToPay.substring(s.amountEnd);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(COUNT_MS);
        animator.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            total.setText(MoneyFormat.format(s.grandTotal * f));
            remaining.setText(MoneyFormat.format(s.remaining * f));
            if (payCounts) setPayText(pay, MoneyFormat.format(s.remaining * f), suffix);
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                total.setText(MoneyFormat.format(s.grandTotal));
                remaining.setText(MoneyFormat.format(s.remaining));
                if (payCounts) setPayText(pay, s.amountToPay.substring(0, s.amountEnd), suffix);
            }
        });
        animator.start();
    }

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
