package com.example.velocitysuites;

import java.util.function.LongSupplier;

/**
 * Swallows a repeated tap. A second tap on "View Transaction", a card, Export or Retry that lands inside
 * {@link #DEFAULT_WINDOW_MS} of the first is ignored, so a nervous double-tap can't open the same screen
 * twice, stack two confirm dialogs or fire two identical requests. One guard per screen, shared by all of
 * that screen's tap handlers: a tap on the card followed straight away by a tap on one of its buttons is the
 * same gesture, not two.
 * <p>
 * The clock is injectable so the window boundaries are unit-testable (see ClickGuardTest).
 */
public final class ClickGuard {

    public static final long DEFAULT_WINDOW_MS = 700;

    private final long windowMs;
    private final LongSupplier clock;
    private boolean hasAccepted;
    private long lastAcceptedAt;

    public ClickGuard() {
        this(DEFAULT_WINDOW_MS, android.os.SystemClock::elapsedRealtime);
    }

    ClickGuard(long windowMs, LongSupplier clock) {
        this.windowMs = windowMs;
        this.clock = clock;
    }

    /** True if this tap should be handled; false if it is a repeat of one just handled. */
    public boolean tryAcquire() {
        long now = clock.getAsLong();
        if (hasAccepted && now - lastAcceptedAt < windowMs) return false;
        hasAccepted = true;
        lastAcceptedAt = now;
        return true;
    }
}
