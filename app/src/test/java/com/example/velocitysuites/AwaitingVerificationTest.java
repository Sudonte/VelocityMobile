package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.ui.TestApplication;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/** An unverified booking reads "Awaiting verification" (amber) on every screen; "Confirmed" only after the front desk verifies it. */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, application = TestApplication.class)
public class AwaitingVerificationTest {

    private static Context ctx() {
        return new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_VelocitySuites);
    }

    private static Booking booking(String status, boolean verified, double paid) {
        Booking b = new Booking("7", "1", "Deluxe", "Deluxe", "Nov 01, 2026", "Nov 02, 2026", 2, 3000, status, "Oct 01, 2026");
        b.setHasBooking(true);
        b.setDirectBooking(true);
        b.setStaffVerified(verified);
        b.setAmountPaid(paid);
        return b;
    }

    @Test
    public void unverifiedConfirmedBooking_isAwaiting_verifiedOneIsConfirmed() {
        assertTrue(TransactionActions.isAwaitingVerification(booking("Confirmed", false, 0)));
        assertTrue("fully paid but still unverified", TransactionActions.isAwaitingVerification(booking("Confirmed", false, 3000)));
        assertFalse(TransactionActions.isAwaitingVerification(booking("Confirmed", true, 1500)));
        assertFalse(TransactionActions.isAwaitingVerification(booking("Checked-In", false, 0)));
        assertFalse(TransactionActions.isAwaitingVerification(booking("Cancelled", false, 0)));
        assertFalse(TransactionActions.isAwaitingVerification(null));
        Booking reservation = booking("Confirmed", false, 0);
        reservation.setHasBooking(false);
        reservation.setDirectBooking(false);
        assertFalse("a reservation is not a booking", TransactionActions.isAwaitingVerification(reservation));
    }

    @Test
    public void labelAndCancelRuleAgree_forEveryUnverifiedConfirmedBooking() {
        for (double paid : new double[]{0, 1500}) {
            Booking b = booking("Confirmed", false, paid);
            assertTrue(TransactionActions.isAwaitingVerification(b));
            assertTrue("awaiting verification and cancellable", TransactionActions.canCancel(b));
        }
        Booking verified = booking("Confirmed", true, 1500);
        assertFalse(TransactionActions.isAwaitingVerification(verified));
        assertFalse("verified: Confirmed and not cancellable", TransactionActions.canCancel(verified));
    }

    @Test
    public void everyStatusWordHelper_sayAwaitingVerification() {
        Context c = ctx();
        Booking b = booking("Confirmed", false, 0);
        assertEquals("Awaiting verification", TransactionStatusChip.label(c, b));
        assertEquals("Awaiting verification", BookingStatusWords.label(c, b));
        assertEquals("Awaiting verification", BookingStatusPresenter.statusWord(c, b));
        assertEquals("Awaiting verification", BookingStatusPresenter.computeStatusLabel(c, b));
        Booking ok = booking("Confirmed", true, 0);
        assertEquals("Confirmed", TransactionStatusChip.label(c, ok));
        assertEquals("Confirmed", BookingStatusPresenter.computeStatusLabel(c, ok));
        assertEquals("Awaiting verification", c.getString(R.string.next_stay_status_waiting_confirmation));
        assertEquals(NextStay.PlainStatus.WAITING_CONFIRMATION, NextStay.plainStatus(b));
        assertEquals(NextStay.PlainStatus.CONFIRMED, NextStay.plainStatus(ok));
    }

    @Test
    public void awaitingChip_isAmber_andConfirmedChip_isGreen() {
        Context c = ctx();
        TextView awaiting = new TextView(c);
        TransactionStatusChip.apply(c, awaiting, booking("Confirmed", false, 0));
        assertEquals("Awaiting verification", awaiting.getText().toString());
        assertEquals(c.getColor(R.color.status_pending_fg), awaiting.getCurrentTextColor());
        TextView confirmed = new TextView(c);
        TransactionStatusChip.apply(c, confirmed, booking("Confirmed", true, 0));
        assertEquals("Confirmed", confirmed.getText().toString());
        assertEquals(c.getColor(R.color.status_paid_fg), confirmed.getCurrentTextColor());
        // the details badge uses the same colours
        TextView badge = new TextView(c);
        BookingStatusPresenter.styleStatusBadge(c, badge, booking("Confirmed", false, 0));
        assertEquals(c.getColor(R.color.status_pending_fg), badge.getCurrentTextColor());
        BookingStatusPresenter.styleStatusBadge(c, badge, booking("Confirmed", true, 0));
        assertEquals(c.getColor(R.color.status_paid_fg), badge.getCurrentTextColor());
    }
}
