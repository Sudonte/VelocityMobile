package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Camera and gallery for the profile photo and the ID (1.68.0): the permission is explained before it is asked and a refusal is handled,
 * the camera writes a real photo through the FileProvider, a missing camera app is a message, big photos are shrunk to 1600px, EXIF rotation
 * is baked in, and an unreadable photo is an error the screen can say - never a crash.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, application = com.example.velocitysuites.ui.TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PhotoHandlingTest {

    private final Context app = ApplicationProvider.getApplicationContext();

    @Before
    public void setUp() {
        app.getSharedPreferences("VelocityPhotoCapture", Context.MODE_PRIVATE).edit().clear().apply();
        // the real FileProvider needs the device's folder layout; the manifest + file_paths.xml are checked in TransactionHistoryPolishTest
        PhotoCapture.uriFactory = (c, f) -> Uri.parse("content://" + c.getPackageName() + ".fileprovider/photos/" + f.getName());
        shadowOf((android.app.Application) app).denyPermissions(Manifest.permission.CAMERA);
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    // ---- straightening and shrinking ----

    private File jpeg(int w, int h, int orientation) throws IOException {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        b.eraseColor(0xFF336699);
        File f = File.createTempFile("src_", ".jpg", app.getCacheDir());
        try (FileOutputStream out = new FileOutputStream(f)) {
            b.compress(Bitmap.CompressFormat.JPEG, 95, out);
        }
        ExifInterface exif = new ExifInterface(f.getAbsolutePath());
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(orientation));
        exif.saveAttributes();
        return f;
    }

    private int[] sizeOf(File f) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        return new int[]{o.outWidth, o.outHeight};
    }

    @Test
    public void aLargeCameraPhoto_isShrunkToTheCap_withoutLosingItsShape() throws Exception {
        File out = File.createTempFile("out_", ".jpg", app.getCacheDir());
        IdImageCompressor.compress(app, Uri.fromFile(jpeg(4000, 3000, ExifInterface.ORIENTATION_NORMAL)), out);
        int[] size = sizeOf(out);
        assertTrue("longest edge <= 1600: " + size[0] + "x" + size[1], Math.max(size[0], size[1]) <= 1600);
        assertTrue("still landscape", size[0] > size[1]);
        assertEquals("4:3 kept", 4.0 / 3.0, size[0] / (double) size[1], 0.02);
        assertEquals("JPEG at about 80%", 80, IdImageCompressor.JPEG_QUALITY);
    }

    @Test
    public void aSidewaysPhoto_isTurnedUpright_becauseTheCompressedCopyHasNoExif() throws Exception {
        // the camera stored 3000x2000 pixels with "rotate 90" (a portrait shot)
        File out = File.createTempFile("out_", ".jpg", app.getCacheDir());
        IdImageCompressor.compress(app, Uri.fromFile(jpeg(3000, 2000, ExifInterface.ORIENTATION_ROTATE_90)), out);
        int[] size = sizeOf(out);
        assertTrue("now portrait: " + size[0] + "x" + size[1], size[1] > size[0]);
        assertTrue(Math.max(size[0], size[1]) <= 1600);
        assertEquals(ExifInterface.ORIENTATION_UNDEFINED, new ExifInterface(out.getAbsolutePath()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED));
    }

    @Test
    public void aSmallPhoto_keepsItsSize() throws Exception {
        File out = File.createTempFile("out_", ".jpg", app.getCacheDir());
        IdImageCompressor.compress(app, Uri.fromFile(jpeg(800, 600, ExifInterface.ORIENTATION_NORMAL)), out);
        assertEquals(800, sizeOf(out)[0]);
    }

    @Test
    public void anUnreadablePhoto_isAnErrorTheScreenCanSay_notACrash() throws Exception {
        File notAnImage = File.createTempFile("bad_", ".jpg", app.getCacheDir());
        try (FileOutputStream out = new FileOutputStream(notAnImage)) {
            out.write("this is not a picture".getBytes());
        }
        File out = File.createTempFile("out_", ".jpg", app.getCacheDir());
        try {
            IdImageCompressor.compress(app, Uri.fromFile(notAnImage), out);
            fail("an unreadable photo must be refused");
        } catch (IOException expected) {
            // the screen shows "That photo could not be read"
        }
        try {
            IdImageCompressor.compress(app, Uri.fromFile(new File(app.getCacheDir(), "missing.jpg")), out);
            fail("a missing photo must be refused");
        } catch (IOException expected) {
            // same
        }
    }

    @Test
    public void initials_comeFromTheName_atMostTwoLetters() {
        assertEquals("AR", InitialsDrawable.initialsOf("Ana Reyes"));
        assertEquals("first and last name", "AR", InitialsDrawable.initialsOf("  ana   maria  reyes "));
        assertEquals("M", InitialsDrawable.initialsOf("Madonna"));
        assertEquals("?", InitialsDrawable.initialsOf("   "));
        assertEquals("?", InitialsDrawable.initialsOf(null));
        assertEquals("É", InitialsDrawable.initialsOf("élodie"));
    }

    // ---- the source sheet ----

    @Test
    public void theSheet_offersTakeAndChoose_andRemoveOnlyWhenThereIsAPhoto() {
        ResetPasswordActivity host = Robolectric.buildActivity(ResetPasswordActivity.class).setup().get();
        final List<String> chosen = new ArrayList<>();
        PhotoSourceSheet.Choice choice = new PhotoSourceSheet.Choice() {
            @Override public void takePhoto() { chosen.add("take"); }
            @Override public void chooseFromGallery() { chosen.add("gallery"); }
            @Override public void removePhoto() { chosen.add("remove"); }
        };

        BottomSheetDialog without = PhotoSourceSheet.show(host, R.string.profile_photo_sheet_title, 0, false, choice);
        assertEquals("Take a photo", ((android.widget.TextView) without.findViewById(R.id.photoSheetTake)).getText().toString());
        assertEquals("Choose from gallery", ((android.widget.TextView) without.findViewById(R.id.photoSheetGallery)).getText().toString());
        assertEquals(View.GONE, without.findViewById(R.id.photoSheetRemove).getVisibility());
        assertEquals("Cancel", ((android.widget.TextView) without.findViewById(R.id.photoSheetCancel)).getText().toString());
        without.findViewById(R.id.photoSheetCancel).performClick();
        assertFalse(without.isShowing());

        BottomSheetDialog with = PhotoSourceSheet.show(host, R.string.profile_photo_sheet_title, 0, true, choice);
        assertEquals(View.VISIBLE, with.findViewById(R.id.photoSheetRemove).getVisibility());
        assertEquals("Remove photo", ((android.widget.TextView) with.findViewById(R.id.photoSheetRemove)).getText().toString());
        with.findViewById(R.id.photoSheetTake).performClick();
        PhotoSourceSheet.show(host, R.string.profile_photo_sheet_title, 0, true, choice).findViewById(R.id.photoSheetGallery).performClick();
        PhotoSourceSheet.show(host, R.string.profile_photo_sheet_title, 0, true, choice).findViewById(R.id.photoSheetRemove).performClick();
        assertEquals(java.util.Arrays.asList("take", "gallery", "remove"), chosen);

    }

    // ---- the camera and its permission ----

    private static final class Probe implements PhotoCapture.Listener {
        final List<Uri> photos = new ArrayList<>();
        final List<PhotoCapture.Problem> problems = new ArrayList<>();
        @Override public void onPhoto(Uri photo) { photos.add(photo); }
        @Override public void onProblem(PhotoCapture.Problem p) { problems.add(p); }
    }

    private void withCameraApp() {
        ResolveInfo info = new ResolveInfo();
        info.activityInfo = new android.content.pm.ActivityInfo();
        info.activityInfo.packageName = "com.example.camera";
        info.activityInfo.name = "Capture";
        shadowOf(app.getPackageManager()).addResolveInfoForIntent(new Intent(MediaStore.ACTION_IMAGE_CAPTURE), info);
    }

    /** The capture helper is created before the screen starts, like a real screen does in onCreate. */
    private ActivityController<ResetPasswordActivity> hostWith(Probe probe, PhotoCapture[] out) {
        ActivityController<ResetPasswordActivity> controller = Robolectric.buildActivity(ResetPasswordActivity.class).create();
        ResetPasswordActivity a = controller.get();
        out[0] = new PhotoCapture(a, () -> a, probe);
        controller.start().resume().visible();
        idle();
        return controller;
    }

    private static AlertDialog latest() {
        return (AlertDialog) ShadowDialog.getLatestDialog();
    }

    private static String titleOf(AlertDialog d) {
        return ((android.widget.TextView) d.findViewById(androidx.appcompat.R.id.alertTitle)).getText().toString();
    }

    @Test
    public void noCameraApp_isAMessage_notACrash() {
        Probe probe = new Probe();
        PhotoCapture[] pc = new PhotoCapture[1];
        hostWith(probe, pc);
        pc[0].takePhoto();
        assertEquals(java.util.Collections.singletonList(PhotoCapture.Problem.NO_CAMERA_APP), probe.problems);
    }

    @Test
    public void theCameraPermission_isExplainedFirst_andAskedOnlyAfterContinue() {
        withCameraApp();
        Probe probe = new Probe();
        PhotoCapture[] pc = new PhotoCapture[1];
        ActivityController<ResetPasswordActivity> controller = hostWith(probe, pc);

        pc[0].takePhoto();
        idle();
        AlertDialog why = latest();
        assertEquals("Use your camera?", titleOf(why));
        assertTrue(((android.widget.TextView) why.findViewById(android.R.id.message)).getText().toString().contains("only to take the photo you ask for"));
        assertNull("nothing is asked before the guest agrees", shadowOf(controller.get()).getNextStartedActivityForResult());

        why.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        shadowOf(controller.get()).getNextStartedActivityForResult(); // the system permission screen opened
        assertTrue("remembered that it was asked once", app.getSharedPreferences("VelocityPhotoCapture", Context.MODE_PRIVATE).getBoolean("camera_permission_asked", false));
    }

    @Test
    public void aCameraRefusedForGood_offersTheAppSettings() {
        withCameraApp();
        app.getSharedPreferences("VelocityPhotoCapture", Context.MODE_PRIVATE).edit().putBoolean("camera_permission_asked", true).apply();
        Probe probe = new Probe();
        PhotoCapture[] pc = new PhotoCapture[1];
        ActivityController<ResetPasswordActivity> controller = hostWith(probe, pc);

        pc[0].takePhoto();
        idle();
        AlertDialog denied = latest();
        assertEquals("Camera is turned off", titleOf(denied));
        denied.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        Intent settings = shadowOf(controller.get()).getNextStartedActivity();
        assertNotNull(settings);
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, settings.getAction());
        assertEquals("package:" + app.getPackageName(), settings.getDataString());
        assertTrue(probe.photos.isEmpty());
    }

    @Test
    public void withThePermission_theCameraWritesAFullPhotoThroughTheFileProvider_andItComesBack() throws Exception {
        withCameraApp();
        shadowOf((android.app.Application) app).grantPermissions(Manifest.permission.CAMERA);
        Probe probe = new Probe();
        PhotoCapture[] pc = new PhotoCapture[1];
        ActivityController<ResetPasswordActivity> controller = hostWith(probe, pc);

        pc[0].takePhoto();
        idle();
        assertTrue("problems: " + probe.problems + " dialog: " + ShadowDialog.getLatestDialog(), probe.problems.isEmpty());
        Intent camera = shadowOf(controller.get()).getNextStartedActivityForResult().intent;
        assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, camera.getAction());
        Uri target = camera.getParcelableExtra(MediaStore.EXTRA_OUTPUT);
        assertNotNull("a destination file, not the small preview bitmap", target);
        assertEquals(app.getPackageName() + ".fileprovider", target.getAuthority());
        assertTrue("the camera app may write it", (camera.getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0);

        // the camera app saved the picture, then the guest confirmed it
        File dir = new File(app.getCacheDir(), "photos");
        File[] files = dir.listFiles();
        assertNotNull(files);
        File written = files[0];
        try (FileOutputStream out = new FileOutputStream(written)) {
            Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, out);
        }
        shadowOf(controller.get()).receiveResult(camera, Activity.RESULT_OK, new Intent());
        idle();
        assertEquals(1, probe.photos.size());
        assertEquals(app.getPackageName() + ".fileprovider", probe.photos.get(0).getAuthority());
    }

    @Test
    public void cancellingTheCamera_reportsNothing_andLeavesNoEmptyFile() throws Exception {
        withCameraApp();
        shadowOf((android.app.Application) app).grantPermissions(Manifest.permission.CAMERA);
        Probe probe = new Probe();
        PhotoCapture[] pc = new PhotoCapture[1];
        ActivityController<ResetPasswordActivity> controller = hostWith(probe, pc);
        pc[0].takePhoto();
        idle();
        Intent camera = shadowOf(controller.get()).getNextStartedActivityForResult().intent;
        shadowOf(controller.get()).receiveResult(camera, Activity.RESULT_CANCELED, null);
        idle();
        assertTrue(probe.photos.isEmpty());
        assertTrue(probe.problems.isEmpty());
        File[] left = new File(app.getCacheDir(), "photos").listFiles();
        assertTrue("the empty capture file is removed", left == null || left.length == 0);
    }

    @Test
    public void theFileBeingWritten_survivesTheScreenBeingRecreated() throws Exception {
        withCameraApp();
        shadowOf((android.app.Application) app).grantPermissions(Manifest.permission.CAMERA);
        Probe probe = new Probe();
        PhotoCapture[] pc = new PhotoCapture[1];
        ActivityController<ResetPasswordActivity> controller = hostWith(probe, pc);
        pc[0].takePhoto();
        idle();
        Bundle saved = new Bundle();
        pc[0].saveState(saved);
        assertNotNull("the pending file is in the saved state", saved.getString("photo_capture_pending"));

        // the screen is recreated (process death): a new screen, a new helper, the saved state handed back
        ActivityController<ResetPasswordActivity> again2 = Robolectric.buildActivity(ResetPasswordActivity.class).create();
        PhotoCapture restored = new PhotoCapture(again2.get(), () -> again2.get(), probe);
        restored.restoreState(saved);
        Bundle again = new Bundle();
        restored.saveState(again);
        assertEquals(saved.getString("photo_capture_pending"), again.getString("photo_capture_pending"));
    }

    @Test
    public void theGalleryOpensThePickerForImages() {
        Probe probe = new Probe();
        PhotoCapture[] pc = new PhotoCapture[1];
        ActivityController<ResetPasswordActivity> controller = hostWith(probe, pc);
        pc[0].chooseFromGallery();
        Intent picker = shadowOf(controller.get()).getNextStartedActivityForResult().intent;
        assertEquals(Intent.ACTION_GET_CONTENT, picker.getAction());
        assertEquals("image/*", picker.getType());
        shadowOf(controller.get()).receiveResult(picker, Activity.RESULT_OK, new Intent().setData(Uri.parse("content://media/external/images/1")));
        idle();
        assertEquals(1, probe.photos.size());
        assertEquals("content://media/external/images/1", probe.photos.get(0).toString());
    }
}
