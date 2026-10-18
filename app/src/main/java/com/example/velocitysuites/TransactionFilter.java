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
        ALL, BOOKINGS, RESERVATIONS, PAYMENTS, PENDING, PAID, PARTIALLY_PAID, CANCELLED, REJECTED, STAYS, CHECKED_IN, CHECKED_OUT;

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
        /** The Transaction dropdown: a kind (bookings, reservations, payments) or a payment / lifecycle state. */
        Type type = Type.ALL;
        /** Raw search text; split into words, every word must match. */
        String query = "";
        /** A room type name (case-insensitive), or null for any. */
        @Nullable String roomType;
        /** A booking / reservation lifecycle status ("Checked-In", "Cancelled", ...), matched case-insensitively, or null for any. */
        @Nullable String bookingStatus;
        /** Stays checking in ON OR AFTER this date (null: any). */
        @Nullable LocalDate checkInFrom;
        /** Stays checking out ON OR BEFORE this date (null: any). */
        @Nullable LocalDate checkOutTo;

        /**
         * Sets the check-in date filter; refuses (false, nothing changed) a date AFTER the check-out date already chosen,
         * so the window can never be empty by construction. Null clears it.
         */
        boolean setCheckIn(@Nullable LocalDate date) {
            if (date != null && checkOutTo != null && date.isAfter(checkOutTo)) return false;
            checkInFrom = date;
            return true;
        }

        /** Sets the check-out date filter; refuses a date BEFORE the check-in date already chosen. Null clears it. */
        boolean setCheckOut(@Nullable LocalDate date) {
            if (date != null && checkInFrom != null && date.isBefore(checkInFrom)) return false;
            checkOutTo = date;
            return true;
        }

        boolean hasDateRange() {
            return checkInFrom != null || checkOutTo != null;
        }

        /** True when anything narrows the list - drives whether the empty state offers "Clear filters". */
        boolean isActive() {
            return !query.trim().isEmpty() || activeFilterCount() > 0;
        }

        /** How many dropdown / date filters are set (not the search) - the "Show filters (N active)" count. */
        int activeFilterCount() {
            return (type != Type.ALL ? 1 : 0) + (bookingStatus != null ? 1 : 0) + (roomType != null ? 1 : 0)
                    + (checkInFrom != null ? 1 : 0) + (checkOutTo != null ? 1 : 0);
        }

        /** Back to "everything": no search, no filter. */
        void clear() {
            type = Type.ALL;
            query = "";
            roomType = null;
            bookingStatus = null;
            checkInFrom = null;
            checkOutTo = null;
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
            case CHECKED_IN:
                return "Checked-In".equalsIgnoreCase(row.lifecycleStatus);
            case CHECKED_OUT:
                return isCheckedOut(row.lifecycleStatus);
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
        if (bookingStatus == null) return true;
        if ("Checked-Out".equalsIgnoreCase(bookingStatus)) return isCheckedOut(row.lifecycleStatus);
        return bookingStatus.equalsIgnoreCase(row.lifecycleStatus);
    }

    private static boolean isCheckedOut(@Nullable String lifecycle) {
        return "Checked-Out".equalsIgnoreCase(lifecycle) || "Completed".equalsIgnoreCase(lifecycle);
    }

    /**
     * The two date fields, as plain calendar dates (never instants, so no time zone can shift a day): a check-in date
     * keeps stays that check in ON OR AFTER it, a check-out date keeps stays that check out ON OR BEFORE it, both keep
     * the stays inside that window. A stay whose check-in can't be read never matches a date filter; one whose check-out
     * can't be read counts as the check-in day only.
     */
    private static boolean matchesDate(TransactionRow row, Criteria c) {
        if (!c.hasDateRange()) return true;
        LocalDate in = row.checkInDate;
        if (in == null) return false;
        LocalDate out = row.checkOutDate != null && !row.checkOutDate.isBefore(in) ? row.checkOutDate : in;
        if (c.checkInFrom != null && in.isBefore(c.checkInFrom)) return false;
        return c.checkOutTo == null || !out.isAfter(c.checkOutTo);
    }

    /** Lower-cased, whitespace-split search words. Empty for a blank query. */
    static String[] tokensOf(@Nullable String query) {
        if (query == null) return new String[0];
        String trimmed = query.trim().toLowerCase(Locale.US);
        return trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");
    }
}
