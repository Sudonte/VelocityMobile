package com.example.velocitysuites;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Your rooms": every room of a transaction and the APPROVED amenities, grouped per item with the quantities added up. Pure data
 * (the one shared model behind {@link TransactionRoomsView}, so the Next Stay details, Booking Details, Transaction History and
 * the Upcoming sheet cannot drift).
 *
 * <p>Only approved amenities are listed and counted. Requests that are still waiting are listed apart ({@link #waiting}) and never
 * counted. The server's stay bill does not say which room an approved amenity belongs to, so those lines go under one "All rooms"
 * group; a source that does carry a room number is grouped under that room.
 */
final class TransactionRooms {

    /** One room: its number once assigned at check-in, else only its type. */
    static final class RoomItem {
        @Nullable final String number;
        final String type;

        RoomItem(@Nullable String number, String type) {
            this.number = number == null || number.trim().isEmpty() ? null : number.trim();
            this.type = type == null ? "" : type.trim();
        }
    }

    /** One amenity (or several requests of it added up). */
    static final class AmenityItem {
        final String name;
        final int quantity;
        final double subtotal;

        AmenityItem(String name, int quantity, double subtotal) {
            this.name = name;
            this.quantity = quantity;
            this.subtotal = subtotal;
        }
    }

    /** The amenities of one room - or of "all rooms" when the source does not link them. */
    static final class Group {
        /** The room number, or null for the "All rooms" group. */
        @Nullable final String roomNumber;
        final List<AmenityItem> items;
        final double subtotal;

        Group(@Nullable String roomNumber, List<AmenityItem> items) {
            this.roomNumber = roomNumber;
            this.items = items;
            double sum = 0;
            for (AmenityItem item : items) sum += item.subtotal;
            this.subtotal = sum;
        }
    }

    /** An amenity line as a source gives it; {@code roomNumber} is null when the source does not link it to a room. */
    static final class Line {
        final String name;
        final int quantity;
        final double subtotal;
        @Nullable final String roomNumber;

        Line(String name, int quantity, double subtotal, @Nullable String roomNumber) {
            this.name = name == null ? "" : name.trim();
            this.quantity = Math.max(1, quantity);
            this.subtotal = subtotal;
            this.roomNumber = roomNumber == null || roomNumber.trim().isEmpty() ? null : roomNumber.trim();
        }
    }

    final List<RoomItem> rooms;
    /** Groups that have at least one amenity (per linked room, and "All rooms" for the rest). */
    final List<Group> groups;
    /** Requests still waiting for the front desk (never counted anywhere). */
    final List<AmenityItem> waiting;
    final double amenitiesTotal;

    TransactionRooms(List<RoomItem> rooms, List<Line> approved, List<AmenityItem> waiting) {
        this.rooms = rooms;
        this.waiting = waiting;
        Map<String, List<Line>> byRoom = new LinkedHashMap<>();
        List<Line> unlinked = new ArrayList<>();
        for (Line line : approved) {
            if (line.name.isEmpty()) continue;
            if (line.roomNumber == null) unlinked.add(line);
            else byRoom.computeIfAbsent(line.roomNumber, k -> new ArrayList<>()).add(line);
        }
        List<Group> built = new ArrayList<>();
        double total = 0;
        for (Map.Entry<String, List<Line>> e : byRoom.entrySet()) {
            Group g = new Group(e.getKey(), grouped(e.getValue()));
            built.add(g);
            total += g.subtotal;
        }
        if (!unlinked.isEmpty()) {
            Group g = new Group(null, grouped(unlinked));
            built.add(g);
            total += g.subtotal;
        }
        this.groups = built;
        this.amenitiesTotal = total;
    }

    /** One item per amenity name (case-insensitive), quantities and amounts added up. */
    static List<AmenityItem> grouped(List<Line> lines) {
        Map<String, double[]> sums = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (Line l : lines) {
            String key = l.name.toLowerCase(Locale.ROOT);
            double[] sum = sums.get(key);
            if (sum == null) {
                sum = new double[2];
                sums.put(key, sum);
                names.put(key, l.name);
            }
            sum[0] += l.quantity;
            sum[1] += l.subtotal;
        }
        List<AmenityItem> out = new ArrayList<>();
        for (Map.Entry<String, double[]> e : sums.entrySet()) out.add(new AmenityItem(names.get(e.getKey()), (int) Math.round(e.getValue()[0]), e.getValue()[1]));
        return out;
    }

    /** True when the amenities are listed per linked room rather than under "All rooms". */
    boolean linkedToRooms() {
        for (Group g : groups) if (g.roomNumber != null) return true;
        return false;
    }

    @Nullable
    Group groupFor(@Nullable String roomNumber) {
        if (roomNumber == null) return null;
        for (Group g : groups) if (roomNumber.equals(g.roomNumber)) return g;
        return null;
    }

    @Nullable
    Group allRoomsGroup() {
        for (Group g : groups) if (g.roomNumber == null) return g;
        return null;
    }

    // ---- building it from a transaction

    @NonNull
    static TransactionRooms of(@NonNull Booking b) {
        List<RoomItem> rooms = new ArrayList<>();
        List<Line> approved = new ArrayList<>();
        List<AmenityItem> waiting = new ArrayList<>();
        StayInfo stay = b.getStay();
        if (stay != null && !stay.rooms.isEmpty()) {
            for (StayInfo.RoomLine r : stay.rooms) rooms.add(new RoomItem(r.roomNumber, r.roomType));
        } else if (!b.getRooms().isEmpty()) {
            for (BookingRoom r : b.getRooms()) {
                List<String> assigned = r.getAssignedRoomNumbers();
                int count = Math.max(1, r.getQuantity());
                for (int i = 0; i < count; i++) {
                    rooms.add(new RoomItem(assigned != null && i < assigned.size() ? assigned.get(i) : null, r.getRoomTypeName()));
                }
            }
        } else {
            String type = b.getRoomType() != null && !b.getRoomType().trim().isEmpty() ? b.getRoomType() : b.getRoomName();
            int count = Math.max(1, b.getRoomsRequested());
            for (int i = 0; i < count; i++) rooms.add(new RoomItem(i == 0 ? b.getRoomNumber() : null, type));
        }
        if (stay != null) {
            for (StayInfo.AmenityLine a : stay.approvedAmenities) approved.add(new Line(a.name, a.quantity, a.subtotal, null));
            List<Line> pending = new ArrayList<>();
            for (StayInfo.RequestedAmenity r : stay.requestedAmenities) {
                if (!r.rejected) pending.add(new Line(r.name, r.quantity, r.subtotal, null));
            }
            waiting.addAll(grouped(pending));
        } else {
            for (BookingAmenity a : b.getAmenities()) approved.add(new Line(a.getAmenityName(), a.getQuantity(), a.getSubtotal(), null));
        }
        return new TransactionRooms(rooms, approved, waiting);
    }
}
