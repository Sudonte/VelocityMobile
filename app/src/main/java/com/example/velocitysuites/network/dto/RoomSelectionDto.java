package com.example.velocitysuites.network.dto;

/**
 * One DISTINCT room type + quantity the guest selected - sent as part of
 * ReservationRequest.rooms so a guest who selected several room types in
 * one wizard pass (Step8ReviewPaymentFragment) submits them all as ONE
 * reservation transaction instead of one request per room type. Mirrors
 * the `rooms[]` shape RoomRepository#createDirectBooking() already sends
 * for a "New Booking" (Api\BookingController::store() validates the exact
 * same `rooms.*.room_type_id`/`rooms.*.quantity` pair).
 */
public class RoomSelectionDto {
    public long room_type_id;
    public int quantity;

    public RoomSelectionDto(long roomTypeId, int quantity) {
        this.room_type_id = roomTypeId;
        this.quantity = quantity;
    }
}
