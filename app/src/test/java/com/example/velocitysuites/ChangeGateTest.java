package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A silent refresh may only redraw a screen when the data really changed (otherwise the card the guest has open
 * collapses every 30 seconds). ChangeGate is that decision: same content -> false, anything different -> true.
 */
public class ChangeGateTest {

    private static Booking booking(String id, String status, double paid) {
        Booking b = new Booking(id, "1", "Deluxe", "Deluxe", "Oct 09, 2026", "Oct 11, 2026", 2, 4000.0, status, "Oct 08, 2026");
        b.setAmountPaid(paid);
        return b;
    }

    private static Notification notification(String id, boolean read) {
        return new Notification(id, "Payment verified", "Your payment was verified", "Oct 09, 2026 10:00 AM", "payment", read);
    }

    @Test
    public void theFirstContentIsAlwaysAChange() {
        assertTrue(new ChangeGate().accept(Collections.singletonList(booking("1", "Pending", 0))));
    }

    @Test
    public void identicalContent_isNotAChange_evenWhenItIsBrandNewObjects() {
        ChangeGate gate = new ChangeGate();
        gate.accept(Arrays.asList(booking("1", "Pending", 0), booking("2", "Confirmed", 1000)));

        boolean changed = gate.accept(Arrays.asList(booking("1", "Pending", 0), booking("2", "Confirmed", 1000)));

        assertFalse("a poll that returns the same data (as fresh objects) must not redraw", changed);
    }

    @Test
    public void aChangedFieldIsAChange_thenQuietAgain() {
        ChangeGate gate = new ChangeGate();
        gate.accept(Collections.singletonList(booking("1", "Pending", 0)));

        assertTrue("status changed", gate.accept(Collections.singletonList(booking("1", "Confirmed", 0))));
        assertFalse("and the same again is quiet", gate.accept(Collections.singletonList(booking("1", "Confirmed", 0))));
        assertTrue("amount paid changed", gate.accept(Collections.singletonList(booking("1", "Confirmed", 500))));
    }

    @Test
    public void anAddedOrRemovedItemIsAChange() {
        ChangeGate gate = new ChangeGate();
        gate.accept(Collections.singletonList(booking("1", "Pending", 0)));

        assertTrue(gate.accept(Arrays.asList(booking("1", "Pending", 0), booking("2", "Pending", 0))));
        assertTrue(gate.accept(Collections.singletonList(booking("2", "Pending", 0))));
        assertTrue(gate.accept(new ArrayList<Booking>()));
        assertFalse(gate.accept(new ArrayList<Booking>()));
    }

    @Test
    public void aDifferentOrderIsAChange() {
        ChangeGate gate = new ChangeGate();
        gate.accept(Arrays.asList(booking("1", "Pending", 0), booking("2", "Pending", 0)));

        assertTrue(gate.accept(Arrays.asList(booking("2", "Pending", 0), booking("1", "Pending", 0))));
    }

    @Test
    public void severalPiecesOfContent_areComparedTogether() {
        ChangeGate gate = new ChangeGate();
        List<Booking> bookings = Collections.singletonList(booking("1", "Pending", 0));
        gate.accept(bookings, Collections.singletonList(notification("n1", false)));

        assertFalse(gate.accept(Collections.singletonList(booking("1", "Pending", 0)), Collections.singletonList(notification("n1", false))));
        assertTrue("a notification being read counts", gate.accept(bookings, Collections.singletonList(notification("n1", true))));
        assertTrue("a new notification counts", gate.accept(bookings, Arrays.asList(notification("n2", false), notification("n1", true))));
    }

    @Test
    public void forget_makesTheNextContentAChange() {
        ChangeGate gate = new ChangeGate();
        gate.accept("same");
        assertFalse(gate.accept("same"));

        gate.forget();

        assertTrue(gate.accept("same"));
        assertFalse(gate.accept("same"));
    }

    @Test
    public void nullsAndEmptyAreHandled() {
        ChangeGate gate = new ChangeGate();
        assertTrue(gate.accept((Object) null));
        assertFalse(gate.accept((Object) null));
        assertTrue(gate.accept(new Object[0]));
        assertTrue(gate.accept("x", null));
    }

    /**
     * Two models that point at each other cannot be turned into JSON (Gson only skips a field that refers to its own
     * object, not a longer cycle). The gate must then say "changed" - never crash, never say "same".
     */
    private static final class Ping {
        Pong other;
    }

    private static final class Pong {
        Ping other;
    }

    private static Ping cycle() {
        Ping ping = new Ping();
        Pong pong = new Pong();
        ping.other = pong;
        pong.other = ping;
        return ping;
    }

    @Test
    public void somethingThatCannotBeFingerprinted_isReportedAsChanged_everyTime() {
        ChangeGate gate = new ChangeGate();
        Ping cyclic = cycle();

        assertTrue(gate.accept(cyclic));
        assertTrue("never silently treated as unchanged", gate.accept(cyclic));
        assertEquals("and the fingerprint itself says 'unknown'", null, ChangeGate.fingerprint(cyclic));
    }

    @Test
    public void theFingerprintIsStableAndShort() {
        String a = ChangeGate.fingerprint(booking("1", "Pending", 0));
        String b = ChangeGate.fingerprint(booking("1", "Pending", 0));
        assertNotNull(a);
        assertEquals(a, b);
        assertEquals("SHA-256 as hex, so it stays small however many bookings it covers", 64, a.length());
    }
}
