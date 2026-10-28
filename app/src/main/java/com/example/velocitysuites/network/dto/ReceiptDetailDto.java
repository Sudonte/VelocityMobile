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
    /** 1 = a payment verified in the Booking module, 2 = the balance settled at check-in, 3 = the official receipt at check-out. */
    public Integer receipt_stage;
    /** Receipt 2 only: the frozen check-in settlement (null for the other receipts). */
    public CheckinSettlementDto checkin_settlement;
    /** The highlighted bottom block, frozen with the receipt (absent on receipts issued before receipts were stored). */
    public HighlightDto highlight;
    public long booking_id;
    /** Set only when this booking was converted from a Reservation. */
    public Long reservation_id;
    public String guest_account_name;
    public String representative_name;
    public String room_type;
    public List<BookingRoomDto> room_lines;
    /** Every stay of the transaction as it was when this receipt was issued (see StayDto); absent on a receipt issued before stays. */
    public List<StayDto> stays;
    public String check_in;
    public String check_out;
    public int number_of_nights;
    /** The itemized stay - null until the stay is in house. */
    public StayBillDto stay_bill;
    public List<String> assigned_room_numbers;
    public int adults;
    public int children;
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
    /** The receipts issued for this stay so far (number, stage, date, amount). */
    public List<ReceiptNumberDto> receipt_numbers;
    public DiscountInfoDto discount_info;
    /** The whole story of the transaction from creation on (the check-out receipt prints it). */
    public List<TimelineStepDto> transaction_history;

    public static class ReceiptNumberDto {
        public int stage;
        public String receipt_number;
        public String receipt_type;
        public String issued_at;
        public double amount;
    }

    public static class DiscountInfoDto {
        public String id_type;
        public String status;
    }

    /** Receipt 2: what was settled in the Check-In module, as the server froze it. */
    public static class CheckinSettlementDto {
        /** partial_to_full | full_payment | full_confirmation */
        public String settlement_type;
        public double amount_settled;
        public String payment_method;
        public double change_returned;
        public double total_amount;
        public double total_paid_before;
        public double total_paid;
        public double balance;
        public DiscountResultDto discount;
        public String settled_at;
        public String verified_by;
    }

    /** The numbers a receipt highlights at its bottom - every figure the server froze when it issued the receipt. */
    public static class HighlightDto {
        /** booking_verification | checkin_payment | checkout_final */
        public String kind;
        public String payment_type;
        public String payment_method;
        public Double amount_paid_now;
        public Double previous_payments;
        public Double total_amount;
        public Double total_paid;
        public Double remaining_balance;
        public Double amount_to_pay;
        public String amount_to_pay_note;
        public Double room_charges;
        public Double amenities_total;
        public Double additional_charges_total;
        public Double discount;
        /** The promo the desk verified: its own line, off the room rate. */
        public Double promo;
        public String promo_name;
        public String discount_name;
        public Double grand_total;
        public Double paid_receipt_1;
        public Double paid_receipt_2;
        public Double paid_at_checkout;
        /** The final receipt's lines (absent on receipts issued before they were frozen): amenities by room, desk charges, every payment. */
        public java.util.List<AmenityGroupDto> amenities_by_room;
        public java.util.List<ChargeDto> other_charges;
        public java.util.List<PaymentLineDto> payments;
        public String no_refund_note;
    }

    public static class AmenityGroupDto {
        /** The room number, or null for amenities booked for the whole stay. */
        public String room;
        /** The booked room ("Deluxe (room 2)") when the amenity was chosen for one at booking time and no room number is assigned yet. */
        public String label;
        public java.util.List<StayBillDto.Line> items;
        public Double subtotal;
    }

    public static class ChargeDto {
        public String name;
        public Double amount;
    }

    public static class PaymentLineDto {
        public String label;
        public Double amount;
        public String method;
        public String date;
    }

    /** The discount ID result at check-in. */
    public static class DiscountResultDto {
        public boolean requested;
        /** not_requested | approved | rejected */
        public String status;
        public String name;
        public double amount;
        public String rejection_reason;
    }

    public static class AnchorPaymentDto {
        public double amount_paid;
        /** Cash only (null for GCash). */
        public Double cash_received;
        public Double change_given;
        public String payment_method;
        public Integer payment_percentage;
        public String gcash_number;
        public String gcash_reference_number;
        public String verified_at;
        public String verified_by;
    }
}
