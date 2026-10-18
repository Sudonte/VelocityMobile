package com.example.velocitysuites;

import android.content.Context;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * The preview rule shared by every Dashboard list section: collapsed shows the first {@link #PREVIEW_COUNT} items and a
 * "Show all (N)" button, expanded shows every item and "Show less". A list of {@link #PREVIEW_COUNT} or fewer shows
 * everything and no button. Pure rules plus the one button, so the behaviour is the same everywhere and unit-testable.
 */
final class SectionPreview {

    /** How many items a collapsed list section shows. */
    static final int PREVIEW_COUNT = 3;

    private SectionPreview() {
    }

    /** The items to draw. */
    @NonNull
    static <T> List<T> visible(@NonNull List<T> all, boolean expanded) {
        return expanded || all.size() <= PREVIEW_COUNT ? all : all.subList(0, PREVIEW_COUNT);
    }

    /** True when the list is longer than the preview, so "Show all / Show less" is needed. */
    static boolean needsToggle(int total) {
        return total > PREVIEW_COUNT;
    }

    /** Adds the "Show all (N)" / "Show less" button at the end of a list (nothing, and null, when {@link #needsToggle} is false). */
    @Nullable
    static MaterialButton addToggle(@NonNull Context context, @NonNull ViewGroup container, int total, boolean expanded,
                                    @NonNull Runnable onToggle) {
        if (!needsToggle(total)) return null;
        MaterialButton button = new MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setId(R.id.btnPreviewToggle);
        button.setText(expanded ? context.getString(R.string.preview_show_less) : context.getString(R.string.preview_show_all_format, total));
        float density = context.getResources().getDisplayMetrics().density;
        button.setMinHeight((int) (48 * density));
        ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (10 * density);
        container.addView(button, lp);
        button.setOnClickListener(v -> onToggle.run());
        return button;
    }
}
