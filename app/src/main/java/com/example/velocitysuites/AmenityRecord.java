package com.example.velocitysuites;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * One amenity request line as ANY source reports it (the amenity-requests API, the stay bill, the phone's own saved submissions, an
 * amenity notification): what, how many, how much, for which room and in which state. Pure data; {@link TransactionRooms} decides
 * which of them go under which room.
 */
final class AmenityRecord {

    enum Status { PENDING, APPROVED, REJECTED }

    /** The server's request id, or 0 when the source has none. */
    final long id;
    final String name;
    final int quantity;
    /** What the line adds up to, or -1 when the source does not say. */
    final double subtotal;
    /** The room NUMBER ("1-001") exactly as the source gave it, or null when the source does not link the line to a room. */
    @Nullable final String room;
    final String roomType;
    final Status status;
    /** When the guest asked / when the front desk decided (epoch millis, 0 = not given). */
    final long requestedAtMillis;
    final long decidedAtMillis;

    AmenityRecord(long id, @Nullable String name, int quantity, double subtotal, @Nullable String room, @Nullable String roomType, @NonNull Status status) {
        this(id, name, quantity, subtotal, room, roomType, status, 0, 0);
    }

    AmenityRecord(long id, @Nullable String name, int quantity, double subtotal, @Nullable String room, @Nullable String roomType,
                  @NonNull Status status, long requestedAtMillis, long decidedAtMillis) {
        this.requestedAtMillis = requestedAtMillis;
        this.decidedAtMillis = decidedAtMillis;
        this.id = id;
        this.name = name == null ? "" : name.trim();
        this.quantity = Math.max(1, quantity);
        this.subtotal = subtotal;
        this.room = normalizeRoom(room);
        this.roomType = roomType == null ? "" : roomType.trim();
        this.status = status;
    }

    /** "Room 1-001" / " 1-001 " -> "1-001"; null for blank. */
    @Nullable
    static String normalizeRoom(@Nullable String room) {
        if (room == null) return null;
        String s = room.trim();
        if (s.regionMatches(true, 0, "room", 0, 4)) s = s.substring(4).trim();
        return s.isEmpty() ? null : s;
    }

    /** Case-insensitive key of a room number, for comparing sources. */
    @NonNull
    static String roomKey(@Nullable String room) {
        String n = normalizeRoom(room);
        return n == null ? "" : n.toLowerCase(Locale.ROOT);
    }

    @NonNull
    String nameKey() {
        return name.toLowerCase(Locale.ROOT);
    }
}
