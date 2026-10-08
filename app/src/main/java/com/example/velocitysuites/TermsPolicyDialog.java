package com.example.velocitysuites;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** The full-screen, scrollable Terms and Policy viewer with a pinned Done button - the same one everywhere. */
public final class TermsPolicyDialog {

    private TermsPolicyDialog() {
    }

    /**
     * @param addendum optional transaction-specific text appended to the shared document
     * @param onRead called once the guest has scrolled to the end of the Terms (or immediately if the whole
     *               document fits on screen) - this is what unlocks the agreement checkbox
     */
    public static void show(Context context, @Nullable CharSequence addendum, @Nullable Runnable onRead) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_terms_agreement, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(context).setView(dialogView).create();

        ((TextView) dialogView.findViewById(R.id.termsBodyText)).setText(TermsContent.build(context, addendum));
        dialogView.findViewById(R.id.termsCloseButton).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.termsDoneButton).setOnClickListener(v -> dialog.dismiss());

        NestedScrollView scrollView = dialogView.findViewById(R.id.termsScrollView);
        boolean[] reported = {false};
        Runnable checkEnd = () -> {
            View content = scrollView.getChildAt(0);
            if (reported[0] || content == null) return;
            if (TermsGate.hasReachedEnd(scrollView.getScrollY(), scrollView.getHeight(), content.getHeight())) {
                reported[0] = true;
                if (onRead != null) onRead.run();
            }
        };
        scrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, x, y, oldX, oldY) -> checkEnd.run());
        scrollView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> checkEnd.run());

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }
}
