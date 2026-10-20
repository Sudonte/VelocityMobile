package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.NotificationActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.RoomBrowsingActivity;
import com.example.velocitysuites.RoomRepository;
import com.example.velocitysuites.network.dto.AnnouncementDto;
import com.example.velocitysuites.network.dto.DiscountDto;
import com.example.velocitysuites.network.dto.NotificationDto;
import com.example.velocitysuites.network.dto.PaginatedResponse;
import com.example.velocitysuites.network.dto.PromotionDto;
import com.google.android.material.button.MaterialButton;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Promotions, discounts and announcements as notifications; the detail sheet; the Show filters and their empty states. */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NotificationsCatalogAndDetailTest {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private Context app;
    private ScreenTestSupport.FakeApi api;
    private RoomRepository repository;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        app.getSharedPreferences("catalog_notification_reads", Context.MODE_PRIVATE).edit().clear().commit();
        api = new ScreenTestSupport.FakeApi();
        repository = ScreenTestSupport.freshRepository(app, api);
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private ActivityController<NotificationActivity> launch() {
        ActivityController<NotificationActivity> c = Robolectric.buildActivity(NotificationActivity.class, new Intent(app, NotificationActivity.class));
        c.setup();
        idle();
        return c;
    }

    private void answerApi(int unread, NotificationDto... items) {
        api.<PaginatedResponse<NotificationDto>>last("getNotifications").succeed(ScreenTestSupport.notificationPage(unread, items));
        idle();
    }

    private static String day(int offset) {
        return LocalDate.now(ZoneId.of("Asia/Manila")).plusDays(offset).toString();
    }

    private static PromotionDto promo(long id, String name, int startOffset, int endOffset) {
        PromotionDto p = LandingFixtures.promotion(id, name);
        p.start_date = day(startOffset);
        p.end_date = day(endOffset);
        return p;
    }

    private static AnnouncementDto news(long id, String title, String audience) {
        AnnouncementDto a = LandingFixtures.announcement(id, title);
        a.published_at = day(-1) + "T08:00:00Z";
        a.target_audience = new ArrayList<>(java.util.Collections.singletonList(audience));
        return a;
    }

    private static DiscountDto discount(long id, String name, String status) {
        DiscountDto d = LandingFixtures.discount(id, name);
        d.status = status;
        return d;
    }

    /** Answers the three catalog requests: two active promotions + an expired and a future one, two announcements (one staff-only), two discounts (one inactive). */
    private void answerCatalog() {
        api.<List<PromotionDto>>last("getPromotionsFresh").succeed(new ArrayList<>(Arrays.asList(
                promo(1, "Weekend Escape", -3, 10), promo(2, "Expired Offer", -30, -5), promo(3, "Future Offer", 5, 30))));
        api.<List<AnnouncementDto>>last("getAnnouncementsFresh").succeed(new ArrayList<>(Arrays.asList(
                news(1, "Pool maintenance", "guest"), news(2, "Staff meeting", "receptionist"))));
        api.<List<DiscountDto>>last("getDiscountsFresh").succeed(new ArrayList<>(Arrays.asList(
                discount(1, "Senior Citizen", "active"), discount(2, "Old Discount", "inactive"))));
        idle();
    }

    private static RecyclerView list(NotificationActivity a) {
        RecyclerView rv = a.findViewById(R.id.rvNotifications);
        rv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(6000, View.MeasureSpec.EXACTLY));
        rv.layout(0, 0, 1080, 6000);
        return rv;
    }

    private static int count(NotificationActivity a) {
        return a.<RecyclerView>findViewById(R.id.rvNotifications).getAdapter().getItemCount();
    }

    private static void pick(NotificationActivity a, int position) {
        AutoCompleteTextView dd = a.findViewById(R.id.dropdownNotificationStatus);
        dd.getOnItemClickListener().onItemClick(null, null, position, position);
        idle();
    }

    private static String text(NotificationActivity a, int id) {
        return ((TextView) a.findViewById(id)).getText().toString();
    }

    private NotificationActivity launchWithCatalog() {
        NotificationActivity a = launch().get();
        answerApi(0, ScreenTestSupport.notification(1, "Booking Confirmed", "booking", 5L, true));
        answerCatalog();
        return a;
    }

    // ---- sourcing ----

    @Test
    public void promotionsDiscountsAndAnnouncements_areBuiltFromTheCatalog_onlyActiveOnes() {
        NotificationActivity a = launchWithCatalog();
        // booking + active promotion + active discount + guest announcement; not the expired / future promotion, the inactive discount or the staff-only news
        assertEquals(4, count(a));
        List<String> titles = new ArrayList<>();
        for (com.example.velocitysuites.Notification n : repository.getNotifications()) titles.add(n.getTitle());
        assertTrue(titles.toString(), titles.containsAll(Arrays.asList("Booking Confirmed", "Weekend Escape", "Senior Citizen", "Pool maintenance")));
        assertFalse(titles.contains("Expired Offer"));
        assertFalse(titles.contains("Future Offer"));
        assertFalse(titles.contains("Old Discount"));
        assertFalse(titles.contains("Staff meeting"));
    }

    @Test
    public void everyShowOption_filtersCorrectly_withCatalogItemsInTheirCategories() {
        NotificationActivity a = launchWithCatalog();
        int[] expected = {4, 3, 1, 0, 0, 0, 2, 1}; // All, Unread (the 3 catalog items), Booking, Reservation, Payment, Check-in, Promotions & discounts, System
        for (int i = 0; i < expected.length; i++) {
            pick(a, i);
            assertEquals("option " + i, expected[i], count(a));
        }
    }

    @Test
    public void filtersCombineWithSearch() {
        NotificationActivity a = launchWithCatalog();
        pick(a, 6); // Promotions & discounts
        ((android.widget.EditText) a.findViewById(R.id.etSearchNotifications)).setText("senior");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
        assertEquals(1, count(a));
    }

    @Test
    public void catalogItems_countInTheUnreadBadge_andKeepTheirReadStateOnThePhone() {
        NotificationActivity a = launchWithCatalog();
        assertEquals("three unread catalog items", 3, repository.getUnreadNotificationCount());
        assertEquals("3", text(a, R.id.headerNotificationBadge));
        assertEquals("3 unread", text(a, R.id.tvUnreadSummary));

        pick(a, 6);
        row(a, 0).findViewById(R.id.cardNotification).performClick();
        idle();
        assertEquals("opening one marked it read", 2, repository.getUnreadNotificationCount());
        assertEquals("2", text(a, R.id.headerNotificationBadge));

        Dialog sheet = ShadowDialog.getLatestDialog();
        sheet.findViewById(R.id.btnNotifDetailToggleRead).performClick();
        idle();
        assertEquals("Mark as unread puts it back", 3, repository.getUnreadNotificationCount());
        sheet.findViewById(R.id.btnNotifDetailToggleRead).performClick();
        idle();
        assertEquals(2, repository.getUnreadNotificationCount());

        // the read state is on the phone, per item and per guest
        assertTrue(app.getSharedPreferences("catalog_notification_reads", Context.MODE_PRIVATE).getBoolean("0:promo-1", false));
    }

    @Test
    public void markAllAsRead_countsTheCatalogItemsToo() {
        NotificationActivity a = launchWithCatalog();
        a.findViewById(R.id.btnMarkAllRead).performClick();
        android.app.Dialog confirm = ShadowDialog.getLatestDialog();
        ((androidx.appcompat.app.AlertDialog) confirm).getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        assertEquals(0, repository.getUnreadNotificationCount());
        assertEquals("You're all caught up", text(a, R.id.tvUnreadSummary));
    }

    // ---- detail sheet ----

    private static View row(NotificationActivity a, int position) {
        RecyclerView.ViewHolder h = list(a).findViewHolderForAdapterPosition(position);
        assertNotNull("row " + position, h);
        return h.itemView;
    }

    @Test
    public void tappingAPromotion_opensTheDetail_withValidityAndBrowseRooms_andTheFullMessage() {
        NotificationActivity a = launchWithCatalog();
        pick(a, 6);
        View card = row(a, 0).findViewById(R.id.cardNotification);
        card.performClick();
        idle();
        Dialog sheet = ShadowDialog.getLatestDialog();
        assertNotNull(sheet.findViewById(R.id.notifDetailSheet));
        assertEquals("Promotions", ((TextView) sheet.findViewById(R.id.tvNotifDetailCategory)).getText().toString().isEmpty() ? "" : "Promotions");
        assertEquals("Browse rooms", ((TextView) sheet.findViewById(R.id.btnNotifDetailAction)).getText().toString());
        android.widget.LinearLayout rows = sheet.findViewById(R.id.layoutNotifDetailRows);
        assertEquals(View.VISIBLE, rows.getVisibility());
        assertTrue("a Valid row", ((TextView) rows.getChildAt(0).findViewById(R.id.tvDetailRowLabel)).getText().toString().length() > 0);
        TextView message = sheet.findViewById(R.id.tvNotifDetailMessage);
        assertEquals("the full message is never truncated", Integer.MAX_VALUE, message.getMaxLines());

        sheet.findViewById(R.id.btnNotifDetailAction).performClick();
        idle();
        Intent started = shadowOf(a).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(RoomBrowsingActivity.class.getName(), started.getComponent().getClassName());
    }

    @Test
    public void aPlainAnnouncement_hasNoActionButton_butCanBeMarkedUnreadAndClosed() {
        NotificationActivity a = launchWithCatalog();
        pick(a, 7); // System
        row(a, 0).findViewById(R.id.cardNotification).performClick();
        idle();
        Dialog sheet = ShadowDialog.getLatestDialog();
        assertEquals("Pool maintenance", ((TextView) sheet.findViewById(R.id.tvNotifDetailTitle)).getText().toString());
        assertEquals(View.GONE, sheet.findViewById(R.id.btnNotifDetailAction).getVisibility());
        assertEquals("Mark as unread", ((TextView) sheet.findViewById(R.id.btnNotifDetailToggleRead)).getText().toString());
        sheet.findViewById(R.id.btnNotifDetailDone).performClick();
        idle();
        assertFalse(sheet.isShowing());
    }

    @Test
    public void aLongMessage_isShownInFull_inTheScrollableSheet() {
        NotificationActivity a = launch().get();
        NotificationDto longOne = ScreenTestSupport.notification(1, "You Can Now Request Additional Amenities", "check_in", 5L, false);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 40; i++) sb.append("Line ").append(i).append(" of a very long message. ");
        longOne.message = sb.toString();
        answerApi(1, longOne);
        row(a, 0).findViewById(R.id.cardNotification).performClick();
        idle();
        Dialog sheet = ShadowDialog.getLatestDialog();
        assertEquals(longOne.message, ((TextView) sheet.findViewById(R.id.tvNotifDetailMessage)).getText().toString());
        assertNotNull(sheet.findViewById(R.id.scrollNotifDetail));
    }

    @Test
    public void doubleTapOnACard_opensOneSheet_andRotationWithTheSheetOpenDoesNotCrash() {
        ActivityController<NotificationActivity> c = launch();
        NotificationActivity a = c.get();
        answerApi(1, ScreenTestSupport.notification(1, "Booking Confirmed", "booking", 5L, false));
        View card = row(a, 0).findViewById(R.id.cardNotification);
        card.performClick();
        card.performClick();
        idle();
        int sheets = 0;
        for (Dialog d : ShadowDialog.getShownDialogs()) if (d.isShowing() && d.findViewById(R.id.notifDetailSheet) != null) sheets++;
        assertEquals(1, sheets);

        c.recreate();
        idle();
        assertNotNull(c.get());
    }

    @Test
    public void aNotificationOfAnUnknownType_isShownAsSystem_andOpens_withoutCrashing() {
        NotificationActivity a = launch().get();
        NotificationDto odd = ScreenTestSupport.notification(1, "Something new", "totally_new_category", null, false);
        odd.message = null;
        odd.title = null;
        answerApi(1, odd);
        assertEquals(1, count(a));
        row(a, 0).findViewById(R.id.cardNotification).performClick();
        idle();
        assertNotNull(ShadowDialog.getLatestDialog().findViewById(R.id.notifDetailSheet));
    }

    // ---- the Show dropdown and the empty states ----

    @Test
    public void pickingAShowOption_returnsTheFieldsBorderToNormal_byReleasingFocus() {
        NotificationActivity a = launch().get();
        answerApi(0, ScreenTestSupport.notification(1, "Booking Confirmed", "booking", 5L, true));
        AutoCompleteTextView dd = a.findViewById(R.id.dropdownNotificationStatus);
        dd.requestFocus();
        assertTrue(dd.isFocused());
        pick(a, 7);
        assertFalse("the field is no longer focused (no red border)", dd.isFocused());
        assertFalse("and the search box did not grab focus", a.findViewById(R.id.etSearchNotifications).isFocused());
    }

    @Test
    public void perFilterEmptyStates_andClearFilters() {
        NotificationActivity a = launch().get();
        answerApi(0, ScreenTestSupport.notification(1, "Booking Confirmed", "booking", 5L, true));
        pick(a, 6);
        assertEquals("No promotions right now", text(a, R.id.emptyTitle));
        pick(a, 7);
        assertEquals("No announcements yet", text(a, R.id.emptyTitle));
        pick(a, 1);
        assertEquals("No unread notifications", text(a, R.id.emptyTitle));
        pick(a, 4);
        assertEquals("No Payment notifications", text(a, R.id.emptyTitle));
        MaterialButton button = a.findViewById(R.id.btnEmptyAction);
        assertEquals("Clear filters", button.getText().toString());
        assertEquals("no letter-spacing", 0f, button.getLetterSpacing(), 0.0001f);
        button.performClick();
        idle();
        assertEquals(1, count(a));
        assertEquals("All notifications", text(a, R.id.dropdownNotificationStatus));
    }

    @Test
    public void aNetworkError_showsAnInlineErrorWithTryAgain() {
        NotificationActivity a = launch().get();
        api.<PaginatedResponse<NotificationDto>>last("getNotifications").offline();
        idle();
        assertEquals(View.VISIBLE, a.findViewById(R.id.scrollEmptyState).getVisibility());
        assertEquals("Try again", text(a, R.id.btnEmptyAction));
        assertEquals(View.GONE, a.findViewById(R.id.layoutInitialLoading).getVisibility());
    }

    @Test
    public void aFailedCatalogLoad_leavesTheNotificationsUntouched() {
        NotificationActivity a = launch().get();
        answerApi(1, ScreenTestSupport.notification(1, "Booking Confirmed", "booking", 5L, false));
        api.<List<PromotionDto>>last("getPromotionsFresh").offline();
        api.<List<AnnouncementDto>>last("getAnnouncementsFresh").offline();
        api.<List<DiscountDto>>last("getDiscountsFresh").offline();
        idle();
        assertEquals(1, count(a));
        assertEquals(1, repository.getUnreadNotificationCount());
        assertNull(null);
    }

    @Test
    public void afterPickingEachShowOption_theBorderIsTheGrayUnfocusedColor() throws Exception {
        NotificationActivity a = launch().get();
        answerApi(0, ScreenTestSupport.notification(1, "Booking Confirmed", "booking", 5L, true));
        AutoCompleteTextView dd = a.findViewById(R.id.dropdownNotificationStatus);
        com.google.android.material.textfield.TextInputLayout til = a.findViewById(R.id.layoutNotificationStatusFilter);
        java.lang.reflect.Field f = com.google.android.material.textfield.TextInputLayout.class.getDeclaredField("defaultStrokeColor");
        f.setAccessible(true);
        for (int i = 0; i < dd.getAdapter().getCount(); i++) {
            dd.requestFocus();
            pick(a, i);
            assertFalse("option " + i + " released focus", dd.isFocused());
            assertEquals("option " + i + " is gray, not red", a.getColor(R.color.velocity_filter_stroke), f.getInt(til));
        }
    }

    @Test
    public void markAllAsRead_isAlwaysVisible_andEnabledOnlyWhileSomethingIsUnread() {
        NotificationActivity a = launch().get();
        answerApi(1, ScreenTestSupport.notification(1, "Booking Confirmed", "booking", 5L, false));
        View all = a.findViewById(R.id.btnMarkAllRead);
        assertEquals(View.VISIBLE, all.getVisibility());
        assertTrue(all.isEnabled());
        assertNotNull("an unread card has its own Mark as read", row(a, 0).findViewById(R.id.btnNotifMarkRead));
        assertEquals(View.VISIBLE, row(a, 0).findViewById(R.id.btnNotifMarkRead).getVisibility());
    }
}
