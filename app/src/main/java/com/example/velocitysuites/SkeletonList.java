package com.example.velocitysuites;

import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

/**
 * Placeholder cards for a list that is still loading, so a screen is never blank or briefly shows "nothing yet" before its first
 * answer. They pulse softly (not at all when the system's animations are off).
 */
final class SkeletonList {

    static final int COUNT = 4;

    private SkeletonList() {
    }

    /** Fills {@code box} with placeholder cards (once) and starts the pulse. */
    static void fill(@NonNull LinearLayout box) {
        if (box.getChildCount() == 0) {
            LayoutInflater inflater = LayoutInflater.from(box.getContext());
            for (int i = 0; i < COUNT; i++) box.addView(inflater.inflate(R.layout.view_card_skeleton, box, false));
        }
        box.clearAnimation();
        if (Motion.enabled(box.getContext())) box.startAnimation(AnimationUtils.loadAnimation(box.getContext(), R.anim.skeleton_pulse));
    }

    static void setVisible(@NonNull LinearLayout box, boolean visible) {
        if (visible) fill(box);
        else box.clearAnimation();
        box.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
