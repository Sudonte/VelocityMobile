package com.example.velocitysuites;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.appcompat.widget.AppCompatImageView;

/**
 * ImageView with pinch-to-zoom, drag-to-pan (only while zoomed in) and
 * double-tap to toggle between fit and 3x. The image always starts fitted to
 * the view and can never be panned out of view.
 */
public class ZoomableImageView extends AppCompatImageView {

    private static final float MAX_SCALE_FACTOR = 5f;
    private static final float DOUBLE_TAP_SCALE_FACTOR = 3f;

    private final Matrix matrix = new Matrix();
    private final float[] values = new float[9];
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;

    private float fitScale = 1f;
    private float lastX;
    private float lastY;
    private int activePointerId = MotionEvent.INVALID_POINTER_ID;

    public ZoomableImageView(Context context) {
        this(context, null);
    }

    public ZoomableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setScaleType(ScaleType.MATRIX);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                zoomBy(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
                return true;
            }
        });
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                float current = currentScale();
                float target = current > fitScale * 1.05f ? fitScale : fitScale * DOUBLE_TAP_SCALE_FACTOR;
                zoomBy(target / current, e.getX(), e.getY());
                return true;
            }
        });
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        fitToView();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        fitToView();
    }

    private void fitToView() {
        Drawable d = getDrawable();
        int vw = getWidth();
        int vh = getHeight();
        if (d == null || vw == 0 || vh == 0 || d.getIntrinsicWidth() <= 0 || d.getIntrinsicHeight() <= 0) {
            return;
        }
        float dw = d.getIntrinsicWidth();
        float dh = d.getIntrinsicHeight();
        fitScale = Math.min(vw / dw, vh / dh);
        matrix.reset();
        matrix.postScale(fitScale, fitScale);
        matrix.postTranslate((vw - dw * fitScale) / 2f, (vh - dh * fitScale) / 2f);
        setImageMatrix(matrix);
    }

    private float currentScale() {
        matrix.getValues(values);
        return values[Matrix.MSCALE_X];
    }

    private void zoomBy(float factor, float px, float py) {
        float current = currentScale();
        if (current <= 0f) return;
        float target = Math.max(fitScale, Math.min(current * factor, fitScale * MAX_SCALE_FACTOR));
        float applied = target / current;
        matrix.postScale(applied, applied, px, py);
        clampToBounds();
        setImageMatrix(matrix);
    }

    private void clampToBounds() {
        Drawable d = getDrawable();
        if (d == null) return;
        RectF rect = new RectF(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
        matrix.mapRect(rect);
        float dx = 0;
        float dy = 0;
        if (rect.width() <= getWidth()) {
            dx = (getWidth() - rect.width()) / 2f - rect.left;
        } else if (rect.left > 0) {
            dx = -rect.left;
        } else if (rect.right < getWidth()) {
            dx = getWidth() - rect.right;
        }
        if (rect.height() <= getHeight()) {
            dy = (getHeight() - rect.height()) / 2f - rect.top;
        } else if (rect.top > 0) {
            dy = -rect.top;
        } else if (rect.bottom < getHeight()) {
            dy = getHeight() - rect.bottom;
        }
        matrix.postTranslate(dx, dy);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (getDrawable() == null) return super.onTouchEvent(event);
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activePointerId = event.getPointerId(0);
                lastX = event.getX();
                lastY = event.getY();
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(currentScale() > fitScale * 1.01f);
                break;
            case MotionEvent.ACTION_MOVE:
                if (!scaleDetector.isInProgress() && currentScale() > fitScale * 1.01f) {
                    int index = event.findPointerIndex(activePointerId);
                    if (index >= 0) {
                        float x = event.getX(index);
                        float y = event.getY(index);
                        matrix.postTranslate(x - lastX, y - lastY);
                        clampToBounds();
                        setImageMatrix(matrix);
                        lastX = x;
                        lastY = y;
                    }
                }
                break;
            case MotionEvent.ACTION_POINTER_UP: {
                int upIndex = event.getActionIndex();
                if (event.getPointerId(upIndex) == activePointerId) {
                    int newIndex = upIndex == 0 ? 1 : 0;
                    activePointerId = event.getPointerId(newIndex);
                    lastX = event.getX(newIndex);
                    lastY = event.getY(newIndex);
                }
                break;
            }
            case MotionEvent.ACTION_UP:
                performClick();
                activePointerId = MotionEvent.INVALID_POINTER_ID;
                break;
            case MotionEvent.ACTION_CANCEL:
                activePointerId = MotionEvent.INVALID_POINTER_ID;
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
