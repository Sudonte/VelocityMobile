package com.example.velocitysuites;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * What the Dashboard's "Pending & Confirmed Stays" lists: every booking and reservation whose status is Pending or Confirmed,
 * soonest check-in first (an unreadable date sorts last). Collapsed it shows {@link #COLLAPSED_LIMIT} of them, expanded all.
 */
final class RecentUpcomingStays {

    static final int COLLAPSED_LIMIT = 5;

    private RecentUpcomingStays() {
    }

    static boolean qualifies(@Nullable Booking b) {
        if (b == null) return false;
        String status = b.getStatus();
        return "Pending".equalsIgnoreCase(status) || "Confirmed".equalsIgnoreCase(status);
    }

    /** All the guest's Pending / Confirmed transactions, soonest check-in first. */
    @NonNull
    static List<Booking> select(@NonNull List<Booking> bookings) {
        List<Booking> out = new ArrayList<>();
        for (Booking b : bookings) {
            if (qualifies(b)) out.add(b);
        }
        Collections.sort(out, BY_CHECK_IN);
        return out;
    }

    /** The rows to draw: the first {@link #COLLAPSED_LIMIT}, or all of them when expanded. */
    @NonNull
    static List<Booking> visible(@NonNull List<Booking> all, boolean expanded) {
        return expanded || all.size() <= COLLAPSED_LIMIT ? all : all.subList(0, COLLAPSED_LIMIT);
    }

    private static final Comparator<Booking> BY_CHECK_IN = (x, y) -> {
        LocalDate a = PaymentDates.parseDate(x.getCheckInDate());
        LocalDate b = PaymentDates.parseDate(y.getCheckInDate());
        if (a == null || b == null) return a == null ? (b == null ? 0 : 1) : -1;
        return a.compareTo(b);
    };
}
