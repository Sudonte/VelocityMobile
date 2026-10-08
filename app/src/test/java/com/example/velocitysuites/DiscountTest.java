package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class DiscountTest {

    private static Discount d(String id, String name, String type, double value, String status) {
        return new Discount(id, name, type, value, "desc", status, "2026-09-26T03:16:05.000000Z", null);
    }

    @Test
    public void activeOnly_dropsInactiveAndBrokenRows() {
        List<Discount> all = new ArrayList<>();
        all.add(d("1", "Senior Citizen", "percentage", 20, "active"));
        all.add(d("2", "Retired", "percentage", 5, "inactive"));
        all.add(d("3", "Case", "fixed", 100, "Active"));
        all.add(d("4", "   ", "fixed", 100, "active"));
        all.add(d(null, "No id", "fixed", 100, "active"));
        all.add(null);
        List<Discount> result = Discount.activeOnly(all);
        assertEquals(2, result.size());
        assertEquals("Senior Citizen", result.get(0).getName());
        assertEquals("Case", result.get(1).getName());
    }

    @Test
    public void activeOnly_treatsMissingStatusAsActive_andNullListAsEmpty() {
        List<Discount> all = new ArrayList<>();
        all.add(new Discount("9", "Legacy", "percentage", 10, "x"));
        assertEquals(1, Discount.activeOnly(all).size());
        assertTrue(Discount.activeOnly(null).isEmpty());
    }

    @Test
    public void valueLabel_formatsPercentageAndFixed() {
        assertEquals("20%", d("1", "a", "percentage", 20, "active").getValueLabel());
        assertEquals("12.5%", d("1", "a", "percentage", 12.5, "active").getValueLabel());
        assertEquals("₱500.00", d("1", "a", "fixed", 500, "active").getValueLabel());
        assertEquals("₱1,250.50", d("1", "a", "fixed", 1250.5, "active").getValueLabel());
        assertEquals("20% OFF", d("1", "a", "percentage", 20, "active").getBadgeLabel());
    }

    @Test
    public void wizardState_setDiscountKeepsLegacyIdTypeInSync() {
        BookingWizardState state = new BookingWizardState(BookingWizardState.Mode.BOOKING);
        assertEquals("None", state.idCardType);
        assertNull(state.discountIdOrNull());
        assertFalse(state.discountNeedsId());

        state.setDiscount(d("7", "VIP", "percentage", 15, "active"));
        assertEquals("VIP", state.idCardType);
        assertEquals(Long.valueOf(7), state.discountIdOrNull());
        assertTrue("a chosen discount needs an ID", state.discountNeedsId());

        state.idCardOnFile = true; // edit mode: ID already stored
        assertFalse("a stored ID satisfies it", state.discountNeedsId());
        state.removeIdCard = true;
        assertTrue("...until the guest removes it", state.discountNeedsId());

        state.setDiscount(null);
        assertEquals("None", state.idCardType);
        assertFalse(state.discountNeedsId());
    }
}
