package com.example.velocitysuites;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The one aligned action row of a Booking / Reservation card: child 0 holds the buttons on the LEFT (Pay now, View details,
 * Book again ...), child 1 the text actions on the RIGHT (Edit · Cancel, Delete). Side by side when both fit; otherwise the
 * text actions wrap to a second row, right-aligned - never hidden behind a menu, and no button text ever wraps. The choice is
 * made from the real widths, so it also holds at a large font size.
 */
public class CardActionRow extends LinearLayout {

    public CardActionRow(@NonNull Context context) {
        super(context);
    }

    public CardActionRow(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (getChildCount() == 2) arrange(MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight());
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void arrange(int available) {
        View left = getChildAt(0);
        View right = getChildAt(1);
        LayoutParams leftParams = (LayoutParams) left.getLayoutParams();
        LayoutParams rightParams = (LayoutParams) right.getLayoutParams();
        int unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        boolean leftShown = left.getVisibility() != GONE;
        boolean rightShown = right.getVisibility() != GONE;
        boolean stacked = false;
        if (leftShown && rightShown) {
            leftParams.width = LayoutParams.WRAP_CONTENT;
            leftParams.weight = 0;
            left.setLayoutParams(leftParams);
            left.measure(unspecified, unspecified);
            right.measure(unspecified, unspecified);
            int gap = Math.round(8 * getResources().getDisplayMetrics().density);
            stacked = left.getMeasuredWidth() + gap + right.getMeasuredWidth() > available;
        }
        if (stacked) {
            setOrientation(VERTICAL);
            leftParams.width = LayoutParams.WRAP_CONTENT;
            leftParams.weight = 0;
            rightParams.width = LayoutParams.MATCH_PARENT;
            rightParams.weight = 0;
            ((LinearLayout) right).setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        } else {
            setOrientation(HORIZONTAL);
            leftParams.width = 0;
            leftParams.weight = 1;
            rightParams.width = LayoutParams.WRAP_CONTENT;
            rightParams.weight = 0;
            ((LinearLayout) right).setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        }
        left.setLayoutParams(leftParams);
        right.setLayoutParams(rightParams);
    }

    /** True when the right-hand text actions sit on their own row under the buttons. */
    public boolean isStacked() {
        return getOrientation() == VERTICAL;
    }
}
