package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.LandingActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.network.dto.AmenityDto;
import com.example.velocitysuites.network.dto.AnnouncementDto;
import com.example.velocitysuites.network.dto.DiscountDto;
import com.example.velocitysuites.network.dto.PromotionDto;
import com.example.velocitysuites.network.dto.RoomsResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.time.Duration;
import java.util.List;

/**
 * The landing page, as the guest uses it: what it asks the server for, what pulling down does, what a failed
 * refresh leaves behind, what happens when the page comes back on screen, and that it stops talking to the server
 * when it isn't on screen. The server is a fake that answers when (and how) each test says.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LandingScreenTest {

    /** The five reads the landing page makes - always through the no-cache variants. */
    private static final String[] FRESH = {"getRoomsFresh", "getAnnouncementsFresh", "getAmenitiesFresh", "getPromotionsFresh", "getDiscountsFresh"};
    private static final String[] PLAIN = {"getRooms", "getAnnouncements", "getAmenities", "getPromotions", "getDiscounts"};

    private Context app;
    private ScreenTestSupport.FakeApi api;
    private ActivityController<LandingActivity> controller;
    private LandingActivity activity;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
    }

    // ---- helpers ----

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void advance(Duration d) {
        shadowOf(Looper.getMainLooper()).idleFor(d);
    }

    private void launch() {
        controller = Robolectric.buildActivity(LandingActivity.class);
        controller.setup();
        idle();
        activity = controller.get();
        advance(Duration.ofMillis(700)); // the entrance animation
    }

    private void relayout() {
        View decor = activity.getWindow().getDecorView();
        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        decor.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, dm.widthPixels, dm.heightPixels);
    }

    private int count(String method) {
        return api.count(method);
    }

    private void assertEveryFreshCallMadeTimes(int times) {
        for (String method : FRESH) assertEquals(method, times, count(method));
        for (String method : PLAIN) assertEquals("the landing page must not use the cacheable " + method, 0, count(method));
    }

    private void answerRound(int round, RoomsResponse rooms, List<AnnouncementDto> announcements, List<AmenityDto> amenities,
                             List<PromotionDto> promotions, List<DiscountDto> discounts) {
        api.<RoomsResponse>call("getRoomsFresh", round).succeed(rooms);
        api.<List<AnnouncementDto>>call("getAnnouncementsFresh", round).succeed(announcements);
        api.<List<AmenityDto>>call("getAmenitiesFresh", round).succeed(amenities);
        api.<List<PromotionDto>>call("getPromotionsFresh", round).succeed(promotions);
        api.<List<DiscountDto>>call("getDiscountsFresh", round).succeed(discounts);
        idle();
    }

    /** Two rooms on offer (a third type is fully booked and is not listed), two announcements, two amenities, two offers. */
    private void answerRoundWithTheUsualContent(int round) {
        answerRound(round,
                LandingFixtures.rooms(LandingFixtures.roomType(1, "Deluxe", "1800.00", 3),
                        LandingFixtures.roomType(2, "Suite", "3000.00", 2),
                        LandingFixtures.roomType(3, "Family", "4000.00", 0)),
                LandingFixtures.announcements(LandingFixtures.announcement(1, "Pool closed"), LandingFixtures.announcement(2, "New menu")),
                LandingFixtures.amenities(LandingFixtures.amenity(1, "Wi-Fi", "Connectivity"), LandingFixtures.amenity(2, "Parking", "Services")),
                LandingFixtures.promotions(LandingFixtures.promotion(1, "Weekend Escape")),
                LandingFixtures.discounts(LandingFixtures.discount(1, "Senior Citizen")));
    }

    private void failRound(int round, String... methods) {
        for (String method : methods) api.call(method, round).offline();
        idle();
    }

    private void failEverything(int round) {
        failRound(round, FRESH);
    }

    private SwipeRefreshLayout swipe() {
        return activity.findViewById(R.id.landingSwipeRefresh);
    }

    private RecyclerView rooms() {
        return activity.findViewById(R.id.rvLandingRooms);
    }

    private int roomCards() {
        return rooms().getAdapter().getItemCount();
    }

    private int announcementRows() {
        return ((RecyclerView) activity.findViewById(R.id.rvLandingAnnouncements)).getAdapter().getItemCount();
    }

    private int offerRows() {
        return ((RecyclerView) activity.findViewById(R.id.rvLandingPromotions)).getAdapter().getItemCount();
    }

    private int amenityTiles() {
        return ((GridLayout) activity.findViewById(R.id.gridLandingAmenities)).getChildCount();
    }

    private boolean visible(int id) {
        return activity.findViewById(id).getVisibility() == View.VISIBLE;
    }

    /** A real finger drag from the top of the page downwards - what the guest does to refresh. */
    private void pullDown() {
        View decor = activity.getWindow().getDecorView();
        long t = SystemClock.uptimeMillis();
        float x = 540f;
        float y0 = 500f;
        decor.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y0, 0));
        for (int i = 1; i <= 20; i++) {
            decor.dispatchTouchEvent(MotionEvent.obtain(t, t + i * 16L, MotionEvent.ACTION_MOVE, x, y0 + i * 60f, 0));
        }
        decor.dispatchTouchEvent(MotionEvent.obtain(t, t + 400, MotionEvent.ACTION_UP, x, y0 + 20 * 60f, 0));
        assertTrue("the drag reached the pull layout and started its spinner", swipe().isRefreshing());
        // The library announces the refresh (onRefresh) when its snap-into-place animation finishes, and Robolectric
        // never draws that animation - so announce it the way the library would, as the other refresh tests do.
        try {
            java.lang.reflect.Field field = SwipeRefreshLayout.class.getDeclaredField("mListener");
            field.setAccessible(true);
            ((SwipeRefreshLayout.OnRefreshListener) field.get(swipe())).onRefresh();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        idle();
    }

    /** What the Snackbar says, or null when none is showing. */
    private String snackbarText() {
        TextView text = activity.findViewById(com.google.android.material.R.id.snackbar_text);
        return text == null ? null : text.getText().toString();
    }

    private void tapSnackbarRetry() {
        Button action = activity.findViewById(com.google.android.material.R.id.snackbar_action);
        assertNotNull("the failure message must offer a Retry button", action);
        action.performClick();
        idle();
        advance(Duration.ofMillis(700)); // the message slides away
    }

    private String string(int id) {
        return activity.getString(id);
    }

    // ---- what it asks the server ----

    @Test
    public void opening_asksForEverySectionThroughTheNoCacheEndpointsOnly() {
        launch();

        assertEveryFreshCallMadeTimes(1);
    }

    @Test
    public void noCacheEndpointsCarryTheNoCacheRequestHeaders() throws Exception {
        for (String method : FRESH) {
            java.lang.reflect.Method declared = null;
            for (java.lang.reflect.Method m : com.example.velocitysuites.network.ApiService.class.getMethods()) {
                if (m.getName().equals(method)) declared = m;
            }
            assertNotNull(method + " must exist", declared);
            retrofit2.http.Headers headers = declared.getAnnotation(retrofit2.http.Headers.class);
            assertNotNull(method + " must set request headers", headers);
            List<String> values = java.util.Arrays.asList(headers.value());
            assertTrue(method + " must send Cache-Control: no-cache", values.contains("Cache-Control: no-cache"));
            assertTrue(method + " must send Pragma: no-cache", values.contains("Pragma: no-cache"));
        }
    }

    @Test
    public void whileWaitingForTheFirstAnswer_eachSectionShowsItsOwnSpinner() {
        launch();

        assertTrue(visible(R.id.landingRoomsLoading));
        assertTrue(visible(R.id.announcementsLoading));
        assertTrue(visible(R.id.progressLandingAmenities));
        assertTrue(visible(R.id.promotionsLoading));
        assertFalse("a silent first load never shows the pull spinner", swipe().isRefreshing());
    }

    @Test
    public void onceAnswered_theSectionsShowTheirContent() {
        launch();
        answerRoundWithTheUsualContent(0);

        assertEquals("only rooms that can be booked are listed", 2, roomCards());
        assertEquals("2 Available", ((TextView) activity.findViewById(R.id.tvLandingRoomsCount)).getText().toString());
        assertEquals(2, announcementRows());
        assertEquals(2, amenityTiles());
        assertEquals("one promotion + one discount", 2, offerRows());
        assertFalse(visible(R.id.landingRoomsLoading));
        assertFalse(visible(R.id.announcementsLoading));
        assertFalse(visible(R.id.progressLandingAmenities));
        assertFalse(visible(R.id.promotionsLoading));
        assertFalse(swipe().isRefreshing());
    }

    // ---- pull to refresh ----

    @Test
    public void pullingDown_refetchesEverySection_keepsTheOldContentUp_andStopsTheSpinnerWhenDone() {
        launch();
        answerRoundWithTheUsualContent(0);

        pullDown();

        assertTrue("the pull spinner is showing", swipe().isRefreshing());
        assertEveryFreshCallMadeTimes(2);
        assertEquals("the rooms the guest was looking at stay on screen while refreshing", 2, roomCards());
        assertTrue(visible(R.id.rvLandingRooms));
        assertTrue(visible(R.id.rvLandingAnnouncements));
        assertTrue(visible(R.id.rvLandingPromotions));
        assertTrue(visible(R.id.gridLandingAmenities));
        assertFalse("sections do not blank out into their own spinners", visible(R.id.landingRoomsLoading));
        assertFalse(visible(R.id.announcementsLoading));
        assertFalse(visible(R.id.promotionsLoading));
        assertFalse(visible(R.id.progressLandingAmenities));

        answerRound(1,
                LandingFixtures.rooms(LandingFixtures.roomType(1, "Deluxe", "1800.00", 3),
                        LandingFixtures.roomType(2, "Suite", "3000.00", 2),
                        LandingFixtures.roomType(4, "Penthouse", "9000.00", 1)),
                LandingFixtures.announcements(LandingFixtures.announcement(3, "Fresh news")),
                LandingFixtures.amenities(LandingFixtures.amenity(1, "Wi-Fi", "Connectivity")),
                LandingFixtures.promotions(),
                LandingFixtures.discounts(LandingFixtures.discount(1, "Senior Citizen")));

        assertFalse("the spinner stops when every section has answered", swipe().isRefreshing());
        assertEquals("new data replaces old: three bookable rooms now", 3, roomCards());
        assertEquals(1, announcementRows());
        assertEquals(1, amenityTiles());
        assertEquals(1, offerRows());
        assertNull("a refresh that worked says nothing", snackbarText());
    }

    @Test
    public void pullingDown_whenEverythingFails_keepsTheOldContent_stopsTheSpinner_andOffersRetry() {
        launch();
        answerRoundWithTheUsualContent(0);
        pullDown();

        failEverything(1);

        assertFalse("the spinner must stop on failure too", swipe().isRefreshing());
        assertEquals("old rooms still there", 2, roomCards());
        assertEquals(2, announcementRows());
        assertEquals(2, amenityTiles());
        assertEquals(2, offerRows());
        assertTrue(visible(R.id.rvLandingRooms));
        assertFalse("no error panel replaces working content", visible(R.id.layoutRoomError));
        assertFalse(visible(R.id.announcementsStateView));
        assertFalse(visible(R.id.promotionsStateView));
        assertFalse(visible(R.id.amenitiesStateView));
        assertEquals(string(R.string.landing_refresh_failed_all), snackbarText());

        tapSnackbarRetry();

        assertEveryFreshCallMadeTimes(3);
        assertTrue("Retry shows the spinner again", swipe().isRefreshing());
        assertNull("starting again clears the old message", snackbarText());
        answerRoundWithTheUsualContent(2);
        assertFalse(swipe().isRefreshing());
        assertNull(snackbarText());
        assertEquals(2, roomCards());
    }

    @Test
    public void pullingDown_whenOneSectionFails_saysSo_keepsThatSectionsContent_andUpdatesTheRest() {
        launch();
        answerRoundWithTheUsualContent(0);
        pullDown();

        api.<RoomsResponse>call("getRoomsFresh", 1).offline();
        api.<List<AnnouncementDto>>call("getAnnouncementsFresh", 1).succeed(
                LandingFixtures.announcements(LandingFixtures.announcement(3, "Fresh news")));
        api.<List<AmenityDto>>call("getAmenitiesFresh", 1).succeed(LandingFixtures.amenities(LandingFixtures.amenity(1, "Wi-Fi", "Connectivity")));
        api.<List<PromotionDto>>call("getPromotionsFresh", 1).succeed(LandingFixtures.promotions(LandingFixtures.promotion(1, "Weekend Escape")));
        api.<List<DiscountDto>>call("getDiscountsFresh", 1).succeed(LandingFixtures.discounts(LandingFixtures.discount(1, "Senior Citizen")));
        idle();

        assertFalse(swipe().isRefreshing());
        assertEquals(string(R.string.landing_refresh_failed_some), snackbarText());
        assertEquals("rooms failed: the old list stays", 2, roomCards());
        assertTrue(visible(R.id.rvLandingRooms));
        assertEquals("announcements worked: updated", 1, announcementRows());
        assertEquals(1, amenityTiles());
    }

    @Test
    public void aSecondRefreshWhileOneIsRunning_isIgnored() throws Exception {
        launch();
        answerRoundWithTheUsualContent(0);
        pullDown();
        assertEveryFreshCallMadeTimes(2);

        // the layout itself ignores a new pull while refreshing; a Retry tap / the TalkBack action go straight to the
        // listener, so call that directly too
        Object listener = listenerOf(swipe());
        ((SwipeRefreshLayout.OnRefreshListener) listener).onRefresh();
        ((SwipeRefreshLayout.OnRefreshListener) listener).onRefresh();
        pullDown();

        assertEveryFreshCallMadeTimes(2);
        assertTrue("and the first refresh's spinner was not cancelled by the ignored requests", swipe().isRefreshing());
    }

    private static Object listenerOf(SwipeRefreshLayout swipe) throws Exception {
        java.lang.reflect.Field field = SwipeRefreshLayout.class.getDeclaredField("mListener");
        field.setAccessible(true);
        return field.get(swipe);
    }

    @Test
    public void aRefreshThatTakesAWhile_stillShowsTheSpinnerUntilTheLastSectionAnswers() {
        launch();
        answerRoundWithTheUsualContent(0);
        pullDown();

        api.<RoomsResponse>call("getRoomsFresh", 1).succeed(LandingFixtures.rooms(LandingFixtures.roomType(1, "Deluxe", "1800.00", 3)));
        api.<List<AnnouncementDto>>call("getAnnouncementsFresh", 1).succeed(LandingFixtures.announcements());
        api.<List<AmenityDto>>call("getAmenitiesFresh", 1).succeed(LandingFixtures.amenities());
        api.<List<PromotionDto>>call("getPromotionsFresh", 1).succeed(LandingFixtures.promotions());
        idle();
        assertTrue("discounts are still out", swipe().isRefreshing());

        api.<List<DiscountDto>>call("getDiscountsFresh", 1).succeed(LandingFixtures.discounts());
        idle();
        assertFalse(swipe().isRefreshing());
    }

    @Test
    public void accessibilityUsersGetARefreshAction() {
        launch();
        answerRoundWithTheUsualContent(0);

        View scroller = activity.findViewById(R.id.landingScroll);
        android.view.accessibility.AccessibilityNodeInfo info = scroller.createAccessibilityNodeInfo();
        boolean found = false;
        int actionId = 0;
        for (android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction action : info.getActionList()) {
            if (action.getLabel() != null && string(R.string.landing_refresh_action).contentEquals(action.getLabel())) {
                found = true;
                actionId = action.getId();
            }
        }
        assertTrue("TalkBack cannot pull down, so the page offers 'Refresh page' as an action", found);

        assertTrue(scroller.performAccessibilityAction(actionId, null));
        idle();
        assertEveryFreshCallMadeTimes(2);
        assertTrue(swipe().isRefreshing());
    }

    // ---- first load failing ----

    @Test
    public void ifTheFirstLoadFails_everySectionShowsItsOwnMessage_withoutASnackbar_andRetryWorks() {
        launch();

        failEverything(0);

        assertFalse(swipe().isRefreshing());
        assertTrue("rooms: error panel with Retry", visible(R.id.layoutRoomError));
        assertTrue(visible(R.id.btnRetryRooms));
        assertFalse(visible(R.id.landingRoomsLoading));
        assertTrue(visible(R.id.announcementsStateView));
        assertTrue(visible(R.id.amenitiesStateView));
        assertTrue(visible(R.id.promotionsStateView));
        assertNull("nothing was pulled, so no Snackbar", snackbarText());
        TextView title = activity.findViewById(R.id.announcementsStateView).findViewById(R.id.stateTitle);
        assertEquals(string(R.string.announcements_error_title), title.getText().toString());

        activity.findViewById(R.id.btnRetryRooms).performClick();
        idle();

        assertEveryFreshCallMadeTimes(2);
        assertTrue("retrying shows the section spinners again", visible(R.id.landingRoomsLoading));
        assertFalse(visible(R.id.layoutRoomError));

        answerRoundWithTheUsualContent(1);
        assertEquals(2, roomCards());
        assertFalse(visible(R.id.layoutRoomError));
        assertFalse(visible(R.id.announcementsStateView));
    }

    @Test
    public void ifOnlyOneHalfOfTheOffersLoads_andItIsEmpty_theSectionDoesNotClaimThereAreNoOffers() {
        launch();

        api.<RoomsResponse>call("getRoomsFresh", 0).succeed(LandingFixtures.rooms(LandingFixtures.roomType(1, "Deluxe", "1800.00", 3)));
        api.<List<AnnouncementDto>>call("getAnnouncementsFresh", 0).succeed(LandingFixtures.announcements());
        api.<List<AmenityDto>>call("getAmenitiesFresh", 0).succeed(LandingFixtures.amenities());
        api.<List<PromotionDto>>call("getPromotionsFresh", 0).succeed(LandingFixtures.promotions());
        api.<List<DiscountDto>>call("getDiscountsFresh", 0).offline();
        idle();

        TextView title = activity.findViewById(R.id.promotionsStateView).findViewById(R.id.stateTitle);
        assertTrue(visible(R.id.promotionsStateView));
        assertEquals("unknown is not the same as none", string(R.string.promotions_error_title), title.getText().toString());
    }

    @Test
    public void whenOnlyDiscountsFailToRefresh_theirPreviousContentIsKept() {
        launch();
        answerRoundWithTheUsualContent(0); // 1 promotion + 1 discount
        pullDown();

        api.<RoomsResponse>call("getRoomsFresh", 1).succeed(LandingFixtures.rooms(LandingFixtures.roomType(1, "Deluxe", "1800.00", 3)));
        api.<List<AnnouncementDto>>call("getAnnouncementsFresh", 1).succeed(LandingFixtures.announcements());
        api.<List<AmenityDto>>call("getAmenitiesFresh", 1).succeed(LandingFixtures.amenities());
        api.<List<PromotionDto>>call("getPromotionsFresh", 1).succeed(LandingFixtures.promotions(
                LandingFixtures.promotion(1, "Weekend Escape"), LandingFixtures.promotion(2, "Honeymoon")));
        api.<List<DiscountDto>>call("getDiscountsFresh", 1).offline();
        idle();

        assertEquals("2 fresh promotions + the discount from the last good load", 3, offerRows());
        assertEquals(string(R.string.landing_refresh_failed_some), snackbarText());
    }

    // ---- coming back to the page / staying on it ----

    @Test
    public void comingBackToThePage_catchesUpSilently_withoutBlankingTheContent() {
        launch();
        answerRoundWithTheUsualContent(0);

        controller.pause();
        controller.resume();
        idle();

        assertEveryFreshCallMadeTimes(2);
        assertFalse("silent: no pull spinner", swipe().isRefreshing());
        assertEquals(2, roomCards());
        assertTrue(visible(R.id.rvLandingRooms));
        assertFalse(visible(R.id.landingRoomsLoading));
        assertFalse(visible(R.id.announcementsLoading));

        answerRound(1,
                LandingFixtures.rooms(LandingFixtures.roomType(1, "Deluxe", "1800.00", 3)),
                LandingFixtures.announcements(LandingFixtures.announcement(1, "Pool closed"), LandingFixtures.announcement(2, "New menu"), LandingFixtures.announcement(9, "Breaking")),
                LandingFixtures.amenities(LandingFixtures.amenity(1, "Wi-Fi", "Connectivity")),
                LandingFixtures.promotions(LandingFixtures.promotion(1, "Weekend Escape")),
                LandingFixtures.discounts());
        assertEquals("the catch-up applied: a new announcement appeared by itself", 3, announcementRows());
        assertEquals("and the sold-out room dropped off", 1, roomCards());
    }

    @Test
    public void aSilentRefreshThatFails_isInvisible() {
        launch();
        answerRoundWithTheUsualContent(0);

        controller.pause();
        controller.resume();
        idle();
        failEverything(1);

        assertNull("the guest did nothing, so a background miss shows no message", snackbarText());
        assertEquals("and the page is exactly as it was", 2, roomCards());
        assertTrue(visible(R.id.rvLandingRooms));
        assertFalse(visible(R.id.layoutRoomError));
    }

    @Test
    public void theFirstResumeAfterOpeningDoesNotLoadASecondTime() {
        launch();

        assertEveryFreshCallMadeTimes(1);
    }

    @Test
    public void whileOnScreen_itRefreshesEveryThirtySeconds_andNotBefore() {
        launch();
        answerRoundWithTheUsualContent(0);

        // The timer was started by onResume, a little before launch() returns (the harness moves the clock on by
        // about a second while it settles), so measure from here with room to spare on both sides.
        advance(Duration.ofSeconds(25));
        assertEveryFreshCallMadeTimes(1);

        advance(Duration.ofSeconds(10));
        assertEveryFreshCallMadeTimes(2);
        assertFalse("a timed refresh is silent", swipe().isRefreshing());
    }

    @Test
    public void whenThePageIsCovered_theTimerStops_andComingBackNeverLeavesTwoTimers() {
        launch();
        answerRoundWithTheUsualContent(0);

        controller.pause();
        advance(Duration.ofMinutes(10));
        assertEveryFreshCallMadeTimes(1);

        controller.resume();
        idle();
        assertEveryFreshCallMadeTimes(2); // the catch-up on return
        answerRoundWithTheUsualContent(1);

        advance(Duration.ofSeconds(30));
        assertEveryFreshCallMadeTimes(3); // exactly one timer: one tick, not two
        answerRoundWithTheUsualContent(2);
        advance(Duration.ofSeconds(30));
        assertEveryFreshCallMadeTimes(4);
    }

    @Test
    public void aTimerTickWhileARefreshIsStillRunning_doesNotStartAnother() {
        launch();
        answerRoundWithTheUsualContent(0);
        advance(Duration.ofSeconds(30));
        assertEveryFreshCallMadeTimes(2); // answered by nobody yet

        advance(Duration.ofSeconds(30));
        advance(Duration.ofSeconds(30));

        assertEveryFreshCallMadeTimes(2);
    }

    @Test
    public void afterTheScreenIsGone_lateAnswersAreIgnored_andNothingPollsAnyMore() {
        launch();

        controller.pause().stop().destroy();
        answerRoundWithTheUsualContent(0); // the slow server finally answers a screen that no longer exists
        advance(Duration.ofMinutes(5));

        assertEveryFreshCallMadeTimes(1);
    }

    // ---- the cart ----

    private void selectTwoOfTheFirstRoom() {
        relayout();
        View card = rooms().getChildAt(0);
        assertNotNull("a room card is on screen", card);
        View plus = card.findViewById(R.id.btnRoomQtyPlus);
        plus.performClick();
        relayout();
        rooms().getChildAt(0).findViewById(R.id.btnRoomQtyPlus).performClick();
        relayout();
    }

    @Test
    public void aSelectionIsTrimmedWhenARefreshShowsFewerRoomsLeft() {
        launch();
        answerRoundWithTheUsualContent(0); // Deluxe: 3 left
        selectTwoOfTheFirstRoom();
        String twoSelected = ((TextView) activity.findViewById(R.id.tvSelectionSummaryCount)).getText().toString();
        assertTrue(twoSelected, twoSelected.startsWith("2 "));

        controller.pause();
        controller.resume();
        idle();
        answerRound(1,
                LandingFixtures.rooms(LandingFixtures.roomType(1, "Deluxe", "1800.00", 1), LandingFixtures.roomType(2, "Suite", "3000.00", 2)),
                LandingFixtures.announcements(), LandingFixtures.amenities(), LandingFixtures.promotions(), LandingFixtures.discounts());

        String now = ((TextView) activity.findViewById(R.id.tvSelectionSummaryCount)).getText().toString();
        assertTrue("only one Deluxe is left, so the cart says 1: " + now, now.startsWith("1 "));
        assertTrue(visible(R.id.cardSelectionSummaryBar));
    }

    @Test
    public void aSelectedRoomThatSoldOut_leavesTheCart_andTheSummaryBarGoesAway() {
        launch();
        answerRoundWithTheUsualContent(0);
        selectTwoOfTheFirstRoom();
        assertTrue(visible(R.id.cardSelectionSummaryBar));

        controller.pause();
        controller.resume();
        idle();
        answerRound(1,
                LandingFixtures.rooms(LandingFixtures.roomType(1, "Deluxe", "1800.00", 0), LandingFixtures.roomType(2, "Suite", "3000.00", 2)),
                LandingFixtures.announcements(), LandingFixtures.amenities(), LandingFixtures.promotions(), LandingFixtures.discounts());

        assertFalse("Book Selected must not offer a room that is gone", visible(R.id.cardSelectionSummaryBar));
    }

    // ---- structure ----

    @Test
    public void thePullLayoutWrapsOnlyTheScrollingPage_soTheHeaderAndTheCartBarStayPut() {
        launch();
        answerRoundWithTheUsualContent(0);
        relayout();

        View root = activity.findViewById(R.id.landingRoot);
        SwipeRefreshLayout swipe = swipe();
        assertSame(root, swipe.getParent());
        assertSame("the scroller is inside the pull layout", swipe, ((View) activity.findViewById(R.id.landingScroll).getParent()));
        assertTrue("the brand bar sits above it, outside", activity.findViewById(R.id.landingHeaderCard).getBottom() <= swipe.getTop());
        assertTrue(activity.findViewById(R.id.cardSelectionSummaryBar).getParent() == root);

        int headerTopBefore = activity.findViewById(R.id.landingHeaderCard).getTop();
        pullDown();
        relayout();
        assertEquals("pulling moves the spinner, not the header", headerTopBefore, activity.findViewById(R.id.landingHeaderCard).getTop());
    }

    @Test
    public void theFooterReadsExactly() {
        launch();
        String expected = (char) 0x00A9 + "2026 Velocity Suites Surallah. All rights reserved.";

        TextView footer = activity.findViewById(R.id.landingFooterCopyright);

        assertEquals(expected, footer.getText().toString());
    }

    @Test
    public void theFooterIsReachableAtTheEndOfThePage() {
        launch();
        answerRoundWithTheUsualContent(0);
        relayout();
        NestedScrollView scroll = activity.findViewById(R.id.landingScroll);

        scroll.fullScroll(View.FOCUS_DOWN);
        relayout();

        TextView footer = activity.findViewById(R.id.landingFooterCopyright);
        int footerBottomInScroller = footerBottomRelativeToScroller(footer, scroll) - scroll.getScrollY();
        assertTrue("the copyright line is fully on screen at the end (bottom " + footerBottomInScroller + " of " + scroll.getHeight() + ")",
                footerBottomInScroller <= scroll.getHeight());
        assertTrue(footerBottomInScroller > 0);
    }

    private static int footerBottomRelativeToScroller(View view, View scroller) {
        int bottom = view.getBottom();
        View parent = (View) view.getParent();
        while (parent != scroller && parent != null) {
            bottom += parent.getTop();
            parent = (View) parent.getParent();
        }
        return bottom;
    }

    @Test
    public void everyActionIsAtLeast48dpTall() {
        launch();
        answerRoundWithTheUsualContent(0);
        // States that only exist sometimes: force them on so their real size is measured.
        for (int id : new int[]{R.id.layoutRoomError, R.id.btnToggleRooms, R.id.cardSelectionSummaryBar}) {
            activity.findViewById(id).setVisibility(View.VISIBLE);
        }
        relayout();
        float density = activity.getResources().getDisplayMetrics().density;
        int min = Math.round(48 * density);

        for (int id : new int[]{R.id.btnProceed, R.id.btnExploreNow, R.id.btnRetryRooms, R.id.btnToggleRooms, R.id.btnViewLocation,
                R.id.btnCartClear, R.id.btnCartReserve, R.id.btnCartBook, R.id.layoutSelectionSummaryDetails}) {
            View v = activity.findViewById(id);
            assertTrue(activity.getResources().getResourceEntryName(id) + " is " + v.getHeight() + "px, needs " + min, v.getHeight() >= min);
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h568dp-xhdpi")
    public void onTheSmallestPhone_nothingSticksOutSideways_andSignInStaysOnScreen() {
        launch();
        answerRoundWithTheUsualContent(0);
        relayout();

        assertNoHorizontalOverflow();
        View signIn = activity.findViewById(R.id.btnProceed);
        int[] at = new int[2];
        signIn.getLocationInWindow(at);
        assertTrue("Sign In / Sign Up is fully on screen", at[0] >= 0 && at[0] + signIn.getWidth() <= activity.getResources().getDisplayMetrics().widthPixels);
    }

    @Test
    @Config(qualifiers = "w320dp-h568dp-xhdpi")
    public void withTheLargestFont_nothingSticksOutSideways_theHeroTextIsNotCutOff_andSignInIsStillReachable() {
        RuntimeEnvironment.setFontScale(2.0f);
        launch();
        answerRoundWithTheUsualContent(0);
        relayout();

        assertNoHorizontalOverflow();
        TextView roomsTitle = activity.findViewById(R.id.tvRoomsTitle);
        assertTrue("the 'Available Rooms' title wraps word by word, not letter by letter (" + roomsTitle.getLineCount() + " lines)",
                roomsTitle.getLineCount() <= 2);
        View explore = activity.findViewById(R.id.btnExploreNow);
        ViewGroup overlay = (ViewGroup) explore.getParent();
        ViewGroup hero = (ViewGroup) overlay.getParent();
        assertTrue("hero text starts inside the picture area, not above it (top " + overlay.getTop() + ")", overlay.getTop() >= 0);
        assertTrue("and ends inside it", overlay.getBottom() <= hero.getHeight());
        View signIn = activity.findViewById(R.id.btnProceed);
        int[] at = new int[2];
        signIn.getLocationInWindow(at);
        assertTrue("Sign In / Sign Up is fully on screen", at[0] >= 0 && at[0] + signIn.getWidth() <= activity.getResources().getDisplayMetrics().widthPixels);
        assertTrue(signIn.getHeight() >= Math.round(48 * activity.getResources().getDisplayMetrics().density));
    }

    @Test
    public void withAFontOneAndAHalfTimesBigger_theRoomsTitleIsNotSqueezedByTheLiveRatesTag() {
        RuntimeEnvironment.setFontScale(1.5f);
        launch();
        answerRoundWithTheUsualContent(0);
        relayout();

        TextView roomsTitle = activity.findViewById(R.id.tvRoomsTitle);

        assertTrue("two words, so at most two lines (got " + roomsTitle.getLineCount() + ")", roomsTitle.getLineCount() <= 2);
        View count = activity.findViewById(R.id.tvLandingRoomsCount);
        assertTrue("the count stays readable: at most two lines (got " + ((TextView) count).getLineCount() + ")", ((TextView) count).getLineCount() <= 2);
    }

    private void assertNoHorizontalOverflow() {
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        List<String> problems = new java.util.ArrayList<>();
        collectOverflow(activity.findViewById(R.id.landingRoot), screenWidth, problems);
        assertTrue("views sticking out of the screen sideways: " + problems, problems.isEmpty());
    }

    private void collectOverflow(View v, int screenWidth, List<String> problems) {
        if (v.getVisibility() != View.VISIBLE) return;
        int[] at = new int[2];
        v.getLocationInWindow(at);
        // the map is a platform view that reports its own size; skip it and the library's pull spinner
        boolean library = v.getClass().getName().contains("MapView") || v.getClass().getName().contains("CircleImageView");
        if (!library && v.getWidth() > 0 && (at[0] < -1 || at[0] + v.getWidth() > screenWidth + 1)) {
            problems.add(v.getClass().getSimpleName() + idName(v) + " x=" + at[0] + " w=" + v.getWidth() + " of " + screenWidth);
        }
        if (v instanceof ViewGroup && !library) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collectOverflow(g.getChildAt(i), screenWidth, problems);
        }
    }

    private String idName(View v) {
        if (v.getId() == View.NO_ID) return "";
        try {
            return "#" + activity.getResources().getResourceEntryName(v.getId());
        } catch (android.content.res.Resources.NotFoundException generated) {
            return "";
        }
    }
}
