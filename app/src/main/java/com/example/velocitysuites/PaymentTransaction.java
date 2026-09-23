package com.example.velocitysuites;

/**
 * One row in the Transaction History list - a single payment event, paired
 * with the Booking/Reservation it belongs to. Built client-side by
 * flattening every Booking's paymentHistory (List&lt;Booking.PaymentRecord&gt;)
 * across the guest's whole allBookings list (TransactionHistoryActivity),
 * since the backend has no flat payment-ledger endpoint today. Every
 * existing Booking-level filter predicate in applyFilters() still applies
 * unchanged by reading through {@link #parentBooking} - only the list
 * *rendering* is payment-level, not the filtering logic itself.
 */
public class PaymentTransaction implements java.io.Serializable {

    public final Booking parentBooking;
    /** Null when this row is a synthetic summary (no itemized PaymentRecord exists yet) built straight from the parent Booking's own current-snapshot fields. */
    public final Booking.PaymentRecord record;

    public PaymentTransaction(Booking parentBooking, Booking.PaymentRecord record) {
        this.parentBooking = parentBooking;
        this.record = record;
    }

    public String getAmountRaw() {
        if (record != null && record.amount != null) return record.amount;
        return String.valueOf(parentBooking.getAmountPaid());
    }

    public double getAmount() {
        try {
            return record != null && record.amount != null
                    ? Double.parseDouble(record.amount)
                    : parentBooking.getAmountPaid();
        } catch (NumberFormatException e) {
            return parentBooking.getAmountPaid();
        }
    }

    public String getMethod() {
        if (record != null && record.method != null) return record.method;
        return parentBooking.getPaymentMethod();
    }

    public String getDate() {
        if (record != null && record.date != null) return record.date;
        return parentBooking.getPaymentDate();
    }

    public String getStatus() {
        if (record != null && record.status != null) return record.status;
        return parentBooking.getStatus();
    }

    public String getReferenceNumber() {
        if (record != null) return record.referenceNumber;
        return parentBooking.getTransactionRef();
    }

    /** GCash mobile number for this specific payment - see {@link #getReferenceNumber()}, same per-record rule. */
    public String getGcashNumber() {
        if (record != null) return record.gcashNumber;
        return parentBooking.getGcashNumber();
    }

    /**
     * Sortable millis - falls back to 0 (sorts last) if the date string can't
     * be parsed. Tries the current "MMM d, yyyy • h:mm a" display format
     * (see TimeUtils#formatDateTime) first, then the older formats this
     * field may still hold from already-serialized/cached Booking objects,
     * before giving up.
     */
    public long getDateMillis() {
        String date = getDate();
        if (date == null) return 0L;
        String[] patterns = {
                "MMM d, yyyy • h:mm a",
                "MMM dd, yyyy h:mm a",
                "MMM dd, yyyy"
        };
        for (String pattern : patterns) {
            try {
                java.util.Date parsed = new java.text.SimpleDateFormat(pattern, java.util.Locale.ENGLISH).parse(date);
                if (parsed != null) return parsed.getTime();
            } catch (Exception ignored) {
            }
        }
        return 0L;
    }
}
