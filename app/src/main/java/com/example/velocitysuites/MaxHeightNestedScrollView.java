package com.example.velocitysuites;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

/**
 * A NestedScrollView that actually enforces a maximum height. Plain
 * NestedScrollView (like ScrollView) silently ignores the standard
 * android:maxHeight attribute - only a handful of widgets such as ImageView
 * read it in onMeasure(), and generic ViewGroups never do. Several dialogs in
 * this app (dialog_room_details.xml) declared android:maxHeight expecting it
 * to cap the scrollable content, which never actually happened - with
 * wrap_content height the view grows to fit all of its content, unbounded,
 * which can push content/actions below a dialog's visible window bounds once
 * the content is tall enough (long description + full amenities list +
 * policies + quantity selector, on a small screen). setMaxHeightPx() below
 * provides the missing enforcement via a genuine onMeasure() override,
 * without changing surrounding LinearLayout weight semantics (unlike the
 * 0dp/layout_weight="1" pattern used for dialog_available_rooms.xml's room
 * list, which is appropriate there because that dialog's footer must always
 * claim the same amount of space; here the dialog should keep shrink-wrapping
 * to short content and only cap/scroll once content is genuinely too tall).
 */
public class MaxHeightNestedScrollView extends NestedScrollView {

    private int maxHeightPx = -1;

    public MaxHeightNestedScrollView(Context context) {
        super(context);
    }

    public MaxHeightNestedScrollView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public MaxHeightNestedScrollView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setMaxHeightPx(int maxHeightPx) {
        this.maxHeightPx = maxHeightPx;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (maxHeightPx >= 0) {
            int mode = View.MeasureSpec.getMode(heightMeasureSpec);
            int size = View.MeasureSpec.getSize(heightMeasureSpec);
            if (mode != View.MeasureSpec.EXACTLY || size > maxHeightPx) {
                heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(maxHeightPx, View.MeasureSpec.AT_MOST);
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
