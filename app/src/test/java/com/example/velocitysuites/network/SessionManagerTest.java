package com.example.velocitysuites.network;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pure-JVM coverage for SessionManager#isRememberMeStillValid() - the
 * Context-free decision hasValidRememberedSession() delegates to (see that
 * method's own doc for why it was extracted; this project has no
 * Robolectric, so anything touching Context/SharedPreferences directly
 * can't be unit-tested here - same rationale as PaymentStatusResolverTest).
 */
public class SessionManagerTest {

    private static final long NOW = 1_000_000_000L;

    @Test
    public void validRememberMe_notYetExpired_hasToken_isValid() {
        assertTrue(SessionManager.isRememberMeStillValid(true, NOW + 1, NOW, true));
    }

    @Test
    public void expiredRememberMe_isRejected() {
        assertFalse(SessionManager.isRememberMeStillValid(true, NOW - 1, NOW, true));
    }

    @Test
    public void expiryExactlyNow_isRejected() {
        // now < expiresAt is strict - the instant a window elapses, it's gone, not "valid through this millisecond."
        assertFalse(SessionManager.isRememberMeStillValid(true, NOW, NOW, true));
    }

    @Test
    public void rememberMeDisabled_isRejectedRegardlessOfExpiry() {
        assertFalse(SessionManager.isRememberMeStillValid(false, NOW + 1_000_000, NOW, true));
    }

    @Test
    public void missingToken_isRejectedEvenIfRememberMeFlagAndExpiryLookValid() {
        assertFalse(SessionManager.isRememberMeStillValid(true, NOW + 1, NOW, false));
    }
}
