package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.Booking;
import com.example.velocitysuites.BookingDetailsActivity;
import com.example.velocitysuites.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.List;

/**
 * The redesigned details screen (shared by Bookings and Reservations): the Discount ID card leads with the server's
 * verification status, and Payment History lists newest first with a totals block, or "No payments yet.".
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BookingDetailsLayoutTest {

    private Context app;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        ScreenTestSupport.freshRepository(app, new ScreenTestSupport.FakeApi());
    }

    private static Booking booking(String discountStatus) {
        Booking b = new Booking("77", "1", "Deluxe", "Deluxe", "Oct 01, 2026", "Oct 03, 2026", 2, 5000.0, "Confirmed", "Sep 30, 2026");
        b.setHasBooking(true);
        b.setDiscountVerificationStatus(discountStatus);
        b.setIdCardType("Senior Citizen");
        return b;
    }

    private BookingDetailsActivity open(Booking b) {
        BookingDetailsActivity activity = Robolectric.buildActivity(BookingDetailsActivity.class,
                BookingDetailsActivity.newIntent(app, b)).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        return activity;
    }

    private static String textOf(ViewGroup group) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof TextView) sb.append(((TextView) child).getText()).append('\n');
            else if (child instanceof ViewGroup) sb.append(textOf((ViewGroup) child));
        }
        return sb.toString();
    }

    @Test
    public void pendingId_showsAmberChipThenHelperText_beforeTheDetails() {
        BookingDetailsActivity a = open(booking("pending"));
        assertEquals(View.VISIBLE, a.findViewById(R.id.cardDiscountId).getVisibility());
        LinearLayout content = a.findViewById(R.id.sectionDiscountIdContent);
        String text = textOf(content);
        assertTrue(text, text.startsWith("Waiting for the front desk to verify your ID"));
        assertTrue(text.contains("Please present your Senior Citizen / PWD ID at the front desk."));
        assertTrue("amount is marked pending", text.contains("Pending verification"));
    }

    @Test
    public void approvedId_saysVerified_andNamesTheDiscount() {
        BookingDetailsActivity a = open(booking("approved"));
        String text = textOf(a.findViewById(R.id.sectionDiscountIdContent));
        assertTrue(text, text.startsWith("Your ID has been successfully verified"));
        assertTrue(text.contains("Your Senior Citizen discount has been applied."));
    }

    @Test
    public void rejectedId_saysSo_withNoNewFlow() {
        BookingDetailsActivity a = open(booking("rejected"));
        String text = textOf(a.findViewById(R.id.sectionDiscountIdContent));
        assertTrue(text, text.startsWith("ID verification unsuccessful"));
    }

    @Test
    public void noDiscountRequested_hidesTheWholeCard() {
        assertEquals(View.GONE, open(booking("not_requested")).findViewById(R.id.cardDiscountId).getVisibility());
        assertEquals(View.GONE, open(booking(null)).findViewById(R.id.cardDiscountId).getVisibility());
    }

    @Test
    public void paymentHistory_withNoPayments_saysSo_andStillShowsTotals() {
        BookingDetailsActivity a = open(booking("not_requested"));
        String text = textOf(a.findViewById(R.id.sectionPaymentHistoryContent));
        assertTrue(text, text.contains("No payments yet."));
        assertTrue(text.contains("Total"));
        assertFalse("no discount line without an approved ID", text.contains("Discount"));
    }

    @Test
    public void paymentHistory_listsNewestFirst_andShowsPaidAndRemaining() {
        Booking b = booking("not_requested");
        List<Booking.PaymentTransactionRecord> txs = new ArrayList<>();
        txs.add(new Booking.PaymentTransactionRecord(1, "cash", "deposit", "partial_payment", 1000, "completed", "verified",
                null, null, null, null, null, null, null, "2026-09-30T10:00:00Z", 1000, 4000, null, null));
        txs.add(new Booking.PaymentTransactionRecord(2, "gcash", "final", "final_payment", 1500, "completed", "pending_verification",
                null, null, "1234567890123", null, null, null, null, "2026-10-01T10:00:00Z", 2500, 2500, null, null));
        b.setPaymentTransactions(txs);
        b.setAmountPaid(2500.0);
        BookingDetailsActivity a = open(b);

        LinearLayout content = a.findViewById(R.id.sectionPaymentHistoryContent);
        String text = textOf(content);
        int newer = text.indexOf("GCash");
        int older = text.indexOf("\nCash ·");
        assertTrue(text, newer >= 0 && older >= 0 && newer < older);
        assertTrue(text, text.contains("Pending verification"));
        assertTrue(text, text.contains("Verified"));
        assertTrue(text, text.contains("Remaining Balance"));
    }
}
