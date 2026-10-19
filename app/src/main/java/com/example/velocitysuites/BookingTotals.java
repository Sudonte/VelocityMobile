package com.example.velocitysuites;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * THE total of a booking or reservation, for every screen: the amount the guest actually owes in all (after discounts, including
 * approved amenities) - {@link TransactionStatusHelper#grandTotalOf}, the same figure Payment Status, Your Next Stay and the
 * Booking Details summary use - so Total = Paid + Balance everywhere. The raw stored total ({@link Booking#getTotalAmount()}) is
 * the price before any discount and is never shown as "the total" on its own.
 */
final class BookingTotals {

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
}
