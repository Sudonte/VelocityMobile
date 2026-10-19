package com.example.velocitysuites;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The name and the price of a room card: child 0 (the name) takes the free width, child 1 (the price) is as wide as its
 * text. Side by side when both fit on one line - the name on the left, the price on the right - otherwise the price sits
 * directly under the name, so neither is ever squeezed into a few letters per line or overlaps the other. The choice is
 * made from the real text widths, so it also holds at a large system font size.
 */
public class NamePriceRow extends LinearLayout {

    public NamePriceRow(@NonNull Context context) {
        super(context);
    }

    public NamePriceRow(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (getChildCount() == 2) arrange(MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight());
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void arrange(int available) {
        View name = getChildAt(0);
        View price = getChildAt(1);
        LayoutParams nameParams = (LayoutParams) name.getLayoutParams();
        LayoutParams priceParams = (LayoutParams) price.getLayoutParams();
        int gap = Math.round(8 * getResources().getDisplayMetrics().density);
        int unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);

        // The name's natural width on ONE line (a name that needs two lines next to the price is better stacked).
        nameParams.width = LayoutParams.WRAP_CONTENT;
        nameParams.weight = 0;
        name.setLayoutParams(nameParams);
        name.measure(unspecified, unspecified);
        price.measure(unspecified, unspecified);
        boolean stacked = name.getMeasuredWidth() + gap + price.getMeasuredWidth() > available;

        if (stacked) {
            setOrientation(VERTICAL);
            nameParams.width = LayoutParams.MATCH_PARENT;
            nameParams.weight = 0;
            priceParams.setMarginStart(0);
            priceParams.topMargin = Math.round(2 * getResources().getDisplayMetrics().density);
        } else {
            setOrientation(HORIZONTAL);
            nameParams.width = 0;
            nameParams.weight = 1;
            priceParams.setMarginStart(gap);
            priceParams.topMargin = 0;
        }
        name.setLayoutParams(nameParams);
        price.setLayoutParams(priceParams);
    }

    /** True when the price sits under the name instead of beside it. */
    public boolean isStacked() {
        return getOrientation() == VERTICAL;
    }
}
