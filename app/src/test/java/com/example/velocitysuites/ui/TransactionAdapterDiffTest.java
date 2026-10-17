package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.Booking;
import com.example.velocitysuites.TransactionAdapter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** TransactionAdapter reports row-level changes (insert / remove / change) instead of "everything changed". */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, application = TestApplication.class)
public class TransactionAdapterDiffTest {

    private static Booking booking(String id, String status) {
        Booking b = new Booking(id, "1", "Deluxe", "Deluxe", "Oct 01, 2026", "Oct 03, 2026", 2, 5000.0, status, "Sep 30, 2026");
        b.setHasBooking(true);
        return b;
    }

    private static final class Spy extends RecyclerView.AdapterDataObserver {
        final List<String> events = new ArrayList<>();
        @Override public void onChanged() { events.add("CHANGED-ALL"); }
        @Override public void onItemRangeChanged(int s, int c) { events.add("change@" + s + "x" + c); }
        @Override public void onItemRangeChanged(int s, int c, Object p) { events.add("change@" + s + "x" + c); }
        @Override public void onItemRangeInserted(int s, int c) { events.add("insert@" + s + "x" + c); }
        @Override public void onItemRangeRemoved(int s, int c) { events.add("remove@" + s + "x" + c); }
    }

    @Test
    public void updateList_reportsOnlyWhatChanged() {
        TransactionAdapter adapter = new TransactionAdapter(new ArrayList<>(Arrays.asList(booking("1", "Pending"), booking("2", "Pending"))), b -> { });
        Spy spy = new Spy();
        adapter.registerAdapterDataObserver(spy);

        adapter.updateList(Arrays.asList(booking("1", "Pending"), booking("2", "Confirmed"), booking("3", "Pending")));
        assertEquals(new java.util.HashSet<>(Arrays.asList("change@1x1", "insert@2x1")), new java.util.HashSet<>(spy.events));
        assertEquals(3, adapter.getItemCount());

        spy.events.clear();
        adapter.updateList(Arrays.asList(booking("1", "Pending"), booking("2", "Confirmed"), booking("3", "Pending")));
        assertEquals("identical data: no rebinding at all", new ArrayList<String>(), spy.events);
    }

    @Test
    public void highlightingARow_changesOnlyThatRow() {
        TransactionAdapter adapter = new TransactionAdapter(new ArrayList<>(Arrays.asList(booking("1", "Pending"), booking("2", "Pending"), booking("3", "Pending"))), b -> { });
        Spy spy = new Spy();
        adapter.registerAdapterDataObserver(spy);
        adapter.setHighlightedBookingId("2");
        assertEquals(Arrays.asList("change@1x1"), spy.events);
        spy.events.clear();
        adapter.setHighlightedBookingId("3");
        assertEquals(Arrays.asList("change@1x1", "change@2x1"), spy.events);
    }
}
