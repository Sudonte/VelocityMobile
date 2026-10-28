package com.example.velocitysuites;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The "full details" of a Your Next Stay card, shown only after "Show full details":
 * room charges, the approved amenities grouped by item (quantities added up), the amenities total, then Total, Paid and
 * ONE Balance due line. Everything comes from the server's own figures (the stay bill when the stay is in house, else
 * the transaction's own total and verified payments); nothing is invented. Pure rows, so the wording is unit-testable.
 * Which cards are open is kept here for as long as the app's process lives: it survives scrolling, rotation and a trip
 * to another screen, and starts collapsed again after a restart.
 */
final class NextStayDetails {

    enum Kind { LINE, HEADER, DIVIDER, NOTE, ROOMS }

    enum Tone { NORMAL, DUE, PAID }

    static final class Row {
        final Kind kind;
        final String label;
        final String value;
        final boolean strong;
        final Tone tone;

        private Row(Kind kind, String label, String value, boolean strong, Tone tone) {
            this.kind = kind;
            this.label = label;
            this.value = value;
            this.strong = strong;
            this.tone = tone;
        }
    }

    private static final Set<String> OPEN = new HashSet<>();

    private NextStayDetails() {
    }

    static boolean isOpen(@NonNull Booking b) {
        return OPEN.contains(StayCalendarModel.keyOf(b));
    }

    static void setOpen(@NonNull Booking b, boolean open) {
        if (open) OPEN.add(StayCalendarModel.keyOf(b));
        else OPEN.remove(StayCalendarModel.keyOf(b));
    }

    /** Test hook: what a fresh process would have. */
    static void resetForTests() {
        OPEN.clear();
    }

    /** One amenity (or charge) line per item: "Additional Blanket × 2", with the quantities and amounts of repeats added up. */
    @NonNull
    static List<BalanceBreakdown.Item> grouped(@Nullable List<BalanceBreakdown.Item> items) {
        Map<String, double[]> totals = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        if (items != null) {
            for (BalanceBreakdown.Item item : items) {
                if (item == null) continue;
                String key = item.name.trim().toLowerCase(Locale.ROOT);
                double[] sum = totals.get(key);
                if (sum == null) {
                    sum = new double[2];
                    totals.put(key, sum);
                    names.put(key, item.name.trim());
                }
                sum[0] += item.quantity;
                sum[1] += item.subtotal;
            }
        }
        List<BalanceBreakdown.Item> out = new ArrayList<>();
        for (Map.Entry<String, double[]> e : totals.entrySet()) {
            int quantity = (int) Math.round(e.getValue()[0]);
            double subtotal = e.getValue()[1];
            out.add(BalanceBreakdown.itemOf(names.get(e.getKey()), quantity, quantity == 0 ? 0 : subtotal / quantity, subtotal));
        }
        return out;
    }

    @NonNull
    static List<Row> rows(@NonNull Context c, @NonNull Booking booking) {
        return rows(c, booking, true);
    }

    @NonNull
    static List<Row> rows(@NonNull Context c, @NonNull Booking booking, boolean withRooms) {
        List<Row> rows = new ArrayList<>();
        StayInfo info = booking.getStay();
        BalanceBreakdown b = info != null ? info.balanceBreakdown : null;
        if (b == null) {
            TransactionStatusHelper.Summary s = TransactionStatusHelper.summarize(booking);
            if (withRooms) rows.add(new Row(Kind.ROOMS, "", "", false, Tone.NORMAL));
            rows.add(line(c.getString(R.string.next_stay_detail_total), s.grandTotal, true, Tone.NORMAL));
            rows.add(line(c.getString(R.string.next_stay_paid), s.verifiedPaid, false, Tone.NORMAL));
            rows.add(balanceLine(c, s.balance));
            return rows;
        }
        rows.add(line(c.getString(R.string.next_stay_detail_room_charges), b.roomCharge, false, Tone.NORMAL));
        if (b.bookedAmenities > MoneyFormat.EPSILON) {
            rows.add(line(c.getString(R.string.next_stay_detail_booked_amenities), b.bookedAmenities, false, Tone.NORMAL));
        }
        if (b.discount > MoneyFormat.EPSILON) {
            rows.add(new Row(Kind.LINE, c.getString(R.string.next_stay_detail_discount), "-" + MoneyFormat.format(b.discount), false, Tone.NORMAL));
        }
        // Every room as its own small card with its approved amenities, then the amenities total (one shared view).
        rows.add(new Row(Kind.ROOMS, "", "", false, Tone.NORMAL));
        if (!b.amenities.isEmpty()) {
            rows.add(line(c.getString(R.string.next_stay_amenities_total), b.amenitiesTotal, false, Tone.NORMAL));
        }
        if (b.discountOnAmenities > MoneyFormat.EPSILON) {
            rows.add(new Row(Kind.LINE, c.getString(R.string.next_stay_discount_on_amenities), "-" + MoneyFormat.format(b.discountOnAmenities), false, Tone.NORMAL));
        }
        List<BalanceBreakdown.Item> charges = grouped(b.charges);
        if (!charges.isEmpty()) {
            rows.add(new Row(Kind.HEADER, c.getString(R.string.next_stay_detail_additional_charges), "", false, Tone.NORMAL));
            for (BalanceBreakdown.Item item : charges) rows.add(itemLine(c, item));
        }
        rows.add(new Row(Kind.DIVIDER, "", "", false, Tone.NORMAL));
        rows.add(line(c.getString(R.string.next_stay_detail_total), b.finalGrandTotal, true, Tone.NORMAL));
        rows.add(line(c.getString(R.string.next_stay_paid), b.totalPaid, false, Tone.NORMAL));
        rows.add(balanceLine(c, b.balanceDue));
        if (b.balanceDue > MoneyFormat.EPSILON && "Checked-In".equalsIgnoreCase(booking.getStatus())) {
            rows.add(new Row(Kind.NOTE, c.getString(R.string.next_stay_detail_payable_at_checkout), "", false, Tone.NORMAL));
        }
        int pending = 0;
        for (StayInfo.RequestedAmenity r : info.requestedAmenities) if (!r.rejected) pending++;
        if (pending > 0) {
            rows.add(new Row(Kind.NOTE, c.getResources().getQuantityString(R.plurals.next_stay_pending_requests, pending, pending),
                    "", false, Tone.NORMAL));
        }
        return rows;
    }

