package com.example.velocitysuites;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

/**
 * The Welcome screen's small effects: the logo fading and scaling in, then the heading, then the four cards one after another; a card
 * shrinking a little while it is pressed; the sheet's steps fading in. Each takes 150-300 ms, and all of them are skipped (the final state is
 * there at once) when the system animations are off.
 */
public final class WelcomeEffects {

    public static final long STEP_MS = 200L;
    private static final long ENTER_MS = 260L;
    private static final long STAGGER_MS = 70L;

    /** Tests switch the motion off so the screen they read is always the final one. */
    public static boolean motion = true;

    private WelcomeEffects() {
    }

    static boolean enabled(@NonNull Context c) {
        return motion && Motion.enabled(c) && ValueAnimator.areAnimatorsEnabled();
    }

    /** Fades the pieces in, in order: logo (also scaling), brand, heading, line, then each card. */
    static void enter(@NonNull Context c, @NonNull View[] inOrder) {
        if (!enabled(c)) return;
        float dy = 12 * c.getResources().getDisplayMetrics().density;
        for (int i = 0; i < inOrder.length; i++) {
            View v = inOrder[i];
            v.setAlpha(0f);
            v.setTranslationY(dy);
            if (i == 0) {
                v.setScaleX(0.85f);
                v.setScaleY(0.85f);
            }
            v.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                    .setStartDelay(i * STAGGER_MS).setDuration(ENTER_MS).start();
        }
    }

    /** A card shrinks to 97% while a finger is on it (the ripple comes from its foreground); it never consumes the touch. */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    static void pressScale(@NonNull View card) {
        card.setOnTouchListener((v, e) -> {
            if (!enabled(v.getContext())) return false;
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.97f).scaleY(0.97f).setStartDelay(0).setDuration(120L).start();
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(150L).start();
            }
            return false;
        });
    }
}
