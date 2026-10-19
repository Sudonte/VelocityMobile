package com.example.velocitysuites;

import androidx.annotation.Nullable;

import com.example.velocitysuites.network.dto.StayBillDto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The 3rd receipt's balance breakdown, exactly as the server (App\Support\StayBill#receiptBreakdown) printed it for the
 * receptionist's web view: the check-in summary (paid in full, discount taken ONCE), what was added since (amenities and
 * the desk's additional charges), the remaining balance and the final grand total. Nothing is computed on the phone, so
 * both sides always show the same figures; {@link #addsUp()} only lets tests prove the numbers are consistent.
 */
public final class BalanceBreakdown implements Serializable {

    /** One itemized charge: name, quantity, unit price, subtotal. */
    public static final class Item implements Serializable {
        public final String name;
        public final int quantity;
        public final double unitPrice;
        public final double subtotal;

        Item(String name, int quantity, double unitPrice, double subtotal) {
            this.name = name != null ? name : "";
            this.quantity = Math.max(1, quantity);
            this.unitPrice = unitPrice;
            this.subtotal = subtotal;
        }
    }

    public final double roomCharge;
    public final double bookedAmenities;
    @Nullable public final String discountName;
    @Nullable public final Double discountPercent;
    public final double discount;
    public final double checkinGrandTotal;
    public final double amountPaidAtCheckin;
    public final boolean paidInFull;
    public final List<Item> amenities;
    public final double amenitiesTotal;
    public final List<Item> charges;
    public final double chargesTotal;
    public final double stayAdjustment;
    public final double additionalSubtotal;
    public final double remainingBalance;
    public final double finalGrandTotal;
    public final double totalPaid;
    public final double balanceDue;

    private BalanceBreakdown(StayBillDto.ReceiptBreakdown d) {
        StayBillDto.Checkin c = d.checkin;
        StayBillDto.AfterCheckin a = d.after_checkin;
        this.roomCharge = c.room_charge;
        this.bookedAmenities = c.booked_amenities;
        this.discountName = c.discount_name;
        this.discountPercent = c.discount_percent;
        this.discount = c.discount;
        this.checkinGrandTotal = c.grand_total;
        this.amountPaidAtCheckin = c.amount_paid;
        this.paidInFull = c.paid_in_full;
        this.amenities = items(a.amenities);
        this.amenitiesTotal = a.amenities_total;
        this.charges = items(a.charges);
        this.chargesTotal = a.charges_total;
        this.stayAdjustment = a.stay_adjustment;
        this.additionalSubtotal = a.subtotal;
        this.remainingBalance = d.remaining_balance;
        this.finalGrandTotal = d.final_grand_total;
        this.totalPaid = d.total_paid;
        this.balanceDue = d.balance_due;
    }

    /** Null in, null out - a stay that was not settled at check-in has no such breakdown (older receipts keep their layout). */
    @Nullable
    public static BalanceBreakdown from(@Nullable StayBillDto.ReceiptBreakdown dto) {
        if (dto == null || dto.checkin == null || dto.after_checkin == null) return null;
        return new BalanceBreakdown(dto);
    }

    public boolean hasAdditionalCharges() {
        return !amenities.isEmpty() || !charges.isEmpty() || Math.abs(stayAdjustment) >= 0.005;
    }

    /** Check-in grand total + what was added since == final grand total, and the check-in lines sum to its grand total. */
    public boolean addsUp() {
        double checkin = Math.round((roomCharge + bookedAmenities - discount) * 100.0) / 100.0;
        double total = Math.round((checkinGrandTotal + additionalSubtotal) * 100.0) / 100.0;
        return Math.abs(checkin - checkinGrandTotal) < 0.005 && Math.abs(total - finalGrandTotal) < 0.005;
    }

    static Item itemOf(String name, int quantity, double unitPrice, double subtotal) {
        return new Item(name, quantity, unitPrice, subtotal);
    }

    private static List<Item> items(@Nullable List<StayBillDto.Line> lines) {
        List<Item> out = new ArrayList<>();
        if (lines != null) {
            for (StayBillDto.Line l : lines) {
                if (l != null) out.add(new Item(l.name, l.quantity, l.unit_price, l.subtotal));
            }
        }
        return Collections.unmodifiableList(out);
    }
}