    private static Row line(String label, double amount, boolean strong, Tone tone) {
        return new Row(Kind.LINE, label, MoneyFormat.format(amount), strong, tone);
    }

    private static Row itemLine(Context c, BalanceBreakdown.Item item) {
        return new Row(Kind.LINE, c.getString(R.string.next_stay_detail_item_format, item.name, item.quantity),
                MoneyFormat.format(item.subtotal), false, Tone.NORMAL);
    }

    /** The only balance line: bold red while something is owed, green "₱0.00" once nothing is. */
    private static Row balanceLine(Context c, double balance) {
        boolean due = balance > MoneyFormat.EPSILON;
        return new Row(Kind.LINE, c.getString(R.string.next_stay_balance_due), MoneyFormat.format(due ? balance : 0), true,
                due ? Tone.DUE : Tone.PAID);
    }

    /** Fills {@code box} with the rows: label on the left, amount right-aligned, the same text sizes on every card. */
    static void render(@NonNull Context c, @NonNull LinearLayout box, @NonNull List<Row> rows) {
        render(c, box, rows, null);
    }

    /** Fills {@code box}; a ROOMS row becomes the shared "Your rooms" section built from {@code booking}. */
    static void render(@NonNull Context c, @NonNull LinearLayout box, @NonNull List<Row> rows, @Nullable Booking booking) {
        box.removeAllViews();
        float density = c.getResources().getDisplayMetrics().density;
        for (Row row : rows) {
            switch (row.kind) {
                case ROOMS: {
                    if (booking != null) TransactionRoomsView.addLive(c, box, booking);
                    break;
                }
                case DIVIDER: {
                    View divider = new View(c);
                    divider.setBackgroundColor(c.getColor(R.color.velocity_divider_hairline));
                    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(density)));
                    p.topMargin = Math.round(6 * density);
                    p.bottomMargin = Math.round(4 * density);
                    box.addView(divider, p);
                    break;
                }
                case HEADER: {
                    TextView t = new TextView(c);
                    t.setText(row.label);
                    t.setTextSize(13);
                    t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
                    t.setTextColor(c.getColor(R.color.velocity_text_primary));
                    t.setPadding(0, Math.round(8 * density), 0, Math.round(2 * density));
                    box.addView(t);
                    break;
                }
                case NOTE: {
                    TextView t = new TextView(c);
                    t.setText(row.label);
                    t.setTextSize(12);
                    t.setTextColor(c.getColor(R.color.velocity_text_secondary));
                    t.setPadding(0, Math.round(2 * density), 0, 0);
                    box.addView(t);
                    break;
                }
                default: {
                    LinearLayout line = new LinearLayout(c);
                    line.setOrientation(LinearLayout.HORIZONTAL);
                    line.setPadding(0, Math.round(3 * density), 0, Math.round(3 * density));
                    TextView label = new TextView(c);
                    label.setText(row.label);
                    label.setTextSize(13);
                    label.setTextColor(c.getColor(row.strong ? R.color.velocity_text_primary : R.color.velocity_text_secondary));
                    if (row.strong) label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);
                    line.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    TextView value = new TextView(c);
                    value.setText(row.value);
                    value.setGravity(Gravity.END);
                    value.setTextSize(13);
                    value.setTextColor(c.getColor(row.tone == Tone.DUE ? R.color.velocity_red_primary
                            : row.tone == Tone.PAID ? R.color.status_paid_fg : R.color.velocity_text_primary));
                    if (row.strong) value.setTypeface(value.getTypeface(), android.graphics.Typeface.BOLD);
                    LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    vp.setMarginStart(Math.round(10 * density));
                    line.addView(value, vp);
                    box.addView(line);
                }
            }
        }
    }
}
