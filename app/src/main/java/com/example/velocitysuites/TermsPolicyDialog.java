package com.example.velocitysuites;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** The full-screen, scrollable Terms and Policy viewer with a pinned Done button - the same one everywhere. */
public final class TermsPolicyDialog {

    private TermsPolicyDialog() {
    }

    /**
     * @param addendum optional transaction-specific text appended to the shared document
     * @param onOpened called as soon as the Terms are on screen - opening them is what unlocks the agreement checkbox
     */
    public static void show(Context context, @Nullable CharSequence addendum, @Nullable Runnable onOpened) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_terms_agreement, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(context).setView(dialogView).create();

        ((TextView) dialogView.findViewById(R.id.termsBodyText)).setText(TermsContent.build(context, addendum));
        dialogView.findViewById(R.id.termsCloseButton).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.termsDoneButton).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        if (onOpened != null) onOpened.run();
    }
}
