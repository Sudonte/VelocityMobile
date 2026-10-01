package com.example.velocitysuites;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * Start/end icons for a TextView, sized from its own text size and tinted in code.
 * <p>
 * A TextView draws a compound drawable at the drawable's intrinsic size (24dp for this app's vector icons),
 * which is far too big beside an 11-12sp label and would not follow the guest's system font size. Sizing the
 * icon as a multiple of the text size keeps it in proportion at every font scale, and lets an "icon + label"
 * pair stay ONE TextView instead of a LinearLayout holding an ImageView and a TextView (two extra views per
 * row of a long list - lint's UseCompoundDrawables).
 */
final class TextIcons {

    private TextIcons() {
    }

    /** A fresh copy of the icon, {@code sizePx} square and tinted; null for res 0 or a drawable that failed to load. */
    @Nullable
    static Drawable sized(@NonNull Context ctx, @DrawableRes int res, int sizePx, @ColorInt int tint) {
        if (res == 0) return null;
        Drawable source = ContextCompat.getDrawable(ctx, res);
        if (source == null) return null;
        // mutate(): sizing/tinting this copy must never recolor the shared drawable other views are showing.
        Drawable icon = source.mutate();
        icon.setBounds(0, 0, sizePx, sizePx);
        icon.setTint(tint);
        return icon;
    }

    /**
     * Sets the view's start and end icons (0 = none), each {@code scale} x the view's text size and tinted like
     * its current text color - so call it after the text color is final.
     */
    static void setRelative(@NonNull TextView view, @DrawableRes int startRes, @DrawableRes int endRes, float scale) {
        Context ctx = view.getContext();
        int size = Math.round(view.getTextSize() * scale);
        int tint = view.getCurrentTextColor();
        view.setCompoundDrawablesRelative(sized(ctx, startRes, size, tint), null, sized(ctx, endRes, size, tint), null);
    }
}
