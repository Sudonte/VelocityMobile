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
}
