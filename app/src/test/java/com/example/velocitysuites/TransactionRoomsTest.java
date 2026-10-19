package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.network.ApiMapper;
import com.example.velocitysuites.network.dto.DirectBookingResponseDto;
import com.example.velocitysuites.ui.TestApplication;
import com.google.gson.Gson;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** "Your rooms": each room, its approved amenities grouped with summed quantities, subtotals, "All rooms" fallback, one shared view. */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, application = TestApplication.class)
public class TransactionRoomsTest {

    private static Context ctx() {
        return new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_VelocitySuites);
    }

    /** An in-house stay with two rooms; approved: Pillow x1, Blanket x1 + Blanket x1 (two requests); one request still pending. */
    private static Booking inHouse() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Manila"));
        String json = "{\"id\":901,\"check_in\":\"" + today.minusDays(1) + "\",\"check_out\":\"" + today.plusDays(1) + "\","
                + "\"rooms_requested\":2,\"adults\":2,\"number_of_guests\":2,\"booking_status\":\"CHECKED_IN\",\"total_amount_due\":8000,"
                + "\"confirmed_at\":\"2026-09-30T14:00:00Z\",\"room_type\":{\"id\":1,\"name\":\"Deluxe\",\"rate\":\"1000.00\"},\"room_type_id\":1,\"payments\":[],"
                + "\"stay_bill\":{\"check_in\":\"2026-10-16\",\"scheduled_check_out\":\"2026-10-18\",\"actual_check_out\":\"2026-10-18\","
                + "\"scheduled_nights\":2,\"actual_nights\":2,\"extra_nights\":0,\"room_charge\":7560,\"total\":7860,\"total_paid\":7560,\"balance\":300,"
                + "\"rooms\":[{\"room_number\":\"1-001\",\"room_type\":\"Deluxe\",\"rate\":1000,\"nights\":2,\"subtotal\":3780},"
                + "{\"room_number\":\"602\",\"room_type\":\"Executive\",\"rate\":1000,\"nights\":2,\"subtotal\":3780}],"
                + "\"additional_amenities\":[{\"amenity_name\":\"Additional Pillow\",\"quantity\":1,\"unit_price\":50,\"subtotal\":50},"
                + "{\"amenity_name\":\"Additional Blanket\",\"quantity\":1,\"unit_price\":100,\"subtotal\":100},"
                + "{\"amenity_name\":\"Additional Blanket\",\"quantity\":1,\"unit_price\":100,\"subtotal\":100}],\"additional_amenities_total\":250,"
                + "\"requested_amenities\":[{\"amenity_name\":\"Massage\",\"quantity\":1,\"subtotal\":300,\"status\":\"pending\"},"
                + "{\"amenity_name\":\"Spa\",\"quantity\":1,\"subtotal\":500,\"status\":\"rejected\",\"reason\":\"closed\"}]}}";
        return ApiMapper.toBooking(new Gson().fromJson(json, DirectBookingResponseDto.class));
    }

    private static void collect(View v, List<String> out) {
        if (v instanceof TextView && v.getVisibility() == View.VISIBLE && ((TextView) v).getText().length() > 0) out.add(((TextView) v).getText().toString());
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) collect(((ViewGroup) v).getChildAt(i), out);
    }

    @Test
    public void everyRoomIsListed_withItsNumberAndType() {
        TransactionRooms model = TransactionRooms.of(inHouse());
        assertEquals(2, model.rooms.size());
        assertEquals("1-001", model.rooms.get(0).number);
        assertEquals("Deluxe", model.rooms.get(0).type);
        assertEquals("602", model.rooms.get(1).number);
    }

    @Test
    public void approvedAmenities_areGroupedByItemWithSummedQuantity_andOnlyApprovedAreCounted() {
        TransactionRooms model = TransactionRooms.of(inHouse());
        assertFalse("the server's stay bill does not link an amenity to a room", model.linkedToRooms());
        TransactionRooms.Group all = model.allRoomsGroup();
        assertNotNull(all);
        assertEquals(2, all.items.size());
        assertEquals("Additional Pillow", all.items.get(0).name);
        assertEquals(1, all.items.get(0).quantity);
        assertEquals("Additional Blanket", all.items.get(1).name);
        assertEquals(2, all.items.get(1).quantity);
        assertEquals(200.0, all.items.get(1).subtotal, 0.001);
        assertEquals(250.0, all.subtotal, 0.001);
        assertEquals(250.0, model.amenitiesTotal, 0.001);
        assertEquals("one waiting request; the rejected one is not shown", 1, model.waiting.size());
        assertEquals("Massage", model.waiting.get(0).name);
    }

    @Test
    public void amenitiesThatCarryARoomNumber_areGroupedPerRoom_withTheirOwnSubtotals() {
        List<TransactionRooms.RoomItem> rooms = Arrays.asList(new TransactionRooms.RoomItem("1-001", "Deluxe"), new TransactionRooms.RoomItem("602", "Executive"));
        List<TransactionRooms.Line> lines = new ArrayList<>();
        lines.add(new TransactionRooms.Line("Additional Pillow", 1, 50, "1-001"));
        lines.add(new TransactionRooms.Line("Additional Blanket", 1, 100, "1-001"));
        lines.add(new TransactionRooms.Line("Additional Blanket", 1, 100, "602"));
        TransactionRooms model = new TransactionRooms(rooms, lines, new ArrayList<>());
        assertTrue(model.linkedToRooms());
        assertEquals(150.0, model.groupFor("1-001").subtotal, 0.001);
        assertEquals(100.0, model.groupFor("602").subtotal, 0.001);
        assertNull(model.allRoomsGroup());
        assertEquals(250.0, model.amenitiesTotal, 0.001);
        // the view: each room card shows its own amenities and subtotal
        List<String> texts = new ArrayList<>();
        collect(TransactionRoomsView.build(ctx(), model), texts);
        String all = String.join(" | ", texts);
        assertTrue(all, all.indexOf("Room 1-001 · Deluxe") < all.indexOf("Additional Pillow × 1"));
        assertTrue(all, all.indexOf("Room amenities subtotal | ₱150.00") > 0);
        assertTrue(all, all.indexOf("Room 602 · Executive") > all.indexOf("₱150.00"));
        assertTrue(all, all.contains("Room amenities subtotal | ₱100.00"));
        assertTrue(all, all.endsWith("Amenities total | ₱250.00"));
    }

    @Test
    public void theView_showsRoomsAllRoomsWaitingAndTheTotal() {
        List<String> texts = new ArrayList<>();
        collect(TransactionRoomsView.build(ctx(), inHouse()), texts);
        String all = String.join(" | ", texts);
        assertTrue(all, all.startsWith("Your rooms | Room 1-001 · Deluxe | Room 602 · Executive | All rooms | Approved amenities"));
        assertTrue(all, all.contains("Additional Pillow × 1 | ₱50.00"));
        assertTrue(all, all.contains("Additional Blanket × 2 | ₱200.00"));
        assertTrue(all, all.contains("Amenities subtotal | ₱250.00"));
        assertTrue(all, all.contains("Waiting for approval | Massage × 1 | Pending"));
        assertTrue(all, all.endsWith("Amenities total | ₱250.00"));
        assertFalse("a rejected request is never listed", all.contains("Spa"));
        assertFalse("a waiting request is never counted", all.contains("₱300.00"));
    }

    @Test
    public void aStayWithNoAmenities_saysSo_andABookingBeforeCheckInHasNoRoomNumberYet() {
        LocalDate in = LocalDate.now(ZoneId.of("Asia/Manila")).plusDays(3);
        Booking b = new Booking("5", "1", "Deluxe", "Deluxe", in.toString(), in.plusDays(1).toString(), 2, 1000, "Confirmed", "Oct 01, 2026");
        b.setHasBooking(true);
        b.setDirectBooking(true);
        List<String> texts = new ArrayList<>();
        collect(TransactionRoomsView.build(ctx(), b), texts);
        String all = String.join(" | ", texts);
        assertTrue(all, all.contains("Deluxe · room assigned at check-in"));
        assertTrue(all, all.contains("No amenities added"));
        assertFalse(all, all.contains("Amenities total"));
    }

    @Test
    public void totalsStayConsistent_withTheSharedTotal() {
        Booking b = inHouse();
        TransactionStatusHelper.Summary s = TransactionStatusHelper.summarize(b);
        assertEquals(s.grandTotal, BookingTotals.totalOf(b, null), 0.001);
        // the Next Stay details use the same rooms section and end with Total = Paid + one Balance due line
        List<NextStayDetails.Row> rows = NextStayDetails.rows(ctx(), b);
        boolean hasRooms = false;
        for (NextStayDetails.Row r : rows) hasRooms |= r.kind == NextStayDetails.Kind.ROOMS;
        assertTrue(hasRooms);
        LinearLayout box = new LinearLayout(ctx());
        NextStayDetails.render(ctx(), box, rows, b);
        assertNotNull(box.findViewById(R.id.yourRoomsSection));
    }
}
