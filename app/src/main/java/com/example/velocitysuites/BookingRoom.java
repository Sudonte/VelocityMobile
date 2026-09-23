package com.example.velocitysuites;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * One room-type line item within a multi-room-type Booking/Reservation
 * transaction (e.g. "2 Deluxe Room" is one BookingRoom with quantity=2) -
 * see MULTI_ROOM_TRANSACTION_BACKEND_SPEC.md (root of the repo). Populated
 * on {@link Booking#getRooms()} once the backend returns a `room_lines`
 * array (network/dto/BookingRoomDto) - confirmed live 2026-09-18; empty for
 * a transaction predating this feature. Until then, Booking's existing
 * single roomType/roomName fields remain the source of truth for display.
 */
public class BookingRoom implements Serializable {
    private final String roomTypeId;
    private final String roomTypeName;
    private final int quantity;
    private final double pricePerNight;
    private final long nights;
    private final double subtotal;
    private final List<String> assignedRoomNumbers;

    public BookingRoom(String roomTypeId, String roomTypeName, int quantity, double pricePerNight, long nights, double subtotal,
                        List<String> assignedRoomNumbers) {
        this.roomTypeId = roomTypeId;
        this.roomTypeName = roomTypeName;
        this.quantity = quantity;
        this.pricePerNight = pricePerNight;
        this.nights = nights;
        this.subtotal = subtotal;
        this.assignedRoomNumbers = assignedRoomNumbers != null ? assignedRoomNumbers : Collections.emptyList();
    }

    public String getRoomTypeId() { return roomTypeId; }
    public String getRoomTypeName() { return roomTypeName; }
    public int getQuantity() { return quantity; }
    public double getPricePerNight() { return pricePerNight; }
    public long getNights() { return nights; }
    public double getSubtotal() { return subtotal; }

    /** Physical room number(s) assigned to this room type at check-in - empty before check-in (or for a not-yet-converted Reservation, which never has physical assignments). */
    public List<String> getAssignedRoomNumbers() { return assignedRoomNumbers; }
}
