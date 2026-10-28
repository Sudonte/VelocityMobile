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
    /** The nightly rate after the front desk verified a promo for this room type (0 = no promo on this line). */
    private final double ratePerNightAfterPromo;
    private final boolean promoApplied;

    public BookingRoom(String roomTypeId, String roomTypeName, int quantity, double pricePerNight, long nights, double subtotal,
                        List<String> assignedRoomNumbers) {
        this(roomTypeId, roomTypeName, quantity, pricePerNight, nights, subtotal, assignedRoomNumbers, -1);
    }

    public BookingRoom(String roomTypeId, String roomTypeName, int quantity, double pricePerNight, long nights, double subtotal,
                        List<String> assignedRoomNumbers, double ratePerNightAfterPromo) {
        this.promoApplied = ratePerNightAfterPromo >= 0 && ratePerNightAfterPromo < pricePerNight - 0.004;
        this.ratePerNightAfterPromo = Math.max(0, ratePerNightAfterPromo);
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
    /** The nightly rate with the verified promo, or 0 when no promo applies to this line. */
    public double getRatePerNightAfterPromo() { return ratePerNightAfterPromo; }
    public boolean hasPromoRate() { return promoApplied; }
    public double getSubtotal() { return subtotal; }

    /** Physical room number(s) assigned to this room type at check-in - empty before check-in (or for a not-yet-converted Reservation, which never has physical assignments). */
    public List<String> getAssignedRoomNumbers() { return assignedRoomNumbers; }
}
