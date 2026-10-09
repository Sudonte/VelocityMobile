package com.example.velocitysuites;

import android.os.Handler;
import android.os.Looper;

/**
 * A repeating "check the server again" beat that runs only while its screen is in the foreground.
 * <p>
 * Call {@link #start()} from {@code onResume()} and {@link #stop()} from {@code onPause()} (and, as a belt and
 * braces, {@code onDestroy()}). Between the two, {@code task} runs every {@link #INTERVAL_MS}; outside them it
 * never runs, so a screen that is covered, minimised or finished costs no traffic and can't be touched by a late
 * tick. Three guarantees the screens used to each re-implement by hand:
 * <ul>
 *   <li><b>One timer, ever.</b> There is a single tick {@code Runnable} and a {@code running} flag, so a second
 *       {@code start()} (a repeated onResume, a start without the matching stop) can't create a second timer.</li>
 *   <li><b>Stop means stop.</b> {@code stop()} removes the pending tick; a tick that was already on its way when
 *       {@code stop()} ran does nothing.</li>
 *   <li><b>The first beat is one interval after start().</b> Whether the screen also reloads the moment it becomes
 *       visible is the screen's own decision - it knows whether this resume follows its initial load.</li>
 * </ul>
 * Main thread only. The scheduler is injectable so the JVM tests can crank the clock by hand.
 */
public final class VisiblePoller {

    /** The app-wide beat - the same 30 seconds the website's notifications page refreshes on. */
    public static final long INTERVAL_MS = 30_000L;

    /** Where ticks are scheduled: a main-looper Handler in the app, a hand-cranked fake in the tests. */
    public interface Scheduler {
        void postDelayed(Runnable runnable, long delayMs);

        void cancel(Runnable runnable);
    }

    private final Runnable task;
    private final long intervalMs;
    private final Scheduler scheduler;
    private boolean running;
    /** The one and only tick, so cancel(tick) always removes exactly what was scheduled. */
    private final Runnable tick = this::onTick;

    /** A poller on the main looper at the app-wide interval. */
    public VisiblePoller(Runnable task) {
        this(task, INTERVAL_MS, new MainLooperScheduler());
    }

    public VisiblePoller(Runnable task, long intervalMs, Scheduler scheduler) {
        this.task = task;
        this.intervalMs = intervalMs;
        this.scheduler = scheduler;
    }

    /** The screen became visible. Safe to call again: a poller that is already running is left alone. */
    public void start() {
        if (running) return;
        running = true;
        scheduler.postDelayed(tick, intervalMs);
    }

    /** The screen is no longer visible (or is going away). Safe to call when not running. */
    public void stop() {
        running = false;
        scheduler.cancel(tick);
    }

    public boolean isRunning() {
        return running;
    }

    private void onTick() {
        if (!running) return; // stop() raced a tick that was already dispatched
        // Schedule the next beat BEFORE running the task, so a task that calls stop() cancels it.
        scheduler.postDelayed(tick, intervalMs);
        task.run();
    }

    /** Kept in its own class so the JVM tests, which never use it, don't need the Android Looper at all. */
    private static final class MainLooperScheduler implements Scheduler {
        private final Handler handler = new Handler(Looper.getMainLooper());

        @Override
        public void postDelayed(Runnable runnable, long delayMs) {
            handler.postDelayed(runnable, delayMs);
        }

        @Override
        public void cancel(Runnable runnable) {
            handler.removeCallbacks(runnable);
        }
    }
}
