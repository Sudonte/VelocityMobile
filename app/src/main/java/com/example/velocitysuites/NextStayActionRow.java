package com.example.velocitysuites;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The action row of a Your Next Stay card: child 0 is the outlined "Request Amenities" button (gone unless the guest is
 * checked in), child 1 is "View details ›". Side by side when both fit - the button on the left, View details on the
 * right - otherwise stacked (the full-width button first, View details right-aligned under it), so the two can never
 * overlap and the button's label never has to wrap. The choice is made from the real text widths, so it also holds at a
 * large font scale.
 */
public class NextStayActionRow extends LinearLayout {

    private static final int GAP_DP = 8;

    public NextStayActionRow(@NonNull Context context) {
        super(context);
    }

    public NextStayActionRow(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (getChildCount() == 2) arrange(MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight());
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void arrange(int available) {
        View button = getChildAt(0);
        View details = getChildAt(1);
        LayoutParams buttonParams = (LayoutParams) button.getLayoutParams();
        LayoutParams detailsParams = (LayoutParams) details.getLayoutParams();
        int gap = Math.round(GAP_DP * getResources().getDisplayMetrics().density);
        int unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);

        boolean stacked = false;
        if (button.getVisibility() != GONE) {
            buttonParams.width = LayoutParams.WRAP_CONTENT;
            buttonParams.weight = 0;
            button.measure(unspecified, unspecified);
            details.measure(unspecified, unspecified);
            stacked = button.getMeasuredWidth() + gap + details.getMeasuredWidth() > available;
        }

        if (stacked) {
            setOrientation(VERTICAL);
            buttonParams.width = LayoutParams.MATCH_PARENT;
            buttonParams.weight = 0;
            buttonParams.setMarginEnd(0);
            detailsParams.width = LayoutParams.MATCH_PARENT;
            detailsParams.weight = 0;
            detailsParams.topMargin = 0;
        } else {
            setOrientation(HORIZONTAL);
            buttonParams.setMarginEnd(button.getVisibility() == GONE ? 0 : gap);
            detailsParams.width = 0;
            detailsParams.weight = 1;
            detailsParams.topMargin = 0;
        }
        setGravity(Gravity.CENTER_VERTICAL);
        details.setLayoutParams(detailsParams);
        button.setLayoutParams(buttonParams);
    }

    /** True when the two actions are stacked (the full-width button above View details). */
    public boolean isStacked() {
        return getOrientation() == VERTICAL;
    }
}
