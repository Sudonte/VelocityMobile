package com.example.velocitysuites;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * The dates a rooms screen opens with: check-in is the earliest day a guest may book (today + 2 days on the hotel's clock, Manila) and
 * check-out the day after. They are pre-filled, availability loads for them at once, and they count as chosen - the guest can book
 * without touching them, or change them. Also the UTC-midnight conversions the date pickers and the saved state use.
 */
public final class StayDates {

    public static final String EXTRA_CHECK_IN_UTC = "CHECK_IN_UTC";
    public static final String EXTRA_CHECK_OUT_UTC = "CHECK_OUT_UTC";

    private StayDates() {
    }

    /** Today + 2 days in Manila, at the device's local midnight. */
    @NonNull
    public static Calendar defaultCheckIn() {
        return CheckInWindow.toDeviceMidnight(CheckInWindow.earliest());
    }

    /** The day after {@code checkIn}. */
    @NonNull
    public static Calendar defaultCheckOut(@NonNull Calendar checkIn) {
        Calendar out = (Calendar) checkIn.clone();
        out.add(Calendar.DAY_OF_MONTH, 1);
        return out;
    }

    /** Both set, check-out after check-in, and the check-in still inside the booking window. */
    public static boolean valid(@Nullable Calendar checkIn, @Nullable Calendar checkOut) {
        return checkIn != null && checkOut != null && checkOut.after(checkIn) && CheckInWindow.isAllowed(checkIn);
    }

    public static long toUtcMidnight(@NonNull Calendar local) {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH));
        return utc.getTimeInMillis();
    }

    @NonNull
    public static Calendar fromUtcMidnight(long utcMillis) {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.setTimeInMillis(utcMillis);
        Calendar local = Calendar.getInstance();
        local.clear();
        local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH));
        return local;
    }

    public static boolean sameDay(@Nullable Calendar a, @Nullable Calendar b) {
        return a != null && b != null && a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }
}
