package com.example.velocitysuites;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;

/**
 * Decodes a picked photo at a screen-sized resolution instead of full size. A 12-megapixel gallery photo is ~48 MB as
 * a Bitmap: decoding it whole on the main thread froze the screen and could run the app out of memory.
 */
final class ImageDecoding {

    static final int DEFAULT_MAX_EDGE = 1280;

    private ImageDecoding() { }

    /** Largest power-of-two sample size that keeps both edges at or above maxEdge (1 = no reduction). */
    static int sampleSizeFor(int width, int height, int maxEdge) {
        if (width <= 0 || height <= 0 || maxEdge <= 0) return 1;
        int sample = 1;
        while ((width / (sample * 2)) >= maxEdge && (height / (sample * 2)) >= maxEdge) {
            sample *= 2;
        }
        return sample;
    }

    /** The decoded bitmap, or null when the image can't be read (missing file, corrupt data, not enough memory). */
    @Nullable
    static Bitmap decode(ContentResolver resolver, Uri uri, int maxEdge) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = resolver.openInputStream(uri)) {
                if (in == null) return null;
                BitmapFactory.decodeStream(in, null, bounds);
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxEdge);
            try (InputStream in = resolver.openInputStream(uri)) {
                if (in == null) return null;
                return BitmapFactory.decodeStream(in, null, opts);
            }
        } catch (IOException | SecurityException | OutOfMemoryError e) {
            return null;
        }
    }
}
