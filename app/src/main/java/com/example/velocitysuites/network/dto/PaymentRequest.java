package com.example.velocitysuites.network.dto;

import java.util.Locale;

public class PaymentRequest {
    public String payment_method;
    public String payment_type;
    public String reference_number;
    public double amount_paid;
    /**
     * The exact tier (20/30/40/50/100) the guest picked on the payment
     * screen - always a whole percentage, never a 0.20-1.00 fraction (see
     * PaymentActivity#selectedGcashPercentageForRequest()). Boxed so it's
     * genuinely omitted from the JSON body when null rather than
     * serializing as a primitive 0 - the backend's `nullable|in:20,30,40,
     * 50,100` rule only skips validation when the key is truly absent.
     * Lets Api\PaymentController::store() validate the amount actually
     * submitted against a server-computed required_payment_amount instead
     * of only checking it falls within the old 20%-50% range, and persist
     * both onto the reservation for every other screen to read back
     * consistently (Booking Details, Payment Receipt, receptionist/manager
     * web).
     */
    public Integer selected_payment_percentage;

    public PaymentRequest(String paymentMethod, String paymentType, String referenceNumber, double amountPaid) {
        this.payment_method = paymentMethod;
        this.payment_type = paymentType;
        this.reference_number = referenceNumber;
        this.amount_paid = amountPaid;
    }
}
