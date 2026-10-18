package com.example.velocitysuites;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * What the Dashboard's Payment Status section lists: EVERY active transaction - unpaid, partly paid or fully paid - and
 * nothing that is over (Checked Out, Cancelled, Rejected). Amounts owed come first (unpaid, then partly paid), then fully
 * paid; within each group the nearest check-in first. Pure rules, so they are unit-testable.
 */
final class PaymentStatusList {

    private PaymentStatusList() {
    }

    /** True while the transaction is still in play: not checked out / completed, not cancelled or rejected. */
    static boolean isActive(@Nullable Booking b) {
        if (b == null) return false;
        if (TransactionCategorizer.categorize(b) == TransactionCategorizer.Category.CANCELLED) return false;
        String status = b.getStatus();
        return !("Checked-Out".equalsIgnoreCase(status) || "Completed".equalsIgnoreCase(status));
    }

    static PaymentBalanceTone toneOf(@NonNull Booking b) {
        return PaymentBalanceTone.of(TransactionStatusHelper.grandTotalOf(b), TransactionStatusHelper.verifiedPaidOf(b));
    }

    /** 0 = unpaid, 1 = partly paid, 2 = fully paid. */
    private static int rank(Booking b) {
        PaymentBalanceTone tone = toneOf(b);
        return tone == PaymentBalanceTone.OWED ? 0 : tone == PaymentBalanceTone.PARTIAL ? 1 : 2;
    }

    @NonNull
    static List<Booking> select(@Nullable List<Booking> bookings) {
        List<Booking> out = new ArrayList<>();
        if (bookings != null) {
            for (Booking b : bookings) if (isActive(b)) out.add(b);
        }
        Collections.sort(out, Comparator.<Booking>comparingInt(PaymentStatusList::rank)
                .thenComparing((x, y) -> {
                    LocalDate a = PaymentDates.parseDate(x.getCheckInDate());
                    LocalDate c = PaymentDates.parseDate(y.getCheckInDate());
                    if (a == null || c == null) return a == null ? (c == null ? 0 : 1) : -1;
                    return a.compareTo(c);
                }));
        return out;
    }
}
