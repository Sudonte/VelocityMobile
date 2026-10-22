package com.example.velocitysuites;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

/**
 * Pure-JVM coverage for BookingWizardState#totalSelectedCapacity() - the
 * foundation Step5AdditionalGuestsFragment's combined adults+children guard
 * (Requirement 3) relies on. Room is a plain Serializable POJO with no
 * Android dependency, so this needs no Robolectric/Mockito.
 */
public class BookingWizardStateTest {

    private BookingWizardState state;

    @Before
    public void setUp() {
        state = new BookingWizardState(BookingWizardState.Mode.BOOKING);
    }

    private static Room roomOfCapacity(String id, int capacity) {
        return new Room(id, "Room " + id, "Deluxe", capacity, 2500.0, "desc",
                0, true, Collections.<RoomAmenity>emptyList(), "Queen", "24 sqm", "No smoking");
    }

    @Test
    public void noRoomsSelected_capacityIsZero() {
        assertEquals(0, state.totalSelectedCapacity());
    }

    @Test
    public void singleRoom_capacityMatchesRoom() {
        state.selectedRooms.add(roomOfCapacity("R1", 2));
        assertEquals(2, state.totalSelectedCapacity());
    }

    @Test
    public void sameRoomTypeTwice_quantityRepresentedAsDuplicateEntries_summed() {
        // Per BookingWizardState's own doc: duplicates in the flat list ARE
        // the per-type quantity - "Deluxe x2" is two separate Room entries.
        state.selectedRooms.add(roomOfCapacity("R1", 2));
        state.selectedRooms.add(roomOfCapacity("R1", 2));
        assertEquals(4, state.totalSelectedCapacity());
    }

    @Test
    public void multipleDistinctRoomTypes_summedAcrossAllTypes() {
        state.selectedRooms.add(roomOfCapacity("R1", 2));
        state.selectedRooms.add(roomOfCapacity("R2", 4));
        state.selectedRooms.add(roomOfCapacity("R2", 4));
        assertEquals(10, state.totalSelectedCapacity());
    }

    // ---- several stays in one transaction

    private static java.util.Calendar day(int offset) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(2026, java.util.Calendar.NOVEMBER, 1 + offset, 0, 0, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c;
    }

    private void pick(int in, int out, Room room, int qty) {
        state.checkIn = day(in);
        state.checkOut = day(out);
        state.selectedRooms.clear();
        for (int i = 0; i < qty; i++) state.selectedRooms.add(room);
    }

    @Test
    public void oneStay_isNotAGroup_andTotalsAreAsBefore() {
        pick(0, 2, roomOfCapacity("R1", 2), 2); // 2,500 x 2 nights x 2
        assertEquals(1, state.stayCount());
        assertEquals(false, state.isGroup());
        assertEquals(10000.0, state.roomsTotal(), 0.001);
        assertEquals(10000.0, state.groupTotal(), 0.001);
    }

    @Test
    public void addingAStay_keepsTheFirstOne_andTheGroupTotalSumsEveryStay() {
        pick(0, 2, roomOfCapacity("R1", 2), 2);           // 10,000
        assertEquals(true, state.addStay());
        assertEquals(2, state.stayCount());
        assertEquals("the new stay starts empty", 0, state.selectedRooms.size());
        assertEquals(null, state.checkIn);
        pick(10, 13, roomOfCapacity("R2", 4), 1);        // 2,500 x 3 nights x 1 = 7,500

        assertEquals(17500.0, state.groupTotal(), 0.001);
        assertEquals(17500.0, state.roomsTotal(), 0.001);
        assertEquals(2, state.allStays().size());
        assertEquals(2L, state.allStays().get(0).nights());
        assertEquals(3L, state.allStays().get(1).nights());
        assertEquals("guests must fit the smallest stay: 1 room of 4 vs 2 rooms of 2", 4, state.totalSelectedCapacity());
    }

    @Test
    public void editingAnEarlierStay_andRemovingOne() {
        pick(0, 2, roomOfCapacity("R1", 2), 1);
        state.addStay();
        pick(10, 11, roomOfCapacity("R2", 2), 1);

        state.activate(0);
        assertEquals(0, state.activeStayIndex());
        assertEquals(2L, state.nights());
        state.selectedRooms.add(roomOfCapacity("R1", 2)); // 2 rooms now
        state.activate(1);
        state.activate(0);
        assertEquals(2, state.selectedRooms.size());

        state.removeStay(1);
        assertEquals(1, state.stayCount());
        assertEquals(false, state.isGroup());
        state.removeStay(0);
        assertEquals("the last stay can never be removed", 1, state.stayCount());
    }

    @Test
    public void thereIsAMaximumNumberOfStays() {
        pick(0, 1, roomOfCapacity("R1", 2), 1);
        for (int i = 1; i < BookingWizardState.MAX_STAYS; i++) assertEquals(true, state.addStay());
        assertEquals(false, state.addStay());
        assertEquals(BookingWizardState.MAX_STAYS, state.stayCount());
    }
}
