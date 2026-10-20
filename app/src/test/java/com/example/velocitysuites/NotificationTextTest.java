package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The friendly titles and descriptions of notifications (NotificationText): built from the facts a notification carries,
 * three or four sentences, never invented; the server's own words when no template applies.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, application = com.example.velocitysuites.ui.TestApplication.class)
public class NotificationTextTest {

    @org.junit.Rule
    public final ThemeResetRule themeReset = new ThemeResetRule();

    private Context app;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
    }

    private static Notification n(String type, String title, String message) {
        return new Notification("1", title, message, "x", type, false, "569");
    }

    private NotificationText.Content build(Notification n, String label) {
        return NotificationText.build(app, n, null, label);
    }

    /** Full stops, question and exclamation marks that end a sentence (a "₱50.00" does not). */
    private static int sentences(String text) {
        Matcher m = Pattern.compile("[.!?](\\s|$)").matcher(text);
        int count = 0;
        while (m.find()) count++;
        return count;
    }

    private static void assertThreeOrFour(NotificationText.Content c) {
        assertTrue("templated: " + c.description, c.templated);
        int s = sentences(c.description);
        assertTrue(s + " sentences: " + c.description, s >= 3 && s <= 4);
        assertFalse("no all caps: " + c.title, c.title.equals(c.title.toUpperCase()) && c.title.length() > 3);
    }

    // ---- amenity requests

    @Test
    public void amenityApproved_isParsedFromTheServerMessage_andWordedExactlyLikeTheBrief() {
        Notification n = n(Notification.TYPE_BOOKING, "Amenity Request Approved",
                "Your request for 1 x Additional Pillow (Room 1-001) (₱50.00) was approved and added to your bill.");
        NotificationText.Content c = build(n, "Booking #569");
        assertEquals("Your Additional Pillow request is approved", c.title);
        assertEquals("The front desk approved 1 × Additional Pillow for Room 1-001 on Booking #569. ₱50.00 has been added to your bill. "
                + "You can settle it at check-out or pay through the app. Open the booking to see your updated total.", c.description);
        assertThreeOrFour(c);
        assertFalse("no double parentheses", c.description.contains("(("));
        assertFalse("no \"1 x\"", c.description.contains("1 x"));
    }

    @Test
    public void amenityApproved_withoutARoomAnAmountOrALinkedBooking_usesOnlyWhatIsKnown() {
        NotificationText.Content c = build(n(Notification.TYPE_CHECK_IN, "Amenity Request Approved",
                "Your request for 2 x Extra Towel was approved and added to your bill."), null);
        assertEquals("Your Extra Towel request is approved", c.title);
        assertEquals("The front desk approved 2 × Extra Towel. It has been added to your bill. "
                + "You can settle it at check-out or pay through the app.", c.description);
        assertFalse("nothing about a room or a booking that is not known", c.description.contains("Room") || c.description.contains("Booking"));
    }

    @Test
    public void amenityRejected_saysItWasNotApproved_andNothingWasAdded() {
        NotificationText.Content c = build(n(Notification.TYPE_BOOKING, "Amenity Request Rejected",
                "Your request for 1 x Additional Blanket (Room 602) (₱100.00) was rejected. Reason: Out of stock."), "Booking #569");
        assertEquals("Your Additional Blanket request was not approved", c.title);
        assertTrue(c.description, c.description.startsWith("The front desk could not approve 1 × Additional Blanket for Room 602 on Booking #569."));
        assertTrue("the server's reason is kept", c.description.contains("Reason: Out of stock."));
        assertTrue(c.description.contains("Nothing was added to your bill."));
        assertThreeOrFour(c);
    }

    @Test
    public void anAmenityMessageThatCannotBeParsed_isShownUnchanged() {
        NotificationText.Content c = build(n(Notification.TYPE_BOOKING, "Amenity Request Approved", "Something we have never seen before."), "Booking #569");
        assertFalse(c.templated);
        assertEquals("Amenity Request Approved", c.title);
        assertEquals("Something we have never seen before.", c.description);
    }

    // ---- bookings and reservations

    @Test
    public void bookingConfirmed_rejected_cancelled() {
        NotificationText.Content confirmed = build(n(Notification.TYPE_BOOKING, "Booking Confirmed", "Your booking was confirmed."), "Booking #567");
        assertEquals("Booking #567 is confirmed", confirmed.title);
        assertTrue(confirmed.description.startsWith("Booking #567 is confirmed. "));
        assertFalse("no check-in / check-out times: the data does not carry them", confirmed.description.contains("PM"));
        assertThreeOrFour(confirmed);

        NotificationText.Content rejected = build(n(Notification.TYPE_RESERVATION, "Reservation Rejected", "Reason: Room unavailable."), "Reservation #12");
        assertEquals("Reservation #12 was rejected", rejected.title);
        assertTrue(rejected.description.contains("Reason: Room unavailable."));
        assertThreeOrFour(rejected);

        NotificationText.Content cancelled = build(n(Notification.TYPE_BOOKING, "Booking Cancelled", "Your booking was cancelled."), "Booking #9");
        assertEquals("Booking #9 was cancelled", cancelled.title);
        assertThreeOrFour(cancelled);
    }

    @Test
    public void reservationConfirmed_usesTheWordReservation() {
        NotificationText.Content c = build(n(Notification.TYPE_RESERVATION, "Reservation Confirmed", "ok"), null);
        assertEquals("Your reservation is confirmed", c.title);
        assertTrue(c.description, c.description.startsWith("Your reservation is confirmed."));
        assertTrue(c.description.contains("Open the reservation"));
    }

    // ---- payments

    @Test
    public void paymentReceived_verified_rejected() {
        NotificationText.Content received = build(n(Notification.TYPE_PAYMENT, "Payment Pending Validation", "We received your GCash payment of ₱1,500.00."), "Booking #567");
        assertEquals("We received your payment", received.title);
        assertTrue(received.description, received.description.startsWith("Your payment of ₱1,500.00 for Booking #567 was received."));
        assertTrue("waiting for the desk", received.description.contains("waiting for the front desk to verify it"));
        assertThreeOrFour(received);

        NotificationText.Content verified = build(n(Notification.TYPE_PAYMENT, "Payment Verified", "Your payment was verified."), "Booking #567");
        assertEquals("Your payment is verified", verified.title);
        assertTrue(verified.description.startsWith("Your payment for Booking #567 was verified."));
        assertThreeOrFour(verified);

        NotificationText.Content rejected = build(n(Notification.TYPE_PAYMENT, "Payment Rejected", "Reason: Reference number not found."), "Booking #567");
        assertEquals("Your payment was not accepted", rejected.title);
        assertTrue(rejected.description.contains("Reason: Reference number not found."));
        assertThreeOrFour(rejected);
    }

    // ---- stays

    @Test
    public void checkInAndCheckOut() {
        NotificationText.Content in = build(n(Notification.TYPE_CHECK_IN, "Checked In", "You are checked in."), "Booking #567");
        assertEquals("You're checked in", in.title);
        assertTrue(in.description.startsWith("You were checked in on Booking #567."));
        assertTrue(in.description.contains("request additional amenities"));
        assertFalse("no times", in.description.contains("PM"));

        NotificationText.Content out = build(n(Notification.TYPE_CHECK_IN, "Checked Out", "You are checked out."), "Booking #567");
        assertEquals("You're checked out", out.title);
        assertThreeOrFour(out);
    }

    // ---- promotions, discounts, announcements

    @Test
    public void promotion_addsTheCatalogFacts_andPointsToBrowseRooms() {
        Notification promo = n(Notification.TYPE_PROMOTION, "Weekend Escape", "Two nights, one price");
        promo.setCatalogDetails(Arrays.asList("Valid|Oct 20 – Nov 30, 2026", "Room type|Deluxe", "Includes|Free breakfast"));
        NotificationText.Content c = build(promo, null);
        assertEquals("Weekend Escape", c.title);
        assertEquals("Two nights, one price. It is valid Oct 20 – Nov 30, 2026. It applies to Deluxe. It includes Free breakfast. "
                + "Browse rooms to see it on your dates.", c.description);
    }

    @Test
    public void announcement_isFromVelocitySuites_withItsPublishedDate() {
        Notification a = new Notification("2", "Maintenance", "The pool is closed on Sunday.", "x", Notification.TYPE_ANNOUNCEMENT, false, null,
                null, "Oct 19, 2026 at 8:00 AM");
        NotificationText.Content c = build(a, null);
        assertEquals("Maintenance", c.title);
        assertEquals("The pool is closed on Sunday. This is an announcement from Velocity Suites. It was published on Oct 19, 2026 at 8:00 AM.", c.description);
        assertThreeOrFour(c);
    }

    // ---- fall back, never crash

    @Test
    public void anythingWithoutATemplate_keepsTheServersTextUnchanged() {
        for (String type : new String[]{Notification.TYPE_SMS, "SomethingNew", null}) {
            NotificationText.Content c = build(n(type, "Hello There", "Plain message."), "Booking #1");
            assertFalse(String.valueOf(type), c.templated);
            assertEquals("Hello There", c.title);
            assertEquals("Plain message.", c.description);
        }
        NotificationText.Content odd = build(n(Notification.TYPE_BOOKING, "You Can Now Request Additional Amenities", "Open your booking."), "Booking #1");
        assertFalse("an amenity notification that is neither approved nor rejected", odd.templated);
    }

    @Test
    public void nullFieldsNeverCrash() {
        Notification empty = new Notification(null, null, null, null, null, false);
        NotificationText.Content c = build(empty, null);
        assertEquals("", c.title);
        assertEquals("", c.description);
        for (String type : new String[]{Notification.TYPE_BOOKING, Notification.TYPE_PAYMENT, Notification.TYPE_CHECK_IN,
                Notification.TYPE_PROMOTION, Notification.TYPE_ANNOUNCEMENT, Notification.TYPE_SYSTEM}) {
            NotificationText.Content x = build(new Notification("1", null, null, null, type, false), null);
            assertEquals(type, "", x.title);
        }
    }
}
