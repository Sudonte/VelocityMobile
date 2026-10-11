package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.AdditionalAmenitiesActivity;
import com.example.velocitysuites.Booking;
import com.example.velocitysuites.BookingDetailsActivity;
import com.example.velocitysuites.DashboardActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.network.SessionManager;
import com.example.velocitysuites.network.ApiMapper;
import com.example.velocitysuites.network.dto.AdditionalAmenityInfoDto;
import com.example.velocitysuites.network.dto.DirectBookingResponseDto;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;

/** A guest who is staying can reach "Request Additional Amenities" from the Dashboard's Next Stay card and from Booking Details. */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AmenityEntryPointsTest {

    private Context app;
    private ScreenTestSupport.FakeApi api;

    @Before
    public void setUp() throws Exception {
        app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
        ScreenTestSupport.goOnline(app);
        SessionManager.saveSession(app, "test-token", 1L, "Ana", "Reyes", "", "Ana Reyes", "ana@example.com",
                "09171234567", "Female", "Jan 01, 1990", "");
        Field last = com.example.velocitysuites.NavUtils.class.getDeclaredField("lastClickAtMs");
        last.setAccessible(true);
        last.setLong(null, 0L);
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Manila"));
    }

    private static DirectBookingResponseDto booking(long id, String status, int checkInDaysAway, int checkOutDaysAway) {
        DirectBookingResponseDto d = ScreenTestSupport.directBooking(id, 1800);
        d.booking_status = status;
        d.check_in = today().plusDays(checkInDaysAway).toString();
        d.check_out = today().plusDays(checkOutDaysAway).toString();
        return d;
    }

    private DashboardActivity dashboardWith(DirectBookingResponseDto booking) {
        DashboardActivity a = Robolectric.buildActivity(DashboardActivity.class).setup().get();
        idle();
        ScreenTestSupport.answerBookingLoad(api, 0, ScreenTestSupport.reservations(), ScreenTestSupport.directBookings(booking));
        idle();
        return a;
    }

    private static Intent pickerIntent(DashboardActivity a) {
        Intent next;
        while ((next = shadowOf(a).getNextStartedActivity()) != null) {
            if (!"android.content.pm.action.REQUEST_PERMISSIONS".equals(next.getAction())) return next;
        }
        return null;
    }

    @Test
    public void nextStay_whileStaying_offersTheButton_andItOpensThePicker() {
        DashboardActivity a = dashboardWith(booking(338, "CHECKED_IN", -1, 2));

        View button = a.findViewById(R.id.btnNextStayRequestAmenities);
        assertEquals(View.VISIBLE, button.getVisibility());
        assertTrue("48dp touch target", button.getLayoutParams().height >= Math.round(48 * a.getResources().getDisplayMetrics().density));

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        button.performClick();
        Intent opened = pickerIntent(a);
        assertNotNull(opened);
        assertEquals(new ComponentName(a, AdditionalAmenitiesActivity.class), opened.getComponent());
        assertEquals("338", opened.getStringExtra(AdditionalAmenitiesActivity.EXTRA_ID));
        assertTrue(opened.getBooleanExtra(AdditionalAmenitiesActivity.EXTRA_HAS_BOOKING, false));
    }

    @Test
    public void nextStay_beforeCheckIn_doesNotOfferIt() {
        DashboardActivity a = dashboardWith(booking(339, "ACTIVE_BOOKING", 3, 4));
        assertEquals(View.GONE, a.findViewById(R.id.btnNextStayRequestAmenities).getVisibility());
    }

    private BookingDetailsActivity details(Booking booking) {
        Intent intent = new Intent(app, BookingDetailsActivity.class).putExtra(BookingDetailsActivity.EXTRA_BOOKING, booking);
        BookingDetailsActivity a = Robolectric.buildActivity(BookingDetailsActivity.class, intent).setup().get();
        idle();
        return a;
    }

    private static AdditionalAmenityInfoDto info(boolean can, String reason) {
        AdditionalAmenityInfoDto dto = new AdditionalAmenityInfoDto();
        dto.can_request = can;
        dto.reason = reason;
        dto.requests = new ArrayList<>();
        return dto;
    }

    @Test
    public void bookingDetails_forACheckedInStay_enablesTheButton_andOpensThePicker() {
        Booking booking = ApiMapper.toBooking(booking(338, "CHECKED_IN", -1, 2));
        BookingDetailsActivity a = details(booking);

        View button = a.findViewById(R.id.btnRequestAmenitiesDetails);
        assertEquals(View.VISIBLE, button.getVisibility());
        assertFalse("disabled while the server is asked", button.isEnabled());
        api.<AdditionalAmenityInfoDto>last("getBookingAdditionalAmenities").succeed(info(true, null));
        idle();
        assertTrue(button.isEnabled());
        assertEquals(View.GONE, a.findViewById(R.id.tvRequestAmenitiesDetailsReason).getVisibility());

        button.performClick();
        Intent opened = shadowOf(a).getNextStartedActivity();
        assertEquals(new ComponentName(a, AdditionalAmenitiesActivity.class), opened.getComponent());
        assertEquals("338", opened.getStringExtra(AdditionalAmenitiesActivity.EXTRA_ID));
    }

    @Test
    public void bookingDetails_whenTheServerSaysNo_disablesTheButton_andExplains() {
        BookingDetailsActivity a = details(ApiMapper.toBooking(booking(340, "ACTIVE_BOOKING", 3, 4)));
        api.<AdditionalAmenityInfoDto>last("getBookingAdditionalAmenities").succeed(info(false, "Extra amenities can be requested once your booking is confirmed."));
        idle();

        assertFalse(a.findViewById(R.id.btnRequestAmenitiesDetails).isEnabled());
        TextView reason = a.findViewById(R.id.tvRequestAmenitiesDetailsReason);
        assertEquals(View.VISIBLE, reason.getVisibility());
        assertEquals("Extra amenities can be requested once your booking is confirmed.", reason.getText().toString());
    }

    @Test
    public void bookingDetails_ifTheAnswerCantBeLoaded_stillLetsTheGuestOpenThePicker() {
        BookingDetailsActivity a = details(ApiMapper.toBooking(booking(338, "CHECKED_IN", -1, 2)));
        api.<AdditionalAmenityInfoDto>last("getBookingAdditionalAmenities").offline();
        idle();
        assertTrue("the picker has its own error + Retry", a.findViewById(R.id.btnRequestAmenitiesDetails).isEnabled());
    }

    @Test
    public void bookingDetails_forACheckedOutStay_hidesTheButton() {
        BookingDetailsActivity a = details(ApiMapper.toBooking(booking(341, "COMPLETED_BOOKING", -3, -1)));
        assertEquals(View.GONE, a.findViewById(R.id.btnRequestAmenitiesDetails).getVisibility());
        assertEquals(0, api.count("getBookingAdditionalAmenities"));
    }
}
