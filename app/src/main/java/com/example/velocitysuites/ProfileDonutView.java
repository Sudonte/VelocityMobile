package com.example.velocitysuites;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * A small donut showing how complete the guest's profile is: the arc from the top, clockwise, the percentage in the
 * middle. Green at 100%, amber from 50 to 99%, red below 50%; the unfilled track is a light neutral (dark neutral in dark
 * mode). The arc sweeps in the first time it is shown, and moves smoothly when the value changes later.
 */
public class ProfileDonutView extends View {

    private static final long ANIMATION_MS = 700L;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    private int percent;
    /** What is drawn right now (0..100), animated towards {@link #percent}. */
    private float shown;
    private boolean everShown;
    @Nullable private ValueAnimator animator;

    public ProfileDonutView(@NonNull Context context) {
        this(context, null);
    }

    public ProfileDonutView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float density = context.getResources().getDisplayMetrics().density;
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(8 * density);
        trackPaint.setColor(ContextCompat.getColor(context, R.color.status_cancelled_bg));
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(8 * density);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL));
        textPaint.setTextSize(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 20, context.getResources().getDisplayMetrics()));
        textPaint.setColor(ContextCompat.getColor(context, R.color.velocity_text_primary));
        setProgress(0, false);
    }

    /** The colour for a completeness percentage: green 100, amber 50-99, red below 50. */
    @ColorInt
    static int colorFor(@NonNull Context context, int percent) {
        int res = percent >= 100 ? R.color.velocity_green_primary : percent >= 50 ? R.color.velocity_orange_primary : R.color.velocity_red_primary;
        return ContextCompat.getColor(context, res);
    }

    public int getPercent() {
        return percent;
    }

    /** The value drawn right now (it differs from {@link #getPercent()} only while the arc is moving). */
    float getShownPercent() {
        return shown;
    }

    /**
     * @param animate sweep from the old value (or from 0 the first time) to the new one; false jumps straight there
     */
    public void setProgress(int newPercent, boolean animate) {
        percent = Math.max(0, Math.min(100, newPercent));
        arcPaint.setColor(colorFor(getContext(), percent));
        setContentDescription(getContext().getString(R.string.profile_donut_cd, percent));
        if (animator != null) animator.cancel();
        if (!animate || (everShown && shown == percent)) {
            shown = percent;
            everShown = true;
            invalidate();
            return;
        }
        float from = everShown ? shown : 0f;
        everShown = true;
        animator = ValueAnimator.ofFloat(from, percent);
        animator.setDuration(ANIMATION_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            shown = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = Math.round(88 * getResources().getDisplayMetrics().density);
        setMeasuredDimension(resolveSize(size, widthMeasureSpec), resolveSize(size, heightMeasureSpec));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float stroke = trackPaint.getStrokeWidth();
        float side = Math.min(getWidth(), getHeight());
        float left = (getWidth() - side) / 2f + stroke / 2f;
        float top = (getHeight() - side) / 2f + stroke / 2f;
        oval.set(left, top, left + side - stroke, top + side - stroke);
        canvas.drawArc(oval, 0, 360, false, trackPaint);
        if (shown > 0f) canvas.drawArc(oval, -90, 360f * shown / 100f, false, arcPaint);
        String label = percent + "%";
        float y = getHeight() / 2f - (textPaint.descent() + textPaint.ascent()) / 2f;
        canvas.drawText(label, getWidth() / 2f, y, textPaint);
    }
}
