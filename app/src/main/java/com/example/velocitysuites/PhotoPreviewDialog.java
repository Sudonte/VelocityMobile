package com.example.velocitysuites;

import android.app.Activity;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.bumptech.glide.Glide;

/** The photo that was just taken or chosen, shown before it is used, with the two choices (Use photo / Retake, or Save / Cancel). */
public final class PhotoPreviewDialog {

    private PhotoPreviewDialog() {
    }

    /**
     * @param guidance   a line above the picture (what makes a good photo); 0 for none
     * @param confirm    the main button ("Use photo", "Save")
     * @param other      the other button ("Retake", "Cancel")
     * @param onOther    what the other button does (null = just close)
     */
    public static AlertDialog show(@NonNull Activity activity, @NonNull Uri photo, @StringRes int title, @StringRes int guidance,
                            @StringRes int confirm, @StringRes int other, @NonNull Runnable onConfirm, @Nullable Runnable onOther) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_photo_preview, null);
        TextView guide = view.findViewById(R.id.photoPreviewGuidance);
        if (guidance != 0) {
            guide.setText(guidance);
        } else {
            guide.setVisibility(View.GONE);
        }
        ImageView image = view.findViewById(R.id.photoPreviewImage);
        Glide.with(activity).load(photo).override(1080, 1080).into(image);
        return new SafeDialogs.Material(activity)
                .setTitle(title)
                .setView(view)
                .setCancelable(false)
                .setPositiveButton(confirm, (d, w) -> onConfirm.run())
                .setNegativeButton(other, (d, w) -> {
                    if (onOther != null) onOther.run();
                })
                .show();
    }
}
