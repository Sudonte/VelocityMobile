package com.example.velocitysuites;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compares the rooms a guest has picked in the booking wizard with a FRESH read of what is free for their dates,
 * and trims the selection to what is really available. Plain code (no Android) so the rules are unit tested.
 * Nothing but the room selection is touched - dates, guest details, amenities and the rest stay exactly as entered.
 */
public final class RoomAvailabilityReconciler {

    private RoomAvailabilityReconciler() {
    }

    /** One room type whose picked quantity no longer fits. */
    public static final class Shortfall {
        public final Room room;
        /** How many of this type are free now (0 = gone). */
        public final int available;

        Shortfall(Room room, int available) {
            this.room = room;
            this.available = available;
        }

        public boolean isGone() {
            return available <= 0;
        }
    }

    /**
     * Removes from {@code selected} every room that is no longer available and reports what was removed. A room
     * type missing from {@code fresh} counts as gone. {@code selected} keeps one entry per room picked (duplicates
     * are the quantity), as in BookingWizardState.
     *
     * @return the room types that were trimmed, in selection order; empty if everything still fits
     */
    public static List<Shortfall> reconcile(List<Room> selected, List<Room> fresh) {
        Map<String, Integer> freeById = new LinkedHashMap<>();
        for (Room r : fresh) freeById.put(r.getId(), r.getAvailableCount());

        Map<String, List<Room>> grouped = new LinkedHashMap<>();
        for (Room r : selected) grouped.computeIfAbsent(r.getId(), k -> new ArrayList<>()).add(r);

        List<Shortfall> shortfalls = new ArrayList<>();
        for (Map.Entry<String, List<Room>> entry : grouped.entrySet()) {
            List<Room> group = entry.getValue();
            Integer free = freeById.get(entry.getKey());
            int available = free != null ? Math.max(0, free) : 0;
            if (available >= group.size()) continue;
            shortfalls.add(new Shortfall(group.get(0), available));
            for (int i = group.size() - 1; i >= available; i--) {
                selected.remove(group.get(i));
            }
        }
        return shortfalls;
    }
}
