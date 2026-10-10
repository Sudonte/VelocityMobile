package com.example.velocitysuites;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Every payment the guest has made across all their bookings and reservations, newest first, with the method / date
 * filters and the totals shown above the list. Pure data (no Android views), so the rules are unit-testable.
 */
public final class PaymentHistoryModel {

    public enum Method { ALL, GCASH, CASH }

    public enum Range { ALL_TIME, LAST_30_DAYS, THIS_MONTH }

    /** One payment, with the transaction it belongs to. */
    public static final class Entry {
        public final String transactionId;
        public final boolean direct;
        public final Booking.PaymentRecord record;
        public final long millis;
        public final double amount;

        Entry(String transactionId, boolean direct, Booking.PaymentRecord record, long millis, double amount) {
            this.transactionId = transactionId;
            this.direct = direct;
            this.record = record;
            this.millis = millis;
            this.amount = amount;
        }

        public boolean isCash() {
            return record.method != null && "CASH".equalsIgnoreCase(record.method.trim());
        }

        public boolean isGcash() {
            return record.method != null && "GCASH".equalsIgnoreCase(record.method.trim());
        }

        /** Verified money: the payment was completed. */
        public boolean isCompleted() {
            return "completed".equalsIgnoreCase(record.status == null ? "" : record.status.trim());
        }

        /** Submitted but not yet verified by the front desk. */
        public boolean isPending() {
            return "pending".equalsIgnoreCase(record.status == null ? "" : record.status.trim());
        }
    }

    /** The totals shown above the list, over the payments currently listed. */
    public static final class Totals {
        public final double paid;
        public final double pending;
        public final int count;

        Totals(double paid, double pending, int count) {
            this.paid = paid;
            this.pending = pending;
            this.count = count;
        }
    }

    private PaymentHistoryModel() { }

    /** Every payment of every transaction (hidden ones included - they are the guest's proof), newest first. */
    public static List<Entry> collect(@Nullable List<Booking> bookings) {
        List<Entry> out = new ArrayList<>();
        if (bookings != null) {
            for (Booking b : bookings) {
                if (b == null || b.getPaymentHistory() == null) continue;
                for (Booking.PaymentRecord record : b.getPaymentHistory()) {
                    if (record == null) continue;
                    out.add(new Entry(String.valueOf(b.getId()), b.isDirectBooking(), record,
                            PaymentDates.parseMillis(record.date), MoneyFormat.parse(record.amount)));
                }
            }
        }
        Collections.sort(out, (a, b) -> Long.compare(b.millis, a.millis));
        return out;
    }

    public static List<Entry> filter(@NonNull List<Entry> all, @NonNull Method method, @NonNull Range range,
                                     long nowMillis, @NonNull ZoneId zone) {
        long from = fromMillis(range, nowMillis, zone);
        List<Entry> out = new ArrayList<>();
        for (Entry e : all) {
            if (method == Method.CASH && !e.isCash()) continue;
            if (method == Method.GCASH && !e.isGcash()) continue;
            // A payment with no readable date can't be placed in a date range, so only "All time" lists it.
            if (range != Range.ALL_TIME && (e.millis <= 0 || e.millis < from)) continue;
            out.add(e);
        }
        return out;
    }

    public static Totals totals(@NonNull List<Entry> entries) {
        double paid = 0;
        double pending = 0;
        for (Entry e : entries) {
            if (e.isCompleted()) paid += e.amount;
            else if (e.isPending()) pending += e.amount;
        }
        return new Totals(Math.round(paid * 100) / 100.0, Math.round(pending * 100) / 100.0, entries.size());
    }

    private static long fromMillis(Range range, long nowMillis, ZoneId zone) {
        LocalDate today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate();
        switch (range) {
            case LAST_30_DAYS:
                return today.minusDays(29).atStartOfDay(zone).toInstant().toEpochMilli();
            case THIS_MONTH:
                return today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli();
            default:
                return Long.MIN_VALUE;
        }
    }

    /** "Completed" / "Pending" ... for the status chip. */
    public static String statusText(@Nullable String status) {
        if (status == null || status.trim().isEmpty()) return "";
        String s = status.trim();
        return s.substring(0, 1).toUpperCase(Locale.US) + s.substring(1).toLowerCase(Locale.US);
    }
}
