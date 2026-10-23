package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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

import com.example.velocitysuites.AdditionalAmenitiesActivity;
import com.example.velocitysuites.Booking;
import com.example.velocitysuites.BookingDetailsActivity;
import com.example.velocitysuites.FrontDeskNote;
import com.example.velocitysuites.PaymentReceiptActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.ReceiptDetail;
import com.example.velocitysuites.network.ApiMapper;
import com.example.velocitysuites.network.dto.AdditionalAmenityInfoDto;
import com.example.velocitysuites.network.dto.AdditionalAmenitySubmitRequest;
import com.example.velocitysuites.network.dto.AmenityDto;
import com.example.velocitysuites.network.dto.DirectBookingResponseDto;
import com.example.velocitysuites.network.dto.ReceiptDetailResponse;
import com.google.gson.Gson;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * ONE booking with several stays, on every guest screen that shows it: the payload maps to stays, the list card says "2 stays", the
 * details screen has a Stays section with each stay's rooms (room numbers), amenities and subtotal and a one-line explanation under the
 * total, the receipt lists every stay, a GCash payment under review says so, and an amenity request picks the stay first.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class StaysScreensTest {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private static final String BOOKING_JSON = "{\"id\":581,\"guest_id\":1,\"room_type_id\":1,\"rooms_requested\":4,"
            + "\"check_in\":\"2026-10-25T00:00:00.000000Z\",\"check_out\":\"2026-10-28T00:00:00.000000Z\",\"adults\":1,\"children\":0,"
            + "\"number_of_guests\":1,\"booking_status\":\"ACTIVE_BOOKING\",\"payment_method\":\"gcash\",\"total_amount_due\":18600.0,"
            + "\"stays\":["
            + "{\"id\":10,\"position\":1,\"check_in\":\"2026-10-25\",\"check_out\":\"2026-10-26\",\"nights\":1,\"state\":\"in_house\","
            + "\"rooms\":[{\"room_type_id\":\"1\",\"room_type\":\"Deluxe\",\"quantity\":1,\"price_per_night\":3500,\"nights\":1,\"subtotal\":3500,\"assigned_room_numbers\":[\"303\"]},"
            + "{\"room_type_id\":\"8\",\"room_type\":\"Executive\",\"quantity\":1,\"price_per_night\":5800,\"nights\":1,\"subtotal\":5800,\"assigned_room_numbers\":[\"601\"]}],"
            + "\"amenities\":[{\"amenity_id\":\"4\",\"amenity_name\":\"Extra bed\",\"quantity\":1,\"unit_price\":300,\"subtotal\":300,\"room_label\":\"Deluxe (room 1)\"}],"
            + "\"rooms_subtotal\":9300,\"amenities_subtotal\":300,\"subtotal\":9600},"
            + "{\"id\":11,\"position\":2,\"check_in\":\"2026-10-27\",\"check_out\":\"2026-10-28\",\"nights\":1,\"state\":\"upcoming\","
            + "\"rooms\":[{\"room_type_id\":\"1\",\"room_type\":\"Deluxe\",\"quantity\":1,\"price_per_night\":3500,\"nights\":1,\"subtotal\":3500,\"assigned_room_numbers\":[]},"
            + "{\"room_type_id\":\"8\",\"room_type\":\"Executive\",\"quantity\":1,\"price_per_night\":5800,\"nights\":1,\"subtotal\":5800,\"assigned_room_numbers\":[]}],"
            + "\"amenities\":[],\"rooms_subtotal\":9300,\"amenities_subtotal\":0,\"subtotal\":9300}]}";

    private Context app;
    private ScreenTestSupport.FakeApi api;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
        ScreenTestSupport.goOnline(app);
    }

    private static Booking twoStayBooking() {
        return ApiMapper.toBooking(new Gson().fromJson(BOOKING_JSON, DirectBookingResponseDto.class));
    }

    private static String textOf(View v) {
        StringBuilder sb = new StringBuilder();
        if (v instanceof TextView && v.getVisibility() == View.VISIBLE) sb.append(((TextView) v).getText()).append('\n');
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) sb.append(textOf(((ViewGroup) v).getChildAt(i)));
        return sb.toString();
    }

    @Test
    public void theSuccessScreen_showsOneId_forAllStays_andAViewBookingButton() {
        android.app.Activity host = Robolectric.buildActivity(androidx.appcompat.app.AppCompatActivity.class).setup().get();
        host.setTheme(R.style.Theme_VelocitySuites);
        boolean[] opened = {false};
        com.example.velocitysuites.StayCardTextAccess.showCreated(host, true, "581", twoStayBooking(), () -> opened[0] = true);
        shadowOf(Looper.getMainLooper()).idle();

        androidx.appcompat.app.AlertDialog dialog = (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        String text = textOf(dialog.getWindow().getDecorView()).replace(' ', ' ');
        assertEquals("one ID, not one per stay", 1, text.split("581", -1).length - 1);
        assertTrue(text, text.contains("2 stays"));
        android.widget.Button view = dialog.findViewById(R.id.btnTransactionCreatedAction);
        assertEquals("View booking", view.getText().toString());
        view.performClick();
        assertTrue(opened[0]);
    }

    @Test
    public void thePayloadMapsToStays_andAnOlderServerWithoutThemIsOneStay() {
        Booking b = twoStayBooking();
        assertEquals("Deluxe (room 1)", b.getStays().get(0).amenities.get(0).getRoomLabel());
        assertNull("an amenity row without a room has no label", new com.example.velocitysuites.BookingAmenity("1", "Spa", 1, 10, 10).getRoomLabel());
        assertEquals(2, b.getStays().size());
        assertTrue(b.hasSeveralStays());
        assertEquals(2, b.getStayCount());
        assertEquals(9600.0, b.getStays().get(0).subtotal, 0.001);
        assertEquals("303", b.getStays().get(0).rooms.get(0).getAssignedRoomNumbers().get(0));
        assertTrue(b.getStays().get(0).isInHouse());
        assertFalse(b.getStays().get(1).isInHouse());

        DirectBookingResponseDto old = new Gson().fromJson("{\"id\":9,\"check_in\":\"2026-10-25\",\"check_out\":\"2026-10-26\"}", DirectBookingResponseDto.class);
        Booking single = ApiMapper.toBooking(old);
        assertTrue(single.getStays().isEmpty());
        assertFalse(single.hasSeveralStays());
        assertEquals("a transaction without a stay list reads as one", 1, single.getStayCount());
    }

    @Test
    public void theListCardSaysHowManyStaysInsteadOfNights() {
        Booking b = twoStayBooking();
        b.setStatus("Confirmed");
        String line = com.example.velocitysuites.StayCardTextAccess.nightsAndGuests(app, b);
        assertTrue(line, line.startsWith("2 stays"));
        assertFalse(line, line.contains("night"));
    }

    @Test
    public void detailsListsEveryStay_withRoomsRoomNumbersAmenitiesAndItsOwnSubtotal_andExplainsTheTotal() {
        Booking b = twoStayBooking();
        b.setTotalAmount(18900.0);
        BookingDetailsActivity a = Robolectric.buildActivity(BookingDetailsActivity.class, BookingDetailsActivity.newIntent(app, b)).setup().get();
        shadowOf(Looper.getMainLooper()).idle();

        String all = textOf(a.getWindow().getDecorView());
        assertTrue(all, all.contains("Stays"));
        assertTrue(all, all.contains("Stay 1"));
        assertTrue(all, all.contains("Stay 2"));
        assertTrue("room numbers once assigned: " + all, all.contains("Room 303"));
        assertTrue(all, all.contains("Extra bed"));
        assertTrue("the amenity names the room it was chosen for: " + all, all.contains("Deluxe (room 1)"));
        assertTrue("each stay's own subtotal: " + all, all.contains("Stay 1 Subtotal") && all.contains("₱9,600.00"));
        assertTrue(all, all.contains("Stay 2 Subtotal") && all.contains("₱9,300.00"));
        assertTrue("the one-line explanation under the total: " + all, all.contains("Total for all stays after discount and extras."));
    }

    @Test
    public void reservationDetails_haveTheSameStaysSection() {
        Booking r = twoStayBooking();
        r.setHasBooking(false);
        r.setTotalIncludesAmenities(true); // as every reservation from the server is: no top-up request
        BookingDetailsActivity a = Robolectric.buildActivity(BookingDetailsActivity.class, BookingDetailsActivity.newIntent(app, r)).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        String all = textOf(a.getWindow().getDecorView());
        assertTrue(all, all.contains("Stay 1 Subtotal") && all.contains("Stay 2 Subtotal"));
        assertTrue(all, all.contains("Room 303") && all.contains("Extra bed") && all.contains("Deluxe (room 1)"));
    }

    @Test
    public void transactionHistoryDetails_haveTheSameStaysSection() {
        Booking b = twoStayBooking();
        b.setTotalAmount(18900.0);
        com.example.velocitysuites.PaymentTransaction tx = new com.example.velocitysuites.PaymentTransaction(b,
                new Booking.PaymentRecord("1000.00", "GCASH", "REF1", "2026-10-20", "completed", null));
        com.example.velocitysuites.TransactionDetailsActivity a = Robolectric.buildActivity(com.example.velocitysuites.TransactionDetailsActivity.class,
                com.example.velocitysuites.TransactionDetailsActivity.newIntent(app, tx)).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        String all = textOf(a.getWindow().getDecorView());
        assertTrue(all, all.contains("Stay 1") && all.contains("Stay 2"));
        assertTrue(all, all.contains("Stay 1 Subtotal") && all.contains("₱9,600.00"));
        assertTrue(all, all.contains("Stay 2 Subtotal") && all.contains("₱9,300.00"));
        assertTrue(all, all.contains("Room 303") && all.contains("Deluxe (room 1)"));
    }

    @Test
    public void aSingleStayHasNoStaysSection_andSaysTotalForYourStay() {
        Booking b = new Booking("5", "1", "Deluxe", "Deluxe", "Oct 25, 2026", "Oct 26, 2026", 1, 3500, "Confirmed", "Oct 01, 2026");
        b.setHasBooking(true);
        BookingDetailsActivity a = Robolectric.buildActivity(BookingDetailsActivity.class, BookingDetailsActivity.newIntent(app, b)).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        String all = textOf(a.getWindow().getDecorView());
        assertFalse(all, all.contains("Stay 1 Subtotal"));
        assertTrue(all, all.contains("Total for your stay after discount and extras."));
    }

    @Test
    public void aGcashPaymentWaitingForReviewSaysPaymentUnderReview_notPayAtTheFrontDesk() {
        Booking b = twoStayBooking();
        b.setPaymentPendingVerification(true);
        b.setStaffVerified(false);
        assertEquals("Payment under review", FrontDeskNote.messageFor(app, b));
        b.setPaymentPendingVerification(false);
        assertTrue(FrontDeskNote.messageFor(app, b), FrontDeskNote.messageFor(app, b).contains("front desk"));
    }

    @Test
    public void theReceiptListsEveryStayWithItsRoomsAndSubtotal() {
        String json = ReceiptFromApiPayloadTest.RECEIPT_3.replace("\"receipt\":{", "\"receipt\":{\"stays\":" + BOOKING_JSON.substring(BOOKING_JSON.indexOf("\"stays\":[") + 8, BOOKING_JSON.length() - 1) + ",");
        ReceiptDetail detail = ApiMapper.toReceiptDetail(new Gson().fromJson(json, ReceiptDetailResponse.class));
        assertEquals(2, detail.getStays().size());
        PaymentReceiptActivity.debugPreviewFixtures.put(detail.getReceiptNumber(), detail);
        Intent intent = PaymentReceiptActivity.newIntentForReceipt(app, detail.getReceiptNumber());
        PaymentReceiptActivity a = Robolectric.buildActivity(PaymentReceiptActivity.class, intent).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        try {
            String all = textOf(a.findViewById(R.id.layoutDynamicReceiptContent));
            assertTrue(all, all.contains("Stay 1") && all.contains("Stay 2"));
            assertTrue(all, all.contains("Room 303"));
            assertTrue(all, all.contains("Stay 2 Subtotal"));
            assertTrue("the receipt names the room too: " + all, all.contains("Deluxe (room 1)"));
        } finally {
            PaymentReceiptActivity.debugPreviewFixtures.clear();
        }
    }

    // ---- Request Additional Amenities: the stay first, then the room, then the amenities ----

    private static AmenityDto amenity(long id, String name, String price, int stock) {
        AmenityDto dto = new AmenityDto();
        dto.id = id;
        dto.amenity_name = name;
        dto.charge = price;
        dto.quantity = stock;
        dto.status = "active";
        return dto;
    }

    private static AdditionalAmenityInfoDto.Stay stay(long id, int position, String in, String out) {
        AdditionalAmenityInfoDto.Stay s = new AdditionalAmenityInfoDto.Stay();
        s.stay_id = id;
        s.position = position;
        s.check_in = in;
        s.check_out = out;
        s.nights = 1;
        return s;
    }

    private static AdditionalAmenityInfoDto.Room room(long stayId, long id, String number) {
        AdditionalAmenityInfoDto.Room r = new AdditionalAmenityInfoDto.Room();
        r.stay_id = stayId;
        r.room_id = id;
        r.room_number = number;
        r.room_type = "Deluxe";
        return r;
    }

    @Test
    public void withTwoStaysInHouseTheGuestPicksTheStayFirst_andOnlyThatStaysRoomsAreOffered() {
        Intent intent = new Intent(app, AdditionalAmenitiesActivity.class)
                .putExtra(AdditionalAmenitiesActivity.EXTRA_DIRECT_BOOKING, true)
                .putExtra(AdditionalAmenitiesActivity.EXTRA_ID, "581");
        AdditionalAmenitiesActivity a = Robolectric.buildActivity(AdditionalAmenitiesActivity.class, intent).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        AdditionalAmenityInfoDto info = new AdditionalAmenityInfoDto();
        info.can_request = true;
        info.requests = new ArrayList<>();
        info.stays = Arrays.asList(stay(10, 1, "2026-10-25", "2026-10-26"), stay(11, 2, "2026-10-27", "2026-10-28"));
        info.rooms = Arrays.asList(room(10, 1, "303"), room(11, 2, "305"));
        api.<AdditionalAmenityInfoDto>last("getBookingAdditionalAmenities").succeed(info);
        shadowOf(Looper.getMainLooper()).idle();

        androidx.appcompat.app.AlertDialog d = (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertNotNull("asks which stay first", d);
        assertEquals("Which stay is this for?", String.valueOf(((TextView) d.findViewById(androidx.appcompat.R.id.alertTitle)).getText()));
        android.widget.ListView list = d.getListView();
        assertEquals(2, list.getCount());
        assertTrue(String.valueOf(list.getAdapter().getItem(1)), String.valueOf(list.getAdapter().getItem(1)).startsWith("Stay 2"));
        shadowOf(Looper.getMainLooper()).idle();
        list.performItemClick(null, 1, 0); // stay 2
        shadowOf(Looper.getMainLooper()).idle();
        api.<List<AmenityDto>>last("getAmenities").succeed(Arrays.asList(amenity(7, "Extra Bed", "200.00", 5)));
        shadowOf(Looper.getMainLooper()).idle();

        android.widget.LinearLayout rooms = a.findViewById(R.id.additionalAmenitiesRooms);
        assertEquals("only stay 2's room", 1, rooms.getChildCount());
        assertTrue(textOf(rooms), textOf(rooms).contains("305"));
        assertFalse(textOf(rooms), textOf(rooms).contains("303"));
    }

    @Test
    public void withOneStayInHouseNoStayQuestionIsAsked() {
        Intent intent = new Intent(app, AdditionalAmenitiesActivity.class)
                .putExtra(AdditionalAmenitiesActivity.EXTRA_DIRECT_BOOKING, true)
                .putExtra(AdditionalAmenitiesActivity.EXTRA_ID, "581");
        AdditionalAmenitiesActivity a = Robolectric.buildActivity(AdditionalAmenitiesActivity.class, intent).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        AdditionalAmenityInfoDto info = new AdditionalAmenityInfoDto();
        info.can_request = true;
        info.requests = new ArrayList<>();
        info.stays = Arrays.asList(stay(10, 1, "2026-10-25", "2026-10-26"));
        info.rooms = Arrays.asList(room(10, 1, "303"));
        api.<AdditionalAmenityInfoDto>last("getBookingAdditionalAmenities").succeed(info);
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(null, org.robolectric.shadows.ShadowDialog.getLatestDialog());
        api.<List<AmenityDto>>last("getAmenities").succeed(Arrays.asList(amenity(7, "Extra Bed", "200.00", 5)));
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, ((android.widget.LinearLayout) a.findViewById(R.id.additionalAmenitiesRooms)).getChildCount());
    }
}
