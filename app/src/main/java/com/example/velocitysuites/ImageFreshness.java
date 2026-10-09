package com.example.velocitysuites;

import androidx.annotation.NonNull;

import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.Key;
import com.bumptech.glide.signature.ObjectKey;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lets the landing page's pull-to-refresh get past Glide's image cache.
 * <p>
 * Glide stores a downloaded picture under its URL, so a picture the admin replaced under the SAME address would
 * keep showing the old file for as long as the cache holds it. Every Glide request that goes through
 * {@link #apply} carries a "generation" signature as part of that cache key; the guest's own pull-to-refresh calls
 * {@link #bump()}, which makes every later request a cache miss and downloads the current file.
 * <p>
 * Two deliberate limits:
 * <ul>
 *   <li>Only a guest-initiated refresh bumps it. The silent 30-second refresh does not - that would re-download
 *       every picture twice a minute. (A picture whose URL changed is fetched anyway: a new URL is a new key.)</li>
 *   <li>While the new copy downloads, the previous generation's copy - if it is cached - stays on screen as a
 *       "thumbnail" (cache lookup only, so it never causes a download of its own), instead of every card flashing
 *       back to its placeholder. If it isn't cached the thumbnail request just fails quietly; Glide ignores a
 *       failed thumbnail and only reports the real request's outcome.</li>
 * </ul>
 */
public final class ImageFreshness {

    private static final AtomicInteger generation = new AtomicInteger();

    private ImageFreshness() {
    }

    /** The guest asked for fresh data: every picture loaded from now on is fetched again rather than taken from the cache. */
    public static void bump() {
        generation.incrementAndGet();
    }

    /** The cache-key part every request currently carries. Equal keys = may share a cached copy. */
    @NonNull
    public static Key currentSignature() {
        return new ObjectKey(generation.get());
    }

    /** Adds the current generation to {@code request} (and keeps the previous one visible while the new one loads). */
    @NonNull
    public static <T> RequestBuilder<T> apply(@NonNull RequestBuilder<T> request) {
        int current = generation.get();
        // Build the cache-only look at the previous generation BEFORE changing the signature on the builder itself.
        RequestBuilder<T> previous = current == 0
                ? null
                : request.clone().signature(new ObjectKey(current - 1)).onlyRetrieveFromCache(true);
        RequestBuilder<T> fresh = request.signature(new ObjectKey(current));
        return previous == null ? fresh : fresh.thumbnail(previous);
    }
}
