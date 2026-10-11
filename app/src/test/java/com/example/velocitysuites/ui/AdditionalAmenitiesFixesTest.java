package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.AdditionalAmenitiesActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.network.ApiMapper;
import com.example.velocitysuites.network.dto.AdditionalAmenityInfoDto;
import com.example.velocitysuites.network.dto.DirectBookingResponseDto;
import com.example.velocitysuites.network.dto.ReservationDto;
import com.google.gson.Gson;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * The amenity picker's real-world failure: it asked for the wrong id (a converted reservation's stay was sent to the
 * booking route), told every failure "check your connection", and had a white-on-pink header. Each is pinned here.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AdditionalAmenitiesFixesTest {

    /** What GET /api/guest/bookings/{id}/additional-amenities returns (shape pinned by the backend feature test). */
    private static final String SAMPLE = "{\"booking_id\":338,\"can_request\":true,\"reason\":null,\"requests\":["
            + "{\"id\":5,\"amenity_id\":7,\"name\":\"Extra Bed\",\"quantity\":2,\"unit_price\":200,\"subtotal\":400,"
            + "\"status\":\"rejected\",\"rejection_reason\":\"Out of stock today\",\"note\":null,"
            + "\"requested_at\":\"2026-10-11T03:40:00+00:00\",\"decided_at\":\"2026-10-11T04:00:00+00:00\"},"
            + "{\"id\":6,\"amenity_id\":8,\"name\":\"Breakfast\",\"quantity\":1,\"unit_price\":75.5,\"subtotal\":75.5,"
            + "\"status\":\"pending\",\"rejection_reason\":null,\"note\":\"Before 8pm\","
            + "\"requested_at\":\"2026-10-11T03:41:00+00:00\",\"decided_at\":null}],\"total_approved\":0}";

    private Context app;
    private ScreenTestSupport.FakeApi api;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
        ScreenTestSupport.goOnline(app);
    }

    @Test
    public void aRealServerResponse_parsesIntoTheDataClasses() {
        AdditionalAmenityInfoDto info = new Gson().fromJson(SAMPLE, AdditionalAmenityInfoDto.class);

        assertTrue(info.can_request);
        assertEquals(2, info.requests.size());
        assertEquals("Extra Bed", info.requests.get(0).name);
        assertEquals(2, info.requests.get(0).quantity);
        assertEquals(200.0, info.requests.get(0).unit_price, 0.001);
        assertEquals(400.0, info.requests.get(0).subtotal, 0.001);
        assertEquals("rejected", info.requests.get(0).status);
        assertEquals("Out of stock today", info.requests.get(0).rejection_reason);
        assertEquals("Before 8pm", info.requests.get(1).note);
        assertEquals(75.5, info.requests.get(1).unit_price, 0.001);
    }

    private AdditionalAmenitiesActivity open(boolean direct) {
        Intent intent = new Intent(app, AdditionalAmenitiesActivity.class)
                .putExtra(AdditionalAmenitiesActivity.EXTRA_DIRECT_BOOKING, direct)
                .putExtra(AdditionalAmenitiesActivity.EXTRA_ID, "42");
        AdditionalAmenitiesActivity a = Robolectric.buildActivity(AdditionalAmenitiesActivity.class, intent).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        return a;
    }

    private static String stateText(AdditionalAmenitiesActivity a) {
        return ((TextView) a.findViewById(R.id.additionalAmenitiesStateText)).getText().toString();
    }

    @Test
    public void aConvertedReservationsStay_asksTheReservationRoute_notTheBookingRoute() {
        open(false);
        assertEquals(1, api.count("getReservationAdditionalAmenities"));
        assertEquals(0, api.count("getBookingAdditionalAmenities"));
        assertEquals("42", api.lastArgs.get("getReservationAdditionalAmenities")[0]);
    }

    @Test
    public void aDirectBooking_asksTheBookingRoute() {
        open(true);
        assertEquals(1, api.count("getBookingAdditionalAmenities"));
        assertEquals(0, api.count("getReservationAdditionalAmenities"));
    }

    @Test
    public void aServerError_isNeverBlamedOnTheConnection() {
        for (int status : new int[]{404, 500, 503}) {
            AdditionalAmenitiesActivity a = open(true);
            api.<AdditionalAmenityInfoDto>last("getBookingAdditionalAmenities").http(status);
            shadowOf(Looper.getMainLooper()).idle();

            assertEquals("HTTP " + status, a.getString(R.string.error_server_error), stateText(a));
            assertFalse(stateText(a).toLowerCase().contains("connection"));
            assertEquals(View.VISIBLE, a.findViewById(R.id.additionalAmenitiesRetry).getVisibility());
        }
    }

    @Test
    public void aFrameworkErrorBody_isNotShownToTheGuest() {
        AdditionalAmenitiesActivity a = open(true);
        api.<AdditionalAmenityInfoDto>last("getBookingAdditionalAmenities")
                .httpWithBody(404, "{\"message\":\"No query results for model [App\\\\Models\\\\Booking] 42.\"}");
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(a.getString(R.string.error_server_error), stateText(a));
    }

    @Test
    public void offline_saysNoInternet_butOnlineWithAnUnreachableServerDoesNot() {
        AdditionalAmenitiesActivity a = open(true);
        api.<AdditionalAmenityInfoDto>last("getBookingAdditionalAmenities").offline(); // the device is online (see setUp)
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("online but unreachable: the server's problem", a.getString(R.string.error_server_unreachable), stateText(a));

        // now really offline
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        shadowOf(cm).setActiveNetworkInfo(null);
        shadowOf(cm).setNetworkCapabilities(cm.getActiveNetwork(), null);
        AdditionalAmenitiesActivity b = open(true);
        api.<AdditionalAmenityInfoDto>last("getBookingAdditionalAmenities").offline();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(b.getString(R.string.error_no_internet_connection), stateText(b));
    }

    @Test
    public void theHeaderTitleAndBackArrow_areDarkRedOnTheLightHeader_notWhite() {
        AdditionalAmenitiesActivity a = open(true);
        int red = ContextCompat.getColor(app, R.color.velocity_red_primary);
        TextView title = (TextView) ((android.view.ViewGroup) a.findViewById(R.id.btnAdditionalAmenitiesBack).getParent()).getChildAt(1);
        assertEquals(red, title.getCurrentTextColor());
        android.widget.ImageButton back = a.findViewById(R.id.btnAdditionalAmenitiesBack);
        assertNotNull(back.getImageTintList());
        assertEquals(red, back.getImageTintList().getDefaultColor());
    }

    @Test
    public void aReservationDerivedBooking_andADirectBooking_mapToTheRightRoute() {
        ReservationDto r = ScreenTestSupport.reservation(588, 1800);
        r.booking = new com.example.velocitysuites.network.dto.BookingDto();
        r.booking.id = 338;
        assertFalse("converted reservation: the reservation id and route", ApiMapper.toBooking(r).isDirectBooking());
        assertTrue(ApiMapper.toBooking(ScreenTestSupport.directBooking(338, 1800)).isDirectBooking());
    }
}
