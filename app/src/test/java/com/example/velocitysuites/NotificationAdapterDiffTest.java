package com.example.velocitysuites;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListUpdateCallback;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression coverage for "I confirmed Mark as read, but the card still looks unread".
 * <p>
 * RoomRepository flips {@code isRead} IN PLACE on the same Notification instances the
 * adapter's previous Row objects point at. If the adapter's diff compared
 * {@code old.notification.isRead()} with {@code new.notification.isRead()} it would compare an
 * object with ITSELF, always see "unchanged", dispatch nothing, and never rebind the row - the
 * button label, the unread dot, the bold title and the tinted background would all stay stale.
 * NotificationAdapter.Row therefore snapshots the read state when it is built, and these tests
 * pin that the real DiffUtil - not a re-implementation of it - reports the change.
 * (NotificationAdapter itself needs a Context, so only its Row/diff pieces are exercised here.)
 */
public class NotificationAdapterDiffTest {

    /** Records the exact operations DiffUtil dispatches, in a form that is easy to assert on. */
    private static final class Recorder implements ListUpdateCallback {
        final List<String> ops = new ArrayList<>();

        @Override public void onInserted(int position, int count) { ops.add("insert@" + position + "x" + count); }
        @Override public void onRemoved(int position, int count) { ops.add("remove@" + position + "x" + count); }
        @Override public void onMoved(int fromPosition, int toPosition) { ops.add("move@" + fromPosition + "->" + toPosition); }
        @Override public void onChanged(int position, int count, Object payload) { ops.add("change@" + position + "x" + count); }
    }

    private static Notification notification(String id, boolean read) {
        return new Notification(id, "Title " + id, "Message " + id, "just now", Notification.TYPE_BOOKING, read);
    }

    /** Rows exactly as NotificationAdapter#buildRows() makes them, minus the Context-dependent date-group label. */
    private static List<NotificationAdapter.Row> rowsOf(Notification... notifications) {
        List<NotificationAdapter.Row> rows = new ArrayList<>();
        for (int i = 0; i < notifications.length; i++) {
            rows.add(new NotificationAdapter.Row(notifications[i], notifications[i].isRead(), i == 0, "Today"));
        }
        return rows;
    }

    private static List<String> diff(List<NotificationAdapter.Row> before, List<NotificationAdapter.Row> after) {
        Recorder recorder = new Recorder();
        DiffUtil.calculateDiff(new NotificationAdapter.NotificationDiffCallback(before, after)).dispatchUpdatesTo(recorder);
        return recorder.ops;
    }

    @Test
    public void markingReadInPlace_isDetected_soTheRowGetsRebound() {
        Notification n = notification("1", false);
        List<NotificationAdapter.Row> before = rowsOf(n);

        n.setRead(true); // exactly what RoomRepository#applyReadStateLocally() does to the shared instance
        List<NotificationAdapter.Row> after = rowsOf(n);

        assertEquals(Collections.singletonList("change@0x1"), diff(before, after));
    }

    @Test
    public void markingUnreadInPlace_isDetected_soTheRowGetsRebound() {
        Notification n = notification("1", true);
        List<NotificationAdapter.Row> before = rowsOf(n);

        n.setRead(false);
        List<NotificationAdapter.Row> after = rowsOf(n);

        assertEquals(Collections.singletonList("change@0x1"), diff(before, after));
    }

    @Test
    public void aRowSnapshotKeepsTheStateItWasBuiltWith_evenAfterTheSharedNotificationIsFlipped() {
        Notification n = notification("1", false);
        NotificationAdapter.Row row = rowsOf(n).get(0);

        n.setRead(true);

        assertFalse("the snapshot must not follow the live object", row.isRead);
        assertTrue(row.notification.isRead());
    }

    @Test
    public void onlyTheRowThatChanged_isReported_notItsNeighbours() {
        Notification a = notification("1", false);
        Notification b = notification("2", false);
        Notification c = notification("3", true);
        List<NotificationAdapter.Row> before = rowsOf(a, b, c);

        b.setRead(true);
        List<NotificationAdapter.Row> after = rowsOf(a, b, c);

        assertEquals(Collections.singletonList("change@1x1"), diff(before, after));
    }

    @Test
    public void nothingChanged_dispatchesNothing() {
        Notification a = notification("1", false);
        Notification b = notification("2", true);

        assertTrue(diff(rowsOf(a, b), rowsOf(a, b)).isEmpty());
    }

    @Test
    public void aBrandNewNotificationAtTheFront_isAnInsert_andLeavesTheExistingRowAlone() {
        Notification existing = notification("1", true);
        Notification fresh = notification("2", false);

        List<String> ops = diff(rowsOf(existing), rowsOf(fresh, existing));

        assertTrue("expected an insert at the front, got " + ops, ops.contains("insert@0x1"));
        assertFalse("the existing read row must not be rebound: " + ops, ops.contains("change@1x1"));
    }

    @Test
    public void aRowSwitchingFromReadToUnreadUnderAnotherFilter_isRemovedNotRebound() {
        // Unread filter: marking the only shown row read makes it disappear from the filtered list.
        Notification n = notification("1", false);
        List<NotificationAdapter.Row> before = rowsOf(n);

        n.setRead(true);

        assertEquals(Collections.singletonList("remove@0x1"), diff(before, Collections.<NotificationAdapter.Row>emptyList()));
    }

    /** Every list position covered by a "change@PxC" op - DiffUtil batches adjacent changes into one range, so asserting on covered positions is robust to that batching. */
    private static java.util.Set<Integer> changedPositions(List<String> ops) {
        java.util.Set<Integer> positions = new java.util.TreeSet<>();
        for (String op : ops) {
            if (!op.startsWith("change@")) continue;
            String[] parts = op.substring("change@".length()).split("x");
            int start = Integer.parseInt(parts[0]);
            int count = Integer.parseInt(parts[1]);
            for (int i = start; i < start + count; i++) positions.add(i);
        }
        return positions;
    }

    @Test
    public void severalChangesAtOnce_areAllReported_andTheUntouchedRowIsNot() {
        Notification a = notification("1", false);
        Notification b = notification("2", true);
        Notification c = notification("3", false);
        List<NotificationAdapter.Row> before = rowsOf(a, b, c);

        a.setRead(true);
        b.setRead(false);
        List<NotificationAdapter.Row> after = rowsOf(a, b, c);

        assertEquals(new java.util.TreeSet<>(Arrays.asList(0, 1)), changedPositions(diff(before, after)));
    }
}
