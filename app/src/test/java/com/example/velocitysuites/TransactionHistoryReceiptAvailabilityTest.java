package com.example.velocitysuites;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Pure-JVM coverage for the receipt-availability data
 * PaymentTransactionAdapter#onBindViewHolder() reads off booking.getReceipts()
 * for the Transaction History "N Receipt(s) Available" indicator (Phase 5
 * §10/§11/§20) - the adapter itself is a RecyclerView.Adapter (needs a real
 * Context to bind), so isn't unit-testable here; this covers the exact data
 * contract it reads instead.
 */
public class TransactionHistoryReceiptAvailabilityTest {

    private static Booking booking() {
        return new Booking("B1", "R1", "Deluxe 101", "Deluxe",
                "May 01, 2025", "May 05, 2025", 2, 10000.0, "Confirmed", "Apr 30, 2025");
    }

    private static Booking.ReceiptSummary receipt(String number, String type) {
        return new Booking.ReceiptSummary(number, type, "issued", 5000.0, 50, "2026-09-20T10:00:00Z");
    }

    @Test
    public void zeroReceipts_producesNoFalseIndicator() {
        Booking b = booking();
        assertTrue(b.getReceipts().isEmpty());
    }

    @Test
    public void onePartialReceipt_reportsOneAvailable() {
        Booking b = booking();
        b.setReceipts(Arrays.asList(receipt("PR-20260925-000001", "PARTIAL_RECEIPT")));

        assertEquals(1, b.getReceipts().size());
        assertNotNull(b.findReceipt("PR-20260925-000001"));
    }

    @Test
    public void partialAndOfficial_bothRemainIndependentlyAccessible() {
        Booking b = booking();
        b.setReceipts(Arrays.asList(
                receipt("PR-20260925-000001", "PARTIAL_RECEIPT"),
                receipt("OR-20260925-000099", "OFFICIAL_RECEIPT")
        ));

        assertEquals(2, b.getReceipts().size());
        Booking.ReceiptSummary pr = b.findReceipt("PR-20260925-000001");
        Booking.ReceiptSummary or = b.findReceipt("OR-20260925-000099");
        assertNotNull(pr);
        assertNotNull(or);
        assertEquals("PARTIAL_RECEIPT", pr.receiptType);
        assertEquals("OFFICIAL_RECEIPT", or.receiptType);
        // Neither collapses into "latest receipt only" - both still resolvable
        // independently by their own exact receipt_number.
        assertTrue(pr != or);
    }

    @Test
    public void fullPaymentAndOfficial_bothRemainIndependentlyAccessible() {
        Booking b = booking();
        b.setReceipts(Arrays.asList(
                receipt("FR-20260925-000002", "FULL_PAYMENT_RECEIPT"),
                receipt("OR-20260925-000099", "OFFICIAL_RECEIPT")
        ));

        assertEquals(2, b.getReceipts().size());
        assertNotNull(b.findReceipt("FR-20260925-000002"));
        assertNotNull(b.findReceipt("OR-20260925-000099"));
    }

    @Test
    public void findReceipt_returnsNullForAnUnknownReceiptNumber_neverGuessesLatest() {
        Booking b = booking();
        b.setReceipts(Arrays.asList(receipt("PR-20260925-000001", "PARTIAL_RECEIPT")));

        assertNull(b.findReceipt("OR-99999999-999999"));
    }

    @Test
    public void setReceiptsWithNull_defaultsToEmptyListNeverNull() {
        Booking b = booking();
        b.setReceipts(null);

        assertNotNull(b.getReceipts());
        assertTrue(b.getReceipts().isEmpty());
    }
}
