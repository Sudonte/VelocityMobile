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

    /** "Show all (4)", or "Show all (99+)" once the list is longer than 99 - a number that big is never worth its width. */
    @NonNull
    static String showAllText(@NonNull Context context, int total) {
        return total > 99 ? context.getString(R.string.preview_show_all_overflow) : context.getString(R.string.preview_show_all_format, total);
    }

    /** Adds the "Show all (N)" / "Show less" button at the end of a list (nothing, and null, when {@link #needsToggle} is false). */
    @Nullable
    static MaterialButton addToggle(@NonNull Context context, @NonNull ViewGroup container, int total, boolean expanded,
                                    @NonNull Runnable onToggle) {
        if (!needsToggle(total)) return null;
        MaterialButton button = (MaterialButton) android.view.LayoutInflater.from(context).inflate(R.layout.view_preview_toggle, container, false);
        button.setText(expanded ? context.getString(R.string.preview_show_less) : showAllText(context, total));
        button.setIconResource(expanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
        container.addView(button);
        button.setOnClickListener(v -> onToggle.run());
        return button;
    }
}
