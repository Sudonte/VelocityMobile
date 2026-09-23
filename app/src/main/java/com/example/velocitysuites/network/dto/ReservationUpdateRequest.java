package com.example.velocitysuites.network.dto;

import java.util.List;

/**
 * PUT /guest/reservations/{id} body - see Api\ReservationController::update() and
 * RoomRepository#updateReservation()/#updateReservationFull(). room_type_id/rooms_requested are
 * only set by the wizard-based "Modify" flow (updateReservationFull()) - the plain
 * updateReservation() leaves them null, which Gson omits from the JSON body entirely and the
 * server treats as "unchanged".
 */
public class ReservationUpdateRequest {
    public String check_in;
    public String check_out;
    public int adults;
    public int children;
    public String id_card_type;
    public List<AdditionalGuestDto> additional_guests;
    /**
     * Boxed (not primitive long) so an un-set selection is genuinely omitted
     * from the JSON body (Gson drops null fields by default - see ApiClient's
     * GsonBuilder, which does not call serializeNulls()) instead of always
     * serializing as the primitive default 0. The server's `nullable|exists:
     * room_types,id` rule only skips validation when the key is truly absent
     * or JSON null - a present `0` still runs the `exists` check and fails
     * with "The selected room type id is invalid," which is exactly what
     * happened while this was a primitive and the multi-room-type overload
     * (RoomRepository#updateReservationFull(List<List<Room>>, ...)) left it
     * unset.
     */
    public Long room_type_id;
    /** Same boxed-vs-primitive reasoning as room_type_id above - a present `0` fails the server's `min:1` rule instead of being skipped as "unchanged." */
    public Integer rooms_requested;
    /**
     * One entry per DISTINCT room type the guest selected during the one-time
     * Modify - set instead of (never alongside) room_type_id/rooms_requested
     * above whenever the modified selection has more than one room type, or
     * whenever the guest changed the selection at all. Null (omitted from the
     * JSON body entirely - Gson drops null fields by default) means "leave the
     * reservation's current room selection unchanged"; sending it always fully
     * REPLACES every room line server-side (see Api\ReservationController::update()).
     */
    public List<RoomSelectionDto> rooms;
    /**
     * Every selected paid amenity - null (omitted) means "leave the current
     * amenity selection unchanged"; an explicit empty list is a deliberate
     * "remove all amenities" and is honored, not treated as "no change."
     */
    public List<AmenitySelectionDto> amenities;

    public ReservationUpdateRequest(String checkIn, String checkOut, int adults, int children) {
        this.check_in = checkIn;
        this.check_out = checkOut;
        this.adults = adults;
        this.children = children;
    }
}
