package com.example.velocitysuites;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Pure-JVM coverage for PaymentPercentageUtil - the exact conversion previously only
 * reachable inside PaymentActivity (an Activity, so untestable without a full Android
 * runtime). Exists specifically to make the historical "50% -> 5000%" double-multiplication
 * bug structurally provable-absent, not just "believed fixed by reading the code."
 */
public class PaymentPercentageTest {

    @Test
    public void fractionToApiPercentage_convertsEveryFixedTier() {
        assertEquals(20, PaymentPercentageUtil.fractionToApiPercentage(0.20));
        assertEquals(30, PaymentPercentageUtil.fractionToApiPercentage(0.30));
        assertEquals(40, PaymentPercentageUtil.fractionToApiPercentage(0.40));
        assertEquals(50, PaymentPercentageUtil.fractionToApiPercentage(0.50));
        assertEquals(100, PaymentPercentageUtil.fractionToApiPercentage(1.00));
    }

    @Test
    public void apiPercentageToFraction_convertsEveryFixedTier() {
        assertEquals(0.20, PaymentPercentageUtil.apiPercentageToFraction(20), 0.0001);
        assertEquals(0.30, PaymentPercentageUtil.apiPercentageToFraction(30), 0.0001);
        assertEquals(0.40, PaymentPercentageUtil.apiPercentageToFraction(40), 0.0001);
        assertEquals(0.50, PaymentPercentageUtil.apiPercentageToFraction(50), 0.0001);
        assertEquals(1.00, PaymentPercentageUtil.apiPercentageToFraction(100), 0.0001);
    }

    @Test
    public void roundTrip_fractionToApiAndBack_isLossless() {
        for (double fraction : new double[]{0.20, 0.30, 0.40, 0.50, 1.00}) {
            int api = PaymentPercentageUtil.fractionToApiPercentage(fraction);
            double roundTripped = PaymentPercentageUtil.apiPercentageToFraction(api);
            assertEquals(fraction, roundTripped, 0.0001);
        }
    }

    @Test
    public void formatApiPercentageForDisplay_neverDoubleMultiplies() {
        // The historical bug: an already-whole API value (50) got multiplied by 100 a
        // second time somewhere in the display path, rendering "5000%" instead of "50%".
        // formatApiPercentageForDisplay() takes the whole API value directly - never a
        // 0.20-1.00 fraction - so there is no second multiplication possible.
        assertEquals("20%", PaymentPercentageUtil.formatApiPercentageForDisplay(20));
        assertEquals("30%", PaymentPercentageUtil.formatApiPercentageForDisplay(30));
        assertEquals("40%", PaymentPercentageUtil.formatApiPercentageForDisplay(40));
        assertEquals("50%", PaymentPercentageUtil.formatApiPercentageForDisplay(50));
        assertEquals("100%", PaymentPercentageUtil.formatApiPercentageForDisplay(100));
    }

    @Test
    public void formatApiPercentageForDisplay_roundsAwayStrayDecimals() {
        // Booking#getSelectedPaymentPercentage() is a Double from JSON - defensively
        // handle a value that arrives as e.g. 50.0 exactly, never showing "50.0%".
        assertEquals("50%", PaymentPercentageUtil.formatApiPercentageForDisplay(50.0));
    }
}
