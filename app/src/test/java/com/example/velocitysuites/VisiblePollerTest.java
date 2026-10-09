package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The poller's lifecycle contract on the JVM: runs only between start() and stop(), never twice over, and
 * a stopped poller can't be woken by a tick that was already on its way. The clock is cranked by hand.
 */
public class VisiblePollerTest {

    private static final long INTERVAL = 30_000L;

    /** A scheduler with a virtual clock: nothing runs until advance() says time has passed. */
    private static final class FakeScheduler implements VisiblePoller.Scheduler {
        private final class Entry {
            final Runnable runnable;
            final long dueAt;

            Entry(Runnable runnable, long dueAt) {
                this.runnable = runnable;
                this.dueAt = dueAt;
            }
        }

        private final List<Entry> pending = new ArrayList<>();
        private long now;

        @Override
        public void postDelayed(Runnable runnable, long delayMs) {
            pending.add(new Entry(runnable, now + delayMs));
        }

        @Override
        public void cancel(Runnable runnable) {
            for (Iterator<Entry> it = pending.iterator(); it.hasNext(); ) {
                if (it.next().runnable == runnable) it.remove();
            }
        }

        int pendingCount() {
            return pending.size();
        }

        /** The tick that is queued right now, even if stop() has since removed it (to simulate a tick already dispatched). */
        Runnable firstPending() {
            return pending.get(0).runnable;
        }

        void advance(long ms) {
            long target = now + ms;
            while (true) {
                Entry next = null;
                for (Entry e : pending) {
                    if (e.dueAt <= target && (next == null || e.dueAt < next.dueAt)) next = e;
                }
                if (next == null) break;
                pending.remove(next);
                now = next.dueAt;
                next.runnable.run();
            }
            now = target;
        }
    }

    private FakeScheduler scheduler;
    private int runs;
    private VisiblePoller poller;

    @Before
    public void setUp() {
        scheduler = new FakeScheduler();
        runs = 0;
        poller = new VisiblePoller(() -> runs++, INTERVAL, scheduler);
    }

    @Test
    public void doesNothingUntilStarted() {
        scheduler.advance(10 * INTERVAL);

        assertEquals(0, runs);
        assertFalse(poller.isRunning());
        assertEquals(0, scheduler.pendingCount());
    }

    @Test
    public void firstBeatIsOneIntervalAfterStart_notImmediately() {
        poller.start();

        assertEquals("start() itself does not poll - the screen decides whether resume reloads", 0, runs);
        scheduler.advance(INTERVAL - 1);
        assertEquals(0, runs);
        scheduler.advance(1);
        assertEquals(1, runs);
    }

    @Test
    public void keepsBeatingEveryInterval() {
        poller.start();

        scheduler.advance(5 * INTERVAL);

        assertEquals(5, runs);
        assertEquals("exactly one tick is ever queued", 1, scheduler.pendingCount());
    }

    @Test
    public void stopEndsItImmediately() {
        poller.start();
        scheduler.advance(INTERVAL);
        assertEquals(1, runs);

        poller.stop();
        scheduler.advance(10 * INTERVAL);

        assertEquals("no beat after stop", 1, runs);
        assertFalse(poller.isRunning());
        assertEquals("and nothing is left queued to leak", 0, scheduler.pendingCount());
    }

    @Test
    public void startingTwice_neverCreatesASecondTimer() {
        poller.start();
        poller.start();
        poller.start();

        assertEquals(1, scheduler.pendingCount());
        scheduler.advance(3 * INTERVAL);
        assertEquals("three starts, still one beat per interval", 3, runs);
    }

    @Test
    public void stopThenStart_resumesWithExactlyOneTimer() {
        poller.start();
        scheduler.advance(INTERVAL);
        poller.stop();
        scheduler.advance(5 * INTERVAL); // screen covered: nothing runs
        assertEquals(1, runs);

        poller.start();
        scheduler.advance(2 * INTERVAL);

        assertEquals("one beat per interval again, not two", 3, runs);
        assertEquals(1, scheduler.pendingCount());
    }

    @Test
    public void stopWithoutStart_isHarmless() {
        poller.stop();
        poller.stop();

        scheduler.advance(INTERVAL);
        assertEquals(0, runs);
    }

    @Test
    public void aTickAlreadyDispatchedWhenStopRuns_doesNothing() {
        poller.start();
        Runnable inFlight = scheduler.firstPending();

        poller.stop();
        inFlight.run(); // the Looper had already pulled this tick off the queue

        assertEquals(0, runs);
        assertEquals("and it must not have re-armed the timer", 0, scheduler.pendingCount());
    }

    @Test
    public void aTaskThatStopsThePoller_isNotRescheduled() {
        VisiblePoller[] holder = new VisiblePoller[1];
        holder[0] = new VisiblePoller(() -> {
            runs++;
            holder[0].stop(); // e.g. the screen finishes itself from inside the refresh
        }, INTERVAL, scheduler);

        holder[0].start();
        scheduler.advance(5 * INTERVAL);

        assertEquals(1, runs);
        assertEquals(0, scheduler.pendingCount());
        assertFalse(holder[0].isRunning());
    }

    @Test
    public void isRunning_followsTheLifecycle() {
        assertFalse(poller.isRunning());
        poller.start();
        assertTrue(poller.isRunning());
        poller.stop();
        assertFalse(poller.isRunning());
    }
}
