package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.example.velocitysuites.ImageFreshness;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;

/**
 * Pull-to-refresh has to get past Glide's picture cache: Glide files a downloaded picture under its URL, so a
 * picture replaced under the same address would otherwise keep showing the old file. ImageFreshness adds a
 * "generation" to every request's cache key; the guest's own refresh bumps it.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, application = TestApplication.class)
public class ImageFreshnessTest {

    private static final String URL = "https://example.test/room.jpg";

    private Context app;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
    }

    private RequestBuilder<Drawable> request() {
        return Glide.with(app).load(URL);
    }

    private static RequestBuilder<?> thumbnailOf(RequestBuilder<?> request) throws Exception {
        Field field = RequestBuilder.class.getDeclaredField("thumbnailBuilder");
        field.setAccessible(true);
        return (RequestBuilder<?>) field.get(request);
    }

    @Test
    public void theKeyIsStableUntilSomeoneAsksForFreshPictures() {
        assertEquals(ImageFreshness.currentSignature(), ImageFreshness.currentSignature());
    }

    @Test
    public void bumping_changesTheKey() {
        com.bumptech.glide.load.Key before = ImageFreshness.currentSignature();

        ImageFreshness.bump();

        assertNotEquals(before, ImageFreshness.currentSignature());
    }

    @Test
    public void everyRequestCarriesTheCurrentGeneration() {
        RequestBuilder<Drawable> applied = ImageFreshness.apply(request());

        assertEquals(ImageFreshness.currentSignature(), applied.getSignature());
    }

    @Test
    public void theSamePictureAfterABump_hasADifferentCacheKey_soItIsDownloadedAgain() {
        RequestBuilder<Drawable> before = ImageFreshness.apply(request());

        ImageFreshness.bump();
        RequestBuilder<Drawable> after = ImageFreshness.apply(request());

        assertNotEquals("same URL, new generation -> Glide treats it as a different cache entry", before.getSignature(), after.getSignature());
        assertEquals(ImageFreshness.currentSignature(), after.getSignature());
    }

    @Test
    public void withoutABump_theSamePictureKeepsItsCacheKey_soSilentRefreshesDoNotRedownloadPictures() {
        RequestBuilder<Drawable> first = ImageFreshness.apply(request());
        RequestBuilder<Drawable> second = ImageFreshness.apply(request());

        assertEquals(first.getSignature(), second.getSignature());
    }

    @Test
    public void afterABump_thePreviousCopyStaysOnScreenWhileTheNewOneLoads_fromTheCacheOnly() throws Exception {
        ImageFreshness.bump();

        RequestBuilder<Drawable> applied = ImageFreshness.apply(request());

        RequestBuilder<?> thumbnail = thumbnailOf(applied);
        assertNotNull("the previous generation is the thumbnail shown meanwhile", thumbnail);
        assertTrue("it must only look in the cache - it must never download the picture a second time", thumbnail.getOnlyRetrieveFromCache());
        assertNotEquals("and it is the OLD key, not the new one", applied.getSignature(), thumbnail.getSignature());
        assertFalse("the real request is allowed to hit the network", applied.getOnlyRetrieveFromCache());
    }

    @Test
    public void theCurrentRequestKeepsItsOwnOptions() {
        RequestBuilder<Drawable> applied = ImageFreshness.apply(request().centerCrop().placeholder(android.R.drawable.ic_menu_gallery));

        assertTrue("centerCrop() set before apply() must survive it", applied.isTransformationSet());
        assertFalse(applied.getOnlyRetrieveFromCache());
    }
}
