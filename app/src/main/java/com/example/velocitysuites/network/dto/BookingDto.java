package com.example.velocitysuites.network.dto;

public class BookingDto {
    public long id;
    public long reservation_id;
    // Renamed from booking_date when the Booking table was restructured -
    // a Booking now only ever exists once staff have converted the
    // reservation, so this is when that happened, not a separate "booking
    // date" concept.
    public String confirmed_at;
    public String booking_status;
    // Receptionist verification gate - null until a receptionist clicks
    // "Verify Booking" (Receptionist\BookingController::verify()). Separate
    // from booking_status, which check-in/check-out still drive as before.
    public String verified_at;
    // Guest-view-only hide flag, set on the linked booking row alongside the
    // reservation's when a completed/cancelled transaction is hidden - see
    // ReservationWorkflowService::hide().
    public String hidden_at;
    // Set when a receptionist rejects this already-converted Booking (see
    // Receptionist\BookingController::reject()) - distinct from a payment's
    // own rejection_reason (PaymentDto) and from a pre-conversion
    // Reservation's own rejection_reason (ReservationDto).
    public String rejection_reason;
    // Set the moment this booking transitions to CANCELLED_BOOKING (see
    // ReservationWorkflowService - backend).
    public String cancelled_at;
    // Actual guest arrival time, set by a receptionist's Check-In action -
    // deliberately separate from the scheduled check_in date on the
    // Reservation side (see TimeUtils/Booking timeline docs). Null until
    // check-in happens.
    public String checked_in_at;
    // Actual guest departure time, set by a receptionist's Check-Out action.
    public String checked_out_at;
    // Set once the whole stay/transaction is marked complete, if the backend
    // tracks that as a distinct moment from checked_out_at.
    public String completed_at;
    public RoomDto room;
    public BillingDto billing;
}
