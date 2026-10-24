package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.Booking;
import com.example.velocitysuites.BookingDetailsActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.RoomRepository;
import com.example.velocitysuites.StayInfo;
import com.example.velocitysuites.network.ApiMapper;
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

/**
 * The money of a guest who is in house, on the two screens that follow the front desk: Booking Details groups it
 * (Check-in Billing, Amenities &amp; Charges, Final Summary) and says "Remaining Balance – payable at check-out";
 * pending / rejected amenity requests are listed apart and never counted. (The dashboard's Your Next Stay card is
 * covered in DashboardScreenTest with the same fixture.)
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class StayMoneyScreensTest {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    /** In house since yesterday: 2 nights x 1,000, Senior 20% -> 1,600 paid at check-in; one approved amenity (200), one pending (100), a 300 desk charge. */
    static DirectBookingResponseDto inHouseWithMoney() {
        return inHouse("{\"amenity_name\":\"Massage\",\"quantity\":1,\"subtotal\":100,\"status\":\"pending\"}");
    }

    static DirectBookingResponseDto inHouse(String requestedAmenityJson) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Manila"));
        String json = "{\"id\":901,\"check_in\":\"" + today.minusDays(1) + "\",\"check_out\":\"" + today.plusDays(1) + "\","
                + "\"rooms_requested\":1,\"adults\":1,\"number_of_guests\":1,\"booking_status\":\"CHECKED_IN\",\"total_amount_due\":2100,"
                + "\"confirmed_at\":\"2026-09-30T14:00:00Z\",\"room_type\":{\"id\":1,\"name\":\"Deluxe\",\"rate\":\"1000.00\"},\"room_type_id\":1,\"payments\":[],"
                + "\"stay_bill\":{\"check_in\":\"2026-10-16\",\"scheduled_check_out\":\"2026-10-18\",\"actual_check_out\":\"2026-10-18\","
                + "\"scheduled_nights\":2,\"actual_nights\":2,\"extra_nights\":0,\"room_charge\":2000,\"total\":2100,\"total_paid\":1600,\"balance\":500,"
                + "\"additional_amenities\":[{\"amenity_name\":\"Breakfast\",\"quantity\":1,\"unit_price\":200,\"subtotal\":200}],\"additional_amenities_total\":200,"
                + "\"requested_amenities\":[" + requestedAmenityJson + "],"
                + "\"receipt_breakdown\":{\"checkin\":{\"room_charge\":2000,\"booked_amenities\":0,\"discount_name\":\"Senior Citizen\",\"discount_percent\":20,"
                + "\"discount\":400,\"grand_total\":1600,\"amount_paid\":1600,\"paid_in_full\":true,\"status\":\"PAID IN FULL\"},"
                + "\"after_checkin\":{\"amenities\":[{\"name\":\"Breakfast\",\"quantity\":1,\"unit_price\":200,\"subtotal\":200}],\"amenities_total\":200,"
                + "\"charges\":[{\"name\":\"Broken lamp\",\"quantity\":1,\"unit_price\":300,\"subtotal\":300}],\"charges_total\":300,\"stay_adjustment\":0,\"subtotal\":500},"
                + "\"remaining_balance\":500,\"final_grand_total\":2100,\"total_paid\":1600,\"balance_due\":500}}}";
        return new Gson().fromJson(json, DirectBookingResponseDto.class);
    }

    private Context app;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        RoomRepository ignored = ScreenTestSupport.freshRepository(app, new ScreenTestSupport.FakeApi());
        ScreenTestSupport.goOnline(app);
    }

    private static void collect(View v, List<String> out) {
        if (v instanceof TextView && v.getVisibility() == View.VISIBLE && ((TextView) v).getText().length() > 0) out.add(((TextView) v).getText().toString());
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) collect(((ViewGroup) v).getChildAt(i), out);
    }

    private String summaryText(DirectBookingResponseDto dto) {
        Booking booking = ApiMapper.toBooking(dto);
        BookingDetailsActivity a = Robolectric.buildActivity(BookingDetailsActivity.class, BookingDetailsActivity.newIntent(app, booking)).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        List<String> rows = new ArrayList<>();
        collect(a.getWindow().getDecorView(), rows);
        return String.join(" | ", rows);
    }

    private static void assertInOrder(String all, String... needles) {
        int last = 0;
        for (String needle : needles) {
            int at = all.indexOf(needle, last);
            assertTrue("'" + needle + "' missing or out of order in: " + all, at >= 0);
            last = at;
        }
    }

    @Test
    public void bookingDetails_putsTheMoneyInOneBill_andLabelsTheBalanceDue() {
        String all = summaryText(inHouseWithMoney());

        assertInOrder(all, "Massage × 1 · ₱100.00", "Pending approval – not counted", "Room charges", "₱2,000.00", "Amenities", "₱200.00",
                "Other charges: Broken lamp", "₱300.00", "Discount (Senior Citizen)", "−₱400.00", "Grand total", "₱2,100.00",
                "Paid so far", "₱1,600.00", "Balance due", "₱500.00", "You pay the balance at the hotel.");
        assertFalse("the generic summary and the old groups are replaced", all.contains("Payment Balance") || all.contains("Check-in Billing") || all.contains("Final Summary"));
    }

    @Test
    public void aRejectedRequest_isListedApartWithItsReason_andNeverCounted() {
        String all = summaryText(inHouse("{\"amenity_name\":\"Massage\",\"quantity\":1,\"subtotal\":100,\"status\":\"rejected\",\"reason\":\"Spa is closed\"}"));

        assertInOrder(all, "Massage × 1 · ₱100.00", "Not approved (Spa is closed) – not counted");
        assertFalse("a rejected request is never added to the balance", all.contains("₱600.00"));
    }

    @Test
    public void theStayCarriesApprovedAndRequestedAmenitiesFromTheServer() {
        StayInfo stay = ApiMapper.toBooking(inHouseWithMoney()).getStay();
        assertEquals(1, stay.approvedAmenities.size());
        assertEquals(200.0, stay.approvedAmenitiesTotal, 0.001);
        assertEquals(1, stay.requestedAmenities.size());
        assertFalse(stay.requestedAmenities.get(0).rejected);
    }
}
