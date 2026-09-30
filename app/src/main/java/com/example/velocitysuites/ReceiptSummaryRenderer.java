package com.example.velocitysuites;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;

/**
 * Renders a {@link ReceiptBreakdown} - the receipt's itemized Payment Summary - into a
 * container. The single place this layout is built, so PaymentReceiptActivity's two
 * modes (receipt-number mode and the legacy Booking-snapshot mode) show exactly the same
 * summary for the same transaction and can never drift apart.
 * <p>
 * Reading order, top to bottom: Selected Rooms (name, rate per night, nights, subtotal each)
 * -> Rooms Total -> Amenities (name, quantity, unit price, subtotal each) -> Amenities
 * Total, or "No amenities selected" -> Charges &amp; Adjustments (only when there are any) ->
 * the highlighted Grand Total, LAST, which is by construction the sum of everything above it
 * (see ReceiptBreakdown). Every amount is right-aligned in the app's one currency format
 * (ReceiptCardHelper#formatPrice()); a deduction prints as a negative amount.
 * <p>
 * Every view here is inflated from an item_receipt_*.xml layout, so colors/text styles
 * follow the theme (dark mode included) rather than being set per-view in code.
 */
final class ReceiptSummaryRenderer {

    private ReceiptSummaryRenderer() {
    }

    static void render(Context context, ViewGroup container, ReceiptBreakdown breakdown) {
        if (breakdown.hasItemizedRooms()) {
            renderItemized(context, container, breakdown);
        } else {
            renderSimple(context, container, breakdown);
        }
        addGrandTotal(context, container, breakdown.grandTotal);
    }

    private static void renderItemized(Context context, ViewGroup container, ReceiptBreakdown b) {
        // ---- Selected Rooms ----
        addSubheading(context, container, R.string.receipt_selected_rooms_title);
        for (ReceiptBreakdown.RoomLine room : b.roomLines) {
            String nights = context.getResources().getQuantityString(R.plurals.receipt_nights_count, (int) room.nights, (int) room.nights);
            String rate = ReceiptCardHelper.formatPrice(room.ratePerNight);
            String detail = room.quantity > 1
                    ? context.getString(R.string.receipt_room_line_detail_multi_format, rate,
                            context.getResources().getQuantityString(R.plurals.receipt_rooms_count, room.quantity, room.quantity), nights)
                    : context.getString(R.string.receipt_room_line_detail_format, rate, nights);
            addLine(context, container, room.name, ReceiptCardHelper.formatPrice(room.subtotal), detail);
        }
        addTotalRow(context, container, R.string.receipt_rooms_total_label, b.roomsTotal);

        // ---- Amenities ----
        addSubheading(context, container, R.string.receipt_amenities_title);
        switch (b.amenityStatus) {
            case ITEMIZED:
                for (ReceiptBreakdown.AmenityLine amenity : b.amenityLines) {
                    String detail = context.getString(R.string.receipt_amenity_line_detail_format,
                            amenity.quantity, ReceiptCardHelper.formatPrice(amenity.unitPrice));
                    addLine(context, container, amenity.name, ReceiptCardHelper.formatPrice(amenity.subtotal), detail);
                }
                addTotalRow(context, container, R.string.details_amenities_total_label, b.amenitiesTotal);
                break;
            case AGGREGATE_ONLY:
                // A charge is on record but its per-amenity lines aren't - one total, no invented items.
                addTotalRow(context, container, R.string.details_amenities_total_label, b.amenitiesTotal);
                break;
            case NONE:
                addNote(context, container, R.string.receipt_no_amenities);
                break;
            case UNKNOWN:
            default:
                // Deliberately NOT "No amenities selected" - the data could not be loaded, which is not the same claim.
                addNote(context, container, R.string.receipt_amenities_unavailable);
                break;
        }

        // ---- Charges & Adjustments: only what actually applies ----
        boolean hasOther = Math.abs(b.otherCharges) > 0.009;
        if (b.additionalGuestFee > 0.009 || b.discount > 0.009 || hasOther) {
            addSubheading(context, container, R.string.receipt_charges_adjustments_title);
            if (b.additionalGuestFee > 0.009) {
                addLine(context, container, context.getString(R.string.details_label_additional_guest_fee),
                        ReceiptCardHelper.formatPrice(b.additionalGuestFee), null);
            }
            if (b.discount > 0.009) {
                addLine(context, container, context.getString(R.string.details_label_discount), deduction(context, b.discount), null);
            }
            if (hasOther) {
                addLine(context, container, context.getString(R.string.receipt_other_charges_label),
                        b.otherCharges > 0 ? ReceiptCardHelper.formatPrice(b.otherCharges) : deduction(context, -b.otherCharges), null);
            }
        }
    }

    /**
     * A receipt with no per-room lines at all (a transaction that predates room_lines) - there
     * is nothing honest to itemize, so it keeps the plain Subtotal/Discount rows it always had
     * and only the Grand Total below is upgraded to the highlighted panel.
     */
    private static void renderSimple(Context context, ViewGroup container, ReceiptBreakdown b) {
        if (b.discount > 0.009) {
            addLine(context, container, context.getString(R.string.subtotal_label),
                    ReceiptCardHelper.formatPrice(b.grandTotal + b.discount), null);
            addLine(context, container, context.getString(R.string.details_label_discount), deduction(context, b.discount), null);
        }
    }

    private static String deduction(Context context, double amount) {
        return context.getString(R.string.receipt_deduction_format, ReceiptCardHelper.formatPrice(amount));
    }

    // ---- View builders (each just inflates a layout and fills its text) ----

    private static void addSubheading(Context context, ViewGroup container, int titleRes) {
        TextView view = (TextView) inflate(context, container, R.layout.item_receipt_subheading);
        view.setText(titleRes);
        container.addView(view);
    }

    private static void addLine(Context context, ViewGroup container, String name, String amount, @Nullable String detail) {
        View row = inflate(context, container, R.layout.item_receipt_line);
        ((TextView) row.findViewById(R.id.tvLineName)).setText(name);
        ((TextView) row.findViewById(R.id.tvLineAmount)).setText(amount);
        TextView detailView = row.findViewById(R.id.tvLineDetail);
        if (detail == null || detail.isEmpty()) {
            detailView.setVisibility(View.GONE);
        } else {
            detailView.setText(detail);
        }
        container.addView(row);
    }

    private static void addTotalRow(Context context, ViewGroup container, int labelRes, double amount) {
        View row = inflate(context, container, R.layout.item_receipt_total_row);
        ((TextView) row.findViewById(R.id.tvTotalLabel)).setText(labelRes);
        ((TextView) row.findViewById(R.id.tvTotalAmount)).setText(ReceiptCardHelper.formatPrice(amount));
        container.addView(row);
    }

    private static void addNote(Context context, ViewGroup container, int textRes) {
        TextView view = (TextView) inflate(context, container, R.layout.item_receipt_note);
        view.setText(textRes);
        container.addView(view);
    }

    private static void addGrandTotal(Context context, ViewGroup container, double grandTotal) {
        View panel = inflate(context, container, R.layout.item_receipt_grand_total);
        ((TextView) panel.findViewById(R.id.tvGrandTotalLabel)).setText(R.string.receipt_grand_total_label);
        ((TextView) panel.findViewById(R.id.tvGrandTotalAmount)).setText(ReceiptCardHelper.formatPrice(grandTotal));
        container.addView(panel);
    }

    private static View inflate(Context context, ViewGroup container, int layoutRes) {
        return LayoutInflater.from(context).inflate(layoutRes, container, false);
    }
}
