package com.example.velocitysuites;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Pure-JVM coverage for ReceiptTypeMapper - the exact PARTIAL_RECEIPT/
 * FULL_PAYMENT_RECEIPT/OFFICIAL_RECEIPT label contract from
 * PAYMENT_RECEIPT_HISTORY_BACKEND_SPEC.md §13, plus the "never crash on an
 * unrecognized value" and "never infer from percentage/balance" guarantees.
 */
public class ReceiptTypeMapperTest {

    @Test
    public void partialReceipt_labelsAsPartialPaymentReceipt() {
        assertEquals("Partial Payment Receipt", ReceiptTypeMapper.labelForReceiptType("PARTIAL_RECEIPT"));
    }

    @Test
    public void fullPaymentReceipt_labelsAsPaymentReceipt_neverOfficial() {
        // The exact case the spec calls out: a verified 100% pre-checkout
        // payment must render as "Payment Receipt", never "Official Payment
        // Receipt" - that label is checkout-only.
        String label = ReceiptTypeMapper.labelForReceiptType("FULL_PAYMENT_RECEIPT");
        assertEquals("Payment Receipt", label);
        assertNotEqualsOfficial(label);
    }

    @Test
    public void officialReceipt_labelsAsOfficialPaymentReceipt() {
        assertEquals("Official Payment Receipt", ReceiptTypeMapper.labelForReceiptType("OFFICIAL_RECEIPT"));
    }

    @Test
    public void unknownFutureReceiptType_fallsBackToGenericPaymentReceipt_neverCrashes() {
        assertEquals("Payment Receipt", ReceiptTypeMapper.labelForReceiptType("SOME_NEW_RECEIPT_TYPE_FROM_A_FUTURE_BACKEND"));
    }

    @Test
    public void nullReceiptType_fallsBackToGenericPaymentReceipt() {
        assertEquals("Payment Receipt", ReceiptTypeMapper.labelForReceiptType(null));
    }

    @Test
    public void transactionTypes_labelCorrectly() {
        assertEquals("Partial Payment", ReceiptTypeMapper.labelForTransactionType("PARTIAL_PAYMENT"));
        assertEquals("Full Payment", ReceiptTypeMapper.labelForTransactionType("FULL_PAYMENT"));
        assertEquals("Checkout Payment", ReceiptTypeMapper.labelForTransactionType("CHECKOUT_PAYMENT"));
    }

    @Test
    public void unknownTransactionType_titleCasesRatherThanCrashing() {
        assertEquals("Some New Type", ReceiptTypeMapper.labelForTransactionType("SOME_NEW_TYPE"));
    }

    @Test
    public void nullTransactionType_fallsBackGracefully() {
        assertEquals("Payment", ReceiptTypeMapper.labelForTransactionType(null));
    }

    private static void assertNotEqualsOfficial(String label) {
        if ("Official Payment Receipt".equals(label)) {
            throw new AssertionError("FULL_PAYMENT_RECEIPT must never render as Official Payment Receipt");
        }
    }
}
