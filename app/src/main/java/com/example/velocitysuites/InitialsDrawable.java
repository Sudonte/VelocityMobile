package com.example.velocitysuites;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/** A round avatar with the guest's initials, shown when there is no profile photo. */
final class InitialsDrawable extends Drawable {

    private final String initials;
    private final Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

    InitialsDrawable(@Nullable String fullName, int backgroundColor, int textColor) {
        this.initials = initialsOf(fullName);
        circle.setColor(backgroundColor);
        text.setColor(textColor);
        text.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        text.setTextAlign(Paint.Align.CENTER);
    }

    /** "Ana Reyes" -> "AR", "Madonna" -> "M", "  " -> "?". At most two letters, upper case. */
    @NonNull
    static String initialsOf(@Nullable String fullName) {
        if (fullName == null) return "?";
        String[] parts = fullName.trim().split("\\s+");
        java.util.List<String> words = new java.util.ArrayList<>();
        for (String p : parts) {
            if (!p.isEmpty() && Character.isLetter(p.codePointAt(0))) words.add(p);
        }
        if (words.isEmpty()) return "?";
        StringBuilder out = new StringBuilder();
        out.appendCodePoint(Character.toUpperCase(words.get(0).codePointAt(0))); // first name
        if (words.size() > 1) out.appendCodePoint(Character.toUpperCase(words.get(words.size() - 1).codePointAt(0))); // last name
        return out.toString().toUpperCase(Locale.ROOT);
    }

    String initials() {
        return initials;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        float r = Math.min(b.width(), b.height()) / 2f;
        canvas.drawCircle(b.exactCenterX(), b.exactCenterY(), r, circle);
        text.setTextSize(r * 0.8f);
        float y = b.exactCenterY() - (text.descent() + text.ascent()) / 2f;
        canvas.drawText(initials, b.exactCenterX(), y, text);
    }

    @Override
    public void setAlpha(int alpha) {
        circle.setAlpha(alpha);
        text.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        circle.setColorFilter(colorFilter);
        text.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
