package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.DashboardActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.RoomRepository;
import com.example.velocitysuites.network.SessionManager;
import com.example.velocitysuites.network.dto.DirectBookingResponseDto;
import com.example.velocitysuites.network.dto.NotificationDto;
import com.example.velocitysuites.network.dto.PaginatedResponse;
import com.example.velocitysuites.network.dto.ReservationDto;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The Dashboard's Recent Bookings cards (the shared stay card), its Notifications rows and count badge (UNREAD, the bell's
 * number), the compact "Show all" buttons, and the bell popup - on the real screen, at 360dp and 393dp, in light and dark.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DashboardRecentAndNotificationsScreenTest {

    private Context app;
    private ScreenTestSupport.FakeApi api;
    private RoomRepository repository;

    @Before
    public void setUp() throws Exception {
        Field last = com.example.velocitysuites.NavUtils.class.getDeclaredField("lastClickAtMs");
        last.setAccessible(true);
        last.setLong(null, Long.MIN_VALUE / 2);
        app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        repository = ScreenTestSupport.freshRepository(app, api);
        ScreenTestSupport.goOnline(app);
        SessionManager.saveSession(app, "test-token", 1L, "Ana", "Reyes", "", "Ana Reyes", "ana@example.com",
                "09171234567", "Female", "Jan 01, 1990", "");
    }

    // ---- fixtures ----

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private DashboardActivity launch() {
        ActivityController<DashboardActivity> controller = Robolectric.buildActivity(DashboardActivity.class);
        controller.setup();
        idle();
        return controller.get();
    }

    private static LocalDate manilaToday() {
        return LocalDate.now(ZoneId.of("Asia/Manila"));
    }

    private static DirectBookingResponseDto booking(long id, int daysAway, String status) {
        DirectBookingResponseDto d = ScreenTestSupport.directBooking(id, 1800);
        d.check_in = manilaToday().plusDays(daysAway).toString();
        d.check_out = manilaToday().plusDays(daysAway + 1).toString();
        d.booking_status = status;
        d.room_type.name = "Deluxe";
        return d;
    }

    private void answerBookings(DirectBookingResponseDto... direct) {
        ScreenTestSupport.answerBookingLoad(api, 0, new ArrayList<ReservationDto>(), new ArrayList<>(Arrays.asList(direct)));
        idle();
    }

    private void answerNotifications(int unread, NotificationDto... items) {
        api.<PaginatedResponse<NotificationDto>>last("getNotifications").succeed(ScreenTestSupport.notificationPage(unread, items));
        idle();
    }

    private static NotificationDto[] many(int count, boolean read) {
        NotificationDto[] items = new NotificationDto[count];
        for (int i = 0; i < count; i++) {
            items[i] = ScreenTestSupport.notification(count - i, "Amenity Request Approved", "booking", 567L, read);
        }
        return items;
    }

    private static String text(Activity a, int id) {
        return ((TextView) a.findViewById(id)).getText().toString();
    }

    private static ViewGroup list(DashboardActivity a, int id) {
        return a.findViewById(id);
    }

    private static List<View> cardsOf(ViewGroup container, int cardId) {
        List<View> cards = new ArrayList<>();
        for (int i = 0; i < container.getChildCount(); i++) {
            if (container.getChildAt(i).findViewById(cardId) != null || container.getChildAt(i).getId() == cardId) cards.add(container.getChildAt(i));
        }
        return cards;
    }

    private static List<String> chipTexts(View card) {
        com.google.android.material.chip.ChipGroup chips = card.findViewById(R.id.chipsRecentBooking);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < chips.getChildCount(); i++) out.add(((TextView) chips.getChildAt(i)).getText().toString());
        return out;
    }

    private static void layOut(Activity activity, Context app) {
        View root = activity.getWindow().getDecorView();
        int w = app.getResources().getDisplayMetrics().widthPixels;
        int h = app.getResources().getDisplayMetrics().heightPixels;
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
    }

    // ===================================================================
    // A1. Recent Bookings
    // ===================================================================

    @Test
    public void recentBookings_useTheSharedStayCard_idBoldAndPrimary_chipsOnOneRow_roomAndDatesBelow() {
        DashboardActivity activity = launch();
        answerBookings(booking(569, 2, "ACTIVE_BOOKING"));

        View card = cardsOf(list(activity, R.id.bookingListContainer), R.id.cardRecentBooking).get(0);
        TextView ref = card.findViewById(R.id.tvRecentBookingRef);
        assertEquals("Booking #569", ref.getText().toString());
        assertEquals("primary text colour, not red", ContextCompat.getColor(app, R.color.velocity_text_primary), ref.getCurrentTextColor());
        assertTrue("bold", ref.getTypeface() != null && ref.getTypeface().getWeight() >= 700 || ref.getTypeface().getStyle() == Typeface.BOLD);
        assertEquals("Awaiting verification", chipTexts(card).get(0));
        assertEquals("Unpaid", chipTexts(card).get(1));
        assertEquals("Deluxe", ((TextView) card.findViewById(R.id.tvRecentBookingRoom)).getText().toString());
        String dates = ((TextView) card.findViewById(R.id.tvRecentBookingDates)).getText().toString();
        assertTrue("dates then nights: " + dates, dates.contains("→") && dates.endsWith("· 1 night"));
        assertEquals("the description", "Your latest bookings", activity.getString(R.string.recent_bookings_desc));
    }

    @Test
    public void recentBookings_chipsAreNormalCase_neutralStatus_andPaymentChipUsesPaymentStatusColours() {
        DashboardActivity activity = launch();
        DirectBookingResponseDto partly = booking(571, 3, "ACTIVE_BOOKING");
        partly.payments = new ArrayList<>(Collections.singletonList(ScreenTestSupport.payment(8, "900.00", "completed", "cash")));
        DirectBookingResponseDto paid = booking(572, 4, "ACTIVE_BOOKING");
        paid.payments = new ArrayList<>(Collections.singletonList(ScreenTestSupport.payment(9, "1800.00", "completed", "cash")));
        answerBookings(booking(570, 2, "ACTIVE_BOOKING"), partly, paid);

        List<View> cards = cardsOf(list(activity, R.id.bookingListContainer), R.id.cardRecentBooking);
        assertEquals(3, cards.size());
        int[][] expected = {{R.string.payment_chip_unpaid, R.color.status_unpaid_fg}, {R.string.payment_chip_partial, R.color.status_pending_fg},
                {R.string.payment_chip_paid, R.color.status_paid_fg}};
        // newest booking first: the list is ordered by booking date, so find each by its reference
        for (View card : cards) {
            String ref = ((TextView) card.findViewById(R.id.tvRecentBookingRef)).getText().toString();
            int index = ref.endsWith("570") ? 0 : ref.endsWith("571") ? 1 : 2;
            com.google.android.material.chip.ChipGroup chips = card.findViewById(R.id.chipsRecentBooking);
            TextView status = (TextView) chips.getChildAt(0);
            TextView payment = (TextView) chips.getChildAt(chips.getChildCount() - 1);
            assertEquals(app.getString(expected[index][0]), payment.getText().toString());
            assertEquals("the same colour Payment Status uses", app.getColor(expected[index][1]), payment.getCurrentTextColor());
            assertEquals("the booking status is neutral gray", app.getColor(R.color.status_cancelled_fg), status.getCurrentTextColor());
            assertFalse("normal case, not ALL CAPS", status.isAllCaps() || payment.isAllCaps());
        }
    }

    @Test
    public void recentBookings_aCheckedInStayNeverShowsTomorrow_evenWhenItsCheckInDateIsTomorrowOrToday() {
        DashboardActivity activity = launch();
        answerBookings(booking(567, 1, "CHECKED_IN"), booking(568, 0, "CHECKED_IN"), booking(569, 1, "ACTIVE_BOOKING"), booking(573, 0, "ACTIVE_BOOKING"));

        for (View card : cardsOf(list(activity, R.id.bookingListContainer), R.id.cardRecentBooking)) {
            String ref = ((TextView) card.findViewById(R.id.tvRecentBookingRef)).getText().toString();
            List<String> chips = chipTexts(card);
            if (ref.endsWith("567") || ref.endsWith("568")) {
                assertFalse(ref + " is checked in: " + chips, chips.contains("Tomorrow") || chips.contains("Today"));
                assertEquals("Checked in", chips.get(0));
            } else if (ref.endsWith("569")) {
                assertEquals("a confirmed stay checking in tomorrow", "Tomorrow", chips.get(0));
            } else {
                assertEquals("a confirmed stay checking in today", "Today", chips.get(0));
            }
        }
    }

    @Test
    public void recentBookings_collapsedPreviewKeepsThreeItems_andTheHeaderKeepsItsCountAndViewAll() {
        DashboardActivity activity = launch();
        answerBookings(booking(1, 2, "ACTIVE_BOOKING"), booking(2, 3, "ACTIVE_BOOKING"), booking(3, 4, "ACTIVE_BOOKING"),
                booking(4, 5, "ACTIVE_BOOKING"), booking(5, 6, "ACTIVE_BOOKING"));
        assertEquals(3, cardsOf(list(activity, R.id.bookingListContainer), R.id.cardRecentBooking).size());
        assertEquals("5", text(activity, R.id.bookingCountBadge));
        assertNotNull(activity.findViewById(R.id.btnViewAllBookings));
    }

    // ===================================================================
    // A2. Notifications on the Dashboard
    // ===================================================================

    @Test
    public void notificationBadge_isTheUnreadCount_theBellsNumber_notTheNumberLoaded() {
        DashboardActivity activity = launch();
        answerNotifications(0, many(200, true)); // 200 loaded, none unread - the screenshot's "200"
        assertEquals("hidden at 0 unread", View.GONE, activity.findViewById(R.id.notificationCountBadge).getVisibility());
        assertEquals("the bell agrees", View.GONE, activity.findViewById(R.id.headerNotificationBadge).getVisibility());
    }

    @Test
    public void notificationBadge_matchesTheBell_andCapsAbove99() {
        DashboardActivity activity = launch();
        NotificationDto[] items = many(30, false);
        answerNotifications(3, items); // the backend reports 3 unread; the loaded rows are all unread, and the larger wins
        String bell = text(activity, R.id.headerNotificationBadge);
        assertEquals("30", bell);
        assertEquals("the section badge is the bell's number", bell, text(activity, R.id.notificationCountBadge));
        assertEquals(View.VISIBLE, activity.findViewById(R.id.notificationCountBadge).getVisibility());
    }

    @Test
    public void notificationBadge_showsNinetyNinePlus_aboveNinetyNine() {
        DashboardActivity activity = launch();
        answerNotifications(150, many(5, false));
        assertEquals("99+", text(activity, R.id.headerNotificationBadge));
        assertEquals("99+", text(activity, R.id.notificationCountBadge));
    }

    @Test
    public void notificationBadge_followsReads_markAllFromThePopupUpdatesEveryBadge() throws Exception {
        DashboardActivity activity = launch();
        answerNotifications(3, many(3, false));
        assertEquals("3", text(activity, R.id.notificationCountBadge));

        activity.findViewById(R.id.headerNotificationContainer).performClick();
        idle();
        View content = popupOf(activity).getContentView();
        content.findViewById(R.id.btnNotifPopupMarkAll).performClick();
        idle();

        assertEquals(View.GONE, activity.findViewById(R.id.notificationCountBadge).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.headerNotificationBadge).getVisibility());
        assertEquals(0, repository.getUnreadNotificationCount());
    }

    @Test
    public void notificationRows_showIconTitleMessageTimeAndDot_boldWhileUnread() {
        DashboardActivity activity = launch();
        NotificationDto unread = ScreenTestSupport.notification(2, "Amenity Request Approved", "booking", 567L, false);
        unread.message = "1 x Additional Pillow (Room 1-001) (₱50.00) was approved and added to your bill.";
        answerNotifications(1, unread, ScreenTestSupport.notification(1, "Payment Verified", "payment", 588L, true));

        List<View> rows = cardsOf(list(activity, R.id.notificationListContainer), R.id.cardNotificationRow);
        assertEquals(2, rows.size());
        View first = rows.get(0);
        assertEquals("Amenity Request Approved", ((TextView) first.findViewById(R.id.tvNotifTitle)).getText().toString());
        assertTrue(((TextView) first.findViewById(R.id.tvNotifMessage)).getText().toString().startsWith("1 x Additional Pillow"));
        assertEquals("two-line preview", 2, ((TextView) first.findViewById(R.id.tvNotifMessage)).getMaxLines());
        assertEquals("one-line title", 1, ((TextView) first.findViewById(R.id.tvNotifTitle)).getMaxLines());
        assertEquals(View.VISIBLE, first.findViewById(R.id.viewNotifUnreadDot).getVisibility());
        assertEquals(Typeface.BOLD, ((TextView) first.findViewById(R.id.tvNotifTitle)).getTypeface().getStyle());
        assertNotNull(first.findViewById(R.id.frameNotifIcon));
        View read = rows.get(1);
        assertEquals(View.GONE, read.findViewById(R.id.viewNotifUnreadDot).getVisibility());
        assertFalse("the time is normal case", ((TextView) first.findViewById(R.id.tvNotifTime)).isAllCaps());
    }

    @Test
    public void tappingANotificationRow_marksItRead_dropsTheBadge_andOpensItsTarget() {
        DashboardActivity activity = launch();
        answerNotifications(2, ScreenTestSupport.notification(2, "New Promotion", "promotion", null, false),
                ScreenTestSupport.notification(1, "Booking Confirmed", "booking", 5L, false));
        assertEquals("2", text(activity, R.id.notificationCountBadge));

        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));
        cardsOf(list(activity, R.id.notificationListContainer), R.id.cardNotificationRow).get(0).findViewById(R.id.rowNotification).performClick();
        idle();

        assertEquals("the section badge dropped at once", "1", text(activity, R.id.notificationCountBadge));
        assertEquals("and the bell with it", "1", text(activity, R.id.headerNotificationBadge));
        android.app.Dialog sheet = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertNotNull("the tap opens the detail sheet (the full message first)", sheet);
        assertNotNull(sheet.findViewById(R.id.notifDetailSheet));
        assertEquals("New Promotion", ((TextView) sheet.findViewById(R.id.tvNotifDetailTitle)).getText().toString());
        assertEquals("Browse rooms", ((TextView) sheet.findViewById(R.id.btnNotifDetailAction)).getText().toString());
    }

    // ===================================================================
    // A3. Show all buttons
    // ===================================================================

    private static void assertCompactButton(String where, View button, int parentWidth) {
        com.google.android.material.button.MaterialButton b = (com.google.android.material.button.MaterialButton) button;
        float density = b.getResources().getDisplayMetrics().density;
        assertEquals(where + " no letter-spacing", 0f, b.getLetterSpacing(), 0.0001f);
        assertFalse(where + " normal case", b.isAllCaps());
        assertTrue(where + " 48dp touch target (" + b.getMinHeight() / density + ")", b.getMinHeight() >= 48 * density - 1);
        assertEquals(where + " 40dp to look at", 40f, (b.getMinHeight() - b.getInsetTop() - b.getInsetBottom()) / density, 0.6f);
        assertTrue(where + " wrap-content, not full width (" + b.getWidth() + " of " + parentWidth + ")", b.getWidth() < parentWidth * 0.8f);
        assertEquals(where + " one line", 1, b.getLineCount());
    }

    @Test
    public void showAllButtons_areTheSameCompactStyle_inPaymentStatusAndNotifications_andPayNowToo() {
        DashboardActivity activity = launch();
        ReservationDto unpaid = ScreenTestSupport.reservation(630, 3600);
        unpaid.payment_method = "gcash";
        unpaid.check_in = manilaToday().plusDays(2).toString();
        unpaid.check_out = manilaToday().plusDays(3).toString();
        ScreenTestSupport.answerBookingLoad(api, 0, new ArrayList<>(Arrays.asList(unpaid, unpaid2(631), unpaid2(632), unpaid2(633))), new ArrayList<DirectBookingResponseDto>());
        idle();
        answerNotifications(1, many(5, false));
        layOut(activity, app);
        int width = app.getResources().getDisplayMetrics().widthPixels;

        View payments = list(activity, R.id.paymentListContainer).findViewById(R.id.btnPreviewToggle);
        View notifications = list(activity, R.id.notificationListContainer).findViewById(R.id.btnPreviewToggle);
        assertNotNull(payments);
        assertNotNull(notifications);
        assertEquals("Show all (4)", ((TextView) payments).getText().toString());
        assertEquals("Show all (5)", ((TextView) notifications).getText().toString());
        assertCompactButton("payments", payments, width);
        assertCompactButton("notifications", notifications, width);

        View payNow = list(activity, R.id.paymentListContainer).findViewById(R.id.btnQuickPay);
        assertEquals(0f, ((TextView) payNow).getLetterSpacing(), 0.0001f);
        assertTrue("Pay Now keeps a 48dp touch target", ((com.google.android.material.button.MaterialButton) payNow).getMinHeight() >= 48 * app.getResources().getDisplayMetrics().density - 1);
    }

    private static ReservationDto unpaid2(long id) {
        ReservationDto r = ScreenTestSupport.reservation(id, 3600);
        r.payment_method = "gcash";
        r.check_in = manilaToday().plusDays(3).toString();
        r.check_out = manilaToday().plusDays(4).toString();
        return r;
    }

    @Test
    public void showAll_forNotifications_capsTheNumberAbove99_andTogglesToShowLess() {
        DashboardActivity activity = launch();
        answerNotifications(1, many(200, false));
        TextView toggle = list(activity, R.id.notificationListContainer).findViewById(R.id.btnPreviewToggle);
        assertEquals("Show all (99+)", toggle.getText().toString());
        assertEquals("collapsed: three rows", 3, cardsOf(list(activity, R.id.notificationListContainer), R.id.cardNotificationRow).size());
        toggle.performClick();
        idle();
        assertEquals("Show less", ((TextView) list(activity, R.id.notificationListContainer).findViewById(R.id.btnPreviewToggle)).getText().toString());
    }

    // ===================================================================
    // Layout: 360dp and 393dp, light and dark
    // ===================================================================

    private void assertRecentBookingsAndNotificationsFit() {
        DashboardActivity activity = launch();
        DirectBookingResponseDto partly = booking(569, 1, "ACTIVE_BOOKING");
        partly.payments = new ArrayList<>(Collections.singletonList(ScreenTestSupport.payment(8, "900.00", "completed", "cash")));
        answerBookings(partly, booking(567, 0, "CHECKED_IN"), booking(568, 5, "ACTIVE_BOOKING"));
        NotificationDto longOne = ScreenTestSupport.notification(2, "You Can Now Request Additional Amenities For Your Stay", "booking", 567L, false);
        longOne.message = "You are checked in. Open your booking and tap Request Additional Amenities to ask for extras for any of your rooms.";
        answerNotifications(2, longOne, ScreenTestSupport.notification(1, "Amenity Request Approved", "booking", 567L, true));
        layOut(activity, app);
        View root = activity.getWindow().getDecorView();
        int screenWidth = app.getResources().getDisplayMetrics().widthPixels;

        for (View card : cardsOf(list(activity, R.id.bookingListContainer), R.id.cardRecentBooking)) {
            TextView ref = card.findViewById(R.id.tvRecentBookingRef);
            TextView room = card.findViewById(R.id.tvRecentBookingRoom);
            TextView dates = card.findViewById(R.id.tvRecentBookingDates);
            View chips = card.findViewById(R.id.chipsRecentBooking);
            String where = ref.getText() + "\n" + LayoutHarness.dump(app, card);
            assertEquals("the ID is never truncated\n" + where, 1, ref.getLineCount());
            assertEquals("no ellipsis in the ID\n" + where, 0, ref.getLayout().getEllipsisCount(0));
            assertEquals("dates and nights on one line\n" + where, 1, dates.getLineCount());
            assertEquals("room on one line\n" + where, 1, room.getLineCount());
            assertFalse("ID/chips\n" + where, LayoutHarness.overlaps(root, ref, chips));
            assertFalse("chips/room\n" + where, LayoutHarness.overlaps(root, chips, room));
            assertFalse("room/dates\n" + where, LayoutHarness.overlaps(root, room, dates));
            Rect box = LayoutHarness.boundsIn(root, card);
            com.google.android.material.chip.ChipGroup group = (com.google.android.material.chip.ChipGroup) chips;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView chip = (TextView) group.getChildAt(i);
                assertEquals("chip \"" + chip.getText() + "\" on one line\n" + where, 1, chip.getLineCount());
                assertTrue("chip inside the card\n" + where, box.contains(LayoutHarness.boundsIn(root, chip)));
            }
            for (View v : new View[]{ref, room, dates, chips}) {
                assertTrue("inside the screen\n" + where, LayoutHarness.boundsIn(root, v).right <= screenWidth);
            }
        }

        for (View row : cardsOf(list(activity, R.id.notificationListContainer), R.id.cardNotificationRow)) {
            TextView title = row.findViewById(R.id.tvNotifTitle);
            TextView message = row.findViewById(R.id.tvNotifMessage);
            TextView time = row.findViewById(R.id.tvNotifTime);
            View icon = row.findViewById(R.id.frameNotifIcon);
            String where = title.getText() + "\n" + LayoutHarness.dump(app, row);
            assertEquals("single-line title\n" + where, 1, title.getLineCount());
            assertTrue("message at most two lines\n" + where, message.getLineCount() <= 2);
            assertEquals("time on one line\n" + where, 1, time.getLineCount());
            assertFalse("title/time\n" + where, LayoutHarness.overlaps(root, title, time));
            assertFalse("message/time\n" + where, LayoutHarness.overlaps(root, message, time));
            assertFalse("icon/title\n" + where, LayoutHarness.overlaps(root, icon, title));
            Rect box = LayoutHarness.boundsIn(root, row);
            for (View v : new View[]{title, message, time, icon}) {
                assertTrue("inside its card\n" + where, box.contains(LayoutHarness.boundsIn(root, v)));
            }
        }
    }

    @Test
    public void recentBookingsAndNotificationRows_fitAt360dp_light() {
        assertRecentBookingsAndNotificationsFit();
    }

    @Test
    @Config(sdk = 35, qualifiers = "w360dp-h800dp-night-xxhdpi", application = TestApplication.class)
    public void recentBookingsAndNotificationRows_fitAt360dp_dark() {
        assertRecentBookingsAndNotificationsFit();
    }

    @Test
    @Config(sdk = 35, qualifiers = "w393dp-h851dp-xxhdpi", application = TestApplication.class)
    public void recentBookingsAndNotificationRows_fitAt393dp_light() {
        assertRecentBookingsAndNotificationsFit();
    }

    @Test
    @Config(sdk = 35, qualifiers = "w393dp-h851dp-night-xxhdpi", application = TestApplication.class)
    public void recentBookingsAndNotificationRows_fitAt393dp_dark() {
        assertRecentBookingsAndNotificationsFit();
    }

    @Test
    @Config(sdk = 35, qualifiers = "w360dp-h800dp-night-xxhdpi", application = TestApplication.class)
    public void darkMode_paymentChipsAndNotificationTitlesAreReadable() {
        DashboardActivity activity = launch();
        answerBookings(booking(570, 2, "ACTIVE_BOOKING"));
        answerNotifications(1, ScreenTestSupport.notification(1, "Amenity Request Approved", "booking", 567L, false));
        int surface = app.getColor(R.color.velocity_surface_elevated);
        View card = cardsOf(list(activity, R.id.bookingListContainer), R.id.cardRecentBooking).get(0);
        com.google.android.material.chip.ChipGroup chips = card.findViewById(R.id.chipsRecentBooking);
        for (int i = 0; i < chips.getChildCount(); i++) {
            TextView chip = (TextView) chips.getChildAt(i);
            int bg = chip.getBackgroundTintList().getDefaultColor();
            assertTrue("\"" + chip.getText() + "\" readable on its own chip",
                    androidx.core.graphics.ColorUtils.calculateContrast(chip.getCurrentTextColor(), bg) >= 3.0);
        }
        TextView title = cardsOf(list(activity, R.id.notificationListContainer), R.id.cardNotificationRow).get(0).findViewById(R.id.tvNotifTitle);
        assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(title.getCurrentTextColor(), surface) >= 4.5);
    }

    // ===================================================================
    // The bell popup, on the Dashboard
    // ===================================================================

    private static PopupWindow popupOf(Activity activity) throws Exception {
        Field popupField = com.example.velocitysuites.BaseNavigationActivity.class.getDeclaredField("notificationPopup");
        popupField.setAccessible(true);
        Object popup = popupField.get(activity);
        if (popup == null) return null;
        Field windowField = popup.getClass().getDeclaredField("window");
        windowField.setAccessible(true);
        return (PopupWindow) windowField.get(popup);
    }

    private void assertPopupFits() throws Exception {
        DashboardActivity activity = launch();
        NotificationDto longOne = ScreenTestSupport.notification(3, "You Can Now Request Additional Amenities For Your Stay At Velocity", "booking", 567L, false);
        longOne.message = "You are checked in. Open your booking and tap Request Additional Amenities to ask for extras for any of your rooms.";
        NotificationDto[] items = new NotificationDto[9];
        items[0] = longOne;
        for (int i = 1; i < items.length; i++) items[i] = ScreenTestSupport.notification(10 - i, "Amenity Request Approved", "booking", 567L, i > 2);
        activity.findViewById(R.id.headerNotificationContainer).performClick();
        idle();
        answerNotifications(3, items);

        View content = popupOf(activity).getContentView();
        int widthPx = Math.min(app.getResources().getDisplayMetrics().widthPixels - LayoutHarness.dp(app, 24), LayoutHarness.dp(app, 340));
        content.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        content.layout(0, 0, content.getMeasuredWidth(), content.getMeasuredHeight());

        ViewGroup rows = content.findViewById(R.id.layoutNotifPopupRows);
        assertEquals("the latest eight", 8, rows.getChildCount());
        Rect popupBox = new Rect(0, 0, content.getMeasuredWidth(), content.getMeasuredHeight());
        TextView header = content.findViewById(R.id.tvNotifPopupTitle);
        assertEquals("Notifications", header.getText().toString());
        assertEquals("3 unread", ((TextView) content.findViewById(R.id.tvNotifPopupUnread)).getText().toString());
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            TextView title = row.findViewById(R.id.tvNotifTitle);
            TextView message = row.findViewById(R.id.tvNotifMessage);
            TextView time = row.findViewById(R.id.tvNotifTime);
            String where = "row " + i + " " + title.getText() + "\n" + LayoutHarness.dump(app, content);
            assertTrue("a title wraps to at most two lines instead of being cut\n" + where, title.getLineCount() <= 2);
            assertTrue("message at most two lines\n" + where, message.getLineCount() <= 2);
            assertEquals("time on one line\n" + where, 1, time.getLineCount());
            assertFalse("title/time\n" + where, LayoutHarness.overlaps(content, title, time));
            assertFalse("title/message\n" + where, LayoutHarness.overlaps(content, title, message));
            assertTrue("no empty indent: the icon starts within 16dp of the left edge\n" + where,
                    LayoutHarness.boundsIn(content, row.findViewById(R.id.frameNotifIcon)).left <= LayoutHarness.dp(app, 16));
            assertTrue("inside the popup\n" + where, popupBox.contains(LayoutHarness.boundsIn(content, title)));
        }
        assertFalse("header/unread", LayoutHarness.overlaps(content, header, content.findViewById(R.id.tvNotifPopupUnread)));
        assertFalse("mark all/close", LayoutHarness.overlaps(content, content.findViewById(R.id.btnNotifPopupMarkAll), content.findViewById(R.id.btnNotifPopupClose)));
        TextView viewAll = content.findViewById(R.id.btnNotifPopupViewAll);
        assertEquals("View all notifications", viewAll.getText().toString());
        assertEquals("centred", android.view.Gravity.CENTER, viewAll.getGravity());
    }

    @Test
    public void popupFits_360dp_light() throws Exception {
        assertPopupFits();
    }

    @Test
    @Config(sdk = 35, qualifiers = "w360dp-h800dp-night-xxhdpi", application = TestApplication.class)
    public void popupFits_360dp_dark() throws Exception {
        assertPopupFits();
    }

    @Test
    @Config(sdk = 35, qualifiers = "w393dp-h851dp-xxhdpi", application = TestApplication.class)
    public void popupFits_393dp_light() throws Exception {
        assertPopupFits();
    }

    @Test
    @Config(sdk = 35, qualifiers = "w393dp-h851dp-night-xxhdpi", application = TestApplication.class)
    public void popupFits_393dp_dark() throws Exception {
        assertPopupFits();
    }

    @Test
    public void popup_tappingARow_marksItRead_andUpdatesBothBadges() throws Exception {
        DashboardActivity activity = launch();
        activity.findViewById(R.id.headerNotificationContainer).performClick();
        idle();
        answerNotifications(2, ScreenTestSupport.notification(2, "New Promotion", "promotion", null, false),
                ScreenTestSupport.notification(1, "Booking Confirmed", "booking", 5L, false));
        assertEquals("2", text(activity, R.id.notificationCountBadge));

        ViewGroup rows = popupOf(activity).getContentView().findViewById(R.id.layoutNotifPopupRows);
        rows.getChildAt(0).performClick();
        idle();

        assertEquals("1", text(activity, R.id.headerNotificationBadge));
        assertEquals("1", text(activity, R.id.notificationCountBadge));
        assertNull("the popup closed", popupOf(activity) != null && popupOf(activity).isShowing() ? popupOf(activity) : null);
    }
}
