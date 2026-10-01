package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MoneyFormatTest {

    @Test
    public void formatsWithPesoSignGroupingAndTwoDecimals() {
        assertEquals("₱1,800.00", MoneyFormat.format(1800));
        assertEquals("₱0.00", MoneyFormat.format(0));
        assertEquals("₱900.50", MoneyFormat.format(900.5));
        assertEquals("₱1,234,567.89", MoneyFormat.format(1234567.891));
    }

    @Test
    public void formatIsLocaleIndependent() {
        java.util.Locale previous = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY); // would print "1.800,00" with a locale-aware formatter
            assertEquals("₱1,800.00", MoneyFormat.format(1800));
        } finally {
            java.util.Locale.setDefault(previous);
        }
    }

    @Test
    public void negativeAmountsAreSigned_butNegativeZeroIsNot() {
        assertEquals("-₱5.00", MoneyFormat.format(-5));
        assertEquals("₱0.00", MoneyFormat.format(-0.001));
    }

    @Test
    public void nonFiniteNumbersPrintAsZero_neverNaN() {
        assertEquals("₱0.00", MoneyFormat.format(Double.NaN));
        assertEquals("₱0.00", MoneyFormat.format(Double.POSITIVE_INFINITY));
    }

    @Test
    public void parseReadsPlainAndFormattedAmounts() {
        assertEquals(1800.00, MoneyFormat.parse("1800.00"), 0);
        assertEquals(1800.00, MoneyFormat.parse("₱1,800.00"), 0);
        assertEquals(1800.00, MoneyFormat.parse(" PHP 1,800.00 "), 0);
        assertEquals(-5.5, MoneyFormat.parse("-5.5"), 0);
    }

    @Test
    public void parseNeverThrows_badInputIsZero() {
        assertEquals(0, MoneyFormat.parse(null), 0);
        assertEquals(0, MoneyFormat.parse(""), 0);
        assertEquals(0, MoneyFormat.parse("   "), 0);
        assertEquals(0, MoneyFormat.parse("N/A"), 0);
        assertEquals(0, MoneyFormat.parse("abc"), 0);
        assertEquals(0, MoneyFormat.parse("NaN"), 0);
        assertEquals(0, MoneyFormat.parse("Infinity"), 0);
    }

    @Test
    public void coversToleratesHalfACentavoOfRounding() {
        assertTrue(MoneyFormat.covers(1799.996, 1800));
        assertFalse(MoneyFormat.covers(1799.99, 1800));
        assertTrue(MoneyFormat.covers(0, 0));
    }
}
