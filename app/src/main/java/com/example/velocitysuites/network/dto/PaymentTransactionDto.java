package com.example.velocitysuites.network.dto;

/**
 * One entry in payment_transactions - matches ReceiptService::paymentTransactions()
 * on the backend exactly (see PAYMENT_RECEIPT_HISTORY_BACKEND_SPEC.md), in
 * chronological order (oldest first). Covers every real payment attempt
 * against a booking regardless of source (reservation-originated, direct-
 * booking, or checkout-collected) and regardless of outcome (rejected/
 * failed rows are included too, as an audit trail - see payment_status).
 * <p>
 * Many fields are legitimately null depending on how this specific payment
 * happened - a receptionist-recorded Cash checkout row, for example, has no
 * GCash fields, no verification_status, no verified_at, and no receipt_type/
 * receipt_number (it only ever appears inside the Official Receipt's own
 * history, never as a standalone receipt - see Payment::receiptType(),
 * backend). Android must never assume any of these are present.
 */
public class PaymentTransactionDto {
    public long id;
    /** gcash | cash. */
    public String payment_method;
    /** deposit | final - see Payment::payment_stage (backend). */
    public String payment_stage;
    /** PARTIAL_PAYMENT | FULL_PAYMENT | CHECKOUT_PAYMENT - see ReceiptService::transactionType() (backend). Unknown future values must be handled gracefully, never crash. */
    public String transaction_type;
    public double amount_paid;
    /** pending | completed | failed | rejected. */
    public String payment_status;
    /** pending_verification | verified | rejected | null - only meaningful for a guest-submitted GCash payment; always null for a receptionist-recorded checkout row. */
    public String verification_status;
    public String gcash_number;
    /** Same value as reference_number when payment_method is gcash, otherwise null - see ReceiptService's own doc for why both keys exist. */
    public String gcash_reference_number;
    public String reference_number;
    /** Whole percentage (20/30/40/50/100) - only set for a deposit-stage row, null for a final-stage one (see PaymentSummaryDto's identical contract). */
    public Integer payment_percentage;
    /** Verifying staff member's name, or null - never present for a checkout-collected row (no separate verification step). */
    public String verified_by;
    public String verified_at;
    public String rejection_reason;
    public String payment_date;
    /** Running total paid AFTER this transaction, in chronological order - already backend-computed, never re-derive client-side. */
    public double total_paid_after_transaction;
    /** Running remaining balance AFTER this transaction - see total_paid_after_transaction's identical contract. */
    public double remaining_balance_after_transaction;
    /** PARTIAL_RECEIPT | FULL_PAYMENT_RECEIPT | null - null whenever this specific payment never had (or hasn't yet had) its own receipt issued. Never OFFICIAL_RECEIPT here - that only ever appears on the Billing-level receipts entry. */
    public String receipt_type;
    /** PR-.../FR-... when receipt_type is non-null, otherwise null. Never generate one client-side - display exactly what the backend already issued. */
    public String receipt_number;
}
