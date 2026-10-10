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
    public int actual_nights;
    public int extra_nights;
    public double room_charge;
    public double extra_nights_charge;
    public double amenity_charge;
    public double additional_charges_total;
    public double subtotal;
    public double discount;
    public String discount_name;
    public double total;
    public double total_paid;
    public double balance;
    public List<Room> rooms;

    public static class Room {
        public String room_number;
        public String room_type;
        public String room_type_id;
        public double rate;
        public int nights;
        public int extra_nights;
        public double subtotal;
        /** "active" or "checked_out". */
        public String status;
        public String checked_out_on;
    }
}
