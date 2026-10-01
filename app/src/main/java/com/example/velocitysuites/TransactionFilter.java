package com.example.velocitysuites;

import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Transaction History's search + status/type + room type + booking status + date filters, combined with AND
 * so any mix of them narrows the same list - pure logic over {@link TransactionRow} snapshots, so it is
 * JVM-unit-testable and cheap enough to run on every (debounced) keystroke.
 * <p>
 * The payment-status filters (Pending / Paid / Partially Paid / Cancelled / Rejected) are exactly the five
 * badge states of {@link TransactionStatusHelper}, so choosing "Paid" lists what shows a PAID badge - never
 * a second, separately derived notion of "paid".
 */
final class TransactionFilter {

    private TransactionFilter() {
    }

    /** What the "Filter by Status" dropdown can select. */
    enum Type {
        ALL, BOOKINGS, RESERVATIONS, PAYMENTS, PENDING, PAID, PARTIALLY_PAID, CANCELLED, REJECTED, STAYS;

        /** Maps the string keys used by deep-link extras (and by older saved state) to a Type; anything unknown is ALL. */
        static Type fromKey(@Nullable String key) {
            if (key == null) return ALL;
            switch (key) {
                case "Bookings": return BOOKINGS;
                case "Reservations": return RESERVATIONS;
                case "Payments": return PAYMENTS;
                case "Pending":
                case "PendingPayment": return PENDING;
                case "Paid":
                case "FullyPaid": return PAID;
                case "PartiallyPaid": return PARTIALLY_PAID;
                case "Cancelled": return CANCELLED;
                case "Rejected": return REJECTED;
                case "Stays": return STAYS;
                default: return ALL;
            }
        }
    }

    /** The active filters. A fresh Criteria matches everything. */
    static final class Criteria {
        Type type = Type.ALL;
        /** Raw search text; split into words, every word must match. */
        String query = "";
        /** A room type name (case-insensitive), or null for any. */
        @Nullable String roomType;
        /** A booking lifecycle status ("Confirmed", ...) - applies to paid Bookings only, as the chip always did - or null for any. */
        @Nullable String bookingStatus;
        /** Inclusive calendar-date range on the CHECK-IN date, or null bounds for open-ended. */
        @Nullable LocalDate from;
        @Nullable LocalDate to;

        boolean hasDateRange() {
            return from != null || to != null;
        }

        /** True when anything narrows the list - drives whether the empty state offers "Clear filters". */
        boolean isActive() {
            return type != Type.ALL || !query.trim().isEmpty() || roomType != null || bookingStatus != null || hasDateRange();
        }
    }

    static List<TransactionRow> apply(List<TransactionRow> rows, Criteria criteria) {
        String[] tokens = tokensOf(criteria.query);
        List<TransactionRow> out = new ArrayList<>(rows.size());
        for (TransactionRow row : rows) {
            if (matches(row, criteria, tokens)) out.add(row);
        }
        return out;
    }

    static boolean matches(TransactionRow row, Criteria c) {
        return matches(row, c, tokensOf(c.query));
    }

    private static boolean matches(TransactionRow row, Criteria c, String[] tokens) {
        return matchesType(row, c.type)
                && matchesSearch(row, tokens)
                && matchesRoomType(row, c.roomType)
                && matchesBookingStatus(row, c.bookingStatus)
                && matchesDate(row, c);
    }

    private static boolean matchesType(TransactionRow row, Type type) {
        switch (type) {
            case ALL:
                return true;
            // Reservations = not yet a booking; Bookings = has a booking (even if a payment still awaits
            // verification). Closed (cancelled/rejected) records are listed under their own filters
            // instead, as everywhere else in the app (TransactionCategorizer).
            case BOOKINGS:
                return row.kind == TransactionRow.Kind.BOOKING && !row.closed;
            case RESERVATIONS:
                return row.kind == TransactionRow.Kind.RESERVATION && !row.closed;
            case PAYMENTS:
                return row.hasAnyPayment;
            case PENDING:
                return row.summary.status == TransactionStatusHelper.Status.PENDING;
            case PAID:
                return row.summary.status == TransactionStatusHelper.Status.PAID;
            case PARTIALLY_PAID:
                return row.summary.status == TransactionStatusHelper.Status.PARTIALLY_PAID;
            case CANCELLED:
                return row.summary.status == TransactionStatusHelper.Status.CANCELLED;
            case REJECTED:
                return row.summary.status == TransactionStatusHelper.Status.REJECTED;
            case STAYS:
                return "Checked-Out".equalsIgnoreCase(row.lifecycleStatus) || "Checked-In".equalsIgnoreCase(row.lifecycleStatus);
            default:
                return true;
        }
    }

    private static boolean matchesSearch(TransactionRow row, String[] tokens) {
        for (String token : tokens) {
            if (!row.searchText.contains(token)) return false;
        }
        return true;
    }

    private static boolean matchesRoomType(TransactionRow row, @Nullable String roomType) {
        return roomType == null || row.roomTypesLower.contains(roomType.trim().toLowerCase(Locale.US));
    }

    private static boolean matchesBookingStatus(TransactionRow row, @Nullable String bookingStatus) {
        return bookingStatus == null
                || (row.kind == TransactionRow.Kind.BOOKING && bookingStatus.equalsIgnoreCase(row.lifecycleStatus));
    }

    /**
     * Calendar-date comparison, inclusive at both ends. (The previous version compared the check-in's
     * local-midnight instant with the date picker's UTC-midnight instants, which in Manila's UTC+8 left the
     * FIRST day of any chosen range out: Oct 1 at 00:00 +08:00 is earlier than Oct 1 at 00:00 UTC.)
     */
    private static boolean matchesDate(TransactionRow row, Criteria c) {
        if (!c.hasDateRange()) return true;
        LocalDate checkIn = row.checkInDate;
        if (checkIn == null) return false;
        if (c.from != null && checkIn.isBefore(c.from)) return false;
        return c.to == null || !checkIn.isAfter(c.to);
    }

    /** Lower-cased, whitespace-split search words. Empty for a blank query. */
    static String[] tokensOf(@Nullable String query) {
        if (query == null) return new String[0];
        String trimmed = query.trim().toLowerCase(Locale.US);
        return trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");
    }
}
