package com.example.velocitysuites.ui;

import static com.example.velocitysuites.ui.WizardDriver.idle;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.R;
import com.example.velocitysuites.Room;
import com.example.velocitysuites.RoomBrowsingActivity;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.lang.reflect.Field;
import java.util.Calendar;
import java.util.Map;

/**
 * 1.73.0, the simplified booking and reservation flow (the same steps, words and rules as the guest web). Part 1: Book now and Reserve (on a
 * room card, in the details sheet, and Book Selected / Reserve Selected) need check-in and check-out dates the guest chose; without them the
 * date fields come into view with one inline message and nothing opens.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Release173Test {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private RoomBrowsingActivity browsing() {
        Context app = ApplicationProvider.getApplicationContext();
        ScreenTestSupport.freshRepository(app, new ScreenTestSupport.FakeApi());
        RoomBrowsingActivity a = Robolectric.buildActivity(RoomBrowsingActivity.class, new Intent(app, RoomBrowsingActivity.class)).setup().get();
        idle();
        return a;
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Calendar day(int offset) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.DAY_OF_MONTH, offset);
        return c;
    }

    // ---- Part 1

    @Test
    public void bookNowWithoutDates_asksForThemInline_andOpensNothing() {
        RoomBrowsingActivity a = browsing();
        Room room = WizardDriver.room("1", 1000);

        a.onBookNowClick(room);
        idle();

        TextInputLayout checkIn = a.findViewById(R.id.checkInLayout);
        assertEquals("Choose your check-in and check-out dates first.", checkIn.getError().toString());
        assertEquals(a.getString(R.string.error_dates_first), checkIn.getError().toString());
        assertNull("no booking screen opened", shadowOf(a).getNextStartedActivity());
        assertNull("no details sheet opened", org.robolectric.shadows.ShadowDialog.getLatestDialog());
    }

    @Test
    public void reserveWithoutDates_isBlockedTheSameWay() {
        RoomBrowsingActivity a = browsing();

        a.onReserveNowClick(WizardDriver.room("1", 1000));
        idle();

        assertEquals("Choose your check-in and check-out dates first.", ((TextInputLayout) a.findViewById(R.id.checkInLayout)).getError().toString());
        assertNull(shadowOf(a).getNextStartedActivity());
        assertNull(org.robolectric.shadows.ShadowDialog.getLatestDialog());
    }

    @Test
    public void withOnlyACheckInDate_theMessageSitsUnderCheckOut() throws Exception {
        RoomBrowsingActivity a = browsing();
        set(a, "checkInDate", day(5));

        assertTrue("a missing check-out is not a choice", !a.requireDatesFirst());
        idle();

        assertNull(((TextInputLayout) a.findViewById(R.id.checkInLayout)).getError());
        assertEquals("Choose your check-in and check-out dates first.", ((TextInputLayout) a.findViewById(R.id.checkOutLayout)).getError().toString());
    }

    @Test
    public void bookSelectedAndReserveSelected_areBlockedWithoutDates() throws Exception {
        RoomBrowsingActivity a = browsing();
        Field cart = RoomBrowsingActivity.class.getDeclaredField("selectedQuantities");
        cart.setAccessible(true);
        @SuppressWarnings("unchecked") Map<String, Integer> quantities = (Map<String, Integer>) cart.get(a);
        quantities.put("1", 2);

        a.findViewById(R.id.btnCartBook).performClick();
        idle();
        assertNull("Book Selected opened nothing", shadowOf(a).getNextStartedActivity());
        assertEquals("Choose your check-in and check-out dates first.", ((TextInputLayout) a.findViewById(R.id.checkInLayout)).getError().toString());

        ((TextInputLayout) a.findViewById(R.id.checkInLayout)).setError(null);
        a.findViewById(R.id.btnCartReserve).performClick();
        idle();
        assertNull("Reserve Selected opened nothing", shadowOf(a).getNextStartedActivity());
        assertNotNull(((TextInputLayout) a.findViewById(R.id.checkInLayout)).getError());
    }

    @Test
    public void withBothDates_thereIsNoMessage() throws Exception {
        RoomBrowsingActivity a = browsing();
        set(a, "checkInDate", day(5));
        set(a, "checkOutDate", day(7));

        assertTrue(a.requireDatesFirst());
        assertNull(((TextInputLayout) a.findViewById(R.id.checkInLayout)).getError());
        assertNull(((TextInputLayout) a.findViewById(R.id.checkOutLayout)).getError());
    }
}
