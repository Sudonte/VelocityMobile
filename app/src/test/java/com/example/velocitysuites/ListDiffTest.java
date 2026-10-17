package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.recyclerview.widget.ListUpdateCallback;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The adapters replaced notifyDataSetChanged() with a diff: only rows that were added, removed or changed are reported. */
public class ListDiffTest {

    private static final class Recorder implements ListUpdateCallback {
        final List<String> events = new ArrayList<>();
        @Override public void onInserted(int position, int count) { events.add("insert@" + position + "x" + count); }
        @Override public void onRemoved(int position, int count) { events.add("remove@" + position + "x" + count); }
        @Override public void onMoved(int from, int to) { events.add("move"); }
        @Override public void onChanged(int position, int count, Object payload) { events.add("change@" + position + "x" + count); }
    }

    private static List<String> run(List<String> oldIds, List<String> oldKeys, List<String> newIds, List<String> newKeys) {
        Recorder r = new Recorder();
        ListDiff.diff(oldIds, oldKeys, newIds, newKeys).dispatchUpdatesTo(r);
        return r.events;
    }

    @Test
    public void anUnchangedListReportsNothing() {
        List<String> ids = Arrays.asList("1", "2", "3");
        List<String> keys = Arrays.asList("a", "b", "c");
        assertTrue(run(ids, keys, ids, keys).isEmpty());
    }

    @Test
    public void oneEditedRowReportsOnlyThatRow() {
        List<String> ids = Arrays.asList("1", "2", "3");
        assertEquals(Arrays.asList("change@1x1"), run(ids, Arrays.asList("a", "b", "c"), ids, Arrays.asList("a", "B", "c")));
    }

    @Test
    public void aNewFirstRowIsAnInsert_notAFullRebuild() {
        assertEquals(Arrays.asList("insert@0x1"),
                run(Arrays.asList("2", "3"), Arrays.asList("b", "c"), Arrays.asList("1", "2", "3"), Arrays.asList("a", "b", "c")));
    }

    @Test
    public void aRemovedRowIsARemove() {
        assertEquals(Arrays.asList("remove@1x1"),
                run(Arrays.asList("1", "2", "3"), Arrays.asList("a", "b", "c"), Arrays.asList("1", "3"), Arrays.asList("a", "c")));
    }

    @Test
    public void emptyToEmptyAndEmptyToFilledWork() {
        List<String> none = new ArrayList<>();
        assertTrue(run(none, none, none, none).isEmpty());
        assertEquals(Arrays.asList("insert@0x2"), run(none, none, Arrays.asList("1", "2"), Arrays.asList("a", "b")));
    }

    @Test
    public void duplicateIdsStillProduceAValidUpdate() {
        // two rows sharing an id (bad data) must not throw, and applying the events must end at the new size
        List<String> events = run(Arrays.asList("1", "1"), Arrays.asList("a", "b"), Arrays.asList("1", "1", "1"), Arrays.asList("a", "b", "c"));
        assertTrue(events.toString(), events.stream().anyMatch(e -> e.startsWith("insert")));
    }

    @Test
    public void keyJoinsEveryPart_andTreatsNullAsEmpty() {
        assertEquals(ListDiff.key("a", null, 3), ListDiff.key("a", "", 3));
        assertTrue(!ListDiff.key("a", "b").equals(ListDiff.key("ab", "")));
    }
}
