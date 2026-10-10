package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RoomAvailabilityReconcilerTest {

    private static Room room(String id, int available) {
        Room r = new Room(id, "Room " + id, "Deluxe", 2, 2500.0, "", 0, true,
                Collections.<RoomAmenity>emptyList(), "Queen", "24 sqm", "");
        r.setAvailableCount(available);
        return r;
    }

    private static List<Room> picked(Room... rooms) {
        return new ArrayList<>(Arrays.asList(rooms));
    }

    @Test
    public void everythingStillFree_changesNothing() {
        List<Room> selected = picked(room("1", 0), room("1", 0), room("2", 0));
        List<RoomAvailabilityReconciler.Shortfall> lost =
                RoomAvailabilityReconciler.reconcile(selected, Arrays.asList(room("1", 2), room("2", 5)));
        assertTrue(lost.isEmpty());
        assertEquals(3, selected.size());
    }

    @Test
    public void aRoomThatIsGone_isRemoved_andReportedAsGone() {
        List<Room> selected = picked(room("1", 0), room("2", 0));
        List<RoomAvailabilityReconciler.Shortfall> lost =
                RoomAvailabilityReconciler.reconcile(selected, Arrays.asList(room("1", 0), room("2", 3)));
        assertEquals(1, lost.size());
        assertTrue(lost.get(0).isGone());
        assertEquals("1", lost.get(0).room.getId());
        assertEquals(1, selected.size());
        assertEquals("2", selected.get(0).getId());
    }

    @Test
    public void fewerFreeThanPicked_keepsWhatFits() {
        List<Room> selected = picked(room("1", 0), room("1", 0), room("1", 0));
        List<RoomAvailabilityReconciler.Shortfall> lost =
                RoomAvailabilityReconciler.reconcile(selected, Collections.singletonList(room("1", 2)));
        assertEquals(1, lost.size());
        assertFalse(lost.get(0).isGone());
        assertEquals(2, lost.get(0).available);
        assertEquals(2, selected.size());
    }

    @Test
    public void aTypeMissingFromTheFreshList_countsAsGone() {
        List<Room> selected = picked(room("9", 0));
        List<RoomAvailabilityReconciler.Shortfall> lost =
                RoomAvailabilityReconciler.reconcile(selected, Collections.singletonList(room("1", 4)));
        assertEquals(1, lost.size());
        assertTrue(lost.get(0).isGone());
        assertTrue(selected.isEmpty());
    }
}
