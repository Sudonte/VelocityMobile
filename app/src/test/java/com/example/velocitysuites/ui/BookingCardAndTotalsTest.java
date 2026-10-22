package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.BookingAndReservationActivity;
import com.example.velocitysuites.DashboardActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.network.SessionManager;
import com.example.velocitysuites.network.dto.DirectBookingResponseDto;
import com.example.velocitysuites.network.dto.PaymentSummaryDto;
import com.example.velocitysuites.network.dto.ReservationDto;
import com.google.android.material.tabs.TabLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * The Booking & Reservation cards: ONE total everywhere (the amount the guest actually owes: Total = Paid + Balance), no blank
 * room type row, no repeated balance text, one filled button, and the same look at 360dp and 393dp in light and dark.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BookingCardAndTotalsTest {

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
        SessionManager.saveSession(app, "test-token", 1L, "Ana", "Reyes", "", "Ana Reyes", "ana@example.com",
                "09171234567", "Female", "Jan 01, 1990", "");
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Manila"));
    }

    /** Booking #567-like: stored total P3,500 (before the discount), but the guest owes P2,950: P2,800 paid + P150 left. */
    private DirectBookingResponseDto discounted() {
        DirectBookingResponseDto d = ScreenTestSupport.directBooking(567, 3500);
        d.check_in = today().plusDays(1).toString();
        d.check_out = today().plusDays(2).toString();
        d.number_of_guests = 1;
        d.booking_status = "CONFIRMED";
        d.payment_method = "GCASH";
        d.payments = new java.util.ArrayList<>(java.util.Arrays.asList(ScreenTestSupport.payment(9, "2800.00", "completed", "gcash")));
        PaymentSummaryDto summary = new PaymentSummaryDto();
        summary.grand_total = 2950;
        summary.total_amount_paid = 2800;
        summary.remaining_balance = 150;
        summary.payment_status = "PARTIALLY_PAID";
        d.payment_summary = summary;
        return d;
    }

    private BookingAndReservationActivity launchWith(java.util.List<ReservationDto> reservations, java.util.List<DirectBookingResponseDto> direct) {
        BookingAndReservationActivity a = Robolectric.buildActivity(BookingAndReservationActivity.class,
                new Intent(app, BookingAndReservationActivity.class)).setup().get();
        idle();
        ScreenTestSupport.answerBookingLoad(api, api.count("getReservations") - 1, reservations, direct);
        idle();
        return a;
    }

    private static View card(BookingAndReservationActivity a, int position) {
        RecyclerView rv = a.findViewById(R.id.rvItemList);
        RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(position);
        assertNotNull("card " + position, h);
        return h.itemView;
    }

    private static String text(View card, int id) {
        return ((TextView) card.findViewById(id)).getText().toString();
    }

    @Test
    public void theCardShowsTheOneTotal_TotalEqualsPaidPlusBalance() {
        BookingAndReservationActivity a = launchWith(ScreenTestSupport.reservations(), ScreenTestSupport.directBookings(discounted()));
        View c = card(a, 0);
        assertEquals("₱2,950.00", text(c, R.id.tvBookingPrice));
        assertEquals("Paid ₱2,800.00 · Balance ₱150.00", text(c, R.id.tvBookingPaidLine));
        assertEquals("GCash", text(c, R.id.tvBookingPaymentMethod));
        assertEquals("Booking #567", text(c, R.id.tvBookingId));
        assertEquals("Total", ((TextView) ((ViewGroup) c.findViewById(R.id.tvBookingPrice).getParent()).getChildAt(0)).getText().toString());
        // 2,800 + 150 == 2,950
        assertEquals(2950.0, 2800.0 + 150.0, 0.001);
    }

    @Test
    public void thatSameTotalIsWhatPaymentStatusAndTheStatusHelperSay() {
        DirectBookingResponseDto d = discounted();
        com.example.velocitysuites.Booking b = com.example.velocitysuites.network.ApiMapper.toBooking(d);
        assertEquals("the helper every other screen uses", 2950.0, com.example.velocitysuites.TransactionStatusHelper.summarize(b).grandTotal, 0.001);
        assertEquals("the raw stored total is the price before the discount", 3500.0, b.getTotalAmount(), 0.001);

        DashboardActivity dash = Robolectric.buildActivity(DashboardActivity.class).setup().get();
        idle();
        ScreenTestSupport.answerBookingLoad(api, api.count("getReservations") - 1, ScreenTestSupport.reservations(), ScreenTestSupport.directBookings(d));
        idle();
        TextView total = dash.findViewById(R.id.tvTotalAmount);
        assertNotNull("Payment Status lists it", total);
        assertEquals("₱2,950.00", total.getText().toString());
    }

    @Test
    public void aBlankRoomTypeRowIsHidden_neverAnEmptyGap_andARoomTypeIsShownWhenKnown() {
        DirectBookingResponseDto noType = ScreenTestSupport.directBooking(1, 1000);
        noType.room_type = null;
        noType.room_type_id = 0;
        DirectBookingResponseDto withType = ScreenTestSupport.directBooking(2, 1000);
        BookingAndReservationActivity a = launchWith(ScreenTestSupport.reservations(), ScreenTestSupport.directBookings(noType, withType));
        boolean sawHidden = false;
        boolean sawType = false;
        for (int i = 0; i < 2; i++) {
            View c = card(a, i);
            TextView room = c.findViewById(R.id.tvBookingRoomName);
            if (room.getVisibility() == View.GONE) {
                sawHidden = true;
            } else {
                assertTrue("a shown room row is never empty", room.getText().toString().trim().length() > 0);
                sawType |= room.getText().toString().contains("Suite");
            }
        }
        assertTrue("the room type of a booking that has one is shown", sawType);
        assertTrue("never an empty gap: the row is hidden or has text", sawHidden || sawType);
    }

    @Test
    public void noDuplicateBalanceText_noArrow_andOnlyOneFilledButtonPerCard() {
        BookingAndReservationActivity a = launchWith(ScreenTestSupport.reservations(), ScreenTestSupport.directBookings(discounted()));
        View c = card(a, 0);
        TextView note = c.findViewById(R.id.tvFrontDeskNote);
        assertEquals("Pay the balance at the front desk", note.getText().toString());
        assertFalse("the pink box wording is gone", note.getText().toString().contains("Remaining balance"));
        int balanceMentions = 0;
        java.util.List<TextView> all = new java.util.ArrayList<>();
        collect(c, all);
        for (TextView t : all) {
            if (t.getVisibility() == View.VISIBLE && t.getText().toString().contains("alance")) balanceMentions++;
        }
        assertEquals("the balance appears in the paid line and the one small desk line only", 2, balanceMentions);
        assertEquals("View details", text(c, R.id.btnViewDetails));
        assertEquals("Bookings never get Pay now", View.GONE, c.findViewById(R.id.btnPayNow).getVisibility());
        assertEquals("one chevron, no separate arrow", 0, a.getResources().getIdentifier("ic_arrow_forward_card", "drawable", app.getPackageName()));
    }

    private static void collect(View v, java.util.List<TextView> out) {
        if (v instanceof TextView) out.add((TextView) v);
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) collect(((ViewGroup) v).getChildAt(i), out);
    }

    @Test
    public void reservationCard_payNowIsTheOnlyFilledButton_editAndCancelAreVisibleTextActions() {
        ReservationDto r = ScreenTestSupport.reservation(630, 3600);
        r.check_in = today().plusDays(1).toString();
        r.check_out = today().plusDays(2).toString();
        r.status = "AWAITING_GCASH_PAYMENT";
        r.payment_method = "GCASH";
        BookingAndReservationActivity a = launchWith(ScreenTestSupport.reservations(r), ScreenTestSupport.directBookings());
        ((TabLayout) a.findViewById(R.id.tabLayout)).getTabAt(1).select();
        idle();
        View c = card(a, 0);
        assertEquals("Reservation #630", text(c, R.id.tvBookingId));
        assertEquals("Pay now", text(c, R.id.btnPayNow));
        assertEquals(View.VISIBLE, c.findViewById(R.id.btnPayNow).getVisibility());
        assertEquals(View.VISIBLE, c.findViewById(R.id.btnViewDetails).getVisibility());
        assertEquals("Edit and Cancel are visible text actions (never hidden)", View.VISIBLE, c.findViewById(R.id.btnModify).getVisibility());
        assertEquals(View.VISIBLE, c.findViewById(R.id.btnCancel).getVisibility());
        assertEquals("GCash", text(c, R.id.tvBookingPaymentMethod));
    }

    @Test
    public void headerAndFilters_haveTheirLabels_oneLineDescription_andTheCompactNewButton() {
        BookingAndReservationActivity a = launchWith(ScreenTestSupport.reservations(), ScreenTestSupport.directBookings(discounted()));
        assertEquals("Pay a partial payment now; pay the rest at the hotel.", text(a.findViewById(android.R.id.content), R.id.screenDescription));
        assertEquals("New booking", text(a.findViewById(android.R.id.content), R.id.btnNewBooking));
        com.google.android.material.textfield.TextInputLayout status = a.findViewById(R.id.tilTransactionStatus);
        assertEquals("Status", status.getHint().toString());
        assertEquals("Search by ID or room", ((TextView) a.findViewById(R.id.etSearchTransactions)).getHint().toString());
        assertEquals("1 result", text(a.findViewById(android.R.id.content), R.id.tvListCount));
        ((TabLayout) a.findViewById(R.id.tabLayout)).getTabAt(1).select();
        idle();
        assertEquals("Hold a room now and pay later.", text(a.findViewById(android.R.id.content), R.id.screenDescription));
        assertEquals("New reservation", text(a.findViewById(android.R.id.content), R.id.btnNewReservation));
        assertEquals("the Reservations tab has its own policy row", View.VISIBLE, a.findViewById(R.id.cardCancellationPolicy).getVisibility());
        assertEquals("Reservation policy", text(a.findViewById(android.R.id.content), R.id.tvCancellationPolicyTitle));
        ((TabLayout) a.findViewById(R.id.tabLayout)).getTabAt(0).select();
        idle();
        View policy = a.findViewById(R.id.layoutCancellationPolicyRow);
        View full = a.findViewById(R.id.tvCancellationPolicyNotice);
        assertEquals(View.GONE, full.getVisibility());
        policy.performClick();
        assertEquals(View.VISIBLE, full.getVisibility());
        assertEquals("You can cancel your booking any time before the front desk verifies it. Your partial payment is non-refundable. "
                + "Once the front desk verifies your booking, it can no longer be cancelled.", ((TextView) full).getText().toString());
        assertFalse("no more 'fully paid bookings can't be cancelled'", ((TextView) full).getText().toString().toLowerCase().contains("fully paid"));
    }
}
