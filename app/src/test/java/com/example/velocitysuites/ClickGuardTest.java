package com.example.velocitysuites;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ClickGuardTest {

    private long now;

    private ClickGuard guard() {
        return new ClickGuard(700, () -> now);
    }

    @Test
    public void firstTapIsAlwaysHandled_evenAtTimeZero() {
        now = 0;
        assertTrue(guard().tryAcquire());
    }

    @Test
    public void aSecondTapInsideTheWindowIsIgnored() {
        ClickGuard g = guard();
        now = 1_000;
        assertTrue(g.tryAcquire());
        now = 1_300;
        assertFalse("double tap", g.tryAcquire());
        now = 1_699;
        assertFalse("still inside the window", g.tryAcquire());
    }

    @Test
    public void aTapAfterTheWindowIsHandledAgain() {
        ClickGuard g = guard();
        now = 1_000;
        assertTrue(g.tryAcquire());
        now = 1_700;
        assertTrue(g.tryAcquire());
    }

    @Test
    public void anIgnoredTapDoesNotExtendTheWindow() {
        ClickGuard g = guard();
        now = 0;
        assertTrue(g.tryAcquire());
        now = 600;
        assertFalse(g.tryAcquire());   // ignored - must not restart the 700ms
        now = 701;
        assertTrue("window counts from the last HANDLED tap", g.tryAcquire());
    }
}
