package com.example.velocitysuites;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * THE total of a booking or reservation, for every screen: the amount the guest actually owes in all (after discounts, including
 * approved amenities) - {@link TransactionStatusHelper#grandTotalOf}, the same figure Payment Status, Your Next Stay and the
 * Booking Details summary use - so Total = Paid + Balance everywhere. The raw stored total ({@link Booking#getTotalAmount()}) is
 * the price before any discount and is never shown as "the total" on its own.
 *
 * <p><b>Rooms are priced stay by stay</b>: every room line costs its price x quantity x ITS OWN stay's nights, never the overall span of
 * the transaction (two one-night stays on Oct 26 and Oct 27 span two nights, but each room is paid for one). This matches the server's
 * Booking::roomTotalByStays(), so Room charges + Amenities - Discount equals the Grand total on every screen.
 *
 * <p><b>The discount</b> is a subtraction only once the front desk has checked the ID and applied it; before that it is an expectation
 * ({@link #expectedDiscount}) shown as a note, and the Grand total stays whole.
 */
public final class BookingTotals {

    private BookingTotals() {
    }

    /** The total of one transaction; for a client-side group of sibling records ({@code groupMembers}) the sum over the group. */
    static double totalOf(@NonNull Booking b, @Nullable List<Booking> groupMembers) {
        if (groupMembers != null && !groupMembers.isEmpty() && b.getRooms().isEmpty()) {
            double sum = 0;
            for (Booking member : groupMembers) sum += TransactionStatusHelper.grandTotalOf(member);
            return sum;
        }
        return TransactionStatusHelper.grandTotalOf(b);
    }

    // ---- rooms, stay by stay ----

    /** The nights of one stay: the server's count, else the days between its dates, never less than one. */
    public static long nightsOf(@NonNull Booking.StayRecord stay) {
        if (stay.nights > 0) return stay.nights;
        Long n = StayDateCalculator.nightsBetweenOrNull(stay.checkIn, stay.checkOut);
        return n != null && n > 0 ? n : 1;
    }

    /** One room line on its own stay's nights: price per night x quantity x nights. */
    public static double lineTotal(@NonNull BookingRoom room, @NonNull Booking.StayRecord stay) {
        return round(room.getPricePerNight() * Math.max(1, room.getQuantity()) * nightsOf(stay));
    }

    /** One stay's room charges. */
    public static double stayRoomCharges(@NonNull Booking.StayRecord stay) {
        double sum = 0;
        for (BookingRoom room : stay.rooms) if (room != null) sum += lineTotal(room, stay);
        return round(sum);
    }

    /** Room charges of the whole transaction: every stay on its own nights; without stays, the server's room lines as sent. */
    public static double roomCharges(@NonNull Booking b) {
        if (!b.getStays().isEmpty()) {
            double sum = 0;
            for (Booking.StayRecord stay : b.getStays()) if (stay != null) sum += stayRoomCharges(stay);
            return round(sum);
        }
        double sum = 0;
        for (BookingRoom room : b.getRooms()) if (room != null) sum += room.getSubtotal();
        if (sum > 0.009) return round(sum);
        return round(Math.max(0, b.getTotalAmount() - b.getAmenityCharge()));
    }

    /** Booked amenities: the itemized rows, else the single amenity charge. */
    public static double amenities(@NonNull Booking b) {
        // amenities live in their date range when the server sends ranges: those are what the bill adds up
        double inRanges = 0;
        for (Booking.StayRecord stay : b.getStays()) {
            if (stay == null) continue;
            for (BookingAmenity a : stay.amenities) if (a != null) inRanges += a.getSubtotal();
        }
        if (inRanges > 0.009) return round(inRanges);
        if (!b.getAmenities().isEmpty()) {
            double sum = 0;
            for (BookingAmenity a : b.getAmenities()) if (a != null) sum += a.getSubtotal();
            return round(sum);
        }
        return round(Math.max(0, b.getAmenityCharge()));
    }

    /** The number of distinct room types (Deluxe, Executive = 2). */
    public static int roomTypeCount(@NonNull Booking b) {
        java.util.LinkedHashSet<String> types = new java.util.LinkedHashSet<>();
        for (BookingRoom room : roomsOf(b)) if (room != null && room.getRoomTypeName() != null) types.add(room.getRoomTypeName().trim().toLowerCase(java.util.Locale.ROOT));
        return types.isEmpty() ? 1 : types.size();
    }

    /** The number of rooms (Deluxe x2 + Executive x2 = 4). */
    public static int roomCount(@NonNull Booking b) {
        int count = 0;
        for (BookingRoom room : roomsOf(b)) if (room != null) count += Math.max(1, room.getQuantity());
        return count > 0 ? count : Math.max(1, b.getRoomsRequested());
    }

    /** "Deluxe x2, Executive x2": every type once with its total count across the stays. */
    public static Map<String, Integer> roomCountsByType(@NonNull Booking b) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (BookingRoom room : roomsOf(b)) {
            if (room == null || room.getRoomTypeName() == null || room.getRoomTypeName().trim().isEmpty()) continue;
            counts.merge(room.getRoomTypeName().trim(), Math.max(1, room.getQuantity()), Integer::sum);
        }
        return counts;
    }

    private static List<BookingRoom> roomsOf(Booking b) {
        if (b.getStays().isEmpty()) return b.getRooms();
        List<BookingRoom> all = new java.util.ArrayList<>();
        for (Booking.StayRecord stay : b.getStays()) if (stay != null) all.addAll(stay.rooms);
        return all;
    }

    // ---- the discount ----

    /** The discount name for a label: "PWD 20%" when the amount is a plain share of the bill, else just the ID type. */
    @NonNull
    public static String discountLabel(@NonNull Booking b) {
        String type = b.getIdCardType() == null ? "" : b.getIdCardType().trim();
        return type.isEmpty() || "none".equalsIgnoreCase(type) ? "" : type;
    }

    /** True once the front desk checked the ID and the discount is part of the bill. */
    public static boolean discountApplied(@NonNull Booking b) {
        return "approved".equalsIgnoreCase(b.getDiscountVerificationStatus());
    }

    /** The discount taken off the bill - only once it was applied. */
    public static double discountAppliedAmount(@NonNull Booking b) {
        if (!discountApplied(b)) return 0;
        Booking.PaymentSummary s = b.getPaymentSummary();
        if (s != null && s.discount > 0.009) return round(s.discount);
        return round(Math.max(0, b.getDiscountAmount()));
    }

    /** What the guest can expect off once the ID is checked: the preview amount while the ID is still waiting. */
    public static double expectedDiscount(@NonNull Booking b) {
        if (discountApplied(b)) return 0;
        String status = b.getDiscountVerificationStatus();
        if (!"pending".equalsIgnoreCase(status)) return 0;
        return round(Math.max(0, b.getDiscountAmount()));
    }

    /** The Grand total shown: the server's total (already after an applied discount, whole before it). */
    public static double grandTotal(@NonNull Booking b) {
        return TransactionStatusHelper.grandTotalOf(b);
    }

    /** The total the guest could expect once the discount is applied (only while one is still expected). */
    public static double estimatedAfterDiscount(@NonNull Booking b) {
        return round(Math.max(0, grandTotal(b) - expectedDiscount(b)));
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
