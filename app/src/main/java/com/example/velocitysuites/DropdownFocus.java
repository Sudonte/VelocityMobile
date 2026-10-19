package com.example.velocitysuites;

import android.view.View;
import android.widget.AutoCompleteTextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * An outlined exposed dropdown draws its border in the focus colour while the field has focus; the menu hands focus back to the field
 * when it closes, so after a pick (or a dismissed menu) the border stayed red. {@link #release} gives focus up - to {@code sink}, a
 * focusable view that is not an input, so Android does not hand it to the next field - and is repeated once the menu has closed.
 */
final class DropdownFocus {

    private DropdownFocus() {
    }

    static void release(@NonNull AutoCompleteTextView dropdown, @Nullable View sink) {
        dropdown.clearFocus();
        if (sink != null) sink.requestFocus();
    }

    /** Releases focus whenever the menu closes. Call {@link #release} yourself right after a pick, too. */
    static void attach(@NonNull AutoCompleteTextView dropdown, @Nullable View sink) {
        dropdown.setOnDismissListener(() -> dropdown.post(() -> release(dropdown, sink)));
    }
}
