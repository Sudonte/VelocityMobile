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

import com.example.velocitysuites.Booking;
import com.example.velocitysuites.BookingAmenity;
import com.example.velocitysuites.BookingDetailsActivity;
import com.example.velocitysuites.debug.DebugReceiptFixtures;
import com.example.velocitysuites.MultiStayFixtures;
import com.example.velocitysuites.PaymentReceiptActivity;
import com.example.velocitysuites.PaymentTransaction;
import com.example.velocitysuites.R;
import com.example.velocitysuites.ReceiptDetail;
import com.example.velocitysuites.TransactionDetailsActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * One structure on Booking Details, Reservation Details, Transaction History details and the three receipts (on screen and as PDF):
 * header, Your Stay (date ranges with rooms and amenities), Guests, Bill, Payments. Every value appears once: the grand total, the
 * balance, each amenity, each payment; no "Stay N" label; no second "Remaining Balance"; the bill adds up for one and for two date ranges
 * with amenities chosen per room.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class OneStructureNoDuplicatesTest {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private static final Pattern STAY_N = Pattern.compile("\\bStay \\d");

    private Context app;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        ScreenTestSupport.freshRepository(app, new ScreenTestSupport.FakeApi());
        ScreenTestSupport.goOnline(app);
        PaymentReceiptActivity.debugPreviewFixtures.clear();
        PaymentReceiptActivity.debugPreviewAmenities.clear();
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void texts(View v, List<String> out) {
        if (v instanceof TextView && v.getVisibility() == View.VISIBLE && ((TextView) v).getText().length() > 0) out.add(((TextView) v).getText().toString());
        if (v instanceof ViewGroup && v.getVisibility() == View.VISIBLE) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) texts(((ViewGroup) v).getChildAt(i), out);
        }
    }

    private static List<String> textsOf(View root) {
        List<String> out = new ArrayList<>();
        texts(root, out);
        return out;
    }

    private BookingDetailsActivity details(Booking b) {
        BookingDetailsActivity a = Robolectric.buildActivity(BookingDetailsActivity.class, BookingDetailsActivity.newIntent(app, b)).setup().get();
        idle();
        return a;
    }

    private TransactionDetailsActivity transaction(Booking b) {
        PaymentTransaction tx = new PaymentTransaction(b, new Booking.PaymentRecord("1000.00", "GCASH", "REF1", "2026-10-24", "completed", null));
        TransactionDetailsActivity a = Robolectric.buildActivity(TransactionDetailsActivity.class, TransactionDetailsActivity.newIntent(app, tx)).setup().get();
        idle();
        return a;
    }

    private PaymentReceiptActivity receipt(ReceiptDetail detail) {
        PaymentReceiptActivity.debugPreviewFixtures.put(detail.getReceiptNumber(), detail);
        PaymentReceiptActivity a = Robolectric.buildActivity(PaymentReceiptActivity.class, PaymentReceiptActivity.newIntentForReceipt(app, detail.getReceiptNumber())).setup().get();
        idle();
        return a;
    }

    private static int count(List<String> texts, String exact) {
        int n = 0;
        for (String t : texts) if (t.equals(exact)) n++;
        return n;
    }

    private static String after(List<String> texts, String label) {
        int i = texts.indexOf(label);
        return i >= 0 && i + 1 < texts.size() ? texts.get(i + 1) : null;
    }

    /** What every screen of the family must satisfy, whatever the transaction. */
    private static void assertOneStructure(String name, List<String> texts) {
        String all = String.join("\n", texts);
        assertFalse(name + ": no \"Stay N\" label: " + all, STAY_N.matcher(all).find());
        assertFalse(name + ": no null: " + all, all.contains("null"));
        assertEquals(name + ": the Grand total appears once", 1, count(texts, "Grand total"));
        assertEquals(name + ": the Balance due appears once", 1, count(texts, "Balance due"));
        assertEquals(name + ": Room charges appears once", 1, Math.max(count(texts, "Room charges"), count(texts, "Room charges (all stays)")));
        assertFalse(name + ": no second balance line", all.contains("Remaining Balance") || all.contains("Balance Due") || all.contains("Amount to pay"));
        assertEquals(name + ": the bill is one section", 1, count(texts, "Bill"));
        int stay = texts.indexOf("Your Stay");
        int guests = texts.indexOf("Guests");
        int bill = texts.indexOf("Bill");
        int payments = texts.indexOf("Payments");
        assertTrue(name + ": order Your Stay, Guests, Bill, Payments in " + all, stay >= 0 && guests > stay && bill > guests && payments > bill);
    }

    /** A one-range booking whose amenities were chosen per room: 3,500 + 5,800 rooms, 50 + 300 amenities = 9,650. */
    private Booking oneRangeWithRoomAmenities() {
        Booking b = MultiStayFixtures.booking();
        Booking.StayRecord first = b.getStays().get(0);
        List<BookingAmenity> amenities = Arrays.asList(
                new BookingAmenity("5", "Additional Towel", 1, 50, 50, "Deluxe (room 1)", "1", 1),
                new BookingAmenity("9", "Extra bed", 1, 300, 300, "Executive (room 1)", "2", 1));
        Booking.StayRecord only = new Booking.StayRecord(first.id, 1, first.checkIn, first.checkOut, first.nights, first.state, first.rooms,
                amenities, first.roomsSubtotal, 350, first.roomsSubtotal + 350);
        b.setStays(Arrays.asList(only));
        b.setTotalAmount(9650.0);
        return b;
    }

    @Test
    public void oneDateRange_roomsAndAmenitiesPerRoom_addUpAndAppearOnce() {
        Booking b = oneRangeWithRoomAmenities();
        for (String screen : new String[]{"details", "transaction"}) {
            List<String> t = textsOf((screen.equals("details") ? details(b).getWindow() : transaction(b).getWindow()).getDecorView());
            assertOneStructure(screen, t);
            assertEquals(screen, "₱9,300.00", after(t, "Room charges"));
            assertEquals(screen, "₱350.00", after(t, "Amenities"));
            assertEquals(screen, "₱9,650.00", after(t, "Grand total"));
            assertEquals(screen + ": each amenity once, under its room", 1, count(t, "Deluxe (room 1): Additional Towel × 1"));
            assertEquals(screen, 1, count(t, "Executive (room 1): Extra bed × 1"));
            assertFalse(screen + ": no subtotal row for a single date range", t.contains("Subtotal"));
            assertTrue(screen, t.contains("Deluxe × 1 · ₱3,500.00 × 1 night"));
        }
    }

    @Test
    public void twoDateRanges_eachWithItsOwnSubtotal_addUpToTheGrandTotal() {
        Booking b = MultiStayFixtures.booking();
        for (String screen : new String[]{"details", "transaction"}) {
            List<String> t = textsOf((screen.equals("details") ? details(b).getWindow() : transaction(b).getWindow()).getDecorView());
            assertOneStructure(screen, t);
            assertEquals(screen, 2, count(t, "Subtotal"));
            assertEquals("every room priced on its own range's nights: 9,300 + 9,300", "₱18,600.00", after(t, "Room charges (all stays)"));
            assertEquals(screen, "₱300.00", after(t, "Amenities"));
            assertEquals("Room charges + Amenities = Grand total", "₱18,900.00", after(t, "Grand total"));
            assertTrue(screen, t.contains("Oct 26 → Oct 27, 2026 · 1 night") && t.contains("Oct 27 → Oct 28, 2026 · 1 night"));
        }
    }

    @Test
    public void aReservation_showsTheSameStructure_withTheDiscountAsANote() {
        List<String> t = textsOf(details(MultiStayFixtures.reservation("pending")).getWindow().getDecorView());
        assertOneStructure("reservation", t);
        assertEquals("₱19,015.00", after(t, "Grand total"));
        assertEquals("the expected discount is one note", 1, t.stream().filter(x -> x.startsWith("Expected discount (PWD)")).count());
    }

    @Test
    public void everyReceipt_hasTheSameStructure_onScreenAndInThePdf() {
        PaymentReceiptActivity.debugPreviewAmenities.putAll(DebugReceiptFixtures.allReceiptAmenities());
        for (ReceiptDetail detail : DebugReceiptFixtures.allReceiptDetails().values()) {
            PaymentReceiptActivity screen = receipt(detail);
            List<String> t = textsOf(screen.findViewById(R.id.layoutDynamicReceiptContent));
            assertOneStructure(detail.getReceiptNumber(), t);
            List<String> pdf = textsOf(screen.buildReceiptForPdf(1080));
            assertOneStructure(detail.getReceiptNumber() + " (PDF)", pdf);
            assertEquals("the PDF shows the same bill as the screen", after(t, "Grand total"), after(pdf, "Grand total"));
            assertEquals(after(t, "Balance due"), after(pdf, "Balance due"));
        }
    }

    @Test
    public void theFinalReceipt_endsWithAZeroBalance_andTheNoRefundNote() {
        for (ReceiptDetail detail : DebugReceiptFixtures.allReceiptDetails().values()) {
            if (!"OFFICIAL_RECEIPT".equals(detail.getReceiptType())) continue;
            List<String> t = textsOf(receipt(detail).findViewById(R.id.layoutDynamicReceiptContent));
            assertEquals(detail.getReceiptNumber(), "₱0.00 · Fully paid", after(t, "Balance due"));
            assertTrue(detail.getReceiptNumber(), t.contains("Receipt 3 of 3 · Check-out (final)"));
            assertTrue(detail.getReceiptNumber(), t.contains("No refunds for early check-out (hotel policy)."));
        }
    }

    @Test
    public void eachPaymentIsListedOnce() {
        for (ReceiptDetail detail : DebugReceiptFixtures.allReceiptDetails().values()) {
            List<String> t = textsOf(receipt(detail).findViewById(R.id.layoutDynamicReceiptContent));
            int payments = t.indexOf("Payments");
            List<String> rows = t.subList(payments + 1, t.size());
            long titles = rows.stream().filter(x -> x.contains(" · ") && (x.startsWith("GCash") || x.startsWith("Cash"))).count();
            assertEquals(detail.getReceiptNumber() + ": one title per payment", detail.getPaymentTransactions().size(), titles);
        }
    }
}
