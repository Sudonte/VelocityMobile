package com.example.velocitysuites.network.dto;

import java.util.List;

/**
 * The full payload returned by GET guest/receipts/{receiptNumber} (Api\ReceiptController::show(),
 * ReceiptService::buildReceiptPayload() on the backend) - matches that
 * method's return array exactly. See PAYMENT_RECEIPT_HISTORY_BACKEND_SPEC.md
 * §3/§4/§5 for the single most important contract this DTO carries:
 * <p>
 * For a PARTIAL_RECEIPT/FULL_PAYMENT_RECEIPT (anchor_payment non-null),
 * payment_summary/payment_transactions are a FROZEN POINT-IN-TIME SNAPSHOT
 * of what was true the moment that specific payment was made - NOT the
 * booking's current/live totals. Android must display these values exactly
 * as returned and must NEVER recompute or overwrite them using the
 * booking's current final totals (e.g. from a separately-cached Booking
 * object) - re-opening an old Partial Receipt after the guest later pays
 * the remaining balance must keep showing the ORIGINAL amounts.
 * <p>
 * For an OFFICIAL_RECEIPT (anchor_payment null, billing-anchored),
 * payment_summary/payment_transactions are the live final checkout totals
 * and the complete payment history.
 */
public class ReceiptDetailDto {
    /** PARTIAL_RECEIPT | FULL_PAYMENT_RECEIPT | OFFICIAL_RECEIPT. */
    public String receipt_type;
    public String receipt_number;
    public long booking_id;
    /** Set only when this booking was converted from a Reservation. */
    public Long reservation_id;
    public String guest_account_name;
    public String representative_name;
    public String room_type;
    public List<BookingRoomDto> room_lines;
    public String check_in;
    public String check_out;
    public int number_of_nights;
    public List<String> assigned_room_numbers;
    /**
     * The point-in-time snapshot (PR/FR) or live final totals (OR) - see
     * this class's own doc. Always display exactly as returned here, never
     * substitute a separately-fetched Booking's own live payment_summary.
     */
    public PaymentSummaryDto payment_summary;
    /** History "as it stood at that point" (PR/FR, trimmed) or the complete history (OR) - see this class's own doc. */
    public List<PaymentTransactionDto> payment_transactions;
    /** The specific payment this PARTIAL_RECEIPT/FULL_PAYMENT_RECEIPT is anchored on - null for an OFFICIAL_RECEIPT. */
    public AnchorPaymentDto anchor_payment;
    /** ISO-8601 instant this receipt was issued. */
    public String issued_at;

    public static class AnchorPaymentDto {
        public double amount_paid;
        public String payment_method;
        public Integer payment_percentage;
        public String gcash_number;
        public String gcash_reference_number;
        public String verified_at;
        public String verified_by;
    }
}
