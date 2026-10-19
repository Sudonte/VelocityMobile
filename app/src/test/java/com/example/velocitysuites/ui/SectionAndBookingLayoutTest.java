package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
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
import com.google.android.material.card.MaterialCardView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Layout, at 360dp and 393dp in light and dark: every dashboard section sits in the same container (same edges, same style),
 * the Quick Actions are four equal tiles, and the Booking & Reservation header, filters and cards fit without clipping or overlap.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SectionAndBookingLayoutTest {

    private static final String[] MODES = {"w360dp-h800dp-xxhdpi", "w360dp-h800dp-night-xxhdpi", "w393dp-h851dp-xxhdpi", "w393dp-h851dp-night-xxhdpi"};

    private Context app;
    private ScreenTestSupport.FakeApi api;

    private void configure(String qualifiers) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        RuntimeEnvironment.setQualifiers(qualifiers);
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

    private static void layOut(android.app.Activity a, Context ctx) {
        View decor = a.getWindow().getDecorView();
        int w = ctx.getResources().getDisplayMetrics().widthPixels;
        int h = ctx.getResources().getDisplayMetrics().heightPixels;
        decor.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, w, h);
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Manila"));
    }

    private DirectBookingResponseDto booking() {
        DirectBookingResponseDto d = ScreenTestSupport.directBooking(569, 9750);
        d.check_in = today().plusDays(1).toString();
        d.check_out = today().plusDays(2).toString();
        d.booking_status = "CHECKED_IN";
        d.payment_method = "GCASH";
        d.payments = new java.util.ArrayList<>(java.util.Arrays.asList(ScreenTestSupport.payment(9, "7560.00", "completed", "gcash")));
        PaymentSummaryDto s = new PaymentSummaryDto();
        s.grand_total = 7860;
        s.total_amount_paid = 7560;
        s.remaining_balance = 300;
        s.payment_status = "PARTIALLY_PAID";
        d.payment_summary = s;
        return d;
    }

    // ---- the dashboard

    private void dashboardAt(String qualifiers) {
        configure(qualifiers);
        DashboardActivity a = Robolectric.buildActivity(DashboardActivity.class).setup().get();
        idle();
        ReservationDto r = ScreenTestSupport.reservation(630, 3600);
        r.check_in = today().plusDays(1).toString();
        r.check_out = today().plusDays(2).toString();
        ScreenTestSupport.answerBookingLoad(api, api.count("getReservations") - 1, ScreenTestSupport.reservations(r), ScreenTestSupport.directBookings(booking()));
        idle();
        layOut(a, app);
        idle();
        layOut(a, app);
        View root = a.getWindow().getDecorView();
        int width = app.getResources().getDisplayMetrics().widthPixels;
        float density = app.getResources().getDisplayMetrics().density;

        int[] sections = {R.id.nextStaySectionCard, R.id.quickActionsCard, R.id.stayCalendar, R.id.moreStaysSection, R.id.recentUpcomingCard,
                R.id.bookingReservationSummaryCard, R.id.bookingDetailsCard, R.id.notificationsCard, R.id.paymentStatusCard, R.id.accountSummaryCard};
        MaterialCardView reference = a.findViewById(R.id.accountSummaryCard);
        Rect refBounds = LayoutHarness.boundsIn(root, reference);
        for (int id : sections) {
            View v = a.findViewById(id);
            String name = a.getResources().getResourceEntryName(id) + " @" + qualifiers;
            assertNotNull(name, v);
            assertTrue(name + " is a card", v instanceof MaterialCardView);
            MaterialCardView c = (MaterialCardView) v;
            assertEquals(name + " border width", reference.getStrokeWidth(), c.getStrokeWidth());
            assertEquals(name + " border color", reference.getStrokeColor(), c.getStrokeColor());
            assertEquals(name + " radius", reference.getRadius(), c.getRadius(), 0.01f);
            assertEquals(name + " elevation", reference.getCardElevation(), c.getCardElevation(), 0.01f);
            Rect b = LayoutHarness.boundsIn(root, v);
            assertEquals(name + " left edge", refBounds.left, b.left);
            assertEquals(name + " right edge", refBounds.right, b.right);
            assertTrue(name + " inside the screen", b.left >= 0 && b.right <= width);
        }

        // Quick Actions: four equal tiles in a 2 x 2 grid, 40dp round icons, labels in sentence case
        int[] tiles = {R.id.actionBrowseRooms, R.id.actionBooking, R.id.actionReservation, R.id.actionHistory};
        Rect first = LayoutHarness.boundsIn(root, a.findViewById(tiles[0]));
        Rect second = LayoutHarness.boundsIn(root, a.findViewById(tiles[1]));
        Rect third = LayoutHarness.boundsIn(root, a.findViewById(tiles[2]));
        Rect fourth = LayoutHarness.boundsIn(root, a.findViewById(tiles[3]));
        assertEquals("equal widths @" + qualifiers, first.width(), second.width(), 1);
        assertEquals(first.width(), third.width(), 1);
        assertEquals(first.width(), fourth.width(), 1);
        assertEquals("one row", first.top, second.top);
        assertEquals("second row", third.top, fourth.top);
        assertTrue("two rows", third.top >= first.bottom);
        assertFalse("no overlap", Rect.intersects(first, second));
        assertFalse("no overlap", Rect.intersects(third, fourth));
        for (int id : tiles) {
            MaterialCardView tile = a.findViewById(id);
            android.view.ViewGroup inner = (android.view.ViewGroup) tile.getChildAt(0);
            View icon = inner.getChildAt(0);
            assertEquals("40dp round icon", Math.round(40 * density), icon.getWidth());
            TextView label = (TextView) inner.getChildAt(1);
            assertFalse(label.isAllCaps());
            assertEquals(0f, label.getLetterSpacing(), 0f);
            assertTrue("label inside its tile", LayoutHarness.boundsIn(root, label).right <= LayoutHarness.boundsIn(root, tile).right);
            assertTrue("a tap target of at least 48dp", tile.getHeight() >= Math.round(48 * density));
        }
    }

    @Test
    public void dashboard_360_light() {
        dashboardAt(MODES[0]);
    }

    @Test
    public void dashboard_360_dark() {
        dashboardAt(MODES[1]);
    }

    @Test
    public void dashboard_393_light() {
        dashboardAt(MODES[2]);
    }

    @Test
    public void dashboard_393_dark() {
        dashboardAt(MODES[3]);
    }

    // ---- Booking & Reservation

    private void bookingScreenAt(String qualifiers) {
        configure(qualifiers);
        BookingAndReservationActivity a = Robolectric.buildActivity(BookingAndReservationActivity.class,
                new Intent(app, BookingAndReservationActivity.class)).setup().get();
        idle();
        ScreenTestSupport.answerBookingLoad(api, api.count("getReservations") - 1, ScreenTestSupport.reservations(), ScreenTestSupport.directBookings(booking()));
        idle();
        layOut(a, app);
        idle();
        layOut(a, app);
        View root = a.getWindow().getDecorView();
        float density = app.getResources().getDisplayMetrics().density;
        int width = app.getResources().getDisplayMetrics().widthPixels;
        String at = " @" + qualifiers;

        View list = a.findViewById(R.id.cardListSection);
        Rect listBounds = LayoutHarness.boundsIn(root, list);
        for (int id : new int[]{R.id.tilSearchTransactions, R.id.tilTransactionStatus, R.id.dropdownTransactionSort, R.id.dropdownTransactionRoomType}) {
            Rect b = LayoutHarness.boundsIn(root, a.findViewById(id));
            assertTrue("filter field " + a.getResources().getResourceEntryName(id) + " inside the list card" + at,
                    b.left >= listBounds.left && b.right <= listBounds.right);
        }
        TextView sort = a.findViewById(R.id.dropdownTransactionSort);
        TextView room = a.findViewById(R.id.dropdownTransactionRoomType);
        TextView status = a.findViewById(R.id.dropdownTransactionStatus);
        assertEquals("single-line sort value" + at, 1, sort.getLineCount());
        assertEquals("single-line room type value" + at, 1, room.getLineCount());
        assertEquals("single-line status value" + at, 1, status.getLineCount());
        assertFalse(LayoutHarness.overlaps(root, (View) sort.getParent().getParent(), (View) room.getParent().getParent()));

        TextView desc = a.findViewById(R.id.screenDescription);
        assertEquals("one line description" + at, 1, desc.getLineCount());
        TextView newBooking = a.findViewById(R.id.btnNewBooking);
        assertFalse(newBooking.isAllCaps());
        assertEquals(0f, newBooking.getLetterSpacing(), 0f);
        assertTrue("compact, not full width" + at, newBooking.getWidth() < width * 0.7f);

        RecyclerView rv = a.findViewById(R.id.rvItemList);
        RecyclerView.ViewHolder holder = rv.findViewHolderForAdapterPosition(0);
        assertNotNull(holder);
        View card = holder.itemView;
        Rect cardBounds = LayoutHarness.boundsIn(root, card);
        TextView id = card.findViewById(R.id.tvBookingId);
        TextView chip = card.findViewById(R.id.tvBookingStatus);
        assertEquals("Booking #569" + at, "Booking #569", id.getText().toString());
        assertEquals("the id is never truncated" + at, 1, id.getLineCount());
        assertEquals("Checked in", chip.getText().toString());
        assertFalse(LayoutHarness.overlaps(root, id, chip));
        TextView price = card.findViewById(R.id.tvBookingPrice);
        assertEquals("₱7,860.00", price.getText().toString());
        assertTrue("the total is right-aligned" + at, LayoutHarness.boundsIn(root, price).right >= cardBounds.right - Math.round(16 * density));
        TextView paid = card.findViewById(R.id.tvBookingPaidLine);
        TextView method = card.findViewById(R.id.tvBookingPaymentMethod);
        assertFalse("paid line and method chip do not overlap" + at, LayoutHarness.overlaps(root, paid, method));
        View details = card.findViewById(R.id.btnViewDetails);
        Rect d = LayoutHarness.boundsIn(root, details);
        assertTrue("the button stays inside its card" + at, d.left >= cardBounds.left && d.right <= cardBounds.right && d.bottom <= cardBounds.bottom);
        assertEquals("button text never wraps" + at, 1, ((TextView) details).getLineCount());
        assertTrue("compact: about 40dp to look at", details.getHeight() <= Math.round(48 * density));
        assertEquals(0f, ((TextView) details).getLetterSpacing(), 0f);
        assertTrue(price.getCurrentTextColor() != 0);
    }

    @Test
    public void bookingAndReservation_360_light() {
        bookingScreenAt(MODES[0]);
    }

    @Test
    public void bookingAndReservation_360_dark() {
        bookingScreenAt(MODES[1]);
    }

    @Test
    public void bookingAndReservation_393_light() {
        bookingScreenAt(MODES[2]);
    }

    @Test
    public void bookingAndReservation_393_dark() {
        bookingScreenAt(MODES[3]);
    }

    // ---- Quick Actions open the right tab

    @Test
    public void quickActions_openTheBookingsAndReservationsTabs() {
        configure(MODES[0]);
        DashboardActivity a = Robolectric.buildActivity(DashboardActivity.class).setup().get();
        idle();
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));
        a.findViewById(R.id.actionBooking).performClick();
        Intent bookings = nextScreen(a);
        assertEquals(new ComponentName(a, BookingAndReservationActivity.class), bookings.getComponent());
        assertEquals(BookingAndReservationActivity.SECTION_BOOKING, bookings.getStringExtra(BookingAndReservationActivity.EXTRA_OPEN_SECTION));
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));
        a.findViewById(R.id.actionReservation).performClick();
        Intent reservations = nextScreen(a);
        assertEquals(BookingAndReservationActivity.SECTION_RESERVATION, reservations.getStringExtra(BookingAndReservationActivity.EXTRA_OPEN_SECTION));
    }

    private static Intent nextScreen(DashboardActivity a) {
        Intent next;
        while ((next = shadowOf(a).getNextStartedActivity()) != null) {
            if (!"android.content.pm.action.REQUEST_PERMISSIONS".equals(next.getAction())) return next;
        }
        return null;
    }
}
