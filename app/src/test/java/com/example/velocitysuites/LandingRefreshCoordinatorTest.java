package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The landing page's refresh rules, on the JVM: success, failure, a second pull ignored while one is running,
 * the silent poll, and a screen that goes away mid-refresh. The sections are fakes the test answers by hand, so
 * "the server is slow" is simply "the test has not answered yet".
 */
public class LandingRefreshCoordinatorTest {

    /** A section whose answer the test controls. */
    private static final class FakeSection implements LandingRefreshCoordinator.Section {
        final List<Consumer<Boolean>> pending = new ArrayList<>();
        int started;
        boolean throwOnRefresh;

        @Override
        public void refresh(Consumer<Boolean> onDone) {
            started++;
            if (throwOnRefresh) throw new IllegalStateException("boom");
            pending.add(onDone);
        }

        void answer(boolean loaded) {
            pending.remove(0).accept(loaded);
        }

        void answerAgain(Consumer<Boolean> sameCallback, boolean loaded) {
            sameCallback.accept(loaded);
        }
    }

    private static final class RecordingListener implements LandingRefreshCoordinator.Listener {
        final List<String> events = new ArrayList<>();
        List<String> lastFailed = Collections.emptyList();

        @Override
        public void onRefreshStarted(boolean userInitiated) {
            events.add(userInitiated ? "start:guest" : "start:silent");
        }

        @Override
        public void onRefreshFinished(boolean userInitiated, List<String> failed) {
            events.add((userInitiated ? "finish:guest" : "finish:silent") + failed);
            lastFailed = failed;
        }
    }

    private FakeSection rooms;
    private FakeSection announcements;
    private FakeSection offers;
    private RecordingListener listener;
    private LandingRefreshCoordinator coordinator;

    @Before
    public void setUp() {
        rooms = new FakeSection();
        announcements = new FakeSection();
        offers = new FakeSection();
        listener = new RecordingListener();
        Map<String, LandingRefreshCoordinator.Section> sections = new LinkedHashMap<>();
        sections.put("rooms", rooms);
        sections.put("announcements", announcements);
        sections.put("offers", offers);
        coordinator = new LandingRefreshCoordinator(sections, listener);
    }

    private void answerAll(boolean roomsOk, boolean announcementsOk, boolean offersOk) {
        rooms.answer(roomsOk);
        announcements.answer(announcementsOk);
        offers.answer(offersOk);
    }

    // ---- success ----

    @Test
    public void refresh_asksEverySectionAgain_andReportsOnceWhenAllHaveAnswered() {
        assertTrue(coordinator.refresh());

        assertTrue(coordinator.isRefreshing());
        assertEquals(1, rooms.started);
        assertEquals(1, announcements.started);
        assertEquals(1, offers.started);
        assertEquals(Collections.singletonList("start:guest"), listener.events);

        rooms.answer(true);
        announcements.answer(true);
        assertTrue("one section is still out, so the refresh is not over", coordinator.isRefreshing());
        assertEquals("no early report", Collections.singletonList("start:guest"), listener.events);

        offers.answer(true);
        assertFalse(coordinator.isRefreshing());
        assertEquals(Arrays.asList("start:guest", "finish:guest[]"), listener.events);
    }

    @Test
    public void afterOneRefreshEnds_aNewOneStartsNormally() {
        coordinator.refresh();
        answerAll(true, true, true);

        assertTrue(coordinator.refresh());

        assertEquals(2, rooms.started);
        assertEquals(Arrays.asList("start:guest", "finish:guest[]", "start:guest"), listener.events);
    }

    // ---- failure ----

    @Test
    public void whenOneSectionFails_itIsNamed_andTheOthersStillCount() {
        coordinator.refresh();
        answerAll(true, false, true);

        assertFalse(coordinator.isRefreshing());
        assertEquals(Collections.singletonList("announcements"), listener.lastFailed);
        assertEquals("one report, not one per section", 2, listener.events.size());
    }

    @Test
    public void whenEverySectionFails_allAreNamedInPageOrder() {
        coordinator.refresh();
        offers.answer(false);
        rooms.answer(false);
        announcements.answer(false);

        assertEquals("named in the order the page lists them, not the order the server answered",
                Arrays.asList("rooms", "announcements", "offers"), sortedLikePage(listener.lastFailed));
        assertEquals(3, listener.lastFailed.size());
    }

    private static List<String> sortedLikePage(List<String> failed) {
        List<String> order = Arrays.asList("rooms", "announcements", "offers");
        List<String> sorted = new ArrayList<>(failed);
        Collections.sort(sorted, (a, b) -> order.indexOf(a) - order.indexOf(b));
        return sorted;
    }

    @Test
    public void afterAFailedRefresh_theGuestCanRetry_andItWorks() {
        coordinator.refresh();
        answerAll(false, true, true);
        assertEquals(Collections.singletonList("rooms"), listener.lastFailed);

        assertTrue("Retry is a fresh refresh, not blocked by the failed one", coordinator.refresh());
        assertEquals(2, rooms.started);
        answerAll(true, true, true);

        assertEquals(Collections.emptyList(), listener.lastFailed);
        assertFalse(coordinator.isRefreshing());
    }

    @Test
    public void aSectionThatThrows_countsAsNotLoaded_andDoesNotStallTheRefresh() {
        announcements.throwOnRefresh = true;

        coordinator.refresh();
        rooms.answer(true);
        offers.answer(true);

        assertFalse("the throwing section must not leave the spinner running forever", coordinator.isRefreshing());
        assertEquals(Collections.singletonList("announcements"), listener.lastFailed);
    }

    // ---- a second pull while one is running ----

