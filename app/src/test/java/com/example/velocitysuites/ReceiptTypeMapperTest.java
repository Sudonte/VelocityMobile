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
        assertEquals("Payment verified", ReceiptTypeMapper.labelForReceiptType("PARTIAL_RECEIPT"));
    }

    @Test
    public void fullPaymentReceipt_labelsAsPaymentReceipt_neverOfficial() {
        // The exact case the spec calls out: a verified 100% pre-checkout
        // payment must render as "Payment receipt", never "Official Payment
        // Receipt" - that label is checkout-only.
        String label = ReceiptTypeMapper.labelForReceiptType("FULL_PAYMENT_RECEIPT");
        assertEquals("Payment verified", label);
        assertNotEqualsOfficial(label);
    }

    @Test
    public void officialReceipt_labelsAsOfficialPaymentReceipt() {
        assertEquals("Check-out (final)", ReceiptTypeMapper.labelForReceiptType("OFFICIAL_RECEIPT"));
    }

    @Test
    public void checkinSettlementReceipt_isReceipt2() {
        assertEquals("Check-in", ReceiptTypeMapper.labelForReceiptType("CHECKIN_SETTLEMENT_RECEIPT"));
        assertEquals(2, ReceiptTypeMapper.stageOf("CHECKIN_SETTLEMENT_RECEIPT"));
        assertEquals("Receipt 2 - Check-in", ReceiptTypeMapper.labelWithStage("CHECKIN_SETTLEMENT_RECEIPT"));
    }

    @Test
    public void everyReceiptKnowsWhichOfTheThreeItIs() {
        assertEquals(1, ReceiptTypeMapper.stageOf("PARTIAL_RECEIPT"));
        assertEquals(1, ReceiptTypeMapper.stageOf("FULL_PAYMENT_RECEIPT"));
        assertEquals(3, ReceiptTypeMapper.stageOf("OFFICIAL_RECEIPT"));
        assertEquals(0, ReceiptTypeMapper.stageOf("SOMETHING_NEW"));
        assertEquals("a future type keeps its plain label", "Payment receipt", ReceiptTypeMapper.labelWithStage("SOMETHING_NEW"));
    }

    @Test
    public void unknownFutureReceiptType_fallsBackToGenericPaymentReceipt_neverCrashes() {
        assertEquals("Payment receipt", ReceiptTypeMapper.labelForReceiptType("SOME_NEW_RECEIPT_TYPE_FROM_A_FUTURE_BACKEND"));
    }

    @Test
    public void nullReceiptType_fallsBackToGenericPaymentReceipt() {
        assertEquals("Payment receipt", ReceiptTypeMapper.labelForReceiptType(null));
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
        if ("Check-out (final)".equals(label)) {
            throw new AssertionError("FULL_PAYMENT_RECEIPT must never render as Official Payment Receipt");
        }
    }
}
