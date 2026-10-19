package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.R;
import com.example.velocitysuites.RoomBrowsingActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Calendar;

/** The search card's nights count, the pinned date summary, and Reset. */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class RoomBrowsingSearchCardTest {

    private RoomBrowsingActivity activity;

    @Before
    public void setUp() {
        Context app = ApplicationProvider.getApplicationContext();
        ScreenTestSupport.freshRepository(app, new ScreenTestSupport.FakeApi());
        activity = Robolectric.buildActivity(RoomBrowsingActivity.class, new Intent(app, RoomBrowsingActivity.class)).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
    }

    private void setDates(Calendar in, Calendar out) throws Exception {
        Field fi = RoomBrowsingActivity.class.getDeclaredField("checkInDate");
        fi.setAccessible(true);
        fi.set(activity, in);
        Field fo = RoomBrowsingActivity.class.getDeclaredField("checkOutDate");
        fo.setAccessible(true);
        fo.set(activity, out);
        Method m = RoomBrowsingActivity.class.getDeclaredMethod("updateDateSummary");
        m.setAccessible(true);
        m.invoke(activity);
    }

    private static Calendar day(int year, int month, int day) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month, day);
        return c;
    }

    @Test
    public void nothingShownUntilBothDatesArePicked() {
        assertEquals(View.GONE, activity.findViewById(R.id.tvPinnedDates).getVisibility());
    }

    @Test
    public void bothDates_showNightsUnderTheFields_andACompactSummaryInThePinnedBar() throws Exception {
        setDates(day(2026, Calendar.OCTOBER, 12), day(2026, Calendar.OCTOBER, 14));

        assertEquals("2 nights", ((com.google.android.material.textfield.TextInputLayout) activity.findViewById(R.id.checkOutLayout)).getHelperText().toString());

        TextView pinned = activity.findViewById(R.id.tvPinnedDates);
        assertEquals(View.VISIBLE, pinned.getVisibility());
        assertTrue(pinned.getText().toString(), pinned.getText().toString().endsWith("2 nights"));
        assertTrue(pinned.getText().toString(), pinned.getText().toString().contains("Oct 12 → Oct 14"));
        assertTrue("tapping it is possible", pinned.hasOnClickListeners());
    }

    @Test
    public void oneNight_isSingular() throws Exception {
        setDates(day(2026, Calendar.OCTOBER, 12), day(2026, Calendar.OCTOBER, 13));
        assertEquals("1 night", ((com.google.android.material.textfield.TextInputLayout) activity.findViewById(R.id.checkOutLayout)).getHelperText().toString());
    }

    @Test
    public void reset_clearsTheDatesAndTheSummary() throws Exception {
        setDates(day(2026, Calendar.OCTOBER, 12), day(2026, Calendar.OCTOBER, 14));
        activity.findViewById(R.id.btnResetSearch).performClick();
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals("", ((TextView) activity.findViewById(R.id.etCheckIn)).getText().toString());
        assertEquals(View.GONE, activity.findViewById(R.id.tvPinnedDates).getVisibility());
    }
}
