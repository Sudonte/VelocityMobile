package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.PaymentHistoryActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.network.dto.PaymentDto;
import com.example.velocitysuites.network.dto.ReservationDto;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Payment History: one card per payment (amount, method, status chip, reference, and Cash received / Change for a cash
 * payment - never for GCash), totals at the top, method filters, and loading / empty / error (with Retry) states.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PaymentHistoryScreenTest {

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

    private PaymentHistoryActivity launch() {
        ActivityController<PaymentHistoryActivity> controller = Robolectric.buildActivity(PaymentHistoryActivity.class);
        controller.setup();
        idle();
        return controller.get();
    }

    private static PaymentDto cash(long id, String applied, String received, String change) {
        PaymentDto p = ScreenTestSupport.payment(id, applied, "completed", "cash");
        p.reference_number = null;
        p.cash_received = received;
        p.change_given = change;
        return p;
    }

    private void answer(ReservationDto... reservations) {
        ScreenTestSupport.answerBookingLoad(api, 0, ScreenTestSupport.reservations(reservations), ScreenTestSupport.directBookings());
        idle();
    }

    private static LinearLayout list(PaymentHistoryActivity a) {
        return a.findViewById(R.id.paymentListContainer);
    }

    private static String text(PaymentHistoryActivity a, int id) {
        return ((TextView) a.findViewById(id)).getText().toString();
    }

    @Test
    public void showsOneCardPerPayment_withTotalsAtTheTop() {
        PaymentHistoryActivity activity = launch();
        assertEquals("loader while nothing has loaded", View.VISIBLE, activity.findViewById(R.id.loadingOverlay).getVisibility());

        answer(ScreenTestSupport.reservation(588, 3600,
                ScreenTestSupport.payment(1, "1800.00", "completed", "gcash"),
                cash(2, "1200.00", "2000.00", "800.00"),
                ScreenTestSupport.payment(3, "600.00", "pending", "gcash")));

        assertEquals(View.GONE, activity.findViewById(R.id.loadingOverlay).getVisibility());
        assertEquals(3, list(activity).getChildCount());
        assertEquals("₱3,000.00", text(activity, R.id.tvTotalPaid));
        assertEquals("₱600.00", text(activity, R.id.tvTotalPending));
        assertEquals("3", text(activity, R.id.tvPaymentCount));
        assertEquals(View.GONE, activity.findViewById(R.id.layoutPaymentEmpty).getVisibility());
    }

    @Test
    public void cashShowsCashReceivedAndChange_gcashNeverDoes() {
        PaymentHistoryActivity activity = launch();
        answer(ScreenTestSupport.reservation(588, 3000,
                ScreenTestSupport.payment(1, "1800.00", "completed", "gcash"),
                cash(2, "1200.00", "2000.00", "800.00")));

        int cashCards = 0;
        for (int i = 0; i < list(activity).getChildCount(); i++) {
            View card = list(activity).getChildAt(i);
            boolean isCash = "CASH".contentEquals(((TextView) card.findViewById(R.id.tvHistoryMethod)).getText());
            View cashRows = card.findViewById(R.id.layoutHistoryCash);
            if (isCash) {
                cashCards++;
                assertEquals(View.VISIBLE, cashRows.getVisibility());
                assertEquals("₱2,000.00", ((TextView) card.findViewById(R.id.tvHistoryCashReceived)).getText().toString());
                assertEquals("₱800.00", ((TextView) card.findViewById(R.id.tvHistoryChange)).getText().toString());
                assertEquals("the applied amount stays the headline", "₱1,200.00", ((TextView) card.findViewById(R.id.tvHistoryAmount)).getText().toString());
            } else {
                assertEquals("a GCash payment never shows the cash rows", View.GONE, cashRows.getVisibility());
                assertEquals(View.VISIBLE, card.findViewById(R.id.tvHistoryReference).getVisibility());
            }
        }
        assertEquals(1, cashCards);
    }

    @Test
    public void aCashPaymentFromBeforeCashDetailsExistedHidesTheCashRows() {
        PaymentHistoryActivity activity = launch();
        answer(ScreenTestSupport.reservation(588, 1200, cash(2, "1200.00", null, null)));
        assertEquals(1, list(activity).getChildCount());
        assertEquals(View.GONE, list(activity).getChildAt(0).findViewById(R.id.layoutHistoryCash).getVisibility());
    }

    @Test
    public void methodFiltersNarrowTheListAndTheTotals() {
        PaymentHistoryActivity activity = launch();
        answer(ScreenTestSupport.reservation(588, 3000,
                ScreenTestSupport.payment(1, "1800.00", "completed", "gcash"),
                cash(2, "1200.00", "1200.00", "0.00")));

        activity.findViewById(R.id.chipMethodCash).performClick();
        idle();
        assertEquals(1, list(activity).getChildCount());
        assertEquals("₱1,200.00", text(activity, R.id.tvTotalPaid));

        activity.findViewById(R.id.chipMethodGcash).performClick();
        idle();
        assertEquals(1, list(activity).getChildCount());
        assertEquals("₱1,800.00", text(activity, R.id.tvTotalPaid));

        activity.findViewById(R.id.chipMethodAll).performClick();
        idle();
        assertEquals(2, list(activity).getChildCount());
    }

    @Test
    public void aFilterWithNothingToShowSaysSoInsteadOfLookingBroken() {
        PaymentHistoryActivity activity = launch();
        answer(ScreenTestSupport.reservation(588, 1800, ScreenTestSupport.payment(1, "1800.00", "completed", "gcash")));

        activity.findViewById(R.id.chipMethodCash).performClick();
        idle();
        assertEquals(0, list(activity).getChildCount());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.layoutPaymentEmpty).getVisibility());
        assertEquals(app.getString(R.string.payment_history_empty_filtered_title), text(activity, R.id.tvPaymentEmptyTitle));
    }

    @Test
    public void noPaymentsAtAll_showsTheEmptyState() {
        PaymentHistoryActivity activity = launch();
        answer(ScreenTestSupport.reservation(588, 1800));
        assertEquals(View.VISIBLE, activity.findViewById(R.id.layoutPaymentEmpty).getVisibility());
        assertEquals(app.getString(R.string.payment_history_empty_title), text(activity, R.id.tvPaymentEmptyTitle));
        assertEquals("₱0.00", text(activity, R.id.tvTotalPaid));
    }

    /** Fails every request the screen has made so far that is still waiting (the repository retries a failed load once by itself). */
    private void failEverythingPending() {
        for (int round = 0; round < 3; round++) {
            for (String method : new String[]{"getReservations", "getDirectBookings"}) {
                for (int i = 0; i < api.count(method); i++) {
                    ScreenTestSupport.FakeCall<Object> call = api.call(method, i);
                    if (call.pending() && !call.isCanceled()) {
                        try {
                            call.offline();
                        } catch (RuntimeException alreadyAnswered) {
                            // answered in an earlier round
                        }
                    }
                }
            }
            idle();
        }
    }

    @Test
    public void whenNothingCouldBeLoaded_showsAnErrorWithRetry_thatAsksAgain() {
        PaymentHistoryActivity activity = launch();
        failEverythingPending();

        assertEquals(View.VISIBLE, activity.findViewById(R.id.layoutPaymentError).getVisibility());
        View retry = activity.findViewById(R.id.btnPaymentRetry);
        assertNotNull(retry);
        assertEquals("a 48dp touch target", true, retry.getLayoutParams().height >= (int) (48 * app.getResources().getDisplayMetrics().density));

        int before = api.count("getReservations");
        retry.performClick();
        idle();
        assertEquals("Retry asks the server again", before + 1, api.count("getReservations"));
        assertEquals("and hides the error while it loads", View.GONE, activity.findViewById(R.id.layoutPaymentError).getVisibility());
    }
}
