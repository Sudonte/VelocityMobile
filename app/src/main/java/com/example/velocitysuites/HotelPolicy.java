package com.example.velocitysuites;

import android.content.Context;

import androidx.annotation.NonNull;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The hotel-wide facts, in ONE place: the posted check-in and check-out times and the refund policy. The server does not
 * send them, so the app states them here and every screen that shows them (Booking Details, Upcoming Transactions, the
 * Stay Calendar, Room Details, the room cards, the landing page's hours, the hotel policies list) reads them from here.
 * No other file may hardcode them (HotelPolicyTest scans the sources and the strings for that).
 */
public final class HotelPolicy {

    /** Posted check-in time: guests may arrive from this time. */
    public static final LocalTime CHECK_IN_TIME = LocalTime.of(14, 0, 0);
    /** Posted check-out time: guests leave by this time. */
    public static final LocalTime CHECK_OUT_TIME = LocalTime.of(12, 0, 0);
    /** Payments are not refundable (including on cancellation or early check-out). */
    public static final boolean NON_REFUNDABLE = true;

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private HotelPolicy() {
    }

    /** "2:00 PM". */
    @NonNull
    public static String checkInText() {
        return CHECK_IN_TIME.format(CLOCK);
    }

    /** "12:00 PM". */
    @NonNull
    public static String checkOutText() {
        return CHECK_OUT_TIME.format(CLOCK);
    }

    /** "From 2:00 PM". */
    @NonNull
    public static String checkInFrom(@NonNull Context c) {
        return c.getString(R.string.room_policy_from_format, checkInText());
    }

    /** "Until 12:00 PM". */
    @NonNull
    public static String checkOutUntil(@NonNull Context c) {
        return c.getString(R.string.room_policy_until_format, checkOutText());
    }

    /** "Non-refundable" (or "Refundable" if the policy ever changes). */
    @NonNull
    public static String refundValue(@NonNull Context c) {
        return c.getString(NON_REFUNDABLE ? R.string.room_policy_refund_value : R.string.room_policy_refund_value_refundable);
    }

    /** The small chip on room cards and Room Details: "No refund". Only shown when {@link #NON_REFUNDABLE}. */
    @NonNull
    public static String refundChip(@NonNull Context c) {
        return c.getString(R.string.no_refund_badge);
    }

    /** The landing page's hours block. */
    @NonNull
    public static String hoursBlock(@NonNull Context c) {
        return c.getString(R.string.landing_hotel_hours, checkInText(), checkOutText());
    }

    /** The full hotel policies list, with the posted times filled in and the refund line only while payments are non-refundable. */
    @NonNull
    public static String policiesText(@NonNull Context c) {
        String body = c.getString(R.string.hotel_policies_content, checkInText(), checkOutText());
        return NON_REFUNDABLE ? body + "\n" + c.getString(R.string.hotel_policies_refund_line) : body;
    }
}
