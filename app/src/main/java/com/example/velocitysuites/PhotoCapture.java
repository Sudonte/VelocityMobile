package com.example.velocitysuites;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;

import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.util.function.Supplier;

/**
 * Take a photo with the camera or choose one from the gallery - the one place that does it for the profile photo and the ID upload.
 *
 * <p>The camera permission is asked only when the guest taps "Take a photo", after a short line saying why; a refusal shows a plain
 * message, and a refusal that cannot be asked again offers the app's Settings page. The photo goes to a private file the camera app is
 * allowed to write through a FileProvider (never the small preview bitmap); a missing camera app or gallery is a message, not a crash. The
 * file being written survives the screen being recreated (process death): {@link #saveState} / {@link #restoreState}.
 *
 * <p>Create it in {@code onCreate} (it registers the result launchers). {@link Listener#onPhoto} gets a Uri that
 * {@link IdImageCompressor#compress} reads - the original, untouched; compressing and straightening it is the caller's next step.
 */
public final class PhotoCapture {

    /** Why no photo came back, as something the screen can say. */
    public enum Problem { NO_CAMERA_APP, NO_GALLERY, PERMISSION_DENIED, CAMERA_FAILED }

    public interface Listener {
        /** A photo was taken or chosen; the Uri is readable right now. */
        void onPhoto(@NonNull Uri photo);

        /** Nothing to use: say so (no camera, permission refused, ...). The guest cancelling is not a problem and is not reported. */
        void onProblem(@NonNull Problem problem);
    }

    private static final String KEY_PENDING = "photo_capture_pending";
    private static final String PREFS = "VelocityPhotoCapture";
    private static final String KEY_ASKED = "camera_permission_asked";

    private final Supplier<Activity> activity;
    private final Listener listener;
    private final ActivityResultLauncher<String> permissionLauncher;
    private final ActivityResultLauncher<Uri> cameraLauncher;
    private final ActivityResultLauncher<String> galleryLauncher;
    @Nullable private File pending;

    public PhotoCapture(@NonNull ActivityResultCaller caller, @NonNull Supplier<Activity> activity, @NonNull Listener listener) {
        this.activity = activity;
        this.listener = listener;
        this.permissionLauncher = caller.registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) {
                launchCamera();
            } else {
                afterRefusal();
            }
        });
        this.cameraLauncher = caller.registerForActivityResult(new ActivityResultContracts.TakePicture(), saved -> {
            File file = pending;
            pending = null;
            if (file == null) return;
            if (saved && file.length() > 0) {
                listener.onPhoto(uriFor(file));
            } else {
                //noinspection ResultOfMethodCallIgnored
                file.delete(); // cancelled in the camera app: nothing to report
            }
        });
        this.galleryLauncher = caller.registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) listener.onPhoto(uri);
        });
    }

    // ---- the two ways in ----

    /** Opens the camera (after the permission step); the photo arrives in {@link Listener#onPhoto}. */
    public void takePhoto() {
        Activity a = activity.get();
        if (a == null) return;
        if (new Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(a.getPackageManager()) == null) {
            listener.onProblem(Problem.NO_CAMERA_APP);
            return;
        }
        if (ContextCompat.checkSelfPermission(a, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
            return;
        }
        boolean askedBefore = prefs(a).getBoolean(KEY_ASKED, false);
        if (!askedBefore || ActivityCompat.shouldShowRequestPermissionRationale(a, Manifest.permission.CAMERA)) {
            // say why first, then ask
            new SafeDialogs.Material(a)
                    .setTitle(R.string.camera_why_title)
                    .setMessage(R.string.camera_why_message)
                    .setPositiveButton(R.string.camera_why_continue, (d, w) -> {
                        prefs(a).edit().putBoolean(KEY_ASKED, true).apply();
                        permissionLauncher.launch(Manifest.permission.CAMERA);
                    })
                    .setNegativeButton(R.string.cancel_label, null)
                    .show();
        } else {
            offerSettings(a); // refused before and cannot be asked again
        }
    }

    /** Opens the gallery picker; the chosen photo arrives in {@link Listener#onPhoto}. */
    public void chooseFromGallery() {
        try {
            galleryLauncher.launch("image/*");
        } catch (ActivityNotFoundException e) {
            listener.onProblem(Problem.NO_GALLERY);
        }
    }

    // ---- the camera ----

    private void launchCamera() {
        Activity a = activity.get();
        if (a == null) return;
        try {
            File dir = new File(a.getCacheDir(), "photos");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("no photo folder");
            pending = File.createTempFile("capture_", ".jpg", dir);
            cameraLauncher.launch(uriFor(pending));
        } catch (ActivityNotFoundException e) {
            pending = null;
            listener.onProblem(Problem.NO_CAMERA_APP);
        } catch (IOException | RuntimeException e) {
            pending = null;
            listener.onProblem(Problem.CAMERA_FAILED);
        }
    }

    /** How a photo file becomes the content Uri the camera app writes to; a test can swap it (the real one needs the device's file layout). */
    @androidx.annotation.VisibleForTesting
    public static java.util.function.BiFunction<Context, File, Uri> uriFactory =
            (c, file) -> FileProvider.getUriForFile(c, c.getPackageName() + ".fileprovider", file);

    private Uri uriFor(File file) {
        return uriFactory.apply(activity.get(), file);
    }

    private void afterRefusal() {
        Activity a = activity.get();
        if (a == null) return;
        if (ActivityCompat.shouldShowRequestPermissionRationale(a, Manifest.permission.CAMERA)) {
            listener.onProblem(Problem.PERMISSION_DENIED); // it can be asked again: a plain message
        } else {
            offerSettings(a);
        }
    }

    private void offerSettings(Activity a) {
        new SafeDialogs.Material(a)
                .setTitle(R.string.camera_denied_title)
                .setMessage(R.string.camera_denied_message)
                .setPositiveButton(R.string.camera_open_settings, (d, w) -> {
                    Intent settings = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", a.getPackageName(), null));
                    try {
                        a.startActivity(settings);
                    } catch (ActivityNotFoundException e) {
                        listener.onProblem(Problem.PERMISSION_DENIED);
                    }
                })
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---- surviving the screen being recreated ----

    public void saveState(@NonNull Bundle out) {
        if (pending != null) out.putString(KEY_PENDING, pending.getAbsolutePath());
    }

    public void restoreState(@Nullable Bundle in) {
        if (in == null) return;
        String path = in.getString(KEY_PENDING);
        if (path != null) pending = new File(path);
    }
}
