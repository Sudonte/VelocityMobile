package com.example.velocitysuites.network.dto;

import java.util.List;

/**
 * The server's itemized stay (App\Support\StayBill) - the SAME calculation the receptionist's check-out bill is built
 * from, so a guest's receipt, Booking Details and Payment History can never show different amounts or nights than the
 * front desk billed. Present once a stay is in house or finished (billing.stay_bill / booking.stay_bill / the receipt
 * payload's stay_bill); null/absent before that. Dates are yyyy-MM-dd hotel-local calendar days; nights are calendar
 * days, minimum 1.
 */
public class StayBillDto {
    public String check_in;
    public String scheduled_check_out;
    public String actual_check_out;
    public int scheduled_nights;
    /** The nights billed: always the BOOKED nights (no refunds, no stay-length adjustment) - older settled bills may differ. */
    public int actual_nights;
    public double room_charge;
    public double amenity_charge;
    public double additional_charges_total;
    public double subtotal;
    public double discount;
    public String discount_name;
    /** The promo the desk verified: its own line, taken off the room rate of its room type only. */
    public double promo_discount;
    public String promo_name;
    public double total;
    public double total_paid;
    public double balance;
    public List<Room> rooms;
    /** Approved extra amenities the guest asked for (or the desk added) after booking - already inside {@link #total}. */
    public List<Amenity> additional_amenities;
    public double additional_amenities_total;
    /** The guest's amenity requests the desk has not approved yet (pending) or turned down (rejected) - never part of any total. */
    public List<RequestedAmenity> requested_amenities;
    /** The 3rd receipt's balance breakdown - present once the stay was settled at check-in; null for older stays. */
    public ReceiptBreakdown receipt_breakdown;

    public static class ReceiptBreakdown {
        public Checkin checkin;
        public AfterCheckin after_checkin;
        public double remaining_balance;
        public double final_grand_total;
        public double total_paid;
        public double balance_due;
    }

    /** The check-in summary, frozen when the guest paid. */
    public static class Checkin {
        public double room_charge;
        public double booked_amenities;
        public String discount_name;
        public Double discount_percent;
        public double discount;
        public double promo;
        public String promo_name;
        public double grand_total;
        public double amount_paid;
        public boolean paid_in_full;
        public String status;
    }

    /** What was added after check-in: amenity requests and the receptionist's additional charges. */
    public static class AfterCheckin {
        public List<Line> amenities;
        public double amenities_total;
        public List<Line> charges;
        public double charges_total;
        public double subtotal;
    }

    public static class Line {
        public String name;
        public int quantity;
        public double unit_price;
        public double subtotal;
    }

    public static class RequestedAmenity {
        public String amenity_name;
        /** The room it was requested for, when the server says (older servers send none). */
        public String room_number;
        public int quantity;
        public double subtotal;
        /** "pending" or "rejected". */
        public String status;
        public String reason;
    }

    public static class Amenity {
        public String amenity_name;
        /** The room it was requested for, when the server says (older servers send none). */
        public String room_number;
        public int quantity;
        public double unit_price;
        public double subtotal;
    }

    public static class Room {
        public String room_number;
        public String room_type;
        public String room_type_id;
        public double rate;
        /** The nightly rate after the promo (same as rate when none applies). */
        public Double rate_after_promo;
        public double promo_off;
        public int nights;
        public double subtotal;
        /** "active" or "checked_out". */
        public String status;
        public String checked_out_on;
    }
}
