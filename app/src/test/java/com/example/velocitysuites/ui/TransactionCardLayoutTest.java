package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.Booking;
import com.example.velocitysuites.R;
import com.example.velocitysuites.TransactionHistoryAdapter;
import com.example.velocitysuites.TransactionRow;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The Transaction History card, measured for real at the widths and system font sizes where the previous
 * card broke (the PENDING badge ended up on top of the divider and the "Stay" row at the reporter's large
 * font). Also pins what the card SAYS: the grand total (not P0.00), the paid/balance line, the submitted-
 * awaiting-verification line, and a distinct badge per status.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TransactionCardLayoutTest {

    private static final int[] WIDTHS_DP = {320, 360, 411};
    private static final float[] FONT_SCALES = {1.0f, 1.15f, 1.3f, 1.5f};

    // ---- fixtures: one per status ----

    private static Booking base(String id, String status, double total) {
        Booking b = new Booking(id, "1", "Deluxe", "Deluxe", "Oct 01, 2026", "Oct 02, 2026", 2, total, status, "");
        b.setHasBooking(false);
        return b;
    }

    private static void pay(Booking b, String amount, String status) {
        List<Booking.PaymentRecord> rows = new ArrayList<>(b.getPaymentHistory());
        rows.add(new Booking.PaymentRecord(amount, "GCASH", "1234567890123", "Sep 30, 2026 • 10:09 PM", status));
        b.setPaymentHistory(rows);
        if ("pending".equals(status)) b.setPaymentPendingVerification(true);
    }

    /** The exact case from the bug report: Reservation #588, Deluxe, a P1,800.00 payment submitted but not yet verified. */
    private static Booking pendingSubmitted() {
        Booking b = base("588", "Pending", 1800);
        pay(b, "1800.00", "pending");
        return b;
    }

    private static Booking paid() {
        Booking b = base("589", "Confirmed", 1800);
        b.setHasBooking(true);
        pay(b, "1800.00", "completed");
        return b;
    }

    private static Booking partiallyPaid() {
        Booking b = base("590", "Confirmed", 1800);
        b.setHasBooking(true);
        pay(b, "900.00", "completed");
        return b;
    }

    private static Booking partiallyPaidWithAnotherAwaitingReview() {
        Booking b = partiallyPaid();
        pay(b, "900.00", "pending");
        return b;
    }

    private static Booking cancelled() {
        return base("591", "Cancelled", 1800);
    }

    private static Booking rejected() {
        return base("592", "Rejected", 1800);
    }

    private static List<Booking> oneOfEachStatus() {
        return Arrays.asList(pendingSubmitted(), paid(), partiallyPaid(), cancelled(), rejected(), base("593", "Pending", 1800));
    }

    // ---- harness ----

    private static View card(Context context, Booking booking, int widthDp) {
        TransactionHistoryAdapter adapter = new TransactionHistoryAdapter(row -> { });
        adapter.submit(TransactionRow.fromAll(Collections.singletonList(booking)));
        FrameLayout parent = LayoutHarness.parentOfWidth(context, widthDp);
        TransactionHistoryAdapter.ViewHolder holder = adapter.onCreateViewHolder(parent, 0);
        adapter.onBindViewHolder(holder, 0);
        LayoutHarness.layoutAtWidth(holder.itemView, context, widthDp);
        return holder.itemView;
    }

    private static String where(Context context, View root, float font, int width) {
        return "font=" + font + " width=" + width + "dp\n" + LayoutHarness.dump(context, root);
    }

    private static TextView text(View root, int id) {
        return root.findViewById(id);
    }

    // ---- the overlap bug ----

    @Test
    public void nothingOverlapsAnythingElse_forEveryStatus_atEverySizeAndWidth() {
        for (Booking booking : oneOfEachStatus()) {
            for (float font : FONT_SCALES) {
                for (int width : WIDTHS_DP) {
                    Context context = LayoutHarness.themedContext(font);
                    View root = card(context, booking, width);
                    String ctx = where(context, root, font, width);

                    View badge = root.findViewById(R.id.tvTxnStatusBadge);
                    View ref = root.findViewById(R.id.tvTxnRef);
                    View chevron = root.findViewById(R.id.ivTxnChevron);
                    View room = root.findViewById(R.id.tvTxnRoom);
                    View total = root.findViewById(R.id.tvTxnTotal);
                    View divider = root.findViewById(R.id.dividerTxn);
                    View stay = root.findViewById(R.id.tvTxnStay);
                    View stayLabel = root.findViewById(R.id.tvTxnStayLabel);
                    View payment = root.findViewById(R.id.tvTxnPayment);
                    View paymentLabel = root.findViewById(R.id.tvTxnPaymentLabel);

                    // Row 1: badge | reference | chevron - side by side, never on top of each other
                    assertFalse("badge/ref\n" + ctx, LayoutHarness.overlaps(root, badge, ref));
                    assertFalse("ref/chevron\n" + ctx, LayoutHarness.overlaps(root, ref, chevron));
                    assertFalse("badge/chevron\n" + ctx, LayoutHarness.overlaps(root, badge, chevron));
                    // Row 2: room | total
                    assertFalse("room/total\n" + ctx, LayoutHarness.overlaps(root, room, total));
                    // THE reported bug: nothing in the rows above may touch the divider or the rows below
                    Rect dividerRect = LayoutHarness.boundsIn(root, divider);
                    for (View above : new View[]{badge, ref, chevron, room, total}) {
                        assertTrue("row above the divider pokes into it\n" + ctx,
                                LayoutHarness.boundsIn(root, above).bottom <= dividerRect.top);
                    }
                    for (View below : new View[]{stay, stayLabel, payment, paymentLabel}) {
                        assertTrue("row below starts under the divider\n" + ctx,
                                LayoutHarness.boundsIn(root, below).top >= dividerRect.bottom);
                    }
                    assertFalse("stay/payment\n" + ctx, LayoutHarness.overlaps(root, stay, payment));
                    assertFalse("stay label/value\n" + ctx, LayoutHarness.overlaps(root, stayLabel, stay));
                    assertFalse("payment label/value\n" + ctx, LayoutHarness.overlaps(root, paymentLabel, payment));
                }
            }
        }
    }

    @Test
    public void everythingStaysInsideTheCard() {
        for (Booking booking : oneOfEachStatus()) {
            for (float font : FONT_SCALES) {
                for (int width : WIDTHS_DP) {
                    Context context = LayoutHarness.themedContext(font);
                    View root = card(context, booking, width);
                    Rect card = LayoutHarness.boundsIn(root, root.findViewById(R.id.cardTransaction));
                    for (int id : new int[]{R.id.tvTxnStatusBadge, R.id.tvTxnRef, R.id.ivTxnChevron, R.id.tvTxnRoom, R.id.tvTxnTotal,
                            R.id.dividerTxn, R.id.tvTxnStay, R.id.tvTxnPayment}) {
                        assertTrue("view " + context.getResources().getResourceEntryName(id) + " must be inside the card\n"
                                + where(context, root, font, width), card.contains(LayoutHarness.boundsIn(root, root.findViewById(id))));
                    }
                }
            }
        }
    }

    @Test
    public void row1_badgeLeft_referenceNextToIt_chevronFarRight() {
        Context context = LayoutHarness.themedContext(1.0f);
        View root = card(context, pendingSubmitted(), 360);
        Rect badge = LayoutHarness.boundsIn(root, root.findViewById(R.id.tvTxnStatusBadge));
        Rect ref = LayoutHarness.boundsIn(root, root.findViewById(R.id.tvTxnRef));
        Rect chevron = LayoutHarness.boundsIn(root, root.findViewById(R.id.ivTxnChevron));
        Rect card = LayoutHarness.boundsIn(root, root.findViewById(R.id.cardTransaction));
        assertTrue(badge.left < ref.left);
        assertTrue("reference sits right next to the badge", ref.left - badge.right <= LayoutHarness.dp(context, 12));
        assertTrue("chevron hugs the right edge", chevron.right >= card.right - LayoutHarness.dp(context, 18));
        assertTrue("one row", Math.min(badge.bottom, chevron.bottom) > Math.max(badge.top, chevron.top));
    }

    @Test
    public void row2_roomLeft_grandTotalRight() {
        Context context = LayoutHarness.themedContext(1.0f);
        View root = card(context, pendingSubmitted(), 360);
        Rect room = LayoutHarness.boundsIn(root, root.findViewById(R.id.tvTxnRoom));
        Rect total = LayoutHarness.boundsIn(root, root.findViewById(R.id.tvTxnTotal));
        Rect card = LayoutHarness.boundsIn(root, root.findViewById(R.id.cardTransaction));
        assertTrue(room.left < total.left);
        assertTrue(total.right >= card.right - LayoutHarness.dp(context, 18));
    }

    @Test
    public void stayAndPaymentValuesAlignOnTheSameColumn() {
        for (float font : FONT_SCALES) {
            Context context = LayoutHarness.themedContext(font);
            View root = card(context, partiallyPaid(), 360);
            Rect stay = LayoutHarness.boundsIn(root, root.findViewById(R.id.tvTxnStay));
            Rect payment = LayoutHarness.boundsIn(root, root.findViewById(R.id.tvTxnPayment));
            assertEquals("values share a column\n" + where(context, root, font, 360), stay.left, payment.left);
        }
    }

    // ---- the money: what the card says ----

    @Test
    public void showsTheGrandTotal_notZero() {
        Context context = LayoutHarness.themedContext(1.0f);
        View root = card(context, pendingSubmitted(), 360);
        assertEquals("₱1,800.00", text(root, R.id.tvTxnTotal).getText().toString());
        assertFalse(text(root, R.id.tvTxnTotal).getText().toString().contains("0.00") && text(root, R.id.tvTxnTotal).getText().toString().equals("₱0.00"));
    }

    @Test
    public void aSubmittedPayment_showsSubmittedAndAwaitingVerification_notNotYetPaid() {
        Context context = LayoutHarness.themedContext(1.0f);
        View root = card(context, pendingSubmitted(), 360);
        assertEquals("₱1,800.00 submitted • awaiting verification", text(root, R.id.tvTxnPayment).getText().toString());
        assertEquals("Pending", text(root, R.id.tvTxnStatusBadge).getText().toString());
    }

    @Test
    public void aPartialPayment_showsPaidAndBalance() {
        Context context = LayoutHarness.themedContext(1.0f);
        View root = card(context, partiallyPaid(), 360);
        assertEquals("₱900.00 paid • ₱900.00 balance", text(root, R.id.tvTxnPayment).getText().toString());
        assertEquals("Partially Paid", text(root, R.id.tvTxnStatusBadge).getText().toString());
    }

    @Test
    public void aFullyPaidTransaction_showsPaidAndZeroBalance() {
        Context context = LayoutHarness.themedContext(1.0f);
        View root = card(context, paid(), 360);
        assertEquals("₱1,800.00 paid • ₱0.00 balance", text(root, R.id.tvTxnPayment).getText().toString());
        assertEquals("Paid", text(root, R.id.tvTxnStatusBadge).getText().toString());
    }

    @Test
    public void verifiedPartialPlusASecondPaymentAwaitingReview_showsBothLines() {
        Context context = LayoutHarness.themedContext(1.0f);
        View root = card(context, partiallyPaidWithAnotherAwaitingReview(), 360);
        assertEquals("₱900.00 paid • ₱900.00 balance\n₱900.00 submitted • awaiting verification",
                text(root, R.id.tvTxnPayment).getText().toString());
    }

    @Test
    public void aNewTransactionWithNoPayment_saysSoWithTheBalance() {
        Context context = LayoutHarness.themedContext(1.0f);
        View root = card(context, base("593", "Pending", 1800), 360);
        assertEquals("₱0.00 paid • ₱1,800.00 balance", text(root, R.id.tvTxnPayment).getText().toString());
    }

    @Test
    public void cancelledAndRejected_doNotClaimABalanceIsOwed() {
        Context context = LayoutHarness.themedContext(1.0f);
        assertEquals("No payment made", text(card(context, cancelled(), 360), R.id.tvTxnPayment).getText().toString());
        assertEquals("No payment made", text(card(context, rejected(), 360), R.id.tvTxnPayment).getText().toString());
        Booking paidThenCancelled = cancelled();
        pay(paidThenCancelled, "900.00", "completed");
        assertEquals("₱900.00 paid", text(card(context, paidThenCancelled, 360), R.id.tvTxnPayment).getText().toString());
    }

    @Test
    public void referenceAndStayTextFollowTheSpec() {
        Context context = LayoutHarness.themedContext(1.0f);
        View root = card(context, pendingSubmitted(), 360);
        assertEquals("Reservation #588", text(root, R.id.tvTxnRef).getText().toString());
        assertEquals("Oct 01, 2026 – Oct 02, 2026", text(root, R.id.tvTxnStay).getText().toString());
        assertEquals("Deluxe", text(root, R.id.tvTxnRoom).getText().toString());
        assertEquals("Booking #589", text(card(context, paid(), 360), R.id.tvTxnRef).getText().toString());
    }

    @Test
    public void missingData_showsDashes_notBlanksOrNull() {
        Context context = LayoutHarness.themedContext(1.0f);
        Booking b = base("9", "Pending", 0);
        b.setCheckInDate(null);
        b.setCheckOutDate(null);
        b.setRoomType(null);
        b.setRoomName(null);
        View root = card(context, b, 360);
        assertEquals("—", text(root, R.id.tvTxnRoom).getText().toString());
        assertEquals("—", text(root, R.id.tvTxnStay).getText().toString());
        assertFalse(text(root, R.id.tvTxnPayment).getText().toString().contains("null"));
    }

    // ---- badges ----

    @Test
    public void everyStatusHasItsOwnBadgeColor() {
        Context context = LayoutHarness.themedContext(1.0f);
        java.util.Set<Integer> fills = new java.util.HashSet<>();
        for (Booking b : Arrays.asList(pendingSubmitted(), paid(), partiallyPaid(), cancelled(), rejected())) {
            TextView badge = text(card(context, b, 360), R.id.tvTxnStatusBadge);
            assertNotNull(badge.getBackgroundTintList());
            fills.add(badge.getBackgroundTintList().getDefaultColor());
        }
        assertEquals("pending / paid / partially paid / cancelled / rejected each get a different fill", 5, fills.size());
    }

    @Test
    public void theBadgeUsesTheSharedStatusColors() {
        Context context = LayoutHarness.themedContext(1.0f);
        TextView pending = text(card(context, pendingSubmitted(), 360), R.id.tvTxnStatusBadge);
        assertEquals(context.getColor(R.color.status_pending_bg), pending.getBackgroundTintList().getDefaultColor());
        assertEquals(context.getColor(R.color.status_pending_fg), pending.getCurrentTextColor());
        TextView paid = text(card(context, paid(), 360), R.id.tvTxnStatusBadge);
        assertEquals(context.getColor(R.color.status_paid_bg), paid.getBackgroundTintList().getDefaultColor());
        TextView partial = text(card(context, partiallyPaid(), 360), R.id.tvTxnStatusBadge);
        assertEquals(context.getColor(R.color.status_partial_bg), partial.getBackgroundTintList().getDefaultColor());
    }

    // ---- stress ----

    @Test
    public void veryLongRoomNamesAndHugeTotals_stillNeverOverlap_andTheTotalIsNeverCut() {
        Booking b = base("123456", "Confirmed", 12345678.90);
        b.setHasBooking(true);
        b.setRooms(Arrays.asList(
                new com.example.velocitysuites.BookingRoom("1", "Executive Garden Suite with Private Balcony and Sea View", 3, 1000, 2, 6000, null),
                new com.example.velocitysuites.BookingRoom("2", "Presidential Penthouse", 1, 9000, 2, 18000, null)));
        pay(b, "5000000.00", "completed");
        pay(b, "1000000.00", "pending");
        for (float font : new float[]{1.0f, 1.3f, 1.5f}) {
            for (int width : WIDTHS_DP) {
                Context context = LayoutHarness.themedContext(font);
                View root = card(context, b, width);
                String ctx = where(context, root, font, width);
                TextView total = text(root, R.id.tvTxnTotal);
                assertEquals("total on one line\n" + ctx, 1, total.getLineCount());
                assertEquals("total not ellipsized\n" + ctx, 0, total.getLayout().getEllipsisCount(0));
                assertFalse(ctx, LayoutHarness.overlaps(root, root.findViewById(R.id.tvTxnRoom), total));
                assertFalse(ctx, LayoutHarness.overlaps(root, root.findViewById(R.id.tvTxnStatusBadge), root.findViewById(R.id.tvTxnRef)));
                Rect divider = LayoutHarness.boundsIn(root, root.findViewById(R.id.dividerTxn));
                assertTrue(ctx, LayoutHarness.boundsIn(root, root.findViewById(R.id.tvTxnRoom)).bottom <= divider.top);
            }
        }
    }

    @Test
    public void receiptsAvailableLineAppearsOnlyWhenThereAreReceipts() {
        Context context = LayoutHarness.themedContext(1.0f);
        assertEquals(View.GONE, card(context, paid(), 360).findViewById(R.id.tvTxnReceipts).getVisibility());
        Booking b = paid();
        b.setReceipts(Arrays.asList(
                new Booking.ReceiptSummary("PR-1", "PARTIAL_RECEIPT", "issued", 900, 50, null),
                new Booking.ReceiptSummary("OR-1", "OFFICIAL_RECEIPT", "issued", 1800, null, null)));
        View root = card(context, b, 360);
        TextView receipts = text(root, R.id.tvTxnReceipts);
        assertEquals(View.VISIBLE, receipts.getVisibility());
        assertEquals("2 Receipts Available", receipts.getText().toString());
        // The leading icon is attached in code, sized with the text (not the 24dp intrinsic size).
        android.graphics.drawable.Drawable icon = receipts.getCompoundDrawablesRelative()[0];
        assertNotNull("receipts line has its icon", icon);
        assertEquals(Math.round(receipts.getTextSize() * 1.1f), icon.getBounds().width());
        assertEquals(icon.getBounds().width(), icon.getBounds().height());
    }

    @Test
    public void theCardIsCompact() {
        Context context = LayoutHarness.themedContext(1.0f);
        View root = card(context, pendingSubmitted(), 360);
        float density = context.getResources().getDisplayMetrics().density;
        float heightDp = root.findViewById(R.id.cardTransaction).getHeight() / density;
        assertTrue("card is " + heightDp + "dp tall\n" + where(context, root, 1.0f, 360), heightDp <= 150);
    }
}
