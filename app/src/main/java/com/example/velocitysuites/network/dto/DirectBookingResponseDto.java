package com.example.velocitysuites.network.dto;

import java.util.List;

/**
 * The JSON shape returned by POST/GET api/guest/bookings (Api\BookingController) -
 * a genuinely independent Booking transaction, never derived from a Reservation
 * (reservation_id is always null here). Distinct from ReservationDto/BookingDto,
 * which represent the older Reservation-first path and are unrelated to this one.
 */
public class DirectBookingResponseDto {
    public long id;
    public Long reservation_id;
    public long guest_id;
    public String guest_first_name;
    public String guest_middle_name;
    public String guest_last_name;
    public long room_type_id;
    public Long room_id;
    public int rooms_requested;
    public String check_in;
    public String check_out;
    public int adults;
    public int children;
    public int number_of_guests;
    public String confirmed_at;
    public String booking_status;
    public String payment_method;
    public String id_card_type;
    public String id_card_image_path;
    public List<AdditionalGuestDto> additional_guest_details;
    public boolean discount_requested;
    public String discount_verification_status;
    public String verified_at;
    public String hidden_at;
    // Set when a receptionist rejects this booking (Receptionist\
    // BookingController::reject() - a direct "New Booking" is just as
    // rejectable as a reservation-derived one, since that action only
    // checks booking_status, not reservation_id).
    public String rejection_reason;
    // Set the moment this booking transitions to CANCELLED_BOOKING (see
    // ReservationWorkflowService - backend).
    public String cancelled_at;
    public String checked_in_at;
    public String checked_out_at;
    public String completed_at;
    public RoomDto room;
    public RoomTypeDto room_type;
    public GuestDto guest;
    public List<PaymentDto> payments;
    /**
     * Itemized room-type breakdown (booking_room_lines child rows, via
     * Booking::getRoomLinesAttribute() - confirmed live 2026-09-18). Empty
     * (not null) for a booking predating the multi-room-type feature. Named
     * room_lines, not rooms - the backend's own Booking model already has an
     * unrelated `rooms` relation (physical assigned Room units).
     */
    public List<BookingRoomDto> room_lines;
    /** Itemized paid-amenity breakdown (booking_amenity_lines child rows) - see MULTI_ROOM_TRANSACTION_BACKEND_SPEC.md. Empty (not null) for a booking created before this shipped. */
    public List<BookingAmenityDto> amenities;
    /**
     * The one authoritative grand total for this whole transaction
     * (Booking::getTotalAmountDueAttribute() server-side) - sums every
     * selected room type's subtotal plus every selected amenity's
     * subtotal, independent of amount_paid (which may be a partial/
     * deposit amount). Must be used as the Booking's total instead of
     * the latest payment's own amount_paid - see ApiMapper#toBooking()'s
     * own doc for why reading amount_paid as if it were the total is
     * wrong for any partial payment.
     */
    public double total_amount_due;
    /**
     * The percentage tier (20/30/40/50/100) actually paid at booking time -
     * always a whole percentage value, never a 0.20-1.00 fraction - see
     * Api\BookingController::store()'s own doc. Null for a booking created
     * before this field shipped.
     */
    public Double selected_payment_percentage;
    /** The peso amount that corresponds to selected_payment_percentage - same value as the latest payment's own amount_paid, kept alongside the percentage for convenience. */
    public Double required_payment_amount;

    /**
     * Attached only on Api\BookingController::show() (not index()/store()) -
     * the authoritative Grand Total/Total Amount Paid/Remaining Balance/
     * Payment Status/Official-Receipt-availability block (ReceiptService,
     * backend). Null on any response that doesn't attach it yet - see
     * PaymentSummaryDto's own doc. When present, this must be preferred
     * over any client-side reconstruction from totalAmount/amountPaid.
     */
    public PaymentSummaryDto payment_summary;
    /** Attached only on show() - the complete, chronological Payment Transaction History (ReceiptService::paymentTransactions(), backend). Null/absent on an older response - see PaymentTransactionDto's own doc. */
    public java.util.List<PaymentTransactionDto> payment_transactions;
    /** Attached only on show() - every receipt already issued for this booking (ReceiptService::receiptsList(), backend). Null/absent on an older response - see ReceiptSummaryDto's own doc. */
    public java.util.List<ReceiptSummaryDto> receipts;
}
