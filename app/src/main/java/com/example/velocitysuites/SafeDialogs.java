package com.example.velocitysuites;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Dialog builders whose show() quietly does nothing when the owning screen is already finishing or destroyed - the
 * usual result of a network callback landing after the guest pressed Back or rotated, which otherwise crashes with
 * a WindowManager.BadTokenException. Drop-in replacements for MaterialAlertDialogBuilder / AlertDialog.Builder.
 */
final class SafeDialogs {

    private SafeDialogs() { }

    /** True when the context's Activity (if it has one) can still host a window. */
    static boolean canShow(@Nullable Context context) {
        Context c = context;
        while (c instanceof ContextWrapper) {
            if (c instanceof Activity) {
                Activity a = (Activity) c;
                return !a.isFinishing() && !a.isDestroyed();
            }
            c = ((ContextWrapper) c).getBaseContext();
        }
        return true;
    }

    /** Same as {@link #show(Context, Dialog)}, using the dialog's own context to find its screen. */
    static void show(@NonNull Dialog dialog) {
        show(dialog.getContext(), dialog);
    }

    /** Shows the already-created dialog if its screen is still alive; never throws for a dead window. */
    static void show(@Nullable Context context, @NonNull Dialog dialog) {
        if (!canShow(context)) return;
        try {
            dialog.show();
        } catch (WindowManager.BadTokenException ignored) {
            // The window went away between the check and the call - nothing to show it on.
        }
    }

    static final class Material extends MaterialAlertDialogBuilder {
        Material(@NonNull Context context) {
            super(context);
        }

        Material(@NonNull Context context, int overrideThemeResId) {
            super(context, overrideThemeResId);
        }

        @NonNull
        @Override
        public AlertDialog show() {
            AlertDialog dialog = create();
            SafeDialogs.show(getContext(), dialog);
            return dialog;
        }
    }

    static final class Plain extends AlertDialog.Builder {
        Plain(@NonNull Context context) {
            super(context);
        }

        Plain(@NonNull Context context, int themeResId) {
            super(context, themeResId);
        }

        @NonNull
        @Override
        public AlertDialog show() {
            AlertDialog dialog = create();
            SafeDialogs.show(getContext(), dialog);
            return dialog;
        }
    }
}
