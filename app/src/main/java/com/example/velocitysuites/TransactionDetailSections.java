package com.example.velocitysuites;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The facts of one transaction, grouped the same way on every details view:
 * <ul>
 *   <li><b>Stay Details</b> - ID, type, status, check-in, check-out, nights, guests</li>
 *   <li><b>Room Details</b> - room type, room number (once assigned), quantity, rate per night</li>
 *   <li><b>Payment Details</b> - total, amount paid, balance, payment method, payment status</li>
 * </ul>
 * A fact the app does not have is LEFT OUT - never shown as "N/A", "null", "None", a dash or an empty value
 * ({@link #isShowable}). Whatever can be worked out from what the app already holds is: nights from the two dates,
 * quantity from the room lines, the balance and payment status from the amounts (through {@link TransactionStatusHelper},
 * the same source every other screen uses).
 */
final class TransactionDetailSections {

    /** One label / value line. */
    static final class Row {
        final String label;
        final String value;

        Row(String label, String value) {
            this.label = label;
            this.value = value;
        }
    }

    final List<Row> stay = new ArrayList<>();
    final List<Row> room = new ArrayList<>();
    final List<Row> payment = new ArrayList<>();

    private TransactionDetailSections() {
    }

    /** False for a missing value or any placeholder standing in for one. */
    static boolean isShowable(@Nullable String value) {
        if (value == null) return false;
        String v = value.trim();
        if (v.isEmpty()) return false;
        switch (v.toLowerCase(java.util.Locale.US)) {
            case "n/a":
            case "na":
            case "null":
            case "none":
            case "-":
            case "—":
            case "–":
            case "undefined":
                return false;
            default:
                return true;
        }
    }

    private static void add(List<Row> rows, String label, @Nullable String value) {
        if (isShowable(value)) rows.add(new Row(label, value.trim()));
    }

    /**
     * @param paymentMethod the latest payment's method ("cash" / "gcash") when the caller knows it; the booking's own
     *                      method is used when this is null
     */
    @NonNull
    static TransactionDetailSections of(@NonNull Context context, @NonNull Booking b, @Nullable String paymentMethod) {
        TransactionDetailSections out = new TransactionDetailSections();
        TransactionStatusHelper.Summary money = TransactionStatusHelper.summarize(b);

        // ---- Stay Details
        add(out.stay, context.getString(b.isHasBooking() ? R.string.details_label_booking_id : R.string.details_label_reservation_id),
                b.getDisplayId());
        add(out.stay, context.getString(R.string.details_label_type),
                context.getString(b.isHasBooking() ? R.string.upcoming_event_booking_prefix : R.string.upcoming_event_reservation_prefix));
        add(out.stay, context.getString(R.string.details_label_status), TransactionStatusChip.label(b.getStatus()));
        add(out.stay, context.getString(R.string.details_label_check_in), b.getCheckInDate());
        add(out.stay, context.getString(R.string.details_label_check_out), b.getCheckOutDate());
        Long nights = StayDateCalculator.nightsBetweenOrNull(b.getCheckInDate(), b.getCheckOutDate());
        if (nights != null && nights > 0) {
            add(out.stay, context.getString(R.string.nights_label),
                    context.getResources().getQuantityString(R.plurals.receipt_nights_count, nights.intValue(), nights.intValue()));
        }
        if (b.getGuests() > 0) {
            add(out.stay, context.getString(R.string.total_guests_label),
                    context.getResources().getQuantityString(R.plurals.guests_count, b.getGuests(), b.getGuests()));
        }

        // ---- Room Details
        List<String> types = new ArrayList<>();
        for (String name : b.getAllRoomTypeNames()) {
            if (isShowable(name) && !types.contains(name.trim())) types.add(name.trim());
        }
        add(out.room, context.getString(R.string.details_label_room_type), String.join(", ", types));
        List<String> numbers = new ArrayList<>();
        int quantity = 0;
        Map<String, Double> rates = new LinkedHashMap<>();
        for (BookingRoom r : b.getRooms()) {
            if (r == null) continue;
            quantity += Math.max(1, r.getQuantity());
            if (r.getAssignedRoomNumbers() != null) {
                for (String n : r.getAssignedRoomNumbers()) if (isShowable(n) && !numbers.contains(n.trim())) numbers.add(n.trim());
            }
            if (r.getPricePerNight() > 0 && isShowable(r.getRoomTypeName())) rates.put(r.getRoomTypeName().trim(), r.getPricePerNight());
        }
        if (numbers.isEmpty() && isShowable(b.getRoomNumber()) && !"Pending Assignment".equalsIgnoreCase(b.getRoomNumber().trim())) {
            numbers.add(b.getRoomNumber().trim());
        }
        add(out.room, context.getString(R.string.details_label_room_number), String.join(", ", numbers));
        if (quantity == 0 && !types.isEmpty()) quantity = Math.max(1, b.getRoomsRequested());
        if (quantity > 0) add(out.room, context.getString(R.string.details_label_quantity), String.valueOf(quantity));
        if (!rates.isEmpty()) {
            boolean same = new java.util.HashSet<>(rates.values()).size() == 1;
            if (same) {
                add(out.room, context.getString(R.string.details_label_rate_per_night), MoneyFormat.format(rates.values().iterator().next()));
            } else {
                List<String> parts = new ArrayList<>();
                for (Map.Entry<String, Double> e : rates.entrySet()) parts.add(e.getKey() + " " + MoneyFormat.format(e.getValue()));
                add(out.room, context.getString(R.string.details_label_rate_per_night), String.join(" · ", parts));
            }
        }

        // ---- Payment Details
        if (money.grandTotal > MoneyFormat.EPSILON) {
            add(out.payment, context.getString(R.string.details_label_total_amount), MoneyFormat.format(money.grandTotal));
            add(out.payment, context.getString(R.string.details_label_amount_paid), MoneyFormat.format(money.verifiedPaid));
            add(out.payment, context.getString(R.string.details_label_balance), MoneyFormat.format(money.balance));
        }
        String method = isShowable(paymentMethod) ? paymentMethod : b.getPaymentMethod();
        if (isShowable(method)) {
            add(out.payment, context.getString(R.string.review_payment_method_label),
                    DisplayLabels.paymentMethod(context, method));
        }
        add(out.payment, context.getString(R.string.payment_status),
                context.getString(TransactionStatusHelper.styleFor(TransactionStatusHelper.statusOf(b)).labelRes));
        return out;
    }

    /** Every row, Stay then Room then Payment. */
    @NonNull
    List<Row> all() {
        List<Row> rows = new ArrayList<>(stay);
        rows.addAll(room);
        rows.addAll(payment);
        return rows;
    }
}
