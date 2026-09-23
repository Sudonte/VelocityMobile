package com.example.velocitysuites;

/**
 * In-process, consume-once handoff of a fully-reviewed Booking-mode wizard
 * state from Step8ReviewPaymentFragment into PaymentActivity. A Booking must
 * never be created before a successful GCash payment submission (see the
 * project's Booking/Reservation separation requirement), so Step 7 no longer
 * collects payment or calls RoomRepository#createDirectBooking() itself -
 * it only assembles the reviewed BookingWizardState and hands it here, then
 * PaymentActivity performs the actual createDirectBooking() call once the
 * GCash portal step succeeds. Same static-holder convention as
 * PendingWizardRooms/PendingRoomSelection - both ends run in one process.
 */
final class PendingBookingPayload {

    private static BookingWizardState pendingState;

    private PendingBookingPayload() {}

    static void set(BookingWizardState state) {
        pendingState = state;
    }

    /** Returns the pending state and clears it so a stale review can never leak into a later, unrelated payment. */
    static BookingWizardState consume() {
        BookingWizardState result = pendingState;
        pendingState = null;
        return result;
    }
}
