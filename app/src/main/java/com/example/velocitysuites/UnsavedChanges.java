package com.example.velocitysuites;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.function.BooleanSupplier;

/**
 * "Discard your changes?" when the guest leaves a form (the Back button, the back gesture, or the screen's own back arrow if it calls
 * {@code getOnBackPressedDispatcher().onBackPressed()}) while something was entered. A clean form leaves at once.
 */
final class UnsavedChanges {

    private UnsavedChanges() {
    }

    static void guard(@NonNull AppCompatActivity activity, @NonNull BooleanSupplier hasChanges) {
        activity.getOnBackPressedDispatcher().addCallback(activity, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!hasChanges.getAsBoolean()) {
                    leave();
                    return;
                }
                new SafeDialogs.Material(activity)
                        .setTitle(R.string.form_discard_title)
                        .setMessage(R.string.form_discard_message)
                        .setNegativeButton(R.string.keep_editing, null)
                        .setPositiveButton(R.string.form_discard_yes, (d, w) -> leave())
                        .show();
            }

            private void leave() {
                setEnabled(false);
                activity.getOnBackPressedDispatcher().onBackPressed();
            }
        });
    }
}
