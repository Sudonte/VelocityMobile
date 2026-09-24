package com.example.velocitysuites.network.dto;

/**
 * One entry in receipts - matches ReceiptService::receiptsList() on the
 * backend exactly (see PAYMENT_RECEIPT_HISTORY_BACKEND_SPEC.md). A booking
 * may legitimately carry several of these at once - one PARTIAL_RECEIPT or
 * FULL_PAYMENT_RECEIPT per verified pre-checkout payment, plus (once
 * checkout completes) exactly one OFFICIAL_RECEIPT - and an earlier one is
 * never removed just because a later one now also exists (see
 * PAYMENT_RECEIPT_HISTORY_BACKEND_SPEC.md §5/§9 - "must remain accessible").
 * Only ever lists a receipt that has ALREADY been issued (a real
 * receipt_number on file) - the backend never backfills/mints one just for
 * this list, so Android must not either.
 */
public class ReceiptSummaryDto {
    /** PR-.../FR-.../OR-... */
    public String receipt_number;
    /** PARTIAL_RECEIPT | FULL_PAYMENT_RECEIPT | OFFICIAL_RECEIPT - do not treat this as only Partial-vs-Official; a verified 100% pre-checkout payment is FULL_PAYMENT_RECEIPT, distinct from both. */
    public String receipt_type;
    /** VERIFIED (a PARTIAL_RECEIPT/FULL_PAYMENT_RECEIPT) or PAID (an OFFICIAL_RECEIPT). */
    public String status;
    public double amount;
    /** Whole percentage (20/30/40/50/100) for a PARTIAL_RECEIPT, 100 for a FULL_PAYMENT_RECEIPT/OFFICIAL_RECEIPT. */
    public Integer payment_percentage;
    /** ISO-8601 instant this specific receipt was issued - the payment's own verified_at for PR/FR, or the billing's own updated_at for OR. Nullable. */
    public String issued_at;
}
