package com.example.velocitysuites.network.dto;

import java.util.List;

/**
 * A Reservation - the pre-booking-conversion record guest/reservations returns (see
 * Api\ReservationController and ApiMapper#toBooking(ReservationDto)). Once staff convert it, the
 * live operational state moves to the nested `booking` (a Booking row); this DTO's own top-level
 * status/verified_at/hidden_at/rejection_reason/cancelled_at remain as the pre-conversion history
 * and are only read as a fallback when `booking` is null - see ApiMapper's own docs on that.
 */
public class ReservationDto {
    public long id;
    public long room_type_id;
    public RoomTypeDto room_type;
    /** dto.room is always null under the current backend - room assignment lives on the Booking once one exists (kept only for old/unconverted records). */
    public RoomDto room;
    public String check_in;
    public String check_out;
    public int rooms_requested;
    public int adults;
    public int children;
    public int number_of_guests;
    public String guest_first_name;
    public String guest_middle_name;
    public String guest_last_name;
    public String id_card_type;

    /** AWAITING_CASH_CONFIRMATION | AWAITING_GCASH_PAYMENT | TO_BE_CONVERTED | REJECTED_RESERVATION | CANCELLED_RESERVATION | CONVERTED_TO_BOOKING - see App\Support\ReservationStatus (backend) and ApiMapper#displayStatus(). */
    public String status;
    public String payment_method;
    public String payment_method_locked_at;
    /** Server-side one-time-edit lock (Reservation::edited_at) - null until the guest's one allowed Modify has been used. */
    public String edited_at;
    public String payment_deadline;
    public Double selected_payment_percentage;
    public Double required_payment_amount;
    public String rejection_reason;
    public String cancelled_at;
    public String verified_at;
    public String hidden_at;
    /** Reservation creation timestamp (UTC ISO-8601) - permanent, distinct from booking.confirmed_at (when it was converted). Null on older cached responses. */
    public String created_at;
    public String updated_at;
    /** Only meaningful once converted (see booking below) - kept here too since a few legacy/edge responses surface it at the reservation level rather than nested under booking. */
    public String checked_in_at;
    public String checked_out_at;
    public String completed_at;

    /** A deposit may already sit against the Reservation itself before conversion (billing_id is null until then) - see ApiMapper's fallback amount-paid logic. */
    public List<PaymentDto> payments;
    /** Nullable JSON column, not a relationship - Laravel serializes an unset value as JSON null, not [] (see ApiMapper's own comment on this). */
    public List<AdditionalGuestDto> additional_guest_details;
    public DiscountPreviewDto discount_preview;
    /** Set once staff convert this reservation into a Booking - see ApiMapper#toBooking()/#toHistoricalReservation(). */
    public BookingDto booking;
    /**
     * Itemized room-type breakdown (reservation_room_lines child rows, via
     * Reservation::getRoomLinesAttribute() - confirmed live 2026-09-18) for a
     * not-yet-converted Reservation - one entry per distinct room type the
     * guest selected. Empty (not null) for a reservation created before this
     * shipped. Once converted, the equivalent breakdown lives at
     * booking.billing.room_lines instead (see that field's own doc). Named
     * room_lines, not rooms - the backend's Booking model already has an
     * unrelated `rooms` relation, so both this and the Booking-side fields
     * use the same non-colliding name for consistency.
     */
    public List<BookingRoomDto> room_lines;
    /** Itemized paid-amenity breakdown (reservation_amenities child rows), pre-conversion - see the `rooms` field's own doc above for the post-conversion equivalent. */
    public List<BookingAmenityDto> amenities;
    /**
     * The one authoritative grand total for this whole reservation
     * (Reservation::total_amount_due server-side) - sums every selected
     * room type's subtotal plus every selected amenity's subtotal,
     * correctly combining ALL room types when more than one was selected
     * in the same submission. Must be used as this reservation's total
     * instead of re-deriving it from just room_type/rooms_requested - see
     * ApiMapper#toBooking(ReservationDto)'s own doc.
     */
    public double total_amount_due;

    /**
     * Attached only on Api\ReservationController::show() (not index()) -
     * delegates to the converted Booking's own authoritative summary once
     * one exists, or a safe zero/PENDING default before that (see
     * Reservation::paymentSummary(), backend). Null on any response that
     * doesn't attach it yet - see PaymentSummaryDto's own doc. This is the
     * SAME kind of block DirectBookingResponseDto#payment_summary carries -
     * deliberately reusing PaymentSummaryDto rather than a second, parallel
     * model, since the backend payload shape doesn't differ.
     */
    public PaymentSummaryDto payment_summary;
    /** Attached only on show() - see DirectBookingResponseDto#payment_transactions's identical contract; empty before conversion (nothing can be receptionist-verified without a Booking yet). */
    public java.util.List<PaymentTransactionDto> payment_transactions;
    /** Attached only on show() - see DirectBookingResponseDto#receipts's identical contract; empty before conversion. */
    public java.util.List<ReceiptSummaryDto> receipts;

    public static class DiscountPreviewDto {
        public double discount;
    }
}
