package com.example.velocitysuites.ui;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.R;
import com.example.velocitysuites.RoomBrowsingActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Calendar;

/**
 * Room browsing keeps the guest's work across recreation (the dark-mode toggle, a system kill): the multi-room cart, the
 * chosen dates and the filters come back, and the launch extras that seeded the first cart are not applied a second time.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class RoomBrowsingRecreateTest {

    private static Calendar day(int year, int month, int day) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month, day);
        return c;
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    public void theCartTheDatesAndTheFiltersSurviveRecreation_andTheLaunchExtrasAreNotAppliedTwice() throws Exception {
        Context app = ApplicationProvider.getApplicationContext();
        ScreenTestSupport.freshRepository(app, new ScreenTestSupport.FakeApi());
        // The landing page hands over a cart of three rooms: two of type 5, one of type 7.
        Intent launch = new Intent(app, RoomBrowsingActivity.class).putExtra("ROOM_IDS", "5,5,7");

        ActivityController<RoomBrowsingActivity> first = Robolectric.buildActivity(RoomBrowsingActivity.class, launch).setup();
        shadowOf(Looper.getMainLooper()).idle();
        RoomBrowsingActivity a = first.get();
        set(a, "checkInDate", day(2026, Calendar.OCTOBER, 12));
        set(a, "checkOutDate", day(2026, Calendar.OCTOBER, 14));
        set(a, "selectedMaxPrice", 4000f);
        set(a, "currentSortMode", "PriceLow");

        Bundle saved = new Bundle();
        first.saveInstanceState(saved);
        first.pause().stop().destroy();

        // ...recreated with the very same launch intent.
        ActivityController<RoomBrowsingActivity> second = Robolectric.buildActivity(RoomBrowsingActivity.class, launch).setup(saved);
        shadowOf(Looper.getMainLooper()).idle();
        RoomBrowsingActivity b = second.get();

        Field in = RoomBrowsingActivity.class.getDeclaredField("checkInDate");
        in.setAccessible(true);
        Calendar restoredCheckIn = (Calendar) in.get(b);
        assertEquals("the check-in date is back", 12, restoredCheckIn.get(Calendar.DAY_OF_MONTH));
        assertEquals(Calendar.OCTOBER, restoredCheckIn.get(Calendar.MONTH));
        assertEquals("2 nights", ((TextView) b.findViewById(R.id.tvNightsSummary)).getText().toString());
        assertEquals(View.VISIBLE, b.findViewById(R.id.tvPinnedDates).getVisibility());

        Bundle again = new Bundle();
        second.saveInstanceState(again);
        assertArrayEquals("the same three rooms, not six", saved.getStringArray("cart_ids"), again.getStringArray("cart_ids"));
        assertArrayEquals(saved.getIntArray("cart_qty"), again.getIntArray("cart_qty"));
        assertArrayEquals(new int[]{2, 1}, again.getIntArray("cart_qty"));
        assertEquals(4000f, again.getFloat("max_price"), 0.01f);
        assertEquals("PriceLow", again.getString("sort_mode"));
        assertTrue(again.getLong("check_in_utc") > 0);
        assertTrue(again.getLong("check_out_utc") > again.getLong("check_in_utc"));

        second.pause().stop().destroy();
    }

    @Test
    public void aFreshLaunchWithNothingSavedStartsEmpty() throws Exception {
        Context app = ApplicationProvider.getApplicationContext();
        ScreenTestSupport.freshRepository(app, new ScreenTestSupport.FakeApi());
        ActivityController<RoomBrowsingActivity> c = Robolectric.buildActivity(RoomBrowsingActivity.class, new Intent(app, RoomBrowsingActivity.class)).setup();
        shadowOf(Looper.getMainLooper()).idle();

        Bundle state = new Bundle();
        c.saveInstanceState(state);
        assertEquals(0, state.getStringArray("cart_ids").length);
        assertEquals(-1L, state.getLong("check_in_utc"));
        assertFalse(((TextView) c.get().findViewById(R.id.etCheckIn)).getText().length() > 0);
        c.pause().stop().destroy();
    }

    @Test
    public void aDatePickerOpenDuringRecreationStillAppliesTheChosenDate() throws Exception {
        Context app = ApplicationProvider.getApplicationContext();
        ScreenTestSupport.freshRepository(app, new ScreenTestSupport.FakeApi());
        ActivityController<RoomBrowsingActivity> controller = Robolectric.buildActivity(RoomBrowsingActivity.class, new Intent(app, RoomBrowsingActivity.class)).setup();
        shadowOf(Looper.getMainLooper()).idle();

        controller.get().findViewById(R.id.etCheckIn).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue("the picker is showing", controller.get().getSupportFragmentManager().findFragmentByTag("CHECK_IN_PICKER") != null);

        controller.recreate(); // dark-mode toggle / system kill with the picker open
        shadowOf(Looper.getMainLooper()).idle();
        RoomBrowsingActivity recreated = controller.get();

        androidx.fragment.app.Fragment picker = recreated.getSupportFragmentManager().findFragmentByTag("CHECK_IN_PICKER");
        assertTrue("the picker came back", picker instanceof com.google.android.material.datepicker.MaterialDatePicker);
        View confirm = ((androidx.fragment.app.DialogFragment) picker).getDialog().findViewById(com.google.android.material.R.id.confirm_button);
        confirm.performClick();
        shadowOf(Looper.getMainLooper()).idle();

        Field in = RoomBrowsingActivity.class.getDeclaredField("checkInDate");
        in.setAccessible(true);
        assertTrue("the date picked in the restored picker was applied", in.get(recreated) != null);
        assertTrue(((TextView) recreated.findViewById(R.id.etCheckIn)).getText().length() > 0);
        controller.pause().stop().destroy();
    }
}
