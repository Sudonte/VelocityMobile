package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.DashboardActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.network.SessionManager;
import com.example.velocitysuites.network.dto.ReservationDto;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** The Dashboard's section titles (one bold shared style, the renamed "Pending & Confirmed Stays") and the one gap between sections. */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DashboardSectionLayoutTest {

    private Context app;
    private ScreenTestSupport.FakeApi api;

    @Before
    public void setUp() throws Exception {
        java.lang.reflect.Field last = com.example.velocitysuites.NavUtils.class.getDeclaredField("lastClickAtMs");
        last.setAccessible(true);
        last.setLong(null, Long.MIN_VALUE / 2);
        app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
        ScreenTestSupport.goOnline(app);
        SessionManager.saveSession(app, "test-token", 1L, "Ana", "Reyes", "", "Ana Reyes", "ana@example.com",
                "09171234567", "Female", "Jan 01, 1990", "");
    }

    private DashboardActivity launch() {
        ActivityController<DashboardActivity> c = Robolectric.buildActivity(DashboardActivity.class);
        c.setup();
        shadowOf(Looper.getMainLooper()).idle();
        return c.get();
    }

    private static ReservationDto stay(long id, int daysAway) {
        ReservationDto r = ScreenTestSupport.reservation(id, 1800);
        r.check_in = LocalDate.now(ZoneId.of("Asia/Manila")).plusDays(daysAway).toString();
        r.check_out = LocalDate.now(ZoneId.of("Asia/Manila")).plusDays(daysAway + 1).toString();
        return r;
    }

    private void answer(ReservationDto... reservations) {
        ScreenTestSupport.answerBookingLoad(api, 0, new ArrayList<>(java.util.Arrays.asList(reservations)), new ArrayList<>());
        shadowOf(Looper.getMainLooper()).idle();
    }

    private void many() {
        ReservationDto[] stays = new ReservationDto[8];
        for (int i = 0; i < stays.length; i++) stays[i] = stay(700 + i, 1 + i);
        answer(stays);
    }

    private void layOut(DashboardActivity a) {
        View root = a.getWindow().getDecorView();
        int w = app.getResources().getDisplayMetrics().widthPixels;
        int h = app.getResources().getDisplayMetrics().heightPixels;
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
    }

    // ---- titles ----

    private static List<TextView> textViewsWith(View root, String text) {
        List<TextView> out = new ArrayList<>();
        collect(root, text, out);
        return out;
    }

    private static void collect(View v, String text, List<TextView> out) {
        if (v instanceof TextView && text.contentEquals(((TextView) v).getText())) out.add((TextView) v);
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) collect(((ViewGroup) v).getChildAt(i), text, out);
    }

    private static final int[] TITLES = {R.string.quick_actions, R.string.booking_reservation_summary_title, R.string.next_stay_section_title,
            R.string.stay_calendar_title, R.string.more_stays_title, R.string.recent_upcoming_title, R.string.recent_bookings_title, R.string.notifications,
            R.string.payment_status, R.string.account_summary, R.string.support_need_help_title};

    @Test
    public void everySectionTitle_usesTheOneSharedBoldStyle() {
        DashboardActivity activity = launch();
        answer();
        layOut(activity);
        float size = -1;
        for (int res : TITLES) {
            String text = app.getString(res);
            List<TextView> found = textViewsWith(activity.findViewById(android.R.id.content), text);
            assertFalse("a title \"" + text + "\" on the Dashboard", found.isEmpty());
            TextView title = found.get(0);
            assertTrue("\"" + text + "\" is bold", title.getTypeface().isBold() || title.getTypeface().getWeight() >= 700);
            assertEquals("\"" + text + "\" no letter-spacing", 0f, title.getLetterSpacing(), 0.0001f);
            assertFalse("\"" + text + "\" not ALL CAPS", title.isAllCaps());
            assertEquals("\"" + text + "\" one line", 1, title.getMaxLines());
            if (title.isShown()) assertEquals("\"" + text + "\" fits on one line", 1, title.getLineCount());
            if (size < 0) size = title.getTextSize();
            assertEquals("\"" + text + "\" same size as the others", size, title.getTextSize(), 0.5f);
        }
    }

    @Test
    public void theRenamedSection_hasItsNewTitleDescriptionAndAccessibilityText_andNoOldNameRemains() {
        DashboardActivity activity = launch();
        answer();
        assertEquals("Pending & Confirmed Stays", app.getString(R.string.recent_upcoming_title));
        assertEquals("Stays waiting for approval or confirmed", app.getString(R.string.recent_upcoming_desc));
        View toggle = activity.findViewById(R.id.recentUpcomingToggle);
        assertTrue(toggle.getContentDescription().toString(), toggle.getContentDescription().toString().matches("(Collapse|Expand) Pending & Confirmed Stays"));
        for (TextView t : allText(activity.findViewById(android.R.id.content))) {
            assertFalse("old name still showing: " + t.getText(), t.getText().toString().contains("Recent Upcoming Stay"));
            CharSequence cd = t.getContentDescription();
            assertFalse(cd != null && cd.toString().contains("Recent Upcoming Stay"));
        }
        assertFalse(toggle.getContentDescription().toString().contains("Recent Upcoming"));
    }

    private static List<TextView> allText(View v) {
        List<TextView> out = new ArrayList<>();
        if (v instanceof TextView) out.add((TextView) v);
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) out.addAll(allText(((ViewGroup) v).getChildAt(i)));
        return out;
    }

    // ---- gaps ----

    /** One dashboard section: its first and last view (a section is one or more siblings in the scrolling column). */
    private static final class Section {
        final String name;
        final View[] views;

        Section(String name, View... views) {
            this.name = name;
            this.views = views;
        }

        boolean visible() {
            return views[0].getVisibility() != View.GONE;
        }

        View first() {
            return views[0];
        }

        /** The last VISIBLE view of the section. */
        View last() {
            for (int i = views.length - 1; i >= 0; i--) if (views[i].getVisibility() != View.GONE) return views[i];
            return views[0];
        }
    }

    private List<Section> sections(DashboardActivity a) {
        List<Section> s = new ArrayList<>();
        s.add(new Section("hero", a.findViewById(R.id.welcomeCard)));
        s.add(new Section("clock", a.findViewById(R.id.dateTimeCard)));
        s.add(new Section("Quick Actions", a.findViewById(R.id.quickActionsCard)));
        s.add(new Section("Booking & Reservation Summary", a.findViewById(R.id.bookingReservationSummaryCard)));
        s.add(new Section("Your Next Stay", a.findViewById(R.id.nextStaySectionCard)));
        s.add(new Section("Stay Calendar", a.findViewById(R.id.stayCalendar)));
        s.add(new Section("Other Recent & Upcoming Stays", a.findViewById(R.id.moreStaysSection)));
        s.add(new Section("Pending & Confirmed Stays", a.findViewById(R.id.recentUpcomingCard)));
        s.add(new Section("Recent Bookings", a.findViewById(R.id.bookingDetailsCard)));
        s.add(new Section("Notifications", a.findViewById(R.id.notificationsCard)));
        s.add(new Section("Payment Status", a.findViewById(R.id.paymentStatusCard)));
        s.add(new Section("Account Summary", a.findViewById(R.id.accountSummaryCard)));
        s.add(new Section("Need help?", a.findViewById(R.id.supportCard)));
        return s;
    }

    private void assertEveryGapIsTheSharedOne(DashboardActivity activity, String state) {
        layOut(activity);
        int gap = app.getResources().getDimensionPixelSize(R.dimen.dashboard_section_gap);
        assertEquals("the shared gap is 24dp", 24 * app.getResources().getDisplayMetrics().density, gap, 1f);
        Section previous = null;
        int checked = 0;
        for (Section section : sections(activity)) {
            assertNotNull(section.name, section.first());
            if (!section.visible()) continue;
            if (previous != null) {
                assertEquals(state + ": " + previous.name + " -> " + section.name, gap, section.first().getTop() - previous.last().getBottom(), 1f);
                assertTrue(state + ": never touching", section.first().getTop() - previous.last().getBottom() > 0);
                checked++;
            }
            previous = section;
        }
        assertTrue(state + ": several neighbours were checked (" + checked + ")", checked >= 8);
    }

    @Test
    public void gapBetweenEverySection_isTheSharedValue_whenEmpty() {
        DashboardActivity activity = launch();
        answer();
        assertEveryGapIsTheSharedOne(activity, "empty");
        assertEquals("Other Stays never disappears: with no data it says so", View.VISIBLE, activity.findViewById(R.id.moreStaysSection).getVisibility());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.moreStaysEmpty).getVisibility());
        assertEquals("No recent or upcoming stays", ((android.widget.TextView) activity.findViewById(R.id.moreStaysEmpty)).getText().toString());
    }

    @Test
    public void gapBetweenEverySection_isTheSharedValue_withDataAndCollapsedPreview() {
        DashboardActivity activity = launch();
        many();
        assertEquals(View.VISIBLE, activity.findViewById(R.id.moreStaysSection).getVisibility());
        assertEveryGapIsTheSharedOne(activity, "collapsed preview");
    }

    @Test
    public void gapBetweenEverySection_isTheSharedValue_withEveryListExpanded() {
        DashboardActivity activity = launch();
        many();
        for (int id : new int[]{R.id.moreStaysToggle, R.id.recentUpcomingToggle, R.id.bookingsToggle, R.id.notificationsToggle, R.id.paymentsToggle}) {
            activity.findViewById(id).performClick();
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        }
        assertEveryGapIsTheSharedOne(activity, "expanded");
    }

    @Test
    public void gapBetweenEverySection_isTheSharedValue_whenYourNextStayIsFullyCollapsed() {
        DashboardActivity activity = launch();
        many();
        activity.findViewById(R.id.nextStayToggle).performClick();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertEveryGapIsTheSharedOne(activity, "next stay collapsed");
    }

    @Test
    public void otherStays_andPendingConfirmed_doNotTouch() {
        DashboardActivity activity = launch();
        many();
        layOut(activity);
        View other = activity.findViewById(R.id.moreStaysSection);
        View pending = activity.findViewById(R.id.recentUpcomingCard);
        assertEquals(View.VISIBLE, other.getVisibility());
        int gap = pending.getTop() - other.getBottom();
        assertTrue("a real gap between the last Other Stays card and the Pending & Confirmed Stays card (" + gap + "px)",
                gap >= 24 * app.getResources().getDisplayMetrics().density - 1);
    }

    @Test
    @Config(sdk = 35, qualifiers = "w393dp-h851dp-night-xxhdpi", application = TestApplication.class)
    public void gaps_393dp_dark() {
        DashboardActivity activity = launch();
        many();
        assertEveryGapIsTheSharedOne(activity, "393 dark");
    }

    // ---- order, hero and clock ----

    @Test
    public void sectionsAreInTheRequestedOrder_fromTopToBottom() {
        DashboardActivity activity = launch();
        answer();
        layOut(activity);
        int[] ids = {R.id.welcomeCard, R.id.dateTimeCard, R.id.quickActionsCard, R.id.bookingReservationSummaryCard, R.id.nextStaySectionCard,
                R.id.stayCalendar, R.id.moreStaysSection, R.id.recentUpcomingCard, R.id.bookingDetailsCard, R.id.notificationsCard,
                R.id.paymentStatusCard, R.id.accountSummaryCard, R.id.supportCard};
        ViewGroup column = (ViewGroup) activity.findViewById(R.id.welcomeCard).getParent();
        int last = -1;
        for (int id : ids) {
            View v = activity.findViewById(id);
            assertNotNull(id + "", v);
            assertSame("one shared column", column, v.getParent());
            int at = column.indexOfChild(v);
            assertTrue("section " + app.getResources().getResourceEntryName(id) + " is below the previous one", at > last);
            last = at;
        }
    }

    @Test
    public void hero_isAGreetingLine_aBoldNameLine_andOneShortSentence_leftAligned() {
        DashboardActivity a = launch();
        answer();
        layOut(a);
        TextView greeting = a.findViewById(R.id.welcomeText);
        TextView name = a.findViewById(R.id.userNameText);
        TextView line = a.findViewById(R.id.welcomeSubtitle);
        assertTrue(greeting.getText().toString(), greeting.getText().toString().matches("Good (morning|afternoon|evening),"));
        assertEquals("Here\u2019s a quick look at your stays and payments.", line.getText().toString());
        assertEquals(1, name.getMaxLines());
        assertEquals(android.text.TextUtils.TruncateAt.END, name.getEllipsize());
        assertTrue(name.getTypeface().getWeight() >= 700 || name.getTypeface().isBold());
        assertTrue("greeting above name above line", greeting.getBottom() <= name.getTop() + 1 && name.getBottom() <= line.getTop() + 1);
        assertEquals("same left edge", greeting.getLeft(), name.getLeft());
        assertEquals("same left edge", name.getLeft(), line.getLeft());
        assertFalse(greeting.isAllCaps() || name.isAllCaps() || line.isAllCaps());
    }

    @Test
    public void dateAndTime_aTickDoesNotRelayoutTheDashboard() {
        DashboardActivity a = launch();
        answer();
        layOut(a);
        TextView date = a.findViewById(R.id.dateText);
        TextView time = a.findViewById(R.id.timeText);
        assertTrue(date.getText().toString(), date.getText().toString().matches("[A-Z][a-z]+, [A-Z][a-z]+ \\d{1,2}, \\d{4}"));
        assertEquals("the date sits above the time, both beside the round icon", date.getLeft(), time.getLeft());
        assertTrue(date.getBottom() <= time.getTop() + 1);
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, time.getLayoutParams().width);
        assertEquals("a tick only changes the clock text", 1, time.getMaxLines());
        View card = a.findViewById(R.id.dateTimeCard);
        assertTrue("tapping opens Upcoming Transactions, so the chevron stays", card.hasOnClickListeners());
        assertEquals(View.VISIBLE, a.findViewById(R.id.dateTimeChevron).getVisibility());
        View icon = a.findViewById(R.id.clockIcon);
        assertEquals(icon.getWidth(), icon.getHeight());
        View block = (View) time.getParent();
        int blockMid = block.getTop() + block.getHeight() / 2;
        int iconMid = icon.getTop() + icon.getHeight() / 2;
        assertEquals("icon and text are centred on the same line", blockMid, iconMid, 3);
        assertTrue(block.getHeight() > 0);
    }

    @Test
    public void clock_stopsWhenTheScreenIsNotVisible_andRestartsOnResume() throws Exception {
        ActivityController<DashboardActivity> c = Robolectric.buildActivity(DashboardActivity.class).setup();
        DashboardActivity a = c.get();
        java.lang.reflect.Field f = DashboardActivity.class.getDeclaredField("clockHandler");
        f.setAccessible(true);
        android.os.Handler handler = (android.os.Handler) f.get(a);
        java.lang.reflect.Field r = DashboardActivity.class.getDeclaredField("clockRunnable");
        r.setAccessible(true);
        Runnable tick = (Runnable) r.get(a);
        assertTrue("ticking while resumed", handler.hasCallbacks(tick));
        c.pause();
        assertFalse("no clock callback left once paused", handler.hasCallbacks(tick));
        c.resume();
        assertTrue(handler.hasCallbacks(tick));
        c.pause().stop().destroy();
        assertFalse("nothing leaks after destroy", handler.hasCallbacks(tick));
    }
}
