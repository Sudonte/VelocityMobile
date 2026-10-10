package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.MoneyFormat;
import com.example.velocitysuites.PaymentActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.network.dto.PaymentSubmitResponse;
import com.example.velocitysuites.network.dto.RequestableAmenityDto;
import com.example.velocitysuites.network.dto.ReservationDto;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowNetworkCapabilities;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The real Payment screen against a fake API: Review Billing (what is summarised, the bill, right-aligned amounts,
 * the pinned action bar that never hides content), GCash Payment (the how-to steps, copy amount, inline validation,
 * one action row per step), the keyboard/navigation-bar insets, and the whole submission - loading, error with a
 * retry, success, and no double submission.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PaymentScreenTest {

    private static final String NUMBER = "9171234567";
    // deliberately NOT the reference the fixture payments carry - the screen rejects a reference already in the guest's history
    private static final String REFERENCE = "9876543210987";

    private Context app;
    private ScreenTestSupport.FakeApi api;
    private ActivityController<PaymentActivity> controller;
    private int amenitiesAnswered;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
        goOnline();
    }

    // ---- helpers ----

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void settle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700));
    }

    /** Robolectric reports no network by default (the offline banner shows); the submit path refuses to run offline. */
    private void goOnline() {
        ConnectivityManager cm = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkCapabilities caps = ShadowNetworkCapabilities.newInstance();
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        shadowOf(cm).setNetworkCapabilities(cm.getActiveNetwork(), caps);
    }

    private PaymentActivity launch(ReservationDto reservation) {
        Intent intent = new Intent(app, PaymentActivity.class).putExtra("BOOKING_ID", String.valueOf(reservation.id));
        controller = Robolectric.buildActivity(PaymentActivity.class, intent);
        controller.setup();
        idle();
        ScreenTestSupport.answerBookingLoad(api, 0, ScreenTestSupport.reservations(reservation), ScreenTestSupport.directBookings());
        idle();
        amenitiesAnswered = 0;
        while (amenitiesAnswered < api.count("getRequestableAmenities")) {
            ScreenTestSupport.FakeCall<List<RequestableAmenityDto>> amenities = api.call("getRequestableAmenities", amenitiesAnswered++);
            amenities.succeed(new ArrayList<>());
            idle();
        }
        settle();
        return controller.get();
    }

    private PaymentActivity launchUnpaid() {
        return launch(ScreenTestSupport.reservation(588, 1800));
    }

    private static void layout(PaymentActivity a) {
        View decor = a.getWindow().getDecorView();
        int w = a.getResources().getDisplayMetrics().widthPixels;
        int h = a.getResources().getDisplayMetrics().heightPixels;
        decor.getViewTreeObserver().dispatchOnPreDraw(); // what every real frame does: the scroller follows the app bar
        decor.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, w, h);
    }

    private static void confirmLatestDialog() {
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull("a confirmation dialog should be showing", dialog);
        ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
    }

    /** Answers the bill re-read the screen makes (resume / right before submit), plus the amenity read that corrects its total. */
    private void answerBillCheck(ReservationDto latest) {
        int index = api.count("getReservation") - 1;
        assertTrue("the screen should have re-read the bill", index >= 0);
        ScreenTestSupport.FakeCall<ReservationDto> call = api.call("getReservation", index);
        call.succeed(latest);
        idle();
        while (amenitiesAnswered < api.count("getRequestableAmenities")) {
            ScreenTestSupport.FakeCall<List<RequestableAmenityDto>> amenities = api.call("getRequestableAmenities", amenitiesAnswered++);
            amenities.succeed(new ArrayList<>());
            idle();
        }
    }

    private static void proceedToGcash(PaymentActivity a) {
        a.findViewById(R.id.proceedToGcashButton).performClick();
        idle();
        confirmLatestDialog();
        settle();
    }

    private static void type(PaymentActivity a, int editTextId, String text) {
        ((EditText) a.findViewById(editTextId)).setText(text);
        idle();
    }

    private static void blur(PaymentActivity a, int editTextId) {
        EditText field = a.findViewById(editTextId);
        field.getOnFocusChangeListener().onFocusChange(field, false);
        idle();
    }

    private void attachReceipt(PaymentActivity a) {
        a.findViewById(R.id.btnUploadReceipt).performClick();
        ShadowActivity.IntentForResult launched = shadowOf(a).getNextStartedActivityForResult();
        assertNotNull("the receipt picker should have been launched", launched);
        Uri uri = Uri.parse("content://velocity.test.receipts/receipt.jpg");
        shadowOf(app.getContentResolver()).registerInputStream(uri, new ByteArrayInputStream(new byte[]{1, 2, 3, 4}));
        shadowOf(a).receiveResult(launched.intent, Activity.RESULT_OK, new Intent().setData(uri));
        idle();
    }

    /** Walks Review -> GCash steps 1..4 with valid input, ending on step 5 (review and submit). */
    private PaymentActivity toSubmitStep() {
        return toSubmitStep(ScreenTestSupport.reservation(588, 1800));
    }

    private PaymentActivity toSubmitStep(ReservationDto reservation) {
        PaymentActivity a = launch(reservation);
        proceedToGcash(a);
        a.findViewById(R.id.btnGcashStep1Next).performClick();
        type(a, R.id.etGcashNumber, NUMBER);
        a.findViewById(R.id.btnGcashStep2Next).performClick();
        type(a, R.id.etGcashReferenceNumber, REFERENCE);
        a.findViewById(R.id.btnGcashStep3Next).performClick();
        attachReceipt(a);
        a.findViewById(R.id.btnGcashStep4Next).performClick();
        idle();
        assertEquals("on the review/submit step", View.VISIBLE, a.findViewById(R.id.gcashStep5Review).getVisibility());
        return a;
    }

    /** The submission runs its file copy on a background thread before the API call is made - wait for that call. */
    private void awaitSubmitCalls(int expected) throws InterruptedException {
        for (int i = 0; i < 300 && api.count("submitGcashPayment") < expected; i++) {
            Thread.sleep(20);
            idle();
        }
        assertEquals("submitGcashPayment calls", expected, api.count("submitGcashPayment"));
    }

    private static String text(PaymentActivity a, int id) {
        return ((TextView) a.findViewById(id)).getText().toString();
    }

    private static int rightEdge(PaymentActivity a, int id) {
        View root = a.findViewById(R.id.paymentContentColumn);
        return LayoutHarness.boundsIn(root, a.findViewById(id)).right;
    }

    private static boolean isVisible(PaymentActivity a, int id) {
        return a.findViewById(id).getVisibility() == View.VISIBLE;
    }

    // ---- paying the rest of a partly paid reservation (same examples as the server's GuestPayRemainingBalanceTest) ----

    @Test
    public void partlyPaid_offersTheRestAsFullPayment_andKeepsTheInRangePartialOptions() {
        // P2,000, P600 verified: P1,400 left, the 20% minimum (P400) still fits
        PaymentActivity a = launch(ScreenTestSupport.reservation(588, 2000,
                ScreenTestSupport.payment(1, "600.00", "completed", "gcash")));
        proceedToGcashPrerequisites(a);

        assertTrue("partial options stay", isVisible(a, R.id.chipPercent20));
        assertEquals("the 20% option is 20% of the original price", "₱400.00", text(a, R.id.tvAmountToPay));
        a.findViewById(R.id.chipPercentFull).performClick();
        idle();
        assertEquals("Full Payment sends the balance, not the price", "₱1,400.00", text(a, R.id.tvAmountToPay));
        assertFalse("no error for an allowed amount", isVisible(a, R.id.tvAmountError));
    }

    @Test
    public void nearlyPaid_hidesPartialOptions_andExplainsWhy() {
        // P2,000, P1,700 verified: P300 left is under the P400 minimum, so only Full is possible
        PaymentActivity a = launch(ScreenTestSupport.reservation(588, 2000,
                ScreenTestSupport.payment(1, "1700.00", "completed", "gcash")));
        proceedToGcashPrerequisites(a);

        assertFalse("partial options are hidden", isVisible(a, R.id.chipPercent20));
        assertFalse(isVisible(a, R.id.chipPercent50));
        assertTrue(isVisible(a, R.id.chipPercentFull));
        assertEquals("Full is selected and sends the balance", "₱300.00", text(a, R.id.tvAmountToPay));
        assertEquals(a.getString(R.string.partial_unavailable_hint, "₱300.00", "₱400.00"), text(a, R.id.tvPaymentModeSub));
        assertFalse(isVisible(a, R.id.tvAmountError));
        assertTrue("and the guest can continue", a.findViewById(R.id.proceedToGcashButton).isEnabled());
    }

    @Test
    public void fullyPaid_cannotContinue_andSaysSo() {
        PaymentActivity a = launch(ScreenTestSupport.reservation(588, 2000,
                ScreenTestSupport.payment(1, "2000.00", "completed", "gcash")));
        proceedToGcashPrerequisites(a);

        assertFalse(a.findViewById(R.id.proceedToGcashButton).isEnabled());
        assertTrue(isVisible(a, R.id.tvAmountError));
        assertEquals(a.getString(R.string.error_payment_settled), text(a, R.id.tvAmountError));
    }

    /** The amount section is part of the review screen; give the layout a moment to settle. */
    private static void proceedToGcashPrerequisites(PaymentActivity a) {
        settle();
    }

    // ---- a discount waiting for the receptionist's ID check: deposits only ----

    private static ReservationDto withDiscount(ReservationDto r, String status) {
        r.discount_verification_status = status;
        return r;
    }

    @Test
    public void discountPending_hidesFullPayment_explainsWhy_andSelectsADeposit() {
        PaymentActivity a = launch(withDiscount(ScreenTestSupport.reservation(588, 2000), "pending"));
        proceedToGcashPrerequisites(a);

        assertFalse("Full Payment is not offered", isVisible(a, R.id.chipPercentFull));
        assertTrue("deposit options are", isVisible(a, R.id.chipPercent20));
        assertEquals(a.getString(R.string.discount_pending_line), text(a, R.id.tvPaymentModeSub));
        assertEquals("a deposit is selected", "₱400.00", text(a, R.id.tvAmountToPay));
        assertTrue(a.findViewById(R.id.proceedToGcashButton).isEnabled());
    }

    @Test
    public void discountApprovedOrRejectedOrNotRequested_offersFullPaymentAsUsual() {
        for (String status : new String[]{"approved", "rejected", "not_requested"}) {
            PaymentActivity a = launch(withDiscount(ScreenTestSupport.reservation(588, 2000), status));
            proceedToGcashPrerequisites(a);
            assertTrue(status, isVisible(a, R.id.chipPercentFull));
        }
    }

    @Test
    public void discountPending_withTooLittleLeftForADeposit_putsPaymentOnHold() {
        PaymentActivity a = launch(withDiscount(ScreenTestSupport.reservation(588, 2000,
                ScreenTestSupport.payment(1, "1700.00", "completed", "gcash")), "pending"));
        proceedToGcashPrerequisites(a);

        assertFalse(isVisible(a, R.id.chipPercentFull));
        assertFalse(isVisible(a, R.id.chipPercent20));
        assertFalse(a.findViewById(R.id.proceedToGcashButton).isEnabled());
        assertTrue(isVisible(a, R.id.tvAmountError));
    }

    @Test
    public void whenTheDiscountIsDecidedWhileAway_optionsUpdate_andWhatWasTypedIsKept() {
        PaymentActivity a = toSubmitStep(withDiscount(ScreenTestSupport.reservation(588, 2000), "pending"));
        assertFalse(isVisible(a, R.id.chipPercentFull));

        leaveAndComeBack();
        // approved meanwhile: the server's total now includes the discount
        answerBillCheck(withDiscount(ScreenTestSupport.reservation(588, 1600), "approved"));

        assertEquals(a.getString(R.string.bill_changed_title), latestDialogTitle());
        assertTrue("Full Payment is back", isVisible(a, R.id.chipPercentFull));
        assertEquals("typed reference kept", REFERENCE, ((EditText) a.findViewById(R.id.etGcashReferenceNumber)).getText().toString());
        assertEquals("receipt kept", a.getString(R.string.receipt_attached_success), text(a, R.id.tvReceiptStatus));
    }

    @Test
    public void submittingWhileTheDiscountIsStillPending_neverSendsAFullPayment() throws Exception {
        PaymentActivity a = toSubmitStep(withDiscount(ScreenTestSupport.reservation(588, 2000), "pending"));
        a.findViewById(R.id.completePaymentButton).performClick();
        idle();
        confirmLatestDialog();
        answerBillCheck(withDiscount(ScreenTestSupport.reservation(588, 2000), "pending"));

        awaitSubmitCalls(1); // the selected deposit goes out; it is not a Full payment
    }

    // ---- Cash as a deposit while the discount is being verified, and the deposit cap ----

    private static ReservationDto cashReservation(ReservationDto r) {
        r.payment_method = "cash";
        return r;
    }

    @Test
    public void discountPending_cashIsADeposit_withTheCashLine() {
        PaymentActivity a = launch(cashReservation(withDiscount(ScreenTestSupport.reservation(588, 2000), "pending")));
        proceedToGcashPrerequisites(a);

        assertTrue("the deposit options are offered for Cash too", isVisible(a, R.id.chipPercent20));
        assertFalse("Full Payment is not", isVisible(a, R.id.chipPercentFull));
        assertEquals(a.getString(R.string.discount_pending_cash_line), text(a, R.id.tvPaymentModeSub));
        assertEquals("a deposit amount is selected", "₱400.00", text(a, R.id.tvAmountToPay));
        assertTrue(a.findViewById(R.id.proceedToGcashButton).isEnabled());
    }

    @Test
    public void withoutAPendingDiscount_cashStaysFullOnly() {
        PaymentActivity a = launch(cashReservation(ScreenTestSupport.reservation(588, 2000)));
        proceedToGcashPrerequisites(a);

        assertTrue(isVisible(a, R.id.chipPercentFull));
    }

    @Test
    public void whenTheDepositCapIsUsedUp_theMaximumDepositMessageShows_andPaymentIsBlocked() {
        ReservationDto r = withDiscount(ScreenTestSupport.reservation(588, 2000,
                ScreenTestSupport.payment(1, "1000.00", "completed", "gcash")), "pending");
        r.deposit_cap = 1000.0;
        PaymentActivity a = launch(r);
        proceedToGcashPrerequisites(a);

        assertFalse(a.findViewById(R.id.proceedToGcashButton).isEnabled());
        assertEquals(a.getString(R.string.max_deposit_reached), text(a, R.id.tvAmountError));
        assertEquals(a.getString(R.string.max_deposit_reached), text(a, R.id.tvPaymentModeSub));
        assertFalse(isVisible(a, R.id.chipPercentFull));
        assertFalse(isVisible(a, R.id.chipPercent20));
    }

    @Test
    public void theDepositOptionsAreCappedByWhatIsLeftUnderTheCap() {
        ReservationDto r = withDiscount(ScreenTestSupport.reservation(588, 2000,
                ScreenTestSupport.payment(1, "600.00", "completed", "gcash")), "pending");
        r.deposit_cap = 1000.0; // 400 left under the cap
        PaymentActivity a = launch(r);
        proceedToGcashPrerequisites(a);

        a.findViewById(R.id.chipPercent50).performClick(); // 50% of 2,000 = 1,000 would overshoot the cap
        idle();
        assertEquals("₱400.00", text(a, R.id.tvAmountToPay));
        assertTrue(a.findViewById(R.id.proceedToGcashButton).isEnabled());
    }

    // ---- Review Billing: what is shown ----

    @Test
    public void reviewBilling_summarisesTheBooking_thenShowsTheBill() {
        PaymentActivity a = launchUnpaid();
        layout(a);

        assertEquals("₱1,800.00", text(a, R.id.tvTotalAmount));
        assertEquals("1× Deluxe", text(a, R.id.tvSummaryRooms));
        assertEquals("Oct 01, 2026", text(a, R.id.tvCheckInSummary));
        assertEquals("Oct 02, 2026", text(a, R.id.tvCheckOutSummary));
        assertEquals("1 Night", text(a, R.id.tvNightsSummary));
        assertEquals("2 Guests", text(a, R.id.tvSummaryGuests));

        assertEquals("₱1,800.00", text(a, R.id.tvRoomCharges));
        assertEquals("₱0.00", text(a, R.id.tvAmenitiesTotalSummary));
        assertEquals("₱1,800.00", text(a, R.id.tvGrandTotal));
        assertEquals("₱0.00", text(a, R.id.tvAlreadyPaidSummary));
        assertEquals("₱1,800.00", text(a, R.id.tvOutstandingBalanceSummary));
        assertEquals("0% paid", text(a, R.id.tvPaidPercent));
        assertFalse("no discount, no pre-discount subtotal row", isVisible(a, R.id.layoutSubtotalRow));
        assertFalse(isVisible(a, R.id.layoutDiscountSummary));
        assertFalse(isVisible(a, R.id.layoutAdditionalGuestFee));

        // the summary comes first, the bill after it, the payment options after that
        View root = a.findViewById(R.id.paymentContentColumn);
        int summaryTop = LayoutHarness.boundsIn(root, a.findViewById(R.id.cardBookingSummary)).top;
        int billTop = LayoutHarness.boundsIn(root, a.findViewById(R.id.cardBilling)).top;
        int optionsTop = LayoutHarness.boundsIn(root, a.findViewById(R.id.cgPaymentMethod)).top;
        assertTrue("summary above bill", summaryTop < billTop);
        assertTrue("bill above the payment options", billTop < optionsTop);
    }

    @Test
    public void reviewBilling_aPartlyPaidDiscountedBill_showsEveryLine() {
        ReservationDto reservation = ScreenTestSupport.reservation(588, 1800,
                ScreenTestSupport.payment(1, "500.00", "completed", "gcash"));
        ReservationDto.DiscountPreviewDto discount = new ReservationDto.DiscountPreviewDto();
        discount.discount = 200;
        reservation.discount_preview = discount;
        PaymentActivity a = launch(reservation);
        layout(a);

        assertTrue("a discount shows the subtotal it applies to", isVisible(a, R.id.layoutSubtotalRow));
        assertTrue(isVisible(a, R.id.layoutDiscountSummary));
        assertEquals("-₱200.00", text(a, R.id.tvDiscountSummary));
        assertEquals("₱500.00", text(a, R.id.tvAlreadyPaidSummary));
        assertEquals("₱1,300.00", text(a, R.id.tvOutstandingBalanceSummary));
        assertEquals("27% paid", text(a, R.id.tvPaidPercent));
    }

    @Test
    public void reviewBilling_everyAmountIsRightAligned_andFormattedByMoneyFormat() {
        PaymentActivity a = launchUnpaid();
        layout(a);
        // (the Balance Due figure sits inside its own tinted panel, so it is right-aligned within that panel instead)
        int[] amounts = {R.id.tvRoomCharges, R.id.tvAmenitiesTotalSummary, R.id.tvGrandTotal, R.id.tvAlreadyPaidSummary};
        int expectedRight = rightEdge(a, R.id.tvGrandTotal);
        for (int id : amounts) {
            TextView amount = a.findViewById(id);
            assertEquals("amount " + amount.getResources().getResourceEntryName(id) + " ends on the shared right edge",
                    expectedRight, rightEdge(a, id));
            assertEquals(Gravity.END, amount.getGravity() & Gravity.END);
            assertTrue("formatted like every other peso amount in the app: " + amount.getText(),
                    amount.getText().toString().matches("₱[0-9,]+\\.[0-9]{2}"));
        }
        // the dynamic line items (room rows) too: name on the left, amount on the right edge
        ViewGroup rooms = a.findViewById(R.id.layoutSelectedRoomsSummary);
        assertEquals(1, rooms.getChildCount());
        ViewGroup row = (ViewGroup) rooms.getChildAt(0);
        TextView roomAmount = (TextView) row.getChildAt(row.getChildCount() - 1);
        assertEquals(MoneyFormat.format(1800), roomAmount.getText().toString());
        assertEquals(expectedRight, LayoutHarness.boundsIn(a.findViewById(R.id.paymentContentColumn), roomAmount).right);
    }

    @Test
    public void reviewBilling_theTotalIsTheMostProminentNumber() {
        PaymentActivity a = launchUnpaid();
        float total = ((TextView) a.findViewById(R.id.tvTotalAmount)).getTextSize();
        for (int id : new int[]{R.id.tvGrandTotal, R.id.tvOutstandingBalanceSummary, R.id.tvAlreadyPaidSummary,
                R.id.tvRoomCharges, R.id.tvAmountToPay, R.id.tvBarAmount}) {
            assertTrue("the total must be the biggest number on screen", total > ((TextView) a.findViewById(id)).getTextSize());
        }
    }

    // ---- the pinned action bar ----

    @Test
    public void actionBar_showsTheReviewAction_andNeverHidesContent() {
        PaymentActivity a = launchUnpaid();
        layout(a);
        View bar = a.findViewById(R.id.paymentActionBar);
        assertTrue(isVisible(a, R.id.paymentActionBar));
        assertTrue(isVisible(a, R.id.rowActionReview));
        for (int id : new int[]{R.id.rowActionStep1, R.id.rowActionStep2, R.id.rowActionStep3, R.id.rowActionStep4, R.id.rowActionStep5}) {
            assertFalse("only one row of actions at a time", isVisible(a, id));
        }
        assertEquals(MoneyFormat.format(360), text(a, R.id.tvBarAmount));
        assertTrue("Proceed lives in the bar", ((View) a.findViewById(R.id.proceedToGcashButton).getParent().getParent()) == bar
                || a.findViewById(R.id.proceedToGcashButton).getParent().getParent().getParent() == bar);

        // the scrolling screen ends exactly where the bar begins - nothing can sit behind it
        View scrollingScreen = a.findViewById(R.id.paymentRoot);
        assertEquals(bar.getTop(), scrollingScreen.getBottom());

        // scrolled to the very end, the last piece of content is fully above the bar. (A finger scroll collapses the
        // app bar before it moves the content - the scroller is taller than the screen while the bar is open - so
        // the end of the content is only ever reached with the bar collapsed; do the same here.)
        NestedScrollView scroll = a.findViewById(R.id.screenContent);
        ((com.google.android.material.appbar.AppBarLayout) a.findViewById(R.id.appBarLayout)).setExpanded(false, false);
        idle();
        layout(a);
        scroll.scrollTo(0, scroll.getChildAt(0).getHeight());
        layout(a);
        ViewGroup content = (ViewGroup) scroll.getChildAt(0);
        View last = null;
        for (int i = content.getChildCount() - 1; i >= 0 && last == null; i--) {
            if (content.getChildAt(i).getVisibility() == View.VISIBLE) last = content.getChildAt(i);
        }
        assertNotNull(last);
        View root = a.findViewById(R.id.paymentContentColumn);
        assertTrue("the last content (" + last.getClass().getSimpleName() + ") ends above the bar",
                LayoutHarness.boundsIn(root, last).bottom <= bar.getTop());
    }

    @Test
    public void actionBar_showsOneRowPerGcashStep_andBackToSummaryReturnsToReview() {
        PaymentActivity a = launchUnpaid();
        proceedToGcash(a);
        int[] rows = {R.id.rowActionStep1, R.id.rowActionStep2, R.id.rowActionStep3, R.id.rowActionStep4, R.id.rowActionStep5};
        assertOnlyRow(a, rows[0]);

        a.findViewById(R.id.btnGcashStep1Next).performClick();
        assertOnlyRow(a, rows[1]);
        assertEquals("Step 2 of 5", text(a, R.id.tvGcashStepLabel));
        type(a, R.id.etGcashNumber, NUMBER);
        a.findViewById(R.id.btnGcashStep2Next).performClick();
        assertOnlyRow(a, rows[2]);
        a.findViewById(R.id.btnGcashStep3Back).performClick();
        assertOnlyRow(a, rows[1]);
        a.findViewById(R.id.btnGcashStep2Back).performClick();
        assertOnlyRow(a, rows[0]);

        a.findViewById(R.id.backToSummaryButton).performClick();
        assertOnlyRow(a, R.id.rowActionReview);
        assertTrue(isVisible(a, R.id.checkoutSummarySection));
    }

    private static void assertOnlyRow(PaymentActivity a, int rowId) {
        for (int id : new int[]{R.id.rowActionReview, R.id.rowActionStep1, R.id.rowActionStep2, R.id.rowActionStep3,
                R.id.rowActionStep4, R.id.rowActionStep5}) {
            assertEquals("row " + a.getResources().getResourceEntryName(id), id == rowId, isVisible(a, id));
        }
        assertTrue(isVisible(a, R.id.paymentActionBar));
    }

    @Test
    public void actionBar_isHidden_whenAPaymentIsAlreadyPendingVerification() {
        PaymentActivity a = launch(ScreenTestSupport.reservation(588, 1800,
                ScreenTestSupport.payment(1, "360.00", "pending", "gcash")));
        proceedToGcash(a);
        assertTrue("the status card is showing", isVisible(a, R.id.cardPaymentStatusBanner));
        assertFalse("the form is hidden while there is nothing to submit", isVisible(a, R.id.cardGcashSubmissionForm));
        assertFalse("so the bar has nothing to act on", isVisible(a, R.id.paymentActionBar));
    }

    @Test
    public void actionBar_buttonsAreAtLeast48dp_atTheLargestFontOnASmallPhone() {
        RuntimeEnvironment.setFontScale(2.0f);
        PaymentActivity a = launchUnpaid();
        layout(a);
        float density = a.getResources().getDisplayMetrics().density;
        MaterialButton proceed = a.findViewById(R.id.proceedToGcashButton);
        assertTrue("Proceed is at least 48dp tall: " + proceed.getHeight() / density, proceed.getHeight() / density >= 48f);
        View scroller = a.findViewById(R.id.paymentRoot);
        assertTrue("content keeps a usable height above the bar: " + scroller.getHeight() / density + "dp",
                scroller.getHeight() / density >= 160f);
    }

    @Test
    @Config(qualifiers = "w320dp-h568dp-xhdpi")
    public void actionBar_onASmallPhone_stillLeavesRoomForContent() {
        PaymentActivity a = launchUnpaid();
        layout(a);
        float density = a.getResources().getDisplayMetrics().density;
        View scroller = a.findViewById(R.id.paymentRoot);
        assertTrue("content keeps a usable height above the bar on 320x568: " + scroller.getHeight() / density + "dp",
                scroller.getHeight() / density >= 300f);
    }

    // ---- keyboard and navigation bar ----

    private static void dispatchInsets(PaymentActivity a, int navBarBottomPx, int imeBottomPx) {
        WindowInsetsCompat insets = new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 0, 0, navBarBottomPx))
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, imeBottomPx))
                .build();
        a.findViewById(R.id.paymentContentColumn).dispatchApplyWindowInsets(insets.toWindowInsets());
        idle();
    }

    @Test
    public void insets_theBarClearsTheNavigationBar_andRidesAboveTheKeyboard() {
        PaymentActivity a = launchUnpaid();
        View column = a.findViewById(R.id.paymentContentColumn);

        dispatchInsets(a, 144, 0);
        assertEquals("above the navigation bar", 144, column.getPaddingBottom());
        assertTrue("amount line kept while typing is not happening", isVisible(a, R.id.paymentBarSummary));

        dispatchInsets(a, 144, 900);
        assertEquals("directly above the keyboard", 900, column.getPaddingBottom());
        assertFalse("amount line dropped while the keyboard is up so the bar stays small", isVisible(a, R.id.paymentBarSummary));

        dispatchInsets(a, 144, 0);
        assertEquals(144, column.getPaddingBottom());
        assertTrue(isVisible(a, R.id.paymentBarSummary));
    }

    // ---- GCash step 1: how to pay, copy amount ----

    @Test
    public void gcashStep1_hasNumberedHowToPaySteps_andTheExactAmount() {
        PaymentActivity a = launchUnpaid();
        proceedToGcash(a);
        layout(a);
        assertEquals("Pay exactly ₱360.00.", text(a, R.id.tvHowToPayAmountStep));
        assertEquals("₱360.00", text(a, R.id.tvPortalAmount));
        // numbered 1-4, in order, as one announcement each
        ViewGroup step1 = a.findViewById(R.id.gcashStep1Qr);
        List<String> numbers = new ArrayList<>();
        for (int i = 0; i < step1.getChildCount(); i++) {
            View child = step1.getChildAt(i);
            if (child instanceof ViewGroup && child.isScreenReaderFocusable()) {
                numbers.add(((TextView) ((ViewGroup) child).getChildAt(0)).getText().toString());
            }
        }
        assertEquals("[1, 2, 3, 4]", numbers.toString());
    }

    @Test
    public void gcashStep1_copyAmountPutsAPastableNumberOnTheClipboard() {
        PaymentActivity a = launchUnpaid();
        proceedToGcash(a);
        a.findViewById(R.id.btnCopyAmount).performClick();
        ClipboardManager clipboard = (ClipboardManager) app.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = clipboard.getPrimaryClip();
        assertNotNull(clip);
        assertEquals("no peso sign, no thousands separator - GCash's amount field takes a plain number",
                "360.00", clip.getItemAt(0).getText().toString());
    }

    @Test
    public void gcashStep1_theQrFillsTheAvailableWidth_notWiderThanItsCard() {
        PaymentActivity a = launchUnpaid();
        proceedToGcash(a);
        layout(a);
        View qr = a.findViewById(R.id.cardGcashQr);
        ViewGroup stepContainer = a.findViewById(R.id.gcashStep1Qr);
        assertTrue("the QR card (" + qr.getWidth() + "px) fits inside its container (" + stepContainer.getWidth() + "px)",
                qr.getWidth() <= stepContainer.getWidth());
        assertTrue("and uses the width a phone has (up to 300dp)",
                qr.getWidth() >= Math.min(stepContainer.getWidth(), 296 * a.getResources().getDisplayMetrics().density) - 2);
    }

    // ---- inline validation ----

    private static String inlineError(PaymentActivity a, int tilId) {
        TextInputLayout til = a.findViewById(tilId);
        return til.getError() == null ? null : til.getError().toString();
    }

    @Test
    public void validation_mobileNumber_saysWhatIsWrong_inline() {
        PaymentActivity a = launchUnpaid();
        proceedToGcash(a);
        a.findViewById(R.id.btnGcashStep1Next).performClick();
        MaterialButton next = a.findViewById(R.id.btnGcashStep2Next);

        assertFalse("Next waits for a valid number", next.isEnabled());
        assertNull("an untouched field shows no error", inlineError(a, R.id.tilGcashNumber));

        type(a, R.id.etGcashNumber, "8");
        assertEquals("a wrong first digit is flagged at once", a.getString(R.string.error_gcash_number_prefix), inlineError(a, R.id.tilGcashNumber));

        type(a, R.id.etGcashNumber, "9171");
        assertNull("an unfinished number is left alone while the guest is still typing", inlineError(a, R.id.tilGcashNumber));
        blur(a, R.id.etGcashNumber);
        assertEquals("leaving it unfinished says how many digits there are",
                a.getString(R.string.error_gcash_number_short_format, 4), inlineError(a, R.id.tilGcashNumber));
        assertFalse(next.isEnabled());

        type(a, R.id.etGcashNumber, NUMBER);
        assertNull("a valid number clears the error", inlineError(a, R.id.tilGcashNumber));
        assertTrue("and enables Next", next.isEnabled());
    }

    @Test
    public void validation_mobileNumber_acceptsTheShapesAGuestPastes() {
        PaymentActivity a = launchUnpaid();
        proceedToGcash(a);
        a.findViewById(R.id.btnGcashStep1Next).performClick();
        for (String pasted : new String[]{"09171234567", "+639171234567", "639171234567"}) {
            type(a, R.id.etGcashNumber, pasted);
            assertEquals(pasted + " is stored as the local number", NUMBER, text(a, R.id.etGcashNumber));
            assertNull(inlineError(a, R.id.tilGcashNumber));
        }
    }

    @Test
    public void validation_referenceNumber_saysHowManyDigitsWereEntered() {
        PaymentActivity a = launchUnpaid();
        proceedToGcash(a);
        a.findViewById(R.id.btnGcashStep1Next).performClick();
        type(a, R.id.etGcashNumber, NUMBER);
        a.findViewById(R.id.btnGcashStep2Next).performClick();
        MaterialButton next = a.findViewById(R.id.btnGcashStep3Next);

        assertFalse(next.isEnabled());
        assertNull(inlineError(a, R.id.tilGcashReferenceNumber));
        type(a, R.id.etGcashReferenceNumber, "12345");
        blur(a, R.id.etGcashReferenceNumber);
        assertEquals(a.getString(R.string.error_gcash_reference_length_format, 5), inlineError(a, R.id.tilGcashReferenceNumber));
        assertFalse(next.isEnabled());

        type(a, R.id.etGcashReferenceNumber, REFERENCE);
        assertNull("13 digits clears it", inlineError(a, R.id.tilGcashReferenceNumber));
        assertTrue(next.isEnabled());
    }

    @Test
    public void validation_receipt_tellsTheGuestWhatIsNeeded_untilOneIsAttached() {
        PaymentActivity a = launchUnpaid();
        proceedToGcash(a);
        a.findViewById(R.id.btnGcashStep1Next).performClick();
        type(a, R.id.etGcashNumber, NUMBER);
        a.findViewById(R.id.btnGcashStep2Next).performClick();
        type(a, R.id.etGcashReferenceNumber, REFERENCE);
        a.findViewById(R.id.btnGcashStep3Next).performClick();

        assertEquals(a.getString(R.string.receipt_not_attached), text(a, R.id.tvReceiptStatus));
        assertFalse("Next waits for the receipt", a.findViewById(R.id.btnGcashStep4Next).isEnabled());
        attachReceipt(a);
        assertEquals(a.getString(R.string.receipt_attached_success), text(a, R.id.tvReceiptStatus));
        assertTrue(a.findViewById(R.id.btnGcashStep4Next).isEnabled());
    }

    // ---- submission: loading, error, retry, success, no double submit ----

    @Test
    public void submit_showsLoading_blocksADoubleSubmit_thenSucceedsWithAmountAndReference() throws Exception {
        PaymentActivity a = toSubmitStep();
        MaterialButton submit = a.findViewById(R.id.completePaymentButton);
        assertTrue("Submit is ready with everything valid", submit.isEnabled());

        submit.performClick();
        idle();
        confirmLatestDialog(); // "Submit this GCash payment of ₱360.00?"
        answerBillCheck(ScreenTestSupport.reservation(588, 1800)); // bill unchanged

        // loading: a modal "processing" dialog, and the button says so and is disabled
        Dialog loading = ShadowDialog.getLatestDialog();
        assertTrue(loading.isShowing());
        assertEquals(a.getString(R.string.processing_payment), ((TextView) loading.findViewById(R.id.loadingMessage)).getText().toString());
        assertFalse("disabled while submitting", submit.isEnabled());
        assertEquals(a.getString(R.string.submitting_payment_button), submit.getText().toString());
        awaitSubmitCalls(1);

        // a second tap while the first is in flight must not send a second payment
        submit.performClick();
        idle();
        Dialog second = ShadowDialog.getLatestDialog();
        if (second instanceof AlertDialog && second != loading && ((AlertDialog) second).getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            ((AlertDialog) second).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        }
        Thread.sleep(200);
        idle();
        assertEquals("still exactly one request", 1, api.count("submitGcashPayment"));

        ScreenTestSupport.FakeCall<PaymentSubmitResponse> call = api.call("submitGcashPayment", 0);
        call.succeed(new PaymentSubmitResponse());
        idle();

        assertFalse("loading dialog is gone", loading.isShowing());
        assertFalse("no error banner on success", isVisible(a, R.id.cardSubmitError));
        Dialog success = ShadowDialog.getLatestDialog();
        assertTrue(success.isShowing());
        String message = ((TextView) success.findViewById(android.R.id.message)).getText().toString();
        assertTrue("says how much: " + message, message.contains("₱360.00"));
        assertTrue("and which reference: " + message, message.contains("9876 543 210987"));
        assertEquals(a.getString(R.string.payment_success_title),
                ((TextView) success.findViewById(androidx.appcompat.R.id.alertTitle)).getText().toString());
    }

    @Test
    public void submit_aFailure_staysOnScreenAsABanner_andCanBeRetried() throws Exception {
        PaymentActivity a = toSubmitStep();
        MaterialButton submit = a.findViewById(R.id.completePaymentButton);

        submit.performClick();
        idle();
        confirmLatestDialog();
        answerBillCheck(ScreenTestSupport.reservation(588, 1800));
        Dialog loading = ShadowDialog.getLatestDialog();
        awaitSubmitCalls(1);

        ScreenTestSupport.FakeCall<PaymentSubmitResponse> first = api.call("submitGcashPayment", 0);
        first.http(500);
        idle();

        assertFalse("loading is over", loading.isShowing());
        assertTrue("the error stays on the review step as a banner, not a toast that disappears", isVisible(a, R.id.cardSubmitError));
        assertEquals(a.getString(R.string.error_server_error), text(a, R.id.tvSubmitErrorMessage));
        assertEquals("still on the review step with the guest's details intact", View.VISIBLE, a.findViewById(R.id.gcashStep5Review).getVisibility());
        assertTrue("Submit is available again to retry", submit.isEnabled());
        assertEquals(a.getString(R.string.submit_payment_verification_button), submit.getText().toString());

        // retry: the banner goes away as soon as the next attempt starts, and the second request is sent
        submit.performClick();
        idle();
        confirmLatestDialog();
        answerBillCheck(ScreenTestSupport.reservation(588, 1800));
        assertFalse("the old error is cleared when a new attempt begins", isVisible(a, R.id.cardSubmitError));
        awaitSubmitCalls(2);
        ScreenTestSupport.FakeCall<PaymentSubmitResponse> second = api.call("submitGcashPayment", 1);
        second.succeed(new PaymentSubmitResponse());
        idle();
        assertFalse(isVisible(a, R.id.cardSubmitError));
        assertTrue(ShadowDialog.getLatestDialog().isShowing());
    }

    @Test
    public void submit_offline_isRefusedAtOnce_withABannerAndNoRequest() throws Exception {
        PaymentActivity a = toSubmitStep();
        // go offline again
        ConnectivityManager cm = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        shadowOf(cm).setNetworkCapabilities(cm.getActiveNetwork(), ShadowNetworkCapabilities.newInstance());

        a.findViewById(R.id.completePaymentButton).performClick();
        idle();
        confirmLatestDialog();
        assertTrue(isVisible(a, R.id.cardSubmitError));
        assertEquals(a.getString(R.string.error_no_internet_connection), text(a, R.id.tvSubmitErrorMessage));
        Thread.sleep(100);
        assertEquals("nothing was sent", 0, api.count("submitGcashPayment"));
        assertTrue("the guest can retry", a.findViewById(R.id.completePaymentButton).isEnabled());
    }

    // ---- the bill changing while the guest is off paying in the GCash app ----

    private static String latestDialogTitle() {
        Dialog d = ShadowDialog.getLatestDialog();
        TextView t = d == null ? null : d.findViewById(androidx.appcompat.R.id.alertTitle);
        return t == null ? null : t.getText().toString();
    }

    private void leaveAndComeBack() {
        controller.pause();
        idle();
        controller.resume();
        idle();
    }

    @Test
    public void returnToGcash_unchangedBill_saysNothingAndShowsNothingNew() {
        PaymentActivity a = toSubmitStep();
        Dialog before = ShadowDialog.getLatestDialog();
        String amountBefore = text(a, R.id.tvAmountToPay);

        leaveAndComeBack();
        answerBillCheck(ScreenTestSupport.reservation(588, 1800));

        assertEquals("no new dialog", before, ShadowDialog.getLatestDialog());
        assertEquals(amountBefore, text(a, R.id.tvAmountToPay));
    }

    @Test
    public void returnToGcash_changedBill_updatesAmount_keepsInput_andNeedsConfirmBeforeSubmit() throws Exception {
        PaymentActivity a = toSubmitStep();
        String amountBefore = text(a, R.id.tvAmountToPay);

        leaveAndComeBack();
        answerBillCheck(ScreenTestSupport.reservation(588, 1500));

        assertEquals(a.getString(R.string.bill_changed_title), latestDialogTitle());
        assertNotEquals("the amount shown follows the new bill", amountBefore, text(a, R.id.tvAmountToPay));
        assertEquals("the pinned bar quotes the same amount", text(a, R.id.tvAmountToPay), text(a, R.id.tvBarAmount));
        assertEquals("typed reference kept", REFERENCE, ((EditText) a.findViewById(R.id.etGcashReferenceNumber)).getText().toString());
        assertEquals("receipt kept", a.getString(R.string.receipt_attached_success), text(a, R.id.tvReceiptStatus));

        // confirm the notice, then submit: the pre-submit re-read passes and the payment goes out once
        confirmLatestDialog();
        a.findViewById(R.id.completePaymentButton).performClick();
        idle();
        confirmLatestDialog();
        answerBillCheck(ScreenTestSupport.reservation(588, 1500));
        awaitSubmitCalls(1);
    }

    @Test
    public void submit_beforeConfirmingAChangedAmount_isBlocked() {
        PaymentActivity a = toSubmitStep();
        leaveAndComeBack();
        answerBillCheck(ScreenTestSupport.reservation(588, 1500));
        // the guest dismisses nothing and goes straight for Submit: the notice comes back, nothing is sent
        a.findViewById(R.id.completePaymentButton).performClick();
        idle();
        confirmLatestDialog(); // the "Submit this payment?" dialog
        assertEquals(a.getString(R.string.bill_changed_title), latestDialogTitle());
        assertEquals(0, api.count("submitGcashPayment"));
    }

    @Test
    public void submit_billChangedRightBeforeSending_stopsAndShowsTheNewAmount() {
        PaymentActivity a = toSubmitStep();
        a.findViewById(R.id.completePaymentButton).performClick();
        idle();
        confirmLatestDialog();
        answerBillCheck(ScreenTestSupport.reservation(588, 1500));

        assertEquals(a.getString(R.string.bill_changed_title), latestDialogTitle());
        assertEquals("nothing was sent", 0, api.count("submitGcashPayment"));
        assertEquals("the form is intact", View.VISIBLE, a.findViewById(R.id.gcashStep5Review).getVisibility());
        assertTrue("and submittable again once confirmed", a.findViewById(R.id.completePaymentButton).isEnabled());
    }

    @Test
    public void submit_billCheckFails_doesNotSend_andRetryWorks() throws Exception {
        PaymentActivity a = toSubmitStep();
        a.findViewById(R.id.completePaymentButton).performClick();
        idle();
        confirmLatestDialog();
        ScreenTestSupport.FakeCall<ReservationDto> failing = api.last("getReservation");
        failing.http(500);
        idle();

        assertEquals(a.getString(R.string.bill_check_failed_title), latestDialogTitle());
        assertEquals(0, api.count("submitGcashPayment"));
        assertEquals("typed reference kept", REFERENCE, ((EditText) a.findViewById(R.id.etGcashReferenceNumber)).getText().toString());

        confirmLatestDialog(); // Retry
        answerBillCheck(ScreenTestSupport.reservation(588, 1800));
        awaitSubmitCalls(1);
    }

    // ---- shape ----

    private static void collectProblems(View v, List<String> problems) {
        String name = v.getClass().getSimpleName() + (v.getId() == View.NO_ID ? "" : "#" + v.getResources().getResourceEntryName(v.getId()));
        if (!(v instanceof MaterialCardView) && v.getElevation() != 0f) problems.add(name + " elevation=" + v.getElevation());
        if (v instanceof MaterialButton) {
            MaterialButton b = (MaterialButton) v;
            if (b.getCornerRadius() != 0) problems.add(name + " cornerRadius=" + b.getCornerRadius());
            if (b.getStateListAnimator() != null) problems.add(name + " has a press animator");
        }
        if (v instanceof MaterialCardView) {
            MaterialCardView c = (MaterialCardView) v;
            if (c.getRadius() != v.getResources().getDimension(R.dimen.card_corner_radius)) problems.add(name + " radius=" + c.getRadius());
            if (c.getCardElevation() != v.getResources().getDimension(R.dimen.card_elevation)) problems.add(name + " cardElevation=" + c.getCardElevation());
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collectProblems(g.getChildAt(i), problems);
        }
    }

    @Test
    public void noShadowsAndNoRoundedCorners_onReviewBillingOrAnyGcashStep() {
        PaymentActivity a = launchUnpaid();
        List<String> problems = new ArrayList<>();
        // the screen's own content: the scroller, the action bar - not the shared header (circular profile photo), drawer or offline banner
        collectProblems(a.findViewById(R.id.screenContent), problems);
        collectProblems(a.findViewById(R.id.paymentActionBar), problems);
        proceedToGcash(a);
        collectProblems(a.findViewById(R.id.screenContent), problems);
        assertTrue(problems.toString(), problems.isEmpty());
        AppBarProblems.assertNoShadow(a);
    }

    /** The raised header casts no shadow (checked on the real view). */
    private static final class AppBarProblems {
        static void assertNoShadow(PaymentActivity a) {
            View appBar = a.findViewById(R.id.appBarLayout);
            assertNull("no state-list animator that could raise it", appBar.getStateListAnimator());
            if (appBar.getOutlineProvider() != null) {
                android.graphics.Outline outline = new android.graphics.Outline();
                appBar.getOutlineProvider().getOutline(appBar, outline);
                assertTrue("a raised view with an outline would cast a shadow", outline.isEmpty() || outline.getAlpha() == 0f);
            }
        }
    }
}
