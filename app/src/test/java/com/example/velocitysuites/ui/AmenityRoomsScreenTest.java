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
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.Booking;
import com.example.velocitysuites.BookingDetailsActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.network.ApiMapper;
import com.example.velocitysuites.network.SessionManager;
import com.example.velocitysuites.network.dto.AdditionalAmenityInfoDto;
import com.example.velocitysuites.network.dto.DirectBookingResponseDto;
import com.google.gson.Gson;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Source (a): Booking Details asks for the guest's amenity requests once and redraws "Your rooms" with each item under its room. */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AmenityRoomsScreenTest {

    private Context app;
    private ScreenTestSupport.FakeApi api;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
        ScreenTestSupport.goOnline(app);
        SessionManager.saveSession(app, "test-token", 1L, "Ana", "Reyes", "", "Ana Reyes", "ana@example.com",
                "09171234567", "Female", "Jan 01, 1990", "");
    }

    private static Booking inHouse() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Manila"));
        String json = "{\"id\":569,\"check_in\":\"" + today.minusDays(1) + "\",\"check_out\":\"" + today.plusDays(1) + "\","
                + "\"rooms_requested\":2,\"adults\":2,\"number_of_guests\":2,\"booking_status\":\"CHECKED_IN\",\"total_amount_due\":8000,"
                + "\"confirmed_at\":\"2026-09-30T14:00:00Z\",\"room_type\":{\"id\":1,\"name\":\"Deluxe\",\"rate\":\"1000.00\"},\"room_type_id\":1,\"payments\":[],"
                + "\"stay_bill\":{\"check_in\":\"2026-10-16\",\"scheduled_check_out\":\"2026-10-18\",\"actual_check_out\":\"2026-10-18\","
                + "\"scheduled_nights\":2,\"actual_nights\":2,\"extra_nights\":0,\"room_charge\":7560,\"total\":7860,\"total_paid\":7560,\"balance\":300,"
                + "\"rooms\":[{\"room_number\":\"1-001\",\"room_type\":\"Deluxe\",\"rate\":1000,\"nights\":2,\"subtotal\":3780},"
                + "{\"room_number\":\"602\",\"room_type\":\"Executive\",\"rate\":1000,\"nights\":2,\"subtotal\":3780}],"
                + "\"additional_amenities\":[{\"amenity_name\":\"Additional Pillow\",\"quantity\":1,\"unit_price\":50,\"subtotal\":50},"
                + "{\"amenity_name\":\"Additional Blanket\",\"quantity\":1,\"unit_price\":100,\"subtotal\":100},"
                + "{\"amenity_name\":\"Additional Towel\",\"quantity\":1,\"unit_price\":50,\"subtotal\":50},"
                + "{\"amenity_name\":\"Additional Blanket\",\"quantity\":1,\"unit_price\":100,\"subtotal\":100}],\"additional_amenities_total\":300}}";
        return ApiMapper.toBooking(new Gson().fromJson(json, DirectBookingResponseDto.class));
    }

    private static void texts(View v, List<String> out) {
        if (v instanceof TextView && v.getVisibility() == View.VISIBLE && ((TextView) v).getText().length() > 0) out.add(((TextView) v).getText().toString());
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) texts(((ViewGroup) v).getChildAt(i), out);
    }

    private static AdditionalAmenityInfoDto requests() {
        String json = "{\"can_request\":true,\"requests\":["
                + "{\"id\":1,\"name\":\"Additional Pillow\",\"quantity\":1,\"unit_price\":50,\"subtotal\":50,\"room_number\":\"1-001\",\"status\":\"approved\"},"
                + "{\"id\":2,\"name\":\"Additional Blanket\",\"quantity\":1,\"unit_price\":100,\"subtotal\":100,\"room_number\":\"1-001\",\"status\":\"approved\"},"
                + "{\"id\":3,\"name\":\"Additional Towel\",\"quantity\":1,\"unit_price\":50,\"subtotal\":50,\"room_number\":\"602\",\"status\":\"approved\"},"
                + "{\"id\":4,\"name\":\"Additional Blanket\",\"quantity\":1,\"unit_price\":100,\"subtotal\":100,\"room_number\":\"602\",\"status\":\"approved\"},"
                + "{\"id\":5,\"name\":\"Massage\",\"quantity\":1,\"unit_price\":300,\"subtotal\":300,\"room_number\":\"602\",\"status\":\"pending\"}]}";
        return new Gson().fromJson(json, AdditionalAmenityInfoDto.class);
    }

    @Test
    public void bookingDetails_redrawsYourRooms_withEachItemUnderItsRoom_afterOneRequestListCall() {
        Intent intent = new Intent(app, BookingDetailsActivity.class).putExtra(BookingDetailsActivity.EXTRA_BOOKING, inHouse());
        BookingDetailsActivity a = Robolectric.buildActivity(BookingDetailsActivity.class, intent).setup().get();
        shadowOf(Looper.getMainLooper()).idle();

        List<String> before = new ArrayList<>();
        texts(a.findViewById(R.id.yourRoomsSection), before);
        String beforeText = String.join(" | ", before);
        assertTrue("until the server answers, the bill's items are under Other amenities: " + beforeText, beforeText.contains("Other amenities"));

        assertEquals("the button check and the rooms share ONE call", 1, api.count("getBookingAdditionalAmenities"));
        api.<AdditionalAmenityInfoDto>last("getBookingAdditionalAmenities").succeed(requests());
        shadowOf(Looper.getMainLooper()).idle();

        List<String> after = new ArrayList<>();
        View section = a.findViewById(R.id.yourRoomsSection);
        assertNotNull(section);
        texts(section, after);
        String all = String.join(" | ", after);
        assertTrue(all, all.contains("Room 1-001 | Deluxe | Approved amenities | Additional Pillow × 1 | ₱50.00 | Additional Blanket × 1 | ₱100.00 | Room amenities | ₱150.00"));
        assertTrue(all, all.contains("Room 602 | Executive | Approved amenities | Additional Towel × 1 | ₱50.00 | Additional Blanket × 1 | ₱100.00 | Waiting for approval | Massage × 1 | ₱300.00 | Pending | Room amenities | ₱150.00"));
        assertTrue(all, all.endsWith("Amenities total | ₱300.00"));
        assertFalse(all, all.contains("Other amenities"));
    }

    @Test
    public void bookingDetails_ifTheRequestListCannotBeLoaded_keepsTheBillAndShowsOtherAmenities() {
        Intent intent = new Intent(app, BookingDetailsActivity.class).putExtra(BookingDetailsActivity.EXTRA_BOOKING, inHouse());
        BookingDetailsActivity a = Robolectric.buildActivity(BookingDetailsActivity.class, intent).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        api.<AdditionalAmenityInfoDto>last("getBookingAdditionalAmenities").offline();
        shadowOf(Looper.getMainLooper()).idle();

        List<String> out = new ArrayList<>();
        texts(a.findViewById(R.id.yourRoomsSection), out);
        String all = String.join(" | ", out);
        assertTrue(all, all.contains("Other amenities"));
        assertTrue(all, all.endsWith("Amenities total | ₱300.00"));
    }
}
