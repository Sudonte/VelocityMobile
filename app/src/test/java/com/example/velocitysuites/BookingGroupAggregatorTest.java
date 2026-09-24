package com.example.velocitysuites;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Pure-JVM coverage for BookingGroupAggregator - consolidates what used to be two
 * separate, Activity-embedded (so untestable) copies of the same group-summing loop
 * in BookingDetailsActivity and PaymentReceiptActivity.
 */
public class BookingGroupAggregatorTest {

    private static Booking member(double totalAmount, double amountPaid, double roomCharge,
                                   double amenityCharge, double additionalGuestFee) {
        Booking b = new Booking("B" + System.nanoTime(), "R1", "Deluxe 101", "Deluxe",
                "May 01, 2025", "May 05, 2025", 2, totalAmount, "Confirmed", "Apr 30, 2025");
        b.setAmountPaid(amountPaid);
        b.setRoomCharge(roomCharge);
        b.setAmenityCharge(amenityCharge);
        b.setAdditionalGuestFee(additionalGuestFee);
        return b;
    }

    @Test
    public void emptyGroup_sumsToZero() {
        BookingGroupAggregator.Totals totals = BookingGroupAggregator.sum(Collections.emptyList());
        assertEquals(0, totals.totalAmount, 0.001);
        assertEquals(0, totals.amountPaid, 0.001);
        assertEquals(0, totals.roomCharge, 0.001);
        assertEquals(0, totals.amenityCharge, 0.001);
        assertEquals(0, totals.additionalGuestFee, 0.001);
    }

    @Test
    public void singleMember_matchesThatMemberExactly() {
        List<Booking> members = Collections.singletonList(member(10000.0, 2000.0, 9800.0, 200.0, 0.0));
        BookingGroupAggregator.Totals totals = BookingGroupAggregator.sum(members);
        assertEquals(10000.0, totals.totalAmount, 0.001);
        assertEquals(2000.0, totals.amountPaid, 0.001);
        assertEquals(9800.0, totals.roomCharge, 0.001);
        assertEquals(200.0, totals.amenityCharge, 0.001);
    }

    @Test
    public void twoMembers_sumsBothExactly() {
        // Deluxe x2 sibling (10,000 room-only, amenities attached here per the
        // index==0 convention) + Suite x1 sibling (16,000 room-only, no amenities).
        List<Booking> members = new ArrayList<>();
        members.add(member(10150.0, 10150.0, 10000.0, 150.0, 0.0));
        members.add(member(16000.0, 16000.0, 16000.0, 0.0, 0.0));

        BookingGroupAggregator.Totals totals = BookingGroupAggregator.sum(members);
        assertEquals(26150.0, totals.totalAmount, 0.001);
        assertEquals(26150.0, totals.amountPaid, 0.001);
        assertEquals(26000.0, totals.roomCharge, 0.001);
        assertEquals(150.0, totals.amenityCharge, 0.001);
    }

    @Test
    public void threeMembers_sumsAllThreeExactly() {
        List<Booking> members = new ArrayList<>();
        members.add(member(10000.0, 5000.0, 10000.0, 0.0, 0.0));
        members.add(member(16000.0, 8000.0, 16000.0, 0.0, 0.0));
        members.add(member(7500.0, 3750.0, 7350.0, 150.0, 0.0));

        BookingGroupAggregator.Totals totals = BookingGroupAggregator.sum(members);
        assertEquals(33500.0, totals.totalAmount, 0.001);
        assertEquals(16750.0, totals.amountPaid, 0.001);
        assertEquals(33350.0, totals.roomCharge, 0.001);
        assertEquals(150.0, totals.amenityCharge, 0.001);
    }

    @Test
    public void amountPaid_prefersEachMembersAuthoritativePaymentSummaryOverItsLegacyField() {
        // Phase 5 §11: once the backend has attached its own payment_summary to a
        // sibling, that member's legacy amountPaid (which may be stale/wrong - e.g.
        // undercounting an amenity paid separately) must not be summed instead.
        Booking withAuthoritativeSummary = member(10000.0, 2000.0, 10000.0, 0.0, 0.0);
        withAuthoritativeSummary.setPaymentSummary(new Booking.PaymentSummary(
                10000.0, 7000.0, 3000.0, "PARTIALLY_PAID", 70, false));
        Booking legacyOnly = member(16000.0, 8000.0, 16000.0, 0.0, 0.0);

        List<Booking> members = new ArrayList<>();
        members.add(withAuthoritativeSummary);
        members.add(legacyOnly);

        BookingGroupAggregator.Totals totals = BookingGroupAggregator.sum(members);
        // 7000 (authoritative) + 8000 (legacy fallback), never 2000 + 8000.
        assertEquals(15000.0, totals.amountPaid, 0.001);
    }

    @Test
    public void amenityChargeOnNonFirstMember_stillPickedUpRegardlessOfPosition() {
        // Same index==0 attachment convention, but verifying the sum doesn't
        // depend on WHICH position actually carries the non-zero amenityCharge.
        List<Booking> members = new ArrayList<>();
        members.add(member(10000.0, 10000.0, 10000.0, 0.0, 0.0));
        members.add(member(16150.0, 16150.0, 16000.0, 150.0, 0.0));

        BookingGroupAggregator.Totals totals = BookingGroupAggregator.sum(members);
        assertEquals(150.0, totals.amenityCharge, 0.001);
        assertEquals(26150.0, totals.totalAmount, 0.001);
    }
}
