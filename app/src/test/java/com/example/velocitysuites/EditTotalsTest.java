package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class EditTotalsTest {

    @Test
    public void balanceDue_whenTheNewTotalExceedsWhatWasPaid() {
        EditTotals t = EditTotals.of(2000, 4000, 3000);
        assertEquals(2000.0, t.oldTotal, 0.001);
        assertEquals(4000.0, t.newTotal, 0.001);
        assertEquals(3000.0, t.amountPaid, 0.001);
        assertEquals(1000.0, t.balanceDue, 0.001);
        assertEquals(0.0, t.excess, 0.001);
    }

    @Test
    public void excess_whenTheNewTotalDropsBelowWhatWasPaid() {
        EditTotals t = EditTotals.of(4000, 2000, 3000);
        assertEquals(0.0, t.balanceDue, 0.001);
        assertEquals(1000.0, t.excess, 0.001);
    }

    @Test
    public void exactlyCovered_hasNeitherBalanceNorExcess_evenWithFloatDrift() {
        EditTotals t = EditTotals.of(100, 0.1 + 0.2, 0.3);
        assertEquals(0.0, t.balanceDue, 0.0);
        assertEquals(0.0, t.excess, 0.0);
    }

    @Test
    public void negativePaidIsTreatedAsNothingPaid() {
        EditTotals t = EditTotals.of(0, 500, -20);
        assertEquals(0.0, t.amountPaid, 0.0);
        assertEquals(500.0, t.balanceDue, 0.001);
    }
}
