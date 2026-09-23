package com.example.velocitysuites.network.dto;

public class PaymentDto {
    public long id;
    public long billing_id;
    public String payment_method;
    public String reference_number;
    public String amount_paid;
    public String payment_status;
    public String payment_date;
    public BillingDto billing;
    /** Registered GCash mobile number the guest paid from - new as of the receipt-verification workflow. */
    public String gcash_number;
    /** Absolute URL to the uploaded GCash receipt image, pre-built by the server (Payment::getReceiptUrlAttribute). */
    public String receipt_url;
    /** Reason a receptionist rejected this payment, if any. */
    public String rejection_reason;
    /** Server-authoritative tri-state: "pending_verification" | "verified" | "rejected" | null. */
    public String verification_status;
    /** Moment a receptionist verified this payment - null until verification_status is "verified". */
    public String verified_at;
    /** Moment a receptionist rejected this payment - null until verification_status is "rejected". */
    public String rejected_at;
}
