package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.BillingSummaryActivity;
import com.example.velocitysuites.Booking;
import com.example.velocitysuites.BookingDetailsActivity;
import com.example.velocitysuites.MultiStayFixtures;
import com.example.velocitysuites.PaymentTransaction;
import com.example.velocitysuites.R;
import com.example.velocitysuites.TransactionDetailsActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.Arrays;

/**
 * The two device transactions (two ONE-night stays, a span of two nights) on every details screen: each room is priced on ITS OWN stay's
 * night (18,600, not 37,200), rooms have names, amenities are listed once, the discount is a note until the ID is checked, guests read
 * "Minor 1 - age 5", and nothing shows "null" or a raw server value.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MultiStayScreensTest {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private Context app;
    private ScreenTestSupport.FakeApi api;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
        ScreenTestSupport.goOnline(app);
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static String all(View v) {
        StringBuilder sb = new StringBuilder();
        if (v instanceof TextView && v.getVisibility() == View.VISIBLE) sb.append(((TextView) v).getText()).append('\n');
        if (v instanceof ViewGroup && v.getVisibility() == View.VISIBLE) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) sb.append(all(((ViewGroup) v).getChildAt(i)));
        }
        return sb.toString().replace((char) 160, ' ');
    }

    private BookingDetailsActivity details(Booking b) {
        BookingDetailsActivity a = Robolectric.buildActivity(BookingDetailsActivity.class, BookingDetailsActivity.newIntent(app, b)).setup().get();
        idle();
        return a;
    }

    private static int count(String text, String needle) {
        int n = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) n++;
        return n;
    }

    // ---- Booking Details ----

    @Test
    public void bookingDetails_priceEachRoomOnItsOwnNights_groupedUnderTheirDates() {
        Booking b = MultiStayFixtures.booking();
        b.setAdditionalGuests(Arrays.asList(new Booking.AdditionalGuest("Child 1", 5, null, "Child"), new Booking.AdditionalGuest("Child 2", 7, null, "Child")));
        String text = all(details(b).getWindow().getDecorView());

        assertFalse("no ₱37,200: " + text, text.contains("37,200"));
        assertFalse("no null: " + text, text.contains("null"));
        assertFalse("the dates head each range, never 'Stay N': " + text, text.contains("Stay 1") || text.contains("Stay 2"));
        assertTrue(text, text.contains("Oct 26 → Oct 27, 2026 · 1 night"));
        assertTrue(text, text.contains("Oct 27 → Oct 28, 2026 · 1 night"));
        assertTrue(text, text.contains("Deluxe × 1 · ₱3,500.00 × 1 night\n₱3,500.00") && text.contains("Executive × 1 · ₱5,800.00 × 1 night\n₱5,800.00"));
        assertEquals("a subtotal under each of the two ranges", 2, count(text, "Subtotal\n"));
        assertTrue(text, text.contains("Room charges (all stays)\n₱18,600.00"));
        assertTrue("Room charges + Amenities = Grand total: " + text, text.contains("Amenities\n₱300.00") && text.contains("Grand total\n₱18,900.00"));
        assertEquals("the grand total is shown once", 1, count(text, "Grand total"));
        assertTrue(text, text.contains("Minor 1 · age 5") && text.contains("Minor 2 · age 7"));
        assertFalse("not 'Child 1: Child': " + text, text.contains("Child 1"));
        assertTrue("Adults count is shown: " + text, text.contains("Adults\n1\n"));
    }

    @Test
    public void bookingDetails_listAmenitiesOnce_underTheirDates() {
        Booking b = MultiStayFixtures.booking();
        String text = all(details(b).getWindow().getDecorView());
        assertEquals("each amenity once: " + text, 1, count(text, "Additional Towel"));
        assertEquals(1, count(text, "Additional Blanket"));
        assertFalse("the old duplicate section is gone: " + text, text.contains("Selected Amenities"));
        assertTrue(text, text.contains("Amenities for this stay\nAdditional Towel × 2\n₱100.00"));
    }

    @Test
    public void bookingDetails_aRoomsAmenitiesShowUnderThatRoom() {
        Booking b = MultiStayFixtures.booking();
        // the same data as the device, with the amenities now chosen for a room
        com.example.velocitysuites.BookingAmenity bed = new com.example.velocitysuites.BookingAmenity("9", "Extra bed", 1, 300, 300, "Deluxe (room 2)", "1", 2);
        Booking.StayRecord s1 = b.getStays().get(0);
        b.setStays(Arrays.asList(new Booking.StayRecord(s1.id, s1.position, s1.checkIn, s1.checkOut, s1.nights, s1.state, s1.rooms,
                Arrays.asList(bed), s1.roomsSubtotal, 300, s1.subtotal), b.getStays().get(1)));
        String text = all(details(b).getWindow().getDecorView());
        assertTrue(text, text.contains("Deluxe (room 2): Extra bed × 1\n₱300.00"));
        assertFalse("nothing is left without a room here", text.contains("Amenities for this stay\nExtra bed"));
    }

    @Test
    public void bookingDetails_neverShowsARawServerValue() {
        Booking b = MultiStayFixtures.booking();
        b.setPaymentVerificationStatus("pending_verification");
        String text = all(details(b).getWindow().getDecorView());
        assertFalse(text, text.contains("pending_verification"));
        assertFalse(text, text.contains("Expected discount"));
    }

    @Test
    public void bookingDetails_sectionOrder_stayGuestsBillPaymentsReceiptsTimeline() {
        String text = all(details(MultiStayFixtures.booking()).getWindow().getDecorView());
        int stay = text.indexOf("Your Stay\n");
        int guests = text.indexOf("Guests\n");
        int bill = text.indexOf("Bill\n");
        int payments = text.indexOf("Payments\n");
        int timeline = text.indexOf("Booking created");
        assertTrue(text, stay >= 0 && guests > stay && bill > guests && payments > bill);
        assertTrue(timeline < 0 || timeline > payments);
    }

    // ---- Reservation Details ----

    @Test
    public void reservationDetails_sameFigures_andTheDiscountIsAnExpectedNote() {
        String text = all(details(MultiStayFixtures.reservation("pending")).getWindow().getDecorView());
        assertFalse(text, text.contains("37,200") || text.contains("null"));
        assertTrue(text, text.contains("Room charges (all stays)\n₱18,600.00") && text.contains("Amenities\n₱415.00") && text.contains("Grand total\n₱19,015.00"));
        assertTrue(text, text.contains("Expected discount (PWD): −₱3,803.00 — applied after your ID is checked at check-in."));
        assertFalse("not subtracted yet: " + text, text.contains("Discount (PWD)\n"));
        assertEquals("amenities once", 1, count(text, "Additional Towel"));
        assertEquals(1, count(text, "Test1"));
    }

    @Test
    public void reservationDetails_afterTheIdIsChecked_theDiscountIsInTheBreakdown() {
        Booking r = MultiStayFixtures.reservation("approved");
        r.setTotalAmount(15212.0);
        String text = all(details(r).getWindow().getDecorView());
        assertTrue(text, text.contains("Discount (PWD)\n−₱3,803.00"));
        assertTrue(text, text.contains("Grand total\n₱15,212.00"));
        assertFalse(text, text.contains("Expected discount"));
    }

    // ---- Transaction History details ----

    private TransactionDetailsActivity transaction(Booking b) {
        PaymentTransaction tx = new PaymentTransaction(b, new Booking.PaymentRecord("3803.00", "GCASH", "REF1", "2026-10-24", "pending", null));
        TransactionDetailsActivity a = Robolectric.buildActivity(TransactionDetailsActivity.class, TransactionDetailsActivity.newIntent(app, tx)).setup().get();
        idle();
        return a;
    }

    @Test
    public void transactionHistoryDetails_addUpVisibly() {
        String text = all(transaction(MultiStayFixtures.booking()).getWindow().getDecorView());
        assertFalse(text, text.contains("37,200") || text.contains("null"));
        assertTrue(text, text.contains("Room charges (all stays)\n₱18,600.00") && text.contains("Amenities\n₱300.00"));
        assertTrue(text, text.contains("Grand total\n₱18,900.00"));
        assertEquals("the grand total is shown once", 1, count(text, "Grand total"));
        assertTrue(text, text.contains("Subtotal"));
        assertFalse(text, text.contains("Stay 1") || text.contains("Stay 2"));
        String reservation = all(transaction(MultiStayFixtures.reservation("pending")).getWindow().getDecorView());
        assertTrue(reservation, reservation.contains("Expected discount (PWD)") && reservation.contains("₱19,015.00"));
    }

    // ---- Billing Summary ----

    private BillingSummaryActivity billing(com.example.velocitysuites.network.dto.ReservationDto r) {
        BillingSummaryActivity a = Robolectric.buildActivity(BillingSummaryActivity.class,
                new Intent(app, BillingSummaryActivity.class).putExtra(BillingSummaryActivity.EXTRA_RESERVATION_ID, String.valueOf(r.id))).setup().get();
        idle();
        ScreenTestSupport.answerBookingLoad(api, 0, ScreenTestSupport.reservations(r), ScreenTestSupport.directBookings());
        idle();
        return a;
    }

    @Test
    public void billingSummary_listsTheStays_withNamedRooms_andTheDiscountAsANote() {
        BillingSummaryActivity a = billing(MultiStayFixtures.reservationDto("pending"));
        String text = all(a.getWindow().getDecorView());
        assertFalse(text, text.contains("null") || text.contains("37,200"));
        assertTrue(text, text.contains("2 Deluxe, 2 Executive · 2 stays"));
        assertTrue(text, text.contains("Oct 26 → Oct 27, 2026 · 1 night") && text.contains("Oct 27 → Oct 28, 2026 · 1 night"));
        assertFalse(text, text.contains("Stay 1") || text.contains("Stay 2"));
        assertTrue(text, text.contains("Deluxe × 1") && text.contains("Executive × 1") && text.contains("Subtotal\n₱9,300.00"));
        assertTrue(text, text.contains("Room total\n₱18,600.00") || text.contains("₱18,600.00"));
        assertTrue(text, text.contains("₱19,015.00"));
        assertTrue(text, text.contains("Expected discount (PWD): −₱3,803.00"));
        assertEquals("not subtracted", View.GONE, a.findViewById(R.id.layoutBillingDiscount).getVisibility());
        assertTrue(text, text.contains("Pay now (20%–50%): ₱3,803.00 – ₱9,507.50"));
        assertTrue("each stay lists its own amenities and a subtotal", text.contains("Amenities for this stay\nAdditional Towel × 1\n₱50.00") && text.contains("Subtotal\n₱9,715.00") && !text.contains("Stay 1 subtotal"));
        assertFalse("amenities are listed once, in their stay", text.contains("Add-on Amenities"));
        assertTrue("the grand total is rooms + amenities", text.contains("Room total\n₱18,600.00") && text.contains("Amenities total\n₱415.00") && text.contains("Grand total\n₱19,015.00"));
        assertEquals("a reservation continues to payment", "Continue to payment", ((TextView) a.findViewById(R.id.btnConfirmBilling)).getText().toString());
    }

    @Test
    public void billingSummary_afterTheIdIsChecked_subtractsTheDiscount() {
        com.example.velocitysuites.network.dto.ReservationDto dto = MultiStayFixtures.reservationDto("approved");
        dto.total_amount_due = 15212.0;
        BillingSummaryActivity a = billing(dto);
        assertEquals(View.VISIBLE, a.findViewById(R.id.layoutBillingDiscount).getVisibility());
        assertEquals("-₱3,803.00", ((TextView) a.findViewById(R.id.tvBillingDiscount)).getText().toString());
        assertEquals("Discount (PWD)", ((TextView) a.findViewById(R.id.tvBillingDiscountLabel)).getText().toString());
        assertEquals("₱15,212.00", ((TextView) a.findViewById(R.id.tvBillingHeaderGrandTotal)).getText().toString());
        assertEquals(View.GONE, a.findViewById(R.id.tvBillingExpectedDiscount).getVisibility());
    }

    // ---- list cards ----

    @Test
    public void listCards_nameEveryTypeWithItsCount() {
        for (Booking b : new Booking[]{MultiStayFixtures.booking(), MultiStayFixtures.reservation("pending")}) {
            String text = com.example.velocitysuites.TransactionRowAccess.roomText(b);
            assertEquals("Deluxe ×2 • Executive ×2", text);
        }
    }

    // ---- layout ----

    @Test
    public void detailsAndBillingFitAt360And393dp_inLightAndDark() {
        String[] configs = {"w360dp-h800dp-notnight", "w360dp-h800dp-night", "w393dp-h851dp-notnight", "w393dp-h851dp-night"};
        try {
            for (String q : configs) {
                RuntimeEnvironment.setQualifiers("+" + q);
                ScreenTestSupport.freshRepository(app, api = new ScreenTestSupport.FakeApi());
                BookingDetailsActivity d = details(MultiStayFixtures.reservation("pending"));
                int w = d.getResources().getDisplayMetrics().widthPixels;
                LayoutAssert.assertFits(q + " reservation details", d.findViewById(R.id.sectionRoomInfoContent), w);
                LayoutAssert.assertFits(q + " money", d.findViewById(R.id.sectionPaymentSummaryContent), w);
                BillingSummaryActivity b = billing(MultiStayFixtures.reservationDto("pending"));
                LayoutAssert.assertFits(q + " billing summary", b.findViewById(R.id.layoutBillingRoomsRows), w);
            }
        } finally {
            RuntimeEnvironment.setQualifiers("+w360dp-h800dp-notnight");
        }
    }
}
