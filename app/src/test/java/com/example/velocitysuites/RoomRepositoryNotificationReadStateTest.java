package com.example.velocitysuites;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The read/unread bookkeeping behind per-notification "Mark as read"/"Mark as
 * unread": every local change must move the unread total by exactly one - and only
 * on a REAL transition - so the header badge and filter counts never lag a change the
 * guest just made; and a poll/refresh that lands while a change is still in flight must
 * not flip the row (or the count) back. Only the pure cache logic is covered here - the
 * PUT round-trip itself needs a live backend (see RoomRepository#setNotificationReadState()).
 */
public class RoomRepositoryNotificationReadStateTest {

    private RoomRepository repository;

    @Before
    public void setUp() {
        repository = RoomRepository.getInstance(null);
        repository.setNotificationsForTesting(Collections.<Notification>emptyList(), 0);
    }

    private static Notification notification(String id, boolean isRead) {
        return new Notification(id, "Title " + id, "Message " + id, "just now", Notification.TYPE_BOOKING, isRead);
    }

    private static boolean isRead(List<Notification> list, String id) {
        for (Notification n : list) {
            if (n.getId().equals(id)) return n.isRead();
        }
        throw new AssertionError("no notification with id " + id);
    }

    // ---- applyReadStateLocally ----

    @Test
    public void markRead_flipsTheRow_andDecrementsTheUnreadTotalByOne() {
        repository.setNotificationsForTesting(Arrays.asList(notification("1", false), notification("2", false)), 2);

        assertTrue(repository.applyReadStateLocally("1", true));

        assertTrue(isRead(repository.getNotifications(), "1"));
        assertFalse(isRead(repository.getNotifications(), "2"));
        assertEquals(1, repository.getUnreadNotificationCount());
    }

    @Test
    public void markUnread_flipsTheRow_andIncrementsTheUnreadTotalByOne() {
        repository.setNotificationsForTesting(Arrays.asList(notification("1", true), notification("2", false)), 1);

        assertTrue(repository.applyReadStateLocally("1", false));

        assertFalse(isRead(repository.getNotifications(), "1"));
        assertEquals(2, repository.getUnreadNotificationCount());
    }

    @Test
    public void applyingTheStateAlreadyShown_isANoOp_andNeverMovesTheCount() {
        repository.setNotificationsForTesting(Arrays.asList(notification("1", true), notification("2", false)), 1);

        assertFalse(repository.applyReadStateLocally("1", true));
        assertFalse(repository.applyReadStateLocally("2", false));

        assertEquals(1, repository.getUnreadNotificationCount());
        assertEquals(1, repository.getBackendUnreadCount());
    }

    @Test
    public void unknownOrNullId_changesNothing() {
        repository.setNotificationsForTesting(Collections.singletonList(notification("1", false)), 1);

        assertFalse(repository.applyReadStateLocally("999", true));
        assertFalse(repository.applyReadStateLocally(null, true));

        assertFalse(isRead(repository.getNotifications(), "1"));
        assertEquals(1, repository.getUnreadNotificationCount());
    }

    @Test
    public void theBackendTotalNeverGoesNegative_evenIfItWasStaleAndLowerThanWhatIsVisible() {
        // Backend total 0 (cache lag) while a row is visibly unread - marking it read must clamp at 0, not -1.
        repository.setNotificationsForTesting(Collections.singletonList(notification("1", false)), 0);

        assertTrue(repository.applyReadStateLocally("1", true));

        assertEquals(0, repository.getBackendUnreadCount());
        assertEquals(0, repository.getUnreadNotificationCount());
    }

    @Test
    public void toggleThereAndBack_returnsToTheOriginalCount() {
        repository.setNotificationsForTesting(Arrays.asList(notification("1", false), notification("2", false)), 2);

        repository.applyReadStateLocally("1", true);
        repository.applyReadStateLocally("1", false);

        assertEquals(2, repository.getUnreadNotificationCount());
        assertFalse(isRead(repository.getNotifications(), "1"));
    }

    // ---- getUnreadNotificationCount ----

    @Test
    public void unreadCount_usesTheBackendTotalWhenGuestHasMoreUnreadThanIsLoaded() {
        // 12 unread on the server, only 3 in the loaded window.
        repository.setNotificationsForTesting(
                Arrays.asList(notification("1", false), notification("2", false), notification("3", false), notification("4", true)), 12);

        assertEquals(12, repository.getUnreadNotificationCount());
    }

    @Test
    public void unreadCount_neverUndercountsWhatIsVisiblyUnread_whenTheBackendCacheLags() {
        // The backend caches its total for ~20s, so a just-polled new unread row can outrun it.
        repository.setNotificationsForTesting(
                Arrays.asList(notification("1", false), notification("2", false), notification("3", false)), 1);

        assertEquals(3, repository.getUnreadNotificationCount());
    }

    @Test
    public void unreadCount_isZeroWhenEverythingIsRead() {
        repository.setNotificationsForTesting(Arrays.asList(notification("1", true), notification("2", true)), 0);

        assertEquals(0, repository.getUnreadNotificationCount());
    }

    // ---- reconcilePendingReadStates ----

    @Test
    public void reconcile_withNothingInFlight_returnsTheServerCountUntouched() {
        List<Notification> fresh = new ArrayList<>(Arrays.asList(notification("1", false), notification("2", true)));

        assertEquals(7, repository.reconcilePendingReadStates(fresh, 7));

        assertFalse(isRead(fresh, "1"));
        assertTrue(isRead(fresh, "2"));
    }

    @Test
    public void reconcile_pendingMarkRead_whileServerStillSaysUnread_forcesReadAndCorrectsTheCount() {
        // The poll was answered BEFORE the server applied our PUT: row 1 still unread, total still includes it.
        repository.putPendingReadStateForTesting("1", true);
        List<Notification> fresh = new ArrayList<>(Arrays.asList(notification("1", false), notification("2", false)));

        int count = repository.reconcilePendingReadStates(fresh, 2);

        assertTrue("the in-flight change must not be flipped back by the stale response", isRead(fresh, "1"));
        assertFalse(isRead(fresh, "2"));
        assertEquals(1, count);
    }

    @Test
    public void reconcile_pendingMarkRead_whenServerAlreadyAppliedIt_changesNothing() {
        repository.putPendingReadStateForTesting("1", true);
        List<Notification> fresh = new ArrayList<>(Arrays.asList(notification("1", true), notification("2", false)));

        int count = repository.reconcilePendingReadStates(fresh, 1);

        assertTrue(isRead(fresh, "1"));
        assertEquals("the server's total already excludes row 1 - must not be decremented twice", 1, count);
    }

    @Test
    public void reconcile_pendingMarkUnread_whileServerStillSaysRead_forcesUnreadAndCorrectsTheCount() {
        repository.putPendingReadStateForTesting("1", false);
        List<Notification> fresh = new ArrayList<>(Arrays.asList(notification("1", true), notification("2", false)));

        int count = repository.reconcilePendingReadStates(fresh, 1);

        assertFalse(isRead(fresh, "1"));
        assertEquals(2, count);
    }

    @Test
    public void reconcile_aPendingRowOutsideTheFetchedPage_isIgnored() {
        repository.putPendingReadStateForTesting("999", true);
        List<Notification> fresh = new ArrayList<>(Arrays.asList(notification("1", false)));

        assertEquals(5, repository.reconcilePendingReadStates(fresh, 5));
        assertFalse(isRead(fresh, "1"));
    }

    @Test
    public void reconcile_neverReturnsANegativeCount() {
        repository.putPendingReadStateForTesting("1", true);
        repository.putPendingReadStateForTesting("2", true);
        List<Notification> fresh = new ArrayList<>(Arrays.asList(notification("1", false), notification("2", false)));

        // A stale/low server total (0) minus two forced-read rows must clamp at zero, not go to -2.
        assertEquals(0, repository.reconcilePendingReadStates(fresh, 0));
    }
}
