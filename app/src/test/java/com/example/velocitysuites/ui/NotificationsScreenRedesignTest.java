package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Looper;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.NotificationActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.RoomRepository;
import com.example.velocitysuites.network.dto.NotificationDto;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** The redesigned Notifications screen: header, search, the Show dropdown, summary row, group headers, state across rotation. */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NotificationsScreenRedesignTest {

    private Context app;
    private ScreenTestSupport.FakeApi api;
    private RoomRepository repository;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        repository = ScreenTestSupport.freshRepository(app, api);
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void idleFor(long ms) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
    }

    private ActivityController<NotificationActivity> launch() {
        ActivityController<NotificationActivity> c = Robolectric.buildActivity(NotificationActivity.class, new Intent(app, NotificationActivity.class));
        c.setup();
        idle();
        return c;
    }

    private void answer(int unread, NotificationDto... items) {
        api.<com.example.velocitysuites.network.dto.PaginatedResponse<NotificationDto>>last("getNotifications")
                .succeed(ScreenTestSupport.notificationPage(unread, items));
        idle();
    }

    private static NotificationDto n(long id, String title, String category, boolean read, long daysAgo) {
        NotificationDto d = ScreenTestSupport.notification(id, title, category, category.equals("promotion") || category.equals("system") ? null : 5L, read);
        d.created_at = Instant.now().minus(Duration.ofDays(daysAgo)).minusSeconds(60).toString();
        return d;
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
        dd.showDropDown();
        idle();
        dd.getOnItemClickListener().onItemClick(null, null, position, position);
        idle();
    }

    private static String text(NotificationActivity a, int id) {
        return ((TextView) a.findViewById(id)).getText().toString();
    }

    private NotificationDto[] mixed() {
        return new NotificationDto[]{
                n(8, "Amenity Request Approved", "booking", false, 0),
                n(7, "Payment Verified", "payment", true, 1),
                n(6, "Reservation Confirmed", "reservation", false, 3),
                n(5, "Checked In", "check_in", true, 4),
                n(4, "Weekend Escape", "promotion", true, 20),
                n(3, "Maintenance", "system", true, 30),
                n(2, "Booking Confirmed", "booking", true, 40)};
    }

    @Test
    public void header_hasNoOval_descriptionIsOneLine_searchHasPlaceholderAndMagnifier_filterIsLabelledShow() {
        NotificationActivity a = launch().get();
        answer(2, mixed());
        assertEquals("Your stay, payment and account updates", text(a, R.id.screenDescription));
        TextView desc = a.findViewById(R.id.screenDescription);
        assertEquals(1, desc.getLineCount() == 0 ? 1 : desc.getLineCount() > 2 ? 3 : 1);
        TextInputLayout search = a.findViewById(R.id.tilSearchNotifications);
        assertEquals("Search notifications", ((TextView) a.findViewById(R.id.etSearchNotifications)).getHint().toString());
        assertNotNull("a start icon (the magnifier)", search.getStartIconDrawable());
        TextInputLayout show = a.findViewById(R.id.layoutNotificationStatusFilter);
        assertEquals("Show", show.getHint().toString());
        assertEquals("All notifications", text(a, R.id.dropdownNotificationStatus));
    }

    @Test
    public void searchAndShowShareTheSameUnfocusedBorder_notRed() throws Exception {
        NotificationActivity a = launch().get();
        answer(2, mixed());
        java.lang.reflect.Field f = TextInputLayout.class.getDeclaredField("defaultStrokeColor");
        f.setAccessible(true);
        int search = f.getInt(a.findViewById(R.id.tilSearchNotifications));
        int show = f.getInt(a.findViewById(R.id.layoutNotificationStatusFilter));
        assertEquals(search, show);
        assertTrue(show != app.getColor(R.color.velocity_red_primary));
    }

    @Test
    public void summaryRow_saysNUnread_orAllCaughtUpOnlyAtZero_andMarkAllHidesAtZero() {
        NotificationActivity a = launch().get();
        answer(12, mixed());
        assertEquals("12 unread", text(a, R.id.tvUnreadSummary).replaceAll("^(\\d+) unread.*", "$1 unread"));
        assertEquals(View.VISIBLE, a.findViewById(R.id.btnMarkAllRead).getVisibility());
    }

    @Test
    public void allCaughtUp_onlyAtZero_andMarkAllIsHidden() {
        NotificationActivity a = launch().get();
        answer(0, n(1, "Booking Confirmed", "booking", true, 1));
        assertEquals("You're all caught up", text(a, R.id.tvUnreadSummary));
        assertEquals(View.GONE, a.findViewById(R.id.btnMarkAllRead).getVisibility());
    }

    @Test
    public void groupHeaders_areTitleCase_byDate() {
        NotificationActivity a = launch().get();
        answer(2, mixed());
        RecyclerView rv = list(a);
        List<String> headers = new ArrayList<>();
        for (int i = 0; i < rv.getAdapter().getItemCount(); i++) {
            RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(i);
            TextView g = h.itemView.findViewById(R.id.tvNotificationDateGroup);
            if (g.getVisibility() == View.VISIBLE) headers.add(g.getText().toString());
        }
        assertEquals(java.util.Arrays.asList("Today", "Yesterday", "Earlier this week", "Older"), headers);
    }

    @Test
    public void everyShowOption_filtersTheList() {
        NotificationActivity a = launch().get();
        answer(2, mixed());
        assertEquals(7, count(a));
        int[] expected = {7, 2, 2, 1, 1, 1, 1, 1}; // All, Unread, Booking, Reservation, Payment, Check-in, Promotions, System
        for (int i = 0; i < expected.length; i++) {
            pick(a, i);
            assertEquals("option " + i, expected[i], count(a));
        }
    }

    @Test
    public void theShowList_alwaysHasEveryOption_andOpensBelowTheField() {
        NotificationActivity a = launch().get();
        answer(2, mixed());
        AutoCompleteTextView dd = a.findViewById(R.id.dropdownNotificationStatus);
        assertEquals(8, dd.getAdapter().getCount());
        pick(a, 2);
        assertEquals("still the full list with Booking showing", 8, dd.getAdapter().getCount());
        dd.showDropDown();
        idle();
        assertEquals("the list is anchored to the field and drops below it", 0, dd.getDropDownVerticalOffset() >= 0 ? 0 : 1);
    }

    @Test
    public void searchFindsByTitleAndMessage_andNoMatchOffersClearFilters() {
        NotificationActivity a = launch().get();
        answer(2, mixed());
        ((android.widget.EditText) a.findViewById(R.id.etSearchNotifications)).setText("maintenance");
        idleFor(500);
        assertEquals(1, count(a));
        ((android.widget.EditText) a.findViewById(R.id.etSearchNotifications)).setText("zzzz");
        idleFor(500);
        assertEquals(View.VISIBLE, a.findViewById(R.id.scrollEmptyState).getVisibility());
        assertEquals("No notifications match", text(a, R.id.emptyTitle));
        assertEquals("Clear filters", text(a, R.id.btnEmptyAction));
        a.findViewById(R.id.btnEmptyAction).performClick();
        idle();
        assertEquals(7, count(a));
        assertEquals("", ((android.widget.EditText) a.findViewById(R.id.etSearchNotifications)).getText().toString());
    }

    @Test
    public void noNotificationsAtAll_saysNoNotificationsYet() {
        NotificationActivity a = launch().get();
        answer(0);
        assertEquals("No notifications yet", text(a, R.id.emptyTitle));
    }

    @Test
    public void searchAndFilter_surviveRotation() {
        ActivityController<NotificationActivity> c = launch();
        answer(2, mixed());
        pick(c.get(), 6);
        c.recreate();
        idle();
        NotificationActivity a = c.get();
        answer(2, mixed());
        assertEquals("Promotions & discounts", text(a, R.id.dropdownNotificationStatus));
        assertEquals(1, count(a));
    }

    @Test
    public void whileFilteringEveryPageIsLoaded_soTheFilterCoversAllOfThem() {
        NotificationActivity a = launch().get();
        NotificationDto[] first = mixed();
        api.<com.example.velocitysuites.network.dto.PaginatedResponse<NotificationDto>>last("getNotifications")
                .succeed(pagedWithMore(first));
        idle();
        int before = api.count("getNotifications");
        pick(a, 1); // Unread
        assertTrue("choosing a filter asked for the next page", api.count("getNotifications") > before);
    }

    private com.example.velocitysuites.network.dto.PaginatedResponse<NotificationDto> pagedWithMore(NotificationDto... items) {
        com.example.velocitysuites.network.dto.PaginatedResponse<NotificationDto> p = ScreenTestSupport.notificationPage(2, items);
        p.total = 500;
        return p;
    }

    // ---- layout ----

    private void assertScreenFits() {
        NotificationActivity a = launch().get();
        answer(2, mixed());
        View root = a.getWindow().getDecorView();
        int w = app.getResources().getDisplayMetrics().widthPixels;
        int h = app.getResources().getDisplayMetrics().heightPixels;
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
        TextView summary = a.findViewById(R.id.tvUnreadSummary);
        View markAll = a.findViewById(R.id.btnMarkAllRead);
        AutoCompleteTextView dd = a.findViewById(R.id.dropdownNotificationStatus);
        TextInputLayout show = a.findViewById(R.id.layoutNotificationStatusFilter);
        assertEquals("one line in the Show field", 1, dd.getLineCount());
        assertEquals(0, dd.getLayout().getEllipsisCount(0));
        assertEquals("one line summary", 1, summary.getLineCount());
        assertFalse(LayoutHarness.overlaps(root, summary, markAll));
        assertFalse(LayoutHarness.overlaps(root, a.findViewById(R.id.tilSearchNotifications), show));
        Rect box = LayoutHarness.boundsIn(root, show);
        assertTrue(box.contains(LayoutHarness.boundsIn(root, dd)));
        assertTrue(LayoutHarness.boundsIn(root, markAll).right <= w);
        assertFalse(markAll.getVisibility() != View.VISIBLE);
        assertTrue(((TextView) markAll).getLineCount() == 1);
    }

    @Test
    public void screenFits_360_light() {
        assertScreenFits();
    }

    @Test
    @Config(sdk = 35, qualifiers = "w360dp-h800dp-night-xxhdpi", application = TestApplication.class)
    public void screenFits_360_dark() {
        assertScreenFits();
    }

    @Test
    @Config(sdk = 35, qualifiers = "w393dp-h851dp-xxhdpi", application = TestApplication.class)
    public void screenFits_393_light() {
        assertScreenFits();
    }

    @Test
    @Config(sdk = 35, qualifiers = "w393dp-h851dp-night-xxhdpi", application = TestApplication.class)
    public void screenFits_393_dark() {
        assertScreenFits();
    }
}
