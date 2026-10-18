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

    /** The payment-status filter a legacy type key stands for (Paid, Pending, Partially paid), or ALL. */
    static Type paymentStatusOf(Type type) {
        return type == Type.PENDING || type == Type.PAID || type == Type.PARTIALLY_PAID ? type : Type.ALL;
    }

    /** The booking-status filter a legacy type key stands for ("Cancelled", "Rejected"), or null. */
    @Nullable
    static String bookingStatusOf(Type type) {
        if (type == Type.CANCELLED) return "Cancelled";
        if (type == Type.REJECTED) return "Rejected";
        return null;
    }

    /** The active filters. A fresh Criteria matches everything. */
    static final class Criteria {
        Type type = Type.ALL;
        /** Raw search text; split into words, every word must match. */
        String query = "";
        /** A room type name (case-insensitive), or null for any. */
        @Nullable String roomType;
        /** A booking / reservation lifecycle status ("Checked-In", "Cancelled", ...), matched case-insensitively, or null for any. */
        @Nullable String bookingStatus;
        /** Payment status on its own (PENDING, PAID or PARTIALLY_PAID; anything else means "any"), combined with {@link #type} by AND. */
        Type paymentStatus = Type.ALL;
        /** Inclusive calendar-date range; a stay matches when it OVERLAPS it (null bounds are open-ended). */
        @Nullable LocalDate from;
        @Nullable LocalDate to;

        boolean hasDateRange() {
            return from != null || to != null;
        }

        /** True when anything narrows the list - drives whether the empty state offers "Clear filters". */
        boolean isActive() {
            return type != Type.ALL || !query.trim().isEmpty() || activeFilterCount() > 0;
        }

        /** How many of the Filters-sheet filters are set (payment status, booking status, room type, dates) - the "Filters · 2" badge. */
        int activeFilterCount() {
            return (paymentStatus != Type.ALL ? 1 : 0) + (bookingStatus != null ? 1 : 0) + (roomType != null ? 1 : 0) + (hasDateRange() ? 1 : 0);
        }

        /** Back to "everything": no search, no filter. */
        void clear() {
            type = Type.ALL;
            query = "";
            roomType = null;
            bookingStatus = null;
            paymentStatus = Type.ALL;
            from = null;
            to = null;
        }

        Criteria copy() {
            Criteria c = new Criteria();
            c.type = type;
            c.query = query;
            c.roomType = roomType;
            c.bookingStatus = bookingStatus;
            c.paymentStatus = paymentStatus;
            c.from = from;
            c.to = to;
            return c;
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
                && matchesPaymentStatus(row, c.paymentStatus)
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

    private static boolean matchesPaymentStatus(TransactionRow row, Type status) {
        switch (status) {
            case PENDING:
            case PAID:
            case PARTIALLY_PAID:
                return matchesType(row, status);
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
        return bookingStatus == null || bookingStatus.equalsIgnoreCase(row.lifecycleStatus);
    }

    /**
     * Calendar-date overlap, inclusive at both ends: the stay (check-in to check-out) matches when it shares at least one
     * day with the chosen range. A stay whose check-out can't be read counts as the check-in day only; one whose check-in
     * can't be read never matches a range. (Dates are plain calendar dates, never instants, so no time zone can shift a
     * day - the date picker's UTC-midnight values are turned into dates before they get here.)
     */
    private static boolean matchesDate(TransactionRow row, Criteria c) {
        if (!c.hasDateRange()) return true;
        LocalDate in = row.checkInDate;
        if (in == null) return false;
        LocalDate out = row.checkOutDate != null && !row.checkOutDate.isBefore(in) ? row.checkOutDate : in;
        if (c.from != null && out.isBefore(c.from)) return false;
        return c.to == null || !in.isAfter(c.to);
    }

    /** Lower-cased, whitespace-split search words. Empty for a blank query. */
    static String[] tokensOf(@Nullable String query) {
        if (query == null) return new String[0];
        String trimmed = query.trim().toLowerCase(Locale.US);
        return trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");
    }
}
