package com.example.velocitysuites.network.dto;

import java.util.List;

public class BillingDto {
    public long id;
    public long booking_id;
    public String room_charge;
    public String additional_guest_fee;
    public String amenity_charge;
    public String discount;
    public String total_amount;
    public String billing_status;
    public List<PaymentDto> payments;
    /**
     * Itemized room-type breakdown for a converted transaction (booking_room_lines
     * child rows, via Billing::getRoomLinesAttribute() delegating to the owning
     * Booking - see that method's own doc, confirmed live 2026-09-18). Named
     * room_lines rather than rooms - the backend's own Booking model already has
     * a real, unrelated `rooms` relation (physical assigned Room units), so this
     * itemized quantity/price breakdown needed its own distinct field name to
     * avoid colliding with it server-side.
     */
    public List<BookingRoomDto> room_lines;
    /** Itemized paid-amenity breakdown (booking_amenities child rows) - null/absent until the backend ships the multi-room contract. */
    public List<BookingAmenityDto> amenities;

    public double totalAmountAsDouble() {
        return parse(total_amount);
    }

    /** Sum of payments the staff has verified (payment_status=completed). */
    public double amountPaidCompleted() {
        double sum = 0;
        if (payments != null) {
            for (PaymentDto p : payments) {
                if ("completed".equalsIgnoreCase(p.payment_status)) {
                    sum += parse(p.amount_paid);
                }
            }
        }
        return sum;
    }

    /** True when a self-submitted payment is still awaiting staff verification. */
    public boolean hasPendingPayment() {
        if (payments != null) {
            for (PaymentDto p : payments) {
                if ("pending".equalsIgnoreCase(p.payment_status)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Most recent payment. Selects by the highest `id` (an auto-increment
     * primary key, the only field guaranteed to be monotonically ordered)
     * rather than trusting the array's arrival order - a receipt/detail
     * screen showing the wrong payment's GCash number or reference because
     * the API happened to return the list in a different order would be a
     * silent, hard-to-notice correctness bug.
     */
    public PaymentDto latestPayment() {
        if (payments == null || payments.isEmpty()) return null;
        PaymentDto latest = payments.get(0);
        for (PaymentDto p : payments) {
            if (p.id > latest.id) latest = p;
        }
        return latest;
    }

    private double parse(String s) {
        try {
            return s != null ? Double.parseDouble(s) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
