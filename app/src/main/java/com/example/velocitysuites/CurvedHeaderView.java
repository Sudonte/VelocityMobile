package com.example.velocitysuites;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * Self-contained Login/Registration hero header: draws the full red brand
 * gradient AND the dome-shaped red-and-white curve transition to the page
 * background in one onDraw pass, all measured from this view's own actual
 * runtime width/height. Deliberately a single view (not a separate
 * background-photo View plus a separate curve View joined by negative
 * margins) so there is no seam, gap, or misalignment between "the red area"
 * and "the curve" to keep in sync across screen sizes/densities - every
 * coordinate below is derived from getWidth()/getHeight() at draw time, so
 * it always fills exactly this view's bounds edge to edge regardless of
 * device.
 *
 * The bottom curveZone of the view is reserved for the dome transition: the
 * curve's two ends sit flush with the view's bottom-left/bottom-right
 * corners (zero white intrusion at the edges) and its peak reaches up
 * curveZone from the bottom at the horizontal center - so the entire bottom
 * edge of the view is white edge-to-edge, guaranteeing a seamless handoff
 * to whatever sits directly below it in the layout.
 */
public class CurvedHeaderView extends android.view.View {

    private final Paint heroPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint domeFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint domeStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path domePath = new Path();
    private final Path curveLinePath = new Path();
    private final android.graphics.Rect heroBounds = new android.graphics.Rect();

    private int colorDeep, colorMedium, colorDark, colorPageBg;

    public CurvedHeaderView(Context context) {
        super(context);
        init(context);
    }

    public CurvedHeaderView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CurvedHeaderView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        colorDeep = ContextCompat.getColor(context, R.color.velocity_red_deep);
        colorMedium = ContextCompat.getColor(context, R.color.velocity_red_medium);
        colorDark = ContextCompat.getColor(context, R.color.velocity_red_dark);
        colorPageBg = ContextCompat.getColor(context, R.color.velocity_red_bg_end);

        domeFillPaint.setStyle(Paint.Style.FILL);
        domeFillPaint.setColor(colorPageBg);

        domeStrokePaint.setStyle(Paint.Style.STROKE);
        domeStrokePaint.setColor(ContextCompat.getColor(context, R.color.velocity_red_primary));
        domeStrokePaint.setStrokeWidth(context.getResources().getDisplayMetrics().density * 3f);
        domeStrokePaint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;

        heroBounds.set(0, 0, w, h);
        heroPaint.setShader(new LinearGradient(
                0, 0, w * 0.75f, h,
                new int[]{colorDeep, colorMedium, colorDark},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP));

        float curveZone = Math.min(h * 0.32f, getResources().getDisplayMetrics().density * 64f);
        float peakY = h - curveZone;

        domePath.reset();
        domePath.moveTo(0, h);
        domePath.quadTo(w / 2f, peakY, w, h);
        domePath.lineTo(w, h);
        domePath.lineTo(0, h);
        domePath.close();

        curveLinePath.reset();
        curveLinePath.moveTo(0, h);
        curveLinePath.quadTo(w / 2f, peakY, w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(heroBounds, heroPaint);
        canvas.drawPath(domePath, domeFillPaint);
        canvas.drawPath(curveLinePath, domeStrokePaint);
    }
}