    @Test
    public void aSecondGuestRefreshWhileOneIsRunning_isIgnored() {
        assertTrue(coordinator.refresh());

        assertFalse("ignored", coordinator.refresh());
        assertFalse("still ignored", coordinator.refresh());

        assertEquals("no section was asked a second time", 1, rooms.started);
        assertEquals("no second 'start' was announced", Collections.singletonList("start:guest"), listener.events);

        answerAll(true, true, true);
        assertEquals("and no extra refresh runs after it either", Arrays.asList("start:guest", "finish:guest[]"), listener.events);
        assertEquals(1, rooms.started);
    }

    @Test
    public void aDuplicateAnswerFromOneSection_isNotCountedTwice() {
        coordinator.refresh();
        Consumer<Boolean> roomsCallback = rooms.pending.get(0);

        roomsCallback.accept(true);
        roomsCallback.accept(true);
        roomsCallback.accept(false);
        assertTrue("two more answers from rooms must not stand in for announcements and offers", coordinator.isRefreshing());

        announcements.answer(true);
        assertTrue(coordinator.isRefreshing());
        offers.answer(true);

        assertFalse(coordinator.isRefreshing());
        assertEquals(Collections.emptyList(), listener.lastFailed);
    }

    // ---- the silent poll ----

    @Test
    public void poll_isSilentAndRefreshesEverything() {
        assertTrue(coordinator.poll());
        assertEquals(1, rooms.started);
        assertEquals(1, announcements.started);
        assertEquals(1, offers.started);

        answerAll(true, false, true);

        assertEquals("the listener can tell it is the silent one, so it shows no spinner and no failure message",
                Arrays.asList("start:silent", "finish:silent[announcements]"), listener.events);
    }

    @Test
    public void poll_doesNothingWhileARefreshIsAlreadyRunning() {
        coordinator.refresh();

        assertFalse(coordinator.poll());

        assertEquals("no duplicate fetch", 1, rooms.started);
        assertEquals(Collections.singletonList("start:guest"), listener.events);
    }

    @Test
    public void poll_doesNothingWhileAnotherPollIsRunning() {
        coordinator.poll();

        assertFalse(coordinator.poll());

        assertEquals(1, rooms.started);
    }

    @Test
    public void aGuestPullDuringASilentPoll_isNotLost_itRunsRightAfterThePoll() {
        coordinator.poll();

        assertTrue("accepted - the guest's pull must produce a genuinely fresh fetch", coordinator.refresh());
        assertFalse("a third request while that one is waiting is still ignored", coordinator.refresh());
        assertEquals("not started yet - the poll is still out", 1, rooms.started);

        answerAll(true, true, true);

        assertEquals("the poll finished and the guest's refresh started straight after it",
                Arrays.asList("start:silent", "finish:silent[]", "start:guest"), listener.events);
        assertEquals(2, rooms.started);
        assertTrue(coordinator.isRefreshing());

        answerAll(true, true, true);
        assertEquals(Arrays.asList("start:silent", "finish:silent[]", "start:guest", "finish:guest[]"), listener.events);
        assertFalse(coordinator.isRefreshing());
    }

    // ---- the screen goes away ----

    @Test
    public void cancel_silencesAnswersStillInFlight() {
        coordinator.refresh();
        rooms.answer(true);

        coordinator.cancel();
        announcements.answer(true);
        offers.answer(true);

        assertEquals("nothing reaches the (destroyed) screen after cancel", Collections.singletonList("start:guest"), listener.events);
        assertFalse(coordinator.isRefreshing());
    }

    @Test
    public void afterCancel_nothingStartsAgain() {
        coordinator.cancel();

        assertFalse(coordinator.refresh());
        assertFalse(coordinator.poll());

        assertEquals(0, rooms.started);
        assertTrue(listener.events.isEmpty());
    }

    @Test
    public void cancel_dropsAGuestRefreshThatWasWaitingBehindAPoll() {
        coordinator.poll();
        coordinator.refresh();

        coordinator.cancel();
        answerAll(true, true, true);

        assertEquals("the queued refresh must not start on a dead screen", Collections.singletonList("start:silent"), listener.events);
        assertEquals(1, rooms.started);
    }

    // ---- edge cases ----

    @Test
    public void aPageWithNoSections_finishesImmediately() {
        RecordingListener emptyListener = new RecordingListener();
        LandingRefreshCoordinator empty = new LandingRefreshCoordinator(new LinkedHashMap<>(), emptyListener);

        assertTrue(empty.refresh());

        assertFalse("with nothing to wait for the spinner must not hang", empty.isRefreshing());
        assertEquals(Arrays.asList("start:guest", "finish:guest[]"), emptyListener.events);
    }

    @Test
    public void aSectionThatAnswersSynchronously_isHandled() {
        List<String> events = new ArrayList<>();
        Map<String, LandingRefreshCoordinator.Section> sections = new LinkedHashMap<>();
        sections.put("a", done -> done.accept(true));   // e.g. a section with nothing to load answers on the spot
        sections.put("b", done -> done.accept(false));
        LandingRefreshCoordinator sync = new LandingRefreshCoordinator(sections, new LandingRefreshCoordinator.Listener() {
            @Override
            public void onRefreshStarted(boolean userInitiated) {
                events.add("start");
            }

            @Override
            public void onRefreshFinished(boolean userInitiated, List<String> failed) {
                events.add("finish" + failed);
            }
        });

        assertTrue(sync.refresh());

        assertEquals(Arrays.asList("start", "finish[b]"), events);
        assertFalse(sync.isRefreshing());
        assertTrue("and a later refresh still works", sync.refresh());
    }
}
