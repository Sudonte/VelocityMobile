package com.example.velocitysuites;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
// minSdk 29: the InputStream constructor of the platform ExifInterface is reliable here; no extra dependency needed.
import android.annotation.SuppressLint;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Downscales and re-encodes a picked ID photo as JPEG before it is uploaded, so a multi-MB camera
 * original neither lags the upload nor trips the server's 5 MB image limit. The longest edge is
 * capped (IDs stay legible for the receptionist) and the EXIF rotation is baked into the pixels,
 * since the re-encoded file carries no EXIF.
 */
@SuppressLint("ExifInterface")
final class IdImageCompressor {

    static final int MAX_EDGE_PX = 1600;
    static final int JPEG_QUALITY = 80;

    private IdImageCompressor() {
    }

    /** Writes the compressed copy of {@code source} into {@code dest}; throws if the image can't be decoded. */
    static void compress(Context context, Uri source, File dest) throws IOException {
        ContentResolver resolver = context.getContentResolver();

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = resolver.openInputStream(source)) {
            if (in == null) throw new IOException("Could not open selected image.");
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Not a readable image.");

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_EDGE_PX);
        Bitmap decoded;
        try (InputStream in = resolver.openInputStream(source)) {
            if (in == null) throw new IOException("Could not open selected image.");
            decoded = BitmapFactory.decodeStream(in, null, opts);
        }
        if (decoded == null) throw new IOException("Not a readable image.");

        Bitmap result = decoded;
        try {
            int longest = Math.max(decoded.getWidth(), decoded.getHeight());
            Matrix matrix = new Matrix();
            if (longest > MAX_EDGE_PX) {
                float scale = (float) MAX_EDGE_PX / longest;
                matrix.postScale(scale, scale);
            }
            matrix.postConcat(rotationFor(resolver, source));
            if (!matrix.isIdentity()) {
                result = Bitmap.createBitmap(decoded, 0, 0, decoded.getWidth(), decoded.getHeight(), matrix, true);
            }
            try (FileOutputStream out = new FileOutputStream(dest)) {
                if (!result.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                    throw new IOException("Could not compress image.");
                }
            }
        } finally {
            if (result != decoded) result.recycle();
            decoded.recycle();
        }
    }

    /** Largest power-of-two sample size that keeps the longest edge at or above {@code maxEdge}. */
    static int sampleSizeFor(int width, int height, int maxEdge) {
        int sample = 1;
        int longest = Math.max(width, height);
        while (longest / (sample * 2) >= maxEdge) sample *= 2;
        return sample;
    }

    private static Matrix rotationFor(ContentResolver resolver, Uri source) {
        Matrix matrix = new Matrix();
        try (InputStream in = resolver.openInputStream(source)) {
            if (in == null) return matrix;
            int orientation = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90: matrix.postRotate(90); break;
                case ExifInterface.ORIENTATION_ROTATE_180: matrix.postRotate(180); break;
                case ExifInterface.ORIENTATION_ROTATE_270: matrix.postRotate(270); break;
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matrix.postScale(-1, 1); break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL: matrix.postScale(1, -1); break;
                default: break;
            }
        } catch (IOException | RuntimeException ignored) {
            // No readable EXIF - keep the pixels as decoded.
        }
        return matrix;
    }
}
