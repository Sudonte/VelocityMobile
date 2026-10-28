package com.example.velocitysuites;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.lang.ref.WeakReference;

/** The sheet that asks where a photo comes from: "Take a photo", "Choose from gallery", optionally "Remove photo", and Cancel. */
final class PhotoSourceSheet {

    interface Choice {
        void takePhoto();

        void chooseFromGallery();

        /** Only called when the sheet was opened with a remove option. */
        default void removePhoto() {
        }
    }

    // one sheet at a time, and never a leak of the screen it belongs to
    private static WeakReference<BottomSheetDialog> showing = new WeakReference<>(null);

    private PhotoSourceSheet() {
    }

    @android.annotation.SuppressLint("InflateParams") // a sheet's content view has no parent to inflate into
    static BottomSheetDialog show(@NonNull Activity activity, @StringRes int title, @StringRes int hint, boolean withRemove, @NonNull Choice choice) {
        BottomSheetDialog previous = showing.get();
        if (previous != null && previous.isShowing()) previous.dismiss();
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View view = LayoutInflater.from(activity).inflate(R.layout.sheet_photo_source, null);
        ((TextView) view.findViewById(R.id.photoSheetTitle)).setText(title);
        TextView hintView = view.findViewById(R.id.photoSheetHint);
        if (hint != 0) {
            hintView.setText(hint);
            hintView.setVisibility(View.VISIBLE);
        }
        view.findViewById(R.id.photoSheetTake).setOnClickListener(v -> {
            dialog.dismiss();
            choice.takePhoto();
        });
        view.findViewById(R.id.photoSheetGallery).setOnClickListener(v -> {
            dialog.dismiss();
            choice.chooseFromGallery();
        });
        View remove = view.findViewById(R.id.photoSheetRemove);
        remove.setVisibility(withRemove ? View.VISIBLE : View.GONE);
        remove.setOnClickListener(v -> {
            dialog.dismiss();
            choice.removePhoto();
        });
        view.findViewById(R.id.photoSheetCancel).setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(view);
        dialog.setOnShowListener(d -> {
            // open fully, even in landscape or with a large font
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet).setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        });
        showing = new WeakReference<>(dialog);
        SafeDialogs.show(activity, dialog);
        return dialog;
    }
}
