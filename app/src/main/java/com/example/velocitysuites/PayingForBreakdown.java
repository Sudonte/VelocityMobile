package com.example.velocitysuites;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The ONE breakdown of a booking or reservation, built the same way for every screen and receipt: the payment steps ("What you're paying
 * for"), Billing Summary, Booking / Reservation Details, Transaction History details and the three receipts (on screen and as PDF).
 * Every value appears once.
 *
 * <p>Per date range, headed by its dates ("Oct 26 → Oct 27, 2026 · 1 night", never "Stay N"): its rooms, then its amenities (under the
 * room they were chosen for, else under "Amenities for this stay") and, with several date ranges, the range's subtotal. After the date
 * ranges: Room charges, Amenities, Other charges (front desk), the Discount (only once applied; otherwise a note), the Grand total and,
 * on the details screens and receipts, Paid so far and Balance due. Label left, amount right.
 */
final class PayingForBreakdown {

    /** One priced line: "Deluxe × 1" and its amount; {@code note} is a small line under it (the assigned room number). */
    static final class Line {
        final String label;
        final double amount;
        @Nullable final String note;

        Line(String label, double amount) {
            this(label, amount, null);
        }

        Line(String label, double amount, @Nullable String note) {
            this.label = label;
            this.amount = amount;
            this.note = note;
        }
    }

    /** Amenities of one room ({@code room} = "Deluxe (room 1)") or of the stay as a whole ({@code room} = null). */
    static final class AmenityGroup {
        @Nullable final String room;
        final List<Line> lines = new ArrayList<>();

        AmenityGroup(@Nullable String room) {
            this.room = room;
        }
    }

    static final class StayBlock {
        @Nullable String heading;
        final List<Line> rooms = new ArrayList<>();
        final List<AmenityGroup> amenities = new ArrayList<>();
        double subtotal;
        int position;

        double roomCharges() {
            double sum = 0;
            for (Line l : rooms) sum += l.amount;
            return sum;
        }

        double amenityCharges() {
            double sum = 0;
            for (AmenityGroup g : amenities) for (Line l : g.lines) sum += l.amount;
            return sum;
        }

        boolean hasAmenities() {
            for (AmenityGroup g : amenities) if (!g.lines.isEmpty()) return true;
            return false;
        }
    }

    final List<StayBlock> stays = new ArrayList<>();
    /** The Grand total the guest pays against (rooms + amenities + other charges, before an expected discount; after an applied one, the server's net). */
    double grandTotal;
    /** The discount an ID check is still expected to take off (a note, never a subtraction); 0 for none. */
    double expectedDiscount;
    @Nullable String discountName;
    /** A discount the front desk already applied (a subtraction); 0 for none. */
    double appliedDiscount;

    // ---- the bill of the details screens and receipts ----
    /** Figures that come from the server's own snapshot instead of the lines above (null = add the lines up). */
    @Nullable Double roomsOverride;
    @Nullable Double amenitiesOverride;
    /** Charges the front desk added (an additional guest fee, a damaged item): one row each. */
    final List<Line> otherCharges = new ArrayList<>();
    double otherChargesTotal;
    /** Paid so far and Balance due are part of the bill (details screens and receipts); the payment steps stop at the Grand total. */
    boolean settlement;
    double paid;
    /** Submitted and not yet verified: shown on its own line, never inside Paid so far. */
    double waiting;
    double balance;
    /** A cancelled or rejected transaction owes nothing: no Paid / Balance rows. */
    boolean cancelled;
    /** Amenity requests the front desk has not approved (waiting or refused): listed apart with their status, never counted in a total. */
    final List<Line> requests = new ArrayList<>();
    /** One short plain-language line under the bill ("You pay the balance at the hotel."). */
    @Nullable String plainLine;

    double roomCharges() {
        if (roomsOverride != null) return roomsOverride;
        double sum = 0;
        for (StayBlock s : stays) sum += s.roomCharges();
        return sum;
    }

    double amenityCharges() {
        if (amenitiesOverride != null) return amenitiesOverride;
        double sum = 0;
        for (StayBlock s : stays) sum += s.amenityCharges();
        return sum;
    }

    boolean hasAmenities() {
        for (StayBlock s : stays) if (s.hasAmenities()) return true;
        return amenityCharges() > 0.009;
    }

    boolean isEmpty() {
        return stays.isEmpty();
    }

    // ---- building: a saved booking or reservation ----

    /** For the payment screens: a saved booking or reservation, with {@code grandTotal} what the screen charges against. */
    static PayingForBreakdown of(Context c, @NonNull Booking booking, double grandTotal) {
        PayingForBreakdown out = new PayingForBreakdown();
        out.grandTotal = grandTotal;
        out.addRanges(c, booking, false);
        out.expectedDiscount = BookingTotals.expectedDiscount(booking);
        out.appliedDiscount = BookingTotals.discountAppliedAmount(booking);
        out.discountName = BookingTotals.discountLabel(booking);
        return out;
    }

    /** For Booking / Reservation Details and Transaction History details: the ranges and the whole bill, settled against what was paid. */
    static PayingForBreakdown forDetails(Context c, @NonNull Booking booking) {
        PayingForBreakdown out = new PayingForBreakdown();
        out.addRanges(c, booking, true);
        TransactionStatusHelper.Summary summary = TransactionStatusHelper.summarize(booking);
        out.settlement = true;
        out.cancelled = summary.status == TransactionStatusHelper.Status.CANCELLED || summary.status == TransactionStatusHelper.Status.REJECTED;
        out.discountName = BookingTotals.discountLabel(booking);
        out.expectedDiscount = BookingTotals.expectedDiscount(booking);
        StayInfo stay = booking.getStay();
        BalanceBreakdown b = stay != null ? stay.balanceBreakdown : null;
        if (stay != null) {
            for (StayInfo.RequestedAmenity r : stay.requestedAmenities) {
                String status = r.rejected
                        ? (r.reason == null || r.reason.isEmpty() ? c.getString(R.string.details_request_rejected) : c.getString(R.string.details_request_rejected_reason, r.reason))
                        : c.getString(R.string.details_request_pending);
                out.requests.add(new Line(c.getString(R.string.summary_amenity_row_qty_format, r.name, r.quantity) + " · " + MoneyFormat.format(r.subtotal), 0, status));
            }
        }
        if (b != null) {
            // in house or finished and billed at check-in: the server's own figures (the ones the third receipt prints)
            out.roomsOverride = b.roomCharge;
            out.amenitiesOverride = b.bookedAmenities + b.amenitiesTotal;
            for (BalanceBreakdown.Item item : b.charges) out.otherCharges.add(new Line(item.name, item.subtotal));
            out.otherChargesTotal = b.chargesTotal;
            out.appliedDiscount = b.discount;
            if (b.discountName != null && !b.discountName.isEmpty()) out.discountName = b.discountName;
            out.grandTotal = b.finalGrandTotal;
            out.paid = b.totalPaid;
            out.balance = b.balanceDue;
        } else {
            out.roomsOverride = BookingTotals.roomCharges(booking);
            out.amenitiesOverride = BookingTotals.amenities(booking);
            double fee = Math.max(0, booking.getAdditionalGuestFee());
            if (fee > 0.009) {
                out.otherCharges.add(new Line(c.getString(R.string.details_label_additional_guest_fee), fee));
                out.otherChargesTotal = fee;
            }
            out.appliedDiscount = BookingTotals.discountAppliedAmount(booking);
            out.grandTotal = summary.grandTotal;
            out.paid = summary.verifiedPaid;
            out.balance = summary.balance;
        }
        out.waiting = TransactionStatusHelper.summarize(booking).pendingSubmitted;
        out.plainLine = out.plainLine(c);
        return out;
    }

    private void addRanges(Context c, @NonNull Booking booking, boolean detailed) {
        if (!booking.getStays().isEmpty()) {
            for (Booking.StayRecord stay : booking.getStays()) {
                if (stay != null) stays.add(blockOf(c, stay, detailed));
            }
            return;
        }
        // an older record with no date ranges: one block from its own rooms and amenities
        StayBlock block = new StayBlock();
        Long counted = StayDateCalculator.nightsBetweenOrNull(booking.getCheckInDate(), booking.getCheckOutDate());
        long nights = counted != null ? Math.max(1, counted) : 1;
        String nightsText = c.getResources().getQuantityString(R.plurals.receipt_nights_count, (int) nights, (int) nights);
        String dates = StayCardText.dates(booking);
        block.heading = dates.isEmpty() ? null : c.getString(R.string.details_single_stay_heading_format, dates, nightsText);
        for (BookingRoom room : booking.getRooms()) {
            if (room != null) block.rooms.add(roomLine(c, room, nights, nightsText, room.getSubtotal(), detailed));
        }
        addBookedAmenities(c, block, booking.getAmenities());
        double total = booking.getTotalAmount();
        if (block.rooms.isEmpty() && total > 0.009) {
            block.rooms.add(new Line(roomName(c, booking.getRoomName(), Math.max(1, booking.getRoomsRequested())), Math.max(0, total - block.amenityCharges())));
        }
        block.subtotal = block.roomCharges() + block.amenityCharges();
        if (!block.rooms.isEmpty()) stays.add(block);
    }

    private static StayBlock blockOf(Context c, Booking.StayRecord stay, boolean detailed) {
        StayBlock block = new StayBlock();
        block.position = stay.position;
        long n = BookingTotals.nightsOf(stay);
        String nights = c.getResources().getQuantityString(R.plurals.receipt_nights_count, (int) n, (int) n);
        String dates = StayCardText.stayDates(stay.checkIn, stay.checkOut);
        block.heading = dates.isEmpty() ? nights : c.getString(R.string.details_single_stay_heading_format, dates, nights);
        // the guest never arrived for these dates: still charged in full, and it says so
        if (stay.isNoShow()) block.heading = c.getString(R.string.stay_heading_no_show_format, block.heading);
        for (BookingRoom room : stay.rooms) {
            if (room != null) block.rooms.add(roomLine(c, room, n, nights, BookingTotals.lineTotal(room, stay), detailed));
        }
        addBookedAmenities(c, block, stay.amenities);
        block.subtotal = block.roomCharges() + block.amenityCharges();
        return block;
    }

    /** "Deluxe × 1" (payment steps) or "Deluxe × 1 · ₱3,500.00 × 1 night" (details and receipts), with the room number under it once assigned. */
    private static Line roomLine(Context c, BookingRoom room, long nights, String nightsText, double amount, boolean detailed) {
        int qty = Math.max(1, room.getQuantity());
        String name = room.getRoomTypeName() != null && !room.getRoomTypeName().trim().isEmpty() ? room.getRoomTypeName() : c.getString(R.string.label_not_available);
        if (!detailed) return new Line(roomName(c, name, qty), amount);
        String note = room.getAssignedRoomNumbers().isEmpty() ? null
                : c.getString(R.string.stay_room_numbers_format, android.text.TextUtils.join(", ", room.getAssignedRoomNumbers()));
        return new Line(c.getString(R.string.details_room_line_format, name, qty, MoneyFormat.format(room.getPricePerNight()), nightsText), amount, note);
    }

    // ---- building: a new booking or reservation that is still the reviewed wizard state ----

    /** A new booking or reservation that is still the reviewed wizard state. */
    static PayingForBreakdown of(Context c, @NonNull BookingWizardState state) {
        PayingForBreakdown out = new PayingForBreakdown();
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        List<BookingWizardState.Stay> all = state.allStays();
        for (int i = 0; i < all.size(); i++) {
            BookingWizardState.Stay stay = all.get(i);
            if (stay.checkIn == null || stay.checkOut == null) continue;
            StayBlock block = new StayBlock();
            block.position = i + 1;
            long n = stay.nights();
            String nights = c.getResources().getQuantityString(R.plurals.receipt_nights_count, (int) n, (int) n);
            String dates = StayCardText.stayDates(iso.format(stay.checkIn.getTime()), iso.format(stay.checkOut.getTime()));
            block.heading = c.getString(R.string.details_single_stay_heading_format, dates, nights);
            for (List<Room> group : stay.roomsGroupedByType().values()) {
                Room room = group.get(0);
                block.rooms.add(new Line(roomName(c, room.getName(), group.size()), room.getPricePerNight() * n * group.size()));
            }
            if (stay.roomAmenityQty.isEmpty()) {
                // chosen without a per-room split: the stay's own list
                AmenityGroup g = new AmenityGroup(null);
                for (AddOnAmenity a : stay.amenities) g.lines.add(new Line(c.getString(R.string.summary_amenity_row_qty_format, a.getName(), a.getQuantity()), a.getSubtotal()));
                if (!g.lines.isEmpty()) block.amenities.add(g);
            } else {
                addChosenAmenities(c, block, stay, stay.anyRoomSlot(), null);
                for (BookingWizardState.Stay.Slot slot : stay.roomSlots()) {
                    addChosenAmenities(c, block, stay, slot, c.getString(R.string.amenity_room_heading_format, slot.room.getName(), slot.number));
                }
            }
            block.subtotal = block.roomCharges() + block.amenityCharges();
            out.stays.add(block);
        }
        out.grandTotal = state.roomsTotal() + state.amenitiesTotal();
        if (state.discount != null) {
            out.expectedDiscount = state.discount.estimateOff(out.grandTotal);
            out.discountName = state.discount.getName();
        }
        return out;
    }

    private static void addChosenAmenities(Context c, StayBlock block, BookingWizardState.Stay stay, BookingWizardState.Stay.Slot slot, @Nullable String roomLabel) {
        java.util.Map<String, Integer> chosen = stay.roomAmenityQty.get(slot.key);
        if (chosen == null || chosen.isEmpty()) return;
        AmenityGroup g = new AmenityGroup(roomLabel);
        for (java.util.Map.Entry<String, Integer> e : chosen.entrySet()) {
            AddOnAmenity a = stay.amenityCatalog.get(e.getKey());
            if (a == null || e.getValue() <= 0) continue;
            g.lines.add(new Line(c.getString(R.string.summary_amenity_row_qty_format, a.getName(), e.getValue()), a.getPrice() * e.getValue()));
        }
        if (!g.lines.isEmpty()) block.amenities.add(g);
    }

    private static void addBookedAmenities(Context c, StayBlock block, List<BookingAmenity> amenities) {
        java.util.Map<String, AmenityGroup> byRoom = new java.util.LinkedHashMap<>();
        for (BookingAmenity a : amenities) {
            if (a == null) continue;
            String room = a.getRoomLabel();
            AmenityGroup g = byRoom.get(room == null ? "" : room);
            if (g == null) {
                g = new AmenityGroup(room);
                byRoom.put(room == null ? "" : room, g);
            }
            g.lines.add(new Line(c.getString(R.string.summary_amenity_row_qty_format, a.getAmenityName(), a.getQuantity()), a.getSubtotal()));
        }
        block.amenities.addAll(byRoom.values());
    }

    // ---- building: a receipt ----

    /**
     * A receipt as the same breakdown. The ranges come from the receipt's own stays (else its room lines); the money is the receipt's own
     * snapshot: the check-out (final) lines, the check-in breakdown, or the booking's total at that time.
     */
    static PayingForBreakdown forReceipt(Context c, @NonNull ReceiptDetail detail, @Nullable List<BookingAmenity> amenities, double aggregateAmenityCharge) {
        PayingForBreakdown out = new PayingForBreakdown();
        out.settlement = true;
        ReceiptBreakdown rb = ReceiptBreakdown.forReceiptDetail(detail, amenities, aggregateAmenityCharge);
        List<Booking.StayRecord> recorded = detail.getStays();
        if (recorded != null && !recorded.isEmpty()) {
            for (Booking.StayRecord stay : recorded) if (stay != null) out.stays.add(blockOf(c, stay, true));
        } else {
            StayBlock block = new StayBlock();
            long nights = Math.max(1, detail.getNumberOfNights());
            String nightsText = c.getResources().getQuantityString(R.plurals.receipt_nights_count, (int) nights, (int) nights);
            String dates = StayCardText.stayDates(detail.getCheckIn(), detail.getCheckOut());
            block.heading = dates.isEmpty() ? nightsText : c.getString(R.string.details_single_stay_heading_format, dates, nightsText);
            List<BookingRoom> lines = detail.getRoomLines();
            for (int i = 0; i < rb.roomLines.size(); i++) {
                ReceiptBreakdown.RoomLine line = rb.roomLines.get(i);
                BookingRoom source = i < lines.size() ? lines.get(i) : null;
                String note = source == null || source.getAssignedRoomNumbers().isEmpty() ? null
                        : c.getString(R.string.stay_room_numbers_format, android.text.TextUtils.join(", ", source.getAssignedRoomNumbers()));
                block.rooms.add(new Line(c.getString(R.string.details_room_line_format, line.name, line.quantity, MoneyFormat.format(line.ratePerNight),
                        c.getResources().getQuantityString(R.plurals.receipt_nights_count, (int) line.nights, (int) line.nights)), line.subtotal, note));
            }
            if (block.rooms.isEmpty() && rb.roomsTotal > 0.009) {
                block.rooms.add(new Line(roomName(c, detail.getRoomType(), 1), rb.roomsTotal));
            }
            if (amenities != null) addBookedAmenities(c, block, amenities);
            block.subtotal = block.roomCharges() + block.amenityCharges();
            out.stays.add(block);
        }
        ReceiptDetail.Highlight h = detail.getHighlight();
        BalanceBreakdown b = detail.getStay() != null ? detail.getStay().balanceBreakdown : null;
        Booking.PaymentSummary summary = detail.getPaymentSummary();
        boolean finalSnapshot = h != null && ReceiptDetail.Highlight.CHECKOUT_FINAL.equals(h.kind)
                && (h.finalLines || (h.has("room_charges") && !(b != null && "OFFICIAL_RECEIPT".equals(detail.getReceiptType()))));
        if (finalSnapshot) {
            out.roomsOverride = h.amount("room_charges");
            out.amenitiesOverride = h.amount("amenities_total");
            if (!h.amenityGroups.isEmpty() && (recorded == null || recorded.isEmpty())) {
                // the final receipt's own amenities, grouped by room, replace the booking-time list
                for (StayBlock s : out.stays) s.amenities.clear();
                StayBlock first = out.stays.get(0);
                for (ReceiptDetail.Highlight.AmenityGroup g : h.amenityGroups) {
                    String room = g.room != null && !g.room.isEmpty() ? c.getString(R.string.receipt_final_room_format, g.room)
                            : g.label != null && !g.label.isEmpty() ? g.label : null;
                    AmenityGroup group = new AmenityGroup(room);
                    for (ReceiptDetail.Highlight.AmenityItem item : g.items) {
                        group.lines.add(new Line(c.getString(R.string.summary_amenity_row_qty_format, item.name, item.quantity), item.subtotal));
                    }
                    if (!group.lines.isEmpty()) first.amenities.add(group);
                }
            }
            for (ReceiptDetail.Highlight.ChargeLine charge : h.otherCharges) out.otherCharges.add(new Line(charge.name, charge.amount));
            out.otherChargesTotal = h.amount("additional_charges_total");
            out.appliedDiscount = h.amount("discount");
            out.grandTotal = h.amount("grand_total");
            out.paid = h.amount("total_paid");
            out.balance = h.amount("remaining_balance");
        } else if (b != null && "OFFICIAL_RECEIPT".equals(detail.getReceiptType())) {
            out.roomsOverride = b.roomCharge;
            out.amenitiesOverride = b.bookedAmenities + b.amenitiesTotal;
            for (BalanceBreakdown.Item item : b.charges) out.otherCharges.add(new Line(item.name, item.subtotal));
            out.otherChargesTotal = b.chargesTotal;
            out.appliedDiscount = b.discount;
            out.discountName = b.discountName;
            out.grandTotal = b.finalGrandTotal;
            out.paid = b.totalPaid;
            out.balance = b.balanceDue;
        } else {
            out.roomsOverride = rb.roomsTotal;
            out.amenitiesOverride = rb.amenitiesTotal;
            out.otherChargesTotal = rb.additionalGuestFee + rb.otherCharges;
            // what the itemized lines don't explain of the total is the front desk's: one "Other charges (front desk)" row, no sub-line
            if (rb.additionalGuestFee > 0.009 && Math.abs(rb.otherCharges) > 0.009) {
                out.otherCharges.add(new Line(c.getString(R.string.details_label_additional_guest_fee), rb.additionalGuestFee));
                out.otherCharges.add(new Line(c.getString(R.string.bill_other_charges), rb.otherCharges));
            } else if (rb.additionalGuestFee > 0.009) {
                out.otherCharges.add(new Line(c.getString(R.string.details_label_additional_guest_fee), rb.additionalGuestFee));
            }
            out.appliedDiscount = rb.discount;
            out.grandTotal = rb.grandTotal;
            if (h != null && h.has("total_paid")) {
                out.paid = h.amount("total_paid");
                out.balance = h.amount("remaining_balance");
            } else if (summary != null) {
                out.paid = summary.totalAmountPaid;
                out.balance = summary.remainingBalance;
            } else {
                out.balance = rb.grandTotal;
            }
        }
        int stage = ReceiptTypeMapper.stageOf(detail.getReceiptType());
        if (stage == 1 && out.balance > 0.009) out.plainLine = c.getString(R.string.receipt_balance_at_hotel_format, MoneyFormat.format(out.balance));
        else if (stage == 2 && out.balance > 0.009) out.plainLine = c.getString(R.string.receipt_balance_at_checkout_format, MoneyFormat.format(out.balance));
        else out.plainLine = out.plainLine(c);
        return out;
    }

    @Nullable
    private String plainLine(Context c) {
        if (cancelled) return c.getString(R.string.balance_plain_cancelled);
        // settled: the green "Fully paid" on the Balance due row already says it
        return balance > 0.009 ? c.getString(R.string.bill_plain_balance_at_hotel) : null;
    }

    private static String roomName(Context c, @Nullable String name, int count) {
        return c.getString(R.string.gcash_room_line_format, name != null && !name.trim().isEmpty() ? name : c.getString(R.string.label_not_available), count);
    }

    // ---- drawing ----

    /** The payment steps: every date range, then the bill down to the Grand total. */
    void render(Context c, ViewGroup into) {
        into.removeAllViews();
        renderRanges(c, into);
        renderBill(c, into);
    }

    /** The date ranges only (heading, rooms, amenities, subtotal with several); "No amenities added" when there are none. */
    void renderRanges(Context c, ViewGroup into) {
        boolean several = stays.size() > 1;
        for (StayBlock stay : stays) renderStay(c, into, stay, several);
        if (!hasAmenities() && !stays.isEmpty() && requests.isEmpty()) into.addView(noAmenities(c));
        for (Line r : requests) {
            // "Massage × 1 · ₱100.00" and why it is not in the bill
            into.addView(ReviewSheet.row(c, ReviewSheet.Row.of(r.label, r.note)));
        }
    }

    /** The bill: Room charges, Amenities, Other charges, Discount, Grand total, then (details and receipts) Paid so far and Balance due. */
    void renderBill(Context c, ViewGroup into) {
        if (stays.isEmpty()) return;
        View rule = new View(c);
        rule.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1))));
        rule.setBackgroundColor(ContextCompat.getColor(c, R.color.velocity_divider_hairline));
        LinearLayout.LayoutParams ruleLp = (LinearLayout.LayoutParams) rule.getLayoutParams();
        ruleLp.topMargin = dp(c, 8);
        ruleLp.bottomMargin = dp(c, 4);
        into.addView(rule);
        into.addView(row(c, stays.size() > 1 ? c.getString(R.string.details_room_charges_all_stays) : c.getString(R.string.review_rooms_total_label), roomCharges(), false));
        into.addView(row(c, c.getString(settlement ? R.string.bill_amenities : R.string.breakdown_amenities_total_label), amenityCharges(), false));
        if (!otherCharges.isEmpty()) {
            for (Line l : otherCharges) {
                boolean generic = l.label.equals(c.getString(R.string.bill_other_charges));
                into.addView(row(c, generic ? l.label : c.getString(R.string.bill_other_charge_format, l.label), l.amount, false));
            }
        } else if (otherChargesTotal > 0.009) {
            into.addView(row(c, c.getString(R.string.bill_other_charges), otherChargesTotal, false));
        }
        if (appliedDiscount > 0.009) {
            into.addView(row(c, discountName == null || discountName.isEmpty() ? c.getString(R.string.details_label_discount)
                    : c.getString(R.string.details_discount_applied_format, discountName), -appliedDiscount, false));
        }
        into.addView(row(c, c.getString(R.string.review_grand_total_label), grandTotal, true));
        if (expectedDiscount > 0.009) {
            into.addView(small(c, c.getString(R.string.details_expected_discount_format,
                    discountName == null || discountName.isEmpty() ? c.getString(R.string.details_label_discount) : discountName, MoneyFormat.format(expectedDiscount)), 6));
        }
        if (settlement && !cancelled) {
            into.addView(row(c, c.getString(R.string.bill_paid_so_far), paid, false));
            if (waiting > 0.009) into.addView(row(c, c.getString(R.string.bill_waiting_for_verification), waiting, false));
            if (balance > 0.009) {
                into.addView(row(c, c.getString(R.string.bill_balance_due), balance, true));
            } else {
                into.addView(ReviewSheet.row(c, ReviewSheet.Row.boldColored(c.getString(R.string.bill_balance_due),
                        c.getString(R.string.bill_fully_paid_format, MoneyFormat.format(0)), R.color.velocity_green_dark)));
            }
        }
        if (settlement && !cancelled && waiting > 0.009) {
            into.addView(small(c, c.getString(R.string.bill_waiting_note, MoneyFormat.format(waiting)), 8));
        }
        if (plainLine != null && !plainLine.isEmpty()) into.addView(small(c, plainLine, 8));
    }

    /** "No amenities added", in the secondary colour. */
    static TextView noAmenities(Context c) {
        return small(c, c.getString(R.string.breakdown_no_amenities), 6);
    }

    /** One date range: heading, rooms, its amenities and (with several ranges) its subtotal. */
    static void renderStay(Context c, ViewGroup into, StayBlock stay, boolean withSubtotal) {
        if (stay.heading != null) into.addView(heading(c, stay.heading));
        for (Line l : stay.rooms) {
            into.addView(row(c, l.label, l.amount, false));
            if (l.note != null) into.addView(small(c, l.note, 0));
        }
        for (AmenityGroup g : stay.amenities) {
            if (g.lines.isEmpty()) continue;
            if (g.room == null) into.addView(small(c, c.getString(R.string.breakdown_stay_amenities_heading), 4));
            for (Line l : g.lines) {
                into.addView(row(c, g.room == null ? l.label : c.getString(R.string.breakdown_room_amenity_format, g.room, l.label), l.amount, false));
            }
        }
        if (withSubtotal) into.addView(row(c, c.getString(R.string.breakdown_stay_subtotal), stay.subtotal, true));
    }

    private static View row(Context c, String label, double amount, boolean bold) {
        String shown = amount < 0 ? "−" + MoneyFormat.format(-amount) : MoneyFormat.format(amount);
        return ReviewSheet.row(c, bold ? ReviewSheet.Row.bold(label, shown) : ReviewSheet.Row.of(label, shown));
    }

    private static TextView heading(Context c, String text) {
        TextView tv = new TextView(c);
        tv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tv.setText(text);
        tv.setTextSize(13f);
        tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tv.setTextColor(ContextCompat.getColor(c, R.color.velocity_text_primary));
        tv.setPadding(0, dp(c, 8), 0, dp(c, 2));
        return tv;
    }

    /** A small secondary-colour line ("No amenities added", the discount note). */
    private static TextView small(Context c, String text, int topDp) {
        TextView tv = new TextView(c);
        tv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tv.setText(text);
        tv.setTextSize(12f);
        tv.setTextColor(ContextCompat.getColor(c, R.color.velocity_text_secondary));
        tv.setPadding(0, dp(c, topDp), 0, dp(c, 2));
        return tv;
    }

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
