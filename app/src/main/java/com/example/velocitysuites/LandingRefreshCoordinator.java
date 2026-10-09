package com.example.velocitysuites;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Decides WHEN the landing page re-fetches its backend-loaded sections and WHAT the outcome of one refresh is -
 * pure Java, no Android types, so the rules are unit-tested on the JVM. (This project has no ViewModel layer:
 * screens are Activities over the RoomRepository singleton, so this is the one place the refresh logic lives
 * rather than being scattered through LandingActivity's callbacks.)
 * <p>
 * The rules:
 * <ul>
 *   <li>A refresh asks EVERY section to load again and waits until every one has answered, then reports once,
 *       with the names of the sections that failed. Sections that did load have already applied their fresh data;
 *       a failed section is expected to have kept whatever it showed (that is the section's job).</li>
 *   <li>Only one refresh runs at a time. A second pull/retry while the guest's own refresh is running is ignored.
 *       A silent background poll that is already in flight does not swallow the guest's pull either: the pull
 *       runs right after it, so the guest always gets a genuinely fresh fetch.</li>
 *   <li>{@link #poll()} is the same thing without the visible parts (no spinner, no failure message): it is what
 *       keeps the page current while it is on screen.</li>
 *   <li>{@link #cancel()} (the screen is gone) silences everything still in flight.</li>
 * </ul>
 * Main thread only - Retrofit delivers callbacks on the main looper, which is where the UI work happens anyway.
 */
public final class LandingRefreshCoordinator {

    /** One backend-loaded part of the page. {@link #refresh} must call {@code onDone} exactly once: true if it loaded. */
    public interface Section {
        void refresh(Consumer<Boolean> onDone);
    }

    public interface Listener {
        /** A refresh began. {@code userInitiated}: the guest pulled or tapped Retry (show a spinner); false: the silent poll. */
        void onRefreshStarted(boolean userInitiated);

        /** Every section has answered. {@code failed} names the sections that did not load (they kept their old content). */
        void onRefreshFinished(boolean userInitiated, List<String> failed);
    }

    private final Map<String, Section> sections = new LinkedHashMap<>();
    private final Listener listener;
    private boolean running;
    private boolean runningForGuest;
    private boolean guestRefreshQueued;
    private boolean cancelled;
    /** Identifies the current run so a late answer from an earlier (or cancelled) run is never counted twice. */
    private int runId;

    public LandingRefreshCoordinator(Map<String, Section> sections, Listener listener) {
        this.sections.putAll(sections);
        this.listener = listener;
    }

    /** True while a refresh (the guest's or the silent poll) is in flight. */
    public boolean isRefreshing() {
        return running;
    }

    /**
     * The guest asked for a refresh (pull-to-refresh or Retry). Returns false when it is ignored because the
     * guest's own refresh is already running; true when it started - or, if a silent poll is in flight, when it
     * was queued to start the moment that poll ends.
     */
    public boolean refresh() {
        if (cancelled) return false;
        if (running) {
            if (runningForGuest || guestRefreshQueued) return false;
            guestRefreshQueued = true;
            return true;
        }
        start(true);
        return true;
    }

    /** The silent, periodic refresh. Returns false (and does nothing) if any refresh is already running. */
    public boolean poll() {
        if (cancelled || running) return false;
        start(false);
        return true;
    }

    /** The screen is going away: nothing still in flight may touch it any more. */
    public void cancel() {
        cancelled = true;
        running = false;
        guestRefreshQueued = false;
        runId++;
    }

    private void start(boolean userInitiated) {
        running = true;
        runningForGuest = userInitiated;
        final int thisRun = ++runId;
        final List<String> failed = new ArrayList<>();
        final int[] pending = {sections.size()};
        listener.onRefreshStarted(userInitiated);
        if (sections.isEmpty()) {
            finish(thisRun, userInitiated, failed);
            return;
        }
        for (Map.Entry<String, Section> entry : sections.entrySet()) {
            final String name = entry.getKey();
            final boolean[] answered = {false};
            Consumer<Boolean> onDone = loaded -> {
                if (thisRun != runId || cancelled || answered[0]) return; // late, cancelled or duplicate answer
                answered[0] = true;
                if (!Boolean.TRUE.equals(loaded)) failed.add(name);
                if (--pending[0] == 0) finish(thisRun, userInitiated, failed);
            };
            try {
                entry.getValue().refresh(onDone);
            } catch (RuntimeException e) {
                onDone.accept(false); // a section that blows up simply counts as not loaded
            }
        }
    }

    private void finish(int thisRun, boolean userInitiated, List<String> failed) {
        if (thisRun != runId || cancelled) return;
        running = false;
        runningForGuest = false;
        listener.onRefreshFinished(userInitiated, new ArrayList<>(failed));
        if (guestRefreshQueued && !cancelled && !running) {
            guestRefreshQueued = false;
            start(true);
        }
    }
}
