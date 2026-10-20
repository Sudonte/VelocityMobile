package com.example.velocitysuites;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

/**
 * Shows or hides a block with a short height animation, so a Dashboard section opens and closes without a layout jump.
 * The final state is always exact (VISIBLE with wrap_content height, or GONE); when the block has not been laid out yet
 * (nothing to animate from) it simply switches.
 */
final class SectionAnimator {

    static final long DURATION_MS = 180L;

    private SectionAnimator() {
    }

    static void set(@NonNull View view, boolean show, boolean animate) {
        Object running = view.getTag(R.id.sectionAnimator);
        if (running instanceof Animator) ((Animator) running).cancel();
        ViewGroup.LayoutParams params = view.getLayoutParams();
        View parent = view.getParent() instanceof View ? (View) view.getParent() : null;
        boolean canAnimate = animate && Motion.enabled(view.getContext()) && params != null && parent != null && parent.getWidth() > 0;
        if (!canAnimate) {
            finish(view, show);
            return;
        }
        int from;
        int to;
        if (show) {
            view.measure(View.MeasureSpec.makeMeasureSpec(parent.getWidth(), View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            from = 0;
            to = view.getMeasuredHeight();
            params.height = 0;
            view.setVisibility(View.VISIBLE);
        } else {
            from = view.getHeight();
            to = 0;
            if (from <= 0 || view.getVisibility() != View.VISIBLE) {
                finish(view, false);
                return;
            }
        }
        ValueAnimator animator = ValueAnimator.ofInt(from, to);
        animator.setDuration(DURATION_MS);
        animator.addUpdateListener(a -> {
            params.height = (Integer) a.getAnimatedValue();
            view.setLayoutParams(params);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                view.setTag(R.id.sectionAnimator, null);
                if (!cancelled) finish(view, show);
            }
        });
        view.setTag(R.id.sectionAnimator, animator);
        animator.start();
    }

    private static void finish(View view, boolean show) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null) params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        view.setVisibility(show ? View.VISIBLE : View.GONE);
        view.requestLayout();
    }
}
