package com.example.velocitysuites;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Keeps the landing page's room "cart" honest after the room list is refreshed from the server.
 * <p>
 * The cart is a room-id -> quantity map the guest builds with the steppers on the room cards. Since the page now
 * refreshes itself (pull-to-refresh, on return, every 30 seconds), the rooms can change underneath a selection:
 * a room sells out, is taken off the list, or has fewer left than the guest picked. Left alone, the "Book Selected"
 * bar would keep offering rooms that are gone. After each refresh this drops cart entries whose room is no longer
 * in the available list, and trims quantities down to the number still free - exactly the cap the card's own "+"
 * button already enforces ({@link Room#getAvailableCount()}).
 */
final class CartSelectionReconciler {

    private CartSelectionReconciler() {
    }

    /** @return true if the cart changed (so the caller should repaint its summary bar). */
    static boolean reconcile(Map<String, Integer> selectedQuantities, List<Room> availableRooms) {
        if (selectedQuantities.isEmpty()) return false;
        Map<String, Room> byId = new HashMap<>();
        if (availableRooms != null) {
            for (Room room : availableRooms) {
                if (room != null) byId.put(room.getId(), room);
            }
        }
        boolean changed = false;
        for (Iterator<Map.Entry<String, Integer>> it = selectedQuantities.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Integer> entry = it.next();
            Room room = byId.get(entry.getKey());
            int freeNow = room == null ? 0 : Math.max(0, room.getAvailableCount());
            Integer chosen = entry.getValue();
            if (freeNow == 0 || chosen == null || chosen <= 0) {
                it.remove();
                changed = true;
            } else if (chosen > freeNow) {
                entry.setValue(freeNow);
                changed = true;
            }
        }
        return changed;
    }
}
