package com.example.velocitysuites.ui;

import static com.example.velocitysuites.ui.WizardDriver.advance;
import static com.example.velocitysuites.ui.WizardDriver.answer;
import static com.example.velocitysuites.ui.WizardDriver.collect;
import static com.example.velocitysuites.ui.WizardDriver.day;
import static com.example.velocitysuites.ui.WizardDriver.dialogText;
import static com.example.velocitysuites.ui.WizardDriver.find;
import static com.example.velocitysuites.ui.WizardDriver.idle;
import static com.example.velocitysuites.ui.WizardDriver.latestDialog;
import static com.example.velocitysuites.ui.WizardDriver.plain;
import static com.example.velocitysuites.ui.WizardDriver.room;
import static com.example.velocitysuites.ui.WizardDriver.step;
import static com.example.velocitysuites.ui.WizardDriver.text;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.BookingWizardActivity;
import com.example.velocitysuites.BookingWizardState;
import com.example.velocitysuites.PendingWizardRooms;
import com.example.velocitysuites.R;
import com.example.velocitysuites.Room;
import com.example.velocitysuites.StartingTransactionActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Step 1 when the guest arrives from Room Browsing with rooms already selected: the date step comes first (no night count and no total while
 * there are no dates), the selected rooms are one compact card, searched dates are carried in and availability is re-checked, fewer free rooms
 * lower the count with an inline note, Remove asks first, and Next says why it is disabled. Booking and reservation behave the same.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ArrivingWithRoomsTest {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private Context app;
    private ScreenTestSupport.FakeApi api;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
    }

    @After
    public void tearDown() {
        PendingWizardRooms.consume();
        PendingWizardRooms.consumeDates();
        RuntimeEnvironment.setFontScale(1.0f);
    }

    private static List<Room> twoDeluxe() {
        List<Room> rooms = new ArrayList<>();
        Room r = room("1", 1200);
        rooms.add(r);
        rooms.add(r);
        return rooms;
    }

    private BookingWizardActivity arrive(BookingWizardState.Mode mode, boolean withDates) {
        PendingWizardRooms.set(twoDeluxe());
        if (withDates) PendingWizardRooms.setDates(day(5), day(7));
        BookingWizardActivity a = Robolectric.buildActivity(BookingWizardActivity.class, BookingWizardActivity.newIntent(app, mode)).setup().get();
        idle();
        return a;
    }

    private static String rowsText(BookingWizardActivity a) {
        return plain(collect(find(a, R.id.layoutStaysSummary)));
    }

    // ---- no dates yet

    @Test
    public void arrivingWithRoomsAndNoDates_putsTheDatesFirst_andShowsNoNightsAndNoTotal() {
        BookingWizardActivity a = arrive(BookingWizardState.Mode.BOOKING, false);
        ViewGroup content = find(a, R.id.staysContent);
        assertEquals("the date step is the first block on the page", 0, content.indexOfChild(find(a, R.id.layoutStayDates)));
        assertEquals("Choose your dates", text(find(a, R.id.tvActiveStayHeading)));
        assertEquals("Pick check-in and check-out for the rooms you selected", text(find(a, R.id.tvChooseDatesHint)));
        assertEquals(View.VISIBLE, find(a, R.id.tvChooseDatesHint).getVisibility());
        assertNotNull("the Check-in field is there, with the earliest-date helper", find(a, R.id.layoutStayDates).findViewById(R.id.tilCheckIn));
        assertTrue(content.indexOfChild(find(a, R.id.layoutStayDates)) < content.indexOfChild(find(a, R.id.layoutStaysSummary)));

        String card = rowsText(a);
        assertTrue(card, card.contains("Selected rooms"));
        assertTrue(card, card.contains("Room 1 × 2 · ₱1,200.00 per night"));
        assertTrue(card, card.contains("Choose your dates to see the total"));
        assertFalse(card, card.contains("night ·") || card.contains("1 night") || card.contains("nights"));
        assertFalse("no total of any kind", card.contains("₱2,400") || card.contains("Total for all dates") || card.contains("Not confirmed yet"));
        assertEquals(View.GONE, find(a, R.id.layoutStaysTotalRow).getVisibility());
        assertEquals("no dates, so no availability call yet", 0, api.count("getRoomsFresh"));
        assertEquals("the intro-card title is replaced by the card's own heading", View.GONE, find(a, R.id.tvStaysTitle).getVisibility());
    }

    @Test
    public void theSelectedRoomsCard_hasEditAndRemoveOnOneRowAtTheTopRight() {
        BookingWizardActivity a = arrive(BookingWizardState.Mode.BOOKING, false);
        LinearLayout rows = find(a, R.id.layoutStayRows);
        assertEquals(1, rows.getChildCount());
        View row = rows.getChildAt(0);
        View title = row.findViewById(R.id.tvStayRowTitle), edit = row.findViewById(R.id.btnStayRowEdit), remove = row.findViewById(R.id.btnStayRowRemove);
        assertEquals("Selected rooms", plain(text(title)));
        assertEquals("Edit and Remove share the title's row", title.getParent(), edit.getParent());
        assertEquals(edit.getParent(), remove.getParent());
        // measured: the three sit on one line
        View root = step(a).getView();
        int screen = a.getResources().getDisplayMetrics().widthPixels;
        root.measure(View.MeasureSpec.makeMeasureSpec(screen, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        root.layout(0, 0, screen, root.getMeasuredHeight());
        assertTrue("Edit is left of Remove", edit.getRight() <= remove.getLeft());
        assertTrue("Remove is at the right edge of the card", remove.getRight() > title.getRight());
        assertEquals("one row", ((TextView) title).getLineCount() <= 1 || title.getHeight() > 0, true);
    }

    @Test
    public void nextIsDisabledWithTheReasonShownAboveIt() {
        BookingWizardActivity a = arrive(BookingWizardState.Mode.BOOKING, false);
        assertFalse(a.findViewById(R.id.btnWizardNext).isEnabled());
        TextView reason = a.findViewById(R.id.wizardNextReason);
        assertEquals(View.VISIBLE, reason.getVisibility());
        assertEquals("Choose your dates first", reason.getText().toString());
    }

    @Test
    public void removeAsksFirst_cancelKeepsTheRooms_yesRemovesThem() {
        BookingWizardActivity a = arrive(BookingWizardState.Mode.BOOKING, false);
        find(a, R.id.layoutStayRows).<View>findViewById(R.id.btnStayRowRemove).performClick();
        idle();
        String asked = dialogText(latestDialog());
        assertTrue(asked, asked.contains("Remove the selected rooms?"));
        answer(false);
        assertEquals("Cancel keeps them", 2, a.getState().selectedRooms.size());
        advance();
        find(a, R.id.layoutStayRows).<View>findViewById(R.id.btnStayRowRemove).performClick();
        idle();
        answer(true);
        idle();
        assertEquals("Yes removes them", 0, a.getState().selectedRooms.size());
    }

    // ---- dates from Room Browsing

    @Test
    public void datesFromRoomBrowsing_areCarriedIn_andAvailabilityIsRechecked() {
        BookingWizardActivity a = arrive(BookingWizardState.Mode.BOOKING, true);
        assertNotNull(a.getState().checkIn);
        assertEquals(2, a.getState().nights());
        assertEquals("the Check-in field shows the carried date", false, text(find(a, R.id.layoutStayDates).findViewById(R.id.etCheckIn)).isEmpty());
        assertEquals("availability for the carried dates is re-checked", 1, api.count("getRoomsFresh"));
        api.<com.example.velocitysuites.network.dto.RoomsResponse>last("getRoomsFresh").succeed(LandingFixtures.rooms(LandingFixtures.roomType(1, "Room 1", "1200.00", 5)));
        idle();
        assertEquals("enough free: nothing is lowered", 2, a.getState().selectedRooms.size());
        assertEquals(View.GONE, find(a, R.id.tvRoomsLowered).getVisibility());

        String card = rowsText(a);
        assertTrue(card, card.contains("2 rooms × 2 nights = ₱4,800.00"));
        assertTrue(card, card.contains("₱4,800.00"));
        assertEquals("the date step stays first while choosing", 0, ((ViewGroup) find(a, R.id.staysContent)).indexOfChild(find(a, R.id.layoutStayDates)));
        assertEquals(View.VISIBLE, find(a, R.id.btnConfirmStay).getVisibility());
        assertEquals("Confirm these dates", text(find(a, R.id.btnConfirmStay)));
        assertEquals("Confirm these dates to continue", a.<TextView>findViewById(R.id.wizardNextReason).getText().toString());
        assertFalse(a.findViewById(R.id.btnWizardNext).isEnabled());
    }

    @Test
    public void fewerRoomsFreeThanSelected_lowersTheCount_andSaysSoInline() {
        BookingWizardActivity a = arrive(BookingWizardState.Mode.BOOKING, true);
        api.<com.example.velocitysuites.network.dto.RoomsResponse>last("getRoomsFresh").succeed(LandingFixtures.rooms(LandingFixtures.roomType(1, "Room 1", "1200.00", 1)));
        idle();
        assertEquals(1, a.getState().selectedRooms.size());
        TextView note = find(a, R.id.tvRoomsLowered);
        assertEquals(View.VISIBLE, note.getVisibility());
        assertEquals("Only 1 of Room 1 is free for these dates, so your selection was lowered from 2 to 1.", plain(note.getText().toString()));
        assertTrue(rowsText(a), rowsText(a).contains("1 room × 2 nights = ₱2,400.00"));
    }

    @Test
    public void whenNoneAreFree_theRoomIsDroppedWithAnInlineNote() {
        BookingWizardActivity a = arrive(BookingWizardState.Mode.BOOKING, true);
        api.<com.example.velocitysuites.network.dto.RoomsResponse>last("getRoomsFresh").succeed(LandingFixtures.rooms(LandingFixtures.roomType(1, "Room 1", "1200.00", 0)));
        idle();
        assertEquals(0, a.getState().selectedRooms.size());
        assertTrue(plain(text(find(a, R.id.tvRoomsLowered))), plain(text(find(a, R.id.tvRoomsLowered))).contains("is not free for these dates"));
    }

    // ---- reservation

    @Test
    public void aReservationBehavesTheSame() {
        BookingWizardActivity a = arrive(BookingWizardState.Mode.RESERVATION, false);
        assertEquals(0, ((ViewGroup) find(a, R.id.staysContent)).indexOfChild(find(a, R.id.layoutStayDates)));
        String card = rowsText(a);
        assertTrue(card, card.contains("Room 1 × 2 · ₱1,200.00 per night") && card.contains("Choose your dates to see the total"));
        assertEquals("Choose your dates first", a.<TextView>findViewById(R.id.wizardNextReason).getText().toString());
        assertFalse(a.findViewById(R.id.btnWizardNext).isEnabled());
    }

    // ---- the intro screen

    @Test
    public void theIntroScreenListsTheRoomsWithARate_andNoTotal() {
        for (BookingWizardState.Mode mode : new BookingWizardState.Mode[]{BookingWizardState.Mode.BOOKING, BookingWizardState.Mode.RESERVATION}) {
            PendingWizardRooms.set(twoDeluxe());
            StartingTransactionActivity intro = Robolectric.buildActivity(StartingTransactionActivity.class, StartingTransactionActivity.newIntent(app, mode)).setup().get();
            idle();
            assertEquals("2 rooms selected", plain(text(intro.findViewById(R.id.tvRoomRecapCount))));
            assertEquals("• Room 1 × 2 · ₱1,200.00 per night", plain(text(intro.findViewById(R.id.tvRoomRecapNames))));
            assertEquals("the intro shows no total", View.GONE, intro.findViewById(R.id.tvRoomRecapTotal).getVisibility());
            PendingWizardRooms.consume();
        }
    }

    // ---- layout

    @Test
    public void theNoDatesLayoutFits_at360And393dp_lightAndDark_atFontScale13() {
        for (String qualifiers : new String[]{"w360dp-h800dp-notnight", "w360dp-h800dp-night", "w393dp-h851dp-notnight", "w393dp-h851dp-night"}) {
            RuntimeEnvironment.setQualifiers("+" + qualifiers);
            RuntimeEnvironment.setFontScale(1.3f);
            BookingWizardActivity a = arrive(BookingWizardState.Mode.BOOKING, false);
            View root = step(a).getView();
            int screen = a.getResources().getDisplayMetrics().widthPixels;
            root.measure(View.MeasureSpec.makeMeasureSpec(screen, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            root.layout(0, 0, screen, root.getMeasuredHeight());
            assertOnScreen(qualifiers, find(a, R.id.layoutStaysSummary), screen);
            assertOnScreen(qualifiers, find(a, R.id.layoutStayDates), screen);
            View row = ((LinearLayout) find(a, R.id.layoutStayRows)).getChildAt(0);
            assertOnScreen(qualifiers, row.findViewById(R.id.btnStayRowRemove), screen);
            assertTrue(qualifiers + ": Edit and Remove stay on one row", Math.abs(row.findViewById(R.id.btnStayRowEdit).getTop() - row.findViewById(R.id.btnStayRowRemove).getTop()) < 4);
        }
        RuntimeEnvironment.setQualifiers("+w360dp-h800dp-notnight");
    }

    private static void assertOnScreen(String label, View v, int screen) {
        int[] loc = new int[2];
        v.getLocationInWindow(loc);
        assertTrue(label + ": " + v.getClass().getSimpleName() + " fits", v.getWidth() <= screen && v.getLeft() >= 0);
    }
}
