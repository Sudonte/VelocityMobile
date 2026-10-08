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

    private static Discount dated(String start, String end) {
        return new Discount("1", "X", "percentage", 10, "d", "active", null, null, start, end);
    }

    @Test
    public void validity_isInclusive_andEmptySidesMeanNoLimit() {
        java.time.LocalDate day = java.time.LocalDate.of(2026, 10, 8);
        assertTrue(dated(null, null).isValidOn(day));
        assertTrue("last day", dated(null, "2026-10-08").isValidOn(day));
        assertTrue("first day", dated("2026-10-08", null).isValidOn(day));
        assertFalse("ended yesterday", dated(null, "2026-10-07").isValidOn(day));
        assertFalse("starts tomorrow", dated("2026-10-09", null).isValidOn(day));
        assertTrue(dated("2026-10-01", "2026-12-31").isValidOn(day));
    }

    @Test
    public void validityLabel_readsLikeTheWeb() {
        assertEquals("No expiry", dated(null, null).validityLabel());
        assertEquals("Valid until Dec 31, 2026", dated(null, "2026-12-31").validityLabel());
        assertEquals("Valid from Oct 1, 2026", dated("2026-10-01", null).validityLabel());
        assertEquals("Valid Oct 1, 2026 - Dec 31, 2026", dated("2026-10-01", "2026-12-31").validityLabel());
        assertEquals("a timestamp from the API still parses", "Valid until Dec 31, 2026", dated(null, "2026-12-31T00:00:00.000000Z").validityLabel());
    }

    @Test
    public void activeOnly_dropsExpiredAndNotYetStartedDiscounts() {
        java.util.List<Discount> all = new ArrayList<>();
        all.add(dated(null, null));
        all.add(new Discount("2", "Expired", "percentage", 10, "d", "active", null, null, null, "2020-01-01"));
        all.add(new Discount("3", "Future", "percentage", 10, "d", "active", null, null, "2999-01-01", null));
        all.add(new Discount("4", "Forever", "percentage", 10, "d", "active", null, null, "2020-01-01", "2999-12-31"));
        java.util.List<Discount> result = Discount.activeOnly(all);
        assertEquals(2, result.size());
        assertEquals("X", result.get(0).getName());
        assertEquals("Forever", result.get(1).getName());
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
    public void estimateOff_followsTheModuleValueAndNeverExceedsTheBase() {
        assertEquals(400.0, d("1", "Senior Citizen", "percentage", 20, "active").estimateOff(2000), 0.001);
        assertEquals(500.0, d("1", "Senior Citizen", "percentage", 25, "active").estimateOff(2000), 0.001);
        assertEquals(150.0, d("1", "PWD", "fixed", 150, "active").estimateOff(2000), 0.001);
        assertEquals(100.0, d("1", "PWD", "fixed", 150, "active").estimateOff(100), 0.001);
        assertEquals("a discount known only by name has no value yet", 0.0, new Discount(null, "VIP", null, 0, null).estimateOff(2000), 0.0);
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
