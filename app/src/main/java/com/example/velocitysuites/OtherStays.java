package com.example.velocitysuites;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What "Other Recent and Upcoming Stays" lists: every non-cancelled transaction that Your Next Stay is NOT showing for
 * the selected day - upcoming / in-house ones soonest first, finished ones latest first. Because it is "everything
 * minus what the selected day shows", no transaction can be hidden from both sections at once. Pure data and rules, so
 * it is unit-testable.
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
    static OtherStays build(@Nullable List<Booking> all, @NonNull Collection<Booking> shownInNextStay) {
        Set<String> shown = new HashSet<>();
        for (Booking b : shownInNextStay) shown.add(StayCalendarModel.keyOf(b));
        List<Booking> upcoming = new ArrayList<>();
        List<Booking> recent = new ArrayList<>();
        if (all != null) {
            for (Booking b : all) {
                if (b == null || shown.contains(StayCalendarModel.keyOf(b))) continue;
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
