package com.example.velocitysuites;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What "Other Recent and Upcoming Stays" lists: every non-cancelled transaction - upcoming / in-house ones soonest first,
 * finished ones latest first. The list is INDEPENDENT of Your Next Stay and the Stay Calendar: whatever day, week, month or
 * filter is selected there, this section does not change (a stay may appear in both). Pure data and rules, so it is
 * unit-testable.
 */
final class OtherStays {

    final List<Booking> upcoming;
    final List<Booking> recent;

    private OtherStays(List<Booking> upcoming, List<Booking> recent) {
        this.upcoming = upcoming;
        this.recent = recent;
    }

    int size() {
        return upcoming.size() + recent.size();
    }

    @NonNull
    static OtherStays build(@Nullable List<Booking> all) {
        List<Booking> upcoming = new ArrayList<>();
        List<Booking> recent = new ArrayList<>();
        if (all != null) {
            for (Booking b : all) {
                if (b == null) continue;
                TransactionCategorizer.Category category = TransactionCategorizer.categorize(b);
                if (category == TransactionCategorizer.Category.UPCOMING || category == TransactionCategorizer.Category.ACTIVE) upcoming.add(b);
                else if (category == TransactionCategorizer.Category.COMPLETED) recent.add(b);
            }
        }
        java.util.Comparator<Booking> byCheckIn = (x, y) -> {
            LocalDate a = PaymentDates.parseDate(x.getCheckInDate());
            LocalDate c = PaymentDates.parseDate(y.getCheckInDate());
            if (a == null || c == null) return a == null ? (c == null ? 0 : 1) : -1;
            return a.compareTo(c);
        };
        Collections.sort(upcoming, byCheckIn);
        Collections.sort(recent, byCheckIn.reversed());
        return new OtherStays(upcoming, recent);
    }
}
