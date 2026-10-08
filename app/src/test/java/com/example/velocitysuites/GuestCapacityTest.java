package com.example.velocitysuites;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GuestCapacityTest {

    @Test
    public void totalMayReachCapacityInAnyMix() {
        assertTrue(GuestCapacity.isValid(1, 5, 6));
        assertTrue(GuestCapacity.isValid(3, 3, 6));
        assertTrue(GuestCapacity.isValid(6, 0, 6));
        assertFalse(GuestCapacity.isValid(3, 4, 6));
    }

    @Test
    public void atLeastOneAdultIsRequired() {
        assertFalse(GuestCapacity.isValid(0, 2, 6));
    }

    @Test
    public void childrenAreNoLongerCappedAtThree() {
        assertTrue(GuestCapacity.isValid(2, 4, 6));
    }

    @Test
    public void canAddGuest_stopsExactlyAtCapacity() {
        assertTrue(GuestCapacity.canAddGuest(2, 3, 6));
        assertFalse(GuestCapacity.canAddGuest(3, 3, 6));
    }

    @Test
    public void unknownCapacityStillAllowsTheRequiredAdult() {
        assertEquals(1, GuestCapacity.effectiveMax(0));
        assertTrue(GuestCapacity.isValid(1, 0, 0));
        assertFalse(GuestCapacity.canAddGuest(1, 0, 0));
    }

    @Test
    public void clamp_lowersChildrenBeforeAdults() {
        assertArrayEquals(new int[]{2, 2}, GuestCapacity.clamp(2, 5, 4));
        assertArrayEquals(new int[]{4, 0}, GuestCapacity.clamp(6, 1, 4));
        assertArrayEquals(new int[]{1, 0}, GuestCapacity.clamp(0, 0, 4));
        assertArrayEquals(new int[]{2, 3}, GuestCapacity.clamp(2, 3, 6));
    }

    @Test
    public void state_clampGuestsWhenRoomsShrink() {
        BookingWizardState state = new BookingWizardState(BookingWizardState.Mode.BOOKING);
        state.adults = 3;
        state.children = 3;
        // no rooms selected -> capacity 0 -> effective max 1
        assertTrue(state.clampGuestsToCapacity());
        assertEquals(1, state.adults);
        assertEquals(0, state.children);
        assertFalse(state.clampGuestsToCapacity());
    }
}
