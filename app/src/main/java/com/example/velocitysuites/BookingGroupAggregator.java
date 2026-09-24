package com.example.velocitysuites;

import java.util.List;

/**
 * Single source of truth for summing money fields across a BookingGroupState group's
 * sibling records - extracted from the two near-identical loops that had accumulated in
 * BookingDetailsActivity#buildPaymentSummarySection() and PaymentReceiptActivity#populateReceipt()
 * (both Activities, so neither copy was JVM-testable - see BookingGroupAggregatorTest).
 *
 * <p><b>Status handling (deliberately unchanged):</b> {@link #sum} sums every member
 * regardless of that member's own status (Confirmed/Cancelled/Rejected/etc.) - this matches
 * both callers' pre-existing behavior exactly, not a new decision made here. Whether a
 * Cancelled/Rejected sibling in an otherwise-active group should be excluded from the
 * group's displayed Grand Total is genuinely unresolved: `BookingGroupState` siblings are
 * real, independent `bookings`/`reservations` rows (each individually cancellable/deletable
 * via its own id - see BookingAndReservationActivity's per-row Cancel/Delete actions), and
 * MULTI_ROOM_TRANSACTION_BACKEND_SPEC.md's only explicit cascading-status rule ("cancelling
 * releases every room line's inventory, not just one") describes the FUTURE true
 * single-transaction architecture (one parent row, real child room lines) it's proposing,
 * not this current client-side grouping stopgap - the doc explicitly says BookingGroupState
 * itself is "unrelated" to that rule and is left untouched by that proposal. Absent a
 * specified rule for the current architecture, this class does not invent one; it only
 * centralizes the existing sum-everything behavior so it's expressed once instead of twice.
 */
public final class BookingGroupAggregator {

    private BookingGroupAggregator() {
    }

    public static final class Totals {
        public final double totalAmount;
        public final double amountPaid;
        public final double roomCharge;
        public final double amenityCharge;
        public final double additionalGuestFee;

        Totals(double totalAmount, double amountPaid, double roomCharge, double amenityCharge, double additionalGuestFee) {
            this.totalAmount = totalAmount;
            this.amountPaid = amountPaid;
            this.roomCharge = roomCharge;
            this.amenityCharge = amenityCharge;
            this.additionalGuestFee = additionalGuestFee;
        }
        // Deliberately no remainingBalance() helper here - the two existing callers
        // already disagreed on clamping (BookingDetailsActivity leaves it unclamped,
        // matching Booking#getRemainingBalance()'s own contract; PaymentReceiptActivity
        // clamps to zero for receipt display) before this class existed. Encoding
        // either choice here would silently change the other caller's behavior, so
        // each still computes remainingBalance itself from totalAmount/amountPaid.
    }

    /** Sums every sibling's totalAmount/amountPaid/roomCharge/amenityCharge/additionalGuestFee - see this class's own doc for the status-handling caveat. Empty input sums to all zeros, never null. */
    public static Totals sum(List<Booking> groupMembers) {
        double totalAmount = 0, amountPaid = 0, roomCharge = 0, amenityCharge = 0, additionalGuestFee = 0;
        for (Booking member : groupMembers) {
            totalAmount += member.getTotalAmount();
            // Each sibling's own backend-authoritative payment_summary.total_amount_paid
            // when the backend has attached one to that member, else its legacy
            // amountPaid field - see Booking#getEffectiveTotalAmountPaid()'s own doc.
            amountPaid += member.getEffectiveTotalAmountPaid();
            roomCharge += member.getRoomCharge();
            // Amenities/additional-guest fee were attached to exactly one sibling at
            // creation time (PaymentActivity#submitPendingBookingGroups()'s/
            // submitPendingReservationGroups()'s index==0 rule), and only that sibling
            // ever carries a non-zero value once it converts and gets a real Billing
            // row - summing across every sibling picks that one up correctly
            // regardless of its position in the group.
            amenityCharge += member.getAmenityCharge();
            additionalGuestFee += member.getAdditionalGuestFee();
        }
        return new Totals(totalAmount, amountPaid, roomCharge, amenityCharge, additionalGuestFee);
    }
}
