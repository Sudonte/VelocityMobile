package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * After the landing page refreshes its rooms, the guest's selection must still be bookable: a room that's gone
 * leaves the cart, and a quantity larger than what's left is trimmed to what's left.
 */
public class CartSelectionReconcilerTest {

    private static Room room(String id, int availableCount) {
        Room room = new Room(id, "Room " + id, "Deluxe", 2, 1800.0, "", 0, availableCount > 0,
                Collections.<RoomAmenity>emptyList(), "Queen", "24 sqm", "");
        room.setAvailableCount(availableCount);
        return room;
    }

    private static Map<String, Integer> cart(Object... pairs) {
        Map<String, Integer> cart = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) cart.put((String) pairs[i], (Integer) pairs[i + 1]);
        return cart;
    }

    @Test
    public void anUntouchedSelectionIsLeftAlone() {
        Map<String, Integer> cart = cart("1", 2, "2", 1);

        boolean changed = CartSelectionReconciler.reconcile(cart, Arrays.asList(room("1", 3), room("2", 1)));

        assertFalse(changed);
        assertEquals(cart("1", 2, "2", 1), cart);
    }

    @Test
    public void aQuantityLargerThanWhatIsLeft_isTrimmedToWhatIsLeft() {
        Map<String, Integer> cart = cart("1", 3);

        boolean changed = CartSelectionReconciler.reconcile(cart, Collections.singletonList(room("1", 2)));

        assertTrue(changed);
        assertEquals(Integer.valueOf(2), cart.get("1"));
    }

    @Test
    public void aRoomThatIsNoLongerListed_leavesTheCart() {
        Map<String, Integer> cart = cart("1", 2, "9", 1);

        boolean changed = CartSelectionReconciler.reconcile(cart, Collections.singletonList(room("1", 3)));

        assertTrue(changed);
        assertEquals("only the room that is still on offer remains", cart("1", 2), cart);
    }

    @Test
    public void aRoomWithNoneLeft_leavesTheCart() {
        Map<String, Integer> cart = cart("1", 1);

        boolean changed = CartSelectionReconciler.reconcile(cart, Collections.singletonList(room("1", 0)));

        assertTrue(changed);
        assertTrue(cart.isEmpty());
    }

    @Test
    public void anEmptyRoomList_emptiesTheCart() {
        Map<String, Integer> cart = cart("1", 2, "2", 1);

        boolean changed = CartSelectionReconciler.reconcile(cart, new ArrayList<Room>());

        assertTrue(changed);
        assertTrue(cart.isEmpty());
    }

    @Test
    public void anEmptyCart_isNeverChanged() {
        Map<String, Integer> cart = new LinkedHashMap<>();

        assertFalse(CartSelectionReconciler.reconcile(cart, Collections.singletonList(room("1", 3))));
        assertFalse(CartSelectionReconciler.reconcile(cart, null));
    }

    @Test
    public void aNullRoomList_isTreatedAsNothingOnOffer() {
        Map<String, Integer> cart = cart("1", 2);

        assertTrue(CartSelectionReconciler.reconcile(cart, null));
        assertTrue(cart.isEmpty());
    }

    @Test
    public void severalChangesAtOnce_areAllApplied() {
        Map<String, Integer> cart = cart("1", 3, "2", 1, "3", 2);

        boolean changed = CartSelectionReconciler.reconcile(cart, Arrays.asList(room("1", 1), room("3", 5)));

        assertTrue(changed);
        assertEquals("1 trimmed, 2 removed, 3 untouched", cart("1", 1, "3", 2), cart);
    }
}
