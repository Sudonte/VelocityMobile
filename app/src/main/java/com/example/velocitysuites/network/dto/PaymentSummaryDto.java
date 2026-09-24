package com.example.velocitysuites.network.dto;

/**
 * The authoritative Grand Total / Total Amount Paid / Remaining Balance /
 * Payment Status block - matches ReceiptService::paymentSummary() on the
 * backend exactly (see PAYMENT_RECEIPT_HISTORY_BACKEND_SPEC.md). Attached at
 * the top level of a Booking/Reservation "show" response (Api\BookingController::show()/
 * Api\ReservationController::show()), sibling to the transaction's own
 * fields - not nested inside booking/billing. Null on any response that
 * doesn't attach it yet (e.g. the older list endpoints, or a build of the
 * backend predating this feature) - every caller must treat this DTO itself
 * as absent, not just its individual fields, until the backend deploys.
 * <p>
 * Unlike PaymentDto/BillingDto's money fields (Strings, because Payment/
 * Billing's Eloquent decimal casts serialize that way), these fields come
 * from a plain PHP array of already-cast floats/ints, so the backend emits
 * real JSON numbers here - Java primitives, not Strings.
 */
public class PaymentSummaryDto {
    public double grand_total;
    public double total_amount_paid;
    public double remaining_balance;
    /** PENDING | PARTIALLY_PAID | PAID - see PaymentMath::paymentStatus() (backend). */
    public String payment_status;
    /**
     * Whole percentage (20/30/40/50/100), never a 0.20-1.00 fraction - see
     * PaymentMath::normalizePercentage() (backend) and PaymentPercentageUtil
     * (this app) for the shared "never multiply by 100 twice" contract.
     * Null when no percentage is on file for this transaction.
     */
    public Integer payment_percentage;
    /**
     * True only once checkout has ACTUALLY completed (billing_status=paid
     * AND booking_status=COMPLETED_BOOKING - see Billing::isOfficialReceiptAvailable(),
     * backend). Android must read this flag rather than infer it from
     * remaining_balance/payment_status itself - see
     * PAYMENT_RECEIPT_HISTORY_BACKEND_SPEC.md Scenario C (a verified 100%
     * pre-checkout payment can make remaining_balance 0 well before this
     * ever becomes true).
     */
    public boolean official_receipt_available;
}
