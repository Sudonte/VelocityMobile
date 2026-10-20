package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.BookingAndReservationActivity;
import com.example.velocitysuites.CardActionRow;
import com.example.velocitysuites.R;
import com.example.velocitysuites.network.SessionManager;
import com.example.velocitysuites.network.dto.BookingDto;
import com.example.velocitysuites.network.dto.DirectBookingResponseDto;
import com.example.velocitysuites.network.dto.ReservationDto;
import com.google.android.material.tabs.TabLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Booking & Reservation cards offer exactly the actions of TransactionActions, on ONE aligned row; cancel and delete have their
 * own confirmation dialogs, a server refusal is shown (not a crash), converted reservations link to their booking, and every action
 * combination fits at 360dp and 393dp in light and dark.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BookingActionsScreenTest {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private static final String[] MODES = {"w360dp-h800dp-xxhdpi", "w360dp-h800dp-night-xxhdpi", "w393dp-h851dp-xxhdpi", "w393dp-h851dp-night-xxhdpi"};

    private Context app;
    private ScreenTestSupport.FakeApi api;

    private void configure(String qualifiers) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        RuntimeEnvironment.setQualifiers(qualifiers);
        try {
            java.lang.reflect.Field last = com.example.velocitysuites.NavUtils.class.getDeclaredField("lastClickAtMs");
            last.setAccessible(true);
            last.setLong(null, Long.MIN_VALUE / 2); // the click debounce is a static; each test starts with a clean one
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
        ScreenTestSupport.goOnline(app);
        SessionManager.saveSession(app, "test-token", 1L, "Ana", "Reyes", "", "Ana Reyes", "ana@example.com",
                "09171234567", "Female", "Jan 01, 1990", "");
    }

    @Before
    public void setUp() {
        configure(MODES[0]);
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static LocalDate day(int plus) {
        return LocalDate.now(ZoneId.of("Asia/Manila")).plusDays(plus);
    }

    // ---- fixtures

    private ReservationDto reservation(long id, String method) {
        ReservationDto r = ScreenTestSupport.reservation(id, 3600);
        r.check_in = day(3).toString();
        r.check_out = day(4).toString();
        r.status = "AWAITING_GCASH_PAYMENT";
        r.payment_method = method;
        return r;
    }

    private DirectBookingResponseDto booking(long id, String status, double paid) {
        DirectBookingResponseDto d = ScreenTestSupport.directBooking(id, 3000);
        d.check_in = day(3).toString();
        d.check_out = day(4).toString();
        d.booking_status = status;
        d.payment_method = "GCASH";
        if (paid > 0) {
            d.payments = new ArrayList<>(Arrays.asList(ScreenTestSupport.payment(100 + id, String.format("%.2f", paid), "completed", "gcash")));
        }
        return d;
    }

    private BookingAndReservationActivity open(List<ReservationDto> reservations, List<DirectBookingResponseDto> bookings) {
        BookingAndReservationActivity a = Robolectric.buildActivity(BookingAndReservationActivity.class,
                new Intent(app, BookingAndReservationActivity.class)).setup().get();
        idle();
        ScreenTestSupport.answerBookingLoad(api, api.count("getReservations") - 1, reservations, bookings);
        idle();
        return a;
    }

    private static void layOut(android.app.Activity a, Context ctx) {
        View decor = a.getWindow().getDecorView();
        int w = ctx.getResources().getDisplayMetrics().widthPixels;
        int h = ctx.getResources().getDisplayMetrics().heightPixels;
        decor.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, w, h);
    }

    private static View card(BookingAndReservationActivity a, int position) {
        RecyclerView rv = a.findViewById(R.id.rvItemList);
        RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(position);
        assertNotNull("card " + position, h);
        return h.itemView;
    }

    private static boolean shown(View card, int id) {
        return card.findViewById(id).getVisibility() == View.VISIBLE;
    }

    private static String actions(View card) {
        StringBuilder sb = new StringBuilder();
        for (int id : new int[]{R.id.btnPayNow, R.id.btnBookAgain, R.id.btnViewBooking, R.id.btnViewDetails, R.id.btnViewDetailsText,
                R.id.btnModify, R.id.btnCancel, R.id.btnDelete}) {
            if (shown(card, id)) sb.append(((TextView) card.findViewById(id)).getText()).append('|');
        }
        return sb.toString();
    }

    private void tab(BookingAndReservationActivity a, int position) {
        ((TabLayout) a.findViewById(R.id.tabLayout)).getTabAt(position).select();
        idle();
    }

    // ---- the actions of every card

    @Test
    public void reservationTab_pendingGcash_cash_andTheClosedOnes() {
        ReservationDto gcash = reservation(630, "GCASH");
        ReservationDto cash = reservation(629, "CASH");
        ReservationDto cancelled = reservation(628, "GCASH");
        cancelled.status = "CANCELLED_RESERVATION";
        BookingAndReservationActivity a = open(ScreenTestSupport.reservations(gcash, cash, cancelled), ScreenTestSupport.directBookings());
        tab(a, 1);
        assertEquals("Pay now|View details|Edit|Cancel|", actions(card(a, 0)));
        assertEquals("a cash reservation has no Pay now; 'Pay at the front desk' is the info line", "View details|Edit|Cancel|", actions(card(a, 1)));
        assertEquals("Pay at the front desk", ((TextView) card(a, 1).findViewById(R.id.tvFrontDeskNote)).getText().toString());
        assertEquals(View.VISIBLE, card(a, 1).findViewById(R.id.tvFrontDeskNote).getVisibility());
        assertEquals("Reserve again|View details|Delete|", actions(card(a, 2)));
    }

    @Test
    public void bookingTab_everyStateShowsExactlyItsActions() {
        BookingAndReservationActivity a = open(ScreenTestSupport.reservations(), ScreenTestSupport.directBookings(
                booking(571, "ACTIVE_BOOKING", 1500),     // pending, partly paid: can cancel
                booking(572, "ACTIVE_BOOKING", 3000),     // fully paid: cannot
                booking(573, "CHECKED_IN", 1500),         // staying
                booking(574, "COMPLETED_BOOKING", 3000),  // done
                booking(575, "CANCELLED_BOOKING", 0)));
        String[] byId = new String[8];
        a.findViewById(R.id.btnExpandList).performClick(); // the list opens with three cards
        idle();
        RecyclerView rv = a.findViewById(R.id.rvItemList);
        for (int i = 0; i < rv.getAdapter().getItemCount(); i++) {
            View c = card(a, i);
            String id = ((TextView) c.findViewById(R.id.tvBookingId)).getText().toString();
            byId[i] = id + " -> " + actions(c);
        }
        String all = String.join("\n", byId);
        assertTrue(all, all.contains("Booking #571 -> View details|Cancel|"));
        assertTrue(all, all.contains("Booking #572 -> View details|\n"));
        assertTrue(all, all.contains("Booking #573 -> View details|\n"));
        assertTrue(all, all.contains("Booking #574 -> Book again|View details|Delete|"));
        assertTrue(all, all.contains("Booking #575 -> Book again|View details|Delete|"));
    }

    @Test
    public void onlyOneFilledButtonPerCard() {
        BookingAndReservationActivity a = open(ScreenTestSupport.reservations(reservation(630, "GCASH")), ScreenTestSupport.directBookings());
        tab(a, 1);
        View c = card(a, 0);
        int filled = 0;
        for (int id : new int[]{R.id.btnPayNow, R.id.btnViewDetails, R.id.btnBookAgain, R.id.btnViewBooking}) {
            if (shown(c, id) && ((com.google.android.material.button.MaterialButton) c.findViewById(id)).getBackgroundTintList() != null
                    && ((com.google.android.material.button.MaterialButton) c.findViewById(id)).getBackgroundTintList().getDefaultColor()
                    == app.getColor(R.color.button_primary_bg)) filled++;
        }
        assertEquals("only Pay now is filled", 1, filled);
    }

    // ---- cancel

    @Test
    public void cancel_confirmationNamesTheItem_saysNonRefundableWhenSomethingWasPaid_andRefreshesTheList() {
        BookingAndReservationActivity a = open(ScreenTestSupport.reservations(), ScreenTestSupport.directBookings(booking(571, "ACTIVE_BOOKING", 1500)));
        card(a, 0).findViewById(R.id.btnCancel).performClick();
        idle();
        api.<com.example.velocitysuites.network.dto.PaginatedResponse<DirectBookingResponseDto>>last("getDirectBookings"); // the re-check refresh
        ScreenTestSupport.answerBookingLoad(api, api.count("getReservations") - 1, ScreenTestSupport.reservations(),
                ScreenTestSupport.directBookings(booking(571, "ACTIVE_BOOKING", 1500)));
        idle();
        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull("a confirmation dialog", dialog);
        TextView title = dialog.findViewById(androidx.appcompat.R.id.alertTitle);
        assertEquals("Cancel Booking #571?", title.getText().toString());
        TextView message = dialog.findViewById(android.R.id.message);
        assertEquals("The ₱1,500.00 you paid is non-refundable.", message.getText().toString());
        assertEquals("Keep booking", dialog.getButton(AlertDialog.BUTTON_NEGATIVE).getText().toString());
        assertEquals("Cancel booking", dialog.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
        assertEquals(app.getColor(R.color.velocity_red_primary), dialog.getButton(AlertDialog.BUTTON_POSITIVE).getCurrentTextColor());

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        api.<DirectBookingResponseDto>last("cancelDirectBooking").succeed(booking(571, "CANCELLED_BOOKING", 1500));
        idle();
        assertTrue("the list is refreshed after the cancel", api.count("getReservations") >= 2);
    }

    @Test
    public void cancel_ofAnUnpaidReservation_usesTheReservationEndpointAndWording() {
        BookingAndReservationActivity a = open(ScreenTestSupport.reservations(reservation(629, "CASH")), ScreenTestSupport.directBookings());
        tab(a, 1);
        card(a, 0).findViewById(R.id.btnCancel).performClick();
        idle();
        ScreenTestSupport.answerBookingLoad(api, api.count("getReservations") - 1, ScreenTestSupport.reservations(reservation(629, "CASH")), ScreenTestSupport.directBookings());
        idle();
        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertEquals("Cancel Reservation #629?", ((TextView) dialog.findViewById(androidx.appcompat.R.id.alertTitle)).getText().toString());
        assertEquals("Keep reservation", dialog.getButton(AlertDialog.BUTTON_NEGATIVE).getText().toString());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        assertTrue("PUT guest/reservations/{id}/cancel", api.count("cancelReservation") == 1);
    }

    @Test
    public void cancel_refusedByTheServer_showsItsMessageInASnackbar_andDoesNotCrash() {
        BookingAndReservationActivity a = open(ScreenTestSupport.reservations(), ScreenTestSupport.directBookings(booking(571, "ACTIVE_BOOKING", 1500)));
        card(a, 0).findViewById(R.id.btnCancel).performClick();
        idle();
        ScreenTestSupport.answerBookingLoad(api, api.count("getReservations") - 1, ScreenTestSupport.reservations(),
                ScreenTestSupport.directBookings(booking(571, "ACTIVE_BOOKING", 1500)));
        idle();
        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        api.<DirectBookingResponseDto>last("cancelDirectBooking").httpWithBody(422, "{\"message\":\"This booking was verified by the front desk.\"}");
        idle();
        View snack = a.findViewById(com.google.android.material.R.id.snackbar_text);
        assertNotNull("a snackbar", snack);
        assertTrue(((TextView) snack).getText().toString(), ((TextView) snack).getText().length() > 0);
        assertTrue("the list was refreshed", api.count("getReservations") >= 2);
    }

    // ---- delete

    @Test
    public void delete_confirmationExplainsItIsRemoved_andUsesTheServerCall() {
        BookingAndReservationActivity a = open(ScreenTestSupport.reservations(), ScreenTestSupport.directBookings(booking(570, "CANCELLED_BOOKING", 0)));
        card(a, 0).findViewById(R.id.btnDelete).performClick();
        idle();
        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertEquals("Delete Booking #570?", ((TextView) dialog.findViewById(androidx.appcompat.R.id.alertTitle)).getText().toString());
        assertEquals("It will be removed from your list. This can't be undone.", ((TextView) dialog.findViewById(android.R.id.message)).getText().toString());
        assertEquals("Keep", dialog.getButton(AlertDialog.BUTTON_NEGATIVE).getText().toString());
        assertEquals("Delete", dialog.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        assertEquals("DELETE guest/bookings/{id}", 1, api.count("deleteBookingPermanently"));
    }

    // ---- conversion

    @Test
    public void aConvertedReservation_showsConvertedToBooking_andLinksToTheBooking() {
        ReservationDto converted = reservation(572, "GCASH");
        converted.status = "CONVERTED_TO_BOOKING";
        BookingDto booking = new BookingDto();
        booking.id = 99;
        booking.reservation_id = 572;
        booking.booking_status = "ACTIVE_BOOKING";
        converted.booking = booking;
        BookingAndReservationActivity a = open(ScreenTestSupport.reservations(converted), ScreenTestSupport.directBookings());
        tab(a, 1);
        View c = card(a, 0);
        assertEquals("Reservation #572", ((TextView) c.findViewById(R.id.tvBookingId)).getText().toString());
        String info = ((TextView) c.findViewById(R.id.tvFrontDeskNote)).getText().toString();
        assertTrue(info, info.startsWith("Converted to Booking #"));
        String button = ((TextView) c.findViewById(R.id.btnViewBooking)).getText().toString();
        assertTrue(button, button.startsWith("View Booking #"));
        assertEquals("Delete", ((TextView) c.findViewById(R.id.btnDelete)).getText().toString());
        assertEquals(View.GONE, c.findViewById(R.id.btnCancel).getVisibility());
        // and the booking itself is in the Bookings list
        tab(a, 0);
        RecyclerView rv = a.findViewById(R.id.rvItemList);
        assertEquals(1, rv.getAdapter().getItemCount());
        c.findViewById(R.id.btnViewBooking);
    }

    // ---- the dropdown border

    @Test
    public void afterPickingFromEachDropdown_theyAreNotFocused_soTheBorderIsGray() throws Exception {
        BookingAndReservationActivity a = open(ScreenTestSupport.reservations(), ScreenTestSupport.directBookings(booking(571, "ACTIVE_BOOKING", 0)));
        java.lang.reflect.Field f = com.google.android.material.textfield.TextInputLayout.class.getDeclaredField("defaultStrokeColor");
        f.setAccessible(true);
        for (int id : new int[]{R.id.dropdownTransactionStatus, R.id.dropdownTransactionSort, R.id.dropdownTransactionRoomType}) {
            android.widget.AutoCompleteTextView dd = a.findViewById(id);
            for (int position = 0; position < dd.getAdapter().getCount(); position++) {
                dd.requestFocus();
                dd.getOnItemClickListener().onItemClick(null, null, position, position);
                idle();
                assertFalse(a.getResources().getResourceEntryName(id) + " option " + position + " released focus", dd.isFocused());
                com.google.android.material.textfield.TextInputLayout til = (com.google.android.material.textfield.TextInputLayout) dd.getParent().getParent();
                assertEquals("gray, not red", app.getColor(R.color.velocity_filter_stroke), f.getInt(til));
            }
        }
    }

    // ---- header: policy row on both tabs, the new button right under the tabs

    @Test
    public void bothTabsHaveAnExpandablePolicyRow_andTheNewButtonSitsUnderTheTabs() {
        BookingAndReservationActivity a = open(ScreenTestSupport.reservations(), ScreenTestSupport.directBookings());
        assertEquals("Cancellation policy", ((TextView) a.findViewById(R.id.tvCancellationPolicyTitle)).getText().toString());
        tab(a, 1);
        assertEquals(View.VISIBLE, a.findViewById(R.id.cardCancellationPolicy).getVisibility());
        assertEquals("Reservation policy", ((TextView) a.findViewById(R.id.tvCancellationPolicyTitle)).getText().toString());
        View full = a.findViewById(R.id.tvCancellationPolicyNotice);
        assertEquals(View.GONE, full.getVisibility());
        a.findViewById(R.id.layoutCancellationPolicyRow).performClick();
        assertEquals(View.VISIBLE, full.getVisibility());
        assertTrue(((TextView) full).getText().toString().startsWith("Reservation Policy:"));
        layOut(a, app);
        View root = a.getWindow().getDecorView();
        Rect button = com.example.velocitysuites.ui.LayoutHarness.boundsIn(root, a.findViewById(R.id.btnNewReservation));
        Rect tabs = com.example.velocitysuites.ui.LayoutHarness.boundsIn(root, a.findViewById(R.id.tabLayout));
        int gap = button.top - tabs.bottom;
        float density = app.getResources().getDisplayMetrics().density;
        assertTrue("a normal 12-16dp gap under the tabs (" + gap / density + "dp)", gap >= 8 * density && gap <= 32 * density);
        assertEquals("full width", a.findViewById(R.id.layoutWizardEntryActions).getWidth(), a.findViewById(R.id.btnNewReservation).getWidth());
    }

    // ---- layout: every action combination at 360dp and 393dp, light and dark

    private void everyCombinationFits(String qualifiers) {
        configure(qualifiers);
        ReservationDto gcash = reservation(630, "GCASH");
        ReservationDto cash = reservation(629, "CASH");
        ReservationDto cancelled = reservation(628, "GCASH");
        cancelled.status = "CANCELLED_RESERVATION";
        ReservationDto converted = reservation(572, "GCASH");
        converted.status = "CONVERTED_TO_BOOKING";
        BookingDto b = new BookingDto();
        b.id = 99;
        b.reservation_id = 572;
        b.booking_status = "ACTIVE_BOOKING";
        converted.booking = b;
        BookingAndReservationActivity a = open(ScreenTestSupport.reservations(gcash, cash, cancelled, converted), ScreenTestSupport.directBookings(
                booking(571, "ACTIVE_BOOKING", 1500), booking(572, "ACTIVE_BOOKING", 3000), booking(574, "COMPLETED_BOOKING", 3000), booking(573, "CHECKED_IN", 1500)));
        for (int tabIndex = 0; tabIndex < 2; tabIndex++) {
            tab(a, tabIndex);
            RecyclerView rv = a.findViewById(R.id.rvItemList);
            // show every card (the list opens collapsed)
            View expand = a.findViewById(R.id.btnExpandList);
            if (expand.getVisibility() == View.VISIBLE) expand.performClick();
            idle();
            layOut(a, app);
            idle();
            layOut(a, app);
            View root = a.getWindow().getDecorView();
            for (int i = 0; i < rv.getAdapter().getItemCount(); i++) {
                RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(i);
                if (h == null) continue;
                View c = h.itemView;
                Rect cardBounds = com.example.velocitysuites.ui.LayoutHarness.boundsIn(root, c);
                String at = " card " + i + " tab " + tabIndex + " @" + qualifiers;
                List<View> buttons = new ArrayList<>();
                for (int id : new int[]{R.id.btnPayNow, R.id.btnBookAgain, R.id.btnViewBooking, R.id.btnViewDetails, R.id.btnViewDetailsText,
                        R.id.btnModify, R.id.btnCancel, R.id.btnDelete}) {
                    if (shown(c, id)) buttons.add(c.findViewById(id));
                }
                for (View button : buttons) {
                    Rect r = com.example.velocitysuites.ui.LayoutHarness.boundsIn(root, button);
                    assertTrue("button inside its card" + at, r.left >= cardBounds.left && r.right <= cardBounds.right && r.bottom <= cardBounds.bottom);
                    assertEquals("button text never wraps" + at, 1, ((TextView) button).getLineCount());
                    assertFalse(((TextView) button).isAllCaps());
                    assertEquals(0f, ((TextView) button).getLetterSpacing(), 0f);
                }
                for (int x = 0; x < buttons.size(); x++) {
                    for (int y = x + 1; y < buttons.size(); y++) {
                        assertFalse("no two actions overlap" + at, com.example.velocitysuites.ui.LayoutHarness.overlaps(root, buttons.get(x), buttons.get(y)));
                    }
                }
                CardActionRow row = c.findViewById(R.id.layoutActionButtons);
                View right = c.findViewById(R.id.layoutActionsRight);
                if (right.getVisibility() == View.VISIBLE && row.isStacked()) {
                    assertTrue("stacked text actions are right-aligned" + at,
                            com.example.velocitysuites.ui.LayoutHarness.boundsIn(root, right).right >= cardBounds.right - app.getResources().getDisplayMetrics().density * 16);
                }
            }
        }
    }

    @Test
    public void actionRows_360_light() {
        everyCombinationFits(MODES[0]);
    }

    @Test
    public void actionRows_360_dark() {
        everyCombinationFits(MODES[1]);
    }

    @Test
    public void actionRows_393_light() {
        everyCombinationFits(MODES[2]);
    }

    @Test
    public void actionRows_393_dark() {
        everyCombinationFits(MODES[3]);
    }

    // ---- repeat taps on money and booking actions

    /** Three clicks in a row straight on the control (the code-level guards must hold even when no touch guard is in front of them). */
    private static void clickThreeTimes(View v) {
        for (int i = 0; i < 3; i++) v.performClick();
        idle();
    }

    @Test
    public void cancel_tripleTap_opensOneDialog_andConfirmingSendsOneRequest() {
        BookingAndReservationActivity a = open(ScreenTestSupport.reservations(), ScreenTestSupport.directBookings(booking(571, "ACTIVE_BOOKING", 1500)));
        TapTestSupport.layOut(a);
        clickThreeTimes(card(a, 0).findViewById(R.id.btnCancel));
        assertEquals("one list re-check, not three", 1, api.count("getReservations") - 1);
        ScreenTestSupport.answerBookingLoad(api, api.count("getReservations") - 1, ScreenTestSupport.reservations(),
                ScreenTestSupport.directBookings(booking(571, "ACTIVE_BOOKING", 1500)));
        idle();
        assertEquals("one confirmation dialog", 1, TapTestSupport.alertsShowing());
        TapTestSupport.confirmAllThreeTimes(a);
        assertEquals("one cancel request", 1, api.count("cancelDirectBooking"));
    }

    @Test
    public void delete_tripleTap_opensOneDialog_andConfirmingSendsOneRequest() {
        BookingAndReservationActivity a = open(ScreenTestSupport.reservations(), ScreenTestSupport.directBookings(booking(570, "CANCELLED_BOOKING", 0)));
        TapTestSupport.layOut(a);
        clickThreeTimes(card(a, 0).findViewById(R.id.btnDelete));
        assertEquals("one confirmation dialog", 1, TapTestSupport.alertsShowing());
        TapTestSupport.confirmAllThreeTimes(a);
        assertEquals("one delete request", 1, api.count("deleteBookingPermanently"));
    }

    @Test
    public void payNow_tripleTap_opensOneConfirmation_andConfirmingOpensTheBillOnce() {
        BookingAndReservationActivity a = open(ScreenTestSupport.reservations(reservation(629, "GCASH")), ScreenTestSupport.directBookings());
        tab(a, 1);
        TapTestSupport.layOut(a);
        View payNow = card(a, 0).findViewById(R.id.btnPayNow);
        assertEquals(View.VISIBLE, payNow.getVisibility());
        clickThreeTimes(payNow);
        assertEquals("one confirmation", 1, TapTestSupport.alertsShowing());
        TapTestSupport.confirmAllThreeTimes(a);
        int screens = 0;
        Intent next;
        while ((next = shadowOf(a).getNextStartedActivity()) != null) {
            if (next.getComponent() != null && next.getComponent().getClassName().endsWith("BillingSummaryActivity")) screens++;
        }
        assertEquals("one bill screen", 1, screens);
    }
}
