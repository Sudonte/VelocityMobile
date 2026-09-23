package com.example.velocitysuites.network.dto;

import java.util.List;

/** POST /guest/reservations body - see Api\ReservationController::store() and RoomRepository#createReservation(). */
public class ReservationRequest {
    public long room_type_id;
    public int rooms_requested;
    /**
     * One entry per DISTINCT room type the guest selected, each with its
     * own quantity - set instead of (never alongside) room_type_id/
     * rooms_requested above whenever the guest selected more than one room
     * type in one wizard pass, so the whole submission becomes ONE
     * reservation transaction (see RoomRepository#createReservation()).
     * Null for the legacy single-room-type case, which still sends
     * room_type_id/rooms_requested exactly as before - the backend accepts
     * either shape.
     */
    public List<RoomSelectionDto> rooms;
    public String check_in;
    public String check_out;
    public int adults;
    public int children;
    public String guest_first_name;
    public String guest_middle_name;
    public String guest_last_name;
    public String id_card_type;
    public String payment_method;
    public Integer selected_payment_percentage;
    public List<AdditionalGuestDto> additional_guests;
    public List<AmenitySelectionDto> amenities;
    /**
     * One per Confirm-button tap (never per room/line) - lets a double-tap
     * or client/network retry of the same submission attempt safely return
     * the original reservation instead of creating a duplicate. See
     * MULTI_ROOM_TRANSACTION_BACKEND_SPEC.md section 9b and
     * Api\ReservationController::store()'s matching field.
     */
    public String idempotency_key;

    public ReservationRequest(long roomTypeId, int roomsRequested, String checkIn, String checkOut,
                               int adults, int children, String guestFirstName, String guestLastName) {
        this.room_type_id = roomTypeId;
        this.rooms_requested = roomsRequested;
        this.check_in = checkIn;
        this.check_out = checkOut;
        this.adults = adults;
        this.children = children;
        this.guest_first_name = guestFirstName;
        this.guest_last_name = guestLastName;
    }
}
