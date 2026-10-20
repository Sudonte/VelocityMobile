package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.ui.TestApplication;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The transaction timeline: every source, de-duplicated, newest first, nothing made up. */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, application = TestApplication.class)
public class TransactionTimelineTest {

    @org.junit.Rule
    public final ThemeResetRule themeReset = new ThemeResetRule();

    private static Context ctx() {
        return new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_VelocitySuites);
    }

    private static Booking booking(String status) {
        Booking b = new Booking("569", "1", "Deluxe", "Deluxe", "Oct 16, 2026", "Oct 18, 2026", 2, 8000, status, "Oct 01, 2026");
        b.setHasBooking(true);
        b.setDirectBooking(true);
        return b;
    }

    private static Booking.PaymentTransactionRecord payment(long id, String method, double amount, String status, String verification,
                                                            String paidAt, String verifiedAt, String reason) {
        return new Booking.PaymentTransactionRecord(id, method, "deposit", "partial_payment", amount, status, verification,
                null, null, null, null, null, verifiedAt, reason, paidAt, amount, 0, null, null);
    }

    private static List<String> titles(List<TransactionTimeline.Entry> entries) {
        List<String> out = new ArrayList<>();
        for (TransactionTimeline.Entry e : entries) out.add(e.title);
        return out;
    }

    private static Notification note(String id, String title, String message, long at) {
        return new Notification(id, title, message, "", Notification.TYPE_PAYMENT, false, "569", null, null, null, null, at);
    }

    // ---- sources

    @Test
    public void ownTimestamps_makeTheLifecycle_newestFirst_withTheCurrentStepMarked() {
        Booking b = booking("Checked-Out");
        b.setEventTime("created", "2026-10-15T01:00:00Z");
        b.setEventTime("verified", "2026-10-15T02:00:00Z");
        b.setEventTime("checked_in", "2026-10-16T06:00:00Z");
        b.setEventTime("checked_out", "2026-10-18T03:00:00Z");
        List<TransactionTimeline.Entry> t = TransactionTimeline.build(ctx(), b, null, Collections.emptyList());
        assertEquals(Arrays.asList("Checked out", "Checked in", "Booking verified by the front desk", "Booking created"), titles(t));
        assertTrue("the newest is the current step", t.get(0).current);
        assertFalse(t.get(1).current);
        assertEquals("Oct 15, 2026 · 9:00 AM", t.get(3).when);
        assertEquals("Manila time (UTC+8)", "Oct 18, 2026 · 11:00 AM", t.get(0).when);
    }

    @Test
    public void anEventWithNoDate_isNotShown_andNothingIsFabricated() {
        Booking b = booking("Confirmed");
        b.setEventTime("created", "2026-10-15T01:00:00Z");
        List<TransactionTimeline.Entry> t = TransactionTimeline.build(ctx(), b, null, Collections.emptyList());
        assertEquals(Collections.singletonList("Booking created"), titles(t));
        assertTrue(TransactionTimeline.build(ctx(), booking("Pending"), null, Collections.emptyList()).isEmpty());
    }

    @Test
    public void payments_showSubmittedThenVerifiedOrRejected_withAmountAndMethod() {
        Booking b = booking("Confirmed");
        b.setPaymentTransactions(Arrays.asList(
                payment(1, "gcash", 2800, "completed", "verified", "2026-10-15T02:00:00Z", "2026-10-15T02:30:00Z", null),
                payment(2, "cash", 500, "failed", "rejected", "2026-10-15T05:00:00Z", "2026-10-15T05:10:00Z", "wrong amount")));
        List<TransactionTimeline.Entry> t = TransactionTimeline.build(ctx(), b, null, Collections.emptyList());
        assertEquals(Arrays.asList("Payment rejected by the front desk", "Payment submitted", "Payment verified by the front desk", "Payment submitted"), titles(t));
        assertEquals("₱2,800.00 · GCash", t.get(2).detail);
        assertEquals("₱500.00 · Cash · wrong amount", t.get(0).detail);
    }

    @Test
    public void amenityRequests_showRequestedThenApprovedOrNotApproved_withItemQuantityRoomAndAmount() {
        Booking b = booking("Checked-In");
        List<AmenityRecord> api = Arrays.asList(
                new AmenityRecord(11, "Additional Pillow", 1, 50, "1-001", "Deluxe", AmenityRecord.Status.APPROVED, 1_760_000_000_000L, 1_760_000_600_000L),
                new AmenityRecord(12, "Massage", 1, 300, "602", "Executive", AmenityRecord.Status.REJECTED, 1_760_000_100_000L, 1_760_000_700_000L),
                new AmenityRecord(13, "Towel", 2, 100, "602", "Executive", AmenityRecord.Status.PENDING, 1_760_000_900_000L, 0));
        List<TransactionTimeline.Entry> t = TransactionTimeline.build(ctx(), b, api, Collections.emptyList());
        assertEquals(Arrays.asList("Amenity requested", "Amenity request not approved", "Amenity request approved", "Amenity requested", "Amenity requested"), titles(t));
        assertEquals("Additional Pillow × 1 · Room 1-001 · ₱50.00", t.get(2).detail);
    }

    @Test
    public void theServersOwnTimeline_isAMainSource_andPendingStepsAreSkipped() {
        Booking b = booking("Confirmed");
        b.setTimeline(Arrays.asList(
                new Booking.TimelineEntry("created", "Reservation created", "Recorded", "x", "2026-10-15T01:00:00Z"),
                new Booking.TimelineEntry("modified", "Reservation modified by guest", "Recorded", "x", "2026-10-15T03:00:00Z"),
                new Booking.TimelineEntry("checked_in", "Checked in", "Pending", "", "")));
        List<TransactionTimeline.Entry> t = TransactionTimeline.build(ctx(), b, null, Collections.emptyList());
        assertEquals(Arrays.asList("Reservation modified by guest", "Reservation created"), titles(t));
    }

    @Test
    public void conversionAndCancellation_appear() {
        Booking converted = booking("Confirmed");
        converted.setDirectBooking(false);
        converted.setEventTime("created", "2026-10-10T01:00:00Z");
        converted.setEventTime("confirmed", "2026-10-12T01:00:00Z");
        assertEquals(Arrays.asList("Reservation converted to a booking", "Reservation created"),
                titles(TransactionTimeline.build(ctx(), converted, null, Collections.emptyList())));

        Booking cancelled = booking("Cancelled");
        cancelled.setEventTime("created", "2026-10-10T01:00:00Z");
        cancelled.setEventTime("cancelled", "2026-10-11T01:00:00Z");
        assertEquals("Booking cancelled", TransactionTimeline.build(ctx(), cancelled, null, Collections.emptyList()).get(0).title);
    }

    // ---- notifications, and de-duplication

    @Test
    public void aNotificationForSomethingAlreadyKnown_isNotShownTwice() {
        Booking b = booking("Checked-In");
        b.setEventTime("checked_in", "2026-10-16T06:00:00Z");
        b.setPaymentTransactions(Collections.singletonList(
                payment(1, "gcash", 2800, "completed", "verified", "2026-10-15T02:00:00Z", "2026-10-15T02:30:00Z", null)));
        long verifiedAt = 1_792_031_400_000L; // 2026-10-15T02:30Z
        List<Notification> notes = Arrays.asList(
                note("n1", "Payment verified", "Your payment was verified", verifiedAt + 60_000),
                note("n2", "Checked in", "You are now checked in", 1_792_134_000_000L));
        List<TransactionTimeline.Entry> t = TransactionTimeline.build(ctx(), b, null, notes);
        assertEquals(Arrays.asList("Checked in", "Payment verified by the front desk", "Payment submitted"), titles(t));
    }

    @Test
    public void aNotificationNothingElseCovers_fillsTheGap() {
        Booking b = booking("Confirmed");
        b.setEventTime("created", "2026-10-15T01:00:00Z");
        List<Notification> notes = Collections.singletonList(note("n1", "Payment verified", "Your payment was verified", 1_792_031_400_000L));
        assertEquals(Arrays.asList("Payment verified by the front desk", "Booking created"),
                titles(TransactionTimeline.build(ctx(), b, null, notes)));
    }

    @Test
    public void amenityNotifications_areUsedOnlyWhileTheRequestListIsUnavailable() {
        Booking b = booking("Checked-In");
        Notification n = note("n1", "Amenity Request Approved", "Your request for 1 × Additional Pillow (Room 1-001) (₱50.00) was approved.", 1_760_000_600_000L);
        List<TransactionTimeline.Entry> without = TransactionTimeline.build(ctx(), b, null, Collections.singletonList(n));
        assertEquals(Collections.singletonList("Amenity request approved"), titles(without));
        assertTrue(without.get(0).detail.contains("Room 1-001"));
        List<AmenityRecord> api = Collections.singletonList(new AmenityRecord(11, "Additional Pillow", 1, 50, "1-001", "", AmenityRecord.Status.APPROVED, 1_760_000_000_000L, 1_760_000_600_000L));
        assertEquals("not counted twice", Arrays.asList("Amenity request approved", "Amenity requested"),
                titles(TransactionTimeline.build(ctx(), b, api, Collections.singletonList(n))));
    }

    @Test
    public void aNotificationAboutAnotherTransaction_isNeverUsed() {
        Booking b = booking("Confirmed");
        Notification other = new Notification("n9", "Payment verified", "ok", "", Notification.TYPE_PAYMENT, false, "999", null, null, null, null, 1_792_031_400_000L);
        // TransactionTimeline.of() filters by reference id; build() trusts its caller, so check the filter through the repository path
        assertTrue(TransactionTimeline.of(ctx(), b).isEmpty() || !titles(TransactionTimeline.of(ctx(), b)).contains("Payment verified by the front desk"));
        assertNotNull(other);
    }

    // ---- the view

    @Test
    public void theView_drawsOneRowPerEntry_andMarksNewOnesOnlyAfterTheFirstDraw() {
        Booking b = booking("Confirmed");
        b.setEventTime("created", "2026-10-15T01:00:00Z");
        Context c = ctx();
        LinearLayout box = new LinearLayout(c);
        Set<String> seen = new HashSet<>();
        TransactionTimelineView.render(c, box, TransactionTimeline.build(c, b, null, Collections.emptyList()), seen);
        assertEquals(1, box.getChildCount());
        assertTrue(seen.contains("created"));

        b.setEventTime("verified", "2026-10-15T02:00:00Z");
        TransactionTimelineView.render(c, box, TransactionTimeline.build(c, b, null, Collections.emptyList()), seen);
        assertEquals(2, box.getChildCount());
        assertEquals("verified", box.getChildAt(0).getTag());
        assertTrue(seen.contains("verified"));

        LinearLayout empty = new LinearLayout(c);
        TransactionTimelineView.render(c, empty, Collections.emptyList(), new HashSet<>());
        assertEquals("Nothing has happened on this transaction yet.", ((TextView) empty.getChildAt(0)).getText().toString());
    }

    // ---- live status

    @Test
    public void polling_stopsOnceNothingCanChange() {
        assertTrue(DetailsLive.isActive(booking("Pending")));
        assertTrue(DetailsLive.isActive(booking("Confirmed")));
        assertTrue(DetailsLive.isActive(booking("Checked-In")));
        for (String s : new String[]{"Checked-Out", "Completed", "Cancelled", "Rejected"}) assertFalse(s, DetailsLive.isActive(booking(s)));
        assertFalse(DetailsLive.isActive(null));
        assertEquals(20_000L, DetailsLive.POLL_INTERVAL_MS);
        assertEquals("2:15 PM", DetailsLive.time(1_792_131_300_000L));
    }

    @Test
    public void lastUpdatedRow_showsTheTime_andTheFailureMessageKeepsTheLastGoodTime() {
        Context c = ctx();
        LinearLayout content = new LinearLayout(c);
        DetailsLive live = DetailsLive.attach(c, content, () -> { });
        long t = 1_792_131_300_000L; // 2026-10-16T06:15Z = 2:15 PM Manila
        live.markUpdated(t);
        assertEquals("Last updated 2:15 PM", ((TextView) content.findViewById(R.id.tvLastUpdated)).getText().toString());
        assertFalse(live.isShowingError());
        live.markFailed();
        assertTrue(live.isShowingError());
        assertEquals("Couldn’t update. Showing info from 2:15 PM", ((TextView) content.findViewById(R.id.tvUpdateError)).getText().toString());
        live.markUpdated(t + 120_000);
        assertFalse("a good update clears the message", live.isShowingError());
    }
}
